<?php
// Chats (20) green dots: which of these people (comma-separated ids, up to 100) are online now.
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');current_user();
$ids=array_slice(array_values(array_filter(explode(',',(string)($_GET['ids']??'')),fn($x)=>preg_match('/^[0-9a-f-]{36}$/i',$x))),0,100);
if(!$ids)json_response(['online'=>[]]);$in=implode(',',array_fill(0,count($ids),'?'));
$q=db()->prepare("SELECT DISTINCT p.user_id FROM presence_sessions p JOIN sessions s ON s.id=p.session_id WHERE p.user_id IN ($in) AND p.expires_at>UTC_TIMESTAMP(6) AND s.revoked_at IS NULL AND s.expires_at>UTC_TIMESTAMP(6)");$q->execute($ids);
json_response(['online'=>array_column($q->fetchAll(),'user_id')]);
