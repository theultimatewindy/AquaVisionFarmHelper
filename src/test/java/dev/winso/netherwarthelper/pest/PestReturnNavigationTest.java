package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.winso.netherwarthelper.pest.PestReturnRoute.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/**
 * Pure horizontal integration of route selection, fixed-leg camera steering and
 * ordinary key acceleration/drag. It does not simulate terrain, vertical flight,
 * server corrections, or the Minecraft input-tick callback wiring.
 */
class PestReturnNavigationTest {
	private static final double ACCELERATION = 0.049;
	private static final double DRAG = 0.91;
	private static final double TOLERANCE = 0.25;
	private static final Point ANCHOR = new Point(0.0, 90.0, 0.0);
	private static final BiPredicate<Point, Point> CLEAR = (from, to) -> true;

	@Test
	void clearReturnSkipsFiftySixBreadcrumbChaseLoopsAndSettlesAtAnchor() {
		List<Point> crumbs = new ArrayList<>();
		crumbs.add(ANCHOR);
		for (int index = 1; index < 55; index++) {
			double angle = index * Math.PI / 4.0;
			crumbs.add(new Point(55.0 + 20.0 * Math.cos(angle), 90.0, 20.0 * Math.sin(angle)));
		}
		crumbs.add(new Point(40.0, 90.0, 0.0));
		Flight flight = new Flight(40.0, 0.0, 0.0, 0.0, 90.0);
		assertEquals(56, crumbs.size());
		int next = PestReturnRoute.nextIndex(crumbs, crumbs.size() - 1, flight.position(), CLEAR);
		assertEquals(0, next);
		assertTrue(PestReturnRoute.timeoutTicks(crumbs, flight.position()) > 60 * 20);
		int ticks = flight.flyLeg(crumbs.get(next), true, 0.0, 300);
		assertTrue(ticks < 300, "The clear shortcut should reach the anchor without replaying chase loops");
		assertTrue(flight.travelled < 45.0, "A clear forty-block return should not trace the long hunt route");
		flight.assertCoastsInside(ANCHOR);
	}

	@Test
	void fixedLegHeadingProducesAStraightClearCorridorAfterInitialCameraSettling() {
		for (double initialYaw : new double[] {0.0, 90.0, 175.0, -120.0}) {
			Flight flight = new Flight(40.0, 0.0, 0.0, 0.0, initialYaw);
			List<Point> route = List.of(ANCHOR, flight.position());
			int next = PestReturnRoute.nextIndex(route, 1, flight.position(), CLEAR);
			assertEquals(0, next);
			assertTrue(flight.flyLeg(route.get(next), true, 0.0, 300) < 300);
			assertTrue(flight.maximumLateLateralError <= 0.22,
				"Resting straight-line start should stay within roughly 0.2 blocks after settling; yaw="
					+ initialYaw + ", lateral=" + flight.maximumLateLateralError);
			assertTrue(flight.cameraTravel < 360.0, "A return must not make a full pursuit circle");
			assertEquals(1, flight.desiredHeadingChanges,
				"Camera aim should change once from the fixed leg heading to the saved farming heading");
			flight.assertCoastsInside(ANCHOR);
		}
	}

	@Test
	void inheritedSidewaysMomentumBrakesWithoutCirclingTheFinalAnchor() {
		Flight flight = new Flight(8.0, 0.0, 0.0, 0.8, -90.0);
		List<Point> route = List.of(ANCHOR, flight.position());
		assertEquals(0, PestReturnRoute.nextIndex(route, 1, flight.position(), CLEAR));
		assertTrue(flight.flyLeg(ANCHOR, true, 90.0, 250) < 250);
		assertTrue(flight.travelled < 23.0, "Inherited sideways motion should brake rather than orbit indefinitely");
		assertTrue(flight.cameraTravel < 360.0);
		flight.assertCoastsInside(ANCHOR);
	}

	@Test
	void savedHeadingStaysLatchedWhenInheritedSprintMomentumLeavesTheTwoBlockArea() {
		Flight flight = new Flight(2.0, 0.0, 1.0, 0.6, -90.0);
		List<Point> route = List.of(ANCHOR, flight.position());
		assertEquals(0, PestReturnRoute.nextIndex(route, 1, flight.position(), CLEAR));
		assertTrue(flight.flyLeg(ANCHOR, true, 0.0, 250) < 250);
		assertTrue(flight.retainedSavedHeadingOutsideTwoBlocks,
			"This regression must exercise outward drift after entering the final heading area");
		assertEquals(1, flight.desiredHeadingChanges,
			"Crossing back outside two blocks must not restore the old leg heading");
		flight.assertCoastsInside(ANCHOR);
	}

