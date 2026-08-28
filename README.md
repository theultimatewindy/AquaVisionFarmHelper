# Nether Wart Farm Helper

A focused, client-only Fabric mod for Minecraft Java Edition 26.2. It automates one movement pattern for a five-deep Nether Wart test farm:

```text
Attack + A  ->  detect blocked lane end  ->  W transition
Attack + D  ->  detect blocked lane end  ->  W transition
repeat
```

The mod does not change movement speed, aim the camera, inspect crops, manage inventory, send packets, sell items, teleport, reconnect, or pathfind. Use it only in worlds and on servers where automation is allowed.

## Requirements

- Minecraft Java Edition 26.2
- JDK 25 for development
- Fabric Loader 0.19.3 or newer compatible 0.19.x release
- Fabric API 0.158.0+26.2
- Gradle 9.5.1 (the included wrapper downloads it)

Minecraft 26.2 is unobfuscated. This project therefore uses the modern `net.fabricmc.fabric-loom` plugin and Minecraft's official class names, with no Yarn mappings dependency.

## Build

From the project directory:

```bash
./gradlew build
```

On Windows PowerShell or Command Prompt:

```powershell
.\gradlew.bat build
```

The runnable mod JAR is generated in `build/libs/`. The `-sources.jar` file is for development and should not be installed as the mod.

To launch a development client:

```bash
./gradlew runClient
```

## Install

1. Install Fabric Loader for Minecraft 26.2.
2. Put Fabric API 0.158.0+26.2 in the instance's `mods` folder.
3. Put `nether-wart-farm-helper-1.0.0.jar` in the same `mods` folder.
4. Start the Fabric 26.2 profile.

This is a client-only mod. A server does not need to install it.

## Controls

| Key | Action |
| --- | --- |
| F6 | Start a new session, or stop the current session |
| F7 | Pause or resume without resetting the lane counter |
| F8 | Emergency stop and immediately force Attack, A, D, and W to released |

All three bindings appear under **Options > Controls > Key Binds > Nether Wart Farm Helper** and can be changed.

F8 has priority over the other actions and is also captured directly inside inventory, chat, pause, and other screens, where ordinary gameplay keybind clicks are suppressed. Stop, pause, disconnect, death or respawn, world/dimension replacement, client shutdown, invalid player/world state, an open GUI, and unexpected controller errors all release the four inputs owned by the mod.

## First test setup

1. Use a backup or disposable test world first.
2. Stand at the beginning of a long, five-deep Nether Wart lane with a solid end that physically blocks sideways movement.
3. Set the intended movement-speed setup (approximately 93 in the environment this farm was designed for). The mod never changes speed.
4. Aim yaw and pitch manually for the farm. The mod records the starting yaw but never rotates the camera.
5. Press F6. The default session begins with Attack + A.
6. Keep a hand near F8 during calibration.

The lane-end detector relies on the player being physically unable to continue sideways. An open-ended lane is not a detectable lane end and the mod will keep moving sideways.

## State machine

The controller uses explicit states instead of one monolithic tick routine:

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

`PAUSED` preserves the active phase and lane. If the player moves farther than `pausePositionTolerance` while paused, resume becomes a fail-safe stop because the saved transition phase may no longer match the physical position. `STOPPED` is used after emergency and fail-safe stops. A fresh F6 start always resets the lane to 1.

During normal farming, the input combinations are strictly Attack + A or Attack + D. W is released. During a lane transition, only W is held and Attack/A/D are released. After the W timer, all controlled inputs remain released for the configured settle period before the opposite lateral direction begins.

## Lane-end detection

The long sideways section is not timed. At every end-of-client tick, `MovementMonitor` compares the current X/Z position with the previous sample and projects that displacement onto the expected local left or right direction. A lane end is confirmed only when projected progress remains below `minimumMovementDelta` for `stuckDetectionTicks` consecutive samples after `laneStartGraceTicks` startup samples.

A single slow tick, initial acceleration, or a short lag spike therefore does not immediately change lanes. Debug mode also shows horizontal displacement, projected progress, the consecutive stuck count, and horizontal collision state.

### Why yaw-relative math matters

Global X increasing does not always mean the A key is making progress. With the starting yaw in radians, the local-left unit vector is:

```text
left = (cos(yaw), sin(yaw)) in the X/Z plane
right = -left
progress = dot(currentPosition - previousPosition, expectedDirection)
```

This works when the player faces north, south, east, west, or an intermediate angle. The optional orientation guard pauses if yaw moves too far from the recorded starting yaw, so the original lane coordinate frame is not silently invalidated.

