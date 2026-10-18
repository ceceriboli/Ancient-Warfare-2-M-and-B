package net.shadowmage.ancientwarfare.npc.faction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFactionLeader;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionMembershipData;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionRelationData;
import net.shadowmage.ancientwarfare.npc.network.PacketFactionDiplomacy;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Players pledging allegiance to a faction. A member is treated by other factions according to the relation
 * between them and the member's faction; personal standing only decides when the factions are not at war nor allied.
 */
public class FactionMembership {
	private FactionMembership() {}

	/**
	 * @return whether npcFaction is hostile to the player because of their faction, or null when personal standing decides
	 */
	@Nullable
	public static Boolean getHostility(World world, @Nullable UUID playerId, String npcFaction) {
		if (!AWDiplomacyStatics.membershipEnabled || playerId == null) {
			return null;
		}
		String playerFaction = getFaction(world, playerId);
		if (playerFaction == null) {
			return null;
		}
		if (playerFaction.equals(npcFaction)) {
			return false;
		}
		FactionRelation relation = FactionRelations.getSetRelation(world, npcFaction, playerFaction);
		if (relation == FactionRelation.WAR) {
			return true;
		}
		if (relation == FactionRelation.ALLIANCE) {
			return false;
		}
		if (relation == null && FactionRelations.isHostile(world, npcFaction, playerFaction)) {
			return true;
		}
		return null;
	}

	@Nullable
	public static String getFaction(World world, UUID playerId) {
		return world.isRemote ? ClientFactionDiplomacy.getFaction(playerId) : getData(world).getFaction(playerId);
	}

	public static void join(MinecraftServer server, EntityPlayerMP player, String faction, boolean announce) {
		World world = server.getEntityWorld();
		revertInherited(world, player.getUniqueID()); // moved by an operator from another faction
		getData(world).set(player.getUniqueID(), player.getName(), faction);
		// a member is well regarded by its faction
		if (FactionTracker.INSTANCE.getStandingFor(world, player.getName(), faction) < AWDiplomacyStatics.memberStanding) {
			FactionTracker.INSTANCE.setStandingFor(world, player.getName(), faction, AWDiplomacyStatics.memberStanding);
		}
		inheritStandings(world, player.getUniqueID(), player.getName(), faction);
		if (announce) {
			announce(server, player.getUniqueID(), "command.aw.membership.announce.join", player.getName(), faction);
		}
		sendDiplomacy(player);
	}

	/**
	 * The member takes the relations of its faction as standings with every other faction.
	 */
	private static void inheritStandings(World world, UUID playerId, String playerName, String faction) {
		for (String other : FactionRegistry.getFactionNames()) {
			if (!other.equals(faction)) {
				inheritStanding(world, playerId, playerName, faction, other);
			}
		}
	}

	/**
	 * At most member_enemy_standing with an enemy of the faction, at least member_ally_standing with an ally, at least 0 with the others
	 * (a faction at peace with the member's faction is not hostile to it). Factions with a fixed standing are left as they are.
	 * The change is recorded, so that leaving the faction takes it back.
	 */
	private static void inheritStanding(World world, UUID playerId, String playerName, String faction, String other) {
		if (!AWDiplomacyStatics.membershipEnabled || !AWDiplomacyStatics.memberInheritStandings) {
			return;
		}
		int standing = FactionTracker.INSTANCE.getStandingFor(world, playerName, other);
		int inherited;
		if (FactionRelations.isHostile(world, faction, other) || FactionRelations.isHostile(world, other, faction)) {
			inherited = Math.min(standing, AWDiplomacyStatics.memberEnemyStanding);
		} else if (FactionRelations.getSetRelation(world, faction, other) == FactionRelation.ALLIANCE) {
			inherited = Math.max(standing, AWDiplomacyStatics.memberAllyStanding);
		} else {
			inherited = Math.max(standing, 0);
		}
		if (inherited != standing) {
			FactionTracker.INSTANCE.setStandingFor(world, playerName, other, inherited);
			// what really changed (a fixed standing, or the player's team, may keep it as it was)
			getData(world).addInherited(playerId, other, FactionTracker.INSTANCE.getStandingFor(world, playerName, other) - standing);
		}
	}

