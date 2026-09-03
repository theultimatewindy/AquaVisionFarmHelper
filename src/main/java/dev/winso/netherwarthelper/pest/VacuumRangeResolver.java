package dev.winso.netherwarthelper.pest;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Resolves the effective range of known Garden vacuum items by display name.
 */
public final class VacuumRangeResolver {
	public static final double GENERIC_VACUUM_RANGE = 5.0;

	private VacuumRangeResolver() {
	}

	/**
	 * Returns an empty result for non-vacuum items. Unknown items whose display
	 * name still contains "vacuum" use the conservative generic range.
	 */
	public static OptionalDouble resolveRange(String displayName) {
		String normalized = normalize(displayName);
		if (normalized.isEmpty()) {
			return OptionalDouble.empty();
		}

		if (normalized.contains("infinivacuum") && normalized.contains("hooverius")) {
			return OptionalDouble.of(15.0);
		}
		if (normalized.contains("infinivacuum")) {
			return OptionalDouble.of(12.5);
		}
		if (normalized.contains("hyper vacuum")) {
			return OptionalDouble.of(10.0);
		}
		if (normalized.contains("turbo vacuum")) {
			return OptionalDouble.of(7.5);
		}
		if (normalized.contains("skymart vacuum")) {
			return OptionalDouble.of(5.0);
		}
		if (normalized.contains("vacuum")) {
			return OptionalDouble.of(GENERIC_VACUUM_RANGE);
		}
		return OptionalDouble.empty();
	}

	public static boolean isVacuum(String displayName) {
		return resolveRange(displayName).isPresent();
	}

	private static String normalize(String displayName) {
		if (displayName == null) {
			return "";
		}
		return PestCountParser.stripFormatting(displayName)
			.strip()
			.toLowerCase(Locale.ROOT)
			.replaceAll("\\s+", " ");
	}
}
