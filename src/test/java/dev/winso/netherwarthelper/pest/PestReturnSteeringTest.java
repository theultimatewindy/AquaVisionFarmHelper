package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Horizontal-only simulation from vanilla movement/drag; not a server or collision-path integration test. */
class PestReturnSteeringTest {
	@Test
	void airbornePhysicsIncludesVanillasSprintMultiplier() {
		assertEquals(0.049, PestReturnSteering.airborneAcceleration(0.05, false), 1.0e-12);
		assertEquals(0.098, PestReturnSteering.airborneAcceleration(0.05, true), 1.0e-12);
		assertThrows(IllegalArgumentException.class,
			() -> PestReturnSteering.airborneAcceleration(Double.NaN, false));
	}

	@Test
	void returnLegTurnsBeforeMovingAndUsesHeadingHysteresis() {
		assertFalse(PestReturnSteering.isLegHeadingAcquired(5.0, 0.0, false));
		assertTrue(PestReturnSteering.isLegHeadingAcquired(4.0, 0.0, false));
		assertTrue(PestReturnSteering.isLegHeadingAcquired(34.0, 0.0, true));
		assertFalse(PestReturnSteering.isLegHeadingAcquired(36.0, 0.0, true));
	}

	private static final double FLIGHT_ACCELERATION = 0.049;
	private static final double FLIGHT_DRAG = 0.91;
	private static final double EPSILON = 1.0e-8;

	@Test
	void atRestInsideTheArrivalAreaRequestsNoMovement() {
		assertEquals(new PestReturnSteering.Controls(false, false, false, false, true),
			PestReturnSteering.steer(0, 0, 0, 0, 123, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25));
		assertTrue(PestReturnSteering.steer(0.1, 0.1, 0, 0, -90, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25).settled());
	}

	@Test
	void allCardinalYawDirectionsHaveTheCorrectForwardAxis() {
		for (int yaw : new int[] {0, 90, 180, 270}) {
			double radians = Math.toRadians(yaw);
			var controls = PestReturnSteering.steer(-Math.sin(radians) * 10, Math.cos(radians) * 10,
				0, 0, yaw, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25);
			assertTrue(controls.forward(), "yaw " + yaw);
			assertFalse(controls.backward() || controls.left() || controls.right());
		}
	}

	@Test
	void leftIsPositiveXAtYawZeroAndPositiveZAtYawNinety() {
		assertTrue(PestReturnSteering.steer(10, 0, 0, 0, 0, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25).left());
		assertTrue(PestReturnSteering.steer(0, 10, 0, 0, 90, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25).left());
		assertTrue(PestReturnSteering.steer(-10, 0, 0, 0, 0, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25).right());
	}

	@Test
	void diagonalKeysUseNormalizedUnitInputAfterVanillaSquareMovementAdjustment() {
		var controls = PestReturnSteering.steer(10, 10, 0, 0, 0, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25);
		assertTrue(controls.forward() && controls.left());
		Flight simulation = new Flight(0, 0, 0, 0, 0);
		simulation.move(controls, FLIGHT_ACCELERATION, FLIGHT_DRAG);
		assertEquals(FLIGHT_ACCELERATION / 0.98, Math.hypot(simulation.x, simulation.z), EPSILON);
	}

	@Test
	void inheritedSprintMomentumIsBrakedWhileTheCameraFacesAway() {
		var controls = PestReturnSteering.steer(-10, 0, 1, 0, -90, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25);
		assertTrue(controls.backward());
		assertFalse(controls.forward());
		Flight simulation = new Flight(10, 0, 1, 0, -90);
		assertTrue(simulation.returnToOrigin(FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25, true, 200) < 200);
	}

	@Test
	void alignedLongTravelRemainsStraightWithoutAlternatingSidewaysInputs() {
		Flight simulation = new Flight(0, -50, 0, 0, 0);
		for (int tick = 0; tick < 80; tick++) {
			var controls = PestReturnSteering.steer(-simulation.x, -simulation.z, simulation.vx, simulation.vz,
				0, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25);
			assertFalse(controls.left() || controls.right());
			simulation.move(controls, FLIGHT_ACCELERATION, FLIGHT_DRAG);
			assertEquals(0, simulation.x, EPSILON);
		}
		assertTrue(simulation.z > -25, "Normal return travel should make steady forward progress");
	}

