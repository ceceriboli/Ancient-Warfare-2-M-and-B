package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFactionTrader;
import net.shadowmage.ancientwarfare.npc.faction.FactionTracker;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;
import net.shadowmage.ancientwarfare.npc.network.PacketMarket;
import net.shadowmage.ancientwarfare.npc.trade.FactionTrade;
import net.shadowmage.ancientwarfare.npc.trade.FactionTradeList;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The markets of the cities, in the manner of Mount & Blade: a city holds goods (Market) and coins (its treasury); a faction trader standing
 * in the city opens its market window. The price of an item is its value x a factor of the stock of its good against what the city needs
 * (little stock: dear, much stock: cheap), recomputed for every single unit, plus a margin when you buy and minus one when you sell (larger
 * for weapons and armor). The city pays only with the coins of its treasury and buys a good only up to market_max_stock_ratio times what it
 * needs. Every day the stock moves back toward what the city's culture holds. The items of the trader's old trade list that are not market
 * goods are sold as specials, at special_price_multiplier times their old price. Player kingdoms have no market.
 */
public final class Markets {
	private static final int MAX_ROW_UNITS = 64;
	private static final double TRADE_RANGE_SQ = 10 * 10;
	private static final double PRICE_EXPONENT = 0.5D;
	private static final float MIN_FACTOR = 0.5F;
	private static final float MAX_FACTOR = 3.0F;
	private static final String GOOD_ROW = "g:";
	private static final String SPECIAL_ROW = "s:";
	private static final String PLAYER_ROW = "p:";
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private Markets() {}

	private static final class Session {
		private final int traderId;
		private final int cityId;

		private Session(int traderId, int cityId) {
			this.traderId = traderId;
			this.cityId = cityId;
		}
	}

	// ------------------------------------------------------------------ data

	public static boolean hasMarket(Settlement city) {
		return AWDiplomacyStatics.marketEnabled && city.isActive() && city.getType() == Settlement.Type.FACTION_CITY && !Kingdoms.isKingdom(city.getFaction());
	}

	/**
	 * @return the market of the city; goods it has not held yet start at their equilibrium
	 */
	static Market getMarket(Settlement city) {
		Market market = city.getMarket();
		if (market == null) {
			market = new Market();
			city.setMarket(market);
		}
		for (Goods.Good good : Goods.getAll()) {
			if (!market.has(good.getId())) {
				market.setStock(good.getId(), getEquilibrium(city, good));
			}
		}
		return market;
	}

	/**
	 * @return what the city needs of a good: its demand per 100 maximum manpower (its size), at least market_min_need
	 */
	public static float getNeed(Settlement city, Goods.Good good) {
		return Math.max(AWDiplomacyStatics.marketMinNeed, good.getDemand() * city.getBaseMaxManpower() / 100F);
	}

	/**
	 * @return the stock the market drifts to: the need x the culture's factor for the good
	 */
	public static float getEquilibrium(Settlement city, Goods.Good good) {
		return getNeed(city, good) * Goods.getCultureFactor(city.getFaction(), good.getId());
	}

	public static float getPriceFactor(float stock, float need) {
		double factor = Math.pow(need / Math.max(stock, 0.1F * need), PRICE_EXPONENT);
		return (float) MathHelper.clamp(factor, MIN_FACTOR, MAX_FACTOR);
	}

	static float getMaxStock(Settlement city, Goods.Good good) {
		return getNeed(city, good) * (float) AWDiplomacyStatics.marketMaxStockRatio;
	}

	/**
	 * Every day the stock of every good moves market_equilibrium_speed of the way back to its equilibrium (production and consumption)
	 */
	static void onDays(Settlement city, long days) {
		if (!hasMarket(city) || city.getMarket() == null || days <= 0) {
			return;
		}
		Market market = getMarket(city);
		double keep = Math.pow(1D - AWDiplomacyStatics.marketEquilibriumSpeed, Math.min(days, 365));
		for (Goods.Good good : Goods.getAll()) {
			float stock = market.getStock(good.getId());
			float equilibrium = getEquilibrium(city, good);
			market.setStock(good.getId(), (float) (equilibrium + (stock - equilibrium) * keep));
		}
	}

