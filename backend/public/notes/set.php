<?php
// Chats "Your note": 60 characters for 24 hours (empty body deletes it).
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();$body=trim((string)($d['body']??''));
if(strlen($body)>180)json_response(['error'=>['code'=>'invalid_note','message'=>'Notes are up to 60 characters']],400);
mutate($u,$d,'note.set',function($pdo)use($u,$body){if($body===''){$q=$pdo->prepare('DELETE FROM notes WHERE user_id=?');$q->execute([$u['id']]);return ['ok'=>true];}
 $q=$pdo->prepare('INSERT INTO notes(user_id,body,created_at,expires_at) VALUES(?,?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 24 HOUR)) ON DUPLICATE KEY UPDATE body=VALUES(body),created_at=VALUES(created_at),expires_at=VALUES(expires_at)');$q->execute([$u['id'],$body]);return ['ok'=>true];});
