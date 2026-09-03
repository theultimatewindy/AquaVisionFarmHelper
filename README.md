# Aqua Vision is OP

A focused, client-only Fabric farm helper for Minecraft Java Edition 26.2. Version 1.3.0 automates one movement pattern for five-deep test farms:

```text
Attack + A  ->  detect blocked lane end  ->  hold W until forward-blocked
Attack + D  ->  detect blocked lane end  ->  hold W until forward-blocked
repeat
```

Each F6 start snaps the player's absolute yaw to `90°` and pitch to a level `0°`, then holds that pitch while the pattern runs. The helper can continue while Minecraft is Alt-Tabbed or minimized, sends a native computer alert when monitored crop activity stalls or the session is interrupted, and can complete a farm-end void/respawn/warp loop automatically. Version 1.3.0 also adds optional Garden pest handling and an in-game configuration screen.

The normal farm pattern does not change movement speed, manage inventory, sell items, teleport, or reconnect. When pest automation is explicitly enabled, it uses vanilla inventory swaps, flight controls, vacuum clicks, HUD data, and reversible waypoint navigation. Use it only in worlds and on servers where automation is allowed.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3 or a newer compatible 0.19.x release
- Fabric API 0.158.0+26.2
- JDK 25 for development

Minecraft 26.2 is unobfuscated. This project uses Fabric Loom with Minecraft's official class names and does not require Yarn mappings.

## Install

1. Install Fabric Loader for Minecraft 26.2.
2. Put Fabric API 0.158.0+26.2 in the instance's `mods` folder.
3. Remove every older release of this helper, including `aqua-vision-is-op-1.2.1.jar`. They share the same internal mod ID and cannot be installed together.
4. Put `aqua-vision-is-op-1.3.0.jar` in the `mods` folder.
5. Start the Fabric 26.2 profile.

Only the normal `.jar` is installable. The `-sources.jar` is provided for inspection and development and must not be placed in the `mods` folder.

## Controls

| Key | Action |
| --- | --- |
| F6 | Start a new session, or stop the current session |
| F7 | Pause or resume without resetting the lane counter |
| F8 | Emergency stop and immediately release every mod-controlled input |

The bindings appear under **Options > Controls > Key Binds > Aqua Vision is OP** and can be changed.

F8 has priority over other mod actions and also works while an in-game screen is open. During void recovery, F6 cancels the session and F7 cancels recovery with a safe stop, including on the death screen. When another desktop application has keyboard focus, Minecraft cannot receive these keys; refocus Minecraft first if a manual stop is needed.

Manual F6, F7, and F8 actions never create a desktop notification. Session-state notifications are reserved for automatic pauses and genuine safety failures.

The `/avop` client commands provide configuration and focused installation checks:

- `/avop` or `/avop config` opens the in-game configuration screen. Stop farming first.
- `/avop pests` prints a fresh, read-only detection report: pest total, enabled toggle, threshold, count source, start status, flight/vacuum availability, and relevant sidebar/tab evidence. It never sends a server command or starts cleanup. Run it while stopped; opening chat during farming still invokes the existing screen-pause protection.
- `/avop testalert` requests the desktop alert immediately.
- `/avop testpestalert` requests a desktop pest alert using the currently configured notification count, without changing the detected count or its one-alert latch.
- `/avop returntest mark` records a stopped player's lane point and manual path; after moving away, `/avop returntest go` runs the real return navigator. `/avop returntest cancel` clears the mark.
- `/avop warp` opens chat prefilled with `/warp garden` and leaves it open so you can press Enter manually.
- `/avop warpsend` opens the same prefilled chat for one second and automatically submits it through the exact path used by void recovery.

Stop the farming session before either warp test. Both warp tests execute the real server command, so use them only where `/warp garden` is safe.

## Fixed startup orientation

With the default configuration, F6 immediately sets yaw to absolute `90°` and pitch to `0°`. Minecraft yaw `+90°` faces west, toward negative X; pitch `0°` is a level, horizontal view. Both values are captured before the first A/D input is applied, so lane-end math and the orientation guard use the correct direction.

