package org.inventivetalent.schematic2structure;

import org.inventivetalent.nbt.*;
import org.inventivetalent.nbt.stream.NBTInputStream;
import org.inventivetalent.nbt.stream.NBTOutputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

/// Forked from https://github.com/TaylorWilton/Schematic2Structure
public class Schematic2Structure {

	/**
	 * Main Method
	 *
	 * @param args - the file name of the schematic
	 */
	public static void main(String[] args) {
		File schematicFile;

		if (!validateSchematicFile(args[0])) {
			String usage = "Usage: Schematic2Structure <schematic-file> <output-file>\n" +
					"\t\t<schematic-file> - a Schematic File created by a program like MCEdit or MC Noteblock Studio, with the .schematic extension\n" +
					"\t\t<output-location> - where you want the resulting nbt structure to be saved\n" +
					"\t\t(this is optional, and if not provided, will default to the name of the schematic file with the .nbt extension)";

			System.out.println(usage);
			return;
		} else {
			schematicFile = new File(args[0]);
			if (!schematicFile.exists()) {
				System.err.println("File not found");
				return;
			}
			System.out.println("Converting Schematic file " + schematicFile);
		}

		// hashmaps for blocks & properties respectively
		HashMap<Integer, String> blockMap = new HashMap<>();
		HashMap<String, String> propertiesMap = new HashMap<>();

		HashMap<Integer, Block> palette = new HashMap<>();
		ArrayList<Block> structureBlocks = new ArrayList<>();

		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Schematic2Structure.class.getResourceAsStream("/blocks.csv")))) {
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				/*
				 * Split the line
				 * The data format is as follows:
				 * data[0] - the block id
				 * data[1] - the English name of the block (ex Stone)
				 * data[2] - the Minecraft name of the block (ex minecraft:stone)
				 *
				 * data[1] currently has no use
				 */

				String[] data = line.split(",");
				// the blockID that Minecraft uses
				int id = Integer.parseInt(data[0]);
				// the block name that Minecraft uses (ex minecraft:stone, rather than Stone)
				String blockName = data[2];

				// add to the map
				blockMap.put(id, blockName);

			}
		} catch (IOException ex) {
			System.out.println("Failed to read block info file");
			ex.printStackTrace();
			return;
		}

		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Schematic2Structure.class.getResourceAsStream("/properties.csv")))) {
			// Read the properties file
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				String[] data = line.split("\\|");

				String key = data[0];
				String properties = data[1];
				propertiesMap.put(key, properties);
			}
		} catch (IOException ex) {
			System.out.println("Failed to read properties info file");
			ex.printStackTrace();
			return;
		}

		try (NBTInputStream nbtIn = new NBTInputStream(new FileInputStream(schematicFile), true)) {
			CompoundTag schematicMap = (CompoundTag) nbtIn.readNBTTag();

			short width = (short) schematicMap.get("Width").getValue();// x
			short height = (short) schematicMap.get("Height").getValue();// y
			short length = (short) schematicMap.get("Length").getValue();// z

			// validate the dimensions to ensure that structure is the right size
			// (i.e 32 blocks or smaller in each dimension)
			// this is done now, so that time isn't wasted reading all the blocks
			if (!validateStructure(width, height, length)) {
				System.out.println("Structure is too large!");
				return;
			}

			// get entities & tile entities, but keep them in their current form for now
			ListTag<CompoundTag> entities;
			ListTag<CompoundTag> tileEntitles;
			// looks like this occasionally throws exceptions about the lists being of the wrong type when empty, so just create new ones
			try {
			 entities = schematicMap.getOrCreateList("Entities", CompoundTag.class);
			} catch (IllegalArgumentException ignored) {
				entities = new ListTag<>(TagID.TAG_COMPOUND, "Entities");
			}
			try {
				tileEntitles = schematicMap.getOrCreateList("TileEntities", CompoundTag.class);
			} catch (IllegalArgumentException ignored) {
				tileEntitles = new ListTag<>(TagID.TAG_COMPOUND, "Entities");
			}

			// set up size list now, because we have the data and we can move on now
			ListTag<IntTag> sizeListTag = new ListTag<>(TagID.TAG_INT, "size");
			sizeListTag.add(new IntTag(width));
			sizeListTag.add(new IntTag(height));
			sizeListTag.add(new IntTag(length));

			ListTag<CompoundTag> entityListTag = new ListTag<>(TagID.TAG_COMPOUND, "entities");
			// loop through the entities - think of the overhead
			for (CompoundTag ct : entities) {
				Entity e = new Entity(ct);
				// get the compound tag
				CompoundTag entityTag = e.getStructureFormat();
				// chuck it in the list
				entityListTag.add(entityTag);
			}
			// do it all over again for tile entities
			for (CompoundTag ct : tileEntitles) {
				TileEntity e = new TileEntity(ct);
				CompoundTag entityTag = e.getStructureFormat();
				entityListTag.add(entityTag);
			}

			byte[] schematicBlocks = (byte[]) schematicMap.get("Blocks").getValue();
			byte[] schematicBlockData = (byte[]) schematicMap.get("Data").getValue();
			for (int i = 0; i < schematicBlocks.length; i++) {
				int blockId = schematicBlocks[i] & 0xff;
				int data = schematicBlockData[i] & 0xff;

				String name = blockMap.get(blockId);

				Block b = new Block(blockId, name, data);

				// add to the list of blocks
				structureBlocks.add(b);

				int hash = b.hashCode();
				String key = b.getKey();
				// get associated properties and set them for the block
				if (propertiesMap.containsKey(key)) {
					b.setProperties(propertiesMap.get(key));
				}
				// if the block isn't already in the palette, add it now
				if (!palette.containsKey(hash)) {
					palette.put(hash, b);
				}

			}

			// ArrayList of compound tags, that will eventually become part of the palette in the structure
			ListTag<CompoundTag> paletteCompoundList = new ListTag<>(TagID.TAG_COMPOUND, "palette");
			// Loop over items in palette
			for (int i = 0; i < palette.values().toArray().length; i++) {
				// current block
				Block current = (Block) (palette.values().toArray())[i];

				// properties
				String blockProperties = current.getProperties();

				// if the block has properties then loop though them and add them to a compound list
				if (blockProperties != null && blockProperties.length() > 0) {
					HashMap<String, NBTTag> blockMapCompound = new HashMap<>();
					String[] blockPropertiesArray = blockProperties.split(",");
					HashMap<String, NBTTag> propertiesMapCompound = new HashMap<>();

					// Loop through the block properties
					for (int j = 0; j < blockPropertiesArray.length; j++) {
						String blockProperty = blockPropertiesArray[j];
						String[] result = blockProperty.split(":");
						propertiesMapCompound.put(String.valueOf(j), new StringTag(result[0], result[1]));
					}
					blockMapCompound.put("Properties", new CompoundTag("Properties", propertiesMapCompound));
					blockMapCompound.put("Name", new StringTag("Name", current.getName()));
					paletteCompoundList.add(new CompoundTag("block", blockMapCompound));

				} else { // otherwise just make a compound tag.
					HashMap<String, NBTTag> blockMapCompound = new HashMap<>();
					blockMapCompound.put("Name", new StringTag("Name", current.getName()));
					paletteCompoundList.add(new CompoundTag("block", blockMapCompound));
				}

			}
			ListTag<CompoundTag> blockCompoundList = new ListTag<>(TagID.TAG_COMPOUND, "blocks");

			Object[] hashesArray = palette.keySet().toArray();
			ArrayList<Integer> paletteHashes = new ArrayList<>();
			for (Object aHashesArray : hashesArray) {
				paletteHashes.add((Integer) aHashesArray);
			}

			// loop over all the blocks
			// Sorted by height (bottom to top) then length then width -- the index of the block at X,Y,Z is (Y×length + Z)×width + X
			for (int z = 0; z < length; z++) {
				for (int y = 0; y < width; y++) {
					for (int x = 0; x < height; x++) {
						int position = (y * length + z) * height + x;
						Block current = structureBlocks.get(position);

						int index = paletteHashes.indexOf(current.hashCode());

						CompoundTag blockCompound = new CompoundTag();
						ListTag<IntTag> pos = new ListTag<>(TagID.TAG_INT, "pos");

						pos.add(new IntTag("x", z));
						pos.add(new IntTag("y", y));
						pos.add(new IntTag("z", x));

						blockCompound.set("pos", new ListTag(TagID.TAG_INT, "pos"));
						blockCompound.set("state", new IntTag("state", index));

						blockCompoundList.add(blockCompound);
					}
				}
			}

			HashMap<String, NBTTag> structureMap = new HashMap<>();
			structureMap.put("blocks", blockCompoundList);
			structureMap.put("palette", paletteCompoundList);
			structureMap.put("entities", entityListTag);
			structureMap.put("size", sizeListTag);
			structureMap.put("author", new StringTag("author", "inventivetalent"));
			structureMap.put("version", new IntTag("version", 1));

			CompoundTag structureTag = new CompoundTag("structure", structureMap);

			String output = (schematicFile.getPath().split("\\."))[0] + ".nbt";
			System.out.println("Structure saved at " + output);
			FileOutputStream fos = new FileOutputStream(output);
			NBTOutputStream NBToutput = new NBTOutputStream(fos);

			NBToutput.writeTag(structureTag);
			NBToutput.close();

		} catch (FileNotFoundException ex) {
			System.out.println("Unable to open file " + ex.getMessage());
			ex.printStackTrace();
		} catch (Exception ex) {
			System.out.println("Error reading file");
			ex.printStackTrace();
		}
	}

	/**
	 * validates the structure parameters to make sure that the structure is a valid size
	 *
	 * @param h height
	 * @param w width
	 * @param l length
	 * @return true if valid, false if invalid
	 */
	static boolean validateStructure(int h, int w, int l) {
		return ((h < 33 && w < 33 && l < 33) && (h > 0 && w > 0 && l > 0));
	}

	/**
	 * Validation of the schematic filename first - test to see if it ends with '.schematic'
	 *
	 * @param schematicFilename the name of the file we're opening
	 * @return whether the file is a valid schematic or not
	 */
	static boolean validateSchematicFile(String schematicFilename) {
		return schematicFilename.endsWith(".schematic");
	}

}
