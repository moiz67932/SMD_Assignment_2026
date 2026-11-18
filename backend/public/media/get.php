<?php
require_once dirname(__DIR__,2).'/src/media.php';require_method('GET');$u=current_user();$id=(string)($_GET['id']??'');$q=db()->prepare('SELECT * FROM media WHERE id=?');$q->execute([$id]);$m=$q->fetch();$allowed=false;
if($m){
 // Unattached media is private to its owner; published story media must still respect expiry.
 $q=db()->prepare('SELECT * FROM stories WHERE media_id=?');$q->execute([$id]);$stories=$q->fetchAll();
 foreach($stories as $s)if(can_view_story($s,$u['id']))$allowed=true;
 $q=db()->prepare('SELECT post_id FROM post_media WHERE media_id=?');$q->execute([$id]);$posts=$q->fetchAll();foreach($posts as $p)if(can_view_post($p['post_id'],$u['id']))$allowed=true;
 $q=db()->prepare('SELECT 1 FROM users WHERE photo_media_id=? OR cover_media_id=? LIMIT 1');$q->execute([$id,$id]);$profile=(bool)$q->fetchColumn();if($profile)$allowed=true;
 $q=db()->prepare('SELECT c.post_id FROM comments c WHERE c.media_id=? AND c.deleted_at IS NULL');$q->execute([$id]);$comments=$q->fetchAll();foreach($comments as $c)if(can_view_post($c['post_id'],$u['id']))$allowed=true;
 $q=db()->prepare('SELECT m.chat_id FROM message_attachments a JOIN messages m ON m.id=a.message_id WHERE a.media_id=? AND m.deleted_at IS NULL');$q->execute([$id]);$attachments=$q->fetchAll();foreach($attachments as $a)if(chat_member($a['chat_id'],$u['id']))$allowed=true;
 if(!$attachments&&!$stories&&!$posts&&!$comments&&!$profile&&$m['owner_id']===$u['id'])$allowed=true;
}
if(!$allowed)json_response(['error'=>['code'=>'not_found','message'=>'Media unavailable']],404);
$path=media_root().'/'.basename($m['storage_path']);if(!is_file($path))json_response(['error'=>['code'=>'not_found','message'=>'Media unavailable']],404);
header('Cache-Control: private, no-store');header('X-Content-Type-Options: nosniff');header('Content-Type: '.$m['mime_type']);header('Content-Disposition: inline; filename="'.basename($m['storage_path']).'"');header('Accept-Ranges: bytes');
$size=filesize($path);$start=0;$end=$size-1;
if(isset($_SERVER['HTTP_RANGE'])){if(!preg_match('/^bytes=(\d*)-(\d*)$/',$_SERVER['HTTP_RANGE'],$r)||($r[1]===''&&$r[2]==='')){http_response_code(416);header("Content-Range: bytes */$size");exit;}
 if($r[1]==='')$start=max(0,$size-(int)$r[2]);else{$start=(int)$r[1];if($r[2]!=='')$end=min($end,(int)$r[2]);}
 if($start>$end||$start>=$size){http_response_code(416);header("Content-Range: bytes */$size");exit;}http_response_code(206);header("Content-Range: bytes $start-$end/$size");}
header('Content-Length: '.($end-$start+1));$f=fopen($path,'rb');fseek($f,$start);$remaining=$end-$start+1;while($remaining>0&&!feof($f)){$chunk=fread($f,min(65536,$remaining));echo $chunk;$remaining-=strlen($chunk);}fclose($f);
