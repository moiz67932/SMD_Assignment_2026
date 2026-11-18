<?php
// Host goes live (Agora broadcast channel). Also the host's heartbeat: a live with no heartbeat for 30 s is over.
require_once dirname(__DIR__,2).'/src/notifications.php';require_method('POST');$u=current_user();$d=request_json();$channel=(string)($d['channel']??'');
if(!preg_match('/^[A-Za-z0-9_-]{1,80}$/',$channel))json_response(['error'=>['code'=>'invalid_channel','message'=>'Invalid channel']],400);
$q=db()->prepare('SELECT 1 FROM lives WHERE host_id=? AND heartbeat_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 30 SECOND)');$q->execute([$u['id']]);$already=(bool)$q->fetchColumn();
$q=db()->prepare('INSERT INTO lives(host_id,channel,started_at,heartbeat_at) VALUES(?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE channel=VALUES(channel),heartbeat_at=UTC_TIMESTAMP(6)'.($already?'':',started_at=UTC_TIMESTAMP(6)'));$q->execute([$u['id'],$channel]);
if(!$already){$pdo=db();$pdo->beginTransaction();$q=$pdo->prepare('SELECT IF(user_low=?,user_high,user_low) FROM friendships WHERE user_low=? OR user_high=? LIMIT 40');$q->execute([$u['id'],$u['id'],$u['id']]);
 foreach($q->fetchAll(PDO::FETCH_COLUMN) as $friend)notify_user($pdo,$friend,$u['id'],'live',$u['id'],'is live now');$pdo->commit();}
json_response(['ok'=>true]);
