package dev.winso.netherwarthelper.notification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

final class WindowsNativeApiTest {
	@Test
	void requiredUser32AlertFunctionsAreAvailable() {
		assumeTrue(Platform.isWindows());

		NativeLibrary user32 = NativeLibrary.getInstance("user32");
		assertNotNull(user32.getFunction("MessageBoxW"));
		assertNotNull(user32.getFunction("MessageBeep"));
		assertNotNull(user32.getFunction("FlashWindowEx"));

		NativeLibrary wtsapi32 = NativeLibrary.getInstance("Wtsapi32");
		assertNotNull(wtsapi32.getFunction("WTSSendMessageW"));
	}
}