The body and head are aligned for a clean third-person yaw transition. Pitch remains locked at the configured value while the session is actively running, including while Minecraft is in the background. F7 pause or F6/F8 stop releases the camera; F7 resume restores the fixed pitch. Yaw is set at startup but is not continuously steered.

## Background operation

With `runInBackground` enabled, an active session—including void recovery and pest cleanup—continues while Minecraft is unfocused or minimized:

- a narrow 26.2 client mixin prevents only the automatic focus-loss pause screen while the session is actively farming;
- farming and pest movement, Attack, and vacuum Use remain owned by the controller and are reapplied from stored states each background client tick;
- continuous Attack remains available after the automatic warp chat releases mouse capture, so a background post-warp restart can use the held tool without requiring Minecraft to be refocused; and
- normal focus behavior returns immediately on F7 pause, F6 stop, F8, disconnect, failure, or client shutdown.

This does not change or save Minecraft's `pauseOnLostFocus` option, does not capture the real mouse, does not bring Minecraft to the foreground, and does not steal desktop focus. Manually opening chat, inventory, pause, or another screen still pauses and releases inputs by default.

The process must still be running and receiving client ticks. Operating-system sleep/hibernate, a frozen or crashed client, disconnection, or a server-side rejection cannot be bypassed. Minimized rendering may be throttled by Minecraft, but game ticks continue and catch up normally.

## Void loop recovery

With `voidLoopEnabled` on, F6 records the farm's starting position and dimension. Downward motion is measured from either reported vertical velocity or actual decreasing Y positions, so a server that reports zero fall velocity can still be recognized. Once the player falls at least `voidFallTriggerDistance` blocks below the recorded height, the helper treats it as the intended farm-end opening: Attack, A, D, and W are released immediately, pitch locking stops, and the crop-inactivity timer is suspended. Independently, five continuous seconds of downward movement authorizes `/warp garden` even if the height threshold, death state, or velocity flag was never reported reliably; interrupting the descent resets this timer.

If a normal void death happens first, the helper confirms Minecraft's `FELL_OUT_OF_WORLD` damage, waits through the death screen, and requests the same vanilla respawn action as the on-screen Respawn button. Otherwise, the five-second continuous-fall path acts while the original player is still alive. In either case, it opens Minecraft's real T-style chat screen prefilled with `/warp garden`, leaves it visible for one second, and submits it through `ChatScreen` exactly as pressing Enter would. This also places the command in Minecraft's recent-chat history. Attack and movement remain released while the server performs the warp.

Some servers rescue or relocate a falling player without presenting a normal vanilla death screen. A grounded returned player or a replacement client player after the intentional fall is therefore also authorized to open and submit `/warp garden`; it no longer stops merely because that server-side return happened away from the recorded farm start.

The controller arms its own restart countdown immediately as it invokes Minecraft's `/warp garden` submission function, so the timer does not depend on later death, respawn, connection-loaded, or recovery-state detection. The HUD visibly counts **RESTARTING IN 4s** down to zero. Four seconds later, movement resumes as soon as a living player and world are present and no screen is open; Minecraft's unreliable `onGround` flag is deliberately not required. This behaves like a fresh F6 start: the helper records the returned position and dimension as the next loop's anchor, reapplies yaw/pitch, resets the pattern to lane 1 and the configured starting direction, rearms the crop monitor, and begins farming again.

A non-void death keeps the existing safe-stop behavior. If no living player and world are available after submission, inputs remain released and the post-warp wait becomes a safe stop after 30 seconds. Disconnecting, F6, F7, F8, or closing Minecraft cancels the pending restart. The automatic fall, death, respawn, chat submission, warp, and restart do not trigger an interruption alert.

## Crop-activity alert

The default 3-second activity monitor is designed around the crop primarily used with this helper. A successful client-observed `NETHER_WART` block break resets its timer. Other crop types can still use the movement pattern, but should set `noWartFailsafeEnabled` to `false` unless their activity is added to the monitor later.

