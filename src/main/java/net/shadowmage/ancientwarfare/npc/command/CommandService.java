package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.faction.Diplomacy;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionMembershipData;
import net.shadowmage.ancientwarfare.npc.war.Service;
import net.shadowmage.ancientwarfare.npc.war.Settlements;
import net.shadowmage.ancientwarfare.npc.war.Siege;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /awservice propose [(city id) (raid | siege)] for players (also run by the clickable texts of the ruler's dialogue): proposes a campaign
 * to the ruler of the player's faction. For operators: /awservice call (attack | defend) [player] starts a campaign at once (for testing),
 * /awservice strikes (player) (n) sets the strikes of a member.
 */
public class CommandService extends CommandBase {
	private static final String PROPOSE = "propose";
	private static final String CALL = "call";
	private static final String STRIKES = "strikes";
	private static final String ATTACK = "attack";
	private static final String DEFEND = "defend";
	private static final String RAID = "raid";
	private static final String SIEGE = "siege";

	@Override
	public String getName() {
		return "awservice";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		// non operator players can only use commands that skip the permission check, call and strikes check it themselves
		return true;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.service.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		String cmd = args[0].toLowerCase(Locale.ROOT);
		if (cmd.equals(PROPOSE)) {
			EntityPlayerMP player = getCommandSenderAsPlayer(sender);
			if (args.length < 3) {
				Service.listProposals(player);
			} else {
				Siege.Kind kind = Siege.Kind.fromName(args[2].toLowerCase(Locale.ROOT));
				if (kind == null) {
					throw new WrongUsageException(getUsage(sender));
				}
				Service.proposeInChat(player, parseInt(args[1]), kind);
			}
			return;
		}
		if (cmd.equals("wages")) {
			EntityPlayerMP player = getCommandSenderAsPlayer(sender);
			player.sendMessage(Service.payWages(player));
			return;
		}
		if (!sender.canUseCommand(2, getName())) {
			throw new CommandException("commands.generic.permission");
		}
		if (args.length < 2) {
			throw new WrongUsageException(getUsage(sender));
		}
		switch (cmd) {
			case CALL: {
				String side = args[1].toLowerCase(Locale.ROOT);
				if (!ATTACK.equals(side) && !DEFEND.equals(side)) {
					throw new WrongUsageException(getUsage(sender));
				}
				// optional raid | siege, then optional player
				Siege.Kind kind = args.length > 2 ? Siege.Kind.fromName(args[2].toLowerCase(Locale.ROOT)) : null;
				int playerIndex = kind != null ? 3 : 2;
				EntityPlayerMP player = args.length > playerIndex ? getPlayer(server, sender, args[playerIndex]) : getCommandSenderAsPlayer(sender);
				String faction = FactionMembership.getFaction(player.world, player.getUniqueID());
				if (faction == null) {
					throw new CommandException("command.aw.service.not_member", player.getName());
				}
				WorldServer world = (WorldServer) player.world;
				Siege siege = Service.startCampaign(world, Settlements.getData(world), player, faction, ATTACK.equals(side), world.getTotalWorldTime(), true, kind);
				if (siege == null) {
					throw new CommandException("command.aw.service.no_campaign." + side);
				}
				sender.sendMessage(new TextComponentTranslation("command.aw.service.campaign_started", siege.getId(), siege.getKind().getName()));
				break;
			}
			case STRIKES: {
				if (args.length < 3) {
					throw new WrongUsageException(getUsage(sender));
				}
				EntityPlayerMP player = getPlayer(server, sender, args[1]);
				FactionMembershipData data = AWGameData.INSTANCE.getData(player.world, FactionMembershipData.class);
				if (data.getMember(player.getUniqueID()) == null) {
					throw new CommandException("command.aw.service.not_member", player.getName());
				}
				int strikes = parseInt(args[2], 0);
				data.setStrikes(player.getUniqueID(), strikes, Diplomacy.getDay(player.world));
				sender.sendMessage(new TextComponentTranslation("command.aw.service.strikes_set", player.getName(), strikes));
				break;
			}
			default:
				throw new WrongUsageException(getUsage(sender));
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, PROPOSE, CALL, STRIKES);
		}
		String cmd = args[0].toLowerCase(Locale.ROOT);
		if (args.length == 2 && cmd.equals(CALL)) {
			return CommandBase.getListOfStringsMatchingLastWord(args, ATTACK, DEFEND);
		}
		if (args.length == 3 && cmd.equals(CALL)) {
			List<String> options = new ArrayList<>(Arrays.asList(RAID, SIEGE));
			options.addAll(Arrays.asList(server.getOnlinePlayerNames()));
			return CommandBase.getListOfStringsMatchingLastWord(args, options);
		}
		if (args.length == 4 && cmd.equals(CALL) || args.length == 2 && cmd.equals(STRIKES)) {
			return CommandBase.getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
		}
		return Collections.emptyList();
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		if (args.length > 0 && args[0].equalsIgnoreCase(CALL)) {
			boolean withKind = args.length > 2 && Siege.Kind.fromName(args[2].toLowerCase(Locale.ROOT)) != null;
			return index == (withKind ? 3 : 2);
		}
		return index == 1 && args.length > 0 && args[0].equalsIgnoreCase(STRIKES);
	}
}
