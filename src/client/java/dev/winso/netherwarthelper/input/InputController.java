package dev.winso.netherwarthelper.input;

import dev.winso.netherwarthelper.controller.FarmingDirection;
import net.minecraft.client.Minecraft;

/** Centralized ownership of every vanilla key state modified by the mod. */
public final class InputController {
	private final Minecraft minecraft;

	public InputController(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public void setAttack(boolean pressed) {
		minecraft.options.keyAttack.setDown(pressed);
	}

	public void setLeft(boolean pressed) {
		minecraft.options.keyLeft.setDown(pressed);
	}

	public void setRight(boolean pressed) {
		minecraft.options.keyRight.setDown(pressed);
	}

	public void setForward(boolean pressed) {
		minecraft.options.keyUp.setDown(pressed);
	}

	public void applyFarming(FarmingDirection direction, boolean holdAttack) {
		setForward(false);
		setLeft(direction == FarmingDirection.LEFT);
		setRight(direction == FarmingDirection.RIGHT);
		setAttack(holdAttack);
	}

	public void applyForwardShift() {
		setAttack(false);
		setLeft(false);
		setRight(false);
		setForward(true);
	}

	public void releaseMovement() {
		setLeft(false);
		setRight(false);
		setForward(false);
	}

	public void releaseAll() {
		setAttack(false);
		releaseMovement();
	}
}
