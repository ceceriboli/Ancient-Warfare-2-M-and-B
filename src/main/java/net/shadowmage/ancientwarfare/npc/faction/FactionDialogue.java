package net.shadowmage.ancientwarfare.npc.faction;

import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFactionLeader;
import net.shadowmage.ancientwarfare.npc.network.PacketFactionDialogue;
import net.shadowmage.ancientwarfare.npc.war.Service;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.Settlements;
import net.shadowmage.ancientwarfare.npc.war.Siege;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialogue window of a ruling leader (instead of clickable texts in chat, with dialogue_gui): pledge allegiance, propose a campaign,
 * renounce. The server builds each page and checks every button pressed again (the player must still be near the ruler, see the
 * actions below); the client only shows the pages (GuiFactionDialogue).
 */
public class FactionDialogue {
	private FactionDialogue() {}

	private static final String CLOSE = "close";
	private static final String BACK = "back";
	private static final String PLEDGE = "pledge";
	private static final String RENOUNCE = "renounce";
	private static final String RENOUNCE_CONFIRM = "renounce_yes";
	private static final String PROPOSE = "propose";
	private static final String TARGET_PREFIX = "target:";
	private static final String PROPOSE_PREFIX = "propose:";
	private static final int MAX_TARGETS = 6;
	private static final double EXTRA_RANGE = 4D;

	/**
	 * Called when the player speaks to a faction npc.
	 *
	 * @return true if a dialogue window was opened (the npc does not speak in chat)
	 */
	public static boolean open(EntityPlayerMP player, NpcFaction npc, String line, boolean hostile) {
		if (!AWDiplomacyStatics.dialogueGui || !AWDiplomacyStatics.membershipEnabled || hostile || !isRuler(npc)) {
			return false;
		}
		String playerFaction = FactionMembership.getFaction(player.world, player.getUniqueID());
		if (playerFaction != null && !playerFaction.equals(npc.getFaction())) {
			return false; // nothing to offer to a member of another faction
		}
		sendMainPage(player, npc, new TextComponentString(line));
		return true;
	}

	private static boolean isRuler(Entity entity) {
		return entity instanceof NpcFactionLeader && ((NpcFactionLeader) entity).isRuler();
	}

	/**
	 * A button of the window was pressed.
	 */
	public static void handleAction(EntityPlayerMP player, int entityId, String action) {
		Entity entity = player.world.getEntityByID(entityId);
		if (!isRuler(entity) || !entity.isEntityAlive() || player.getDistance(entity) > AWDiplomacyStatics.leaderJoinRange + EXTRA_RANGE
				|| ((NpcFaction) entity).isHostileTowards(player) || CLOSE.equals(action)) {
			sendClose(player);
			return;
		}
		NpcFaction npc = (NpcFaction) entity;
		String npcFaction = npc.getFaction();
		String playerFaction = FactionMembership.getFaction(player.world, player.getUniqueID());
		boolean member = npcFaction.equals(playerFaction);
		if (PLEDGE.equals(action) && playerFaction == null) {
			runCommand(player, "awmembership join " + npcFaction);
			if (npcFaction.equals(FactionMembership.getFaction(player.world, player.getUniqueID()))) {
				sendMainPage(player, npc, new TextComponentTranslation("gui.aw.dialogue.welcome", FactionMembership.factionName(npcFaction)));
			} else {
				sendClose(player); // refused: the reason is in chat
			}
		} else if (BACK.equals(action)) {
			sendMainPage(player, npc, new TextComponentTranslation("gui.aw.dialogue.anything_else"));
		} else if (RENOUNCE.equals(action) && member) {
			sendPage(player, npc, new TextComponentTranslation("gui.aw.dialogue.renounce.confirm", FactionMembership.factionName(npcFaction)),
					button(new TextComponentTranslation("gui.aw.dialogue.renounce.yes"), RENOUNCE_CONFIRM), button(new TextComponentTranslation("gui.aw.dialogue.no"), BACK));
		} else if (RENOUNCE_CONFIRM.equals(action) && member) {
			runCommand(player, "awmembership leave");
			sendClose(player);
		} else if (PROPOSE.equals(action) && member) {
			sendTargetsPage(player, npc);
		} else if (action.startsWith(TARGET_PREFIX) && member) {
			sendKindPage(player, npc, parseId(action.substring(TARGET_PREFIX.length())));
		} else if (action.startsWith(PROPOSE_PREFIX) && member) {
			String[] parts = action.substring(PROPOSE_PREFIX.length()).split(":");
			Siege.Kind kind = parts.length == 2 ? Siege.Kind.fromName(parts[1]) : null;
			if (kind == null) {
				sendClose(player);
				return;
			}
			ITextComponent answer;
			try {
				answer = Service.propose(player, parseId(parts[0]), kind);
			}
			catch (CommandException e) {
				answer = error(e);
			}
			sendPage(player, npc, answer, button(new TextComponentTranslation("gui.aw.dialogue.farewell"), CLOSE));
		} else {
			sendClose(player);
		}
	}

