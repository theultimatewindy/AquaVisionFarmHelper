package dev.winso.netherwarthelper.pest;

/** Prevents altitude-control key pulses from invoking vanilla's seven-tick double-jump flight toggle. */
public final class FlightJumpGuard {
	public static final int MIN_PRESS_INTERVAL_TICKS = 8;
	private int ticksSincePress = MIN_PRESS_INTERVAL_TICKS;
	private boolean pressed;

	/** Call once per controlled client tick, including ticks that release Jump to hover. */
	public boolean tick(boolean requested) {
		ticksSincePress = Math.min(MIN_PRESS_INTERVAL_TICKS, ticksSincePress + 1);
		if (!requested) {
			pressed = false;
			return false;
		}
		if (pressed) return true;
		if (ticksSincePress < MIN_PRESS_INTERVAL_TICKS) return false;
		pressed = true;
		ticksSincePress = 0;
		return true;
	}

	public void reset() {
		pressed = false;
		ticksSincePress = MIN_PRESS_INTERVAL_TICKS;
	}
}
