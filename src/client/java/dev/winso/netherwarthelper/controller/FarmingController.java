package dev.winso.netherwarthelper.controller;

import dev.winso.netherwarthelper.background.BackgroundOperationController;
import dev.winso.netherwarthelper.config.FarmConfig;
import dev.winso.netherwarthelper.failsafe.NoWartFailsafeMonitor;
import dev.winso.netherwarthelper.input.InputController;
import dev.winso.netherwarthelper.movement.DirectionMath;
import dev.winso.netherwarthelper.movement.MovementMonitor;
import dev.winso.netherwarthelper.notification.DesktopNotifier;
import dev.winso.netherwarthelper.orientation.PitchLockRuntime;
import dev.winso.netherwarthelper.pest.PestNavigationMath;
import dev.winso.netherwarthelper.recovery.PostWarpRestartCountdown;
import dev.winso.netherwarthelper.recovery.VoidLoopRecovery;
import dev.winso.netherwarthelper.recovery.VoidLoopRecovery.Action;
import dev.winso.netherwarthelper.recovery.VoidLoopRecovery.Phase;
import dev.winso.netherwarthelper.recovery.VoidLoopRecovery.PlayerSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FarmingController {
	private static final Logger LOGGER = LoggerFactory.getLogger("AquaVisionOP/Controller");
	private static final String BRAND = "Aqua Vision is OP";
	private static final String VOID_RETURN_COMMAND = "warp garden";
	private static final int WARP_CHAT_VISIBLE_TICKS = VoidLoopRecovery.TICKS_PER_SECOND;
	private static final double DIRECTIONAL_WALL_PROBE_DISTANCE = 0.02;
	private static final double WALL_PROBE_EDGE_EPSILON = 1.0E-5;

	private final InputController inputs;
	private final DesktopNotifier desktopNotifier;
	private final BackgroundOperationController backgroundOperation;
	private final PitchLockRuntime pitchLock = new PitchLockRuntime();
	private final MovementMonitor movementMonitor = new MovementMonitor();
	private final NoWartFailsafeMonitor noWartFailsafe = new NoWartFailsafeMonitor();
	private final VoidLoopRecovery voidLoopRecovery = new VoidLoopRecovery();
	private final PostWarpRestartCountdown postWarpRestartCountdown = new PostWarpRestartCountdown();

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
	private ResourceKey<Level> sessionDimension;
	private double pauseX;
	private double pauseY;
	private double pauseZ;
	private double debugX;
	private double debugZ;
	private boolean debugHorizontalCollision;
	private boolean wartBrokenSinceLastTick;
	private ChatScreen pendingWarpChatScreen;
	private int pendingWarpChatTicks;
	private boolean diagnosticWarpRequested;
	private boolean diagnosticWarpAutoSubmit;
	private ChatScreen diagnosticWarpChatScreen;
	private int diagnosticWarpChatTicks;

	public FarmingController(
		InputController inputs,
		DesktopNotifier desktopNotifier,
		BackgroundOperationController backgroundOperation
	) {
		this.inputs = inputs;
		this.desktopNotifier = desktopNotifier;
		this.backgroundOperation = backgroundOperation;
	}

	public boolean start(Minecraft minecraft, FarmConfig newConfig) {
		inputs.releaseAll();
		backgroundOperation.end();
		pitchLock.end();
		noWartFailsafe.clear();
		voidLoopRecovery.clear();
		postWarpRestartCountdown.clear();
		if (minecraft.player == null || minecraft.level == null
			|| !minecraft.player.isAlive() || minecraft.player.isRemoved()) {
			announce(minecraft, BRAND + ": enter a world with a living player before starting.");
			return false;
		}

		config = newConfig;
		config.validate();
		noWartFailsafe.startSession(config.noWartFailsafeEnabled, config.noWartTimeoutSeconds);
		wartBrokenSinceLastTick = false;
		alignStartingOrientation(minecraft.player);
		backgroundOperation.begin(config.runInBackground);
		sessionRunning = true;
		lane = 1;
		transitionTimer = 0;
		startingYaw = minecraft.player.getYRot();
		startingPitch = minecraft.player.getXRot();
		sessionLevel = minecraft.level;
		sessionPlayer = minecraft.player;
		sessionDimension = minecraft.level.dimension();
		voidLoopRecovery.startSession(
			config.voidLoopEnabled,
			sessionPlayer.getX(),
			sessionPlayer.getY(),
			sessionPlayer.getZ(),
			config.voidFallTriggerDistance,
			config.respawnStartTolerance,
			config.respawnRestartDelayTicks
		);
		pitchLock.begin(config.lockPitchWhileRunning, sessionPlayer, startingPitch);
		direction = config.startingDirection;
		enterFarmingState(minecraft.player, direction);
		inputs.applyFarming(direction, config.holdAttack);
		LOGGER.info("Started in {} at yaw {} and pitch {}", direction, startingYaw, startingPitch);
		announce(
			minecraft,
			BRAND + ": started (yaw " + startingYaw + "°, pitch " + startingPitch
				+ "°, " + direction + ", lane 1)"
		);
		return true;
	}

	public void beforeClientTick(Minecraft minecraft) {
		if (!sessionRunning || state == FarmingState.PAUSED) {
			return;
		}

		try {
			if (!state.isRecovering() && (
				minecraft.player == null
					|| minecraft.level == null
					|| minecraft.player != sessionPlayer
					|| minecraft.level != sessionLevel
					|| !minecraft.player.isAlive()
					|| minecraft.player.isRemoved()
			)) {
				inputs.releaseAll();
				pitchLock.end();
				return;
			}
			if (!state.isRecovering() && state != FarmingState.PEST_CLEANUP) {
				enforceStartingPitch(minecraft.player);
			}
			if (!backgroundOperation.isActive()) {
				return;
			}
			backgroundOperation.maintain();
			if (!minecraft.isWindowActive()) {
				inputs.reapplyDesired();
			}
		} catch (RuntimeException exception) {
			inputs.releaseAll();
			failSafeStop(minecraft, "unexpected background-input error");
			LOGGER.error("Background input maintenance failed; all controlled inputs were released", exception);
		}
	}

	public void stop(Minecraft minecraft) {
		inputs.releaseAll();
		closePendingWarpChat(minecraft);
		sessionRunning = false;
		state = FarmingState.IDLE;
		pausedState = FarmingState.IDLE;
		resetTemporaryState();
		LOGGER.info("Stopped");
		announce(minecraft, BRAND + ": stopped");
	}

	public void emergencyStop(Minecraft minecraft) {
		inputs.releaseAll();
		closePendingWarpChat(minecraft);
		sessionRunning = false;
		state = FarmingState.STOPPED;
		pausedState = FarmingState.IDLE;
		resetTemporaryState();
		LOGGER.warn("Emergency stop");
		announce(minecraft, BRAND + ": EMERGENCY STOP");
	}

	public void failSafeStop(Minecraft minecraft, String reason) {
		failSafeStop(minecraft, reason, true);
	}

	private void failSafeStop(Minecraft minecraft, String reason, boolean notify) {
		boolean wasRunning = sessionRunning;
		inputs.releaseAll();
		closePendingWarpChat(minecraft);
		sessionRunning = false;
		state = FarmingState.STOPPED;
		pausedState = FarmingState.IDLE;
		resetTemporaryState();
		LOGGER.warn("Fail-safe stop: {}", reason);
		announce(minecraft, BRAND + ": stopped safely: " + reason);
		if (wasRunning && notify) {
			notifySessionStateChange(minecraft, "Stopped safely: " + reason);
		}
	}

	public void shutdown(Minecraft minecraft) {
		inputs.releaseAll();
		closePendingWarpChat(minecraft);
		sessionRunning = false;
		state = FarmingState.STOPPED;
		pausedState = FarmingState.IDLE;
		resetTemporaryState();
		desktopNotifier.shutdown();
	}

	public void clearInactiveState() {
		inputs.releaseAll();
		backgroundOperation.end();
		pitchLock.end();
		voidLoopRecovery.clear();
		postWarpRestartCountdown.clear();
	}

	public void togglePause(Minecraft minecraft) {
		if (!sessionRunning) {
			return;
		}
		if (state.isRecovering()) {
			failSafeStop(minecraft, "void-loop recovery cancelled with F7", false);
			return;
		}
		if (state == FarmingState.PAUSED) {
			resume(minecraft);
		} else {
			pause(minecraft, "Paused with F7", false);
		}
	}

	public void tick(Minecraft minecraft) {
		if (!sessionRunning) {
			return;
		}

		try {
			tickSafely(minecraft);
		} catch (RuntimeException exception) {
			LOGGER.error("Unexpected controller failure; all controlled inputs were released", exception);
			failSafeStop(minecraft, "unexpected controller error; check latest.log");
		}
	}

	public boolean queueWarpCommandTest(Minecraft minecraft, boolean autoSubmit) {
		if (sessionRunning) {
			announce(minecraft, BRAND + ": stop the farming session with F6 before testing /warp garden.");
			return false;
		}
		if (minecraft.player == null
			|| minecraft.getConnection() == null
			|| minecraft.player.connection != minecraft.getConnection()) {
			announce(minecraft, BRAND + ": join a server before testing /warp garden.");
			return false;
		}
		if (diagnosticWarpRequested || diagnosticWarpChatScreen != null) {
			announce(minecraft, BRAND + ": a /warp garden test is already pending.");
			return false;
		}

		diagnosticWarpRequested = true;
		diagnosticWarpAutoSubmit = autoSubmit;
		diagnosticWarpChatTicks = 0;
		return true;
	}

	public void tickWarpCommandTest(Minecraft minecraft) {
		if (diagnosticWarpRequested) {
			if (minecraft.gui.screen() != null) {
				return;
			}
			if (minecraft.player == null
				|| minecraft.getConnection() == null
				|| minecraft.player.connection != minecraft.getConnection()) {
				diagnosticWarpRequested = false;
				announce(minecraft, BRAND + ": /warp garden test canceled because the connection was unavailable.");
				return;
			}

			ChatScreen chatScreen = new ChatScreen("/" + VOID_RETURN_COMMAND, false);
			diagnosticWarpRequested = false;
			minecraft.gui.setScreen(chatScreen);
			if (diagnosticWarpAutoSubmit) {
				diagnosticWarpChatScreen = chatScreen;
				diagnosticWarpChatTicks = 0;
				LOGGER.info("Opened /{} for the automatic command diagnostic", VOID_RETURN_COMMAND);
			} else {
				diagnosticWarpAutoSubmit = false;
				LOGGER.info("Opened /{} for the manual Enter diagnostic", VOID_RETURN_COMMAND);
				announce(minecraft, BRAND + ": press Enter to submit the prefilled /" + VOID_RETURN_COMMAND + ".");
				return;
			}
		}

		ChatScreen chatScreen = diagnosticWarpChatScreen;
		if (chatScreen == null) {
			return;
		}
		if (minecraft.gui.screen() != chatScreen
			|| minecraft.player == null
			|| minecraft.getConnection() == null
			|| minecraft.player.connection != minecraft.getConnection()) {
			diagnosticWarpChatScreen = null;
			diagnosticWarpChatTicks = 0;
			diagnosticWarpAutoSubmit = false;
			announce(minecraft, BRAND + ": automatic /warp garden test was canceled before submission.");
			return;
		}

		diagnosticWarpChatTicks++;
		if (diagnosticWarpChatTicks < WARP_CHAT_VISIBLE_TICKS) {
			return;
		}
		chatScreen.handleChatInput("/" + VOID_RETURN_COMMAND, true);
		voidLoopRecovery.confirmWarpCommandSubmitted();
		syncVoidRecoveryState();
		minecraft.gui.setScreen(null);
		diagnosticWarpChatScreen = null;
		diagnosticWarpChatTicks = 0;
		diagnosticWarpAutoSubmit = false;
		LOGGER.info("Submitted /{} through the automatic command diagnostic", VOID_RETURN_COMMAND);
		announce(minecraft, BRAND + ": /" + VOID_RETURN_COMMAND + " automatic test submitted.");
	}

	private void tickSafely(Minecraft minecraft) {
		if (state.isRecovering()) {
			tickVoidLoopRecovery(minecraft);
			return;
		}

		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			failSafeStop(minecraft, "world or player became unavailable");
			return;
		}
		if (player != sessionPlayer || minecraft.level != sessionLevel) {
			if (state == FarmingState.PEST_CLEANUP) {
				failSafeStop(minecraft, "world or player changed during pest cleanup");
				return;
			}
			if (beginRecoveryFromReplacedPlayer()) {
				beginVoidLoopRecovery(minecraft);
				tickVoidLoopRecovery(minecraft);
				return;
			}
			failSafeStop(minecraft, "world or player instance changed");
			return;
		}

		boolean deadOrRemoved = !player.isAlive() || player.isRemoved();
		boolean voidDamage = hasVoidDamage(player);
		if (state == FarmingState.PEST_CLEANUP) {
			if (deadOrRemoved) {
				failSafeStop(minecraft, "player died during pest cleanup");
				return;
			}
			debugX = player.getX();
			debugZ = player.getZ();
			debugHorizontalCollision = player.horizontalCollision;
			return;
		}
		if (voidLoopRecovery.observeActivePlayer(
			player.getY(),
			player.getDeltaMovement().y(),
			deadOrRemoved,
			voidDamage,
			player.getY() < minecraft.level.getMinY()
		)) {
			beginVoidLoopRecovery(minecraft);
			tickVoidLoopRecovery(minecraft);
			return;
		}
		if (deadOrRemoved) {
			failSafeStop(minecraft, "player died from a non-void cause");
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
			pause(minecraft, "A screen was opened", true);
			return;
		}

		if (config.orientationGuardEnabled && orientationDifference(player) > config.orientationToleranceDegrees) {
			pause(minecraft, "Camera yaw moved outside the configured tolerance", true);
			return;
		}

		tickNoWartFailsafe(minecraft);

		switch (state) {
			case FARM_LEFT -> handleFarming(player, FarmingDirection.LEFT);
			case FARM_RIGHT -> handleFarming(player, FarmingDirection.RIGHT);
			case END_LEFT_DETECTED -> beginForwardShift(player, true);
			case END_RIGHT_DETECTED -> beginForwardShift(player, false);
			case SHIFT_FORWARD_AFTER_LEFT -> handleForwardShift(player, true);
			case SHIFT_FORWARD_AFTER_RIGHT -> handleForwardShift(player, false);
			case FORWARD_END_AFTER_LEFT_DETECTED -> handleForwardSettle(player, true);
			case FORWARD_END_AFTER_RIGHT_DETECTED -> handleForwardSettle(player, false);
			case IDLE, VOID_FALLING, WAITING_FOR_RESPAWN, WAITING_TO_WARP,
				WAITING_TO_RESTART, PAUSED, STOPPED ->
				inputs.releaseAll();
			case PEST_CLEANUP -> {
				// The pest controller owns inputs during this dedicated state.
			}
		}
	}

	public boolean beginPestCleanup(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (!sessionRunning || !state.isFarming() || player == null || minecraft.level == null
			|| player != sessionPlayer || minecraft.level != sessionLevel
			|| !player.isAlive() || player.isRemoved()) {
			return false;
		}

		pausedState = state;
		pauseX = player.getX();
		pauseY = player.getY();
		pauseZ = player.getZ();
		inputs.releaseAll();
		pitchLock.end();
		movementMonitor.clear();
		noWartFailsafe.clear();
		wartBrokenSinceLastTick = false;
		state = FarmingState.PEST_CLEANUP;
		LOGGER.info("Pest cleanup started from {} on lane {} at {}, {}, {}", pausedState, lane, pauseX, pauseY, pauseZ);
		announce(minecraft, BRAND + ": pest cleanup started; farming position saved.");
		return true;
	}

	public boolean completePestCleanup(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (!sessionRunning || state != FarmingState.PEST_CLEANUP || player == null || minecraft.level == null
			|| player != sessionPlayer || minecraft.level != sessionLevel
			|| !player.isAlive() || player.isRemoved()) {
			return false;
		}

		double horizontalDistance = Math.hypot(player.getX() - pauseX, player.getZ() - pauseZ);
		double verticalDistance = Math.abs(player.getY() - pauseY);
		if (horizontalDistance > config.pausePositionTolerance
			|| !PestNavigationMath.isWithinFinalReturnHeight(player.onGround(), verticalDistance)) {
			failSafeStop(minecraft, "pest cleanup could not return to the saved farming position");
			return false;
		}

		state = pausedState == FarmingState.FARM_RIGHT ? FarmingState.FARM_RIGHT : FarmingState.FARM_LEFT;
		direction = state == FarmingState.FARM_RIGHT ? FarmingDirection.RIGHT : FarmingDirection.LEFT;
		movementMonitor.reset(player.getX(), player.getZ());
		noWartFailsafe.startSession(config.noWartFailsafeEnabled, config.noWartTimeoutSeconds);
		wartBrokenSinceLastTick = false;
		pitchLock.begin(config.lockPitchWhileRunning, player, startingPitch);
		player.absSnapRotationTo(startingYaw, startingPitch);
		inputs.applyFarming(direction, config.holdAttack);
		LOGGER.info("Pest cleanup completed; resumed {} on lane {}", state, lane);
		announce(minecraft, BRAND + ": pests cleared; resumed lane " + lane + ".");
		return true;
	}

	private boolean beginRecoveryFromReplacedPlayer() {
		if (sessionPlayer == null) {
			return false;
		}
		return voidLoopRecovery.observeActivePlayer(
			sessionPlayer.getY(),
			sessionPlayer.getDeltaMovement().y(),
			!sessionPlayer.isAlive() || sessionPlayer.isRemoved(),
			hasVoidDamage(sessionPlayer),
			sessionLevel != null && sessionPlayer.getY() < sessionLevel.getMinY()
		);
	}

	private void beginVoidLoopRecovery(Minecraft minecraft) {
		inputs.releaseAll();
		pitchLock.end();
		movementMonitor.clear();
		noWartFailsafe.clear();
		wartBrokenSinceLastTick = false;
		syncVoidRecoveryState();
		LOGGER.info("Void-loop recovery started in {}", voidLoopRecovery.getPhase());
		announce(minecraft, BRAND + ": void loop detected; inputs released while respawning.");
	}

	private void tickVoidLoopRecovery(Minecraft minecraft) {
		inputs.releaseAll();
		if (submitPendingWarpChat(minecraft)) {
			return;
		}
		if (postWarpRestartCountdown.isActive()) {
			tickPostWarpRestartCountdown(minecraft);
			return;
		}
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		boolean available = player != null && level != null;
		if (available && player.isAlive() && !player.isRemoved() && player.isSpectator()) {
			failSafeStop(minecraft, "respawn returned in spectator mode");
			return;
		}

		boolean clientReady = available
			&& minecraft.getConnection() != null
			&& minecraft.getConnection().hasClientLoaded()
			&& player.level() == level;
		boolean sameDimension = available
			&& sessionDimension != null
			&& sessionDimension.equals(level.dimension());
		Screen currentScreen = minecraft.gui.screen();
		if (config.pauseWhenScreenOpen
			&& currentScreen != null
			&& !(currentScreen instanceof DeathScreen)
			&& !(currentScreen instanceof LevelLoadingScreen)) {
			failSafeStop(minecraft, "an unexpected screen opened during void-loop recovery");
			return;
		}
		boolean screenOpen = currentScreen != null;
		boolean originalDeathObserved = sessionPlayer != null && !sessionPlayer.isAlive();
		boolean originalVoidDeath = originalDeathObserved && hasVoidDamage(sessionPlayer);
		PlayerSnapshot snapshot = new PlayerSnapshot(
			available,
			available && player.isAlive(),
			available && player.isRemoved(),
			available && player.onGround(),
			screenOpen,
			currentScreen instanceof DeathScreen,
			clientReady,
			sameDimension,
			available && player == sessionPlayer,
			originalDeathObserved,
			originalVoidDeath,
			available && player.getY() < level.getMinY(),
			available && hasVoidDamage(player),
			available ? player.getX() : 0.0,
			available ? player.getY() : 0.0,
			available ? player.getZ() : 0.0,
			available ? player.getDeltaMovement().y() : 0.0
		);
		Action action = voidLoopRecovery.tickRecovery(snapshot);
		syncVoidRecoveryState();

		switch (action) {
			case NONE -> {
			}
			case REQUEST_RESPAWN -> {
				if (player == null) {
					failSafeStop(minecraft, "player vanished before the respawn request");
					return;
				}
				inputs.releaseAll();
				player.respawn();
				LOGGER.info("Requested the vanilla respawn after a confirmed void death");
			}
			case SEND_WARP_COMMAND -> openVoidReturnChat(minecraft, player);
			case RESTART -> restartAfterVoidLoop(minecraft, player);
			case ABORT_NON_VOID_DEATH -> failSafeStop(minecraft, "void fall ended in a non-void death");
			case ABORT_TIMEOUT -> failSafeStop(minecraft, "void-loop recovery timed out after 30 seconds");
		}
	}

	private void openVoidReturnChat(Minecraft minecraft, LocalPlayer player) {
		if (player == null
			|| minecraft.getConnection() == null
			|| player.connection != minecraft.getConnection()
			|| minecraft.gui.screen() != null) {
			failSafeStop(minecraft, "client connection was not ready for /warp garden");
			return;
		}

		inputs.releaseAll();
		pendingWarpChatScreen = new ChatScreen("/" + VOID_RETURN_COMMAND, false);
		pendingWarpChatTicks = 0;
		minecraft.gui.setScreen(pendingWarpChatScreen);
		LOGGER.info("Opened Minecraft chat with /{} after the void return", VOID_RETURN_COMMAND);
	}

	private boolean submitPendingWarpChat(Minecraft minecraft) {
		ChatScreen chatScreen = pendingWarpChatScreen;
		if (chatScreen == null) {
			return false;
		}
		if (minecraft.gui.screen() != chatScreen
			|| minecraft.player == null
			|| minecraft.getConnection() == null
			|| minecraft.player.connection != minecraft.getConnection()) {
			pendingWarpChatScreen = null;
			failSafeStop(minecraft, "automatic /warp garden chat was interrupted before submission");
			return true;
		}

		inputs.releaseAll();
		pendingWarpChatTicks++;
		if (pendingWarpChatTicks < WARP_CHAT_VISIBLE_TICKS) {
			return true;
		}
		postWarpRestartCountdown.arm(config.respawnRestartDelayTicks);
		voidLoopRecovery.confirmWarpCommandSubmitted();
		syncVoidRecoveryState();
		LOGGER.info("Armed the post-warp farming restart for {} ticks", config.respawnRestartDelayTicks);
		chatScreen.handleChatInput("/" + VOID_RETURN_COMMAND, true);
		minecraft.gui.setScreen(null);
		pendingWarpChatScreen = null;
		pendingWarpChatTicks = 0;
		LOGGER.info("Submitted /{} through Minecraft's chat screen", VOID_RETURN_COMMAND);
		announce(minecraft, BRAND + ": submitted /" + VOID_RETURN_COMMAND + "; farming restarts in four seconds.");
		return true;
	}

	private void tickPostWarpRestartCountdown(Minecraft minecraft) {
		inputs.releaseAll();
		LocalPlayer player = minecraft.player;
		if (player != null && player.isAlive() && !player.isRemoved() && player.isSpectator()) {
			failSafeStop(minecraft, "warp returned in spectator mode");
			return;
		}

		boolean readyToRestart = player != null
			&& minecraft.level != null
			&& player.isAlive()
			&& !player.isRemoved()
			&& minecraft.gui.screen() == null;
		if (postWarpRestartCountdown.tick(readyToRestart)) {
			restartAfterVoidLoop(minecraft, player);
			return;
		}
		if (postWarpRestartCountdown.getElapsedTicks() >= VoidLoopRecovery.RECOVERY_TIMEOUT_TICKS) {
			failSafeStop(minecraft, "post-warp restart timed out after 30 seconds");
		}
	}

	private void closePendingWarpChat(Minecraft minecraft) {
		if (pendingWarpChatScreen != null && minecraft.gui.screen() == pendingWarpChatScreen) {
			minecraft.gui.setScreen(null);
		}
		pendingWarpChatScreen = null;
		pendingWarpChatTicks = 0;
	}

	private void restartAfterVoidLoop(Minecraft minecraft, LocalPlayer player) {
		if (player == null || minecraft.level == null || !player.isAlive() || player.isRemoved()) {
			failSafeStop(minecraft, "respawned player was not ready for restart");
			return;
		}

		inputs.releaseAll();
		postWarpRestartCountdown.clear();
		alignStartingOrientation(player);
		lane = 1;
		transitionTimer = 0;
		startingYaw = player.getYRot();
		startingPitch = player.getXRot();
		sessionLevel = minecraft.level;
		sessionPlayer = player;
		sessionDimension = minecraft.level.dimension();
		direction = config.startingDirection;
		noWartFailsafe.startSession(config.noWartFailsafeEnabled, config.noWartTimeoutSeconds);
		wartBrokenSinceLastTick = false;
		voidLoopRecovery.completeRestart(player.getX(), player.getY(), player.getZ());
		pitchLock.begin(config.lockPitchWhileRunning, player, startingPitch);
		backgroundOperation.begin(config.runInBackground);
		enterFarmingState(player, direction);
		inputs.applyFarming(direction, config.holdAttack);
		LOGGER.info("Void loop completed; restarted in {} on lane 1", direction);
		announce(minecraft, BRAND + ": four-second return complete; restarted on lane 1.");
	}

	private void syncVoidRecoveryState() {
		state = switch (voidLoopRecovery.getPhase()) {
			case FALLING -> FarmingState.VOID_FALLING;
			case WAITING_FOR_RESPAWN -> FarmingState.WAITING_FOR_RESPAWN;
			case WAITING_TO_WARP -> FarmingState.WAITING_TO_WARP;
			case WAITING_FOR_START -> FarmingState.WAITING_TO_RESTART;
			case INACTIVE, MONITORING -> state;
		};
	}

	private static boolean hasVoidDamage(LocalPlayer player) {
		DamageSource lastDamage = player.getLastDamageSource();
		return lastDamage != null && lastDamage.is(DamageTypes.FELL_OUT_OF_WORLD);
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
			config.laneStartGraceTicks,
			hasWallInDirection(player, expectedVector)
		);

		if (laneEnd) {
			LOGGER.info(
				"Lane {} end detected while farming {} (progress={}, stuckTicks={})",
				lane,
				expectedDirection,
				movementMonitor.getLastExpectedProgress(),
				movementMonitor.getStuckCounter()
			);
			// Change the owned input in this tick, without an idle transition tick.
			beginForwardShift(player, expectedDirection == FarmingDirection.LEFT);
		}
	}

	private void beginForwardShift(LocalPlayer player, boolean afterLeft) {
		state = afterLeft
			? FarmingState.SHIFT_FORWARD_AFTER_LEFT
			: FarmingState.SHIFT_FORWARD_AFTER_RIGHT;
		inputs.applyForwardShift();
		transitionTimer = 0;
		movementMonitor.reset(player.getX(), player.getZ());
		LOGGER.info("Shifting forward after lane {}", lane);
	}

	private void handleForwardShift(LocalPlayer player, boolean afterLeft) {
		inputs.applyForwardShift();
		transitionTimer++;
		DirectionMath.HorizontalVector forwardVector = DirectionMath.forwardUnit(startingYaw);
		boolean forwardEnd = movementMonitor.update(
			player.getX(),
			player.getZ(),
			forwardVector,
			config.minimumMovementDelta,
			config.forwardStuckDetectionTicks,
			config.forwardShiftTicks,
			hasWallInDirection(player, forwardVector)
		);
		if (!forwardEnd) {
			return;
		}

		LOGGER.info(
			"Forward end detected after lane {} (progress={}, stuckTicks={})",
			lane,
			movementMonitor.getLastExpectedProgress(),
			movementMonitor.getStuckCounter()
		);
		transitionTimer = 0;
		if (config.transitionSettleTicks == 0) {
			finishForwardShift(player, afterLeft);
		} else {
			inputs.releaseAll();
			state = afterLeft
				? FarmingState.FORWARD_END_AFTER_LEFT_DETECTED
				: FarmingState.FORWARD_END_AFTER_RIGHT_DETECTED;
		}
	}

	private void handleForwardSettle(LocalPlayer player, boolean afterLeft) {
		inputs.releaseAll();
		transitionTimer++;
		if (transitionTimer < config.transitionSettleTicks) {
			return;
		}
		finishForwardShift(player, afterLeft);
	}

	private void finishForwardShift(LocalPlayer player, boolean afterLeft) {
		lane++;
		FarmingDirection nextDirection = afterLeft ? FarmingDirection.RIGHT : FarmingDirection.LEFT;
		enterFarmingState(player, nextDirection);
		inputs.applyFarming(nextDirection, config.holdAttack);
		LOGGER.info("Entering {} on lane {}", state, lane);
	}

	private static boolean hasWallInDirection(
		LocalPlayer player,
		DirectionMath.HorizontalVector direction
	) {
		// Shrinking the probe avoids counting mere contact with the wall beside W.
		// Only solid blocks ahead of the commanded axis confirm a lane end; a
		// generic horizontalCollision flag could still refer to the previous axis.
		return !player.level().noBlockCollision(
			player,
			player.getBoundingBox().deflate(WALL_PROBE_EDGE_EPSILON).move(
				direction.x() * DIRECTIONAL_WALL_PROBE_DISTANCE,
				0.0,
				direction.z() * DIRECTIONAL_WALL_PROBE_DISTANCE
			)
		);
	}

	private void enterFarmingState(LocalPlayer player, FarmingDirection newDirection) {
		direction = newDirection;
		state = newDirection == FarmingDirection.LEFT ? FarmingState.FARM_LEFT : FarmingState.FARM_RIGHT;
		transitionTimer = 0;
		movementMonitor.reset(player.getX(), player.getZ());
	}

	private void pause(Minecraft minecraft, String reason, boolean notify) {
		if (!sessionRunning || state == FarmingState.PAUSED || minecraft.player == null) {
			return;
		}
		pausedState = state;
		pauseX = minecraft.player.getX();
		pauseZ = minecraft.player.getZ();
		inputs.releaseAll();
		backgroundOperation.end();
		pitchLock.end();
		state = FarmingState.PAUSED;
		LOGGER.info("Paused from {}: {}", pausedState, reason);
		announce(minecraft, BRAND + ": paused: " + reason);
		if (notify) {
			notifySessionStateChange(minecraft, "Paused: " + reason);
		}
	}

	private void resume(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !player.isAlive() || player.isRemoved()) {
			failSafeStop(minecraft, "cannot resume without a living player in a world");
			return;
		}
		if (config.pauseWhenScreenOpen && minecraft.gui.screen() != null) {
			announce(minecraft, "Close the current screen before resuming " + BRAND + ".");
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
		pitchLock.begin(config.lockPitchWhileRunning, player, startingPitch);
		enforceStartingPitch(player);
		backgroundOperation.begin(config.runInBackground);
		LOGGER.info("Resumed into {}", state);
		announce(minecraft, BRAND + ": resumed");
	}

	public void recordNetherWartBroken(Minecraft minecraft, ClientLevel level, LocalPlayer player) {
		if (!sessionRunning || level != sessionLevel || player != sessionPlayer) {
			return;
		}

		wartBrokenSinceLastTick = true;
		if (noWartFailsafe.recordWartBreak()) {
			LOGGER.info("Nether Wart activity resumed; no-wart alert cleared and rearmed");
		}
	}

	private void tickNoWartFailsafe(Minecraft minecraft) {
		if (wartBrokenSinceLastTick) {
			wartBrokenSinceLastTick = false;
			return;
		}
		if (!noWartFailsafe.tick(true)) {
			return;
		}

		LOGGER.warn("No monitored crop activity was detected for {} seconds", config.noWartTimeoutSeconds);
		if (config.noWartDesktopNotification) {
			desktopNotifier.showCropInactivityAlert(minecraft, config.noWartTimeoutSeconds);
		}
	}

	private void notifySessionStateChange(Minecraft minecraft, String reason) {
		if (config.sessionStateDesktopNotification) {
			desktopNotifier.showSessionStateAlert(minecraft, reason);
		}
	}

	private void alignStartingOrientation(LocalPlayer player) {
		if (!config.alignYawOnStart && !config.lockPitchWhileRunning) {
			return;
		}

		float yaw = config.alignYawOnStart ? (float) config.startYawDegrees : player.getYRot();
		float pitch = config.lockPitchWhileRunning ? (float) config.fixedPitchDegrees : player.getXRot();
		player.absSnapRotationTo(yaw, pitch);
		if (config.alignYawOnStart) {
			player.setYHeadRot(yaw);
			player.setYBodyRot(yaw);
			player.yHeadRotO = yaw;
			player.yBodyRotO = yaw;
		}
	}

	private void enforceStartingPitch(LocalPlayer player) {
		pitchLock.apply(player);
	}

	private double orientationDifference(LocalPlayer player) {
		return Mth.degreesDifferenceAbs(startingYaw, player.getYRot());
	}

	private void resetTemporaryState() {
		lane = 1;
		transitionTimer = 0;
		sessionLevel = null;
		sessionPlayer = null;
		sessionDimension = null;
		movementMonitor.clear();
		noWartFailsafe.clear();
		voidLoopRecovery.clear();
		postWarpRestartCountdown.clear();
		pendingWarpChatScreen = null;
		pendingWarpChatTicks = 0;
		wartBrokenSinceLastTick = false;
		backgroundOperation.end();
		pitchLock.end();
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

	public boolean isPestCleanup() {
		return state == FarmingState.PEST_CLEANUP;
	}

	public boolean canStartPestCleanup() {
		return sessionRunning && state.isFarming();
	}

	public boolean isRecovering() {
		return state.isRecovering();
	}

	public Phase getVoidRecoveryPhase() {
		return voidLoopRecovery.getPhase();
	}

	public int getVoidRecoveryElapsedSeconds() {
		if (postWarpRestartCountdown.isActive()) {
			return postWarpRestartCountdown.getElapsedTicks() / VoidLoopRecovery.TICKS_PER_SECOND;
		}
		return voidLoopRecovery.getRecoveryTicks() / VoidLoopRecovery.TICKS_PER_SECOND;
	}

	public boolean isPostWarpRestartCountdownActive() {
		return postWarpRestartCountdown.isActive();
	}

	public int getPostWarpRestartSecondsRemaining() {
		return postWarpRestartCountdown.getRemainingSeconds(VoidLoopRecovery.TICKS_PER_SECOND);
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

	public boolean isNoWartAlertActive() {
		return noWartFailsafe.isAlertActive();
	}

	public int getNoWartElapsedSeconds() {
		return noWartFailsafe.getTicksSinceLastWartBreak() / NoWartFailsafeMonitor.TICKS_PER_SECOND;
	}
}
