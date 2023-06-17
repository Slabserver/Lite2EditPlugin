package org.slabserver.plugin.lite2edit;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

public class Lite2Edit extends JavaPlugin {
	public static JDA jda;
	public static Lite2Edit plugin;
	private ScheduledExecutorService executor;
	protected Map<Long, Long> downloadedBytes;
	protected Config config;

	public Lite2Edit() {
		
	}

	public Lite2Edit(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
		super(loader, description, dataFolder, file);
	}
	
	@Override
	public void onEnable() {
		downloadedBytes = Collections.synchronizedMap(new HashMap<>());
		executor = Executors.newSingleThreadScheduledExecutor();
		executor.schedule(downloadedBytes::clear, 1, TimeUnit.DAYS);
		
		try {
			Path folder = getDataFolder().toPath();
			Path configPath = folder.resolve("config.yml");
			Files.createDirectories(folder);
			if (!Files.exists(configPath)) {
				Files.write(configPath, Arrays.asList(
						"#Discord bot token",
						"token: ''",
						"",
						"#Numeric ID of the Discord server a user must be a member of to upload schematics",
						"whitelistedGuild: 0",
						"",
						"#Numeric ID of the role the member must have to upload schematics",
						"whitelistedRole: 0",
						"",
						"#Number of megabytes a user is allowed to upload each day",
						"#Trying to upload past this limit will result in an error message",
						"dailyUploadLimit: 50",
						"",
						"#Remove unsafe tile entities from uploaded schematics",
						"sanitize: true"
				));
			}
			
			config = new Config(this);
			jda = JDABuilder.createDefault(config.token).build();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		jda.addEventListener(new DiscordListener(this));
		plugin = this;
	}

	@Override
	public void onDisable() {
		if (jda != null)
			jda.shutdownNow();
		executor.shutdownNow();
	}
	
	public static Lite2Edit getInstance() {
		return plugin;
	}

}
