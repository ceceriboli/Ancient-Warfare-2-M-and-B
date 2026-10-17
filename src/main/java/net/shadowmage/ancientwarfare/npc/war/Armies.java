package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.ai.faction.NpcAIFactionAssault;
import net.shadowmage.ancientwarfare.npc.ai.faction.NpcAIFactionFollowCommander;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;
import net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFactionLeader;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelations;
import net.shadowmage.ancientwarfare.npc.faction.FactionTracker;
import net.shadowmage.ancientwarfare.npc.gamedata.ArmyData;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Armies led by players (phase 6). A member of a faction raises a levy from the ruler of a city of its faction (taken from the city's
 * manpower): the soldiers follow it (NpcAIFactionFollowCommander). On a charge the levy, and the player's own Ancient Warfare combat npcs
 * that follow it, assault the nearest enemy city (NpcAIFactionAssault on its ruler); if the ruler dies the city passes to the winner as
 * for any siege. On disband the survivors of the levy go back to the manpower of their city.
 * <p>
 * Levy soldiers carry their commander in TAG_ARMY, the player's own npcs in TAG_CHARGE while they charge (their follow and home are
 * saved and given back afterwards: Ancient Warfare shortens the attack range of an npc the farther it is from the player it follows).
 */
public class Armies {
	private Armies() {}

	private static final String TAG_ARMY_ORIGIN = "awArmyOrigin";
	private static final String TAG_CHARGE = "awCharge";
	private static final String TAG_CHARGE_HOME = "awChargeHome";
	private static final String TAG_CHARGE_HOME_RANGE = "awChargeHomeRange";
	private static final String WEIGHTS_FALLBACK = "faction.soldier";
	private static final int MAX_GATHERING_HEIGHT_DIFF = 3;

	private static final Map<UUID, List<WeakReference<NpcBase>>> members = new HashMap<>();

	public static ArmyData getData(World world) {
		return AWGameData.INSTANCE.getData(world, ArmyData.class);
	}

