<?php
require_once dirname(__DIR__,2).'/src/actions.php';require_method('GET');$u=current_user();$chat=(string)($_GET['chat_id']??'');
if(!chat_member($chat,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Chat unavailable']],403);
$q=db()->prepare('SELECT * FROM messages WHERE chat_id=? ORDER BY created_at DESC,id DESC LIMIT 200');$q->execute([$chat]);$rows=array_reverse($q->fetchAll());
foreach($rows as &$row){$m=db()->prepare('SELECT m.id,m.mime_type,m.original_name,m.byte_size FROM message_attachments a JOIN media m ON m.id=a.media_id WHERE a.message_id=?');$m->execute([$row['id']]);$row['attachments']=$m->fetchAll();
 $h=db()->prepare('SELECT user_id FROM message_hearts WHERE message_id=?');$h->execute([$row['id']]);$row['hearts']=array_column($h->fetchAll(),'user_id');$row['extra']=$row['extra']?json_decode($row['extra'],true):null;}unset($row);
// The other member: when they last read this chat ("Seen") and whether they are typing now.
$q=db()->prepare('SELECT IF(user_low=?,user_high,user_low) FROM chats WHERE id=?');$q->execute([$u['id'],$chat]);$peer=$q->fetchColumn();
$q=db()->prepare('SELECT last_read_at FROM chat_reads WHERE chat_id=? AND user_id=?');$q->execute([$chat,$peer]);$peerRead=$q->fetchColumn()?:null;
$q=db()->prepare('SELECT 1 FROM chat_typing WHERE chat_id=? AND user_id=? AND until>UTC_TIMESTAMP(6)');$q->execute([$chat,$peer]);
json_response(['messages'=>$rows,'peer_id'=>$peer,'peer_last_read_at'=>$peerRead,'peer_typing'=>(bool)$q->fetchColumn(),'server_time'=>gmdate('Y-m-d H:i:s')]);
