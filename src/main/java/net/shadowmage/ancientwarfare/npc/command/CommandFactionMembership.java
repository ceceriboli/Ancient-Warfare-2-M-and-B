package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.faction.FactionTracker;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * /awmembership join|leave|status for players (also run by the clickable text in leader dialogue),
 * /awmembership set|clear for operators.
 */
public class CommandFactionMembership extends CommandBase {
	private static final String JOIN = "join";
	private static final String LEAVE = "leave";
	private static final String STATUS = "status";
	private static final String SET = "set";
	private static final String CLEAR = "clear";

	@Override
	public String getName() {
		return "awmembership";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		// non operator players can only use commands that skip the permission check, set and clear check it themselves
		return true;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.membership.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		String cmd = args[0].toLowerCase(Locale.ROOT);
		switch (cmd) {
			case JOIN:
				join(server, getCommandSenderAsPlayer(sender), args);
				break;
			case LEAVE:
				leave(server, getCommandSenderAsPlayer(sender));
				break;
			case STATUS:
				status(sender, getCommandSenderAsPlayer(sender));
				break;
			case SET:
				checkOperator(sender);
				if (args.length < 3) {
					throw new WrongUsageException(getUsage(sender));
				}
				EntityPlayerMP target = getPlayer(server, sender, args[1]);
				String faction = validateFaction(args[2]);
				FactionMembership.leave(server, target.getUniqueID(), target.getName(), 0, null);
				FactionMembership.join(server, target, faction, false);
				sender.sendMessage(new TextComponentTranslation("command.aw.membership.set", target.getName(), FactionMembership.factionName(faction)));
				break;
			case CLEAR:
				checkOperator(sender);
				if (args.length < 2) {
					throw new WrongUsageException(getUsage(sender));
				}
				EntityPlayerMP cleared = getPlayer(server, sender, args[1]);
				FactionMembership.leave(server, cleared.getUniqueID(), cleared.getName(), 0, null);
				sender.sendMessage(new TextComponentTranslation("command.aw.membership.cleared", cleared.getName()));
				break;
			default:
				throw new WrongUsageException(getUsage(sender));
		}
	}

	private void join(MinecraftServer server, EntityPlayerMP player, String[] args) throws CommandException {
		if (!AWDiplomacyStatics.membershipEnabled) {
			throw new CommandException("command.aw.membership.disabled");
		}
		if (args.length < 2) {
			throw new WrongUsageException(getUsage(player));
		}
		String faction = validateFaction(args[1]);
		String currentFaction = FactionMembership.getFaction(player.world, player.getUniqueID());
		if (currentFaction != null) {
			throw new CommandException("command.aw.membership.already_member", FactionMembership.factionName(currentFaction));
		}
		int standing = FactionTracker.INSTANCE.getStandingFor(player.world, player.getName(), faction);
		if (standing < AWDiplomacyStatics.joinMinStanding) {
			throw new CommandException("command.aw.membership.standing_too_low", FactionMembership.factionName(faction), AWDiplomacyStatics.joinMinStanding, standing);
		}
		if (!FactionMembership.findNearestLeader(player.world, player, faction, AWDiplomacyStatics.leaderJoinRange).isPresent()) {
			throw new CommandException("command.aw.membership.no_leader_nearby", FactionMembership.factionName(faction), AWDiplomacyStatics.leaderJoinRange);
		}
		FactionMembership.join(server, player, faction, true);
	}

	private void leave(MinecraftServer server, EntityPlayerMP player) throws CommandException {
		String faction = FactionMembership.leave(server, player.getUniqueID(), player.getName(), FactionMembership.RENOUNCE, "command.aw.membership.announce.leave");
		if (faction == null) {
			throw new CommandException("command.aw.membership.not_member");
		}
	}

	private void status(ICommandSender sender, EntityPlayerMP player) {
		String faction = FactionMembership.getFaction(player.world, player.getUniqueID());
		if (faction == null) {
			sender.sendMessage(new TextComponentTranslation("command.aw.membership.status.none"));
		} else {
			int standing = FactionTracker.INSTANCE.getStandingFor(player.world, player.getName(), faction);
			sender.sendMessage(new TextComponentTranslation("command.aw.membership.status.member", FactionMembership.factionName(faction), standing));
		}
	}

	private void checkOperator(ICommandSender sender) throws CommandException {
		if (!sender.canUseCommand(2, getName())) {
			throw new CommandException("commands.generic.permission");
		}
	}

	private String validateFaction(String name) throws CommandException {
		if (!FactionRegistry.getFactionNames().contains(name)) {
			throw new CommandException("command.aw.relation.unknown_faction", name);
		}
		return name;
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, JOIN, LEAVE, STATUS, SET, CLEAR);
		}
		String cmd = args[0].toLowerCase(Locale.ROOT);
		if ((args.length == 2 && cmd.equals(JOIN)) || (args.length == 3 && cmd.equals(SET))) {
			return CommandBase.getListOfStringsMatchingLastWord(args, new TreeSet<>(FactionRegistry.getFactionNames()));
		}
		if (args.length == 2 && (cmd.equals(SET) || cmd.equals(CLEAR))) {
			return CommandBase.getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
		}
		return Collections.emptyList();
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		return index == 1 && args.length > 0 && (args[0].equalsIgnoreCase(SET) || args[0].equalsIgnoreCase(CLEAR));
	}
}
