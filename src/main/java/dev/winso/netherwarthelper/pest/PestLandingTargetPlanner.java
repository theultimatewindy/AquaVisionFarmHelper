package dev.winso.netherwarthelper.pest;

import java.util.ArrayList;
import java.util.List;

/** Candidate lane insets ordered away from the crop-facing direction first. */
public final class PestLandingTargetPlanner {
	public static final double DEFAULT_INSET = 0.10;
	public static final double MINIMUM_SETTLE_TOLERANCE = 0.125;
	private static final double MAXIMUM_SETTLE_TOLERANCE = 0.25;
	private static final double POSITION_SAFETY_MARGIN = 0.005;
	private static final double POSITION_NUMERIC_MARGIN = 1.0e-6;

	private PestLandingTargetPlanner() {
	}

	public static List<Point> candidates(double savedX, double savedZ, double savedYawDegrees, double inset) {
		if (!Double.isFinite(savedX) || !Double.isFinite(savedZ) || !Double.isFinite(savedYawDegrees)
			|| !Double.isFinite(inset) || inset <= 0.0) {
			throw new IllegalArgumentException("Landing target inputs must be finite and inset positive");
		}
		double backwardHeading = Math.toRadians(savedYawDegrees + 180.0);
		int[] relativeAngles = {0, -45, 45, -90, 90, -135, 135, 180};
		List<Point> result = new ArrayList<>(relativeAngles.length + 1);
		for (int relativeAngle : relativeAngles) {
			double radians = backwardHeading + Math.toRadians(relativeAngle);
			result.add(new Point(
				savedX - Math.sin(radians) * inset,
				savedZ + Math.cos(radians) * inset
			));
		}
		result.add(new Point(savedX, savedZ));
		return List.copyOf(result);
	}

	/** Shrinks the inset when the user selected a particularly tight saved-position tolerance. */
	public static double preferredInset(double savedPositionTolerance) {
		if (!Double.isFinite(savedPositionTolerance) || savedPositionTolerance <= 0.0) {
			throw new IllegalArgumentException("Saved-position tolerance must be finite and positive");
		}
		double available = savedPositionTolerance - MINIMUM_SETTLE_TOLERANCE - POSITION_SAFETY_MARGIN;
		if (available <= POSITION_NUMERIC_MARGIN) {
			throw new IllegalArgumentException("Saved-position tolerance is too small for stable landing");
		}
		return Math.min(DEFAULT_INSET, available - POSITION_NUMERIC_MARGIN);
	}

	/** Extends through the known-safe landing point so digital flight gets a stable clearance pulse. */
	public static Point edgeClearanceTarget(
		double currentX,
		double currentZ,
		double landingX,
		double landingZ,
		double savedYawDegrees,
		double extension
	) {
		if (!Double.isFinite(currentX) || !Double.isFinite(currentZ)
			|| !Double.isFinite(landingX) || !Double.isFinite(landingZ)
			|| !Double.isFinite(savedYawDegrees) || !Double.isFinite(extension) || extension <= 0.0) {
			throw new IllegalArgumentException("Edge-clearance inputs must be finite and extension positive");
		}
		double dx = landingX - currentX;
		double dz = landingZ - currentZ;
		double length = Math.hypot(dx, dz);
		if (length < 1.0e-6) {
			double backward = Math.toRadians(savedYawDegrees + 180.0);
			dx = -Math.sin(backward);
			dz = Math.cos(backward);
			length = 1.0;
		}
		return new Point(landingX + dx / length * extension, landingZ + dz / length * extension);
	}

	/** Keeps the final target error plus its inset inside the saved-position tolerance. */
	public static double settleTolerance(double savedPositionTolerance, double insetDistance) {
		if (!Double.isFinite(savedPositionTolerance) || savedPositionTolerance <= 0.0
			|| !Double.isFinite(insetDistance) || insetDistance < 0.0) {
			throw new IllegalArgumentException("Landing tolerances must be finite and non-negative");
		}
		double available = savedPositionTolerance - insetDistance - POSITION_SAFETY_MARGIN;
		if (available < MINIMUM_SETTLE_TOLERANCE) {
			throw new IllegalArgumentException("Landing inset leaves no safe settling tolerance");
		}
		return Math.min(MAXIMUM_SETTLE_TOLERANCE, available);
	}

	public record Point(double x, double z) {
	}
}
