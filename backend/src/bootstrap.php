<?php
declare(strict_types=1);
set_exception_handler(function(Throwable $error): void {
    $domain = $error instanceof DomainException;
    if (!$domain) error_log(get_class($error) . ': ' . $error->getMessage());
    json_response(['error'=>['code'=>$domain?'operation_denied':'server_error','message'=>$domain?$error->getMessage():'Unable to complete request']],$domain?403:500);
});

function env_value(string $key, ?string $default = null): ?string {
    $value = getenv($key);
    return $value === false ? $default : $value;
}
function db(): PDO {
    static $pdo = null;
    if ($pdo instanceof PDO) return $pdo;
    $dsn = sprintf('mysql:host=%s;port=%s;dbname=%s;charset=utf8mb4', env_value('DB_HOST', '127.0.0.1'), env_value('DB_PORT', '3306'), env_value('DB_NAME', 'kinnect'));
    $pdo = new PDO($dsn, env_value('DB_USER', 'kinnect'), env_value('DB_PASSWORD', ''), [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_EMULATE_PREPARES => false,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    return $pdo;
}
function json_response(array $body, int $status = 200): never {
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($body, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}
function request_json(): array {
    $data = json_decode(file_get_contents('php://input') ?: '', true);
    if (!is_array($data)) json_response(['error' => ['code' => 'invalid_json', 'message' => 'Expected a JSON object']], 400);
    return $data;
}
function require_method(string $method): void {
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== $method) {
        header('Allow: ' . $method);
        json_response(['error' => ['code' => 'method_not_allowed', 'message' => 'Method not allowed']], 405);
    }
}
function current_user(): array {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/^Bearer ([A-Za-z0-9_-]{40,})$/', $header, $m)) {
        json_response(['error' => ['code' => 'unauthorized', 'message' => 'Authentication required']], 401);
    }
    $q = db()->prepare('SELECT u.id, u.email, u.display_name, u.username, u.bio, u.profile_setup_complete FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at > UTC_TIMESTAMP() LIMIT 1');
    $q->execute([hash('sha256', $m[1])]);
    $user = $q->fetch();
    if (!$user) json_response(['error' => ['code' => 'unauthorized', 'message' => 'Session expired']], 401);
    return $user;
}
function uuid_v4(): string {
    $b = random_bytes(16); $b[6] = chr((ord($b[6]) & 0x0f) | 0x40); $b[8] = chr((ord($b[8]) & 0x3f) | 0x80);
    $h = bin2hex($b); return substr($h,0,8).'-'.substr($h,8,4).'-'.substr($h,12,4).'-'.substr($h,16,4).'-'.substr($h,20);
}
function issue_session(string $userId): array {
    $token = rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
    $q = db()->prepare('INSERT INTO sessions(id,user_id,token_hash,issued_at,expires_at) VALUES(?,?,?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 DAY))');
    $q->execute([uuid_v4(), $userId, hash('sha256', $token)]);
    return ['token' => $token, 'expires_in' => 2592000];
}
