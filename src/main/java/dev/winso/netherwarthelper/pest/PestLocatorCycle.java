package dev.winso.netherwarthelper.pest;

/** One locator click, a bounded particle capture, then uninterrupted trail following. */
public final class PestLocatorCycle {
	public static final int CLICK_INTERVAL_TICKS = 80;
	public static final int MIN_CAPTURE_TICKS = 10;
	public static final int QUIET_TICKS = 6;
	public static final int MAX_CAPTURE_TICKS = 40;
	public static final int MAX_FOLLOW_TICKS = 160;
	private State state = State.IDLE;
	private int nextClickTick;
	private int stateStartTick;
	private int lastParticleTick;
	private long lastParticleSequence;
	private int clickCount;

	public enum State { IDLE, WAITING, CAPTURING, FOLLOWING }
	public enum Action { WAIT, CLICK, FOLLOW, NO_TRAIL, FOLLOW_TIMEOUT }

	/** Reacquire after a target disappears or a waypoint is reached; never reset the click cooldown. */
	public void beginSearch(int tick) {
		state = State.WAITING;
		stateStartTick = tick;
		lastParticleSequence = 0;
	}

	public Action tick(int tick, boolean canClick, long particleSequence, boolean reliableDirection) {
		if (state == State.WAITING && tick > stateStartTick && tick >= nextClickTick && canClick) {
			state = State.CAPTURING;
			stateStartTick = tick;
			lastParticleTick = tick;
			lastParticleSequence = 0;
			nextClickTick = tick + CLICK_INTERVAL_TICKS;
			clickCount++;
			return Action.CLICK;
		}
		if (state == State.CAPTURING) {
			if (particleSequence != lastParticleSequence) {
				lastParticleSequence = particleSequence;
				lastParticleTick = tick;
			}
			int elapsed = tick - stateStartTick;
			if (reliableDirection && elapsed >= MIN_CAPTURE_TICKS
				&& (tick - lastParticleTick >= QUIET_TICKS || elapsed >= MAX_CAPTURE_TICKS)) {
				state = State.FOLLOWING;
				stateStartTick = tick;
				return Action.FOLLOW;
			}
			if (elapsed >= MAX_CAPTURE_TICKS) {
				beginSearch(tick);
				return Action.NO_TRAIL;
			}
		}
		if (state == State.FOLLOWING && tick - stateStartTick >= MAX_FOLLOW_TICKS) {
			beginSearch(tick);
			return Action.FOLLOW_TIMEOUT;
		}
		return Action.WAIT;
	}

	/** A live target or plot/return navigation owns controls now. Keep the last locator cooldown. */
	public void suspend() {
		state = State.IDLE;
	}

	public void reset() {
		state = State.IDLE;
		nextClickTick = 0;
		stateStartTick = 0;
		lastParticleTick = 0;
		lastParticleSequence = 0;
		clickCount = 0;
	}

	public State state() { return state; }
	public int clickCount() { return clickCount; }
	public int cooldownTicks(int tick) { return Math.max(0, nextClickTick - tick); }
}
