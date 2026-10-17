package net.shadowmage.ancientwarfare.npc.gamedata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Armies led by players (see Armies): for each commander, the city its levy was raised from and the city it is charging (0 if none).
 * The soldiers are not saved here: each carries its commander in its persistent entity data.
 */
public class ArmyData extends WorldSavedData {
	private final Map<UUID, Army> armies = new HashMap<>();

	public ArmyData(String name) {
		super(name);
	}

	@Nullable
	public Army get(UUID commander) {
		return armies.get(commander);
	}

	public Army getOrCreate(UUID commander) {
		markDirty();
		return armies.computeIfAbsent(commander, k -> new Army());
	}

	public void remove(UUID commander) {
		if (armies.remove(commander) != null) {
			markDirty();
		}
	}

	public Map<UUID, Army> getAll() {
		return Collections.unmodifiableMap(armies);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		armies.clear();
		NBTTagList list = tag.getTagList("armies", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound armyTag = list.getCompoundTagAt(i);
			Army army = new Army();
			army.originCity = armyTag.getInteger("origin");
			army.targetCity = armyTag.getInteger("target");
			armies.put(armyTag.getUniqueId("commander"), army);
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		NBTTagList list = new NBTTagList();
		for (Map.Entry<UUID, Army> e : armies.entrySet()) {
			NBTTagCompound armyTag = new NBTTagCompound();
			armyTag.setUniqueId("commander", e.getKey());
			armyTag.setInteger("origin", e.getValue().originCity);
			armyTag.setInteger("target", e.getValue().targetCity);
			list.appendTag(armyTag);
		}
		tag.setTag("armies", list);
		return tag;
	}

	public static class Army {
		private int originCity;
		private int targetCity;

		public int getOriginCity() {
			return originCity;
		}

		public void setOriginCity(int originCity) {
			this.originCity = originCity;
		}

		/**
		 * @return the city the army is charging, 0 if it follows its commander
		 */
		public int getTargetCity() {
			return targetCity;
		}

		public void setTargetCity(int targetCity) {
			this.targetCity = targetCity;
		}
	}
}
