<?php
// Keep one of my stories on my profile after 24 hours (its media stays viewable by my friends).
require_once dirname(__DIR__,2).'/src/visibility.php';require_method('POST');$u=current_user();$d=request_json();$story=(string)($d['story_id']??'');
mutate($u,$d,'highlight.add',function($pdo)use($u,$story){$q=$pdo->prepare('SELECT * FROM stories WHERE id=? AND owner_id=?');$q->execute([$story,$u['id']]);$s=$q->fetch();if(!$s)throw new DomainException('Story unavailable');
 $q=$pdo->prepare('INSERT IGNORE INTO highlights(user_id,story_id,media_id,label,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([$u['id'],$story,$s['media_id'],$s['label']]);return ['ok'=>true];});
