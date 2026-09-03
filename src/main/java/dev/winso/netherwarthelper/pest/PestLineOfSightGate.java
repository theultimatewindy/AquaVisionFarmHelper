package dev.winso.netherwarthelper.pest;

/** Keeps one-frame line-of-sight recovery from chattering pest movement. */
public final class PestLineOfSightGate {
	public static final int REQUIRED_CLEAR_TICKS = 3;

	private int clearTicks;
	private boolean confirmed;

	public void reset() {
		clearTicks = 0;
		confirmed = false;
	}

	public void reset(boolean initiallyClear) {
		confirmed = initiallyClear;
		clearTicks = initiallyClear ? REQUIRED_CLEAR_TICKS : 0;
	}

	public boolean observe(boolean clear) {
		if (!clear) {
			reset();
			return false;
		}
		if (!confirmed && ++clearTicks >= REQUIRED_CLEAR_TICKS) confirmed = true;
		return confirmed;
	}
}
