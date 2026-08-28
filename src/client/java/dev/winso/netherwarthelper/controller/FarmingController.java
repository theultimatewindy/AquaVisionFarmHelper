package dev.winso.netherwarthelper.controller;

import dev.winso.netherwarthelper.config.FarmConfig;
import dev.winso.netherwarthelper.input.InputController;
import dev.winso.netherwarthelper.movement.DirectionMath;
import dev.winso.netherwarthelper.movement.MovementMonitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FarmingController {
	private static final Logger LOGGER = LoggerFactory.getLogger("NetherWartFarmHelper/Controller");

	private final InputController inputs;
	private final MovementMonitor movementMonitor = new MovementMonitor();

	private FarmConfig config = new FarmConfig();
	private FarmingState state = FarmingState.IDLE;
	private FarmingState pausedState = FarmingState.IDLE;
	private FarmingDirection direction = FarmingDirection.LEFT;
	private boolean sessionRunning;
	private int lane = 1;
	private int transitionTimer;
	private float startingYaw;
	private float startingPitch;
	private ClientLevel sessionLevel;
	private LocalPlayer sessionPlayer;
	private double pauseX;
	private double pauseZ;
	private double debugX;
	private double debugZ;
	private boolean debugHorizontalCollision;

	public FarmingController(InputController inputs) {
		this.inputs = inputs;
	}

	public boolean start(Minecraft minecraft, FarmConfig newConfig) {
		inputs.releaseAll();
		if (minecraft.player == null || minecraft.level == null
			|| !minecraft.player.isAlive() || minecraft.player.isRemoved()) {
			announce(minecraft, "Farm Helper: enter a world with a living player before starting.");
			return false;
		}

		config = newConfig;
		config.validate();
		sessionRunning = true;
		lane = 1;
		transitionTimer = 0;
		startingYaw = minecraft.player.getYRot();
		startingPitch = minecraft.player.getXRot();
		sessionLevel = minecraft.level;
		sessionPlayer = minecraft.player;
		direction = config.startingDirection;
		enterFarmingState(minecraft.player, direction);
		inputs.applyFarming(direction, config.holdAttack);
		LOGGER.info("Started in {} at yaw {} and pitch {}", direction, startingYaw, startingPitch);
		announce(minecraft, "Farm Helper started (" + direction + ", lane 1)");
		return true;
	}

	public void stop(Minecraft minecraft) {
		inputs.releaseAll();
		sessionRunning = false;
		state = FarmingState.IDLE;
		pausedState = FarmingState.IDLE;
		resetTemporaryState();
		LOGGER.info("Stopped");
		announce(minecraft, "Farm Helper stopped");
	}

	public void emergencyStop(Minecraft minecraft) {
		inputs.releaseAll();
		sessionRunning = false;
		state = FarmingState.STOPPED;
		pausedState = FarmingState.IDLE;
		resetTemporaryState();
		LOGGER.warn("Emergency stop");
		announce(minecraft, "Farm Helper EMERGENCY STOP");
	}

	public void failSafeStop(Minecraft minecraft, String reason) {
		inputs.releaseAll();
		sessionRunning = false;
		state = FarmingState.STOPPED;
		pausedState = FarmingState.IDLE;
		resetTemporaryState();
		LOGGER.warn("Fail-safe stop: {}", reason);
		announce(minecraft, "Farm Helper stopped safely: " + reason);
	}

	public void shutdown() {
		inputs.releaseAll();
		sessionRunning = false;
		state = FarmingState.STOPPED;
		pausedState = FarmingState.IDLE;
		resetTemporaryState();
	}

	public void togglePause(Minecraft minecraft) {
		if (!sessionRunning) {
			return;
		}
		if (state == FarmingState.PAUSED) {
			resume(minecraft);
		} else {
			pause(minecraft, "Paused");
		}
	}

	public void tick(Minecraft minecraft) {
		if (!sessionRunning) {
			return;
		}

		try {
			tickSafely(minecraft);
		} catch (RuntimeException exception) {
			inputs.releaseAll();
			sessionRunning = false;
			state = FarmingState.STOPPED;
			resetTemporaryState();
			LOGGER.error("Unexpected controller failure; all controlled inputs were released", exception);
			announce(minecraft, "Farm Helper stopped after an unexpected error. Check latest.log.");
		}
	}

	private void tickSafely(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			failSafeStop(minecraft, "world or player became unavailable");
			return;
		}
		if (!player.isAlive() || player.isRemoved()) {
			failSafeStop(minecraft, "player died");
			return;
		}
		if (player != sessionPlayer || minecraft.level != sessionLevel) {
			failSafeStop(minecraft, "world or player instance changed");
			return;
		}

		debugX = player.getX();
		debugZ = player.getZ();
		debugHorizontalCollision = player.horizontalCollision;

		if (state == FarmingState.PAUSED) {
			inputs.releaseAll();
			return;
		}

		if (config.pauseWhenScreenOpen && minecraft.gui.screen() != null) {
			pause(minecraft, "A screen was opened");
			return;
		}

		if (config.orientationGuardEnabled && orientationDifference(player) > config.orientationToleranceDegrees) {
			pause(minecraft, "Camera yaw moved outside the configured tolerance");
			return;
		}

		switch (state) {
			case FARM_LEFT -> handleFarming(player, FarmingDirection.LEFT);
			case FARM_RIGHT -> handleFarming(player, FarmingDirection.RIGHT);
			case END_LEFT_DETECTED -> beginForwardShift(true);
			case END_RIGHT_DETECTED -> beginForwardShift(false);
			case SHIFT_FORWARD_AFTER_LEFT -> handleForwardShift(player, true);
			case SHIFT_FORWARD_AFTER_RIGHT -> handleForwardShift(player, false);
			case IDLE, PAUSED, STOPPED -> inputs.releaseAll();
		}
	}

	private void handleFarming(LocalPlayer player, FarmingDirection expectedDirection) {
		direction = expectedDirection;
		inputs.applyFarming(expectedDirection, config.holdAttack);

		DirectionMath.HorizontalVector expectedVector = DirectionMath.lateralUnit(
			startingYaw,
			expectedDirection.isLeft()
		);
		boolean laneEnd = movementMonitor.update(
			player.getX(),
			player.getZ(),
			expectedVector,
			config.minimumMovementDelta,
			config.stuckDetectionTicks,
			config.laneStartGraceTicks
		);

		if (laneEnd) {
			inputs.releaseAll();
			state = expectedDirection == FarmingDirection.LEFT
				? FarmingState.END_LEFT_DETECTED
				: FarmingState.END_RIGHT_DETECTED;
			LOGGER.info(
				"Lane {} end detected while farming {} (progress={}, stuckTicks={})",
				lane,
				expectedDirection,
				movementMonitor.getLastExpectedProgress(),
				movementMonitor.getStuckCounter()
			);
		}
	}

	private void beginForwardShift(boolean afterLeft) {
		state = afterLeft
			? FarmingState.SHIFT_FORWARD_AFTER_LEFT
			: FarmingState.SHIFT_FORWARD_AFTER_RIGHT;
		inputs.applyForwardShift();
		transitionTimer = 1;
		LOGGER.info("Shifting forward after lane {}", lane);
	}

	private void handleForwardShift(LocalPlayer player, boolean afterLeft) {
		if (transitionTimer < config.forwardShiftTicks) {
			inputs.applyForwardShift();
			transitionTimer++;
			return;
		}

		inputs.releaseAll();
		int completedSettleTicks = transitionTimer - config.forwardShiftTicks;
		if (completedSettleTicks < config.transitionSettleTicks) {
			transitionTimer++;
			return;
		}

		lane++;
		FarmingDirection nextDirection = afterLeft ? FarmingDirection.RIGHT : FarmingDirection.LEFT;
		enterFarmingState(player, nextDirection);
		inputs.applyFarming(nextDirection, config.holdAttack);
		LOGGER.info("Entering {} on lane {}", state, lane);
	}

	private void enterFarmingState(LocalPlayer player, FarmingDirection newDirection) {
		direction = newDirection;
		state = newDirection == FarmingDirection.LEFT ? FarmingState.FARM_LEFT : FarmingState.FARM_RIGHT;
		transitionTimer = 0;
		movementMonitor.reset(player.getX(), player.getZ());
	}

	private void pause(Minecraft minecraft, String reason) {
		if (!sessionRunning || state == FarmingState.PAUSED || minecraft.player == null) {
			return;
		}
		pausedState = state;
		pauseX = minecraft.player.getX();
		pauseZ = minecraft.player.getZ();
		inputs.releaseAll();
		state = FarmingState.PAUSED;
		LOGGER.info("Paused from {}: {}", pausedState, reason);
		announce(minecraft, "Farm Helper paused: " + reason);
	}

	private void resume(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !player.isAlive() || player.isRemoved()) {
			failSafeStop(minecraft, "cannot resume without a living player in a world");
			return;
		}
		if (config.pauseWhenScreenOpen && minecraft.gui.screen() != null) {
			announce(minecraft, "Close the current screen before resuming Farm Helper.");
			return;
		}
		if (config.orientationGuardEnabled && orientationDifference(player) > config.orientationToleranceDegrees) {
			announce(minecraft, "Return the camera to its starting yaw before resuming.");
			return;
		}

		double pausedMovement = Math.hypot(player.getX() - pauseX, player.getZ() - pauseZ);
		if (pausedMovement > config.pausePositionTolerance) {
			failSafeStop(minecraft, "player moved while paused; realign and start a new session");
			return;
		}

		state = pausedState.isFarming() || pausedState.isShifting()
			|| pausedState == FarmingState.END_LEFT_DETECTED
			|| pausedState == FarmingState.END_RIGHT_DETECTED
			? pausedState
			: (direction == FarmingDirection.LEFT ? FarmingState.FARM_LEFT : FarmingState.FARM_RIGHT);
		if (state.isFarming()) {
			movementMonitor.reset(player.getX(), player.getZ());
		}
		LOGGER.info("Resumed into {}", state);
		announce(minecraft, "Farm Helper resumed");
	}

	private double orientationDifference(LocalPlayer player) {
		return Mth.degreesDifferenceAbs(startingYaw, player.getYRot());
	}

	private void resetTemporaryState() {
		lane = 1;
		transitionTimer = 0;
		sessionLevel = null;
		sessionPlayer = null;
		movementMonitor.clear();
	}

	private static void announce(Minecraft minecraft, String message) {
		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(Component.literal(message));
		}
	}

	public boolean isSessionRunning() {
		return sessionRunning;
	}

	public boolean isPaused() {
		return state == FarmingState.PAUSED;
	}

	public FarmingState getState() {
		return state;
	}

	public FarmingDirection getDirection() {
		return direction;
	}

	public int getLane() {
		return lane;
	}

	public int getTransitionTimer() {
		return transitionTimer;
	}

	public double getLastHorizontalDelta() {
		return movementMonitor.getLastHorizontalDelta();
	}

	public double getLastExpectedProgress() {
		return movementMonitor.getLastExpectedProgress();
	}

	public int getStuckCounter() {
		return movementMonitor.getStuckCounter();
	}

	public double getDebugX() {
		return debugX;
	}

	public double getDebugZ() {
		return debugZ;
	}

	public boolean hasHorizontalCollision() {
		return debugHorizontalCollision;
	}
}
