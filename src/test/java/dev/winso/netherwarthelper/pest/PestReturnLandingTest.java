package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PestReturnLandingTest {
	@Test void touchingTheFloorSlightlyShortOfTheSavedLaneDoesNotRequireAnotherJump() {
		assertTrue(PestNavigationMath.canFinishReturnOnGround(true, 0.4, 0));
		assertTrue(PestNavigationMath.canFinishReturnOnGround(true, 1.25, 0.125));
		assertFalse(PestNavigationMath.canFinishReturnOnGround(true, 0.09, 14.0 / 16.0),
			"The raised soul-sand edge is not the saved farming floor");
		assertFalse(PestNavigationMath.isWithinFinalReturnHeight(true, 14.0 / 16.0));
		assertFalse(PestNavigationMath.isWithinFinalReturnHeight(false, 14.0 / 16.0),
			"Airborne alignment is never a completed landing");
	}

	@Test void groundedLandingExceptionDoesNotAuthorizeWalkingTheOutboundRouteOrWrongFloor() {
		assertFalse(PestNavigationMath.canFinishReturnOnGround(false, 0.4, 0));
		assertFalse(PestNavigationMath.canFinishReturnOnGround(true, 2, 0));
		assertFalse(PestNavigationMath.canFinishReturnOnGround(true, 0.4, 0.1251));
		assertFalse(PestNavigationMath.canFinishReturnOnGround(true, Double.NaN, 0));
		assertFalse(PestNavigationMath.canFinishReturnOnGround(true, 0, Double.POSITIVE_INFINITY));
	}

	@Test void airborneAlignmentNeverCompletesBeforeActualLanding() {
		assertFalse(PestNavigationMath.hasLandedAtSavedLane(true, false, 0.02, 0.0));
		assertFalse(PestNavigationMath.hasLandedAtSavedLane(true, false, 0.02, 0.1));
		assertFalse(PestNavigationMath.hasLandedAtSavedLane(true, false, 0.02, 0.75),
			"The former airborne completion band must keep descending");
		assertFalse(PestNavigationMath.hasLandedAtSavedLane(false, true, 0.02, 14.0 / 16.0),
			"Ground contact alone is insufficient until horizontal braking settles");
		assertFalse(PestNavigationMath.hasLandedAtSavedLane(true, true, 0.02, 14.0 / 16.0));
		assertTrue(PestNavigationMath.hasLandedAtSavedLane(true, true, 0.02, 0.125));
	}

	@Test void finalDescentContinuesThroughTheOldAirborneTolerance() {
		assertTrue(PestNavigationMath.shouldContinueFinalDescent(true, false, 68.75, 68.0));
		assertTrue(PestNavigationMath.shouldContinueFinalDescent(true, false, 68.0, 68.0));
		assertFalse(PestNavigationMath.shouldContinueFinalDescent(true, true, 68.875, 68.0));
		assertFalse(PestNavigationMath.shouldContinueFinalDescent(false, false, 75.0, 68.0));
		assertFalse(PestNavigationMath.shouldContinueFinalDescent(true, false, 67.874, 68.0),
			"A missing floor must not cause an unlimited descent below the saved lane");
	}

	@Test void finalDescentLatchSurvivesSmallFlightDriftButNotLaneEscape() {
		boolean committed = PestNavigationMath.nextFinalDescentCommitted(false, true, 0.02);
		assertTrue(committed);
		committed = PestNavigationMath.nextFinalDescentCommitted(committed, false, 0.55);
		assertTrue(committed, "A transient loss of tight settling must not restart ascent");
		assertTrue(PestNavigationMath.nextFinalDescentCommitted(committed, false, 1.25));
		assertFalse(PestNavigationMath.nextFinalDescentCommitted(committed, false, 1.2501));
		assertFalse(PestNavigationMath.nextFinalDescentCommitted(false, true, Double.NaN));
	}

	@Test void missingLandingSurfaceIsDetectedBelowTheRecordedSafetyMargin() {
		assertFalse(PestNavigationMath.passedFinalDescentFloor(67.875, 68.0));
		assertTrue(PestNavigationMath.passedFinalDescentFloor(67.874, 68.0));
		assertFalse(PestNavigationMath.passedFinalDescentFloor(Double.NaN, 68.0));
	}

	@Test void committedAirborneDescentStillRecoversUnexpectedFlightLoss() {
		assertFalse(PestNavigationMath.canHandleFinalReturnWithoutFlightRecovery(
			true, false, false, false, false));
		assertTrue(PestNavigationMath.canHandleFinalReturnWithoutFlightRecovery(
			true, false, true, false, false));
		assertTrue(PestNavigationMath.canHandleFinalReturnWithoutFlightRecovery(
			true, true, false, false, false));
		assertTrue(PestNavigationMath.canHandleFinalReturnWithoutFlightRecovery(
			false, true, false, true, false));
	}
}
