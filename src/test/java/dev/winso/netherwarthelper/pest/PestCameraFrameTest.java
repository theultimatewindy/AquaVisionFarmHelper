package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PestCameraFrameTest {
	private static final double EPSILON = 1.0e-6;

	@Test
	void interpolatesBothAnglesBetweenCompletedTickEndpoints() {
		PestCameraFrame frame = new PestCameraFrame(5, 30, -10, 38, 2);
		assertEquals(30, frame.yawAt(0), EPSILON);
		assertEquals(34, frame.yawAt(0.5), EPSILON);
		assertEquals(38, frame.yawAt(1), EPSILON);
		assertEquals(-10, frame.pitchAt(0), EPSILON);
		assertEquals(-4, frame.pitchAt(0.5), EPSILON);
		assertEquals(2, frame.pitchAt(1), EPSILON);
	}

	@Test
	void wrapBoundaryTakesShortViewTurn() {
		PestCameraFrame frame = new PestCameraFrame(5, 179, 0, -179, 0);
		assertEquals(180, frame.yawAt(0.5), EPSILON);
		assertEquals(181, frame.yawAt(1), EPSILON);
	}

	@Test
	void laterTickOrChangedViewInvalidatesInterpolation() {
		PestCameraFrame frame = new PestCameraFrame(5, 30, -10, 38, 2);
		assertTrue(frame.matches(5, 38, 2));
		assertTrue(frame.matches(5, 398, 2));
		assertFalse(frame.matches(6, 38, 2));
		assertFalse(frame.matches(4, 38, 2));
		assertFalse(frame.matches(5, 39, 2));
		assertFalse(frame.matches(5, 38, 3));
		assertFalse(frame.matches(5, Double.NaN, 2));
	}

	@Test
	void steeringMomentumRequiresConsecutiveTicksAndAnUnchangedView() {
		PestCameraFrame frame = new PestCameraFrame(5, 30, -10, 38, 2);
		assertTrue(frame.canContinueSteeringAt(6, 38, 2));
		assertTrue(frame.canContinueSteeringAt(6, 398, 2));
		assertFalse(frame.canContinueSteeringAt(5, 38, 2));
		assertFalse(frame.canContinueSteeringAt(7, 38, 2));
		assertFalse(frame.canContinueSteeringAt(100, 38, 2));
		assertFalse(frame.canContinueSteeringAt(6, 40, 2));
		assertFalse(frame.canContinueSteeringAt(6, 38, 4));
	}

	@Test
	void locatorHoverDropsMomentumBeforeAimingAtTheOppositeTarget() {
		PestAimSmoother smoother = new PestAimSmoother();
		double yaw = 0;
		for (int tick = 1; tick <= 4; tick++) {
			yaw = smoother.step(yaw, 0, 120, 0, 12, 8).yawDegrees();
		}
		PestCameraFrame frame = new PestCameraFrame(4, yaw - 12, 0, yaw, 0);
		assertFalse(frame.canContinueSteeringAt(30, yaw, 0));
		if (!frame.canContinueSteeringAt(30, yaw, 0)) {
			smoother.reset();
		}
		assertTrue(smoother.step(yaw, 0, -60, 0, 12, 8).yawDegrees() < yaw);
	}

	@Test
	void unusualFrameFractionsRemainFiniteAndWithinTurn() {
		PestCameraFrame frame = new PestCameraFrame(5, 30, -10, 38, 2);
		assertEquals(30, frame.yawAt(-1), EPSILON);
		assertEquals(38, frame.yawAt(2), EPSILON);
		assertEquals(38, frame.yawAt(Double.NaN), EPSILON);
		assertEquals(2, frame.pitchAt(Double.POSITIVE_INFINITY), EPSILON);
		assertEquals(-10, frame.pitchAt(Double.NEGATIVE_INFINITY), EPSILON);
		assertThrows(IllegalArgumentException.class, () -> new PestCameraFrame(5, 0, 0, Double.NaN, 0));
	}
}
