package dev.winso.netherwarthelper.pest;

import dev.winso.netherwarthelper.pest.PestNavigationMath.AimAngles;

/** Deterministic tick-based steering with a speed limit and gradual changes in angular speed. */
public final class PestAimSmoother {
	public static final double MAXIMUM_PITCH_DEGREES = 85.0;
	private static final double ANGLE_EPSILON = 1.0e-5;
	private static final double POSITION_EPSILON = 1.0e-5;
	private static final double APPROACH_FRACTION = 0.35;
	private static final double ACCELERATION_FRACTION = 0.25;

	private double yawVelocity;
	private double pitchVelocity;

	public void reset() {
		yawVelocity = 0.0;
		pitchVelocity = 0.0;
	}

	/** At a vertically aligned waypoint, keep the current heading instead of choosing arbitrary yaw zero. */
	public AimAngles aimAt(
		double currentYaw,
		double currentPitch,
		double fromX,
		double fromY,
		double fromZ,
		double targetX,
		double targetY,
		double targetZ,
		double maximumYawStep,
		double maximumPitchStep
	) {
		requireFinite(fromX, fromY, fromZ, targetX, targetY, targetZ);
		double deltaX = targetX - fromX;
		double deltaY = targetY - fromY;
		double deltaZ = targetZ - fromZ;
		double horizontalDistance = Math.hypot(deltaX, deltaZ);
		boolean verticalOnly = horizontalDistance <= POSITION_EPSILON;
		double targetYaw = verticalOnly ? currentYaw : Math.toDegrees(Math.atan2(-deltaX, deltaZ));
		double targetPitch = verticalOnly && Math.abs(deltaY) <= POSITION_EPSILON
			? currentPitch : -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));
		if (verticalOnly) {
			yawVelocity = 0.0;
		}
		return step(currentYaw, currentPitch, targetYaw, targetPitch, maximumYawStep, maximumPitchStep);
	}

	public AimAngles step(
		double currentYaw,
		double currentPitch,
		double targetYaw,
		double targetPitch,
		double maximumYawStep,
		double maximumPitchStep
	) {
		requireFinite(currentYaw, currentPitch, targetYaw, targetPitch);
		validateMaximumStep(maximumYawStep);
		validateMaximumStep(maximumPitchStep);
		double yawError = PestNavigationMath.wrapDegrees(
			PestNavigationMath.wrapDegrees(targetYaw) - PestNavigationMath.wrapDegrees(currentYaw)
		);
		double safePitch = clampPitch(currentPitch);
		double pitchError = clampPitch(targetPitch) - safePitch;
		yawVelocity = nextVelocity(yawVelocity, yawError, maximumYawStep);
		pitchVelocity = nextVelocity(pitchVelocity, pitchError, maximumPitchStep);
		return new AimAngles(currentYaw + yawVelocity, clampPitch(safePitch + pitchVelocity));
	}

	private static double nextVelocity(double previousVelocity, double error, double maximumStep) {
		if (maximumStep == 0.0 || Math.abs(error) <= ANGLE_EPSILON) {
			return 0.0;
		}
		double desiredVelocity = clamp(error * APPROACH_FRACTION, maximumStep);
		double acceleration = maximumStep * ACCELERATION_FRACTION;
		double velocity = clamp(previousVelocity + clamp(desiredVelocity - previousVelocity, acceleration), maximumStep);
		// A moving target can suddenly cross the current view. Brake without reversing in one tick;
		// when moving toward it, never cross it just to preserve the previous angular speed.
		if (Math.signum(velocity) == Math.signum(error) && Math.abs(velocity) > Math.abs(error)) {
			velocity = error;
		}
		return velocity;
	}

	private static double clamp(double value, double absoluteLimit) {
		return Math.max(-absoluteLimit, Math.min(absoluteLimit, value));
	}

	private static double clampPitch(double pitch) {
		return clamp(pitch, MAXIMUM_PITCH_DEGREES);
	}

	private static void validateMaximumStep(double maximumStep) {
		if (!Double.isFinite(maximumStep) || maximumStep < 0.0) {
			throw new IllegalArgumentException("Angular step limits must be finite and non-negative");
		}
	}

	private static void requireFinite(double... values) {
		for (double value : values) {
			if (!Double.isFinite(value)) {
				throw new IllegalArgumentException("Aim coordinates and angles must be finite");
			}
		}
	}
}
