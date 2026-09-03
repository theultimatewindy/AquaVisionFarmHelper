package dev.winso.netherwarthelper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

final class FarmConfigMigrationTest {
	private static final String VERSION_1_1_CONFIG = """
		{
		  "startingDirection": "LEFT",
		  "forwardShiftTicks": 10,
		  "transitionSettleTicks": 2,
		  "stuckDetectionTicks": 8,
		  "minimumMovementDelta": 0.003,
		  "laneStartGraceTicks": 10,
		  "holdAttack": true,
		  "showHud": true,
		  "showDebugInfo": false,
		  "pauseWhenScreenOpen": true,
		  "orientationGuardEnabled": true,
		  "orientationToleranceDegrees": 12.0,
		  "pausePositionTolerance": 0.35,
		  "noWartFailsafeEnabled": true,
		  "noWartTimeoutSeconds": 10,
		  "noWartDesktopNotification": true
		}
		""";

	@Test
	void olderConfigKeepsSafeUpgradeDefaults() {
		FarmConfig config = new Gson().fromJson(VERSION_1_1_CONFIG, FarmConfig.class);
		assertTrue(config.migrateFrom(0));
		config.validate();

		assertEquals(FarmConfig.CURRENT_CONFIG_VERSION, config.configVersion);
		assertEquals(3, config.noWartTimeoutSeconds);
		assertTrue(config.alignYawOnStart);
		assertEquals(90.0, config.startYawDegrees);
		assertTrue(config.lockPitchWhileRunning);
		assertEquals(0.0, config.fixedPitchDegrees);
		assertTrue(config.runInBackground);
		assertTrue(config.voidLoopEnabled);
		assertEquals(2, config.forwardStuckDetectionTicks);
		assertTrue(config.sessionStateDesktopNotification);
		assertEquals(6.0, config.voidFallTriggerDistance);
		assertEquals(5.0, config.respawnStartTolerance);
		assertEquals(80, config.respawnRestartDelayTicks);
		assertFalse(config.pestAutomationEnabled);
		assertEquals(3, config.pestActivationThreshold);
		assertTrue(config.pestMoveVacuumFromInventory);
		assertEquals(9, config.pestVacuumHotbarSlot);
		assertTrue(config.pestLocatorEnabled);
		assertTrue(config.pestCountDesktopNotification);
		assertEquals(3, config.pestCountNotificationThreshold);
	}

	@Test
	void missingLegacyFailsafeTimeoutUsesThreeSecondDefault() {
		FarmConfig config = new Gson().fromJson("{\"startingDirection\":\"LEFT\"}", FarmConfig.class);
		assertTrue(config.migrateFrom(0));
		config.validate();

		assertEquals(3, config.noWartTimeoutSeconds);
	}

	@Test
	void versionThreeConfigPreservesAUserSelectedTimeoutWhileAddingVoidLoop() {
		FarmConfig config = new Gson().fromJson(
			"{\"configVersion\":3,\"noWartTimeoutSeconds\":10}",
			FarmConfig.class
		);

		assertTrue(config.migrateFrom(3));
		config.validate();
		assertEquals(10, config.noWartTimeoutSeconds);
		assertEquals(FarmConfig.CURRENT_CONFIG_VERSION, config.configVersion);
		assertTrue(config.voidLoopEnabled);
	}

	@Test
	void versionFourConfigAddsMovementAndSessionAlertDefaults() {
		FarmConfig config = new Gson().fromJson("{\"configVersion\":4}", FarmConfig.class);

		assertTrue(config.migrateFrom(4));
		config.validate();
		assertEquals(FarmConfig.CURRENT_CONFIG_VERSION, config.configVersion);
		assertEquals(2, config.forwardStuckDetectionTicks);
		assertTrue(config.sessionStateDesktopNotification);
	}

	@Test
	void versionFiveConfigUpgradesPostWarpRestartToFourSeconds() {
		FarmConfig config = new Gson().fromJson(
			"{\"configVersion\":5,\"respawnRestartDelayTicks\":10}",
			FarmConfig.class
		);

		assertTrue(config.migrateFrom(5));
		config.validate();
		assertEquals(FarmConfig.CURRENT_CONFIG_VERSION, config.configVersion);
		assertEquals(80, config.respawnRestartDelayTicks);
	}

