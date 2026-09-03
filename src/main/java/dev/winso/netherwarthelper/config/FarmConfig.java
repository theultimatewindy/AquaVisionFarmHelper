package dev.winso.netherwarthelper.config;

import dev.winso.netherwarthelper.controller.FarmingDirection;

public final class FarmConfig {
	public static final int CURRENT_CONFIG_VERSION = 10;
	private static final int DEFAULT_FORWARD_SHIFT_TICKS = 2;
	private static final int DEFAULT_FORWARD_STUCK_DETECTION_TICKS = 2;
	private static final int DEFAULT_TRANSITION_SETTLE_TICKS = 0;
	private static final int DEFAULT_STUCK_DETECTION_TICKS = 2;
	private static final double DEFAULT_MINIMUM_MOVEMENT_DELTA = 0.003;
	private static final int DEFAULT_LANE_START_GRACE_TICKS = 2;
	private static final double DEFAULT_ORIENTATION_TOLERANCE = 12.0;
	private static final double DEFAULT_PAUSE_POSITION_TOLERANCE = 0.35;
	private static final int DEFAULT_NO_WART_TIMEOUT_SECONDS = 3;
	private static final double DEFAULT_START_YAW_DEGREES = 90.0;
	private static final double DEFAULT_FIXED_PITCH_DEGREES = 0.0;
	private static final double DEFAULT_VOID_FALL_TRIGGER_DISTANCE = 6.0;
	private static final double DEFAULT_RESPAWN_START_TOLERANCE = 5.0;
	private static final int DEFAULT_RESPAWN_RESTART_DELAY_TICKS = 4 * 20;
	private static final int DEFAULT_PEST_ACTIVATION_THRESHOLD = 3;
	private static final int DEFAULT_PEST_COUNT_NOTIFICATION_THRESHOLD = 3;
	private static final int DEFAULT_PEST_VACUUM_HOTBAR_SLOT = 9;
	private static final int DEFAULT_PEST_SEARCH_TIMEOUT_SECONDS = 20;
	private static final int DEFAULT_PEST_CLEANUP_TIMEOUT_SECONDS = 180;
	private static final double DEFAULT_PEST_CRUISE_HEIGHT = 90.0;

	public int configVersion = CURRENT_CONFIG_VERSION;
	public FarmingDirection startingDirection = FarmingDirection.LEFT;
	public int forwardShiftTicks = DEFAULT_FORWARD_SHIFT_TICKS;
	public int forwardStuckDetectionTicks = DEFAULT_FORWARD_STUCK_DETECTION_TICKS;
	public int transitionSettleTicks = DEFAULT_TRANSITION_SETTLE_TICKS;
	public int stuckDetectionTicks = DEFAULT_STUCK_DETECTION_TICKS;
	public double minimumMovementDelta = DEFAULT_MINIMUM_MOVEMENT_DELTA;
	public int laneStartGraceTicks = DEFAULT_LANE_START_GRACE_TICKS;
	public boolean holdAttack = true;
	public boolean showHud = true;
	public boolean showDebugInfo = false;
	public boolean pauseWhenScreenOpen = true;
	public boolean orientationGuardEnabled = true;
	public double orientationToleranceDegrees = DEFAULT_ORIENTATION_TOLERANCE;
	public double pausePositionTolerance = DEFAULT_PAUSE_POSITION_TOLERANCE;
	public boolean noWartFailsafeEnabled = true;
	public int noWartTimeoutSeconds = DEFAULT_NO_WART_TIMEOUT_SECONDS;
	public boolean noWartDesktopNotification = true;
	public boolean sessionStateDesktopNotification = true;
	public boolean alignYawOnStart = true;
	public double startYawDegrees = DEFAULT_START_YAW_DEGREES;
	public boolean lockPitchWhileRunning = true;
	public double fixedPitchDegrees = DEFAULT_FIXED_PITCH_DEGREES;
	public boolean runInBackground = true;
	public boolean voidLoopEnabled = true;
	public double voidFallTriggerDistance = DEFAULT_VOID_FALL_TRIGGER_DISTANCE;
	public double respawnStartTolerance = DEFAULT_RESPAWN_START_TOLERANCE;
	public int respawnRestartDelayTicks = DEFAULT_RESPAWN_RESTART_DELAY_TICKS;
	public boolean pestAutomationEnabled = false;
	public boolean pestCountDesktopNotification = true;
	public int pestCountNotificationThreshold = DEFAULT_PEST_COUNT_NOTIFICATION_THRESHOLD;
	public int pestActivationThreshold = DEFAULT_PEST_ACTIVATION_THRESHOLD;
	public boolean pestMoveVacuumFromInventory = true;
	/** One-based hotbar slot used by the configuration UI. */
	public int pestVacuumHotbarSlot = DEFAULT_PEST_VACUUM_HOTBAR_SLOT;
	public boolean pestLocatorEnabled = true;
	public int pestSearchTimeoutSeconds = DEFAULT_PEST_SEARCH_TIMEOUT_SECONDS;
	public int pestCleanupTimeoutSeconds = DEFAULT_PEST_CLEANUP_TIMEOUT_SECONDS;
	public double pestCruiseHeight = DEFAULT_PEST_CRUISE_HEIGHT;

