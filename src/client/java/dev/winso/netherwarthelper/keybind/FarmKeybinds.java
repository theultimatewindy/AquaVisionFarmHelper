package dev.winso.netherwarthelper.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

public final class FarmKeybinds {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		Identifier.fromNamespaceAndPath("nether_wart_farm_helper", "controls")
	);

	private final KeyMapping toggle = register(
		"key.nether_wart_farm_helper.toggle",
		InputConstants.KEY_F6
	);
	private final KeyMapping pause = register(
		"key.nether_wart_farm_helper.pause",
		InputConstants.KEY_F7
	);
	private final KeyMapping emergencyStop = register(
		"key.nether_wart_farm_helper.emergency_stop",
		InputConstants.KEY_F8
	);

	public boolean consumeToggle() {
		return consumeAll(toggle);
	}

	public boolean consumePause() {
		return consumeAll(pause);
	}

	public boolean consumeEmergencyStop() {
		return consumeAll(emergencyStop);
	}

	public void drainNormalActions() {
		consumeAll(toggle);
		consumeAll(pause);
	}

	public boolean matchesEmergencyStop(KeyEvent event) {
		return emergencyStop.matches(event);
	}

	public boolean matchesToggle(KeyEvent event) {
		return toggle.matches(event);
	}

	public boolean matchesPause(KeyEvent event) {
		return pause.matches(event);
	}

	private static KeyMapping register(String translationKey, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
			translationKey,
			InputConstants.Type.KEYSYM,
			defaultKey,
			CATEGORY
		));
	}

	private static boolean consumeAll(KeyMapping mapping) {
		boolean consumed = false;
		while (mapping.consumeClick()) {
			consumed = true;
		}
		return consumed;
	}
}