	/**
	 * A plundered or conquered city loses half of its stock
	 */
	static void onPlundered(Settlement city) {
		Market market = city.getMarket();
		if (market == null) {
			return;
		}
		for (Goods.Good good : Goods.getAll()) {
			market.setStock(good.getId(), market.getStock(good.getId()) / 2F);
		}
	}

	/**
	 * @return the stock of a good in the city's market (operator commands)
	 */
	public static float getStock(Settlement city, Goods.Good good) {
		return getMarket(city).getStock(good.getId());
	}

	public static void setStock(Settlement city, Goods.Good good, float stock) {
		getMarket(city).setStock(good.getId(), stock);
	}

	/**
	 * @return the price of one reference item of a good, to buy and to sell, at the current stock and without discount (operator commands)
	 */
	public static int[] getReferencePrices(Settlement city, Goods.Good good) {
		Goods.ItemEntry entry = good.getItems().get(0);
		float stock = getStock(city, good);
		float need = getNeed(city, good);
		return new int[] {getBuyPrice(entry, stock, need, 0F), getSellPrice(entry, stock, need, 0F, 1F)};
	}

	public static int getCityCopper(Settlement city) {
		return city.getTreasury() * Coins.value(Coins.getMetal()) + city.getTreasuryCopper();
	}

	static void setCityCopper(Settlement city, int copper) {
		int coinValue = Math.max(1, Coins.value(Coins.getMetal()));
		city.setTreasury(copper / coinValue);
		city.setTreasuryCopper(copper % coinValue);
	}

	// ------------------------------------------------------------------ prices

	/**
	 * @return the share of the margins taken off for the player's standing with the faction (up to market_standing_discount)
	 */
	static float getDiscount(World world, EntityPlayer player, String faction) {
		int standing = FactionTracker.INSTANCE.getStandingFor(world, player.getName(), faction);
		if (standing <= 0 || AWDiplomacyStatics.marketDiscountStanding <= 0) {
			return 0F;
		}
		return (float) (Math.min(1D, standing / (double) AWDiplomacyStatics.marketDiscountStanding) * AWDiplomacyStatics.marketStandingDiscount);
	}

	static int getBuyPrice(Goods.ItemEntry entry, float stock, float need, float discount) {
		float margin = Goods.getMargins(entry.getGood().getKind())[0] * (1F - discount);
		return Math.max(1, (int) Math.ceil(entry.getValue() * getPriceFactor(stock, need) * (1F + margin)));
	}

	static int getSellPrice(Goods.ItemEntry entry, float stock, float need, float discount, float condition) {
		float margin = Goods.getMargins(entry.getGood().getKind())[1] * (1F - discount);
		return (int) Math.floor(entry.getValue() * getPriceFactor(stock, need) * (1F - margin) * condition);
	}

	/**
	 * @return how much of its value a worn item keeps (1 for items that do not wear)
	 */
	private static float getCondition(ItemStack stack) {
		return stack.isItemStackDamageable() && stack.getMaxDamage() > 0 ? 1F - stack.getItemDamage() / (float) stack.getMaxDamage() : 1F;
	}

	/**
	 * @return the sum in gold, silver and copper coins, e.g. "2g 3s 5c"
	 */
	public static String formatMoney(int copper) {
		int gold = copper / 64;
		int silver = copper % 64 / 8;
		int rest = copper % 8;
		StringBuilder text = new StringBuilder();
		if (gold > 0) {
			text.append(gold).append("g ");
		}
		if (silver > 0) {
			text.append(silver).append("s ");
		}
		if (rest > 0 || text.length() == 0) {
			text.append(rest).append("c");
		}
		return text.toString().trim();
	}

	// ------------------------------------------------------------------ the window

