package dev.winso.netherwarthelper.pest;

/**
 * Coordinate and angle helpers shared by pest targeting and return navigation.
 */
public final class PestNavigationMath {
	public static final double FINAL_DESCENT_HORIZONTAL_CORRIDOR = 1.25;
	public static final double FINAL_DESCENT_FLOOR_MARGIN = 0.125;
	public static final double FINAL_LANDING_VERTICAL_TOLERANCE = 0.125;

	private PestNavigationMath() {
	}

	public static boolean isAtTrailWaypoint(double horizontalDistance, double verticalDistance) {
		return horizontalDistance >= 0.0 && horizontalDistance <= 2.0
			&& verticalDistance >= 0.0 && verticalDistance <= 2.5;
	}

	/** Prevent forward flight while the smoothly turning camera is still facing away. */
	public static boolean isHeadingAligned(double currentYaw, double targetYaw, double toleranceDegrees) {
		return Math.abs(wrapDegrees(targetYaw - currentYaw)) <= toleranceDegrees;
	}

	/** Only the small, same-height final landing area may finish by walking instead of taking off again. */
	public static boolean canFinishReturnOnGround(boolean onGround, double horizontalDistance, double verticalDistance) {
		return onGround && horizontalDistance >= 0.0 && horizontalDistance <= FINAL_DESCENT_HORIZONTAL_CORRIDOR
			&& isWithinFinalReturnHeight(true, verticalDistance);
	}

	/** A return is complete only after the aligned player has made real ground contact. */
	public static boolean hasLandedAtSavedLane(
		boolean horizontallySettled,
		boolean onGround,
		double horizontalDistance,
		double verticalDistance
	) {
		return horizontallySettled && canFinishReturnOnGround(onGround, horizontalDistance, verticalDistance);
	}

	/**
	 * Latch the vertical landing once tight horizontal braking succeeds. Small flight drift must not
	 * bounce the controller back into ascent; leaving the wider lane corridor safely releases it.
	 */
	public static boolean nextFinalDescentCommitted(
		boolean currentlyCommitted,
		boolean horizontallySettled,
		double horizontalDistance
	) {
		if (!insideFinalDescentCorridor(horizontalDistance)) return false;
		return currentlyCommitted || horizontallySettled;
	}

	public static boolean insideFinalDescentCorridor(double horizontalDistance) {
		return Double.isFinite(horizontalDistance) && horizontalDistance >= 0.0
			&& horizontalDistance <= FINAL_DESCENT_HORIZONTAL_CORRIDOR;
	}

	/**
	 * Once horizontal braking has settled over the saved lane, keep descending through the old
	 * airborne completion band. Stop below the recorded foot height instead of blindly descending
	 * if the expected landing surface is no longer present.
	 */
	public static boolean shouldContinueFinalDescent(
		boolean finalDescentCommitted,
		boolean onGround,
		double currentY,
		double savedY
	) {
		return finalDescentCommitted && !onGround && Double.isFinite(currentY) && Double.isFinite(savedY)
			&& currentY >= savedY - FINAL_DESCENT_FLOOR_MARGIN;
	}

	public static boolean passedFinalDescentFloor(double currentY, double savedY) {
		return Double.isFinite(currentY) && Double.isFinite(savedY)
			&& currentY < savedY - FINAL_DESCENT_FLOOR_MARGIN;
	}

	public static boolean isWithinFinalReturnHeight(boolean onGround, double verticalDistance) {
		// Grounded 14/16 above the saved foot height means the player caught the raised crop bed.
		return onGround && verticalDistance >= 0.0
			&& verticalDistance <= FINAL_LANDING_VERTICAL_TOLERANCE;
	}

	/** Ground contact or confirmed active flight is required before skipping flight recovery. */
	public static boolean canHandleFinalReturnWithoutFlightRecovery(
		boolean finalDescentCommitted,
		boolean onGround,
		boolean flying,
		boolean atSavedLane,
		boolean canFinishOnGround
	) {
		return atSavedLane || canFinishOnGround || (finalDescentCommitted && (onGround || flying));
	}

	/**
	 * Calculates Minecraft yaw and pitch from one world-space point to another.
	 * Yaw zero faces positive Z; pitch is negative when looking upward.
	 */
	public static AimAngles aimAt(
		double fromX,
		double fromY,
		double fromZ,
		double targetX,
		double targetY,
		double targetZ
	) {
		double deltaX = targetX - fromX;
		double deltaY = targetY - fromY;
		double deltaZ = targetZ - fromZ;
		double horizontalDistance = Math.hypot(deltaX, deltaZ);
		double yaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
		double pitch = -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));
		return new AimAngles(yaw, pitch);
	}

	public static double distanceSquared(
		double firstX,
		double firstY,
		double firstZ,
		double secondX,
		double secondY,
		double secondZ
	) {
		double deltaX = secondX - firstX;
		double deltaY = secondY - firstY;
		double deltaZ = secondZ - firstZ;
		return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
	}

	public static double horizontalDistanceSquared(
		double firstX,
		double firstZ,
		double secondX,
		double secondZ
	) {
		double deltaX = secondX - firstX;
		double deltaZ = secondZ - firstZ;
		return deltaX * deltaX + deltaZ * deltaZ;
	}

	public static boolean isWithinDistance(
		double firstX,
		double firstY,
		double firstZ,
		double secondX,
		double secondY,
		double secondZ,
		double maximumDistance
	) {
		validateMaximumDistance(maximumDistance);
		return distanceSquared(firstX, firstY, firstZ, secondX, secondY, secondZ)
			<= maximumDistance * maximumDistance;
	}

	public static boolean isWithinHorizontalDistance(
		double firstX,
		double firstZ,
		double secondX,
		double secondZ,
		double maximumDistance
	) {
		validateMaximumDistance(maximumDistance);
		return horizontalDistanceSquared(firstX, firstZ, secondX, secondZ)
			<= maximumDistance * maximumDistance;
	}

	/**
	 * Moves an angle toward a target by at most {@code maximumChangeDegrees},
	 * following the shortest route across the -180/180 boundary.
	 */
	public static double approachDegrees(
		double currentDegrees,
		double targetDegrees,
		double maximumChangeDegrees
	) {
		if (!Double.isFinite(maximumChangeDegrees) || maximumChangeDegrees < 0.0) {
			throw new IllegalArgumentException("maximumChangeDegrees must be finite and non-negative");
		}
		double delta = wrapDegrees(targetDegrees - currentDegrees);
		double clampedDelta = Math.max(-maximumChangeDegrees, Math.min(maximumChangeDegrees, delta));
		return currentDegrees + clampedDelta;
	}

	public static double wrapDegrees(double degrees) {
		double wrapped = degrees % 360.0;
		if (wrapped >= 180.0) {
			wrapped -= 360.0;
		}
		if (wrapped < -180.0) {
			wrapped += 360.0;
		}
		return wrapped;
	}

	private static void validateMaximumDistance(double maximumDistance) {
		if (!Double.isFinite(maximumDistance) || maximumDistance < 0.0) {
			throw new IllegalArgumentException("maximumDistance must be finite and non-negative");
		}
	}

	public record AimAngles(double yawDegrees, double pitchDegrees) {
	}
}
