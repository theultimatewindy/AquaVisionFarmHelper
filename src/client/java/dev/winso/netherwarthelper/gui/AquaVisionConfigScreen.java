package dev.winso.netherwarthelper.gui;

import dev.winso.netherwarthelper.config.FarmConfig;
import dev.winso.netherwarthelper.controller.FarmingDirection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A small, dependency-free configuration screen opened by the /avop config command. */
public final class AquaVisionConfigScreen extends Screen {
	private static final Logger LOGGER = LoggerFactory.getLogger("AquaVisionOP/ConfigScreen");
	private static final Component TITLE = Component.literal("Aqua Vision is OP Configuration");
	private static final Component SAVE_FAILED = Component.literal(
		"The configuration could not be saved. Check latest.log and try again."
	).withStyle(ChatFormatting.RED);
	private static final Component COMPACT_SAVE_FAILED = Component.literal(
		"Save failed - check latest.log"
	).withStyle(ChatFormatting.RED);

	private static final int MAX_CONTENT_WIDTH = 360;
	private static final int HORIZONTAL_MARGIN = 12;
	private static final int TAB_GAP = 4;
	private static final int OPTION_WIDTH = 300;
	private static final int BUTTON_HEIGHT = Button.DEFAULT_HEIGHT;
	private static final int PAGE_ROW_COUNT = 6;

	private final Screen parent;
	private final FarmConfig draft;
	private final Predicate<FarmConfig> saveHandler;

	private ConfigPage currentPage = ConfigPage.GENERAL;
	private boolean saveFailed;

	public AquaVisionConfigScreen(
		Screen parent,
		FarmConfig draft,
		Predicate<FarmConfig> saveHandler
	) {
		super(TITLE);
		this.parent = parent;
		this.draft = Objects.requireNonNull(draft, "draft");
		this.saveHandler = Objects.requireNonNull(saveHandler, "saveHandler");
	}

	@Override
	protected void init() {
		boolean compact = height < 235;
		int contentWidth = Math.min(MAX_CONTENT_WIDTH, Math.max(120, width - HORIZONTAL_MARGIN * 2));
		int contentLeft = (width - contentWidth) / 2;

		addCenteredText(saveFailed && compact ? COMPACT_SAVE_FAILED : TITLE, compact ? 6 : 14, contentWidth);
		addPageTabs(contentLeft, compact ? 24 : 40, contentWidth);

		if (!compact) {
			MultiLineTextWidget description = new MultiLineTextWidget(
				contentLeft,
				68,
				saveFailed ? SAVE_FAILED : currentPage.description,
				font
			).setMaxWidth(contentWidth).setMaxRows(2);
			addRenderableOnly(description);
		}

		int footerY = height - 24;
		int optionTop = compact ? 50 : 90;
		int availableForGaps = footerY - 4 - optionTop - BUTTON_HEIGHT;
		int rowStep = Math.max(
			BUTTON_HEIGHT,
			Math.min(24, Math.max(0, availableForGaps) / (PAGE_ROW_COUNT - 1))
		);
		int optionWidth = Math.min(OPTION_WIDTH, contentWidth);
		int optionX = (width - optionWidth) / 2;

		switch (currentPage) {
			case GENERAL -> addGeneralOptions(optionX, optionTop, optionWidth, rowStep);
			case SAFETY -> addSafetyOptions(optionX, optionTop, optionWidth, rowStep);
			case VOID_LOOP -> addVoidLoopOptions(optionX, optionTop, optionWidth, rowStep);
			case PESTS -> addPestOptions(optionX, optionTop, optionWidth, rowStep);
		}

		addFooter(contentWidth, footerY);
	}

	private void addPageTabs(int x, int y, int totalWidth) {
		ConfigPage[] pages = ConfigPage.values();
		int tabWidth = Math.max(1, (totalWidth - TAB_GAP * (pages.length - 1)) / pages.length);

		for (int index = 0; index < pages.length; index++) {
			ConfigPage page = pages[index];
			Button tab = Button.builder(page.label, ignored -> {
				currentPage = page;
				rebuildWidgets();
			}).bounds(x + index * (tabWidth + TAB_GAP), y, tabWidth, BUTTON_HEIGHT).build();
			tab.active = page != currentPage;
			tab.setTooltip(Tooltip.create(page.description));
			addRenderableWidget(tab);
		}
	}

