package dev.winso.netherwarthelper.failsafe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NoWartFailsafeMonitorTest {
	@Test
	void triggersExactlyAtConfiguredTimeoutAndDoesNotSpam() {
		NoWartFailsafeMonitor monitor = new NoWartFailsafeMonitor();
		monitor.startSession(true, 10);

		for (int tick = 1; tick < 200; tick++) {
			assertFalse(monitor.tick(true), "triggered early at tick " + tick);
		}
		assertTrue(monitor.tick(true));
		assertTrue(monitor.isAlertActive());
		assertFalse(monitor.tick(true));
		assertFalse(monitor.tick(true));
	}

	@Test
	void pausedTicksDoNotAdvanceOrDiscardPartialProgress() {
		NoWartFailsafeMonitor monitor = new NoWartFailsafeMonitor();
		monitor.startSession(true, 1);

		for (int tick = 0; tick < 9; tick++) {
			assertFalse(monitor.tick(true));
		}
		for (int tick = 0; tick < 100; tick++) {
			assertFalse(monitor.tick(false));
		}
		for (int tick = 0; tick < 10; tick++) {
			assertFalse(monitor.tick(true));
		}
		assertTrue(monitor.tick(true));
	}

	@Test
	void wartBreakClearsAndRearmsAnIncident() {
		NoWartFailsafeMonitor monitor = new NoWartFailsafeMonitor();
		monitor.startSession(true, 1);

		for (int tick = 0; tick < 19; tick++) {
			monitor.tick(true);
		}
		assertTrue(monitor.tick(true));
		assertTrue(monitor.recordWartBreak());
		assertFalse(monitor.isAlertActive());

		for (int tick = 0; tick < 19; tick++) {
			assertFalse(monitor.tick(true));
		}
		assertTrue(monitor.tick(true));
	}

	@Test
	void wartBreakBeforeTimeoutRestartsTheTimer() {
		NoWartFailsafeMonitor monitor = new NoWartFailsafeMonitor();
		monitor.startSession(true, 1);

		for (int tick = 0; tick < 19; tick++) {
			assertFalse(monitor.tick(true));
		}
		assertFalse(monitor.recordWartBreak());
		for (int tick = 0; tick < 19; tick++) {
			assertFalse(monitor.tick(true));
		}
		assertTrue(monitor.tick(true));
	}

	@Test
	void disabledMonitorNeverAlerts() {
		NoWartFailsafeMonitor monitor = new NoWartFailsafeMonitor();
		monitor.startSession(false, 1);

		for (int tick = 0; tick < 100; tick++) {
			assertFalse(monitor.tick(true));
		}
		assertFalse(monitor.isAlertActive());
		assertFalse(monitor.recordWartBreak());
	}
}
