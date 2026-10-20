package net.shadowmage.ancientwarfare.npc.war;

import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Random names for cities, built from two parts in the style of the culture of the city's faction (e.g. "Raven" + "hold" for the Empire,
 * "Skull" + " Cove" for pirates). A kingdom uses the parts of its culture; a faction without parts of its own uses the generic medieval ones.
 */
public class SettlementNames {
	private SettlementNames() {}

	private static final Style GENERIC = new Style(
			new String[] {"Ash", "Black", "Bram", "Brook", "Cold", "Crow", "Dun", "Eagle", "Elder", "Fair", "Fern", "Frost", "Glen", "Gold", "Grey", "Hart",
					"Hawk", "High", "Iron", "Kings", "Lion", "Long", "Marsh", "Mill", "Moon", "North", "Oak", "Raven", "Red", "Rose", "Salt", "Silver",
					"South", "Stag", "Stone", "Storm", "Thorn", "Wolf", "West", "White", "Wild", "Winter"},
			new String[] {"burg", "bury", "crest", "dale", "fell", "ford", "gard", "gate", "haven", "helm", "hold", "holm", "keep", "mere", "moor",
					"mouth", "reach", "stead", "ton", "vale", "wall", "watch", "wick", "wood"});

	private static final Map<String, Style> STYLES = new HashMap<>();

