package dev.winso.netherwarthelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("NetherWartFarmHelper/Config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "nether-wart-farm-helper.json";

	private final Path configPath;

	public ConfigManager() {
		this.configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public FarmConfig load() {
		if (Files.notExists(configPath)) {
			FarmConfig defaults = new FarmConfig();
			defaults.validate();
			save(defaults);
			return defaults;
		}

		try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
			FarmConfig config = GSON.fromJson(reader, FarmConfig.class);
			if (config == null) {
				throw new JsonParseException("Configuration root was null");
			}
			config.validate();
			return config;
		} catch (IOException | JsonParseException exception) {
			LOGGER.error("Could not read {}; using safe defaults without overwriting the file", configPath, exception);
			FarmConfig defaults = new FarmConfig();
			defaults.validate();
			return defaults;
		}
	}

	public void save(FarmConfig config) {
		config.validate();
		try {
			Files.createDirectories(configPath.getParent());
			try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException exception) {
			LOGGER.error("Could not write default configuration to {}", configPath, exception);
		}
	}

	public Path getConfigPath() {
		return configPath;
	}
}
