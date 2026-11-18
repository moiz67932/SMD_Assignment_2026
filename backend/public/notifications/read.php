<?php
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();mutate($u,$d,'notification.read',function($pdo)use($u,$d){$q=$pdo->prepare('UPDATE notifications SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6)) WHERE id=? AND recipient_id=?');$q->execute([$d['id']??'',$u['id']]);return ['ok'=>true];});
