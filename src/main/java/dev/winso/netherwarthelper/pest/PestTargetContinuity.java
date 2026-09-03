package dev.winso.netherwarthelper.pest;

import java.util.List;
import java.util.Objects;

/**
 * Keeps one positively identified pest through brief head/body detector gaps without
 * allowing an invalid or superseded entity to become a permanent target.
 */
public final class PestTargetContinuity {
	public static final int MAX_MISSED_TICKS = 10;

	private PestTargetContinuity() {
	}

	/**
	 * Selects a fresh target by index, retains the current identity for a bounded gap,
	 * or reports that no target is safe to use. A fresh alternative wins immediately
	 * when the current identity is absent from a non-empty detector result.
	 */
	public static <T> Decision<T> select(
		T currentId,
		boolean currentValid,
		int previousMissingTicks,
		List<? extends T> freshIds
	) {
		Objects.requireNonNull(freshIds, "freshIds");
		if (previousMissingTicks < 0) {
			throw new IllegalArgumentException("Missing-tick count cannot be negative");
		}

		if (currentId != null && currentValid) {
			for (int index = 0; index < freshIds.size(); index++) {
				T candidateId = Objects.requireNonNull(freshIds.get(index), "fresh target ID");
				if (Objects.equals(candidateId, currentId)) {
					return new Decision<>(Source.FRESH, candidateId, index, 0);
				}
			}
			if (freshIds.isEmpty()) {
				int nextMissingTicks = previousMissingTicks + 1;
				if (nextMissingTicks <= MAX_MISSED_TICKS) {
					return new Decision<>(Source.RETAINED, currentId, -1, nextMissingTicks);
				}
				return new Decision<>(Source.NONE, null, -1, nextMissingTicks);
			}
		}

		if (!freshIds.isEmpty()) {
			T candidateId = Objects.requireNonNull(freshIds.getFirst(), "fresh target ID");
			return new Decision<>(Source.FRESH, candidateId, 0, 0);
		}
		return new Decision<>(Source.NONE, null, -1, 0);
	}

	public enum Source {
		FRESH,
		RETAINED,
		NONE
	}

	/** freshIndex is non-negative only when source is FRESH. */
	public record Decision<T>(Source source, T selectedId, int freshIndex, int missingTicks) {
		public boolean hasTarget() {
			return source != Source.NONE;
		}

		public boolean fresh() {
			return source == Source.FRESH;
		}

		public boolean retained() {
			return source == Source.RETAINED;
		}
	}
}
