<?php
// Shared post output for feed.php and get.php: author, media, reaction summary, my reaction, comment count, details.
require_once __DIR__.'/visibility.php';
function decorate_posts(array $posts,string $viewer):array {
 foreach($posts as &$post){
  $m=db()->prepare('SELECT m.id,m.mime_type,m.byte_size,pm.position FROM post_media pm JOIN media m ON m.id=pm.media_id WHERE pm.post_id=? ORDER BY pm.position');$m->execute([$post['id']]);$post['media']=$m->fetchAll();
  $a=db()->prepare('SELECT display_name,photo_media_id FROM users WHERE id=?');$a->execute([$post['author_id']]);$author=$a->fetch()?:[];
  $post['author_name']=$author['display_name']??'';$post['author_photo_media_id']=$author['photo_media_id']??null;
  $r=db()->prepare('SELECT reaction,COUNT(*) AS n FROM post_reactions WHERE post_id=? GROUP BY reaction ORDER BY n DESC');$r->execute([$post['id']]);$post['reactions']=$r->fetchAll();
  $r=db()->prepare('SELECT reaction FROM post_reactions WHERE post_id=? AND user_id=?');$r->execute([$post['id'],$viewer]);$post['my_reaction']=$r->fetchColumn()?:null;
  $c=db()->prepare('SELECT COUNT(*) FROM comments WHERE post_id=? AND deleted_at IS NULL');$c->execute([$post['id']]);$post['comment_count']=(int)$c->fetchColumn();
  $post['details']=$post['details']?json_decode($post['details'],true):null;
 }
 unset($post);return $posts;
}
