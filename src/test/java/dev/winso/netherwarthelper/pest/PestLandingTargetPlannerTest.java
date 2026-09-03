package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PestLandingTargetPlannerTest {
	private static final double EPSILON = 1.0e-9;

	@Test
	void yawNinetyFirstMovesAwayFromTheNegativeXCropFace() {
		List<PestLandingTargetPlanner.Point> candidates = PestLandingTargetPlanner.candidates(
			-119.667956, 94.716618, 90.0, 0.10);
		assertEquals(-119.567956, candidates.getFirst().x(), EPSILON);
		assertEquals(94.716618, candidates.getFirst().z(), EPSILON);
		assertEquals(new PestLandingTargetPlanner.Point(-119.667956, 94.716618), candidates.getLast());
	}

	@Test
	void fallbackCandidatesCircleTheAnchorWithoutExceedingTheInset() {
		var candidates = PestLandingTargetPlanner.candidates(10.0, 20.0, 0.0, 0.10);
		assertEquals(9, candidates.size());
		for (int index = 0; index < candidates.size() - 1; index++) {
			var candidate = candidates.get(index);
			assertEquals(0.10, Math.hypot(candidate.x() - 10.0, candidate.z() - 20.0), EPSILON);
		}
	}

	@Test
	void invalidCoordinatesCannotReachNavigation() {
		assertThrows(IllegalArgumentException.class,
			() -> PestLandingTargetPlanner.candidates(Double.NaN, 0.0, 90.0, 0.1));
		assertThrows(IllegalArgumentException.class,
			() -> PestLandingTargetPlanner.candidates(0.0, 0.0, 90.0, 0.0));
	}

	@Test
	void finalToleranceBudgetsTheInsetInsideTheSavedPositionLimit() {
		assertEquals(0.245, PestLandingTargetPlanner.settleTolerance(0.35, 0.10), EPSILON);
		assertEquals(0.125, PestLandingTargetPlanner.settleTolerance(0.15, 0.02), EPSILON);
		assertThrows(IllegalArgumentException.class,
			() -> PestLandingTargetPlanner.settleTolerance(0.15, 0.0975));
	}

	@Test
	void tightConfigsShrinkTheInsetAndEdgeClearanceContinuesThroughTheSafePoint() {
		assertEquals(0.10, PestLandingTargetPlanner.preferredInset(0.35), EPSILON);
		assertEquals(0.019999, PestLandingTargetPlanner.preferredInset(0.15), EPSILON);
		var target = PestLandingTargetPlanner.edgeClearanceTarget(0.0, 0.0, 0.10, 0.0, 90.0, 0.20);
		assertEquals(0.30, target.x(), EPSILON);
		assertEquals(0.0, target.z(), EPSILON);
	}

	@Test
	void reconstructedMinimumConfigInsetKeepsFloatingPointHeadroom() {
		double savedX = -119.66795621967121;
		double savedZ = 235.8072745569535;
		double inset = PestLandingTargetPlanner.preferredInset(0.15);
		var candidate = PestLandingTargetPlanner.candidates(savedX, savedZ, 90.0, inset).getFirst();
		double reconstructedDistance = Math.hypot(candidate.x() - savedX, candidate.z() - savedZ);
		assertTrue(PestLandingTargetPlanner.settleTolerance(0.15, reconstructedDistance) >= 0.125);
	}
}
