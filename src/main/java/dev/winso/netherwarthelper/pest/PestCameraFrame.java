package dev.winso.netherwarthelper.pest;

/** One tick's completed camera turn, eligible for interpolation only while that tick remains current. */
public record PestCameraFrame(long tick, double fromYaw, double fromPitch, double toYaw, double toPitch) {
	private static final double MATCH_EPSILON = 1.0e-4;

	public PestCameraFrame {
		if (!Double.isFinite(fromYaw) || !Double.isFinite(fromPitch)
			|| !Double.isFinite(toYaw) || !Double.isFinite(toPitch)) {
			throw new IllegalArgumentException("Camera frame angles must be finite");
		}
	}

	/** An intervening manual turn, teleport, or new tick invalidates this view-only frame. */
	public boolean matches(long currentTick, double currentYaw, double currentPitch) {
		return tick == currentTick
			&& Math.abs(PestNavigationMath.wrapDegrees(currentYaw - toYaw)) <= MATCH_EPSILON
			&& Math.abs(currentPitch - toPitch) <= MATCH_EPSILON;
	}

	/** Carry angular momentum only into the immediately following, otherwise unchanged player tick. */
	public boolean canContinueSteeringAt(long currentTick, double currentYaw, double currentPitch) {
		return tick == currentTick - 1 && matches(tick, currentYaw, currentPitch);
	}

	public double yawAt(double fraction) {
		double turn = PestNavigationMath.wrapDegrees(
			PestNavigationMath.wrapDegrees(toYaw) - PestNavigationMath.wrapDegrees(fromYaw)
		);
		return fromYaw + turn * clampFraction(fraction);
	}

	public double pitchAt(double fraction) {
		return fromPitch + (toPitch - fromPitch) * clampFraction(fraction);
	}

	private static double clampFraction(double fraction) {
		return Double.isNaN(fraction) ? 1.0 : Math.max(0.0, Math.min(1.0, fraction));
	}
}
