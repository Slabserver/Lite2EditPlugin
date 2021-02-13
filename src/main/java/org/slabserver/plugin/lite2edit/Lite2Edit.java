package org.slabserver.plugin.lite2edit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.yaml.snakeyaml.Yaml;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class Lite2Edit extends JavaPlugin {
	private JDA jda;
	private long whitelistedRoleId;

	public Lite2Edit() {
		
	}

	public Lite2Edit(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
		super(loader, description, dataFolder, file);
	}
	
	@Override
	public void onEnable() {
		try {
			String token = "";
			Files.createDirectories(getDataFolder().toPath());
			File config = new File(getDataFolder() + "/config.yml");
			if (config.exists()) {
				Map<String, Object> options = new Yaml().load(new FileInputStream(config));
				token = (String) options.get("token");
				whitelistedRoleId = (long) options.get("whitelistedRoleId");
			}
			else {
				Files.write(config.toPath(), "token: \nwhitelistedRoleId: \n".getBytes(StandardCharsets.UTF_8));
			}
			jda = JDABuilder.createDefault(token).build();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		jda.addEventListener(new ListenerAdapter() {
			@Override
			public void onPrivateMessageReceived(PrivateMessageReceivedEvent event) {
				if (event.getAuthor().equals(jda.getSelfUser()))
					return;
				Role whitelistedRole = jda.getRoleById(whitelistedRoleId);
				boolean hasWhitelistedRole = whitelistedRole != null 
						&& whitelistedRole.getGuild().retrieveMember(event.getAuthor())
							.complete().getRoles().contains(whitelistedRole);
				if (!hasWhitelistedRole)
					return;
				
				List<Attachment> attachments = event.getMessage().getAttachments();
				for (Attachment att : attachments) {
					String filename = att.getFileName();
					if (filename.endsWith(".litematic")) {
						/*
						 * download all litematics
						 * convert to schematics
						 * move to worldedit folder
						 * delete original
						 */
						File outputDir = new File(getDataFolder() + "/" + att.getId());
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
									event.getChannel().sendMessage(msg).queue();
									deleteDir(outputDir);
								});
					}
					else if (filename.endsWith(".schem")) {
						/*
						 * download schematics
						 * move to worldedit folder
						 * delete folder
						 */
						File outputDir = new File(getDataFolder() + "/" + att.getId());
						att.downloadToFile(outputDir + "/" + att.getFileName())
								.thenAccept(inputFile -> {
									String msg;
									try {
										String outputFile = moveToWorldEditDir(inputFile).getName();
										msg = "Uploaded " + outputFile;
									} catch (IOException e) {
										e.printStackTrace();
										msg = "IO Exception";
									}
									event.getChannel().sendMessage(msg).queue();
									deleteDir(outputDir);
								});
					}
				}
			}

		});
	}

	@Override
	public void onDisable() {
		if (jda != null)
			jda.shutdownNow();
	}
	
	private void deleteDir(File dir) {
		for (File file : dir.listFiles())
			file.delete();
		dir.delete();
	}
	
	private Path getWorldEditDir() {
		return getDataFolder().toPath().resolveSibling("/WorldEdit/schematics");
	}
	
	private File moveToWorldEditDir(File file) throws IOException {
		String destinationName = getWorldEditDir() + "/" + file.getName();
		File destination = new File(destinationName);
		for (int i = 1; i < 1000 && destination.exists(); ++i) {
			destination = new File(destinationName + "(" + (i) + ")");
		}
		Files.move(file.toPath(), destination.toPath());
		return destination;
	}

}
