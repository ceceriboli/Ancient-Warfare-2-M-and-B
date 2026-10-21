package net.shadowmage.ancientwarfare.npc.ai;

import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;
import net.shadowmage.ancientwarfare.npc.war.Settlements;

import javax.annotation.Nullable;

/**
 * Every npc that has been in water for a moment heads for the nearest place on land where it can stand (within SWIM_OUT_RANGE blocks, up to
 * two blocks above its feet), swimming up and pushing against the bank as a player does: in water, an entity pushing against a block with
 * free space above it is lifted out (vanilla). The vanilla swimming task only keeps an npc afloat. Not while fighting (it has a target), riding
 * or sleeping; the attackers of a siege also go when they have a target, they are always in a fight (they have their own way out in
 * NpcAIFactionAssault, which only runs when this task does not). When swimming does not get it out
 * within SWIM_OUT_TICKS it is put on the land it was heading for (the swimming alone did not work in every place: banks, boats, deep water).
 */
public class NpcAISwimOut extends NpcAI<NpcBase> {
	private static final int SWIM_OUT_RANGE = 8;
	private static final int TELEPORT_RANGE = 16;
	private static final int IN_WATER_TICKS = 30;
	private static final int SWIM_OUT_TICKS = 40;
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
			if (now > swimOutUntil) {
				if (npc.isInWater()) {
					putOnLand();
				}
				swimOutTo = null;
				inWaterSince = -1;
				pausedUntil = now + PAUSE_TICKS;
				return false;
			}
			return npc.isInWater() || !npc.onGround;
		}
		boolean siegeAttacker = npc.getEntityData().hasKey(Settlements.TAG_SIEGE);
		if (!npc.isInWater() || npc.isRiding() || npc.isPlayerSleeping() || npc.isSleeping() || !siegeAttacker && npc.getAttackTarget() != null) {
			inWaterSince = -1;
			return false;
		}
		if (inWaterSince < 0) {
			inWaterSince = now;
		}
		if (now - inWaterSince < IN_WATER_TICKS || now < pausedUntil) {
			return false;
		}
		swimOutTo = findLand(SWIM_OUT_RANGE);
		if (swimOutTo == null) {
			pausedUntil = now + PAUSE_TICKS;
			return false;
		}
		swimOutUntil = now + SWIM_OUT_TICKS;
		return true;
	}

	/**
	 * Swimming did not get the npc out: it is put on the land it was heading for, or on the nearest within TELEPORT_RANGE.
	 */
	private void putOnLand() {
		BlockPos land = swimOutTo != null && npc.world.isAirBlock(swimOutTo) ? swimOutTo : findLand(TELEPORT_RANGE);
		if (land == null) {
			return;
		}
		AncientWarfareNPC.LOG.info("{} was put on land at {} after {} ticks in the water", npc.getName(), land, SWIM_OUT_TICKS);
		npc.setPositionAndUpdate(land.getX() + 0.5D, land.getY(), land.getZ() + 0.5D);
		npc.getNavigator().clearPath();
		npc.motionX = 0D;
		npc.motionY = 0D;
		npc.motionZ = 0D;
		npc.fallDistance = 0F;
		if (npc.world instanceof WorldServer) {
			((WorldServer) npc.world).spawnParticle(EnumParticleTypes.WATER_SPLASH, npc.posX, npc.posY + 0.5D, npc.posZ, 20, 0.3D, 0.5D, 0.3D, 0.1D);
		}
	}

	@Nullable
	private BlockPos findLand(int range) {
		BlockPos origin = npc.getPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.getAllInBoxMutable(origin.add(-range, -1, -range), origin.add(range, 2, range))) {
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
