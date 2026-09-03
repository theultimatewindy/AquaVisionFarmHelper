package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.winso.netherwarthelper.pest.PestNavigationMath.AimAngles;
import org.junit.jupiter.api.Test;

class PestAimSmootherTest {
	private static final double EPSILON = 1.0e-4;

	@Test
	void wrapsByShortestAngleWithoutNormalizingCurrentYawBackAcrossBoundary() {
		PestAimSmoother smoother = new PestAimSmoother();
		AimAngles first = smoother.step(179, 0, -179, 0, 10, 10);
		assertTrue(first.yawDegrees() > 179 && first.yawDegrees() < 181);
		AimAngles current = first;
		for (int tick = 0; tick < 60; tick++) {
			current = smoother.step(current.yawDegrees(), current.pitchDegrees(), -179, 0, 10, 10);
		}
		assertEquals(181, current.yawDegrees(), EPSILON);
	}

	@Test
	void steadyTargetAcceleratesAndDeceleratesWithinLimitsWithoutOvershoot() {
		PestAimSmoother smoother = new PestAimSmoother();
		AimAngles current = new AimAngles(0, 0);
		double previousYawStep = 0;
		double previousPitchStep = 0;
		for (int tick = 0; tick < 90; tick++) {
			AimAngles next = smoother.step(current.yawDegrees(), current.pitchDegrees(), 120, 70, 12, 8);
			double yawStep = next.yawDegrees() - current.yawDegrees();
			double pitchStep = next.pitchDegrees() - current.pitchDegrees();
			assertTrue(yawStep >= -EPSILON && yawStep <= 12 + EPSILON);
			assertTrue(pitchStep >= -EPSILON && pitchStep <= 8 + EPSILON);
			assertTrue(Math.abs(yawStep - previousYawStep) <= 3 + EPSILON);
			assertTrue(Math.abs(pitchStep - previousPitchStep) <= 2 + EPSILON);
			assertTrue(next.yawDegrees() <= 120 && next.pitchDegrees() <= 70);
			previousYawStep = yawStep;
			previousPitchStep = pitchStep;
			current = next;
		}
		assertEquals(120, current.yawDegrees(), EPSILON);
		assertEquals(70, current.pitchDegrees(), EPSILON);
	}

	@Test
	void reversalsBrakeBeforeChangingDirectionAndStayBounded() {
		PestAimSmoother smoother = new PestAimSmoother();
		AimAngles current = new AimAngles(0, 0);
		double previousStep = 0;
		for (int tick = 0; tick < 4; tick++) {
			AimAngles next = smoother.step(current.yawDegrees(), 0, 120, 0, 12, 8);
			previousStep = next.yawDegrees() - current.yawDegrees();
			current = next;
		}
		for (int tick = 0; tick < 90; tick++) {
			AimAngles next = smoother.step(current.yawDegrees(), 0, -60, 0, 12, 8);
			double step = next.yawDegrees() - current.yawDegrees();
			assertTrue(Math.abs(step) <= 12 + EPSILON);
			assertTrue(Math.abs(step - previousStep) <= 3 + EPSILON);
			assertTrue(next.yawDegrees() >= -60);
			previousStep = step;
			current = next;
		}
		assertEquals(-60, current.yawDegrees(), EPSILON);
	}

	@Test
	void coincidentDestinationPreservesCurrentAnglesAndClearsDrift() {
		PestAimSmoother smoother = new PestAimSmoother();
		smoother.step(30, 5, 120, 40, 12, 8);
		AimAngles result = smoother.aimAt(30, 5, 1, 2, 3, 1, 2, 3, 12, 8);
		assertEquals(30, result.yawDegrees(), EPSILON);
		assertEquals(5, result.pitchDegrees(), EPSILON);
	}

	@Test
	void verticalDestinationKeepsHeadingAndClampsPitch() {
		PestAimSmoother smoother = new PestAimSmoother();
		AimAngles current = new AimAngles(123, 0);
		for (int tick = 0; tick < 90; tick++) {
			current = smoother.aimAt(current.yawDegrees(), current.pitchDegrees(), 1, 2, 3, 1, 30, 3, 12, 8);
			assertEquals(123, current.yawDegrees(), EPSILON);
			assertTrue(current.pitchDegrees() >= -85 && current.pitchDegrees() <= 85);
		}
		assertEquals(-85, current.pitchDegrees(), EPSILON);
	}

	@Test
	void resettingDropsAngularMomentumForNextCleanup() {
		PestAimSmoother smoother = new PestAimSmoother();
		for (int tick = 0; tick < 4; tick++) {
			smoother.step(0, 0, 120, 70, 12, 8);
		}
		smoother.reset();
		AimAngles result = smoother.step(0, 0, 120, 70, 12, 8);
		assertEquals(3, result.yawDegrees(), EPSILON);
		assertEquals(2, result.pitchDegrees(), EPSILON);
	}

	@Test
	void abruptNearbyTargetNeverOvershootsAndZeroLimitsStopSteering() {
		PestAimSmoother smoother = new PestAimSmoother();
		for (int tick = 0; tick < 4; tick++) {
			smoother.step(0, 0, 120, 70, 12, 8);
		}
		AimAngles close = smoother.step(0, 0, 0.1, 0.1, 12, 8);
		assertEquals(0.1, close.yawDegrees(), EPSILON);
		assertEquals(0.1, close.pitchDegrees(), EPSILON);
		AimAngles frozen = smoother.step(30, 5, 120, 70, 0, 0);
		assertEquals(30, frozen.yawDegrees(), EPSILON);
		assertEquals(5, frozen.pitchDegrees(), EPSILON);
	}

	@Test
	void nonFiniteInputsAndInvalidLimitsCannotPoisonSubsequentAngles() {
		PestAimSmoother smoother = new PestAimSmoother();
		assertThrows(IllegalArgumentException.class, () -> smoother.step(Double.NaN, 0, 0, 0, 12, 8));
		assertThrows(IllegalArgumentException.class, () -> smoother.step(0, 0, Double.POSITIVE_INFINITY, 0, 12, 8));
		assertThrows(IllegalArgumentException.class, () -> smoother.step(0, 0, 0, 0, -1, 8));
		assertThrows(IllegalArgumentException.class, () -> smoother.step(0, 0, 0, 0, 12, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> smoother.aimAt(0, 0, 0, 0, 0, 1, Double.NaN, 1, 12, 8));
		AimAngles result = smoother.step(Double.MAX_VALUE, 0, -Double.MAX_VALUE, 30, 12, 8);
		assertTrue(Double.isFinite(result.yawDegrees()));
		assertTrue(Double.isFinite(result.pitchDegrees()));
	}
}
