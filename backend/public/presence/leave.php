<?php
// The app went to the background: end this session's presence now instead of waiting for the 45 s heartbeat timeout.
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('POST');current_user();$hash=hash('sha256',substr($_SERVER['HTTP_AUTHORIZATION'],7));
$q=db()->prepare('UPDATE presence_sessions p JOIN sessions s ON s.id=p.session_id SET p.expires_at=UTC_TIMESTAMP(6) WHERE s.token_hash=?');$q->execute([$hash]);json_response(['ok'=>true]);
