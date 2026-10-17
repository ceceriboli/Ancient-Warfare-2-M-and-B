package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.faction.Diplomacy;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelation;
import net.shadowmage.ancientwarfare.npc.gamedata.DiplomacyData;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionRelationData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * /awdiplomacy status [faction] | day | exhaustion (faction) (enemy) (value), for operators
 */
public class CommandDiplomacy extends CommandBase {
	private static final String STATUS = "status";
	private static final String DAY = "day";
	private static final String EXHAUSTION = "exhaustion";

	@Override
	public String getName() {
		return "awdiplomacy";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.diplomacy.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		WorldServer overworld = server.getWorld(0);
		switch (args[0].toLowerCase(Locale.ROOT)) {
			case STATUS:
				if (args.length > 1) {
					status(sender, overworld, checkFaction(args[1]));
				} else {
					summary(sender, overworld);
				}
				break;
			case DAY:
				Diplomacy.runDay(overworld);
				sender.sendMessage(new TextComponentTranslation("command.aw.diplomacy.day_done"));
				break;
			case EXHAUSTION:
				if (args.length < 4) {
					throw new WrongUsageException(getUsage(sender));
				}
				String faction = checkFaction(args[1]);
				String enemy = checkFaction(args[2]);
				int value = parseInt(args[3], 0);
				Diplomacy.setExhaustion(overworld, faction, enemy, value);
				sender.sendMessage(new TextComponentTranslation("command.aw.diplomacy.exhaustion_set", FactionMembership.factionName(faction),
						FactionMembership.factionName(enemy), value));
				break;
			default:
				throw new WrongUsageException(getUsage(sender));
		}
	}

	private String checkFaction(String name) throws CommandException {
		String faction = name.toLowerCase(Locale.ROOT);
		if (!FactionRegistry.getFactionNames().contains(faction)) {
			throw new CommandException("command.aw.diplomacy.unknown_faction", name);
		}
		return faction;
	}

	/**
	 * Wars, truces and alliances of the factions with cities (the others are counted only).
	 */
	private void summary(ICommandSender sender, WorldServer world) {
		FactionRelationData relations = AWGameData.INSTANCE.getData(world, FactionRelationData.class);
		int wars = 0;
		int alliances = 0;
		for (FactionRelation relation : relations.getAll().values()) {
			wars += relation == FactionRelation.WAR ? 1 : 0;
			alliances += relation == FactionRelation.ALLIANCE ? 1 : 0;
		}
		Set<String> withCities = new TreeSet<>(Diplomacy.getFactionsWithCities(world));
		sender.sendMessage(new TextComponentTranslation("command.aw.diplomacy.summary", wars, alliances, Diplomacy.getDay(world), withCities.size()));
		for (String faction : withCities) {
			status(sender, world, faction);
		}
	}

	private void status(ICommandSender sender, WorldServer world, String faction) {
		FactionRelationData relations = AWGameData.INSTANCE.getData(world, FactionRelationData.class);
		DiplomacyData data = Diplomacy.getData(world);
		Set<String> withCities = Diplomacy.getFactionsWithCities(world);
		long day = Diplomacy.getDay(world);
		sender.sendMessage(new TextComponentTranslation("command.aw.diplomacy.status.header", FactionMembership.factionName(faction),
				withCities.contains(faction) ? new TextComponentTranslation("command.aw.diplomacy.has_cities") : new TextComponentTranslation("command.aw.diplomacy.no_cities"),
				Diplomacy.isEternalEnemy(faction) ? new TextComponentTranslation("command.aw.diplomacy.eternal") : ""));
		int otherWars = 0;
		for (Map.Entry<String, FactionRelation> e : relations.getAll().entrySet()) {
			String[] pair = FactionRelationData.splitKey(e.getKey());
			if (!pair[0].equals(faction) && !pair[1].equals(faction)) {
				continue;
			}
			String other = pair[0].equals(faction) ? pair[1] : pair[0];
			DiplomacyData.Entry entry = data.get(faction, other);
			if (e.getValue() == FactionRelation.WAR) {
				if (!withCities.contains(other)) {
					otherWars++;
					continue;
				}
				sender.sendMessage(new TextComponentTranslation("command.aw.diplomacy.status.war", FactionMembership.factionName(other), day - entry.getSince(),
						entry.getExhaustion(faction, other), entry.getExhaustion(other, faction)));
			} else if (e.getValue() == FactionRelation.ALLIANCE) {
				sender.sendMessage(new TextComponentTranslation("command.aw.diplomacy.status.alliance", FactionMembership.factionName(other), day - entry.getSince()));
			} else if (entry.getTruceUntil() > day) {
				sender.sendMessage(new TextComponentTranslation("command.aw.diplomacy.status.truce", FactionMembership.factionName(other), entry.getTruceUntil() - day));
			}
		}
		if (otherWars > 0) {
			sender.sendMessage(new TextComponentTranslation("command.aw.diplomacy.status.other_wars", otherWars));
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, STATUS, DAY, EXHAUSTION);
		}
		if (args.length == 2 && (STATUS.equalsIgnoreCase(args[0]) || EXHAUSTION.equalsIgnoreCase(args[0]))
				|| args.length == 3 && EXHAUSTION.equalsIgnoreCase(args[0])) {
			return CommandBase.getListOfStringsMatchingLastWord(args, FactionRegistry.getFactionNames());
		}
		return Collections.emptyList();
	}
}
