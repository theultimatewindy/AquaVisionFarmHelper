# Aqua Vision is OP

A focused, client-only Fabric farm helper for Minecraft Java Edition 26.2. This completed version 1.2 automates one movement pattern for five-deep test farms:

```text
Attack + A  ->  detect blocked lane end  ->  W transition
Attack + D  ->  detect blocked lane end  ->  W transition
repeat
```

Each F6 start snaps the player's absolute yaw to `90°` and pitch to a level `0°`, then holds that pitch while the pattern runs. The helper can continue while Minecraft is Alt-Tabbed or minimized and sends a native computer alert when monitored crop activity stalls.

The mod does not change movement speed, manage inventory, send custom packets, sell items, teleport, reconnect, or pathfind. Use it only in worlds and on servers where automation is allowed.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3 or a newer compatible 0.19.x release
- Fabric API 0.158.0+26.2
- JDK 25 for development

Minecraft 26.2 is unobfuscated. This project uses Fabric Loom with Minecraft's official class names and does not require Yarn mappings.

## Install

1. Install Fabric Loader for Minecraft 26.2.
2. Put Fabric API 0.158.0+26.2 in the instance's `mods` folder.
3. Remove every older or unfinished release of this helper, including any previous copy named `aqua-vision-is-op-1.2.0.jar`. They share the same internal mod ID and cannot be installed together.
4. Put `aqua-vision-is-op-1.2.0.jar` in the `mods` folder.
5. Start the Fabric 26.2 profile.

Only the normal `.jar` is installable. The `-sources.jar` is provided for inspection and development and must not be placed in the `mods` folder.

## Controls

| Key | Action |
| --- | --- |
| F6 | Start a new session, or stop the current session |
| F7 | Pause or resume without resetting the lane counter |
| F8 | Emergency stop and immediately release Attack, A, D, and W |

The bindings appear under **Options > Controls > Key Binds > Aqua Vision is OP** and can be changed.

F8 has priority over other mod actions and also works while an in-game screen is open. When another desktop application has keyboard focus, Minecraft cannot receive F8; refocus Minecraft first if an emergency stop is needed.

Use the client command `/avop testalert` while connected to a world to request the same desktop alert immediately. It works whether the farming session is running or stopped and is the quickest installation check for the Windows dialog, sound, and taskbar flash.

## Fixed startup orientation

With the default configuration, F6 immediately sets yaw to absolute `90°` and pitch to `0°`. Minecraft yaw `+90°` faces west, toward negative X; pitch `0°` is a level, horizontal view. Both values are captured before the first A/D input is applied, so lane-end math and the orientation guard use the correct direction.

The body and head are aligned for a clean third-person yaw transition. Pitch remains locked at the configured value while the session is actively running, including while Minecraft is in the background. F7 pause or F6/F8 stop releases the camera; F7 resume restores the fixed pitch. Yaw is set at startup but is not continuously steered.

## Background operation

With `runInBackground` enabled, an active session continues while Minecraft is unfocused or minimized:

- a narrow 26.2 client mixin prevents only the automatic focus-loss pause screen while the session is actively farming;
- Attack, A, D, and W remain owned by the controller and are reapplied from stored states each background client tick; and
- normal focus behavior returns immediately on F7 pause, F6 stop, F8, disconnect, failure, or client shutdown.

This does not change or save Minecraft's `pauseOnLostFocus` option, does not capture the real mouse, does not bring Minecraft to the foreground, and does not steal desktop focus. Manually opening chat, inventory, pause, or another screen still pauses and releases inputs by default.

The process must still be running and receiving client ticks. Operating-system sleep/hibernate, a frozen or crashed client, disconnection, or a server-side rejection cannot be bypassed. Minimized rendering may be throttled by Minecraft, but game ticks continue and catch up normally.

## Crop-activity alert

The default 3-second activity monitor is designed around the crop primarily used with this helper. A successful client-observed `NETHER_WART` block break resets its timer. Other crop types can still use the movement pattern, but should set `noWartFailsafeEnabled` to `false` unless their activity is added to the monitor later.

When the timeout is reached on Windows, the mod plays the native warning sound, repeatedly flashes the Minecraft taskbar button until Minecraft is brought forward, and places a topmost Windows warning dialog on the currently active desktop. The dialog remains visible until dismissed and is not kept behind the background Minecraft window. This path uses the Windows session API directly and does not depend on Java's system-tray support or Action Center. Other operating systems retain the Java tray-notification fallback. A red HUD warning also remains visible.

The failsafe warning is deliberately not posted to in-game chat. Automation continues; the first later monitored break clears the HUD warning and rearms one future alert. Paused time does not count. Windows Focus Assist does not suppress the native dialog, although system sound settings can silence its audible component. If every desktop delivery path fails, the error is logged and the HUD warning remains available without stopping the farm session.

