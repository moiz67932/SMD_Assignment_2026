<?php
// Opening a chat marks it read ("Seen" for the other person; clears my unread count). Not an action receipt: it is idempotent.
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();$chat=(string)($d['chat_id']??'');
if(!chat_member($chat,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Chat unavailable']],403);
$q=db()->prepare('INSERT INTO chat_reads(chat_id,user_id,last_read_at) VALUES(?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE last_read_at=UTC_TIMESTAMP(6)');$q->execute([$chat,$u['id']]);json_response(['ok'=>true]);
