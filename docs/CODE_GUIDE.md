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
| `posts/{postId}` | added `images` (ordered list of Cloudinary URLs), `imageCount`, `background` (colour of a text-only post); stopped writing the always-zero `reactionCount` / `commentCount` (counts come from `postReactions` / `comments`) | author writes, any signed-in user reads (unchanged) |
| `postReactions/{postId}/{uid}` | value must be one of Like, Love, Laugh, Wow, Sad, Angry (one reaction per user per post) | user writes only their own child |
| `notifications/{uid}/{id}` | new types `reaction` (with `postId`) for reactions on your post | sender creates with its own `senderId` (unchanged) |

### Why any signed-in user can read `posts`
The Home feed asks for each author's posts with `posts.orderByChild("authorId").equalTo(uid)` (index: `.indexOn: ["createdAt", "authorId"]`).
Realtime Database rules are not filters: a query on `posts` is only allowed if the whole `posts` node is readable, and a rule
on `posts/{postId}` cannot hide single posts from that query. So `posts` stays readable by every signed-in user, and the app
decides what to show (me + friends) on the client. Making posts friends-only would need a fan-out copy per friend
(for example `feeds/{uid}/{postId}`) written by a server, which this project does not have.
| `comments/{postId}/{commentId}` | added `imageRef` (photo comment); `parentCommentId` now always points at the **top-level** comment of the thread, `parentName` is the person replied to | author creates/edits/deletes own comment (unchanged) |
| `notifications/{uid}/{id}` | new types `comment` and `reply` (with `postId`) | sender creates (unchanged) |
| `stories/{uid}/public/{storyId}` and `stories/{uid}/closeFriends/{storyId}` | new: `mediaRef` (https URL), `type` image/video, `label` (≤ 80 chars), `audience`, `createdAt`, `expiresAt` (= server-adjusted now + 24 h) | owner writes; `public` readable by the owner's friends, `closeFriends` only by people in `closeFriends/{uid}` |
| `storyViews/{owner}/{storyId}/{viewer} = time` | new: who saw a story ("Seen by N") | viewer writes own entry (not the owner); owner reads all and may delete a whole story's views; viewer can read own entry (seen ring) |
| `closeFriends/{uid}/{friendUid} = true` | now used: chosen on the Close friends screen | owner reads/writes |
| `storyReactions/{owner}/{storyId}/{viewer}` | new: Like / Love / Laugh sent from the Story viewer (hearts on Your story) | viewer writes own entry; owner reads and may delete a whole story's reactions |
| `chats/{chatId}/lastMessage` | added `messageId` (so an edit/delete can tell whether it touched the last message) | members only (unchanged) |
| `chats/{chatId}/messages/{id}` | message `type` can be `text`, `image` (`imageRef`), `post` (`sharedPostId`, `sharedAuthorName`, `sharedSnippet`, `sharedImage`) or `system` | members only; sender creates; edit/delete by sender within 5 minutes (unchanged) |
| `chats/{chatId}/lastRead/{uid} = time` | new: when each member last had the chat open (drives the small "seen" avatar) | member writes own value; members read |
| `chats/{chatId}/typing/{uid} = true` | now used: set while typing, removed after 4 s idle / on stop / on disconnect | member writes own value; members read (unchanged) |
| `messageReactions/{chatId}` | read permission moved from each message to the whole chat (one listener for all hearts) | members read; each user writes only their own heart |
| messages | added `replyToSenderId` ("You replied to Aisha") | unchanged |
| `profiles/{uid}` | Sign up now also writes `firstNameLower` / `lastNameLower`; all four search fields are indexed (`.indexOn`) | owner writes, signed-in users read (unchanged) |
| `pushQueue/{pushId}` | new: `toUid`, `fromUid`, `type` (message / screenshot / call), `title`, `body`, `chatId`, optional `callId` / `video`, `createdAt`. Written by the app, read and deleted by `fcm-server` (Admin SDK) | signed-in users may only **create** items with their own `fromUid`; nobody can read through the client SDK |
| `callRequests/{callee}/{callId}` | `status` now one of ringing / accepted / declined / missed / cancelled / ended (validated); the Agora channel is the `callId` | caller creates; caller or callee update; callee reads all own requests; caller reads own call |
| `callLogs/{callId}` | `status`, `durationSeconds`, `endedAt` updated when a call finishes | caller or callee (unchanged) |
| messages | new `type: "call"` with `callId` ("Voice call · 2:31", "Missed video call"), written once by the caller | sender = caller (unchanged rules) |
