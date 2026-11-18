<?php
// Call log: my calls (in and out), newest first.
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();
$q=db()->prepare('SELECT c.*,IF(c.caller_id=?,c.callee_id,c.caller_id) AS peer_id,u.display_name AS peer_name FROM calls c JOIN users u ON u.id=IF(c.caller_id=?,c.callee_id,c.caller_id) WHERE c.caller_id=? OR c.callee_id=? ORDER BY c.created_at DESC LIMIT 100');$q->execute([$u['id'],$u['id'],$u['id'],$u['id']]);json_response(['calls'=>$q->fetchAll()]);
