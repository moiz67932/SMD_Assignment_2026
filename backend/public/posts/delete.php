<?php
// Only the author can delete their post (soft delete: it disappears from every feed).
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();
mutate($u,$d,'post.delete',function($pdo)use($u,$d){$q=$pdo->prepare('UPDATE posts SET deleted_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=? AND author_id=? AND deleted_at IS NULL');$q->execute([$d['id']??'',$u['id']]);if(!$q->rowCount())throw new DomainException('Post unavailable');return ['ok'=>true];});
