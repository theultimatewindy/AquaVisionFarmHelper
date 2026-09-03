package dev.winso.netherwarthelper.mixin;

import dev.winso.netherwarthelper.pest.PestParticleTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelParticleMixin {
	@Inject(method = "doAddParticle", at = @At("HEAD"))
	private void aquaVisionIsOp$captureVacuumLocatorParticle(
		ParticleOptions options,
		boolean force,
		boolean decreased,
		double x,
		double y,
		double z,
		double velocityX,
		double velocityY,
		double velocityZ,
		CallbackInfo callbackInfo
	) {
		if (options.getType() == ParticleTypes.ANGRY_VILLAGER) {
			PestParticleTracker.recordAngryVillager(x, y, z);
		}
	}
}
