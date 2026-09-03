package dev.winso.netherwarthelper.pest;

import com.mojang.authlib.properties.Property;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

/** Recognizes Hypixel Garden pest heads without depending on entity IDs. */
public final class PestEntityDetector {
	private static final Map<String, String> TEXTURE_HASHES = textureHashes();
	private static final List<String> VISIBLE_NAMES = TEXTURE_HASHES.values().stream()
		.map(name -> name.toLowerCase(Locale.ROOT))
		.toList();

	public List<PestTarget> findTargets(ClientLevel level, LocalPlayer player) {
		List<Entity> entities = new ArrayList<>();
		level.entitiesForRendering().forEach(entities::add);
		List<PestTarget> targets = new ArrayList<>();
		for (Entity entity : entities) {
			if (!(entity instanceof ArmorStand armorStand) || entity.isRemoved() || !entity.isAlive()) {
				continue;
			}
			String pestName = identify(armorStand);
			if (pestName == null) {
				continue;
			}
			Entity pestBody = nearestPestBody(armorStand, entities);
			if (pestBody != null) {
				targets.add(new PestTarget(pestBody, pestName, pestBody.getBoundingBox().getCenter()));
			}
		}
		targets.sort(Comparator.comparingDouble(target -> player.distanceToSqr(target.position())));
		return targets;
	}

	private static Entity nearestPestBody(ArmorStand head, List<Entity> entities) {
		Entity nearest = null;
		double nearestDistanceSquared = 1.75 * 1.75;
		for (Entity candidate : entities) {
			if (!(candidate instanceof Bat) && !(candidate instanceof Silverfish)) {
				continue;
			}
			if (candidate.isRemoved() || !candidate.isAlive()) {
				continue;
			}
			double distanceSquared = head.distanceToSqr(candidate);
			if (distanceSquared <= nearestDistanceSquared) {
				nearest = candidate;
				nearestDistanceSquared = distanceSquared;
			}
		}
		return nearest;
	}

	private static String identify(ArmorStand armorStand) {
		String visible = ((armorStand.getCustomName() != null ? armorStand.getCustomName() : armorStand.getName())
			.getString()).toLowerCase(Locale.ROOT);
		for (String name : VISIBLE_NAMES) {
			if (visible.contains(name)) {
				return titleCase(name);
			}
		}

		ItemStack head = armorStand.getItemBySlot(EquipmentSlot.HEAD);
		if (head.isEmpty()) {
			return null;
		}
		ResolvableProfile profile = head.get(DataComponents.PROFILE);
		if (profile == null) {
			return null;
		}

		for (Property property : profile.partialProfile().properties().get("textures")) {
			String decoded = decodeTextureProperty(property.value());
			if (decoded == null) {
				continue;
			}
			for (Map.Entry<String, String> entry : TEXTURE_HASHES.entrySet()) {
				if (decoded.contains(entry.getKey())) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

	private static String decodeTextureProperty(String value) {
		try {
			return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static String titleCase(String value) {
		if (value.isEmpty()) {
			return value;
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static Map<String, String> textureHashes() {
		Map<String, String> hashes = new LinkedHashMap<>();
		hashes.put("70a1e836bf1968b2eaa4837227a19204f17295d870ee9e754bd6b6d60ddbed3c", "Beetle");
		hashes.put("a24c69f96ce556221e195c8ef2bfad71ebf7f95f5ae914a484a8d0ec21672674", "Cricket");
		hashes.put("6403ba4027a333d8d2fd32ab59d1cfdbaa7d908d80d2381db2a69cbe65450ad8", "Earthworm");
		hashes.put("9d90e777826a52461368e26d1b2e19bfa1ba582d602483e545f4124d0f731842", "Fly");
		hashes.put("4b24a482a32db1ea78fb98060b0c2fa4a373cbd18a68edddeb7419455a59cda9", "Locust");
		hashes.put("be6baf6431a9daa2ca604d5a3c26e9a761d5952f0817174a4fe0b764616e21ff", "Mite");
		hashes.put("52a9fe05bc663efcd12e56a3ccc5ec035bf577b78708548b6f4ffcf1d30eccfe", "Mosquito");
		hashes.put("65485c4b34e5b5470be94de100e61f7816f81bc5a11dfdf0eccf890172da5d0a", "Moth");
		hashes.put("a8abb471db0ab78703011979dc8b40798a941f3a4dec3ec61cbeec2af8cffe8", "Rat");
		hashes.put("7a79d0fd677b54530961117ef84adc206e2cc5045c1344d61d776bf8ac2fe1ba", "Slug");
		hashes.put("1e04bb6367caa4e88f5fd0ee80f0745d137a6060223dbbc42a16471fdf64bb83", "Praying Mantis");
		hashes.put("4ce79e90adf34718f313ec24d6c6135b69b3788c618498446ccc83ca640c0b14", "Firefly");
		hashes.put("254aff4c0b2dce3a672349cc0ee9e6f3a9deebe4b3556e84611eca250a7821bf", "Dragonfly");
		return Map.copyOf(hashes);
	}

	public record PestTarget(Entity entity, String name, Vec3 position) {
		public boolean isValid() {
			return entity != null && !entity.isRemoved() && entity.isAlive();
		}
	}
}
