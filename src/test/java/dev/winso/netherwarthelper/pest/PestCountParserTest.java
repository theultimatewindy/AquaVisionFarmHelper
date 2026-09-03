package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PestCountParserTest {
	@Test
	void parsesFormattedGardenPestLine() {
		var parsed = PestCountParser.parseLine("\u00A7aThe \u00A7bGarden \u00A76\u0D60 \u00A7c3x");

		assertTrue(parsed.isPresent());
		assertEquals(3, parsed.getAsInt());
	}

	@Test
	void searchesMultipleLinesAndSupportsSpacingAndThousandsSeparators() {
		var parsed = PestCountParser.parse(List.of(
			"Purse: 123x",
			"The Garden  \u0D60  1,234 x  "
		));

		assertTrue(parsed.isPresent());
		assertEquals(1_234, parsed.getAsInt());
	}

	@Test
	void preservesARealZeroButDoesNotInventOneForMissingData() {
		assertEquals(0, PestCountParser.parseLine("The Garden \u0D60 0x").orElseThrow());
		assertTrue(PestCountParser.parse(List.of("The Garden")).isEmpty());
		assertTrue(PestCountParser.parseLine("The Garden 3").isEmpty());
		assertTrue(PestCountParser.parseLine("The Garden \u0D60 3x updated").isEmpty());
		assertTrue(PestCountParser.parse(null).isEmpty());
		assertTrue(PestCountParser.parseLine(null).isEmpty());
		assertTrue(PestCountParser.read(null, null).count().isEmpty());
	}

	@Test
	void supportsReferenceCounterWithoutMandatoryTrailingX() {
		for (String token : List.of("8", "x8", "8x", "x 8", "8 x", "X8")) {
			assertEquals(8, PestCountParser.parseLine("The Garden \u0D60 " + token).orElseThrow(), token);
		}
	}

	@Test
	void normalizesFormattingInvisibleSuffixesAndUnicodeSpaces() {
		assertEquals(8, PestCountParser.parseLine(
			"\u23E3 \u00A7aThe\u00A0\u00A7bGarden\u202F\u0D60\u2009\u00A7cx8\u200B\uE012\uFEFF"
		).orElseThrow());
		assertEquals(8, PestCountParser.parseLine("The\u2003Garden \u0D60 8\uD83C\uDF1F").orElseThrow());
	}

	@Test
	void supportsGlyphOnlyCounterOnlyWithGardenLocationInSameSnapshot() {
		assertEquals(8, PestCountParser.parse(List.of("The Garden", "\u0D60 8")).orElseThrow());
		assertEquals(8, PestCountParser.parse(List.of("\u0D60 x8", "The Garden")).orElseThrow());
		assertTrue(PestCountParser.parseLine("\u0D60 8").isEmpty());
		assertTrue(PestCountParser.parse(List.of("Village", "\u0D60 8")).isEmpty());
		assertTrue(PestCountParser.parse(List.of("\u0D60 8")).isEmpty());
	}

	@Test
	void doesNotReadUnrelatedNumbersAsGardenTotals() {
		for (String line : List.of(
			"Purse: 8", "Plots: 8", "Plot: \u0D60 8", "Current Plot: \u0D60 8",
			"The Garden Current Plot: \u0D60 8", "Visit The Garden \u0D60 8",
			"Pests Killed: 8", "Vacuum Bag: 8", "Pests: 8", "The Garden \u0D60 8 / 10"
		)) {
			assertTrue(PestCountParser.parse(List.of("The Garden", line)).isEmpty(), line);
		}
	}

	@Test
	void rejectsMalformedAndOverflowingCounters() {
		for (String token : List.of("-8", "8.0", "1,23", "1,,234", "x8x", "8 more", "99999999999999999999")) {
			assertTrue(PestCountParser.parseLine("The Garden \u0D60 " + token).isEmpty(), token);
		}
	}

	@Test
	void acceptsOnlyExactLabelledTabTotalsWithGardenContext() {
		for (String line : List.of("Pests: 8", "Total Pests:8", "\u00A7cPests:\u00A0x8")) {
			var reading = PestCountParser.read(List.of("The Garden"), List.of(line));
			assertEquals(8, reading.count().orElseThrow(), line);
			assertEquals("tab list", reading.source());
			assertTrue(reading.inGarden());
		}
		assertTrue(PestCountParser.read(List.of("The Garden"), List.of("Pests: 0")).count().isPresent());
		assertEquals(0, PestCountParser.read(List.of("The Garden"), List.of("Pests: 0")).count().orElseThrow());
		assertTrue(PestCountParser.read(List.of("Village"), List.of("Pests: 8")).count().isEmpty());
		assertFalse(PestCountParser.read(List.of("Village"), List.of("Pests: 8")).inGarden());
	}

	@Test
	void rejectsPlotCountsAndOtherPestNumbersInTabList() {
		for (String line : List.of(
			"Plots: 1, 3, 8", "Current Plot Pests: 8", "Plot 3 Pests: 8", "Pests Killed: 8",
			"Vacuum Bag Pests: 8", "Pests: 8 / 40", "Pests: 8 killed", "Pests: +8"
		)) {
			assertTrue(PestCountParser.read(List.of("The Garden"), List.of(line)).count().isEmpty(), line);
		}
	}

	@Test
	void preservesSidebarSourceWhenTabAgreesAndReportsConflictsAsUnknown() {
		var matching = PestCountParser.read(List.of("The Garden \u0D60 8"), List.of("Pests: 8"));
		assertEquals(8, matching.count().orElseThrow());
		assertEquals("sidebar", matching.source());
		assertEquals("The Garden \u0D60 8", matching.evidence());

		var conflict = PestCountParser.read(List.of("The Garden \u0D60 0"), List.of("Pests: 8"));
		assertTrue(conflict.count().isEmpty());
		assertEquals("conflict", conflict.source());
		assertTrue(conflict.evidence().contains("Pests: 8"));
		assertTrue(conflict.inGarden());
		assertTrue(PestCountParser.parse(List.of("The Garden \u0D60 0", "\u0D60 8")).isEmpty());
		assertTrue(PestCountParser.read(List.of("The Garden"), List.of("Pests: 0", "Total Pests: 8")).count().isEmpty());
	}

	@Test
	void readsFourGardenPestsNotTwoPlotPestsFromReportedDiagnostic() {
		// The running client's log substitutes '?' for unsupported icons. Preserve its
		// actual formatting codes, including the invisible score owner inside "Garden".
		var reading = PestCountParser.read(List.of(
			"Late Summer 19t\u00A7!h",
			" \u00A7711:40am \u00A7e?\u00A7z",
			" \u00A77? \u00A7cThe Garde\u00A7y\u00A7cn \u00A74\u00A7l?\u00A77 x4",
			"   \u00A7aPlot \u00A77- \u00A7b\u00A7x\u00A7b7 \u00A74\u00A7l?\u00A77 x2",
			"Purse: 1,331,823", "Flight Duration: 52:39:59"
		), List.of("Pest Traps: 0/3", "Pests:", "Garden Level: XII (75.7%)",
			"Area: Garden", "Plots: 5, 7", "Bonus Pest Chance: 40"));

		assertTrue(reading.inGarden());
		assertEquals(4, reading.count().orElseThrow());
		assertEquals("sidebar", reading.source());
		assertTrue(reading.evidence().endsWith("x4"));
	}

	@Test
	void gardenMultiplierDoesNotDependOnOnePestIcon() {
		for (String icon : List.of("", "?", "\u0D60", "\u03DF", "\uE012", "\uD83D\uDC1B")) {
			for (String counter : List.of("x4", "4x", "x 4", "4 x", "\u00D74")) {
				assertEquals(4, PestCountParser.parseLine("\u23E3 The Garden " + icon + " " + counter)
					.orElseThrow(), icon + " " + counter);
			}
		}
		assertEquals(0, PestCountParser.parseLine("The Garden x0").orElseThrow());
		assertTrue(PestCountParser.parseLine("The Garden").isEmpty());
	}

	@Test
	void tabAreaEstablishesLocationButDoesNotInventATotal() {
		var missing = PestCountParser.read(List.of("Plot - 7 x2"),
			List.of("Area: Garden", "Pests:", "Plots: 5, 7", "Pest Traps: 0/3"));
		assertTrue(missing.inGarden());
		assertTrue(missing.count().isEmpty());
		assertEquals("Area: Garden", missing.evidence());

		var explicit = PestCountParser.read(List.of(), List.of("Area: Garden", "Pests: 4"));
		assertTrue(explicit.inGarden());
		assertEquals(4, explicit.count().orElseThrow());
		assertEquals("tab list", explicit.source());
		assertTrue(PestCountParser.read(List.of("x2", "\u0D60 x2"), List.of("Area: Garden")).count().isEmpty());
		assertFalse(PestCountParser.read(List.of(), List.of("Area: Village", "Pests: 4")).inGarden());
	}

	@Test
	void glyphIndependentCounterRemainsBoundedToGardenLocation() {
		for (String line : List.of("Plot - 7 x2", "Current Plot x2", "The Garden Plot - 7 x2",
			"Visit The Garden x4", "The Garden x4 updated", "The Garden x4 / 10",
			"The Garden -4x", "The Garden -x4", "The Garden \u22124x", "The Garden +4x",
			"The Garden 4", "Pests Killed: x4", "Vacuum Bag: x4", "Pest Traps: 0/3")) {
			assertTrue(PestCountParser.read(List.of(line), List.of("Area: Garden")).count().isEmpty(), line);
		}
	}
}
