<?php
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();$other=(string)($d['other_id']??'');$op=(string)($d['operation']??'');
if(!valid_uuid($other)||$other===$u['id']||!in_array($op,['send','accept','decline','cancel','remove'],true))json_response(['error'=>['code'=>'invalid_action','message'=>'Invalid friend operation']],400);
mutate($u,$d,'friend.'.$op,function($pdo)use($u,$other,$op){
 $pair=[$u['id'],$other];sort($pair);
 // Lock users in canonical order as the common relationship serialization point.
 $q=$pdo->prepare('SELECT id FROM users WHERE id IN (?,?) ORDER BY id FOR UPDATE');$q->execute($pair);if(count($q->fetchAll())!==2)throw new DomainException('User unavailable');
 if($op==='remove'){$q=$pdo->prepare('DELETE FROM friendships WHERE user_low=? AND user_high=?');$q->execute($pair);return ['state'=>'none'];}
 $q=$pdo->prepare('SELECT 1 FROM friendships WHERE user_low=? AND user_high=?');$q->execute($pair);if($q->fetchColumn())return ['state'=>'friends'];
 if($op==='send'){
  $q=$pdo->prepare("SELECT sender_id FROM friend_requests WHERE state='pending' AND ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?)) FOR UPDATE");$q->execute([$u['id'],$other,$other,$u['id']]);$existing=$q->fetch();
  if($existing)return ['state'=>$existing['sender_id']===$u['id']?'requested':'incoming'];
  $q=$pdo->prepare("INSERT INTO friend_requests(id,sender_id,receiver_id,state,created_at,updated_at) VALUES(?,?,?,'pending',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");$q->execute([uuid_v4(),$u['id'],$other]);return ['state'=>'requested'];
 }
 $sender=$op==='cancel'?$u['id']:$other;$receiver=$op==='cancel'?$other:$u['id'];
 $q=$pdo->prepare("SELECT id FROM friend_requests WHERE sender_id=? AND receiver_id=? AND state='pending' FOR UPDATE");$q->execute([$sender,$receiver]);$r=$q->fetch();if(!$r)throw new DomainException('Pending request unavailable');
 // History remains in action receipts; remove resolved requests so future requests are possible.
 $q=$pdo->prepare('DELETE FROM friend_requests WHERE id=?');$q->execute([$r['id']]);
 if($op==='accept'){$q=$pdo->prepare('INSERT INTO friendships(user_low,user_high,created_at) VALUES(?,?,UTC_TIMESTAMP(6))');$q->execute($pair);return ['state'=>'friends'];}
 return ['state'=>'none'];
});
