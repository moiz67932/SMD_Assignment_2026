<?php
require_once __DIR__.'/bootstrap.php';
/** Caller owns the transaction: logical notification and delivery intents commit together. */
function notify_user(PDO $pdo,string $recipient,string $actor,string $type,string $resource,string $body,array $extra=[]):void {
 $id=uuid_v4();$q=$pdo->prepare('INSERT INTO notifications(id,recipient_id,actor_id,type,resource_id,body,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([$id,$recipient,$actor,$type,$resource,substr($body,0,500)]);
 $q=$pdo->prepare('SELECT id FROM devices WHERE user_id=? AND enabled=1');$q->execute([$recipient]);$devices=$q->fetchAll();
 $payload=array_merge(['notification_id'=>$id,'type'=>$type,'fromUid'=>$actor,'title'=>'Kinnect','body'=>substr($body,0,500)],$extra);
 foreach($devices as $device){$q=$pdo->prepare('INSERT INTO push_jobs(id,notification_id,device_id,payload,next_attempt_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([uuid_v4(),$id,$device['id'],json_encode($payload,JSON_THROW_ON_ERROR)]);}
}
