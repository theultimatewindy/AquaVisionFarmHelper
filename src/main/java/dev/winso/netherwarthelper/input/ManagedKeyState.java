package dev.winso.netherwarthelper.input;

/** Absolute-state reconciliation for Minecraft keys that may use toggle mode. */
public final class ManagedKeyState {
	private ManagedKeyState() {
	}

	public static Command reconcile(boolean actualDown, boolean desiredDown, boolean toggleMode) {
		if (actualDown == desiredDown) return Command.NONE;
		if (toggleMode) return Command.PRESS_TRUE;
		return desiredDown ? Command.PRESS_TRUE : Command.RELEASE_FALSE;
	}

	public enum Command {
		NONE,
		PRESS_TRUE,
		RELEASE_FALSE
	}
}
