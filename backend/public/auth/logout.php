<?php
declare(strict_types=1);
require_once dirname(__DIR__, 2) . '/src/bootstrap.php';
require_method('POST'); $user=current_user(); $token=substr($_SERVER['HTTP_AUTHORIZATION'],7);
$pdo=db();$pdo->beginTransaction();
$q=$pdo->prepare('UPDATE presence_sessions p JOIN sessions s ON s.id=p.session_id SET p.expires_at=UTC_TIMESTAMP(6) WHERE s.token_hash=?');$q->execute([hash('sha256',$token)]);
$q=$pdo->prepare('UPDATE sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE token_hash=? AND revoked_at IS NULL'); $q->execute([hash('sha256',$token)]);
$pdo->commit();json_response(['ok'=>true]);
