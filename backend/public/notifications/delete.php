<?php
// Notifications (18) "Remove this notification": only the recipient can delete it.
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();
mutate($u,$d,'notification.delete',function($pdo)use($u,$d){$id=(string)($d['id']??'');
 $q=$pdo->prepare('DELETE j FROM push_jobs j JOIN notifications n ON n.id=j.notification_id WHERE n.id=? AND n.recipient_id=?');$q->execute([$id,$u['id']]);
 $q=$pdo->prepare('DELETE FROM notifications WHERE id=? AND recipient_id=?');$q->execute([$id,$u['id']]);return ['ok'=>true];});
