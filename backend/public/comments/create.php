<?php
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('POST');$u=current_user();$d=request_json();$id=(string)($d['id']??'');$post=(string)($d['post_id']??'');$body=trim((string)($d['body']??''));$parent=$d['parent_id']??null;
if(!valid_uuid($id)||$body===''||strlen($body)>5000)json_response(['error'=>['code'=>'invalid_comment','message'=>'Provide comment UUID and text up to 5000 bytes']],400);
if(!can_view_post($post,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Post unavailable']],403);
mutate($u,$d,'comment.create',function($pdo)use($id,$post,$body,$parent,$u){if($parent!==null){$q=$pdo->prepare('SELECT 1 FROM comments WHERE id=? AND post_id=? AND deleted_at IS NULL');$q->execute([$parent,$post]);if(!$q->fetchColumn())throw new DomainException('Reply parent unavailable');}
$q=$pdo->prepare('INSERT INTO comments(id,post_id,author_id,parent_id,body,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([$id,$post,$u['id'],$parent,$body]);return ['id'=>$id];});
