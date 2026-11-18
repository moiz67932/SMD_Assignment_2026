<?php
declare(strict_types=1);
require_once dirname(__DIR__, 2) . '/src/bootstrap.php';
require_method('POST'); $data=request_json();
$email=strtolower(trim((string)($data['email']??''))); $password=(string)($data['password']??''); $name=trim((string)($data['name']??''));
if (!filter_var($email,FILTER_VALIDATE_EMAIL) || strlen($password)<8 || strlen($password)>200 || $name==='') json_response(['error'=>['code'=>'invalid_input','message'=>'Provide a valid email, a password of at least 8 characters, and a name']],400);
$id=uuid_v4();
if (strlen($name)>100) json_response(['error'=>['code'=>'invalid_name','message'=>'Name must fit within 100 bytes']],400);
try { $q=db()->prepare('INSERT INTO users(id,email,email_normalized,password_hash,display_name) VALUES(?,?,?,?,?)'); $q->execute([$id,$email,$email,password_hash($password,PASSWORD_DEFAULT),$name]); }
catch(PDOException $e) { if ((string)$e->getCode()==='23000') json_response(['error'=>['code'=>'email_taken','message'=>'An account already uses that email']],409); throw $e; }
json_response(['user'=>['id'=>$id,'email'=>$email,'display_name'=>$name,'profile_setup_complete'=>false], ...issue_session($id)],201);
