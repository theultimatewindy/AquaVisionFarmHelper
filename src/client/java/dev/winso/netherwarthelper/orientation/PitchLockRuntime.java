package dev.winso.netherwarthelper.orientation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Applies the configured pitch to the same player that started the current session. */
public final class PitchLockRuntime {
	private static final PitchLockState STATE = new PitchLockState();
	private static LocalPlayer sessionPlayer;

	public void begin(boolean enabled, LocalPlayer player, float pitch) {
		end();
		if (!enabled || player == null) {
			return;
		}

		sessionPlayer = player;
		STATE.begin(true, pitch);
	}

	public void apply(LocalPlayer player) {
		applyToSessionPlayer(player);
	}

	public void end() {
		STATE.end();
		sessionPlayer = null;
	}

	public static void applyAfterMouseInput() {
		applyToSessionPlayer(Minecraft.getInstance().player);
	}

	private static void applyToSessionPlayer(LocalPlayer player) {
		if (!STATE.isActive() || player == null || player != sessionPlayer) {
			return;
		}

		float pitch = STATE.getPitch();
		player.setXRot(pitch);
		player.xRotO = pitch;
	}
}
