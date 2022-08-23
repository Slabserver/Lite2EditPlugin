package org.slabserver.plugin.lite2edit;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.yaml.snakeyaml.Yaml;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

public class Lite2Edit extends JavaPlugin {
	public static JDA jda;
	private ScheduledExecutorService ses;
	protected long whitelistedGuild, whitelistedRole;
	protected Map<Long, Integer> downloadedBytes;

	public Lite2Edit() {
		
	}

	public Lite2Edit(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
		super(loader, description, dataFolder, file);
	}
	
	@Override
	public void onEnable() {
		downloadedBytes = Collections.synchronizedMap(new HashMap<>());
		ses = Executors.newSingleThreadScheduledExecutor();
		ses.schedule(downloadedBytes::clear, 1, TimeUnit.DAYS);
		
		try {
			Files.createDirectories(getDataFolder().toPath());
			File config = new File(getDataFolder() + "/config.yml");
			if (config.exists()) {
				Map<String, Object> options = new Yaml().load(new FileInputStream(config));
				String token = (String) options.get("token");
				whitelistedGuild = (long) options.get("whitelistedGuild");
				whitelistedRole = (long) options.get("whitelistedRole");
				jda = JDABuilder.createDefault(token).build();
			}
			else {
				Files.write(config.toPath(),
						"token: \nwhitelistedGuild: \nwhitelistedRole: \n".getBytes(StandardCharsets.UTF_8));
			}
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		jda.addEventListener(new DiscordListener(this));
	}

	@Override
	public void onDisable() {
		if (jda != null)
			jda.shutdownNow();
		ses.shutdownNow();
	}

}
