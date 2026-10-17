package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.Settlements;
import net.shadowmage.ancientwarfare.npc.war.Siege;
import net.shadowmage.ancientwarfare.npc.war.Sieges;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /awsiege start (city id) (faction) [raid|siege] | stop (city id) | list, for operators
 */
public class CommandSiege extends CommandBase {
	private static final String START = "start";
	private static final String STOP = "stop";
	private static final String LIST = "list";

	@Override
	public String getName() {
		return "awsiege";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.siege.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		SettlementData data = Settlements.getData(sender.getEntityWorld());
		switch (args[0].toLowerCase(Locale.ROOT)) {
			case START: {
				if (args.length < 3) {
					throw new WrongUsageException(getUsage(sender));
				}
				Settlement settlement = getSettlement(data, args[1]);
				String faction = args[2].toLowerCase(Locale.ROOT);
				WorldServer world = server.getWorld(settlement.getDimension());
				Siege.Kind kind = args.length > 3 ? Siege.Kind.fromName(args[3].toLowerCase(Locale.ROOT)) : Siege.Kind.RAID;
				if (kind == null) {
					throw new WrongUsageException(getUsage(sender));
				}
				String error = Sieges.canStart(world, settlement, faction);
				if (error != null) {
					throw new CommandException(error, faction);
				}
				Siege siege = Sieges.start(world, data, settlement, faction, kind);
				sender.sendMessage(new TextComponentTranslation("command.aw." + kind.getName() + ".started", Settlements.displayName(settlement),
						FactionMembership.factionName(faction), siege.getTotalWaves(), siege.getAttackersPerWave()));
				break;
			}
			case STOP: {
				if (args.length < 2) {
					throw new WrongUsageException(getUsage(sender));
				}
				Settlement settlement = getSettlement(data, args[1]);
				Siege siege = Sieges.getSiegeOf(server.getWorld(settlement.getDimension()), settlement);
				if (siege == null) {
					throw new CommandException("command.aw.siege.not_besieged");
				}
				Sieges.stop(server.getWorld(settlement.getDimension()), data, siege);
				sender.sendMessage(new TextComponentTranslation("command.aw.siege.stopped", Settlements.displayName(settlement)));
				break;
			}
			case LIST:
				list(sender, data);
				break;
			default:
				throw new WrongUsageException(getUsage(sender));
		}
	}

	private Settlement getSettlement(SettlementData data, String id) throws CommandException {
		Settlement settlement = data.get(parseInt(id));
		if (settlement == null) {
			throw new CommandException("command.aw.settlement.not_found", id);
		}
		return settlement;
	}

	private void list(ICommandSender sender, SettlementData data) {
		if (data.getSieges().isEmpty()) {
			sender.sendMessage(new TextComponentTranslation("command.aw.siege.list.empty"));
			return;
		}
		for (Siege siege : data.getSieges()) {
			Settlement settlement = data.get(siege.getSettlementId());
			Object city = settlement == null ? "#" + siege.getSettlementId() : Settlements.displayName(settlement);
			sender.sendMessage(new TextComponentTranslation("command.aw.siege.list.entry", siege.getId(), siege.getKind().getName(), city,
					FactionMembership.factionName(siege.getAttackerFaction()),
					siege.getState().getName(), siege.getWavesSpawned(), siege.getTotalWaves(), siege.getLoadedAttackers().size()));
			sender.sendMessage(new TextComponentTranslation("command.aw.siege.list.timers", siege.getIdleTicks() / 20, AWDiplomacyStatics.siegeWaveStallTicks / 20,
					siege.getTotalTicks() / 20, AWDiplomacyStatics.siegeMaxTicks / 20, siege.isSuspended() ? new TextComponentTranslation("command.aw.siege.list.suspended") : ""));
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, START, STOP, LIST);
		}
		if (args.length == 3 && START.equalsIgnoreCase(args[0])) {
			return CommandBase.getListOfStringsMatchingLastWord(args, FactionRegistry.getFactionNames());
		}
		if (args.length == 4 && START.equalsIgnoreCase(args[0])) {
			return CommandBase.getListOfStringsMatchingLastWord(args, Siege.Kind.RAID.getName(), Siege.Kind.SIEGE.getName());
		}
		return Collections.emptyList();
	}
}
