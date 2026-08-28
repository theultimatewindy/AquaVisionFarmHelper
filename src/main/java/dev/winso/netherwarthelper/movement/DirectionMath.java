package dev.winso.netherwarthelper.movement;

/**
 * Converts Minecraft yaw into unit vectors in the horizontal X/Z plane.
 * Minecraft yaw 0 faces +Z. Positive yaw turns toward -X.
 */
public final class DirectionMath {
	private DirectionMath() {
	}

	public static HorizontalVector lateralUnit(double yawDegrees, boolean left) {
		double yawRadians = Math.toRadians(yawDegrees);
		double side = left ? 1.0 : -1.0;
		return new HorizontalVector(
			Math.cos(yawRadians) * side,
			Math.sin(yawRadians) * side
		);
	}

	public static double projectedProgress(
		double deltaX,
		double deltaZ,
		HorizontalVector expectedDirection
	) {
		return deltaX * expectedDirection.x() + deltaZ * expectedDirection.z();
	}

	public record HorizontalVector(double x, double z) {
	}
}
