<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('POST');$u=current_user();
$q=db()->prepare('DELETE FROM live_viewers WHERE host_id=?');$q->execute([$u['id']]);$q=db()->prepare('DELETE FROM lives WHERE host_id=?');$q->execute([$u['id']]);json_response(['ok'=>true]);
