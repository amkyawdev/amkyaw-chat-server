# amkyaw server

**AmkyawDev-Chat-Server** — a local chat server that runs on your phone and bridges the LAN room to a Telegram bot.

Terminal-emulator UI: near-black surfaces, matrix-green neon, monospace only, glow instead of shadow.

```
amkyaw@server:~$ ./boot.sh
[ OK ] Initializing amkyaw kernel...
[ OK ] Loading network modules...
[ OK ] Scanning WiFi interface...
[ OK ] Binding port :8080
[ OK ] Establishing peer links...
[ OK ] Connecting telegram relay...
[ OK ] amkyaw server ONLINE
_
```

## Screens

| # | Screen | Class | Layout |
|---|--------|-------|--------|
| 1 | Boot / splash | `ui/TerminalBootActivity.kt` | `res/layout/activity_boot.xml` |
| 2 | Dashboard | `MainActivity.kt` | `res/layout/activity_main.xml` |
| 3 | Chat room | `ui/ChatActivity.kt` | `res/layout/activity_chat.xml` |

Open `design/mockups.html` in a browser for 1:1 mockups with live matrix rain, scanlines, typewriter boot log and a blinking cursor.

## Build

Requires JDK 17 and the Android SDK (compileSdk 34).

```bash
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease   # minified, unsigned unless signing env vars are set
```

CI builds both APKs on every push: `.github/workflows/build-apk.yml`.

### Signed releases

Release signing reads the keystore from the environment, so no keystore or
password is ever committed:

| Variable | Meaning |
|----------|---------|
| `AMKYAW_KEYSTORE_FILE` | path to the `.jks` file |
| `AMKYAW_KEYSTORE_PASSWORD` | keystore password |
| `AMKYAW_KEY_ALIAS` | key alias |
| `AMKYAW_KEY_PASSWORD` | key password |

```bash
export AMKYAW_KEYSTORE_FILE=/secure/path/amkyaw-release.jks
export AMKYAW_KEYSTORE_PASSWORD=...
export AMKYAW_KEY_ALIAS=amkyaw
export AMKYAW_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

When those variables are absent the release build still succeeds, just unsigned.

Pushing a `v*` tag publishes a GitHub release with the signed APK attached
(`.github/workflows/release.yml`). The workflow needs four repository secrets:
`AMKYAW_KEYSTORE_BASE64`, `AMKYAW_KEYSTORE_PASSWORD`, `AMKYAW_KEY_ALIAS` and
`AMKYAW_KEY_PASSWORD`.

> Keep the keystore file backed up somewhere safe. Losing it means you can never
> ship an update to an installed app, because Android identifies apps by signing
> certificate.

## Architecture

```
AmkyawApp ── owns ──> ServerManager ──> ChatServer      (WebSocket, LAN)
   │                        │        └─> TelegramBotService (long-poll)
   │                        └─── ChatRoom (StateFlow transcript)
   │
   └── ServerService (foreground) keeps the server alive off-screen
```

* **`ChatServer`** — Java-WebSocket server on `:8080`. Every frame is `sender\ttext`, broadcast to all other peers and surfaced to the room.
* **`TelegramBotService`** — long-polls `getUpdates`, handles `/status /ip /whoami /peers /clear /echo`, relays room messages to the bound chat. The relay chat id is learned from the first inbound message (the Bot API cannot address a user before they write to the bot).
* **`PeerRelay`** — loop guard between the two transports, keyed on message id.
* **`PeerDiscovery`** — `_amkyaw._tcp` mDNS advertisement + browse, so peers find the host without typing an IP.
* **`assets/chat.html`** — zero-install browser client served to LAN peers.

State reaches the UI through `StateFlow`, collected with `repeatOnLifecycle`, so nothing renders into a stopped activity.

## Design tokens

All values live in `res/values/`. Nothing is hardcoded in layouts.

**Surfaces** `bg_void #0A0E14` · `bg_surface #0D1117` · `bg_surface_alt #141A22` · `bg_bar #111820` · `bg_btn_green #0A2E0A`
**Accents** `neon_green #00FF41` · `cyan #00D9FF` · `red_alert #FF0040` · `yellow_alert #FFD700`
**Text** `text_primary #00FF41` · `text_secondary #5A7D5A` · `text_dim #2D4A2D` · `text_white #C9D1D9`

