package dev.winso.netherwarthelper.input;

import dev.winso.netherwarthelper.controller.FarmingDirection;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Centralized ownership of every vanilla key state modified by the mod. */
public final class InputController {
	private final Minecraft minecraft;
	private boolean attackDown;
	private boolean leftDown;
	private boolean rightDown;
	private boolean forwardDown;
	private boolean backwardDown;
	private boolean jumpDown;
	private boolean shiftDown;
	// Sprint is deliberately not owned: vanilla toggle sprint and AutoSprint mods remain authoritative.
	private boolean useDown;

	public InputController(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public void setAttack(boolean pressed) {
		attackDown = pressed;
		setManagedKey(minecraft.options.keyAttack, pressed, minecraft.options.toggleAttack().get());
	}

	public void setLeft(boolean pressed) {
		leftDown = pressed;
		setManagedKey(minecraft.options.keyLeft, pressed, false);
	}

	public void setRight(boolean pressed) {
		rightDown = pressed;
		setManagedKey(minecraft.options.keyRight, pressed, false);
	}

	public void setForward(boolean pressed) {
		forwardDown = pressed;
		setManagedKey(minecraft.options.keyUp, pressed, false);
	}

	public void setBackward(boolean pressed) {
		backwardDown = pressed;
		setManagedKey(minecraft.options.keyDown, pressed, false);
	}

	public void setJump(boolean pressed) {
		jumpDown = pressed;
		setManagedKey(minecraft.options.keyJump, pressed, false);
	}

	public boolean isJumpRequested() {
		return jumpDown;
	}

	public void setShift(boolean pressed) {
		shiftDown = pressed;
		setManagedKey(minecraft.options.keyShift, pressed, minecraft.options.toggleCrouch().get());
	}

	public boolean isShiftRequested() {
		return shiftDown;
	}

	public void setUse(boolean pressed) {
		useDown = pressed;
		setManagedKey(minecraft.options.keyUse, pressed, minecraft.options.toggleUse().get());
	}

	/** Reasserts the mod-owned state after GLFW clears physical keys on focus loss. */
	public void reapplyDesired() {
		setManagedKey(minecraft.options.keyAttack, attackDown, minecraft.options.toggleAttack().get());
		setManagedKey(minecraft.options.keyLeft, leftDown, false);
		setManagedKey(minecraft.options.keyRight, rightDown, false);
		setManagedKey(minecraft.options.keyUp, forwardDown, false);
		setManagedKey(minecraft.options.keyDown, backwardDown, false);
		setManagedKey(minecraft.options.keyJump, jumpDown, false);
		setManagedKey(minecraft.options.keyShift, shiftDown, minecraft.options.toggleCrouch().get());
		setManagedKey(minecraft.options.keyUse, useDown, minecraft.options.toggleUse().get());
	}

	private static void setManagedKey(KeyMapping key, boolean desiredDown, boolean toggleMode) {
		switch (ManagedKeyState.reconcile(key.isDown(), desiredDown, toggleMode)) {
			case PRESS_TRUE -> key.setDown(true);
			case RELEASE_FALSE -> key.setDown(false);
			case NONE -> {
			}
		}
	}

	public void applyFarming(FarmingDirection direction, boolean holdAttack) {
		setForward(false);
		setBackward(false);
		setLeft(direction == FarmingDirection.LEFT);
		setRight(direction == FarmingDirection.RIGHT);
		setAttack(holdAttack);
	}

	public void applyForwardShift() {
		setAttack(false);
		setBackward(false);
		setLeft(false);
		setRight(false);
		setForward(true);
	}

	public void releaseMovement() {
		setLeft(false);
		setRight(false);
		setForward(false);
		setBackward(false);
		setJump(false);
		setShift(false);
	}

	public void releaseAll() {
		setAttack(false);
		setUse(false);
		releaseMovement();
	}
}
