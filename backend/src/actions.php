<?php
declare(strict_types=1);
require_once __DIR__.'/bootstrap.php';
function valid_uuid(string $id): bool { return (bool)preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i',$id); }
function mutate(array $actor, array $data, string $operation, callable $callback): never {
    $id=(string)($data['action_uuid']??'');
    if(!valid_uuid($id)) json_response(['error'=>['code'=>'invalid_action','message'=>'A UUID action_uuid is required']],400);
    // Normalize associative JSON keys recursively before comparing retries.
    $normalize=function($value) use (&$normalize) { if(!is_array($value)) return $value; if(!array_is_list($value)) ksort($value); return array_map($normalize,$value); };
    $hash=hash('sha256',json_encode($normalize($data),JSON_THROW_ON_ERROR)); $pdo=db(); $pdo->beginTransaction();
    try {
        // Lock the actor to serialize concurrent mutations and receipt creation.
        $q=$pdo->prepare('SELECT id FROM users WHERE id=? FOR UPDATE');$q->execute([$actor['id']]);
        $q=$pdo->prepare('SELECT operation,request_hash,response_json FROM processed_actions WHERE actor_id=? AND action_uuid=?');$q->execute([$actor['id'],$id]);$old=$q->fetch();
        if($old){ $pdo->rollBack(); if($old['operation']!==$operation || !hash_equals($old['request_hash'],$hash)) json_response(['error'=>['code'=>'action_conflict','message'=>'Action UUID was already used for different input']],409); json_response(json_decode($old['response_json'],true)); }
        $result=$callback($pdo);
        $q=$pdo->prepare('INSERT INTO processed_actions(actor_id,action_uuid,operation,request_hash,response_json,committed_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([$actor['id'],$id,$operation,$hash,json_encode($result,JSON_THROW_ON_ERROR)]);
        $pdo->commit(); json_response($result);
    }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}
}
function chat_member(string $chatId,string $userId): bool {
    $q=db()->prepare('SELECT 1 FROM chats WHERE id=? AND (user_low=? OR user_high=?)');$q->execute([$chatId,$userId,$userId]);return (bool)$q->fetchColumn();
}
