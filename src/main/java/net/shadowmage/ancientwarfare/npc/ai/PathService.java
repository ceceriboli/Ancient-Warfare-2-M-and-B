package net.shadowmage.ancientwarfare.npc.ai;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared bookkeeping of the path searches of Ancient Warfare npcs (see NpcNavigator), per world:
 * - a budget of searches per tick (path_budget_per_tick);
 * - a queue: an npc that already walks a path and asks for a new one while the budget is spent keeps walking its old path, and its request is
 *   searched in a later tick (at most one pending request per npc, the latest);
 * - a shared cache of recent paths that reached their target: an npc asking for a path to the same target, starting near one of these paths,
 *   gets a short connecting path to it followed by the rest of the cached path, instead of a full search. When a block changes on a cached
 *   path, only the part after the change is kept, so the broken stretch is searched again and the rest is still reused.
 * Minecraft 1.12 stops every search after 200 steps, so a single search never blocks the server: what costs is the number of searches.
 */
public final class PathService {
	private static final int CACHE_MAX_PATHS = 128;
	private static final int CACHE_MAX_AGE_TICKS = 1200;
	private static final int MIN_CACHED_LENGTH = 4;
	private static final double JOIN_RANGE = 8;
	private static final int QUEUE_MAX_AGE_TICKS = 40;
	private static final int QUEUE_MIN_PER_TICK = 4;

	private static final Map<World, State> STATES = new WeakHashMap<>();

	private PathService() {
	}

	private static State state(World world) {
		return STATES.computeIfAbsent(world, w -> {
			State state = new State();
			if (w instanceof WorldServer) {
				w.addEventListener(new Listener(state));
			}
			return state;
		});
	}

	public static void onWorldUnload(World world) {
		STATES.remove(world);
	}

	/* ---------- budget ---------- */

	static void countSearch(World world) {
		State state = state(world);
		long now = world.getTotalWorldTime();
		if (state.budgetTick != now) {
			state.budgetTick = now;
			state.searches = 0;
		}
		state.searches++;
	}

	static boolean overBudget(World world) {
		int budget = AWDiplomacyStatics.pathBudgetPerTick;
		if (budget <= 0) {
			return false;
		}
		State state = STATES.get(world);
		return state != null && state.budgetTick == world.getTotalWorldTime() && state.searches >= budget;
	}

	/* ---------- queue ---------- */

	static void defer(World world, NpcNavigator navigator, int targetId, BlockPos pos, @Nullable Entity target) {
		State state = state(world);
		Deferred deferred = state.queue.get(navigator);
		if (deferred == null) {
			state.queue.put(navigator, new Deferred(targetId, pos, target, world.getTotalWorldTime()));
		} else {
			// keep its place in the queue, answer the latest request
			deferred.update(targetId, pos, target);
		}
	}

	/**
	 * Called at the end of every world tick: searches the queued requests with what is left of the budget (at least a few per tick).
	 */
	public static void tick(WorldServer world) {
		State state = STATES.get(world);
		if (state == null) {
			return;
		}
		long now = world.getTotalWorldTime();
		if (!state.queue.isEmpty()) {
			int used = state.budgetTick == now ? state.searches : 0;
			int budget = AWDiplomacyStatics.pathBudgetPerTick;
			int allowance = budget <= 0 ? Integer.MAX_VALUE : Math.max(QUEUE_MIN_PER_TICK, budget - used);
			Iterator<Map.Entry<NpcNavigator, Deferred>> it = state.queue.entrySet().iterator();
			while (it.hasNext() && allowance > 0) {
				Map.Entry<NpcNavigator, Deferred> entry = it.next();
				it.remove();
				Deferred deferred = entry.getValue();
				if (now - deferred.since > QUEUE_MAX_AGE_TICKS) {
					continue;
				}
				if (entry.getKey().completeDeferred(deferred.targetId, deferred.pos, deferred.target.get(), deferred.since)) {
					allowance--;
				}
			}
		}
		if (now % 100 == 0) {
			state.cache.removeIf(cached -> now - cached.tick > CACHE_MAX_AGE_TICKS);
		}
	}

	/* ---------- shared cache ---------- */

	private static int sizeKey(EntityLiving mover, boolean canSwim, boolean canOpenDoors) {
		return MathHelper.ceil(mover.width) * 1000 + MathHelper.ceil(mover.height) * 10 + (canSwim ? 1 : 0) + (canOpenDoors ? 2 : 0);
	}

	private static boolean near(int x, int y, int z, BlockPos pos, double horizontal, int vertical) {
		double dx = x - pos.getX();
		double dz = z - pos.getZ();
		return dx * dx + dz * dz <= horizontal * horizontal && Math.abs(y - pos.getY()) <= vertical;
	}