	/**
	 * Takes back the standings inherited from the player's faction: each one goes back to its value before joining, plus the changes the
	 * player made on its own while a member (kills, trades).
	 */
	private static void revertInherited(World world, UUID playerId) {
		FactionMembershipData.Member member = getData(world).getMember(playerId);
		if (member == null) {
			return;
		}
		for (Map.Entry<String, Integer> entry : member.getInherited().entrySet()) {
			int standing = FactionTracker.INSTANCE.getStandingFor(world, member.playerName, entry.getKey());
			FactionTracker.INSTANCE.setStandingFor(world, member.playerName, entry.getKey(), standing - entry.getValue());
		}
	}

	/**
	 * Called when the relation between two factions changed: members of either faction take the new relation as standing.
	 */
	static void onRelationChanged(World world, String faction, String otherFaction) {
		for (FactionMembershipData.Member member : getData(world).getMembers()) {
			if (member.faction.equals(faction)) {
				inheritStanding(world, member.playerId, member.playerName, faction, otherFaction);
			} else if (member.faction.equals(otherFaction)) {
				inheritStanding(world, member.playerId, member.playerName, otherFaction, faction);
			}
		}
	}

	/**
	 * Standing penalty value for a player renouncing its faction on its own: its standing with the faction goes back to 0 if positive.
	 */
	public static final int RENOUNCE = -1;

	/**
	 * Removes the player from their faction, takes standingPenalty from their standing with it (or resets a positive standing to 0 with
	 * {@link #RENOUNCE}) and announces it with announceKey (if not null).
	 *
	 * @return the faction the player left, or null if they were not a member of any
	 */
	@Nullable
	public static String leave(MinecraftServer server, UUID playerId, String playerName, int standingPenalty, @Nullable String announceKey) {
		World world = server.getEntityWorld();
		FactionMembershipData data = getData(world);
		String faction = data.getFaction(playerId);
		if (faction == null) {
			return null;
		}
		revertInherited(world, playerId);
		data.remove(playerId);
		if (standingPenalty > 0) {
			FactionTracker.INSTANCE.adjustStandingFor(world, playerName, faction, -standingPenalty);
		}
		if (standingPenalty == RENOUNCE && FactionTracker.INSTANCE.getStandingFor(world, playerName, faction) > 0) {
			// renouncing on one's own: back to neutral (not hostile), a negative standing stays as it is
			FactionTracker.INSTANCE.setStandingFor(world, playerName, faction, 0);
		}
		if (announceKey != null) {
			announce(server, playerId, announceKey, playerName, faction);
		}
		EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
		if (player != null) {
			sendDiplomacy(player);
		}
		return faction;
	}

	/**
	 * The faction has lost its last city: its members are released from their oath, without penalty (the standings they inherited from
	 * it are given back, the standing with the faction stays as it is).
	 */
	public static void onFactionGone(MinecraftServer server, String faction) {
		if (!AWDiplomacyStatics.membershipEnabled || !AWDiplomacyStatics.leaveWhenFactionGone) {
			return;
		}
		for (FactionMembershipData.Member member : new ArrayList<>(getData(server.getEntityWorld()).getMembers())) {
			if (member.faction.equals(faction)) {
				leave(server, member.playerId, member.playerName, 0, "command.aw.membership.announce.faction_gone");
			}
		}
	}

