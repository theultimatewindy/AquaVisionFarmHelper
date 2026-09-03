package dev.winso.netherwarthelper.controller;

public enum FarmingDirection {
	LEFT,
	RIGHT;

	public boolean isLeft() {
		return this == LEFT;
	}

	public FarmingDirection opposite() {
		return this == LEFT ? RIGHT : LEFT;
	}
}
