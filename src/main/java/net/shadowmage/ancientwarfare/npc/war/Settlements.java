package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFactionLeader;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;
import net.shadowmage.ancientwarfare.structure.gamedata.StructureEntry;
import net.shadowmage.ancientwarfare.structure.gamedata.StructureMap;
import net.shadowmage.ancientwarfare.structure.gamedata.TownEntry;
import net.shadowmage.ancientwarfare.structure.gamedata.TownMap;
import net.shadowmage.ancientwarfare.structure.tile.SpawnerSettings;
import net.shadowmage.ancientwarfare.structure.tile.TileAdvancedSpawner;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Faction cities: a city is founded by a faction leader together with the faction npcs around it (its garrison).
 * While the leader lives, killed garrison units respawn using the city's manpower. When the leader dies the city falls:
 * the remaining units disband and the city stays abandoned.
 */
public class Settlements {
	private Settlements() {}

	public static final String TAG_SETTLEMENT = "awSettlement";
	public static final String TAG_ROLE = "awSettlementRole";
	public static final String TAG_SIEGE = "awSiege";
	public static final String TAG_ARMY = "awArmy";
	private static final String ROLE_LEADER = "leader";
	private static final String ROLE_GARRISON = "garrison";

	private static final int LEADER_REGISTRATION_DELAY = 40;
	private static final int LEADER_CHECK_INTERVAL = 1200;
	private static final int MISSING_LEADER_CHECKS = 3;
	private static final int DAY_TICKS = 24000;
	private static final int MIN_BOUNDS_BELOW = 24;
	private static final int MIN_BOUNDS_ABOVE = 40;
	private static final String[] RESPAWN_REMOVED_TAGS = {"UUIDMost", "UUIDLeast", "Pos", "Motion", "Health", "HurtTime", "HurtByTimestamp", "DeathTime",
			"AbsorptionAmount", "ActiveEffects", "Fire", "FallDistance", "Passengers", "Leashed", "Leash"};

	private static final List<Scheduled<NpcFactionLeader>> pendingLeaders = new ArrayList<>();
	private static final List<Scheduled<NpcFaction>> disbanding = new ArrayList<>();
	private static final List<Scheduled<NpcFaction>> turningBandits = new ArrayList<>();

	public static SettlementData getData(World world) {
		return AWGameData.INSTANCE.getData(world, SettlementData.class);
	}

	public static boolean isSiegeOrArmyUnit(Entity entity) {
		NBTTagCompound data = entity.getEntityData();
		return data.hasKey(TAG_SIEGE) || data.hasKey(TAG_ARMY);
	}

	@Nullable
	public static Settlement getSettlementOf(Entity entity) {
		NBTTagCompound data = entity.getEntityData();
		return data.hasKey(TAG_SETTLEMENT) ? getData(entity.world).get(data.getInteger(TAG_SETTLEMENT)) : null;
	}

	public static void onEntityJoinWorld(Entity entity) {
		if (!AWDiplomacyStatics.settlementsEnabled || entity.world.isRemote || !(entity instanceof NpcFaction)) {
			return;
		}
		NpcFaction npc = (NpcFaction) entity;
		if (npc.getEntityData().hasKey(TAG_SIEGE)) {
			Sieges.onAttackerJoinWorld(npc);
			return;
		}
		if (isSiegeOrArmyUnit(npc)) {
			return;
		}
		long now = npc.world.getTotalWorldTime();
		NBTTagCompound data = npc.getEntityData();
		if (data.hasKey(TAG_SETTLEMENT)) {
			Settlement settlement = getData(npc.world).get(data.getInteger(TAG_SETTLEMENT));
			if (settlement == null) {
				data.removeTag(TAG_SETTLEMENT);
				data.removeTag(TAG_ROLE);
			} else if (!settlement.isActive()) {
				// loaded again after its city fell
				leaveFallenCity(npc, now + 20 + npc.world.rand.nextInt(100));
			}
			return;
		}
		if (npc instanceof NpcFactionLeader) {
			pendingLeaders.add(new Scheduled<>((NpcFactionLeader) npc, now + LEADER_REGISTRATION_DELAY));
			return;
		}
		if (npc.isPassive()) {
			return;
		}
		Settlement settlement = getData(npc.world).getActiveAt(npc.world.provider.getDimension(), npc.getPosition(), Settlement.Type.FACTION_CITY);
		if (settlement != null && settlement.getFaction().equals(npc.getFaction())) {
			tag(npc, settlement, ROLE_GARRISON);
		}
	}