	/**
	 * A player right clicks a faction trader: in an active faction city the trader opens the city's market.
	 *
	 * @return false if the trader is not in a city with a market (it shows its old trade list)
	 */
	public static boolean tryOpen(EntityPlayerMP player, NpcFactionTrader trader) {
		if (!AWDiplomacyStatics.marketEnabled) {
			return false;
		}
		Settlement city = Settlements.getData(trader.world).getActiveAt(trader.world.provider.getDimension(), trader.getPosition(), Settlement.Type.FACTION_CITY);
		if (city == null || !hasMarket(city)) {
			return false;
		}
		if (FactionTracker.INSTANCE.isHostileToPlayer(trader.world, player.getUniqueID(), player.getName(), city.getFaction())) {
			player.sendMessage(new TextComponentTranslation("gui.aw.market.hostile", Settlements.displayName(city)));
			return true;
		}
		if (Sieges.isBesieged(city)) {
			player.sendMessage(new TextComponentTranslation("gui.aw.market.besieged", Settlements.displayName(city)));
			return true;
		}
		Session session = new Session(trader.getEntityId(), city.getId());
		SESSIONS.put(player.getUniqueID(), session);
		sendView(player, trader, city, null);
		return true;
	}

	private static void sendView(EntityPlayerMP player, NpcFactionTrader trader, Settlement city, @Nullable ITextComponent message) {
		World world = player.world;
		Market market = getMarket(city);
		float discount = getDiscount(world, player, city.getFaction());
		NBTTagCompound tag = new NBTTagCompound();
		tag.setString("title", ITextComponent.Serializer.componentToJson(new TextComponentTranslation("gui.aw.market.title", Settlements.displayName(city))));
		tag.setInteger("playerCopper", Coins.countCopper(player));
		tag.setInteger("cityCopper", getCityCopper(city));
		tag.setFloat("discount", discount);
		tag.setBoolean("oldTrades", !AWDiplomacyStatics.marketReplacesTradeLists && !trader.getTradeList().isEmpty());
		if (message != null) {
			tag.setString("message", ITextComponent.Serializer.componentToJson(message));
		}
		NBTTagList marketRows = new NBTTagList();
		for (Goods.Good good : Goods.getAll()) {
			float need = getNeed(city, good);
			float stock = market.getStock(good.getId());
			for (Goods.ItemEntry entry : good.getItems()) {
				ItemStack stack = entry.createStack();
				if (stack.isEmpty()) {
					continue;
				}
				int[] prices = buyPrices(entry, stock, need, discount);
				marketRows.appendTag(row(GOOD_ROW + entry.getKey(), stack, (int) Math.floor(stock / entry.getUnits()), prices, getPriceFactor(stock, need)));
			}
		}
		if (AWDiplomacyStatics.marketReplacesTradeLists) {
			FactionTradeList list = trader.getTradeList();
			list.updateTradesForView();
			for (int i = 0; i < list.size(); i++) {
				FactionTrade trade = list.get(i);
				int price = getSpecialPrice(trade);
				ItemStack output = getSpecialOutput(trade);
				if (price <= 0 || output.isEmpty()) {
					continue;
				}
				int[] prices = new int[Math.min(MAX_ROW_UNITS, Math.max(0, trade.getCurrentAvailable()))];
				java.util.Arrays.fill(prices, price);
				NBTTagCompound special = row(SPECIAL_ROW + i, output, trade.getCurrentAvailable(), prices, MAX_FACTOR);
				special.setBoolean("special", true);
				marketRows.appendTag(special);
			}
		}
		tag.setTag("market", marketRows);
		NBTTagList playerRows = new NBTTagList();
		for (int slot = 0; slot < player.inventory.mainInventory.size(); slot++) {
			ItemStack stack = player.inventory.mainInventory.get(slot);
			Goods.ItemEntry entry = Goods.find(stack);
			if (entry == null) {
				continue;
			}
			Goods.Good good = entry.getGood();
			int[] prices = sellPrices(entry, market.getStock(good.getId()), getNeed(city, good), getMaxStock(city, good), discount, getCondition(stack), stack.getCount());
			playerRows.appendTag(row(PLAYER_ROW + slot, stack, stack.getCount(), prices, getPriceFactor(market.getStock(good.getId()), getNeed(city, good))));
		}
		tag.setTag("player", playerRows);
		NetworkHandler.sendToPlayer(player, new PacketMarket(tag));
	}

