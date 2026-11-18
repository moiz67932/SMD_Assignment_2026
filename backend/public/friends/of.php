<?php
// Friends of any user (Profile 15 / Other profile 17 friend grid). Names and photos are public profile fields.
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');current_user();$id=(string)($_GET['id']??'');
$q=db()->prepare('SELECT u.id,u.display_name,u.username,u.photo_media_id FROM friendships f JOIN users u ON u.id=IF(f.user_low=?,f.user_high,f.user_low) WHERE f.user_low=? OR f.user_high=? ORDER BY u.display_name LIMIT 200');$q->execute([$id,$id,$id]);json_response(['friends'=>$q->fetchAll()]);
