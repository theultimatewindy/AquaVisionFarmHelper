package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PestFlightSafetyTest {
	private static final double EPSILON = 1.0e-9;

	@Test
	void turningAwayFromDistantLowTargetDoesNotPermitVerticalDescent() {
		// The heading gate has suppressed W while a 180-degree turn is in progress.
		assertFalse(PestFlightSafety.mayDescendToward(false, 12.0, 1.25));
		assertTrue(PestFlightSafety.mayDescendToward(true, 12.0, 1.25));
	}

	@Test
	void verticalWaypointCanDescendWithoutForwardMovement() {
		assertTrue(PestFlightSafety.mayDescendToward(false, 0.0, 1.25));
		assertTrue(PestFlightSafety.mayDescendToward(false, 1.25, 1.25));
		assertFalse(PestFlightSafety.mayDescendToward(false, 1.2501, 1.25));
	}

	@Test
	void groundProtectionBrakesDescentWithAscentWhenThereIsRoom() {
		assertEquals(new PestFlightSafety.Controls(true, false),
			PestFlightSafety.protect(true, true, false, false, true));
		assertEquals(new PestFlightSafety.Controls(true, false),
			PestFlightSafety.protect(true, true, false, false, false));
		assertEquals(new PestFlightSafety.Controls(true, false),
			PestFlightSafety.protect(true, true, false, true, false));
	}

	@Test
	void lowCeilingStopsBothVerticalInputsWithoutOwningHorizontalMovement() {
		assertEquals(new PestFlightSafety.Controls(false, false),
			PestFlightSafety.protect(true, false, false, true, false));
		assertEquals(new PestFlightSafety.Controls(false, false),
			PestFlightSafety.protect(true, false, false, false, true));
	}

	@Test
	void downwardMomentumExpandsFloorProbeBeforeContact() {
		assertEquals(1.0, PestFlightSafety.groundProbeDistance(0.0), EPSILON);
		assertEquals(1.0, PestFlightSafety.groundProbeDistance(0.375), EPSILON);
		assertEquals(1.675, PestFlightSafety.groundProbeDistance(-0.225), EPSILON);
		assertEquals(2.125, PestFlightSafety.groundProbeDistance(-0.375), EPSILON);
	}

	@Test
	void unavailableVelocityCannotPropagateNanOrInfinity() {
		assertEquals(PestFlightSafety.MIN_GROUND_CLEARANCE,
			PestFlightSafety.groundProbeDistance(Double.NaN));
		assertEquals(PestFlightSafety.MIN_GROUND_CLEARANCE,
			PestFlightSafety.groundProbeDistance(Double.NEGATIVE_INFINITY));
		assertEquals(PestFlightSafety.MIN_GROUND_CLEARANCE,
			PestFlightSafety.groundProbeDistance(Double.POSITIVE_INFINITY));
	}

	@Test
	void finalBreadcrumbDoesNotPermitLandingBeforeHorizontalAlignment() {
		assertFalse(PestFlightSafety.mayLandAtSavedLane(false, 0, 0.0, 0.35));
		assertFalse(PestFlightSafety.mayLandAtSavedLane(true, 1, 0.0, 0.35));
		assertFalse(PestFlightSafety.mayLandAtSavedLane(true, 0, 2.0, 0.35));
		assertFalse(PestFlightSafety.mayLandAtSavedLane(true, 0, 0.26, 0.35));
		assertTrue(PestFlightSafety.mayLandAtSavedLane(true, 0, 0.25, 0.35));
		assertFalse(PestFlightSafety.mayLandAtSavedLane(true, 0, 0.15, 0.1));
		assertTrue(PestFlightSafety.mayLandAtSavedLane(true, 0, 0.1, 0.1));
	}

	@Test
	void finalReturnLandingBypassesFloorProtection() {
		assertEquals(new PestFlightSafety.Controls(false, true),
			PestFlightSafety.protect(true, false, true, false, true));
		assertEquals(new PestFlightSafety.Controls(false, true),
			PestFlightSafety.protect(true, true, true, false, true));
		assertEquals(new PestFlightSafety.Controls(true, false),
			PestFlightSafety.protect(true, false, true, true, false));
	}

	@Test
	void committedFinalDescentNeverCombinesJumpAndShift() {
		assertEquals(new PestFlightSafety.Controls(false, true),
			PestFlightSafety.finalDescentControls(false, 68.75, 68.0));
		assertEquals(new PestFlightSafety.Controls(false, true),
			PestFlightSafety.finalDescentControls(false, 68.0, 68.0));
		assertEquals(new PestFlightSafety.Controls(false, false),
			PestFlightSafety.finalDescentControls(true, 68.875, 68.0));
	}

	@Test
	void diagonalDescentInClearAirKeepsRequestedControls() {
		assertTrue(PestFlightSafety.mayDescendToward(true, 8.0, 1.25));
		assertEquals(new PestFlightSafety.Controls(false, true),
			PestFlightSafety.protect(false, true, false, false, true));
		assertEquals(new PestFlightSafety.Controls(true, false),
			PestFlightSafety.protect(false, true, false, true, false));
		assertEquals(new PestFlightSafety.Controls(false, false),
			PestFlightSafety.protect(false, false, false, false, false));
	}
}
