<?php
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('GET');$u=current_user();$user=(string)($_GET['user']??$u['id']);
if(!are_friends($user,$u['id']))json_response(['highlights'=>[]]);
$q=db()->prepare('SELECT h.*,m.mime_type FROM highlights h JOIN media m ON m.id=h.media_id WHERE h.user_id=? ORDER BY h.created_at DESC');$q->execute([$user]);json_response(['highlights'=>$q->fetchAll()]);
