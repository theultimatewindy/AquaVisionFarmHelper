package dev.winso.netherwarthelper;

import dev.winso.netherwarthelper.background.BackgroundOperationController;
import dev.winso.netherwarthelper.config.ConfigManager;
import dev.winso.netherwarthelper.config.FarmConfig;
import dev.winso.netherwarthelper.controller.FarmingController;
import dev.winso.netherwarthelper.gui.AquaVisionConfigScreen;
import dev.winso.netherwarthelper.hud.FarmHudRenderer;
import dev.winso.netherwarthelper.input.InputController;
import dev.winso.netherwarthelper.keybind.FarmKeybinds;
import dev.winso.netherwarthelper.notification.DesktopNotifier;
import dev.winso.netherwarthelper.pest.PestAutomationController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetherWartFarmHelperClient implements ClientModInitializer {
	public static final String MOD_ID = "nether_wart_farm_helper";
	private static final Logger LOGGER = LoggerFactory.getLogger("AquaVisionOP");

	private ConfigManager configManager;
	private FarmConfig config;
	private InputController inputs;
	private FarmingController controller;
	private FarmKeybinds keybinds;
	private DesktopNotifier desktopNotifier;
	private PestAutomationController pestAutomation;
	private boolean pendingConfigOpen;

	@Override
	public void onInitializeClient() {
		Minecraft minecraft = Minecraft.getInstance();
		configManager = new ConfigManager();
		config = configManager.load();
		inputs = new InputController(minecraft);
		desktopNotifier = new DesktopNotifier();
		controller = new FarmingController(
			inputs,
			desktopNotifier,
			new BackgroundOperationController()
		);
		keybinds = new FarmKeybinds();
		pestAutomation = new PestAutomationController(inputs, controller, desktopNotifier, () -> config);
		registerClientCommands();

		FarmHudRenderer hud = new FarmHudRenderer(controller, pestAutomation, () -> config);
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "status"),
			hud::render
		);

		ClientTickEvents.START_CLIENT_TICK.register(controller::beforeClientTick);
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, blockPos, blockState) -> {
			if (blockState.is(Blocks.NETHER_WART)) {
				controller.recordNetherWartBroken(minecraft, level, player);
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
			ScreenKeyboardEvents.allowKeyPress(screen).register((currentScreen, event) -> {
				if (keybinds.matchesEmergencyStop(event)) {
					keybinds.drainNormalActions();
					pestAutomation.cancel(client);
					controller.emergencyStop(client);
					return false;
				}
				if (controller.isPestCleanup()
					&& (keybinds.matchesToggle(event) || keybinds.matchesPause(event))) {
					keybinds.drainNormalActions();
					pestAutomation.cancel(client);
					controller.stop(client);
					return false;
				}

				if (controller.isRecovering() && keybinds.matchesToggle(event)) {
					keybinds.drainNormalActions();
					controller.stop(client);
					return false;
				}
				if (controller.isRecovering() && keybinds.matchesPause(event)) {
					keybinds.drainNormalActions();
					controller.togglePause(client);
					return false;
				}
				return true;
			})
		);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			pestAutomation.cancel(client);
			if (controller.isSessionRunning()) {
				controller.failSafeStop(client, "disconnected or left the world");
			} else {
				controller.clearInactiveState();
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			pestAutomation.cancel(client);
			controller.shutdown(client);
		});
		LOGGER.info("Aqua Vision is OP initialized; config file is {}", configManager.getConfigPath());
	}

	private void registerClientCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(
				ClientCommands.literal("avop")
					.executes(context -> queueConfigScreen(context.getSource().getClient()))
					.then(ClientCommands.literal("config").executes(context ->
						queueConfigScreen(context.getSource().getClient())
					))
					.then(ClientCommands.literal("pests").executes(context -> {
						for (String line : pestAutomation.getDiagnosticLines(context.getSource().getClient())) {
							context.getSource().sendFeedback(Component.literal("Aqua Vision is OP: " + line));
						}
						return 1;
					}))
					.then(ClientCommands.literal("returntest")
						.executes(context -> {
							context.getSource().sendFeedback(Component.literal(
								"Aqua Vision is OP: use /avop returntest mark, move away manually, then /avop returntest go."
							));
							return 1;
						})
						.then(ClientCommands.literal("mark").executes(context -> {
							PestAutomationController.ReturnTestCommandResult result = pestAutomation.markReturnTest(
								context.getSource().getClient());
							context.getSource().sendFeedback(Component.literal("Aqua Vision is OP: " + result.message() + "."));
							return result.success() ? 1 : 0;
						}))
						.then(ClientCommands.literal("go").executes(context -> {
							PestAutomationController.ReturnTestCommandResult result = pestAutomation.startReturnTest(
								context.getSource().getClient());
							context.getSource().sendFeedback(Component.literal("Aqua Vision is OP: " + result.message() + "."));
							return result.success() ? 1 : 0;
						}))
						.then(ClientCommands.literal("cancel").executes(context -> {
							PestAutomationController.ReturnTestCommandResult result = pestAutomation.cancelReturnTestMarker();
							context.getSource().sendFeedback(Component.literal("Aqua Vision is OP: " + result.message() + "."));
							return result.success() ? 1 : 0;
						}))
					)
					.then(ClientCommands.literal("testalert").executes(context -> {
						FarmConfig currentConfig = configManager.load();
						desktopNotifier.showCropInactivityAlert(
							context.getSource().getClient(),
							currentConfig.noWartTimeoutSeconds
						);
						context.getSource().sendFeedback(
							Component.literal("Aqua Vision is OP: desktop test alert requested.")
						);
						return 1;
					}))
					.then(ClientCommands.literal("testpestalert").executes(context -> {
						FarmConfig currentConfig = configManager.load();
						desktopNotifier.showPestThresholdAlert(
							context.getSource().getClient(), currentConfig.pestCountNotificationThreshold);
						context.getSource().sendFeedback(
							Component.literal("Aqua Vision is OP: configured "
								+ currentConfig.pestCountNotificationThreshold + "-pest desktop test alert requested.")
						);
						return 1;
					}))
					.then(ClientCommands.literal("warp").executes(context -> {
						boolean queued = controller.queueWarpCommandTest(
							context.getSource().getClient(),
							false
						);
						if (queued) {
							context.getSource().sendFeedback(Component.literal(
								"Aqua Vision is OP: opening /warp garden; press Enter manually."
							));
						}
						return queued ? 1 : 0;
					}))
					.then(ClientCommands.literal("warpsend").executes(context -> {
						boolean queued = controller.queueWarpCommandTest(
							context.getSource().getClient(),
							true
						);
						if (queued) {
							context.getSource().sendFeedback(Component.literal(
								"Aqua Vision is OP: opening and automatically submitting /warp garden."
							));
						}
						return queued ? 1 : 0;
					}))
			)
		);
	}

	private int queueConfigScreen(Minecraft minecraft) {
		if (controller.isSessionRunning()) {
			if (minecraft.player != null) {
				minecraft.player.sendSystemMessage(Component.literal(
					"Aqua Vision is OP: stop the farming session before opening configuration."
				));
			}
			return 0;
		}
		pendingConfigOpen = true;
		return 1;
	}

	private void openPendingConfigScreen(Minecraft minecraft) {
		if (!pendingConfigOpen || minecraft.gui.screen() != null) {
			return;
		}
		pendingConfigOpen = false;
		FarmConfig draft = configManager.load();
		minecraft.gui.setScreen(new AquaVisionConfigScreen(null, draft, saved -> {
			if (!configManager.save(saved)) {
				return false;
			}
			config = saved;
			return true;
		}));
	}

	private void onEndClientTick(Minecraft minecraft) {
		try {
			openPendingConfigScreen(minecraft);
			controller.tickWarpCommandTest(minecraft);
			boolean startedThisTick = false;
			if (keybinds.consumeEmergencyStop()) {
				keybinds.drainNormalActions();
				pestAutomation.cancel(minecraft);
				controller.emergencyStop(minecraft);
				return;
			}

			if (keybinds.consumeToggle()) {
				if (pestAutomation.isReturnTestRunning()) {
					pestAutomation.cancel(minecraft);
					return;
				} else if (controller.isSessionRunning()) {
					pestAutomation.cancel(minecraft);
					controller.stop(minecraft);
				} else {
					config = configManager.load();
					startedThisTick = controller.start(minecraft, config);
				}
			}

			if (keybinds.consumePause()) {
				if (pestAutomation.isReturnTestRunning()) {
					pestAutomation.cancel(minecraft);
					return;
				} else if (controller.isPestCleanup()) {
					pestAutomation.cancel(minecraft);
					controller.stop(minecraft);
				} else {
					controller.togglePause(minecraft);
				}
			}

			if (!startedThisTick) {
				controller.tick(minecraft);
				pestAutomation.tick(minecraft);
			}
		} catch (RuntimeException exception) {
			try {
				pestAutomation.cancel(minecraft);
			} catch (RuntimeException restoreException) {
				LOGGER.error("Pest cleanup restoration also failed", restoreException);
			}
			inputs.releaseAll();
			controller.failSafeStop(minecraft, "unexpected client tick error");
			LOGGER.error("Unexpected client tick failure; all controlled inputs were released", exception);
		}
	}
}
