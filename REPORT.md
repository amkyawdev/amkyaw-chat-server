# amkyaw server — build report

Target repository: `amkyawdev/amkyaw-chat-server`
Generated: 2026-10-31

---

## 1. Outcome

| Item | Result |
|---|---|
| Project scaffold | Complete — matches the specified file tree |
| XML resources | 37 files, all well-formed, all references resolve |
| Kotlin sources | 26 files, 0 compile errors |
| `:app:assembleDebug` | **BUILD SUCCESSFUL** |
| APK | `app/build/outputs/apk/debug/app-debug.apk` (7.06 MB) |
| Toolchain used | JDK 17.0.21 (Temurin), Android SDK 34, Build-Tools 34.0.0, Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24 |

The APK was produced by a real `aapt2` + `kotlinc` + `d8` pipeline, not by static
inspection alone. Every error listed in section 3 was found by that pipeline and
fixed before the build went green.

---

## 2. What was built

### Screens

| # | Screen | Activity | Layout |
|---|---|---|---|
| 1 | Boot / splash | `ui/TerminalBootActivity` | `res/layout/activity_boot.xml` |
| 2 | Dashboard | `MainActivity` | `res/layout/activity_main.xml` |
| 3 | Chat room | `ui/ChatActivity` | `res/layout/activity_chat.xml` |

### Design system

Every value is a resource; no colour, size or spacing is hardcoded in a layout.

* **Surfaces** `bg_void #0A0E14`, `bg_surface #0D1117`, `bg_surface_alt #141A22`,
  `bg_bar #111820`, `bg_btn_green #0A2E0A`
* **Accents** `neon_green #00FF41`, `cyan #00D9FF`, `red_alert #FF0040`,
  `yellow_alert #FFD700`
* **Type** 10 / 11 / 12 / 13 / 14 / 16 / 28 sp
* **Spacing** 4dp grid, `space_2` … `space_32`
* **Metrics** terminal radius 4dp, bubble radius 2dp, border 1dp, buttons
  48/44dp, inputs 48dp, chat header 48dp, chat bar 56dp, status dot 8dp,
  cursor 8×16dp, bubble max width 78%

### Custom views

| View | Effect |
|---|---|
| `MatrixRainView` | katakana rain, bright head + 14-glyph dimming tail, per-column density mask |
| `ScanlineOverlay` | CRT scanlines, 3dp spacing at 5% alpha |
| `TypingTextView` | typewriter reveal with blinking block cursor |
| `GlitchTextView` | RGB-split echo plus shake animation |

### Runtime

* `ChatServer` — Java-WebSocket server on `:8080`, frames are `sender\ttext`,
  broadcast to all other peers.
* `TelegramBotService` — long-poll `getUpdates`, commands `/status /ip /whoami
  /peers /clear /echo /help`, relay back into the LAN room.
* `PeerRelay` — loop guard keyed on message id.
* `PeerDiscovery` — `_amkyaw._tcp` mDNS advertise + browse.
* `ServerService` — foreground service keeping the server alive off-screen.
* `assets/chat.html` — zero-install browser client for LAN peers.

---

## 3. Errors found and fixed

These are the real defects the compiler caught. They are worth recording
because several are non-obvious.

### 3.1 Resource linking

| # | Error | Cause | Fix |
|---|---|---|---|
| 1 | `style/Widget.Amkyaw.Bubble not found` | Dotted style names imply parent inheritance, so `Widget.Amkyaw.Bubble.Mine` requires a `Widget.Amkyaw.Bubble` to exist | Declared the two base styles |
| 2 | `style/Widget not found` | An attribute-less `<style name="Widget.Amkyaw"/>` then resolves its implicit parent to the framework style `Widget` | Added `parent=""` to cut inheritance |
| 3 | `'textShortMessage\|textMultiLine\|capSentences' is incompatible with attribute inputType` | `textShortMessage` and `textMultiLine` are mutually exclusive variants of the same flag group | Reduced to `textCapSentences\|textMultiLine` |

