package net.shadowmage.ancientwarfare.npc.ai.faction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;
import net.shadowmage.ancientwarfare.npc.war.Armies;

import javax.annotation.Nullable;

/**
 * A soldier of a player's army follows its commander like a tamed wolf follows its owner (faction npcs cannot use the follow of Ancient
 * Warfare, reserved to npcs the player may command): it walks to the commander, re-pathing twice a second, and teleports next to it when
 * the commander is more than 12 blocks away or when it is stuck (a door, a room). It fights enemies near the commander. Idle while the
 * army charges a city.
 */
public class NpcAIFactionFollowCommander extends NpcAI<NpcBase> {
	public static final int PRIORITY = 1;
	private static final double FOLLOW_STOP_DIST_SQ = 3D * 3D;
	private static final double FIGHT_LEASH_DIST_SQ = 16D * 16D;
	private static final double TELEPORT_DIST_SQ = 12D * 12D;
	private static final int REPATH_TICKS = 10;
	private static final int STUCK_TICKS = 40;
	private static final double JOIN_DIST_SQ = 8D * 8D;

	@Nullable
	private EntityPlayer commander;
	private int repathDelay;
	private int stuckTicks;
	private double lastX;
	private double lastZ;

	public NpcAIFactionFollowCommander(NpcBase npc) {
		super(npc);
		setMutexBits(MOVE);
	}

	@Override
	public boolean shouldExecute() {
		if (!super.shouldExecute() || Armies.isCharging(npc)) {
			return false;
		}
		commander = Armies.getCommander(npc);
		if (commander == null || commander.isSpectator()) {
			return false;
		}
		if (npc.getEntityData().getBoolean(Armies.TAG_ARMY_WAITING)) {
			// a newly raised soldier waits at the gathering point until its commander comes to it
			if (npc.getDistanceSq(commander) > JOIN_DIST_SQ) {
				return false;
			}
			npc.getEntityData().removeTag(Armies.TAG_ARMY_WAITING);
		}
		if (npc.getAttackTarget() != null && npc.getAttackTarget().isEntityAlive()) {
			// fights near its commander, comes back when it gets too far
			return npc.getDistanceSq(commander) >= FIGHT_LEASH_DIST_SQ;
		}
		return npc.getDistanceSq(commander) > FOLLOW_STOP_DIST_SQ;
	}

	@Override
	public void startExecuting() {
		repathDelay = 0;
		stuckTicks = 0;
		lastX = npc.posX;
		lastZ = npc.posZ;
		npc.addAITask(TASK_FOLLOW);
	}

	@Override
	public void updateTask() {
		if (commander == null) {
			return;
		}
		npc.getLookHelper().setLookPositionWithEntity(commander, 10F, (float) npc.getVerticalFaceSpeed());
		double distSq = npc.getDistanceSq(commander);
		// stuck: hardly moved while far from the commander
		double movedSq = (npc.posX - lastX) * (npc.posX - lastX) + (npc.posZ - lastZ) * (npc.posZ - lastZ);
		stuckTicks = movedSq < 0.0025D && distSq > FOLLOW_STOP_DIST_SQ * 4 ? stuckTicks + 1 : 0;
		lastX = npc.posX;
		lastZ = npc.posZ;
		if ((distSq > TELEPORT_DIST_SQ || stuckTicks >= STUCK_TICKS) && teleportToCommander()) {
			stuckTicks = 0;
			return;
		}
		if (--repathDelay > 0) {
			return;
		}
		repathDelay = REPATH_TICKS;
		if (distSq > FOLLOW_STOP_DIST_SQ) {
			npc.addAITask(TASK_MOVE);
			npc.getNavigator().tryMoveToEntityLiving(commander, 1.1D);
		} else {
			npc.removeAITask(TASK_MOVE);
			npc.getNavigator().clearPath();
		}
	}

	/**
	 * Like a tamed wolf: to a free spot (solid block below, two air blocks) within 2 blocks of the commander. A mounted npc moves with its horse.
	 */
	private boolean teleportToCommander() {
		if (commander == null || !commander.onGround && !commander.isRiding()) {
			return false; // not while the commander jumps or flies
		}
		int x = MathHelper.floor(commander.posX) - 2;
		int z = MathHelper.floor(commander.posZ) - 2;
		int y = MathHelper.floor(commander.getEntityBoundingBox().minY);
		for (int dx = 0; dx <= 4; dx++) {
			for (int dz = 0; dz <= 4; dz++) {
				if (dx >= 1 && dz >= 1 && dx <= 3 && dz <= 3) {
					continue; // not right next to the commander, like the wolves
				}
				BlockPos pos = new BlockPos(x + dx, y, z + dz);
				if (npc.world.getBlockState(pos.down()).isTopSolid() && npc.world.isAirBlock(pos) && npc.world.isAirBlock(pos.up())) {
					Entity mover = npc.getRidingEntity() != null ? npc.getRidingEntity() : npc;
					mover.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, npc.rotationYaw, npc.rotationPitch);
					npc.getNavigator().clearPath();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void resetTask() {
		commander = null;
		npc.removeAITask(TASK_FOLLOW + TASK_MOVE);
		npc.getNavigator().clearPath();
	}
}
