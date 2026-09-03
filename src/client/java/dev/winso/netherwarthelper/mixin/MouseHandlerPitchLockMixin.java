package dev.winso.netherwarthelper.mixin;

import dev.winso.netherwarthelper.orientation.PitchLockRuntime;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerPitchLockMixin {
	@Inject(method = "handleAccumulatedMovement", at = @At("RETURN"))
	private void aquaVisionIsOp$restorePitchAfterMouseInput(CallbackInfo callbackInfo) {
		PitchLockRuntime.applyAfterMouseInput();
	}
}
