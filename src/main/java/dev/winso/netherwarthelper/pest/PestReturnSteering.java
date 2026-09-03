package dev.winso.netherwarthelper.pest;

/** Return-only horizontal braking using ordinary movement keys, without changing position or velocity. */
public final class PestReturnSteering {
	private static final double POSITION_GAIN = 0.15;
	private static final double SETTLED_SPEED = 0.025;
	private static final double CARDINAL_INPUT_SCALE = 0.98;
	private static final int APPROACH_LOOKAHEAD_TICKS = 3;
	private static final double HEADING_ACQUIRE_DEGREES = 4.0;
	private static final double HEADING_RELEASE_DEGREES = 35.0;
	// Local axes: yaw zero faces +Z and Left points +X; each diagonal is normalized before acceleration.
	private static final int[][] INPUTS = {
		{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
	};
	private static final Controls REST = new Controls(false, false, false, false, false);
	private static final Controls SETTLED = new Controls(false, false, false, false, true);

	private PestReturnSteering() {
	}

	/** Matches vanilla creative-flight acceleration, including its sprint multiplier. */
	public static double airborneAcceleration(double abilityFlyingSpeed, boolean sprinting) {
		if (!Double.isFinite(abilityFlyingSpeed) || abilityFlyingSpeed <= 0.0) {
			throw new IllegalArgumentException("Flying speed must be finite and positive");
		}
		return 0.98 * abilityFlyingSpeed * (sprinting ? 2.0 : 1.0);
	}

	/**
	 * A return leg turns first, then moves. The wider release band prevents a small camera or
	 * server correction from tapping W off again after the fixed leg heading was acquired.
	 */
	public static boolean isLegHeadingAcquired(double currentYaw, double targetYaw, boolean previouslyAcquired) {
		requireFinite(currentYaw, targetYaw);
		return PestNavigationMath.isHeadingAligned(currentYaw, targetYaw,
			previouslyAcquired ? HEADING_RELEASE_DEGREES : HEADING_ACQUIRE_DEGREES);
	}

	/**
	 * dx/dz point from the player to the waypoint; vx/vz are the current post-drag velocity.
	 * inputAcceleration and drag describe the next movement tick (for default unsprinted flight,
	 * cardinal acceleration 0.98 * 0.05 and drag 0.91). Vanilla's square-movement adjustment
	 * restores diagonal input to unit length, so its acceleration is cardinal acceleration / 0.98.
	 * The caller keeps camera steering and vertical flight control separate.
	 */
	public static Controls steer(
		double dx, double dz, double vx, double vz, double yawDegrees,
		double inputAcceleration, double drag, double tolerance
	) {
		requireFinite(dx, dz, vx, vz, yawDegrees, inputAcceleration, drag, tolerance);
		if (inputAcceleration <= 0.0 || drag <= 0.0 || drag >= 1.0 || tolerance <= 0.0) {
			throw new IllegalArgumentException("Acceleration/tolerance must be positive and drag strictly between zero and one");
		}
		double distance = Math.hypot(dx, dz);
		double stoppingX = dx - vx / (1.0 - drag);
		double stoppingZ = dz - vz / (1.0 - drag);
		double stoppingDistance = Math.hypot(stoppingX, stoppingZ);
		if (isSettled(dx, dz, vx, vz, drag, tolerance)) {
			return SETTLED;
		}
		// Once ordinary drag will settle inside the arrival area, do not restart pursuit and overshoot it.
		if (stoppingDistance <= tolerance * 0.8) return REST;

		Planner planner = new Planner(dx, dz, vx, vz, yawDegrees, inputAcceleration, drag);
		if (distance > Math.max(2.0, inputAcceleration / (1.0 - drag) * 4.0)) {
			planner.chooseVelocityControl(distance, inputAcceleration);
		} else {
			// Digital keys have a minimum impulse: one-step PD alone can cycle forever near a small
			// waypoint. A fixed, short lookahead selects a brake/coast pulse sequence that can settle.
			int lookahead = inputAcceleration / (1.0 - drag) > tolerance * 4.0
				? APPROACH_LOOKAHEAD_TICKS + 1 : APPROACH_LOOKAHEAD_TICKS;
			planner.approach(dx, dz, vx, vz, lookahead, -1, 0.0);
		}
		int[] chosen = INPUTS[planner.bestInput];
		return new Controls(chosen[0] > 0, chosen[0] < 0, chosen[1] > 0, chosen[1] < 0, false);
	}

	/** Cheap arrival check shared with final-descent/completion logic; it does not run the input search. */
	public static boolean isSettled(double dx, double dz, double vx, double vz, double drag, double tolerance) {
		requireFinite(dx, dz, vx, vz, drag, tolerance);
		if (drag <= 0.0 || drag >= 1.0 || tolerance <= 0.0) {
			throw new IllegalArgumentException("Tolerance must be positive and drag strictly between zero and one");
		}
		return Math.hypot(dx, dz) <= tolerance && Math.hypot(vx, vz) <= SETTLED_SPEED
			&& Math.hypot(dx - vx / (1.0 - drag), dz - vz / (1.0 - drag)) <= tolerance;
	}

	private static final class Planner {
		private final double dx;
		private final double dz;
		private final double vx;
		private final double vz;
		private final double drag;
		private final double[] accelerationX = new double[INPUTS.length];
		private final double[] accelerationZ = new double[INPUTS.length];
		private double bestCost = Double.POSITIVE_INFINITY;
		private int bestInput;

		private Planner(double dx, double dz, double vx, double vz, double yaw, double acceleration, double drag) {
			this.dx = dx;
			this.dz = dz;
			this.vx = vx;
			this.vz = vz;
			this.drag = drag;
			double radians = Math.toRadians(PestNavigationMath.wrapDegrees(yaw));
			double sin = Math.sin(radians);
			double cos = Math.cos(radians);
			for (int index = 0; index < INPUTS.length; index++) {
				int forward = INPUTS[index][0];
				int left = INPUTS[index][1];
				double magnitude = Math.max(1.0, Math.hypot(forward, left));
				double effectiveAcceleration = magnitude > 1.0 ? acceleration / CARDINAL_INPUT_SCALE : acceleration;
				accelerationX[index] = (-sin * forward + cos * left) * effectiveAcceleration / magnitude;
				accelerationZ[index] = (cos * forward + sin * left) * effectiveAcceleration / magnitude;
			}
		}

		private void chooseVelocityControl(double distance, double acceleration) {
			// Use the natural full-input equilibrium on long legs. Artificially capping cruise below
			// this speed makes a digital controller alternate W and coast every few ticks, which is
			// visible as a flinch and can fight a separate AutoSprint mod. Position gain still reduces
			// the requested speed near the waypoint so the lookahead controller can brake precisely.
			double speedLimit = acceleration * drag / (1.0 - drag);
			double desiredSpeed = Math.min(speedLimit, distance * POSITION_GAIN);
			double desiredX = dx / distance * desiredSpeed;
			double desiredZ = dz / distance * desiredSpeed;
			for (int index = 0; index < INPUTS.length; index++) {
				double nextX = (vx + accelerationX[index]) * drag;
				double nextZ = (vz + accelerationZ[index]) * drag;
				double cost = square(nextX - desiredX) + square(nextZ - desiredZ) + inputCost(index) * 0.000001;
				consider(index, cost);
			}
		}

		private void approach(double errorX, double errorZ, double velocityX, double velocityZ,
			int remainingTicks, int firstInput, double accumulatedCost) {
			if (remainingTicks == 0) {
				double stopX = errorX - velocityX / (1.0 - drag);
				double stopZ = errorZ - velocityZ / (1.0 - drag);
				double cost = square(stopX) + square(stopZ)
					+ 0.12 * (square(errorX) + square(errorZ))
					+ 0.1 * (square(velocityX) + square(velocityZ)) + accumulatedCost;
				consider(firstInput, cost);
				return;
			}
			for (int index = 0; index < INPUTS.length; index++) {
				double movedX = velocityX + accelerationX[index];
				double movedZ = velocityZ + accelerationZ[index];
				double nextErrorX = errorX - movedX;
				double nextErrorZ = errorZ - movedZ;
				double nextCost = accumulatedCost + 0.001 * (square(nextErrorX) + square(nextErrorZ))
					+ 0.00001 * inputCost(index);
				approach(nextErrorX, nextErrorZ, movedX * drag, movedZ * drag,
					remainingTicks - 1, firstInput < 0 ? index : firstInput, nextCost);
			}
		}

		private void consider(int index, double cost) {
			if (cost < bestCost) {
				bestCost = cost;
				bestInput = index;
			}
		}
	}

	private static int inputCost(int index) {
		return Math.abs(INPUTS[index][0]) + Math.abs(INPUTS[index][1]);
	}

	private static double square(double value) {
		return value * value;
	}

	private static void requireFinite(double... values) {
		for (double value : values) {
			if (!Double.isFinite(value)) throw new IllegalArgumentException("Return steering inputs must be finite");
		}
	}

	public record Controls(boolean forward, boolean backward, boolean left, boolean right, boolean settled) {
	}
}