	private static NBTTagCompound row(String id, ItemStack stack, int stock, int[] prices, float factor) {
		NBTTagCompound row = new NBTTagCompound();
		row.setString("id", id);
		row.setTag("stack", stack.writeToNBT(new NBTTagCompound()));
		row.setInteger("stock", stock);
		row.setIntArray("prices", prices);
		row.setFloat("factor", factor);
		return row;
	}

	/**
	 * @return the price of each unit bought in a row, one after the other (the stock falls with each)
	 */
	private static int[] buyPrices(Goods.ItemEntry entry, float stock, float need, float discount) {
		int count = Math.min(MAX_ROW_UNITS, (int) Math.floor(stock / entry.getUnits()));
		int[] prices = new int[Math.max(0, count)];
		float left = stock;
		for (int i = 0; i < prices.length; i++) {
			prices[i] = getBuyPrice(entry, left, need, discount);
			left -= entry.getUnits();
		}
		return prices;
	}

	/**
	 * @return the price of each unit sold from a stack, one after the other (the stock rises with each; the city stops at its maximum)
	 */
	private static int[] sellPrices(Goods.ItemEntry entry, float stock, float need, float maxStock, float discount, float condition, int count) {
		int[] prices = new int[Math.min(MAX_ROW_UNITS, count)];
		float level = stock;
		int sold = 0;
		for (; sold < prices.length && level + entry.getUnits() <= maxStock; sold++) {
			prices[sold] = getSellPrice(entry, level, need, discount, condition);
			level += entry.getUnits();
		}
		return sold == prices.length ? prices : java.util.Arrays.copyOf(prices, sold);
	}

	/**
	 * @return the price in copper of one trade of the old list sold as a special: all its inputs are coins and its output is not a market
	 * good; 0 otherwise
	 */
	private static int getSpecialPrice(FactionTrade trade) {
		int copper = 0;
		boolean any = false;
		for (int i = 0; i < trade.size(); i++) {
			ItemStack input = trade.getInputStack(i);
			if (input.isEmpty()) {
				continue;
			}
			if (!Coins.isMoney(input)) {
				return 0;
			}
			copper += Coins.copperValue(input);
			any = true;
		}
		return any ? (int) Math.ceil(copper * AWDiplomacyStatics.specialPriceMultiplier) : 0;
	}

	private static ItemStack getSpecialOutput(FactionTrade trade) {
		for (int i = 0; i < trade.size(); i++) {
			ItemStack output = trade.getOutputStack(i);
			if (!output.isEmpty()) {
				return Goods.find(output) == null && !Coins.isMoney(output) ? output : ItemStack.EMPTY;
			}
		}
		return ItemStack.EMPTY;
	}

	// ------------------------------------------------------------------ actions

