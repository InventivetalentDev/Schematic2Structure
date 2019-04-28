package org.inventivetalent.schematic2structure;

import org.inventivetalent.nbt.*;

import java.util.Map;

class Entity {
    // the tag in structure format
    private CompoundTag entityTag;

    /**
     * Entity Class - deals with all the fun parts of NBT!
     *
     * @param entityCompound - the root tag of the entity
     */
    Entity(CompoundTag entityCompound) {
        CompoundTag newCompound = new CompoundTag("Entity");

        ListTag<DoubleTag> posList = entityCompound.getList("Pos", DoubleTag.class);

        ListTag<DoubleTag> newPosList = new ListTag<>( TagID.TAG_DOUBLE,"pos");
        newPosList.add(posList.get(0));
        newPosList.add(posList.get(1));
        newPosList.add(posList.get(2));

        newCompound.set("pos", newPosList);

        CompoundTag entityNbt = new CompoundTag("nbt");
        for (Map.Entry<String,NBTTag> entry : entityCompound) {
            if(entry.getKey().equals("Pos"))continue;
            entityNbt.set(entry.getKey(), entry.getValue());
        }
        newCompound.set("nbt", entityNbt);// hope this works

        entityTag = newCompound;
    }

    /**
     * getStructureFormat
     *
     * @return - a CompoundTag formatted correctly for Mojang's NBT Structure Specifications
     */
    CompoundTag getStructureFormat() {
        return entityTag;
    }
}
