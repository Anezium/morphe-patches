---
task: youtube-glasses-mode
date: 2026-09-27
status: delivered-with-visual-deviations
repo: E:\Tools\Rokid\Morphe-Patches
branch: rokid-glasses-controls
scope_globs:
  - "patches/src/main/kotlin/app/morphe/patches/youtube/interaction/rokidcontrols/**"
  - "patches/src/test/kotlin/app/morphe/patches/youtube/interaction/rokidcontrols/**"
  - "extensions/youtube/src/main/java/app/morphe/extension/youtube/rokid/**"
  - "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/RokidControlsPatch.java"
  - "patches/src/main/kotlin/app/morphe/patches/youtube/shared/Fingerprints.kt"
  - "contracts/2026-09-27-youtube-glasses-mode.contract.md"
forbidden_globs:
  - "patches/build.gradle.kts"
  - "extensions/**/settings/**"
  - "patches/src/main/resources/**"
  - "**/local.properties"
  - "E:/Tools/Rokid/RokidNexus/**"
  - "E:/Tools/Rokid/RokidNexus-*/**"
test_commands:
  - "powershell -File E:\\Tools\\Rokid\\tmp\\morphe-rokid-prototype\\invoke-gradle.ps1 :patches:test"
  - "powershell -File E:\\Tools\\Rokid\\tmp\\morphe-rokid-prototype\\run-build-and-package.ps1"
max_failures: 2
---

# Goal

YouTube 21.04.223 patched with "Rokid controls" is comfortable to use on Rokid glasses
with the temple touchpad only: one axis (swipe forward/back), tap, back gesture. When this
is done, the `app.morphe.android.youtube.rokidtest` build on the glasses shows the five
screens of the mockup `E:\Tools\Rokid\RokidNexus-youtube\build\mockup\youtube-glasses-mode.html`
(open it in a browser first; it is the spec): a feed with one focused card and a visible
ring, a sections list instead of the tab bar, a watch screen with an icon rail and a
seek bar, fullscreen shortcuts, and search that hands typing to the phone. Delivered as
three committed slices on branch `rokid-glasses-controls`, each verified on the device.

# Non-goals

- No change to which stock YouTube version is targeted (21.04.223) and no new patch.
  Everything lives inside the existing "Rokid controls" patch and its extension classes.
- No keyboard bridge to the phone: the Nexus IME on the glasses already opens the phone
  keyboard when a field takes focus. Search only has to focus YouTube's own search field.
- No Shorts support, no comments navigation, no like/subscribe actions, no settings UI.
- No work in the RokidNexus repositories, no phone-side change.
- No pointer/cursor mode. One axis only.

# Constraints

- MUST work on branch `rokid-glasses-controls` of `E:\Tools\Rokid\Morphe-Patches` (HEAD
  `e1dbf10b` at contract time). MUST NOT `git checkout` another branch: it is a single
  shared checkout. MUST commit after each slice (author Anezium, English, imperative, no
  AI attribution, no Co-Authored-By).
- MUST keep the patch opt-in: `rokidControlsPatch` keeps `default = false`.
- MUST keep the key contract of `RokidKeyMapper` / `RokidKeyBypass`: BACK is never consumed
  by the mapper (the sections list intercepts Back in the controller, and only on a
  feed surface); keys reaching an editable field or a foreign window pass through.
- MUST keep everything drawn by the patch as overlay views added to the content root
  (like `RokidPlayerRailView`): never edit YouTube layouts or Litho components. Hiding
  chrome means setting existing views GONE by resource id, restored when the patch
  is detached (`RokidControlsController.detach`).
- MUST keep `RokidControlsState`, `RokidKeyMapper`, `RokidFeedScope`, `RokidKeyBypass` and
  any new pure state class Android-free: they are compiled into `:patches` tests through
  `patches/build.gradle.kts` `sourceSets.test` (that file is forbidden to edit; put new
  pure classes in `extensions/youtube/src/main/java/app/morphe/extension/youtube/rokid/core/`
  so they are picked up).
- MUST keep black (`#000000`) as the background of every overlay: black is transparent
  on the glasses optics. Text white, accent `#FF0033`, dim `#A3A3A3`, focus ring white 3 dp.
- MUST size for 480×640 px at 240 dpi (320×426 dp). Rail keys 80 dp, feed title 21 sp
  max 2 lines, hints strip 11.5 sp mono at the bottom.
- MUST log every handled key through the existing `logKey` (`logcat -s RokidControls`) and
  MUST NOT log editor keystrokes.
