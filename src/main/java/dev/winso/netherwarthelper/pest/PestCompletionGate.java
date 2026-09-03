package dev.winso.netherwarthelper.pest;

import java.util.OptionalInt;

/** Confirms clearance from fresh Garden evidence; an absent counter alone never means zero. */
public final class PestCompletionGate {
	public static final int EXPLICIT_ZERO_POLLS = 2;
	public static final int OMITTED_COUNTER_POLLS = 5;
	public static final int LAST_POSITIVE_MAX_AGE_TICKS = 40;
	public static final int POSITIVE_HUD_LAG_TICKS = 40;
	public static final int REMOVAL_CANDIDATE_TICKS = 200;

	private int lastPositiveCount = -1;
	private int lastPositivePollTick = -1;
	private int lastFreshPollTick = -1;
	private int removalTick = -1;
	private int clearPolls;
	private EvidenceMode evidenceMode = EvidenceMode.NONE;
	private boolean confirmed;
	private String evidence = "Waiting for confirmed Garden clearance";

	public void reset() {
		lastPositiveCount = -1;
		lastPositivePollTick = -1;
		lastFreshPollTick = -1;
		removalTick = -1;
		clearPolls = 0;
		evidenceMode = EvidenceMode.NONE;
		confirmed = false;
		evidence = "Waiting for confirmed Garden clearance";
	}

	/**
	 * The caller must invoke this only after the actually vacuumed target becomes dead/removed,
	 * with no other loaded targets and recent confirmed vacuum contact. Unloading/lost sight is
	 * not a kill. A fresh previous total of exactly one supplies the independent count evidence.
	 */
	public boolean noteLastTargetRemoved(int tick) {
		if (confirmed || removalTick >= 0 || lastPositiveCount != 1 || lastPositivePollTick < 0
			|| !withinAge(tick, lastPositivePollTick, LAST_POSITIVE_MAX_AGE_TICKS)) {
			return false;
		}
		removalTick = tick;
		clearStreak();
		evidence = "Last vacuumed target removed after total 1; awaiting fresh clear HUD polls";
		return true;
	}

	/**
	 * Called each controlled tick so target reappearance vetoes pending evidence immediately.
	 * Only fresh, distinct polls can advance confirmation; confirmed clearance stays latched until reset.
	 */
	public boolean observe(
		int tick,
		boolean freshPoll,
		OptionalInt count,
		boolean inGarden,
		boolean conflictingCount,
		boolean counterFreeHud,
		boolean hasTargets
	) {
		if (confirmed) return true;
		if (removalTick >= 0 && !withinAge(tick, removalTick, REMOVAL_CANDIDATE_TICKS)) {
			disarmRemoval("Last-target removal confirmation expired");
		}
		if (!inGarden || conflictingCount) {
			disarmRemoval(!inGarden ? "Garden context unavailable" : "Conflicting pest totals");
			lastPositiveCount = -1;
			lastPositivePollTick = -1;
			return false;
		}

		OptionalInt observedCount = count == null ? OptionalInt.empty() : count;
		boolean distinctFreshPoll = freshPoll && tick >= 0 && tick != lastFreshPollTick;
		if (distinctFreshPoll) {
			lastFreshPollTick = tick;
			if (observedCount.isPresent() && observedCount.getAsInt() > 0) {
				lastPositiveCount = observedCount.getAsInt();
				lastPositivePollTick = tick;
			}
		}
		if (hasTargets) {
			disarmRemoval("Live pest targets remain");
			return false;
		}
		if (!distinctFreshPoll) {
			if (observedCount.isEmpty() && !counterFreeHud && evidenceMode == EvidenceMode.OMITTED_COUNTER) {
				clearStreak();
				evidence = "Waiting for a coherent counter-free Garden HUD";
			}
			return false;
		}

		if (observedCount.isPresent()) {
			int total = observedCount.getAsInt();
			if (total == 0) {
				advance(EvidenceMode.EXPLICIT_ZERO);
				confirmed = clearPolls >= EXPLICIT_ZERO_POLLS;
				evidence = "Explicit Garden zero: " + clearPolls + "/" + EXPLICIT_ZERO_POLLS
					+ " fresh polls with no live targets";
				return confirmed;
			}
			clearStreak();
			if (total < 0) {
				disarmRemoval("Invalid pest total");
			} else if (removalTick >= 0 && (total > 1 || !withinAge(tick, removalTick, POSITIVE_HUD_LAG_TICKS))) {
				disarmRemoval("Positive pest total contradicts the last-target removal");
			} else {
				evidence = removalTick >= 0 ? "Waiting for the last-pest HUD count to refresh"
					: "Garden still reports " + total + " pests";
			}
			return false;
		}

		if (removalTick < 0 || !counterFreeHud) {
			clearStreak();
			evidence = removalTick < 0 ? "Unknown count without a verified last-target removal"
				: "Waiting for a coherent counter-free Garden HUD";
			return false;
		}
		advance(EvidenceMode.OMITTED_COUNTER);
		confirmed = clearPolls >= OMITTED_COUNTER_POLLS;
		evidence = "Verified last-target removal and counter-free Garden HUD: " + clearPolls + "/"
			+ OMITTED_COUNTER_POLLS + " fresh polls with no live targets";
		return confirmed;
	}

	public boolean confirmed() {
		return confirmed;
	}

	public boolean pendingLastRemoval() {
		return removalTick >= 0 && !confirmed;
	}

	public int confirmedPolls() {
		return clearPolls;
	}

	public String evidence() {
		return evidence;
	}

	/** A small bounded grace is only for finishing an already-armed last-removal confirmation. */
	public boolean canFinishAfterDeadline(int tick) {
		return pendingLastRemoval() && withinAge(tick, removalTick, REMOVAL_CANDIDATE_TICKS);
	}

	private void advance(EvidenceMode nextMode) {
		if (evidenceMode != nextMode) clearPolls = 0;
		evidenceMode = nextMode;
		clearPolls++;
	}

	private void disarmRemoval(String reason) {
		removalTick = -1;
		clearStreak();
		evidence = reason;
	}

	private void clearStreak() {
		clearPolls = 0;
		evidenceMode = EvidenceMode.NONE;
	}

	private static boolean withinAge(int tick, int previousTick, int maximumAge) {
		long age = (long) tick - previousTick;
		return age >= 0 && age <= maximumAge;
	}

	private enum EvidenceMode { NONE, EXPLICIT_ZERO, OMITTED_COUNTER }
}
