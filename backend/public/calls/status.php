<?php
// accepted / declined / cancelled / missed / ended. Ending stores the duration (call log).
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();$id=(string)($d['id']??'');$status=(string)($d['status']??'');
if(!in_array($status,['accepted','declined','cancelled','missed','ended'],true))json_response(['error'=>['code'=>'invalid_status','message'=>'Invalid call status']],400);
mutate($u,$d,'call.status',function($pdo)use($u,$id,$status){
 $q=$pdo->prepare('SELECT * FROM calls WHERE id=? AND (caller_id=? OR callee_id=?) FOR UPDATE');$q->execute([$id,$u['id'],$u['id']]);$c=$q->fetch();if(!$c)throw new DomainException('Call unavailable');
 if(in_array($c['status'],['declined','cancelled','missed','ended'],true))return ['call'=>$c]; // already finished: keep the first outcome
 if($status==='accepted'){$q=$pdo->prepare("UPDATE calls SET status='accepted',answered_at=UTC_TIMESTAMP(6) WHERE id=?");$q->execute([$id]);}
 elseif($status==='ended'){$q=$pdo->prepare("UPDATE calls SET status=IF(answered_at IS NULL,'missed','ended'),ended_at=UTC_TIMESTAMP(6),duration_seconds=IF(answered_at IS NULL,0,TIMESTAMPDIFF(SECOND,answered_at,UTC_TIMESTAMP(6))) WHERE id=?");$q->execute([$id]);}
 else{$q=$pdo->prepare('UPDATE calls SET status=?,ended_at=UTC_TIMESTAMP(6) WHERE id=?');$q->execute([$status,$id]);}
 $q=$pdo->prepare('SELECT * FROM calls WHERE id=?');$q->execute([$id]);return ['call'=>$q->fetch()];});
