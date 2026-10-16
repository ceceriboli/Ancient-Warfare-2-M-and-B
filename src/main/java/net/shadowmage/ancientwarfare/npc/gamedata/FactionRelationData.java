package net.shadowmage.ancientwarfare.npc.gamedata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Faction to faction relations set during the game. Relations are symmetric, one entry per pair of factions.
 * Pairs without an entry fall back to hostile_towards_factions from the faction registry.
 */
public class FactionRelationData extends WorldSavedData {
	private static final String KEY_SEPARATOR = "|";

	private final Map<String, FactionRelation> relations = new TreeMap<>();

	public FactionRelationData(String name) {
		super(name);
	}

	@Nullable
	public FactionRelation get(String factionA, String factionB) {
		return relations.get(getKey(factionA, factionB));
	}

	public void set(String factionA, String factionB, FactionRelation relation) {
		relations.put(getKey(factionA, factionB), relation);
		markDirty();
	}

	public void reset(String factionA, String factionB) {
		if (relations.remove(getKey(factionA, factionB)) != null) {
			markDirty();
		}
	}

	/**
	 * @return all relations set in this world, keyed by "factionA|factionB" with the names in alphabetical order
	 */
	public Map<String, FactionRelation> getAll() {
		return Collections.unmodifiableMap(relations);
	}

	public static String[] splitKey(String key) {
		int separator = key.indexOf(KEY_SEPARATOR);
		return new String[] {key.substring(0, separator), key.substring(separator + 1)};
	}

	public static String getKey(String factionA, String factionB) {
		return factionA.compareTo(factionB) <= 0 ? factionA + KEY_SEPARATOR + factionB : factionB + KEY_SEPARATOR + factionA;
	}

	/**
	 * Reads relations written by {@link #writeRelations}, also used to send the relations to clients.
	 */
	public static Map<String, FactionRelation> readRelations(NBTTagCompound tag) {
		Map<String, FactionRelation> ret = new TreeMap<>();
		NBTTagList relationList = tag.getTagList("relations", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < relationList.tagCount(); i++) {
			NBTTagCompound relationTag = relationList.getCompoundTagAt(i);
			FactionRelation relation = FactionRelation.fromName(relationTag.getString("relation"));
			if (relation != null) {
				ret.put(getKey(relationTag.getString("factionA"), relationTag.getString("factionB")), relation);
			}
		}
		return ret;
	}

	public static void writeRelations(NBTTagCompound tag, Map<String, FactionRelation> relations) {
		NBTTagList relationList = new NBTTagList();
		for (Map.Entry<String, FactionRelation> entry : relations.entrySet()) {
			String[] factions = splitKey(entry.getKey());
			NBTTagCompound relationTag = new NBTTagCompound();
			relationTag.setString("factionA", factions[0]);
			relationTag.setString("factionB", factions[1]);
			relationTag.setString("relation", entry.getValue().getName());
			relationList.appendTag(relationTag);
		}
		tag.setTag("relations", relationList);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		relations.clear();
		relations.putAll(readRelations(tag));
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		writeRelations(tag, relations);
		return tag;
	}
}
