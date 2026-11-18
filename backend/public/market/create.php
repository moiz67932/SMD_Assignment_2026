<?php
require_once dirname(__DIR__,2).'/src/actions.php';require_method('POST');$u=current_user();$d=request_json();$id=(string)($d['id']??'');$title=trim((string)($d['title']??''));$price=trim((string)($d['price']??''));
if(!valid_uuid($id)||$title===''||strlen($title)>240||$price===''||strlen($price)>30)json_response(['error'=>['code'=>'invalid_item','message'=>'Enter an item name and price']],400);
mutate($u,$d,'market.create',function($pdo)use($u,$id,$title,$price){$q=$pdo->prepare('INSERT INTO marketplace_items(id,seller_id,title,price,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))');$q->execute([$id,$u['id'],$title,$price]);return ['item'=>['id'=>$id]];});
