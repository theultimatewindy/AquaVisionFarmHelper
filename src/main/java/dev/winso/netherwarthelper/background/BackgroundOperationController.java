package dev.winso.netherwarthelper.background;

/** Scopes the vanilla focus override to an actively running automation session. */
public final class BackgroundOperationController {
	private static volatile boolean backgroundInputActive;

	private boolean active;

	public void begin(boolean enabled) {
		end();
		active = enabled;
		backgroundInputActive = enabled;
	}

	public void maintain() {
		if (active) {
			backgroundInputActive = true;
		}
	}

	public void end() {
		if (!active) {
			backgroundInputActive = false;
			return;
		}

		backgroundInputActive = false;
		active = false;
	}

	public boolean isActive() {
		return active;
	}

	public static boolean isBackgroundInputActive() {
		return backgroundInputActive;
	}
}
