<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();
$q=db()->prepare('SELECT u.id,u.display_name,u.username,u.photo_media_id FROM friendships f JOIN users u ON u.id=IF(f.user_low=?,f.user_high,f.user_low) WHERE f.user_low=? OR f.user_high=? ORDER BY u.display_name');$q->execute([$u['id'],$u['id'],$u['id']]);$friends=$q->fetchAll();
$q=db()->prepare("SELECT id,sender_id,receiver_id,created_at FROM friend_requests WHERE state='pending' AND (sender_id=? OR receiver_id=?) ORDER BY created_at DESC");$q->execute([$u['id'],$u['id']]);json_response(['friends'=>$friends,'requests'=>$q->fetchAll()]);
