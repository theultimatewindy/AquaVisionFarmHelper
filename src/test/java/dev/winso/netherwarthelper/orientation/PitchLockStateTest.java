package dev.winso.netherwarthelper.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PitchLockStateTest {
	@Test
	void enabledLockStoresPitchUntilEnded() {
		PitchLockState state = new PitchLockState();
		state.begin(true, 0.0F);

		assertTrue(state.isActive());
		assertEquals(0.0F, state.getPitch());

		state.end();
		assertFalse(state.isActive());
	}

	@Test
	void disabledLockRemainsInactive() {
		PitchLockState state = new PitchLockState();
		state.begin(false, 15.0F);

		assertFalse(state.isActive());
		assertEquals(15.0F, state.getPitch());
	}
}