	@Test
	void currentVersionDoesNotMigrate() {
		FarmConfig config = new FarmConfig();
		assertFalse(config.migrateFrom(FarmConfig.CURRENT_CONFIG_VERSION));
	}

	@Test
	void versionSevenDefaultTimingsUpgradeWithoutChangingPestSettings() {
		FarmConfig config = new Gson().fromJson("""
			{
			  "configVersion": 7,
			  "forwardShiftTicks": 10,
			  "forwardStuckDetectionTicks": 3,
			  "transitionSettleTicks": 2,
			  "stuckDetectionTicks": 8,
			  "laneStartGraceTicks": 10,
			  "pestAutomationEnabled": true,
			  "pestActivationThreshold": 4,
			  "pestCruiseHeight": 100.0,
			  "noWartTimeoutSeconds": 5
			}
			""", FarmConfig.class);

		assertTrue(config.migrateFrom(7));
		config.validate();
		assertEquals(FarmConfig.CURRENT_CONFIG_VERSION, config.configVersion);
		assertEquals(2, config.forwardShiftTicks);
		assertEquals(2, config.forwardStuckDetectionTicks);
		assertEquals(0, config.transitionSettleTicks);
		assertEquals(2, config.stuckDetectionTicks);
		assertEquals(2, config.laneStartGraceTicks);
		assertTrue(config.pestAutomationEnabled);
		assertEquals(4, config.pestActivationThreshold);
		assertEquals(100.0, config.pestCruiseHeight);
		assertEquals(5, config.noWartTimeoutSeconds);
		assertFalse(config.migrateFrom(config.configVersion));
	}

	@Test
	void versionSevenCustomizedTimingsArePreserved() {
		FarmConfig config = new Gson().fromJson("""
			{
			  "configVersion": 7,
			  "forwardShiftTicks": 5,
			  "forwardStuckDetectionTicks": 6,
			  "transitionSettleTicks": 4,
			  "stuckDetectionTicks": 12,
			  "laneStartGraceTicks": 7
			}
			""", FarmConfig.class);

		assertTrue(config.migrateFrom(7));
		config.validate();
		assertEquals(5, config.forwardShiftTicks);
		assertEquals(6, config.forwardStuckDetectionTicks);
		assertEquals(4, config.transitionSettleTicks);
		assertEquals(12, config.stuckDetectionTicks);
		assertEquals(7, config.laneStartGraceTicks);
	}

	@Test
	void versionEightKeepsTimingsEvenWhenTheyMatchOldDefaults() {
		FarmConfig config = new FarmConfig();
		config.forwardShiftTicks = 10;
		config.forwardStuckDetectionTicks = 3;
		config.transitionSettleTicks = 2;
		config.stuckDetectionTicks = 8;
		config.laneStartGraceTicks = 10;

		assertFalse(config.migrateFrom(FarmConfig.CURRENT_CONFIG_VERSION));
		config.validate();
		assertEquals(10, config.forwardShiftTicks);
		assertEquals(3, config.forwardStuckDetectionTicks);
		assertEquals(2, config.transitionSettleTicks);
		assertEquals(8, config.stuckDetectionTicks);
		assertEquals(10, config.laneStartGraceTicks);
	}

	@Test
	void versionEightAddsTheRequestedPestDesktopAlertWithoutChangingAutomation() {
		FarmConfig config = new Gson().fromJson("""
			{
			  "configVersion": 8,
			  "pestAutomationEnabled": false
			}
			""", FarmConfig.class);

		assertTrue(config.migrateFrom(8));
		assertTrue(config.pestCountDesktopNotification);
		assertFalse(config.pestAutomationEnabled);
		assertEquals(FarmConfig.CURRENT_CONFIG_VERSION, config.configVersion);
	}

