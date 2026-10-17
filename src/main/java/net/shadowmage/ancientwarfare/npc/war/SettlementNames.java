package net.shadowmage.ancientwarfare.npc.war;

import java.util.Collection;
import java.util.Random;

/**
 * Random medieval names for cities, built from two parts (e.g. "Raven" + "hold").
 */
public class SettlementNames {
	private SettlementNames() {}

	private static final String[] FIRST = {"Ash", "Black", "Bram", "Brook", "Cold", "Crow", "Dun", "Eagle", "Elder", "Fair", "Fern", "Frost", "Glen", "Gold",
			"Grey", "Hart", "Hawk", "High", "Iron", "Kings", "Lion", "Long", "Marsh", "Mill", "Moon", "North", "Oak", "Raven", "Red", "Rose", "Salt", "Silver",
			"South", "Stag", "Stone", "Storm", "Thorn", "Wolf", "West", "White", "Wild", "Winter"};
	private static final String[] SECOND = {"burg", "bury", "crest", "dale", "fell", "ford", "gard", "gate", "haven", "helm", "hold", "holm", "keep", "mere",
			"moor", "mouth", "reach", "stead", "ton", "vale", "wall", "watch", "wick", "wood"};
	private static final int ATTEMPTS = 50;

	/**
	 * @return a name not used by any of the given settlements (ignoring case)
	 */
	public static String generate(Random rand, Collection<Settlement> existing) {
		String name = "";
		for (int i = 0; i < ATTEMPTS; i++) {
			name = FIRST[rand.nextInt(FIRST.length)] + SECOND[rand.nextInt(SECOND.length)];
			if (!isUsed(name, existing)) {
				return name;
			}
		}
		// very unlikely: more than a thousand cities, add a number
		int number = 2;
		while (isUsed(name + " " + number, existing)) {
			number++;
		}
		return name + " " + number;
	}

	public static boolean isUsed(String name, Collection<Settlement> existing) {
		for (Settlement settlement : existing) {
			if (settlement.getName().equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}
}
