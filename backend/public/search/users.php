<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();$filter=$_GET['filter']??'everyone';
if(!in_array($filter,['everyone','friends','requests'],true))json_response(['error'=>['code'=>'invalid_filter','message'=>'Choose everyone, friends or requests']],400);
$term=trim((string)($_GET['q']??''));if(strlen($term)>100)json_response(['error'=>['code'=>'invalid_query','message'=>'Search is too long']],400);
$where='u.id <> ? AND (LOCATE(?,u.display_name)>0 OR LOCATE(?,COALESCE(u.username,\'\'))>0)';$args=[$u['id'],$term,$term];
if($filter==='friends'){$where.=' AND EXISTS(SELECT 1 FROM friendships f WHERE (f.user_low=? AND f.user_high=u.id) OR (f.user_high=? AND f.user_low=u.id))';array_push($args,$u['id'],$u['id']);}
if($filter==='requests'){$where.=" AND EXISTS(SELECT 1 FROM friend_requests r WHERE r.state='pending' AND ((r.sender_id=? AND r.receiver_id=u.id) OR (r.receiver_id=? AND r.sender_id=u.id)))";array_push($args,$u['id'],$u['id']);}
$q=db()->prepare('SELECT u.id,u.display_name,u.username,u.bio,u.photo_media_id FROM users u WHERE '.$where.' ORDER BY u.display_name,u.id LIMIT 50');$q->execute($args);json_response(['users'=>$q->fetchAll()]);
