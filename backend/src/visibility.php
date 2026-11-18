<?php
require_once __DIR__.'/actions.php';
function can_view_post(string $id,string $viewer): bool {
 $q=db()->prepare('SELECT 1 FROM posts p WHERE p.id=? AND p.deleted_at IS NULL AND (p.author_id=? OR EXISTS(SELECT 1 FROM friendships f WHERE (f.user_low=p.author_id AND f.user_high=?) OR (f.user_high=p.author_id AND f.user_low=?)))');$q->execute([$id,$viewer,$viewer,$viewer]);return (bool)$q->fetchColumn();
}

function are_friends(string $a,string $b):bool { if($a===$b)return true; $pair=[$a,$b];sort($pair);$q=db()->prepare('SELECT 1 FROM friendships WHERE user_low=? AND user_high=?');$q->execute($pair);return (bool)$q->fetchColumn(); }
function group_member(string $group,string $user):bool { $q=db()->prepare('SELECT 1 FROM group_members WHERE group_id=? AND user_id=?');$q->execute([$group,$user]);return (bool)$q->fetchColumn(); }
