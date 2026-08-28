package dev.winso.netherwarthelper.movement;

/**
 * Detects sustained failure to make progress in a commanded local direction.
 * A grace period and consecutive-tick confirmation prevent one slow tick from
 * being interpreted as the end of a lane.
 */
public final class MovementMonitor {
	private boolean initialized;
	private double previousX;
	private double previousZ;
	private double lastHorizontalDelta;
	private double lastExpectedProgress;
	private int sampleCount;
	private int stuckCounter;

	public void reset(double x, double z) {
		initialized = true;
		previousX = x;
		previousZ = z;
		lastHorizontalDelta = 0.0;
		lastExpectedProgress = 0.0;
		sampleCount = 0;
		stuckCounter = 0;
	}

	public void clear() {
		initialized = false;
		previousX = 0.0;
		previousZ = 0.0;
		lastHorizontalDelta = 0.0;
		lastExpectedProgress = 0.0;
		sampleCount = 0;
		stuckCounter = 0;
	}

	public boolean update(
		double x,
		double z,
		DirectionMath.HorizontalVector expectedDirection,
		double minimumMovementDelta,
		int stuckDetectionTicks,
		int graceTicks
	) {
		if (!initialized) {
			reset(x, z);
			return false;
		}

		double deltaX = x - previousX;
		double deltaZ = z - previousZ;
		previousX = x;
		previousZ = z;

		lastHorizontalDelta = Math.hypot(deltaX, deltaZ);
		lastExpectedProgress = DirectionMath.projectedProgress(deltaX, deltaZ, expectedDirection);
		sampleCount++;

		if (sampleCount <= Math.max(0, graceTicks)) {
			stuckCounter = 0;
			return false;
		}

		if (lastExpectedProgress < Math.max(0.0, minimumMovementDelta)) {
			stuckCounter++;
		} else {
			stuckCounter = 0;
		}

		return stuckCounter >= Math.max(1, stuckDetectionTicks);
	}

	public double getLastHorizontalDelta() {
		return lastHorizontalDelta;
	}

	public double getLastExpectedProgress() {
		return lastExpectedProgress;
	}

	public int getStuckCounter() {
		return stuckCounter;
	}

	public int getSampleCount() {
		return sampleCount;
	}
}
