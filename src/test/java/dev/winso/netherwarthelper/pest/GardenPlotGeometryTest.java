package dev.winso.netherwarthelper.pest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GardenPlotGeometryTest {
	private static final double EPSILON = 1.0e-9;

	@Test
	void mapsCenterCardinalAndCornerPlots() {
		assertCenter(0, -8.0, -8.0);
		assertCenter(1, -8.0, -104.0);
		assertCenter(2, -104.0, -8.0);
		assertCenter(3, 88.0, -8.0);
		assertCenter(4, -8.0, 88.0);
		assertCenter(21, -200.0, -200.0);
		assertCenter(24, 184.0, 184.0);
		assertEquals(25, GardenPlotGeometry.centers().size());
	}

	@Test
	void selectsNearestValidInfestedPlot() {
		var nearest = GardenPlotGeometry.nearestTo(-7.0, -90.0, List.of(24, 1, 6, 99));

		assertEquals(1, nearest.orElseThrow().plotId());
		assertTrue(GardenPlotGeometry.nearestTo(0.0, 0.0, List.of(99, -1)).isEmpty());
		assertTrue(GardenPlotGeometry.nearestTo(0.0, 0.0, null).isEmpty());
	}

	@Test
	void nearestTieUsesSmallerPlotId() {
		var nearest = GardenPlotGeometry.nearestTo(-56.0, -56.0, List.of(5, 0));

		assertEquals(0, nearest.orElseThrow().plotId());
	}

	private static void assertCenter(int plotId, double expectedX, double expectedZ) {
		var center = GardenPlotGeometry.centerOf(plotId).orElseThrow();
		assertEquals(expectedX, center.x(), EPSILON);
		assertEquals(expectedZ, center.z(), EPSILON);
	}
}
