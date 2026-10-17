package net.shadowmage.ancientwarfare.npc.gamedata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.shadowmage.ancientwarfare.npc.war.Settlement;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Faction cities and player towns of all dimensions.
 */
public class SettlementData extends WorldSavedData {
	private final Map<Integer, Settlement> settlements = new TreeMap<>();
	private int nextId = 1;

	public SettlementData(String name) {
		super(name);
	}

	public int getNextId() {
		return nextId++;
	}

	public void add(Settlement settlement) {
		settlements.put(settlement.getId(), settlement);
		markDirty();
	}

	@Nullable
	public Settlement get(int id) {
		return settlements.get(id);
	}

	public Collection<Settlement> getAll() {
		return Collections.unmodifiableCollection(settlements.values());
	}

	/**
	 * @return the active settlement of the given type containing pos, or null
	 */
	@Nullable
	public Settlement getActiveAt(int dimension, BlockPos pos, Settlement.Type type) {
		for (Settlement settlement : settlements.values()) {
			if (settlement.isActive() && settlement.getType() == type && settlement.contains(dimension, pos)) {
				return settlement;
			}
		}
		return null;
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		settlements.clear();
		nextId = Math.max(1, tag.getInteger("nextId"));
		NBTTagList list = tag.getTagList("settlements", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			Settlement settlement = Settlement.readFromNBT(list.getCompoundTagAt(i));
			settlements.put(settlement.getId(), settlement);
			nextId = Math.max(nextId, settlement.getId() + 1);
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag.setInteger("nextId", nextId);
		NBTTagList list = new NBTTagList();
		for (Settlement settlement : settlements.values()) {
			list.appendTag(settlement.writeToNBT(new NBTTagCompound()));
		}
		tag.setTag("settlements", list);
		return tag;
	}
}
