<?php
// Reaction picker (05): who reacted to a post and with what.
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('GET');$u=current_user();$id=(string)($_GET['post_id']??'');
if(!can_view_post($id,$u['id']))json_response(['error'=>['code'=>'not_found','message'=>'Post unavailable']],404);
$q=db()->prepare('SELECT r.user_id,r.reaction,r.updated_at,u.display_name,u.photo_media_id FROM post_reactions r JOIN users u ON u.id=r.user_id WHERE r.post_id=? ORDER BY r.updated_at DESC');$q->execute([$id]);json_response(['reactions'=>$q->fetchAll()]);
