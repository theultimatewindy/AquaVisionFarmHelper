package dev.winso.netherwarthelper.background;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BackgroundFocusPolicyTest {
	@Test
	void preservesRealFocus() {
		assertTrue(BackgroundFocusPolicy.shouldTreatWindowAsFocused(true, false));
		assertTrue(BackgroundFocusPolicy.shouldTreatWindowAsFocused(true, true));
	}

	@Test
	void overridesLostFocusOnlyDuringBackgroundAutomation() {
		assertTrue(BackgroundFocusPolicy.shouldTreatWindowAsFocused(false, true));
		assertFalse(BackgroundFocusPolicy.shouldTreatWindowAsFocused(false, false));
	}

	@Test
	void allowsContinuousAttackWithoutMouseCaptureOnlyDuringBackgroundAutomation() {
		assertTrue(BackgroundFocusPolicy.shouldAllowContinuousAttack(true, false));
		assertTrue(BackgroundFocusPolicy.shouldAllowContinuousAttack(true, true));
		assertTrue(BackgroundFocusPolicy.shouldAllowContinuousAttack(false, true));
		assertFalse(BackgroundFocusPolicy.shouldAllowContinuousAttack(false, false));
	}
}