	public static void onLivingDeath(Entity entity, DamageSource source) {
		if (entity.world.isRemote || !(entity instanceof NpcFaction) || !entity.getEntityData().hasKey(TAG_SETTLEMENT)) {
			return;
		}
		NpcFaction npc = (NpcFaction) entity;
		SettlementData data = getData(npc.world);
		Settlement settlement = data.get(npc.getEntityData().getInteger(TAG_SETTLEMENT));
		if (settlement == null || !settlement.isActive()) {
			return;
		}
		String role = npc.getEntityData().getString(TAG_ROLE);
		if (ROLE_LEADER.equals(role) && npc.getUniqueID().equals(settlement.getLeaderId())) {
			Entity killer = source.getTrueSource();
			AncientWarfareNPC.LOG.info("Leader of city {} killed at {} by {} ({}{})", settlement.getId(), npc.getPosition(), source.getDamageType(),
					killer == null ? "no entity" : killer.getName(), killer instanceof NpcFaction ? ", faction " + ((NpcFaction) killer).getFaction()
							+ (isSiegeOrArmyUnit(killer) ? ", attacker" : "") : "");
			collapse(npc.world, data, settlement);
		} else if (ROLE_GARRISON.equals(role) && settlement.getManpower() > 0) {
			NBTTagCompound entityTag = snapshotForRespawn(npc);
			if (entityTag != null) {
				settlement.setManpower(settlement.getManpower() - 1);
				settlement.getPendingRespawns().add(new Settlement.PendingRespawn(entityTag, npc.world.getTotalWorldTime() + AWDiplomacyStatics.respawnDelayTicks));
				data.markDirty();
			}
		}
	}

	public static void tick(WorldServer world) {
		long now = world.getTotalWorldTime();
		if (now % 20 != 0) {
			return;
		}
		processDisbanding(world, now);
		processTurningBandits(world, now);
		if (!AWDiplomacyStatics.settlementsEnabled) {
			return;
		}
		processPendingLeaders(world, now);
		SettlementData data = getData(world);
		int dimension = world.provider.getDimension();
		for (Settlement settlement : new ArrayList<>(data.getAll())) {
			if (settlement.getDimension() != dimension || !settlement.isActive() || settlement.getType() != Settlement.Type.FACTION_CITY) {
				continue;
			}
			processRespawns(world, data, settlement, now);
			regenerateManpower(world, data, settlement);
			if (now % LEADER_CHECK_INTERVAL == 0) {
				checkLeader(world, data, settlement);
			}
		}
		Sieges.tick(world, data, now);
		ManpowerBars.update(world, data);
	}

	public static void setManpower(World world, Settlement settlement, int manpower) {
		settlement.setManpower(manpower);
		settlement.setMaxManpower(Math.max(settlement.getMaxManpower(), manpower));
		getData(world).markDirty();
	}

	public static ITextComponent displayName(Settlement settlement) {
		String name = settlement.getName().isEmpty() ? "#" + settlement.getId() : settlement.getName();
		return new TextComponentTranslation("command.aw.settlement.name", name, FactionMembership.factionName(settlement.getFaction()));
	}

	private static void processPendingLeaders(WorldServer world, long now) {
		Iterator<Scheduled<NpcFactionLeader>> it = pendingLeaders.iterator();
		while (it.hasNext()) {
			Scheduled<NpcFactionLeader> pending = it.next();
			NpcFactionLeader leader = pending.entity.get();
			if (leader == null || leader.isDead) {
				it.remove();
			} else if (leader.world == world && pending.dueTick <= now) {
				it.remove();
				foundCity(world, leader);
			}
		}
	}

