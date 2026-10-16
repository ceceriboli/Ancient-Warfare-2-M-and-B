package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelation;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelations;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionRelationData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public class CommandFactionRelation extends CommandBase {
	private static final String RESET = "reset";
	private static final String GET = "get";
	private static final String LIST = "list";

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public String getName() {
		return "awrelation";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.relation.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		String cmd = args[0].toLowerCase(Locale.ROOT);
		if (cmd.equals(LIST)) {
			if (args.length == 1) {
				listSetRelations(sender);
			} else {
				listRelationsOf(sender, validateFaction(args[1]));
			}
			return;
		}
		if (args.length < 3) {
			throw new WrongUsageException(getUsage(sender));
		}
		String faction = validateFaction(args[1]);
		String otherFaction = validateFaction(args[2]);
		if (faction.equals(otherFaction)) {
			throw new CommandException("command.aw.relation.same_faction");
		}

		if (cmd.equals(GET)) {
			showRelation(sender, faction, otherFaction);
		} else if (cmd.equals(RESET)) {
			FactionRelations.setRelation(server, faction, otherFaction, null);
		} else {
			FactionRelation relation = FactionRelation.fromName(cmd);
			if (relation == null) {
				throw new WrongUsageException(getUsage(sender));
			}
			FactionRelations.setRelation(server, faction, otherFaction, relation);
		}
	}

	private String validateFaction(String name) throws CommandException {
		if (!FactionRegistry.getFactionNames().contains(name)) {
			throw new CommandException("command.aw.relation.unknown_faction", name);
		}
		return name;
	}

	private void showRelation(ICommandSender sender, String faction, String otherFaction) {
		World world = sender.getEntityWorld();
		FactionRelation relation = FactionRelations.getSetRelation(world, faction, otherFaction);
		if (relation != null) {
			sender.sendMessage(new TextComponentTranslation("command.aw.relation.get.set", faction, otherFaction, relationName(relation)));
		} else {
			sender.sendMessage(registryRelation(faction, otherFaction));
			sender.sendMessage(registryRelation(otherFaction, faction));
		}
	}

	private void listSetRelations(ICommandSender sender) {
		Map<String, FactionRelation> relations = AWGameData.INSTANCE.getData(sender.getEntityWorld(), FactionRelationData.class).getAll();
		if (relations.isEmpty()) {
			sender.sendMessage(new TextComponentTranslation("command.aw.relation.list.empty"));
			return;
		}
		sender.sendMessage(new TextComponentTranslation("command.aw.relation.list.header"));
		for (Map.Entry<String, FactionRelation> entry : relations.entrySet()) {
			String[] factions = FactionRelationData.splitKey(entry.getKey());
			sender.sendMessage(new TextComponentTranslation("command.aw.relation.get.set", factions[0], factions[1], relationName(entry.getValue())));
		}
	}

	private void listRelationsOf(ICommandSender sender, String faction) {
		World world = sender.getEntityWorld();
		sender.sendMessage(new TextComponentTranslation("command.aw.relation.list.faction.header", faction));
		for (String otherFaction : new TreeSet<>(FactionRegistry.getFactionNames())) {
			if (otherFaction.equals(faction)) {
				continue;
			}
			FactionRelation relation = FactionRelations.getSetRelation(world, faction, otherFaction);
			if (relation != null) {
				sender.sendMessage(new TextComponentTranslation("command.aw.relation.get.set", faction, otherFaction, relationName(relation)));
			} else {
				sender.sendMessage(registryRelation(faction, otherFaction));
			}
		}
	}

	private TextComponentTranslation registryRelation(String faction, String otherFaction) {
		String hostility = FactionRegistry.getFaction(faction).isHostileTowards(otherFaction) ? "command.aw.relation.hostile" : "command.aw.relation.not_hostile";
		return new TextComponentTranslation("command.aw.relation.get.registry", faction, otherFaction, new TextComponentTranslation(hostility));
	}

	private TextComponentTranslation relationName(FactionRelation relation) {
		return new TextComponentTranslation("command.aw.relation.name." + relation.getName());
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			List<String> subcommands = new ArrayList<>();
			for (FactionRelation relation : FactionRelation.values()) {
				subcommands.add(relation.getName());
			}
			Collections.addAll(subcommands, RESET, GET, LIST);
			return CommandBase.getListOfStringsMatchingLastWord(args, subcommands);
		} else if (args.length == 2 || (args.length == 3 && !args[0].equalsIgnoreCase(LIST))) {
			return CommandBase.getListOfStringsMatchingLastWord(args, new TreeSet<>(FactionRegistry.getFactionNames()));
		}
		return Collections.emptyList();
	}
}
