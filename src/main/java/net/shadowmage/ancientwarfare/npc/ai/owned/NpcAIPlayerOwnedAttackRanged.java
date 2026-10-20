package net.shadowmage.ancientwarfare.npc.ai.owned;

import com.google.common.primitives.Floats;
import net.minecraft.entity.IRangedAttackMob;
import net.shadowmage.ancientwarfare.npc.ai.NpcAIAttack;
import net.shadowmage.ancientwarfare.npc.ai.RangedAim;
import net.shadowmage.ancientwarfare.npc.config.AWNPCStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;

public class NpcAIPlayerOwnedAttackRanged extends NpcAIAttack<NpcBase> {

	private final IRangedAttackMob rangedAttacker;
	private static final double ATTACK_DISTANCE = AWNPCStatics.archerRange * AWNPCStatics.archerRange;
	private final RangedAim aim;

	public NpcAIPlayerOwnedAttackRanged(NpcBase npc) {
		super(npc);
		rangedAttacker = (IRangedAttackMob) npc;//will classcastexception if improperly used..
		moveSpeed = 1.d;
		aim = new RangedAim(npc);
		setMutexBits(ATTACK + MOVE);
	}

	@Override
	protected boolean shouldCloseOnTarget(double dist) {
		if (npc.doNotPursue) {
			return (dist < 0);
		}
		// out of range, out of sight, no clear line for an arrow or its arrows keep missing: it moves (RangedAim)
		return dist > ATTACK_DISTANCE || !npc.getEntitySenses().canSee(getTarget()) || aim.mustMove(getTarget());
	}

	@Override
	protected void doAttack(double dist) {
		npc.removeAITask(TASK_MOVE);
		npc.getNavigator().clearPath();
		if (getAttackDelay() <= 0) {
			float pwr = (float) (ATTACK_DISTANCE / dist);
			//noinspection UnstableApiUsage
			pwr = Floats.constrainToRange(pwr, 0.1f, 1f);
			aim.onShot(getTarget());
			rangedAttacker.attackEntityWithRangedAttack(getTarget(), pwr);
			setAttackDelay(35);
		}
	}
}