	private static void foundCity(WorldServer world, NpcFactionLeader leader) {
		SettlementData data = getData(world);
		int dimension = world.provider.getDimension();
		BlockPos pos = leader.getPosition();
		if (data.getActiveAt(dimension, pos, Settlement.Type.FACTION_CITY) != null) {
			// already inside an active city: one leader per city
			return;
		}

		BlockPos min;
		BlockPos max;
		String structureName;
		Optional<TownEntry> town = Optional.empty();
		Optional<StructureEntry> structure = Optional.empty();
		if (AWDiplomacyStatics.settlementUseStructureBounds) {
			town = AWGameData.INSTANCE.getPerWorldData(world, TownMap.class).getTownAt(pos);
			structure = AWGameData.INSTANCE.getPerWorldData(world, StructureMap.class).getStructureAt(world, pos);
		}
		if (town.isPresent()) {
			min = town.get().getBB().min;
			max = town.get().getBB().max;
			structureName = "";
		} else if (structure.isPresent()) {
			min = structure.get().getBB().min;
			max = structure.get().getBB().max;
			structureName = structure.get().getName();
		} else {
			int radius = AWDiplomacyStatics.settlementDefaultRadius;
			min = pos.add(-radius, 0, -radius);
			max = pos.add(radius, 0, radius);
			structureName = "";
		}
		// structure bounds can be flat, make sure npcs on the floors around the leader are included
		min = new BlockPos(min.getX(), Math.max(0, Math.min(min.getY(), pos.getY() - MIN_BOUNDS_BELOW)), min.getZ());
		max = new BlockPos(max.getX(), Math.min(255, Math.max(max.getY(), pos.getY() + MIN_BOUNDS_ABOVE)), max.getZ());

		String name = SettlementNames.generate(world.rand, data.getAll());
		Settlement settlement = Settlement.createFactionCity(data.getNextId(), dimension, name, structureName, leader.getFaction(), min, max,
				leader.getUniqueID(), pos, 0, world.getTotalWorldTime() / DAY_TICKS);
		// no siege right after the founding (siege_cooldown_days)
		settlement.setLastSiegeEnd(world.getTotalWorldTime());
		settlement.setLastFullSiegeEnd(world.getTotalWorldTime());
		List<NpcFaction> garrison = new ArrayList<>();
		for (NpcFaction npc : world.getEntitiesWithinAABB(NpcFaction.class, settlement.getAABB())) {
			if (npc != leader && !(npc instanceof NpcFactionLeader) && !npc.isPassive() && leader.getFaction().equals(npc.getFaction())
					&& !isSiegeOrArmyUnit(npc) && !isInActiveSettlement(data, npc)) {
				garrison.add(npc);
			}
		}
		int manpower = Math.max(AWDiplomacyStatics.manpowerMin, (int) Math.ceil(garrison.size() * AWDiplomacyStatics.manpowerPerUnit));
		settlement.setManpower(manpower);
		settlement.setMaxManpower(manpower);
		data.add(settlement);
		tag(leader, settlement, ROLE_LEADER);
		garrison.forEach(npc -> tag(npc, settlement, ROLE_GARRISON));
		AncientWarfareNPC.LOG.info("Founded city {} ({}) of faction {} at {} with {} garrison units and {} manpower", settlement.getId(), name,
				settlement.getFaction(), pos, garrison.size(), manpower);
	}

	private static boolean isInActiveSettlement(SettlementData data, NpcFaction npc) {
		if (!npc.getEntityData().hasKey(TAG_SETTLEMENT)) {
			return false;
		}
		Settlement settlement = data.get(npc.getEntityData().getInteger(TAG_SETTLEMENT));
		return settlement != null && settlement.isActive();
	}

	private static void tag(NpcFaction npc, Settlement settlement, String role) {
		npc.getEntityData().setInteger(TAG_SETTLEMENT, settlement.getId());
		npc.getEntityData().setString(TAG_ROLE, role);
	}

	@Nullable
	private static NBTTagCompound snapshotForRespawn(NpcFaction npc) {
		ResourceLocation key = EntityList.getKey(npc);
		if (key == null) {
			return null;
		}
		NBTTagCompound tag = new NBTTagCompound();
		npc.writeToNBT(tag);
		tag.setString("id", key.toString());
		for (String removed : RESPAWN_REMOVED_TAGS) {
			tag.removeTag(removed);
		}
		return tag;
	}

