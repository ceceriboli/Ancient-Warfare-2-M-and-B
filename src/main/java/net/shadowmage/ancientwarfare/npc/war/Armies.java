package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.ai.faction.NpcAIFactionAssault;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;
import net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The player's own soldiers in the attacks of its faction (military service, see {@link Service}). When a member of a faction takes part
 * in an attack of its faction, its own Ancient Warfare combat npcs that follow it join the assault on the city leader
 * (NpcAIFactionAssault), and follow it again when the attack is over.
 * <p>
 * Charging npcs carry their commander in TAG_CHARGE and the attack in TAG_CHARGE_SIEGE; their follow and home are saved and given back
 * afterwards (Ancient Warfare shortens the attack range of an npc the farther it is from home, or from the player it follows).
 * <p>
 * The levy raised from a ruler (TAG_ARMY) was replaced by the military service: levy soldiers still in the world go back to the
 * manpower of their city when they are loaded.
 */
public class Armies {
	private Armies() {}

	private static final String TAG_ARMY_ORIGIN = "awArmyOrigin";
	private static final String TAG_CHARGE = "awCharge";
	private static final String TAG_CHARGE_SIEGE = "awChargeSiege";
	private static final String TAG_CHARGE_HOME = "awChargeHome";
	private static final String TAG_CHARGE_HOME_RANGE = "awChargeHomeRange";

	private static final Map<UUID, List<WeakReference<NpcBase>>> members = new HashMap<>();

