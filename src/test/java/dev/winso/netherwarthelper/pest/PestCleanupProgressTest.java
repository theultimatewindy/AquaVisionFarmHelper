package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PestCleanupProgressTest {
	@Test void authoritativeCountReductionRestartsTheInactivityBudget() {
		PestCleanupProgress progress = new PestCleanupProgress();
		progress.reset(8);
		assertEquals(3600, progress.deadlineTick(3600, 18000));
		assertTrue(progress.observe(3500, true, OptionalInt.of(7)));
		assertEquals(7100, progress.deadlineTick(3600, 18000));
		assertEquals(PestRunDeadline.Failure.NONE,
			PestRunDeadline.check(7100, progress.deadlineTick(3600, 18000), -1, false));
		assertEquals(PestRunDeadline.Failure.CLEANUP,
			PestRunDeadline.check(7101, progress.deadlineTick(3600, 18000), -1, false));
		assertTrue(progress.observe(7000, true, OptionalInt.of(2)));
		assertEquals(10600, progress.deadlineTick(3600, 18000));
	}

	@Test void equalIncreasedUnknownOrStaleCountsDoNotExtendCleanup() {
		PestCleanupProgress progress = new PestCleanupProgress();
		progress.reset(4);
		assertFalse(progress.observe(100, true, OptionalInt.of(4)));
		assertFalse(progress.observe(200, true, OptionalInt.of(5)));
		assertFalse(progress.observe(300, false, OptionalInt.of(3)));
		assertFalse(progress.observe(400, true, OptionalInt.empty()));
		assertEquals(0, progress.lastProgressTick());
	}

	@Test void hardDeadlineAlwaysBoundsRepeatedProgress() {
		PestCleanupProgress progress = new PestCleanupProgress();
		progress.reset(8);
		assertTrue(progress.observe(17000, true, OptionalInt.of(1)));
		assertEquals(18000, progress.deadlineTick(3600, 18000));
	}

	@Test void invalidDeadlinesAreRejected() {
		PestCleanupProgress progress = new PestCleanupProgress();
		assertThrows(IllegalArgumentException.class, () -> progress.deadlineTick(0, 10));
		assertThrows(IllegalArgumentException.class, () -> progress.observe(-1, true, OptionalInt.of(1)));
	}
}