	private void addGeneralOptions(int x, int y, int width, int rowStep) {
		FarmingDirection direction = draft.startingDirection == FarmingDirection.RIGHT
			? FarmingDirection.RIGHT
			: FarmingDirection.LEFT;
		CycleButton<FarmingDirection> directionButton = CycleButton
			.<FarmingDirection>builder(AquaVisionConfigScreen::directionLabel, direction)
			.withValues(FarmingDirection.LEFT, FarmingDirection.RIGHT)
			.create(
				x,
				y,
				width,
				BUTTON_HEIGHT,
				Component.literal("Starting direction"),
				(ignored, value) -> {
					draft.startingDirection = value;
					saveFailed = false;
				}
			);
		directionButton.setTooltip(Tooltip.create(Component.literal(
			"Choose whether a new F6 session begins by moving left or right."
		)));
		addRenderableWidget(directionButton);

		addBooleanOption(
			x, y + rowStep, width, "Hold attack while farming", draft.holdAttack,
			"Continuously uses the held farming tool while the movement pattern runs.",
			value -> draft.holdAttack = value
		);
		addBooleanOption(
			x, y + rowStep * 2, width, "Run in background", draft.runInBackground,
			"Keeps controlled movement and tool use active while Minecraft is Alt-Tabbed.",
			value -> draft.runInBackground = value
		);
		addBooleanOption(
			x, y + rowStep * 3, width, "Show status HUD", draft.showHud,
			"Shows the current farming, recovery, and failsafe state on screen.",
			value -> draft.showHud = value
		);
		addBooleanOption(
			x, y + rowStep * 4, width, "Show debug details", draft.showDebugInfo,
			"Adds diagnostic movement and state details to the status HUD.",
			value -> draft.showDebugInfo = value
		);
	}

	private void addSafetyOptions(int x, int y, int width, int rowStep) {
		addBooleanOption(
			x, y, width, "Crop inactivity failsafe", draft.noWartFailsafeEnabled,
			"Alerts when no target crop has been broken for the configured timeout.",
			value -> draft.noWartFailsafeEnabled = value
		);
		addBooleanOption(
			x, y + rowStep, width, "Crop inactivity desktop alert", draft.noWartDesktopNotification,
			"Sends a Windows notification when the crop inactivity failsafe activates.",
			value -> draft.noWartDesktopNotification = value
		);
		addBooleanOption(
			x, y + rowStep * 2, width, "Session-state desktop alerts", draft.sessionStateDesktopNotification,
			"Sends a Windows notification after an unexpected automatic pause or stop.",
			value -> draft.sessionStateDesktopNotification = value
		);
		addBooleanOption(
			x, y + rowStep * 3, width, "Orientation guard", draft.orientationGuardEnabled,
			"Pauses if the camera yaw moves too far away from the session's starting yaw.",
			value -> draft.orientationGuardEnabled = value
		);
		addBooleanOption(
			x, y + rowStep * 4, width, "Lock pitch while running", draft.lockPitchWhileRunning,
			"Keeps the vertical camera angle fixed throughout an active farming session.",
			value -> draft.lockPitchWhileRunning = value
		);
	}

	private void addVoidLoopOptions(int x, int y, int width, int rowStep) {
		addBooleanOption(
			x, y, width, "Automatic void loop", draft.voidLoopEnabled,
			"Detects the planned void fall, submits /warp garden, and restarts farming.",
			value -> draft.voidLoopEnabled = value
		);

		MultiLineTextWidget note = new MultiLineTextWidget(
			x,
			y + rowStep + 4,
			Component.literal(
				"Fall distance, warp timing, and restart timing remain in the JSON file so the tested loop is not changed accidentally."
			),
			font
		).setMaxWidth(width).setMaxRows(4);
		addRenderableOnly(note);
	}