	/**
	 * The player pressed a button of the market window: close, the old trades, or confirm a deal (a list of rows and quantities). The server
	 * computes the deal again from the current stock, coins and inventory; it is done whole or not at all.
	 */
	public static void handleAction(EntityPlayerMP player, NBTTagCompound tag) {
		String action = tag.getString("action");
		Session session = SESSIONS.get(player.getUniqueID());
		if ("close".equals(action) || session == null) {
			SESSIONS.remove(player.getUniqueID());
			return;
		}
		Entity entity = player.world.getEntityByID(session.traderId);
		SettlementData data = Settlements.getData(player.world);
		Settlement city = data.get(session.cityId);
		if (!(entity instanceof NpcFactionTrader) || !entity.isEntityAlive() || entity.getDistanceSq(player) > TRADE_RANGE_SQ || city == null || !hasMarket(city)
				|| !city.contains(entity.world.provider.getDimension(), entity.getPosition())) {
			SESSIONS.remove(player.getUniqueID());
			NetworkHandler.sendToPlayer(player, new PacketMarket(closeTag()));
			return;
		}
		NpcFactionTrader trader = (NpcFactionTrader) entity;
		if ("oldtrades".equals(action)) {
			if (!AWDiplomacyStatics.marketReplacesTradeLists) {
				SESSIONS.remove(player.getUniqueID());
				trader.startTrade(player);
				NetworkHandler.INSTANCE.openGui(player, NetworkHandler.GUI_NPC_FACTION_TRADE_VIEW, trader.getEntityId(), 0, 0);
			}
			return;
		}
		if (!"confirm".equals(action)) {
			return;
		}
		if (Sieges.isBesieged(city) || FactionTracker.INSTANCE.isHostileToPlayer(player.world, player.getUniqueID(), player.getName(), city.getFaction())) {
			SESSIONS.remove(player.getUniqueID());
			NetworkHandler.sendToPlayer(player, new PacketMarket(closeTag()));
			return;
		}
		ITextComponent result = confirm(player, trader, city, tag.getTagList("deal", Constants.NBT.TAG_COMPOUND));
		data.markDirty();
		sendView(player, trader, city, result);
	}

	private static NBTTagCompound closeTag() {
		NBTTagCompound close = new NBTTagCompound();
		close.setBoolean("close", true);
		return close;
	}