## Configuration

On first launch, the mod creates:

```text
<game directory>/config/nether-wart-farm-helper.json
```

The file is reloaded whenever a new F6 session starts. Stop the session before editing it. An `example-config.json` is included in the source project.

Default values:

| Setting | Default | Meaning |
| --- | ---: | --- |
| `startingDirection` | `LEFT` | `LEFT` starts with A; `RIGHT` starts with D |
| `forwardShiftTicks` | `10` | Number of ticks to hold only W between lanes |
| `transitionSettleTicks` | `2` | Released-input ticks before lateral farming resumes |
| `stuckDetectionTicks` | `8` | Consecutive low-progress samples required for a lane end |
| `minimumMovementDelta` | `0.003` | Minimum projected blocks-per-tick progress considered movement |
| `laneStartGraceTicks` | `10` | Samples ignored after entering a lane to allow acceleration |
| `holdAttack` | `true` | Hold the vanilla Attack mapping during lateral farming |
| `showHud` | `true` | Show the compact status overlay |
| `showDebugInfo` | `false` | Add movement and timer diagnostics to the overlay |
| `pauseWhenScreenOpen` | `true` | Pause and release inputs when chat, inventory, menus, or another screen opens |
| `orientationGuardEnabled` | `true` | Pause when yaw leaves the starting reference frame |
| `orientationToleranceDegrees` | `12.0` | Maximum absolute wrapped yaw deviation |
| `pausePositionTolerance` | `0.35` | Maximum horizontal movement allowed while paused before resume fails safe |

Invalid numeric values are clamped to safe ranges. If the JSON cannot be parsed, the mod logs the error, uses defaults for that session, and leaves the invalid file untouched so it can be repaired.

## Calibration

Change one value at a time and test with F8 ready.

- If W does not move far enough into the next lane, increase `forwardShiftTicks`.
- If W moves too far, decrease `forwardShiftTicks`.
- If a lane end triggers during a temporary slowdown, increase `stuckDetectionTicks`, increase `laneStartGraceTicks`, or lower `minimumMovementDelta`.
- If a real blocked end takes too long to confirm, decrease `stuckDetectionTicks` or raise `minimumMovementDelta` slightly.
- If W and A/D feel too close together, increase `transitionSettleTicks`.
- If normal camera jitter pauses the helper, raise `orientationToleranceDegrees` carefully or disable the orientation guard.
- Enable `showDebugInfo` to observe `Delta`, `Progress`, `Collision`, `Stuck`, and the transition timer.

`minimumMovementDelta` is a sensitivity threshold: raising it classifies more small movements as "not enough progress"; lowering it classifies more small movements as valid progress.

## HUD

Normal examples:

```text
Farm Helper: OFF

Farm Helper: ON
Lane: 4
Direction: LEFT

Farm Helper: ON
Lane: 4
State: SHIFTING

Farm Helper: PAUSED
Lane: 4
```

Debug mode adds state, X/Z position, horizontal delta, expected-direction progress, collision, stuck counter, and transition timer. Logs are emitted for meaningful state changes only, not every tick.

## Project structure

```text
src/client/java/dev/winso/netherwarthelper/
  NetherWartFarmHelperClient.java     Fabric entrypoint and event wiring
  controller/                         Finite-state machine and direction/state enums
  input/                              Centralized vanilla key-state control
  config/                             JSON model, validation, loading, and saving
  hud/                                Compact and debug HUD extraction
  keybind/                            F6/F7/F8 registration and click handling

src/main/java/dev/winso/netherwarthelper/movement/
  DirectionMath.java                  Yaw-relative X/Z vectors and projection
  MovementMonitor.java                Grace period and consecutive-stuck detection

src/test/java/.../movement/           Projection and debounce unit tests
```

## Known limitations

- Version 1 supports only the alternating A/W/D pattern. It does not verify that the targeted block is Nether Wart or mature.
- It assumes the camera was aimed correctly before F6 and does not correct yaw or pitch.
- It needs a physical blocked end to detect a completed long lane.
- The short W transition is intentionally tick-timed and must be calibrated for the farm and movement setup.
- Lag lasting at least `stuckDetectionTicks` samples can still resemble a blocked end.
- Pausing during a transition is safe only if the player stays within `pausePositionTolerance`; otherwise the session stops.
- Input simulation cannot guarantee permission on a multiplayer server. Server rules and anti-cheat policies still apply.

## License

MIT. The old Forge 1.8.9 FarmHelper artifact supplied as a reference was inspected only for high-level behavioral ideas; this implementation is original Fabric 26.2 code and does not copy its source.
