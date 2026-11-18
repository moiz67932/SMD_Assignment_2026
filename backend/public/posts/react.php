<?php
require_once dirname(__DIR__,2).'/src/visibility.php';require_once dirname(__DIR__,2).'/src/notifications.php';require_method('POST');$u=current_user();$d=request_json();$id=(string)($d['post_id']??'');$reaction=$d['reaction']??null;
if($reaction!==null&&!in_array($reaction,['Like','Love','Laugh','Wow','Sad','Angry'],true))json_response(['error'=>['code'=>'invalid_reaction','message'=>'Invalid reaction']],400);
if(!can_view_post($id,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Post unavailable']],403);
mutate($u,$d,'post.react',function($pdo)use($id,$u,$reaction){if($reaction===null){$q=$pdo->prepare('DELETE FROM post_reactions WHERE post_id=? AND user_id=?');$q->execute([$id,$u['id']]);}else{$q=$pdo->prepare('INSERT INTO post_reactions(post_id,user_id,reaction,updated_at) VALUES(?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE reaction=VALUES(reaction),updated_at=UTC_TIMESTAMP(6)');$q->execute([$id,$u['id'],$reaction]);
 // Tell the author (not for my own posts, and only once per post so changing the reaction does not spam them).
 $q=$pdo->prepare('SELECT author_id FROM posts WHERE id=?');$q->execute([$id]);$author=$q->fetchColumn();
 if($author&&$author!==$u['id']){$q=$pdo->prepare("SELECT 1 FROM notifications WHERE recipient_id=? AND actor_id=? AND type='reaction' AND resource_id=?");$q->execute([$author,$u['id'],$id]);if(!$q->fetchColumn())notify_user($pdo,$author,$u['id'],'reaction',$id,$u['display_name'].' reacted to your post',['postId'=>$id]);}}
 return ['ok'=>true,'reaction'=>$reaction];});