	@Nullable
	private static UUID getCommanderId(Entity npc) {
		String id = npc.getEntityData().getString(TAG_CHARGE);
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

	/**
	 * @return the id of the siege the npc attacks in, as an attacker of the siege or as a soldier of a player that joined its assault; 0 if none
	 */
	public static int getSiegeId(Entity npc) {
		NBTTagCompound data = npc.getEntityData();
		return data.hasKey(Settlements.TAG_SIEGE) ? data.getInteger(Settlements.TAG_SIEGE) : data.getInteger(TAG_CHARGE_SIEGE);
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
			if (npc == null || !npc.isEntityAlive() || !npc.isAddedToWorld() || !commander.equals(getCommanderId(npc))) {
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

	/**
	 * The player takes part in an attack of its faction: its own combat npcs that follow it, and are not charging yet, join the assault.
	 */
	static void joinAssault(EntityPlayerMP player, Siege siege) {
		if (!AWDiplomacyStatics.armiesEnabled) {
			return;
		}
		UUID commander = player.getUniqueID();
		int joined = 0;
		for (NpcPlayerOwned npc : player.world.getEntitiesWithinAABB(NpcPlayerOwned.class, player.getEntityBoundingBox().grow(AWDiplomacyStatics.armyOwnedNpcRange))) {
			if (npc.isEntityAlive() && !npc.isPassive() && commander.equals(npc.getOwner().getUUID()) && npc.getFollowingEntity() == player
					&& !npc.getEntityData().hasKey(TAG_CHARGE)) {
				NBTTagCompound data = npc.getEntityData();
				data.setString(TAG_CHARGE, commander.toString());
				data.setInteger(TAG_CHARGE_SIEGE, siege.getId());
				if (npc.hasHome()) {
					data.setLong(TAG_CHARGE_HOME, npc.getHomePosition().toLong());
					data.setInteger(TAG_CHARGE_HOME_RANGE, npc.getHomeRange());
				}
				npc.clearFollowingEntity();
				addMember(commander, npc);
				joined++;
			}
		}
		if (joined > 0) {
			siege.addOwnSoldiers(joined);
			player.sendMessage(new TextComponentTranslation("command.aw.army.join", joined));
			AncientWarfareNPC.LOG.info("{}: {} own soldiers join the assault of {} {}", player.getName(), joined, siege.getKind().getName(), siege.getId());
		}
	}

	/**
	 * The attack is over: the npcs that charged in it follow their commander again.
	 */
	static void releaseAll(World world, int siegeId) {
		for (UUID commander : new ArrayList<>(members.keySet())) {
			int released = 0;
			for (NpcBase npc : getMembers(commander)) {
				if (npc.getEntityData().getInteger(TAG_CHARGE_SIEGE) == siegeId) {
					restoreOwnNpc(npc);
					released++;
				}
			}
			EntityPlayer player = world.getPlayerEntityByUUID(commander);
			if (released > 0 && player != null) {
				player.sendMessage(new TextComponentTranslation("command.aw.army.released", released));
			}
		}
	}

	public static void status(EntityPlayerMP player) {
		List<NpcBase> list = getMembers(player.getUniqueID());
		SettlementData data = Settlements.getData(player.world);
		Siege siege = list.isEmpty() ? null : data.getSiege(list.get(0).getEntityData().getInteger(TAG_CHARGE_SIEGE));
		Settlement target = siege == null ? null : data.get(siege.getSettlementId());
		player.sendMessage(new TextComponentTranslation("command.aw.army.status", list.size(),
				target == null ? new TextComponentTranslation("command.aw.army.status.following") : Settlements.displayName(target)));
	}

	/**
	 * A charging npc of a player was loaded: it joins its attack again, or follows its commander if the attack is over.
	 * A levy soldier (replaced by the military service) goes back to the manpower of its city.
	 */
	public static void onEntityJoinWorld(Entity entity) {
		if (entity.world.isRemote || !(entity instanceof NpcBase)) {
			return;
		}
		NpcBase npc = (NpcBase) entity;
		NBTTagCompound data = npc.getEntityData();
		if (data.hasKey(Settlements.TAG_ARMY)) {
			if (npc instanceof NpcFaction) {
				disbandLevy((NpcFaction) npc);
			}
			return;
		}
		if (!data.hasKey(TAG_CHARGE)) {
			return;
		}
		UUID commander = getCommanderId(npc);
		Siege siege = Settlements.getData(npc.world).getSiege(data.getInteger(TAG_CHARGE_SIEGE));
		if (commander == null || siege == null) {
			restoreOwnNpc(npc);
		} else {
			addMember(commander, npc);
			Sieges.getOrAddAssaultAI(npc);
		}
	}

	private static void disbandLevy(NpcFaction npc) {
		NBTTagCompound data = npc.getEntityData();
		if (data.hasKey(TAG_ARMY_ORIGIN)) {
			SettlementData settlements = Settlements.getData(npc.world);
			Settlement origin = settlements.get(data.getInteger(TAG_ARMY_ORIGIN));
			if (origin != null && origin.isActive() && origin.getFaction().equals(npc.getFaction())) {
				origin.setManpower(Math.min(origin.getMaxManpower(), origin.getManpower() + 1));
				settlements.markDirty();
			}
			// counted once, even if the soldier is loaded again before it vanishes
			data.removeTag(TAG_ARMY_ORIGIN);
		}
		Settlements.disband(npc, npc.world.getTotalWorldTime() + 20);
	}

	/**
	 * Called every tick for each server world: keeps the charging npcs aimed at the leader of the city they attack.
	 */
	public static void tick(WorldServer world) {
		if (world.getTotalWorldTime() % 20 != 0) {
			return;
		}
		SettlementData data = Settlements.getData(world);
		for (UUID commander : new ArrayList<>(members.keySet())) {
			List<NpcBase> list = getMembers(commander);
			if (list.isEmpty()) {
				members.remove(commander);
				continue;
			}
			for (NpcBase npc : list) {
				if (npc.world != world) {
					continue;
				}
				Siege siege = data.getSiege(npc.getEntityData().getInteger(TAG_CHARGE_SIEGE));
				Settlement settlement = siege == null ? null : data.get(siege.getSettlementId());
				if (settlement == null || !settlement.isActive()) {
					restoreOwnNpc(npc);
					continue;
				}
				// the home follows the soldier: Ancient Warfare shortens the attack range of an npc far from home
				npc.setHomePosAndDistance(npc.getPosition(), npc.getHomeRange());
				NpcAIFactionAssault ai = Sieges.getOrAddAssaultAI(npc);
				ai.setTarget(Sieges.getTarget(world, settlement));
				ai.setBreachAllowed(Sieges.isBreachAllowed(siege));
				ai.setBuildAllowed(Sieges.isBuildAllowed(siege));
			}
		}
	}

	/**
	 * An order of the command baton takes the npc out of the assault it joined.
	 *
	 * @return true if it was in one
	 */
	public static boolean leaveAssault(NpcBase npc) {
		if (!npc.getEntityData().hasKey(TAG_CHARGE)) {
			return false;
		}
		restoreOwnNpc(npc);
		return true;
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
		data.removeTag(TAG_CHARGE_SIEGE);
		data.removeTag(TAG_CHARGE_HOME);
		data.removeTag(TAG_CHARGE_HOME_RANGE);
		npc.setAttackTarget(null);
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
}