	@Test
	void longStraightCruiseKeepsForwardHeldAtNormalAndSprintFlightSpeed() {
		for (double acceleration : new double[] {FLIGHT_ACCELERATION, FLIGHT_ACCELERATION * 2.0}) {
			Flight simulation = new Flight(0, -50, 0, 0, 0);
			boolean forwardWasDown = false;
			boolean brakingStarted = false;
			int forwardEdges = 0;
			int tick;
			for (tick = 0; tick < 200; tick++) {
				var controls = PestReturnSteering.steer(-simulation.x, -simulation.z,
					simulation.vx, simulation.vz, simulation.yaw, acceleration, FLIGHT_DRAG, 0.25);
				if (controls.settled()) break;
				if (controls.forward() != forwardWasDown) forwardEdges++;
				if (forwardWasDown && !controls.forward()) brakingStarted = true;
				assertFalse(brakingStarted && controls.forward(),
					"Straight cruise must not tap W again after entering its coast/brake phase");
				assertFalse(controls.backward() || controls.left() || controls.right());
				simulation.move(controls, acceleration, FLIGHT_DRAG);
				forwardWasDown = controls.forward();
			}
			assertTrue(tick < 200, "The smoother cruise must retain exact final convergence");
			assertTrue(brakingStarted, "The return must release W once to coast into the lane");
			assertEquals(2, forwardEdges, "Expected one W press followed by one W release");
		}
	}

	@Test
	void brakesAnOrbitThatForwardOnlyPursuitCannotSettle() {
		Flight previous = new Flight(2, 0, 0, 0.5, 0);
		assertFalse(previous.oldForwardOnlyReturnSettles(600));
		Flight improved = new Flight(2, 0, 0, 0.5, 0);
		assertTrue(improved.returnToOrigin(FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25, true, 200) < 200);
	}

	@Test
	void reachesQuarterBlockPrecisionAndCoastsInsideItAfterRelease() {
		for (int sample = 0; sample < 32; sample++) {
			Flight simulation = new Flight(15 * Math.sin(sample * 7), 15 * Math.cos(sample * 3),
				Math.sin(sample) * 0.99, Math.cos(sample * 3) * 0.99, sample * 10);
			assertTrue(simulation.returnToOrigin(FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25, true, 220) < 220,
				"sample " + sample);
			simulation.assertCoastsInside(FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25);
		}
	}

	@Test
	void normalAndFastGroundMovementConvergeDespiteDigitalKeyQuantization() {
		for (double acceleration : new double[] {0.098, 0.392}) {
			for (int sample = 0; sample < 16; sample++) {
				Flight simulation = new Flight(5 * Math.sin(sample * 7), 5 * Math.cos(sample * 3),
					Math.sin(sample) * 0.9, Math.cos(sample * 3) * 0.9, (sample % 4) * 90);
				assertTrue(simulation.returnToOrigin(acceleration, 0.546, 0.25, false, 150) < 150);
				simulation.assertCoastsInside(acceleration, 0.546, 0.25);
			}
		}
		Flight minimumImpulseCase = new Flight(0.4, 0, 0, 0, 0);
		assertTrue(minimumImpulseCase.returnToOrigin(0.392, 0.546, 0.25, false, 100) < 100);
	}

	@Test
	void customFlightSpeedAndTighterToleranceAreSupported() {
		for (double acceleration : new double[] {0.0245, 0.049, 0.196}) {
			Flight simulation = new Flight(4, -3, 0.5, -0.2, 179);
			assertTrue(simulation.returnToOrigin(acceleration, FLIGHT_DRAG, 0.15, true, 250) < 250);
			simulation.assertCoastsInside(acceleration, FLIGHT_DRAG, 0.15);
		}
	}

	@Test
	void reservedMinimumLandingToleranceConvergesAtNormalAndSprintFlightSpeed() {
		for (double acceleration : new double[] {0.049, 0.098}) {
			Flight simulation = new Flight(4, -3, 0.25, -0.1, 90);
			assertTrue(simulation.returnToOrigin(acceleration, FLIGHT_DRAG,
				PestLandingTargetPlanner.MINIMUM_SETTLE_TOLERANCE, true, 300) < 300);
		}
	}

	@Test
	void reservedMinimumLandingToleranceHandlesBoostedGroundSpeed() {
		for (double acceleration : new double[] {0.098, 0.196, 0.392}) {
			Flight simulation = new Flight(1.0, -0.5, 0.0, 0.0, 90);
			assertTrue(simulation.returnToOrigin(acceleration, 0.546,
				PestLandingTargetPlanner.MINIMUM_SETTLE_TOLERANCE, false, 300) < 300,
				"ground acceleration " + acceleration);
		}
	}

	@Test
	void positionAloneDoesNotDeclareArrivalWhileMomentumWouldCarryPastTheLane() {
		assertFalse(PestReturnSteering.steer(0.1, 0, 0.5, 0, 0, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.25).settled());
		assertFalse(PestReturnSteering.steer(0.1, 0, 0.024, 0, 0, FLIGHT_ACCELERATION, FLIGHT_DRAG, 0.15).settled());
		assertFalse(PestReturnSteering.isSettled(0.1, 0, 0.024, 0, FLIGHT_DRAG, 0.15));
		assertTrue(PestReturnSteering.isSettled(0.1, 0, 0.005, 0, FLIGHT_DRAG, 0.15));
	}

