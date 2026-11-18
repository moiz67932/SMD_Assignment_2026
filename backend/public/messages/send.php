<?php
require_once dirname(__DIR__,2).'/src/media.php';require_once dirname(__DIR__,2).'/src/notifications.php';require_method('POST');$u=current_user();$d=request_json();
$chat=(string)($d['chat_id']??'');$peer=(string)($d['peer_id']??'');
// Offline-first: a message queued before the chat existed carries only the other person's id; create the chat (idempotent).
if($chat===''&&valid_uuid($peer)&&$peer!==$u['id']){$q=db()->prepare('SELECT 1 FROM users WHERE id=?');$q->execute([$peer]);if($q->fetchColumn()){$pair=[$u['id'],$peer];sort($pair);
 $q=db()->prepare('INSERT INTO chats(id,user_low,user_high,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id=id');$q->execute([uuid_v4(),...$pair]);
 $q=db()->prepare('SELECT id FROM chats WHERE user_low=? AND user_high=?');$q->execute($pair);$chat=(string)$q->fetchColumn();}}$body=trim((string)($d['body']??''));$id=(string)($d['id']??'');$type=(string)($d['type']??'text');$media=$d['media_id']??null;
if(!valid_uuid($id)||strlen($body)>10000||!in_array($type,['text','image','video','audio','file','post','call'],true)||(in_array($type,['text','call'],true)&&$body==='')||(in_array($type,['image','video','audio','file'],true)&&!is_string($media)))json_response(['error'=>['code'=>'invalid_message','message'=>'Invalid message or attachment']],400);
$extra=$d['extra']??null;if($extra!==null&&(!is_array($extra)||strlen(json_encode($extra))>2000))json_response(['error'=>['code'=>'invalid_extra','message'=>'Invalid message details']],400);
if($type==='post'&&!valid_uuid((string)($extra['post_id']??'')))json_response(['error'=>['code'=>'invalid_post','message'=>'Shared post id missing']],400);
if(!chat_member($chat,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Chat unavailable']],403);
mutate($u,$d,'message.send',function($pdo)use($id,$chat,$body,$u,$type,$media,$extra){if($media!==null){$m=owned_media($media,$u['id']);if(in_array($type,['image','video','audio'],true)&&!str_starts_with($m['mime_type'],$type.'/'))throw new DomainException('Attachment type mismatch');}
$q=$pdo->prepare('INSERT INTO messages(id,chat_id,sender_id,body,type,extra,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([$id,$chat,$u['id'],$body,$type,$extra===null?null:json_encode($extra,JSON_UNESCAPED_UNICODE)]);
$q=$pdo->prepare('INSERT INTO chat_reads(chat_id,user_id,last_read_at) VALUES(?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE last_read_at=UTC_TIMESTAMP(6)');$q->execute([$chat,$u['id']]);
$q=$pdo->prepare('DELETE FROM chat_typing WHERE chat_id=? AND user_id=?');$q->execute([$chat,$u['id']]);if($media!==null){$q=$pdo->prepare('INSERT INTO message_attachments(message_id,media_id) VALUES(?,?)');$q->execute([$id,$media]);}
$q=$pdo->prepare('SELECT user_low,user_high FROM chats WHERE id=?');$q->execute([$chat]);$c=$q->fetch();$recipient=$c['user_low']===$u['id']?$c['user_high']:$c['user_low'];if($type!=='call')notify_user($pdo,$recipient,$u['id'],'message',$id,$type==='text'?$body:($type==='post'?'Shared a post':'Sent a '.$type),['chatId'=>$chat,'title'=>$u['display_name']]);
$q=$pdo->prepare('SELECT * FROM messages WHERE id=?');$q->execute([$id]);return ['message'=>$q->fetch()];});
