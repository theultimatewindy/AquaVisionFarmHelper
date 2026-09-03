package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PestNavigationMathTest {
	private static final double EPSILON = 1.0e-9;

	@Test
	void locatorArrivalIsTighterThanPlotArrival() {
		assertFalse(PestNavigationMath.isAtTrailWaypoint(8.0, 0.0));
		assertFalse(PestNavigationMath.isAtTrailWaypoint(2.01, 0.0));
		assertFalse(PestNavigationMath.isAtTrailWaypoint(0.0, 2.51));
		assertTrue(PestNavigationMath.isAtTrailWaypoint(2.0, 2.5));
		assertFalse(PestNavigationMath.isAtTrailWaypoint(Double.NaN, 0.0));
	}

	@Test
	void flightWaitsForTheCameraToFaceItsDestination() {
		assertFalse(PestNavigationMath.isHeadingAligned(0.0, 180.0, 35.0));
		assertFalse(PestNavigationMath.isHeadingAligned(0.0, 35.01, 35.0));
		assertTrue(PestNavigationMath.isHeadingAligned(0.0, 35.0, 35.0));
		assertTrue(PestNavigationMath.isHeadingAligned(179.0, -179.0, 15.0));
		assertFalse(PestNavigationMath.isHeadingAligned(Double.NaN, 0.0, 35.0));
	}

	@Test
	void calculatesMinecraftYawForCardinalDirections() {
		assertAim(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0);
		assertAim(0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 90.0, 0.0);
		assertAim(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, -90.0, 0.0);
		assertAim(0.0, 0.0, 0.0, 0.0, 0.0, -1.0, -180.0, 0.0);
	}

	@Test
	void calculatesPitchFromEyeToTarget() {
		assertAim(0.0, 2.0, 0.0, 0.0, 3.0, 1.0, 0.0, -45.0);
		assertAim(0.0, 2.0, 0.0, 0.0, 1.0, 1.0, 0.0, 45.0);
	}

	@Test
	void approachesAnglesAcrossWrapBoundary() {
		assertEquals(181.0, PestNavigationMath.approachDegrees(179.0, -179.0, 5.0), EPSILON);
		assertEquals(10.0, PestNavigationMath.approachDegrees(0.0, 90.0, 10.0), EPSILON);
		assertEquals(-10.0, PestNavigationMath.approachDegrees(0.0, -90.0, 10.0), EPSILON);
		assertThrows(
			IllegalArgumentException.class,
			() -> PestNavigationMath.approachDegrees(0.0, 10.0, -1.0)
		);
	}

	@Test
	void checksThreeDimensionalAndHorizontalProximity() {
		assertEquals(25.0, PestNavigationMath.distanceSquared(0, 0, 0, 3, 4, 0), EPSILON);
		assertTrue(PestNavigationMath.isWithinDistance(0, 0, 0, 3, 4, 0, 5.0));
		assertFalse(PestNavigationMath.isWithinDistance(0, 0, 0, 3, 4, 0, 4.99));
		assertTrue(PestNavigationMath.isWithinHorizontalDistance(0, 0, 3, 4, 5.0));
		assertThrows(
			IllegalArgumentException.class,
			() -> PestNavigationMath.isWithinHorizontalDistance(0, 0, 0, 0, Double.NaN)
		);
	}

	private static void assertAim(
		double fromX,
		double fromY,
		double fromZ,
		double targetX,
		double targetY,
		double targetZ,
		double expectedYaw,
		double expectedPitch
	) {
		var angles = PestNavigationMath.aimAt(fromX, fromY, fromZ, targetX, targetY, targetZ);
		assertEquals(expectedYaw, angles.yawDegrees(), EPSILON);
		assertEquals(expectedPitch, angles.pitchDegrees(), EPSILON);
	}
}
