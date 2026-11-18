<?php
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('POST');$u=current_user();$d=request_json();$id=(string)($d['post_id']??'');$reaction=$d['reaction']??null;
if($reaction!==null&&!in_array($reaction,['Like','Love','Laugh','Wow','Sad','Angry'],true))json_response(['error'=>['code'=>'invalid_reaction','message'=>'Invalid reaction']],400);
if(!can_view_post($id,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Post unavailable']],403);
mutate($u,$d,'post.react',function($pdo)use($id,$u,$reaction){if($reaction===null){$q=$pdo->prepare('DELETE FROM post_reactions WHERE post_id=? AND user_id=?');$q->execute([$id,$u['id']]);}else{$q=$pdo->prepare('INSERT INTO post_reactions(post_id,user_id,reaction,updated_at) VALUES(?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE reaction=VALUES(reaction),updated_at=UTC_TIMESTAMP(6)');$q->execute([$id,$u['id'],$reaction]);}return ['ok'=>true,'reaction'=>$reaction];});
