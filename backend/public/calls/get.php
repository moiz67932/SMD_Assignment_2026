<?php
// One call (the caller waits for accepted / declined; both see it end).
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');$u=current_user();
$q=db()->prepare('SELECT c.*,u.display_name AS caller_name FROM calls c JOIN users u ON u.id=c.caller_id WHERE c.id=? AND (c.caller_id=? OR c.callee_id=?)');$q->execute([$_GET['id']??'',$u['id'],$u['id']]);$c=$q->fetch();
if(!$c)json_response(['error'=>['code'=>'not_found','message'=>'Call unavailable']],404);json_response(['call'=>$c]);
