<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();$q=db()->prepare('SELECT * FROM notifications WHERE recipient_id=? ORDER BY created_at DESC,id DESC LIMIT 100');$q->execute([$u['id']]);json_response(['notifications'=>$q->fetchAll()]);
