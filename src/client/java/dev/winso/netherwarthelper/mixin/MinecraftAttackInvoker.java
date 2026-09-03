package dev.winso.netherwarthelper.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAttackInvoker {
	@Accessor("missTime")
	int aquaVisionIsOp$getMissTime();

	@Invoker("startAttack")
	boolean aquaVisionIsOp$startAttack();
}
