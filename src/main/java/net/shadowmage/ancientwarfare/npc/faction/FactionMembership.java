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

import javax.annotation.Nullable;
import java.util.Comparator;
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
		getData(server.getEntityWorld()).set(player.getUniqueID(), player.getName(), faction);
		if (announce) {
			announce(server, player.getUniqueID(), "command.aw.membership.announce.join", player.getName(), faction);
		}
		sendDiplomacy(player);
	}

	/**
	 * Removes the player from their faction, takes standingPenalty from their standing with it and announces it with announceKey (if not null).
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
		data.remove(playerId);
		if (standingPenalty > 0) {
			FactionTracker.INSTANCE.adjustStandingFor(world, playerName, faction, -standingPenalty);
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
				player.sendMessage(clickable(new TextComponentTranslation("command.aw.membership.click.renounce"), TextFormatting.RED,
						"/awmembership leave", new TextComponentTranslation("command.aw.membership.click.renounce.hover", AWDiplomacyStatics.leaveStandingPenalty, factionName(npcFaction))));
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
