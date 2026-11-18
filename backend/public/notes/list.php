<?php
// My note and my friends' notes that have not expired.
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('GET');$u=current_user();
$q=db()->prepare('SELECT n.user_id,n.body,n.created_at,u.display_name FROM notes n JOIN users u ON u.id=n.user_id WHERE n.expires_at>UTC_TIMESTAMP(6) AND (n.user_id=? OR EXISTS(SELECT 1 FROM friendships f WHERE (f.user_low=n.user_id AND f.user_high=?) OR (f.user_high=n.user_id AND f.user_low=?))) ORDER BY n.created_at DESC');$q->execute([$u['id'],$u['id'],$u['id']]);json_response(['notes'=>$q->fetchAll()]);
