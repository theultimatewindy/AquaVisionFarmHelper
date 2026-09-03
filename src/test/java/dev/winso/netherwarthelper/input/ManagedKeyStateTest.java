package dev.winso.netherwarthelper.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ManagedKeyStateTest {
	@Test
	void stableDesiredStateDoesNothingInEitherMode() {
		assertEquals(ManagedKeyState.Command.NONE, ManagedKeyState.reconcile(false, false, false));
		assertEquals(ManagedKeyState.Command.NONE, ManagedKeyState.reconcile(true, true, false));
		assertEquals(ManagedKeyState.Command.NONE, ManagedKeyState.reconcile(false, false, true));
		assertEquals(ManagedKeyState.Command.NONE, ManagedKeyState.reconcile(true, true, true));
	}

	@Test
	void holdModeUsesOrdinaryPressAndRelease() {
		assertEquals(ManagedKeyState.Command.PRESS_TRUE, ManagedKeyState.reconcile(false, true, false));
		assertEquals(ManagedKeyState.Command.RELEASE_FALSE, ManagedKeyState.reconcile(true, false, false));
	}

	@Test
	void toggleModePressesExactlyOnceForEitherStateChange() {
		assertEquals(ManagedKeyState.Command.PRESS_TRUE, ManagedKeyState.reconcile(false, true, true));
		assertEquals(ManagedKeyState.Command.PRESS_TRUE, ManagedKeyState.reconcile(true, false, true));
	}

	@Test
	void applyingAToggleCommandMakesTheNextReconciliationStable() {
		boolean actual = false;
		assertEquals(ManagedKeyState.Command.PRESS_TRUE, ManagedKeyState.reconcile(actual, true, true));
		actual = !actual;
		assertEquals(ManagedKeyState.Command.NONE, ManagedKeyState.reconcile(actual, true, true));
		assertEquals(ManagedKeyState.Command.PRESS_TRUE, ManagedKeyState.reconcile(actual, false, true));
		actual = !actual;
		assertEquals(ManagedKeyState.Command.NONE, ManagedKeyState.reconcile(actual, false, true));
	}
}
