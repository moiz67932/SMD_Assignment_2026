<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();
$q=db()->prepare('SELECT id,display_name,username,bio,photo_media_id,cover_media_id,profile_setup_complete,first_name,last_name,gender,website,IF(id=?,email,NULL) AS email,IF(id=?,phone,NULL) AS phone,IF(id=?,birthday,NULL) AS birthday FROM users WHERE id=?');$q->execute([$u['id'],$u['id'],$u['id'],$_GET['id']??$u['id']]);$row=$q->fetch();if(!$row)json_response(['error'=>['code'=>'not_found','message'=>'Profile unavailable']],404);json_response(['user'=>$row]);
