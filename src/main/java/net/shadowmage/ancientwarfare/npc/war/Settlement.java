package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
	private String structureName = "";
	private String faction = "";
	private BlockPos center = BlockPos.ORIGIN;
	private BlockPos min = BlockPos.ORIGIN;
	private BlockPos max = BlockPos.ORIGIN;
	@Nullable
	private UUID leaderId;
	private BlockPos lastLeaderPos = BlockPos.ORIGIN;
	private int manpower;
	private int maxManpower;
	private int manpowerCap;
	private int garrisonCap;
	private long lastRegenDay;
	private int activeSiegeId;
	private long lastSiegeEnd;
	private long lastFullSiegeEnd;
	private final List<PendingRespawn> pendingRespawns = new ArrayList<>();
	/**
	 * Players who served in the attack that conquered the city, with the tick their war booty ends (its chests are open to them)
	 */
	private final Map<UUID, Long> plunderers = new HashMap<>();

	// not saved: consecutive checks where the leader could not be found although its area was loaded
	int missingLeaderChecks = 0;

	public Settlement(int id) {
		this.id = id;
	}

	public static Settlement createFactionCity(int id, int dimension, String name, String structureName, String faction, BlockPos min, BlockPos max,
			@Nullable UUID leaderId, BlockPos leaderPos, int manpower, long day) {
		Settlement settlement = new Settlement(id);
		settlement.type = Type.FACTION_CITY;
		settlement.dimension = dimension;
		settlement.name = name;
		settlement.structureName = structureName;
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

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the name of the Ancient Warfare structure the city was founded in, or an empty string
	 */
	public String getStructureName() {
		return structureName;
	}

	public String getFaction() {
		return faction;
	}

	void setFaction(String faction) {
		this.faction = faction;
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

	void setLeader(@Nullable UUID leaderId, BlockPos pos) {
		this.leaderId = leaderId;
		this.lastLeaderPos = pos;
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

	/**
	 * @return the maximum manpower a conquered city slowly grows back to (its maximum before the conquest), 0 if none
	 */
	public int getManpowerCap() {
		return manpowerCap;
	}

	void setManpowerCap(int manpowerCap) {
		this.manpowerCap = Math.max(0, manpowerCap);
	}

	/**
	 * @return the garrison size of a conquered city before the conquest, reinforcements come until it is reached again; 0 if none
	 */
	public int getGarrisonCap() {
		return garrisonCap;
	}

	void setGarrisonCap(int garrisonCap) {
		this.garrisonCap = Math.max(0, garrisonCap);
	}

	long getLastRegenDay() {
		return lastRegenDay;
	}

	void setLastRegenDay(long lastRegenDay) {
		this.lastRegenDay = lastRegenDay;
	}

	/**
	 * @return the id of the siege against this settlement, 0 if there is none
	 */
	public int getActiveSiegeId() {
		return activeSiegeId;
	}

	void setActiveSiegeId(int activeSiegeId) {
		this.activeSiegeId = activeSiegeId;
	}

	public long getLastSiegeEnd() {
		return lastSiegeEnd;
	}

	void setLastSiegeEnd(long lastSiegeEnd) {
		this.lastSiegeEnd = lastSiegeEnd;
	}

	/**
	 * @return the tick the last full siege (with siege engines) ended, or the city was founded
	 */
	public long getLastFullSiegeEnd() {
		return lastFullSiegeEnd;
	}

	void setLastFullSiegeEnd(long lastFullSiegeEnd) {
		this.lastFullSiegeEnd = lastFullSiegeEnd;
	}

	public List<PendingRespawn> getPendingRespawns() {
		return pendingRespawns;
	}

	/**
	 * @return true if the player may plunder the city at the given tick (war booty)
	 */
	public boolean isPlunderer(UUID playerId, long now) {
		Long until = plunderers.get(playerId);
		return until != null && now < until;
	}

	void addPlunderer(UUID playerId, long until, long now) {
		plunderers.values().removeIf(end -> end <= now);
		plunderers.put(playerId, until);
	}

	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag.setInteger("id", id);
		tag.setString("type", type.getName());
		tag.setString("status", status.getName());
		tag.setInteger("dimension", dimension);
		tag.setString("name", name);
		tag.setString("structureName", structureName);
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
		tag.setInteger("manpowerCap", manpowerCap);
		tag.setInteger("garrisonCap", garrisonCap);
		tag.setLong("lastRegenDay", lastRegenDay);
		tag.setInteger("activeSiege", activeSiegeId);
		tag.setLong("lastSiegeEnd", lastSiegeEnd);
		tag.setLong("lastFullSiegeEnd", lastFullSiegeEnd);
		NBTTagList respawnList = new NBTTagList();
		for (PendingRespawn respawn : pendingRespawns) {
			NBTTagCompound respawnTag = new NBTTagCompound();
			respawnTag.setTag("entity", respawn.entityTag);
			respawnTag.setLong("due", respawn.dueTick);
			respawnList.appendTag(respawnTag);
		}
		tag.setTag("pendingRespawns", respawnList);
		NBTTagList plunderList = new NBTTagList();
		for (Map.Entry<UUID, Long> entry : plunderers.entrySet()) {
			NBTTagCompound plunderTag = new NBTTagCompound();
			plunderTag.setUniqueId("player", entry.getKey());
			plunderTag.setLong("until", entry.getValue());
			plunderList.appendTag(plunderTag);
		}
		tag.setTag("plunderers", plunderList);
		return tag;
	}

	public static Settlement readFromNBT(NBTTagCompound tag) {
		Settlement settlement = new Settlement(tag.getInteger("id"));
		settlement.type = Type.PLAYER_TOWN.getName().equals(tag.getString("type")) ? Type.PLAYER_TOWN : Type.FACTION_CITY;
		settlement.status = Status.ABANDONED.getName().equals(tag.getString("status")) ? Status.ABANDONED : Status.ACTIVE;
		settlement.dimension = tag.getInteger("dimension");
		if (tag.hasKey("structureName")) {
			settlement.name = tag.getString("name");
			settlement.structureName = tag.getString("structureName");
		} else {
			// saved before cities had their own names: "name" was the structure name
			settlement.structureName = tag.getString("name");
		}
		settlement.faction = tag.getString("faction");
		settlement.center = BlockPos.fromLong(tag.getLong("center"));
		settlement.min = BlockPos.fromLong(tag.getLong("min"));
		settlement.max = BlockPos.fromLong(tag.getLong("max"));
		settlement.leaderId = tag.hasUniqueId("leader") ? tag.getUniqueId("leader") : null;
		settlement.lastLeaderPos = BlockPos.fromLong(tag.getLong("lastLeaderPos"));
		settlement.manpower = tag.getInteger("manpower");
		settlement.maxManpower = tag.getInteger("maxManpower");
		settlement.manpowerCap = tag.getInteger("manpowerCap");
		settlement.garrisonCap = tag.getInteger("garrisonCap");
		settlement.lastRegenDay = tag.getLong("lastRegenDay");
		settlement.activeSiegeId = tag.getInteger("activeSiege");
		settlement.lastSiegeEnd = tag.getLong("lastSiegeEnd");
		settlement.lastFullSiegeEnd = tag.getLong("lastFullSiegeEnd");
		NBTTagList respawnList = tag.getTagList("pendingRespawns", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < respawnList.tagCount(); i++) {
			NBTTagCompound respawnTag = respawnList.getCompoundTagAt(i);
			settlement.pendingRespawns.add(new PendingRespawn(respawnTag.getCompoundTag("entity"), respawnTag.getLong("due")));
		}
		NBTTagList plunderList = tag.getTagList("plunderers", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < plunderList.tagCount(); i++) {
			NBTTagCompound plunderTag = plunderList.getCompoundTagAt(i);
			UUID playerId = plunderTag.getUniqueId("player");
			if (playerId != null) {
				settlement.plunderers.put(playerId, plunderTag.getLong("until"));
			}
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
