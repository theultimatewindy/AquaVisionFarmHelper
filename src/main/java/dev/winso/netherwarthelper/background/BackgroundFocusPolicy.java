package dev.winso.netherwarthelper.background;

/** Pure policy for the vanilla focus check that can open an automatic pause screen. */
public final class BackgroundFocusPolicy {
	private BackgroundFocusPolicy() {
	}

	public static boolean shouldTreatWindowAsFocused(
		boolean actuallyFocused,
		boolean backgroundAutomationActive
	) {
		return actuallyFocused || backgroundAutomationActive;
	}

	/**
	 * Vanilla requires a captured mouse before it continues a held attack. Opening
	 * the warp chat releases that capture, and an unfocused window cannot recapture
	 * it, so active background automation must provide the equivalent permission.
	 */
	public static boolean shouldAllowContinuousAttack(
		boolean mouseGrabbed,
		boolean backgroundAutomationActive
	) {
		return mouseGrabbed || backgroundAutomationActive;
	}
}
