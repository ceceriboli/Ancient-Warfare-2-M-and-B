package net.shadowmage.ancientwarfare.npc.ai;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

public class NpcNavigator extends PathNavigateGround {
	private final EntityLiving entity;
	private final WalkNodeProcessor nodeProcessor = new NpcWalkNodeProcessor();

	public NpcNavigator(EntityLiving living) {
		super(living, living.world);
		entity = living;
	}

	@Override
	public void setCanSwim(boolean value) {
		super.setCanSwim(value);
		nodeProcessor.setCanSwim(value);
	}

	@Override
	public void setEnterDoors(boolean value) {
		super.setEnterDoors(value);
		nodeProcessor.setCanEnterDoors(value);
	}

	public void onWorldChange() {
		world = entity.world;
		lastTargetPos = null;
		lastPath = null;
	}

	@Nullable
	@Override
	public Path getPathToPos(BlockPos pos) {
		if (!canNavigate()) {
			return null;
		}
		if (reusable(-1, pos)) {
			return lastPath;
		}
		return remember(-1, pos, pathToXYZ(pos));
	}

	@Nullable
	@Override
	public Path getPathToEntityLiving(Entity target) {
		if (!canNavigate()) {
			return null;
		}
		BlockPos pos = new BlockPos(target);
		if (reusable(target.getEntityId(), pos)) {
			return lastPath;
		}
		return remember(target.getEntityId(), pos, pathToEntity(target));
	}

	/*
	 * Path requests are expensive (one A* search each) and many AIs ask again every tick for the same target. The answer to the same request
	 * (same target, moved less than a block) is reused for path_cache_ticks; when more than path_budget_per_tick paths were computed in this
	 * tick, a recent answer to a similar request (target within 4 blocks, at most 5 times path_cache_ticks old) is reused too. A request
	 * without any recent answer is always computed.
	 */
	private static final Map<World, long[]> BUDGETS = new WeakHashMap<>();
	private int lastTargetId = Integer.MIN_VALUE;
	private BlockPos lastTargetPos;
	private long lastPathTick = Long.MIN_VALUE;
	private Path lastPath;

	private boolean reusable(int targetId, BlockPos pos) {
		int cacheTicks = AWDiplomacyStatics.pathCacheTicks;
		if (world.isRemote || cacheTicks <= 0 || lastTargetPos == null || targetId != lastTargetId || (lastPath != null && lastPath.isFinished())) {
			return false;
		}
		long age = world.getTotalWorldTime() - lastPathTick;
		double distanceSq = lastTargetPos.distanceSq(pos);
		if (age >= 0 && age < cacheTicks && distanceSq <= 1) {
			return true;
		}
		return age >= 0 && age < cacheTicks * 5L && distanceSq <= 16 && overBudget();
	}

	@Nullable
	private Path remember(int targetId, BlockPos pos, @Nullable Path path) {
		if (!world.isRemote) {
			lastTargetId = targetId;
			lastTargetPos = pos;
			lastPathTick = world.getTotalWorldTime();
			lastPath = path;
			countPath();
		}
		return path;
	}

	private boolean overBudget() {
		int budget = AWDiplomacyStatics.pathBudgetPerTick;
		if (budget <= 0) {
			return false;
		}
		long[] used = BUDGETS.get(world);
		return used != null && used[0] == world.getTotalWorldTime() && used[1] >= budget;
	}

	private void countPath() {
		long[] used = BUDGETS.computeIfAbsent(world, w -> new long[2]);
		long now = world.getTotalWorldTime();
		if (used[0] != now) {
			used[0] = now;
			used[1] = 0;
		}
		used[1]++;
	}

	@Override
	public boolean setPath(@Nullable Path path, double speed) {
		if (hasMount()) {
			//noinspection ConstantConditions
			((EntityLiving) entity.getRidingEntity()).getNavigator().setPath(path, speed);
		}
		return super.setPath(path, speed);
	}

