package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PestMovingTargetControlTest {
	@Test
	void rangeEdgeKeepsChasingWhileVacuuming() {
		var controls = decide(4.60, 4.40, true, true, true, true, 0.0, false);
		assertTrue(controls.use());
		assertTrue(controls.forward());
		assertFalse(controls.sprint());
	}

	@Test
	void innerStandoffStopsForwardMotionWithoutReleasingVacuum() {
		var controls = decide(2.80, 2.70, true, true, true, true, 0.0, false);
		assertTrue(controls.use());
		assertFalse(controls.forward());
		assertFalse(controls.sprint());
	}

	@Test
	void leavingUseRangeDoesNotToggleForwardOff() {
		var justInside = decide(4.64, 4.20, true, true, true, true, 0.0, false);
		var justOutside = decide(4.66, 4.20, true, true, true, true, 0.0, false);
		assertTrue(justInside.use());
		assertFalse(justOutside.use());
		assertTrue(justInside.forward());
		assertTrue(justOutside.forward());
	}

	@Test
	void vacuumSafetyGatesDoNotInterruptAnOuterBandChase() {
		assertChasesWithoutUse(decide(4.0, 4.0, false, true, true, true, 0.0, false));
		assertChasesWithoutUse(decide(4.0, 4.0, true, true, false, true, 0.0, false));
		assertChasesWithoutUse(decide(4.0, 4.0, true, true, true, false, 0.0, false));
	}

	@Test
	void wideHeadingGatePreventsBlindForwardMotionWhileTheCameraTurns() {
		var controls = decide(4.0, 4.0, true, false, false, true, 0.0, false);
		assertFalse(controls.use());
		assertFalse(controls.forward());
		assertFalse(controls.sprint());
	}

	@Test
	void blockedLineOfSightClosesPastTheNormalStandoff() {
		var controls = decide(2.2, 2.0, false, true, true, true, 0.0, false);
		assertFalse(controls.use());
		assertTrue(controls.forward());
	}

	@Test
	void sprintIsOnlyUsedFarOutsideVacuumRange() {
		assertTrue(decide(9.0, 9.0, true, true, true, true, 0.0, false).sprint());
		var longRangeOuterBand = PestMovingTargetControl.decide(14.0, 14.0, 15.0,
			true, true, true, true, false, 0.0, false);
		assertTrue(longRangeOuterBand.forward());
		assertFalse(longRangeOuterBand.sprint());
		assertFalse(decide(9.0, 9.0, true, false, true, true, 0.0, false).sprint());
	}

	@Test
	void verticalInputsPreserveCollisionAndTurnSafety() {
		var ascending = decide(8.0, 8.0, true, true, true, true, 1.0, false);
		assertTrue(ascending.jump());
		assertFalse(ascending.descend());

		var descendingAligned = decide(8.0, 8.0, true, true, true, true, -1.0, false);
		assertFalse(descendingAligned.jump());
		assertTrue(descendingAligned.descend());

		var turning = decide(8.0, 8.0, true, false, true, true, -1.0, false);
		assertFalse(turning.forward());
		assertFalse(turning.descend());

		var collision = decide(8.0, 8.0, false, true, true, true, -1.0, true);
		assertTrue(collision.jump());
		assertFalse(collision.descend());
	}

	@Test
	void knownVacuumRangesProduceAConservativeUseBandAndTwoBlockStandoff() {
		assertEquals(4.65, PestMovingTargetControl.reliableUseRange(5.0), 1.0e-9);
		assertEquals(3.0, PestMovingTargetControl.innerStandoff(5.0), 1.0e-9);
		assertEquals(14.65, PestMovingTargetControl.reliableUseRange(15.0), 1.0e-9);
		assertEquals(13.0, PestMovingTargetControl.innerStandoff(15.0), 1.0e-9);
	}

	@Test
	void approachDistanceUsesStartStopHysteresis() {
		var idleInsideStartBand = PestMovingTargetControl.decide(3.5, 3.5, 5.0,
			true, true, true, true, false, 0.0, false);
		var heldInsideStartBand = PestMovingTargetControl.decide(3.5, 3.5, 5.0,
			true, true, true, true, true, 0.0, false);
		var heldAtStopBand = PestMovingTargetControl.decide(3.0, 3.0, 5.0,
			true, true, true, true, true, 0.0, false);
		assertFalse(idleInsideStartBand.forward());
		assertTrue(heldInsideStartBand.forward());
		assertFalse(heldAtStopBand.forward());
	}

	@Test
	void headingUsesStartStopHysteresis() {
		assertFalse(PestMovingTargetControl.isMovementHeadingAligned(0.0, 40.0, false));
		assertTrue(PestMovingTargetControl.isMovementHeadingAligned(0.0, 40.0, true));
		assertFalse(PestMovingTargetControl.isMovementHeadingAligned(0.0, 55.0, true));
	}

	@Test
	void oneFrameLineOfSightRecoveryDoesNotTapForward() {
		PestLineOfSightGate sight = new PestLineOfSightGate();
		sight.reset(true);
		boolean forward = false;
		for (boolean clear : new boolean[] {false, true, false, true, false, true, true}) {
			boolean stableSight = sight.observe(clear);
			forward = PestMovingTargetControl.decide(2.6, 2.5, 5.0,
				stableSight, true, true, true, forward, 0.0, false).forward();
			assertTrue(forward);
		}
		boolean stableSight = sight.observe(true);
		forward = PestMovingTargetControl.decide(2.6, 2.5, 5.0,
			stableSight, true, true, true, forward, 0.0, false).forward();
		assertFalse(forward, "stable clear sight may stop at the normal vacuum standoff");
	}

	@Test
	void invalidNumericInputsCannotLeakIntoMovementKeys() {
		assertThrows(IllegalArgumentException.class,
			() -> decide(Double.NaN, 4.0, true, true, true, true, 0.0, false));
		assertThrows(IllegalArgumentException.class,
			() -> decide(4.0, Double.POSITIVE_INFINITY, true, true, true, true, 0.0, false));
		assertThrows(IllegalArgumentException.class,
			() -> PestMovingTargetControl.decide(4.0, 4.0, 0.0,
				true, true, true, true, false, 0.0, false));
		assertThrows(IllegalArgumentException.class,
			() -> decide(4.0, 4.0, true, true, true, true, Double.NaN, false));
	}

	private static PestMovingTargetControl.Controls decide(
		double distance,
		double horizontalDistance,
		boolean lineOfSight,
		boolean movementAligned,
		boolean vacuumAligned,
		boolean targetFresh,
		double deltaY,
		boolean horizontalCollision
	) {
		return PestMovingTargetControl.decide(distance, horizontalDistance, 5.0,
			lineOfSight, movementAligned, vacuumAligned, targetFresh, false, deltaY, horizontalCollision);
	}

	private static void assertChasesWithoutUse(PestMovingTargetControl.Controls controls) {
		assertFalse(controls.use());
		assertTrue(controls.forward());
	}
}