	@Nullable
	private static UUID getCommanderId(Entity npc) {
		NBTTagCompound data = npc.getEntityData();
		String id = data.hasKey(Settlements.TAG_ARMY) ? data.getString(Settlements.TAG_ARMY) : data.getString(TAG_CHARGE);
		if (id.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(id);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Nullable
	public static EntityPlayer getCommander(NpcBase npc) {
		UUID id = getCommanderId(npc);
		return id == null ? null : npc.world.getPlayerEntityByUUID(id);
	}

	public static boolean isCharging(NpcBase npc) {
		UUID id = getCommanderId(npc);
		ArmyData.Army army = id == null ? null : getData(npc.world).get(id);
		return army != null && army.getTargetCity() != 0;
	}

	private static List<NpcBase> getMembers(UUID commander) {
		List<NpcBase> ret = new ArrayList<>();
		List<WeakReference<NpcBase>> refs = members.get(commander);
		if (refs == null) {
			return ret;
		}
		Iterator<WeakReference<NpcBase>> it = refs.iterator();
		while (it.hasNext()) {
			NpcBase npc = it.next().get();
			UUID id = npc == null ? null : getCommanderId(npc);
			if (npc == null || !npc.isEntityAlive() || !npc.isAddedToWorld() || !commander.equals(id)) {
				it.remove();
			} else if (!ret.contains(npc)) {
				ret.add(npc);
			}
		}
		return ret;
	}

	private static void addMember(UUID commander, NpcBase npc) {
		members.computeIfAbsent(commander, k -> new ArrayList<>()).add(new WeakReference<>(npc));
	}

	private static boolean isLevy(NpcBase npc) {
		return npc.getEntityData().hasKey(Settlements.TAG_ARMY);
	}

	/**
	 * Raises a levy from the ruler of a city of the player's faction, standing near the player.
	 */
	public static void raise(EntityPlayerMP player) throws CommandException {
		checkEnabled();
		World world = player.world;
		String faction = FactionMembership.getFaction(world, player.getUniqueID());
		if (faction == null) {
			throw new CommandException("command.aw.army.no_faction");
		}
		Optional<NpcFactionLeader> leader = FactionMembership.findNearestLeader(world, player, faction, AWDiplomacyStatics.leaderJoinRange);
		Settlement city = leader.map(Settlements::getSettlementOf).orElse(null);
		if (city == null || !city.isActive() || !faction.equals(city.getFaction())) {
			throw new CommandException("command.aw.army.no_ruler", FactionMembership.factionName(faction));
		}
		UUID commander = player.getUniqueID();
		if (getMembers(commander).stream().anyMatch(Armies::isLevy)) {
			throw new CommandException("command.aw.army.already");
		}
		int count = Math.min(AWDiplomacyStatics.armySize, city.getManpower());
		if (count <= 0) {
			throw new CommandException("command.aw.army.no_manpower", Settlements.displayName(city));
		}
		// the army exists before its soldiers join the world (onEntityJoinWorld disbands levy soldiers without army)
		ArmyData.Army army = getData(world).getOrCreate(commander);
		army.setOriginCity(city.getId());
		army.setTargetCity(0);
		List<String> types = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		Sieges.parseWeights(AWDiplomacyStatics.armyUnitWeights, WEIGHTS_FALLBACK, types, weights);
		int raised = 0;
		// the army gathers in the open (under the sky) inside the city, near the player, not in the ruler's hall among doors
		BlockPos gathering = findOpenGround(world, city, player.getPosition());
		for (int i = 0; i < count; i++) {
			NpcFaction unit = Sieges.createWeightedUnit(world, types, weights, faction);
			if (unit == null) {
				continue;
			}
			BlockPos pos = findStandableNear(world, gathering);
			unit.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360F, 0F);
			unit.setHomeAreaAtCurrentPosition();
			unit.setHealth(unit.getMaxHealth());
			unit.getEntityData().setString(Settlements.TAG_ARMY, commander.toString());
			unit.getEntityData().setInteger(TAG_ARMY_ORIGIN, city.getId());
			if (world.spawnEntity(unit)) {
				raised++;
				if (world instanceof WorldServer) {
					((WorldServer) world).spawnParticle(EnumParticleTypes.CLOUD, unit.posX, unit.posY + 1D, unit.posZ, 10, 0.3D, 0.5D, 0.3D, 0.02D);
				}
			}
		}
		city.setManpower(city.getManpower() - raised);
		Settlements.getData(world).markDirty();
		player.sendMessage(new TextComponentTranslation("command.aw.army.raised", raised, Settlements.displayName(city), city.getManpower(),
				gathering.getX(), gathering.getY(), gathering.getZ()));
		AncientWarfareNPC.LOG.info("{} raised an army of {} from city {} ({})", player.getName(), raised, city.getId(), faction);
	}

	/**
	 * The army (levy and the player's own combat npcs following it) assaults the nearest enemy city.
	 */
	public static void charge(EntityPlayerMP player) throws CommandException {
		checkEnabled();
		World world = player.world;
		UUID commander = player.getUniqueID();
		Settlement target = findEnemyCity(player);
		if (target == null) {
			throw new CommandException("command.aw.army.no_target", AWDiplomacyStatics.armyChargeRange);
		}
		int own = 0;
		for (NpcPlayerOwned npc : world.getEntitiesWithinAABB(NpcPlayerOwned.class, player.getEntityBoundingBox().grow(AWDiplomacyStatics.armyOwnedNpcRange))) {
			if (npc.isEntityAlive() && !npc.isPassive() && commander.equals(npc.getOwner().getUUID()) && npc.getFollowingEntity() == player
					&& !npc.getEntityData().hasKey(TAG_CHARGE)) {
				NBTTagCompound data = npc.getEntityData();
				data.setString(TAG_CHARGE, commander.toString());
				if (npc.hasHome()) {
					data.setLong(TAG_CHARGE_HOME, npc.getHomePosition().toLong());
					data.setInteger(TAG_CHARGE_HOME_RANGE, npc.getHomeRange());
				}
				npc.clearFollowingEntity();
				addMember(commander, npc);
				own++;
			}
		}
		int levy = (int) getMembers(commander).stream().filter(Armies::isLevy).count();
		if (levy + own == 0) {
			throw new CommandException("command.aw.army.no_soldiers");
		}
		ArmyData.Army army = getData(world).getOrCreate(commander);
		army.setTargetCity(target.getId());
		player.sendMessage(new TextComponentTranslation("command.aw.army.charge", Settlements.displayName(target), levy, own));
		AncientWarfareNPC.LOG.info("{} charges city {} ({}) with {} levy and {} own soldiers", player.getName(), target.getId(), target.getFaction(), levy, own);
	}

	/**
	 * Ends the charge: the army follows its commander again.
	 */
	public static void follow(EntityPlayerMP player) throws CommandException {
		ArmyData.Army army = getData(player.world).get(player.getUniqueID());
		if (army == null || army.getTargetCity() == 0) {
			throw new CommandException("command.aw.army.not_charging");
		}
		endCharge(player.world, player.getUniqueID());
		player.sendMessage(new TextComponentTranslation("command.aw.army.follow"));
	}

	/**
	 * The levy goes home: its survivors go back to the manpower of their city (if it still belongs to their faction).
	 */
	public static void disband(EntityPlayerMP player) throws CommandException {
		World world = player.world;
		UUID commander = player.getUniqueID();
		ArmyData.Army army = getData(world).get(commander);
		List<NpcBase> army_members = getMembers(commander);
		if (army == null && army_members.isEmpty()) {
			throw new CommandException("command.aw.army.none");
		}
		endCharge(world, commander);
		int returned = 0;
		Settlement origin = army == null ? null : Settlements.getData(world).get(army.getOriginCity());
		long now = world.getTotalWorldTime();
		for (NpcBase npc : getMembers(commander)) {
			if (isLevy(npc) && npc instanceof NpcFaction) {
				NpcFaction unit = (NpcFaction) npc;
				if (origin != null && origin.isActive() && origin.getFaction().equals(unit.getFaction())) {
					returned++;
				}
				unit.setAttackTarget(null);
				Settlements.disband(unit, now + 20 + world.rand.nextInt(60));
			}
		}
		if (origin != null && returned > 0) {
			origin.setManpower(Math.min(origin.getMaxManpower(), origin.getManpower() + returned));
			Settlements.getData(world).markDirty();
		}
		getData(world).remove(commander);
		members.remove(commander);
		player.sendMessage(new TextComponentTranslation("command.aw.army.disbanded", returned, origin == null ? "-" : Settlements.displayName(origin)));
	}

	public static void status(EntityPlayerMP player) {
		UUID commander = player.getUniqueID();
		ArmyData.Army army = getData(player.world).get(commander);
		List<NpcBase> list = getMembers(commander);
		long levy = list.stream().filter(Armies::isLevy).count();
		long own = list.size() - levy;
		Settlement origin = army == null ? null : Settlements.getData(player.world).get(army.getOriginCity());
		Settlement target = army == null || army.getTargetCity() == 0 ? null : Settlements.getData(player.world).get(army.getTargetCity());
		player.sendMessage(new TextComponentTranslation("command.aw.army.status", levy, origin == null ? "-" : Settlements.displayName(origin), own,
				target == null ? new TextComponentTranslation("command.aw.army.status.following") : Settlements.displayName(target)));
	}

	private static void checkEnabled() throws CommandException {
		if (!AWDiplomacyStatics.armiesEnabled || !AWDiplomacyStatics.settlementsEnabled) {
			throw new CommandException("command.aw.army.disabled");
		}
	}

	/**
	 * @return the nearest active city within army_charge_range whose faction is hostile to the player (to its faction if it has one)
	 */
	@Nullable
	private static Settlement findEnemyCity(EntityPlayerMP player) {
		World world = player.world;
		String faction = FactionMembership.getFaction(world, player.getUniqueID());
		int dimension = world.provider.getDimension();
		double rangeSq = (double) AWDiplomacyStatics.armyChargeRange * AWDiplomacyStatics.armyChargeRange;
		Settlement nearest = null;
		double nearestDistSq = Double.MAX_VALUE;
		for (Settlement city : Settlements.getData(world).getAll()) {
			if (!city.isActive() || city.getType() != Settlement.Type.FACTION_CITY || city.getDimension() != dimension || city.getFaction().equals(faction)) {
				continue;
			}
			boolean hostile = faction != null ? FactionRelations.isHostile(world, faction, city.getFaction())
					: FactionTracker.INSTANCE.isHostileToPlayer(world, player.getUniqueID(), player.getName(), city.getFaction());
			double distSq = player.getDistanceSq(city.getCenter());
			if (hostile && distSq <= rangeSq && distSq < nearestDistSq) {
				nearest = city;
				nearestDistSq = distSq;
			}
		}
		return nearest;
	}

	/**
	 * A levy soldier or a charging npc of the player was loaded: it joins its army again, or goes back to normal if the army is gone.
	 */
	public static void onEntityJoinWorld(Entity entity) {
		if (entity.world.isRemote || !(entity instanceof NpcBase)) {
			return;
		}
		NpcBase npc = (NpcBase) entity;
		UUID commander = getCommanderId(npc);
		if (commander == null) {
			return;
		}
		ArmyData.Army army = getData(npc.world).get(commander);
		if (isLevy(npc)) {
			if (army == null) {
				if (npc instanceof NpcFaction) {
					Settlements.disband((NpcFaction) npc, npc.world.getTotalWorldTime() + 20);
				}
				return;
			}
			addMember(commander, npc);
			addFollowAI(npc);
			Sieges.getOrAddAssaultAI(npc);
		} else if (army == null || army.getTargetCity() == 0) {
			restoreOwnNpc(npc);
		} else {
			addMember(commander, npc);
			Sieges.getOrAddAssaultAI(npc);
		}
	}

	private static void addFollowAI(NpcBase npc) {
		for (EntityAITasks.EntityAITaskEntry entry : npc.tasks.taskEntries) {
			if (entry.action instanceof NpcAIFactionFollowCommander) {
				return;
			}
		}
		npc.tasks.addTask(NpcAIFactionFollowCommander.PRIORITY, new NpcAIFactionFollowCommander(npc));
	}

	/**
	 * Called every tick for each server world: keeps the charges aimed at the ruler of their city and ends them when it falls.
	 */
	public static void tick(WorldServer world) {
		if (world.getTotalWorldTime() % 20 != 0) {
			return;
		}
		ArmyData data = getData(world);
		for (Map.Entry<UUID, ArmyData.Army> e : new ArrayList<>(data.getAll().entrySet())) {
			UUID commander = e.getKey();
			List<NpcBase> list = getMembers(commander);
			if (list.isEmpty() || list.get(0).world != world) {
				continue;
			}
			int targetId = e.getValue().getTargetCity();
			Settlement target = targetId == 0 ? null : Settlements.getData(world).get(targetId);
			if (targetId != 0 && (target == null || !isStillEnemy(world, commander, target))) {
				endCharge(world, commander);
				EntityPlayer player = world.getPlayerEntityByUUID(commander);
				if (player != null) {
					player.sendMessage(target == null ? new TextComponentTranslation("command.aw.army.charge_over_unknown")
							: new TextComponentTranslation(target.isActive() ? "command.aw.army.charge_over_taken" : "command.aw.army.charge_over_fallen",
							Settlements.displayName(target)));
				}
				target = null;
			}
			BlockPos leaderPos = target == null ? null : Sieges.getTarget(world, target);
			boolean breach = !"none".equals(AWDiplomacyStatics.attackerBreach);
			EntityPlayer player = world.getPlayerEntityByUUID(commander);
			for (NpcBase npc : list) {
				// charging: the home follows the soldier (Ancient Warfare shortens the attack range of an npc far from home); following:
				// the home is on the commander, so that the move home AIs (archers stay at home) do not pull the soldier back
				BlockPos home = target == null && player != null ? player.getPosition() : npc.getPosition();
				npc.setHomePosAndDistance(home, npc.getHomeRange());
				NpcAIFactionAssault ai = Sieges.getOrAddAssaultAI(npc);
				ai.setTarget(leaderPos);
				ai.setBreachAllowed(breach);
			}
		}
	}

	/**
	 * @return false once the city is abandoned or held by a faction that is no longer hostile to the commander (e.g. its own after a conquest)
	 */
	private static boolean isStillEnemy(World world, UUID commander, Settlement city) {
		if (!city.isActive()) {
			return false;
		}
		String faction = FactionMembership.getFaction(world, commander);
		if (faction != null) {
			return !faction.equals(city.getFaction()) && FactionRelations.isHostile(world, faction, city.getFaction());
		}
		EntityPlayer player = world.getPlayerEntityByUUID(commander);
		return player == null || FactionTracker.INSTANCE.isHostileToPlayer(world, commander, player.getName(), city.getFaction());
	}

	private static void endCharge(World world, UUID commander) {
		ArmyData.Army army = getData(world).get(commander);
		if (army != null) {
			army.setTargetCity(0);
			getData(world).markDirty();
		}
		for (NpcBase npc : getMembers(commander)) {
			npc.setAttackTarget(null);
			Sieges.getOrAddAssaultAI(npc).setTarget(null);
			if (!isLevy(npc)) {
				restoreOwnNpc(npc);
			}
		}
	}

	/**
	 * Gives back to one of the player's own npcs its follow and its home after a charge.
	 */
	private static void restoreOwnNpc(NpcBase npc) {
		NBTTagCompound data = npc.getEntityData();
		UUID commander = getCommanderId(npc);
		if (data.hasKey(TAG_CHARGE_HOME)) {
			npc.setHomePosAndDistance(BlockPos.fromLong(data.getLong(TAG_CHARGE_HOME)), data.getInteger(TAG_CHARGE_HOME_RANGE));
		}
		data.removeTag(TAG_CHARGE);
		data.removeTag(TAG_CHARGE_HOME);
		data.removeTag(TAG_CHARGE_HOME_RANGE);
		for (EntityAITasks.EntityAITaskEntry entry : npc.tasks.taskEntries) {
			if (entry.action instanceof NpcAIFactionAssault) {
				((NpcAIFactionAssault) entry.action).setTarget(null);
			}
		}
		EntityPlayer player = commander == null ? null : npc.world.getPlayerEntityByUUID(commander);
		if (player != null) {
			npc.setFollowingEntity(player);
		}
	}

	/**
	 * @return the open ground (surface under the sky, not water) inside the city nearest to pos, searched in growing rings, or pos
	 */
	private static BlockPos findOpenGround(World world, Settlement city, BlockPos pos) {
		int dimension = world.provider.getDimension();
		for (int radius = 0; radius <= 32; radius += 4) {
			for (int attempt = 0; attempt < 12; attempt++) {
				double angle = world.rand.nextDouble() * Math.PI * 2D;
				BlockPos column = pos.add((int) (Math.cos(angle) * radius), 0, (int) (Math.sin(angle) * radius));
				BlockPos top = world.getTopSolidOrLiquidBlock(column);
				// near the player's height: the highest block under the sky in a city is often a roof
				if (city.contains(dimension, top) && Math.abs(top.getY() - pos.getY()) <= MAX_GATHERING_HEIGHT_DIFF && world.canSeeSky(top)
						&& world.isAirBlock(top.up()) && !world.getBlockState(top.down()).getMaterial().isLiquid()) {
					return top;
				}
			}
		}
		return pos;
	}

	private static BlockPos findStandableNear(World world, BlockPos pos) {
		for (int attempt = 0; attempt < 16; attempt++) {
			BlockPos column = pos.add(world.rand.nextInt(9) - 4, 0, world.rand.nextInt(9) - 4);
			for (int dy = 3; dy >= -3; dy--) {
				BlockPos candidate = column.up(dy);
				if (world.isAirBlock(candidate) && world.isAirBlock(candidate.up()) && world.getBlockState(candidate.down()).isFullBlock()) {
					return candidate;
				}
			}
		}
		return pos;
	}
}
