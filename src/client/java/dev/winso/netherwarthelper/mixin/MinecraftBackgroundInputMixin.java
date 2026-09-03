package dev.winso.netherwarthelper.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.winso.netherwarthelper.background.BackgroundFocusPolicy;
import dev.winso.netherwarthelper.background.BackgroundOperationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.Minecraft.class)
public abstract class MinecraftBackgroundInputMixin {
	@ModifyExpressionValue(
		method = "pauseIfInactive",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/Window;isFocused()Z"
		)
	)
	private boolean aquaVisionIsOp$keepAutomationActive(boolean windowFocused) {
		return BackgroundFocusPolicy.shouldTreatWindowAsFocused(
			windowFocused,
			BackgroundOperationController.isBackgroundInputActive()
		);
	}

	@ModifyExpressionValue(
		method = "handleKeybinds",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"
		)
	)
	private boolean aquaVisionIsOp$keepContinuousAttackActive(boolean mouseGrabbed) {
		return BackgroundFocusPolicy.shouldAllowContinuousAttack(
			mouseGrabbed,
			BackgroundOperationController.isBackgroundInputActive()
		);
	}
}
