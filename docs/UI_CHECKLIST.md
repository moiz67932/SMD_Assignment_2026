# UI checklist (design vs. build)

The reference is `Kinnect_UI (1).pdf`, one page per screen. Nothing here was checked on a phone: no device or emulator was connected while this was written. "Matches (static review)" means the layout XML was compared with the design page and the structure, styles and colours line up. It does **not** mean pixel-perfect. Every row still needs a look on a real phone.

Global items for every screen:
- **Insets (edge-to-edge):** all screens get status/navigation bar padding from `Insets.kt`. Check that no toolbar sits under the status bar and no bottom tab sits under the gesture bar.
- **Status-bar icons:** dark icons on light screens; light icons on Splash, Camera, Story editor/viewer, Your story, Voice call and Incoming call.
- **Avatars:** initials circles, replaced by the profile photo when one exists. Check that photos are round and that rows don't flicker while scrolling.
- **Badges:** red counts on the chat icon and the bell (Home, Friends, Notifications, Marketplace, Menu).

| # | Screen | Status | What to check on a device |
|---|---|---|---|
| 01 | Splash | Matches (static review) | Shows 5 s; teal background; "from SMD" stays at the bottom above the nav bar |
| 02 | Log in | Fixed | Recent login card: real initials/photo, "…" removes it; Forgot password link |
| 03 | Sign up | Fixed | Strength meter: 1 red / 2 orange / 3 teal bars with label colour; "Step 2 of 3" is kept as design text |
| 04 | Home feed | Fixed | Stories row ("Create story" first, rings), post card grid (1 / 2 / 3+ with "+N"), reaction summary row, Like turns teal or shows the chosen reaction, badges |
| 05 | Reaction picker | Fixed | Title "{Name}'s post", post photo, enlarged current reaction with the dark bubble above it (bubble position is computed at runtime: check alignment) |
| 06 | Comments | Fixed | Nested replies with thread line, "View N more replies", like badge on the bubble corner, photo comment, "Replying to … Cancel" bar; keyboard must not cover the input |
| 07 | Create post | Fixed | Now scrolls (short phones could not reach the bottom options before); selected-photo strip; colour swatches; upload line |
| 08 | Photo picker | Fixed | Real gallery grid (square tiles), numbered selection, "Next (N)", video durations, Gallery/Photo/Video tabs, permission-denied message |
| 09 | Camera | Needs on-device check | No live viewfinder (system camera is used); the dark preview with a hint replaces the design's camera image. STORY/POST underline moves; other modes dimmed |
| 10 | Story editor | Fixed | Label pill over the photo/video, selected audience button turns teal, upload status bubble |
| 11 | Story viewer | Fixed | Segments advance, label pill, "2h · Public", reply field + 3 quick reactions above the nav bar |
| 12 | Your story | Fixed | "Seen by N" + avatars, hearts column grows with Love reactions, Create/Highlight/Send/More row |
| 13 | Search | Fixed | Chips highlight, clear (x) button, recent searches with clock icons, friend buttons (teal = action, grey = pending/friends) |
| 14 | Friends | Fixed | "Friend requests N" with red number, Confirm/Delete side by side, mutual-friend mini avatars, "People you may know" |
| 15 | Profile | Fixed | Cover photo, avatar ring is lost when a photo is shown (photo replaces the outlined circle); friend tiles show photos with rounded corners |
| 16 | Edit profile | Fixed | Photo/cover "Edit" labels show upload %; Bio and Website rows save the right fields |
| 17 | Other profile | Fixed | No flash of sample text; "Friends with A, B and N others"; photos grid or "No photos yet"; "…" menu |
| 18 | Notifications | Fixed | Teal tint for New, type badge on avatars, inline Confirm/Delete for pending requests, "See previous notifications" |
| 19 | Menu | Matches (static review) | Real name/photo; "See more" opens Your story / Close friends; design-only tiles show a toast |
| 20 | Chats | Fixed | Active strip with green dots, bold unread rows + teal dot, "You: …", search filter, pencil button |
| 21 | Chat | Fixed | Intro card, date separators ("TODAY 4:49 PM"), grouped bubbles with avatar on the last one, heart badge position, "You replied to …" + quote, seen avatar, typing dots, online dot on the toolbar avatar; keyboard must push the input up |
| 22 | Voice call | Fixed | Name/avatar, "Calling…" then the timer, mute/speaker turn white when active, video: full-screen remote + small local preview, switch-camera button. **Deliberate difference:** "End-to-end encrypted" is replaced by "Kinnect voice call" (the claim is not guaranteed by Agora's default setup) |
| 23 | Marketplace | Matches (static review) | Live items grid; "Karachi · 40 km" replaced by the live item count |
| new | Media viewer | Matches design language | Black full-screen, round close button, "2 / 5" counter |
| new | Close friends | Matches design language | Teal info card, star toggles turn green |
| new | Share post | Matches design language | Post preview card, note field, Send → "Sent" |
| new | Incoming call | Matches design language | Same halo avatar as the Voice call screen, red Decline / green Accept, shows over the lock screen |
| new | Chat intro / typing rows | Matches design language | Inside Chat (21) |