**Type** `10sp` caption · `11sp` boot · `12sp` body/labels · `13sp` input & bubbles · `14sp` buttons · `16sp` prompt · `28sp` title
**Spacing** 4dp grid, `space_2` … `space_32`
**Metrics** terminal radius 4dp · bubble radius 2dp · border 1dp · buttons 48dp/44dp · inputs 48dp · chat header 48dp · chat bar 56dp · status dot 8dp · cursor 8×16dp · bubble max width 78%

### Rules

* Glow is a TextView shadow layer (`shadowDx/Dy = 0`, radius 4–8dp) — never a drop shadow.
* Buttons are sharp (2dp), thin-bordered, uppercase, `letter-spacing 0.1em`, with an `_` suffix (`OPEN_CHAT`).
* Pressed state brightens the fill and drops the glow; no elevation, no ripple.
* Inputs are square with a bracket placeholder (`[ENTER] BOT_TOKEN`).
* ASCII only for iconography: `>_ [+] [!] [<]`.

### Motion

| Effect | Where | Spec |
|--------|-------|------|
| Matrix rain | boot 25%, dashboard 10% | `MatrixRainView`, katakana, bright head, 14-glyph tail |
| Scanlines | all screens | `ScanlineOverlay`, 3dp spacing at 5% alpha |
| Typewriter | boot log | `TypingTextView`, 45ms/char |
| Blinking cursor | boot + chat input | `@anim/blink`, 500ms reverse |
| Glitch | error states | `@anim/glitch`, 5 jitters + `GlitchTextView` RGB split |
| Message in | new bubbles | `@anim/message_in`, 180ms rise+fade |

## Security

The Telegram bot token is the only secret. It is stored in the private app sandbox, excluded from both cloud backup and device transfer (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`), never logged, and only ever printed masked as `123456789:****`.

## Layout

```
app/src/main/
├── AndroidManifest.xml
├── assets/chat.html                  browser client for LAN peers
├── java/com/amkyawdev/chatserver/
│   ├── AmkyawApp.kt  MainActivity.kt  ServerService.kt
│   ├── ServerManager.kt  NotificationHelper.kt  BootReceiver.kt
│   ├── chat/      ChatMessage ChatRoom ChatServer ChatClient PeerDiscovery PeerRelay
│   ├── telegram/  TelegramApi TelegramBotService BotCommandHandler TelegramConfig
│   ├── ui/        TerminalBootActivity ChatActivity MessageAdapter
│   │              MatrixRainView GlitchTextView ScanlineOverlay TypingTextView
│   └── util/      IpUtils PrefsHelper LogHelper
└── res/
    ├── anim/      blink glitch fade_in fade_out message_in
    ├── color/     btn_green_text btn_cyan_text status_dot_tint
    ├── drawable/  bg_terminal bg_btn_green bg_btn_cyan bg_input
    │              bg_message_other bg_message_mine bg_glow_green cursor
    │              ic_status_dot ic_server ic_send ic_launcher_foreground
    ├── font/      jetbrains_mono{,_regular,_bold}
    ├── layout/    activity_boot activity_main activity_chat
    │              item_message item_message_mine
    ├── mipmap-*/  ic_launcher ic_launcher_round
    ├── values/    colors dimens strings themes styles attrs
    └── xml/       backup_rules data_extraction_rules
```

Portrait only, 360dp minimum width.

## Notes

The bot token is never required to use the LAN chat. Without a token the dashboard shows `[IDLE] relay not bound` and everything else works.

Release builds enable R8 with `shrinkResources`; keep rules for Java-WebSocket, OkHttp and Okio are in `app/proguard-rules.pro`.