When the timeout is reached on Windows, the mod plays the native warning sound, repeatedly flashes the Minecraft taskbar button until Minecraft is brought forward, and places a topmost Windows warning dialog on the currently active desktop. The dialog remains visible until dismissed and is not kept behind the background Minecraft window. This path uses the Windows session API directly and does not depend on Java's system-tray support or Action Center. Other operating systems retain the Java tray-notification fallback. A red HUD warning also remains visible.

The failsafe warning is deliberately not posted to in-game chat. Automation continues; the first later monitored break clears the HUD warning and rearms one future alert. Paused time and intentional void recovery do not count. Windows Focus Assist does not suppress the native dialog, although system sound settings can silence its audible component. If every desktop delivery path fails, the error is logged and the HUD warning remains available without stopping the farm session.

The same native desktop path is used for automatic protection events such as an unexpected screen pause, orientation protection, disconnect, non-void death, recovery failure, or timeout. Manual F6/F7/F8 actions and normal Minecraft shutdown are silent. Set `sessionStateDesktopNotification` to `false` to disable these automatic interruption alerts.

## Garden pest automation

Pest automation is **off by default**. Stop farming, run `/avop config`, open the **Pests** page, and enable it only after testing in a safe Garden setup. The cleanup activation count and desktop-notification count are separate settings from 1 through 8. The **Pest alert** option is on by default and **Alert at** defaults to three: it displays the same topmost native Windows dialog used by the failsafes once when the confirmed count reaches the selected amount or more. It does not repeat while the count stays high; a fresh confirmed count below the selected amount rearms it. Unknown HUD data does not rearm or trigger the alert.

The helper reads the Garden pest total once per second, including while farming is stopped. It reads the actual visible sidebar (including a team-colored sidebar), removes formatting/invisible decorations, and accepts `x4` or `4x` on the exact Garden location line without requiring one particular pest icon. The older recognized pest-icon plus plain-number format also remains supported. Garden location can be established by the sidebar or an exact `Area: Garden` tab row. An explicit `Pests:` or `Total Pests:` tab-list total can also be read in that Garden context. An empty `Pests:` heading, `Pest Traps: 0/3`, plot IDs, captured pests in the Vacuum Bag, and kill statistics are not Garden totals. Missing, malformed, or conflicting totals remain **unknown**, never an invented zero.

The HUD shows `Pests: 8 | threshold 3 | auto ON` (using your actual values), even before F6. The helper requires two fresh HUD polls at or above the configured threshold before interrupting a normal left/right lane. Lane-end and W-shift transitions no longer erase that confirmation; cleanup begins at the next safe A/D lane. Manual pause/stop, disabling the feature, void recovery, or changing worlds requires fresh confirmation. The lane number, direction, exact position, yaw, pitch, selected hotbar slot, and flight state are saved. Farming keys and the crop-inactivity timer are then suspended without ending background operation.

For this 1.3.0 screenshot-based detection fix, first stop farming and run `/avop pests` in the Garden. If the sidebar says `The Garden [icon] x4` and `Plot - 7 [icon] x2`, the report should say **four total pests**, not two, with `Garden: true`; tab `Plots: 5, 7` should supply those two infested plot IDs. Check that automation is ON with a threshold of three. If it is OFF, enable it on the Pests page and save. Then close chat and press F6. If the count is still unknown or incorrect, capture the diagnostic output and the visible Garden sidebar/tab display. The report now includes an ASCII-escaped Garden line so icon characters remain identifiable even when a system log cannot store them. Debug HUD mode also shows the current start/wait reason. Regression tests cover the reported formatting, four-versus-two distinction, and activation threshold; live cleanup/navigation still needs an in-game check.

The cleanup requires a grounded farming-lane start, Garden flight permission, and a vacuum somewhere in the 36-slot player inventory. It selects the strongest eligible recognized vacuum. If the vacuum is outside the hotbar and **Move vacuum from inventory** is enabled, the helper swaps it into the configured hotbar slot using Minecraft's normal inventory action and reverses the swap afterward. Both slots are fingerprint-checked before use and restoration so an unexpected inventory change produces a safe warning instead of a blind swap. Known vacuum ranges from Skymart through Hooverius are respected; an unknown item whose name contains `Vacuum` uses a conservative five-block range.