	private static void processRespawns(WorldServer world, SettlementData data, Settlement settlement, long now) {
		Iterator<Settlement.PendingRespawn> it = settlement.getPendingRespawns().iterator();
		while (it.hasNext()) {
			Settlement.PendingRespawn respawn = it.next();
			if (respawn.dueTick > now) {
				continue;
			}
			BlockPos pos = getRespawnPos(world, settlement, respawn.entityTag);
			if (pos == null) {
				continue; // area not loaded, wait
			}
			it.remove();
			data.markDirty();
			Entity entity = EntityList.createEntityFromNBT(respawn.entityTag, world);
			if (!(entity instanceof EntityLiving)) {
				continue;
			}
			EntityLiving living = (EntityLiving) entity;
			living.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360F, 0F);
			living.setHealth(living.getMaxHealth());
			if (world.spawnEntity(living)) {
				world.spawnParticle(EnumParticleTypes.CLOUD, living.posX, living.posY + 1D, living.posZ, 10, 0.3D, 0.5D, 0.3D, 0.02D);
			}
		}
	}

	@Nullable
	private static BlockPos getRespawnPos(World world, Settlement settlement, NBTTagCompound entityTag) {
		BlockPos pos = settlement.getCenter();
		if (entityTag.hasKey("home")) {
			BlockPos home = BlockPos.fromLong(entityTag.getLong("home"));
			if (settlement.contains(world.provider.getDimension(), home)) {
				pos = home;
			}
		}
		if (!world.isBlockLoaded(pos)) {
			return null;
		}
		for (int dy = 0; dy < 6; dy++) {
			BlockPos candidate = pos.up(dy);
			if (world.isAirBlock(candidate) && world.isAirBlock(candidate.up()) && !world.isAirBlock(candidate.down())) {
				return candidate;
			}
		}
		return world.getHeight(pos);
	}

	private static void regenerateManpower(World world, SettlementData data, Settlement settlement) {
		long day = world.getTotalWorldTime() / DAY_TICKS;
		if (day <= settlement.getLastRegenDay()) {
			return;
		}
		if (AWDiplomacyStatics.manpowerRegenPerDay > 0 && settlement.getManpower() < settlement.getMaxManpower() && !Sieges.isBesieged(settlement)) {
			long regen = (day - settlement.getLastRegenDay()) * AWDiplomacyStatics.manpowerRegenPerDay;
			settlement.setManpower((int) Math.min(settlement.getMaxManpower(), settlement.getManpower() + regen));
		}
		settlement.setLastRegenDay(day);
		data.markDirty();
	}

	private static void checkLeader(WorldServer world, SettlementData data, Settlement settlement) {
		UUID leaderId = settlement.getLeaderId();
		if (leaderId == null) {
			return;
		}
		if (!world.isBlockLoaded(settlement.getCenter()) || !world.isBlockLoaded(settlement.getLastLeaderPos())) {
			settlement.missingLeaderChecks = 0;
			return;
		}
		Entity leader = world.getEntityFromUuid(leaderId);
		if (leader != null && leader.isEntityAlive()) {
			settlement.missingLeaderChecks = 0;
			if (!leader.getPosition().equals(settlement.getLastLeaderPos())) {
				settlement.setLastLeaderPos(leader.getPosition());
				data.markDirty();
			}
		} else if (++settlement.missingLeaderChecks >= MISSING_LEADER_CHECKS) {
			// the leader was removed without dying (e.g. Ancient Warfare structure conquest)
			collapse(world, data, settlement);
		}
	}

	/**
	 * The leader died: the city is abandoned, its remaining units disband and its hostile spawners are removed.
	 */
	public static void collapse(World world, SettlementData data, Settlement settlement) {
		settlement.setAbandoned();
		data.markDirty();
		Sieges.onSettlementFallen(world, settlement);
		MinecraftServer server = world.getMinecraftServer();
		if (AWDiplomacyStatics.announceSettlementChanges && server != null) {
			ITextComponent message = new TextComponentTranslation("command.aw.settlement.announce.fallen", displayName(settlement));
			message.getStyle().setColor(TextFormatting.GOLD);
			server.getPlayerList().sendMessage(message);
		}
		long now = world.getTotalWorldTime();
		for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
			if (entity instanceof NpcFaction && entity.isEntityAlive() && entity.getEntityData().hasKey(TAG_SETTLEMENT)
					&& entity.getEntityData().getInteger(TAG_SETTLEMENT) == settlement.getId()) {
				leaveFallenCity((NpcFaction) entity, now + 20 + world.rand.nextInt(60));
			}
		}
		int dimension = world.provider.getDimension();
		for (TileEntity tile : new ArrayList<>(world.loadedTileEntityList)) {
			if (tile instanceof TileAdvancedSpawner && settlement.contains(dimension, tile.getPos())
					&& SpawnerSettings.spawnsHostileNpcs(((TileAdvancedSpawner) tile).getSettings())) {
				world.setBlockToAir(tile.getPos());
			}
		}
		AncientWarfareNPC.LOG.info("City {} of faction {} has fallen", settlement.getId(), settlement.getFaction());
	}

	/**
	 * A unit of a fallen city: depending on fallen_garrison it becomes a bandit, stays without city or vanishes.
	 */
	private static void leaveFallenCity(NpcFaction npc, long dueTick) {
		switch (AWDiplomacyStatics.fallenGarrison) {
			case "disband":
				npc.setAttackTarget(null);
				disbanding.add(new Scheduled<>(npc, dueTick));
				break;
			case "stay":
				untag(npc);
				break;
			default:
				turningBandits.add(new Scheduled<>(npc, dueTick));
				break;
		}
	}

	private static void untag(NpcFaction npc) {
		npc.getEntityData().removeTag(TAG_SETTLEMENT);
		npc.getEntityData().removeTag(TAG_ROLE);
	}

	private static void processTurningBandits(WorldServer world, long now) {
		Iterator<Scheduled<NpcFaction>> it = turningBandits.iterator();
		List<NpcFaction> due = new ArrayList<>();
		while (it.hasNext()) {
			Scheduled<NpcFaction> scheduled = it.next();
			NpcFaction npc = scheduled.entity.get();
			if (npc == null || npc.isDead) {
				it.remove();
			} else if (npc.world == world && scheduled.dueTick <= now) {
				it.remove();
				due.add(npc);
			}
		}
		due.forEach(npc -> turnBandit(world, npc));
	}

	/**
	 * Replaces the npc with a copy in the bandit faction, keeping its equipment, name and health. The npc is replaced and not just
	 * changed because clients only receive the faction of an npc when it spawns.
	 */
	private static void turnBandit(WorldServer world, NpcFaction npc) {
		String faction = AWDiplomacyStatics.banditFaction;
		ResourceLocation key = EntityList.getKey(npc);
		if (faction.equals(npc.getFaction()) || !FactionRegistry.getFactionNames().contains(faction) || key == null) {
			untag(npc);
			return;
		}
		NBTTagCompound tag = new NBTTagCompound();
		npc.writeToNBT(tag);
		tag.setString("id", key.toString());
		tag.setString("factionName", faction);
		for (String removed : new String[] {"UUIDMost", "UUIDLeast", "Passengers", "revengePlayers"}) {
			tag.removeTag(removed);
		}
		NBTTagCompound forgeData = tag.getCompoundTag("ForgeData");
		forgeData.removeTag(TAG_SETTLEMENT);
		forgeData.removeTag(TAG_ROLE);
		tag.setTag("ForgeData", forgeData);
		Entity bandit = EntityList.createEntityFromNBT(tag, world);
		if (bandit == null) {
			untag(npc);
			return;
		}
		bandit.setLocationAndAngles(npc.posX, npc.posY, npc.posZ, npc.rotationYaw, npc.rotationPitch);
		npc.dismountRidingEntity();
		npc.setDropItemsWhenDead(false);
		npc.setDead();
		world.spawnEntity(bandit);
	}

	/**
	 * Makes a unit leave: it vanishes without dropping its items after the given tick.
	 */
	public static void disband(NpcFaction npc, long dueTick) {
		disbanding.add(new Scheduled<>(npc, dueTick));
	}

	private static void processDisbanding(WorldServer world, long now) {
		Iterator<Scheduled<NpcFaction>> it = disbanding.iterator();
		while (it.hasNext()) {
			Scheduled<NpcFaction> scheduled = it.next();
			NpcFaction npc = scheduled.entity.get();
			if (npc == null || npc.isDead) {
				it.remove();
			} else if (npc.world == world && scheduled.dueTick <= now) {
				it.remove();
				world.spawnParticle(EnumParticleTypes.CLOUD, npc.posX, npc.posY + 1D, npc.posZ, 15, 0.3D, 0.5D, 0.3D, 0.02D);
				npc.setDropItemsWhenDead(false);
				npc.setDead();
			}
		}
	}

	private static class Scheduled<T extends Entity> {
		private final WeakReference<T> entity;
		private final long dueTick;

		private Scheduled(T entity, long dueTick) {
			this.entity = new WeakReference<>(entity);
			this.dueTick = dueTick;
		}
	}
}
