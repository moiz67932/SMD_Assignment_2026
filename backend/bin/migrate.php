<?php
declare(strict_types=1);
require_once dirname(__DIR__) . '/src/bootstrap.php';
// MySQL DDL implicitly commits. Never pretend an entire schema file is transactional.
$pdo = db();
$pdo->exec('CREATE TABLE IF NOT EXISTS schema_migrations (name VARCHAR(190) PRIMARY KEY, applied_at DATETIME(6) NOT NULL) ENGINE=InnoDB');
$lock = $pdo->query("SELECT GET_LOCK('kinnect_schema_migrations', 10)")->fetchColumn();
if ((int)$lock !== 1) throw new RuntimeException('Another migration is running');
try {
    $files = glob(dirname(__DIR__) . '/migrations/*.sql'); sort($files);
    foreach ($files as $file) {
        $name = basename($file);
        $q = $pdo->prepare('SELECT 1 FROM schema_migrations WHERE name=?'); $q->execute([$name]);
        if ($q->fetchColumn()) continue;
        $pdo->exec(file_get_contents($file));
        $q = $pdo->prepare('INSERT INTO schema_migrations(name,applied_at) VALUES(?,UTC_TIMESTAMP(6))'); $q->execute([$name]);
        echo "applied $name\n";
    }
} finally { $pdo->query("SELECT RELEASE_LOCK('kinnect_schema_migrations')"); }
