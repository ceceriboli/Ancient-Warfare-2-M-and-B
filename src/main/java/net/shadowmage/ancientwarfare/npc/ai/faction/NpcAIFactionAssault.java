package net.shadowmage.ancientwarfare.npc.ai.faction;

import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;

import javax.annotation.Nullable;

/**
 * Marches on a target position (the leader of a city under siege) while the npc has nothing to fight.
 * Gives way to the attack AI as soon as the npc has an attack target. The target is set from outside (see Sieges).
 * <p>
 * The target is often not reachable directly (a leader on an upper floor of a tower, or out of the path finding range):
 * then the npc moves in steps to reachable positions in the direction of the target.
 */
public class NpcAIFactionAssault extends NpcAI<NpcBase> {
	public static final int PRIORITY = 3;
	private static final double ARRIVED_DIST_SQ = 4D * 4D;
	private static final double DIRECT_PATH_RANGE = 32D;
	private static final int STEP_RANGE_XZ = 16;
	private static final int STEP_RANGE_Y = 7;
	private static final int REPATH_TICKS = 20;

	@Nullable
	private BlockPos target;
	private int repathDelay;

	public NpcAIFactionAssault(NpcBase npc) {
		super(npc);
		setMutexBits(MOVE);
	}

	public void setTarget(@Nullable BlockPos target) {
		this.target = target;
	}

	@Override
	public boolean shouldExecute() {
		return super.shouldExecute() && target != null && npc.getAttackTarget() == null && getDistanceSqToTarget() > ARRIVED_DIST_SQ;
	}

	private double getDistanceSqToTarget() {
		//noinspection ConstantConditions
		return npc.getDistanceSq(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
	}

	@Override
	public void startExecuting() {
		npc.addAITask(TASK_MOVE);
		repathDelay = 0;
	}

	@Override
	public void updateTask() {
		if (target == null || (--repathDelay > 0 && !npc.getNavigator().noPath())) {
			return;
		}
		repathDelay = REPATH_TICKS;
		double distSq = getDistanceSqToTarget();
		if (distSq <= DIRECT_PATH_RANGE * DIRECT_PATH_RANGE && moveDirectly(distSq)) {
			return;
		}
		Vec3d step = RandomPositionGenerator.findRandomTargetBlockTowards(npc, STEP_RANGE_XZ, STEP_RANGE_Y,
				new Vec3d(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D));
		if (step != null && npc.getNavigator().tryMoveToXYZ(step.x, step.y, step.z, moveSpeed)) {
			return;
		}
		moveRetryDelay = 0;
		moveToPosition(target, distSq);
	}

	/**
	 * @return true if a path brings the npc closer to the target (possibly only part of the way, e.g. to the foot of a tower)
	 */
	private boolean moveDirectly(double distSq) {
		//noinspection ConstantConditions
		Path path = npc.getNavigator().getPathToPos(target);
		if (path == null) {
			return false;
		}
		PathPoint end = path.getFinalPathPoint();
		//noinspection ConstantConditions
		if (end == null || end.distanceToSquared(new PathPoint(target.getX(), target.getY(), target.getZ())) >= distSq - 4D) {
			return false;
		}
		return npc.getNavigator().setPath(path, moveSpeed);
	}

	@Override
	public void resetTask() {
		npc.removeAITask(TASK_MOVE);
		npc.getNavigator().clearPath();
	}
}
