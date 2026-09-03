package dev.winso.netherwarthelper.failsafe;

/** Tracks active client ticks since the last observed Nether Wart block break. */
public final class NoWartFailsafeMonitor {
	public static final int TICKS_PER_SECOND = 20;

	private boolean enabled;
	private int timeoutTicks = 3 * TICKS_PER_SECOND;
	private int ticksSinceLastWartBreak;
	private boolean alertActive;

	public void startSession(boolean enabled, int timeoutSeconds) {
		this.enabled = enabled;
		int safeSeconds = Math.max(1, Math.min(timeoutSeconds, Integer.MAX_VALUE / TICKS_PER_SECOND));
		timeoutTicks = safeSeconds * TICKS_PER_SECOND;
		ticksSinceLastWartBreak = 0;
		alertActive = false;
	}

	/**
	 * Advances the timer only while automation is active. Returns true exactly once when a
	 * no-wart incident first reaches the configured timeout.
	 */
	public boolean tick(boolean automationActive) {
		if (!enabled || !automationActive) {
			return false;
		}

		if (ticksSinceLastWartBreak < Integer.MAX_VALUE) {
			ticksSinceLastWartBreak++;
		}
		if (!alertActive && ticksSinceLastWartBreak >= timeoutTicks) {
			alertActive = true;
			return true;
		}
		return false;
	}

	/** Returns true when this break recovered a previously alerted incident. */
	public boolean recordWartBreak() {
		boolean recovered = enabled && alertActive;
		ticksSinceLastWartBreak = 0;
		alertActive = false;
		return recovered;
	}

	public void clear() {
		enabled = false;
		ticksSinceLastWartBreak = 0;
		alertActive = false;
	}

	public boolean isAlertActive() {
		return alertActive;
	}

	public int getTicksSinceLastWartBreak() {
		return ticksSinceLastWartBreak;
	}
}
