<?php
require_once __DIR__.'/visibility.php';
function owned_media(string $id,string $actor,?string $prefix=null): array {
 $q=db()->prepare('SELECT * FROM media WHERE id=? AND owner_id=?');$q->execute([$id,$actor]);$m=$q->fetch();
 if(!$m||($prefix!==null&&!str_starts_with($m['mime_type'],$prefix)))throw new DomainException('Media unavailable or invalid for this resource');return $m;
}
function media_root():string {$root=env_value('MEDIA_ROOT',dirname(__DIR__).'/var/media');if(!is_dir($root)&&!mkdir($root,0700,true)&&!is_dir($root))throw new RuntimeException('Media directory unavailable');return $root;}
function upload_part(array $file,string $id,string $actor):array {
 if(!valid_uuid($id)||($file['error']??-1)!==UPLOAD_ERR_OK||!is_uploaded_file($file['tmp_name']))throw new DomainException('Invalid upload');
 $size=filesize($file['tmp_name']);if($size<1||$size>25*1024*1024)throw new DomainException('Upload must be between 1 byte and 25 MB');
 $mime=(new finfo(FILEINFO_MIME_TYPE))->file($file['tmp_name']);
 $types=['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp','image/gif'=>'gif','video/mp4'=>'mp4','video/webm'=>'webm','audio/mp4'=>'m4a','audio/mpeg'=>'mp3','audio/ogg'=>'ogg','application/pdf'=>'pdf','text/plain'=>'txt','application/zip'=>'zip'];
 if(!isset($types[$mime]))throw new DomainException('Unsupported file type');
 $hash=hash_file('sha256',$file['tmp_name']);$q=db()->prepare('SELECT * FROM media WHERE id=?');$q->execute([$id]);$old=$q->fetch();
 if($old){if($old['owner_id']!==$actor||!hash_equals($old['sha256'],$hash))throw new DomainException('Upload UUID conflict');return $old;}
 $path=$id.'.'.$types[$mime];$target=media_root().'/'.$path;
 if(!move_uploaded_file($file['tmp_name'],$target))throw new RuntimeException('Unable to persist uploaded file');chmod($target,0600);
 $name=substr(basename(str_replace('\\','/',(string)$file['name'])),0,255);
 $q=db()->prepare('INSERT INTO media(id,owner_id,storage_path,mime_type,original_name,byte_size,sha256,created_at) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([$id,$actor,$path,$mime,$name,$size,$hash]);
 return ['id'=>$id,'mime_type'=>$mime,'original_name'=>$name,'byte_size'=>$size,'sha256'=>$hash];
}
function can_view_story(array $s,string $actor):bool {
 if($s['deleted_at']!==null||strtotime($s['expires_at'].' UTC')<=time())return false;
 if($s['owner_id']===$actor)return true;
 if($s['audience']==='close_friends'){$q=db()->prepare('SELECT 1 FROM close_friends WHERE owner_id=? AND member_id=?');$q->execute([$s['owner_id'],$actor]);}
 else {$pair=[$s['owner_id'],$actor];sort($pair);$q=db()->prepare('SELECT 1 FROM friendships WHERE user_low=? AND user_high=?');$q->execute($pair);}
 return (bool)$q->fetchColumn();
}
