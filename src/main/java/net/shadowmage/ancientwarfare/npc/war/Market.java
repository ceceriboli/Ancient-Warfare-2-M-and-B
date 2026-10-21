package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.nbt.NBTTagCompound;

import java.util.HashMap;
import java.util.Map;

/**
 * The market of a city: the stock of each good, in units of the good's reference item (see Goods). Saved with the city. A good the market
 * has not seen yet starts at its equilibrium (Markets.getEquilibrium).
 */
public class Market {
	private final Map<String, Float> stock = new HashMap<>();

	public boolean has(String good) {
		return stock.containsKey(good);
	}

	public float getStock(String good) {
		return stock.getOrDefault(good, 0F);
	}

	void setStock(String good, float value) {
		stock.put(good, Math.max(0F, value));
	}

	NBTTagCompound writeToNBT() {
		NBTTagCompound tag = new NBTTagCompound();
		for (Map.Entry<String, Float> entry : stock.entrySet()) {
			tag.setFloat(entry.getKey(), entry.getValue());
		}
		return tag;
	}

	static Market readFromNBT(NBTTagCompound tag) {
		Market market = new Market();
		for (String key : tag.getKeySet()) {
			market.stock.put(key, tag.getFloat(key));
		}
		return market;
	}
}
