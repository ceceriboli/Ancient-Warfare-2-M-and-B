package net.shadowmage.ancientwarfare.npc.ai;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.util.math.AxisAlignedBB;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;

import javax.annotation.Nullable;

public class NpcAIAttackNearest extends EntityAINearestAttackableTarget<EntityLivingBase> {
	@SuppressWarnings({"java:S4738", "Guava"})
	public NpcAIAttackNearest(NpcBase npc, @Nullable final Predicate<Entity> targetSelector) {
		super(npc, EntityLivingBase.class, 0, true, false, targetSelector);
	}

	// the next tick this npc may search for a target: the searches of many npcs are spread over target_scan_interval ticks
	private long nextScanTick = -1;

	@Override
	public boolean shouldExecute() {
		boolean ret = isScanDue() && super.shouldExecute();
		if (!ret && taskOwner.getAttackTarget() != null && taskOwner.getAttackTarget().isDead) {
			taskOwner.setAttackTarget(null);
		}
		return ret;
	}

	private boolean isScanDue() {
		int interval = AWDiplomacyStatics.targetScanInterval;
		if (interval <= 0 || taskOwner.world.isRemote) {
			return true;
		}
		long now = taskOwner.world.getTotalWorldTime();
		if (nextScanTick < 0) {
			nextScanTick = now + taskOwner.getRNG().nextInt(interval);
		}
		if (now < nextScanTick) {
			return false;
		}
		nextScanTick = now + interval;
		return true;
	}

	@Override
	protected boolean isSuitableTarget(@Nullable EntityLivingBase target, boolean unused) {
		return AIHelper.isTarget((NpcBase) taskOwner, target, shouldCheckSight);
	}

	@Override
	protected AxisAlignedBB getTargetableArea(double targetDistance) {
		return taskOwner.getEntityBoundingBox().grow(targetDistance, targetDistance, targetDistance);
	}
}
