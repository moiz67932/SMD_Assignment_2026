<?php
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();
mutate($u,$d,'message.delete',function($pdo)use($u,$d){$q=$pdo->prepare("UPDATE messages SET body='',deleted_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=? AND sender_id=? AND deleted_at IS NULL AND UTC_TIMESTAMP(6) < DATE_ADD(created_at, INTERVAL 5 MINUTE)");$q->execute([$d['id']??'',$u['id']]);if($q->rowCount()!==1)throw new DomainException('Message cannot be deleted');return ['ok'=>true];});
