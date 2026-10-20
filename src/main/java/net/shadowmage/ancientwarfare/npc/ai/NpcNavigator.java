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
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;

import javax.annotation.Nullable;

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
		return !canNavigate() ? null : request(-1, pos, null);
	}

	@Nullable
	@Override
	public Path getPathToEntityLiving(Entity target) {
		return !canNavigate() ? null : request(target.getEntityId(), new BlockPos(target), target);
	}

	/*
	 * Path searches are what costs (one A* search each, at most 200 steps in Minecraft 1.12) and many AIs ask again every tick for the same
	 * target. In order, a request gets:
	 * - its previous answer, if it asks for the same target (moved less than a block) within path_cache_ticks; with the budget of the tick
	 *   spent, also for a target within 4 blocks and an answer at most 5 x path_cache_ticks old;
	 * - a path from the shared cache of PathService (a short connecting search and the rest of a path another npc found to the same target);
	 * - with the budget of the tick spent (path_budget_per_tick) and a path still being walked: the path it walks now, and the request is
	 *   searched in a later tick by PathService, which then gives it the new path;
	 * - otherwise a search now. An npc without a path is never made to wait.
	 */
	private int lastTargetId = Integer.MIN_VALUE;
	private BlockPos lastTargetPos;
	private long lastPathTick = Long.MIN_VALUE;
	private Path lastPath;
	private int requestedTargetId = Integer.MIN_VALUE;
	private BlockPos requestedPos;

	@Nullable
	private Path request(int targetId, BlockPos pos, @Nullable Entity target) {
		if (world.isRemote) {
			return search(pos, target);
		}
		requestedTargetId = targetId;
		requestedPos = pos;
		if (reusable(targetId, pos)) {
			return lastPath;
		}
		boolean overBudget = PathService.overBudget(world);
		Path shared = PathService.fromCache(world, this, mountOrEntity(), nodeProcessor.getCanSwim(), nodeProcessor.getCanOpenDoors(), pos, !overBudget);
		if (shared != null) {
			return remember(targetId, pos, shared);
		}
		if (overBudget && AWDiplomacyStatics.pathQueue && !noPath()) {
			PathService.defer(world, this, targetId, pos, target);
			return currentPath;
		}
		Path path = searchCounted(pos, target);
		PathService.offer(world, mountOrEntity(), nodeProcessor.getCanSwim(), nodeProcessor.getCanOpenDoors(), pos, path);
		return remember(targetId, pos, path);
	}

	/**
	 * A queued request is searched now, if this npc still wants that target and got no answer since; the new path replaces the one it walks.
	 *
	 * @return true if a search was made
	 */
	boolean completeDeferred(int targetId, BlockPos pos, @Nullable Entity target, long since) {
		if (entity.isDead || !canNavigate() || targetId != requestedTargetId || (targetId < 0 && !pos.equals(requestedPos))
				|| (target == null && targetId >= 0) || (lastTargetId == targetId && lastPathTick >= since)) {
			return false;
		}
		BlockPos current = target == null ? pos : new BlockPos(target);
		Path path = searchCounted(current, target);
		PathService.offer(world, mountOrEntity(), nodeProcessor.getCanSwim(), nodeProcessor.getCanOpenDoors(), current, path);
		remember(targetId, current, path);
		if (path != null) {
			setPath(path, speed);
		}
		return true;
	}

	@Nullable
	Path searchCounted(BlockPos pos, @Nullable Entity target) {
		if (!world.isRemote) {
			PathService.countSearch(world);
		}
		return search(pos, target);
	}

	@Nullable
	private Path search(BlockPos pos, @Nullable Entity target) {
		return target == null ? pathToXYZ(pos) : pathToEntity(target);
	}

	private boolean reusable(int targetId, BlockPos pos) {
		int cacheTicks = AWDiplomacyStatics.pathCacheTicks;
		if (cacheTicks <= 0 || lastTargetPos == null || targetId != lastTargetId || (lastPath != null && lastPath.isFinished())) {
			return false;
		}
		long age = world.getTotalWorldTime() - lastPathTick;
		double distanceSq = lastTargetPos.distanceSq(pos);
		if (age >= 0 && age < cacheTicks && distanceSq <= 1) {
			return true;
		}
		return age >= 0 && age < cacheTicks * 5L && distanceSq <= 16 && PathService.overBudget(world);
	}

	@Nullable
	private Path remember(int targetId, BlockPos pos, @Nullable Path path) {
		lastTargetId = targetId;
		lastTargetPos = pos;
		lastPathTick = world.getTotalWorldTime();
		lastPath = path;
		return path;
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
