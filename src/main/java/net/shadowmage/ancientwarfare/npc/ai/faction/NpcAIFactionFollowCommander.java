package net.shadowmage.ancientwarfare.npc.ai.faction;

import net.minecraft.entity.player.EntityPlayer;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;
import net.shadowmage.ancientwarfare.npc.war.Armies;

import javax.annotation.Nullable;

/**
 * A soldier of a player's army follows its commander (faction npcs cannot use the follow of Ancient Warfare, reserved to npcs the player
 * may command). It fights enemies near the commander and comes back when it gets too far. Idle while the army charges a city.
 */
public class NpcAIFactionFollowCommander extends NpcAI<NpcBase> {
	public static final int PRIORITY = 1;
	private static final double FOLLOW_STOP_DIST_SQ = 4D * 4D;
	private static final double FIGHT_LEASH_DIST_SQ = 16D * 16D;

	@Nullable
	private EntityPlayer commander;

	public NpcAIFactionFollowCommander(NpcBase npc) {
		super(npc);
		setMutexBits(MOVE);
	}

	@Override
	public boolean shouldExecute() {
		if (!super.shouldExecute() || Armies.isCharging(npc)) {
			return false;
		}
		commander = Armies.getCommander(npc);
		if (commander == null) {
			return false;
		}
		if (npc.getAttackTarget() != null && npc.getAttackTarget().isEntityAlive()) {
			// fights near its commander, comes back when it gets too far
			return npc.getDistanceSq(commander) >= FIGHT_LEASH_DIST_SQ;
		}
		return npc.getDistanceSq(commander) > FOLLOW_STOP_DIST_SQ;
	}

	@Override
	public void startExecuting() {
		moveRetryDelay = 0;
		npc.addAITask(TASK_FOLLOW);
	}

	@Override
	public void updateTask() {
		if (commander == null) {
			return;
		}
		npc.getLookHelper().setLookPositionWithEntity(commander, 10F, (float) npc.getVerticalFaceSpeed());
		double distSq = npc.getDistanceSq(commander);
		if (distSq > FOLLOW_STOP_DIST_SQ) {
			npc.addAITask(TASK_MOVE);
			moveToEntity(commander, distSq);
		} else {
			npc.removeAITask(TASK_MOVE);
			npc.getNavigator().clearPath();
		}
	}

	@Override
	public void resetTask() {
		commander = null;
		npc.removeAITask(TASK_FOLLOW + TASK_MOVE);
		npc.getNavigator().clearPath();
	}
}
