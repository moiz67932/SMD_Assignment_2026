<?php
require_once dirname(__DIR__,2).'/src/bootstrap.php';require_method('GET');current_user();
$q=db()->prepare('SELECT i.*,u.display_name AS seller_name FROM marketplace_items i JOIN users u ON u.id=i.seller_id ORDER BY i.created_at DESC LIMIT 100');$q->execute();json_response(['items'=>$q->fetchAll()]);
