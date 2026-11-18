<?php
require_once dirname(__DIR__,2).'/src/media.php';require_method('POST');$u=current_user();$id=(string)($_POST['id']??'');$action=(string)($_POST['action_uuid']??'');
if(!isset($_FILES['file'])||!valid_uuid($id)||!valid_uuid($action))json_response(['error'=>['code'=>'invalid_upload','message'=>'Provide file, id and action_uuid']],400);
if(!is_array($_FILES['file']) || is_array($_FILES['file']['tmp_name']??null))json_response(['error'=>['code'=>'invalid_upload','message'=>'Expected one uploaded file']],400);
$uploadError=$_FILES['file']['error']??UPLOAD_ERR_NO_FILE;
if($uploadError!==UPLOAD_ERR_OK){
 $tooLarge=in_array($uploadError,[UPLOAD_ERR_INI_SIZE,UPLOAD_ERR_FORM_SIZE],true);
 json_response(['error'=>['code'=>$tooLarge?'upload_too_large':'upload_failed','message'=>$tooLarge?'Attachment exceeds server upload limits':'Attachment upload failed; select the file and retry']],$tooLarge?413:400);
}
if(!is_string($_FILES['file']['tmp_name']??null)||!is_uploaded_file($_FILES['file']['tmp_name']))json_response(['error'=>['code'=>'invalid_upload','message'=>'Uploaded file unavailable']],400);
// Fingerprint the actual bytes as part of the replay contract.
$d=['action_uuid'=>$action,'id'=>$id,'sha256'=>hash_file('sha256',$_FILES['file']['tmp_name'])];
mutate($u,$d,'media.upload',function($pdo)use($id,$u){return ['media'=>upload_part($_FILES['file'],$id,$u['id'])];});
