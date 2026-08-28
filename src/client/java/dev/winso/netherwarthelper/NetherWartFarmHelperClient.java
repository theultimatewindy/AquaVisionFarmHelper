package dev.winso.netherwarthelper;

import dev.winso.netherwarthelper.config.ConfigManager;
import dev.winso.netherwarthelper.config.FarmConfig;
import dev.winso.netherwarthelper.controller.FarmingController;
import dev.winso.netherwarthelper.hud.FarmHudRenderer;
import dev.winso.netherwarthelper.input.InputController;
import dev.winso.netherwarthelper.keybind.FarmKeybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetherWartFarmHelperClient implements ClientModInitializer {
	public static final String MOD_ID = "nether_wart_farm_helper";
	private static final Logger LOGGER = LoggerFactory.getLogger("NetherWartFarmHelper");

	private ConfigManager configManager;
	private FarmConfig config;
	private InputController inputs;
	private FarmingController controller;
	private FarmKeybinds keybinds;

	@Override
	public void onInitializeClient() {
		Minecraft minecraft = Minecraft.getInstance();
		configManager = new ConfigManager();
		config = configManager.load();
		inputs = new InputController(minecraft);
		controller = new FarmingController(inputs);
		keybinds = new FarmKeybinds();

		FarmHudRenderer hud = new FarmHudRenderer(controller, () -> config);
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "status"),
			hud::render
		);

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
			ScreenKeyboardEvents.allowKeyPress(screen).register((currentScreen, event) -> {
				if (!keybinds.matchesEmergencyStop(event)) {
					return true;
				}

				keybinds.drainNormalActions();
				controller.emergencyStop(client);
				return false;
			})
		);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			if (controller.isSessionRunning()) {
				controller.failSafeStop(client, "disconnected or left the world");
			} else {
				inputs.releaseAll();
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> controller.shutdown());
		LOGGER.info("Initialized; config file is {}", configManager.getConfigPath());
	}

	private void onEndClientTick(Minecraft minecraft) {
		try {
			boolean startedThisTick = false;
			if (keybinds.consumeEmergencyStop()) {
				keybinds.drainNormalActions();
				controller.emergencyStop(minecraft);
				return;
			}

			if (keybinds.consumeToggle()) {
				if (controller.isSessionRunning()) {
					controller.stop(minecraft);
				} else {
					config = configManager.load();
					startedThisTick = controller.start(minecraft, config);
				}
			}

			if (keybinds.consumePause()) {
				controller.togglePause(minecraft);
			}

			if (!startedThisTick) {
				controller.tick(minecraft);
			}
		} catch (RuntimeException exception) {
			inputs.releaseAll();
			controller.failSafeStop(minecraft, "unexpected client tick error");
			LOGGER.error("Unexpected client tick failure; all controlled inputs were released", exception);
		}
	}
}
