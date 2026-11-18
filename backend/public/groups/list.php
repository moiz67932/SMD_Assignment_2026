<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();
$q=db()->prepare('SELECT g.id,g.name,(SELECT COUNT(*) FROM group_members m2 WHERE m2.group_id=g.id) AS member_count FROM groups_ g JOIN group_members m ON m.group_id=g.id WHERE m.user_id=? ORDER BY g.created_at DESC');$q->execute([$u['id']]);json_response(['groups'=>$q->fetchAll()]);
