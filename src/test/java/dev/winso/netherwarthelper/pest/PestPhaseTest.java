package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PestPhaseTest {
	@Test
	void distinguishesIdleActiveAndTerminalPhases() {
		assertFalse(PestPhase.IDLE.isActive());
		assertFalse(PestPhase.IDLE.isTerminal());
		assertTrue(PestPhase.LOCATING.isActive());
		assertTrue(PestPhase.TAKING_OFF.isActive());
		assertFalse(PestPhase.LOCATING.isTerminal());
		assertFalse(PestPhase.COMPLETE.isActive());
		assertTrue(PestPhase.COMPLETE.isTerminal());
		assertTrue(PestPhase.FAILED.isTerminal());
	}
}
