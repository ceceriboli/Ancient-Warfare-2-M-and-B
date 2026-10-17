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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /awservice call (attack | defend) [player] | strikes (player) (n), for operators: starts a campaign of the military service at once
 * (for testing), sets the strikes of a member.
 */
public class CommandService extends CommandBase {
	private static final String CALL = "call";
	private static final String STRIKES = "strikes";
	private static final String ATTACK = "attack";
	private static final String DEFEND = "defend";

	@Override
	public String getName() {
		return "awservice";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.service.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException(getUsage(sender));
		}
		switch (args[0].toLowerCase(Locale.ROOT)) {
			case CALL: {
				String side = args[1].toLowerCase(Locale.ROOT);
				if (!ATTACK.equals(side) && !DEFEND.equals(side)) {
					throw new WrongUsageException(getUsage(sender));
				}
				EntityPlayerMP player = args.length > 2 ? getPlayer(server, sender, args[2]) : getCommandSenderAsPlayer(sender);
				String faction = FactionMembership.getFaction(player.world, player.getUniqueID());
				if (faction == null) {
					throw new CommandException("command.aw.service.not_member", player.getName());
				}
				WorldServer world = (WorldServer) player.world;
				Siege siege = Service.startCampaign(world, Settlements.getData(world), player, faction, ATTACK.equals(side), world.getTotalWorldTime(), true);
				if (siege == null) {
					throw new CommandException("command.aw.service.no_campaign." + side);
				}
				sender.sendMessage(new TextComponentTranslation("command.aw.service.campaign_started", siege.getId()));
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
			return CommandBase.getListOfStringsMatchingLastWord(args, CALL, STRIKES);
		}
		String cmd = args[0].toLowerCase(Locale.ROOT);
		if (args.length == 2 && cmd.equals(CALL)) {
			return CommandBase.getListOfStringsMatchingLastWord(args, ATTACK, DEFEND);
		}
		if (args.length == 3 && cmd.equals(CALL) || args.length == 2 && cmd.equals(STRIKES)) {
			return CommandBase.getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
		}
		return Collections.emptyList();
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		return args.length > 0 && (index == 2 && args[0].equalsIgnoreCase(CALL) || index == 1 && args[0].equalsIgnoreCase(STRIKES));
	}
}
