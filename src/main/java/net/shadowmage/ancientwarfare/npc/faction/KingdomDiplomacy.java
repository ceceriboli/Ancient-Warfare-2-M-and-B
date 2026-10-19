package net.shadowmage.ancientwarfare.npc.faction;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.gamedata.DiplomacyData;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionRelationData;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The diplomacy of a player kingdom, ordered by its owner at the governor: war is declared freely (outside of a truce), peace and alliances
 * are accepted by the other faction with the rules of the automatic diplomacy (no randomness). Each method returns the governor's answer.
 */
public class KingdomDiplomacy {
	private KingdomDiplomacy() {}

	/**
	 * @return the factions with active cities the kingdom can deal with, sorted by id
	 */
	public static List<String> getPartners(World world, String kingdom) {
		List<String> ret = new ArrayList<>();
		for (String faction : new TreeSet<>(Diplomacy.getFactionsWithCities(world))) {
			if (!faction.equals(kingdom) && FactionRegistry.getFactionNames().contains(faction)) {
				ret.add(faction);
			}
		}
		return ret;
	}

	private static FactionRelationData relations(World world) {
		return AWGameData.INSTANCE.getData(world, FactionRelationData.class);
	}

	public static FactionRelation getRelation(World world, String kingdom, String other) {
		FactionRelation relation = relations(world).get(kingdom, other);
		if (relation != null) {
			return relation;
		}
		return FactionRelations.isHostile(world, kingdom, other) || FactionRelations.isHostile(world, other, kingdom) ? FactionRelation.WAR : FactionRelation.PEACE;
	}

	/**
	 * @return days left of the truce between the two factions, 0 if none
	 */
	public static long getTruceDaysLeft(World world, String kingdom, String other) {
		return Math.max(0L, Diplomacy.getData(world).get(kingdom, other).getTruceUntil() - Diplomacy.getDay(world));
	}

	/**
	 * @return "at war since 3 days, exhaustion 20 (yours) / 35 (theirs)" and the like
	 */
	public static ITextComponent describe(World world, String kingdom, String other) {
		FactionRelation relation = getRelation(world, kingdom, other);
		DiplomacyData.Entry entry = Diplomacy.getData(world).get(kingdom, other);
		long truce = getTruceDaysLeft(world, kingdom, other);
		ITextComponent text;
		if (relation == FactionRelation.WAR) {
			text = new TextComponentTranslation("gui.aw.kingdom.relation.war", FactionMembership.factionName(other), Math.max(0L, Diplomacy.getDay(world) - entry.getSince()),
					entry.getExhaustion(kingdom, other), entry.getExhaustion(other, kingdom), AWDiplomacyStatics.peaceExhaustion);
		} else if (relation == FactionRelation.ALLIANCE) {
			text = new TextComponentTranslation("gui.aw.kingdom.relation.alliance", FactionMembership.factionName(other));
		} else {
			text = new TextComponentTranslation("gui.aw.kingdom.relation.peace", FactionMembership.factionName(other));
		}
		if (truce > 0) {
			text.appendText(" ").appendSibling(new TextComponentTranslation("gui.aw.kingdom.relation.truce", truce));
		}
		if (Diplomacy.isEternalEnemy(other)) {
			text.appendText(" ").appendSibling(new TextComponentTranslation("gui.aw.kingdom.relation.eternal"));
		}
		return text;
	}

	/**
	 * @return a short label for the list of factions: "Norska: war"
	 */
	public static ITextComponent label(World world, String kingdom, String other) {
		FactionRelation relation = getRelation(world, kingdom, other);
		ITextComponent state = new TextComponentTranslation("gui.aw.kingdom.state." + relation.getName());
		state.getStyle().setColor(relation == FactionRelation.WAR ? TextFormatting.RED : relation == FactionRelation.ALLIANCE ? TextFormatting.AQUA : TextFormatting.GREEN);
		return new TextComponentTranslation("gui.aw.kingdom.partner", FactionMembership.factionName(other), state);
	}

	public static boolean canDeclareWar(World world, String kingdom, String other) {
		return getRelation(world, kingdom, other) == FactionRelation.PEACE;
	}

	public static ITextComponent declareWar(MinecraftServer server, String kingdom, String other) {
		World world = server.getEntityWorld();
		FactionRelation relation = getRelation(world, kingdom, other);
		if (relation == FactionRelation.WAR) {
			return answer("gui.aw.kingdom.answer.already_war", other);
		}
		if (relation == FactionRelation.ALLIANCE) {
			return answer("gui.aw.kingdom.answer.allied", other);
		}
		long truce = getTruceDaysLeft(world, kingdom, other);
		boolean grace = Kingdoms.isInGrace(world, kingdom);
		if (truce > 0 && !grace) {
			return answer("gui.aw.kingdom.answer.truce", other, truce);
		}
		if (truce > 0) {
			// the starting truce can be broken: the capital loses its protection
			DiplomacyData data = Diplomacy.getData(world);
			data.get(kingdom, other).setTruceUntil(Diplomacy.getDay(world));
			data.markDirty();
		}
		FactionRelations.setRelation(server, kingdom, other, FactionRelation.WAR, "command.aw.kingdom.announce.war");
		AncientWarfareNPC.LOG.info("Kingdom {} declared war on {}{}", kingdom, other, grace ? " (grace period over)" : "");
		ITextComponent done = answer("gui.aw.kingdom.answer.war_declared", other);
		if (grace) {
			Kingdoms.endGrace(world, kingdom);
			done.appendText(" ").appendSibling(new TextComponentTranslation("gui.aw.kingdom.answer.grace_ended"));
		}
		return done;
	}

