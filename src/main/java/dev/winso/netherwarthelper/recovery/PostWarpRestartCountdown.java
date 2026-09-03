package dev.winso.netherwarthelper.recovery;

/** Controller-independent countdown between a submitted warp command and a fresh macro start. */
public final class PostWarpRestartCountdown {
	private int delayTicks;
	private int elapsedTicks;
	private boolean active;

	public void arm(int delayTicks) {
		this.delayTicks = Math.max(1, delayTicks);
		elapsedTicks = 0;
		active = true;
	}

	/** Advances time and returns true once when the delay has elapsed and restart is currently safe. */
	public boolean tick(boolean readyToRestart) {
		if (!active) {
			return false;
		}
		elapsedTicks++;
		if (elapsedTicks < delayTicks || !readyToRestart) {
			return false;
		}
		active = false;
		return true;
	}

	public void clear() {
		delayTicks = 0;
		elapsedTicks = 0;
		active = false;
	}

	public boolean isActive() {
		return active;
	}

	public int getElapsedTicks() {
		return elapsedTicks;
	}

	public int getRemainingSeconds(int ticksPerSecond) {
		if (!active) {
			return 0;
		}
		int remainingTicks = Math.max(0, delayTicks - elapsedTicks);
		return (remainingTicks + ticksPerSecond - 1) / ticksPerSecond;
	}
}
