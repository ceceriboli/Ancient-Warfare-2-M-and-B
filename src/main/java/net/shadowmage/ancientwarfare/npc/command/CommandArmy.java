package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.shadowmage.ancientwarfare.npc.war.Armies;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /awarmy raise | charge | follow | disband | status, for players (raise is also run by the clickable text in the ruler's dialogue)
 */
public class CommandArmy extends CommandBase {
	private static final String RAISE = "raise";
	private static final String CHARGE = "charge";
	private static final String FOLLOW = "follow";
	private static final String DISBAND = "disband";
	private static final String STATUS = "status";

	@Override
	public String getName() {
		return "awarmy";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		// in vanilla non operator players cannot use any command that checks the permission (EntityPlayerMP.canUseCommand)
		return true;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.army.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		EntityPlayerMP player = getCommandSenderAsPlayer(sender);
		switch (args[0].toLowerCase(Locale.ROOT)) {
			case RAISE:
				Armies.raise(player);
				break;
			case CHARGE:
				Armies.charge(player);
				break;
			case FOLLOW:
				Armies.follow(player);
				break;
			case DISBAND:
				Armies.disband(player);
				break;
			case STATUS:
				Armies.status(player);
				break;
			default:
				throw new WrongUsageException(getUsage(sender));
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, RAISE, CHARGE, FOLLOW, DISBAND, STATUS);
		}
		return Collections.emptyList();
	}
}
