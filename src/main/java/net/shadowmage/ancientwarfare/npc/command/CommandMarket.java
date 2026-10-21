package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;
import net.shadowmage.ancientwarfare.npc.war.Goods;
import net.shadowmage.ancientwarfare.npc.war.Markets;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.Settlements;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Operators: /awmarket info [city] shows the market of a city (stock against need, prices of the reference item of each good, treasury);
 * /awmarket set (city) (good) (stock) and /awmarket reset (city) change it, for tests.
 */
public class CommandMarket extends CommandBase {
	private static final String INFO = "info";
	private static final String SET = "set";
	private static final String RESET = "reset";

	@Override
	public String getName() {
		return "awmarket";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.market.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		World world = sender.getEntityWorld();
		SettlementData data = Settlements.getData(world);
		switch (args[0].toLowerCase(Locale.ROOT)) {
			case INFO:
				Settlement city = args.length > 1 ? get(data, args[1]) : data.getActiveAt(world.provider.getDimension(), sender.getPosition(), Settlement.Type.FACTION_CITY);
				if (city == null) {
					throw new CommandException("command.aw.settlement.none_here");
				}
				info(sender, city);
				break;
			case SET:
				if (args.length < 4) {
					throw new WrongUsageException(getUsage(sender));
				}
				Settlement target = get(data, args[1]);
				Goods.Good good = Goods.get(args[2]);
				if (good == null) {
					throw new CommandException("command.aw.market.no_good", args[2]);
				}
				Markets.setStock(target, good, (float) parseDouble(args[3], 0D));
				data.markDirty();
				sender.sendMessage(new TextComponentTranslation("command.aw.market.set", Settlements.displayName(target), good.getId(), args[3]));
				break;
			case RESET:
				if (args.length < 2) {
					throw new WrongUsageException(getUsage(sender));
				}
				Settlement reset = get(data, args[1]);
				for (Goods.Good each : Goods.getAll()) {
					Markets.setStock(reset, each, Markets.getEquilibrium(reset, each));
				}
				data.markDirty();
				sender.sendMessage(new TextComponentTranslation("command.aw.market.reset", Settlements.displayName(reset)));
				break;
			default:
				throw new WrongUsageException(getUsage(sender));
		}
	}

	private static Settlement get(SettlementData data, String id) throws CommandException {
		Settlement settlement = data.get(parseInt(id));
		if (settlement == null) {
			throw new CommandException("command.aw.settlement.not_found", id);
		}
		return settlement;
	}

	private static void info(ICommandSender sender, Settlement city) {
		if (!Markets.hasMarket(city)) {
			sender.sendMessage(new TextComponentTranslation("command.aw.market.none", Settlements.displayName(city)));
			return;
		}
		sender.sendMessage(new TextComponentTranslation("command.aw.market.header", Settlements.displayName(city), Markets.formatMoney(Markets.getCityCopper(city))));
		for (Goods.Good good : Goods.getAll()) {
			int[] prices = Markets.getReferencePrices(city, good);
			sender.sendMessage(new TextComponentTranslation("command.aw.market.line", good.getId(), Math.round(Markets.getStock(city, good)),
					Math.round(Markets.getNeed(city, good)), Math.round(Markets.getEquilibrium(city, good)), good.getItems().get(0).getKey(),
					Markets.formatMoney(prices[0]), Markets.formatMoney(prices[1])));
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, INFO, SET, RESET);
		}
		if (args.length == 3 && SET.equalsIgnoreCase(args[0])) {
			List<String> goods = new ArrayList<>();
			for (Goods.Good good : Goods.getAll()) {
				goods.add(good.getId());
			}
			return getListOfStringsMatchingLastWord(args, goods);
		}
		return Collections.emptyList();
	}
}
