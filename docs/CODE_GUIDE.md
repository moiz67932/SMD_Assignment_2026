# Kinnect code guide

How the app is built, feature by feature, written so either of us can explain and change any part in the viva.
Every screen is one `AppCompatActivity` (no Fragments) with an XML layout, `findViewById`, and RecyclerView adapters.
All Firebase access goes straight from the screens through the singleton `FirebaseData`.

## 1. Shared building blocks (read these first)

| File | What it does | Why it exists |
|---|---|---|
| `FirebaseData.kt` | References to the database roots, plus `now()` = phone time + server offset | One place for paths; `now()` keeps the 24 h stories and 5 min edits correct even if a phone clock is wrong |
| `KinnectApplication.kt` | Edge-to-edge insets for every screen, presence (online/offline), the global incoming-call listener | Things that must happen for every Activity |
| `ListenerBag.kt` | Collects Firebase listeners and removes them all at once | Convention: attach in `onStart`, `removeAll()` in `onStop`, so nothing leaks |
| `MediaUploader.kt` / `MediaLoader.kt` | Upload a photo/video to Cloudinary (unsigned preset) / show a URL with Glide | The database only stores the returned https URL |
| `AvatarBinder.kt` | Draws a profile photo into the existing initials circle | No layout changes needed for photos |
| `ProfileCache.kt` | Remembers `profiles/{uid}` already loaded | Lists don't re-read the same profile for every row |
| `FriendState.kt` / `FriendActions.kt` | Live friends + incoming + outgoing requests / every request action as one multi-path update | Same friend button logic on 4 screens |
| `ChatSender.kt` | Sends any chat message (text, photo, post, call line) | Used by Chat, Story replies, Share post, Calls |
| `PushQueue.kt` | Adds a push request for `fcm-server` | The phone never holds the FCM server key |
| `Nav.kt`, `TimeFormat.kt`, `Session.kt`, `Insets.kt`, `TabBadges.kt` | Tab navigation without stacking, "5m / 2h / Yesterday", login memory + logout order, system bar padding, unread badges | Small helpers |

Viva questions:
- *Why remove listeners in onStop?* A `ValueEventListener` keeps downloading and calling back even when the screen is hidden; removing it avoids leaks and wasted data.
- *Why is a Firebase write sometimes a map of paths (`updateChildren`)?* A multi-path update is atomic: all paths change or none do (e.g. both sides of a friendship).
- *What is `.sv: timestamp`?* A placeholder the server replaces with its own time, so all phones agree on message order.

## 2. Splash, sign up, log in, log out (01, 02, 03, 16, 19)
Files: `MainActivity`, `LoginActivity`, `SignupActivity`, `EditProfileActivity`, `MenuActivity`, `Session`.
- Splash posts a 5000 ms `Handler` callback in `onCreate` and cancels it in `onDestroy`. The profile check (`users/{uid}/profileSetupComplete`) runs in parallel; at 5 s the screen routes: logged out → Log in, profile known incomplete → Edit profile, else → Home (cached flag if the network is slow).
- Sign up creates the Firebase Auth account, then writes `users/{uid}` (private) and `profiles/{uid}` (public). The strength meter is a `TextWatcher` that recolours 3 bars on every keystroke.
- Edit profile claims `usernames/{name}` with a **transaction** (only succeeds if nobody owns it), so usernames are unique. A first-time save goes to Home with a cleared back stack.
- Recent login: SharedPreferences `recent_email/recent_name/recent_photo`, written after a successful login.
- Log out (`Session.logout`): presence offline → remove this phone's FCM token → stop global listeners → `signOut()` → Login with `NEW_TASK | CLEAR_TASK`. Offline and token removal happen **before** sign-out because the rules need the user to be signed in for those writes.

Viva: *How is the username unique?* `usernames/{username} = uid`, written with a transaction that aborts if the node exists; the rule also only allows creating it when it does not exist.

## 3. Friends and profiles (13, 14, 15, 17, 18)
Files: `FriendActions`, `FriendState`, `FriendsActivity`, `SearchActivity`, `UserSearchAdapter`, `OtherProfileActivity`, `ProfileActivity`, `NotificationsActivity`.
- `friendRequests/{receiver}/{sender}` = incoming requests, `sentRequests/{sender}/{receiver}` = outgoing ("Requested"), `friends/{a}/{b} = true` both ways.
- Confirm = one update: both `friends` entries, delete the request and the sent index, notify the sender. Rules only allow creating `friends` while a matching request exists.
- The button label is `FriendState.relation(uid)`: NONE → "Add friend", OUTGOING → "Requested", FRIENDS → "Friends", INCOMING → "Confirm".
- Mutual friends = set intersection of `friends/{me}` and `friends/{them}`. Suggestions = friends of my friends ranked by mutual count.

Viva: *Why `sentRequests`?* Without it, showing "Requested" would need reading `friendRequests/{other}` for every row, which the rules (rightly) don't let you read in bulk.

