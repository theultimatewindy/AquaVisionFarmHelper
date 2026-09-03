package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PestRunDeadlineTest {
	@Test void unfinishedHuntStillHasItsConfiguredDeadline() {
		assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(3600, 3600, -1, false));
		assertEquals(PestRunDeadline.Failure.CLEANUP, PestRunDeadline.check(3601, 3600, -1, false));
	}

	@Test void clearAtDeadlineGetsASeparateReturnBudget() {
		assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(3601, 3600, 3601, false));
		assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(4801, 3600, 3601, false));
		assertEquals(PestRunDeadline.Failure.RETURN, PestRunDeadline.check(4802, 3600, 3601, false));
	}

	@Test void earlyReturnAlsoHasABoundedBudget() {
		assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(1250, 3600, 50, false));
		assertEquals(PestRunDeadline.Failure.RETURN, PestRunDeadline.check(1251, 3600, 50, false));
	}

	@Test void onlyTheBoundedFinalPestCheckMayDelayAHuntTimeout() {
		assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(3601, 3600, -1, true));
		assertEquals(PestRunDeadline.Failure.CLEANUP, PestRunDeadline.check(3801, 3600, -1, false));
		assertEquals(PestRunDeadline.Failure.RETURN, PestRunDeadline.check(1251, 3600, 50, true));
	}

	@Test void routeSpecificBudgetContinuesPastSixtySecondsButStillExpires() {
		int returnStart = 3601;
		int routeBudget = 150 * 20;
		assertEquals(PestRunDeadline.Failure.NONE,
			PestRunDeadline.check(returnStart + 60 * 20 + 1, 3600, returnStart, false, routeBudget));
		assertEquals(PestRunDeadline.Failure.NONE,
			PestRunDeadline.check(returnStart + routeBudget, 3600, returnStart, false, routeBudget));
		assertEquals(PestRunDeadline.Failure.RETURN,
			PestRunDeadline.check(returnStart + routeBudget + 1, 3600, returnStart, false, routeBudget));
	}

	@Test void lastPestGraceCannotExtendRouteSpecificReturnDeadline() {
		assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(2450, 3600, 50, true, 2400));
		assertEquals(PestRunDeadline.Failure.RETURN, PestRunDeadline.check(2451, 3600, 50, true, 2400));
	}

	@Test void returnBudgetClampsToSixtyThroughThreeHundredSeconds() {
		for (int shortBudget : new int[] {Integer.MIN_VALUE, -1, 0, 100, 1199}) {
			assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(1200, 3600, 0, false, shortBudget));
			assertEquals(PestRunDeadline.Failure.RETURN, PestRunDeadline.check(1201, 3600, 0, false, shortBudget));
		}
		for (int longBudget : new int[] {6001, Integer.MAX_VALUE}) {
			assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(6000, 3600, 0, false, longBudget));
			assertEquals(PestRunDeadline.Failure.RETURN, PestRunDeadline.check(6001, 3600, 0, false, longBudget));
		}
	}

	@Test void routeBudgetDoesNotChangeUnfinishedHuntDeadline() {
		assertEquals(PestRunDeadline.Failure.CLEANUP, PestRunDeadline.check(3601, 3600, -1, false, 6000));
		assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(3601, 3600, -1, true, 6000));
	}
}
