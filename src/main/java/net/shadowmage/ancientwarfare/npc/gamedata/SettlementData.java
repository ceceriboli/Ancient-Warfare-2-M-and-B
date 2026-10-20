package net.shadowmage.ancientwarfare.npc.gamedata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.SettlementNames;
import net.shadowmage.ancientwarfare.npc.war.Siege;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Faction cities, player towns and sieges of all dimensions.
 */
public class SettlementData extends WorldSavedData {
	private final Map<Integer, Settlement> settlements = new TreeMap<>();
	private final Map<Integer, Siege> sieges = new TreeMap<>();
	private final Map<Integer, Integer> mergedInto = new TreeMap<>();
	private int nextId = 1;
	private int nextSiegeId = 1;

	public SettlementData(String name) {
		super(name);
	}

	public int getNextId() {
		return nextId++;
	}

	public int getNextSiegeId() {
		return nextSiegeId++;
	}

	public void add(Settlement settlement) {
		settlements.put(settlement.getId(), settlement);
		markDirty();
	}

	/**
	 * @return the settlement with the id, or the one it was merged into
	 */
	@Nullable
	public Settlement get(int id) {
		Settlement settlement = settlements.get(id);
		for (int hops = 0; settlement == null && mergedInto.containsKey(id) && hops < 32; hops++) {
			id = mergedInto.get(id);
			settlement = settlements.get(id);
		}
		return settlement;
	}

	/**
	 * Removes a settlement merged into another: its id keeps leading to the other one (units tagged with it).
	 */
	public void merge(Settlement absorbed, Settlement into) {
		settlements.remove(absorbed.getId());
		mergedInto.put(absorbed.getId(), into.getId());
		markDirty();
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

	/**
	 * @return a settlement of the given type containing pos, active or abandoned (the active one first), or null
	 */
	@Nullable
	public Settlement getAt(int dimension, BlockPos pos, Settlement.Type type) {
		Settlement active = getActiveAt(dimension, pos, type);
		if (active != null) {
			return active;
		}
		for (Settlement settlement : settlements.values()) {
			if (settlement.getType() == type && settlement.contains(dimension, pos)) {
				return settlement;
			}
		}
		return null;
	}

	public void addSiege(Siege siege) {
		sieges.put(siege.getId(), siege);
		markDirty();
	}

	public void removeSiege(Siege siege) {
		sieges.remove(siege.getId());
		markDirty();
	}

	@Nullable
	public Siege getSiege(int id) {
		return sieges.get(id);
	}

	public Collection<Siege> getSieges() {
		return Collections.unmodifiableCollection(sieges.values());
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		settlements.clear();
		sieges.clear();
		nextId = Math.max(1, tag.getInteger("nextId"));
		nextSiegeId = Math.max(1, tag.getInteger("nextSiegeId"));
		NBTTagList list = tag.getTagList("settlements", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			Settlement settlement = Settlement.readFromNBT(list.getCompoundTagAt(i));
			settlements.put(settlement.getId(), settlement);
			nextId = Math.max(nextId, settlement.getId() + 1);
		}
		for (Settlement settlement : settlements.values()) {
			if (settlement.getName().isEmpty()) {
				// saved before cities had their own names
				settlement.setName(SettlementNames.generate(new Random(settlement.getId() * 7919L + settlement.getCenter().toLong()), settlements.values(), settlement.getFaction()));
				markDirty();
			}
		}
		mergedInto.clear();
		NBTTagList mergedList = tag.getTagList("merged", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < mergedList.tagCount(); i++) {
			mergedInto.put(mergedList.getCompoundTagAt(i).getInteger("from"), mergedList.getCompoundTagAt(i).getInteger("into"));
		}
		NBTTagList siegeList = tag.getTagList("sieges", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < siegeList.tagCount(); i++) {
			Siege siege = Siege.readFromNBT(siegeList.getCompoundTagAt(i));
			sieges.put(siege.getId(), siege);
			nextSiegeId = Math.max(nextSiegeId, siege.getId() + 1);
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag.setInteger("nextId", nextId);
		tag.setInteger("nextSiegeId", nextSiegeId);
		NBTTagList list = new NBTTagList();
		for (Settlement settlement : settlements.values()) {
			list.appendTag(settlement.writeToNBT(new NBTTagCompound()));
		}
		tag.setTag("settlements", list);
		NBTTagList mergedList = new NBTTagList();
		mergedInto.forEach((from, into) -> {
			NBTTagCompound entry = new NBTTagCompound();
			entry.setInteger("from", from);
			entry.setInteger("into", into);
			mergedList.appendTag(entry);
		});
		tag.setTag("merged", mergedList);
		NBTTagList siegeList = new NBTTagList();
		for (Siege siege : sieges.values()) {
			siegeList.appendTag(siege.writeToNBT(new NBTTagCompound()));
		}
		tag.setTag("sieges", siegeList);
		return tag;
	}
}
