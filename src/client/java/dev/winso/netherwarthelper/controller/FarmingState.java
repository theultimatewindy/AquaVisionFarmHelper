package dev.winso.netherwarthelper.controller;

public enum FarmingState {
	IDLE,
	FARM_LEFT,
	END_LEFT_DETECTED,
	SHIFT_FORWARD_AFTER_LEFT,
	FORWARD_END_AFTER_LEFT_DETECTED,
	FARM_RIGHT,
	END_RIGHT_DETECTED,
	SHIFT_FORWARD_AFTER_RIGHT,
	FORWARD_END_AFTER_RIGHT_DETECTED,
	VOID_FALLING,
	WAITING_FOR_RESPAWN,
	WAITING_TO_WARP,
	WAITING_TO_RESTART,
	PEST_CLEANUP,
	PAUSED,
	STOPPED;

	public boolean isFarming() {
		return this == FARM_LEFT || this == FARM_RIGHT;
	}

	public boolean isShifting() {
		return this == SHIFT_FORWARD_AFTER_LEFT
			|| this == SHIFT_FORWARD_AFTER_RIGHT
			|| this == FORWARD_END_AFTER_LEFT_DETECTED
			|| this == FORWARD_END_AFTER_RIGHT_DETECTED;
	}

	public boolean isRecovering() {
		return this == VOID_FALLING
			|| this == WAITING_FOR_RESPAWN
			|| this == WAITING_TO_WARP
			|| this == WAITING_TO_RESTART;
	}
}
