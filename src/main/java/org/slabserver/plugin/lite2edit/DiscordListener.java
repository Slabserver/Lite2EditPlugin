package org.slabserver.plugin.lite2edit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class DiscordListener extends ListenerAdapter {
	private final JDA jda;
	private final Lite2Edit plugin;

	public DiscordListener(Lite2Edit plugin) {
		this.plugin = plugin;
		this.jda = Lite2Edit.jda;
	}

	@Override
	public void onPrivateMessageReceived(PrivateMessageReceivedEvent event) {
		if (event.getAuthor().equals(jda.getSelfUser()))
			return;
		if (!containsSchematics(event))
			return;
		
		Config config = plugin.config;
		long userId = event.getAuthor().getIdLong();
		String userTag = event.getAuthor().getAsTag();
		long userBytes = plugin.downloadedBytes.getOrDefault(userId, 0L);
		if (userBytes > (config.dailyUploadLimit << 20)) {
			plugin.getLogger().info(userTag + " has uploaded too many schematics");
			event.getChannel().sendMessage("You've uploaded too many schematics").queue();
			return;
		}
		Guild guild = jda.getGuildById(config.whitelistedGuild);
		Role whitelistedRole;
		if (guild == null) {
			failure(event, "Configured whitelisted guild could not be found. Contact server administrator for help.");
		}
		else if ((whitelistedRole = guild.getRoleById(config.whitelistedRole)) == null) {
			failure(event, "Configured whitelisted role could not be found. Contact server administrator for help.");
		}
		else {
			guild.retrieveMemberById(userId).queue(member -> {
				if (member.getRoles().contains(whitelistedRole)) {
					handleUploads(event);
				}
				else {
					failure(event, "You're not whitelisted to use this bot.");
				}
			});
		}
	}
	
	private void failure(PrivateMessageReceivedEvent event, String error) {
		String userTag = event.getAuthor().getAsTag();
		plugin.getLogger().info(userTag + " encountered an error: " + error);
		event.getChannel().sendMessage("Error: " + error).queue();
	}

	private void handleUploads(PrivateMessageReceivedEvent event) {
		List<Attachment> attachments = event.getMessage().getAttachments();
		String userTag = event.getAuthor().getAsTag();
		long userId = event.getAuthor().getIdLong();
		long userBytes = plugin.downloadedBytes.getOrDefault(userId, 0L);

		for (Attachment att : attachments) {
			String filename = att.getFileName();
			boolean litematic = filename.endsWith(".litematic");
			if (litematic || filename.endsWith(".schem")) {
				/*
				 * count bytes
				 * convert to worldedit schematics (if litematic)
				 * move to worldedit folder
				 */
				plugin.getLogger().info(userTag + " uploaded " + filename);
				userBytes += att.getSize();
				String path = plugin.getDataFolder() + "/uploads/" + userTag.replaceAll("[^\\w]+", "_");
				File outputDir = new File(path);
				outputDir.mkdirs();
				try {
					path += "/" + filename;
					att.downloadToFile(path).thenAccept(inputFile -> {
						String msg;
						try {
							// convert litematic to worldedit and sanitize output
							if (litematic) {
								List<File> schematics = Converter.litematicToWorldEdit(inputFile, outputDir, plugin.config.sanitize);
								List<String> lines = new ArrayList<>();
								for (File schem : schematics) {
									lines.add("Uploaded `" + copyToSchematicFolders(schem).getName() + "`");
								}
								msg = String.join("\n", lines);
							}
							// sanitize worldedit schematic
							else {
								if (plugin.config.sanitize)
									Sanitizer.sanitize(inputFile);
								String outputFile = copyToSchematicFolders(inputFile).getName();
								msg = "Uploaded `" + outputFile + "`";
							}
						} catch (IOException e) {
							e.printStackTrace();
							msg = "IO Exception. Contact server administrator for help.";
						} catch (Exception e) {
							e.printStackTrace();
							msg = "Unexpected error. Is this file a valid schematic?";
						}
						plugin.getLogger().info(msg);
						event.getChannel().sendMessage(msg).queue();
					});
				} catch (Exception e) {
					e.printStackTrace();
					String msg = "Failed to download `" + filename + "`";
					plugin.getLogger().info(msg);
					event.getChannel().sendMessage(msg).queue();
				}
			}
		}
		plugin.downloadedBytes.put(userId, userBytes);
	}

	private boolean containsSchematics(PrivateMessageReceivedEvent event) {
		List<Attachment> attachments = event.getMessage().getAttachments();
		for (Attachment att : attachments) {
			String filename = att.getFileName();
			if (filename.endsWith(".litematic") || filename.endsWith(".schem"))
				return true;
		}
		return false;
	}
	
	private File copyToSchematicFolders(File file) throws IOException {
		String pluginsDir = plugin.getDataFolder().getParent();
		String weDir = pluginsDir + "/WorldEdit/schematics";
		String faweDir = pluginsDir + "/FastAsyncWorldEdit/schematics";
		
		Files.createDirectories(Paths.get(weDir));
		Files.createDirectories(Paths.get(faweDir));
		String destinationName = weDir + "/" + file.getName();
		File destination = new File(destinationName);
		int index = destinationName.lastIndexOf('.');
		for (int i = 1; i < 1000 && destination.exists(); ++i) {
			if (index < 0)
				destination = new File(destinationName + "(" + i + ")");
			else {
				String path = new StringBuilder(destinationName)
						.insert(index, "(" + i + ")")
						.toString();
				destination = new File(path);
			}
		}
		Files.copy(file.toPath(), destination.toPath());
		Files.copy(file.toPath(), Paths.get(faweDir + "/" + destination.getName()), StandardCopyOption.REPLACE_EXISTING);
		return destination;
	}

}
