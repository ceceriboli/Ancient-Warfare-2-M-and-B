package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.faction.FactionTracker;

import javax.annotation.Nullable;

/**
 * Loot chests of the structures (locked by Ancient Warfare while a faction npc sees the player, see npc.event.EventHandler) for the members
 * of a faction: the npcs of their own faction let them open its chests once they are trusted (standing at least chest_trust_standing),
 * and the players who served in the attack that conquered a city plunder its chests freely for chest_plunder_ticks (war booty).
 * Decided on the server only: cities and war booty are not known on the client.
 */
public class FactionChests {
	private FactionChests() {}

	/**
	 * @return the faction of the player if these rules apply to it (a member, the feature enabled), otherwise null
	 */
	@Nullable
	public static String getMemberFaction(World world, EntityPlayer player) {
		if (!AWDiplomacyStatics.factionChestsEnabled || !AWDiplomacyStatics.membershipEnabled) {
			return null;
		}
		return FactionMembership.getFaction(world, player.getUniqueID());
	}

	/**
	 * @return true if the chest is in a city the player conquered with its faction and may still plunder
	 */
	public static boolean isWarBooty(World world, EntityPlayer player, String faction, BlockPos pos) {
		Settlement city = Settlements.getData(world).getActiveAt(world.provider.getDimension(), pos, Settlement.Type.FACTION_CITY);
		return city != null && faction.equals(city.getFaction()) && city.isPlunderer(player.getUniqueID(), world.getTotalWorldTime());
	}

	/**
	 * @return true if npcs of npcFaction let the player open their chests: it is a member of that faction and trusted enough
	 */
	public static boolean isTrustedBy(World world, EntityPlayer player, String faction, String npcFaction) {
		return faction.equals(npcFaction) && getStanding(world, player, faction) >= AWDiplomacyStatics.chestTrustStanding;
	}

	public static int getStanding(World world, EntityPlayer player, String faction) {
		return FactionTracker.INSTANCE.getStandingFor(world, player.getName(), faction);
	}

	/**
	 * @return true if the chest is in an active city of the player's faction and the player is trusted (opens it without claiming the flag)
	 */
	public static boolean isTrustedCity(World world, EntityPlayer player, String faction, BlockPos pos) {
		Settlement city = Settlements.getData(world).getActiveAt(world.provider.getDimension(), pos, Settlement.Type.FACTION_CITY);
		return city != null && isTrustedBy(world, player, faction, city.getFaction());
	}
}
