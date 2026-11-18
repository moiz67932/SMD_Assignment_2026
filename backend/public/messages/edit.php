<?php
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();$body=trim((string)($d['body']??''));
if($body===''||strlen($body)>10000)json_response(['error'=>['code'=>'invalid_message','message'=>'Provide text up to 10000 bytes']],400);
mutate($u,$d,'message.edit',function($pdo)use($u,$d,$body){$q=$pdo->prepare('UPDATE messages SET body=?,edited_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=? AND sender_id=? AND deleted_at IS NULL AND UTC_TIMESTAMP(6) < DATE_ADD(created_at, INTERVAL 5 MINUTE)');$q->execute([$body,$d['id']??'',$u['id']]);if($q->rowCount()!==1)throw new DomainException('Message cannot be edited');return ['ok'=>true];});
