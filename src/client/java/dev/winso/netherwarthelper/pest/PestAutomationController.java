package dev.winso.netherwarthelper.pest;

import dev.winso.netherwarthelper.config.FarmConfig;
import dev.winso.netherwarthelper.controller.FarmingController;
import dev.winso.netherwarthelper.input.InputController;
import dev.winso.netherwarthelper.mixin.MinecraftAttackInvoker;
import dev.winso.netherwarthelper.notification.DesktopNotifier;
import dev.winso.netherwarthelper.pest.GardenPlotGeometry.PlotCenter;
import dev.winso.netherwarthelper.pest.PestEntityDetector.PestTarget;
import dev.winso.netherwarthelper.pest.PestGardenReader.Snapshot;
import dev.winso.netherwarthelper.pest.PestNavigationMath.AimAngles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates Garden pest interruption, vacuum use, coarse flight, and exact return. */
public final class PestAutomationController {
	private static final Logger LOGGER = LoggerFactory.getLogger("AquaVisionOP/Pests");
	private static final int TICKS_PER_SECOND = 20;
	private static final int STATUS_POLL_TICKS = TICKS_PER_SECOND;
	private static final int PREPARE_TICKS = 10;
	private static final int MAXIMUM_CLEANUP_TICKS = 15 * 60 * TICKS_PER_SECOND;
	private static final double BREADCRUMB_SPACING = 8.0;
	private static final double RETURN_TEST_BREADCRUMB_SPACING = 0.75;
	private static final double EDGE_CLEARANCE_EXTENSION = 0.20;
	private static final int MAXIMUM_RETURN_TEST_BREADCRUMBS = 8192;

	private final InputController inputs;
	private final FarmingController farmingController;
	private final DesktopNotifier desktopNotifier;
	private final Supplier<FarmConfig> configSupplier;
	private final PestGardenReader gardenReader = new PestGardenReader();
	private final PestStartGate startGate = new PestStartGate();
	private final PestThresholdAlertGate thresholdAlertGate = new PestThresholdAlertGate();
	private final PestCompletionGate completionGate = new PestCompletionGate();
	private final PestCleanupProgress cleanupProgress = new PestCleanupProgress();
	private final PestFlightMonitor flightMonitor = new PestFlightMonitor();
	private final FlightJumpGuard flightJumpGuard = new FlightJumpGuard();
	private final PestLineOfSightGate pestLineOfSightGate = new PestLineOfSightGate();
	private final PestEntityDetector entityDetector = new PestEntityDetector();
	private final PestParticleTracker particleTracker = new PestParticleTracker();
	private final PestLocatorCycle locator = new PestLocatorCycle();
	private final PestCameraSteering camera = new PestCameraSteering();
	private final List<Vec3> outboundBreadcrumbs = new ArrayList<>();
	private final Set<Integer> visitedPlots = new HashSet<>();

	private PestPhase phase = PestPhase.IDLE;
	private Snapshot gardenSnapshot = Snapshot.unknown();
	private ClientLevel observedLevel;
	private LocalPlayer observedPlayer;
	private boolean observedSessionRunning;
	private FarmConfig activeConfig;
	private LocalPlayer cleanupPlayer;
	private PestTarget target;
	private int targetMissingTicks;
	private boolean targetFresh;
	private boolean pestForwardLatched;
	private Entity lastVacuumTarget;
	private Vec3 lastVacuumPosition;
	private int lastVacuumUseTick = -100;
	private Vec3 savedPosition;
	private Vec3 landingPosition;
	private float savedYaw;
	private float savedPitch;
	private Vec3 travelTarget;
	private int selectedPlot = -1;
	private int originalSelectedSlot = -1;
	private int vacuumHotbarSlot = -1;
	private int swappedInventorySlot = -1;
	private int swappedMenuSlot = -1;
	private double vacuumRange = VacuumRangeResolver.GENERIC_VACUUM_RANGE;
	private double cruiseY;
	private boolean inventorySwapped;
	private boolean originalFlying;
	private boolean changedFlying;
	private ItemFingerprint vacuumFingerprint;
	private ItemFingerprint displacedHotbarFingerprint;
	private int statusPollTicks;
	private boolean counterFreeGardenHud;
	private int cleanupTicks;
	private int phaseTicks;
	private int searchStartTick;
	private int missingTrails;
	private int returnIndex;
	private int returnStartTick = -1;
	private List<PestReturnRoute.Point> returnRoute = List.of();
	private int returnBudgetTicks = PestRunDeadline.RETURN_TIMEOUT_TICKS;
	private int returnRouteCheckTick = -100;
	private int returnHeadingIndex = -1;
	private double returnHeading;
	private boolean returnLegHeadingAcquired;
	private boolean returnFacingSavedHeading;
	private boolean finalDescentCommitted;
	private boolean returnTestRunning;
	private ClientLevel returnTestMarkedLevel;
	private LocalPlayer returnTestMarkedPlayer;
	private Vec3 returnTestMarkedPosition;
	private Vec3 returnTestMarkedLandingPosition;
	private float returnTestMarkedYaw;
	private float returnTestMarkedPitch;
	private final List<Vec3> returnTestBreadcrumbs = new ArrayList<>();
	private int groundedReturnTicks;
	private int terminalTicks;
	private String status = "Waiting";
	private String clearanceStatus = "Inactive";

	public PestAutomationController(
		InputController inputs,
		FarmingController farmingController,
		DesktopNotifier desktopNotifier,
		Supplier<FarmConfig> configSupplier
	) {
		this.inputs = inputs;
		this.farmingController = farmingController;
		this.desktopNotifier = desktopNotifier;
		this.configSupplier = configSupplier;
	}

	public void tick(Minecraft minecraft) {
		recordReturnTestBreadcrumb(minecraft);
		try {
			tickInternal(minecraft);
		} finally {
			if (phase.isActive() && (farmingController.isPestCleanup() || returnTestRunning)
				&& minecraft.player == cleanupPlayer) {
				protectFlightClearance(minecraft.player);
				// Apply to every path, including releases for hovering, locator clicks and vacuum use.
				inputs.setJump(flightJumpGuard.tick(inputs.isJumpRequested()));
			} else {
				flightJumpGuard.reset();
			}
		}
	}

