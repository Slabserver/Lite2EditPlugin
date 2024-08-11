package org.slabserver.plugin.lite2edit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import se.llbit.nbt.ByteArrayTag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.IntTag;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.NamedTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.StringTag;
import se.llbit.nbt.Tag;

public class Sanitizer {

	public static File sanitize(File worldEditFile) throws IOException {
		DataInputStream inStream = new DataInputStream(new GZIPInputStream(new FileInputStream(worldEditFile)));
		CompoundTag worldEditRoot = CompoundTag.read(inStream).asCompound();
		inStream.close();
		
		worldEditRoot = sanitize(worldEditRoot);
		DataOutputStream outStream = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(worldEditFile)));
		worldEditRoot.write(outStream);
		outStream.close();
		return worldEditFile;
	}

	public static CompoundTag sanitize(CompoundTag worldEditRoot) {
		// find blocks to remove
		Set<Integer> blocksToRemove = new HashSet<>();
		CompoundTag worldEdit = worldEditRoot.iterator().next().unpack().asCompound();
		CompoundTag palette = worldEdit.get("Palette").asCompound();
		for (NamedTag namedTag : palette) {
			String blockName = namedTag.name();
			int index = blockName.indexOf('[');
			if (index > -1)
				blockName = blockName.substring(0, index);
			if (blockName.contains("command_block")
					|| blockName.contains("structure_block")
					|| blockName.contains("jigsaw")) {
				blocksToRemove.add(namedTag.unpack().intValue());
			}
		}
		
		// find tile entities to edit or remove
		boolean modifiedTileEntities = false;
		ListTag blockEntities = worldEdit.get("BlockEntities").asList();
		List<CompoundTag> newBlockEntities = new ArrayList<>();
		for (SpecificTag specificTag : blockEntities) {
			CompoundTag blockEntity = specificTag.asCompound();
			String id = null;
			int count = 0;
			for (NamedTag namedTag : blockEntity) {
				// both "Id" and "id" are valid in world edit apparently
				// may be case sensitive
				if (namedTag.name().equalsIgnoreCase("id")) {
					id = namedTag.unpack().stringValue();
					count++;
				}
			}
			if (count != 1) {
				modifiedTileEntities = true;
				continue;
			}
			switch (id) {
			// Signs: remove lines containing click events
			case "minecraft:sign":
			case "sign":
			case "minecraft:hanging_sign":
			case "hanging_sign":
				CompoundTag newBlockEntity = new CompoundTag();
				for (NamedTag namedTag : blockEntity) {
					String key = namedTag.name();
					String json;
					switch (key) {
					// pre 1.20 format
					case "Text1":
					case "Text2":
					case "Text3":
					case "Text4":
						json = namedTag.unpack().stringValue();
						if (json.contains("\"clickEvent\"")) {
							modifiedTileEntities = true;
							newBlockEntity.add(key, new StringTag(""));
						}
						else
							newBlockEntity.add(namedTag);
						break;
					
					// 1.20+ format
					case "back_text":
					case "front_text":
						CompoundTag text = namedTag.unpack().asCompound();
						ListTag messages = text.get("messages").asList();
						ListTag newMessages = new ListTag(Tag.TAG_STRING, Collections.emptyList());
						for (int i = 0; i < messages.size(); i++) {
							json = messages.get(i).stringValue();
							if (json.contains("\"clickEvent\"")) {
								modifiedTileEntities = true;
								newMessages.add(new StringTag(""));
							}
							else {
								newMessages.add(new StringTag(json));
							}
						}
						
						CompoundTag newText = new CompoundTag();
						newText.add("messages", newMessages);
						// copy remaining tags
						for (NamedTag tag : text) {
							if (!tag.name().equals("messages"))
								newText.add(tag);
						}
						newBlockEntity.add(key, newText);
						break;
					default:
						newBlockEntity.add(namedTag);
						break;
					}
				}
				newBlockEntities.add(newBlockEntity);
				break;
			
			// Remove OP-only blocks
			case "minecraft:command_block":
			case "command_block":
			case "minecraft:structure_block":
			case "structure_block":
			case "minecraft:jigsaw":
			case "jigsaw":
				modifiedTileEntities = true;
				break;
			
			// other tile entities are copied without modifications
			default:
				newBlockEntities.add(blockEntity);
				break;
			}
		}
		
		if (modifiedTileEntities || !blocksToRemove.isEmpty())
			Lite2Edit.getInstance().getLogger().info("Sanitizing schematic");
		
		byte[] blocks = worldEdit.get("BlockData").byteArray();
		if (!blocksToRemove.isEmpty()) {
			// check if stone is in the palette already
			// if not, replace first blacklisted block with stone
			Integer stoneId = null;
			for (NamedTag namedTag : palette) {
				if (namedTag.isNamed("minecraft:stone")) {
					stoneId = namedTag.unpack().intValue();
					break;
				}
			}
			if (stoneId == null) {
				CompoundTag newPalette = new CompoundTag();
				for (NamedTag tag : palette) {
					int blockId = tag.unpack().intValue();
					if (stoneId == null && blocksToRemove.contains(blockId)) {
						newPalette.add("minecraft:stone", new IntTag(blockId));
						stoneId = blockId;
					}
					else {
						newPalette.add(tag);
					}
				}
				palette = newPalette;
			}
			
			// replace blacklisted blocks with stone
			byte[] newBlocks = new byte[blocks.length * 2];
			int j = 0;
			for (int i = 0; i < blocks.length; ) {
				int block, blockToAdd;
				if (blocks[i] < 0)
					block = (blocks[i++] & 127) + (blocks[i++] * 128);
				else
					block = blocks[i++];
				if (blocksToRemove.contains(block))
					blockToAdd = stoneId;
				else
					blockToAdd = block;
				
				if (block > 127) {
					newBlocks[j++] = (byte) (blockToAdd | 128);
					newBlocks[j++] = (byte) (blockToAdd / 128);
				}
				else {
					newBlocks[j++] = (byte) blockToAdd;
				}
			}
			blocks = Arrays.copyOf(newBlocks, j);
		}
		
		CompoundTag newWorldEdit = new CompoundTag();
		newWorldEdit.add("Palette", palette);
		newWorldEdit.add("BlockEntities", new ListTag(Tag.TAG_COMPOUND, newBlockEntities));
		newWorldEdit.add("BlockData", new ByteArrayTag(blocks));
		for (NamedTag namedTag : worldEdit) {
			switch (namedTag.name()) {
			case "Palette":
			case "BlockEntities":
			case "BlockData":
			case "Entities":
				continue;
			default:
				newWorldEdit.add(namedTag);
			}
		}
		CompoundTag newWorldEditRoot = new CompoundTag();
		newWorldEditRoot.add("", newWorldEdit);
		return newWorldEditRoot;
	}

}
