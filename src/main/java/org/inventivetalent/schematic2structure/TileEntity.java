package org.inventivetalent.schematic2structure;

import org.inventivetalent.nbt.*;

import java.util.Map;

public class TileEntity {
	// the tag in Structure format
	private CompoundTag tileEntityTag;

	public TileEntity(CompoundTag entityCompound) {
		CompoundTag newCompound = new CompoundTag("TileEntity");

		ListTag<DoubleTag> newPosList = new ListTag<>(TagID.TAG_DOUBLE, "pos");
		newPosList.add((DoubleTag) entityCompound.get("x"));
		newPosList.add((DoubleTag) entityCompound.get("y"));
		newPosList.add((DoubleTag) entityCompound.get("z"));

		// start combining everything together
		newCompound.set("blockPos", new ListTag(TagID.TAG_INT, "pos"));

		CompoundTag entityNbt = new CompoundTag("nbt");
		for (Map.Entry<String, NBTTag> entry : entityCompound) {
			if (entry.getKey().equals("Pos")) { continue; }
			entityNbt.set(entry.getKey(), entry.getValue());
		}
		newCompound.set("nbt", entityNbt);// hope this works

		tileEntityTag = newCompound;
	}

	/**
	 * getStructureFormat
	 *
	 * @return - a CompoundTag formatted correctly for Mojang's NBT Structure Specifications
	 */
	CompoundTag getStructureFormat() {
		return tileEntityTag;
	}

}