	private void tickInternal(Minecraft minecraft) {
		FarmConfig currentConfig = configSupplier.get();
		if (currentConfig == null) {
			startGate.reset();
			return;
		}
		currentConfig.validate();
		boolean freshPoll = pollGardenStatus(minecraft);
		if (thresholdAlertGate.shouldAlert(currentConfig.pestCountDesktopNotification,
			freshPoll && gardenSnapshot.inGarden(),
			gardenSnapshot.pestCount(), currentConfig.pestCountNotificationThreshold)) {
			desktopNotifier.showPestThresholdAlert(minecraft, gardenSnapshot.pestCount().orElseThrow());
			LOGGER.info("Pest threshold desktop alert sent for {} reported pests (configured threshold {})",
				gardenSnapshot.pestCount().orElseThrow(), currentConfig.pestCountNotificationThreshold);
		}

		if (phase.isTerminal()) {
			if (++terminalTicks >= TICKS_PER_SECOND) {
				phase = PestPhase.IDLE;
				status = "Waiting";
				terminalTicks = 0;
			}
			return;
		}

		if (!phase.isActive()) {
			tickTrigger(minecraft, currentConfig, freshPoll);
			return;
		}
		if (!farmingController.isPestCleanup() && !returnTestRunning) {
			cancel(minecraft);
			return;
		}

		LocalPlayer player = minecraft.player;
		if (player == null || player != cleanupPlayer || minecraft.level == null
			|| !player.isAlive() || player.isRemoved()) {
			// FarmingController owns death, disconnect, and void-loop recovery.
			cancel(minecraft);
			return;
		}
		if (activeConfig.pauseWhenScreenOpen && minecraft.gui.screen() != null) {
			fail(minecraft, "a screen opened during pest cleanup");
			return;
		}
		if (player.getY() < minecraft.level.getMinY()) {
			fail(minecraft, "player fell out of the Garden during pest cleanup");
			return;
		}
		if (phase != PestPhase.PREPARING && !returnTestRunning) {
			if (!selectedVacuumIsReady(player)) {
				fail(minecraft, "the selected vacuum moved or changed during pest cleanup");
				return;
			}
			if (player.getInventory().getSelectedSlot() != vacuumHotbarSlot) {
				player.getInventory().setSelectedSlot(vacuumHotbarSlot);
			}
		}

		cleanupTicks++;
		phaseTicks++;
		if (!returnTestRunning && cleanupProgress.observe(cleanupTicks, freshPoll, gardenSnapshot.pestCount())) {
			LOGGER.info("Pest cleanup progress: authoritative count reduced to {}; inactivity deadline restarted",
				cleanupProgress.lowestReportedCount());
		}
		recordBreadcrumb(player.position());
		List<PestTarget> targets = phase == PestPhase.RETURNING ? List.of()
			: entityDetector.findTargets(minecraft.level, player);
		if (phase != PestPhase.RETURNING) {
			// Decide completion before flight recovery and timeout handling can prevent a valid return.
			completionGate.observe(cleanupTicks, freshPoll, gardenSnapshot.pestCount(), gardenSnapshot.inGarden(),
				"conflict".equals(gardenSnapshot.countSource()), counterFreeGardenHud,
				!targets.isEmpty() || (target != null && target.isValid()));
			// Head and body removals may arrive on different ticks, so retain the actual used body independently.
			if (lastVacuumTarget != null && (!lastVacuumTarget.isAlive() || lastVacuumTarget.isRemoved())
				&& targets.isEmpty() && cleanupTicks - lastVacuumUseTick <= 10
				&& lastVacuumPosition != null && player.position().distanceTo(lastVacuumPosition) <= 3.0) {
				if (completionGate.noteLastTargetRemoved(cleanupTicks)) {
					LOGGER.info("Last locally vacuumed target removed after a total of one; checking for the hidden zero counter");
				}
				// Consume the removal once even if flight recovery delays the normal target transition.
				lastVacuumTarget = null;
				lastVacuumPosition = null;
				lastVacuumUseTick = -100;
			}
			if (freshPoll && completionGate.pendingLastRemoval()) {
				LOGGER.info("Pest clear check: {} (counter-free Garden HUD={})", completionGate.evidence(), counterFreeGardenHud);
			}
			if (completionGate.confirmed()) beginReturn();
		}
		int cleanupInactivityBudget = activeConfig.pestCleanupTimeoutSeconds * TICKS_PER_SECOND;
		int cleanupDeadline = cleanupProgress.deadlineTick(cleanupInactivityBudget,
			Math.max(cleanupInactivityBudget, MAXIMUM_CLEANUP_TICKS));
		PestRunDeadline.Failure deadline = PestRunDeadline.check(cleanupTicks,
			cleanupDeadline, returnStartTick,
			completionGate.canFinishAfterDeadline(cleanupTicks), returnBudgetTicks);
		if (deadline != PestRunDeadline.Failure.NONE) {
			fail(minecraft, deadline == PestRunDeadline.Failure.RETURN
				? "pests were cleared, but the return to the saved lane timed out"
				: "pest cleanup timed out before every pest was cleared");
			return;
		}

		if (phase == PestPhase.PREPARING) {
			inputs.releaseMovement();
			inputs.setUse(false);
			if (!selectedVacuumIsReady(player)) {
				status = "Waiting for vacuum inventory sync";
				if (phaseTicks >= PREPARE_TICKS) {
					fail(minecraft, "the configured hotbar slot did not receive the selected vacuum");
				}
				return;
			}
			if (phaseTicks < PREPARE_TICKS) {
				status = "Preparing vacuum";
				return;
			}
			phase = PestPhase.TAKING_OFF;
			phaseTicks = 0;
		}

		// Finishing the exact return is a landing, not a reason to jump away from the saved lane.
		if (phase == PestPhase.RETURNING && returnIndex <= 0
			&& PestNavigationMath.canHandleFinalReturnWithoutFlightRecovery(
				finalDescentCommitted, player.onGround(), player.getAbilities().flying,
				isAtSavedLane(player), canFinishReturnOnGround(player))) {
			tickReturn(minecraft, player);
			return;
		}
		if (!ensureFlight(minecraft, player)) return;
		if (phase == PestPhase.TAKING_OFF) {
			// Restore the working outbound ascent before hovering to read a locator trail.
			chooseNextSearchPoint(player);
		}
		if (phase == PestPhase.RETURNING) {
			tickReturn(minecraft, player);
			return;
		}

		if (completionGate.pendingLastRemoval()) {
			target = null;
			targetMissingTicks = 0;
			targetFresh = false;
			pestForwardLatched = false;
			pestLineOfSightGate.reset();
			travelTarget = null;
			selectedPlot = -1;
			particleTracker.close();
			locator.suspend();
			inputs.releaseAll();
			phase = PestPhase.CONFIRMING_CLEAR;
			status = "Confirming empty Garden: " + completionGate.confirmedPolls() + "/"
				+ PestCompletionGate.OMITTED_COUNTER_POLLS;
			return;
		}
		if (phase == PestPhase.CONFIRMING_CLEAR) beginLocatorSearch(true);

		PestTarget previousTarget = target;
		TargetChoice targetChoice = chooseTarget(targets);
		target = targetChoice.target();
		targetFresh = targetChoice.fresh();
		if (target != null) {
			if (previousTarget == null || previousTarget.entity() != target.entity()) {
				phaseTicks = 0;
				pestForwardLatched = false;
				pestLineOfSightGate.reset(player.hasLineOfSight(target.entity()));
				inputs.setUse(false);
				LOGGER.info("Tracking pest {} (entity {})", target.name(), target.entity().getId());
			}
			tickTarget(minecraft, player, target);
			return;
		}
		if (previousTarget != null) {
			// A cleared or unloaded target must not leave its vacuum timer/old trail in the next hunt.
			LOGGER.info("Pest target gone; reacquiring, Garden total {}", gardenSnapshot.pestCount().orElse(-1));
			beginLocatorSearch(true);
			status = "Finding the next pest";
			return; // Let vanilla release right-click before the next left-click request.
		}

		inputs.setAttack(false);
		inputs.setUse(false);
		if (phase == PestPhase.FOLLOWING_TRAIL && travelTarget != null) {
			boolean arrived = PestNavigationMath.isAtTrailWaypoint(
				Math.hypot(player.getX() - travelTarget.x, player.getZ() - travelTarget.z),
				Math.abs(player.getY() - travelTarget.y));
			boolean expired = locator.tick(cleanupTicks, false, 0, false)
				== PestLocatorCycle.Action.FOLLOW_TIMEOUT;
			if (arrived || expired) {
				beginLocatorSearch(false);
				status = arrived ? "Checking the next trail segment" : "Refreshing the pest trail";
			} else {
				status = "Following pest locator";
				navigateTo(player, travelTarget, false);
				return;
			}
		}
		if (phase == PestPhase.TRAVELLING_TO_PLOT && travelTarget != null) {
			if (PestNavigationMath.isWithinHorizontalDistance(
				player.getX(), player.getZ(), travelTarget.x, travelTarget.z, 8.0
			) && Math.abs(player.getY() - travelTarget.y) <= 5.0) {
				if (selectedPlot >= 0) {
					visitedPlots.add(selectedPlot);
				}
				int arrivedPlot = selectedPlot;
				beginLocatorSearch(true);
				status = "Searching plot " + arrivedPlot;
				return;
			}
			status = selectedPlot >= 0 ? "Flying to plot " + selectedPlot : "Following pest locator";
			navigateTo(player, travelTarget, false);
			return;
		}

		tickLocatorSearch(minecraft, player);
	}

	private void tickTrigger(Minecraft minecraft, FarmConfig config, boolean freshPoll) {
		boolean armed = config.pestAutomationEnabled && farmingController.isSessionRunning()
			&& !farmingController.isPaused() && !farmingController.isRecovering()
			&& minecraft.player != null && minecraft.player.isAlive() && minecraft.level != null
			&& minecraft.gui.screen() == null && gardenSnapshot.inGarden();
		boolean ready = startGate.shouldStart(armed, freshPoll, gardenSnapshot.pestCount(),
			config.pestActivationThreshold, farmingController.canStartPestCleanup());
		status = detectionStatus(minecraft, config, gardenSnapshot);
		if (ready) {
			startGate.reset();
			startCleanup(minecraft, config, gardenSnapshot.pestCount().orElseThrow());
		}
	}

	private void startCleanup(Minecraft minecraft, FarmConfig config, int reportedPests) {
		LocalPlayer player = minecraft.player;
		VacuumSelection selection = player == null ? null : findBestVacuum(
			player.getInventory(),
			config.pestMoveVacuumFromInventory
		);
		if (player == null || selection == null) {
			farmingController.failSafeStop(minecraft, "pest automation needs a vacuum in the inventory");
			phase = PestPhase.FAILED;
			status = "Vacuum missing";
			return;
		}
		if (selection.inventorySlot() >= Inventory.getSelectionSize() && !config.pestMoveVacuumFromInventory) {
			farmingController.failSafeStop(minecraft, "the vacuum is not in the hotbar and automatic inventory moving is disabled");
			phase = PestPhase.FAILED;
			status = "Vacuum not in hotbar";
			return;
		}
		if (!player.getAbilities().mayfly) {
			farmingController.failSafeStop(minecraft, "Garden flight is unavailable, so pest cleanup cannot return safely");
			phase = PestPhase.FAILED;
			status = "Flight unavailable";
			return;
		}
		if (!player.onGround()) {
			farmingController.failSafeStop(minecraft, "pest cleanup must start from a grounded farming lane");
			phase = PestPhase.FAILED;
			status = "Lane start is airborne";
			return;
		}
		Vec3 recordedPosition = player.position();
		Optional<Vec3> plannedLanding = chooseCollisionSafeLandingPosition(
			player, recordedPosition, player.getYRot(), config.pausePositionTolerance);
		if (plannedLanding.isEmpty()) {
			farmingController.failSafeStop(minecraft,
				"no collision-safe landing point was available beside the saved farming lane");
			phase = PestPhase.FAILED;
			status = "No safe lane landing";
			return;
		}
		if (!farmingController.beginPestCleanup(minecraft)) {
			return;
		}

		activeConfig = config;
		cleanupPlayer = player;
		savedPosition = recordedPosition;
		savedYaw = player.getYRot();
		savedPitch = player.getXRot();
		landingPosition = plannedLanding.orElseThrow();
		cruiseY = Math.max(config.pestCruiseHeight, savedPosition.y + 5.0);
		originalSelectedSlot = player.getInventory().getSelectedSlot();
		vacuumRange = selection.range();
		vacuumFingerprint = selection.fingerprint();
		originalFlying = player.getAbilities().flying;
		changedFlying = !originalFlying;
		flightMonitor.reset();
		flightJumpGuard.reset();
		locator.reset();
		particleTracker.close();
		camera.reset();
		outboundBreadcrumbs.clear();
		outboundBreadcrumbs.add(savedPosition);
		visitedPlots.clear();
		phase = PestPhase.PREPARING;
		phaseTicks = 0;
		cleanupTicks = 0;
		completionGate.reset();
		cleanupProgress.reset(reportedPests);
		returnStartTick = -1;
		returnRoute = List.of();
		returnBudgetTicks = PestRunDeadline.RETURN_TIMEOUT_TICKS;
		returnRouteCheckTick = -100;
		returnHeadingIndex = -1;
		returnLegHeadingAcquired = false;
		returnFacingSavedHeading = false;
		finalDescentCommitted = false;
		lastVacuumTarget = null;
		lastVacuumPosition = null;
		lastVacuumUseTick = -100;
		startGate.reset();
		selectedPlot = -1;
		travelTarget = null;
		target = null;
		targetMissingTicks = 0;
		targetFresh = false;
		pestForwardLatched = false;
		pestLineOfSightGate.reset();
		status = "Preparing for " + reportedPests + " pests";

		try {
			if (!prepareVacuum(minecraft, player, selection, config)) {
				fail(minecraft, "the vacuum could not be moved into the configured hotbar slot");
				return;
			}
		} catch (RuntimeException exception) {
			LOGGER.error("Could not acquire pest-cleanup controls", exception);
			fail(minecraft, "pest cleanup could not prepare flight or the vacuum");
			return;
		}
		LOGGER.info(
			"Pest cleanup started for {} reported pests using {} in slot {} at range {}",
			reportedPests,
			selection.name(),
			vacuumHotbarSlot + 1,
			vacuumRange
		);
	}

