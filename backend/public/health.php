<?php
declare(strict_types=1);
require_once dirname(__DIR__) . '/src/bootstrap.php';
try {
    db()->query('SELECT 1');
    json_response(['ok' => true, 'service' => 'kinnect-api', 'server_time' => gmdate(DATE_ATOM)]);
} catch (Throwable $e) {
    json_response(['ok' => false, 'error' => ['code' => 'unavailable', 'message' => 'Service unavailable']], 503);
}
