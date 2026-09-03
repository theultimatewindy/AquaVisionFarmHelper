package dev.winso.netherwarthelper.hud;

import dev.winso.netherwarthelper.config.FarmConfig;
import dev.winso.netherwarthelper.controller.FarmingController;
import dev.winso.netherwarthelper.pest.PestAutomationController;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FarmHudRenderer {
	private static final String BRAND = "Aqua Vision is OP";
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_TEXT_COLOR = 0xFFB7B7B7;
	private static final int ALERT_TEXT_COLOR = 0xFFFF5555;
	private static final int BACKGROUND_COLOR = 0xB0000000;

	private final FarmingController controller;
	private final PestAutomationController pestAutomation;
	private final Supplier<FarmConfig> configSupplier;

	public FarmHudRenderer(
		FarmingController controller,
		PestAutomationController pestAutomation,
		Supplier<FarmConfig> configSupplier
	) {
		this.controller = controller;
		this.pestAutomation = pestAutomation;
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
			String line = lines.get(index);
			int color = line.startsWith("FAILSAFE:")
				? ALERT_TEXT_COLOR
				: (index == 0 ? TEXT_COLOR : MUTED_TEXT_COLOR);
			graphics.text(
				minecraft.font,
				line,
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
			lines.add(BRAND + ": OFF");
			lines.add(pestAutomation.getDetectionSummary());
			if (config.showDebugInfo) {
				lines.add("State: " + controller.getState());
				lines.add("Pest status: " + pestAutomation.getStatus());
			}
			return lines;
		}

		if (controller.isRecovering()) {
			lines.add(BRAND + ": VOID LOOP");
			lines.add(switch (controller.getVoidRecoveryPhase()) {
				case FALLING -> "Recovery: FALLING";
				case WAITING_FOR_RESPAWN -> "Recovery: RESPAWNING";
				case WAITING_TO_WARP -> "Recovery: SENDING /WARP GARDEN";
				case WAITING_FOR_START -> controller.isPostWarpRestartCountdownActive()
					? "Recovery: RESTARTING IN " + controller.getPostWarpRestartSecondsRemaining() + "s"
					: "Recovery: PREPARING RESTART";
				case INACTIVE, MONITORING -> "Recovery: PREPARING";
			});
			lines.add("Inputs: RELEASED");
		} else if (controller.isPestCleanup()) {
			lines.add(BRAND + ": PEST CLEANUP");
			lines.add(pestAutomation.getDetectionSummary());
			lines.add(pestAutomation.getStatus());
		} else if (controller.isPaused()) {
			lines.add(BRAND + ": PAUSED");
			addFailsafeWarning(lines);
			lines.add("Lane: " + controller.getLane());
		} else {
			lines.add(BRAND + ": ON");
			addFailsafeWarning(lines);
			lines.add("Lane: " + controller.getLane());
			if (controller.getState().isShifting()
				|| controller.getState().name().startsWith("END_")) {
				lines.add("State: SHIFTING");
			} else {
				lines.add("Direction: " + controller.getDirection());
			}
		}

		if (!controller.isPestCleanup()) {
			lines.add(pestAutomation.getDetectionSummary());
		}

		if (config.showDebugInfo) {
			lines.add("State: " + controller.getState());
			if (!controller.isPestCleanup()) {
				lines.add("Pest status: " + pestAutomation.getStatus());
			}
			lines.add(String.format(Locale.ROOT, "Pos: %.3f, %.3f", controller.getDebugX(), controller.getDebugZ()));
			lines.add(String.format(Locale.ROOT, "Delta: %.5f", controller.getLastHorizontalDelta()));
			lines.add(String.format(Locale.ROOT, "Progress: %.5f", controller.getLastExpectedProgress()));
			lines.add("Collision: " + controller.hasHorizontalCollision());
			lines.add("Stuck: " + controller.getStuckCounter() + "/" + config.stuckDetectionTicks);
			lines.add("Transition: " + controller.getTransitionTimer());
			if (controller.isRecovering()) {
				lines.add("Recovery timer: " + controller.getVoidRecoveryElapsedSeconds() + "s / 30s");
			}
			if (controller.isPestCleanup()) {
				lines.add("Pest phase: " + pestAutomation.getPhase());
				lines.add("Pest timer: " + pestAutomation.getElapsedSeconds() + "s");
				if (pestAutomation.getSelectedPlot() >= 0) {
					lines.add("Pest plot: " + pestAutomation.getSelectedPlot());
				}
			}
			if (config.noWartFailsafeEnabled) {
				lines.add(
					"Wart timer: " + controller.getNoWartElapsedSeconds()
						+ "s / " + config.noWartTimeoutSeconds + "s"
				);
			}
		}
		return lines;
	}

	private void addFailsafeWarning(List<String> lines) {
		if (controller.isNoWartAlertActive()) {
			lines.add(
				"FAILSAFE: No crop activity for "
					+ controller.getNoWartElapsedSeconds() + "s"
			);
		}
	}
}
