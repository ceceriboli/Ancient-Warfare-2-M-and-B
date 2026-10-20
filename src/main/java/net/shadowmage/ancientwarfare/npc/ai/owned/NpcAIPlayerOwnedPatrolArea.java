package net.shadowmage.ancientwarfare.npc.ai.owned;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned;
import net.shadowmage.ancientwarfare.npc.npc_command.GroupOrders;
import net.shadowmage.ancientwarfare.npc.npc_command.NpcCommand.Command;

/**
 * Patrol order of the command baton (GroupOrders): the soldier walks from point to point inside the area around the patrol center, every few
 * seconds a new one; it fights what it meets (its attack AIs have priority) and comes back to the area afterwards (its home is the center).
 */
public class NpcAIPlayerOwnedPatrolArea extends NpcAI<NpcPlayerOwned> {
	private static final int PAUSE_TICKS = 60;
	private BlockPos point;
	private int pause;

	public NpcAIPlayerOwnedPatrolArea(NpcPlayerOwned npc) {
		super(npc);
		setMutexBits(MOVE);
	}

	@Override
	public boolean shouldExecute() {
		if (!super.shouldExecute() || npc.getAttackTarget() != null || npc.getFollowingEntity() != null || npc.getCurrentCommand() != Command.NONE) {
			return false;
		}
		NBTTagCompound data = npc.getEntityData();
		if (!data.hasKey(GroupOrders.TAG_PATROL)) {
			return false;
		}
		if (pause > 0) {
			pause--;
			return false;
		}
		point = pickPoint(BlockPos.fromLong(data.getLong(GroupOrders.TAG_PATROL)), data.getInteger(GroupOrders.TAG_PATROL_RADIUS));
		return point != null;
	}

	@Override
	public void startExecuting() {
		npc.addAITask(TASK_MOVE);
	}

	@Override
	public void updateTask() {
		double distSq = npc.getDistanceSq(point);
		if (distSq <= MIN_RANGE || npc.getNavigator().noPath() && moveRetryDelay > 0) {
			point = null;
			pause = PAUSE_TICKS + npc.getRNG().nextInt(PAUSE_TICKS);
			return;
		}
		moveToPosition(point, distSq);
	}

	@Override
	public void resetTask() {
		npc.removeAITask(TASK_MOVE);
		point = null;
	}

	/**
	 * @return a place to stand (two free blocks above a full block, not in water) within radius of the center, or null
	 */
	private BlockPos pickPoint(BlockPos center, int radius) {
		for (int attempt = 0; attempt < 8; attempt++) {
			BlockPos column = center.add(npc.getRNG().nextInt(radius * 2 + 1) - radius, 0, npc.getRNG().nextInt(radius * 2 + 1) - radius);
			for (int dy = 4; dy >= -4; dy--) {
				BlockPos pos = column.up(dy);
				if (npc.world.isAirBlock(pos) && npc.world.isAirBlock(pos.up()) && npc.world.getBlockState(pos.down()).isFullBlock()
						&& !npc.world.getBlockState(pos.down()).getMaterial().isLiquid()) {
					return pos;
				}
			}
		}
		return null;
	}
}
