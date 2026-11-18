<?php
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('POST');$u=current_user();$d=request_json();$id=(string)($d['id']??'');$name=trim((string)($d['name']??''));$members=$d['members']??[];
if(!valid_uuid($id)||$name===''||strlen($name)>120||!is_array($members)||count($members)<1||count($members)>50)json_response(['error'=>['code'=>'invalid_group','message'=>'Give the group a name and at least one friend']],400);
mutate($u,$d,'group.create',function($pdo)use($u,$id,$name,$members){$q=$pdo->prepare('INSERT INTO groups_(id,name,created_by,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))');$q->execute([$id,$name,$u['id']]);
 foreach(array_unique(array_merge([$u['id']],$members)) as $m){if(!is_string($m)||!are_friends($m,$u['id']))throw new DomainException('Only friends can be added');$q=$pdo->prepare('INSERT IGNORE INTO group_members(group_id,user_id) VALUES(?,?)');$q->execute([$id,$m]);}
 return ['group'=>['id'=>$id,'name'=>$name]];});