The 1.3.0 flight fix separates preparation from takeoff: after the vacuum is ready, the player jumps off the ground, requests flight only while airborne and permitted, and waits for flight to remain active before navigating. Minecraft clears flight when grounded, so setting it during the earlier preparation wait was not sufficient. New Jump presses are spaced outside Minecraft's double-tap window, preventing small altitude adjustments from toggling flight off. Flight loss temporarily releases navigation and vacuum use while the helper recovers; blocked takeoff times out after three seconds, requests are limited to three per attempt, and repeated flight loss stops safely after three recoveries. The final return can land at the saved lane without triggering another takeoff. This does not grant flight permission, change flying speed, or bypass a server rejection. `/avop pests` includes the current flying/grounded flags, takeoff state, and floor-clearance status for diagnosis.

The follow-up flight regression fix keeps the smoother camera but prevents descent while turning toward a distant low pest. Target navigation aims the player's feet one block above the pest body instead of treating its body center as a landing spot. During flight, a block-collision probe checks at least one block below the player plus a reserve for downward momentum: nearby ground cancels descent and requests ascent when there is overhead room, or holds vertical inputs neutral in a low ceiling. This protection also applies while hovering, reading the locator, vacuuming, and following return waypoints; the deliberate final landing at the saved lane is exempt. Flight-loss logs now record the phase, grounded/flight flags, vertical speed, vertical inputs, and target so any remaining server-specific failure can be diagnosed.

Loaded pests are recognized by pairing their Garden head textures with the nearby Bat or Silverfish body used by the server, then approached with a line-of-sight check. Brief one-frame head/body pairing gaps retain the same living body for at most half a second instead of abandoning it and restarting the locator; right-click remains disabled until the detector confirms it again. The aim point follows the living body every tick. Near the outer edge of vacuum range, the helper can keep moving forward while holding vanilla right-click, then brakes at a smaller inner standoff. Separate start/stop distance and camera-angle bands keep short pest movements from repeatedly tapping W. Toggle-mode Attack, Use, and Sneak mappings are reconciled to an absolute state. Pest automation deliberately leaves the Sprint key completely unmanaged, so a vanilla sprint preference or AutoSprint mod remains the sole sprint owner instead of fighting the helper every tick. Use still requires a fresh detection, reliable range, line of sight, and tight yaw/pitch alignment. Camera turns remain smooth and normal manual camera controls are unchanged.

When no pest is loaded, the enabled vacuum locator sends **one left-click**, releases Attack, and collects the angry-villager particle trail. It waits at least half a second and for a short quiet period in the trail, with a two-second capture limit; at least three distinct points spanning one block are required. It then follows the waypoint to within about two blocks without clicking again during that flight. New locator requests are spaced at least **four seconds apart**, including after a target disappears or a search phase changes. The helper also waits for vanilla's click cooldown and for right-click use to be released; it does not hold left-click or bypass those checks.

After a pest disappears, the old target and trail are cleared. Another already-loaded pest is selected directly; otherwise a fresh locator search starts after releasing the vacuum. The initial outbound route again uses the reported plot and configured cruise height, as in the previously working flight build. Missing trails or an expired search fall back to the reported infested plots and the Garden's 25-plot geometry. Locator travel is kept at or above that cruise height; approaching a recognized loaded pest can descend separately with floor protection. Flight waypoints are recorded on the outbound route. `/avop pests` includes the locator stage, click count, cooldown, captured point count, and target name for diagnosis.

