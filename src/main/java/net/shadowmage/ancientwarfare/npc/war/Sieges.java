package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.world.World;

/**
 * Sieges of settlements by factions at war with them.
 */
public class Sieges {
	private Sieges() {}

	public static boolean isBesieged(Settlement settlement) {
		return false;
	}

	public static void onSettlementFallen(World world, Settlement settlement) {
		//noop until sieges are implemented
	}
}