	/**
	 * @return a path to target made of a connecting search (only if allowed) and the rest of a cached path, or null if no cached path helps
	 */
	@Nullable
	static Path fromCache(World world, NpcNavigator navigator, EntityLiving mover, boolean canSwim, boolean canOpenDoors, BlockPos target,
			boolean allowConnector) {
		if (!AWDiplomacyStatics.pathSharedCache) {
			return null;
		}
		State state = STATES.get(world);
		if (state == null || state.cache.isEmpty()) {
			return null;
		}
		int key = sizeKey(mover, canSwim, canOpenDoors);
		BlockPos start = new BlockPos(mover);
		Cached best = null;
		int bestIndex = -1;
		double bestScore = Double.MAX_VALUE;
		for (Cached cached : state.cache) {
			if (cached.sizeKey != key || !near(cached.target.getX(), cached.target.getY(), cached.target.getZ(), target, 1.5, 1)) {
				continue;
			}
			int last = cached.xs.length - 1;
			for (int i = 0; i < last; i++) {
				double dx = cached.xs[i] - start.getX();
				double dz = cached.zs[i] - start.getZ();
				double horizontal = dx * dx + dz * dz;
				if (horizontal > JOIN_RANGE * JOIN_RANGE || Math.abs(cached.ys[i] - start.getY()) > 2) {
					continue;
				}
				double score = Math.sqrt(horizontal) + (last - i);
				if (score < bestScore) {
					bestScore = score;
					best = cached;
					bestIndex = i;
				}
			}
		}
		if (best == null) {
			return null;
		}
		BlockPos join = new BlockPos(best.xs[bestIndex], best.ys[bestIndex], best.zs[bestIndex]);
		if (near(join.getX(), join.getY(), join.getZ(), start, 1.5, 1)) {
			return best.toPath(bestIndex, null);
		}
		if (!allowConnector) {
			return null;
		}
		Path connector = navigator.searchCounted(join, null);
		PathPoint end = connector == null ? null : connector.getFinalPathPoint();
		if (end == null || !near(end.x, end.y, end.z, join, 1.0, 0)) {
			return null;
		}
		return best.toPath(bestIndex + 1, connector);
	}

	/**
	 * Remembers a searched path in the shared cache if it reached its target.
	 */
	static void offer(World world, EntityLiving mover, boolean canSwim, boolean canOpenDoors, BlockPos target, @Nullable Path path) {
		if (!AWDiplomacyStatics.pathSharedCache || world.isRemote || path == null || path.getCurrentPathLength() < MIN_CACHED_LENGTH) {
			return;
		}
		PathPoint end = path.getFinalPathPoint();
		if (end == null || !near(end.x, end.y, end.z, target, 1.5, 1)) {
			return;
		}
		State state = state(world);
		int key = sizeKey(mover, canSwim, canOpenDoors);
		PathPoint first = path.getPathPointFromIndex(0);
		// a path from (about) the same place to the same target replaces the old one
		state.cache.removeIf(cached -> cached.sizeKey == key && cached.target.equals(target) && near(cached.xs[0], cached.ys[0], cached.zs[0],
				new BlockPos(first.x, first.y, first.z), 2, 1));
		if (state.cache.size() >= CACHE_MAX_PATHS) {
			state.cache.remove(0);
		}
		state.cache.add(new Cached(path, target, key, world.getTotalWorldTime()));
	}

	/**
	 * A block changed: the cached paths passing there keep only their part after the change.
	 */
	private static void onBlockChanged(State state, BlockPos pos) {
		boolean touched = false;
		for (Cached cached : state.cache) {
			if (cached.mayTouch(pos)) {
				touched = true;
				break;
			}
		}
		if (!touched) {
			return; // block changes happen all the time (water, crops, leaves): most touch no path
		}
		List<Cached> kept = new ArrayList<>(state.cache.size());
		for (Cached cached : state.cache) {
			if (!cached.mayTouch(pos)) {
				kept.add(cached);
				continue;
			}
			int lastTouched = -1;
			for (int i = 0; i < cached.xs.length; i++) {
				if (Math.abs(cached.xs[i] - pos.getX()) <= 1 && Math.abs(cached.zs[i] - pos.getZ()) <= 1 && pos.getY() >= cached.ys[i] - 1
						&& pos.getY() <= cached.ys[i] + 3) {
					lastTouched = i;
				}
			}
			if (lastTouched < 0) {
				kept.add(cached);
			} else if (cached.xs.length - (lastTouched + 1) >= MIN_CACHED_LENGTH) {
				kept.add(cached.tail(lastTouched + 1));
			}
		}
		state.cache.clear();
		state.cache.addAll(kept);
	}

	/* ---------- data ---------- */

	private static final class State {
		private long budgetTick = -1;
		private int searches;
		private final Map<NpcNavigator, Deferred> queue = new LinkedHashMap<>();
		private final List<Cached> cache = new ArrayList<>();
	}

	private static final class Deferred {
		private int targetId;
		private BlockPos pos;
		private WeakReference<Entity> target;
		private final long since;