	private static void sendMainPage(EntityPlayerMP player, NpcFaction npc, ITextComponent text) {
		String npcFaction = npc.getFaction();
		String playerFaction = FactionMembership.getFaction(player.world, player.getUniqueID());
		List<Button> buttons = new ArrayList<>();
		if (playerFaction == null) {
			buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.pledge", FactionMembership.factionName(npcFaction)), PLEDGE)
					.hover(new TextComponentTranslation("command.aw.membership.click.pledge.hover")));
		} else if (playerFaction.equals(npcFaction)) {
			if (AWDiplomacyStatics.serviceEnabled && AWDiplomacyStatics.siegesEnabled) {
				buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.propose"), PROPOSE)
						.hover(new TextComponentTranslation("command.aw.service.click.propose.hover")));
			}
			buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.renounce"), RENOUNCE));
		}
		buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.farewell"), CLOSE));
		sendPage(player, npc, text, buttons.toArray(new Button[0]));
	}

	private static void sendTargetsPage(EntityPlayerMP player, NpcFaction npc) {
		String faction;
		try {
			faction = Service.checkProposer(player);
		}
		catch (CommandException e) {
			sendPage(player, npc, error(e), button(new TextComponentTranslation("gui.aw.dialogue.back"), BACK));
			return;
		}
		WorldServer world = (WorldServer) player.world;
		List<Settlement> targets = Service.getProposalTargets(world, player, faction, world.getTotalWorldTime());
		if (targets.isEmpty()) {
			sendPage(player, npc, new TextComponentTranslation("command.aw.service.propose.none", AWDiplomacyStatics.serviceCallRange),
					button(new TextComponentTranslation("gui.aw.dialogue.back"), BACK));
			return;
		}
		List<Button> buttons = new ArrayList<>();
		for (Settlement city : targets.subList(0, Math.min(MAX_TARGETS, targets.size()))) {
			buttons.add(button(describe(player, city), TARGET_PREFIX + city.getId()));
		}
		buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.back"), BACK));
		sendPage(player, npc, new TextComponentTranslation("gui.aw.dialogue.targets"), buttons.toArray(new Button[0]));
	}

	private static void sendKindPage(EntityPlayerMP player, NpcFaction npc, int cityId) {
		WorldServer world = (WorldServer) player.world;
		Settlement city = Settlements.getData(world).get(cityId);
		if (city == null) {
			sendPage(player, npc, new TextComponentTranslation("command.aw.service.propose.invalid"), button(new TextComponentTranslation("gui.aw.dialogue.back"), BACK));
			return;
		}
		Button raid = button(new TextComponentTranslation("gui.aw.dialogue.raid", AWDiplomacyStatics.campaignProposalStanding), PROPOSE_PREFIX + cityId + ":raid")
				.hover(new TextComponentTranslation("command.aw.service.propose.raid.hover", AWDiplomacyStatics.campaignProposalStanding));
		boolean siegeAllowed = Service.isFullSiegeAllowed(city, world.getTotalWorldTime());
		Button siege = button(siegeAllowed ? new TextComponentTranslation("gui.aw.dialogue.siege", AWDiplomacyStatics.campaignProposalSiegeStanding)
				: new TextComponentTranslation("gui.aw.dialogue.siege_too_recent"), PROPOSE_PREFIX + cityId + ":siege")
				.hover(new TextComponentTranslation("command.aw.service.propose.siege.hover", AWDiplomacyStatics.campaignProposalSiegeStanding));
		siege.enabled = siegeAllowed;
		sendPage(player, npc, new TextComponentTranslation("gui.aw.dialogue.kind", describe(player, city)), raid, siege,
				button(new TextComponentTranslation("gui.aw.dialogue.back"), PROPOSE));
	}

	/**
	 * @return "Crowfell (Norska), 420 blocks north-east"
	 */
	private static ITextComponent describe(EntityPlayerMP player, Settlement city) {
		BlockPos target = city.getLastLeaderPos();
		double dx = target.getX() + 0.5D - player.posX;
		double dz = target.getZ() + 0.5D - player.posZ;
		return new TextComponentTranslation("gui.aw.dialogue.city", Settlements.displayName(city), (int) Math.sqrt(dx * dx + dz * dz), Service.direction(dx, dz));
	}

	private static ITextComponent error(CommandException e) {
		ITextComponent text = new TextComponentTranslation(e.getMessage(), e.getErrorObjects());
		text.getStyle().setColor(TextFormatting.RED);
		return text;
	}

	private static int parseId(String text) {
		try {
			return Integer.parseInt(text);
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}

	private static void runCommand(EntityPlayerMP player, String command) {
		MinecraftServer server = player.getServer();
		if (server != null) {
			server.getCommandManager().executeCommand(player, command);
		}
	}

	private static void sendPage(EntityPlayerMP player, NpcFaction npc, ITextComponent text, Button... buttons) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setInteger("entity", npc.getEntityId());
		ITextComponent title = new TextComponentTranslation("gui.ancientwarfarenpc.leader_title", FactionMembership.factionName(npc.getFaction()));
		tag.setString("title", ITextComponent.Serializer.componentToJson(title));
		tag.setString("text", ITextComponent.Serializer.componentToJson(text));
		NBTTagList list = new NBTTagList();
		for (Button button : buttons) {
			NBTTagCompound buttonTag = new NBTTagCompound();
			buttonTag.setString("label", ITextComponent.Serializer.componentToJson(button.label));
			buttonTag.setString("action", button.action);
			buttonTag.setBoolean("enabled", button.enabled);
			if (button.hover != null) {
				buttonTag.setString("hover", ITextComponent.Serializer.componentToJson(button.hover));
			}
			list.appendTag(buttonTag);
		}
		tag.setTag("buttons", list);
		NetworkHandler.sendToPlayer(player, new PacketFactionDialogue(tag));
	}

	private static void sendClose(EntityPlayerMP player) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setBoolean("close", true);
		NetworkHandler.sendToPlayer(player, new PacketFactionDialogue(tag));
	}

	private static Button button(ITextComponent label, String action) {
		return new Button(label, action);
	}

	private static class Button {
		private final ITextComponent label;
		private final String action;
		private boolean enabled = true;
		@Nullable
		private ITextComponent hover;

		private Button(ITextComponent label, String action) {
			this.label = label;
			this.action = action;
		}

		private Button hover(ITextComponent hover) {
			this.hover = hover;
			return this;
		}
	}
}
