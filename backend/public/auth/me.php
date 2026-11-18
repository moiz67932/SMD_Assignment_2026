<?php
declare(strict_types=1);
require_once dirname(__DIR__, 2) . '/src/bootstrap.php';
require_method('GET'); json_response(['user'=>current_user()]);
