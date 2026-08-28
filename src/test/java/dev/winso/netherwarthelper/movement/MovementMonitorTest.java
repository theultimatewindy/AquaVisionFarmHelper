package dev.winso.netherwarthelper.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MovementMonitorTest {
	private static final DirectionMath.HorizontalVector LEFT = DirectionMath.lateralUnit(0.0, true);

	@Test
	void requiresConsecutiveSlowTicksAfterGracePeriod() {
		MovementMonitor monitor = new MovementMonitor();
		monitor.reset(0.0, 0.0);

		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2));
		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2));
		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2));
		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2));
		assertTrue(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2));
	}

	@Test
	void meaningfulProgressResetsTheCounter() {
		MovementMonitor monitor = new MovementMonitor();
		monitor.reset(0.0, 0.0);

		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 2, 0));
		assertFalse(monitor.update(0.2, 0.0, LEFT, 0.003, 2, 0));
		assertFalse(monitor.update(0.2, 0.0, LEFT, 0.003, 2, 0));
		assertTrue(monitor.update(0.2, 0.0, LEFT, 0.003, 2, 0));
	}
}
