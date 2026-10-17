package net.shadowmage.ancientwarfare.npc.gamedata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * State of the dynamic diplomacy (see Diplomacy): war exhaustion of both sides, start of the current relation and truce, for each pair
 * of factions (keyed like {@link FactionRelationData}, names in alphabetical order). The relation itself is in FactionRelationData.
 */
public class DiplomacyData extends WorldSavedData {
	private final Map<String, Entry> entries = new TreeMap<>();
	private boolean initialized;
	private long lastDay = -1;

	public DiplomacyData(String name) {
		super(name);
	}

	public boolean isInitialized() {
		return initialized;
	}

	public void setInitialized() {
		initialized = true;
		markDirty();
	}

	public long getLastDay() {
		return lastDay;
	}

	public void setLastDay(long lastDay) {
		this.lastDay = lastDay;
		markDirty();
	}

	/**
	 * @return the entry of the pair, created if missing
	 */
	public Entry get(String factionA, String factionB) {
		return entries.computeIfAbsent(FactionRelationData.getKey(factionA, factionB), k -> new Entry());
	}

	public Map<String, Entry> getAll() {
		return Collections.unmodifiableMap(entries);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		entries.clear();
		initialized = tag.getBoolean("initialized");
		lastDay = tag.hasKey("lastDay") ? tag.getLong("lastDay") : -1;
		NBTTagList list = tag.getTagList("pairs", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound entryTag = list.getCompoundTagAt(i);
			Entry entry = new Entry();
			entry.since = entryTag.getLong("since");
			entry.exhaustionFirst = entryTag.getInteger("exhaustionFirst");
			entry.exhaustionSecond = entryTag.getInteger("exhaustionSecond");
			entry.truceUntil = entryTag.getLong("truceUntil");
			entries.put(entryTag.getString("key"), entry);
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag.setBoolean("initialized", initialized);
		tag.setLong("lastDay", lastDay);
		NBTTagList list = new NBTTagList();
		for (Map.Entry<String, Entry> e : entries.entrySet()) {
			NBTTagCompound entryTag = new NBTTagCompound();
			entryTag.setString("key", e.getKey());
			entryTag.setLong("since", e.getValue().since);
			entryTag.setInteger("exhaustionFirst", e.getValue().exhaustionFirst);
			entryTag.setInteger("exhaustionSecond", e.getValue().exhaustionSecond);
			entryTag.setLong("truceUntil", e.getValue().truceUntil);
			list.appendTag(entryTag);
		}
		tag.setTag("pairs", list);
		return tag;
	}

	/**
	 * Diplomacy of a pair of factions. "First" and "second" follow the alphabetical order of the key.
	 */
	public static class Entry {
		private long since;
		private int exhaustionFirst;
		private int exhaustionSecond;
		private long truceUntil;

		public long getSince() {
			return since;
		}

		public void setSince(long since) {
			this.since = since;
		}

		public long getTruceUntil() {
			return truceUntil;
		}

		public void setTruceUntil(long truceUntil) {
			this.truceUntil = truceUntil;
		}

		/**
		 * @return the war exhaustion of the given side of the pair (factionA and factionB as passed to DiplomacyData.get)
		 */
		public int getExhaustion(String faction, String otherFaction) {
			return faction.compareTo(otherFaction) <= 0 ? exhaustionFirst : exhaustionSecond;
		}

		public void setExhaustion(String faction, String otherFaction, int value) {
			if (faction.compareTo(otherFaction) <= 0) {
				exhaustionFirst = Math.max(0, value);
			} else {
				exhaustionSecond = Math.max(0, value);
			}
		}

		public int getMaxExhaustion() {
			return Math.max(exhaustionFirst, exhaustionSecond);
		}

		public void resetExhaustion() {
			exhaustionFirst = 0;
			exhaustionSecond = 0;
		}
	}
}
