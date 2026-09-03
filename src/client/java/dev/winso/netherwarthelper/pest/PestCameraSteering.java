package dev.winso.netherwarthelper.pest;

import dev.winso.netherwarthelper.pest.PestNavigationMath.AimAngles;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Camera steering only: this helper never owns movement, attack, or item-use inputs. */
public final class PestCameraSteering {
	private static PestCameraSteering activeSteering;

	private final PestAimSmoother smoother = new PestAimSmoother();
	private LocalPlayer steeringPlayer;
	private PestCameraFrame frame;

	public void reset() {
		smoother.reset();
		steeringPlayer = null;
		frame = null;
		if (activeSteering == this) {
			activeSteering = null;
		}
	}

	public void aimAt(LocalPlayer player, Vec3 destination, double maximumYawStep, double maximumPitchStep) {
		if (player.isPassenger()) {
			reset();
			return;
		}
		if (steeringPlayer != player) {
			reset();
			steeringPlayer = player;
		}
		double previousYaw = player.getYRot();
		double previousPitch = player.getXRot();
		if (frame != null) {
			if (frame.tick() == player.tickCount) {
				if (!frame.matches(player.tickCount, previousYaw, previousPitch)) {
					smoother.reset();
				}
				return;
			}
			if (!frame.canContinueSteeringAt(player.tickCount, previousYaw, previousPitch)) {
				// A locator hover or manual turn must not leave momentum for an unrelated next target.
				smoother.reset();
			}
		}
		AimAngles angles = smoother.aimAt(
			previousYaw, previousPitch,
			player.getX(), player.getEyeY(), player.getZ(),
			destination.x, destination.y, destination.z,
			maximumYawStep, maximumPitchStep
		);
		// absSnapRotationTo also replaces xRotO/yRotO, removing vanilla render interpolation.
		// Set current angles only, and let the normal player tick update head/body rotation.
		player.setYRot((float) angles.yawDegrees());
		player.setXRot((float) angles.pitchDegrees());
		frame = new PestCameraFrame(player.tickCount, previousYaw, previousPitch, player.getYRot(), player.getXRot());
		activeSteering = this;
	}

	/** LocalPlayer bypasses vanilla view interpolation, so the scoped mixin reads this frame instead. */
	public static PestCameraFrame frameFor(LocalPlayer player) {
		PestCameraSteering active = activeSteering;
		if (active == null || active.steeringPlayer != player || player.isPassenger()) {
			return null;
		}
		PestCameraFrame activeFrame = active.frame;
		return activeFrame != null && activeFrame.matches(player.tickCount, player.getYRot(), player.getXRot())
			? activeFrame : null;
	}
}
