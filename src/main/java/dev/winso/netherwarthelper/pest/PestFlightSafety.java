package dev.winso.netherwarthelper.pest;

/** Input-only ground clearance protection; flight permission remains owned by the server. */
public final class PestFlightSafety {
	public static final double MIN_GROUND_CLEARANCE = 1.0;
	public static final double ASCENT_PROBE = 0.2;

	private PestFlightSafety() {
	}

	/**
	 * Include a stopping reserve because permitted flight retains vertical momentum
	 * after Shift is released. Vanilla damps that momentum by 0.6 each tick.
	 */
	public static double groundProbeDistance(double verticalVelocity) {
		if (!Double.isFinite(verticalVelocity)) return MIN_GROUND_CLEARANCE;
		return MIN_GROUND_CLEARANCE + Math.max(0.0, -verticalVelocity) * 3.0;
	}

	/** Final return landing is deliberate; other near-ground phases must not descend into the floor. */
	public static Controls protect(
		boolean nearGround,
		boolean ascentClear,
		boolean allowLanding,
		boolean jumpRequested,
		boolean descendRequested
	) {
		if (allowLanding || !nearGround) return new Controls(jumpRequested, descendRequested);
		// Do not press into a low ceiling. Horizontal movement can still leave the passage.
		return new Controls(ascentClear, false);
	}

	/** While turning toward a distant waypoint, do not descend vertically over the current floor. */
	public static boolean mayDescendToward(
		boolean forwardRequested,
		double horizontalDistance,
		double horizontalArrivalTolerance
	) {
		return forwardRequested || horizontalDistance <= horizontalArrivalTolerance;
	}

	/** Reaching the final breadcrumb is not enough: only descend onto the horizontally aligned lane. */
	public static boolean mayLandAtSavedLane(
		boolean returning, int returnIndex, double horizontalDistance, double positionTolerance
	) {
		return returning && returnIndex <= 0 && horizontalDistance >= 0.0
			&& horizontalDistance <= Math.min(0.25, positionTolerance);
	}

	/** Final descent overrides any collision-generated ascent so vertical inputs cannot cancel out. */
	public static Controls finalDescentControls(boolean onGround, double currentY, double savedY) {
		return new Controls(false,
			PestNavigationMath.shouldContinueFinalDescent(true, onGround, currentY, savedY));
	}

	public record Controls(boolean jump, boolean descend) {
	}
}
