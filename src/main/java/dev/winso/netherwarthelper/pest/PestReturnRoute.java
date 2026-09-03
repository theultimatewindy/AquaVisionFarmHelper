package dev.winso.netherwarthelper.pest;

import java.util.List;
import java.util.function.BiPredicate;

/** Pure return-route decisions. Collision approval and player movement belong to the caller. */
public final class PestReturnRoute {
	private static final double MINIMUM_TIMEOUT_SECONDS = 60.0;
	private static final double MAXIMUM_TIMEOUT_SECONDS = 300.0;
	private static final double SETTLING_ALLOWANCE_SECONDS = 30.0;
	private static final double BUDGET_SPEED_BLOCKS_PER_SECOND = 4.0;

	private PestReturnRoute() {
	}

	/**
	 * Selects the earliest remaining breadcrumb reachable by a caller-approved
	 * segment. Index zero remains the original, exact anchor. No point is changed
	 * or skipped solely because it is nearby, and an unapproved route retains its
	 * current index rather than claiming progress.
	 */
	public static int nextIndex(
		List<Point> breadcrumbs,
		int currentIndex,
		Point position,
		BiPredicate<Point, Point> canTravel
	) {
		validateRoute(breadcrumbs);
		requirePoint(position);
		if (currentIndex < 0 || currentIndex >= breadcrumbs.size()) {
			throw new IllegalArgumentException("Current return index must refer to a breadcrumb");
		}
		if (canTravel == null) throw new IllegalArgumentException("A collision approval callback is required");
		for (int index = 0; index <= currentIndex; index++) {
			if (canTravel.test(position, breadcrumbs.get(index))) return index;
		}
		return currentIndex;
	}

	/** Intermediate flight waypoints are not the stricter exact saved-lane landing check. */
	public static boolean reachedIntermediate(Point position, Point waypoint) {
		requirePoint(position);
		requirePoint(waypoint);
		return Math.hypot(position.x() - waypoint.x(), position.z() - waypoint.z()) <= 2.5
			&& Math.abs(position.y() - waypoint.y()) <= 3.0;
	}

	/**
	 * Budgets the full recorded reverse route, including the current-to-last leg.
	 * Collision-approved shortcuts may make the actual trip shorter; they never
	 * reduce this conservative, five-minute-capped allowance.
	 */
	public static int timeoutTicks(List<Point> route, Point current) {
		validateRoute(route);
		requirePoint(current);
		double length = 0.0;
		Point previous = current;
		for (int index = route.size() - 1; index >= 0; index--) {
			Point waypoint = route.get(index);
			length += Math.hypot(Math.hypot(previous.x() - waypoint.x(), previous.y() - waypoint.y()),
				previous.z() - waypoint.z());
			previous = waypoint;
		}
		double seconds = SETTLING_ALLOWANCE_SECONDS + length / BUDGET_SPEED_BLOCKS_PER_SECOND;
		seconds = Math.max(MINIMUM_TIMEOUT_SECONDS, Math.min(MAXIMUM_TIMEOUT_SECONDS, seconds));
		return (int) Math.ceil(seconds * 20.0);
	}

	private static void validateRoute(List<Point> route) {
		if (route == null || route.isEmpty()) throw new IllegalArgumentException("Return route must not be empty");
		for (Point point : route) requirePoint(point);
	}

	private static void requirePoint(Point point) {
		if (point == null) throw new IllegalArgumentException("Return positions must not be null");
	}

	public record Point(double x, double y, double z) {
		public Point {
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				throw new IllegalArgumentException("Return positions must be finite");
			}
		}
	}
}
