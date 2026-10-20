package net.shadowmage.ancientwarfare.npc.npc_command;

import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;
import net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned;
import net.shadowmage.ancientwarfare.npc.npc_command.NpcCommand.Command;
import net.shadowmage.ancientwarfare.npc.npc_command.NpcCommand.CommandType;
import net.shadowmage.ancientwarfare.npc.war.Armies;

import java.util.ArrayList;
import java.util.List;

/**
 * Orders for the whole group of a command baton, on top of the Ancient Warfare ones (attack, move, guard, home, upkeep): follow me, hold
 * position, retreat; and the choice of many soldiers at once. No formations: the soldiers act on their own once ordered.
 */
public final class GroupOrders {
	public static final String TAG_HOLD = "awHold";
	public static final String TAG_RETREAT_UNTIL = "awRetreatUntil";
	public static final String TAG_PATROL = "awPatrol";
	public static final String TAG_PATROL_RADIUS = "awPatrolRadius";
	private static final int PATROL_RADIUS = 12;
	private static final int HOLD_RANGE = 4;

	private GroupOrders() {}

	/**
	 * @return the player's own fighting npcs within baton_select_range of the player; of the same type as like, if given
	 */
	public static List<NpcPlayerOwned> findSoldiers(EntityPlayer player, NpcBase like) {
		double range = AWDiplomacyStatics.batonSelectRange;
		List<NpcPlayerOwned> found = new ArrayList<>();
		for (NpcPlayerOwned npc : player.world.getEntitiesWithinAABB(NpcPlayerOwned.class, player.getEntityBoundingBox().grow(range, range / 2, range))) {
			if (npc.isEntityAlive() && !npc.isPassive() && npc.hasCommandPermissions(player.getUniqueID(), player.getName())
					&& (like == null || npc.getNpcType().equals(like.getNpcType())) && npc.getDistanceSq(player) <= range * range) {
				found.add(npc);
			}
		}
		found.sort((a, b) -> Double.compare(a.getDistanceSq(player), b.getDistanceSq(player)));
		return found.size() > AWDiplomacyStatics.batonMaxUnits ? new ArrayList<>(found.subList(0, AWDiplomacyStatics.batonMaxUnits)) : found;
	}

	/**
	 * @return true if the order is one of the group orders of this class (given at once, not kept as the npc's command)
	 */
	public static boolean isGroupOrder(CommandType type) {
		return type == CommandType.FOLLOW || type == CommandType.HOLD || type == CommandType.RETREAT || type == CommandType.PATROL;
	}

	/**
	 * Every order of the baton takes a soldier out of an assault it joined (military service) and ends a hold position, except a new hold.
	 */
	public static void beforeOrder(NpcPlayerOwned npc, CommandType type) {
		Armies.leaveAssault(npc);
		NBTTagCompound data = npc.getEntityData();
		if (type != CommandType.HOLD && data.getBoolean(TAG_HOLD)) {
			data.removeTag(TAG_HOLD);
			npc.setDoNotPursue(false);
		}
		if (type != CommandType.PATROL) {
			data.removeTag(TAG_PATROL);
			data.removeTag(TAG_PATROL_RADIUS);
		}
		// an order replaces following the player (recruits and reinforcements start following him): only follow and retreat keep it
		if (type != CommandType.FOLLOW && type != CommandType.RETREAT) {
			npc.clearFollowingEntity();
		}
	}

	public static void apply(NpcPlayerOwned npc, EntityPlayer player, CommandType type, net.minecraft.util.math.BlockPos pos) {
		switch (type) {
			case PATROL:
				// walks around the area, fights what it meets, comes back (home = center), see NpcAIPlayerOwnedPatrolArea
				npc.setPlayerCommand(Command.NONE);
				npc.clearFollowingEntity();
				npc.getNavigator().clearPath();
				npc.setHomePosAndDistance(pos, PATROL_RADIUS);
				npc.getEntityData().setLong(TAG_PATROL, pos.toLong());
				npc.getEntityData().setInteger(TAG_PATROL_RADIUS, PATROL_RADIUS);
				break;
			case FOLLOW:
				npc.setPlayerCommand(Command.NONE);
				npc.setFollowingEntity(player);
				break;
			case HOLD:
				// stays where it is: it fights what comes close, archers shoot from there (doNotPursue)
				npc.setPlayerCommand(Command.NONE);
				npc.clearFollowingEntity();
				npc.getNavigator().clearPath();
				npc.setHomePosAndDistance(npc.getPosition(), HOLD_RANGE);
				npc.setDoNotPursue(true);
				npc.getEntityData().setBoolean(TAG_HOLD, true);
				break;
			case RETREAT:
				// stops fighting, comes back to the player and takes no new enemy for a while
				npc.setPlayerCommand(Command.NONE);
				dropTargets(npc);
				npc.getNavigator().clearPath();
				npc.getEntityData().setLong(TAG_RETREAT_UNTIL, npc.world.getTotalWorldTime() + AWDiplomacyStatics.batonRetreatTicks);
				npc.setFollowingEntity(player);
				break;
			default:
		}
	}

	/**
	 * @return true while the npc retreats on an order of the baton: it takes no new enemy
	 */
	public static boolean isRetreating(NpcBase npc) {
		return npc.getEntityData().getLong(TAG_RETREAT_UNTIL) > npc.world.getTotalWorldTime();
	}

	private static void dropTargets(NpcBase npc) {
		// a running target task keeps its own copy of the target (EntityAITarget), reset it as well
		for (EntityAITasks.EntityAITaskEntry entry : npc.targetTasks.taskEntries) {
			if (entry.using) {
				entry.action.resetTask();
			}
		}
		npc.setAttackTarget(null);
		npc.setRevengeTarget(null);
	}

	public static void reportOrder(EntityPlayer player, CommandType type, int count) {
		player.sendStatusMessage(new TextComponentTranslation("guistrings.npc.baton.ordered", new TextComponentTranslation("guistrings.npc.baton.order."
				+ type.name().toLowerCase(java.util.Locale.ROOT)), count), true);
	}
}
