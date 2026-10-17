package net.shadowmage.ancientwarfare.npc.ai.faction;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;

/**
 * Keeps the ruler of a city at its post (its home): it fights the enemies that come within defender_guard_radius of the post and
 * walks back when it is farther, instead of running to the front line. Runs above the attack AI and blocks it while it runs.
 */
public class NpcAIFactionHoldPost extends NpcAI<NpcBase> {
	public static final int PRIORITY = 0;
	private static final double AT_POST_DIST_SQ = 2D * 2D;
	private static final int REPATH_TICKS = 20;

	private int repathDelay;

	public NpcAIFactionHoldPost(NpcBase npc) {
		super(npc);
		setMutexBits(MOVE | ATTACK);
	}

	@Override
	public boolean shouldExecute() {
		if (!super.shouldExecute() || !npc.hasHome() || npc.isRiding()) {
			return false;
		}
		double radiusSq = (double) AWDiplomacyStatics.defenderGuardRadius * AWDiplomacyStatics.defenderGuardRadius;
		BlockPos post = npc.getHomePosition();
		if (npc.getDistanceSqToCenter(post) > radiusSq) {
			return true; // too far from the post
		}
		EntityLivingBase enemy = npc.getAttackTarget();
		// an enemy beyond the guarded area is not chased: the ruler waits at its post
		return enemy != null && enemy.getDistanceSqToCenter(post) > radiusSq;
	}

	@Override
	public void startExecuting() {
		npc.addAITask(TASK_GO_HOME);
		repathDelay = 0;
	}

	@Override
	public void updateTask() {
		if (--repathDelay > 0) {
			return;
		}
		repathDelay = REPATH_TICKS;
		BlockPos post = npc.getHomePosition();
		if (npc.getDistanceSqToCenter(post) > AT_POST_DIST_SQ) {
			npc.getNavigator().tryMoveToXYZ(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 1.0D);
		} else {
			npc.getNavigator().clearPath();
			EntityLivingBase enemy = npc.getAttackTarget();
			if (enemy != null) {
				npc.getLookHelper().setLookPositionWithEntity(enemy, 30F, 30F);
			}
		}
	}

	@Override
	public void resetTask() {
		npc.removeAITask(TASK_GO_HOME);
		npc.getNavigator().clearPath();
	}
}