	@Test
	void versionNineAddsASeparateAlertThresholdWithoutChangingExistingChoices() {
		FarmConfig config = new Gson().fromJson("""
			{
			  "configVersion": 9,
			  "pestAutomationEnabled": true,
			  "pestCountDesktopNotification": false,
			  "pestActivationThreshold": 6
			}
			""", FarmConfig.class);

		assertTrue(config.migrateFrom(9));
		config.validate();
		assertTrue(config.pestAutomationEnabled);
		assertFalse(config.pestCountDesktopNotification);
		assertEquals(6, config.pestActivationThreshold);
		assertEquals(3, config.pestCountNotificationThreshold);
		assertEquals(FarmConfig.CURRENT_CONFIG_VERSION, config.configVersion);
	}

	@Test
	void validationRepairsNonFiniteAndOutOfRangeNewValues() {
		FarmConfig config = new FarmConfig();
		config.startYawDegrees = Double.NaN;
		config.fixedPitchDegrees = Double.NaN;
		config.noWartTimeoutSeconds = Integer.MAX_VALUE;
		config.forwardStuckDetectionTicks = Integer.MAX_VALUE;
		config.voidFallTriggerDistance = Double.NaN;
		config.respawnStartTolerance = Double.NaN;
		config.respawnRestartDelayTicks = Integer.MAX_VALUE;
		config.pestActivationThreshold = Integer.MAX_VALUE;
		config.pestCountNotificationThreshold = Integer.MAX_VALUE;
		config.pestVacuumHotbarSlot = Integer.MAX_VALUE;
		config.pestSearchTimeoutSeconds = Integer.MAX_VALUE;
		config.pestCleanupTimeoutSeconds = Integer.MAX_VALUE;
		config.pestCruiseHeight = Double.NaN;
		config.validate();

		assertEquals(90.0, config.startYawDegrees);
		assertEquals(0.0, config.fixedPitchDegrees);
		assertEquals(3600, config.noWartTimeoutSeconds);
		assertEquals(20, config.forwardStuckDetectionTicks);
		assertEquals(6.0, config.voidFallTriggerDistance);
		assertEquals(5.0, config.respawnStartTolerance);
		assertEquals(200, config.respawnRestartDelayTicks);
		assertEquals(8, config.pestActivationThreshold);
		assertEquals(8, config.pestCountNotificationThreshold);
		assertEquals(9, config.pestVacuumHotbarSlot);
		assertEquals(120, config.pestSearchTimeoutSeconds);
		assertEquals(900, config.pestCleanupTimeoutSeconds);
		assertEquals(90.0, config.pestCruiseHeight);

		config.startYawDegrees = 500.0;
		config.fixedPitchDegrees = 500.0;
		config.noWartTimeoutSeconds = -1;
		config.forwardStuckDetectionTicks = -1;
		config.voidFallTriggerDistance = -1.0;
		config.respawnStartTolerance = 500.0;
		config.respawnRestartDelayTicks = -1;
		config.pestActivationThreshold = -1;
		config.pestCountNotificationThreshold = -1;
		config.pestVacuumHotbarSlot = -1;
		config.pestSearchTimeoutSeconds = -1;
		config.pestCleanupTimeoutSeconds = -1;
		config.pestCruiseHeight = -1.0;
		config.pausePositionTolerance = -1.0;
		config.validate();

		assertEquals(180.0, config.startYawDegrees);
		assertEquals(90.0, config.fixedPitchDegrees);
		assertEquals(1, config.noWartTimeoutSeconds);
		assertEquals(1, config.forwardStuckDetectionTicks);
		assertEquals(2.0, config.voidFallTriggerDistance);
		assertEquals(64.0, config.respawnStartTolerance);
		assertEquals(1, config.respawnRestartDelayTicks);
		assertEquals(1, config.pestActivationThreshold);
		assertEquals(1, config.pestCountNotificationThreshold);
		assertEquals(1, config.pestVacuumHotbarSlot);
		assertEquals(5, config.pestSearchTimeoutSeconds);
		assertEquals(30, config.pestCleanupTimeoutSeconds);
		assertEquals(60.0, config.pestCruiseHeight);
		assertEquals(0.15, config.pausePositionTolerance);

		config.fixedPitchDegrees = -500.0;
		config.validate();
		assertEquals(-90.0, config.fixedPitchDegrees);
	}
}