Cleanup completes after two fresh explicit-zero polls with no recognized live pest. The server can instead remove the Garden total row entirely after the last kill. This is now handled by a separate guarded completion check: the most recent authoritative total must be one, the actually vacuumed body must become dead/removed shortly after use, and the player must remain nearby. The helper then hovers while requiring **five fresh counter-free Garden HUD polls** with no loaded pests, a populated normal sidebar, confirmed `Area: Garden`, and no positive/malformed pest or infested-plot evidence. A blank `Pests:` heading, missing sidebar/tab data, an unknown count alone, or simply failing to find a distant pest never means zero. Head and body removal arriving on separate ticks is supported. Reappearing targets, conflicting data, or a persistent positive total cancel the check. Confirmation is bounded to ten seconds and shown as **CONFIRMING_CLEAR**; the ordinary raw count parser still leaves omitted values unknown.

Once cleared, completion stays latched while the helper returns to the saved lane position, restores the previous item and flight state, and resumes the same direction and lane. This return-navigation fix skips old chase detours only when the player's full body corridor is clear of blocks in loaded chunks; otherwise the recorded reverse waypoints remain. At the start of each leg, horizontal input waits until the smooth camera turn is within four degrees of the collision-checked heading. Long, clear return legs then hold W continuously at the player's natural flight speed instead of speed-limiting with repeated W/coast pulses. Predictive forward/backward/strafe braking is reserved for waypoint approaches and exact final alignment, while Sprint remains under Minecraft or the user's AutoSprint mod. Directional key states are only written when their requested state actually changes, apart from a necessary reassertion if focus handling clears a held key. Each leg keeps a stable camera heading; near the final point, it smoothly faces the saved farming direction instead of repeatedly turning toward tiny position errors. The final target uses a small collision-tested inset away from the farming view when possible. Final descent and handoff require both quarter-block horizontal alignment (or the configured tighter tolerance) and settled horizontal momentum. Background key ownership includes the directional braking inputs but never Sprint.

The return has its own **60–300-second timeout**, sized from the recorded route length, rather than sharing the nearly expired cleanup deadline. A grounded position 14/16 of a block above the recorded foot height is the raised soul-sand crop edge, not a completed lane return. The final check now requires the saved floor height within one eighth of a block. If the player catches the raised edge, the helper keeps return mode active, re-takes flight, crosses a collision-tested lane inset, and descends again instead of attempting a high-speed precision walk or resuming there. A return failure is reported as a return failure, not as uncleared pests. `/avop pests` shows the return waypoint, remaining horizontal distance, height error, speed, and elapsed/budget time; the local log records return progress once per second. The three-second crop monitor is rearmed only after farming resumes. Missing vacuum, unavailable flight, navigation timeout, death, unexpected world changes, or failure to reach the saved position releases inputs and uses the normal desktop fail-safe alert. If cleanup ends in mid-air away from the saved lane and flight permission remains available, the helper retains flight and stops motion; land manually after the warning. It cannot prevent a fall if the server revokes or rejects flight. Manual F6/F7/F8 cancellation remains free of desktop alerts.

To test the return without waiting for pests, stop farming and stand at the desired lane point. Run `/avop returntest mark`, close chat, and manually move or fly at least two blocks away along a safe path. Run `/avop returntest go`; it uses the production flight, route selection, braking, collision-safe lane inset, landing, and state-restoration path and reports pass/fail in chat. A pass requires real ground contact at the recorded floor height; landing on the raised soul-sand edge cannot pass. Keep F8 available. The marker is tied to the current player and world and is discarded if either changes.

Server HUD text, particle behavior, plot loading, and collision layouts can change independently of this mod. Keep F8 available and watch a cleanup with at least two pests, including a low pest: the player should stay airborne while turning, then approach and vacuum without landing, search for the next pest, and finally return to the saved lane. This remains version **1.3.0**; camera smoothing, single locator clicks, lane timing, configuration, background farming, and void-loop behavior are retained. Automated tests and a build do not replace this live in-game check.

## First test setup

