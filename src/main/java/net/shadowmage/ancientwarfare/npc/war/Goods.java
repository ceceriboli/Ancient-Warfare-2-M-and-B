package net.shadowmage.ancientwarfare.npc.war;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.shadowmage.ancientwarfare.core.registry.IRegistryDataParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The goods of the city markets (registry economy/goods.json): groups of interchangeable items with a value each in copper coins, a kind
 * (it sets the margins: the price you pay above the value, the price you get below it) and a demand (units per 100 manpower of a city).
 * The stock of a good is counted in units of its first item (its reference); an item counts as its value / the reference value.
 * The cultures give a city more (over 1) or less (under 1) of a good than it needs, which makes it cheap or dear there.
 */
public final class Goods {
	private static final Map<String, Good> GOODS = new LinkedHashMap<>();
	// "modid:name:meta" or "modid:name" (any meta) -> the item entry
	private static final Map<String, ItemEntry> ITEMS = new HashMap<>();
	private static final Map<String, float[]> MARGINS = new HashMap<>();
	private static final Map<String, Map<String, Float>> CULTURES = new HashMap<>();

	private Goods() {}

	public static final class Good {
		private final String id;
		private final String kind;
		private final float demand;
		private final List<ItemEntry> items = new ArrayList<>();

		private Good(String id, String kind, float demand) {
			this.id = id;
			this.kind = kind;
			this.demand = demand;
		}

		public String getId() {
			return id;
		}

		public String getKind() {
			return kind;
		}

		public float getDemand() {
			return demand;
		}

		public List<ItemEntry> getItems() {
			return Collections.unmodifiableList(items);
		}

		float getReferenceValue() {
			return items.isEmpty() ? 1F : items.get(0).value;
		}
	}

	public static final class ItemEntry {
		private final Good good;
		private final String key;
		private final String itemName;
		private final int meta; // -1: any
		private final float value;

		private ItemEntry(Good good, String key, String itemName, int meta, float value) {
			this.good = good;
			this.key = key;
			this.itemName = itemName;
			this.meta = meta;
			this.value = value;
		}

		public Good getGood() {
			return good;
		}

		public String getKey() {
			return key;
		}

		/**
		 * @return the value of one item in copper coins (at a price factor of 1, without margin)
		 */
		public float getValue() {
			return value;
		}

		/**
		 * @return how many units of stock of its good one item is
		 */
		public float getUnits() {
			return value / good.getReferenceValue();
		}

		/**
		 * @return one item to show or give, empty if its mod is not loaded
		 */
		public ItemStack createStack() {
			Item item = Item.REGISTRY.getObject(new ResourceLocation(itemName));
			return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, Math.max(0, meta));
		}
	}

	public static Collection<Good> getAll() {
		return Collections.unmodifiableCollection(GOODS.values());
	}

	@Nullable
	public static Good get(String id) {
		return GOODS.get(id);
	}

	@Nullable
	public static ItemEntry getEntry(String key) {
		return ITEMS.get(key);
	}

	/**
	 * @return the catalog entry of an item stack (by its exact metadata first, then any metadata), or null if markets do not trade it
	 */
	@Nullable
	public static ItemEntry find(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
			return null;
		}
		String name = stack.getItem().getRegistryName().toString();
		int meta = stack.getItem().isDamageable() ? 0 : stack.getMetadata();
		ItemEntry exact = ITEMS.get(name + ":" + meta);
		return exact != null ? exact : ITEMS.get(name);
	}

	/**
	 * @return {buy margin, sell margin} of a kind of good
	 */
	static float[] getMargins(String kind) {
		return MARGINS.getOrDefault(kind, new float[] {0.12F, 0.11F});
	}

	/**
	 * @return how much of a good a city of the culture holds compared to what it needs (1 if the culture has no profile for it)
	 */
	static float getCultureFactor(String culture, String good) {
		Map<String, Float> profile = CULTURES.get(culture);
		return profile == null ? 1F : profile.getOrDefault(good, 1F);
	}

	public static class Parser implements IRegistryDataParser {
		@Override
		public String getName() {
			return "goods";
		}

		@Override
		public void parse(JsonObject json) {
			GOODS.clear();
			ITEMS.clear();
			MARGINS.clear();
			CULTURES.clear();
			for (Map.Entry<String, JsonElement> kind : JsonUtils.getJsonObject(json, "kinds").entrySet()) {
				JsonObject kindJson = JsonUtils.getJsonObject(kind.getValue(), kind.getKey());
				MARGINS.put(kind.getKey(), new float[] {JsonUtils.getFloat(kindJson, "buy_margin"), JsonUtils.getFloat(kindJson, "sell_margin")});
			}
			for (Map.Entry<String, JsonElement> goodEntry : JsonUtils.getJsonObject(json, "goods").entrySet()) {
				JsonObject goodJson = JsonUtils.getJsonObject(goodEntry.getValue(), goodEntry.getKey());
				Good good = new Good(goodEntry.getKey(), JsonUtils.getString(goodJson, "kind"), JsonUtils.getFloat(goodJson, "demand"));
				for (Map.Entry<String, JsonElement> itemEntry : JsonUtils.getJsonObject(goodJson, "items").entrySet()) {
					String key = itemEntry.getKey();
					String[] parts = key.split(":");
					String itemName = parts.length > 1 ? parts[0] + ":" + parts[1] : key;
					int meta = parts.length > 2 ? Integer.parseInt(parts[2]) : -1;
					ItemEntry entry = new ItemEntry(good, key, itemName, meta, itemEntry.getValue().getAsFloat());
					good.items.add(entry);
					ITEMS.put(key, entry);
				}
				GOODS.put(good.id, good);
			}
			if (json.has("cultures")) {
				for (Map.Entry<String, JsonElement> culture : JsonUtils.getJsonObject(json, "cultures").entrySet()) {
					Map<String, Float> profile = new HashMap<>();
					for (Map.Entry<String, JsonElement> factor : culture.getValue().getAsJsonObject().entrySet()) {
						profile.put(factor.getKey(), factor.getValue().getAsFloat());
					}
					CULTURES.put(culture.getKey(), profile);
				}
			}
		}
	}
}
