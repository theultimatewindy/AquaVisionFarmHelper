package dev.winso.netherwarthelper.pest;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Receives the vacuum locator trail from the ClientLevel particle mixin. */
public final class PestParticleTracker {
	private static PestParticleTracker activeTracker;

	private final PestParticleTrail trail = new PestParticleTrail();
	private Vec3 origin = Vec3.ZERO;
	private boolean armed;
	private long acceptedPointSequence;

	public void arm(Vec3 playerPosition) {
		if (playerPosition == null
			|| !Double.isFinite(playerPosition.x)
			|| !Double.isFinite(playerPosition.y)
			|| !Double.isFinite(playerPosition.z)) {
			close();
			return;
		}
		origin = playerPosition;
		trail.clear();
		acceptedPointSequence = 0;
		armed = true;
		activeTracker = this;
	}

	public void close() {
		armed = false;
		trail.clear();
		acceptedPointSequence = 0;
		if (activeTracker == this) {
			activeTracker = null;
		}
	}

	public Optional<Vec3> endpoint() {
		if (!armed) {
			return Optional.empty();
		}
		return trail.estimatedEndpoint().map(point -> new Vec3(point.x(), point.y(), point.z()));
	}

	public int pointCount() {
		return trail.size();
	}

	public boolean hasReliableDirection() {
		return armed && trail.hasReliableDirection();
	}

	/** Advances even when the bounded trail discards an old point. */
	public long acceptedPointSequence() {
		return acceptedPointSequence;
	}

	public static void recordAngryVillager(double x, double y, double z) {
		PestParticleTracker tracker = activeTracker;
		if (tracker != null) {
			tracker.record(new Vec3(x, y, z));
		}
	}

	private void record(Vec3 point) {
		if (!armed) {
			return;
		}
		if (trail.tryAdd(point.x, point.y, point.z, origin.x, origin.y, origin.z)) {
			acceptedPointSequence++;
		}
	}
}
