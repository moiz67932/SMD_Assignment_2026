<?php
// Chats (20): my conversations, newest first, with the other person, the last message and my unread count.
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();$me=$u['id'];
$q=db()->prepare('SELECT c.id,IF(c.user_low=?,c.user_high,c.user_low) AS peer_id,u.display_name AS peer_name,u.photo_media_id AS peer_photo_media_id,
 (SELECT r.last_read_at FROM chat_reads r WHERE r.chat_id=c.id AND r.user_id=?) AS my_last_read_at
 FROM chats c JOIN users u ON u.id=IF(c.user_low=?,c.user_high,c.user_low) WHERE c.user_low=? OR c.user_high=?');
$q->execute([$me,$me,$me,$me,$me]);$chats=[];
foreach($q->fetchAll() as $c){
 $m=db()->prepare('SELECT id,sender_id,body,type,created_at,deleted_at FROM messages WHERE chat_id=? ORDER BY created_at DESC,id DESC LIMIT 1');$m->execute([$c['id']]);$last=$m->fetch();
 if(!$last)continue; // a chat with no messages yet is not listed
 $n=db()->prepare('SELECT COUNT(*) FROM messages WHERE chat_id=? AND sender_id<>? AND deleted_at IS NULL AND type<>\'system\' AND (? IS NULL OR created_at>?)');$n->execute([$c['id'],$me,$c['my_last_read_at'],$c['my_last_read_at']]);
 $c['unread']=(int)$n->fetchColumn();$c['last_message']=$last;$chats[]=$c;
}
usort($chats,fn($a,$b)=>strcmp($b['last_message']['created_at'],$a['last_message']['created_at']));json_response(['chats'=>$chats]);
