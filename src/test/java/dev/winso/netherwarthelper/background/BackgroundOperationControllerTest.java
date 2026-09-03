package dev.winso.netherwarthelper.background;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class BackgroundOperationControllerTest {
	private final BackgroundOperationController controller = new BackgroundOperationController();

	@AfterEach
	void clearSharedFlag() {
		controller.end();
	}

	@Test
	void enabledSessionMaintainsAndClearsSharedFlag() {
		controller.begin(true);

		assertTrue(controller.isActive());
		assertTrue(BackgroundOperationController.isBackgroundInputActive());

		controller.maintain();
		assertTrue(BackgroundOperationController.isBackgroundInputActive());

		controller.end();
		assertFalse(controller.isActive());
		assertFalse(BackgroundOperationController.isBackgroundInputActive());
	}

	@Test
	void disabledSessionNeverActivatesSharedFlag() {
		controller.begin(false);
		controller.maintain();

		assertFalse(controller.isActive());
		assertFalse(BackgroundOperationController.isBackgroundInputActive());
	}
}
