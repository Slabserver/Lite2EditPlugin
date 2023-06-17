package org.slabserver.plugin.lite2edit;

import org.bukkit.configuration.file.FileConfiguration;

public class Config {
	public String token;
	public long whitelistedGuild, whitelistedRole, dailyUploadLimit;
	public boolean sanitize;

	public Config(Lite2Edit plugin) {
		FileConfiguration config = plugin.getConfig();
		token = config.getString("token", "");
		whitelistedGuild = config.getLong("whitelistedGuild");
		whitelistedRole = config.getLong("whitelistedRole");
		dailyUploadLimit = config.getLong("dailyUploadLimit", 50);
		sanitize = config.getBoolean("sanitize", true);
	}

}