1. Use a backup or disposable test world first.
2. Stand at the beginning of a long, five-deep test-farm lane with a solid end that physically blocks sideways movement.
3. Set the intended movement-speed setup. The mod never changes speed.
4. Press F6. Yaw snaps to `90°`, pitch snaps to level `0°`, and the default session starts with Attack + A.
5. After confirming normal movement, Alt-Tab and verify that movement and breaking continue.
6. For the alert test, leave the helper active where no monitored crop can be broken, minimize Minecraft, and wait 3 active seconds for the native Windows dialog, sound, and taskbar flash.
7. Stop the helper, run `/avop warp`, verify that `/warp garden` appears in chat, and press Enter manually. This proves whether the server accepts the command.
8. Run `/avop warpsend` and verify that the same chat box remains visible for one second before the command submits automatically. This proves the exact automatic submission path separately from fall detection.
9. Build the void opening only in the disposable test world and confirm that `/warp garden` places the player at the intended starting spawn.
10. Run the full lane, watch the HUD change to **VOID LOOP**, and verify that every controlled input stays released once early recovery begins.
11. While Minecraft remains Alt-Tabbed, confirm that the actual Minecraft chat box appears with `/warp garden` after five continuous falling seconds, remains visible for one second, submits it once, and records it in recent chat. Then watch the HUD count **RESTARTING IN 4s** to zero and confirm that the returned position is recorded as the new start, yaw/pitch realign, the HUD returns to lane 1, and both held-tool Attack and the configured starting direction resume without refocusing Minecraft.
12. Keep a hand near F8 during foreground calibration and repeat the loop once before leaving it unattended.

The lane-end detector relies on the player being physically blocked. An open-ended lane is not a detectable lane end and the helper will keep moving sideways.

## State machine

```text
IDLE
  -> FARM_LEFT
  -> END_LEFT_DETECTED
  -> SHIFT_FORWARD_AFTER_LEFT
  -> FARM_RIGHT
  -> END_RIGHT_DETECTED
  -> SHIFT_FORWARD_AFTER_RIGHT
  -> FARM_LEFT ...

any active farming phase
  -> PEST_CLEANUP
  -> same FARM_LEFT or FARM_RIGHT state and lane

any active farming phase
  -> VOID_FALLING
  -> WAITING_FOR_RESPAWN
  -> WAITING_TO_WARP
  -> WAITING_TO_RESTART
  -> FARM_LEFT or FARM_RIGHT (lane 1)
```

`PEST_CLEANUP` preserves the farming phase and lane while the pest controller temporarily owns movement and Use. `PAUSED` preserves the normal farming phase and lane. If the player moves farther than `pausePositionTolerance` while paused, resume becomes a safe stop because the saved transition may no longer match the farm. Pest and void recovery are deliberately not pausable: a manual F6/F7/F8 cancels the current session without a desktop notification. `STOPPED` follows emergency and fail-safe stops. A fresh F6 start or the four-second post-warp restart resets the lane to 1, records the current return point, and reapplies startup yaw and pitch.

During lateral farming, the combinations are strictly Attack + A or Attack + D, with W released. During a lane transition, only W is held until forward progress remains blocked for the configured confirmation samples. W takes over in the same tick the sideways wall is confirmed; the opposite A/D direction and Attack take over in the same tick the forward wall is confirmed when the default zero settling delay is used. A customized nonzero settling delay is still respected.

## Lane-end detection

Neither the long sideways section nor the short W section has a fixed stop time. At every end-of-client tick, `MovementMonitor` projects X/Z displacement onto the commanded local direction and checks for a solid collision shape just ahead in that direction. A lateral lane end is confirmed only after projected progress remains below `minimumMovementDelta` against that obstruction for `stuckDetectionTicks` consecutive samples following `laneStartGraceTicks` startup samples. Afterward W continues for at least `forwardShiftTicks` grace samples and remains held until `forwardStuckDetectionTicks` consecutive blocked forward samples confirm the small lane's physical end. A wall still touching the player's side does not count as a forward wall.

The faster 1.3.0 defaults use two confirmation samples (about 0.1 seconds at 20 client ticks per second), two startup grace samples, and no extra settling pause. This replaces the old eight-sample sideways confirmation, ten-sample grace periods, and additional idle handoff ticks. The remaining confirmation prevents a single slow tick from immediately changing lanes. Debug mode shows displacement, projected progress, collision state, the stuck counter, transition timer, and crop-activity timer.

