package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PestFlightMonitorTest {
	private final PestFlightMonitor flight = new PestFlightMonitor();

	@Test void jumpsBeforeRequestingFlightAndConfirmsBeforeNavigation() {
		var grounded = flight.tick(true, false, true, 64);
		assertTrue(grounded.holdJump());
		assertFalse(grounded.requestFlight());
		assertFalse(grounded.ready());
		assertTrue(flight.tick(true, false, false, 64.42).requestFlight());
		assertFalse(flight.tick(true, true, false, 64.65).ready());
		assertFalse(flight.tick(true, true, false, 64.70).ready());
		assertTrue(flight.tick(true, true, false, 64.72).ready());
	}

	@Test void flightFlagWhileGroundedIsNotReadyAndDoesNotResend() {
		for (int tick = 0; tick < 10; tick++) {
			var result = flight.tick(true, true, true, 64);
			assertFalse(result.ready());
			assertFalse(result.requestFlight());
			assertTrue(result.holdJump());
		}
	}

	@Test void alreadyAirborneFlightNeedsNoJumpOrPacket() {
		var result = flight.tick(true, true, false, 90);
		assertTrue(result.ready());
		assertFalse(result.holdJump());
		assertFalse(result.requestFlight());
	}

	@Test void rejectedFlightRetriesAreSpacedAndBounded() {
		int packets = 0;
		int lastRequest = -PestFlightMonitor.REQUEST_INTERVAL_TICKS;
		boolean failed = false;
		for (int tick = 0; tick < PestFlightMonitor.TAKEOFF_TIMEOUT_TICKS + 2; tick++) {
			var result = flight.tick(true, false, false, 70);
			if (result.requestFlight()) {
				assertTrue(tick - lastRequest >= PestFlightMonitor.REQUEST_INTERVAL_TICKS);
				lastRequest = tick;
				packets++;
			}
			assertFalse(result.ready());
			if (result.failed()) { failed = true; break; }
		}
		assertTrue(failed);
		assertEquals(PestFlightMonitor.MAX_REQUESTS, packets);
	}

	@Test void groundObstructionTimesOutInsteadOfJumpingForever() {
		for (int tick = 0; tick < PestFlightMonitor.TAKEOFF_TIMEOUT_TICKS; tick++) {
			assertFalse(flight.tick(true, false, true, 64).failed());
		}
		var failure = flight.tick(true, false, true, 64);
		assertTrue(failure.failed());
		assertFalse(failure.holdJump());
		assertFalse(failure.requestFlight());
	}

	@Test void revokedPermissionNeverRequestsFlight() {
		flight.tick(true, true, false, 90);
		var result = flight.tick(false, false, false, 90);
		assertTrue(result.failed());
		assertFalse(result.ready());
		assertFalse(result.holdJump());
		assertFalse(result.requestFlight());
		assertTrue(flight.tick(true, false, false, 90).failed());
	}

	@Test void losingFlightSuspendsNavigationAndRepeatedLossStops() {
		assertTrue(flight.tick(true, true, false, 90).ready());
		for (int recovery = 0; recovery < PestFlightMonitor.MAX_RECOVERIES; recovery++) {
			var lost = flight.tick(true, false, false, 90);
			assertFalse(lost.ready());
			assertTrue(lost.requestFlight());
			assertFalse(flight.tick(true, true, false, 90).ready());
			assertFalse(flight.tick(true, true, false, 90).ready());
			assertTrue(flight.tick(true, true, false, 90).ready());
		}
		assertTrue(flight.tick(true, false, false, 90).failed());
	}

	@Test void resetClearsFailedRunAndNoVerticalLiftCannotConfirmGroundTakeoff() {
		flight.tick(false, false, true, 64);
		flight.reset();
		flight.tick(true, false, true, 64);
		for (int tick = 0; tick < 8; tick++) {
			assertFalse(flight.tick(true, true, false, 64.01).ready());
		}
		assertFalse(flight.tick(true, true, false, 64.5).ready());
		assertFalse(flight.tick(true, true, false, 64.5).ready());
		assertTrue(flight.tick(true, true, false, 64.5).ready());
	}

	@Test void smallButRealLiftUnderATwoBlockCeilingCanConfirmFlight() {
		flight.tick(true, false, true, 64);
		assertTrue(flight.tick(true, false, false, 64.19).requestFlight());
		assertFalse(flight.tick(true, true, false, 64.19).ready());
		assertFalse(flight.tick(true, true, false, 64.19).ready());
		assertTrue(flight.tick(true, true, false, 64.19).ready());
	}
}
