package net.shadowmage.ancientwarfare.npc.ai.faction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.Settlements;

import javax.annotation.Nullable;

/**
 * An npc that runs away for a while (the survivors of a fallen city): it flees from the place it left and from anyone nearby, and does
 * not attack anyone until the time is over (see {@link #isFleeing}, checked in NpcFaction.isHostileTowards). The winners can chase it
 * while it is inside the fallen city ({@link #isInsideFledCity}), not outside. Then it settles where it is.
 * The time and the place are in the persistent entity data, so the npc keeps fleeing after being loaded again.
 */
public class NpcAIFactionFlee extends NpcAI<NpcFaction> {
	public static final String TAG_FLEE_UNTIL = "awFleeUntil";
	public static final String TAG_FLEE_FROM = "awFleeFrom";
	public static final String TAG_FLEE_CITY = "awFleeCity";
	public static final int PRIORITY = 0;
	private static final double SAFE_DISTANCE = 64D;
	private static final double DANGER_RANGE = 12D;
	private static final double FLEE_SPEED = 1.2D;
	private static final int REPATH_TICKS = 20;
	private static final double CHASE_RELEASE_RANGE = 48D;

	private int repathDelay;
	private boolean releasedChasers;

	public NpcAIFactionFlee(NpcFaction npc) {
		super(npc);
		setMutexBits(MOVE);
	}

	public static boolean isFleeing(Entity entity) {
		NBTTagCompound data = entity.getEntityData();
		return data.hasKey(TAG_FLEE_UNTIL) && data.getLong(TAG_FLEE_UNTIL) > entity.world.getTotalWorldTime();
	}

	/**
	 * @return true while a fleeing npc is still inside the city it flees from: the winners can chase it there, outside it is left alone
	 */
	public static boolean isInsideFledCity(Entity entity) {
		NBTTagCompound data = entity.getEntityData();
		if (!data.hasKey(TAG_FLEE_CITY)) {
			return false;
		}
		Settlement city = Settlements.getData(entity.world).get(data.getInteger(TAG_FLEE_CITY));
		return city != null && city.contains(entity.world.provider.getDimension(), entity.getPosition());
	}

	@Override
	public boolean shouldExecute() {
		NBTTagCompound data = npc.getEntityData();
		if (!data.hasKey(TAG_FLEE_UNTIL)) {
			return false;
		}
		if (!isFleeing(npc)) {
			// the time is over: settle here instead of going back to the place it fled from
			data.removeTag(TAG_FLEE_UNTIL);
			data.removeTag(TAG_FLEE_FROM);
			data.removeTag(TAG_FLEE_CITY);
			npc.setHomeAreaAtCurrentPosition();
			return false;
		}
		return super.shouldExecute();
	}

	@Override
	public void startExecuting() {
		npc.addAITask(TASK_FLEE);
		npc.setAttackTarget(null);
		npc.setRevengeTarget(null);
		repathDelay = 0;
	}

	@Override
	public void updateTask() {
		if (--repathDelay > 0 && !npc.getNavigator().noPath()) {
			return;
		}
		repathDelay = REPATH_TICKS;
		if (!releasedChasers && npc.getEntityData().hasKey(TAG_FLEE_CITY) && !isInsideFledCity(npc)) {
			releaseChasers();
		}
		Vec3d danger = getDanger();
		if (danger == null) {
			npc.getNavigator().clearPath();
			return;
		}
		// the home (the fallen city) would keep the random positions close to it
		npc.detachHome();
		Vec3d away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(npc, 16, 7, danger);
		if (away != null) {
			npc.getNavigator().tryMoveToXYZ(away.x, away.y, away.z, FLEE_SPEED);
		}
	}

	/**
	 * Out of the fallen city: the npcs chasing it give up (a target already taken is not checked again against isHostileTowards).
	 */
	private void releaseChasers() {
		releasedChasers = true;
		for (NpcFaction other : npc.world.getEntitiesWithinAABB(NpcFaction.class, npc.getEntityBoundingBox().grow(CHASE_RELEASE_RANGE))) {
			if (other.getAttackTarget() == npc || other.getRevengeTarget() == npc) {
				for (EntityAITasks.EntityAITaskEntry entry : other.targetTasks.taskEntries) {
					if (entry.using) {
						entry.action.resetTask();
					}
				}
				other.setAttackTarget(null);
				other.setRevengeTarget(null);
			}
		}
	}

	/**
	 * @return the position to run away from: someone close, otherwise the place it fled from while it is not far enough, otherwise null
	 */
	@Nullable
	private Vec3d getDanger() {
		EntityLivingBase nearest = null;
		double nearestDistSq = DANGER_RANGE * DANGER_RANGE;
		for (EntityLivingBase entity : npc.world.getEntitiesWithinAABB(EntityLivingBase.class, npc.getEntityBoundingBox().grow(DANGER_RANGE))) {
			boolean threat = entity instanceof EntityPlayer && !((EntityPlayer) entity).isSpectator()
					|| entity instanceof NpcFaction && !((NpcFaction) entity).getFaction().equals(npc.getFaction());
			double distSq = entity.getDistanceSq(npc);
			if (entity != npc && entity.isEntityAlive() && threat && distSq < nearestDistSq) {
				nearest = entity;
				nearestDistSq = distSq;
			}
		}
		if (nearest != null) {
			return nearest.getPositionVector();
		}
		NBTTagCompound data = npc.getEntityData();
		if (data.hasKey(TAG_FLEE_FROM)) {
			BlockPos from = BlockPos.fromLong(data.getLong(TAG_FLEE_FROM));
			if (npc.getDistanceSq(from) < SAFE_DISTANCE * SAFE_DISTANCE) {
				return new Vec3d(from.getX() + 0.5D, from.getY(), from.getZ() + 0.5D);
			}
		}
		return null;
	}

	@Override
	public void resetTask() {
		npc.removeAITask(TASK_FLEE);
		npc.getNavigator().clearPath();
	}
}
