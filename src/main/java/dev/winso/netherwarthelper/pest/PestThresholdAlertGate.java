package dev.winso.netherwarthelper.pest;

import java.util.OptionalInt;

/** One desktop alert per confirmed pest-threshold episode, without treating unknown data as a reset. */
public final class PestThresholdAlertGate {
	private boolean thresholdReached;

	public boolean shouldAlert(boolean enabled, boolean freshPoll, OptionalInt reportedPests, int threshold) {
		if (threshold < 1) throw new IllegalArgumentException("Pest alert threshold must be positive");
		if (!enabled) {
			thresholdReached = false;
			return false;
		}
		if (!freshPoll || reportedPests == null || reportedPests.isEmpty()) return false;
		if (reportedPests.getAsInt() < threshold) {
			thresholdReached = false;
			return false;
		}
		if (thresholdReached) return false;
		thresholdReached = true;
		return true;
	}
}
