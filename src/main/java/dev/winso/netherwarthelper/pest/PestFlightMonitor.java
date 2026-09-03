package dev.winso.netherwarthelper.pest;

/** Permitted flight must survive takeoff before navigation can own the movement keys. */
public final class PestFlightMonitor {
	public static final int TAKEOFF_TIMEOUT_TICKS = 60;
	public static final int REQUEST_INTERVAL_TICKS = 10;
	public static final int MAX_REQUESTS = 3;
	public static final int MAX_RECOVERIES = 3;
	private static final int CONFIRMATION_TICKS = 3;
	private Phase phase = Phase.IDLE;
	private boolean needsHeightGain;
	private double takeoffY;
	private int attemptTicks;
	private int confirmedTicks;
	private int requestCooldown;
	private int requests;
	private int recoveries;
	private String failure = "";

	public Step tick(boolean mayFly, boolean flying, boolean onGround, double y) {
		if (phase == Phase.FAILED) return new Step(false, false, false, failure);
		if (!mayFly) return fail("Garden flight permission is unavailable");
		if (!Double.isFinite(y)) return fail("player height is unavailable during takeoff");
		if (phase == Phase.IDLE) {
			if (flying && !onGround) {
				phase = Phase.FLYING;
				return ready();
			}
			beginAttempt(onGround, y);
		} else if (phase == Phase.FLYING) {
			if (flying && !onGround) return ready();
			if (++recoveries > MAX_RECOVERIES) return fail("flight was repeatedly lost during pest cleanup");
			beginAttempt(onGround, y);
		}

		if (++attemptTicks > TAKEOFF_TIMEOUT_TICKS) {
			return fail("could not take off and confirm flight; check overhead clearance and flight permission");
		}
		requestCooldown = Math.max(0, requestCooldown - 1);
		// A two-block-high passage leaves only about 0.2 blocks above a standing player.
		// Require a small real lift, not a full jump height, before confirming sustained flight.
		if (flying && !onGround && (!needsHeightGain || y >= takeoffY + 0.05)) {
			phase = Phase.CONFIRMING;
			if (++confirmedTicks >= CONFIRMATION_TICKS) {
				phase = Phase.FLYING;
				return ready();
			}
			return waiting(false, false);
		}
		confirmedTicks = 0;
		if (onGround) {
			phase = Phase.TAKING_OFF;
			// Vanilla clears flight on landing. Lift off normally instead of sending it on the ground.
			return waiting(true, false);
		}
		phase = Phase.CONFIRMING;
		if (flying) return waiting(needsHeightGain, false);
		if (requestCooldown > 0) return waiting(false, false);
		if (requests >= MAX_REQUESTS) return fail("the server did not retain the permitted flight state");
		requests++;
		requestCooldown = REQUEST_INTERVAL_TICKS;
		return waiting(false, true);
	}

	private void beginAttempt(boolean onGround, double y) {
		phase = Phase.TAKING_OFF;
		needsHeightGain = onGround;
		takeoffY = y;
		attemptTicks = confirmedTicks = requestCooldown = requests = 0;
	}

	private static Step ready() { return new Step(true, false, false, ""); }
	private static Step waiting(boolean jump, boolean request) { return new Step(false, jump, request, ""); }
	private Step fail(String reason) {
		phase = Phase.FAILED;
		failure = reason;
		return new Step(false, false, false, reason);
	}

	public Phase getPhase() { return phase; }
	public int getRequests() { return requests; }
	public int getRecoveries() { return recoveries; }

	public void reset() {
		phase = Phase.IDLE;
		needsHeightGain = false;
		takeoffY = 0;
		attemptTicks = confirmedTicks = requestCooldown = requests = recoveries = 0;
		failure = "";
	}

	public enum Phase { IDLE, TAKING_OFF, CONFIRMING, FLYING, FAILED }
	public record Step(boolean ready, boolean holdJump, boolean requestFlight, String failure) {
		public boolean failed() { return !failure.isEmpty(); }
	}
}
