package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PestLocatorCycleTest {
	@Test void singleClickWaitsForReleasedUseAndVanillaReadiness() {
		PestLocatorCycle cycle = new PestLocatorCycle();
		cycle.beginSearch(10);
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(10, true, 0, false));
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(11, false, 0, false));
		assertEquals(PestLocatorCycle.Action.CLICK, cycle.tick(12, true, 0, false));
		for (int tick = 13; tick < 52; tick++) {
			assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(tick, true, 0, false));
		}
		assertEquals(1, cycle.clickCount());
	}

	@Test void collectsBeyondFirstTwoParticlesAndWaitsForQuiet() {
		PestLocatorCycle cycle = clickedCycle();
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(3, true, 2, false));
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(9, true, 5, true));
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(11, true, 8, true));
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(16, true, 8, true));
		assertEquals(PestLocatorCycle.Action.FOLLOW, cycle.tick(17, true, 8, true));
		assertEquals(PestLocatorCycle.State.FOLLOWING, cycle.state());
	}

	@Test void captureHasMinimumAndMaximumDurations() {
		PestLocatorCycle cycle = clickedCycle();
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(2, true, 3, true));
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(10, true, 3, true));
		assertEquals(PestLocatorCycle.Action.FOLLOW, cycle.tick(11, true, 3, true));
		cycle = clickedCycle();
		for (int tick = 2; tick <= 40; tick++) {
			assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(tick, true, tick, true));
		}
		assertEquals(PestLocatorCycle.Action.FOLLOW, cycle.tick(41, true, 41, true));
	}

	@Test void missingTrailRetriesOnlyAfterGlobalCooldown() {
		PestLocatorCycle cycle = clickedCycle();
		assertEquals(PestLocatorCycle.Action.NO_TRAIL, cycle.tick(41, true, 2, false));
		for (int tick = 42; tick < 81; tick++) {
			assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(tick, true, 0, false));
		}
		assertEquals(PestLocatorCycle.Action.CLICK, cycle.tick(81, true, 0, false));
	}

	@Test void doesNotClickDuringFollowingEvenAfterCooldown() {
		PestLocatorCycle cycle = clickedCycle();
		cycle.tick(2, true, 3, true);
		assertEquals(PestLocatorCycle.Action.FOLLOW, cycle.tick(11, true, 3, true));
		for (int tick = 12; tick < 171; tick++) {
			assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(tick, true, 0, false));
		}
		assertEquals(PestLocatorCycle.Action.FOLLOW_TIMEOUT, cycle.tick(171, true, 0, false));
		assertEquals(PestLocatorCycle.Action.CLICK, cycle.tick(172, true, 0, false));
	}

	@Test void targetLossClearsOldCaptureButPreservesCooldown() {
		PestLocatorCycle cycle = clickedCycle();
		cycle.suspend(); // first pest acquired
		cycle.beginSearch(35); // first pest disappears; another is not loaded yet
		assertEquals(PestLocatorCycle.State.WAITING, cycle.state());
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(36, true, 0, false));
		assertEquals(PestLocatorCycle.Action.CLICK, cycle.tick(81, true, 0, false));
		assertEquals(2, cycle.clickCount());
	}

	@Test void longVacuumPeriodCannotBecomeExpiredCaptureTimer() {
		PestLocatorCycle cycle = clickedCycle();
		cycle.suspend();
		cycle.beginSearch(500);
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(500, true, 0, false));
		assertEquals(PestLocatorCycle.Action.CLICK, cycle.tick(501, true, 0, false));
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(502, true, 0, false));
	}

	@Test void arrivalAndPlotChangesDoNotBypassClickSpacing() {
		PestLocatorCycle cycle = clickedCycle();
		cycle.beginSearch(15);
		cycle.suspend();
		cycle.beginSearch(20);
		assertEquals(60, cycle.cooldownTicks(21));
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(21, true, 0, false));
	}

	@Test void cancelStopsClicksAndNewCleanupStartsClean() {
		PestLocatorCycle cycle = clickedCycle();
		cycle.reset();
		assertEquals(PestLocatorCycle.Action.WAIT, cycle.tick(500, true, 100, true));
		assertEquals(0, cycle.clickCount());
		cycle.beginSearch(0);
		assertEquals(PestLocatorCycle.Action.CLICK, cycle.tick(1, true, 0, false));
	}

	private static PestLocatorCycle clickedCycle() {
		PestLocatorCycle cycle = new PestLocatorCycle();
		cycle.beginSearch(0);
		assertEquals(PestLocatorCycle.Action.CLICK, cycle.tick(1, true, 0, false));
		return cycle;
	}
}
