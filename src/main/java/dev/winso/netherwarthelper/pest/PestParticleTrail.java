package dev.winso.netherwarthelper.pest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Filters the angry-villager particle chain emitted by a Garden vacuum's pest
 * locator and estimates a point beyond the trail's current endpoint.
 */
public final class PestParticleTrail {
	public static final double FIRST_PARTICLE_MAX_DISTANCE = 5.0;
	public static final double NEXT_PARTICLE_MAX_DISTANCE = 1.75;
	public static final double ENDPOINT_EXTRAPOLATION_DISTANCE = 10.0;
	public static final int MAX_PARTICLE_POINTS = 256;

	private static final double MINIMUM_DIRECTION_LENGTH_SQUARED = 1.0e-12;
	private static final double MINIMUM_PARTICLE_SEPARATION_SQUARED = 0.01 * 0.01;
	private static final double RELIABLE_DIRECTION_LENGTH_SQUARED = 1.0;

	private final List<Point> points = new ArrayList<>();

	/**
	 * Attempts to append a particle. The first point must be close to the player;
	 * later points must continue the existing chain.
	 */
	public boolean tryAdd(Point particle, Point playerPosition) {
		if (particle == null || playerPosition == null || !particle.isFinite() || !playerPosition.isFinite()) {
			return false;
		}

		Point anchor = points.isEmpty() ? playerPosition : points.getLast();
		double maximumDistance = points.isEmpty()
			? FIRST_PARTICLE_MAX_DISTANCE
			: NEXT_PARTICLE_MAX_DISTANCE;
		if (particle.distanceSquaredTo(anchor) > maximumDistance * maximumDistance) {
			return false;
		}
		for (Point accepted : points) {
			if (particle.distanceSquaredTo(accepted) < MINIMUM_PARTICLE_SEPARATION_SQUARED) {
				return false;
			}
		}

		if (points.size() == MAX_PARTICLE_POINTS) {
			// Keep the original bearing anchor while allowing a long trail's tip to advance.
			points.remove(1);
		}
		points.add(particle);
		return true;
	}

	/**
	 * Whether enough distinct particles span at least one block to navigate from
	 * this trail, instead of treating its first small particle burst as a heading.
	 */
	public boolean hasReliableDirection() {
		return points.size() >= 3
			&& points.getFirst().distanceSquaredTo(points.getLast()) >= RELIABLE_DIRECTION_LENGTH_SQUARED;
	}

	public boolean tryAdd(
		double particleX,
		double particleY,
		double particleZ,
		double playerX,
		double playerY,
		double playerZ
	) {
		return tryAdd(
			new Point(particleX, particleY, particleZ),
			new Point(playerX, playerY, playerZ)
		);
	}

	/**
	 * Extends the horizontal components of the first-to-last direction by ten
	 * blocks, keeping the last particle's height rather than projecting upward
	 * or downward beyond the observed trail.
	 */
	public Optional<Point> estimatedEndpoint() {
		if (points.size() < 2) {
			return Optional.empty();
		}

		Point first = points.getFirst();
		Point last = points.getLast();
		double deltaX = last.x() - first.x();
		double deltaY = last.y() - first.y();
		double deltaZ = last.z() - first.z();
		double lengthSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
		if (lengthSquared <= MINIMUM_DIRECTION_LENGTH_SQUARED) {
			return Optional.empty();
		}

		double scale = ENDPOINT_EXTRAPOLATION_DISTANCE / Math.sqrt(lengthSquared);
		return Optional.of(new Point(
			last.x() + deltaX * scale,
			last.y(),
			last.z() + deltaZ * scale
		));
	}

	public List<Point> points() {
		return List.copyOf(points);
	}

	public int size() {
		return points.size();
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	public void clear() {
		points.clear();
	}

	public record Point(double x, double y, double z) {
		private boolean isFinite() {
			return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
		}

		public double distanceSquaredTo(Point other) {
			return PestNavigationMath.distanceSquared(x, y, z, other.x, other.y, other.z);
		}
	}
}
