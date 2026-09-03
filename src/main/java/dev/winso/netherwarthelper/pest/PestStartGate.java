package dev.winso.netherwarthelper.pest;

import java.util.OptionalInt;

/** Confirms fresh HUD polls independently of when a lane is safe to interrupt. */
public final class PestStartGate {
	public static final int REQUIRED_POLLS = 2;
	private int confirmedPolls;
	private int lastThreshold = -1;

	public boolean shouldStart(boolean armed, boolean freshPoll, OptionalInt count,
		int threshold, boolean safeLane) {
		if (!armed || threshold != lastThreshold) {
			reset();
			lastThreshold = threshold;
		}
		if (!armed) {
			return false;
		}
		if (freshPoll) {
			confirmedPolls = count.isPresent() && count.getAsInt() >= threshold
				? Math.min(REQUIRED_POLLS, confirmedPolls + 1) : 0;
		}
		return safeLane && confirmedPolls >= REQUIRED_POLLS;
	}

	public int confirmedPolls() {
		return confirmedPolls;
	}

	public void reset() {
		confirmedPolls = 0;
		lastThreshold = -1;
	}
}
