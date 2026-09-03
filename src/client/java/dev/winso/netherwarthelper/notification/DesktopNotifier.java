package dev.winso.netherwarthelper.notification;

import java.awt.AWTException;
import java.awt.AWTError;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends a native tray alert and asks the operating system to draw attention to Minecraft. */
public final class DesktopNotifier {
	private static final Logger LOGGER = LoggerFactory.getLogger("AquaVisionOP/Notifications");
	private static final String TITLE = "Aqua Vision is OP";
	private final WindowsNativeNotifier windowsNativeNotifier = new WindowsNativeNotifier();

	private volatile TrayIcon trayIcon;
	private volatile boolean trayUnavailable;
	private volatile boolean awtNotificationScheduled;
	private volatile boolean shuttingDown;

	public void showCropInactivityAlert(Minecraft minecraft, int timeoutSeconds) {
		showAlert(
			minecraft,
			"No monitored crop has been broken for " + timeoutSeconds + " seconds. Check the farm."
		);
	}

	public void showSessionStateAlert(Minecraft minecraft, String reason) {
		showAlert(minecraft, "Farm helper interrupted: " + reason + ". Check Minecraft.");
	}

	public void showPestThresholdAlert(Minecraft minecraft, int reportedPests) {
		showAlert(minecraft, "The Garden now has " + reportedPests + " pests. Check Minecraft.");
	}

	private void showAlert(Minecraft minecraft, String message) {
		if (shuttingDown) {
			return;
		}
		requestWindowAttention(minecraft);
		if (windowsNativeNotifier.isSupported()) {
			windowsNativeNotifier.show(minecraft.getWindow().handle(), message);
			return;
		}

		try {
			EventQueue.invokeLater(() -> displayTrayMessage(message));
			awtNotificationScheduled = true;
		} catch (RuntimeException | AWTError | LinkageError exception) {
			logTrayUnavailable("desktop notification initialization failed", exception);
		}
	}

	public void shutdown() {
		shuttingDown = true;
		windowsNativeNotifier.shutdown();
		if (!awtNotificationScheduled && trayIcon == null) {
			return;
		}
		try {
			EventQueue.invokeLater(this::removeTrayIcon);
		} catch (RuntimeException | AWTError | LinkageError exception) {
			LOGGER.debug("Could not remove the desktop notification icon during shutdown", exception);
		}
	}

	private void removeTrayIcon() {
		try {
			if (trayIcon != null && !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
				SystemTray.getSystemTray().remove(trayIcon);
			}
		} catch (RuntimeException | AWTError | LinkageError exception) {
			LOGGER.debug("Could not remove the desktop notification icon during shutdown", exception);
		} finally {
			trayIcon = null;
		}
	}

	private void displayTrayMessage(String message) {
		if (trayUnavailable) {
			return;
		}

		try {
			if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
				logTrayUnavailable("desktop system-tray notifications are not supported");
				return;
			}
			if (trayIcon == null) {
				trayIcon = new TrayIcon(createIcon(), TITLE);
				trayIcon.setImageAutoSize(true);
				SystemTray.getSystemTray().add(trayIcon);
			}
			trayIcon.displayMessage(
				TITLE,
				message,
				TrayIcon.MessageType.WARNING
			);
		} catch (AWTException | RuntimeException | AWTError | LinkageError exception) {
			logTrayUnavailable("could not display the desktop notification", exception);
		}
	}

	private static void requestWindowAttention(Minecraft minecraft) {
		try {
			if (!minecraft.getWindow().isFocused()) {
				GLFW.glfwRequestWindowAttention(minecraft.getWindow().handle());
			}
		} catch (RuntimeException | LinkageError exception) {
			LOGGER.debug("Could not request attention for the Minecraft window", exception);
		}
	}

	private static Image createIcon() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(new Color(0x0A2530));
			graphics.fillRect(0, 0, 16, 16);
			graphics.setColor(new Color(0x55E7F2));
			graphics.fillOval(2, 2, 12, 12);
			graphics.setColor(new Color(0xF7FFFF));
			graphics.fillOval(6, 5, 4, 6);
		} finally {
			graphics.dispose();
		}
		return image;
	}

	private void logTrayUnavailable(String reason) {
		if (!trayUnavailable) {
			trayUnavailable = true;
			LOGGER.warn("{}; the HUD failsafe warning will remain available", reason);
		}
	}

	private void logTrayUnavailable(String reason, Throwable exception) {
		if (!trayUnavailable) {
			trayUnavailable = true;
			LOGGER.warn("{}; the HUD failsafe warning will remain available", reason, exception);
		}
	}
}
