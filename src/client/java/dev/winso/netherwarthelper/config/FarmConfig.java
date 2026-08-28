package dev.winso.netherwarthelper.config;

import dev.winso.netherwarthelper.controller.FarmingDirection;

public final class FarmConfig {
	private static final int DEFAULT_FORWARD_SHIFT_TICKS = 10;
	private static final int DEFAULT_TRANSITION_SETTLE_TICKS = 2;
	private static final int DEFAULT_STUCK_DETECTION_TICKS = 8;
	private static final double DEFAULT_MINIMUM_MOVEMENT_DELTA = 0.003;
	private static final int DEFAULT_LANE_START_GRACE_TICKS = 10;
	private static final double DEFAULT_ORIENTATION_TOLERANCE = 12.0;
	private static final double DEFAULT_PAUSE_POSITION_TOLERANCE = 0.35;

	public FarmingDirection startingDirection = FarmingDirection.LEFT;
	public int forwardShiftTicks = DEFAULT_FORWARD_SHIFT_TICKS;
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

	public void validate() {
		if (startingDirection == null) {
			startingDirection = FarmingDirection.LEFT;
		}
		forwardShiftTicks = clamp(forwardShiftTicks, 1, 100);
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
			0.0,
			5.0
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
