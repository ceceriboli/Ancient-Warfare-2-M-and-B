package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.init.AWNPCItems;
import net.shadowmage.ancientwarfare.npc.item.ItemCoin;

import java.util.Locale;

/**
 * Ancient Warfare coins as money: wages, taxes and prices are counted in the coin of service_wage_coin (gold by default). A payment can
 * be made with any copper, silver and gold coins by value (copper 1, silver 8, gold 64: the exchange rate of the traders, about 8 to 1).
 */
public class Coins {
	private Coins() {}

	private static final ItemCoin.CoinMetal[] BY_VALUE = {ItemCoin.CoinMetal.GOLD, ItemCoin.CoinMetal.SILVER, ItemCoin.CoinMetal.COPPER};

	public static ItemCoin.CoinMetal getMetal() {
		ItemCoin.CoinMetal metal = ItemCoin.CoinMetal.byName(AWDiplomacyStatics.serviceWageCoin.trim().toLowerCase(Locale.ROOT));
		return metal == null ? ItemCoin.CoinMetal.SILVER : metal;
	}

	public static ITextComponent getName() {
		return new TextComponentTranslation("item.coin." + getMetal().getName() + ".name");
	}

	/**
	 * @return the value of one coin in copper coins; 0 for coins that are not money (ancient)
	 */
	static int value(ItemCoin.CoinMetal metal) {
		switch (metal) {
			case GOLD:
				return 64;
			case SILVER:
				return 8;
			case COPPER:
				return 1;
			default:
				return 0;
		}
	}

	/**
	 * Gives coins of the money metal (what does not fit in the inventory is dropped at the player's feet).
	 */
	public static void give(EntityPlayer player, int amount) {
		giveMetal(player, getMetal(), amount);
	}

	/**
	 * Gives a sum in copper coins as gold, silver and copper coins (the fewest coins)
	 */
	public static void giveCopper(EntityPlayer player, int copper) {
		int left = copper;
		for (ItemCoin.CoinMetal metal : BY_VALUE) {
			int coins = left / value(metal);
			left -= coins * value(metal);
			giveMetal(player, metal, coins);
		}
		player.inventoryContainer.detectAndSendChanges();
	}

	/**
	 * Takes a sum in copper coins from the player's coins of any metal and gives the change back
	 *
	 * @return false (and takes nothing) if the player has not enough money
	 */
	public static boolean takeCopper(EntityPlayer player, int copper) {
		int total = countCopper(player);
		if (copper <= 0 || total < copper) {
			return copper <= 0;
		}
		for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
			ItemStack stack = player.inventory.getStackInSlot(slot);
			if (stack.getItem() == AWNPCItems.COIN && value(ItemCoin.getMetal(stack)) > 0) {
				player.inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
			}
		}
		giveCopper(player, total - copper);
		return true;
	}

	/**
	 * @return true for a copper, silver or gold coin (money; ancient coins are not)
	 */
	public static boolean isMoney(ItemStack stack) {
		return stack.getItem() == AWNPCItems.COIN && value(ItemCoin.getMetal(stack)) > 0;
	}

	/**
	 * @return the value of a coin stack in copper coins, 0 for anything else
	 */
	public static int copperValue(ItemStack stack) {
		return stack.getItem() == AWNPCItems.COIN ? value(ItemCoin.getMetal(stack)) * stack.getCount() : 0;
	}

	/**
	 * Drops coins of the money metal where the entity stands (the treasury of a city when its ruler dies)
	 */
	public static void drop(net.minecraft.entity.Entity entity, int amount) {
		int left = amount;
		while (left > 0) {
			ItemStack stack = ItemCoin.getCoinStack(getMetal(), Math.min(left, 64));
			left -= stack.getCount();
			entity.entityDropItem(stack, 0.5F);
		}
	}

	private static void giveMetal(EntityPlayer player, ItemCoin.CoinMetal metal, int amount) {
		int left = amount;
		while (left > 0) {
			ItemStack stack = ItemCoin.getCoinStack(metal, Math.min(left, 64));
			left -= stack.getCount();
			if (!player.inventory.addItemStackToInventory(stack) && !stack.isEmpty()) {
				player.dropItem(stack, false);
			}
		}
	}

	/**
	 * @return the value of the player's copper, silver and gold coins, in coins of the money metal (rounded down)
	 */
	public static int count(EntityPlayer player) {
		return countCopper(player) / Math.max(1, value(getMetal()));
	}

	/**
	 * @return the value of the player's copper, silver and gold coins in copper coins
	 */
	public static int countCopper(EntityPlayer player) {
		int total = 0;
		for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
			ItemStack stack = player.inventory.getStackInSlot(slot);
			if (stack.getItem() == AWNPCItems.COIN) {
				total += value(ItemCoin.getMetal(stack)) * stack.getCount();
			}
		}
		return total;
	}

	/**
	 * Takes the price, in coins of the money metal, from the player's coins of any metal and gives the change back in gold, silver and
	 * copper.
	 *
	 * @return false (and takes nothing) if the player has not enough money
	 */
	public static boolean take(EntityPlayer player, int amount) {
		int price = amount * value(getMetal());
		int total = countCopper(player);
		if (amount <= 0 || total < price) {
			return amount <= 0;
		}
		for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
			ItemStack stack = player.inventory.getStackInSlot(slot);
			if (stack.getItem() == AWNPCItems.COIN && value(ItemCoin.getMetal(stack)) > 0) {
				player.inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
			}
		}
		int change = total - price;
		for (ItemCoin.CoinMetal metal : BY_VALUE) {
			int coins = change / value(metal);
			change -= coins * value(metal);
			giveMetal(player, metal, coins);
		}
		player.inventoryContainer.detectAndSendChanges();
		return true;
	}
}
