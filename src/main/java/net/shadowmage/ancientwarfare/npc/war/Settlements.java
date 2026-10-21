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
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;
import net.shadowmage.ancientwarfare.npc.gamedata.KingdomData;
import net.shadowmage.ancientwarfare.npc.entity.NpcCombat;
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
import java.util.HashSet;
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
	// a soldier of the owner of a kingdom sent as reinforcement to a city of the kingdom: the id of the city
	public static final String TAG_KINGDOM_GARRISON = "awKingdomGarrison";
	public static final String TAG_ROLE = "awSettlementRole";
	public static final String TAG_SIEGE = "awSiege";
	// faction of the city an attacker besieges: the only npcs it is hostile to (with those that attack it), see NpcFaction.isHostileTowards
	public static final String TAG_SIEGE_ENEMY = "awSiegeEnemy";
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
			if (settlement != null && settlement.getId() != data.getInteger(TAG_SETTLEMENT) && settlement.isActive()
					&& settlement.getFaction().equals(npc.getFaction())) {
				// its city was merged into another one while it was not loaded
				retag(npc, settlement);
			}
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
		if (!entity.world.isRemote && entity.getEntityData().hasKey(TAG_KINGDOM_GARRISON)) {
			// a soldier of the kingdom owner sent as reinforcement died: the city may get a new one
			Settlement city = getData(entity.world).get(entity.getEntityData().getInteger(TAG_KINGDOM_GARRISON));
			if (city != null) {
				city.setOwnGarrison(city.getOwnGarrison() - 1);
				getData(entity.world).markDirty();
			}
		}
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
			if (settlement.getTreasury() > 0) {
				// the ruler carried the city's treasury: it falls where he died, for whoever gets there
				AncientWarfareNPC.LOG.info("Ruler of city {} died carrying {} coins of its treasury", settlement.getId(), settlement.getTreasury());
				Coins.drop(npc, settlement.getTreasury());
				settlement.setTreasury(0);
				data.markDirty();
			}
			onLeaderKilled(npc.world, data, settlement, source);
		} else if (isDefender(npc, settlement) && settlement.getManpower() > 0) {
			NBTTagCompound entityTag = snapshotForRespawn(npc);
			Entity killer = source.getTrueSource();
			AncientWarfareNPC.LOG.info("Unit {} of city {} died at {}: {} ({}{}), {} manpower left", npc.getName(), settlement.getId(), npc.getPosition(),
					source.getDamageType(), killer == null ? "no entity" : killer.getName(), killer instanceof NpcFaction ? ", faction " + ((NpcFaction) killer).getFaction() : "",
					settlement.getManpower() - (entityTag != null ? 1 : 0));
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
		CityMap.tick(world, now);
		SettlementData data = getData(world);
		if (AWDiplomacyStatics.settlementFoundRange > 0 && now % STRUCTURE_SCAN_INTERVAL == 0) {
			foundCitiesFromStructures(world, data, now);
		}
		int dimension = world.provider.getDimension();
		mergeOverlappingCities(world, data);
		updateKingdomTribute(data);

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

	/**
	 * Parts of the same town or structure are generated and found at different times (a structure before the town around it, a leader
	 * above the box of its structure...), which can make several cities of the same faction one inside the other. Active cities of the same
	 * faction whose areas overlap become one: the larger keeps its name and takes the area, manpower, garrison and pending respawns of the
	 * other; the highest ruler rules, the other becomes a captain. Cities under attack wait for the end of it.
	 */
	private static void mergeOverlappingCities(WorldServer world, SettlementData data) {
		int dimension = world.provider.getDimension();
		boolean merged = true;
		while (merged) {
			merged = false;
			List<Settlement> cities = new ArrayList<>();
			for (Settlement city : data.getAll()) {
				if (city.getDimension() == dimension && city.isActive() && city.getType() == Settlement.Type.FACTION_CITY && city.getActiveSiegeId() == 0) {
					cities.add(city);
				}
			}
			search:
			for (int i = 0; i < cities.size(); i++) {
				for (int j = i + 1; j < cities.size(); j++) {
					Settlement a = cities.get(i);
					Settlement b = cities.get(j);
					if (a.getFaction().equals(b.getFaction()) && overlaps(a, b)) {
						if (area(b) > area(a)) {
							mergeCity(world, data, a, b);
						} else {
							mergeCity(world, data, b, a);
						}
						merged = true;
						break search;
					}
				}
			}
		}
	}

	/**
	 * A new fighting soldier of the owner of a kingdom was placed with its spawner item inside a city of the kingdom: the maximum manpower of
	 * that city grows by manpower_per_unit, the manpower by the same amount, and the garrison its reinforcements bring back by one. Only at its
	 * creation: moving soldiers around changes nothing, and a soldier packed back into an item and placed again does not count.
	 */
	public static void onOwnSoldierCreated(NpcPlayerOwned npc, EntityPlayer owner) {
		if (npc.world.isRemote || !AWDiplomacyStatics.kingdomsEnabled || !AWDiplomacyStatics.settlementsEnabled || npc.isPassive()) {
			return;
		}
		KingdomData.Kingdom kingdom = Kingdoms.getOwnedBy(npc.world, owner.getUniqueID());
		if (kingdom == null) {
			return;
		}
		SettlementData data = getData(npc.world);
		Settlement city = data.getActiveAt(npc.world.provider.getDimension(), npc.getPosition(), Settlement.Type.FACTION_CITY);
		if (city == null || !city.getFaction().equals(kingdom.id)) {
			return;
		}
		int added = (int) Math.ceil(AWDiplomacyStatics.manpowerPerUnit);
		if (added <= 0) {
			return;
		}
		city.setMaxManpower(city.getBaseMaxManpower() + added);
		city.setManpower(city.getManpower() + added);
		city.setGarrisonCap(city.getGarrisonCap() + 1);
		data.markDirty();
		AncientWarfareNPC.LOG.info("City {} of {} grows to {} manpower and a garrison of {}: new soldier {} of {}", city.getId(), city.getFaction(), city.getMaxManpower(),
				city.getGarrisonCap(), npc.getName(), owner.getName());
	}
	/**
	 * The other cities of a kingdom (the ones it conquered) pay tribute to its capital, the city with the owner: kingdom_tribute_ratio of
	 * their own maximum manpower is added to the capital's maximum, and granted as manpower when it grows. The capital pays the campaigns.
	 */
	private static void updateKingdomTribute(SettlementData data) {
		for (Settlement capital : data.getAll()) {
			if (capital.getOwnerId() == null || !capital.isActive() || !Kingdoms.isKingdom(capital.getFaction())) {
				continue;
			}
			double sum = 0;
			for (Settlement city : data.getAll()) {
				if (city != capital && city.isActive() && city.getType() == Settlement.Type.FACTION_CITY && city.getOwnerId() == null
						&& city.getFaction().equals(capital.getFaction())) {
					sum += city.getBaseMaxManpower();
				}
			}
			int bonus = (int) Math.floor(sum * AWDiplomacyStatics.kingdomTributeRatio);
			if (bonus != capital.getTributeBonus()) {
				int delta = bonus - capital.getTributeBonus();
				capital.setTributeBonus(bonus);
				capital.setManpower(delta > 0 ? capital.getManpower() + delta : Math.min(capital.getManpower(), capital.getMaxManpower()));
				data.markDirty();
			}
		}
	}

	private static boolean overlaps(Settlement a, Settlement b) {
		return a.getMin().getX() <= b.getMax().getX() && a.getMax().getX() >= b.getMin().getX() && a.getMin().getZ() <= b.getMax().getZ()
				&& a.getMax().getZ() >= b.getMin().getZ();
	}

	private static long area(Settlement city) {
		return (long) (city.getMax().getX() - city.getMin().getX() + 1) * (city.getMax().getZ() - city.getMin().getZ() + 1);
	}

	private static void mergeCity(WorldServer world, SettlementData data, Settlement absorbed, Settlement into) {
		into.setBounds(new BlockPos(Math.min(into.getMin().getX(), absorbed.getMin().getX()), Math.min(into.getMin().getY(), absorbed.getMin().getY()),
						Math.min(into.getMin().getZ(), absorbed.getMin().getZ())),
				new BlockPos(Math.max(into.getMax().getX(), absorbed.getMax().getX()), Math.max(into.getMax().getY(), absorbed.getMax().getY()),
						Math.max(into.getMax().getZ(), absorbed.getMax().getZ())));
		into.setMaxManpower(into.getBaseMaxManpower() + absorbed.getBaseMaxManpower());
		into.setManpower(into.getManpower() + absorbed.getManpower());
		into.setManpowerCap(into.getManpowerCap() + absorbed.getManpowerCap());
		if (into.getGarrisonCap() > 0 || absorbed.getGarrisonCap() > 0) {
			// a conquered part: reinforcements up to the garrison of the whole merged city, estimated from its manpower as at a conquest
			// (raised to the real garrison when it is counted, see reinforce)
			into.setGarrisonCap(Math.max(into.getGarrisonCap() + absorbed.getGarrisonCap(),
					(int) Math.floor(into.getBaseMaxManpower() / Math.max(0.01D, AWDiplomacyStatics.manpowerPerUnit))));
		}
		into.getPendingRespawns().addAll(absorbed.getPendingRespawns());
		into.setLastSiegeEnd(Math.max(into.getLastSiegeEnd(), absorbed.getLastSiegeEnd()));
		into.setLastFullSiegeEnd(Math.max(into.getLastFullSiegeEnd(), absorbed.getLastFullSiegeEnd()));
		// the highest ruler rules the merged city (a city without leader yet takes the other's)
		boolean takeRuler = absorbed.getLeaderId() != null
				&& (into.getLeaderId() == null || absorbed.getLastLeaderPos().getY() > into.getLastLeaderPos().getY());
		if (takeRuler || into.getLeaderId() == null && absorbed.getLastLeaderPos().getY() > into.getLastLeaderPos().getY()) {
			into.setLeader(absorbed.getLeaderId(), absorbed.getLastLeaderPos());
			into.missingLeaderChecks = 0;
		}
		data.merge(absorbed, into);
		// loaded units move to the merged city now, the others when they are loaded (their old id leads to it)
		for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
			if (entity instanceof NpcFaction && entity.getEntityData().hasKey(TAG_SETTLEMENT) && entity.getEntityData().getInteger(TAG_SETTLEMENT) == absorbed.getId()) {
				retag((NpcFaction) entity, into);
			}
		}
		AncientWarfareNPC.LOG.info("Merged city {} ({}) into city {} ({}) of faction {}: bounds {} to {}, {} manpower", absorbed.getId(), absorbed.getName(),
				into.getId(), into.getName(), into.getFaction(), into.getMin(), into.getMax(), into.getManpower());
	}

	/**
	 * A unit tagged with a city merged into another one: tagged with that one, a ruler that does not rule it any more as a captain.
	 */
	private static void retag(NpcFaction npc, Settlement settlement) {
		String role = npc.getEntityData().getString(TAG_ROLE);
		if (ROLE_LEADER.equals(role) && !npc.getUniqueID().equals(settlement.getLeaderId())) {
			role = ROLE_CAPTAIN;
		}
		tag(npc, settlement, role);
	}

	/**
	 * @return true if a kingdom city around pos would not overlap a city (active or fallen) of the dimension
	 */
	public static boolean canFoundKingdomCity(World world, BlockPos pos) {
		int radius = AWDiplomacyStatics.kingdomRadius;
		int dimension = world.provider.getDimension();
		for (Settlement city : getData(world).getAll()) {
			if (city.getDimension() == dimension && city.getMin().getX() <= pos.getX() + radius && city.getMax().getX() >= pos.getX() - radius
					&& city.getMin().getZ() <= pos.getZ() + radius && city.getMax().getZ() >= pos.getZ() - radius) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Founds a city of a player kingdom around its town hall, with a governor (the kingdom's leader) next to the town hall. The capital is the
	 * city owned by the player; the other ones have no owner, like the conquered ones (they pay tribute to the capital). Manpower:
	 * kingdom_start_manpower plus the player's own soldiers in the city.
	 */
	@Nullable
	public static Settlement foundKingdomCity(WorldServer world, String faction, EntityPlayer owner, BlockPos townHall, boolean capital) {
		SettlementData data = getData(world);
		int radius = AWDiplomacyStatics.kingdomRadius;
		BlockPos min = new BlockPos(townHall.getX() - radius, Math.max(0, townHall.getY() - MIN_BOUNDS_BELOW), townHall.getZ() - radius);
		BlockPos max = new BlockPos(townHall.getX() + radius, 255, townHall.getZ() + radius);
		String name = SettlementNames.generate(world.rand, data.getAll(), faction);
		BlockPos post = findStandableNear(world, townHall, min, max);
		Settlement settlement = Settlement.createFactionCity(data.getNextId(), world.provider.getDimension(), name, "", faction, min, max, null, post, 0,
				getDay(world));
		if (capital) {
			settlement.setOwnerId(owner.getUniqueID());
		}
		settlement.setLastSiegeEnd(Sieges.restClock(world));
		settlement.setLastFullSiegeEnd(Sieges.restClock(world));
		int ownSoldiers = 0;
		for (NpcPlayerOwned npc : world.getEntitiesWithinAABB(NpcPlayerOwned.class, settlement.getAABB())) {
			if (npc.isEntityAlive() && !npc.isPassive() && owner.getUniqueID().equals(npc.getOwner().getUUID())) {
				ownSoldiers++;
			}
		}
		int manpower = AWDiplomacyStatics.kingdomStartManpower + (int) Math.ceil(ownSoldiers * AWDiplomacyStatics.manpowerPerUnit);
		settlement.setManpower(manpower);
		settlement.setMaxManpower(manpower);
		settlement.setGarrisonCap(AWDiplomacyStatics.kingdomGarrison);
		data.add(settlement);
		NpcFactionLeader governor = spawnGovernor(world, settlement, faction);
		if (governor != null) {
			settlement.setLeader(governor.getUniqueID(), governor.getPosition());
			tag(governor, settlement, ROLE_LEADER);
		}
		data.markDirty();
		AncientWarfareNPC.LOG.info("Founded kingdom city {} ({}) of {} for {} at {}{}, governor {}, {} own soldiers, {} manpower", settlement.getId(), name, faction,
				owner.getName(), townHall, capital ? " (capital)" : "", governor == null ? "not spawned" : "at " + governor.getPosition(), ownSoldiers, manpower);
		return settlement;
	}

	/**
	 * @return a free position (two blocks of air above a full block) within a few blocks of pos and inside min-max, or the block above pos
	 */
	private static BlockPos findStandableNear(World world, BlockPos pos, BlockPos min, BlockPos max) {
		for (int attempt = 0; attempt < 32; attempt++) {
			BlockPos column = attempt == 0 ? pos : pos.add(world.rand.nextInt(7) - 3, 0, world.rand.nextInt(7) - 3);
			for (int dy = 3; dy >= -3; dy--) {
				BlockPos candidate = column.up(dy);
				if (!candidate.equals(pos) && candidate.getX() >= min.getX() && candidate.getX() <= max.getX() && candidate.getZ() >= min.getZ()
						&& candidate.getZ() <= max.getZ() && world.isAirBlock(candidate) && world.isAirBlock(candidate.up())
						&& world.getBlockState(candidate.down()).isFullBlock()) {
					return candidate;
				}
			}
		}
		return pos.up();
	}

	public static void setManpower(World world, Settlement settlement, int manpower) {
		settlement.setManpower(manpower);
		settlement.setMaxManpower(Math.max(settlement.getBaseMaxManpower(), manpower));
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

		String name = SettlementNames.generate(world.rand, data.getAll(), leader.getFaction());
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
		settlement.setLastSiegeEnd(Sieges.restClock(world));
		settlement.setLastFullSiegeEnd(Sieges.restClock(world));
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
		// a city covers its whole column up to the build limit: towers and leaders above a flat structure box must not found a second city
		min = new BlockPos(min.getX(), Math.max(0, Math.min(min.getY(), pos.getY() - MIN_BOUNDS_BELOW)), min.getZ());
		max = new BlockPos(max.getX(), 255, max.getZ());
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
		// the structure has no leader spawner: the city gets a leader at once (structure_auto_leader_min_units)
		private final boolean needsLeader;

		private LeaderSpawn(String faction, BlockPos pos, int garrison, boolean needsLeader) {
			this.faction = faction;
			this.pos = pos;
			this.garrison = garrison;
			this.needsLeader = needsLeader;
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
			if (spawn.needsLeader && hasLoadedLeader(world, entry)) {
				// a leader placed as an entity founds the city itself (see foundCity)
				structureScanDue.put(key, now + STRUCTURE_RETRY_TICKS);
				continue;
			}
			foundCityFromSpawner(world, data, spawn);
			founded++;
		}
	}

	private static boolean hasLoadedLeader(WorldServer world, StructureEntry entry) {
		BlockPos min = entry.getBB().min;
		BlockPos max = entry.getBB().max;
		return !world.getEntitiesWithinAABB(NpcFactionLeader.class, new AxisAlignedBB(min, max.add(1, 1, 1))).isEmpty();
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
			return findLeaderlessGarrison(world, spawners);
		}
		int garrison = 0;
		for (TileAdvancedSpawner spawner : spawners) {
			garrison += countGarrisonSpawns(world, spawner.getSettings(), faction);
		}
		return new LeaderSpawn(faction, highest.getPos(), Math.max(0, Math.min(MAX_ESTIMATED_GARRISON, garrison) - 1), false);
	}

	/**
	 * A structure without leader spawner whose spawners create at least structure_auto_leader_min_units fighting units of one faction (the
	 * faction with the most) becomes a city of that faction too: its leader will be placed at the highest spawner of that faction.
	 *
	 * @return the spawn of that leader, or null if the structure has too few fighting units (or the option is off)
	 */
	@Nullable
	private static LeaderSpawn findLeaderlessGarrison(WorldServer world, List<TileAdvancedSpawner> spawners) {
		int minUnits = AWDiplomacyStatics.structureAutoLeaderMinUnits;
		if (minUnits <= 0 || spawners.isEmpty()) {
			return null;
		}
		Map<String, Integer> units = new HashMap<>();
		Map<String, TileAdvancedSpawner> highest = new HashMap<>();
		for (TileAdvancedSpawner spawner : spawners) {
			for (String faction : getSpawnedFactions(spawner.getSettings())) {
				int count = countGarrisonSpawns(world, spawner.getSettings(), faction);
				if (count <= 0) {
					continue;
				}
				units.merge(faction, count, Integer::sum);
				TileAdvancedSpawner top = highest.get(faction);
				if (top == null || spawner.getPos().getY() > top.getPos().getY()) {
					highest.put(faction, spawner);
				}
			}
		}
		String faction = null;
		for (Map.Entry<String, Integer> entry : units.entrySet()) {
			if (faction == null || entry.getValue() > units.get(faction)) {
				faction = entry.getKey();
			}
		}
		if (faction == null || units.get(faction) < minUnits) {
			return null;
		}
		return new LeaderSpawn(faction, highest.get(faction).getPos(), Math.min(MAX_ESTIMATED_GARRISON, units.get(faction)), true);
	}

	/**
	 * @return the existing factions of the npcs a spawner creates
	 */
	private static Set<String> getSpawnedFactions(SpawnerSettings settings) {
		Set<String> factions = new HashSet<>();
		for (SpawnerSettings.EntitySpawnGroup group : settings.getSpawnGroups()) {
			for (SpawnerSettings.EntitySpawnSettings spawn : group.getEntitiesToSpawn()) {
				NBTTagCompound tag = spawn.getCustomTag();
				if (tag != null && FactionRegistry.getFactionNames().contains(tag.getString("factionName"))) {
					factions.add(tag.getString("factionName"));
				}
			}
		}
		return factions;
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
		String name = SettlementNames.generate(world.rand, data.getAll(), spawn.faction);
		Settlement settlement = Settlement.createFactionCity(data.getNextId(), world.provider.getDimension(), name, bounds.structureName, spawn.faction, bounds.min,
				bounds.max, null, spawn.pos, 0, getDay(world));
		// no siege right after the founding (siege_cooldown_days)
		settlement.setLastSiegeEnd(Sieges.restClock(world));
		settlement.setLastFullSiegeEnd(Sieges.restClock(world));
		int manpower = Math.max(AWDiplomacyStatics.manpowerMin, (int) Math.ceil(spawn.garrison * AWDiplomacyStatics.manpowerPerUnit));
		settlement.setManpower(manpower);
		settlement.setMaxManpower(manpower);
		data.add(settlement);
		if (spawn.needsLeader) {
			// a structure without leader: a leader of the faction is placed at once at its highest spawner
			NpcFactionLeader leader = spawnGovernor(world, settlement, spawn.faction);
			if (leader != null) {
				settlement.setLeader(leader.getUniqueID(), leader.getPosition());
				tag(leader, settlement, ROLE_LEADER);
				data.markDirty();
			}
			AncientWarfareNPC.LOG.info("Founded city {} ({}) of faction {} from its structure {} without leader: about {} garrison units, {} manpower, leader {}",
					settlement.getId(), name, settlement.getFaction(), bounds.structureName, spawn.garrison, manpower,
					leader == null ? "not placed" : "placed at " + leader.getPosition());
			return;
		}
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

	/**
	 * @return the coins a city pays as taxes every day: city_tax_per_manpower of its own maximum manpower, at least 1
	 */
	public static int getDailyTax(Settlement settlement) {
		return Math.max(1, (int) Math.round(settlement.getBaseMaxManpower() * AWDiplomacyStatics.cityTaxPerManpower));
	}

	/**
	 * Manpower a city regains each day: manpower_regen_ratio of its maximum (its size: 2 per garrison unit), at least manpower_regen_per_day
	 */
	public static int getDailyManpowerRegen(Settlement settlement) {
		return Math.max(AWDiplomacyStatics.manpowerRegenPerDay, (int) Math.round(settlement.getBaseMaxManpower() * AWDiplomacyStatics.manpowerRegenRatio));
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
		if (!Sieges.isBesieged(settlement)) {
			// taxes: coins for the owner of the city, up to city_tax_max_days days of them
			int daily = getDailyTax(settlement);
			settlement.setTreasury((int) Math.min((long) daily * AWDiplomacyStatics.cityTaxMaxDays, settlement.getTreasury() + days * daily));
		}
		if (AWDiplomacyStatics.conqueredManpowerGrowthPerDay > 0 && settlement.getMaxManpower() < settlement.getManpowerCap() && !Sieges.isBesieged(settlement)) {
			// a conquered city slowly grows back to the size it had before the conquest
			long growth = days * AWDiplomacyStatics.conqueredManpowerGrowthPerDay;
			settlement.setMaxManpower((int) Math.min(settlement.getManpowerCap(), settlement.getMaxManpower() + growth));
		}
		if (getDailyManpowerRegen(settlement) > 0 && settlement.getManpower() < settlement.getMaxManpower() && !Sieges.isBesieged(settlement)) {
			long regen = (day - settlement.getLastRegenDay()) * getDailyManpowerRegen(settlement);
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
				// no announcement and the spawners of the structure stay, as in plain Ancient Warfare
				settlement.setAbandoned();
				data.markDirty();
				checkFactionGone(world, settlement.getFaction());
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
			// the leader was removed without dying (e.g. Ancient Warfare structure conquest, a chunk that was not saved)
			if (replaceMissingLeader(world, data, settlement, leaderId)) {
				return;
			}
			AncientWarfareNPC.LOG.info("Leader {} of city {} missing for {} checks around {}, the city falls", leaderId, settlement.getId(),
					MISSING_LEADER_CHECKS, settlement.getLastLeaderPos());
			collapse(world, data, settlement);
		}
	}

	/**
	 * A leader that vanished without being killed is not a lost war: with missing_leader_manpower_cost manpower the city raises a new leader
	 * where the old one was last seen (an old leader that comes back later becomes a captain, see retag).
	 *
	 * @return true if a new leader rules the city
	 */
	private static boolean replaceMissingLeader(WorldServer world, SettlementData data, Settlement settlement, UUID missing) {
		int cost = AWDiplomacyStatics.missingLeaderManpowerCost;
		if (!AWDiplomacyStatics.replaceMissingLeader || settlement.getManpower() < cost) {
			return false;
		}
		NpcFactionLeader leader = spawnGovernor(world, settlement, settlement.getFaction());
		if (leader == null) {
			return false;
		}
		settlement.setManpower(settlement.getManpower() - cost);
		settlement.setLeader(leader.getUniqueID(), leader.getPosition());
		settlement.missingLeaderChecks = 0;
		tag(leader, settlement, ROLE_LEADER);
		data.markDirty();
		AncientWarfareNPC.LOG.info("Leader {} of city {} ({}) missing for {} checks: a new leader {} rules it at {}, for {} manpower ({} left)", missing,
				settlement.getId(), settlement.getName(), MISSING_LEADER_CHECKS, leader.getUniqueID(), leader.getPosition(), cost, settlement.getManpower());
		return true;
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
		checkFactionGone(world, settlement.getFaction());
	}

	/**
	 * A city of the faction is gone (fallen, conquered or dropped): if the faction has no active city left in the world, its members
	 * are released from their oath (leave_when_faction_gone).
	 */
	private static void checkFactionGone(World world, String faction) {
		MinecraftServer server = world.getMinecraftServer();
		if (server != null && !Diplomacy.getFactionsWithCities(world).contains(faction)) {
			AncientWarfareNPC.LOG.info("The faction {} has no city left", faction);
			if (Kingdoms.isKingdom(faction)) {
				Kingdoms.destroy(server, faction); // its owner and members leave it, whatever leave_when_faction_gone says
			} else {
				FactionMembership.onFactionGone(server, faction);
			}
		}
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
		settlement.setOwnerId(null); // a conquered kingdom capital is a city of the winners now
		settlement.setTreasury(0); // the winners take what was in it
		checkFactionGone(world, oldFaction);
		settlement.getPendingRespawns().clear();
		settlement.missingLeaderChecks = 0;
		long now = world.getTotalWorldTime();
		settlement.setLastSiegeEnd(Sieges.restClock(world));
		settlement.setLastFullSiegeEnd(Sieges.restClock(world));
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
		garrison += countOwnerSoldiers(world, settlement);
		if (garrison >= settlement.getGarrisonCap()) {
			// the real garrison is larger (e.g. cities merged into this one): it is the garrison reinforcements bring back. Not in a kingdom:
			// the owner's soldiers walked in would raise it, there it grows only with new soldiers (onOwnSoldierCreated)
			if (garrison > settlement.getGarrisonCap() && !Kingdoms.isKingdom(settlement.getFaction())) {
				settlement.setGarrisonCap(garrison);
				getData(world).markDirty();
			}
			return;
		}
		if (Kingdoms.isKingdom(settlement.getFaction())) {
			reinforceKingdomCity(world, settlement);
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
	 * @return the fighting soldiers of the kingdom owner loaded inside a city of a kingdom (reinforcements, recruits, crafted soldiers), 0 for
	 * other cities
	 */
	public static int countOwnerSoldiers(World world, Settlement settlement) {
		KingdomData.Kingdom kingdom = Kingdoms.get(world, settlement.getFaction());
		if (kingdom == null) {
			return 0;
		}
		int count = 0;
		for (NpcPlayerOwned npc : world.getEntitiesWithinAABB(NpcPlayerOwned.class, settlement.getAABB())) {
			if (npc.isEntityAlive() && !npc.isPassive() && kingdom.ownerId.equals(npc.getOwner().getUUID())) {
				count++;
			}
		}
		return count;
	}

	/**
	 * A city of a player kingdom is reinforced with soldiers of the owner (like recruits and crafted soldiers: they can be made to follow and
	 * ordered with the command baton, and never fight the owner's other soldiers). They wait for the owner to be online, are counted in
	 * Settlement.ownGarrison until they die, and do not make the manpower grow (only soldiers placed with their item do, onOwnSoldierCreated).
	 */
	private static void reinforceKingdomCity(WorldServer world, Settlement settlement) {
		KingdomData.Kingdom kingdom = Kingdoms.get(world, settlement.getFaction());
		EntityPlayer owner = kingdom == null ? null : world.getMinecraftServer().getPlayerList().getPlayerByUUID(kingdom.ownerId);
		if (owner == null) {
			return;
		}
		NpcCombat soldier = new NpcCombat(world);
		soldier.setOwner(owner);
		BlockPos pos = findRecruitPos(world, settlement);
		soldier.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360F, 0F);
		soldier.setHomeAreaAtCurrentPosition();
		// soldiers and archers of the kingdom's culture, 3 to 2
		Recruits.equip(soldier, 1, world.rand.nextInt(5) < 3 ? KingdomLook.ROLE_SOLDIER : KingdomLook.ROLE_ARCHER);
		soldier.setHealth(soldier.getMaxHealth());
		soldier.getEntityData().setInteger(TAG_KINGDOM_GARRISON, settlement.getId());
		if (world.spawnEntity(soldier)) {
			settlement.setOwnGarrison(settlement.getOwnGarrison() + 1);
			getData(world).markDirty();
			AncientWarfareNPC.LOG.info("Kingdom city {} ({}) reinforced with a soldier of {} at {}, cap {}", settlement.getId(), settlement.getName(), owner.getName(),
					soldier.getPosition(), settlement.getGarrisonCap());
			world.spawnParticle(EnumParticleTypes.CLOUD, soldier.posX, soldier.posY + 1D, soldier.posZ, 10, 0.3D, 0.5D, 0.3D, 0.02D);
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
