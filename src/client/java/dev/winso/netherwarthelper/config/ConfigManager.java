package dev.winso.netherwarthelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("AquaVisionOP/Config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "nether-wart-farm-helper.json";

	private final Path configPath;

	public ConfigManager() {
		this(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
	}

	ConfigManager(Path configPath) {
		this.configPath = configPath;
	}

	public FarmConfig load() {
		if (Files.notExists(configPath)) {
			FarmConfig defaults = new FarmConfig();
			defaults.validate();
			save(defaults);
			return defaults;
		}

		FarmConfig config;
		boolean migrated;
		try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				throw new JsonParseException("Configuration root must be a JSON object");
			}

			JsonObject object = root.getAsJsonObject();
			int sourceConfigVersion = readConfigVersion(object);
			config = GSON.fromJson(root, FarmConfig.class);
			if (config == null) {
				throw new JsonParseException("Configuration root was null");
			}
			migrated = config.migrateFrom(sourceConfigVersion);
			config.validate();
		} catch (IOException | JsonParseException exception) {
			LOGGER.error("Could not read {}; using safe defaults without overwriting the file", configPath, exception);
			FarmConfig defaults = new FarmConfig();
			defaults.validate();
			return defaults;
		}

		if (migrated) {
			LOGGER.info("Migrating {} to configuration version {}", configPath, FarmConfig.CURRENT_CONFIG_VERSION);
			save(config);
		}
		return config;
	}

	public boolean save(FarmConfig config) {
		config.validate();
		Path temporaryPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");
		try {
			Files.createDirectories(configPath.getParent());
			try (Writer writer = Files.newBufferedWriter(temporaryPath, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
			try {
				Files.move(
					temporaryPath,
					configPath,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
				);
			} catch (IOException atomicMoveFailure) {
				Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException exception) {
			LOGGER.error("Could not write configuration to {}", configPath, exception);
			try {
				Files.deleteIfExists(temporaryPath);
			} catch (IOException cleanupException) {
				LOGGER.debug("Could not remove temporary configuration file {}", temporaryPath, cleanupException);
			}
			return false;
		}
	}

	private static int readConfigVersion(JsonObject object) {
		JsonElement version = object.get("configVersion");
		if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
			return 0;
		}
		try {
			return version.getAsInt();
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	public Path getConfigPath() {
		return configPath;
	}
}
