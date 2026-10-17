package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A faction city (founded by a faction leader) with its manpower, or a player town.
 * While the leader lives, killed garrison units respawn using manpower; when the leader dies the city is abandoned.
 */
public class Settlement {
	public enum Type {
		FACTION_CITY, PLAYER_TOWN;

		public String getName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Status {
		ACTIVE, ABANDONED;

		public String getName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private final int id;
	private Type type = Type.FACTION_CITY;
	private Status status = Status.ACTIVE;
	private int dimension;
	private String name = "";
	private String faction = "";
	private BlockPos center = BlockPos.ORIGIN;
	private BlockPos min = BlockPos.ORIGIN;
	private BlockPos max = BlockPos.ORIGIN;
	@Nullable
	private UUID leaderId;
	private BlockPos lastLeaderPos = BlockPos.ORIGIN;
	private int manpower;
	private int maxManpower;
	private long lastRegenDay;
	private final List<PendingRespawn> pendingRespawns = new ArrayList<>();

	// not saved: consecutive checks where the leader could not be found although its area was loaded
	int missingLeaderChecks = 0;

	public Settlement(int id) {
		this.id = id;
	}

	public static Settlement createFactionCity(int id, int dimension, String name, String faction, BlockPos min, BlockPos max, UUID leaderId, BlockPos leaderPos,
			int manpower, long day) {
		Settlement settlement = new Settlement(id);
		settlement.type = Type.FACTION_CITY;
		settlement.dimension = dimension;
		settlement.name = name;
		settlement.faction = faction;
		settlement.min = min;
		settlement.max = max;
		settlement.center = new BlockPos((min.getX() + max.getX()) / 2, leaderPos.getY(), (min.getZ() + max.getZ()) / 2);
		settlement.leaderId = leaderId;
		settlement.lastLeaderPos = leaderPos;
		settlement.manpower = manpower;
		settlement.maxManpower = manpower;
		settlement.lastRegenDay = day;
		return settlement;
	}

	public int getId() {
		return id;
	}

	public Type getType() {
		return type;
	}

	public Status getStatus() {
		return status;
	}

	public boolean isActive() {
		return status == Status.ACTIVE;
	}

	void setAbandoned() {
		status = Status.ABANDONED;
		pendingRespawns.clear();
	}

	public int getDimension() {
		return dimension;
	}

	public String getName() {
		return name;
	}

	public String getFaction() {
		return faction;
	}

	public BlockPos getCenter() {
		return center;
	}

	public BlockPos getMin() {
		return min;
	}

	public BlockPos getMax() {
		return max;
	}

	public boolean contains(int dim, BlockPos pos) {
		return dim == dimension && pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
				&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}

	public AxisAlignedBB getAABB() {
		return new AxisAlignedBB(min, max.add(1, 1, 1));
	}

	@Nullable
	public UUID getLeaderId() {
		return leaderId;
	}

	public BlockPos getLastLeaderPos() {
		return lastLeaderPos;
	}

	void setLastLeaderPos(BlockPos lastLeaderPos) {
		this.lastLeaderPos = lastLeaderPos;
	}

	public int getManpower() {
		return manpower;
	}

	public int getMaxManpower() {
		return maxManpower;
	}

	void setManpower(int manpower) {
		this.manpower = Math.max(0, manpower);
	}

	void setMaxManpower(int maxManpower) {
		this.maxManpower = Math.max(0, maxManpower);
	}

	long getLastRegenDay() {
		return lastRegenDay;
	}

	void setLastRegenDay(long lastRegenDay) {
		this.lastRegenDay = lastRegenDay;
	}

	public List<PendingRespawn> getPendingRespawns() {
		return pendingRespawns;
	}

	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag.setInteger("id", id);
		tag.setString("type", type.getName());
		tag.setString("status", status.getName());
		tag.setInteger("dimension", dimension);
		tag.setString("name", name);
		tag.setString("faction", faction);
		tag.setLong("center", center.toLong());
		tag.setLong("min", min.toLong());
		tag.setLong("max", max.toLong());
		if (leaderId != null) {
			tag.setUniqueId("leader", leaderId);
		}
		tag.setLong("lastLeaderPos", lastLeaderPos.toLong());
		tag.setInteger("manpower", manpower);
		tag.setInteger("maxManpower", maxManpower);
		tag.setLong("lastRegenDay", lastRegenDay);
		NBTTagList respawnList = new NBTTagList();
		for (PendingRespawn respawn : pendingRespawns) {
			NBTTagCompound respawnTag = new NBTTagCompound();
			respawnTag.setTag("entity", respawn.entityTag);
			respawnTag.setLong("due", respawn.dueTick);
			respawnList.appendTag(respawnTag);
		}
		tag.setTag("pendingRespawns", respawnList);
		return tag;
	}

	public static Settlement readFromNBT(NBTTagCompound tag) {
		Settlement settlement = new Settlement(tag.getInteger("id"));
		settlement.type = Type.PLAYER_TOWN.getName().equals(tag.getString("type")) ? Type.PLAYER_TOWN : Type.FACTION_CITY;
		settlement.status = Status.ABANDONED.getName().equals(tag.getString("status")) ? Status.ABANDONED : Status.ACTIVE;
		settlement.dimension = tag.getInteger("dimension");
		settlement.name = tag.getString("name");
		settlement.faction = tag.getString("faction");
		settlement.center = BlockPos.fromLong(tag.getLong("center"));
		settlement.min = BlockPos.fromLong(tag.getLong("min"));
		settlement.max = BlockPos.fromLong(tag.getLong("max"));
		settlement.leaderId = tag.hasUniqueId("leader") ? tag.getUniqueId("leader") : null;
		settlement.lastLeaderPos = BlockPos.fromLong(tag.getLong("lastLeaderPos"));
		settlement.manpower = tag.getInteger("manpower");
		settlement.maxManpower = tag.getInteger("maxManpower");
		settlement.lastRegenDay = tag.getLong("lastRegenDay");
		NBTTagList respawnList = tag.getTagList("pendingRespawns", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < respawnList.tagCount(); i++) {
			NBTTagCompound respawnTag = respawnList.getCompoundTagAt(i);
			settlement.pendingRespawns.add(new PendingRespawn(respawnTag.getCompoundTag("entity"), respawnTag.getLong("due")));
		}
		return settlement;
	}

	public static class PendingRespawn {
		final NBTTagCompound entityTag;
		final long dueTick;

		PendingRespawn(NBTTagCompound entityTag, long dueTick) {
			this.entityTag = entityTag;
			this.dueTick = dueTick;
		}
	}
}
