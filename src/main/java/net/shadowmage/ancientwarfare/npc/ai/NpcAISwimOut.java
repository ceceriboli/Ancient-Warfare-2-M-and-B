package net.shadowmage.ancientwarfare.npc.ai;

import net.minecraft.util.math.BlockPos;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;
import net.shadowmage.ancientwarfare.npc.war.Settlements;

import javax.annotation.Nullable;

/**
 * Every npc that has been in water for a moment heads for the nearest place on land where it can stand (within SWIM_OUT_RANGE blocks, up to
 * two blocks above its feet), swimming up and pushing against the bank as a player does: in water, an entity pushing against a block with
 * free space above it is lifted out (vanilla). The vanilla swimming task only keeps an npc afloat. Not while fighting (it has a target), riding
 * or sleeping, and not for the attackers of a siege, which have their own version in NpcAIFactionAssault.
 */
public class NpcAISwimOut extends NpcAI<NpcBase> {
	private static final int SWIM_OUT_RANGE = 8;
	private static final int IN_WATER_TICKS = 30;
	private static final int SWIM_OUT_TICKS = 100;
	private static final int PAUSE_TICKS = 60;

	private long inWaterSince = -1;
	private long pausedUntil;
	private long swimOutUntil;
	@Nullable
	private BlockPos swimOutTo;

	public NpcAISwimOut(NpcBase npc) {
		super(npc);
		setMutexBits(MOVE);
	}

	@Override
	public boolean shouldExecute() {
		if (!super.shouldExecute() || !AWDiplomacyStatics.npcLeaveWater) {
			return false;
		}
		long now = npc.world.getTotalWorldTime();
		if (swimOutTo != null) {
			return now <= swimOutUntil && (npc.isInWater() || !npc.onGround);
		}
		if (!npc.isInWater() || npc.isRiding() || npc.isPlayerSleeping() || npc.isSleeping() || npc.getAttackTarget() != null
				|| npc.getEntityData().hasKey(Settlements.TAG_SIEGE)) {
			inWaterSince = -1;
			return false;
		}
		if (inWaterSince < 0) {
			inWaterSince = now;
		}
		if (now - inWaterSince < IN_WATER_TICKS || now < pausedUntil) {
			return false;
		}
		swimOutTo = findLand();
		if (swimOutTo == null) {
			pausedUntil = now + PAUSE_TICKS;
			return false;
		}
		swimOutUntil = now + SWIM_OUT_TICKS;
		return true;
	}

	@Nullable
	private BlockPos findLand() {
		BlockPos origin = npc.getPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.getAllInBoxMutable(origin.add(-SWIM_OUT_RANGE, -1, -SWIM_OUT_RANGE), origin.add(SWIM_OUT_RANGE, 2, SWIM_OUT_RANGE))) {
			if (!npc.world.isAirBlock(pos) || !npc.world.isAirBlock(pos.up()) || !npc.getNavigator().canEntityStandOnPos(pos)
					|| npc.world.getBlockState(pos.down()).getMaterial().isLiquid()) {
				continue;
			}
			double distance = pos.distanceSq(origin);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = pos.toImmutable();
			}
		}
		return best;
	}

	@Override
	public void startExecuting() {
		npc.getNavigator().clearPath();
		npc.addAITask(TASK_MOVE);
	}

	@Override
	public void updateTask() {
		if (swimOutTo == null) {
			return;
		}
		double x = swimOutTo.getX() + 0.5D;
		double z = swimOutTo.getZ() + 0.5D;
		npc.getJumpHelper().setJumping();
		npc.getMoveHelper().setMoveTo(x, swimOutTo.getY(), z, 1.0D);
		npc.getLookHelper().setLookPosition(x, swimOutTo.getY() + 1D, z, 30F, 30F);
	}

	@Override
	public void resetTask() {
		swimOutTo = null;
		inWaterSince = -1;
		pausedUntil = npc.world.getTotalWorldTime() + PAUSE_TICKS;
		npc.removeAITask(TASK_MOVE);
	}
}
