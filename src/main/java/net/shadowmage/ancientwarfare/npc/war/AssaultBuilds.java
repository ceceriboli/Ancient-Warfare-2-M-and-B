package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.block.BlockLadder;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ladders and bridges built by the attackers of a siege that cannot get any closer to the leader they march on (the raiders of MineColonies
 * do the same: without a path they place ladders to climb obstacles and blocks to bridge gaps; ideas only, the code is ours).
 * <p>
 * They are remembered by siege (not saved): the attackers that get stuck at the same wall climb the ladder of the first, and everything
 * is taken away when the siege ends (a bridge over water or lava gives the liquid back). After a restart in the middle of a siege what was
 * built stays, like the breaches of NpcAIFactionAssault.
 */
public class AssaultBuilds {
	private AssaultBuilds() {}

	private static final Map<Integer, List<Column>> ladders = new HashMap<>();
	private static final Map<Integer, Map<BlockPos, IBlockState>> bridges = new HashMap<>();

	/**
	 * A ladder up a wall: height ladders on the cells above base, each against the wall block next to it (towards dir). The cell above the
	 * wall, beside the top ladder, is the roof the climbers step onto.
	 */
	public static class Column {
		private final BlockPos base;
		private final EnumFacing dir;
		private final int height;
		private int placed;
		private final List<BlockPos> cells = new ArrayList<>();

		public Column(BlockPos base, EnumFacing dir, int height) {
			this.base = base;
			this.dir = dir;
			this.height = height;
		}

		public BlockPos getBase() {
			return base;
		}

		public EnumFacing getDir() {
			return dir;
		}

		public int getHeight() {
			return height;
		}

		/**
		 * @return the number of ladders placed from the bottom
		 */
		public int getPlaced() {
			return placed;
		}

		public boolean isComplete() {
			return placed >= height;
		}

		/**
		 * @return the cell beside the top of the wall that the climbers step onto
		 */
		public BlockPos getRoof() {
			return base.offset(dir).up(height);
		}

		/**
		 * Places the next ladder of the column.
		 *
		 * @return false if it cannot be placed (something in the way, the wall is gone)
		 */
		public boolean placeNext(World world) {
			BlockPos cell = base.up(placed);
			BlockPos wall = cell.offset(dir);
			boolean ladder = world.getBlockState(cell).getBlock() == Blocks.LADDER;
			if (!ladder && !world.isAirBlock(cell) || world.getBlockState(wall).getBlockFaceShape(world, wall, dir.getOpposite()) != BlockFaceShape.SOLID) {
				return false;
			}
			if (!ladder) {
				world.setBlockState(cell, Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, dir.getOpposite()));
				cells.add(cell);
			}
			placed++;
			return true;
		}
	}

	public static Column addLadder(int siegeId, Column column) {
		ladders.computeIfAbsent(siegeId, k -> new ArrayList<>()).add(column);
		return column;
	}

	/**
	 * @return the ladder of the siege whose foot is the nearest to pos (within range blocks and a few blocks of height), or null
	 */
	@Nullable
	public static Column findLadder(int siegeId, BlockPos pos, double range) {
		Column nearest = null;
		double nearestDistSq = range * range;
		for (Column column : ladders.getOrDefault(siegeId, new ArrayList<>())) {
			double dx = column.base.getX() - pos.getX();
			double dz = column.base.getZ() - pos.getZ();
			double distSq = dx * dx + dz * dz;
			if (distSq <= nearestDistSq && Math.abs(column.base.getY() - pos.getY()) <= 3) {
				nearest = column;
				nearestDistSq = distSq;
			}
		}
		return nearest;
	}

	/**
	 * Remembers a block placed to bridge a gap, with the state it replaced (air, water, lava).
	 */
	public static void addBridge(int siegeId, BlockPos pos, IBlockState original) {
		bridges.computeIfAbsent(siegeId, k -> new HashMap<>()).put(pos, original);
	}

	/**
	 * The siege is over: the ladders and bridges of the attackers are taken away (those that are loaded and still as they were placed).
	 */
	public static void removeAll(World world, int siegeId) {
		List<Column> columns = ladders.remove(siegeId);
		if (columns != null) {
			for (Column column : columns) {
				for (BlockPos cell : column.cells) {
					if (world.isBlockLoaded(cell) && world.getBlockState(cell).getBlock() == Blocks.LADDER) {
						world.setBlockToAir(cell);
					}
				}
			}
		}
		Map<BlockPos, IBlockState> blocks = bridges.remove(siegeId);
		if (blocks != null) {
			for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) {
				if (world.isBlockLoaded(entry.getKey()) && world.getBlockState(entry.getKey()).getBlock() == Blocks.PLANKS) {
					world.setBlockState(entry.getKey(), entry.getValue());
				}
			}
		}
	}
}
