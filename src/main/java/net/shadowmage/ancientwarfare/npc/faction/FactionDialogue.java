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
import net.shadowmage.ancientwarfare.npc.gamedata.KingdomData;
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
	private static final String WAGES = "wages";
	private static final String TARGET_PREFIX = "target:";
	private static final String PROPOSE_PREFIX = "propose:";
	private static final int MAX_TARGETS = 6;
	// the owner of a kingdom at its governor
	private static final String KINGDOM_PREFIX = "k:";
	private static final String K_PARTNERS = "partners:";
	private static final String K_PARTNER = "partner:";
	private static final String K_WAR_ASK = "war_ask:";
	private static final String K_WAR = "war:";
	private static final String K_PEACE = "peace:";
	private static final String K_ALLY = "ally:";
	private static final String K_BREAK = "break:";
	private static final String K_REALM = "realm";
	private static final String K_CULTURES = "cultures:";
	private static final String K_CULTURE = "culture:";
	private static final int PAGE_SIZE = 6;
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
		boolean owner = member && Kingdoms.isOwner(player.world, player.getUniqueID(), npcFaction);
		if (owner && action.startsWith(KINGDOM_PREFIX)) {
			handleKingdomAction(player, npc, action.substring(KINGDOM_PREFIX.length()));
		} else if (PLEDGE.equals(action) && playerFaction == null) {
			runCommand(player, "awmembership join " + npcFaction);
			if (npcFaction.equals(FactionMembership.getFaction(player.world, player.getUniqueID()))) {
				sendMainPage(player, npc, new TextComponentTranslation("gui.aw.dialogue.welcome", FactionMembership.factionName(npcFaction)));
			} else {
				sendClose(player); // refused: the reason is in chat
			}
		} else if (BACK.equals(action)) {
			sendMainPage(player, npc, new TextComponentTranslation("gui.aw.dialogue.anything_else"));
		} else if (WAGES.equals(action) && member && !owner) {
			ITextComponent answer;
			try {
				answer = Service.payWages(player);
			}
			catch (CommandException e) {
				answer = error(e);
			}
			sendPage(player, npc, answer, button(new TextComponentTranslation("gui.aw.dialogue.back"), BACK));
		} else if (RENOUNCE.equals(action) && member && !owner) {
			sendPage(player, npc, new TextComponentTranslation("gui.aw.dialogue.renounce.confirm", FactionMembership.factionName(npcFaction)),
					button(new TextComponentTranslation("gui.aw.dialogue.renounce.yes"), RENOUNCE_CONFIRM), button(new TextComponentTranslation("gui.aw.dialogue.no"), BACK));
		} else if (RENOUNCE_CONFIRM.equals(action) && member && !owner) {
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
			boolean owner = Kingdoms.isOwner(player.world, player.getUniqueID(), npcFaction);
			if (owner) {
				buttons.add(button(new TextComponentTranslation("gui.aw.kingdom.diplomacy"), KINGDOM_PREFIX + K_PARTNERS + "0"));
				buttons.add(button(new TextComponentTranslation("gui.aw.kingdom.realm"), KINGDOM_PREFIX + K_REALM));
			}
			if (AWDiplomacyStatics.serviceEnabled && AWDiplomacyStatics.siegesEnabled) {
				buttons.add(button(new TextComponentTranslation(owner ? "gui.aw.kingdom.campaign" : "gui.aw.dialogue.propose"), PROPOSE)
						.hover(new TextComponentTranslation(owner ? "gui.aw.kingdom.campaign.hover" : "command.aw.service.click.propose.hover")));
			}
			if (!owner && Service.getWages(player.world, player) > 0) {
				buttons.add(button(Service.describeWages(player.world, player), WAGES).hover(new TextComponentTranslation("gui.aw.dialogue.wages.hover")));
			}
			if (!owner) {
				buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.renounce"), RENOUNCE));
			}
		}
		buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.farewell"), CLOSE));
		sendPage(player, npc, text, buttons.toArray(new Button[0]));
	}

	/**
	 * Pages of the owner of a kingdom: diplomacy with each faction that has cities, the state of the kingdom and its culture.
	 */
	private static void handleKingdomAction(EntityPlayerMP player, NpcFaction npc, String action) {
		MinecraftServer server = player.getServer();
		String kingdom = npc.getFaction();
		if (server == null) {
			sendClose(player);
			return;
		}
		if (action.startsWith(K_PARTNERS)) {
			sendPartnersPage(player, npc, parseId(action.substring(K_PARTNERS.length())));
		} else if (action.startsWith(K_PARTNER)) {
			sendPartnerPage(player, npc, action.substring(K_PARTNER.length()), null);
		} else if (action.startsWith(K_WAR_ASK)) {
			String other = action.substring(K_WAR_ASK.length());
			sendPage(player, npc, new TextComponentTranslation(Kingdoms.isInGrace(player.world, kingdom) ? "gui.aw.kingdom.war.confirm_grace" : "gui.aw.kingdom.war.confirm", FactionMembership.factionName(other)),
					button(new TextComponentTranslation("gui.aw.kingdom.war.yes"), KINGDOM_PREFIX + K_WAR + other),
					button(new TextComponentTranslation("gui.aw.dialogue.no"), KINGDOM_PREFIX + K_PARTNER + other));
		} else if (action.startsWith(K_WAR)) {
			String other = action.substring(K_WAR.length());
			sendPartnerPage(player, npc, other, isPartner(player, kingdom, other) ? KingdomDiplomacy.declareWar(server, kingdom, other) : null);
		} else if (action.startsWith(K_PEACE)) {
			String other = action.substring(K_PEACE.length());
			sendPartnerPage(player, npc, other, isPartner(player, kingdom, other) ? KingdomDiplomacy.proposePeace(server, kingdom, other) : null);
		} else if (action.startsWith(K_ALLY)) {
			String other = action.substring(K_ALLY.length());
			sendPartnerPage(player, npc, other, isPartner(player, kingdom, other) ? KingdomDiplomacy.proposeAlliance(server, kingdom, other) : null);
		} else if (action.startsWith(K_BREAK)) {
			String other = action.substring(K_BREAK.length());
			sendPartnerPage(player, npc, other, isPartner(player, kingdom, other) ? KingdomDiplomacy.breakAlliance(server, kingdom, other) : null);
		} else if (action.equals(K_REALM)) {
			sendRealmPage(player, npc, null);
		} else if (action.startsWith(K_CULTURES)) {
			sendCulturesPage(player, npc, parseId(action.substring(K_CULTURES.length())));
		} else if (action.startsWith(K_CULTURE)) {
			String culture = action.substring(K_CULTURE.length());
			KingdomData.Kingdom data = Kingdoms.get(player.world, kingdom);
			if (data != null && Kingdoms.getCultures().contains(culture)) {
				Kingdoms.setCulture(player.world, data, culture);
				sendRealmPage(player, npc, new TextComponentTranslation("command.aw.kingdom.culture_set", FactionMembership.factionName(culture)));
			} else {
				sendRealmPage(player, npc, null);
			}
		} else {
			sendClose(player);
		}
	}

	private static boolean isPartner(EntityPlayerMP player, String kingdom, String other) {
		return KingdomDiplomacy.getPartners(player.world, kingdom).contains(other);
	}

	private static void sendPartnersPage(EntityPlayerMP player, NpcFaction npc, int page) {
		List<String> partners = KingdomDiplomacy.getPartners(player.world, npc.getFaction());
		if (partners.isEmpty()) {
			sendPage(player, npc, new TextComponentTranslation("gui.aw.kingdom.no_partners"), button(new TextComponentTranslation("gui.aw.dialogue.back"), BACK));
			return;
		}
		int pages = (partners.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		page = Math.max(0, Math.min(pages - 1, page));
		List<Button> buttons = new ArrayList<>();
		for (String other : partners.subList(page * PAGE_SIZE, Math.min(partners.size(), (page + 1) * PAGE_SIZE))) {
			buttons.add(button(KingdomDiplomacy.label(player.world, npc.getFaction(), other), KINGDOM_PREFIX + K_PARTNER + other));
		}
		if (pages > 1) {
			buttons.add(button(new TextComponentTranslation("gui.aw.kingdom.more", page + 1, pages), KINGDOM_PREFIX + K_PARTNERS + ((page + 1) % pages)));
		}
		buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.back"), BACK));
		sendPage(player, npc, new TextComponentTranslation("gui.aw.kingdom.partners"), buttons.toArray(new Button[0]));
	}

	private static void sendPartnerPage(EntityPlayerMP player, NpcFaction npc, String other, @Nullable ITextComponent answer) {
		String kingdom = npc.getFaction();
		if (!isPartner(player, kingdom, other)) {
			sendPartnersPage(player, npc, 0);
			return;
		}
		ITextComponent text = KingdomDiplomacy.describe(player.world, kingdom, other);
		if (answer != null) {
			// only the answer is yellow: siblings take the style of the component they are appended to
			ITextComponent colored = answer.createCopy();
			colored.getStyle().setColor(TextFormatting.YELLOW);
			text = new TextComponentString("").appendSibling(colored).appendText("\n\n").appendSibling(text);
		}
		FactionRelation relation = KingdomDiplomacy.getRelation(player.world, kingdom, other);
		List<Button> buttons = new ArrayList<>();
		if (relation == FactionRelation.WAR) {
			buttons.add(button(new TextComponentTranslation("gui.aw.kingdom.propose_peace"), KINGDOM_PREFIX + K_PEACE + other)
					.hover(new TextComponentTranslation("gui.aw.kingdom.propose_peace.hover", AWDiplomacyStatics.minWarDays, AWDiplomacyStatics.peaceExhaustion)));
		} else if (relation == FactionRelation.ALLIANCE) {
			buttons.add(button(new TextComponentTranslation("gui.aw.kingdom.break_alliance"), KINGDOM_PREFIX + K_BREAK + other));
		} else {
			Button war = button(new TextComponentTranslation("gui.aw.kingdom.declare_war"), KINGDOM_PREFIX + K_WAR_ASK + other);
			war.enabled = KingdomDiplomacy.getTruceDaysLeft(player.world, kingdom, other) == 0 || Kingdoms.isInGrace(player.world, kingdom);
			buttons.add(war.hover(new TextComponentTranslation("gui.aw.kingdom.declare_war.hover")));
			buttons.add(button(new TextComponentTranslation("gui.aw.kingdom.propose_alliance"), KINGDOM_PREFIX + K_ALLY + other)
					.hover(new TextComponentTranslation("gui.aw.kingdom.propose_alliance.hover")));
		}
		buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.back"), KINGDOM_PREFIX + K_PARTNERS + "0"));
		sendPage(player, npc, text, buttons.toArray(new Button[0]));
	}

	private static void sendRealmPage(EntityPlayerMP player, NpcFaction npc, @Nullable ITextComponent answer) {
		KingdomData.Kingdom kingdom = Kingdoms.get(player.world, npc.getFaction());
		if (kingdom == null) {
			sendClose(player);
			return;
		}
		Settlement city = Kingdoms.getCity(player.world, kingdom);
		int wars = 0;
		int alliances = 0;
		for (String other : KingdomDiplomacy.getPartners(player.world, kingdom.id)) {
			FactionRelation relation = KingdomDiplomacy.getRelation(player.world, kingdom.id, other);
			wars += relation == FactionRelation.WAR ? 1 : 0;
			alliances += relation == FactionRelation.ALLIANCE ? 1 : 0;
		}
		ITextComponent text = new TextComponentString(kingdom.name);
		text.appendText("\n").appendSibling(new TextComponentTranslation("gui.aw.kingdom.realm.capital", city == null ? new TextComponentString("-") : Settlements.displayName(city)));
		text.appendText("\n").appendSibling(new TextComponentTranslation("gui.aw.kingdom.realm.manpower", city == null ? 0 : city.getManpower(), city == null ? 0 : city.getMaxManpower()));
		List<Settlement> cities = Kingdoms.getCities(player.world, kingdom);
		int shown = 0;
		for (Settlement other : cities) {
			if (other != city && shown++ < 5) {
				text.appendText("\n").appendSibling(new TextComponentTranslation("gui.aw.kingdom.realm.conquered", Settlements.displayName(other), other.getManpower(), other.getMaxManpower()));
			}
		}
		if (city != null && city.getTributeBonus() > 0) {
			text.appendText("\n").appendSibling(new TextComponentTranslation("gui.aw.kingdom.realm.tribute", city.getTributeBonus()));
		}
		text.appendText("\n").appendSibling(new TextComponentTranslation("gui.aw.kingdom.realm.culture", FactionMembership.factionName(kingdom.culture)));
		text.appendText("\n").appendSibling(new TextComponentTranslation("gui.aw.kingdom.realm.relations", wars, alliances));
		if (answer != null) {
			// only the answer is yellow: siblings take the style of the component they are appended to
			ITextComponent colored = answer.createCopy();
			colored.getStyle().setColor(TextFormatting.YELLOW);
			text = new TextComponentString("").appendSibling(colored).appendText("\n\n").appendSibling(text);
		}
		sendPage(player, npc, text,
				button(new TextComponentTranslation("gui.aw.kingdom.change_culture"), KINGDOM_PREFIX + K_CULTURES + "0")
						.hover(new TextComponentTranslation("gui.aw.kingdom.change_culture.hover")),
				button(new TextComponentTranslation("gui.aw.dialogue.back"), BACK));
	}

	private static void sendCulturesPage(EntityPlayerMP player, NpcFaction npc, int page) {
		KingdomData.Kingdom kingdom = Kingdoms.get(player.world, npc.getFaction());
		List<String> cultures = Kingdoms.getCultures();
		if (kingdom == null || cultures.isEmpty()) {
			sendRealmPage(player, npc, null);
			return;
		}
		int pages = (cultures.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		page = Math.max(0, Math.min(pages - 1, page));
		List<Button> buttons = new ArrayList<>();
		for (String culture : cultures.subList(page * PAGE_SIZE, Math.min(cultures.size(), (page + 1) * PAGE_SIZE))) {
			Button button = button(FactionMembership.factionName(culture), KINGDOM_PREFIX + K_CULTURE + culture);
			button.enabled = !culture.equals(kingdom.culture);
			buttons.add(button);
		}
		if (pages > 1) {
			buttons.add(button(new TextComponentTranslation("gui.aw.kingdom.more", page + 1, pages), KINGDOM_PREFIX + K_CULTURES + ((page + 1) % pages)));
		}
		buttons.add(button(new TextComponentTranslation("gui.aw.dialogue.back"), KINGDOM_PREFIX + K_REALM));
		sendPage(player, npc, new TextComponentTranslation("gui.aw.kingdom.cultures", FactionMembership.factionName(kingdom.culture)), buttons.toArray(new Button[0]));
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
			long rest = Service.getProposalRestTicks(world, player, faction, world.getTotalWorldTime());
			ITextComponent reason;
			if (rest > 0) {
				// enemy cities at war are near, but they rest after their last attack (or their founding)
				reason = new TextComponentTranslation("gui.aw.dialogue.targets_resting", (rest + 1199) / 1200, AWDiplomacyStatics.siegeCooldownDays);
			} else if (FactionRelations.getDeclaredEnemies(world, faction).isEmpty()) {
				reason = new TextComponentTranslation("gui.aw.dialogue.targets_no_war");
			} else {
				reason = new TextComponentTranslation("command.aw.service.propose.none", AWDiplomacyStatics.serviceCallRange);
			}
			sendPage(player, npc, reason,
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
		Button raid = button(new TextComponentTranslation("gui.aw.dialogue.raid", Service.proposalStanding(Siege.Kind.RAID)), PROPOSE_PREFIX + cityId + ":raid")
				.hover(new TextComponentTranslation("command.aw.service.propose.raid.hover", Service.proposalStanding(Siege.Kind.RAID),
						Service.proposalPenalty(Siege.Kind.RAID)));
		boolean siegeAllowed = Service.isFullSiegeAllowed(world, city);
		Button siege = button(siegeAllowed ? new TextComponentTranslation("gui.aw.dialogue.siege", Service.proposalStanding(Siege.Kind.SIEGE))
				: new TextComponentTranslation("gui.aw.dialogue.siege_too_recent"), PROPOSE_PREFIX + cityId + ":siege")
				.hover(new TextComponentTranslation("command.aw.service.propose.siege.hover", Service.proposalStanding(Siege.Kind.SIEGE),
						Service.proposalPenalty(Siege.Kind.SIEGE)));
		siege.enabled = siegeAllowed;
		String faction = npc.getFaction();
		Settlement capital = Kingdoms.isOwner(world, player.getUniqueID(), faction) ? Kingdoms.getCity(world, Kingdoms.get(world, faction)) : null;
		if (capital != null) {
			// the owner of a kingdom pays with the manpower of its capital, not with standing
			int raidCost = Service.campaignCost(world, city, Siege.Kind.RAID, faction);
			int siegeCost = Service.campaignCost(world, city, Siege.Kind.SIEGE, faction);
			raid = button(new TextComponentTranslation("gui.aw.kingdom.campaign.raid", raidCost), PROPOSE_PREFIX + cityId + ":raid")
					.hover(new TextComponentTranslation("gui.aw.kingdom.campaign.cost.hover", raidCost, Settlements.displayName(capital), capital.getManpower()));
			raid.enabled = capital.getManpower() >= raidCost;
			siege = button(siegeAllowed ? new TextComponentTranslation("gui.aw.kingdom.campaign.siege", siegeCost)
					: new TextComponentTranslation("gui.aw.dialogue.siege_too_recent"), PROPOSE_PREFIX + cityId + ":siege")
					.hover(new TextComponentTranslation("gui.aw.kingdom.campaign.cost.hover", siegeCost, Settlements.displayName(capital), capital.getManpower()));
			siege.enabled = siegeAllowed && capital.getManpower() >= siegeCost;
		}
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
		ITextComponent title = Kingdoms.rulerTitle(npc.getFaction());
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
