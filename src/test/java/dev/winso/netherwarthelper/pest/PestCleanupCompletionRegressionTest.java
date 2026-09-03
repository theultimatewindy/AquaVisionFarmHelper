package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the real parser, contextual HUD check, completion latch, and return deadline together. */
class PestCleanupCompletionRegressionTest {
	private static final List<String> EMPTY_GARDEN_SIDEBAR = List.of(
		"Late Summer 19th", "11:40am", "\u23E3 \u00A7aPlot \u00A77- \u00A7b7",
		"Flight Duration: 52:39:59", "Purse: 1,331,823", "Bits: 5,760", "Copper: 49",
		"Objective", "Talk to the Carpenter", "www.hypixel.net");
	private static final List<String> EMPTY_GARDEN_TAB = List.of(
		"Pest Traps: 0/3", "Pests:", "Garden Level: XII (75.7%)", "Area: Garden", "Bonus Pest Chance: 40");

	@Test void actualFourThreeTwoOneThenMissingTotalCanCompleteNearTheHuntDeadline() {
		PestCompletionGate gate = new PestCompletionGate();
		for (int count = 4; count >= 1; count--) {
			int tick = 3_590 - (count - 1) * 20;
			assertFalse(observe(gate, tick, true, List.of("The Garden x" + count, "Plot - 7 x1"),
				List.of("Area: Garden", "Pests:", "Plots: 7"), true));
		}
		assertTrue(gate.noteLastTargetRemoved(3_591));
		var emptyReading = PestCountParser.read(EMPTY_GARDEN_SIDEBAR, EMPTY_GARDEN_TAB);
		assertTrue(emptyReading.count().isEmpty(), "The raw missing total must stay unknown");
		assertTrue(emptyReading.inGarden());
		assertTrue(PestClearEvidence.hasCounterFreeGardenHud(EMPTY_GARDEN_SIDEBAR, EMPTY_GARDEN_TAB));
		int returnStarted = -1;
		for (int tick = 3_592; tick <= 3_680; tick++) {
			boolean fresh = tick % 20 == 0;
			boolean clear = observe(gate, tick, fresh, EMPTY_GARDEN_SIDEBAR, EMPTY_GARDEN_TAB, false);
			assertEquals(tick == 3_680, clear);
			if (clear) returnStarted = tick;
			assertEquals(PestRunDeadline.Failure.NONE,
				PestRunDeadline.check(tick, 3_600, returnStarted, gate.canFinishAfterDeadline(tick)));
		}
		assertEquals(5, gate.confirmedPolls());
		assertEquals(PestRunDeadline.Failure.NONE, PestRunDeadline.check(3_900, 3_600, returnStarted, false));
		assertTrue(gate.observe(3_900, true, emptyReading.count(), true, false, true, false));
	}

	@Test void disappearingCounterWhilePositivePlotEvidenceRemainsCannotClear() {
		PestCompletionGate gate = armedGate();
		for (int tick = 20; tick <= 220; tick += 20) {
			assertFalse(observe(gate, tick, true, EMPTY_GARDEN_SIDEBAR,
				List.of("Area: Garden", "Pests:", "Plots: 7"), false));
		}
		assertFalse(gate.confirmed());
		assertFalse(gate.pendingLastRemoval());
	}

	@Test void missingSidebarOrTabCannotBeAnEmptyGardenConfirmation() {
		for (boolean sidebarMissing : new boolean[] {false, true}) {
			PestCompletionGate gate = armedGate();
			for (int tick = 20; tick <= 220; tick += 20) {
				assertFalse(observe(gate, tick, true,
					sidebarMissing ? List.of() : EMPTY_GARDEN_SIDEBAR,
					sidebarMissing ? EMPTY_GARDEN_TAB : List.of(), false));
			}
			assertFalse(gate.confirmed());
		}
	}

	@Test void malformedGardenCounterDoesNotBecomeSuppressedZero() {
		PestCompletionGate gate = armedGate();
		for (int tick = 20; tick <= 200; tick += 20) {
			assertFalse(observe(gate, tick, true,
				List.of("The Garden x?", "Plot - 7", "Purse: 100", "Bits: 0"), EMPTY_GARDEN_TAB, false));
		}
		assertFalse(gate.confirmed());
	}

	private static PestCompletionGate armedGate() {
		PestCompletionGate gate = new PestCompletionGate();
		assertFalse(observe(gate, 0, true, List.of("The Garden x1"), EMPTY_GARDEN_TAB, true));
		assertTrue(gate.noteLastTargetRemoved(1));
		return gate;
	}

	private static boolean observe(PestCompletionGate gate, int tick, boolean fresh,
		List<String> sidebar, List<String> tab, boolean targetsPresent) {
		PestCountParser.Reading reading = PestCountParser.read(sidebar, tab);
		return gate.observe(tick, fresh, reading.count(), reading.inGarden(), "conflict".equals(reading.source()),
			PestClearEvidence.hasCounterFreeGardenHud(sidebar, tab), targetsPresent);
	}
}
