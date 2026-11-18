<?php
// "typing…": the flag expires by itself after 6 seconds unless the app refreshes it.
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();$chat=(string)($d['chat_id']??'');
if(!chat_member($chat,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Chat unavailable']],403);
if(!empty($d['typing'])){$q=db()->prepare('INSERT INTO chat_typing(chat_id,user_id,until) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 6 SECOND)) ON DUPLICATE KEY UPDATE until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 6 SECOND)');$q->execute([$chat,$u['id']]);}
else{$q=db()->prepare('DELETE FROM chat_typing WHERE chat_id=? AND user_id=?');$q->execute([$chat,$u['id']]);}json_response(['ok'=>true]);
