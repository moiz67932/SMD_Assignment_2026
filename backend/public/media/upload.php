<?php
require_once dirname(__DIR__,2).'/src/media.php';require_method('POST');$u=current_user();$id=(string)($_POST['id']??'');$action=(string)($_POST['action_uuid']??'');
if(!isset($_FILES['file'])||!valid_uuid($id)||!valid_uuid($action))json_response(['error'=>['code'=>'invalid_upload','message'=>'Provide file, id and action_uuid']],400);
// Fingerprint the actual bytes as part of the replay contract.
$d=['action_uuid'=>$action,'id'=>$id,'sha256'=>hash_file('sha256',$_FILES['file']['tmp_name'])];
mutate($u,$d,'media.upload',function($pdo)use($id,$u){return ['media'=>upload_part($_FILES['file'],$id,$u['id'])];});