	@Test
	void invalidInputsAreRejectedWithoutNonFiniteControls() {
		assertThrows(IllegalArgumentException.class, () -> PestReturnSteering.steer(Double.NaN, 0, 0, 0, 0, 0.049, 0.91, 0.25));
		assertThrows(IllegalArgumentException.class, () -> PestReturnSteering.steer(0, 0, Double.POSITIVE_INFINITY, 0, 0, 0.049, 0.91, 0.25));
		assertThrows(IllegalArgumentException.class, () -> PestReturnSteering.steer(0, 0, 0, 0, Double.NaN, 0.049, 0.91, 0.25));
		assertThrows(IllegalArgumentException.class, () -> PestReturnSteering.steer(0, 0, 0, 0, 0, 0, 0.91, 0.25));
		assertThrows(IllegalArgumentException.class, () -> PestReturnSteering.steer(0, 0, 0, 0, 0, 0.049, 1, 0.25));
		assertThrows(IllegalArgumentException.class, () -> PestReturnSteering.steer(0, 0, 0, 0, 0, 0.049, 0, 0.25));
		assertThrows(IllegalArgumentException.class, () -> PestReturnSteering.steer(0, 0, 0, 0, 0, 0.049, 0.91, -1));
	}

	private static final class Flight {
		private double x;
		private double z;
		private double vx;
		private double vz;
		private double yaw;
		private final PestAimSmoother camera = new PestAimSmoother();

		private Flight(double x, double z, double vx, double vz, double yaw) {
			this.x = x;
			this.z = z;
			this.vx = vx;
			this.vz = vz;
			this.yaw = yaw;
		}

		private int returnToOrigin(double acceleration, double drag, double tolerance, boolean turnCamera, int maximumTicks) {
			for (int tick = 0; tick < maximumTicks; tick++) {
				// Return integration keeps the camera heading once close, avoiding a tiny-point yaw chase.
				if (turnCamera && Math.hypot(x, z) > tolerance) turnTowardOrigin();
				var controls = PestReturnSteering.steer(-x, -z, vx, vz, yaw, acceleration, drag, tolerance);
				assertFalse(controls.forward() && controls.backward());
				assertFalse(controls.left() && controls.right());
				if (controls.settled()) return tick;
				move(controls, acceleration, drag);
			}
			return maximumTicks;
		}

		private boolean oldForwardOnlyReturnSettles(int maximumTicks) {
			for (int tick = 0; tick < maximumTicks; tick++) {
				turnTowardOrigin();
				if (Math.hypot(x, z) <= 0.25 && Math.hypot(vx, vz) <= 0.025) return true;
				double targetYaw = Math.toDegrees(Math.atan2(x, -z));
				boolean forward = Math.hypot(x, z) > 0.1 && PestNavigationMath.isHeadingAligned(yaw, targetYaw, 25);
				move(new PestReturnSteering.Controls(forward, false, false, false, false), FLIGHT_ACCELERATION, FLIGHT_DRAG);
			}
			return false;
		}

		private void turnTowardOrigin() {
			if (Math.hypot(x, z) > 1.0e-5) {
				yaw = camera.aimAt(yaw, 0, x, 0, z, 0, 0, 0, 8, 8).yawDegrees();
			}
		}

		private void move(PestReturnSteering.Controls controls, double acceleration, double drag) {
			int forward = (controls.forward() ? 1 : 0) - (controls.backward() ? 1 : 0);
			int left = (controls.left() ? 1 : 0) - (controls.right() ? 1 : 0);
			double magnitude = Math.max(1.0, Math.hypot(forward, left));
			double radians = Math.toRadians(yaw);
			// LocalPlayer.modifyInputSpeedForSquareMovement restores diagonal length from 0.98 to 1.
			double effectiveAcceleration = magnitude > 1.0 ? acceleration / 0.98 : acceleration;
			vx += (-Math.sin(radians) * forward + Math.cos(radians) * left) * effectiveAcceleration / magnitude;
			vz += (Math.cos(radians) * forward + Math.sin(radians) * left) * effectiveAcceleration / magnitude;
			x += vx;
			z += vz;
			vx *= drag;
			vz *= drag;
			// Vanilla suppresses tiny per-axis movement values before the next travel step.
			if (Math.abs(vx) < 0.003) vx = 0;
			if (Math.abs(vz) < 0.003) vz = 0;
		}

		private void assertCoastsInside(double acceleration, double drag, double tolerance) {
			for (int tick = 0; tick < 60; tick++) {
				move(new PestReturnSteering.Controls(false, false, false, false, false), acceleration, drag);
				assertTrue(Math.hypot(x, z) <= tolerance + EPSILON, "Coasting must remain inside the saved lane tolerance");
			}
		}
	}
}
