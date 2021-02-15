package org.slabserver.plugin.lite2edit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class Lite2Edit extends JavaPlugin {
	private JDA jda;
	private Map<Long, Integer> downloadedBytes;
	private ScheduledExecutorService ses;
	private Path worldEditDir;
	protected long whitelistedGuild, whitelistedRole;

	public Lite2Edit() {
		
	}

	public Lite2Edit(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
		super(loader, description, dataFolder, file);
	}
	
	@Override
	public void onEnable() {
		downloadedBytes = new HashMap<>();
		ses = Executors.newSingleThreadScheduledExecutor();
		ses.schedule(downloadedBytes::clear, 1, TimeUnit.DAYS);
		worldEditDir = Paths.get(getDataFolder().getParent() + "/WorldEdit/schematics");
		
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
		
		jda.addEventListener(new ListenerAdapter() {
			@Override
			public void onPrivateMessageReceived(PrivateMessageReceivedEvent event) {
				if (event.getAuthor().equals(jda.getSelfUser()))
					return;
				List<Attachment> attachments = event.getMessage().getAttachments();
				if (attachments.isEmpty())
					return;
				long userId = event.getAuthor().getIdLong();
				String userTag = event.getAuthor().getAsTag();
				int userBytes = downloadedBytes.getOrDefault(userId, 0);
				if (userBytes > 52428800) {
					getLogger().info(userTag + " has uploaded too many schematics");
					event.getChannel().sendMessage("You've uploaded too many schematics").queue();
					return;
				}
				boolean isWhitelisted = false;
				Guild guild = jda.getGuildById(whitelistedGuild);
				if (guild != null) {
					Member member = guild.retrieveMemberById(userId).complete();
					if (member != null) {
						isWhitelisted = member.getRoles().contains(guild.getRoleById(whitelistedRole));
					}
				}
				if (!isWhitelisted) {
					getLogger().info(userTag + " is not whitelisted");
					event.getChannel().sendMessage("You're not whitelisted").queue();
					return;
				}
				
				for (Attachment att : attachments) {
					String filename = att.getFileName();
					if (filename.endsWith(".litematic")) {
						/*
						 * count bytes
						 * convert to worldedit schematics
						 * move to worldedit folder
						 * delete original
						 */
						getLogger().info(userTag + " uploaded " + filename);
						userBytes += att.getSize();
						File outputDir = new File(getDataFolder() + "/" + att.getId());
						outputDir.mkdirs();
						try {
							att.downloadToFile(outputDir + "/" + att.getFileName())
							.thenAccept(inputFile -> {
								List<File> schematics;
								String msg;
								try {
									schematics = Converter.litematicToWorldEdit(inputFile, outputDir);
									List<String> outputFiles = new ArrayList<>();
									for (File schem : schematics) {
										outputFiles.add(moveToWorldEditDir(schem).getName());
									}
									msg = "Uploaded " + String.join(", ", outputFiles);
								} catch (IOException e) {
									e.printStackTrace();
									msg = "IO Exception";
								} catch (Exception e) {
									e.printStackTrace();
									msg = "Unexpected error. Is this a valid litematic?";
								}
								getLogger().info(msg);
								event.getChannel().sendMessage(msg).queue();
								deleteDir(outputDir);
							});
						} catch (Exception e) {
							e.printStackTrace();
							String msg = "Failed to download " + filename;
							getLogger().info(msg);
							event.getChannel().sendMessage(msg).queue();
						}
					}
					else if (filename.endsWith(".schem")) {
						/*
						 * count bytes
						 * download schematic and sanitize
						 * move to worldedit folder
						 * delete folder
						 */
						getLogger().info(userTag + " uploaded " + filename);
						userBytes += att.getSize();
						File outputDir = new File(getDataFolder() + "/" + att.getId());
						outputDir.mkdirs();
						try {
							att.downloadToFile(outputDir + "/" + att.getFileName())
							.thenAccept(inputFile -> {
								String msg;
								try {
									Sanitizer.sanitize(inputFile);
									String outputFile = moveToWorldEditDir(inputFile).getName();
									msg = "Uploaded " + outputFile;
								} catch (IOException e) {
									e.printStackTrace();
									msg = "IO Exception";
								}
								getLogger().info(msg);
								event.getChannel().sendMessage(msg).queue();
								deleteDir(outputDir);
							});
						} catch (Exception e) {
							e.printStackTrace();
							getLogger().info("Failed to download " + filename);
							event.getChannel().sendMessage("Failed to download " + filename).queue();
						}
					}
				}
				downloadedBytes.put(userId, userBytes);
			}

		});
	}

	@Override
	public void onDisable() {
		if (jda != null)
			jda.shutdownNow();
		ses.shutdownNow();
	}
	
	private void deleteDir(File dir) {
		for (File file : dir.listFiles())
			file.delete();
		dir.delete();
	}
	
	private File moveToWorldEditDir(File file) throws IOException {
		Files.createDirectories(worldEditDir);
		String destinationName = worldEditDir + "/" + file.getName();
		File destination = new File(destinationName);
		int index = destinationName.lastIndexOf('.');
		for (int i = 1; i < 1000 && destination.exists(); ++i) {
			if (index < 0)
				destination = new File(destinationName + "(" + i + ")");
			else {
				destination = new File(new StringBuilder(destinationName)
						.insert(index, "(" + i + ")").toString());
			}
		}
		Files.move(file.toPath(), destination.toPath());
		return destination;
	}

}