## First test setup

1. Use a backup or disposable test world first.
2. Stand at the beginning of a long, five-deep test-farm lane with a solid end that physically blocks sideways movement.
3. Set the intended movement-speed setup. The mod never changes speed.
4. Press F6. Yaw snaps to `90°`, pitch snaps to level `0°`, and the default session starts with Attack + A.
5. After confirming normal movement, Alt-Tab and verify that movement and breaking continue.
6. For the alert test, leave the helper active where no monitored crop can be broken, minimize Minecraft, and wait 3 active seconds for the native Windows dialog, sound, and taskbar flash.
7. Keep a hand near F8 during foreground calibration.

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
```

`PAUSED` preserves the phase and lane. If the player moves farther than `pausePositionTolerance` while paused, resume becomes a safe stop because the saved transition may no longer match the farm. `STOPPED` follows emergency and fail-safe stops. A fresh F6 start resets the lane to 1 and reapplies startup yaw and pitch.

During lateral farming, the combinations are strictly Attack + A or Attack + D, with W released. During a lane transition, only W is held. All four inputs remain released for the configured settle period before the opposite lateral direction starts.

## Lane-end detection

The long sideways section is not timed. At every end-of-client tick, `MovementMonitor` projects X/Z displacement onto the expected local left or right direction. A lane end is confirmed only after projected progress remains below `minimumMovementDelta` for `stuckDetectionTicks` consecutive samples following `laneStartGraceTicks` startup samples.

This debounce prevents a single slow tick, initial acceleration, or a short lag spike from immediately changing lanes. Debug mode shows displacement, projected progress, collision state, the stuck counter, transition timer, and crop-activity timer.

## Configuration

The helper intentionally retains its original internal ID and configuration filename so upgrades keep existing settings:

```text
<game directory>/config/nether-wart-farm-helper.json
```

The file reloads whenever F6 starts a new session. Stop before editing it. On the first load of this completed v1.2 build, an older file is migrated once: its existing settings are retained, the inactivity timeout changes to 3 seconds, the new orientation/background defaults are added, and `configVersion` is set to `3`. Later edits are preserved. Copy fields from `example-config.json` when customization is needed.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `configVersion` | `3` | Internal one-time migration marker; leave this at 3 |
| `startingDirection` | `LEFT` | `LEFT` starts with A; `RIGHT` starts with D |
| `forwardShiftTicks` | `10` | Ticks to hold only W between lanes |
| `transitionSettleTicks` | `2` | Released-input ticks before lateral movement resumes |
| `stuckDetectionTicks` | `8` | Consecutive low-progress samples required for a lane end |
| `minimumMovementDelta` | `0.003` | Minimum expected-direction progress considered movement |
| `laneStartGraceTicks` | `10` | Samples ignored when entering a lane |
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
| `alignYawOnStart` | `true` | Snap yaw when F6 starts a session |
| `startYawDegrees` | `90.0` | Absolute startup yaw, clamped to -180° through +180° |
| `lockPitchWhileRunning` | `true` | Set and continuously hold the configured pitch during an active session |
| `fixedPitchDegrees` | `0.0` | Fixed pitch, clamped to -90° through +90°; 0° is level |
| `runInBackground` | `true` | Keep client automation active while unfocused or minimized |

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
  config/, hud/, keybind/             Config persistence and user interface

src/main/java/dev/winso/netherwarthelper/
  background/                         Focus policy and active-session flag
  config/FarmConfig.java              Validated settings and upgrade defaults
  controller/FarmingDirection.java    Platform-independent direction model
  failsafe/NoWartFailsafeMonitor.java
  movement/DirectionMath.java
  movement/MovementMonitor.java
  orientation/PitchLockState.java     Platform-independent pitch-lock lifecycle

src/test/java/.../                    Background, movement, and failsafe tests
```

## Known limitations

- The movement controller supports only the alternating A/W/D pattern.
- Crop maturity and horizontal aim are not automatically corrected; yaw is aligned only at startup.
- The activity monitor currently recognizes Nether Wart breaks only.
- Multiplayer block-break detection is client-predicted rather than a remote-server acknowledgement.
- A physical blocked end is required for lane transitions.
- The short W transition must be calibrated for the farm and movement setup.
- Long lag can resemble a blocked lane; system sound settings can mute the alert sound, but the Windows dialog remains visible.
- F8 cannot reach Minecraft while another application owns keyboard focus.
- Server rules and anti-cheat policies still apply.

## License

MIT. The supplied FarmHelperV2 and Sunflower artifacts were inspected only for high-level behavior. This Fabric 26.2 implementation is original and does not copy their source.
