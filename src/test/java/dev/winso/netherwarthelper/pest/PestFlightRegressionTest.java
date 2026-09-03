package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Simplified vertical-only flight simulation, not a server or full Minecraft integration test.
 * Cached 26.2 LocalPlayer.aiStep adds 3 * flyingSpeed * (jump - shift), Player.travel moves with
 * that velocity then retains 0.6 of it, and LocalPlayer clears flight after landing. These cases
 * use the default flyingSpeed 0.05, an unobstructed ceiling, and a stationary horizontal position.
 */
class PestFlightRegressionTest {
	private static final double FLOOR_Y = 68.875;
	private static final double REPORTED_START_Y = 69.948;
	private static final double LOW_TARGET_Y = FLOOR_Y - 1.0;
	private static final double EPSILON = 1.0e-9;

	@Test
	void oldUnconditionalDescentLandsBeforeAnEightTickTurnCanFinish() {
		VerticalFlight flight = new VerticalFlight(REPORTED_START_Y, 0.0);
		int ticks = 0;
		while (flight.flying && ticks < 8) {
			// Old navigation holds Shift for the low target even while its heading gate suppresses W.
			flight.tick(false, LOW_TARGET_Y - flight.y < -0.8);
			ticks++;
		}
		assertFalse(flight.flying);
		assertTrue(ticks <= 5, "The old input sequence should reproduce a floor touch in only a few ticks");
		assertEquals(FLOOR_Y, flight.y, EPSILON);
	}

	@Test
	void headingGateThenGroundProtectionKeepsFlightThroughEightyTicksOfLowTargetPursuit() {
		VerticalFlight flight = new VerticalFlight(REPORTED_START_Y, 0.0);
		FlightJumpGuard jumpGuard = new FlightJumpGuard();
		for (int tick = 0; tick < 8; tick++) {
			applyProtectedNavigation(flight, jumpGuard, false, false);
			assertTrue(flight.flying, "Turning toward a distant low target must not land the player");
			assertEquals(REPORTED_START_Y, flight.y, EPSILON);
		}
		for (int tick = 0; tick < 80; tick++) {
			applyProtectedNavigation(flight, jumpGuard, true, false);
			assertTrue(flight.flying, "Floor protection must include residual descent and Jump cooldown");
			assertTrue(flight.y > FLOOR_Y);
		}
		assertTrue(flight.descentTicks > 0, "Aligned navigation should still be able to descend in clear air");
		assertTrue(flight.jumpTicks > 0, "Approaching the floor should brake with permitted ascent");
		assertTrue(flight.minimumJumpPressInterval >= FlightJumpGuard.MIN_PRESS_INTERVAL_TICKS);
	}

	@Test
	void inheritedDownwardMomentumBrakesBeforeTheFloorEvenDuringJumpCooldown() {
		VerticalFlight flight = new VerticalFlight(FLOOR_Y + 1.8, -0.225);
		FlightJumpGuard jumpGuard = new FlightJumpGuard();
		// A recent ascent pulse can leave the next protective press unavailable for several ticks.
		assertTrue(jumpGuard.tick(true));
		assertFalse(jumpGuard.tick(false));
		for (int tick = 0; tick < 80; tick++) {
			applyProtectedNavigation(flight, jumpGuard, true, false);
			assertTrue(flight.flying, "Stopping reserve must protect a player who is already descending");
			assertTrue(flight.y > FLOOR_Y);
		}
		assertTrue(flight.jumpTicks > 0);
		assertTrue(flight.minimumJumpPressInterval >= FlightJumpGuard.MIN_PRESS_INTERVAL_TICKS);
	}

	@Test
	void explicitFinalReturnLandingStillReachesTheFloor() {
		VerticalFlight flight = new VerticalFlight(REPORTED_START_Y, 0.0);
		FlightJumpGuard jumpGuard = new FlightJumpGuard();
		for (int tick = 0; tick < 20 && flight.flying; tick++) {
			applyProtectedNavigation(flight, jumpGuard, true, true);
		}
		assertFalse(flight.flying, "Deliberate final landing must bypass the near-ground ascent guard");
		assertEquals(FLOOR_Y, flight.y, EPSILON);
		assertEquals(0, flight.jumpTicks);
	}

	private static void applyProtectedNavigation(
		VerticalFlight flight, FlightJumpGuard jumpGuard, boolean forwardRequested, boolean allowLanding
	) {
		boolean mayDescend = PestFlightSafety.mayDescendToward(forwardRequested, 12.0, 1.25);
		boolean descendRequested = LOW_TARGET_Y - flight.y < -0.8 && mayDescend;
		boolean nearGround = flight.y - FLOOR_Y <= PestFlightSafety.groundProbeDistance(flight.velocity);
		PestFlightSafety.Controls protectedControls = PestFlightSafety.protect(
			nearGround, true, allowLanding, false, descendRequested
		);
		// The final guarded Jump, not the pre-protection request, is what vanilla consumes next tick.
		flight.tick(jumpGuard.tick(protectedControls.jump()), protectedControls.descend());
	}

	private static final class VerticalFlight {
		private double y;
		private double velocity;
		private boolean flying = true;
		private boolean previousJump;
		private int ticks;
		private int lastJumpPressTick = -1;
		private int minimumJumpPressInterval = Integer.MAX_VALUE;
		private int descentTicks;
		private int jumpTicks;

		private VerticalFlight(double y, double velocity) {
			this.y = y;
			this.velocity = velocity;
		}

		private void tick(boolean jump, boolean descend) {
			assertTrue(flying, "This simplified model stops at landing; grounded physics are out of scope");
			if (jump && !previousJump) {
				if (lastJumpPressTick >= 0) {
					minimumJumpPressInterval = Math.min(minimumJumpPressInterval, ticks - lastJumpPressTick);
				}
				lastJumpPressTick = ticks;
			}
			previousJump = jump;
			if (jump) jumpTicks++;
			if (descend) descentTicks++;
			velocity += 0.15 * ((jump ? 1 : 0) - (descend ? 1 : 0));
			y += velocity;
			velocity *= 0.6;
			if (y <= FLOOR_Y) {
				y = FLOOR_Y;
				velocity = 0.0;
				flying = false;
			}
			ticks++;
		}
	}
}