	static {
		style("empire", new String[] {"Alt", "Alten", "Eisen", "Falken", "Grun", "Hoch", "Kaiser", "Konig", "Linden", "Marien", "Rosen", "Sigmar",
				"Stahl", "Talab", "Wald", "Weiss"}, new String[] {"burg", "dorf", "feld", "hausen", "heim", "hof", "stadt", "stein", "tal", "wald"});
		style("reiksgard", new String[] {"Adler", "Drachen", "Eber", "Greif", "Hammer", "Helden", "Kron", "Lowen", "Reiks", "Ritter", "Schwert",
				"Wolfen"}, new String[] {"burg", "feste", "gard", "hall", "hold", "mark", "stadt", "turm", "wacht"});
		style("norska", new String[] {"Bjorn", "Drak", "Frey", "Grim", "Hrafn", "Jarl", "Kval", "Rag", "Sig", "Skjold", "Thor", "Ulf", "Ygg"},
				new String[] {"by", "fjord", "gard", "heim", "holm", "nes", "stad", "vik", "vold"});
		style("icelord", new String[] {"Blizz", "Frost", "Frozen", "Glacier", "Hail", "Ice", "Rime", "Snow", "Winter"},
				new String[] {"crown", "fang", "hold", "peak", "spire", "throne", "tomb", "veil", "wall"});
		style("sarkonid", new String[] {"Al Khar", "Al Sah", "Bah", "Dar", "Jab", "Kha", "Mar", "Qas", "Sar", "Zah"},
				new String[] {"abad", "afar", "ahir", "akand", "ara", "id", "ira", "oum", "ra", "uk"});
		style("ishtari", new String[] {"Ash", "Bab", "Ish", "Kish", "Lag", "Mar", "Nin", "Sip", "Ur", "Zak"},
				new String[] {"alum", "anna", "aru", "eba", "ilim", "ish", "uruk", "ush", "uz"});
		style("xoltec", new String[] {"Atzca", "Chal", "Coyo", "Huex", "Itza", "Mazat", "Ocot", "Quetza", "Tena", "Tlax", "Xoch", "Zacat"},
				new String[] {"can", "co", "lan", "pan", "pec", "tepec", "titlan", "tlan", "zinco"});
		style("zamurai", new String[] {"Aka", "Fuji", "Hana", "Kami", "Kawa", "Kuro", "Matsu", "Mori", "Shiro", "Taka", "Yoshi"},
				new String[] {"bara", "gawa", "hama", "jima", "kawa", "mura", "oka", "saki", "yama", "zaki"});
		style("shakayana", new String[] {"Ama", "Chandra", "Deva", "Indra", "Kesh", "Mahi", "Raja", "Shan", "Surya", "Vara"},
				new String[] {"bad", "garh", "nagar", "pur", "puram", "sthan", "vati", "wada"});
		style("rakshasa", new String[] {"Asura", "Kali", "Krura", "Naga", "Pishacha", "Ravan", "Vetal"},
				new String[] {"garh", "kot", "lanka", "pur", "puri", "sthan"});
		style("zimba", new String[] {"Bwa", "Dodo", "Ka", "Kili", "Mamba", "Nya", "Simba", "Tanga", "Uzi", "Zambe"},
				new String[] {"bari", "kanda", "ma", "mbe", "ndu", "nga", "ri", "si", "wezi"});
		style("buffloka", new String[] {"Bison", "Buffalo", "Dust", "Grass", "Horn", "Plains", "Sky", "Thunder", "Wind"},
				new String[] {" Camp", " Hills", " Hoof", " Lodge", " Rock", " Run", " Springs"});
		style("kong", new String[] {"Banana", "Canopy", "Kong", "Liana", "Mighty", "Skull", "Thunder", "Vine"},
				new String[] {" Peak", " Rock", " Summit", " Temple", "-tree", " Falls", " Jungle"});
		style("amazon", new String[] {"Anti", "Hippo", "Kalli", "Lysi", "Melan", "Penthe", "Themi", "Thera"},
				new String[] {"ia", "kyra", "lis", "nike", "polis", "scyra", "thea"});
		style("minossian", new String[] {"Akro", "Amni", "Gor", "Ida", "Knos", "Laby", "Mali", "Phais", "Taur"},
				new String[] {"kion", "nos", "os", "polis", "ria", "sos", "thos", "tyr"});
		style("orc", new String[] {"Bol", "Dush", "Gor", "Grash", "Krag", "Lug", "Mog", "Snag", "Ugg", "Zog"},
				new String[] {"bag", "dush", "gash", "gor", "grim", "kul", "mash", "nak", "rot", "ug"});
		style("barbarian", new String[] {"Dun", "Grun", "Hrot", "Kar", "Krom", "Rath", "Skar", "Thul", "Vor"},
				new String[] {"agh", "dor", "gar", "hold", "mor", "rak", "rath", "skar"});
		style("dwarf", new String[] {"Bar", "Dur", "Grim", "Khaz", "Kar", "Mor", "Thor", "Zir", "Gund", "Bael"},
				new String[] {"abad", "dum", "grund", "hold", "kar", "mund", "nar", "rak", "zul"});
		style("elf", new String[] {"Ael", "Cel", "Eld", "Ith", "Lor", "Mith", "Sil", "Thal", "Vael", "Yll"},
				new String[] {"anor", "dor", "ellas", "ithil", "lond", "loth", "mir", "quessir", "rien", "thil"});
		style("hobbit", new String[] {"Bag", "Bramble", "Brandy", "Bywater", "Green", "Hobble", "Little", "Mossy", "Puddle", "Tuck"},
				new String[] {"borough", "bottom", "by", "combe", "end", "hill", "hollow", "ton"});
		style("nogg", new String[] {"Bumble", "Goose", "Lower", "Muddle", "Nogg", "Piddle", "Plum", "Turnip", "Upper", "Wobble"},
				new String[] {"bottom", "bury", "combe", "ford", "puddle", "stow", "ton", "wallop", "wick"});
		style("gnome", new String[] {"Bolt", "Cog", "Fizz", "Gear", "Gimble", "Sprocket", "Tinker", "Whistle"},
				new String[] {"burrow", "dell", "ton", "warren", "widget", "works"});
		style("gremlin", new String[] {"Crank", "Gnash", "Grizzle", "Nib", "Scrap", "Snik", "Twitch"},
				new String[] {"burrow", "hole", "nook", "pit", "warren"});
		style("ent", new String[] {"Ancient", "Bark", "Bough", "Elder", "Moss", "Old", "Root", "Thorn", "Willow"},
				new String[] {"grove", "hollow", "root", "shade", "wald", "wood"});
		style("giant", new String[] {"Bould", "Grond", "Hruk", "Jot", "Krag", "Thrum", "Ymir"},
				new String[] {"fell", "heim", "holt", "peak", "stone", "throne"});
		style("smingol", new String[] {"Bog", "Damp", "Gollum", "Mire", "Slink", "Sneak", "Squelch"},
				new String[] {"cave", "hole", "mire", "pool", "puddle", "tunnel"});
		style("mindflayer", new String[] {"Ilith", "Kth", "Nhal", "Qor", "Thul", "Vhor", "Xth", "Zhul"},
				new String[] {"aar", "eth", "ith", "lith", "oth", "ulun", "yrr"});
		style("guild", new String[] {"Coin", "Copper", "Gilded", "Golden", "Ledger", "Market", "Merchant", "Silver", "Trade"},
				new String[] {"bridge", "cross", "gate", "hall", "port", "row", "ton", "wharf"});
		style("sealsker", new String[] {"Brine", "Fin", "Kelp", "Seal", "Selk", "Skerry", "Surf", "Tide"},
				new String[] {"haven", "holm", "rock", "skerry", "strand", "voe", "wick"});
		style("vyncan", new String[] {"Bryn", "Caer", "Llan", "Pen", "Tal", "Tre", "Vyn", "Ystrad"},
				new String[] {"dinas", "fawr", "gwyn", "mawr", "nant", "wen", "y"});
		style("lizardmen", new String[] {"Hiss", "Ssa", "Sseth", "Szar", "Tzik", "Xiss", "Zsar"},
				new String[] {"assa", "eth", "iss", "ssk", "ssu", "tl", "zik"});
		style("coven", new String[] {"Black", "Cauldron", "Crone", "Grim", "Hag", "Hex", "Raven", "Toad"},
				new String[] {"fen", "hollow", "marsh", "mire", "moor", "wood"});
		style("witchbane", new String[] {"Ash", "Candle", "Holy", "Pyre", "Saint", "Salem", "Silver", "Vigil"},
				new String[] {"bury", "chapel", "cross", "ford", "rest", "ton", "ward"});
		style("brigand", new String[] {"Blackjack", "Cutpurse", "Cutthroat", "Dagger", "Gallows", "Rat", "Robber", "Rogue"},
				new String[] {" Camp", " Den", " Hideout", " Hollow", "'s Rest", " Roost"});
		style("pirate", new String[] {"Bilge", "Blackwater", "Cutlass", "Dead Man's", "Plunder", "Rum", "Skull", "Smuggler's"},
				new String[] {" Bay", " Cove", " Harbour", " Point", " Port", " Reef"});
		style("klown", new String[] {"Bozo", "Chuckle", "Giggle", "Jester", "Merry", "Pratfall", "Tickle"},
				new String[] {" Big Top", " Circus", " Fair", "ville", "ton"});
		style("evil", new String[] {"Blight", "Dread", "Gloom", "Malice", "Mor", "Night", "Scorn", "Shadow", "Vile"},
				new String[] {"fang", "gloom", "grave", "hold", "keep", "maw", "spire", "thorn"});
		style("good", new String[] {"Bright", "Dawn", "Gleam", "Hope", "Light", "Sun", "Valor", "White"},
				new String[] {"guard", "haven", "hold", "keep", "rest", "spire", "watch"});
		style("undead", new String[] {"Ash", "Bone", "Crypt", "Death", "Dust", "Grave", "Rot", "Tomb"},
				new String[] {"barrow", "fell", "grave", "hollow", "moor", "yard"});
		style("monster", new String[] {"Bile", "Claw", "Fang", "Gore", "Maw", "Slime", "Spine"},
				new String[] {"den", "lair", "nest", "pit", "warren"});
		style("vampire", new String[] {"Blood", "Crimson", "Draku", "Moro", "Nacht", "Sangu", "Vlad"},
				new String[] {"grad", "keep", "ovar", "spire", "stein", "var"});
		style("beast", new String[] {"Bristle", "Claw", "Fang", "Howl", "Pack", "Tusk", "Wild"},
				new String[] {"den", "hollow", "lair", "run", "warren"});
		style("wizardly", new String[] {"Arcane", "Astral", "Crystal", "Mage", "Mystic", "Rune", "Spell", "Star"},
				new String[] {"hold", "reach", "spire", "tower", "ward", "watch"});
		style("demon", new String[] {"Abys", "Brim", "Cinder", "Hell", "Infer", "Sulfur", "Zar"},
				new String[] {"gate", "heth", "maw", "pit", "spire", "throne"});
	}

	private static final int ATTEMPTS = 50;

	private static void style(String culture, String[] first, String[] second) {
		STYLES.put(culture, new Style(first, second));
	}

	/**
	 * @return a name in the style of the culture of the faction, not used by any of the given settlements (ignoring case)
	 */
	public static String generate(Random rand, Collection<Settlement> existing, String faction) {
		Style style = STYLES.getOrDefault(Kingdoms.cultureOf(faction), GENERIC);
		String name = "";
		for (int i = 0; i < ATTEMPTS; i++) {
			name = style.make(rand);
			if (!isUsed(name, existing)) {
				return name;
			}
		}
		// the parts of the culture are used up: the generic ones, then a number
		for (int i = 0; i < ATTEMPTS; i++) {
			name = GENERIC.make(rand);
			if (!isUsed(name, existing)) {
				return name;
			}
		}
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

	private static final class Style {
		private final String[] first;
		private final String[] second;

		private Style(String[] first, String[] second) {
			this.first = first;
			this.second = second;
		}

		private String make(Random rand) {
			return first[rand.nextInt(first.length)] + second[rand.nextInt(second.length)];
		}
	}
}
