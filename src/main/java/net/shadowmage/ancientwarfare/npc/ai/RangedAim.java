package net.shadowmage.ancientwarfare.npc.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;

import javax.annotation.Nullable;

/**
 * Keeps an archer useful: it shoots only with a clear line to its target (no block in the way of the arrow: walls, battlements, leaves,
 * glass, fences), and when its arrows keep missing it moves instead of standing there. Seeing the target is not enough: an archer can see
 * an enemy over battlements or through a gap that its arrows hit. One per ranged attack AI.
 */
public class RangedAim {
	private static final int CLEAR_SHOT_CHECK_TICKS = 10;
	private static final int MISSES_BEFORE_MOVING = 3;
	private static final int MOVE_TICKS = 60;

	private final NpcBase npc;
	@Nullable
	private EntityLivingBase checkedTarget;
	private long clearShotCheckTick = Long.MIN_VALUE;
	private boolean clearShot;
	@Nullable
	private EntityLivingBase shotTarget;
	private float shotTargetHealth;
	private int misses;
	private long moveUntil;

	public RangedAim(NpcBase npc) {
		this.npc = npc;
	}

	/**
	 * @return true if the archer must move towards the target instead of shooting: no clear line for an arrow, or its last arrows missed
	 */
	public boolean mustMove(EntityLivingBase target) {
		long now = npc.world.getTotalWorldTime();
		if (now < moveUntil) {
			return true;
		}
		if (target != checkedTarget || now - clearShotCheckTick >= CLEAR_SHOT_CHECK_TICKS) {
			checkedTarget = target;
			clearShotCheckTick = now;
			clearShot = hasClearShot(target);
		}
		return !clearShot;
	}

	/**
	 * Called right before each shot: when the target did not lose health since the previous shots, they missed; after a few misses the
	 * archer moves (towards the target) for a while to find a better position.
	 */
	public void onShot(EntityLivingBase target) {
		if (target != shotTarget || target.getHealth() < shotTargetHealth - 0.01F) {
			shotTarget = target;
			misses = 0;
		} else if (++misses >= MISSES_BEFORE_MOVING) {
			misses = 0;
			moveUntil = npc.world.getTotalWorldTime() + MOVE_TICKS;
		}
		shotTargetHealth = target.getHealth();
	}

	/**
	 * @return true if no block stands between the eyes of the archer and the body or the head of the target
	 */
	private boolean hasClearShot(EntityLivingBase target) {
		Vec3d eyes = new Vec3d(npc.posX, npc.posY + npc.getEyeHeight(), npc.posZ);
		Vec3d body = new Vec3d(target.posX, target.getEntityBoundingBox().minY + target.height * 0.5D, target.posZ);
		Vec3d head = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ);
		return npc.world.rayTraceBlocks(eyes, body, false, true, false) == null || npc.world.rayTraceBlocks(eyes, head, false, true, false) == null;
	}
}
