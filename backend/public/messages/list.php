<?php
require_once dirname(__DIR__,2).'/src/actions.php';require_method('GET');$u=current_user();$chat=(string)($_GET['chat_id']??'');
if(!chat_member($chat,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Chat unavailable']],403);
$q=db()->prepare('SELECT * FROM messages WHERE chat_id=? ORDER BY created_at DESC,id DESC LIMIT 100');$q->execute([$chat]);$rows=array_reverse($q->fetchAll());foreach($rows as &$row){$m=db()->prepare('SELECT m.id,m.mime_type,m.original_name,m.byte_size FROM message_attachments a JOIN media m ON m.id=a.media_id WHERE a.message_id=?');$m->execute([$row['id']]);$row['attachments']=$m->fetchAll();}unset($row);json_response(['messages'=>$rows]);
