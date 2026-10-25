# Kinnect code guide

How the code is organised and how each feature works. The full feature-by-feature guide is filled in as features are finished.

## Schema changes log

Every change to the Realtime Database layout, with the rule that protects it (see `database.rules.json`).

| Path | Change | Rule |
|---|---|---|
| `profiles/{uid}` | added `photo`, `cover`, `website`, `firstNameLower`, `lastNameLower` (written by Edit profile) | owner writes, any signed-in user reads |
| `users/{uid}` | added `photo`, `cover`, `website`, `phone` | owner only |
| `sentRequests/{sender}/{receiver} = timestamp` | new: outgoing requests, so "Requested" shows live without reading other people's request lists | sender writes; receiver may only delete; sender reads |
| `friends/{a}/{b}` | tightened: can only be **created** while a matching `friendRequests` entry exists (i.e. by Confirm); **either** side can delete both entries (Unfriend) | see rule text |
