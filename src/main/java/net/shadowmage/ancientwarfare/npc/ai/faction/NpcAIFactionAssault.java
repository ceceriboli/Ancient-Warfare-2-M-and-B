package net.shadowmage.ancientwarfare.npc.ai.faction;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.BlockPos;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Marches on a target position (the leader of a city under siege). The target is set from outside (see Sieges).
 * <p>
 * Gives way to the attack AI while the npc is actually fighting. An npc stuck on an enemy it cannot reach or see (a defender on a wall,
 * behind a door) for {@link #STUCK_TICKS} pushes on towards the target for {@link #PUSH_TICKS} before trying the fight again.
 * <p>
 * The target is often not reachable directly (a leader on an upper floor of a tower, or beyond the path finding range): the npc then moves
 * in steps to the reachable position closest to the target, and walks around the obstacle when no step brings it closer.
 * Runs above the move home AIs of Ancient Warfare (archers stay at home with priority 2).
 */
public class NpcAIFactionAssault extends NpcAI<NpcBase> {
	public static final int PRIORITY = 1;
	private static final double ARRIVED_DIST_SQ = 4D * 4D;
	private static final double MELEE_REACH_SQ = 3.5D * 3.5D;
	private static final double DIRECT_PATH_RANGE = 32D;
	private static final int STEP_RANGE_XZ = 16;
	private static final int STEP_RANGE_Y = 6;
	private static final int STEP_CANDIDATES = 24;
	private static final int STEP_PATH_CHECKS = 4;
	private static final int REPATH_TICKS = 60;
	private static final int MIN_REPATH_TICKS = 20;
	private static final int STUCK_TICKS = 60;
	private static final int PUSH_TICKS = 100;

	@Nullable
	private BlockPos target;
	private int repathDelay;
	private long stuckSince = -1;
	private long pushUntil;

	public NpcAIFactionAssault(NpcBase npc) {
		super(npc);
		setMutexBits(MOVE);
	}

	public void setTarget(@Nullable BlockPos target) {
		this.target = target;
	}

	@Override
	public boolean shouldExecute() {
		if (!super.shouldExecute() || target == null) {
			return false;
		}
		long now = npc.world.getTotalWorldTime();
		if (now < pushUntil) {
			return true;
		}
		EntityLivingBase enemy = npc.getAttackTarget();
		if (enemy == null || !enemy.isEntityAlive()) {
			stuckSince = -1;
			return getDistanceSqToTarget() > ARRIVED_DIST_SQ;
		}
		if (isFighting(enemy)) {
			stuckSince = -1;
			return false;
		}
		if (stuckSince < 0) {
			stuckSince = now;
		} else if (now - stuckSince >= STUCK_TICKS) {
			stuckSince = -1;
			pushUntil = now + PUSH_TICKS;
			return true;
		}
		return false;
	}

	/**
	 * @return false if the npc has an enemy it can neither reach nor see
	 */
	private boolean isFighting(EntityLivingBase enemy) {
		if (npc.getDistanceSq(enemy) <= MELEE_REACH_SQ) {
			return true;
		}
		if (!npc.getEntitySenses().canSee(enemy)) {
			return false;
		}
		return npc instanceof IRangedAttackMob || !npc.getNavigator().noPath();
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
		if (target == null) {
			return;
		}
		if (repathDelay > 0) {
			repathDelay--;
			// a new path every REPATH_TICKS while walking, sooner when the npc stopped (but not more than once a second)
			if (!npc.getNavigator().noPath() || repathDelay > REPATH_TICKS - MIN_REPATH_TICKS) {
				return;
			}
		}
		repathDelay = REPATH_TICKS;
		double distSq = getDistanceSqToTarget();
		if (distSq <= DIRECT_PATH_RANGE * DIRECT_PATH_RANGE && moveDirectly(distSq)) {
			return;
		}
		if (moveStep(distSq)) {
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

	/**
	 * Moves to a reachable position nearby: the one closest to the target if it brings the npc closer, otherwise one at about the same
	 * distance from the target, to walk around what is in the way.
	 */
	private boolean moveStep(double distSq) {
		Random rand = npc.getRNG();
		BlockPos origin = npc.getPosition();
		BlockPos[] closer = new BlockPos[STEP_PATH_CHECKS];
		double[] closerDist = new double[STEP_PATH_CHECKS];
		BlockPos around = null;
		for (int i = 0; i < STEP_CANDIDATES; i++) {
			BlockPos candidate = findStandable(origin.add(rand.nextInt(STEP_RANGE_XZ * 2 + 1) - STEP_RANGE_XZ, rand.nextInt(STEP_RANGE_Y * 2 + 1) - STEP_RANGE_Y,
					rand.nextInt(STEP_RANGE_XZ * 2 + 1) - STEP_RANGE_XZ));
			if (candidate == null) {
				continue;
			}
			//noinspection ConstantConditions
			double candidateDistSq = candidate.distanceSq(target);
			if (candidateDistSq < distSq - 9D) {
				insertSorted(closer, closerDist, candidate, candidateDistSq);
			} else if (around == null && Math.abs(Math.sqrt(candidateDistSq) - Math.sqrt(distSq)) < 6D) {
				around = candidate;
			}
		}
		for (BlockPos candidate : closer) {
			if (candidate != null && tryMoveTo(candidate)) {
				return true;
			}
		}
		return around != null && tryMoveTo(around);
	}

	private static void insertSorted(BlockPos[] positions, double[] distances, BlockPos pos, double distSq) {
		for (int i = 0; i < positions.length; i++) {
			if (positions[i] == null || distSq < distances[i]) {
				for (int j = positions.length - 1; j > i; j--) {
					positions[j] = positions[j - 1];
					distances[j] = distances[j - 1];
				}
				positions[i] = pos;
				distances[i] = distSq;
				return;
			}
		}
	}

	/**
	 * @return the first position from pos downwards (a few blocks) where the npc can stand, or null
	 */
	@Nullable
	private BlockPos findStandable(BlockPos pos) {
		for (int dy = 0; dy < STEP_RANGE_Y; dy++) {
			BlockPos candidate = pos.down(dy);
			if (npc.world.isAirBlock(candidate) && npc.world.isAirBlock(candidate.up()) && npc.getNavigator().canEntityStandOnPos(candidate)
					&& !npc.world.getBlockState(candidate.down()).getMaterial().isLiquid()) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * @return true if there is a path that ends close to pos and the npc started moving along it
	 */
	private boolean tryMoveTo(BlockPos pos) {
		Path path = npc.getNavigator().getPathToPos(pos);
		if (path == null) {
			return false;
		}
		PathPoint end = path.getFinalPathPoint();
		if (end == null || end.distanceToSquared(new PathPoint(pos.getX(), pos.getY(), pos.getZ())) > 4F) {
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