## 4. Posts, reactions, comments, feed (04, 05, 06, 07, 08)
Files: `CreatePostActivity`, `PhotoPickerActivity`, `PostAdapter`, `HomeActivity`, `Reactions`, `ReactionPickerActivity`, `CommentsActivity`, `CommentAdapter`.
- Photos are uploaded one by one (`MediaUploader`), then `posts/{id}` stores `images` (ordered URLs).
- Grid rule (from page 04): 1 photo = full width; 2 = side by side; 3 or more = big left + two stacked right, and with more than 3 the third tile shows "+N".
- Reactions: `postReactions/{postId}/{uid} = "Love"` → one reaction per user; tapping the same one removes it. Each feed card listens to its own reactions/comments while visible.
- Comments: a reply stores `parentCommentId` = the top-level comment of the thread, and `parentName`; the list shows 2 replies per thread until "View N more replies".
- Feed: one query per author (`orderByChild("authorId").equalTo(uid).limitToLast(20)`) for me + up to 40 friends, merged and sorted by `createdAt`.

Viva: *Why one query per author instead of one query for all posts?* RTDB can only filter on one child; "author in my friends list" is not one query, so we query each author and merge on the phone.

## 5. Stories (04, 09, 10, 11, 12)
Files: `CameraActivity`, `StoryEditorActivity`, `Stories`, `StoryRowAdapter`, `StoryPlayer`, `StoryViewerActivity`, `YourStoryActivity`, `CloseFriendsActivity`.
- `stories/{owner}/public/{id}` (friends can read) and `stories/{owner}/closeFriends/{id}` (only people in `closeFriends/{owner}` can read).
- `expiresAt = FirebaseData.now() + 24 h`. Every reader ignores expired stories; the owner's app deletes its expired ones when Home opens.
- `storyViews/{owner}/{storyId}/{viewer}` = time → "Seen by N" and the grey/teal ring.

Viva: *How do stories disappear after 24 hours without a server?* Two layers: everyone filters out `expiresAt <= now`, and the owner's own app deletes them.

## 6. Chat and Chats (20, 21)
Files: `ChatsActivity`, `ChatActivity`, `MessageAdapter`, `ChatSender`, `SharePostActivity`.
- `chatId` = the two uids sorted and joined with "_". `chats/{chatId}`: `members`, `messages`, `lastMessage`, `unread/{uid}`, `lastRead/{uid}`, `typing/{uid}`; `userChats/{uid}/{chatId}` lists my chats.
- Message types: `text`, `image`, `post`, `system` (screenshot), `call`.
- Edit/delete: offered only on my own messages while `FirebaseData.now() - timestamp < 5 min`; the rule repeats the same check with the server's `now`, so it cannot be bypassed. After an edit/delete `lastMessage` is corrected.
- Hearts: `messageReactions/{chatId}/{messageId}/{uid} = "heart"`; one listener for the whole chat shows both people's hearts.

Viva: *Is the 5-minute limit only in the UI?* No: `database.rules.json` rejects edits/deletes when `now - timestamp >= 300000`.

## 7. Presence (20, 21)
`KinnectApplication`: listens to `.info/connected`; when connected, registers `onDisconnect` to write `{online:false, lastSeen}` and then writes `{online:true}`. If the app goes to the background for 5 s the user goes offline, except while the system camera/gallery is open for us (`ExternalLaunch`).

Viva: *What is onDisconnect?* A write the Firebase server performs by itself when the phone's connection drops (even if the app crashes).

## 8. Search (13)
Four prefix queries on `profiles` (`usernameLower`, `nameLower`, `firstNameLower`, `lastNameLower`) with `startAt(term).endAt(term + "\uf8ff")`, merged, debounced 250 ms; an increasing `generation` number drops results from older queries.

## 9. Push notifications
`ChatSender` adds `pushQueue/{id}` in the same update as the message. `fcm-server/index.js` (Admin SDK) listens to `pushQueue`, sends a data message to every token in `fcmTokens/{toUid}`, deletes dead tokens and the queue item. `KinnectMessagingService` builds the notification and skips it if that chat is open.

Viva: *Why not send FCM from the phone?* Sending needs the project's private key; anyone could extract it from the APK and spam every user.

## 10. Screenshot alert (21)
`ScreenshotDetector`: Android 14+ `Activity.ScreenCaptureCallback`; older versions watch MediaStore for a new "screenshot" image while the chat is open. One function writes the system message, a notification and a push request together.

## 11. Calls (21, 22, Incoming call)
`CallManager`, `IncomingCallActivity`, `VoiceCallActivity`. Signalling in `callRequests/{callee}/{callId}` with `status` ringing → accepted → ended (or declined / missed / cancelled). The callee is reached by a global listener (app open) or an FCM `call` push (full-screen notification). Both join the Agora channel named `callId` with a null token; remote video is attached in `onUserJoined`. The caller writes the call line into the chat.

Viva: *Why is the token null?* The Agora project is in testing mode (App ID only). Production would use a token server that signs a token per channel with the App Certificate.

## 12. Database rules
`database.rules.json` is deployed with `firebase deploy --only database`. Default deny at the root; every path opens only what the app needs, and `.validate` checks field shapes. The table below records each schema change.

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
| (all) | `.validate` added for field shapes: names ≤ 60, usernames `a-z0-9_.` 3–30, bios ≤ 150, photo/cover/media must be `https://` URLs, post text ≤ 2000 and ≤ 10 images, comment text ≤ 1000, message types `text/image/post/system/call`, notification types listed, presence booleans/numbers, call status values | see `database.rules.json` |
| `chats/{chatId}/members` | loophole closed: you can only create a chat whose id contains your uid, and the other member must be the other half of the id (nobody can pre-create a chat between two other people) | creator must be one of the two members |
| `closeFriends/{uid}/{friendUid}` | only real friends can be added | owner only |
