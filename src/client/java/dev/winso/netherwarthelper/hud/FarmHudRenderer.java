package dev.winso.netherwarthelper.hud;

import dev.winso.netherwarthelper.config.FarmConfig;
import dev.winso.netherwarthelper.controller.FarmingController;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FarmHudRenderer {
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_TEXT_COLOR = 0xFFB7B7B7;
	private static final int BACKGROUND_COLOR = 0xB0000000;

	private final FarmingController controller;
	private final Supplier<FarmConfig> configSupplier;

	public FarmHudRenderer(FarmingController controller, Supplier<FarmConfig> configSupplier) {
		this.controller = controller;
		this.configSupplier = configSupplier;
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		FarmConfig config = configSupplier.get();
		if (config == null || !config.showHud) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		List<String> lines = buildLines(config);
		int lineHeight = minecraft.font.lineHeight + 2;
		int contentWidth = lines.stream().mapToInt(minecraft.font::width).max().orElse(0);
		int x = 8;
		int y = 8;
		int padding = 4;
		int width = contentWidth + padding * 2;
		int height = lines.size() * lineHeight + padding * 2 - 2;

		graphics.fill(x, y, x + width, y + height, BACKGROUND_COLOR);
		for (int index = 0; index < lines.size(); index++) {
			int color = index == 0 ? TEXT_COLOR : MUTED_TEXT_COLOR;
			graphics.text(
				minecraft.font,
				lines.get(index),
				x + padding,
				y + padding + index * lineHeight,
				color,
				true
			);
		}
	}

	private List<String> buildLines(FarmConfig config) {
		List<String> lines = new ArrayList<>();
		if (!controller.isSessionRunning()) {
			lines.add("Farm Helper: OFF");
			if (config.showDebugInfo) {
				lines.add("State: " + controller.getState());
			}
			return lines;
		}

		if (controller.isPaused()) {
			lines.add("Farm Helper: PAUSED");
			lines.add("Lane: " + controller.getLane());
		} else {
			lines.add("Farm Helper: ON");
			lines.add("Lane: " + controller.getLane());
			if (controller.getState().isShifting()
				|| controller.getState().name().startsWith("END_")) {
				lines.add("State: SHIFTING");
			} else {
				lines.add("Direction: " + controller.getDirection());
			}
		}

		if (config.showDebugInfo) {
			lines.add("State: " + controller.getState());
			lines.add(String.format(Locale.ROOT, "Pos: %.3f, %.3f", controller.getDebugX(), controller.getDebugZ()));
			lines.add(String.format(Locale.ROOT, "Delta: %.5f", controller.getLastHorizontalDelta()));
			lines.add(String.format(Locale.ROOT, "Progress: %.5f", controller.getLastExpectedProgress()));
			lines.add("Collision: " + controller.hasHorizontalCollision());
			lines.add("Stuck: " + controller.getStuckCounter() + "/" + config.stuckDetectionTicks);
			lines.add("Transition: " + controller.getTransitionTimer());
		}
		return lines;
	}
}
