<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();
// Optional ?author=<id>: only that person's posts (Profile / Other profile). Same visibility rule: me or my friends.
$author=(string)($_GET['author']??'');$extra=$author!==''?' AND p.author_id=?':'';$args=[$u['id'],$u['id'],$u['id']];if($author!=='')$args[]=$author;
$q=db()->prepare('SELECT p.* FROM posts p WHERE p.deleted_at IS NULL AND (p.author_id=? OR EXISTS(SELECT 1 FROM friendships f WHERE (f.user_low=p.author_id AND f.user_high=?) OR (f.user_high=p.author_id AND f.user_low=?)))'.$extra.' ORDER BY p.created_at DESC,p.id DESC LIMIT 50');$q->execute($args);$posts=$q->fetchAll();foreach($posts as &$post){$m=db()->prepare('SELECT m.id,m.mime_type,m.byte_size,pm.position FROM post_media pm JOIN media m ON m.id=pm.media_id WHERE pm.post_id=? ORDER BY pm.position');$m->execute([$post['id']]);$post['media']=$m->fetchAll();}unset($post);json_response(['posts'=>$posts]);
