<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();
$q=db()->prepare('SELECT id,display_name,username,bio,photo_media_id,cover_media_id,profile_setup_complete,first_name,last_name,gender,website,IF(id=?,email,NULL) AS email,IF(id=?,phone,NULL) AS phone,IF(id=?,birthday,NULL) AS birthday,created_at FROM users WHERE id=?');$q->execute([$u['id'],$u['id'],$u['id'],$_GET['id']??$u['id']]);$row=$q->fetch();if(!$row)json_response(['error'=>['code'=>'not_found','message'=>'Profile unavailable']],404);
// Other profile (17): how I am related, friend count and mutual friends.
$me=$u['id'];$id=$row['id'];$pair=[$me,$id];sort($pair);
if($id===$me)$relation='self';
else{$q=db()->prepare('SELECT 1 FROM friendships WHERE user_low=? AND user_high=?');$q->execute($pair);
 if($q->fetchColumn())$relation='friends';
 else{$q=db()->prepare("SELECT sender_id FROM friend_requests WHERE state='pending' AND ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?))");$q->execute([$me,$id,$id,$me]);$r=$q->fetchColumn();$relation=$r===false?'none':($r===$me?'requested':'incoming');}}
$q=db()->prepare('SELECT COUNT(*) FROM friendships WHERE user_low=? OR user_high=?');$q->execute([$id,$id]);$row['friend_count']=(int)$q->fetchColumn();
$q=db()->prepare('SELECT IF(a.user_low=?,a.user_high,a.user_low) AS uid FROM friendships a WHERE (a.user_low=? OR a.user_high=?) AND IF(a.user_low=?,a.user_high,a.user_low) IN (SELECT IF(b.user_low=?,b.user_high,b.user_low) FROM friendships b WHERE b.user_low=? OR b.user_high=?) LIMIT 50');
$q->execute([$id,$id,$id,$id,$me,$me,$me]);$mutual=array_column($q->fetchAll(),'uid');
$row['relation']=$relation;$row['mutual_count']=$id===$me?0:count($mutual);$row['mutual_ids']=$id===$me?[]:array_slice($mutual,0,6);
json_response(['user'=>$row]);
