<?php
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();mutate($u,$d,'device.unregister',function($pdo)use($u,$d){$q=$pdo->prepare('UPDATE devices SET enabled=0 WHERE user_id=? AND device_key=?');$q->execute([$u['id'],$d['device_key']??'']);return ['ok'=>true];});
