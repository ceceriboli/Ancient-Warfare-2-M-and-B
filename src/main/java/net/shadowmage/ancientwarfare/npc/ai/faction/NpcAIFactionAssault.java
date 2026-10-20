package net.shadowmage.ancientwarfare.npc.ai.faction;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.init.Blocks;
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
import net.shadowmage.ancientwarfare.npc.war.Armies;
import net.shadowmage.ancientwarfare.npc.war.AssaultBuilds;

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
 * <p>
 * When building is allowed (see Sieges), an npc that does not move for a while places a ladder up the wall in front of it if the target is
 * higher (the others that get stuck there climb the same ladder), or planks to bridge the gap in front of it (see AssaultBuilds), as the
 * raiders of MineColonies do without a path. Pushing against the wall on a ladder makes the npc climb (vanilla).
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
	private static final int FRUITLESS_FIGHT_TICKS = 200;
	private static final int GIVE_UP_TICKS = 200;
	private static final int PROGRESS_CHECK_TICKS = 40;
	private static final int NO_PROGRESS_CHECKS = 2;
	private static final int MIN_BREACH_TICKS = 20;
	private static final int FOLIAGE_CLEAR_TICKS = 10;
	private static final double BREACH_REACH_SQ = 3D * 3D;
	private static final int LADDER_MIN_RISE = 3;
	private static final int MAX_LADDER_HEIGHT = 24;
	private static final int LADDER_PLACE_TICKS = 6;
	private static final double LADDER_JOIN_RANGE = 10D;
	private static final int BUILD_TIMEOUT_TICKS = 400;
	private static final int TOP_PUSH_TICKS = 40;
	private static final int BRIDGE_MAX_LENGTH = 10;
	private static final int BRIDGE_PLACE_TICKS = 8;
	private static final double AT_CELL_DIST_SQ = 1.5D * 1.5D;
	private static final int SWIM_OUT_RANGE = 6;
	private static final int SWIM_OUT_TICKS = 60;

	private enum BuildMode {
		NONE, LADDER, BRIDGE
	}

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
	private boolean buildAllowed;
	private BuildMode buildMode = BuildMode.NONE;
	private int siegeId;
	private long buildStart;
	private long lastPlace;
	@Nullable
	private AssaultBuilds.Column column;
	private int climbStage;
	private long topSince;
	@Nullable
	private EnumFacing bridgeDir;
	@Nullable
	private BlockPos bridgeStart;
	private int bridgeNext;
	private int bridgeEnd;
	@Nullable
	private BlockPos swimOutTo;
	private long swimOutUntil;

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
		if (enemy != null && enemy.isEntityAlive() && npc.isInWater() && npc.getDistanceSq(enemy) > MELEE_REACH_SQ && startSwimOut(enemy.getPosition())) {
			// standing in water (archers shooting from a pond at an enemy on the bank) it first gets out, then fights
			return true;
		}
		if (enemy == null || !enemy.isEntityAlive()) {
			stuckSince = -1;
			fightEnemy = null;
			return getDistanceSqToTarget() > ARRIVED_DIST_SQ;
		}
		if (isFruitlessFight(enemy, now)) {
			// e.g. an archer shooting at a defender behind battlements: the arrows never hit. The enemy is given up for a while,
			// otherwise it would be taken again as soon as the push ends
			stuckSince = -1;
			fightEnemy = null;
			pushUntil = now + PUSH_TICKS;
			giveUp(enemy);
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
			giveUp(enemy);
			return true;
		}
		return false;
	}

	private void giveUp(EntityLivingBase enemy) {
		npc.markUnreachable(enemy, GIVE_UP_TICKS);
		// a running target task keeps its own copy of the target (EntityAITarget), reset it as well
		for (EntityAITasks.EntityAITaskEntry entry : npc.targetTasks.taskEntries) {
			if (entry.using) {
				entry.action.resetTask();
			}
		}
		npc.setAttackTarget(null);
		npc.setRevengeTarget(null);
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
		if (swimOutTo != null) {
			continueSwimOut();
			return;
		}
		if (breachPos != null) {
			continueBreach();
			return;
		}
		if (buildMode != BuildMode.NONE) {
			continueBuild();
			return;
		}
		// a rider goes on foot, then ladders and bridges (when the way is not a wall at the height of the target), then the breach
		if (checkNoProgress() && (startSwimOut(target) || dismount() || startClearFoliage() || startBuild() || breachAllowed && startBreach())) {
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
	 * An attacker in water heads for the nearest place on land where it can stand (within SWIM_OUT_RANGE blocks, up to two blocks above its
	 * feet, preferring the side of the given position), swimming up and pushing against the bank as a player does: in water, an entity pushing against a
	 * block with free space above it is lifted out (vanilla). The walk on resumes from there.
	 *
	 * @return true if it started
	 */
	private boolean startSwimOut(@Nullable BlockPos towards) {
		if (!npc.isInWater() || npc.isRiding()) {
			return false;
		}
		BlockPos origin = npc.getPosition();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.getAllInBoxMutable(origin.add(-SWIM_OUT_RANGE, -1, -SWIM_OUT_RANGE), origin.add(SWIM_OUT_RANGE, 2, SWIM_OUT_RANGE))) {
			if (!npc.world.isAirBlock(pos) || !npc.world.isAirBlock(pos.up()) || !npc.getNavigator().canEntityStandOnPos(pos)
					|| npc.world.getBlockState(pos.down()).getMaterial().isLiquid()) {
				continue;
			}
			double score = Math.sqrt(pos.distanceSq(origin)) + (towards == null ? 0D : 0.25D * Math.sqrt(pos.distanceSq(towards)));
			if (score < bestScore) {
				bestScore = score;
				best = pos.toImmutable();
			}
		}
		if (best == null) {
			return false;
		}
		swimOutTo = best;
		swimOutUntil = npc.world.getTotalWorldTime() + SWIM_OUT_TICKS;
		pushUntil = Math.max(pushUntil, swimOutUntil);
		npc.getNavigator().clearPath();
		return true;
	}

	private void continueSwimOut() {
		//noinspection ConstantConditions
		if (npc.world.getTotalWorldTime() > swimOutUntil || !npc.isInWater() && npc.onGround) {
			swimOutTo = null;
			repathDelay = 0;
			return;
		}
		double x = swimOutTo.getX() + 0.5D;
		double z = swimOutTo.getZ() + 0.5D;
		npc.getJumpHelper().setJumping();
		npc.getMoveHelper().setMoveTo(x, swimOutTo.getY(), z, moveSpeed);
		npc.getLookHelper().setLookPosition(x, swimOutTo.getY() + 1D, z, 30F, 30F);
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

	/**
	 * An attacker caught in tree leaves (in raids too, archers too) clears the leaves around it and in front of it towards the target, up to
	 * its own height (giants are 4 blocks tall). Quick, and it does not open walls: only leaves.
	 */
	private boolean startClearFoliage() {
		if (!AWDiplomacyStatics.attackerClearFoliage || !ForgeEventFactory.getMobGriefingEvent(npc.world, npc)) {
			return false;
		}
		//noinspection ConstantConditions
		double dx = target.getX() + 0.5D - npc.posX;
		double dz = target.getZ() + 0.5D - npc.posZ;
		double length = Math.max(1.0E-3D, Math.sqrt(dx * dx + dz * dz));
		// the cells of the npc and one block around, a bit further towards the target
		net.minecraft.util.math.AxisAlignedBB box = npc.getEntityBoundingBox().grow(1D, 0D, 1D).offset(dx / length * 0.5D, 0D, dz / length * 0.5D);
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		double aheadX = npc.posX + dx / length;
		double aheadZ = npc.posZ + dz / length;
		for (BlockPos pos : BlockPos.getAllInBoxMutable(new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY - 0.01D, box.maxZ))) {
			IBlockState state = npc.world.getBlockState(pos);
			if (state.getMaterial() != Material.LEAVES || !canBreach(npc.world, pos)) {
				continue;
			}
			double score = (pos.getX() + 0.5D - aheadX) * (pos.getX() + 0.5D - aheadX) + (pos.getZ() + 0.5D - aheadZ) * (pos.getZ() + 0.5D - aheadZ);
			if (score < bestScore) {
				bestScore = score;
				best = pos.toImmutable();
			}
		}
		if (best == null) {
			return false;
		}
		breachPos = best;
		breachTicks = 0;
		breachTotalTicks = FOLIAGE_CLEAR_TICKS;
		npc.getNavigator().clearPath();
		return true;
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

	public void setBuildAllowed(boolean buildAllowed) {
		this.buildAllowed = buildAllowed;
	}

	/**
	 * An npc that cannot get closer climbs the wall in front of it on a ladder when the target is higher (placing the ladders if nobody
	 * has), or bridges the gap in front of it.
	 *
	 * @return true if one of them started
	 */
	private boolean startBuild() {
		if (!buildAllowed || target == null || !ForgeEventFactory.getMobGriefingEvent(npc.world, npc)) {
			return false;
		}
		siegeId = Armies.getSiegeId(npc);
		if (siegeId == 0) {
			return false;
		}
		EnumFacing dir = EnumFacing.getFacingFromVector((float) (target.getX() + 0.5D - npc.posX), 0F, (float) (target.getZ() + 0.5D - npc.posZ));
		return target.getY() - npc.posY >= LADDER_MIN_RISE && startLadder(dir) || startBridge(dir);
	}

	private boolean startLadder(EnumFacing dir) {
		BlockPos pos = npc.getPosition();
		AssaultBuilds.Column existing = AssaultBuilds.findLadder(siegeId, pos, LADDER_JOIN_RANGE);
		if (existing != null) {
			beginClimb(existing);
			return true;
		}
		// the wall in front, or at its sides when the target is towards a corner
		for (EnumFacing facing : new EnumFacing[] {dir, dir.rotateY(), dir.rotateYCCW()}) {
			AssaultBuilds.Column planned = planLadder(pos, facing);
			if (planned != null) {
				beginClimb(AssaultBuilds.addLadder(siegeId, planned));
				return true;
			}
		}
		return false;
	}

	/**
	 * @return a ladder up the wall at the side of base (towards facing) up to the top of the wall, if there is room for the ladders and the
	 * top of the wall can be stepped onto, otherwise null
	 */
	@Nullable
	private AssaultBuilds.Column planLadder(BlockPos base, EnumFacing facing) {
		World world = npc.world;
		for (int i = 0; i < MAX_LADDER_HEIGHT; i++) {
			BlockPos cell = base.up(i);
			if (i > 0 && !world.isAirBlock(cell)) {
				return null; // an overhang in the way
			}
			BlockPos wall = cell.offset(facing);
			if (world.getBlockState(wall).getBlockFaceShape(world, wall, facing.getOpposite()) != BlockFaceShape.SOLID) {
				// the wall ends here: the cell beside this one is the top, with room for the npc to step onto
				return i >= 2 && world.isAirBlock(wall) && world.isAirBlock(wall.up()) ? new AssaultBuilds.Column(base, facing, i) : null;
			}
		}
		return null;
	}

	private void beginClimb(AssaultBuilds.Column column) {
		this.column = column;
		buildMode = BuildMode.LADDER;
		climbStage = 0;
		buildStart = npc.world.getTotalWorldTime();
		lastPlace = 0;
		npc.getNavigator().clearPath();
	}

	/**
	 * @return true if there is an edge in front (no floor) with ground a few blocks ahead on the same level, and the npc started bridging it
	 */
	private boolean startBridge(EnumFacing dir) {
		World world = npc.world;
		BlockPos start = npc.getPosition();
		if (!isFloorMissing(world, start.offset(dir))) {
			return false;
		}
		for (int i = 1; i <= BRIDGE_MAX_LENGTH + 1; i++) {
			BlockPos cell = start.offset(dir, i);
			if (!world.isAirBlock(cell) || !world.isAirBlock(cell.up())) {
				return false; // something in the way: not a gap
			}
			if (!isFloorMissing(world, cell)) {
				bridgeDir = dir;
				bridgeStart = start;
				bridgeNext = 1;
				bridgeEnd = i;
				buildMode = BuildMode.BRIDGE;
				buildStart = world.getTotalWorldTime();
				lastPlace = 0;
				npc.getNavigator().clearPath();
				return true;
			}
		}
		return false;
	}

	private static boolean isFloorMissing(World world, BlockPos cell) {
		BlockPos floor = cell.down();
		return world.isAirBlock(floor) || world.getBlockState(floor).getMaterial().isLiquid();
	}

	private void continueBuild() {
		long now = npc.world.getTotalWorldTime();
		if (!buildAllowed || now - buildStart > BUILD_TIMEOUT_TICKS) {
			stopBuild();
		} else if (buildMode == BuildMode.LADDER) {
			continueClimb(now);
		} else {
			continueBridge(now);
		}
	}

	private void continueClimb(long now) {
		if (column == null) {
			stopBuild();
			return;
		}
		BlockPos base = column.getBase();
		BlockPos wall = base.offset(column.getDir());
		BlockPos pos = npc.getPosition();
		int level = pos.getY() - base.getY();
		if (climbStage == 0) {
			// walk to the foot of the ladder
			if (pos.getX() != base.getX() || pos.getZ() != base.getZ() || Math.abs(level) > 1) {
				if (npc.getNavigator().noPath() && !tryMoveTo(base)) {
					stopBuild();
				}
				return;
			}
			climbStage = 1;
			npc.getNavigator().clearPath();
		}
		if (climbStage == 1) {
			if (Math.abs(npc.posX - (base.getX() + 0.5D)) > 0.9D || Math.abs(npc.posZ - (base.getZ() + 0.5D)) > 0.9D) {
				stopBuild(); // fell or was pushed off the ladder
				return;
			}
			// whoever is at the top of the ladders places the next one
			if (!column.isComplete() && now - lastPlace >= LADDER_PLACE_TICKS && level >= column.getPlaced() - 1) {
				if (!column.placeNext(npc.world)) {
					stopBuild();
					return;
				}
				lastPlace = now;
				npc.swingArm(EnumHand.MAIN_HAND);
				SoundType sound = SoundType.LADDER;
				npc.world.playSound(null, base.up(column.getPlaced() - 1), sound.getPlaceSound(), SoundCategory.HOSTILE, sound.getVolume() * 0.5F, sound.getPitch());
			}
			// pushing against the wall on a ladder makes the npc climb (EntityLivingBase.travel)
			npc.getMoveHelper().setMoveTo(wall.getX() + 0.5D, npc.posY, wall.getZ() + 0.5D, moveSpeed);
			if (column.isComplete() && level >= column.getHeight() - 1) {
				climbStage = 2;
				topSince = now;
			}
			return;
		}
		// at the top: onto the roof beside the wall
		BlockPos roof = column.getRoof();
		npc.getMoveHelper().setMoveTo(roof.getX() + 0.5D, roof.getY(), roof.getZ() + 0.5D, moveSpeed);
		double dx = npc.posX - (roof.getX() + 0.5D);
		double dz = npc.posZ - (roof.getZ() + 0.5D);
		if (npc.posY >= roof.getY() - 0.2D && dx * dx + dz * dz <= 0.8D * 0.8D || now - topSince > TOP_PUSH_TICKS) {
			stopBuild();
			repathDelay = 0;
		}
	}

	private void continueBridge(long now) {
		if (bridgeDir == null || bridgeStart == null) {
			stopBuild();
			return;
		}
		World world = npc.world;
		// the next floor block is placed once the npc stands at the end of what is built
		BlockPos last = bridgeStart.offset(bridgeDir, bridgeNext - 1);
		if (bridgeNext < bridgeEnd && now - lastPlace >= BRIDGE_PLACE_TICKS && horizontalDistSq(last) <= AT_CELL_DIST_SQ) {
			BlockPos cell = bridgeStart.offset(bridgeDir, bridgeNext);
			BlockPos floor = cell.down();
			IBlockState original = world.getBlockState(floor);
			if (world.isAirBlock(floor) || original.getMaterial().isLiquid()) {
				world.setBlockState(floor, Blocks.PLANKS.getDefaultState());
				AssaultBuilds.addBridge(siegeId, floor, original);
				npc.swingArm(EnumHand.MAIN_HAND);
				SoundType sound = SoundType.WOOD;
				world.playSound(null, floor, sound.getPlaceSound(), SoundCategory.HOSTILE, sound.getVolume() * 0.5F, sound.getPitch());
			}
			bridgeNext++;
			lastPlace = now;
		}
		// onto the last floor block, and across once the bridge reaches the ground
		BlockPos goal = bridgeStart.offset(bridgeDir, bridgeNext >= bridgeEnd ? bridgeEnd : bridgeNext - 1);
		npc.getMoveHelper().setMoveTo(goal.getX() + 0.5D, goal.getY(), goal.getZ() + 0.5D, moveSpeed);
		if (bridgeNext >= bridgeEnd && horizontalDistSq(goal) <= AT_CELL_DIST_SQ) {
			stopBuild();
			repathDelay = 0;
		}
	}

	private double horizontalDistSq(BlockPos pos) {
		double dx = npc.posX - (pos.getX() + 0.5D);
		double dz = npc.posZ - (pos.getZ() + 0.5D);
		return dx * dx + dz * dz;
	}

	private void stopBuild() {
		buildMode = BuildMode.NONE;
		column = null;
		bridgeDir = null;
		bridgeStart = null;
		npc.getNavigator().clearPath();
	}

	@Override
	public void resetTask() {
		stopBuild();
		stopBreach();
		npc.removeAITask(TASK_MOVE);
		npc.getNavigator().clearPath();
	}
}
