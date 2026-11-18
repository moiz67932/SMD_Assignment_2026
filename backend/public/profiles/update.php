<?php
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();
$name=trim((string)($d['name']??''));$username=trim((string)($d['username']??''));$bio=trim((string)($d['bio']??''));
if($name===''||strlen($name)>100||!preg_match('/^[a-zA-Z0-9_]{3,30}$/',$username)||$bio===''||strlen($bio)>500)json_response(['error'=>['code'=>'invalid_profile','message'=>'Provide a name, a 3–30 character username (letters, numbers, underscore), and bio']],400);
try { mutate($u,$d,'profile.update',function($pdo)use($u,$name,$username,$bio){$q=$pdo->prepare('UPDATE users SET display_name=?,username=?,username_normalized=?,bio=?,profile_setup_complete=1,updated_at=UTC_TIMESTAMP(6) WHERE id=?');$q->execute([$name,$username,strtolower($username),$bio,$u['id']]);return ['user'=>['id'=>$u['id'],'display_name'=>$name,'username'=>$username,'bio'=>$bio,'profile_setup_complete'=>true]];}); }
catch(PDOException $e){if($e->getCode()==='23000')json_response(['error'=>['code'=>'username_taken','message'=>'Username already taken']],409);throw $e;}
