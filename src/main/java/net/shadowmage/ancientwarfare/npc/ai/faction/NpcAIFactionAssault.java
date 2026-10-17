package net.shadowmage.ancientwarfare.npc.ai.faction;

import net.minecraft.util.math.BlockPos;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;

import javax.annotation.Nullable;

/**
 * Marches on a target position (the leader of a city under siege) while the npc has nothing to fight.
 * Gives way to the attack AI as soon as the npc has an attack target. The target is set from outside (see Sieges).
 */
public class NpcAIFactionAssault extends NpcAI<NpcBase> {
	public static final int PRIORITY = 3;
	private static final double ARRIVED_DIST_SQ = 4D * 4D;

	@Nullable
	private BlockPos target;

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
		moveRetryDelay = 0;
	}

	@Override
	public void updateTask() {
		if (target != null) {
			moveToPosition(target, getDistanceSqToTarget());
		}
	}

	@Override
	public void resetTask() {
		npc.removeAITask(TASK_MOVE);
		npc.getNavigator().clearPath();
	}
}