## Configuration

The helper intentionally retains its original internal ID and configuration filename so upgrades keep existing settings:

```text
<game directory>/config/nether-wart-farm-helper.json
```

The file reloads whenever F6 starts a new session. `/avop config` safely edits the common options and writes the file atomically; advanced timings remain available in JSON. This 1.3.0 bug-fix release migrates the internal `configVersion` to 10 to add the separate configurable pest-notification count. Existing movement, timing, notification toggle, and pest-automation choices are preserved; an upgraded version-9 file receives the default alert count of three. Files older than schema 7 also receive pest settings with automation disabled. Configurations older than schema 3 still receive the intended one-time 3-second timeout migration. Later edits are preserved. Copy fields from `example-config.json` when customization is needed.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `configVersion` | `10` | Internal one-time migration marker; leave this at 10 |
| `startingDirection` | `LEFT` | `LEFT` starts with A; `RIGHT` starts with D |
| `forwardShiftTicks` | `2` | Minimum W grace samples before forward-end detection begins; not a fixed W duration |
| `forwardStuckDetectionTicks` | `2` | Consecutive blocked forward no-progress samples required before changing direction |
| `transitionSettleTicks` | `0` | Additional released-input ticks before lateral movement resumes |
| `stuckDetectionTicks` | `2` | Consecutive low-progress samples against a directional wall required for a lane end |
| `minimumMovementDelta` | `0.003` | Minimum expected-direction progress considered movement |
| `laneStartGraceTicks` | `2` | Samples ignored when entering a lane |
| `holdAttack` | `true` | Hold the vanilla Attack mapping during lateral farming |
| `showHud` | `true` | Show the compact status overlay |
| `showDebugInfo` | `false` | Add movement, transition, and activity diagnostics |
| `pauseWhenScreenOpen` | `true` | Pause when an in-game GUI opens |
| `orientationGuardEnabled` | `true` | Pause when yaw leaves the startup reference frame |
| `orientationToleranceDegrees` | `12.0` | Maximum wrapped yaw deviation |
| `pausePositionTolerance` | `0.35` | Maximum horizontal movement allowed while paused |
| `noWartFailsafeEnabled` | `true` | Enable the currently Nether-Wart-based activity monitor |
| `noWartTimeoutSeconds` | `3` | Active seconds without a monitored break before alerting |
| `noWartDesktopNotification` | `true` | Enable the native desktop alert and window-attention request |
| `sessionStateDesktopNotification` | `true` | Alert for automatic safety pauses, disconnects, failures, and timeouts; manual F6/F7/F8 stay silent |
| `alignYawOnStart` | `true` | Snap yaw when F6 starts a session |
| `startYawDegrees` | `90.0` | Absolute startup yaw, clamped to -180° through +180° |
| `lockPitchWhileRunning` | `true` | Set and continuously hold the configured pitch during an active session |
| `fixedPitchDegrees` | `0.0` | Fixed pitch, clamped to -90° through +90°; 0° is level |
| `runInBackground` | `true` | Keep client automation active while unfocused or minimized |
| `voidLoopEnabled` | `true` | Release inputs, send `/warp garden` after five continuous falling seconds or the death/return fallback, and restart four seconds after submission |
| `voidFallTriggerDistance` | `6.0` | Downward distance from the recorded F6 height that begins safe void recovery |
| `respawnStartTolerance` | `5.0` | Legacy compatibility value; the returned position is now recorded as the fresh anchor |
| `respawnRestartDelayTicks` | `80` | Ticks after actual `/warp garden` submission before farming resumes; 80 ticks is four seconds |
| `pestAutomationEnabled` | `false` | Interrupt a lateral farming lane when the confirmed Garden pest threshold is reached |
| `pestCountDesktopNotification` | `true` | Show one native desktop alert when a fresh Garden count reaches the notification threshold |
| `pestCountNotificationThreshold` | `3` | Pest count required for the independent desktop alert, from 1 through 8; rearm below this value |
| `pestActivationThreshold` | `3` | Confirmed Garden pest count required to begin cleanup, from 1 through 8 |
| `pestMoveVacuumFromInventory` | `true` | Temporarily swap an inventory vacuum into the configured hotbar slot |
| `pestVacuumHotbarSlot` | `9` | One-based hotbar slot used for a temporary vacuum swap |
| `pestLocatorEnabled` | `true` | Use the vacuum's locator click and particle trail when a pest is not loaded |
| `pestSearchTimeoutSeconds` | `20` | Search time before choosing another known plot or retrying the locator |
| `pestCleanupTimeoutSeconds` | `180` | Maximum time without an authoritative count reduction; progress restarts it, with a 15-minute hard cap; return has a separate budget |
| `pestCruiseHeight` | `90.0` | Minimum Y level used for long plot-to-plot flights |