- MUST NOT touch the wearer's main YouTube (`app.morphe.android.youtube`) or MicroG on the
  glasses. Only `app.morphe.android.youtube.rokidtest` may be reinstalled or uninstalled.
- MUST NOT commit screenshots, APKs, `.mpp` bundles or dumps. Put device evidence in
  `E:\Tools\Rokid\tmp\morphe-rokid-prototype\evidence\<slice>\`.
- MUST NOT edit `patches/build.gradle.kts`, any Morphe settings/resources, or files of
  other patches beyond what is already in HEAD. If a hook in another Morphe file is
  unavoidable, stop and report (escalation).
- MUST NOT use `monkey` on the glasses (it turns auto-rotate on). Launch with
  `adb shell am start -n <component>`.

# Acceptance tests

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | Pure state tests | `powershell -File E:\Tools\Rokid\tmp\morphe-rokid-prototype\invoke-gradle.ps1 :patches:test` | BUILD SUCCESSFUL, 0 failures, new tests for every new state class |
| 2 | Bundle + patched APK + install | `powershell -File E:\Tools\Rokid\tmp\morphe-rokid-prototype\run-build-and-package.ps1` | ends with "completed successfully"; `patch-result.json` lists "Rokid controls" |
| 3 | Patched build installed | `adb -s 1901092534053723 shell dumpsys package app.morphe.android.youtube.rokidtest \| findstr lastUpdateTime` | timestamp of this run |
| 4 | Main YouTube untouched | `adb -s 1901092534053723 shell dumpsys package app.morphe.android.youtube \| findstr lastUpdateTime` | unchanged: `2026-09-2x` earlier than this run |
| 5 | Feed chrome hidden (slice 1) | `adb -s 1901092534053723 shell dumpsys activity top \| findstr /C:"app:id/pivot_bar" /C:"app:id/toolbar"` | every matching line carries the GONE flag (`G.` in the view flags) |
| 6 | Feed step + ring (slice 1) | `input keyevent 20` twice then `logcat -d -s RokidControls` + screencap | two `command=FEED_NEXT consume=true`; screenshot shows one card with a white ring at the top and a `n / N` counter top-right |
| 7 | Feed select (slice 1) | `input keyevent 66` then logcat | `command=FEED_SELECT` followed by a key line with `surface=PLAYER` |
| 8 | Rail v2 (slice 2) | in PLAYER: `input keyevent 22` ×2, screencap | `RAIL_MOVED` lines; screenshot shows five 80 dp keys, focused key filled white with a label, others outline only, seek bar + time under the video |
| 9 | Rail auto-hide (slice 2) | wait 4 s after last key, screencap | rail not visible; next `input keyevent 22` shows it again without moving focus |
| 10 | Fullscreen shortcuts (slice 2) | select the fullscreen key, then `input keyevent 22` | logcat `ACTIVATE_SEEK_FORWARD` (no rail move) while `PlayerType == WATCH_WHILE_FULLSCREEN`; `input keyevent 66` toggles play/pause; `input keyevent 4` leaves fullscreen |
| 11 | Sections (slice 3) | on a feed `input keyevent 4`, screencap, `input keyevent 20`, `input keyevent 66` | sections list appears (Back consumed once, logged `SECTIONS_OPEN`); second row focused; Subscriptions feed opens; Back on the sections list finishes the Activity |
| 12 | Search hand-off (slice 3) | open Search from sections | YouTube's search field has focus; `adb -s R5CW12DK1AY shell dumpsys activity activities \| findstr RemoteInputActivity` on the phone shows the Nexus keyboard screen resumed, or its prompt notification is posted |
| 13 | Nothing consumed in an editor | with the search field focused, `input keyevent 20` | logcat shows no line (editor keystrokes are not logged) and the field keeps focus |
| 14 | Diff scope | `git diff --stat e1dbf10b..HEAD -- . ':!contracts'` | only paths under scope_globs |

# Plan sketch

Slice 1 — Feed (commit "Draw the glasses feed: one focused card, ring, hidden chrome")
1. `RokidFocusRingView`: overlay in the content root; `follow(view)` reads the target's
   rect via `getLocationInWindow` and draws a 3 dp white rounded ring 3 dp outside it;
   `hide()`. Re-follow on the target's layout changes (`OnLayoutChangeListener` and
   `ViewTreeObserver.OnScrollChangedListener`, unregistered on detach).
2. Chrome: in `attach`/`refresh`, resolve by id and set GONE: `toolbar`, `toolbar_container`,
   `appbar_layout`, `pivot_bar`, `bottom_bar_container`, and the filter chip bar under
   `results` (find the id with `dumpsys activity top`; do not guess). Store previous
   visibilities and restore in `detach`.
3. `stepFeed`: after `focusFeedItem`, scroll the `results` RecyclerView so the focused
   item's top is at the container's top (`scrollBy(0, item.top - container.paddingTop)`),
   and call the ring. Position counter overlay (`n / N` from `attachedFeedItems` and
   adapter item count when available) top-right, mono 12 sp.
4. Hints strip overlay at the bottom (36 dp, black): `⇅ next / prev · ● open · ◂ sections`.
   Text changes per surface.
5. Tests for any new pure class (e.g. counter formatting, ring geometry helper).

Slice 2 — Watch (commit "Redraw the player rail with icons, seek bar and auto-hide")
1. Replace `RokidPlayerRailView` internals: five 80 dp keys drawn with `Canvas` `Path`
   icons (pause/play, −10, +10, fullscreen, close), focused key filled white with black
   icon and a mono label under it, others 1.5 dp outline, unavailable at 30 % alpha.
2. Seek bar + time under the video: a 5 dp bar with a red played part, `mm:ss / mm:ss`
   from `VideoInformation.getVideoTime()/getVideoLength()`, refreshed by `onVideoTime`.
3. Auto-hide: rail and hints fade after 3 s without keys; the next key only reveals the
   rail (consumed, no rail move) when it was hidden.
4. Fullscreen: when `PlayerType.current == WATCH_WHILE_FULLSCREEN`, `RokidControlsState`
   maps PREVIOUS/NEXT to seek and SELECT to play/pause directly (new `RokidSurface.FULLSCREEN`);
   a transient pill overlay (2 s) shows play state, bar and time. Physical Back still
   passes through (YouTube exits fullscreen itself).
5. State tests for the new surface.

Slice 3 — Sections and search (commit "Add the sections list and search hand-off")
1. `RokidSectionsView` overlay: rows Home, Subscriptions, Search, History, Watch later, You
   (72 dp rows, 3 dp ring on the focused one). Opened by Back on a BROWSE surface: the
   controller consumes that Back (`RokidCommand.SECTIONS_OPEN`), state gains
   `RokidSurface.SECTIONS`. Back on the list closes it and calls `activity.onBackPressed()`
   once more only if the list was opened from the root feed (finishes YouTube).
2. Section activation: click the matching `pivot_bar` tab view (they exist even when GONE;
   `performClick` on the tab's view) for Home / Subscriptions / You; History and Watch
   later through YouTube's own intents (`android.intent.action.VIEW` on
   `https://www.youtube.com/feed/history` and `/playlist?list=WL` with the package set)
   if the tab click is not available. Search: `performClick` on the toolbar search
   action (`menu_item_search` or the `search_box` view), then `requestFocus` the
   `search_query` EditText; the Nexus IME does the rest.
