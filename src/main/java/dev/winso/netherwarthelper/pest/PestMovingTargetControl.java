package dev.winso.netherwarthelper.pest;

/** Ordinary input policy for pursuing a live pest while vacuum use is independently gated. */
public final class PestMovingTargetControl {
	private static final double USE_RANGE_MARGIN = 0.35;
	private static final double MINIMUM_USE_RANGE = 2.5;
	private static final double MINIMUM_STANDOFF = 2.5;
	private static final double OBSTRUCTED_APPROACH_DISTANCE = 1.25;
	private static final double APPROACH_START_MARGIN = 0.60;
	private static final double OBSTRUCTED_APPROACH_START_DISTANCE = 1.85;
	private static final double HEADING_START_TOLERANCE = 30.0;
	private static final double HEADING_STOP_TOLERANCE = 50.0;
	private static final double SPRINT_DISTANCE = 6.0;
	private static final double VERTICAL_TOLERANCE = 0.8;

	private PestMovingTargetControl() {
	}

	/**
	 * movementAligned is the wider forward-motion heading gate; vacuumAligned is the
	 * tighter yaw-and-pitch gate. A recently retained target may still be pursued, but
	 * only a fresh detector observation may hold right-click.
	 */
	public static Controls decide(
		double distance,
		double horizontalDistance,
		double vacuumRange,
		boolean lineOfSight,
		boolean movementAligned,
		boolean vacuumAligned,
		boolean targetFresh,
		boolean forwardWasHeld,
		double deltaY,
		boolean horizontalCollision
	) {
		requireFinite(distance, horizontalDistance, vacuumRange, deltaY);
		if (distance < 0.0 || horizontalDistance < 0.0 || vacuumRange <= 0.0) {
			throw new IllegalArgumentException("Distances must be non-negative and vacuum range positive");
		}

		double useRange = reliableUseRange(vacuumRange);
		double standoff = innerStandoff(vacuumRange);
		boolean use = targetFresh && lineOfSight && vacuumAligned && distance <= useRange;
		double visibleApproachThreshold = standoff + (forwardWasHeld ? 0.0 : APPROACH_START_MARGIN);
		double obstructedApproachThreshold = forwardWasHeld
			? OBSTRUCTED_APPROACH_DISTANCE : OBSTRUCTED_APPROACH_START_DISTANCE;
		boolean needsApproach = horizontalDistance > visibleApproachThreshold
			|| (!lineOfSight && horizontalDistance > obstructedApproachThreshold);
		boolean forward = movementAligned && needsApproach;
		boolean sprint = forward && horizontalDistance > SPRINT_DISTANCE && distance > useRange;
		boolean jump = horizontalCollision || deltaY > VERTICAL_TOLERANCE;
		boolean descend = !horizontalCollision && deltaY < -VERTICAL_TOLERANCE
			&& PestFlightSafety.mayDescendToward(forward, horizontalDistance, standoff);
		return new Controls(use, forward, sprint, jump, descend);
	}

	/** Wider stop angle keeps a smoothly turning chase from tapping W at the alignment boundary. */
	public static boolean isMovementHeadingAligned(double currentYaw, double targetYaw, boolean forwardWasHeld) {
		double tolerance = forwardWasHeld ? HEADING_STOP_TOLERANCE : HEADING_START_TOLERANCE;
		return PestNavigationMath.isHeadingAligned(currentYaw, targetYaw, tolerance);
	}

	public static double reliableUseRange(double vacuumRange) {
		if (!Double.isFinite(vacuumRange) || vacuumRange <= 0.0) {
			throw new IllegalArgumentException("Vacuum range must be finite and positive");
		}
		return Math.max(MINIMUM_USE_RANGE, vacuumRange - USE_RANGE_MARGIN);
	}

	public static double innerStandoff(double vacuumRange) {
		if (!Double.isFinite(vacuumRange) || vacuumRange <= 0.0) {
			throw new IllegalArgumentException("Vacuum range must be finite and positive");
		}
		return Math.max(MINIMUM_STANDOFF, vacuumRange - 2.0);
	}

	private static void requireFinite(double... values) {
		for (double value : values) {
			if (!Double.isFinite(value)) {
				throw new IllegalArgumentException("Moving-target control inputs must be finite");
			}
		}
	}

	public record Controls(boolean use, boolean forward, boolean sprint, boolean jump, boolean descend) {
	}
}
