<?php
// Calls ringing for me right now (started less than 30 s ago). Polled while the app is open; FCM wakes it when closed.
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();
$q=db()->prepare("SELECT c.*,u.display_name AS caller_name FROM calls c JOIN users u ON u.id=c.caller_id WHERE c.callee_id=? AND c.status='ringing' AND c.created_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 30 SECOND) ORDER BY c.created_at DESC");$q->execute([$u['id']]);json_response(['calls'=>$q->fetchAll()]);
