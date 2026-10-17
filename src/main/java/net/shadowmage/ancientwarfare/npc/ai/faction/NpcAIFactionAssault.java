package net.shadowmage.ancientwarfare.npc.ai.faction;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;

import javax.annotation.Nullable;
import java.util.Arrays;
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
 * <p>
 * When breaching is allowed (see Sieges), a melee npc that does not move for a while breaks the block in front of it towards the target,
 * within the limits of the config (hardness, blacklist, mobGriefing): walls, iron doors, gates.
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
	private static final int FRUITLESS_FIGHT_TICKS = 300;
	private static final int PROGRESS_CHECK_TICKS = 40;
	private static final int NO_PROGRESS_CHECKS = 2;
	private static final int MIN_BREACH_TICKS = 20;
	private static final double BREACH_REACH_SQ = 3D * 3D;

	@Nullable
	private BlockPos target;
	private int repathDelay;
	private long stuckSince = -1;
	private long pushUntil;
	@Nullable
	private EntityLivingBase fightEnemy;
	private float fightEnemyHealth;
	private long fightSince;
	private boolean breachAllowed;
	@Nullable
	private BlockPos breachPos;
	private int breachTicks;
	private int breachTotalTicks;
	@Nullable
	private BlockPos progressCheckPos;
	private long progressCheckTick;
	private int noProgressChecks;

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
			fightEnemy = null;
			return getDistanceSqToTarget() > ARRIVED_DIST_SQ;
		}
		if (isFruitlessFight(enemy, now)) {
			// e.g. an archer shooting at a defender behind battlements: the arrows never hit
			stuckSince = -1;
			fightEnemy = null;
			pushUntil = now + PUSH_TICKS;
			return true;
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
	 * @return true if the npc has been fighting the same enemy for {@link #FRUITLESS_FIGHT_TICKS} without the enemy losing health
	 */
	private boolean isFruitlessFight(EntityLivingBase enemy, long now) {
		if (enemy != fightEnemy || enemy.getHealth() < fightEnemyHealth - 0.5F) {
			fightEnemy = enemy;
			fightEnemyHealth = enemy.getHealth();
			fightSince = now;
			return false;
		}
		return now - fightSince >= FRUITLESS_FIGHT_TICKS;
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
		if (breachPos != null) {
			continueBreach();
			return;
		}
		if (checkNoProgress() && (dismount() || breachAllowed && startBreach())) {
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

	/**
	 * @return true when the npc has been standing still (within a block) for two progress checks while not at the target
	 */
	private boolean checkNoProgress() {
		long now = npc.world.getTotalWorldTime();
		if (now - progressCheckTick < PROGRESS_CHECK_TICKS) {
			return false;
		}
		progressCheckTick = now;
		BlockPos pos = npc.getPosition();
		boolean stuck = progressCheckPos != null && progressCheckPos.distanceSq(pos) <= 2D && getDistanceSqToTarget() > ARRIVED_DIST_SQ;
		progressCheckPos = pos;
		noProgressChecks = stuck ? noProgressChecks + 1 : 0;
		if (noProgressChecks >= NO_PROGRESS_CHECKS) {
			noProgressChecks = 0;
			return true;
		}
		return false;
	}

	/**
	 * A rider that cannot get any closer goes on foot: paths are computed for the rider (0.6 blocks wide) but followed by the horse
	 * (1.4 blocks wide), which gets stuck in doors, alleys and breaches. The horse stays where it is.
	 */
	private boolean dismount() {
		if (!(npc.getRidingEntity() instanceof AbstractHorse)) {
			return false;
		}
		npc.dismountRidingEntity();
		npc.getNavigator().clearPath();
		repathDelay = 0;
		return true;
	}

	/**
	 * Melee npcs that cannot get any closer break the block in front of them, towards the target (a wall, an iron door).
	 */
	private boolean startBreach() {
		if (npc instanceof IRangedAttackMob || !ForgeEventFactory.getMobGriefingEvent(npc.world, npc)) {
			return false;
		}
		//noinspection ConstantConditions
		EnumFacing facing = EnumFacing.getFacingFromVector((float) (target.getX() + 0.5D - npc.posX), 0F, (float) (target.getZ() + 0.5D - npc.posZ));
		BlockPos front = npc.getPosition().offset(facing);
		for (BlockPos candidate : new BlockPos[] {front.up(), front, target.getY() > npc.posY + 1 ? npc.getPosition().up(2) : null}) {
			if (candidate != null && canBreach(npc.world, candidate)) {
				breachPos = candidate;
				breachTicks = 0;
				breachTotalTicks = Math.max(MIN_BREACH_TICKS,
						(int) (npc.world.getBlockState(candidate).getBlockHardness(npc.world, candidate) * AWDiplomacyStatics.breachTicksPerHardness));
				npc.getNavigator().clearPath();
				return true;
			}
		}
		return false;
	}

	public static boolean canBreach(World world, BlockPos pos) {
		IBlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		if (state.getMaterial() == Material.AIR || state.getMaterial().isLiquid()) {
			return false;
		}
		float hardness = state.getBlockHardness(world, pos);
		if (hardness < 0 || hardness > AWDiplomacyStatics.breachMaxHardness) {
			return false;
		}
		if (block.hasTileEntity(state) && !(block instanceof BlockDoor)) {
			return false; // chests, spawners and other blocks with contents
		}
		ResourceLocation name = block.getRegistryName();
		return name == null || !Arrays.asList(AWDiplomacyStatics.breachBlockBlacklist).contains(name.toString());
	}

	private void continueBreach() {
		//noinspection ConstantConditions
		if (!canBreach(npc.world, breachPos) || npc.getDistanceSqToCenter(breachPos) > BREACH_REACH_SQ) {
			stopBreach();
			return;
		}
		npc.getLookHelper().setLookPosition(breachPos.getX() + 0.5D, breachPos.getY() + 0.5D, breachPos.getZ() + 0.5D, 30F, 30F);
		breachTicks++;
		if (breachTicks % 10 == 0) {
			npc.swingArm(EnumHand.MAIN_HAND);
			IBlockState state = npc.world.getBlockState(breachPos);
			SoundType sound = state.getBlock().getSoundType(state, npc.world, breachPos, npc);
			npc.world.playSound(null, breachPos, sound.getHitSound(), SoundCategory.HOSTILE, sound.getVolume() * 0.5F, sound.getPitch() * 0.8F);
		}
		npc.world.sendBlockBreakProgress(npc.getEntityId(), breachPos, breachTicks * 10 / breachTotalTicks);
		if (breachTicks >= breachTotalTicks) {
			npc.world.destroyBlock(breachPos, false);
			stopBreach();
			repathDelay = 0;
		}
	}

	private void stopBreach() {
		if (breachPos != null) {
			npc.world.sendBlockBreakProgress(npc.getEntityId(), breachPos, -1);
			breachPos = null;
		}
	}

	public void setBreachAllowed(boolean breachAllowed) {
		this.breachAllowed = breachAllowed;
	}

	@Override
	public void resetTask() {
		stopBreach();
		npc.removeAITask(TASK_MOVE);
		npc.getNavigator().clearPath();
	}
}