### 3.2 Kotlin compilation

| # | Error | Cause | Fix |
|---|---|---|---|
| 4 | `Unresolved reference: it` ×4 | `attrs?.let { a -> ... }` shadows the lambda's `it`, so `obtainStyledAttributes(it, …)` no longer refers to `attrs` | Passed `a` instead of `it` |
| 5 | `Unresolved reference: startDiscovery` | The API is `NsdManager.discoverServices(…)`; there is no `startDiscovery` | Renamed, and wrapped both NSD calls in a retry loop because `FAILURE_ALREADY_ACTIVE` is common when toggling the server |
| 6 | `Unresolved reference: context` | `PrefsHelper(context: Context)` — a non-`val` constructor parameter is not visible inside property getters | Promoted to `private val context` |

### 3.3 Correctness issues fixed before compiling

Found by review, not by the compiler:

* `ChatServer.shutdown()` called `WebSocketServer.stop()`, which **joins its
  worker threads**. Called from the main thread this freezes the UI. Now
  dispatched to a background thread.
* `MessageAdapter` restarted the fade-in on every rebind, so the whole list
  flickered on each message. Now gated on a set of already-animated ids.
* The dashboard's `[+] IP` row was never populated on first paint; it is now
  set in `onResume` and from the state flow.
* `ServerService.stop()` used `startService`, which is subject to the
  background-start restriction. Now `stopService`.
* `MessageAdapter.submit()` allocated a new list on every emit from the room
  flow; now only on submit.

---

## 4. Verification performed

1. **Real build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, APK 7.06 MB.
2. **Static resource check** — a purpose-written validator confirmed all 37 XML
   files parse, and every `@type/name` reference in XML and every `R.type.name`
   reference in Kotlin resolves to a definition in the project.
3. **View-binding consistency** — every `binding.<id>` used in Kotlin was
   matched against the `android:id` values in the corresponding layout.
4. **Kotlin parse** — all 26 sources parse clean under tree-sitter.
5. **Secret scan** — `grep` for `ghp_`, `github_pat_`, `sk-`, `AKIA` patterns
   across the tree: no matches.

### Not verified

No emulator or physical device was available, so the app was **not executed**.
Rendering, animation timing and the network paths are unverified at runtime.
The build is green and the resources link, but "it compiles" is not "it works".

---

## 5. Security notes

* The Telegram bot token is the only secret in the design. It lives in the
  private app sandbox, is excluded from cloud backup and device transfer
  (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`), is never
  logged, and is only printed masked as `123456789:****`.
* **A GitHub token was pasted into the conversation.** It was not used: the
  environment's system-managed `GITHUB_TOKEN` already authenticates as
  `amkyawdev` with access to the target repository. That pasted token is
  exposed in the conversation log and **should be revoked** at
  <https://github.com/settings/tokens>.
* The target repository `amkyawdev/amkyaw-chat-server` is **public**. If
  "private report" was meant literally, the repository visibility must be
  changed in the GitHub UI — an API token cannot flip that setting.

---

## 6. Deliverables

```
AmkyawDev-Chat-Server/
├── .github/workflows/build-apk.yml   CI: debug + release APK artifacts
├── README.md                          design spec + architecture
├── design/mockups.html                1:1 mockups, live rain/scanlines/typewriter
├── build.gradle.kts  settings.gradle.kts  gradle.properties
├── gradlew  gradlew.bat  gradle/wrapper/{jar,properties}
└── app/
    ├── build.gradle.kts  proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/chat.html
        ├── java/com/amkyawdev/chatserver/   (26 Kotlin files)
        └── res/                             (37 XML + fonts + icons)
```

`design/mockups.html` renders all three screens at 360dp with the exact palette,
including the boot typewriter and the matrix rain.
