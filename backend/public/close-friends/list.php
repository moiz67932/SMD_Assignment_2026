<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();$q=db()->prepare('SELECT u.id,u.display_name,u.username,u.photo_media_id FROM close_friends c JOIN users u ON u.id=c.member_id WHERE c.owner_id=? ORDER BY u.display_name');$q->execute([$u['id']]);json_response(['members'=>$q->fetchAll()]);
