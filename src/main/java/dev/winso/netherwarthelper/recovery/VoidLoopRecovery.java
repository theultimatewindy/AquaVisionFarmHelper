package dev.winso.netherwarthelper.recovery;

/**
 * Platform-independent state for an intentional farm-end void loop.
 *
 * <p>The client integration owns input release, the vanilla respawn request, and the final macro
 * restart. This class decides when those actions are safe.</p>
 */
public final class VoidLoopRecovery {
	public static final int TICKS_PER_SECOND = 20;
	public static final int RESPAWN_SCREEN_DELAY_TICKS = TICKS_PER_SECOND;
	public static final int FALL_WARP_DELAY_TICKS = 5 * TICKS_PER_SECOND;
	public static final int RECOVERY_TIMEOUT_TICKS = 30 * TICKS_PER_SECOND;
	private static final double MINIMUM_FALLING_SPEED = -0.01;
	private static final double MINIMUM_FALLING_PROGRESS = 0.01;
	private static final double FALL_MONITOR_START_DISTANCE = 0.5;

	public enum Phase {
		INACTIVE,
		MONITORING,
		FALLING,
		WAITING_FOR_RESPAWN,
		WAITING_TO_WARP,
		WAITING_FOR_START
	}

	public enum Action {
		NONE,
		REQUEST_RESPAWN,
		SEND_WARP_COMMAND,
		RESTART,
		ABORT_NON_VOID_DEATH,
		ABORT_TIMEOUT
	}

	public record PlayerSnapshot(
		boolean available,
		boolean alive,
		boolean removed,
		boolean onGround,
		boolean screenOpen,
		boolean deathScreenOpen,
		boolean clientReady,
		boolean sameDimension,
		boolean originalPlayer,
		boolean originalDeathObserved,
		boolean originalVoidDeath,
		boolean belowWorldMinimum,
		boolean voidDamage,
		double x,
		double y,
		double z,
		double verticalVelocity
	) {
	}

	private boolean enabled;
	private Phase phase = Phase.INACTIVE;
	private double startX;
	private double startY;
	private double startZ;
	private double fallTriggerDistance;
	private double startTolerance;
	private int restartDelayTicks;
	private int recoveryTicks;
	private int continuousFallingTicks;
	private int deathScreenTicks;
	private int postWarpTicks;
	private double lastObservedY = Double.NaN;
	private boolean skipNextFallingIncrement;
	private boolean voidZoneObserved;
	private boolean voidConfirmed;
	private boolean respawnRequested;
	private boolean warpCommandSent;
	private boolean warpCommandSubmitted;

	public void startSession(
		boolean enabled,
		double startX,
		double startY,
		double startZ,
		double fallTriggerDistance,
		double startTolerance,
		int restartDelayTicks
	) {
		this.enabled = enabled;
		this.startX = startX;
		this.startY = startY;
		this.startZ = startZ;
		this.fallTriggerDistance = fallTriggerDistance;
		this.startTolerance = startTolerance;
		this.restartDelayTicks = restartDelayTicks;
		phase = enabled ? Phase.MONITORING : Phase.INACTIVE;
		resetRecoveryCounters();
	}

	/** Returns true once when a significant downward departure starts the expected void loop. */
	public boolean observeActivePlayer(
		double currentY,
		double verticalVelocity,
		boolean deadOrRemoved,
		boolean voidDamage,
		boolean belowWorldMinimum
	) {
		if (!enabled || phase != Phase.MONITORING) {
			return false;
		}

		boolean yMovedDown = !Double.isNaN(lastObservedY)
			&& currentY < lastObservedY - MINIMUM_FALLING_PROGRESS;
		boolean velocityUnavailable = Math.abs(verticalVelocity) <= Math.abs(MINIMUM_FALLING_SPEED);
		boolean downwardProgress = verticalVelocity < MINIMUM_FALLING_SPEED
			|| (velocityUnavailable && yMovedDown);
		boolean belowFallStart = currentY <= startY - FALL_MONITOR_START_DISTANCE;
		if (!deadOrRemoved && belowFallStart && downwardProgress) {
			continuousFallingTicks++;
		} else {
			continuousFallingTicks = 0;
		}
		lastObservedY = currentY;

		boolean belowTrigger = currentY <= startY - fallTriggerDistance;
		boolean significantFall = belowTrigger && downwardProgress;
		boolean fallDelayReached = continuousFallingTicks >= FALL_WARP_DELAY_TICKS;
		if (!significantFall && !fallDelayReached && !(deadOrRemoved && voidDamage)) {
			return false;
		}

		voidZoneObserved = voidDamage || belowWorldMinimum;
		voidConfirmed = deadOrRemoved && voidDamage;
		phase = deadOrRemoved || voidDamage ? Phase.WAITING_FOR_RESPAWN : Phase.FALLING;
		recoveryTicks = 0;
		deathScreenTicks = 0;
		postWarpTicks = 0;
		respawnRequested = false;
		skipNextFallingIncrement = phase == Phase.FALLING;
		return true;
	}