Invalid numeric values are clamped. If JSON parsing fails, the mod logs the error, uses defaults for that session, and leaves the invalid file untouched.

## Build

```powershell
.\gradlew.bat build
```

The installable and source JARs are generated in `build/libs/`. To launch a development client:

```powershell
.\gradlew.bat runClient
```

## Project structure

```text
src/client/java/dev/winso/netherwarthelper/
  NetherWartFarmHelperClient.java     Event wiring
  controller/                         Farming state machine
  input/                              Stored and reapplied vanilla key states
  mixin/                              Narrow focus-loss pause override
  notification/                       Native system notification and window attention
  orientation/                        Per-frame active-session pitch lock
  pest/                               Garden HUD parsing, pest recognition, vacuum control, and return navigation
  config/, hud/, keybind/             Config persistence and user interface

src/main/java/dev/winso/netherwarthelper/
  background/                         Focus policy and active-session flag
  config/FarmConfig.java              Validated settings and upgrade defaults
  controller/FarmingDirection.java    Platform-independent direction model
  failsafe/NoWartFailsafeMonitor.java
  movement/DirectionMath.java
  movement/MovementMonitor.java
  pest/                               Tested parsers, plot geometry, vacuum ranges, and navigation math
  orientation/PitchLockState.java     Platform-independent pitch-lock lifecycle
  recovery/VoidLoopRecovery.java      Void fall, respawn, validation, and settle policy

src/test/java/.../                    Background, movement, failsafe, and recovery tests
```

## Known limitations

- The movement controller supports only the alternating A/W/D pattern.
- Crop maturity and horizontal aim are not automatically corrected; yaw is aligned only at startup.
- The activity monitor currently recognizes Nether Wart breaks only.
- Pest navigation requires Garden flight permission, a recognized inventory item containing `Vacuum`, the expected Garden sidebar/tab text, and the current 25-plot coordinate layout.
- The pest controller uses reversible flight waypoints rather than a full block-by-block pathfinder. Unexpected buildings, closed plot borders, server corrections, unloaded pests, or changed locator particles can cause a timed safe stop instead of a completed cycle.
- Multiplayer block-break detection is client-predicted rather than a remote-server acknowledgement.
- A physical blocked end is required for lane transitions.
- The short W transition requires a physical blocked end; an open forward path keeps W held.
- Long lag can resemble a blocked lane; system sound settings can mute the alert sound, but the Windows dialog remains visible.
- Automatic restart trusts the living, screen-free location present four seconds after the `/warp garden` send call as the next farming anchor. It intentionally does not trust `onGround`; verify the server destination before leaving the loop unattended. Spectator returns never restart.
- The early input-release trigger assumes the farm is mostly level. The five-second fallback also assumes a normal farm route cannot descend continuously for that long.
- F8 cannot reach Minecraft while another application owns keyboard focus.
- Server rules and anti-cheat policies still apply.

## License

MIT. The supplied FarmHelperV2 and Sunflower artifacts were inspected only for high-level behavior. This Fabric 26.2 implementation is original and does not copy their source.
