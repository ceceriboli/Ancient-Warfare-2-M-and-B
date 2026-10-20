package net.shadowmage.ancientwarfare.npc.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;

import java.util.Optional;

public abstract class NpcAIAttack<T extends NpcBase> extends NpcAI<T> {
	private static final int REACH_CHECK_TICKS = 20;
	private static final int UNREACHABLE_CHECKS = 3;
	private static final int UNREACHABLE_TICKS = 200;
	private static final float REACH_DIST_SQ = 3F * 3F;

	private EntityLivingBase target;
	private int attackDelay = 0;
	private int reachCheckTicks;
	private int unreachableChecks;
	private BlockPos lastCheckPos;

	public NpcAIAttack(T npc) {
		super(npc);
	}

	@Override
	public boolean shouldExecute() {
		return super.shouldExecute() && hasTargetInRange();
	}

	private boolean hasTargetInRange() {
		if (target == null) {
			return getAttackTarget().isPresent() && getAttackTarget().map(EntityLivingBase::isEntityAlive).orElse(false) && isTargetInRange();
		}
		return target.isEntityAlive() && getAttackTarget().map(t -> t.equals(target)).orElse(false) && isTargetInRange();
	}

	private boolean isTargetInRange() {
		return getAttackTarget().map(t -> npc.getDistance(t) < getAdjustedTargetDistance()).orElse(false);
	}

	private Optional<EntityLivingBase> getAttackTarget() {
		return npc.getAttackTarget() != null ? Optional.of(npc.getAttackTarget()) : Optional.ofNullable(npc.getRevengeTarget());
	}

	@Override
	public final void startExecuting() {
		target = getAttackTarget().orElse(null);
		moveRetryDelay = 0;
		npc.addAITask(TASK_ATTACK);
		npc.setSwingingArms(true);
		attackDelay = 0;
	}

	@Override
	public final void resetTask() {
		target = null;
		moveRetryDelay = 0;
		npc.removeAITask(TASK_MOVE + TASK_ATTACK);
		npc.setSwingingArms(false);
	}

	protected int getCoolDown() {
		return attackDelay;
	}

	@Override
	public final void updateTask() {
		npc.getLookHelper().setLookPositionWithEntity(target, 30.f, 30.f);
		double distanceToEntitySq = npc.getDistanceSq(target.posX, target.getEntityBoundingBox().minY, target.posZ);
		if (shouldCloseOnTarget(distanceToEntitySq) && holdsPosition()) {
			// ordered to hold its position: it waits for the enemy to come close instead of going after it
			npc.getNavigator().clearPath();
		} else if (shouldCloseOnTarget(distanceToEntitySq)) {
			npc.addAITask(TASK_MOVE);
			moveToEntity(target, distanceToEntitySq);
			checkReachable();
		} else {
			unreachableChecks = 0;
			npc.getNavigator().clearPath();
			attackDelay--;
			doAttack(distanceToEntitySq);
		}
	}

	/**
	 * When the path does not lead to the target (an enemy seen on the other side of a wall: the path ends in the corner closest to it)
	 * and the npc does not move for a while, the target is given up and marked unreachable, so that another one is chosen.
	 */
	private void checkReachable() {
		if (++reachCheckTicks < REACH_CHECK_TICKS) {
			return;
		}
		reachCheckTicks = 0;
		Path path = npc.getNavigator().getPath();
		PathPoint end = path == null ? null : path.getFinalPathPoint();
		boolean reaches = end != null && end.distanceToSquared(new PathPoint(MathHelper.floor(target.posX), MathHelper.floor(target.posY),
				MathHelper.floor(target.posZ))) <= REACH_DIST_SQ;
		BlockPos pos = npc.getPosition();
		boolean moved = lastCheckPos == null || lastCheckPos.distanceSq(pos) > 1D;
		lastCheckPos = pos;
		unreachableChecks = reaches || moved ? 0 : unreachableChecks + 1;
		if (unreachableChecks >= UNREACHABLE_CHECKS) {
			unreachableChecks = 0;
			npc.markUnreachable(target, UNREACHABLE_TICKS);
			// a running target task keeps its own copy of the target (EntityAITarget), reset it as well
			for (EntityAITasks.EntityAITaskEntry entry : npc.targetTasks.taskEntries) {
				if (entry.using) {
					entry.action.resetTask();
				}
			}
			npc.setAttackTarget(null);
			npc.setRevengeTarget(null);
			npc.getNavigator().clearPath();
		}
	}

	private double getAdjustedTargetDistance() {
		if (npc.getFollowingEntity() != null) {
			return Math.max(getTargetDistance() / getDistanceToFollowingEntity(), 3D);
		}
		return Math.max(getTargetDistance() / (getHomeDistance() / 20), 3D);
	}

	private double getDistanceToFollowingEntity() {
		return Math.sqrt(npc.getDistanceSq(npc.getFollowingEntity().getPosition()));
	}

	private double getHomeDistance() {
		return Math.sqrt(npc.getDistanceSq(npc.getHomePosition()));
	}

	private double getTargetDistance() {
		IAttributeInstance iattributeinstance = npc.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE);
		//noinspection ConstantConditions
		return iattributeinstance == null ? 35.0D : iattributeinstance.getAttributeValue();
	}

	/**
	 * @return true if the npc must not move towards a target out of its reach (hold position, see GroupOrders)
	 */
	protected boolean holdsPosition() {
		return false;
	}

	protected abstract boolean shouldCloseOnTarget(double distanceToEntity);

	protected abstract void doAttack(double distanceToEntity);

	public final EntityLivingBase getTarget() {
		return target;
	}

	public final void setAttackDelay(int value) {
		attackDelay = value;
	}

	public final int getAttackDelay() {
		return attackDelay;
	}
}
