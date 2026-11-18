<?php
// Menu > Settings > Change password: needs the current password; the new one is hashed with password_hash.
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('POST');$u=current_user();$d=request_json();
$current=(string)($d['current_password']??'');$new=(string)($d['new_password']??'');
if(strlen($new)<8||strlen($new)>200)json_response(['error'=>['code'=>'weak_password','message'=>'Use at least 8 characters']],400);
$q=db()->prepare('SELECT password_hash FROM users WHERE id=?');$q->execute([$u['id']]);
if(!password_verify($current,(string)$q->fetchColumn()))json_response(['error'=>['code'=>'invalid_credentials','message'=>'Your current password is not correct']],401);
$q=db()->prepare('UPDATE users SET password_hash=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?');$q->execute([password_hash($new,PASSWORD_DEFAULT),$u['id']]);json_response(['ok'=>true]);
