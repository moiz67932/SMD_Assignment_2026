<?php
// Double-tap heart on a message (one per person, toggled).
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();$id=(string)($d['message_id']??'');
$q=db()->prepare('SELECT chat_id FROM messages WHERE id=? AND deleted_at IS NULL');$q->execute([$id]);$chat=$q->fetchColumn();if(!$chat||!chat_member($chat,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Message unavailable']],403);
if(!isset($d['hearted'])||!is_bool($d['hearted']))json_response(['error'=>['code'=>'invalid_state','message'=>'hearted must be a boolean']],400);
mutate($u,$d,'message.heart',function($pdo)use($id,$u,$d){if($d['hearted']){$q=$pdo->prepare('INSERT IGNORE INTO message_hearts(message_id,user_id,created_at) VALUES(?,?,UTC_TIMESTAMP(6))');}else{$q=$pdo->prepare('DELETE FROM message_hearts WHERE message_id=? AND user_id=?');}$q->execute([$id,$u['id']]);return ['ok'=>true,'hearted'=>$d['hearted']];});
