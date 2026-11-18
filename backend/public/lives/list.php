<?php
// Home: which friends are live right now (red LIVE tiles).
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();
$q=db()->prepare('SELECT l.host_id FROM lives l WHERE l.heartbeat_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 30 SECOND) AND l.host_id<>? AND EXISTS(SELECT 1 FROM friendships f WHERE (f.user_low=l.host_id AND f.user_high=?) OR (f.user_high=l.host_id AND f.user_low=?))');$q->execute([$u['id'],$u['id'],$u['id']]);json_response(['hosts'=>$q->fetchAll(PDO::FETCH_COLUMN)]);