	private void addPestOptions(int x, int y, int width, int rowStep) {
		addBooleanOption(
			x, y, width, "Pest automation", draft.pestAutomationEnabled,
			"Pauses farming to handle Garden pests, then returns to the interrupted lane.",
			value -> draft.pestAutomationEnabled = value
		);

		int threshold = clamp(draft.pestActivationThreshold, 1, 8);
		CycleButton<Integer> thresholdButton = CycleButton
			.<Integer>builder(value -> Component.literal(Integer.toString(value)), threshold)
			.withValues(1, 2, 3, 4, 5, 6, 7, 8)
			.create(
				x,
				y + rowStep,
				width,
				BUTTON_HEIGHT,
				Component.literal("Pests required to activate"),
				(ignored, value) -> {
					draft.pestActivationThreshold = value;
					saveFailed = false;
				}
			);
		thresholdButton.setTooltip(Tooltip.create(Component.literal(
			"Starts pest handling when the Garden reports at least this many pests."
		)));
		addRenderableWidget(thresholdButton);

		int alertGap = Button.DEFAULT_SPACING;
		int alertWidth = Math.max(1, (width - alertGap) / 2);
		addBooleanOption(
			x, y + rowStep * 2, alertWidth, "Pest alert", draft.pestCountDesktopNotification,
			"Shows one desktop alert when the Garden reaches the configured alert count.",
			value -> draft.pestCountDesktopNotification = value
		);
		int alertThreshold = clamp(draft.pestCountNotificationThreshold, 1, 8);
		CycleButton<Integer> alertThresholdButton = CycleButton
			.<Integer>builder(value -> Component.literal(Integer.toString(value)), alertThreshold)
			.withValues(1, 2, 3, 4, 5, 6, 7, 8)
			.create(
				x + alertWidth + alertGap,
				y + rowStep * 2,
				width - alertWidth - alertGap,
				BUTTON_HEIGHT,
				Component.literal("Alert at"),
				(ignored, value) -> {
					draft.pestCountNotificationThreshold = value;
					saveFailed = false;
				}
			);
		alertThresholdButton.setTooltip(Tooltip.create(Component.literal(
			"Alert once at this pest count or higher; a lower fresh count rearms it."
		)));
		addRenderableWidget(alertThresholdButton);

		addBooleanOption(
			x, y + rowStep * 3, width, "Move vacuum from inventory", draft.pestMoveVacuumFromInventory,
			"Moves a detected vacuum into the selected hotbar slot when necessary.",
			value -> draft.pestMoveVacuumFromInventory = value
		);

		int hotbarSlot = clamp(draft.pestVacuumHotbarSlot, 1, 9);
		CycleButton<Integer> slotButton = CycleButton
			.<Integer>builder(value -> Component.literal(Integer.toString(value)), hotbarSlot)
			.withValues(1, 2, 3, 4, 5, 6, 7, 8, 9)
			.create(
				x,
				y + rowStep * 4,
				width,
				BUTTON_HEIGHT,
				Component.literal("Vacuum hotbar slot"),
				(ignored, value) -> {
					draft.pestVacuumHotbarSlot = value;
					saveFailed = false;
				}
			);
		slotButton.setTooltip(Tooltip.create(Component.literal(
			"The visible hotbar slot used for the vacuum. Slots are numbered 1 through 9."
		)));
		addRenderableWidget(slotButton);

		addBooleanOption(
			x, y + rowStep * 5, width, "Pest locator support", draft.pestLocatorEnabled,
			"Uses supported Garden locator feedback to search for active pests.",
			value -> draft.pestLocatorEnabled = value
		);
	}

	private void addBooleanOption(
		int x,
		int y,
		int width,
		String label,
		boolean value,
		String tooltip,
		Consumer<Boolean> setter
	) {
		CycleButton<Boolean> button = CycleButton.onOffBuilder(value).create(
			x,
			y,
			width,
			BUTTON_HEIGHT,
			Component.literal(label),
			(ignored, selected) -> {
				setter.accept(selected);
				saveFailed = false;
			}
		);
		button.setTooltip(Tooltip.create(Component.literal(tooltip)));
		addRenderableWidget(button);
	}

	private void addFooter(int contentWidth, int y) {
		int gap = Button.DEFAULT_SPACING;
		int buttonWidth = Math.min(100, Math.max(1, (contentWidth - gap) / 2));
		int footerWidth = buttonWidth * 2 + gap;
		int x = (width - footerWidth) / 2;

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, ignored -> saveAndClose())
			.bounds(x, y, buttonWidth, BUTTON_HEIGHT)
			.build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> closeToParent())
			.bounds(x + buttonWidth + gap, y, buttonWidth, BUTTON_HEIGHT)
			.build());
	}

	private void addCenteredText(Component text, int y, int maxWidth) {
		StringWidget widget = new StringWidget(text, font).setMaxWidth(Math.max(1, maxWidth));
		widget.setX((width - widget.getWidth()) / 2);
		widget.setY(y);
		addRenderableOnly(widget);
	}

	private void saveAndClose() {
		draft.validate();
		try {
			if (saveHandler.test(draft)) {
				closeToParent();
				return;
			}
		} catch (RuntimeException exception) {
			LOGGER.error("Unexpected configuration save failure", exception);
		}

		saveFailed = true;
		rebuildWidgets();
	}

	@Override
	public void onClose() {
		closeToParent();
	}

	private void closeToParent() {
		minecraft.gui.setScreen(parent);
	}

	private static Component directionLabel(FarmingDirection direction) {
		return Component.literal(direction == FarmingDirection.RIGHT ? "Right" : "Left");
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private enum ConfigPage {
		GENERAL(
			Component.literal("General"),
			Component.literal("Basic farming controls and on-screen display settings.")
		),
		SAFETY(
			Component.literal("Safety"),
			Component.literal("Failsafes, desktop notifications, and camera protection.")
		),
		VOID_LOOP(
			Component.literal("Void Loop"),
			Component.literal("Automatic return to the Garden after the planned void fall.")
		),
		PESTS(
			Component.literal("Pests"),
			Component.literal("Configure when pest handling starts and how the vacuum is prepared.")
		);

		private final Component label;
		private final Component description;

		ConfigPage(Component label, Component description) {
			this.label = label;
			this.description = description;
		}
	}
}
