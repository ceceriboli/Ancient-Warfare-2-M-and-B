package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.shadowmage.ancientwarfare.npc.war.Armies;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /awarmy status, for players: the player's own soldiers in the assault of an attack of its faction (they join it on their own, see
 * Armies and Service)
 */
public class CommandArmy extends CommandBase {
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
		if (args.length > 0 && !STATUS.equals(args[0].toLowerCase(Locale.ROOT))) {
			throw new WrongUsageException(getUsage(sender));
		}
		Armies.status(getCommandSenderAsPlayer(sender));
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, STATUS);
		}
		return Collections.emptyList();
	}
}
