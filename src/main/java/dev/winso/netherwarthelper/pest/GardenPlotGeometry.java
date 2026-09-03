package dev.winso.netherwarthelper.pest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Static Garden plot layout used for coarse pest navigation.
 */
public final class GardenPlotGeometry {
	private static final int[][] PLOT_IDS_BY_ROW = {
		{21, 13, 9, 14, 22},
		{15, 5, 1, 6, 16},
		{10, 2, 0, 3, 11},
		{17, 7, 4, 8, 18},
		{23, 19, 12, 20, 24}
	};
	private static final double[] AXIS_CENTERS = {-200.0, -104.0, -8.0, 88.0, 184.0};
	private static final Map<Integer, PlotCenter> CENTERS = createCenters();

	private GardenPlotGeometry() {
	}

	public static Optional<PlotCenter> centerOf(int plotId) {
		return Optional.ofNullable(CENTERS.get(plotId));
	}

	/**
	 * Returns all known plot centers, keyed by plot ID.
	 */
	public static Map<Integer, PlotCenter> centers() {
		return CENTERS;
	}

	/**
	 * Finds the nearest valid plot from {@code plotIds}. Unknown and null IDs
	 * are ignored. Equal distances are resolved by the smaller plot ID.
	 */
	public static Optional<PlotCenter> nearestTo(double x, double z, Iterable<Integer> plotIds) {
		if (plotIds == null) {
			return Optional.empty();
		}

		PlotCenter nearest = null;
		double nearestDistanceSquared = Double.POSITIVE_INFINITY;
		for (Integer plotId : plotIds) {
			if (plotId == null) {
				continue;
			}
			PlotCenter candidate = CENTERS.get(plotId);
			if (candidate == null) {
				continue;
			}

			double candidateDistanceSquared = candidate.distanceSquaredTo(x, z);
			if (candidateDistanceSquared < nearestDistanceSquared
				|| (Double.compare(candidateDistanceSquared, nearestDistanceSquared) == 0
					&& (nearest == null || candidate.plotId() < nearest.plotId()))) {
				nearest = candidate;
				nearestDistanceSquared = candidateDistanceSquared;
			}
		}
		return Optional.ofNullable(nearest);
	}

	private static Map<Integer, PlotCenter> createCenters() {
		Map<Integer, PlotCenter> centers = new LinkedHashMap<>();
		for (int row = 0; row < PLOT_IDS_BY_ROW.length; row++) {
			for (int column = 0; column < PLOT_IDS_BY_ROW[row].length; column++) {
				int plotId = PLOT_IDS_BY_ROW[row][column];
				centers.put(plotId, new PlotCenter(plotId, AXIS_CENTERS[column], AXIS_CENTERS[row]));
			}
		}
		return Collections.unmodifiableMap(centers);
	}

	public record PlotCenter(int plotId, double x, double z) {
		public double distanceSquaredTo(double otherX, double otherZ) {
			double deltaX = otherX - x;
			double deltaZ = otherZ - z;
			return deltaX * deltaX + deltaZ * deltaZ;
		}
	}
}
