package dev.winso.netherwarthelper.mixin;

import dev.winso.netherwarthelper.pest.PestCameraFrame;
import dev.winso.netherwarthelper.pest.PestCameraSteering;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPestCameraMixin {
	@Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
	private void aquaVisionIsOp$interpolatePestYaw(float partialTick, CallbackInfoReturnable<Float> callbackInfo) {
		PestCameraFrame frame = PestCameraSteering.frameFor((LocalPlayer) (Object) this);
		if (frame != null) {
			callbackInfo.setReturnValue((float) frame.yawAt(partialTick));
		}
	}

	@Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
	private void aquaVisionIsOp$interpolatePestPitch(float partialTick, CallbackInfoReturnable<Float> callbackInfo) {
		PestCameraFrame frame = PestCameraSteering.frameFor((LocalPlayer) (Object) this);
		if (frame != null) {
			callbackInfo.setReturnValue((float) frame.pitchAt(partialTick));
		}
	}
}
