<?php
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('POST');$u=current_user();$d=request_json();$g=(string)($d['group_id']??'');$id=(string)($d['id']??'');$body=trim((string)($d['body']??''));
if(!valid_uuid($id)||$body===''||strlen($body)>3000)json_response(['error'=>['code'=>'invalid_message','message'=>'Write a message (up to 1000 characters)']],400);
if(!group_member($g,$u['id']))json_response(['error'=>['code'=>'forbidden','message'=>'Group unavailable']],403);
mutate($u,$d,'group.send',function($pdo)use($u,$g,$id,$body){$q=$pdo->prepare('INSERT INTO group_messages(id,group_id,sender_id,body,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([$id,$g,$u['id'],$body]);return ['message'=>['id'=>$id]];});