	private static ITextComponent confirm(EntityPlayerMP player, NpcFactionTrader trader, Settlement city, NBTTagList deal) {
		World world = player.world;
		Market market = getMarket(city);
		float discount = getDiscount(world, player, city.getFaction());
		Map<String, Float> stock = new HashMap<>();
		for (Goods.Good good : Goods.getAll()) {
			stock.put(good.getId(), market.getStock(good.getId()));
		}
		int sellTotal = 0;
		int buyTotal = 0;
		// first the sales (they fill the market), then the purchases
		for (int i = 0; i < deal.tagCount(); i++) {
			NBTTagCompound line = deal.getCompoundTagAt(i);
			String id = line.getString("id");
			int quantity = line.getInteger("qty");
			if (quantity <= 0 || !id.startsWith(PLAYER_ROW)) {
				continue;
			}
			int slot = Integer.parseInt(id.substring(PLAYER_ROW.length()));
			ItemStack stack = slot >= 0 && slot < player.inventory.mainInventory.size() ? player.inventory.mainInventory.get(slot) : ItemStack.EMPTY;
			Goods.ItemEntry entry = Goods.find(stack);
			if (entry == null || stack.getCount() < quantity) {
				return error("gui.aw.market.error.changed");
			}
			Goods.Good good = entry.getGood();
			float need = getNeed(city, good);
			float max = getMaxStock(city, good);
			float condition = getCondition(stack);
			for (int unit = 0; unit < quantity; unit++) {
				float level = stock.get(good.getId());
				if (level + entry.getUnits() > max) {
					return error("gui.aw.market.error.full", stack.getDisplayName());
				}
				sellTotal += getSellPrice(entry, level, need, discount, condition);
				stock.put(good.getId(), level + entry.getUnits());
			}
		}
		FactionTradeList list = trader.getTradeList();
		for (int i = 0; i < deal.tagCount(); i++) {
			NBTTagCompound line = deal.getCompoundTagAt(i);
			String id = line.getString("id");
			int quantity = line.getInteger("qty");
			if (quantity <= 0) {
				continue;
			}
			if (id.startsWith(GOOD_ROW)) {
				Goods.ItemEntry entry = Goods.getEntry(id.substring(GOOD_ROW.length()));
				if (entry == null || entry.createStack().isEmpty()) {
					return error("gui.aw.market.error.changed");
				}
				Goods.Good good = entry.getGood();
				float need = getNeed(city, good);
				for (int unit = 0; unit < quantity; unit++) {
					float level = stock.get(good.getId());
					if (level < entry.getUnits()) {
						return error("gui.aw.market.error.stock", entry.createStack().getDisplayName());
					}
					buyTotal += getBuyPrice(entry, level, need, discount);
					stock.put(good.getId(), level - entry.getUnits());
				}
			} else if (id.startsWith(SPECIAL_ROW)) {
				if (!AWDiplomacyStatics.marketReplacesTradeLists) {
					return error("gui.aw.market.error.changed");
				}
				int index = Integer.parseInt(id.substring(SPECIAL_ROW.length()));
				if (index < 0 || index >= list.size() || getSpecialPrice(list.get(index)) <= 0 || list.get(index).getCurrentAvailable() < quantity) {
					return error("gui.aw.market.error.changed");
				}
				buyTotal += getSpecialPrice(list.get(index)) * quantity;
			}
		}
		int net = buyTotal - sellTotal;
		int cityCopper = getCityCopper(city);
		if (net > 0 && Coins.countCopper(player) < net) {
			return error("gui.aw.market.error.money", formatMoney(net));
		}
		if (net < 0 && cityCopper < -net) {
			return error("gui.aw.market.error.city_money", Settlements.displayName(city), formatMoney(cityCopper));
		}
		// the deal holds: items, stock, specials, coins
		for (int i = 0; i < deal.tagCount(); i++) {
			NBTTagCompound line = deal.getCompoundTagAt(i);
			String id = line.getString("id");
			int quantity = line.getInteger("qty");
			if (quantity <= 0) {
				continue;
			}
			if (id.startsWith(PLAYER_ROW)) {
				player.inventory.decrStackSize(Integer.parseInt(id.substring(PLAYER_ROW.length())), quantity);
			} else if (id.startsWith(GOOD_ROW)) {
				giveItems(player, Goods.getEntry(id.substring(GOOD_ROW.length())).createStack(), quantity);
			} else if (id.startsWith(SPECIAL_ROW)) {
				FactionTrade trade = list.get(Integer.parseInt(id.substring(SPECIAL_ROW.length())));
				for (int unit = 0; unit < quantity && trade.takeOne(world.getTotalWorldTime()); unit++) {
					for (ItemStack output : trade.getOutput()) {
						if (!output.isEmpty()) {
							giveItems(player, output.copy(), output.getCount());
						}
					}
				}
			}
		}
		for (Map.Entry<String, Float> entry : stock.entrySet()) {
			market.setStock(entry.getKey(), entry.getValue());
		}
		if (net > 0) {
			Coins.takeCopper(player, net);
		} else if (net < 0) {
			Coins.giveCopper(player, -net);
		}
		setCityCopper(city, cityCopper + net);
		player.inventoryContainer.detectAndSendChanges();
		AncientWarfareNPC.LOG.info("{} traded in the market of city {} ({}): bought for {}, sold for {} copper", player.getName(), city.getId(), city.getName(),
				buyTotal, sellTotal);
		if (net == 0) {
			return new TextComponentTranslation("gui.aw.market.done_even");
		}
		return new TextComponentTranslation(net > 0 ? "gui.aw.market.done_paid" : "gui.aw.market.done_received", formatMoney(Math.abs(net)));
	}

	private static void giveItems(EntityPlayer player, ItemStack template, int quantity) {
		int left = quantity;
		while (left > 0) {
			ItemStack stack = template.copy();
			stack.setCount(Math.min(left, Math.max(1, stack.getMaxStackSize())));
			left -= stack.getCount();
			if (!player.inventory.addItemStackToInventory(stack) && !stack.isEmpty()) {
				player.dropItem(stack, false);
			}
		}
	}

	private static ITextComponent error(String key, Object... args) {
		ITextComponent text = new TextComponentTranslation(key, args);
		text.getStyle().setColor(TextFormatting.RED);
		return text;
	}
}
