package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PestClearEvidenceTest {
	private static final List<String> GARDEN_TAB = List.of("Area: Garden", "Pests:");

	@Test
	void totalRowMayDisappearCompletelyWhileTheNormalPlotHudRemains() {
		assertTrue(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 7"), GARDEN_TAB));
		assertTrue(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 0"), GARDEN_TAB));
		assertTrue(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 24"), GARDEN_TAB));
	}

	@Test
	void bareGardenLocationsAlsoProvideContextButNeedStatusMarkers() {
		assertTrue(PestClearEvidence.hasCounterFreeGardenHud(healthy("The Garden"), GARDEN_TAB));
		assertTrue(PestClearEvidence.hasCounterFreeGardenHud(healthy("Garden"), List.of("Area: The Garden")));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(List.of("The Garden"), GARDEN_TAB));
	}

	@Test
	void positiveOrMalformedGardenCountersAlwaysVetoEligibility() {
		for (String row : List.of("The Garden x1", "The Gardenx1", "The Garden 4x", "Garden x?", "The Garden -1",
			"The Garden x99999999999999999999", "The Garden ４", "The Garden pest counter unavailable",
			"The Garden " + PestCountParser.PEST_GLYPH)) {
			assertFalse(PestClearEvidence.hasCounterFreeGardenHud(withExtraRow(row), GARDEN_TAB), row);
		}
	}

	@Test
	void separatePestCountersCannotBeMistakenForAnAbsentTotal() {
		for (String row : List.of(PestCountParser.PEST_GLYPH + " x1", "x4", "8x", "x???",
			"Pests: 4", "Total Pests: unknown")) {
			assertFalse(PestClearEvidence.hasCounterFreeGardenHud(withExtraRow(row), GARDEN_TAB), row);
		}
	}

	@Test
	void plotCountsAndInvalidPlotIdsVetoEligibility() {
		for (String row : List.of("Plot - 7 x1", "Plot - 7 1x", "Plot - 7 " + PestCountParser.PEST_GLYPH,
			"Plot - 7 x?", "Plot - 25", "Plot - 99999999999999", "Plot - -1")) {
			assertFalse(PestClearEvidence.hasCounterFreeGardenHud(withExtraRow(row), GARDEN_TAB), row);
		}
	}

	@Test
	void nonemptyTabPestTotalsAreLeftForTheAuthoritativeCountReader() {
		for (String total : List.of("Pests: 4", "Pests: x1", "Pests: 0", "Pests: None",
			"Total Pests: 0", "Total Pests: unknown", "Pests: 99999999999999999999")) {
			assertFalse(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 7"),
				List.of("Area: Garden", total)), total);
		}
	}

	@Test
	void blankOrExplicitlyEmptyPlotsAreAllowedButPlotZeroIsNotAnEmptyCount() {
		for (String row : List.of("Plots:", "Plots: None", "Plots: No pests")) {
			assertTrue(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 7"),
				List.of("Area: Garden", "Pests:", row)), row);
		}
		for (String row : List.of("Plots: 0", "Plots: 5, 7", "Plots: unknown", "Plots: -", "Plots: ?")) {
			assertFalse(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 7"),
				List.of("Area: Garden", "Pests:", row)), row);
		}
	}

	@Test
	void gardenAreaMustBePresentAndUnambiguous() {
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(healthy("The Garden"), List.of("Pests:")));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 7"), List.of("Area: Hub")));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 7"),
			List.of("Area: Garden", "Area: Hub")));
	}

	@Test
	void emptyPestsHeadingNeverProvidesZeroEvidenceOnItsOwn() {
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(List.of(), GARDEN_TAB));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(List.of("Pests:"), GARDEN_TAB));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(List.of("Purse: 100", "Bits: 0"), GARDEN_TAB));
	}

	@Test
	void partialOrDuplicateStatusRowsAreInsufficient() {
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(List.of("Plot - 7", "Purse: 100"), GARDEN_TAB));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(List.of("Plot - 7", "Purse: 100", "Purse: 101"), GARDEN_TAB));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(List.of("Plot - 7", "Purse:", "Bits:"), GARDEN_TAB));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(List.of("Plot - 7", "Other Purse: 100", "Bits: 0"), GARDEN_TAB));
	}

	@Test
	void recognizesLegacyFormattingUnicodeDecorationAndSpaces() {
		assertTrue(PestClearEvidence.hasCounterFreeGardenHud(
			List.of("\u23e3 \u00a7aPlot\u00a0-\u20097", "\u00a7ePurse: 100", "\u00a7bFlight\u00a0Duration: 05:00"),
			List.of("\u00a7aArea:\u00a0Garden", "\u00a7cPests:", "Plots:\u2009None")));
		assertTrue(PestClearEvidence.hasCounterFreeGardenHud(
			List.of("? The Garden", "Copper: 0", "www.hypixel.net"), GARDEN_TAB));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(withExtraRow("\u23e3 The Garden \u00d74"), GARDEN_TAB));
	}

	@Test
	void absentAndNullListsCannotBecomeCompletionEvidence() {
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(null, GARDEN_TAB));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 7"), null));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(healthy("Plot - 7"), List.of()));
		assertFalse(PestClearEvidence.hasCounterFreeGardenHud(Arrays.asList(null, "", " "), GARDEN_TAB));
	}

	private static List<String> healthy(String location) {
		return List.of(location, "Purse: 100", "Bits: 0");
	}

	private static List<String> withExtraRow(String row) {
		List<String> rows = new ArrayList<>(healthy("Plot - 7"));
		rows.add(row);
		return rows;
	}
}