3. Hints strip text per surface; tests for the sections state machine.

After each slice: run acceptance tests 1–4 and the slice's rows, store evidence, commit.

# Context the executor cannot re-derive

- Existing prototype (HEAD `e1dbf10b`): `RokidControlsController.kt` (651 lines) owns
  the runtime; `RokidControlsState.kt` the rail state machine; `RokidKeyMapper.kt` the
  debounce (240 ms, DPAD_LEFT/UP = PREVIOUS, DPAD_RIGHT/DOWN = NEXT, ENTER/CENTER/SPACE =
  SELECT, BACK passes through); `RokidFeedScope.kt` the verified ids of the 21.04 tree
  (`results`, `loading_layout`, `pane_fragment_contents`; chrome ids `pivot_bar`,
  `toolbar`, `bottom_bar_container`, `search_box`, `search_query`, `appbar_layout`).
  `RokidControlsPatch.java` is the injection gate; hooks: `MainActivity.dispatchKeyEvent`,
  swipe-controls host `attach/reattach/detach/onDestroy`, `videoTimeHook`, `onCreateHook`,
  `hookVideoId`, `EngagementPanel.onDescriptionChange`.
- Glasses: adb serial `1901092534053723`, 480×640 at 240 dpi, Android 12L. The ROM boots
  Wi-Fi off: `adb shell svc wifi enable` before testing (it rejoins "Freebox-1DE39C").
  `adb shell input keyevent 20/19/22/21/66/4` reaches YouTube's `dispatchKeyEvent`, so it
  is enough to drive the patch; `adb exec-out screencap -p > file.png` for evidence.
  `uiautomator dump` fails on YouTube (null root); use `dumpsys activity top` for the
  view tree (ids appear as `app:id/<name>`, GONE views show `G.` in the flags).