	@Test
	void blockedAnchorUsesClearIntermediateSegmentsBeforeReturning() {
		// Synthetic wall at x=4, z=-2..4. The caller's predicate approves entire segments.
		BiPredicate<Point, Point> avoidsWall = (from, to) -> {
			double deltaX = to.x() - from.x();
			if (Math.abs(deltaX) < 1.0e-9) return Math.abs(from.x() - 4.0) > 1.0e-9;
			double fraction = (4.0 - from.x()) / deltaX;
			if (fraction < 0.0 || fraction > 1.0) return true;
			double zAtWall = from.z() + fraction * (to.z() - from.z());
			return zAtWall < -2.0 || zAtWall > 4.0;
		};
		List<Point> route = List.of(ANCHOR, new Point(0, 90, 8), new Point(8, 90, 8), new Point(8, 90, 0));
		Flight flight = new Flight(8.0, 0.0, 0.0, 0.0, 0.0);
		int index = PestReturnRoute.nextIndex(route, 3, flight.position(), avoidsWall);
		assertEquals(2, index);
		for (int legs = 0; legs < 4; legs++) {
			Point destination = route.get(index);
			assertTrue(avoidsWall.test(flight.position(), destination));
			assertTrue(flight.flyLeg(destination, index == 0, 90.0, 250) < 250);
			if (index == 0) break;
			assertTrue(PestReturnRoute.reachedIntermediate(flight.position(), destination));
			index = PestReturnRoute.nextIndex(route, index, flight.position(), avoidsWall);
		}
		assertEquals(0, index, "Clear intermediate segments should eventually expose the anchor");
		flight.assertCoastsInside(ANCHOR);
	}

	private static final class Flight {
		private double x;
		private double z;
		private double vx;
		private double vz;
		private double yaw;
		private double travelled;
		private double cameraTravel;
		private double maximumLateLateralError;
		private int desiredHeadingChanges;
		private boolean retainedSavedHeadingOutsideTwoBlocks;
		private final PestAimSmoother camera = new PestAimSmoother();

		private Flight(double x, double z, double vx, double vz, double yaw) {
			this.x = x;
			this.z = z;
			this.vx = vx;
			this.vz = vz;
			this.yaw = yaw;
		}

		private Point position() {
			return new Point(x, 90.0, z);
		}

		private int flyLeg(Point target, boolean finalAnchor, double savedYaw, int maximumTicks) {
			double fixedHeading = Math.toDegrees(Math.atan2(-(target.x() - x), target.z() - z));
			double previousDesiredHeading = fixedHeading;
			boolean facingSavedHeading = false;
			boolean legHeadingAcquired = false;
			for (int tick = 0; tick < maximumTicks; tick++) {
				double distance = Math.hypot(target.x() - x, target.z() - z);
				if (finalAnchor && distance <= 2.0) facingSavedHeading = true;
				if (facingSavedHeading && distance > 2.0) retainedSavedHeadingOutsideTwoBlocks = true;
				double desiredHeading = facingSavedHeading ? savedYaw : fixedHeading;
				if (Math.abs(PestNavigationMath.wrapDegrees(desiredHeading - previousDesiredHeading)) > 1.0e-6) {
					desiredHeadingChanges++;
				}
				previousDesiredHeading = desiredHeading;
				double previousYaw = yaw;
				yaw = camera.step(yaw, 0.0, desiredHeading, 0.0, 8.0, 8.0).yawDegrees();
				cameraTravel += Math.abs(PestNavigationMath.wrapDegrees(yaw - previousYaw));
				if (!facingSavedHeading) {
					legHeadingAcquired = PestReturnSteering.isLegHeadingAcquired(yaw, fixedHeading, legHeadingAcquired);
				}
				PestReturnSteering.Controls controls = !facingSavedHeading && !legHeadingAcquired
					? new PestReturnSteering.Controls(false, false, false, false, false)
					: PestReturnSteering.steer(target.x() - x, target.z() - z,
						vx, vz, yaw, ACCELERATION, DRAG, TOLERANCE);
				assertFalse(controls.forward() && controls.backward());
				assertFalse(controls.left() && controls.right());
				if (controls.settled()) return tick;
				move(controls);
				if (tick >= 25) maximumLateLateralError = Math.max(maximumLateLateralError, Math.abs(z));
			}
			return maximumTicks;
		}

		private void move(PestReturnSteering.Controls controls) {
			int forward = (controls.forward() ? 1 : 0) - (controls.backward() ? 1 : 0);
			int left = (controls.left() ? 1 : 0) - (controls.right() ? 1 : 0);
			double normalization = Math.max(1.0, Math.hypot(forward, left));
			// 26.2 restores diagonal input to unit length after the 0.98 cardinal-input scaling.
			double acceleration = ACCELERATION / (forward != 0 && left != 0 ? 0.98 : 1.0);
			double radians = Math.toRadians(yaw);
			vx += (-Math.sin(radians) * forward + Math.cos(radians) * left) * acceleration / normalization;
			vz += (Math.cos(radians) * forward + Math.sin(radians) * left) * acceleration / normalization;
			x += vx;
			z += vz;
			travelled += Math.hypot(vx, vz);
			vx *= DRAG;
			vz *= DRAG;
			if (Math.abs(vx) < 0.003) vx = 0.0;
			if (Math.abs(vz) < 0.003) vz = 0.0;
		}

		private void assertCoastsInside(Point target) {
			for (int tick = 0; tick < 60; tick++) {
				move(new PestReturnSteering.Controls(false, false, false, false, false));
				assertTrue(Math.hypot(target.x() - x, target.z() - z) <= TOLERANCE + 1.0e-9,
					"After releasing controls the player must remain within the quarter-block anchor tolerance");
			}
		}
	}
}
