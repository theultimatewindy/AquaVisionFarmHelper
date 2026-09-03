package dev.winso.netherwarthelper.pest;

/**
 * High-level phases of one pest-cleanup interruption.
 */
public enum PestPhase {
	IDLE,
	PREPARING,
	TAKING_OFF,
	TRAVELLING_TO_PLOT,
	LOCATING,
	FOLLOWING_TRAIL,
	APPROACHING,
	VACUUMING,
	CONFIRMING_CLEAR,
	RETURNING,
	COMPLETE,
	FAILED;

	public boolean isActive() {
		return this != IDLE && !isTerminal();
	}

	public boolean isTerminal() {
		return this == COMPLETE || this == FAILED;
	}
}
