package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PestCompletionGateTest {
	@Test
	void unknownCountAndEmptyEntitiesAloneNeverMeanZero() {
		PestCompletionGate gate = new PestCompletionGate();
		for (int tick = 0; tick < 4_000; tick += 20) {
			assertFalse(unknown(gate, tick, true));
		}
		assertFalse(gate.noteLastTargetRemoved(4_000));
		assertFalse(gate.pendingLastRemoval());
		assertFalse(gate.canFinishAfterDeadline(4_000));
	}

	@Test
	void finalVerifiedRemovalAfterFourThreeTwoOneConfirmsFiveCounterFreePolls() {
		PestCompletionGate gate = new PestCompletionGate();
		for (int total = 4; total >= 1; total--) {
			assertFalse(gate.observe((4 - total) * 20, true, OptionalInt.of(total), true, false, false, true));
		}
		assertTrue(gate.noteLastTargetRemoved(61));
		for (int poll = 1; poll <= 5; poll++) {
			assertEquals(poll == 5, unknown(gate, 60 + poll * 20, true));
			assertEquals(poll, gate.confirmedPolls());
		}
		assertTrue(gate.confirmed());
		assertFalse(gate.pendingLastRemoval());
		assertTrue(gate.evidence().contains("Verified last-target removal"));
	}

	@Test
	void onlyRecentExactlyOneCountCanArmRemoval() {
		PestCompletionGate gate = new PestCompletionGate();
		assertFalse(gate.noteLastTargetRemoved(0));
		positive(gate, 0, 2);
		assertFalse(gate.noteLastTargetRemoved(1));
		positive(gate, 20, 1);
		assertFalse(gate.noteLastTargetRemoved(61));
		assertTrue(gate.noteLastTargetRemoved(60));
		gate.reset();
		positive(gate, 100, 1);
		assertFalse(gate.noteLastTargetRemoved(99));
	}

	@Test
	void cachedPositiveCountCannotArmRemovalWithoutAFreshPoll() {
		PestCompletionGate gate = new PestCompletionGate();
		assertFalse(gate.observe(0, false, OptionalInt.of(1), true, false, false, true));
		assertFalse(gate.noteLastTargetRemoved(1));
	}

	@Test
	void repeatedRemovalEventDoesNotResetStreakOrExtendDeadlineGrace() {
		PestCompletionGate gate = armedGate();
		assertFalse(unknown(gate, 20, true));
		assertEquals(1, gate.confirmedPolls());
		assertFalse(gate.noteLastTargetRemoved(25));
		assertEquals(1, gate.confirmedPolls());
		assertTrue(gate.canFinishAfterDeadline(201));
		assertFalse(gate.canFinishAfterDeadline(202));
		assertFalse(unknown(gate, 202, false));
		assertFalse(gate.pendingLastRemoval());
	}

	@Test
	void cachedOrDuplicateCounterFreePollsDoNotAdvanceRemovalConfirmation() {
		PestCompletionGate gate = armedGate();
		assertFalse(unknown(gate, 20, true));
		assertFalse(unknown(gate, 20, true));
		for (int tick = 21; tick < 40; tick++) {
			assertFalse(unknown(gate, tick, false));
		}
		assertEquals(1, gate.confirmedPolls());
		assertFalse(unknown(gate, 40, true));
		assertEquals(2, gate.confirmedPolls());
	}

	@Test
	void explicitZeroRequiresTwoDistinctFreshPollsAndDoesNotNeedRemovalInference() {
		PestCompletionGate gate = new PestCompletionGate();
		assertFalse(zero(gate, 0, true));
		assertFalse(zero(gate, 0, true));
		for (int tick = 1; tick < 20; tick++) {
			assertFalse(zero(gate, tick, false));
		}
		assertEquals(1, gate.confirmedPolls());
		assertTrue(zero(gate, 20, true));
		assertTrue(gate.evidence().contains("Explicit Garden zero"));
	}

	@Test
	void liveTargetBetweenPollsResetsExplicitZeroStreak() {
		PestCompletionGate gate = new PestCompletionGate();
		assertFalse(zero(gate, 0, true));
		assertFalse(gate.observe(5, false, OptionalInt.of(0), true, false, false, true));
		assertEquals(0, gate.confirmedPolls());
		assertFalse(zero(gate, 20, true));
		assertTrue(zero(gate, 40, true));
	}

	@Test
	void liveTargetOnAnyTickDisarmsAnOmittedCounterCandidate() {
		PestCompletionGate gate = armedGate();
		assertFalse(unknown(gate, 20, true));
		assertFalse(gate.observe(21, false, OptionalInt.empty(), true, false, true, true));
		assertFalse(gate.pendingLastRemoval());
		for (int tick = 40; tick <= 160; tick += 20) {
			assertFalse(unknown(gate, tick, true));
		}
	}

	@Test
	void incompleteOrPositivePlotHudResetsOmittedStreakWithoutInventingCompletion() {
		PestCompletionGate gate = armedGate();
		assertFalse(unknown(gate, 20, true));
		assertFalse(unknown(gate, 40, true));
		// counterFreeHud=false also represents nonempty infested-plot evidence checked by the caller.
		assertFalse(gate.observe(41, false, OptionalInt.empty(), true, false, false, false));
		assertEquals(0, gate.confirmedPolls());
		assertTrue(gate.pendingLastRemoval());
		for (int poll = 1; poll <= 5; poll++) {
			assertEquals(poll == 5, unknown(gate, 40 + poll * 20, true));
		}
	}

	@Test
	void leavingGardenOrConflictingTotalsDisarmsAndInvalidatesCountHistory() {
		for (boolean outsideGarden : new boolean[] {false, true}) {
			PestCompletionGate gate = armedGate();
			assertFalse(unknown(gate, 20, true));
			assertFalse(gate.observe(21, false, OptionalInt.empty(), !outsideGarden, !outsideGarden, true, false));
			assertFalse(gate.pendingLastRemoval());
			assertFalse(gate.noteLastTargetRemoved(22));
			assertFalse(unknown(gate, 40, true));
		}
	}

	@Test
	void shortPositiveOneHudLagResetsStreakButMayStillRefreshToClear() {
		PestCompletionGate gate = armedGate();
		assertFalse(unknown(gate, 20, true));
		positive(gate, 40, 1);
		assertEquals(0, gate.confirmedPolls());
		assertTrue(gate.pendingLastRemoval());
		for (int poll = 1; poll <= 5; poll++) {
			assertEquals(poll == 5, unknown(gate, 40 + poll * 20, true));
		}
	}

	@Test
	void positiveCountAfterLagWindowOrAnyCountAboveOneDisarmsCandidate() {
		PestCompletionGate late = armedGate();
		positive(late, 42, 1);
		assertFalse(late.pendingLastRemoval());
		assertFalse(unknown(late, 60, true));
		PestCompletionGate additionalPest = armedGate();
		positive(additionalPest, 2, 2);
		assertFalse(additionalPest.pendingLastRemoval());
		assertFalse(unknown(additionalPest, 20, true));
	}

	@Test
	void lastRemovalCandidateAndDeadlineGraceExpireAfterTwoHundredTicks() {
		PestCompletionGate gate = armedGate();
		assertTrue(gate.canFinishAfterDeadline(201));
		assertFalse(gate.canFinishAfterDeadline(202));
		assertFalse(gate.canFinishAfterDeadline(0));
		assertFalse(unknown(gate, 202, false));
		assertFalse(gate.pendingLastRemoval());
		assertFalse(unknown(gate, 220, true));
	}

	@Test
	void explicitAndOmittedEvidenceCannotBeCombinedIntoOneConfirmationStreak() {
		PestCompletionGate gate = armedGate();
		assertFalse(unknown(gate, 20, true));
		assertFalse(unknown(gate, 40, true));
		assertFalse(unknown(gate, 60, true));
		assertFalse(zero(gate, 80, true));
		assertEquals(1, gate.confirmedPolls());
		assertFalse(unknown(gate, 100, true));
		assertEquals(1, gate.confirmedPolls());
		assertFalse(zero(gate, 120, true));
		assertTrue(zero(gate, 140, true));
	}

	@Test
	void confirmedClearanceStaysLatchedUntilSessionReset() {
		PestCompletionGate gate = new PestCompletionGate();
		assertFalse(zero(gate, 0, true));
		assertTrue(zero(gate, 20, true));
		assertTrue(gate.observe(40, true, OptionalInt.empty(), false, true, false, true));
		assertTrue(gate.confirmed());
		assertFalse(gate.noteLastTargetRemoved(41));
		gate.reset();
		assertFalse(gate.confirmed());
		assertEquals(0, gate.confirmedPolls());
		assertFalse(gate.pendingLastRemoval());
		assertFalse(unknown(gate, 60, true));
	}

	@Test
	void genericUnknownOrPositivePollResetsAnExplicitZeroStreak() {
		PestCompletionGate gate = new PestCompletionGate();
		assertFalse(zero(gate, 0, true));
		assertFalse(unknown(gate, 20, true));
		assertFalse(zero(gate, 40, true));
		positive(gate, 60, 1);
		assertFalse(zero(gate, 80, true));
		assertTrue(zero(gate, 100, true));
	}

	private static PestCompletionGate armedGate() {
		PestCompletionGate gate = new PestCompletionGate();
		positive(gate, 0, 1);
		assertTrue(gate.noteLastTargetRemoved(1));
		return gate;
	}

	private static void positive(PestCompletionGate gate, int tick, int count) {
		assertFalse(gate.observe(tick, true, OptionalInt.of(count), true, false, false, false));
	}

	private static boolean unknown(PestCompletionGate gate, int tick, boolean fresh) {
		return gate.observe(tick, fresh, OptionalInt.empty(), true, false, true, false);
	}

	private static boolean zero(PestCompletionGate gate, int tick, boolean fresh) {
		return gate.observe(tick, fresh, OptionalInt.of(0), true, false, false, false);
	}
}
