package dev.winso.netherwarthelper.pest;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes a populated Garden HUD with its pest counter absent. This is only
 * contextual eligibility, never a standalone zero count or cleanup completion.
 */
public final class PestClearEvidence {
	private static final Pattern GARDEN_AREA = Pattern.compile("(?i)^area\\s*:\\s*(?:the\\s+)?garden$");
	private static final Pattern AREA_LABEL = Pattern.compile("(?i)^area\\s*:");
	private static final Pattern GARDEN_ROW = Pattern.compile("(?i)^(?:the\\s+)?garden.*$");
	private static final Pattern BARE_GARDEN = Pattern.compile("(?i)^(?:the\\s+)?garden$");
	private static final Pattern PLOT_ROW = Pattern.compile("(?i)^plot\\b.*$");
	private static final Pattern BARE_PLOT = Pattern.compile("(?i)^plot\\s*-\\s*([0-9]{1,2})$");
	private static final Pattern PEST_TOTAL = Pattern.compile("(?i)^(?:total\\s+)?pests\\s*:\\s*(.*)$");
	private static final Pattern PLOTS_LABEL = Pattern.compile("(?i)^plots?\\s*:\\s*(.*)$");
	private static final Pattern STATUS = Pattern.compile("(?i)^(purse|bits|copper|flight duration)\\s*:\\s*(.+)$");
	private static final Pattern STANDALONE_MULTIPLIER = Pattern.compile("(?i)^(?:x\\s*.*|[0-9?,.]+\\s*x)$");

	private PestClearEvidence() {
	}

	/**
	 * The caller must separately require a recently vacuumed, removed final target
	 * after an authoritative total of one, no live pests, and repeated fresh polls.
	 */
	public static boolean hasCounterFreeGardenHud(List<String> sidebar, List<String> tab) {
		if (sidebar == null || sidebar.isEmpty() || tab == null || tab.isEmpty()) return false;
		boolean gardenArea = false;
		for (String raw : tab) {
			String line = normalize(raw);
			if (GARDEN_AREA.matcher(line).matches()) {
				gardenArea = true;
			} else if (AREA_LABEL.matcher(line).find()) {
				// Conflicting area rows can occur while the client is switching worlds.
				return false;
			}
			Matcher total = PEST_TOTAL.matcher(line);
			if (total.matches() && !total.group(1).isBlank()) return false;
			Matcher plots = PLOTS_LABEL.matcher(line);
			if (plots.matches()) {
				String value = plots.group(1).strip();
				if (!value.isEmpty() && !value.equalsIgnoreCase("None")
					&& !value.equalsIgnoreCase("No pests")) return false;
			}
		}
		if (!gardenArea) return false;

		boolean location = false;
		Set<String> statusMarkers = new HashSet<>();
		for (String raw : sidebar) {
			String line = normalize(raw);
			if (line.indexOf(PestCountParser.PEST_GLYPH) >= 0
				|| STANDALONE_MULTIPLIER.matcher(line).matches()) return false;
			if (GARDEN_ROW.matcher(line).matches()) {
				if (!BARE_GARDEN.matcher(line).matches()) return false;
				location = true;
			}
			if (PLOT_ROW.matcher(line).matches()) {
				Matcher plot = BARE_PLOT.matcher(line);
				if (!plot.matches() || GardenPlotGeometry.centerOf(Integer.parseInt(plot.group(1))).isEmpty()) {
					return false;
				}
				location = true;
			}
			Matcher total = PEST_TOTAL.matcher(line);
			if (total.matches() && !total.group(1).isBlank()) return false;
			Matcher marker = STATUS.matcher(line);
			if (marker.matches()) statusMarkers.add(marker.group(1).toLowerCase(Locale.ROOT));
			if (line.equalsIgnoreCase("www.hypixel.net")) statusMarkers.add("website");
		}
		return location && statusMarkers.size() >= 2;
	}

	/** Preserve non-decoration text so malformed suffixes cannot disappear into an apparent zero. */
	private static String normalize(String raw) {
		if (raw == null) return "";
		String plain = PestCountParser.stripFormatting(raw);
		StringBuilder text = new StringBuilder(plain.length());
		plain.codePoints().forEach(codePoint -> {
			if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
				text.append(' ');
			} else if (codePoint == '\u00D7') {
				text.append('x');
			} else if (codePoint == '\u2212' || codePoint == '\u2013' || codePoint == '\u2014') {
				text.append('-');
			} else if ((codePoint >= 0x20 && codePoint <= 0x7E)
				|| Character.isLetterOrDigit(codePoint) || codePoint == PestCountParser.PEST_GLYPH) {
				text.appendCodePoint(codePoint);
			}
		});
		// A missing-font location icon may be presented as a question mark before the label.
		return text.toString().replaceAll("\\s+", " ").strip().replaceFirst("^(?:\\?\\s*)+", "");
	}
}
