<?php
// One post (Comments 06 header, Reaction picker 05, a post shared in chat). Same visibility: me or my friends.
require_once dirname(__DIR__,2).'/src/posts.php';require_method('GET');$u=current_user();$id=(string)($_GET['id']??'');
if(!can_view_post($id,$u['id']))json_response(['error'=>['code'=>'not_found','message'=>'Post unavailable']],404);
$q=db()->prepare('SELECT * FROM posts WHERE id=?');$q->execute([$id]);json_response(['post'=>decorate_posts([$q->fetch()],$u['id'])[0]]);