	private boolean ensureFlight(Minecraft minecraft, LocalPlayer player) {
		PestFlightMonitor.Phase previousPhase = flightMonitor.getPhase();
		if (previousPhase == PestFlightMonitor.Phase.FLYING
			&& (!player.getAbilities().flying || player.onGround())) {
			LOGGER.warn("Pest flight lost during {}: allowed={}, flying={}, grounded={}, Y={}, vertical speed={}, jump={}, descend={}, clearance={}, target={}, waypoint={}",
				phase, player.getAbilities().mayfly, player.getAbilities().flying, player.onGround(),
				player.getY(), player.getDeltaMovement().y, inputs.isJumpRequested(), inputs.isShiftRequested(),
				clearanceStatus, target == null ? "none" : target.position(), travelTarget);
		}
		PestFlightMonitor.Step step = flightMonitor.tick(player.getAbilities().mayfly,
			player.getAbilities().flying, player.onGround(), player.getY());
		if (step.failed()) {
			fail(minecraft, step.failure());
			return false;
		}
		if (step.ready()) {
			if (previousPhase != PestFlightMonitor.Phase.FLYING) {
				phaseTicks = 0;
				LOGGER.info("Pest flight confirmed at Y {} (recoveries {})", player.getY(), flightMonitor.getRecoveries());
			}
			return true;
		}
		pestForwardLatched = false;
		pestLineOfSightGate.reset();
		inputs.releaseMovement();
		inputs.setAttack(false);
		inputs.setUse(false);
		inputs.setJump(step.holdJump());
		status = flightMonitor.getPhase() == PestFlightMonitor.Phase.TAKING_OFF
			? "Taking off before pest navigation" : "Confirming permitted flight";
		if (step.requestFlight() && player.getAbilities().mayfly && !player.onGround()
			&& !player.getAbilities().flying) {
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			LOGGER.info("Requested airborne pest flight (attempt {}, recovery {})",
				flightMonitor.getRequests(), flightMonitor.getRecoveries());
		}
		return false;
	}

	private boolean prepareVacuum(
		Minecraft minecraft,
		LocalPlayer player,
		VacuumSelection selection,
		FarmConfig config
	) {
		Inventory inventory = player.getInventory();
		if (selection.inventorySlot() < Inventory.getSelectionSize()) {
			vacuumHotbarSlot = selection.inventorySlot();
			displacedHotbarFingerprint = null;
			inventory.setSelectedSlot(vacuumHotbarSlot);
			return true;
		}

		int hotbarSlot = config.pestVacuumHotbarSlot - 1;
		AbstractContainerMenu menu = player.inventoryMenu;
		int sourceMenuSlot = findMenuSlot(menu, inventory, selection.inventorySlot());
		if (sourceMenuSlot < 0 || minecraft.gameMode == null || player.containerMenu != menu) {
			return false;
		}
		inventorySwapped = true;
		swappedInventorySlot = selection.inventorySlot();
		swappedMenuSlot = sourceMenuSlot;
		vacuumHotbarSlot = hotbarSlot;
		displacedHotbarFingerprint = ItemFingerprint.of(inventory.getItem(hotbarSlot));
		minecraft.gameMode.handleContainerInput(
			menu.containerId,
			sourceMenuSlot,
			hotbarSlot,
			ContainerInput.SWAP,
			player
		);
		inventory.setSelectedSlot(vacuumHotbarSlot);
		return true;
	}

