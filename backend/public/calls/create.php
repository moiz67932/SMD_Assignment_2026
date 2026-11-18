<?php
// Start a call (Chat 21 phone / video icon): log it as ringing and push "incoming call" to the other person's phones.
require_once dirname(__DIR__,2).'/src/actions.php';require_once dirname(__DIR__,2).'/src/notifications.php';require_method('POST');$u=current_user();$d=request_json();
$id=(string)($d['id']??'');$callee=(string)($d['callee_id']??'');$video=!empty($d['video']);
if(!valid_uuid($id)||!valid_uuid($callee)||$callee===$u['id'])json_response(['error'=>['code'=>'invalid_call','message'=>'Invalid call']],400);
mutate($u,$d,'call.create',function($pdo)use($u,$id,$callee,$video){
 $q=$pdo->prepare("INSERT INTO calls(id,caller_id,callee_id,video,status,created_at) VALUES(?,?,?,?,'ringing',UTC_TIMESTAMP(6))");$q->execute([$id,$u['id'],$callee,$video?1:0]);
 notify_user($pdo,$callee,$u['id'],'call',$id,$video?'Incoming video call':'Incoming voice call',['callId'=>$id,'video'=>$video?'true':'false','title'=>$u['display_name']]);
 $q=$pdo->prepare('SELECT * FROM calls WHERE id=?');$q->execute([$id]);return ['call'=>$q->fetch()];});
