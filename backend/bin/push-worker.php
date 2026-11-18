<?php
/** Run under a supervisor. --once is useful for controlled verification. */
declare(strict_types=1);
require_once dirname(__DIR__).'/src/bootstrap.php';
function oauth_token(array $key):string {
 static $cached=null,$expiry=0;if($cached&&time()<$expiry)return $cached;
 $b64=fn($s)=>rtrim(strtr(base64_encode($s),'+/','-_'),'=');$now=time();
 $header=$b64(json_encode(['alg'=>'RS256','typ'=>'JWT']));$claims=$b64(json_encode(['iss'=>$key['client_email'],'scope'=>'https://www.googleapis.com/auth/firebase.messaging','aud'=>'https://oauth2.googleapis.com/token','iat'=>$now,'exp'=>$now+3600]));$input="$header.$claims";
 if(!openssl_sign($input,$signature,$key['private_key'],OPENSSL_ALGO_SHA256))throw new RuntimeException('Unable to sign OAuth assertion');
 $ch=curl_init('https://oauth2.googleapis.com/token');curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>http_build_query(['grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer','assertion'=>$input.'.'.$b64($signature)]),CURLOPT_RETURNTRANSFER=>true,CURLOPT_TIMEOUT=>20]);$raw=curl_exec($ch);$status=curl_getinfo($ch,CURLINFO_RESPONSE_CODE);curl_close($ch);$reply=json_decode($raw?:'',true);if($status!==200||!isset($reply['access_token']))throw new RuntimeException('FCM OAuth unavailable');$cached=$reply['access_token'];$expiry=$now+max(60,((int)($reply['expires_in']??3600))-120);return $cached;
}
$path=env_value('FCM_SERVICE_ACCOUNT');if(!$path||!is_readable($path)){fwrite(STDERR,"Set FCM_SERVICE_ACCOUNT to a private readable service-account JSON path\n");exit(2);}
$key=json_decode(file_get_contents($path),true,512,JSON_THROW_ON_ERROR);foreach(['client_email','private_key','project_id'] as $field)if(empty($key[$field]))throw new RuntimeException('Invalid service account');
$once=in_array('--once',$argv,true);
do {
 $pdo=db();$pdo->beginTransaction();$q=$pdo->query("SELECT j.*,d.fcm_token,d.enabled,d.user_id AS device_user,n.recipient_id FROM push_jobs j JOIN devices d ON d.id=j.device_id JOIN notifications n ON n.id=j.notification_id WHERE j.state IN ('queued','sending') AND j.next_attempt_at<=UTC_TIMESTAMP(6) AND (j.lease_until IS NULL OR j.lease_until<UTC_TIMESTAMP(6)) ORDER BY j.next_attempt_at,j.id LIMIT 1 FOR UPDATE SKIP LOCKED");$job=$q->fetch();
 if(!$job){$pdo->commit();if(!$once)usleep(500000);continue;}
 $q=$pdo->prepare("UPDATE push_jobs SET state='sending',attempts=attempts+1,lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 90 SECOND) WHERE id=?");$q->execute([$job['id']]);$pdo->commit();
 try {
  if(!$job['enabled'] || $job['device_user']!==$job['recipient_id']){$q=$pdo->prepare("UPDATE push_jobs SET state='cancelled',lease_until=NULL WHERE id=?");$q->execute([$job['id']]);continue;}
  $data=array_map(fn($v)=>(string)$v,json_decode($job['payload'],true,512,JSON_THROW_ON_ERROR));$token=oauth_token($key);
  $ch=curl_init('https://fcm.googleapis.com/v1/projects/'.rawurlencode($key['project_id']).'/messages:send');curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_HTTPHEADER=>['Authorization: Bearer '.$token,'Content-Type: application/json'],CURLOPT_POSTFIELDS=>json_encode(['message'=>['token'=>$job['fcm_token'],'data'=>$data,'android'=>['priority'=>'high']]],JSON_THROW_ON_ERROR),CURLOPT_RETURNTRANSFER=>true,CURLOPT_TIMEOUT=>25]);$raw=curl_exec($ch);$code=curl_getinfo($ch,CURLINFO_RESPONSE_CODE);curl_close($ch);
  if($code>=200&&$code<300){$q=$pdo->prepare("UPDATE push_jobs SET state='sent',lease_until=NULL,last_error=NULL WHERE id=?");$q->execute([$job['id']]);}
  elseif(str_contains($raw?:'','UNREGISTERED')){$q=$pdo->prepare('UPDATE devices SET enabled=0 WHERE id=?');$q->execute([$job['device_id']]);$q=$pdo->prepare("UPDATE push_jobs SET state='cancelled',lease_until=NULL,last_error='Unregistered device' WHERE id=?");$q->execute([$job['id']]);}
  else throw new RuntimeException('FCM delivery HTTP '.$code);
 }catch(Throwable $e){$delay=min(3600,5*(2**min(9,(int)$job['attempts'])));$q=$pdo->prepare("UPDATE push_jobs SET state='queued',lease_until=NULL,last_error=?,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL ? SECOND) WHERE id=?");$q->execute([substr($e->getMessage(),0,500),$delay,$job['id']]);}
}while(!$once);