	private static int findMenuSlot(AbstractContainerMenu menu, Inventory inventory, int inventorySlot) {
		for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
			Slot slot = menu.slots.get(menuSlot);
			if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
				return menuSlot;
			}
		}
		return -1;
	}

	private static VacuumSelection findBestVacuum(Inventory inventory, boolean allowInventoryMove) {
		VacuumSelection best = null;
		int slotLimit = allowInventoryMove ? 36 : Inventory.getSelectionSize();
		for (int slot = 0; slot < slotLimit; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String name = stack.getHoverName().getString();
			OptionalDouble range = VacuumRangeResolver.resolveRange(name);
			if (range.isEmpty()) {
				continue;
			}
			VacuumSelection candidate = new VacuumSelection(
				slot,
				range.getAsDouble(),
				name,
				ItemFingerprint.of(stack)
			);
			if (best == null || candidate.range() > best.range()
				|| (candidate.range() == best.range()
					&& slot < Inventory.getSelectionSize() && best.inventorySlot() >= Inventory.getSelectionSize())) {
				best = candidate;
			}
		}
		return best;
	}

	private boolean selectedVacuumIsReady(LocalPlayer player) {
		return vacuumHotbarSlot >= 0
			&& vacuumHotbarSlot < Inventory.getSelectionSize()
			&& vacuumFingerprint != null
			&& vacuumFingerprint.matches(player.getInventory().getItem(vacuumHotbarSlot));
	}

	private boolean pollGardenStatus(Minecraft minecraft) {
		boolean sessionRunning = farmingController.isSessionRunning();
		if (minecraft.level != observedLevel || minecraft.player != observedPlayer
			|| sessionRunning != observedSessionRunning) {
			observedLevel = minecraft.level;
			observedPlayer = minecraft.player;
			observedSessionRunning = sessionRunning;
			gardenSnapshot = Snapshot.unknown();
			startGate.reset();
			completionGate.reset();
			counterFreeGardenHud = false;
			statusPollTicks = STATUS_POLL_TICKS - 1;
		}
		statusPollTicks++;
		if (statusPollTicks < STATUS_POLL_TICKS) {
			return false;
		}
		statusPollTicks = 0;
		Snapshot next = gardenReader.read(minecraft);
		if (!next.pestCount().equals(gardenSnapshot.pestCount())
			|| !next.countSource().equals(gardenSnapshot.countSource())) {
			LOGGER.info("Garden pest total: {} ({}), evidence: {}", next.pestCount().orElse(-1),
				next.countSource(), next.countEvidence());
		}
		gardenSnapshot = next;
		counterFreeGardenHud = next.infestedPlots().isEmpty()
			&& PestClearEvidence.hasCounterFreeGardenHud(next.scoreboardLines(), next.tabLines());
		return true;
	}

	private TargetChoice chooseTarget(List<PestTarget> targets) {
		List<Integer> freshIds = targets.stream().map(candidate -> candidate.entity().getId()).toList();
		Integer currentId = target == null || target.entity() == null ? null : target.entity().getId();
		PestTargetContinuity.Decision<Integer> decision = PestTargetContinuity.select(
			currentId, target != null && target.isValid(), targetMissingTicks, freshIds);
		targetMissingTicks = decision.missingTicks();
		if (!decision.hasTarget()) return new TargetChoice(null, false);
		if (decision.fresh()) return new TargetChoice(targets.get(decision.freshIndex()), true);
		Entity retained = target.entity();
		return new TargetChoice(new PestTarget(retained, target.name(), retained.getBoundingBox().getCenter()), false);
	}

	private void tickTarget(Minecraft minecraft, LocalPlayer player, PestTarget pest) {
		travelTarget = null;
		selectedPlot = -1;
		particleTracker.close();
		locator.suspend();
		inputs.setAttack(false);
		inputs.setBackward(false);
		inputs.setLeft(false);
		inputs.setRight(false);
		Vec3 livePosition = pest.entity().getBoundingBox().getCenter();
		double distance = player.getEyePosition().distanceTo(livePosition);
		double horizontalDistance = Math.hypot(livePosition.x - player.getX(), livePosition.z - player.getZ());
		boolean lineOfSight = player.hasLineOfSight(pest.entity());
		boolean movementLineOfSight = pestLineOfSightGate.observe(lineOfSight);
		camera.aimAt(player, livePosition, 12.0, 10.0);
		AimAngles aim = PestNavigationMath.aimAt(player.getX(), player.getEyeY(), player.getZ(),
			livePosition.x, livePosition.y, livePosition.z);
		boolean movementAligned = horizontalDistance < 0.01
			|| PestMovingTargetControl.isMovementHeadingAligned(
				player.getYRot(), aim.yawDegrees(), pestForwardLatched);
		boolean vacuumAligned = (horizontalDistance < 0.01
			|| PestNavigationMath.isHeadingAligned(player.getYRot(), aim.yawDegrees(), 12.0))
			&& Math.abs(player.getXRot() - aim.pitchDegrees()) <= 12.0;
		PestMovingTargetControl.Controls controls = PestMovingTargetControl.decide(
			distance, horizontalDistance, vacuumRange, movementLineOfSight, movementAligned, vacuumAligned,
			targetFresh, pestForwardLatched, livePosition.y + 1.0 - player.getY(), player.horizontalCollision);
		if (pestForwardLatched != controls.forward()) {
			LOGGER.info("Pest approach forward {} at horizontal distance {} (line of sight={}, aligned={})",
				controls.forward() ? "started" : "stopped", horizontalDistance, lineOfSight, movementAligned);
		}
		pestForwardLatched = controls.forward();
		inputs.setForward(controls.forward());
		inputs.setJump(controls.jump());
		inputs.setShift(controls.descend());
		inputs.setUse(controls.use());
		if (controls.use()) {
			lastVacuumTarget = pest.entity();
			lastVacuumPosition = player.position();
			lastVacuumUseTick = cleanupTicks;
		}
		phase = controls.use() ? PestPhase.VACUUMING : PestPhase.APPROACHING;
		status = controls.use() ? "Vacuuming " + pest.name()
			: !targetFresh ? "Following briefly hidden " + pest.name()
			: !lineOfSight ? "Getting line of sight to " + pest.name()
			: !movementLineOfSight ? "Confirming a clear view of " + pest.name()
			: vacuumAligned ? "Closing on " + pest.name() : "Aiming at " + pest.name();
	}

	private void chooseNextSearchPoint(LocalPlayer player) {
		particleTracker.close();
		locator.suspend();
		if (!gardenSnapshot.infestedPlots().isEmpty()
			&& visitedPlots.containsAll(gardenSnapshot.infestedPlots())) visitedPlots.clear();
		List<Integer> availablePlots = gardenSnapshot.infestedPlots().stream()
			.filter(plot -> !visitedPlots.contains(plot))
			.toList();
		Optional<PlotCenter> nearest = GardenPlotGeometry.nearestTo(player.getX(), player.getZ(), availablePlots);
		if (nearest.isPresent()) {
			PlotCenter center = nearest.get();
			selectedPlot = center.plotId();
			travelTarget = new Vec3(center.x(), cruiseY, center.z());
			phase = PestPhase.TRAVELLING_TO_PLOT;
			phaseTicks = 0;
			return;
		}

		beginLocatorSearch(true);
		status = gardenSnapshot.infestedPlots().isEmpty()
			? "Waiting for pest location data"
			: "Checking the vacuum locator";
	}

	private void tickLocatorSearch(Minecraft minecraft, LocalPlayer player) {
		phase = PestPhase.LOCATING;
		inputs.releaseMovement();
		inputs.setAttack(false);
		inputs.setUse(false);
		if (cleanupTicks - searchStartTick >= activeConfig.pestSearchTimeoutSeconds * TICKS_PER_SECOND
			|| missingTrails >= 2) {
			chooseNextSearchPoint(player);
			return;
		}
		if (!activeConfig.pestLocatorEnabled) {
			status = "Locator disabled; waiting for plot search";
			return;
		}
		MinecraftAttackInvoker attack = (MinecraftAttackInvoker) (Object) minecraft;
		boolean canClick = minecraft.gui.screen() == null && minecraft.gameMode != null
			&& minecraft.hitResult != null && !player.isSpectator() && !player.isHandsBusy()
			&& !player.isUsingItem() && attack.aquaVisionIsOp$getMissTime() <= 0;
		PestLocatorCycle.Action action = locator.tick(cleanupTicks, canClick,
			particleTracker.acceptedPointSequence(), particleTracker.hasReliableDirection());
		if (action == PestLocatorCycle.Action.CLICK) {
			particleTracker.arm(player.position());
			try {
				// The return value means instant block break, NOT whether an air click was sent.
				attack.aquaVisionIsOp$startAttack();
				LOGGER.info("Vacuum locator click {} at cleanup tick {}", locator.clickCount(), cleanupTicks);
				status = "Reading vacuum locator";
			} catch (RuntimeException exception) {
				LOGGER.warn("Vacuum locator click failed; continuing with plot data", exception);
			}
		}

		if (action == PestLocatorCycle.Action.FOLLOW && particleTracker.endpoint().isPresent()) {
			Optional<Vec3> endpoint = particleTracker.endpoint();
			Vec3 rawEndpoint = endpoint.get();
			// Search at the proven cruise height; approach a loaded pest separately with floor protection.
			double destinationY = Math.max(rawEndpoint.y, cruiseY);
			travelTarget = new Vec3(rawEndpoint.x, destinationY, rawEndpoint.z);
			selectedPlot = -1;
			phase = PestPhase.FOLLOWING_TRAIL;
			phaseTicks = 0;
			missingTrails = 0;
			LOGGER.info("Following locator trail with {} points toward {}", particleTracker.pointCount(), travelTarget);
			particleTracker.close();
			status = "Following pest locator";
			return;
		}

		if (action == PestLocatorCycle.Action.NO_TRAIL) {
			missingTrails++;
			particleTracker.close();
			status = "No clear trail; waiting before another locator click";
		} else if (locator.state() == PestLocatorCycle.State.WAITING) {
			status = "Waiting for locator (" + locator.cooldownTicks(cleanupTicks) + " ticks)";
		}
	}

	private void beginLocatorSearch(boolean newEpisode) {
		pestForwardLatched = false;
		pestLineOfSightGate.reset();
		inputs.releaseMovement();
		inputs.setAttack(false);
		inputs.setUse(false);
		particleTracker.close();
		locator.beginSearch(cleanupTicks);
		if (newEpisode) {
			searchStartTick = cleanupTicks;
			missingTrails = 0;
		}
		phase = PestPhase.LOCATING;
		phaseTicks = 0;
		selectedPlot = -1;
		travelTarget = null;
	}

	private void beginReturn() {
		pestForwardLatched = false;
		pestLineOfSightGate.reset();
		inputs.releaseAll();
		particleTracker.close();
		locator.suspend();
		target = null;
		targetMissingTicks = 0;
		targetFresh = false;
		travelTarget = null;
		if (outboundBreadcrumbs.isEmpty()) {
			outboundBreadcrumbs.add(savedPosition);
		}
		returnIndex = outboundBreadcrumbs.size() - 1;
		returnRoute = outboundBreadcrumbs.stream().map(PestAutomationController::routePoint).toList();
		returnBudgetTicks = PestReturnRoute.timeoutTicks(returnRoute, routePoint(cleanupPlayer.position()));
		returnRouteCheckTick = -100;
		returnHeadingIndex = -1;
		returnLegHeadingAcquired = false;
		returnFacingSavedHeading = false;
		finalDescentCommitted = false;
		phase = PestPhase.RETURNING;
		returnStartTick = cleanupTicks;
		phaseTicks = 0;
		status = "Returning to saved lane";
		LOGGER.info("All pests cleared: {}; return has {} breadcrumbs and a {} second budget",
			completionGate.evidence(), returnRoute.size(), returnBudgetTicks / TICKS_PER_SECOND);
	}

	private void tickReturn(Minecraft minecraft, LocalPlayer player) {
		inputs.setUse(false);
		if (!returnPhysics(player).valid()) {
			fail(minecraft, "the current movement attributes cannot safely align the return to the saved lane");
			return;
		}
		selectReturnWaypoint(player);
		if (phaseTicks % TICKS_PER_SECOND == 0) {
			LOGGER.info("Return progress: waypoint={}, saved horizontal distance={}, height error={}, horizontal speed={}, clearance={}",
				returnIndex, Math.hypot(player.getX() - savedPosition.x, player.getZ() - savedPosition.z),
				player.getY() - savedPosition.y, Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z),
				clearanceStatus);
		}
		if (returnIndex > 0) {
			Vec3 waypoint = outboundBreadcrumbs.get(returnIndex);
			status = "Returning to lane (" + returnIndex + " waypoints)";
			navigateReturn(player, waypoint, false);
			return;
		}

		if (isAtSavedLane(player)) {
			inputs.releaseAll();
			player.setDeltaMovement(Vec3.ZERO);
			player.absSnapRotationTo(savedYaw, savedPitch);
			RestoreResult restoreResult = restorePlayerState(minecraft, true);
			if (!restoreResult.inventoryRestored()) {
				farmingController.failSafeStop(minecraft, "inventory changed before the vacuum could be restored safely");
				phase = PestPhase.FAILED;
				terminalTicks = 0;
				status = "Vacuum restore validation failed";
				clearRunReferences();
				return;
			}
			if (returnTestRunning) {
				phase = PestPhase.COMPLETE;
				terminalTicks = 0;
				status = "Return test passed";
				clearRunReferences();
				announce(minecraft, "Aqua Vision is OP: return test passed at the marked lane point.");
			} else if (farmingController.completePestCleanup(minecraft)) {
				phase = PestPhase.COMPLETE;
				terminalTicks = 0;
				status = "Pests cleared";
				clearRunReferences();
			} else {
				farmingController.failSafeStop(minecraft, "pest cleanup return validation failed");
				phase = PestPhase.FAILED;
				terminalTicks = 0;
				status = "Return validation failed";
				clearRunReferences();
			}
			return;
		}

		status = "Aligning with saved lane";
		if (player.onGround()
			&& Math.abs(player.getY() - savedPosition.y) > PestNavigationMath.FINAL_LANDING_VERTICAL_TOLERANCE) {
			// A +14/16 landing is the raised soul-sand edge. Re-take flight instead of trying
			// to precision-walk with potentially boosted SkyBlock ground acceleration.
			inputs.releaseMovement();
			finalDescentCommitted = false;
			groundedReturnTicks = 0;
			status = "Taking off from the raised crop edge";
			return;
		}
		if (canFinishReturnOnGround(player)) {
			if (++groundedReturnTicks > 10 * TICKS_PER_SECOND) {
				fail(minecraft, "the final grounded return could not align with the saved lane");
				return;
			}
			Vec3 landing = finalLandingPosition();
			navigateReturn(player, new Vec3(landing.x, player.getY(), landing.z), true);
			inputs.setJump(false);
			inputs.setShift(false);
			return;
		}
		groundedReturnTicks = 0;
		// Align above the landing area before descending. Once settled, keep holding descend until
		// actual ground contact; an airborne height tolerance must never complete the return.
		Vec3 landing = finalLandingPosition();
		double horizontalDistance = Math.hypot(player.getX() - landing.x, player.getZ() - landing.z);
		boolean nearLanding = horizontalDistance <= PestNavigationMath.FINAL_DESCENT_HORIZONTAL_CORRIDOR;
		boolean landingColumnSafe = !nearLanding || isLandingColumnSafe(player, player.getX(), player.getZ(), savedPosition.y);
		boolean horizontallySettled = returnHorizontallySettled(player) && landingColumnSafe;
		finalDescentCommitted = PestNavigationMath.nextFinalDescentCommitted(
			finalDescentCommitted, horizontallySettled, horizontalDistance);
		if (finalDescentCommitted && !player.onGround()
			&& PestNavigationMath.passedFinalDescentFloor(player.getY(), savedPosition.y)) {
			fail(minecraft, "the saved lane landing surface was not found; stopped below its recorded height");
			return;
		}
		double targetY = finalDescentCommitted
			? savedPosition.y : Math.max(player.getY(), savedPosition.y + 1.0);
		PestLandingTargetPlanner.Point horizontalTarget = new PestLandingTargetPlanner.Point(landing.x, landing.z);
		if (!landingColumnSafe) {
			horizontalTarget = PestLandingTargetPlanner.edgeClearanceTarget(
				player.getX(), player.getZ(), landing.x, landing.z, savedYaw, EDGE_CLEARANCE_EXTENSION);
		}
		navigateReturn(player, new Vec3(horizontalTarget.x(), targetY, horizontalTarget.z()), true,
			landingColumnSafe ? Double.NaN : PestLandingTargetPlanner.MINIMUM_SETTLE_TOLERANCE);
		if (finalDescentCommitted) {
			// Horizontal collision must not produce Jump+Shift and cancel vanilla flight descent.
			PestFlightSafety.Controls verticalControls = PestFlightSafety.finalDescentControls(
				player.onGround(), player.getY(), savedPosition.y);
			inputs.setJump(verticalControls.jump());
			inputs.setShift(landingColumnSafe && verticalControls.descend());
			status = !landingColumnSafe ? "Moving clear of the raised crop edge"
				: player.onGround() && player.getY() - savedPosition.y > PestNavigationMath.FINAL_LANDING_VERTICAL_TOLERANCE
					? "Stepping off the raised crop edge" : "Descending to saved lane";
			if (phaseTicks % 10 == 0) {
				LOGGER.info("Final descent: landing distance={}, saved distance={}, height error={}, column safe={}, onGround={}, flying={}, descend={}, vertical speed={}",
					horizontalDistance,
					Math.hypot(player.getX() - savedPosition.x, player.getZ() - savedPosition.z),
					player.getY() - savedPosition.y, landingColumnSafe, player.onGround(),
					player.getAbilities().flying, inputs.isShiftRequested(), player.getDeltaMovement().y);
			}
		}
	}

	private Vec3 finalLandingPosition() {
		return landingPosition == null ? savedPosition : landingPosition;
	}

	private double finalLandingTolerance() {
		Vec3 landing = finalLandingPosition();
		double insetDistance = Math.hypot(landing.x - savedPosition.x, landing.z - savedPosition.z);
		return PestLandingTargetPlanner.settleTolerance(activeConfig.pausePositionTolerance, insetDistance);
	}

	/**
	 * Prefer a tiny inset away from the saved farming direction, then try the remaining
	 * compass directions. The selected footprint must be clear at the recorded foot Y
	 * and must still have a supporting surface there. This prevents a return from choosing
	 * the raised edge of the adjacent soul-sand crop bed.
	 */
	private Optional<Vec3> chooseCollisionSafeLandingPosition(
		LocalPlayer player,
		Vec3 saved,
		float yaw,
		double positionTolerance
	) {
		double inset = PestLandingTargetPlanner.preferredInset(positionTolerance);
		List<PestLandingTargetPlanner.Point> candidates = PestLandingTargetPlanner.candidates(
			saved.x, saved.z, yaw, inset);
		for (PestLandingTargetPlanner.Point candidate : candidates) {
			if (Math.hypot(candidate.x() - saved.x, candidate.z() - saved.z) > positionTolerance
				|| !isLandingColumnSafe(player, candidate.x(), candidate.z(), saved.y)) continue;
			Vec3 selected = new Vec3(candidate.x(), saved.y, candidate.z());
			LOGGER.info("Saved collision-safe lane landing at {} (offset {}, {})",
				selected, candidate.x() - saved.x, candidate.z() - saved.z);
			return Optional.of(selected);
		}
		LOGGER.warn("No collision-safe inset was available at the recorded lane point {}", saved);
		return Optional.empty();
	}

	/** True when the player's full footprint can occupy the saved Y and has floor support. */
	private boolean isLandingColumnSafe(LocalPlayer player, double x, double z, double savedY) {
		if (player == null || !Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(savedY)) return false;
		AABB shifted = player.getBoundingBox().move(
			x - player.getX(), savedY - player.getY(), z - player.getZ());
		AABB clearance = new AABB(
			shifted.minX - 0.01, shifted.minY + 1.0e-4, shifted.minZ - 0.01,
			shifted.maxX + 0.01, shifted.maxY - 1.0e-4, shifted.maxZ + 0.01);
		AABB support = new AABB(
			shifted.minX + 0.04, shifted.minY - 0.08, shifted.minZ + 0.04,
			shifted.maxX - 0.04, shifted.minY + 0.01, shifted.maxZ - 0.04);
		AABB checked = clearance.minmax(support);
		if (!player.level().hasChunksAt(
			BlockPos.containing(checked.minX, checked.minY, checked.minZ),
			BlockPos.containing(checked.maxX, checked.maxY, checked.maxZ))) return false;
		return player.level().noBlockCollision(player, clearance)
			&& !player.level().noBlockCollision(player, support);
	}

	private static PestReturnRoute.Point routePoint(Vec3 point) {
		return new PestReturnRoute.Point(point.x, point.y, point.z);
	}

	/** Skip old chase detours only through loaded, collision-checked space. Never changes the saved anchor. */
	private void selectReturnWaypoint(LocalPlayer player) {
		int previousIndex = returnIndex;
		while (returnIndex > 0 && PestReturnRoute.reachedIntermediate(routePoint(player.position()), returnRoute.get(returnIndex))) {
			returnIndex--;
		}
		if (cleanupTicks - returnRouteCheckTick >= 10 || returnIndex != previousIndex) {
			returnRouteCheckTick = cleanupTicks;
			int[] collisionChecksLeft = {512};
			returnIndex = PestReturnRoute.nextIndex(returnRoute, returnIndex, routePoint(player.position()), (from, to) -> {
				Vec3 start = new Vec3(from.x(), from.y(), from.z());
				Vec3 end = new Vec3(to.x(), to.y(), to.z());
				if (to.equals(returnRoute.getFirst())) {
					// The actual final route aligns above the lane and then descends vertically, not diagonally.
					Vec3 landing = finalLandingPosition();
					end = new Vec3(landing.x, end.y, landing.z);
					Vec3 aboveLane = new Vec3(end.x, Math.max(start.y, end.y + 1.0), end.z);
					return clearReturnSegment(player, start, aboveLane, collisionChecksLeft)
						&& clearReturnSegment(player, aboveLane, end, collisionChecksLeft);
				}
				return clearReturnSegment(player, start, end, collisionChecksLeft);
			});
		}
		if (returnHeadingIndex != returnIndex) {
			Vec3 waypoint = outboundBreadcrumbs.get(returnIndex);
			returnHeading = Math.hypot(waypoint.x - player.getX(), waypoint.z - player.getZ()) < 0.01
				? player.getYRot() : PestNavigationMath.aimAt(player.getX(), player.getEyeY(), player.getZ(),
					waypoint.x, player.getEyeY(), waypoint.z).yawDegrees();
			returnHeadingIndex = returnIndex;
			returnLegHeadingAcquired = false;
			LOGGER.info("Return waypoint {} selected at {} (previous {})", returnIndex, waypoint, previousIndex);
		}
	}

	private boolean clearReturnSegment(LocalPlayer player, Vec3 start, Vec3 end, int[] checksLeft) {
		Vec3 delta = end.subtract(start);
		double length = delta.length();
		if (length > 96.0) return false;
		int steps = Math.max(1, (int) Math.ceil(length / 0.5));
		if (steps > checksLeft[0]) return false;
		checksLeft[0] -= steps;
		Vec3 step = delta.scale(1.0 / steps);
		// Vertical and horizontal keys accelerate independently. Approve the whole height corridor,
		// not just a thin diagonal the player might leave while climbing/descending earlier.
		Vec3 corridorStart = new Vec3(start.x, Math.min(start.y, end.y), start.z);
		AABB body = player.getBoundingBox().deflate(1.0e-5).move(corridorStart.subtract(player.position()))
			.expandTowards(0, Math.abs(delta.y), 0);
		for (int index = 0; index < steps; index++) {
			AABB swept = body.expandTowards(step.x, 0, step.z);
			if (!player.level().hasChunksAt(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
				BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))
				|| !player.level().noBlockCollision(player, swept)) return false;
			body = body.move(step.x, 0, step.z);
		}
		return true;
	}

	/** Return-only velocity-aware WASD steering. Pest pursuit keeps its existing controls. */
	private void navigateReturn(LocalPlayer player, Vec3 destination, boolean finalApproach) {
		navigateReturn(player, destination, finalApproach, Double.NaN);
	}

	private void navigateReturn(LocalPlayer player, Vec3 destination, boolean finalApproach, double forcedTolerance) {
		inputs.setAttack(false);
		inputs.setUse(false);
		double dx = destination.x - player.getX();
		double dz = destination.z - player.getZ();
		double distance = Math.hypot(dx, dz);
		// Keep one heading per straight leg. Near the anchor, face the saved farming direction instead
		// of looking at tiny position errors that otherwise flip the target yaw by 180 degrees.
		if (finalApproach && distance <= 2.0) returnFacingSavedHeading = true;
		double heading = returnFacingSavedHeading ? savedYaw : returnHeading;
		double radians = Math.toRadians(heading);
		camera.aimAt(player, player.getEyePosition().add(-Math.sin(radians) * 10.0, 0, Math.cos(radians) * 10.0), 8.0, 8.0);
		ReturnPhysics physics = returnPhysics(player);
		double tolerance = Double.isFinite(forcedTolerance) ? forcedTolerance
			: finalApproach ? finalLandingTolerance() : 1.0;
		if (!returnFacingSavedHeading) {
			returnLegHeadingAcquired = PestReturnSteering.isLegHeadingAcquired(
				player.getYRot(), heading, returnLegHeadingAcquired);
		}
		PestReturnSteering.Controls controls = !returnFacingSavedHeading && !returnLegHeadingAcquired
			? new PestReturnSteering.Controls(false, false, false, false, false)
			: PestReturnSteering.steer(dx, dz,
				player.getDeltaMovement().x, player.getDeltaMovement().z, player.getYRot(),
				physics.acceleration(), physics.drag(), tolerance);
		inputs.setForward(controls.forward());
		inputs.setBackward(controls.backward());
		inputs.setLeft(controls.left());
		inputs.setRight(controls.right());
		double deltaY = destination.y - player.getY();
		inputs.setJump(deltaY > (finalApproach ? 0.20 : 0.8) || player.horizontalCollision);
		inputs.setShift(!player.horizontalCollision && deltaY < (finalApproach ? -0.20 : -0.8));
	}

	private boolean returnHorizontallySettled(LocalPlayer player) {
		if (savedPosition == null || activeConfig == null) return false;
		Vec3 landing = finalLandingPosition();
		ReturnPhysics physics = returnPhysics(player);
		return physics.valid() && PestReturnSteering.isSettled(landing.x - player.getX(), landing.z - player.getZ(),
			player.getDeltaMovement().x, player.getDeltaMovement().z, physics.drag(), finalLandingTolerance());
	}

	private ReturnPhysics returnPhysics(LocalPlayer player) {
		double airDrag = modifiedFriction(0.91, player.getAttributeValue(Attributes.AIR_DRAG_MODIFIER));
		if (!player.onGround()) return new ReturnPhysics(PestReturnSteering.airborneAcceleration(
			player.getAbilities().getFlyingSpeed(), player.isSprinting()), airDrag);
		double friction = modifiedFriction(player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement())
			.getBlock().getFriction(), player.getAttributeValue(Attributes.FRICTION_MODIFIER));
		double acceleration = 0.98 * player.getSpeed();
		if (friction > 0.6) acceleration *= 0.21600002 / (friction * friction * friction);
		return new ReturnPhysics(acceleration, friction * airDrag);
	}

	private static double modifiedFriction(double base, double modifier) {
		return Math.clamp(1.0 - (1.0 - base) * modifier, 0.0, 1.0);
	}

	private record ReturnPhysics(double acceleration, double drag) {
		boolean valid() {
			return Double.isFinite(acceleration) && acceleration > 0 && drag > 0 && drag < 1;
		}
	}

	private boolean canFinishReturnOnGround(LocalPlayer player) {
		return savedPosition != null && PestNavigationMath.canFinishReturnOnGround(player.onGround(),
			Math.hypot(player.getX() - savedPosition.x, player.getZ() - savedPosition.z),
			Math.abs(player.getY() - savedPosition.y));
	}

	private boolean isAtSavedLane(LocalPlayer player) {
		if (savedPosition == null || activeConfig == null) return false;
		double horizontalDistance = Math.hypot(player.getX() - savedPosition.x, player.getZ() - savedPosition.z);
		if (horizontalDistance > activeConfig.pausePositionTolerance) return false;
		return PestNavigationMath.hasLandedAtSavedLane(returnHorizontallySettled(player), player.onGround(),
			horizontalDistance, Math.abs(player.getY() - savedPosition.y));
	}

	private void navigateTo(LocalPlayer player, Vec3 destination, boolean precise) {
		inputs.setAttack(false);
		inputs.setUse(false);
		inputs.setLeft(false);
		inputs.setRight(false);
		inputs.setBackward(false);
		double deltaY = destination.y - player.getY();
		double horizontalDistance = Math.hypot(destination.x - player.getX(), destination.z - player.getZ());
		camera.aimAt(player, new Vec3(destination.x, player.getEyeY(), destination.z), precise ? 8.0 : 10.0, 8.0);
		AimAngles aim = PestNavigationMath.aimAt(player.getX(), player.getEyeY(), player.getZ(),
			destination.x, player.getEyeY(), destination.z);
		boolean forward = horizontalDistance > (precise ? 0.10 : 1.25)
			&& PestNavigationMath.isHeadingAligned(player.getYRot(), aim.yawDegrees(), precise ? 25.0 : 35.0);
		inputs.setForward(forward);
		inputs.setJump(deltaY > (precise ? 0.20 : 0.8) || player.horizontalCollision);
		inputs.setShift(!player.horizontalCollision && deltaY < (precise ? -0.20 : -0.8)
			&& PestFlightSafety.mayDescendToward(forward, horizontalDistance, precise ? 0.10 : 1.25));
	}

	/** Apply after every pest phase, including hover/use, without changing permitted-flight ownership. */
	private void protectFlightClearance(LocalPlayer player) {
		if (player == null || !player.getAbilities().flying || player.onGround()
			|| flightMonitor.getPhase() != PestFlightMonitor.Phase.FLYING) {
			clearanceStatus = "Takeoff/recovery";
			return;
		}
		Vec3 landing = finalLandingPosition();
		double savedHorizontalDistance = landing == null ? Double.POSITIVE_INFINITY
			: Math.hypot(player.getX() - landing.x, player.getZ() - landing.z);
		boolean finalLanding = savedPosition != null && activeConfig != null && finalDescentCommitted
			&& phase == PestPhase.RETURNING && returnIndex <= 0
			&& PestNavigationMath.insideFinalDescentCorridor(savedHorizontalDistance);
		if (finalLanding) {
			clearanceStatus = "Final lane landing";
			return;
		}
		AABB body = player.getBoundingBox().deflate(1.0e-5);
		double probeDistance = PestFlightSafety.groundProbeDistance(player.getDeltaMovement().y);
		boolean nearGround = !player.level().noBlockCollision(player,
			body.expandTowards(0.0, -probeDistance, 0.0));
		boolean ascentClear = player.level().noBlockCollision(player,
			body.expandTowards(0.0, PestFlightSafety.ASCENT_PROBE, 0.0));
		PestFlightSafety.Controls controls = PestFlightSafety.protect(nearGround, ascentClear, false,
			inputs.isJumpRequested(), inputs.isShiftRequested());
		inputs.setJump(controls.jump());
		inputs.setShift(controls.descend());
		clearanceStatus = !nearGround ? "Clear" : ascentClear ? "Braking above floor" : "Low-ceiling hover";
	}

	private void recordBreadcrumb(Vec3 position) {
		if (phase == PestPhase.RETURNING || outboundBreadcrumbs.isEmpty()) {
			return;
		}
		if (outboundBreadcrumbs.getLast().distanceTo(position) >= BREADCRUMB_SPACING) {
			outboundBreadcrumbs.add(position);
		}
	}

	private void recordReturnTestBreadcrumb(Minecraft minecraft) {
		if (returnTestRunning || returnTestMarkedPosition == null) return;
		if (minecraft.player != returnTestMarkedPlayer || minecraft.level != returnTestMarkedLevel
			|| minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isRemoved()) {
			clearReturnTestMarker();
			return;
		}
		Vec3 position = minecraft.player.position();
		if (returnTestBreadcrumbs.getLast().distanceTo(position) < RETURN_TEST_BREADCRUMB_SPACING) return;
		if (returnTestBreadcrumbs.size() >= MAXIMUM_RETURN_TEST_BREADCRUMBS) {
			clearReturnTestMarker();
			announce(minecraft, "Aqua Vision is OP: return-test recording expired; mark the lane again.");
			return;
		}
		returnTestBreadcrumbs.add(position);
	}

	public ReturnTestCommandResult markReturnTest(Minecraft minecraft) {
		if (phase.isActive() || farmingController.isSessionRunning()) {
			return new ReturnTestCommandResult(false, "stop farming and pest cleanup before marking a return test");
		}
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !player.isAlive() || player.isRemoved()) {
			return new ReturnTestCommandResult(false, "enter a world with a living player first");
		}
		if (!player.onGround()) {
			return new ReturnTestCommandResult(false, "stand on the farming-lane floor before marking the return test");
		}
		FarmConfig markConfig = configSupplier.get();
		if (markConfig != null) markConfig.validate();
		double markTolerance = markConfig == null ? 0.35 : markConfig.pausePositionTolerance;
		Vec3 markedPosition = player.position();
		Optional<Vec3> markedLanding = chooseCollisionSafeLandingPosition(
			player, markedPosition, player.getYRot(), markTolerance);
		if (markedLanding.isEmpty()) {
			return new ReturnTestCommandResult(false,
				"no collision-safe landing point is available here; move slightly within the lane and mark again");
		}
		returnTestMarkedLevel = minecraft.level;
		returnTestMarkedPlayer = player;
		returnTestMarkedPosition = markedPosition;
		returnTestMarkedLandingPosition = markedLanding.orElseThrow();
		returnTestMarkedYaw = player.getYRot();
		returnTestMarkedPitch = player.getXRot();
		returnTestBreadcrumbs.clear();
		returnTestBreadcrumbs.add(returnTestMarkedPosition);
		return new ReturnTestCommandResult(true,
			"lane point marked; manually move or fly away along a safe route, then run /avop returntest go");
	}

	public ReturnTestCommandResult startReturnTest(Minecraft minecraft) {
		if (phase.isActive() || farmingController.isSessionRunning()) {
			return new ReturnTestCommandResult(false, "stop farming and pest cleanup before starting the return test");
		}
		LocalPlayer player = minecraft.player;
		if (returnTestMarkedPosition == null) {
			return new ReturnTestCommandResult(false, "mark the lane first with /avop returntest mark");
		}
		if (player == null || minecraft.level != returnTestMarkedLevel || player != returnTestMarkedPlayer
			|| !player.isAlive() || player.isRemoved()) {
			clearReturnTestMarker();
			return new ReturnTestCommandResult(false, "the world or player changed; mark the lane again");
		}
		if (!player.getAbilities().mayfly) {
			return new ReturnTestCommandResult(false, "flight is unavailable here, so the return test cannot run safely");
		}
		if (player.position().distanceTo(returnTestMarkedPosition) < 2.0) {
			return new ReturnTestCommandResult(false, "move at least two blocks away from the marked lane first");
		}
		if (returnTestBreadcrumbs.getLast().distanceTo(player.position()) > 0.1) {
			returnTestBreadcrumbs.add(player.position());
		}

		activeConfig = configSupplier.get();
		if (activeConfig == null) return new ReturnTestCommandResult(false, "configuration is unavailable");
		activeConfig.validate();
		cleanupPlayer = player;
		savedPosition = returnTestMarkedPosition;
		landingPosition = returnTestMarkedLandingPosition == null ? savedPosition : returnTestMarkedLandingPosition;
		savedYaw = returnTestMarkedYaw;
		savedPitch = returnTestMarkedPitch;
		originalFlying = player.getAbilities().flying;
		changedFlying = !originalFlying;
		originalSelectedSlot = -1;
		inventorySwapped = false;
		outboundBreadcrumbs.clear();
		outboundBreadcrumbs.addAll(returnTestBreadcrumbs);
		clearReturnTestMarker();
		returnRoute = outboundBreadcrumbs.stream().map(PestAutomationController::routePoint).toList();
		returnIndex = returnRoute.size() - 1;
		returnBudgetTicks = PestReturnRoute.timeoutTicks(returnRoute, routePoint(player.position()));
		returnStartTick = 0;
		returnRouteCheckTick = -100;
		returnHeadingIndex = -1;
		returnLegHeadingAcquired = false;
		returnFacingSavedHeading = false;
		finalDescentCommitted = false;
		returnTestRunning = true;
		cleanupTicks = 0;
		phaseTicks = 0;
		groundedReturnTicks = 0;
		flightMonitor.reset();
		flightJumpGuard.reset();
		camera.reset();
		particleTracker.close();
		locator.suspend();
		inputs.releaseAll();
		phase = PestPhase.RETURNING;
		status = "Return test running";
		LOGGER.info("Return test started through {} recorded breadcrumbs with a {} second budget",
			returnRoute.size(), returnBudgetTicks / TICKS_PER_SECOND);
		return new ReturnTestCommandResult(true, "return test started; press F8 to stop it immediately");
	}

	public ReturnTestCommandResult cancelReturnTestMarker() {
		if (returnTestMarkedPosition == null) {
			return new ReturnTestCommandResult(false, "no return-test lane point is currently marked");
		}
		clearReturnTestMarker();
		return new ReturnTestCommandResult(true, "return-test lane mark cleared");
	}

	private void clearReturnTestMarker() {
		returnTestMarkedLevel = null;
		returnTestMarkedPlayer = null;
		returnTestMarkedPosition = null;
		returnTestMarkedLandingPosition = null;
		returnTestMarkedYaw = 0;
		returnTestMarkedPitch = 0;
		returnTestBreadcrumbs.clear();
	}

	private void fail(Minecraft minecraft, String reason) {
		boolean failedReturnTest = returnTestRunning;
		inputs.releaseAll();
		if (failedReturnTest && cleanupPlayer != null && minecraft.player == cleanupPlayer) {
			cleanupPlayer.setDeltaMovement(Vec3.ZERO);
		}
		particleTracker.close();
		RestoreResult restoreResult = restorePlayerState(minecraft, false);
		phase = PestPhase.FAILED;
		terminalTicks = 0;
		String finalReason = reason;
		if (!restoreResult.inventoryRestored()) {
			finalReason += "; the vacuum inventory swap also needs manual checking";
		}
		if (restoreResult.flightKeptForSafety()) {
			finalReason += "; flight was left enabled so the player would not fall—land manually";
		}
		status = finalReason;
		clearRunReferences();
		if (failedReturnTest) {
			announce(minecraft, "Aqua Vision is OP: return test failed: " + finalReason + ".");
		} else {
			farmingController.failSafeStop(minecraft, finalReason);
		}
	}

	/** Cancels internal pest work without generating an alert; the caller decides the farming state. */
	public void cancel(Minecraft minecraft) {
		boolean cancelledReturnTest = returnTestRunning;
		inputs.releaseAll();
		if (cancelledReturnTest && cleanupPlayer != null && minecraft.player == cleanupPlayer) {
			cleanupPlayer.setDeltaMovement(Vec3.ZERO);
		}
		particleTracker.close();
		RestoreResult restoreResult = restorePlayerState(minecraft, false);
		if (!restoreResult.inventoryRestored()) {
			announce(
				minecraft,
				"Aqua Vision is OP: stopped without a desktop alert, but the vacuum swap could not be restored safely. Check the configured hotbar and original inventory slots."
			);
		}
		if (restoreResult.flightKeptForSafety()) {
			announce(
				minecraft,
				"Aqua Vision is OP: flight remains enabled and motion was stopped because pest cleanup ended away from the saved lane. Land manually before disabling flight."
			);
		}
		phase = PestPhase.IDLE;
		status = "Waiting";
		terminalTicks = 0;
		startGate.reset();
		gardenSnapshot = Snapshot.unknown();
		observedLevel = null;
		observedPlayer = null;
		observedSessionRunning = false;
		statusPollTicks = STATUS_POLL_TICKS - 1;
		clearRunReferences();
		clearReturnTestMarker();
		if (cancelledReturnTest) announce(minecraft, "Aqua Vision is OP: return test cancelled.");
	}

	private RestoreResult restorePlayerState(Minecraft minecraft, boolean exactReturnComplete) {
		LocalPlayer player = cleanupPlayer;
		if (player == null) {
			return new RestoreResult(true, false);
		}
		boolean inventoryRestored = true;
		boolean flightKeptForSafety = false;
		if (inventorySwapped && minecraft.gameMode != null && minecraft.player == player
			&& player.containerMenu == player.inventoryMenu && swappedMenuSlot >= 0 && vacuumHotbarSlot >= 0) {
			ItemStack hotbarItem = player.getInventory().getItem(vacuumHotbarSlot);
			ItemStack displacedItem = player.getInventory().getItem(swappedInventorySlot);
			if (vacuumFingerprint != null && vacuumFingerprint.matches(hotbarItem)
				&& displacedHotbarFingerprint != null && displacedHotbarFingerprint.matches(displacedItem)) {
				try {
					minecraft.gameMode.handleContainerInput(
						player.inventoryMenu.containerId,
						swappedMenuSlot,
						vacuumHotbarSlot,
						ContainerInput.SWAP,
						player
					);
				} catch (RuntimeException exception) {
					inventoryRestored = false;
					LOGGER.warn("Could not restore the pre-cleanup hotbar swap", exception);
				}
			} else {
				inventoryRestored = false;
				LOGGER.warn("Skipped unsafe vacuum restore because the hotbar or inventory slot changed");
			}
		} else if (inventorySwapped) {
			inventoryRestored = false;
			LOGGER.warn("Could not safely restore the vacuum because the inventory menu was unavailable");
		}
		if (originalSelectedSlot >= 0 && originalSelectedSlot < Inventory.getSelectionSize()) {
			player.getInventory().setSelectedSlot(originalSelectedSlot);
		}
		if (changedFlying) {
			// Successful exact returns are now grounded. On every failure, keep flight while airborne;
			// mere proximity to a missing landing surface is not proof that disabling it is safe.
			boolean safeToDisableFlight = exactReturnComplete || player.onGround();
			if (safeToDisableFlight) {
				player.getAbilities().flying = originalFlying && player.getAbilities().mayfly;
				if (minecraft.player == player) {
					try {
						player.onUpdateAbilities();
					} catch (RuntimeException exception) {
						LOGGER.warn("Could not send the restored flight state", exception);
					}
				}
			} else if (player.getAbilities().mayfly && minecraft.player == player) {
				boolean needsFlightUpdate = !player.getAbilities().flying;
				player.getAbilities().flying = true;
				player.setDeltaMovement(Vec3.ZERO);
				flightKeptForSafety = true;
				if (needsFlightUpdate) {
					try {
						player.onUpdateAbilities();
					} catch (RuntimeException exception) {
						LOGGER.warn("Could not request allowed flight during off-route stop", exception);
					}
				}
				LOGGER.warn("Left flight enabled because pest cleanup ended away from a safe return position");
			} else if (!player.getAbilities().mayfly) {
				// A safe stop cannot grant flight permission revoked by the server.
				player.getAbilities().flying = false;
				LOGGER.warn("Could not preserve off-route flight because flight permission is unavailable");
			}
		}
		inventorySwapped = false;
		changedFlying = false;
		cruiseY = 0.0;
		return new RestoreResult(inventoryRestored, flightKeptForSafety);
	}

	private void clearRunReferences() {
		flightMonitor.reset();
		flightJumpGuard.reset();
		locator.reset();
		camera.reset();
		completionGate.reset();
		cleanupProgress.reset(-1);
		activeConfig = null;
		clearanceStatus = "Inactive";
		cleanupPlayer = null;
		target = null;
		targetMissingTicks = 0;
		targetFresh = false;
		pestForwardLatched = false;
		pestLineOfSightGate.reset();
		lastVacuumTarget = null;
		lastVacuumPosition = null;
		lastVacuumUseTick = -100;
		savedPosition = null;
		landingPosition = null;
		travelTarget = null;
		selectedPlot = -1;
		originalSelectedSlot = -1;
		vacuumHotbarSlot = -1;
		swappedInventorySlot = -1;
		swappedMenuSlot = -1;
		inventorySwapped = false;
		changedFlying = false;
		vacuumFingerprint = null;
		displacedHotbarFingerprint = null;
		outboundBreadcrumbs.clear();
		visitedPlots.clear();
		cleanupTicks = 0;
		phaseTicks = 0;
		searchStartTick = 0;
		missingTrails = 0;
		counterFreeGardenHud = false;
		returnIndex = 0;
		returnStartTick = -1;
		returnRoute = List.of();
		returnBudgetTicks = PestRunDeadline.RETURN_TIMEOUT_TICKS;
		returnRouteCheckTick = -100;
		returnHeadingIndex = -1;
		returnHeading = 0;
		returnLegHeadingAcquired = false;
		returnFacingSavedHeading = false;
		finalDescentCommitted = false;
		returnTestRunning = false;
		groundedReturnTicks = 0;
	}

	private static void announce(Minecraft minecraft, String message) {
		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(Component.literal(message));
		}
	}

	public PestPhase getPhase() {
		return phase;
	}

	public String getStatus() {
		return status;
	}

	public int getReportedPestCount() {
		return gardenSnapshot.pestCount().orElse(-1);
	}

	public String getDetectionSummary() {
		return detectionSummary(configSupplier.get(), gardenSnapshot);
	}

	private static String detectionSummary(FarmConfig config, Snapshot snapshot) {
		return "Pests: " + (snapshot.pestCount().isPresent() ? snapshot.pestCount().getAsInt() : "unknown")
			+ " | threshold " + (config == null ? "?" : config.pestActivationThreshold)
			+ " | auto " + (config != null && config.pestAutomationEnabled ? "ON" : "OFF");
	}

	private String detectionStatus(Minecraft minecraft, FarmConfig config, Snapshot snapshot) {
		if (config == null) return "Pest settings unavailable";
		if (!config.pestAutomationEnabled) return "Pest automation OFF: enable in /avop config";
		if (minecraft.player == null || minecraft.level == null) return "Waiting for a world";
		if (!snapshot.inGarden()) return "Garden location not detected in sidebar or tab Area";
		if (snapshot.pestCount().isEmpty()) {
			return "conflict".equals(snapshot.countSource())
				? "Conflicting pest totals: /avop pests" : "Pest total unknown: /avop pests";
		}
		if (!farmingController.isSessionRunning()) return "Farming stopped: press F6 to start";
		if (farmingController.isPaused()) return "Waiting for farming to resume";
		if (farmingController.isRecovering()) return "Waiting for void recovery";
		if (minecraft.gui.screen() != null) return "Waiting for the open screen to close";
		if (snapshot.pestCount().getAsInt() < config.pestActivationThreshold) return "Below pest threshold";
		if (startGate.confirmedPolls() < PestStartGate.REQUIRED_POLLS) {
			return "Confirming pest count: " + startGate.confirmedPolls() + "/" + PestStartGate.REQUIRED_POLLS;
		}
		return farmingController.canStartPestCleanup() ? "Ready for pest cleanup"
			: "Pest count confirmed: waiting for next A/D lane";
	}

	/** Fresh read-only diagnostics: never advances confirmations, moves items, or starts a hunt. */
	public List<String> getDiagnosticLines(Minecraft minecraft) {
		Snapshot snapshot = gardenReader.read(minecraft);
		FarmConfig config = configSupplier.get();
		List<String> lines = new ArrayList<>();
		lines.add(detectionSummary(config, snapshot));
		lines.add("Pest desktop alert: " + (config != null && config.pestCountDesktopNotification ? "ON" : "OFF")
			+ " | threshold " + (config == null ? "?" : config.pestCountNotificationThreshold));
		lines.add("Source: " + snapshot.countSource() + " | Garden: " + snapshot.inGarden()
			+ " | confirmed polls: " + startGate.confirmedPolls() + "/" + PestStartGate.REQUIRED_POLLS);
		lines.add("State: " + farmingController.getState() + " / " + phase + " | "
			+ (phase.isActive() || phase.isTerminal() ? status : detectionStatus(minecraft, config, snapshot)));
		if (!snapshot.countEvidence().isBlank()) lines.add("Count evidence: " + snapshot.countEvidence());
		if (minecraft.player != null && config != null) {
			VacuumSelection vacuum = findBestVacuum(minecraft.player.getInventory(), config.pestMoveVacuumFromInventory);
			lines.add("Flight allowed: " + minecraft.player.getAbilities().mayfly
				+ " | flying: " + minecraft.player.getAbilities().flying + " | grounded: " + minecraft.player.onGround()
				+ " | eligible vacuum: " + (vacuum == null ? "none" : vacuum.name()));
			lines.add("Flight control: " + flightMonitor.getPhase() + " | requests: "
				+ flightMonitor.getRequests() + " | recoveries: " + flightMonitor.getRecoveries());
			lines.add("Flight clearance: " + clearanceStatus);
			if (phase == PestPhase.RETURNING && savedPosition != null) {
				lines.add(String.format(Locale.ROOT,
					"Return: waypoint %d | %.2f blocks from lane | height error %.2f | speed %.3f | %d/%ds",
					returnIndex, Math.hypot(minecraft.player.getX() - savedPosition.x, minecraft.player.getZ() - savedPosition.z),
					minecraft.player.getY() - savedPosition.y,
					Math.hypot(minecraft.player.getDeltaMovement().x, minecraft.player.getDeltaMovement().z),
					(cleanupTicks - returnStartTick) / TICKS_PER_SECOND, returnBudgetTicks / TICKS_PER_SECOND));
			}
		}
		lines.add("Infested plot IDs: " + snapshot.infestedPlots());
		lines.add("Clear check: " + completionGate.evidence() + " | counter-free Garden HUD: "
			+ (snapshot.infestedPlots().isEmpty()
				&& PestClearEvidence.hasCounterFreeGardenHud(snapshot.scoreboardLines(), snapshot.tabLines())));
		lines.add("Locator: " + locator.state() + " | clicks: " + locator.clickCount()
			+ " | cooldown ticks: " + locator.cooldownTicks(cleanupTicks)
			+ " | trail points: " + particleTracker.pointCount()
			+ " | target: " + (target == null ? "none" : target.name()
				+ (targetFresh ? " (fresh)" : " (retained gap " + targetMissingTicks + ")")));
		// Local feedback only; no automatic upload or full player-list/account-data dump.
		lines.add("Sidebar (" + snapshot.scoreboardLines().size() + " lines): "
			+ String.join(" | ", snapshot.scoreboardLines()));
		for (String line : snapshot.scoreboardLines()) {
			String plain = PestCountParser.stripFormatting(line);
			if (plain.toLowerCase(Locale.ROOT).contains("garden")) {
				StringBuilder escaped = new StringBuilder();
				plain.codePoints().forEach(codePoint -> {
					if (codePoint >= 32 && codePoint <= 126) escaped.appendCodePoint(codePoint);
					else escaped.append(String.format(Locale.ROOT, "\\u{%04X}", codePoint));
				});
				lines.add("Garden text (escaped): " + escaped);
			}
		}
		List<String> relevantTab = snapshot.tabLines().stream().filter(line -> {
			String lower = line.toLowerCase(Locale.ROOT);
			return lower.contains("pest") || lower.contains("plot") || lower.contains("garden");
		}).limit(12).toList();
		lines.add("Tab evidence: " + (relevantTab.isEmpty() ? "none" : String.join(" | ", relevantTab)));
		return List.copyOf(lines);
	}

	public int getSelectedPlot() {
		return selectedPlot;
	}

	public int getElapsedSeconds() {
		return cleanupTicks / TICKS_PER_SECOND;
	}

	public boolean isActive() {
		return phase.isActive();
	}

	public boolean isReturnTestRunning() {
		return returnTestRunning;
	}

	public record ReturnTestCommandResult(boolean success, String message) {
	}

	private record VacuumSelection(
		int inventorySlot,
		double range,
		String name,
		ItemFingerprint fingerprint
	) {
	}

	private record RestoreResult(boolean inventoryRestored, boolean flightKeptForSafety) {
	}

	private record TargetChoice(PestTarget target, boolean fresh) {
	}

	private record ItemFingerprint(Item item, String displayName, int count, boolean empty) {
		private static ItemFingerprint of(ItemStack stack) {
			return new ItemFingerprint(
				stack.getItem(),
				stack.isEmpty() ? "" : stack.getHoverName().getString(),
				stack.getCount(),
				stack.isEmpty()
			);
		}

		private boolean matches(ItemStack stack) {
			if (empty) {
				return stack.isEmpty();
			}
			return !stack.isEmpty()
				&& stack.getItem() == item
				&& stack.getCount() == count
				&& stack.getHoverName().getString().equals(displayName);
		}
	}
}