- Launcher component of the test build changes per patch run: resolve it with
  `adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER app.morphe.android.youtube.rokidtest`.
- Build chain (all in `E:\Tools\Rokid\tmp\morphe-rokid-prototype`): `run-build-and-package.ps1`
  runs `:patches:buildAndroid` (needs `gh auth token`, handled by the script), stages
  `patches-custom.mpp`, writes `options-rokidtest.json` (Clone app → `.rokidtest`, Rokid
  controls on, GmsCore support on), patches `youtube-21.04.223-original.apk` with
  `morphe-desktop-1.13.0-all.jar` (`--striplibs=arm64-v8a`), signs with `rokid-prototype.p12`
  (alias `rokid-prototype`, password in the script) and installs on the glasses through
  `verify-device-coexistence.ps1`. Same key every run, so `install -r` works. Takes a few
  minutes; write output to a log file and read the tail.
- Gradle for `:patches:test` needs GitHub Packages credentials: use `invoke-gradle.ps1`.
- The phone (S23, serial `R5CW12DK1AY`) runs the Nexus hub; the Nexus IME
  (`com.anezium.rokidbus.glasses/.NexusRemoteInputMethodService`) is the default keyboard on
  the glasses and opens the phone keyboard by itself when a field gets focus.
- The device screen sleeps after 5 s of inactivity: `adb shell input keyevent 224` wakes it;
  act within ~10 s.
- Past pitfalls: naming a Kotlin property `selected` on a View emits `setSelected(Z)V` and
  overrides `View.setSelected` (see `RailButton.highlighted`); `onCheckIsTextEditor` and
  `InputMethodManager.isActive` fire on static player chrome, never use them for bypass;
  horizontal `focusSearch` from `results` lands on `pivot_bar` Shorts, stay inside the
  container (already handled by `stepFeed`).
- The mockup HTML at `E:\Tools\Rokid\RokidNexus-youtube\build\mockup\youtube-glasses-mode.html`
  lists per-screen behaviour and the gesture map; it is the visual reference, not code.

# Escalation triggers (mechanical)

- Any test_command fails after 2 attempts.
- Diff touches files outside scope_globs or matching forbidden_globs.
- A slice needs a hook in a Morphe file not already modified in HEAD.
- The glasses or the phone are not reachable over adb, or the stock APK / CLI jar is missing.
- YouTube crashes (`AndroidRuntime` in logcat for the `.rokidtest` process) on any acceptance row.

# Autonomy

May decide alone: view ids beyond the listed ones (found with `dumpsys activity top`),
exact dp values within ±20 %, icon paths, animation timings, test names, helper class
names, the order of steps inside a slice.
Must stop and report: any change outside scope, any change to the key contract, skipping
a slice, uninstalling anything other than `.rokidtest`, any acceptance row that cannot be
made green after 2 attempts (report with the logcat/screenshot evidence, do not soften it).
Final report: per slice, commit hash, acceptance rows with the real command output tails,
paths of the evidence screenshots, and anything left undone.

# User-authorized continuation: optional automatic keyboard

On 2026-09-27 the user requested an enable/disable option in the Nexus YouTube
menu and then authorized resuming device verification. This supersedes the
phone-side/Nexus exclusion solely for that setting and its supporting keyboard
behavior, tests and documentation in `E:/Tools/Rokid/RokidNexus-youtube`.
No new transport, plugin capability, glasses IME change or main YouTube update
is required. The phone preference defaults off and is checked against the two
exact Morphe YouTube package names for real editable sessions.

Acceptance row 12 is verified with **Glasses apps > Set up YouTube > Auto-open
keyboard** enabled. Disabled mode must preserve manual keyboard use. Both modes,
preference persistence, and actual phone-to-YouTube typing passed device checks.
Nexus commits: `e0aaee0a`, `a94a2957`. The option was left off after verification.

Delivery retains the previously reported visual deviations: the five rail keys
use the mockup's 80 px rather than the contradictory 80 dp requirement; search
keeps YouTube's native field/UI; destinations other than Home retain native
content layouts. Evidence, command tails and slice commits are recorded in
`E:/Tools/Rokid/tmp/morphe-rokid-prototype/evidence/continuation-report.md`.
