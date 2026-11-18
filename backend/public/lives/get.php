<?php
// One host's live video (friends only), with the number of people watching. ?join=1 counts me as a viewer.
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('GET');$u=current_user();$host=(string)($_GET['host']??'');
if(!are_friends($host,$u['id']))json_response(['error'=>['code'=>'not_found','message'=>'Live video unavailable']],404);
$q=db()->prepare('SELECT l.*,u.display_name AS host_name FROM lives l JOIN users u ON u.id=l.host_id WHERE l.host_id=? AND l.heartbeat_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 30 SECOND)');$q->execute([$host]);$live=$q->fetch();
if(!$live)json_response(['live'=>null]);
if(!empty($_GET['join'])&&$host!==$u['id']){$q=db()->prepare('INSERT INTO live_viewers(host_id,viewer_id,seen_at) VALUES(?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE seen_at=UTC_TIMESTAMP(6)');$q->execute([$host,$u['id']]);}
$q=db()->prepare('SELECT COUNT(*) FROM live_viewers WHERE host_id=? AND seen_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 15 SECOND)');$q->execute([$host]);$live['viewers']=(int)$q->fetchColumn();json_response(['live'=>$live]);
