package dev.winso.netherwarthelper.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.winso.netherwarthelper.config.FarmConfig;
import org.junit.jupiter.api.Test;

class MovementMonitorTest {
	private static final DirectionMath.HorizontalVector LEFT = DirectionMath.lateralUnit(0.0, true);

	@Test
	void requiresConsecutiveSlowTicksAfterGracePeriod() {
		MovementMonitor monitor = new MovementMonitor();
		monitor.reset(0.0, 0.0);

		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2, true));
		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2, true));
		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2, true));
		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2, true));
		assertTrue(monitor.update(0.0, 0.0, LEFT, 0.003, 3, 2, true));
	}

	@Test
	void meaningfulProgressResetsTheCounter() {
		MovementMonitor monitor = new MovementMonitor();
		monitor.reset(0.0, 0.0);

		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 2, 0, true));
		assertFalse(monitor.update(0.2, 0.0, LEFT, 0.003, 2, 0, true));
		assertFalse(monitor.update(0.2, 0.0, LEFT, 0.003, 2, 0, true));
		assertTrue(monitor.update(0.2, 0.0, LEFT, 0.003, 2, 0, true));
	}

	@Test
	void forwardTransitionContinuesThroughMotionAndStopsOnlyAfterBlockedConfirmation() {
		MovementMonitor monitor = new MovementMonitor();
		DirectionMath.HorizontalVector forward = DirectionMath.forwardUnit(90.0);
		monitor.reset(0.0, 0.0);

		for (int tick = 1; tick <= 12; tick++) {
			assertFalse(monitor.update(-0.1 * tick, 0.0, forward, 0.003, 3, 10, false));
		}
		assertFalse(monitor.update(-1.2, 0.0, forward, 0.003, 3, 10, true));
		assertFalse(monitor.update(-1.2, 0.0, forward, 0.003, 3, 10, true));
		assertTrue(monitor.update(-1.2, 0.0, forward, 0.003, 3, 10, true));
	}

	@Test
	void currentDefaultsConfirmARealWallAfterTwoSamplesWithoutWaitingAgain() {
		FarmConfig config = new FarmConfig();
		MovementMonitor monitor = new MovementMonitor();
		monitor.reset(0.0, 0.0);
		for (int tick = 1; tick <= 20; tick++) {
			assertFalse(monitor.update(
				tick * 0.2, 0.0, LEFT, config.minimumMovementDelta,
				config.stuckDetectionTicks, config.laneStartGraceTicks, false
			));
		}
		assertFalse(monitor.update(
			4.0, 0.0, LEFT, config.minimumMovementDelta,
			config.stuckDetectionTicks, config.laneStartGraceTicks, true
		));
		assertTrue(monitor.update(
			4.0, 0.0, LEFT, config.minimumMovementDelta,
			config.stuckDetectionTicks, config.laneStartGraceTicks, true
		));
		assertEquals(2, monitor.getStuckCounter());
		assertEquals(0, config.transitionSettleTicks);
	}

	@Test
	void aSlowTickOrOldSideWallDoesNotEndForwardMovement() {
		MovementMonitor monitor = new MovementMonitor();
		DirectionMath.HorizontalVector forward = DirectionMath.forwardUnit(90.0);
		monitor.reset(0.0, 0.0);
		// The previous lateral wall still touches the player, but there is no
		// obstruction ahead. Even stationary samples must not trigger a turn.
		for (int tick = 0; tick < 20; tick++) {
			assertFalse(monitor.update(0.0, 0.0, forward, 0.003, 2, 2, false));
		}
		assertEquals(0, monitor.getStuckCounter());
		assertFalse(monitor.update(0.0, 0.0, forward, 0.003, 2, 2, true));
		assertFalse(monitor.update(-0.2, 0.0, forward, 0.003, 2, 2, true));
		assertEquals(0, monitor.getStuckCounter());
	}

	@Test
	void losingWallEvidenceClearsTheFastConfirmation() {
		MovementMonitor monitor = new MovementMonitor();
		monitor.reset(0.0, 0.0);
		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 2, 0, true));
		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 2, 0, false));
		assertFalse(monitor.update(0.0, 0.0, LEFT, 0.003, 2, 0, true));
		assertTrue(monitor.update(0.0, 0.0, LEFT, 0.003, 2, 0, true));
	}

	@Test
	void forwardMotionNeverExpiresAtTheGraceDuration() {
		FarmConfig config = new FarmConfig();
		MovementMonitor monitor = new MovementMonitor();
		DirectionMath.HorizontalVector forward = DirectionMath.forwardUnit(90.0);
		monitor.reset(0.0, 0.0);
		for (int tick = 1; tick <= 100; tick++) {
			assertFalse(monitor.update(
				-tick * 0.2, 0.0, forward, config.minimumMovementDelta,
				config.forwardStuckDetectionTicks, config.forwardShiftTicks, false
			));
		}
		assertFalse(monitor.update(
			-20.0, 0.0, forward, config.minimumMovementDelta,
			config.forwardStuckDetectionTicks, config.forwardShiftTicks, true
		));
		assertTrue(monitor.update(
			-20.0, 0.0, forward, config.minimumMovementDelta,
			config.forwardStuckDetectionTicks, config.forwardShiftTicks, true
		));
	}
}
