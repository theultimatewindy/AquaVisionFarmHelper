package dev.winso.netherwarthelper.controller;

public enum FarmingState {
	IDLE,
	FARM_LEFT,
	END_LEFT_DETECTED,
	SHIFT_FORWARD_AFTER_LEFT,
	FARM_RIGHT,
	END_RIGHT_DETECTED,
	SHIFT_FORWARD_AFTER_RIGHT,
	PAUSED,
	STOPPED;

	public boolean isFarming() {
		return this == FARM_LEFT || this == FARM_RIGHT;
	}

	public boolean isShifting() {
		return this == SHIFT_FORWARD_AFTER_LEFT || this == SHIFT_FORWARD_AFTER_RIGHT;
	}
}