		private Deferred(int targetId, BlockPos pos, @Nullable Entity target, long since) {
			this.since = since;
			update(targetId, pos, target);
		}

		private void update(int targetId, BlockPos pos, @Nullable Entity target) {
			this.targetId = targetId;
			this.pos = pos;
			this.target = new WeakReference<>(target);
		}
	}

	/**
	 * Coordinates only: Path and PathPoint objects are changed by the navigators that follow them, so every reuse builds new ones.
	 */
	private static final class Cached {
		private final int[] xs;
		private final int[] ys;
		private final int[] zs;
		private final PathNodeType[] types;
		private final BlockPos target;
		private final int sizeKey;
		private final long tick;
		private int minX = Integer.MAX_VALUE;
		private int minY = Integer.MAX_VALUE;
		private int minZ = Integer.MAX_VALUE;
		private int maxX = Integer.MIN_VALUE;
		private int maxY = Integer.MIN_VALUE;
		private int maxZ = Integer.MIN_VALUE;

		private Cached(Path path, BlockPos target, int sizeKey, long tick) {
			int length = path.getCurrentPathLength();
			xs = new int[length];
			ys = new int[length];
			zs = new int[length];
			types = new PathNodeType[length];
			for (int i = 0; i < length; i++) {
				PathPoint point = path.getPathPointFromIndex(i);
				xs[i] = point.x;
				ys[i] = point.y;
				zs[i] = point.z;
				types[i] = point.nodeType;
			}
			this.target = target;
			this.sizeKey = sizeKey;
			this.tick = tick;
			computeBounds();
		}

		private Cached(Cached from, int start) {
			int length = from.xs.length - start;
			xs = new int[length];
			ys = new int[length];
			zs = new int[length];
			types = new PathNodeType[length];
			System.arraycopy(from.xs, start, xs, 0, length);
			System.arraycopy(from.ys, start, ys, 0, length);
			System.arraycopy(from.zs, start, zs, 0, length);
			System.arraycopy(from.types, start, types, 0, length);
			target = from.target;
			sizeKey = from.sizeKey;
			tick = from.tick;
			computeBounds();
		}

		private void computeBounds() {
			for (int i = 0; i < xs.length; i++) {
				minX = Math.min(minX, xs[i]);
				minY = Math.min(minY, ys[i]);
				minZ = Math.min(minZ, zs[i]);
				maxX = Math.max(maxX, xs[i]);
				maxY = Math.max(maxY, ys[i]);
				maxZ = Math.max(maxZ, zs[i]);
			}
		}

		private boolean mayTouch(BlockPos pos) {
			return pos.getX() >= minX - 1 && pos.getX() <= maxX + 1 && pos.getZ() >= minZ - 1 && pos.getZ() <= maxZ + 1 && pos.getY() >= minY - 1
					&& pos.getY() <= maxY + 3;
		}

		private Cached tail(int start) {
			return new Cached(this, start);
		}

		/**
		 * @return a new path: the points of the connector (if any) followed by the cached points from start
		 */
		private Path toPath(int start, @Nullable Path connector) {
			int head = connector == null ? 0 : connector.getCurrentPathLength();
			PathPoint[] points = new PathPoint[head + xs.length - start];
			for (int i = 0; i < head; i++) {
				PathPoint point = connector.getPathPointFromIndex(i);
				points[i] = point.cloneMove(point.x, point.y, point.z);
			}
			for (int i = start; i < xs.length; i++) {
				PathPoint point = new PathPoint(xs[i], ys[i], zs[i]);
				point.nodeType = types[i];
				points[head + i - start] = point;
			}
			return new Path(points);
		}
	}

	private static final class Listener implements IWorldEventListener {
		private final WeakReference<State> state;

		private Listener(State state) {
			this.state = new WeakReference<>(state);
		}

		@Override
		public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
			State current = state.get();
			if (current != null && oldState != newState) {
				onBlockChanged(current, pos);
			}
		}

		@Override
		public void notifyLightSet(BlockPos pos) {
		}

		@Override
		public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
		}

		@Override
		public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent soundIn, SoundCategory category, double x, double y, double z,
				float volume, float pitch) {
		}

		@Override
		public void playRecord(SoundEvent soundIn, BlockPos pos) {
		}

		@Override
		public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed,
				double zSpeed, int... parameters) {
		}

		@Override
		public void spawnParticle(int id, boolean ignoreRange, boolean minimiseParticleLevel, double x, double y, double z, double xSpeed, double ySpeed,
				double zSpeed, int... parameters) {
		}

		@Override
		public void onEntityAdded(Entity entityIn) {
		}

		@Override
		public void onEntityRemoved(Entity entityIn) {
		}

		@Override
		public void broadcastSound(int soundID, BlockPos pos, int data) {
		}

		@Override
		public void playEvent(EntityPlayer player, int type, BlockPos blockPosIn, int data) {
		}

		@Override
		public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
		}
	}
}
