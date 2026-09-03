package dev.winso.netherwarthelper.notification;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.FLASHWINFO;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides a Windows alert that does not depend on Java's unsupported SystemTray implementation. */
final class WindowsNativeNotifier {
	private static final Logger LOGGER = LoggerFactory.getLogger("AquaVisionOP/WindowsAlert");
	private static final String TITLE = "Aqua Vision is OP";
	private static final int MB_OK = 0x00000000;
	private static final int MB_ICONWARNING = 0x00000030;
	private static final int MB_SETFOREGROUND = 0x00010000;
	private static final int MB_TOPMOST = 0x00040000;
	private static final int WTS_CURRENT_SESSION = -1;
	private static final int UTF_16_BYTES_PER_CHAR = 2;

	private final AtomicBoolean dialogOpen = new AtomicBoolean();
	private volatile boolean shuttingDown;

	public boolean isSupported() {
		return Platform.isWindows();
	}

	public void show(long minecraftWindowHandle, String message) {
		if (!isSupported() || shuttingDown) {
			return;
		}

		flashTaskbar(minecraftWindowHandle);
		if (!dialogOpen.compareAndSet(false, true)) {
			beep();
			return;
		}

		try {
			Thread.ofPlatform()
				.name("Aqua-Vision-OP-Windows-Alert")
				.daemon(true)
				.start(() -> displayDialog(message));
			LOGGER.info("Native Windows alert requested");
		} catch (RuntimeException | LinkageError exception) {
			dialogOpen.set(false);
			LOGGER.error("Could not start the native Windows alert", exception);
		}
	}

	public void shutdown() {
		shuttingDown = true;
	}

	private void displayDialog(String message) {
		try {
			beep();
			if (!displayOnActiveDesktop(message)) {
				NativeUser32.INSTANCE.MessageBox(
					null,
					message,
					TITLE,
					MB_OK | MB_ICONWARNING | MB_SETFOREGROUND | MB_TOPMOST
				);
			}
		} catch (RuntimeException | LinkageError exception) {
			LOGGER.error("Could not display the native Windows alert", exception);
		} finally {
			dialogOpen.set(false);
		}
	}

	private static boolean displayOnActiveDesktop(String message) {
		try {
			IntByReference response = new IntByReference();
			boolean displayed = NativeWtsapi32.INSTANCE.WTSSendMessage(
				null,
				WTS_CURRENT_SESSION,
				TITLE,
				TITLE.length() * UTF_16_BYTES_PER_CHAR,
				message,
				message.length() * UTF_16_BYTES_PER_CHAR,
				MB_OK | MB_ICONWARNING | MB_SETFOREGROUND | MB_TOPMOST,
				0,
				response,
				true
			);
			if (!displayed) {
				LOGGER.warn("Windows could not place the alert on the active desktop (error {})", Native.getLastError());
			}
			return displayed;
		} catch (RuntimeException | LinkageError exception) {
			LOGGER.warn("Active-desktop alert delivery failed; using the local dialog fallback", exception);
			return false;
		}
	}

	private static void beep() {
		try {
			NativeUser32.INSTANCE.MessageBeep(MB_ICONWARNING);
		} catch (RuntimeException | LinkageError exception) {
			LOGGER.debug("Could not play the native Windows alert sound", exception);
		}
	}

	private static void flashTaskbar(long glfwWindowHandle) {
		if (glfwWindowHandle == 0L) {
			return;
		}

		try {
			long win32WindowHandle = GLFWNativeWin32.glfwGetWin32Window(glfwWindowHandle);
			if (win32WindowHandle == 0L) {
				LOGGER.debug("GLFW did not return a Win32 handle for taskbar attention");
				return;
			}
			FLASHWINFO flashInfo = new FLASHWINFO();
			flashInfo.cbSize = flashInfo.size();
			flashInfo.hWnd = new HWND(Pointer.createConstant(win32WindowHandle));
			flashInfo.dwFlags = WinUser.FLASHW_ALL | WinUser.FLASHW_TIMERNOFG;
			flashInfo.uCount = 0;
			flashInfo.dwTimeout = 0;
			User32.INSTANCE.FlashWindowEx(flashInfo);
		} catch (RuntimeException | LinkageError exception) {
			LOGGER.debug("Could not flash the Minecraft taskbar button", exception);
		}
	}

	private interface NativeUser32 extends StdCallLibrary {
		NativeUser32 INSTANCE = Native.load("user32", NativeUser32.class, W32APIOptions.UNICODE_OPTIONS);

		int MessageBox(HWND owner, String text, String caption, int type);

		boolean MessageBeep(int type);
	}

	private interface NativeWtsapi32 extends StdCallLibrary {
		NativeWtsapi32 INSTANCE = Native.load("Wtsapi32", NativeWtsapi32.class, W32APIOptions.UNICODE_OPTIONS);

		boolean WTSSendMessage(
			HANDLE server,
			int sessionId,
			String title,
			int titleLength,
			String message,
			int messageLength,
			int style,
			int timeoutSeconds,
			IntByReference response,
			boolean wait
		);
	}
}
