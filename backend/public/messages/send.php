<?php
require_once dirname(__DIR__,2).'/src/media.php';require_once dirname(__DIR__,2).'/src/notifications.php';require_method('POST');$u=current_user();$d=request_json();
$chat=(string)($d['chat_id']??'');$body=trim((string)($d['body']??''));$id=(string)($d['id']??'');$type=(string)($d['type']??'text');$media=$d['media_id']??null;
if(!valid_uuid($id)||strlen($body)>10000||!in_array($type,['text','image','video','audio','file'],true)||($type==='text'&&$body==='')||($type!=='text'&&!is_string($media)))json_response(['error'=>['code'=>'invalid_message','message'=>'Invalid message or attachment']],400);
if(!chat_member($chat,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Chat unavailable']],403);
mutate($u,$d,'message.send',function($pdo)use($id,$chat,$body,$u,$type,$media){if($media!==null){$m=owned_media($media,$u['id']);if(in_array($type,['image','video','audio'],true)&&!str_starts_with($m['mime_type'],$type.'/'))throw new DomainException('Attachment type mismatch');}
$q=$pdo->prepare('INSERT INTO messages(id,chat_id,sender_id,body,type,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([$id,$chat,$u['id'],$body,$type]);if($media!==null){$q=$pdo->prepare('INSERT INTO message_attachments(message_id,media_id) VALUES(?,?)');$q->execute([$id,$media]);}
$q=$pdo->prepare('SELECT user_low,user_high FROM chats WHERE id=?');$q->execute([$chat]);$c=$q->fetch();$recipient=$c['user_low']===$u['id']?$c['user_high']:$c['user_low'];notify_user($pdo,$recipient,$u['id'],'message',$id,$type==='text'?$body:'Sent a '.$type,['chatId'=>$chat]);
$q=$pdo->prepare('SELECT * FROM messages WHERE id=?');$q->execute([$id]);return ['message'=>$q->fetch()];});
