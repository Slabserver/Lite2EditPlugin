package org.slabserver.plugin.lite2edit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.Attachment;
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
		List<Attachment> attachments = event.getMessage().getAttachments();
		if (attachments.isEmpty())
			return;
		long userId = event.getAuthor().getIdLong();
		String userTag = event.getAuthor().getAsTag();
		int userBytes = plugin.downloadedBytes.getOrDefault(userId, 0);
		if (userBytes > 52428800) {
			plugin.getLogger().info(userTag + " has uploaded too many schematics");
			event.getChannel().sendMessage("You've uploaded too many schematics").queue();
			return;
		}
		boolean isWhitelisted = false;
		Guild guild = jda.getGuildById(plugin.whitelistedGuild);
		if (guild != null) {
			Member member = guild.retrieveMemberById(userId).complete();
			if (member != null) {
				isWhitelisted = member.getRoles().contains(guild.getRoleById(plugin.whitelistedRole));
			}
		}
		if (!isWhitelisted) {
			plugin.getLogger().info(userTag + " is not whitelisted");
			event.getChannel().sendMessage("You're not whitelisted").queue();
			return;
		}
		
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
								List<File> schematics = Converter.litematicToWorldEdit(inputFile, outputDir);
								List<String> lines = new ArrayList<>();
								for (File schem : schematics) {
									copyToSchematicFolders(schem);
									lines.add("Uploaded " + schem.getName());
								}
								msg = String.join("\n", lines);
							}
							// sanitize worldedit schematic
							else {
								Sanitizer.sanitize(inputFile);
								copyToSchematicFolders(inputFile);
								String outputFile = inputFile.getName();
								msg = "Uploaded " + outputFile;
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
					String msg = "Failed to download " + filename;
					plugin.getLogger().info(msg);
					event.getChannel().sendMessage(msg).queue();
				}
			}
		}
		plugin.downloadedBytes.put(userId, userBytes);
	}
	
	private void copyToSchematicFolders(File file) throws IOException {
		String pluginsDir = plugin.getDataFolder().getParent();
		String worldEditDir = pluginsDir + "/WorldEdit/schematics";
		String faweDir = pluginsDir + "/FastAsyncWorldEdit/schematics";
		copyTo(file, worldEditDir);
		copyTo(file, faweDir);
	}
	
	private void copyTo(File file, String dir) throws IOException {
		Files.createDirectories(Paths.get(dir));
		String destinationName = dir + "/" + file.getName();
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
	}

}
