package dev.winso.netherwarthelper.orientation;

/** Platform-independent lifecycle state for the active-session pitch lock. */
public final class PitchLockState {
	private boolean active;
	private float pitch;

	public void begin(boolean enabled, float lockedPitch) {
		active = enabled;
		pitch = lockedPitch;
	}

	public void end() {
		active = false;
	}

	public boolean isActive() {
		return active;
	}

	public float getPitch() {
		return pitch;
	}
}