	public static void sendDiplomacy(EntityPlayerMP player) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setUniqueId("player", player.getUniqueID());
		String faction = getData(player.world).getFaction(player.getUniqueID());
		if (faction != null) {
			tag.setString("faction", faction);
		}
		FactionRelationData.writeRelations(tag, AWGameData.INSTANCE.getData(player.world, FactionRelationData.class).getAll());
		NetworkHandler.sendToPlayer(player, new PacketFactionDiplomacy(tag));
	}

	public static void sendDiplomacyToAll(MinecraftServer server) {
		for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
			sendDiplomacy(player);
		}
	}

	/**
	 * Called after a faction npc spoke to a player: ruling leaders offer to join or leave the faction, other npcs (captains included)
	 * tell where their ruling leader is.
	 */
	public static void afterDialogue(EntityPlayer player, NpcFaction npc, boolean npcHostile) {
		if (!AWDiplomacyStatics.membershipEnabled || npcHostile || player.world.isRemote) {
			return;
		}
		String npcFaction = npc.getFaction();
		if (npc instanceof NpcFactionLeader && ((NpcFactionLeader) npc).isRuler()) {
			String playerFaction = getFaction(player.world, player.getUniqueID());
			if (playerFaction == null) {
				player.sendMessage(clickable(new TextComponentTranslation("command.aw.membership.click.pledge", factionName(npcFaction)), TextFormatting.GREEN,
						"/awmembership join " + npcFaction, new TextComponentTranslation("command.aw.membership.click.pledge.hover")));
			} else if (playerFaction.equals(npcFaction)) {
				if (AWDiplomacyStatics.serviceEnabled && AWDiplomacyStatics.siegesEnabled) {
					player.sendMessage(clickable(new TextComponentTranslation("command.aw.service.click.propose"), TextFormatting.GOLD, "/awservice propose",
							new TextComponentTranslation("command.aw.service.click.propose.hover")));
				}
				player.sendMessage(clickable(new TextComponentTranslation("command.aw.membership.click.renounce"), TextFormatting.RED,
						"/awmembership leave", new TextComponentTranslation("command.aw.membership.click.renounce.hover", factionName(npcFaction))));
			}
		} else if (AWDiplomacyStatics.leaderDirectionsRange > 0 && npc.world.rand.nextDouble() < AWDiplomacyStatics.leaderDirectionsChance) {
			findNearestLeader(npc.world, npc, npcFaction, AWDiplomacyStatics.leaderDirectionsRange).ifPresent(leader -> {
				ITextComponent message = new TextComponentTranslation("command.aw.membership.leader_location",
						(int) Math.floor(leader.posX), (int) Math.floor(leader.posY), (int) Math.floor(leader.posZ));
				message.getStyle().setColor(TextFormatting.GRAY);
				player.sendMessage(message);
			});
		}
	}

	public static Optional<NpcFactionLeader> findNearestLeader(World world, Entity center, String faction, int range) {
		AxisAlignedBB area = center.getEntityBoundingBox().grow(range);
		return world.getEntitiesWithinAABB(NpcFactionLeader.class, area, leader -> leader != null && leader.isEntityAlive() && leader.isRuler()
						&& faction.equals(leader.getFaction()))
				.stream().filter(leader -> leader.getDistance(center) <= range).min(Comparator.comparingDouble(leader -> leader.getDistanceSq(center)));
	}

	public static ITextComponent factionName(String faction) {
		String kingdomName = Kingdoms.displayName(faction);
		if (kingdomName != null) {
			return new net.minecraft.util.text.TextComponentString(kingdomName);
		}
		return new TextComponentTranslation("gui.ancientwarfarenpc.faction_name." + faction);
	}

	private static ITextComponent clickable(ITextComponent text, TextFormatting color, String command, ITextComponent hover) {
		text.setStyle(new Style().setColor(color).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
		return text;
	}

	private static void announce(MinecraftServer server, UUID playerId, String messageKey, String playerName, String faction) {
		ITextComponent message = new TextComponentTranslation(messageKey, playerName, factionName(faction));
		message.getStyle().setColor(TextFormatting.GOLD);
		if (AWDiplomacyStatics.announceMembershipChanges) {
			server.getPlayerList().sendMessage(message);
		} else {
			EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
			if (player != null) {
				player.sendMessage(message);
			}
		}
	}

	private static FactionMembershipData getData(World world) {
		return AWGameData.INSTANCE.getData(world, FactionMembershipData.class);
	}
}
