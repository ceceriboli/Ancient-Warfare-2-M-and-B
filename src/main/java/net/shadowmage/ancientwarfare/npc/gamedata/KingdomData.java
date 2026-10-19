package net.shadowmage.ancientwarfare.npc.gamedata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player kingdoms: factions created during the game. A kingdom faction is never removed, so that its npcs can still be loaded after
 * it is destroyed.
 */
public class KingdomData extends WorldSavedData {
	private final Map<String, Kingdom> kingdoms = new LinkedHashMap<>();
	private int nextId = 1;

	public KingdomData(String name) {
		super(name);
	}

	public Kingdom create(UUID ownerId, String ownerName, String name, String culture, int color) {
		String id;
		do {
			id = Kingdoms.ID_PREFIX + nextId++;
		} while (kingdoms.containsKey(id));
		Kingdom kingdom = new Kingdom(id, name, culture, color, ownerId, ownerName);
		kingdoms.put(id, kingdom);
		markDirty();
		return kingdom;
	}

	@Nullable
	public Kingdom get(String id) {
		return kingdoms.get(id);
	}

	@Nullable
	public Kingdom getOwnedBy(UUID ownerId) {
		return kingdoms.values().stream().filter(k -> !k.destroyed && ownerId.equals(k.ownerId)).findFirst().orElse(null);
	}

	public Collection<Kingdom> getAll() {
		return Collections.unmodifiableCollection(kingdoms.values());
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		kingdoms.clear();
		nextId = Math.max(1, tag.getInteger("nextId"));
		NBTTagList list = tag.getTagList("kingdoms", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			Kingdom kingdom = Kingdom.read(list.getCompoundTagAt(i));
			kingdoms.put(kingdom.id, kingdom);
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag.setInteger("nextId", nextId);
		NBTTagList list = new NBTTagList();
		kingdoms.values().forEach(k -> list.appendTag(k.write(new NBTTagCompound())));
		tag.setTag("kingdoms", list);
		return tag;
	}

	public static class Kingdom {
		public final String id;
		public String name;
		public String culture;
		public int color;
		public final UUID ownerId;
		public String ownerName;
		public int cityId = -1;
		public boolean destroyed = false;
		// Minecraft day until which the capital cannot be attacked (the starting truce), see Kingdoms.isInGrace
		public long graceUntil = 0;

		public Kingdom(String id, String name, String culture, int color, UUID ownerId, String ownerName) {
			this.id = id;
			this.name = name;
			this.culture = culture;
			this.color = color;
			this.ownerId = ownerId;
			this.ownerName = ownerName;
		}

		NBTTagCompound write(NBTTagCompound tag) {
			tag.setString("id", id);
			tag.setString("name", name);
			tag.setString("culture", culture);
			tag.setInteger("color", color);
			tag.setUniqueId("owner", ownerId);
			tag.setString("ownerName", ownerName);
			tag.setInteger("city", cityId);
			tag.setBoolean("destroyed", destroyed);
			tag.setLong("graceUntil", graceUntil);
			return tag;
		}

		static Kingdom read(NBTTagCompound tag) {
			Kingdom kingdom = new Kingdom(tag.getString("id"), tag.getString("name"), tag.getString("culture"), tag.getInteger("color"),
					tag.getUniqueId("owner"), tag.getString("ownerName"));
			kingdom.cityId = tag.getInteger("city");
			kingdom.destroyed = tag.getBoolean("destroyed");
			kingdom.graceUntil = tag.getLong("graceUntil");
			return kingdom;
		}
	}
}
