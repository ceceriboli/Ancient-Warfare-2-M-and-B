package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;
import net.shadowmage.ancientwarfare.npc.gamedata.KingdomData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.Settlements;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * /awkingdom info | rename (name...) | culture (faction) for the owner of a kingdom; /awkingdom debugcreate [culture] for operators
 * creates a kingdom without a city (for testing the kingdom factions).
 */
public class CommandKingdom extends CommandBase {
	private static final String INFO = "info";
	private static final String RENAME = "rename";
	private static final String CULTURE = "culture";
	private static final String DEBUG_CREATE = "debugcreate";
	private static final String CITY_NAME = "cityname";
	private static final String DISBAND = "disband";
	private static final String CONFIRM = "confirm";
	private static final int MAX_NAME_LENGTH = 32;

	@Override
	public String getName() {
		return "awkingdom";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		// open to every player, debugcreate checks the operator level itself
		return true;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.kingdom.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		EntityPlayerMP player = getCommandSenderAsPlayer(sender);
		World world = server.getEntityWorld();
		String cmd = args[0].toLowerCase(Locale.ROOT);
		if (cmd.equals(DEBUG_CREATE)) {
			if (!sender.canUseCommand(2, getName())) {
				throw new CommandException("commands.generic.permission");
			}
			if (Kingdoms.getOwnedBy(world, player.getUniqueID()) != null) {
				throw new CommandException("command.aw.kingdom.already_owner");
			}
			String culture = args.length > 1 ? args[1] : AWDiplomacyStatics.kingdomDefaultCulture;
			checkCulture(culture);
			KingdomData.Kingdom kingdom = Kingdoms.create(world, player, culture);
			sender.sendMessage(new TextComponentTranslation("command.aw.kingdom.created", kingdom.name, kingdom.id, FactionMembership.factionName(culture)));
			return;
		}
		KingdomData.Kingdom kingdom = Kingdoms.getOwnedBy(world, player.getUniqueID());
		if (kingdom == null) {
			throw new CommandException("command.aw.kingdom.no_kingdom");
		}
		switch (cmd) {
			case INFO:
				Settlement capital = Kingdoms.getCity(world, kingdom);
				sender.sendMessage(new TextComponentTranslation("command.aw.kingdom.info", kingdom.name, kingdom.id, FactionMembership.factionName(kingdom.culture),
						capital == null ? new net.minecraft.util.text.TextComponentString("-") : Settlements.displayName(capital)));
				break;
			case RENAME:
				if (args.length < 2) {
					throw new WrongUsageException(getUsage(sender));
				}
				String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
				if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
					throw new CommandException("command.aw.kingdom.bad_name", MAX_NAME_LENGTH);
				}
				Kingdoms.rename(world, kingdom, name);
				sender.sendMessage(new TextComponentTranslation("command.aw.kingdom.renamed", name));
				break;
			case CITY_NAME:
				if (args.length < 2) {
					throw new WrongUsageException(getUsage(sender));
				}
				Settlement city = Kingdoms.getCity(world, kingdom);
				if (city == null) {
					throw new CommandException("command.aw.kingdom.no_city");
				}
				String cityName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
				if (cityName.isEmpty() || cityName.length() > MAX_NAME_LENGTH) {
					throw new CommandException("command.aw.kingdom.bad_name", MAX_NAME_LENGTH);
				}
				for (Settlement other : Settlements.getData(world).getAll()) {
					if (other != city && other.getName().equalsIgnoreCase(cityName)) {
						throw new CommandException("command.aw.settlement.rename.taken", cityName);
					}
				}
				city.setName(cityName);
				Settlements.getData(world).markDirty();
				sender.sendMessage(new TextComponentTranslation("command.aw.kingdom.city_renamed", cityName));
				break;
			case DISBAND:
				if (args.length < 2 || !CONFIRM.equalsIgnoreCase(args[1])) {
					throw new CommandException("command.aw.kingdom.disband_confirm");
				}
				Kingdoms.disband(server, kingdom);
				break;
			case CULTURE:
				if (args.length < 2) {
					throw new WrongUsageException(getUsage(sender));
				}
				checkCulture(args[1]);
				Kingdoms.setCulture(world, kingdom, args[1]);
				sender.sendMessage(new TextComponentTranslation("command.aw.kingdom.culture_set", FactionMembership.factionName(args[1])));
				break;
			default:
				throw new WrongUsageException(getUsage(sender));
		}
	}

	private static void checkCulture(String culture) throws CommandException {
		if (!Kingdoms.isValidCulture(culture)) {
			throw new CommandException("command.aw.kingdom.bad_culture", culture);
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, INFO, RENAME, CITY_NAME, CULTURE, DISBAND, DEBUG_CREATE);
		}
		if (args.length == 2 && (args[0].equalsIgnoreCase(CULTURE) || args[0].equalsIgnoreCase(DEBUG_CREATE))) {
			return CommandBase.getListOfStringsMatchingLastWord(args, new TreeSet<>(FactionRegistry.getFactionNames().stream()
					.filter(Kingdoms::isValidCulture).collect(Collectors.toList())));
		}
		return Collections.emptyList();
	}
}
