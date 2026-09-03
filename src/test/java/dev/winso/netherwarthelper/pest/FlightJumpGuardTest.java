package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FlightJumpGuardTest {
	@Test void heldAscentIsContinuousButNewPressWaitsOutToggleWindow() {
		FlightJumpGuard guard = new FlightJumpGuard();
		assertTrue(guard.tick(true)); // First rising edge.
		assertTrue(guard.tick(true));
		assertFalse(guard.tick(false)); // Hover.
		for (int tick = 3; tick < FlightJumpGuard.MIN_PRESS_INTERVAL_TICKS; tick++) {
			assertFalse(guard.tick(true));
		}
		assertTrue(guard.tick(true)); // Exactly eight ticks after the first edge.
	}

	@Test void frequentAltitudeChangesNeverCreateDoubleTaps() {
		FlightJumpGuard guard = new FlightJumpGuard();
		int lastRise = -FlightJumpGuard.MIN_PRESS_INTERVAL_TICKS;
		boolean lastPressed = false;
		for (int tick = 0; tick < 200; tick++) {
			boolean pressed = guard.tick(tick % 2 == 0);
			if (pressed && !lastPressed) {
				assertTrue(tick - lastRise >= FlightJumpGuard.MIN_PRESS_INTERVAL_TICKS);
				lastRise = tick;
			}
			lastPressed = pressed;
		}
	}

	@Test void releasedTicksRemainReleasedAndCancelResets() {
		FlightJumpGuard guard = new FlightJumpGuard();
		assertTrue(guard.tick(true));
		for (int tick = 0; tick < 20; tick++) assertFalse(guard.tick(false));
		assertTrue(guard.tick(true));
		guard.reset();
		assertFalse(guard.tick(false));
		assertTrue(guard.tick(true));
	}
}
