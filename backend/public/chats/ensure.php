<?php
require_once dirname(__DIR__,2).'/src/actions.php';
require_method('POST');$u=current_user();$d=request_json();$other=(string)($d['other_id']??'');
if(!valid_uuid($other)||$other===$u['id'])json_response(['error'=>['code'=>'invalid_peer','message'=>'Choose another user']],400);
$q=db()->prepare('SELECT id FROM users WHERE id=?');$q->execute([$other]);if(!$q->fetchColumn())json_response(['error'=>['code'=>'not_found','message'=>'User unavailable']],404);
mutate($u,$d,'chat.ensure',function($pdo)use($u,$other){$pair=[$u['id'],$other];sort($pair);$id=uuid_v4();$q=$pdo->prepare('INSERT INTO chats(id,user_low,user_high,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id=id');$q->execute([$id,...$pair]);$q=$pdo->prepare('SELECT id FROM chats WHERE user_low=? AND user_high=?');$q->execute($pair);return ['chat_id'=>$q->fetchColumn()];});
