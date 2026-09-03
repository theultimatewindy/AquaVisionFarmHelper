package dev.winso.netherwarthelper.pest;

/** Completing the hunt starts a separate, bounded return trip instead of reusing its expired budget. */
public final class PestRunDeadline {
	public static final int RETURN_TIMEOUT_TICKS = 60 * 20;
	public static final int MAX_RETURN_TIMEOUT_TICKS = 300 * 20;

	private PestRunDeadline() { }

	public static Failure check(int elapsedTicks, int cleanupBudgetTicks, int returnStartTick,
		boolean finalPestConfirmationGrace) {
		return check(elapsedTicks, cleanupBudgetTicks, returnStartTick,
			finalPestConfirmationGrace, RETURN_TIMEOUT_TICKS);
	}

	/** A route may select a longer return budget, but never an unbounded one. */
	public static Failure check(int elapsedTicks, int cleanupBudgetTicks, int returnStartTick,
		boolean finalPestConfirmationGrace, int returnBudgetTicks) {
		if (returnStartTick >= 0) {
			int boundedReturnBudget = Math.clamp(returnBudgetTicks, RETURN_TIMEOUT_TICKS, MAX_RETURN_TIMEOUT_TICKS);
			return (long) elapsedTicks - returnStartTick > boundedReturnBudget ? Failure.RETURN : Failure.NONE;
		}
		return elapsedTicks > cleanupBudgetTicks && !finalPestConfirmationGrace ? Failure.CLEANUP : Failure.NONE;
	}

	public enum Failure { NONE, CLEANUP, RETURN }
}
