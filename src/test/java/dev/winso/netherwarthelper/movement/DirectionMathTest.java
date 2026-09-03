package dev.winso.netherwarthelper.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DirectionMathTest {
	private static final double EPSILON = 1.0e-9;

	@Test
	void localLeftAndRightFollowMinecraftYaw() {
		assertVector(DirectionMath.lateralUnit(0.0, true), 1.0, 0.0);
		assertVector(DirectionMath.lateralUnit(0.0, false), -1.0, 0.0);
		assertVector(DirectionMath.lateralUnit(90.0, true), 0.0, 1.0);
		assertVector(DirectionMath.lateralUnit(90.0, false), 0.0, -1.0);
		assertVector(DirectionMath.lateralUnit(180.0, true), -1.0, 0.0);
		assertVector(DirectionMath.lateralUnit(-90.0, true), 0.0, -1.0);
	}

	@Test
	void localForwardFollowsMinecraftYaw() {
		assertVector(DirectionMath.forwardUnit(0.0), 0.0, 1.0);
		assertVector(DirectionMath.forwardUnit(90.0), -1.0, 0.0);
		assertVector(DirectionMath.forwardUnit(180.0), 0.0, -1.0);
		assertVector(DirectionMath.forwardUnit(-90.0), 1.0, 0.0);
	}

	@Test
	void projectionRejectsMovementInTheOppositeDirection() {
		var expectedLeft = DirectionMath.lateralUnit(0.0, true);
		assertEquals(0.25, DirectionMath.projectedProgress(0.25, 0.0, expectedLeft), EPSILON);
		assertEquals(-0.25, DirectionMath.projectedProgress(-0.25, 0.0, expectedLeft), EPSILON);
	}

	private static void assertVector(DirectionMath.HorizontalVector vector, double x, double z) {
		assertEquals(x, vector.x(), EPSILON);
		assertEquals(z, vector.z(), EPSILON);
	}
}
