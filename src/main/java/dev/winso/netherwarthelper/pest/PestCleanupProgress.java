package dev.winso.netherwarthelper.pest;

import java.util.OptionalInt;

/** Tracks authoritative reductions so an active multi-pest cleanup is not timed out just after progress. */
public final class PestCleanupProgress {
	private int lowestReportedCount = -1;
	private int lastProgressTick;

	public void reset(int initialCount) {
		lowestReportedCount = Math.max(-1, initialCount);
		lastProgressTick = 0;
	}

	public boolean observe(int tick, boolean freshPoll, OptionalInt reportedCount) {
		if (tick < 0) throw new IllegalArgumentException("Cleanup tick must not be negative");
		if (!freshPoll || reportedCount == null || reportedCount.isEmpty()) return false;
		int count = reportedCount.getAsInt();
		if (count < 0) return false;
		if (lowestReportedCount < 0) {
			lowestReportedCount = count;
			return false;
		}
		if (count >= lowestReportedCount) return false;
		lowestReportedCount = count;
		lastProgressTick = tick;
		return true;
	}

	public int deadlineTick(int inactivityBudgetTicks, int hardDeadlineTick) {
		if (inactivityBudgetTicks < 1 || hardDeadlineTick < 1) {
			throw new IllegalArgumentException("Cleanup deadlines must be positive");
		}
		return Math.min(hardDeadlineTick, saturatedAdd(lastProgressTick, inactivityBudgetTicks));
	}

	public int lowestReportedCount() {
		return lowestReportedCount;
	}

	public int lastProgressTick() {
		return lastProgressTick;
	}

	private static int saturatedAdd(int first, int second) {
		long sum = (long) first + second;
		return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
	}
}
