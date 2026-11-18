<?php
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('GET');$u=current_user();$id=(string)($_GET['post_id']??'');if(!can_view_post($id,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Post unavailable']],403);
$q=db()->prepare('SELECT c.*,(SELECT COUNT(*) FROM comment_likes l WHERE l.comment_id=c.id) AS likes FROM comments c WHERE c.post_id=? AND c.deleted_at IS NULL ORDER BY c.created_at,c.id LIMIT 100');$q->execute([$id]);json_response(['comments'=>$q->fetchAll()]);