	public static ITextComponent proposePeace(MinecraftServer server, String kingdom, String other) {
		World world = server.getEntityWorld();
		if (getRelation(world, kingdom, other) != FactionRelation.WAR) {
			return answer("gui.aw.kingdom.answer.not_war", other);
		}
		if (Diplomacy.isEternalEnemy(other)) {
			return answer("gui.aw.kingdom.answer.eternal", other);
		}
		DiplomacyData.Entry entry = Diplomacy.getData(world).get(kingdom, other);
		long days = Diplomacy.getDay(world) - entry.getSince();
		if (days < AWDiplomacyStatics.minWarDays) {
			return answer("gui.aw.kingdom.answer.war_too_young", other, AWDiplomacyStatics.minWarDays - days);
		}
		if (!Diplomacy.canMakePeace(Diplomacy.getData(world), kingdom, other, Diplomacy.getDay(world))) {
			return answer("gui.aw.kingdom.answer.not_exhausted", other, entry.getMaxExhaustion(), AWDiplomacyStatics.peaceExhaustion);
		}
		Diplomacy.makePeace(server, world, kingdom, other, "command.aw.kingdom.announce.peace");
		AncientWarfareNPC.LOG.info("Kingdom {} made peace with {}", kingdom, other);
		return answer("gui.aw.kingdom.answer.peace_made", other, AWDiplomacyStatics.truceDays);
	}

	public static ITextComponent proposeAlliance(MinecraftServer server, String kingdom, String other) {
		World world = server.getEntityWorld();
		FactionRelation relation = getRelation(world, kingdom, other);
		if (relation == FactionRelation.ALLIANCE) {
			return answer("gui.aw.kingdom.answer.already_allied", other);
		}
		if (relation == FactionRelation.WAR) {
			return answer("gui.aw.kingdom.answer.at_war", other);
		}
		if (Diplomacy.isEternalEnemy(other)) {
			return answer("gui.aw.kingdom.answer.eternal", other);
		}
		long truce = getTruceDaysLeft(world, kingdom, other);
		if (truce > 0) {
			return answer("gui.aw.kingdom.answer.truce_alliance", other, truce);
		}
		FactionRelationData relations = relations(world);
		List<String> factions = new ArrayList<>(FactionRegistry.getFactionNames());
		if (Diplomacy.countAlliances(relations, kingdom, factions) >= AWDiplomacyStatics.maxAlliances
				|| Diplomacy.countAlliances(relations, other, factions) >= AWDiplomacyStatics.maxAlliances) {
			return answer("gui.aw.kingdom.answer.too_many_alliances", other, AWDiplomacyStatics.maxAlliances);
		}
		Set<String> withCities = Diplomacy.getFactionsWithCities(world);
		String enemy = Diplomacy.findCommonEnemy(relations, kingdom, other, withCities);
		if (enemy == null) {
			return answer("gui.aw.kingdom.answer.no_common_enemy", other);
		}
		FactionRelations.setRelation(server, kingdom, other, FactionRelation.ALLIANCE, "command.aw.diplomacy.announce.alliance", FactionMembership.factionName(enemy));
		AncientWarfareNPC.LOG.info("Kingdom {} allied with {} against {}", kingdom, other, enemy);
		return new TextComponentTranslation("gui.aw.kingdom.answer.allied_now", FactionMembership.factionName(other), FactionMembership.factionName(enemy));
	}

	public static ITextComponent breakAlliance(MinecraftServer server, String kingdom, String other) {
		World world = server.getEntityWorld();
		if (getRelation(world, kingdom, other) != FactionRelation.ALLIANCE) {
			return answer("gui.aw.kingdom.answer.not_allied", other);
		}
		FactionRelations.setRelation(server, kingdom, other, FactionRelation.PEACE, "command.aw.diplomacy.announce.alliance_end");
		DiplomacyData data = Diplomacy.getData(world);
		data.get(kingdom, other).setTruceUntil(Diplomacy.getDay(world) + AWDiplomacyStatics.truceDays);
		data.markDirty();
		return answer("gui.aw.kingdom.answer.alliance_broken", other, AWDiplomacyStatics.truceDays);
	}

	private static ITextComponent answer(String key, String other, Object... args) {
		Object[] all = new Object[args.length + 1];
		all[0] = FactionMembership.factionName(other);
		System.arraycopy(args, 0, all, 1, args.length);
		return new TextComponentTranslation(key, all);
	}
}
