package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PestParticleTrailTest {
	private static final double EPSILON = 1.0e-9;
	private static final PestParticleTrail.Point PLAYER = new PestParticleTrail.Point(0.0, 0.0, 0.0);

	@Test
	void firstParticleMustBeWithinFiveBlocksOfPlayer() {
		var trail = new PestParticleTrail();

		assertFalse(trail.tryAdd(new PestParticleTrail.Point(5.01, 0.0, 0.0), PLAYER));
		assertTrue(trail.tryAdd(new PestParticleTrail.Point(3.0, 0.0, 4.0), PLAYER));
		assertEquals(1, trail.size());
	}

	@Test
	void laterParticlesMustContinueWithinOnePointSevenFiveBlocks() {
		var trail = new PestParticleTrail();
		trail.tryAdd(new PestParticleTrail.Point(3.0, 0.0, 4.0), PLAYER);

		assertFalse(trail.tryAdd(new PestParticleTrail.Point(5.0, 0.0, 4.0), PLAYER));
		assertTrue(trail.tryAdd(new PestParticleTrail.Point(4.0, 0.0, 4.0), PLAYER));
		assertEquals(2, trail.points().size());
	}

	@Test
	void extrapolatesTenBlocksPastLastParticle() {
		var trail = new PestParticleTrail();
		trail.tryAdd(new PestParticleTrail.Point(3.0, 0.0, 4.0), PLAYER);
		trail.tryAdd(new PestParticleTrail.Point(4.0, 0.0, 4.0), PLAYER);

		var endpoint = trail.estimatedEndpoint().orElseThrow();
		assertEquals(14.0, endpoint.x(), EPSILON);
		assertEquals(0.0, endpoint.y(), EPSILON);
		assertEquals(4.0, endpoint.z(), EPSILON);

		trail.clear();
		assertTrue(trail.isEmpty());
		assertTrue(trail.estimatedEndpoint().isEmpty());
	}

	@Test
	void requiresTwoDistinctPointsForDirection() {
		var trail = new PestParticleTrail();
		var point = new PestParticleTrail.Point(1.0, 1.0, 1.0);
		trail.tryAdd(point, PLAYER);
		trail.tryAdd(point, PLAYER);

		assertTrue(trail.estimatedEndpoint().isEmpty());
	}

	@Test
	void navigationRequiresThreeDistinctPointsAndOneBlockSpan() {
		var trail = new PestParticleTrail();
		assertFalse(trail.hasReliableDirection());
		assertTrue(trail.tryAdd(new PestParticleTrail.Point(1.0, 0.0, 0.0), PLAYER));
		assertTrue(trail.tryAdd(new PestParticleTrail.Point(1.5, 0.0, 0.0), PLAYER));
		assertTrue(trail.estimatedEndpoint().isPresent());
		assertFalse(trail.hasReliableDirection());
		assertTrue(trail.tryAdd(new PestParticleTrail.Point(1.9, 0.0, 0.0), PLAYER));
		assertFalse(trail.hasReliableDirection());
		assertTrue(trail.tryAdd(new PestParticleTrail.Point(2.0, 0.0, 0.0), PLAYER));
		assertTrue(trail.hasReliableDirection());

		trail.clear();
		assertFalse(trail.hasReliableDirection());
	}

	@Test
	void threePointsWithExactlyOneBlockSpanAreReliable() {
		var trail = new PestParticleTrail();
		trail.tryAdd(new PestParticleTrail.Point(1.0, 0.0, 0.0), PLAYER);
		trail.tryAdd(new PestParticleTrail.Point(1.5, 0.0, 0.0), PLAYER);
		trail.tryAdd(new PestParticleTrail.Point(2.0, 0.0, 0.0), PLAYER);

		assertTrue(trail.hasReliableDirection());
		assertEquals(12.0, trail.estimatedEndpoint().orElseThrow().x(), EPSILON);
	}

	@Test
	void extrapolationPreservesLastParticleHeightAndFullDirectionNormalization() {
		var trail = new PestParticleTrail();
		trail.tryAdd(new PestParticleTrail.Point(1.0, 1.0, 1.0), PLAYER);
		trail.tryAdd(new PestParticleTrail.Point(2.0, 2.0, 2.0), PLAYER);

		var endpoint = trail.estimatedEndpoint().orElseThrow();
		assertEquals(2.0 + 10.0 / Math.sqrt(3.0), endpoint.x(), EPSILON);
		assertEquals(2.0, endpoint.y(), EPSILON);
		assertEquals(2.0 + 10.0 / Math.sqrt(3.0), endpoint.z(), EPSILON);
	}

	@Test
	void duplicatesAndNearZeroMotionDoNotAddEvidence() {
		var trail = new PestParticleTrail();
		var start = new PestParticleTrail.Point(1.0, 0.0, 0.0);
		assertTrue(trail.tryAdd(start, PLAYER));
		assertFalse(trail.tryAdd(start, PLAYER));
		assertFalse(trail.tryAdd(new PestParticleTrail.Point(1.005, 0.0, 0.0), PLAYER));
		assertTrue(trail.tryAdd(new PestParticleTrail.Point(2.0, 0.0, 0.0), PLAYER));
		assertFalse(trail.tryAdd(start, PLAYER));
		assertEquals(2, trail.size());
		assertFalse(trail.hasReliableDirection());
	}

	@Test
	void rejectsNonfiniteParticleAndPlayerCoordinates() {
		var trail = new PestParticleTrail();
		for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			for (var point : new PestParticleTrail.Point[] {
				new PestParticleTrail.Point(invalid, 0.0, 0.0),
				new PestParticleTrail.Point(0.0, invalid, 0.0),
				new PestParticleTrail.Point(0.0, 0.0, invalid)
			}) {
				assertFalse(trail.tryAdd(point, PLAYER));
				assertFalse(trail.tryAdd(PLAYER, point));
			}
		}
		assertTrue(trail.isEmpty());
		assertTrue(trail.tryAdd(new PestParticleTrail.Point(1.0, 0.0, 0.0), PLAYER));
		assertFalse(trail.tryAdd(new PestParticleTrail.Point(2.0, 0.0, 0.0),
			new PestParticleTrail.Point(Double.NaN, 0.0, 0.0)));
		assertEquals(1, trail.size());
	}

	@Test
	void boundsRetainedPointsWithoutFreezingTheTrailsTip() {
		var trail = new PestParticleTrail();
		for (int index = 0; index < 400; index++) {
			assertTrue(trail.tryAdd(new PestParticleTrail.Point(index, 0.0, 0.0), PLAYER));
		}

		assertEquals(PestParticleTrail.MAX_PARTICLE_POINTS, trail.size());
		assertEquals(0.0, trail.points().getFirst().x(), EPSILON);
		assertEquals(399.0, trail.points().getLast().x(), EPSILON);
		assertTrue(trail.hasReliableDirection());
		assertEquals(409.0, trail.estimatedEndpoint().orElseThrow().x(), EPSILON);
	}
}
