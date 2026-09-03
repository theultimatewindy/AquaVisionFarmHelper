package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PestLineOfSightGateTest {
	@Test
	void aNewVisibleTargetCanMoveImmediately() {
		PestLineOfSightGate gate = new PestLineOfSightGate();
		gate.reset(true);
		assertTrue(gate.observe(true));
	}

	@Test
	void sightMustStayClearAfterAnObstruction() {
		PestLineOfSightGate gate = new PestLineOfSightGate();
		gate.reset(true);
		assertFalse(gate.observe(false));
		assertFalse(gate.observe(true));
		assertFalse(gate.observe(false));
		assertFalse(gate.observe(true));
		assertFalse(gate.observe(true));
		assertTrue(gate.observe(true));
	}
}
