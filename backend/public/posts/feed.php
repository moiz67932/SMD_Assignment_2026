<?php
require_once dirname(__DIR__,2).'/src/posts.php';require_method('GET');$u=current_user();
// Optional ?author=<id>: only that person's posts (Profile / Other profile). Same visibility rule: me or my friends.
$author=(string)($_GET['author']??'');$extra=$author!==''?' AND p.author_id=?':'';$args=[$u['id'],$u['id'],$u['id']];if($author!=='')$args[]=$author;
$q=db()->prepare('SELECT p.* FROM posts p WHERE p.deleted_at IS NULL AND (p.author_id=? OR EXISTS(SELECT 1 FROM friendships f WHERE (f.user_low=p.author_id AND f.user_high=?) OR (f.user_high=p.author_id AND f.user_low=?)))'.$extra.' ORDER BY p.created_at DESC,p.id DESC LIMIT 50');$q->execute($args);$posts=decorate_posts($q->fetchAll(),$u['id']);json_response(['posts'=>$posts]);
