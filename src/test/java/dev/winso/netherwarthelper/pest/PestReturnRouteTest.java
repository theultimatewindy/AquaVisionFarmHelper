package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.winso.netherwarthelper.pest.PestReturnRoute.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PestReturnRouteTest {
	private static final Point ANCHOR = new Point(-64.7, 68.0, 53.5186);

	@Test
	void clearDirectSegmentChoosesExactOriginalAnchorWithoutMutatingTheRoute() {
		List<Point> route = List.of(ANCHOR, new Point(-80, 90, 50), new Point(-100, 90, 60));
		Point current = new Point(-110, 91, 65);
		assertEquals(0, PestReturnRoute.nextIndex(route, 2, current, (from, to) -> {
			assertEquals(current, from);
			assertEquals(ANCHOR, to);
			return true;
		}));
		assertEquals(ANCHOR, route.getFirst());
		assertEquals(3, route.size());
	}

	@Test
	void blockedShortcutsRetainCurrentIndex() {
		List<Point> route = straightRoute(4, 8.0);
		assertEquals(3, PestReturnRoute.nextIndex(route, 3, new Point(30, 90, 0), (from, to) -> false));
	}

	@Test
	void blockedFinalAnchorCannotBeSkippedEvenWhenAnotherSegmentIsClear() {
		List<Point> route = straightRoute(4, 8.0);
		assertEquals(1, PestReturnRoute.nextIndex(route, 3, new Point(30, 90, 0),
			(from, to) -> to != route.getFirst()));
	}

	@Test
	void closeBreadcrumbsRequireCollisionApprovalBeforeSkipping() {
		Point current = new Point(10, 90, 0);
		List<Point> route = List.of(new Point(0, 90, 0), new Point(9, 90, 0),
			new Point(9.5, 90, 0), new Point(10, 90, 0));
		assertTrue(PestReturnRoute.reachedIntermediate(current, route.get(1)));
		assertEquals(1, PestReturnRoute.nextIndex(route, 3, current, (from, to) -> to.x() >= 9));
		assertEquals(3, PestReturnRoute.nextIndex(route, 3, current, (from, to) -> false));
	}

	@Test
	void neverSelectsALaterPreviouslyCompletedWaypoint() {
		List<Point> route = straightRoute(5, 8.0);
		List<Point> checked = new ArrayList<>();
		assertEquals(2, PestReturnRoute.nextIndex(route, 2, new Point(17, 90, 0), (from, to) -> {
			checked.add(to);
			return false;
		}));
		assertEquals(route.subList(0, 3), checked);
	}

	@Test
	void intermediateAcceptanceUsesSeparateHorizontalAndVerticalTolerances() {
		Point waypoint = new Point(0, 90, 0);
		assertTrue(PestReturnRoute.reachedIntermediate(new Point(2.5, 93, 0), waypoint));
		assertTrue(PestReturnRoute.reachedIntermediate(new Point(0, 88, 0), waypoint));
		assertFalse(PestReturnRoute.reachedIntermediate(new Point(2.5001, 90, 0), waypoint));
		assertFalse(PestReturnRoute.reachedIntermediate(new Point(0, 93.0001, 0), waypoint));
	}

	@Test
	void shortReturnRetainsOneMinuteMinimum() {
		assertEquals(60 * 20, PestReturnRoute.timeoutTicks(List.of(ANCHOR), ANCHOR));
		assertEquals(60 * 20, PestReturnRoute.timeoutTicks(straightRoute(3, 8), new Point(16, 90, 0)));
	}

	@Test
	void fiftySixEightBlockBreadcrumbsReceiveARealisticBudget() {
		List<Point> route = straightRoute(56, 8.0);
		assertEquals(140 * 20, PestReturnRoute.timeoutTicks(route, route.getLast()));
	}

	@Test
	void timeoutIncludesCurrentToLastLegAndRoundsUpToTicks() {
		List<Point> route = List.of(new Point(0, 90, 0), new Point(100, 90, 0));
		assertEquals(65 * 20, PestReturnRoute.timeoutTicks(route, new Point(140, 90, 0)));
		assertEquals(1301, PestReturnRoute.timeoutTicks(route, new Point(140.01, 90, 0)));
	}

	@Test
	void longOrExtremelyLargeFiniteRoutesRemainCappedAtFiveMinutes() {
		assertEquals(300 * 20, PestReturnRoute.timeoutTicks(straightRoute(200, 8), new Point(1592, 90, 0)));
		assertEquals(300 * 20, PestReturnRoute.timeoutTicks(List.of(new Point(-Double.MAX_VALUE, 0, 0)),
			new Point(Double.MAX_VALUE, 0, 0)));
	}

	@Test
	void nonfiniteAndMissingInputsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Point(0, Double.POSITIVE_INFINITY, 0));
		assertThrows(IllegalArgumentException.class, () -> new Point(0, 0, Double.NEGATIVE_INFINITY));
		assertThrows(IllegalArgumentException.class, () -> PestReturnRoute.timeoutTicks(List.of(), ANCHOR));
		assertThrows(IllegalArgumentException.class, () -> PestReturnRoute.timeoutTicks(null, ANCHOR));
		assertThrows(IllegalArgumentException.class, () -> PestReturnRoute.timeoutTicks(Arrays.asList(ANCHOR, null), ANCHOR));
		assertThrows(IllegalArgumentException.class, () -> PestReturnRoute.timeoutTicks(List.of(ANCHOR), null));
		assertThrows(IllegalArgumentException.class, () -> PestReturnRoute.reachedIntermediate(null, ANCHOR));
		assertThrows(IllegalArgumentException.class, () -> PestReturnRoute.nextIndex(List.of(ANCHOR), -1, ANCHOR, (a, b) -> true));
		assertThrows(IllegalArgumentException.class, () -> PestReturnRoute.nextIndex(List.of(ANCHOR), 1, ANCHOR, (a, b) -> true));
		assertThrows(IllegalArgumentException.class, () -> PestReturnRoute.nextIndex(List.of(ANCHOR), 0, ANCHOR, null));
	}

	private static List<Point> straightRoute(int count, double spacing) {
		List<Point> points = new ArrayList<>();
		for (int index = 0; index < count; index++) points.add(new Point(index * spacing, 90.0, 0.0));
		return List.copyOf(points);
	}
}
