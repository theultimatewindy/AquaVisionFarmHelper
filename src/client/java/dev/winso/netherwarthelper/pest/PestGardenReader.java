package dev.winso.netherwarthelper.pest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Reads the Garden pest counter and infested plot IDs from the normal client HUD data. */
public final class PestGardenReader {
	private static final Pattern PLOTS_LINE = Pattern.compile("(?i)\\bplots?\\s*:\\s*(.*)$");
	private static final Pattern PLOT_NUMBER = Pattern.compile("(?<![0-9])([0-9]{1,2})(?![0-9])");

	public Snapshot read(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.getConnection() == null) {
			return Snapshot.unknown();
		}

		List<String> scoreboardLines = scoreboardLines(minecraft);
		List<String> tabLines = tabLines(minecraft);
		PestCountParser.Reading reading = PestCountParser.read(scoreboardLines, tabLines);
		Set<Integer> infestedPlots = reading.inGarden() ? infestedPlots(tabLines) : Set.of();
		return new Snapshot(reading.count(), infestedPlots, scoreboardLines, tabLines,
			reading.source(), reading.evidence(), reading.inGarden());
	}

	private static List<String> scoreboardLines(Minecraft minecraft) {
		Scoreboard scoreboard = minecraft.level.getScoreboard();
		// Match the objective actually rendered by the vanilla HUD, including team-colored sidebars.
		Objective teamObjective = null;
		PlayerTeam team = minecraft.player == null ? null
			: scoreboard.getPlayersTeam(minecraft.player.getScoreboardName());
		if (team != null && team.getColor().isPresent()) {
			teamObjective = scoreboard.getDisplayObjective(team.getColor().get().displaySlot());
		}
		Objective objective = teamObjective != null ? teamObjective
			: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			return List.of();
		}

		List<String> lines = new ArrayList<>();
		List<PlayerScoreEntry> visibleEntries = scoreboard.listPlayerScores(objective).stream()
			.filter(entry -> !entry.isHidden())
			.sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
				.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
			.limit(15)
			.toList();
		for (PlayerScoreEntry entry : visibleEntries) {
			Component formatted = PlayerTeam.formatNameForTeam(
				scoreboard.getPlayersTeam(entry.owner()),
				entry.ownerName()
			);
			String line = formatted.getString();
			NumberFormat numberFormat = entry.numberFormatOverride() != null
				? entry.numberFormatOverride() : objective.numberFormat();
			// Fixed text is visible content. Default numeric scores are row ordering, NOT pest totals.
			if (numberFormat instanceof FixedFormat fixed && !fixed.value().getString().isBlank()) {
				line += " " + fixed.value().getString();
			}
			lines.add(line);
		}
		return lines;
	}

	private static List<String> tabLines(Minecraft minecraft) {
		List<String> lines = new ArrayList<>();
		for (PlayerInfo playerInfo : minecraft.getConnection().getListedOnlinePlayers()) {
			lines.add(minecraft.gui.hud.getTabList().getNameForDisplay(playerInfo).getString());
		}
		return List.copyOf(lines);
	}

	private static Set<Integer> infestedPlots(List<String> tabLines) {
		Set<Integer> plotIds = new LinkedHashSet<>();
		for (String line : tabLines) {
			Matcher lineMatcher = PLOTS_LINE.matcher(PestCountParser.stripFormatting(line));
			if (!lineMatcher.find()) {
				continue;
			}
			Matcher numberMatcher = PLOT_NUMBER.matcher(lineMatcher.group(1));
			while (numberMatcher.find()) {
				int plotId = Integer.parseInt(numberMatcher.group(1));
				if (GardenPlotGeometry.centerOf(plotId).isPresent()) {
					plotIds.add(plotId);
				}
			}
		}
		return Set.copyOf(plotIds);
	}

	public record Snapshot(OptionalInt pestCount, Set<Integer> infestedPlots, List<String> scoreboardLines,
		List<String> tabLines, String countSource, String countEvidence, boolean inGarden) {
		public Snapshot {
			pestCount = pestCount == null ? OptionalInt.empty() : pestCount;
			infestedPlots = infestedPlots == null ? Set.of() : Set.copyOf(infestedPlots);
			scoreboardLines = scoreboardLines == null ? List.of() : List.copyOf(scoreboardLines);
			tabLines = tabLines == null ? List.of() : List.copyOf(tabLines);
		}

		public static Snapshot unknown() {
			return new Snapshot(OptionalInt.empty(), Set.of(), List.of(), List.of(), "unknown", "", false);
		}
	}
}