	public Action tickRecovery(PlayerSnapshot player) {
		if (!isRecovering()) {
			return Action.NONE;
		}

		recoveryTicks++;
		if (recoveryTicks >= RECOVERY_TIMEOUT_TICKS) {
			return Action.ABORT_TIMEOUT;
		}
		if (warpCommandSubmitted) {
			postWarpTicks++;
		}
		if (player.originalDeathObserved()) {
			if (!player.originalVoidDeath() && !voidConfirmed) {
				return Action.ABORT_NON_VOID_DEATH;
			}
			if (player.originalVoidDeath()) {
				voidConfirmed = true;
			}
		}
		if (!player.available()) {
			continuousFallingTicks = 0;
			lastObservedY = Double.NaN;
			skipNextFallingIncrement = false;
			return Action.NONE;
		}

		if (player.belowWorldMinimum() || (player.alive() && player.voidDamage())) {
			voidZoneObserved = true;
		}

		boolean farmablePlayer = player.alive() && !player.removed();
		boolean yMovedDown = !Double.isNaN(lastObservedY)
			&& player.y() < lastObservedY - MINIMUM_FALLING_PROGRESS;
		boolean velocityUnavailable = Math.abs(player.verticalVelocity()) <= Math.abs(MINIMUM_FALLING_SPEED);
		boolean downwardProgress = player.verticalVelocity() < MINIMUM_FALLING_SPEED
			|| (velocityUnavailable && yMovedDown);
		boolean stillFalling = phase == Phase.FALLING
			&& farmablePlayer
			&& (downwardProgress || skipNextFallingIncrement);
		if (stillFalling) {
			if (skipNextFallingIncrement) {
				skipNextFallingIncrement = false;
			} else {
				continuousFallingTicks++;
			}
		} else {
			continuousFallingTicks = 0;
			skipNextFallingIncrement = false;
		}
		lastObservedY = player.y();
		if (!farmablePlayer) {
			boolean confirmedOriginalVoidDeath = player.originalPlayer()
				&& (voidConfirmed || player.voidDamage() || player.originalVoidDeath());
			if (!confirmedOriginalVoidDeath) {
				return Action.ABORT_NON_VOID_DEATH;
			}
			voidConfirmed = true;
		}

		boolean returnedAfterCandidateFall = farmablePlayer
			&& ((!player.originalPlayer())
				|| (player.onGround() && player.verticalVelocity() >= MINIMUM_FALLING_SPEED));
		boolean fallDelayReached = continuousFallingTicks >= FALL_WARP_DELAY_TICKS;
		boolean readyToSendWarp = (voidConfirmed || returnedAfterCandidateFall || fallDelayReached)
			&& farmablePlayer
			&& (fallDelayReached || player.clientReady())
			&& !player.screenOpen();
		if (readyToSendWarp && !warpCommandSent) {
			warpCommandSent = true;
			warpCommandSubmitted = false;
			phase = Phase.WAITING_TO_WARP;
			postWarpTicks = 0;
			return Action.SEND_WARP_COMMAND;
		}

		boolean readyToRestart = warpCommandSubmitted
			&& postWarpTicks >= restartDelayTicks
			&& farmablePlayer
			&& !player.screenOpen();
		if (readyToRestart) {
			phase = Phase.WAITING_FOR_START;
			return Action.RESTART;
		}
		if (farmablePlayer && !player.originalPlayer()) {
			phase = warpCommandSubmitted ? Phase.WAITING_FOR_START : Phase.WAITING_TO_WARP;
		}

		if (phase == Phase.FALLING) {
			if (!farmablePlayer) {
				if (!voidConfirmed) {
					return Action.ABORT_NON_VOID_DEATH;
				}
				phase = Phase.WAITING_FOR_RESPAWN;
			} else if (returnedAfterCandidateFall) {
				phase = Phase.WAITING_TO_WARP;
			}
		}

		if (!farmablePlayer) {
			if (!respawnRequested) {
				phase = Phase.WAITING_FOR_RESPAWN;
				if (player.deathScreenOpen()) {
					deathScreenTicks++;
					if (deathScreenTicks >= RESPAWN_SCREEN_DELAY_TICKS) {
						respawnRequested = true;
						phase = Phase.WAITING_FOR_RESPAWN;
						return Action.REQUEST_RESPAWN;
					}
				} else {
					deathScreenTicks = 0;
				}
			}
			return Action.NONE;
		}

		if (phase != Phase.FALLING) {
			phase = warpCommandSubmitted ? Phase.WAITING_FOR_START : Phase.WAITING_TO_WARP;
		}
		return Action.NONE;
	}

	public void confirmWarpCommandSubmitted() {
		if (!warpCommandSent) {
			return;
		}
		warpCommandSubmitted = true;
		postWarpTicks = 0;
		phase = Phase.WAITING_FOR_START;
	}

	public void completeRestart(double newStartX, double newStartY, double newStartZ) {
		startX = newStartX;
		startY = newStartY;
		startZ = newStartZ;
		phase = enabled ? Phase.MONITORING : Phase.INACTIVE;
		resetRecoveryCounters();
	}

	public void clear() {
		enabled = false;
		phase = Phase.INACTIVE;
		startX = 0.0;
		startY = 0.0;
		startZ = 0.0;
		fallTriggerDistance = 0.0;
		startTolerance = 0.0;
		restartDelayTicks = 0;
		resetRecoveryCounters();
	}

	private void resetRecoveryCounters() {
		recoveryTicks = 0;
		continuousFallingTicks = 0;
		deathScreenTicks = 0;
		postWarpTicks = 0;
		lastObservedY = Double.NaN;
		skipNextFallingIncrement = false;
		voidZoneObserved = false;
		voidConfirmed = false;
		respawnRequested = false;
		warpCommandSent = false;
		warpCommandSubmitted = false;
	}

	public boolean isRecovering() {
		return phase == Phase.FALLING
			|| phase == Phase.WAITING_FOR_RESPAWN
			|| phase == Phase.WAITING_TO_WARP
			|| phase == Phase.WAITING_FOR_START;
	}

	public Phase getPhase() {
		return phase;
	}

	public int getRecoveryTicks() {
		return recoveryTicks;
	}
}
