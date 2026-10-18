package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.shadowmage.ancientwarfare.npc.ai.faction.NpcAIFactionFlee;
import net.shadowmage.ancientwarfare.npc.ai.faction.NpcAIFactionHoldPost;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.minecraft.entity.player.EntityPlayer;
import net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFactionLeader;
import net.shadowmage.ancientwarfare.npc.faction.Diplomacy;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
	/**
	 * Units left by a fallen city (bandits or units without city): they never found or join a city again
	 */
	public static final String TAG_REMNANT = "awRemnant";
	private static final String ROLE_LEADER = "leader";
	private static final String ROLE_CAPTAIN = "captain";
	private static final String ROLE_GARRISON = "garrison";

	private static final int LEADER_REGISTRATION_DELAY = 40;
	private static final int LEADER_CHECK_INTERVAL = 1200;
	private static final int MISSING_LEADER_CHECKS = 3;
	private static final int DAY_TICKS = 24000;
	private static final int MIN_BOUNDS_BELOW = 24;
	private static final int MIN_BOUNDS_ABOVE = 40;
	private static final int STRUCTURE_SCAN_INTERVAL = 100;
	private static final int STRUCTURE_RETRY_TICKS = 6000;
	private static final int MAX_FOUNDED_PER_SCAN = 4;
	private static final int MAX_STRUCTURE_CHUNKS = 400;
	private static final int MAX_ESTIMATED_GARRISON = 200;
	private static final int AWAITING_LEADER_CHECKS = 30;
	private static final String[] RESPAWN_REMOVED_TAGS = {"UUIDMost", "UUIDLeast", "Pos", "Motion", "Health", "HurtTime", "HurtByTimestamp", "DeathTime",
			"AbsorptionAmount", "ActiveEffects", "Fire", "FallDistance", "Passengers", "Leashed", "Leash"};

	private static final List<Scheduled<NpcFactionLeader>> pendingLeaders = new ArrayList<>();
	private static final List<Scheduled<NpcFaction>> disbanding = new ArrayList<>();
	private static final List<Scheduled<NpcFaction>> turningBandits = new ArrayList<>();
	// structures without a leader spawner, looked at again later (total world time of the next look; by dimension and position)
	private static final Map<String, Long> structureScanDue = new HashMap<>();

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
		if (entity.world.isRemote || !(entity instanceof NpcFaction)) {
			return;
		}
		NpcFaction npc = (NpcFaction) entity;
		if (!AWDiplomacyStatics.settlementsEnabled) {
			// without cities every leader rules
			if (npc instanceof NpcFactionLeader) {
				((NpcFactionLeader) npc).setRuler(true);
			}
			return;
		}
		if (npc.getEntityData().hasKey(TAG_SIEGE)) {
			Sieges.onAttackerJoinWorld(npc);
			return;
		}
		if (npc.getEntityData().hasKey(NpcAIFactionFlee.TAG_FLEE_UNTIL)) {
			addFleeAI(npc);
		}
		if (isSiegeOrArmyUnit(npc) || npc.getEntityData().getBoolean(TAG_REMNANT)) {
			return;
		}
		long now = npc.world.getTotalWorldTime();
		NBTTagCompound data = npc.getEntityData();
		if (data.hasKey(TAG_SETTLEMENT)) {
			Settlement settlement = getData(npc.world).get(data.getInteger(TAG_SETTLEMENT));
			if (settlement == null) {
				data.removeTag(TAG_SETTLEMENT);
				data.removeTag(TAG_ROLE);
			} else if (settlement.isActive() && !settlement.getFaction().equals(npc.getFaction())) {
				// old garrison of a city conquered while it was not loaded
				leaveFallenCity(npc, now + 20 + npc.world.rand.nextInt(100));
			} else if (settlement.isActive()) {
				// units tagged before they were made persistent, and the ruler flag that is not saved
				npc.enablePersistence();
				if (!Sieges.isBesieged(settlement)) {
					// went out after attackers when the siege ended while it was not loaded
					Sieges.restorePost(npc);
				}
				if (ROLE_LEADER.equals(data.getString(TAG_ROLE)) && npc.getUniqueID().equals(settlement.getLeaderId())) {
					addHoldPostAI(npc);
				} else if (ROLE_LEADER.equals(data.getString(TAG_ROLE))) {
					// a higher leader took its place while it was not loaded: it is one of the captains now
					tag(npc, settlement, ROLE_CAPTAIN);
				}
				if (npc instanceof NpcFactionLeader) {
					((NpcFactionLeader) npc).setRuler(ROLE_LEADER.equals(data.getString(TAG_ROLE)) && npc.getUniqueID().equals(settlement.getLeaderId()));
				}
			} else {
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
		if (entity.world.isRemote || !(entity instanceof NpcFaction)) {
			return;
		}
		Sieges.onNpcDeath((NpcFaction) entity);
		if (!entity.getEntityData().hasKey(TAG_SETTLEMENT)) {
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
			// the new leader of a conquered city takes the post of the old one (its home inside the city), not the place it died
			BlockPos post = npc.hasHome() && settlement.contains(npc.world.provider.getDimension(), npc.getHomePosition()) ? npc.getHomePosition()
					: settlement.getLastLeaderPos();
			settlement.setLeader(settlement.getLeaderId(), post);
			onLeaderKilled(npc.world, data, settlement, source);
		} else if (isDefender(npc, settlement) && settlement.getManpower() > 0) {
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
		if (AWDiplomacyStatics.settlementFoundRange > 0 && now % STRUCTURE_SCAN_INTERVAL == 0) {
			foundCitiesFromStructures(world, data, now);
		}
		int dimension = world.provider.getDimension();
		for (Settlement settlement : new ArrayList<>(data.getAll())) {
			if (settlement.getDimension() != dimension || !settlement.isActive() || settlement.getType() != Settlement.Type.FACTION_CITY) {
				continue;
			}
			processRespawns(world, data, settlement, now);
			regenerateManpower(world, data, settlement);
			if (AWDiplomacyStatics.reinforcementIntervalTicks > 0 && (now / 20) % Math.max(1, AWDiplomacyStatics.reinforcementIntervalTicks / 20) == 0) {
				reinforce(world, settlement);
			}
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
		Settlement existing = data.getActiveAt(dimension, pos, Settlement.Type.FACTION_CITY);
		if (existing == null && data.getAt(dimension, pos, Settlement.Type.FACTION_CITY) != null) {
			// the ruins of a fallen city without winner: cities are not founded again
			leader.setRuler(true);
			return;
		}
		if (existing != null) {
			// already inside an active city: one ruling leader per city
			if (!existing.getFaction().equals(leader.getFaction())) {
				// a leader of another faction inside the city: it rules nothing, but is still the leader of its faction here
				leader.setRuler(true);
			} else if (!isLeaderPresent(world, existing) && world.isBlockLoaded(existing.getLastLeaderPos())) {
				// takes over a city whose leader is gone (e.g. replaced by an Ancient Warfare spawner after a despawn)
				AncientWarfareNPC.LOG.info("New leader {} takes over city {} ({}) at {}", leader.getUniqueID(), existing.getId(), existing.getName(), pos);
				existing.setLeader(leader.getUniqueID(), pos);
				existing.missingLeaderChecks = 0;
				data.markDirty();
				tag(leader, existing, ROLE_LEADER);
			} else if (altitude(leader) > rulerAltitude(world, existing)) {
				// the ruler is the highest leader of the city (the lord in the keep, not a captain on the walls)
				promote(world, data, existing, leader);
			} else {
				tag(leader, existing, ROLE_CAPTAIN);
			}
			return;
		}

		Bounds bounds = findBounds(world, pos);
		BlockPos min = bounds.min;
		BlockPos max = bounds.max;
		String structureName = bounds.structureName;

		String name = SettlementNames.generate(world.rand, data.getAll());
		List<NpcFaction> garrison = new ArrayList<>();
		for (NpcFaction npc : world.getEntitiesWithinAABB(NpcFaction.class, new AxisAlignedBB(min, max.add(1, 1, 1)))) {
			if (npc != leader && !npc.isPassive() && leader.getFaction().equals(npc.getFaction()) && !isSiegeOrArmyUnit(npc)
					&& !npc.getEntityData().getBoolean(TAG_REMNANT) && !isInActiveSettlement(data, npc)) {
				garrison.add(npc);
			}
		}
		// the ruler is the highest leader of the city (the lord in the keep, not a captain on the walls), the founder may be a captain
		NpcFactionLeader ruler = leader;
		for (NpcFaction npc : garrison) {
			if (npc instanceof NpcFactionLeader && altitude(npc) > altitude(ruler)) {
				ruler = (NpcFactionLeader) npc;
			}
		}
		if (ruler != leader) {
			garrison.remove(ruler);
			garrison.add(leader);
		}
		Settlement settlement = Settlement.createFactionCity(data.getNextId(), dimension, name, structureName, leader.getFaction(), min, max,
				ruler.getUniqueID(), ruler.getPosition(), 0, getDay(world));
		// no siege right after the founding (siege_cooldown_days)
		settlement.setLastSiegeEnd(world.getTotalWorldTime());
		settlement.setLastFullSiegeEnd(world.getTotalWorldTime());
		int manpower = Math.max(AWDiplomacyStatics.manpowerMin, (int) Math.ceil(garrison.size() * AWDiplomacyStatics.manpowerPerUnit));
		settlement.setManpower(manpower);
		settlement.setMaxManpower(manpower);
		data.add(settlement);
		tag(ruler, settlement, ROLE_LEADER);
		int captains = 0;
		for (NpcFaction npc : garrison) {
			// the other leaders of the city are its captains
			boolean captain = npc instanceof NpcFactionLeader;
			tag(npc, settlement, captain ? ROLE_CAPTAIN : ROLE_GARRISON);
			captains += captain ? 1 : 0;
		}
		AncientWarfareNPC.LOG.info("Founded city {} ({}) of faction {} at {}, ruler at {}, with {} garrison units ({} captains) and {} manpower", settlement.getId(),
				name, settlement.getFaction(), pos, ruler.getPosition(), garrison.size(), captains, manpower);
	}

	private static class Bounds {
		private final BlockPos min;
		private final BlockPos max;
		private final String structureName;

		private Bounds(BlockPos min, BlockPos max, String structureName) {
			this.min = min;
			this.max = max;
			this.structureName = structureName;
		}
	}

	/**
	 * @return the bounds of the city of a leader (or of the spawner of one) at pos: the Ancient Warfare town or structure it is in, or a square
	 * of settlement_default_radius around it; made tall enough for the floors around pos
	 */
	private static Bounds findBounds(WorldServer world, BlockPos pos) {
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
		return new Bounds(min, max, structureName);
	}

	/**
	 * A spawner of an Ancient Warfare structure that creates a faction leader: the faction, where the leader will appear and how many
	 * garrison units the whole structure is going to spawn.
	 */
	private static class LeaderSpawn {
		private final String faction;
		private final BlockPos pos;
		private final int garrison;

		private LeaderSpawn(String faction, BlockPos pos, int garrison) {
			this.faction = faction;
			this.pos = pos;
			this.garrison = garrison;
		}
	}

	/**
	 * Cities that are born from their structure, like the structure itself at world generation: every STRUCTURE_SCAN_INTERVAL ticks the
	 * structures within settlement_found_range of a player that are fully loaded and have no city are searched for the spawner of a faction
	 * leader. The city exists at once (without leader: it comes when a player is close enough for the spawner to work, and is linked to the
	 * city then, see foundCity), instead of waiting for the leader to appear.
	 */
	private static void foundCitiesFromStructures(WorldServer world, SettlementData data, long now) {
		int range = AWDiplomacyStatics.settlementFoundRange;
		double rangeSq = (double) range * range;
		int dimension = world.provider.getDimension();
		StructureMap structures = AWGameData.INSTANCE.getPerWorldData(world, StructureMap.class);
		Set<StructureEntry> near = new LinkedHashSet<>();
		for (EntityPlayer player : world.playerEntities) {
			if (player.isSpectator()) {
				continue;
			}
			for (StructureEntry entry : structures.getEntriesNear(world, MathHelper.floor(player.posX), MathHelper.floor(player.posZ), (range >> 4) + 1, true,
					new ArrayList<>())) {
				BlockPos min = entry.getBB().min;
				BlockPos max = entry.getBB().max;
				double dx = Math.max(0D, Math.max(min.getX() - player.posX, player.posX - max.getX()));
				double dz = Math.max(0D, Math.max(min.getZ() - player.posZ, player.posZ - max.getZ()));
				if (dx * dx + dz * dz <= rangeSq) {
					near.add(entry);
				}
			}
		}
		int founded = 0;
		for (StructureEntry entry : near) {
			if (founded >= MAX_FOUNDED_PER_SCAN) {
				break;
			}
			String key = dimension + ":" + entry.getBB().min.toLong();
			Long due = structureScanDue.get(key);
			if (due != null && due > now && due - now <= STRUCTURE_RETRY_TICKS) {
				continue; // no leader spawner there a moment ago
			}
			if (overlapsCity(data, dimension, entry) || !isStructureLoaded(world, entry)) {
				continue; // a loaded structure is looked at once all of it is loaded
			}
			LeaderSpawn spawn = findLeaderSpawner(world, entry);
			if (spawn == null) {
				// also structures whose spawners are not placed yet are looked at again later
				structureScanDue.put(key, now + STRUCTURE_RETRY_TICKS);
				continue;
			}
			foundCityFromSpawner(world, data, spawn);
			founded++;
		}
	}

	private static boolean overlapsCity(SettlementData data, int dimension, StructureEntry entry) {
		BlockPos min = entry.getBB().min;
		BlockPos max = entry.getBB().max;
		for (Settlement city : data.getAll()) {
			if (city.getDimension() == dimension && city.getType() == Settlement.Type.FACTION_CITY && city.getMin().getX() <= max.getX() && city.getMax().getX() >= min.getX()
					&& city.getMin().getZ() <= max.getZ() && city.getMax().getZ() >= min.getZ()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isStructureLoaded(WorldServer world, StructureEntry entry) {
		BlockPos min = entry.getBB().min;
		BlockPos max = entry.getBB().max;
		if (((max.getX() >> 4) - (min.getX() >> 4) + 1) * ((max.getZ() >> 4) - (min.getZ() >> 4) + 1) > MAX_STRUCTURE_CHUNKS) {
			return false;
		}
		for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
			for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
				if (world.getChunkProvider().getLoadedChunk(cx, cz) == null) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * @return the highest spawner of a faction leader in the structure (all of it loaded), with the garrison its spawners are going to
	 * create for that faction, or null if the structure has no leader spawner
	 */
	@Nullable
	private static LeaderSpawn findLeaderSpawner(WorldServer world, StructureEntry entry) {
		BlockPos min = entry.getBB().min;
		BlockPos max = entry.getBB().max;
		List<TileAdvancedSpawner> spawners = new ArrayList<>();
		for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
			for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
				Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
				if (chunk == null) {
					return null;
				}
				for (TileEntity tile : new ArrayList<>(chunk.getTileEntityMap().values())) {
					BlockPos pos = tile.getPos();
					if (tile instanceof TileAdvancedSpawner && pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
							&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) {
						spawners.add((TileAdvancedSpawner) tile);
					}
				}
			}
		}
		TileAdvancedSpawner highest = null;
		String faction = null;
		for (TileAdvancedSpawner spawner : spawners) {
			String leaderFaction = getLeaderFaction(spawner.getSettings());
			if (leaderFaction != null && (highest == null || spawner.getPos().getY() > highest.getPos().getY())) {
				highest = spawner;
				faction = leaderFaction;
			}
		}
		if (highest == null) {
			return null;
		}
		int garrison = 0;
		for (TileAdvancedSpawner spawner : spawners) {
			garrison += countGarrisonSpawns(world, spawner.getSettings(), faction);
		}
		return new LeaderSpawn(faction, highest.getPos(), Math.max(0, Math.min(MAX_ESTIMATED_GARRISON, garrison) - 1));
	}

	/**
	 * @return the faction of the leader a spawner creates (a faction leader of a faction that exists), or null
	 */
	@Nullable
	private static String getLeaderFaction(SpawnerSettings settings) {
		for (SpawnerSettings.EntitySpawnGroup group : settings.getSpawnGroups()) {
			for (SpawnerSettings.EntitySpawnSettings spawn : group.getEntitiesToSpawn()) {
				ResourceLocation id = spawn.getEntityId();
				NBTTagCompound tag = spawn.getCustomTag();
				if ("ancientwarfarenpc".equals(id.getResourceDomain()) && id.getResourcePath().startsWith("faction.leader") && tag != null
						&& FactionRegistry.getFactionNames().contains(tag.getString("factionName"))) {
					return tag.getString("factionName");
				}
			}
		}
		return null;
	}

	/**
	 * @return the number of units of the faction that are not passive (soldiers, archers, leaders...) the spawner is going to create
	 */
	private static int countGarrisonSpawns(WorldServer world, SpawnerSettings settings, String faction) {
		int count = 0;
		for (SpawnerSettings.EntitySpawnGroup group : settings.getSpawnGroups()) {
			for (SpawnerSettings.EntitySpawnSettings spawn : group.getEntitiesToSpawn()) {
				NBTTagCompound tag = spawn.getCustomTag();
				if (tag == null || !faction.equals(tag.getString("factionName"))) {
					continue;
				}
				Entity entity = EntityList.createEntityByIDFromName(spawn.getEntityId(), world);
				if (entity instanceof NpcFaction && !((NpcFaction) entity).isPassive()) {
					// a limited spawn creates all its units over time, an unlimited one a batch at a time
					count += spawn.getSpawnTotal() >= 0 ? spawn.getSpawnTotal() : spawn.getSpawnMax();
				}
			}
		}
		return count;
	}

	/**
	 * Founds a city without leader at the spawner of its leader: manpower from the garrison its structure is going to spawn.
	 */
	private static void foundCityFromSpawner(WorldServer world, SettlementData data, LeaderSpawn spawn) {
		Bounds bounds = findBounds(world, spawn.pos);
		String name = SettlementNames.generate(world.rand, data.getAll());
		Settlement settlement = Settlement.createFactionCity(data.getNextId(), world.provider.getDimension(), name, bounds.structureName, spawn.faction, bounds.min,
				bounds.max, null, spawn.pos, 0, getDay(world));
		// no siege right after the founding (siege_cooldown_days)
		settlement.setLastSiegeEnd(world.getTotalWorldTime());
		settlement.setLastFullSiegeEnd(world.getTotalWorldTime());
		int manpower = Math.max(AWDiplomacyStatics.manpowerMin, (int) Math.ceil(spawn.garrison * AWDiplomacyStatics.manpowerPerUnit));
		settlement.setManpower(manpower);
		settlement.setMaxManpower(manpower);
		data.add(settlement);
		AncientWarfareNPC.LOG.info("Founded city {} ({}) of faction {} from its structure, leader spawner at {}, about {} garrison units, {} manpower, leader to come",
				settlement.getId(), name, settlement.getFaction(), spawn.pos, spawn.garrison, manpower);
	}

	/**
	 * @return the height of the leader's post (its home), or of where it stands
	 */
	private static int altitude(NpcFaction npc) {
		return npc.hasHome() ? npc.getHomePosition().getY() : MathHelper.floor(npc.posY);
	}

	/**
	 * @return the height of the post of the city's ruler, or of where it was last seen if it is not loaded
	 */
	private static int rulerAltitude(WorldServer world, Settlement settlement) {
		Entity ruler = settlement.getLeaderId() == null ? null : world.getEntityFromUuid(settlement.getLeaderId());
		return ruler instanceof NpcFaction && ruler.isEntityAlive() ? altitude((NpcFaction) ruler) : settlement.getLastLeaderPos().getY();
	}

	/**
	 * A higher leader becomes the ruler of the city, the former ruler one of its captains (or when it is loaded, if it is not now).
	 */
	private static void promote(WorldServer world, SettlementData data, Settlement settlement, NpcFactionLeader leader) {
		Entity former = settlement.getLeaderId() == null ? null : world.getEntityFromUuid(settlement.getLeaderId());
		if (former instanceof NpcFaction && former.isEntityAlive()) {
			tag((NpcFaction) former, settlement, ROLE_CAPTAIN);
		}
		AncientWarfareNPC.LOG.info("Leader {} at {} is higher than the ruler of city {} ({}): it rules the city now", leader.getUniqueID(), leader.getPosition(),
				settlement.getId(), settlement.getName());
		settlement.setLeader(leader.getUniqueID(), leader.getPosition());
		settlement.missingLeaderChecks = 0;
		data.markDirty();
		tag(leader, settlement, ROLE_LEADER);
	}

	/**
	 * @return true for the garrison units and captains of the settlement (not its ruling leader)
	 */
	public static boolean isDefender(NpcFaction npc, Settlement settlement) {
		NBTTagCompound data = npc.getEntityData();
		String role = data.getString(TAG_ROLE);
		return data.hasKey(TAG_SETTLEMENT) && data.getInteger(TAG_SETTLEMENT) == settlement.getId() && (ROLE_GARRISON.equals(role) || ROLE_CAPTAIN.equals(role))
				&& settlement.getFaction().equals(npc.getFaction());
	}

	private static boolean isLeaderPresent(WorldServer world, Settlement settlement) {
		if (settlement.getLeaderId() == null) {
			return false;
		}
		Entity leader = world.getEntityFromUuid(settlement.getLeaderId());
		return leader != null && leader.isEntityAlive();
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
		// npcs created by Ancient Warfare spawners can despawn when no player is within 32 blocks, and the spawner then comes back
		// and later spawns a new npc: a city keeps its units, losses go through manpower
		npc.enablePersistence();
		if (npc instanceof NpcFactionLeader) {
			((NpcFactionLeader) npc).setRuler(ROLE_LEADER.equals(role));
		}
		if (ROLE_LEADER.equals(role)) {
			addHoldPostAI(npc);
		} else {
			// a former ruler, now a captain, defends the city as the others
			List<EntityAIBase> holdPost = new ArrayList<>();
			for (EntityAITasks.EntityAITaskEntry entry : npc.tasks.taskEntries) {
				if (entry.action instanceof NpcAIFactionHoldPost) {
					holdPost.add(entry.action);
				}
			}
			holdPost.forEach(npc.tasks::removeTask);
		}
	}

	/**
	 * The ruler of a city stays at its post instead of running to the front line.
	 */
	private static void addHoldPostAI(NpcFaction npc) {
		for (EntityAITasks.EntityAITaskEntry entry : npc.tasks.taskEntries) {
			if (entry.action instanceof NpcAIFactionHoldPost) {
				return;
			}
		}
		npc.tasks.addTask(NpcAIFactionHoldPost.PRIORITY, new NpcAIFactionHoldPost(npc));
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
		NBTTagCompound forgeData = entityTag.getCompoundTag("ForgeData");
		// the post of a defender that died while out after the attackers, otherwise its home
		String homeKey = forgeData.hasKey("awPost") ? "awPost" : "home";
		NBTTagCompound homeTag = forgeData.hasKey("awPost") ? forgeData : entityTag;
		if (homeTag.hasKey(homeKey)) {
			BlockPos home = BlockPos.fromLong(homeTag.getLong(homeKey));
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

	private static long getDay(World world) {
		return world.getWorldTime() / DAY_TICKS;
	}

	private static void regenerateManpower(World world, SettlementData data, Settlement settlement) {
		// Minecraft days of the world time (they follow /time and sleeping, like the days of the diplomacy)
		long day = getDay(world);
		if (day < settlement.getLastRegenDay()) {
			// the time was set back (or the day was counted on the total world time before)
			settlement.setLastRegenDay(day);
			data.markDirty();
			return;
		}
		if (day == settlement.getLastRegenDay()) {
			return;
		}
		long days = day - settlement.getLastRegenDay();
		if (AWDiplomacyStatics.conqueredManpowerGrowthPerDay > 0 && settlement.getMaxManpower() < settlement.getManpowerCap() && !Sieges.isBesieged(settlement)) {
			// a conquered city slowly grows back to the size it had before the conquest
			long growth = days * AWDiplomacyStatics.conqueredManpowerGrowthPerDay;
			settlement.setMaxManpower((int) Math.min(settlement.getManpowerCap(), settlement.getMaxManpower() + growth));
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
			// a city founded from its structure waits for its leader: while the spawner is loaded it is given AWAITING_LEADER_CHECKS checks
			// (minutes), then the leader will never come (a spawner that does not work, a structure already conquered) and the city is dropped
			if (!world.isBlockLoaded(settlement.getLastLeaderPos())) {
				settlement.missingLeaderChecks = 0;
			} else if (++settlement.missingLeaderChecks >= AWAITING_LEADER_CHECKS) {
				AncientWarfareNPC.LOG.info("City {} ({}) of faction {}: its leader did not come in {} minutes (spawner at {}), the city is dropped", settlement.getId(),
						settlement.getName(), settlement.getFaction(), AWAITING_LEADER_CHECKS * LEADER_CHECK_INTERVAL / 1200, settlement.getLastLeaderPos());
				settlement.setAbandoned();
				data.markDirty();
				releaseGarrison(world, settlement);
			}
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
			AncientWarfareNPC.LOG.info("Leader {} of city {} missing for {} checks around {}, the city falls", leaderId, settlement.getId(),
					MISSING_LEADER_CHECKS, settlement.getLastLeaderPos());
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
		announce(world, new TextComponentTranslation("command.aw.settlement.announce.fallen", displayName(settlement)));
		releaseGarrison(world, settlement);
		AncientWarfareNPC.LOG.info("City {} of faction {} has fallen", settlement.getId(), settlement.getFaction());
	}

	/**
	 * The leader died: the city passes to the winner (the faction attacking it, otherwise the faction of the killer), or is abandoned
	 * when there is no winner. A fallen city is never founded again.
	 */
	private static void onLeaderKilled(World world, SettlementData data, Settlement settlement, DamageSource source) {
		String winner = getWinner(world, settlement, source.getTrueSource());
		if (winner == null) {
			collapse(world, data, settlement);
		} else {
			conquer(world, data, settlement, winner);
		}
	}

	@Nullable
	private static String getWinner(World world, Settlement settlement, @Nullable Entity killer) {
		Siege siege = Sieges.getSiegeOf(world, settlement);
		String winner = null;
		if (siege != null) {
			winner = siege.getAttackerFaction();
		} else if (killer instanceof NpcFaction) {
			winner = ((NpcFaction) killer).getFaction();
		} else if (killer instanceof EntityPlayer) {
			winner = FactionMembership.getFaction(world, killer.getUniqueID());
		} else if (killer instanceof NpcPlayerOwned) {
			winner = FactionMembership.getFaction(world, ((NpcPlayerOwned) killer).getOwner().getUUID());
		}
		return winner == null || winner.equals(settlement.getFaction()) || !FactionRegistry.getFactionNames().contains(winner) ? null : winner;
	}

	/**
	 * The city passes to the winning faction: the old garrison leaves (bandits), the attackers inside the city become its garrison and
	 * a governor of the winners takes the place of the dead leader. The city keeps its name.
	 */
	private static void conquer(World world, SettlementData data, Settlement settlement, String winner) {
		String oldFaction = settlement.getFaction();
		// the new owners start small and slowly grow back to the size the city had (see regenerateManpower)
		settlement.setManpowerCap(Math.max(settlement.getManpowerCap(), settlement.getMaxManpower()));
		// garrison size before the conquest, estimated from the maximum manpower (garrison x manpower_per_unit when founded)
		if (AWDiplomacyStatics.manpowerPerUnit > 0) {
			settlement.setGarrisonCap(Math.max(settlement.getGarrisonCap(), (int) Math.floor(settlement.getMaxManpower() / AWDiplomacyStatics.manpowerPerUnit)));
		}
		Diplomacy.onCityLost(world, oldFaction, winner);
		List<NpcFaction> newGarrison = Sieges.onSettlementConquered(world, settlement);
		releaseGarrison(world, settlement);
		settlement.setFaction(winner);
		settlement.getPendingRespawns().clear();
		settlement.missingLeaderChecks = 0;
		long now = world.getTotalWorldTime();
		settlement.setLastSiegeEnd(now);
		settlement.setLastFullSiegeEnd(now);
		settlement.setLastRegenDay(getDay(world));

		NpcFactionLeader governor = spawnGovernor(world, settlement, winner);
		settlement.setLeader(governor == null ? null : governor.getUniqueID(), governor == null ? settlement.getLastLeaderPos() : governor.getPosition());
		if (governor != null) {
			tag(governor, settlement, ROLE_LEADER);
		}
		int occupation = spawnOccupationGarrison(world, settlement, winner, governor != null ? governor.getPosition() : settlement.getLastLeaderPos(),
				newGarrison);
		for (NpcFaction npc : newGarrison) {
			tag(npc, settlement, npc instanceof NpcFactionLeader ? ROLE_CAPTAIN : ROLE_GARRISON);
			npc.setHomeAreaAtCurrentPosition();
		}
		int manpower = Math.max(AWDiplomacyStatics.manpowerMin, (int) Math.ceil(newGarrison.size() * AWDiplomacyStatics.manpowerPerUnit));
		settlement.setManpower(manpower);
		settlement.setMaxManpower(manpower);
		data.markDirty();
		announce(world, new TextComponentTranslation("command.aw.settlement.announce.conquered", settlement.getName().isEmpty() ? "#" + settlement.getId()
				: settlement.getName(), FactionMembership.factionName(winner), FactionMembership.factionName(oldFaction)));
		AncientWarfareNPC.LOG.info("City {} ({}) of faction {} conquered by {}: governor {}, {} garrison units ({} of the occupation garrison), {} manpower",
				settlement.getId(), settlement.getName(), oldFaction, winner, governor == null ? "none" : governor.getUniqueID(), newGarrison.size(), occupation,
				manpower);
	}

	/**
	 * Units of the winners appear around the governor until the garrison has occupation_garrison units.
	 *
	 * @return the number of units added to the garrison list
	 */
	private static int spawnOccupationGarrison(World world, Settlement settlement, String faction, BlockPos around, List<NpcFaction> garrison) {
		List<String> types = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		Sieges.parseWeights(AWDiplomacyStatics.occupationUnitWeights, "faction.soldier", types, weights);
		int added = 0;
		for (int i = garrison.size(); i < AWDiplomacyStatics.occupationGarrison; i++) {
			NpcFaction unit = Sieges.createWeightedUnit(world, types, weights, faction);
			if (unit == null) {
				continue;
			}
			BlockPos pos = findStandableNear(world, around, settlement);
			unit.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360F, 0F);
			unit.setHomeAreaAtCurrentPosition();
			unit.setHealth(unit.getMaxHealth());
			if (world.spawnEntity(unit)) {
				if (world instanceof WorldServer) {
					((WorldServer) world).spawnParticle(EnumParticleTypes.CLOUD, unit.posX, unit.posY + 1D, unit.posZ, 10, 0.3D, 0.5D, 0.3D, 0.02D);
				}
				garrison.add(unit);
				added++;
			}
		}
		return added;
	}

	/**
	 * A conquered city gets a new recruit of its faction until its garrison is back to the size it had before the conquest (garrisonCap).
	 * Only when the whole city is loaded (otherwise units in unloaded parts would not be counted) and not under attack.
	 */
	private static void reinforce(WorldServer world, Settlement settlement) {
		if (settlement.getGarrisonCap() <= 0 || Sieges.isBesieged(settlement) || !world.isAreaLoaded(settlement.getMin(), settlement.getMax())) {
			return;
		}
		int garrison = settlement.getPendingRespawns().size();
		for (NpcFaction npc : world.getEntitiesWithinAABB(NpcFaction.class, settlement.getAABB())) {
			if (npc.isEntityAlive() && isDefender(npc, settlement)) {
				garrison++;
			}
		}
		if (garrison >= settlement.getGarrisonCap()) {
			return;
		}
		List<String> types = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		Sieges.parseWeights(AWDiplomacyStatics.occupationUnitWeights, "faction.soldier", types, weights);
		NpcFaction unit = Sieges.createWeightedUnit(world, types, weights, settlement.getFaction());
		if (unit == null) {
			return;
		}
		BlockPos pos = findRecruitPos(world, settlement);
		unit.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360F, 0F);
		unit.setHomeAreaAtCurrentPosition();
		unit.setHealth(unit.getMaxHealth());
		if (world.spawnEntity(unit)) {
			tag(unit, settlement, ROLE_GARRISON);
			world.spawnParticle(EnumParticleTypes.CLOUD, unit.posX, unit.posY + 1D, unit.posZ, 10, 0.3D, 0.5D, 0.3D, 0.02D);
		}
	}

	/**
	 * @return a free surface position somewhere in the city (on the ground, not on water), or a position near the leader's post
	 */
	private static BlockPos findRecruitPos(World world, Settlement settlement) {
		int dimension = world.provider.getDimension();
		BlockPos min = settlement.getMin();
		BlockPos max = settlement.getMax();
		for (int attempt = 0; attempt < 16; attempt++) {
			BlockPos column = new BlockPos(min.getX() + world.rand.nextInt(max.getX() - min.getX() + 1), 64, min.getZ() + world.rand.nextInt(max.getZ() - min.getZ() + 1));
			BlockPos top = world.getTopSolidOrLiquidBlock(column);
			if (settlement.contains(dimension, top) && !world.getBlockState(top.down()).getMaterial().isLiquid() && world.isAirBlock(top.up())) {
				return top;
			}
		}
		return findStandableNear(world, settlement.getLastLeaderPos(), settlement);
	}

	/**
	 * @return a free position inside the city within a few blocks of pos, or pos itself
	 */
	private static BlockPos findStandableNear(World world, BlockPos pos, Settlement settlement) {
		int dimension = world.provider.getDimension();
		for (int attempt = 0; attempt < 16; attempt++) {
			BlockPos column = pos.add(world.rand.nextInt(13) - 6, 0, world.rand.nextInt(13) - 6);
			for (int dy = 4; dy >= -4; dy--) {
				BlockPos candidate = column.up(dy);
				if (settlement.contains(dimension, candidate) && world.isAirBlock(candidate) && world.isAirBlock(candidate.up())
						&& world.getBlockState(candidate.down()).isFullBlock()) {
					return candidate;
				}
			}
		}
		return pos;
	}

	@Nullable
	private static NpcFactionLeader spawnGovernor(World world, Settlement settlement, String faction) {
		Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation("ancientwarfarenpc", "faction.leader"), world);
		if (!(entity instanceof NpcFactionLeader)) {
			return null;
		}
		NpcFactionLeader governor = (NpcFactionLeader) entity;
		governor.setFactionNameAndDefaults(faction);
		BlockPos pos = settlement.getLastLeaderPos();
		for (int dy = 0; dy < 6; dy++) {
			BlockPos candidate = pos.up(dy);
			if (world.isAirBlock(candidate) && world.isAirBlock(candidate.up())) {
				pos = candidate;
				break;
			}
		}
		governor.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360F, 0F);
		governor.setHomeAreaAtCurrentPosition();
		governor.setHealth(governor.getMaxHealth());
		// tagged before it joins the world, so that it is not taken for a leader founding a city
		governor.getEntityData().setInteger(TAG_SETTLEMENT, settlement.getId());
		governor.getEntityData().setString(TAG_ROLE, ROLE_LEADER);
		return world.spawnEntity(governor) ? governor : null;
	}

	private static void announce(World world, ITextComponent message) {
		MinecraftServer server = world.getMinecraftServer();
		if (AWDiplomacyStatics.announceSettlementChanges && server != null) {
			message.getStyle().setColor(TextFormatting.GOLD);
			server.getPlayerList().sendMessage(message);
		}
	}

	/**
	 * The units of the city leave (see fallen_garrison) and the hostile spawners of the city are removed.
	 */
	private static void releaseGarrison(World world, Settlement settlement) {
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
			case "bandits":
				turningBandits.add(new Scheduled<>(npc, dueTick));
				break;
			default: {
				// flee: they keep their faction, run away from the fallen city and then stay where they are, without city
				Settlement settlement = getSettlementOf(npc);
				untag(npc);
				startFleeing(npc.world, npc, settlement);
				break;
			}
		}
	}

	private static void startFleeing(World world, NpcFaction npc, @Nullable Settlement city) {
		if (AWDiplomacyStatics.banditFleeTicks <= 0) {
			return;
		}
		npc.getEntityData().setLong(NpcAIFactionFlee.TAG_FLEE_UNTIL, world.getTotalWorldTime() + AWDiplomacyStatics.banditFleeTicks);
		npc.getEntityData().setLong(NpcAIFactionFlee.TAG_FLEE_FROM, (city != null ? city.getLastLeaderPos() : npc.getPosition()).toLong());
		if (city != null) {
			npc.getEntityData().setInteger(NpcAIFactionFlee.TAG_FLEE_CITY, city.getId());
		}
		// a running target task keeps its own copy of the target (EntityAITarget), reset it as well
		for (EntityAITasks.EntityAITaskEntry entry : npc.targetTasks.taskEntries) {
			if (entry.using) {
				entry.action.resetTask();
			}
		}
		npc.setAttackTarget(null);
		npc.setRevengeTarget(null);
		addFleeAI(npc);
	}

	private static void addFleeAI(NpcFaction npc) {
		for (EntityAITasks.EntityAITaskEntry entry : npc.tasks.taskEntries) {
			if (entry.action instanceof NpcAIFactionFlee) {
				return;
			}
		}
		npc.tasks.addTask(NpcAIFactionFlee.PRIORITY, new NpcAIFactionFlee(npc));
	}

	private static void untag(NpcFaction npc) {
		npc.getEntityData().removeTag(TAG_SETTLEMENT);
		npc.getEntityData().removeTag(TAG_ROLE);
		npc.getEntityData().setBoolean(TAG_REMNANT, true);
		if (npc instanceof NpcFactionLeader) {
			((NpcFactionLeader) npc).setRuler(false);
		}
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
			// already a bandit (e.g. a fallen brigand city), or no bandit faction: it flees as it is
			Settlement settlement = getSettlementOf(npc);
			untag(npc);
			startFleeing(world, npc, settlement);
			return;
		}
		NBTTagCompound tag = new NBTTagCompound();
		npc.writeToNBT(tag);
		tag.setString("id", key.toString());
		tag.setString("factionName", faction);
		// a bandit must not despawn: its Ancient Warfare respawn data would bring back a spawner of the old faction
		tag.setBoolean("PersistenceRequired", true);
		for (String removed : new String[] {"UUIDMost", "UUIDLeast", "Passengers", "revengePlayers"}) {
			tag.removeTag(removed);
		}
		Settlement settlement = getSettlementOf(npc);
		NBTTagCompound forgeData = tag.getCompoundTag("ForgeData");
		forgeData.removeTag(TAG_SETTLEMENT);
		forgeData.removeTag(TAG_ROLE);
		forgeData.setBoolean(TAG_REMNANT, true);
		if (AWDiplomacyStatics.banditFleeTicks > 0) {
			// the survivors run away from the fallen city before turning to banditry
			forgeData.setLong(NpcAIFactionFlee.TAG_FLEE_UNTIL, world.getTotalWorldTime() + AWDiplomacyStatics.banditFleeTicks);
			forgeData.setLong(NpcAIFactionFlee.TAG_FLEE_FROM, (settlement != null ? settlement.getLastLeaderPos() : npc.getPosition()).toLong());
			if (settlement != null) {
				forgeData.setInteger(NpcAIFactionFlee.TAG_FLEE_CITY, settlement.getId());
			}
		}
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
