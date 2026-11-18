<?php
declare(strict_types=1);
require_once dirname(__DIR__, 2) . '/src/bootstrap.php';
require_method('POST'); $data=request_json(); $email=strtolower(trim((string)($data['email']??''))); $password=(string)($data['password']??'');
$q=db()->prepare('SELECT id,email,display_name,username,bio,profile_setup_complete,password_hash FROM users WHERE email_normalized=? LIMIT 1'); $q->execute([$email]); $u=$q->fetch();
if (!$u || !password_verify($password,$u['password_hash'])) json_response(['error'=>['code'=>'invalid_credentials','message'=>'Email or password is incorrect']],401);
unset($u['password_hash']); json_response(['user'=>$u, ...issue_session($u['id'])]);
