package dev.winso.netherwarthelper.pest;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the Garden pest total from the formatted scoreboard line used by
 * Hypixel SkyBlock. Missing or malformed data deliberately remains unknown;
 * callers must not interpret a missing line as zero pests.
 */
public final class PestCountParser {
	public static final char PEST_GLYPH = '\u0D60';

	private static final String NUMBER = "(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)";
	// A question mark can be a lossy HUD/font/log replacement for a location or pest icon.
	// Only allow it in decoration positions, never arbitrary punctuation such as a minus sign.
	private static final String GARDEN_LOCATION = "(?:\\?\\s*)*(?:area\\s*:\\s*)?the\\s+garden";
	private static final String MULTIPLIER_TOKEN = "(?:x\\s*" + NUMBER + "|" + NUMBER + "\\s*x)";
	private static final Pattern GARDEN_LINE = Pattern.compile(
		"(?i)^" + GARDEN_LOCATION + "(?:[\\s?]*(?:" + MULTIPLIER_TOKEN
			+ ")?|\\s*" + PEST_GLYPH + ".*)$"
	);
	private static final Pattern GARDEN_TOTAL = Pattern.compile(
		"(?i)^" + GARDEN_LOCATION + "\\s*" + PEST_GLYPH + "\\s*(.+)$"
	);
	private static final Pattern GARDEN_MULTIPLIER_TOTAL = Pattern.compile(
		"(?i)^" + GARDEN_LOCATION + "[\\s?]*(" + MULTIPLIER_TOKEN + ")$"
	);
	private static final Pattern TAB_GARDEN_AREA = Pattern.compile("(?i)^area\\s*:\\s*(?:the\\s+)?garden$");
	private static final Pattern GLYPH_TOTAL = Pattern.compile("^" + PEST_GLYPH + "\\s*(.+)$");
	private static final Pattern LABELLED_TOTAL = Pattern.compile("(?i)^(?:total\\s+)?pests\\s*:\\s*(.+)$");
	private static final Pattern COUNT_TOKEN = Pattern.compile(
		"(?i)^(?:x\\s*(" + NUMBER + ")|(" + NUMBER + ")\\s*x?)$"
	);

	private PestCountParser() {
	}

	/**
	 * Reads one complete sidebar snapshot. A separate glyph-only counter is
	 * accepted only when this same snapshot also identifies the Garden.
	 */
	public static OptionalInt parse(Iterable<String> scoreboardLines) {
		if (scoreboardLines == null) {
			return OptionalInt.empty();
		}

		List<String> lines = new ArrayList<>();
		scoreboardLines.forEach(lines::add);
		return read(lines, List.of()).count();
	}

	/**
	 * Parses a Garden location/counter line. The old counter reader accepts a
	 * plain number as well as an {@code x8} or {@code 8x} multiplier token.
	 */
	public static OptionalInt parseLine(String scoreboardLine) {
		if (scoreboardLine == null) {
			return OptionalInt.empty();
		}

		return read(List.of(scoreboardLine), List.of()).count();
	}

	/**
	 * Combines only explicit total counters in the current visible data. Tab
	 * totals are a fallback, never plot IDs or a sum of individual plot counts.
	 * Disagreeing totals remain unknown until a later coherent snapshot.
	 */
	public static Reading read(List<String> scoreboardLines, List<String> tabLines) {
		List<String> sidebar = normalizedLines(scoreboardLines);
		List<String> tab = normalizedLines(tabLines);
		String gardenEvidence = sidebar.stream()
			.filter(line -> GARDEN_LINE.matcher(line).matches())
			.findFirst().orElse("");
		boolean sidebarGarden = !gardenEvidence.isEmpty();
		if (!sidebarGarden) {
			gardenEvidence = tab.stream().filter(line -> TAB_GARDEN_AREA.matcher(line).matches())
				.findFirst().orElse("");
		}
		if (gardenEvidence.isEmpty()) {
			return new Reading(OptionalInt.empty(), "unknown", "No Garden location in sidebar or tab Area", false);
		}

		List<Candidate> candidates = new ArrayList<>();
		for (String line : sidebar) {
			Matcher matcher = GARDEN_TOTAL.matcher(line);
			if (!matcher.matches()) {
				// Current HUD icons may differ from the old reference icon. The exact Garden
				// location plus an explicit x-counter identifies the total without that icon.
				matcher = GARDEN_MULTIPLIER_TOTAL.matcher(line);
			}
			if (!matcher.matches() && sidebarGarden) {
				matcher = GLYPH_TOTAL.matcher(line);
			}
			if (matcher.matches()) {
				addCandidate(candidates, matcher.group(1), "sidebar", line);
			}
		}
		for (String line : tab) {
			Matcher matcher = LABELLED_TOTAL.matcher(line);
			if (matcher.matches()) {
				addCandidate(candidates, matcher.group(1), "tab list", line);
			}
		}
		if (candidates.isEmpty()) {
			return new Reading(OptionalInt.empty(), "unknown", gardenEvidence, true);
		}

		Candidate first = candidates.getFirst();
		for (Candidate candidate : candidates) {
			if (candidate.count() != first.count()) {
				return new Reading(OptionalInt.empty(), "conflict",
					first.source() + ": " + first.evidence() + " | "
						+ candidate.source() + ": " + candidate.evidence(), true);
			}
		}
		return new Reading(OptionalInt.of(first.count()), first.source(), first.evidence(), true);
	}

	private static void addCandidate(List<Candidate> candidates, String token, String source, String evidence) {
		Matcher matcher = COUNT_TOKEN.matcher(token);
		if (!matcher.matches()) {
			return;
		}
		String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		try {
			candidates.add(new Candidate(Integer.parseInt(number.replace(",", "")), source, evidence));
		} catch (NumberFormatException ignored) {
			// Overflow and malformed values must not become an authoritative zero.
		}
	}

	private static List<String> normalizedLines(List<String> lines) {
		if (lines == null) {
			return List.of();
		}
		return lines.stream().map(PestCountParser::normalize).toList();
	}

	/**
	 * Mirrors the reference reader's removal of non-ASCII decoration, while
	 * retaining the pest glyph and converting Unicode spaces before filtering.
	 * Otherwise a non-breaking space would join separate words or count tokens.
	 */
	private static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String plain = stripFormatting(text);
		StringBuilder normalized = new StringBuilder(plain.length());
		plain.codePoints().forEach(codePoint -> {
			if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
				normalized.append(' ');
			} else if (codePoint == '\u00D7') {
				normalized.append('x');
			} else if (codePoint == '\u2212') {
				// Removing a mathematical minus would turn an invalid negative count positive.
				normalized.append('-');
			} else if ((codePoint >= 0x20 && codePoint <= 0x7E) || codePoint == PEST_GLYPH) {
				normalized.appendCodePoint(codePoint);
			}
		});
		return normalized.toString().strip();
	}

	public record Reading(OptionalInt count, String source, String evidence, boolean inGarden) {
	}

	private record Candidate(int count, String source, String evidence) {
	}

	/**
	 * Removes legacy Minecraft section-sign formatting codes.
	 */
	public static String stripFormatting(String text) {
		if (text == null || text.indexOf('\u00A7') < 0) {
			return text;
		}

		StringBuilder plain = new StringBuilder(text.length());
		for (int index = 0; index < text.length(); index++) {
			char current = text.charAt(index);
			if (current == '\u00A7' && index + 1 < text.length()) {
				index++;
				continue;
			}
			plain.append(current);
		}
		return plain.toString();
	}
}