	@Override
	public void clearPath() {
		if (hasMount()) {
			//noinspection ConstantConditions
			((EntityLiving) entity.getRidingEntity()).getNavigator().clearPath();
		}
		super.clearPath();
	}

	@Override
	public void onUpdateNavigation() {
		super.onUpdateNavigation();
		if (!noPath() && hasMount()) {
			//noinspection ConstantConditions
			((EntityLiving) entity.getRidingEntity()).getNavigator().onUpdateNavigation();
		}
	}

	private boolean hasMount() {
		return entity.getRidingEntity() instanceof EntityLiving;
	}

	private EntityLiving mountOrEntity() {
		//noinspection ConstantConditions
		return hasMount() ? (EntityLiving) entity.getRidingEntity() : entity;
	}

	@Nullable
	private Path pathToEntity(Entity target) {
		ChunkCache chunkcache = cachePath(1, 16);
		Path pathentity = (new PathFinder(nodeProcessor).findPath(chunkcache, mountOrEntity(), target, getPathSearchRange()));
		world.profiler.endSection();
		return pathentity;
	}

	@Nullable
	private Path pathToXYZ(BlockPos pos) {
		ChunkCache chunkcache = cachePath(0, 8);
		Path pathentity = (new PathFinder(nodeProcessor).findPath(chunkcache, mountOrEntity(), pos, getPathSearchRange()));
		world.profiler.endSection();
		return pathentity;
	}

	private ChunkCache cachePath(int h, int r) {
		world.profiler.startSection("pathfind");
		int i = MathHelper.floor(entity.posX);
		int j = MathHelper.floor(entity.posY + h);
		int k = MathHelper.floor(entity.posZ);
		int l = (int) (getPathSearchRange() + r);
		return new ChunkCache(world, new BlockPos(i - l, j - l, k - l), new BlockPos(i + l, j + l, k + l), 0);
	}

	/*
	 * Whether pathing can be done
	 */
	@Override
	protected boolean canNavigate() {
		return super.canNavigate() || hasMount();
	}

	/*
	 * Returns true when an entity could stand at a position, including solid blocks under the entire entity.
	 */
	@Override
	protected boolean isSafeToStandAt(int xOffset, int yOffset, int zOffset, int xSize, int ySize, int zSize, Vec3d origin, double vecX, double vecZ) {
		int k1 = xOffset - xSize / 2;
		int l1 = zOffset - zSize / 2;

		if (!isPositionClear(k1, yOffset, l1, xSize, ySize, zSize, origin, vecX, vecZ)) {
			return false;
		} else {
			for (int i2 = k1; i2 < k1 + xSize; ++i2) {
				for (int j2 = l1; j2 < l1 + zSize; ++j2) {
					double d2 = (double) i2 + 0.5D - origin.x;
					double d3 = (double) j2 + 0.5D - origin.z;

					if (d2 * vecX + d3 * vecZ >= 0.0D) {
						Material material = world.getBlockState(new BlockPos(i2, yOffset - 1, j2)).getMaterial();

						if (material == Material.AIR || material == Material.LAVA || material == Material.FIRE || material == Material.CACTUS) {
							return false;
						}

						if (material == Material.WATER && !entity.isInWater()) {
							return false;
						}
					}
				}
			}

			return true;
		}
	}

	@Override
	public String toString() {
		String result;
		if (noPath()) {
			result = "No Path " + (getPath() != null ? getPath().getCurrentPathLength() : "");
		} else {
			result = "Path to " + getPath().getPathPointFromIndex(getPath().getCurrentPathIndex()).toString();
		}
		if (hasMount() && !((EntityLiving) entity.getRidingEntity()).getNavigator().noPath()) {
			Path path = ((EntityLiving) entity.getRidingEntity()).getNavigator().getPath();
			result += "AND Mount path to " + path.getPathPointFromIndex(path.getCurrentPathIndex()).toString();
		}
		return result;
	}
}