	/** Applies one-time defaults that intentionally changed after older releases. */
	public boolean migrateFrom(int sourceConfigVersion) {
		if (sourceConfigVersion >= CURRENT_CONFIG_VERSION) {
			return false;
		}

		if (sourceConfigVersion < 3) {
			noWartTimeoutSeconds = DEFAULT_NO_WART_TIMEOUT_SECONDS;
		}
		if (sourceConfigVersion < 6) {
			respawnRestartDelayTicks = DEFAULT_RESPAWN_RESTART_DELAY_TICKS;
		}
		if (sourceConfigVersion < 7) {
			pestAutomationEnabled = false;
			pestActivationThreshold = DEFAULT_PEST_ACTIVATION_THRESHOLD;
			pestMoveVacuumFromInventory = true;
			pestVacuumHotbarSlot = DEFAULT_PEST_VACUUM_HOTBAR_SLOT;
			pestLocatorEnabled = true;
			pestSearchTimeoutSeconds = DEFAULT_PEST_SEARCH_TIMEOUT_SECONDS;
			pestCleanupTimeoutSeconds = DEFAULT_PEST_CLEANUP_TIMEOUT_SECONDS;
			pestCruiseHeight = DEFAULT_PEST_CRUISE_HEIGHT;
		}
		if (sourceConfigVersion < 8) {
			// Upgrade only the old default timings; retain separately tuned values.
			if (forwardShiftTicks == 10) {
				forwardShiftTicks = DEFAULT_FORWARD_SHIFT_TICKS;
			}
			if (forwardStuckDetectionTicks == 3) {
				forwardStuckDetectionTicks = DEFAULT_FORWARD_STUCK_DETECTION_TICKS;
			}
			if (transitionSettleTicks == 2) {
				transitionSettleTicks = DEFAULT_TRANSITION_SETTLE_TICKS;
			}
			if (stuckDetectionTicks == 8) {
				stuckDetectionTicks = DEFAULT_STUCK_DETECTION_TICKS;
			}
			if (laneStartGraceTicks == 10) {
				laneStartGraceTicks = DEFAULT_LANE_START_GRACE_TICKS;
			}
		}
		if (sourceConfigVersion < 9) {
			pestCountDesktopNotification = true;
		}
		if (sourceConfigVersion < 10) {
			pestCountNotificationThreshold = DEFAULT_PEST_COUNT_NOTIFICATION_THRESHOLD;
		}
		configVersion = CURRENT_CONFIG_VERSION;
		return true;
	}

	public void validate() {
		configVersion = Math.max(configVersion, CURRENT_CONFIG_VERSION);
		if (startingDirection == null) {
			startingDirection = FarmingDirection.LEFT;
		}
		forwardShiftTicks = clamp(forwardShiftTicks, 1, 100);
		forwardStuckDetectionTicks = clamp(forwardStuckDetectionTicks, 1, 20);
		transitionSettleTicks = clamp(transitionSettleTicks, 0, 20);
		stuckDetectionTicks = clamp(stuckDetectionTicks, 2, 100);
		laneStartGraceTicks = clamp(laneStartGraceTicks, 0, 100);
		minimumMovementDelta = finiteOrDefault(minimumMovementDelta, DEFAULT_MINIMUM_MOVEMENT_DELTA, 0.00001, 1.0);
		orientationToleranceDegrees = finiteOrDefault(
			orientationToleranceDegrees,
			DEFAULT_ORIENTATION_TOLERANCE,
			1.0,
			180.0
		);
		pausePositionTolerance = finiteOrDefault(
			pausePositionTolerance,
			DEFAULT_PAUSE_POSITION_TOLERANCE,
			0.15,
			5.0
		);
		noWartTimeoutSeconds = clamp(noWartTimeoutSeconds, 1, 3600);
		startYawDegrees = finiteOrDefault(startYawDegrees, DEFAULT_START_YAW_DEGREES, -180.0, 180.0);
		fixedPitchDegrees = finiteOrDefault(
			fixedPitchDegrees,
			DEFAULT_FIXED_PITCH_DEGREES,
			-90.0,
			90.0
		);
		voidFallTriggerDistance = finiteOrDefault(
			voidFallTriggerDistance,
			DEFAULT_VOID_FALL_TRIGGER_DISTANCE,
			2.0,
			128.0
		);
		respawnStartTolerance = finiteOrDefault(
			respawnStartTolerance,
			DEFAULT_RESPAWN_START_TOLERANCE,
			0.5,
			64.0
		);
		respawnRestartDelayTicks = clamp(respawnRestartDelayTicks, 1, 200);
		pestActivationThreshold = clamp(pestActivationThreshold, 1, 8);
		pestCountNotificationThreshold = clamp(pestCountNotificationThreshold, 1, 8);
		pestVacuumHotbarSlot = clamp(pestVacuumHotbarSlot, 1, 9);
		pestSearchTimeoutSeconds = clamp(pestSearchTimeoutSeconds, 5, 120);
		pestCleanupTimeoutSeconds = clamp(pestCleanupTimeoutSeconds, 30, 900);
		pestCruiseHeight = finiteOrDefault(
			pestCruiseHeight,
			DEFAULT_PEST_CRUISE_HEIGHT,
			60.0,
			200.0
		);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double finiteOrDefault(double value, double fallback, double minimum, double maximum) {
		if (!Double.isFinite(value)) {
			return fallback;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}
}
