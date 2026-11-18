<?php
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('GET');$u=current_user();$g=(string)($_GET['group_id']??'');
if(!group_member($g,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Group unavailable']],403);
$q=db()->prepare('SELECT gm.*,u.display_name AS sender_name FROM group_messages gm JOIN users u ON u.id=gm.sender_id WHERE gm.group_id=? ORDER BY gm.created_at DESC,gm.id DESC LIMIT 200');$q->execute([$g]);json_response(['messages'=>array_reverse($q->fetchAll())]);
