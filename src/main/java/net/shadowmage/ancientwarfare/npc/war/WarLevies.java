package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Full sieges are paid with the manpower of the attacking faction: one for each attacker, taken from all its cities in proportion to their
 * manpower (loaded or not), never below siege_levy_floor of the maximum manpower of the faction. Under that floor the faction cannot lay
 * full sieges (raids stay free). A lost siege gives back up to siege_levy_loss_refund of what was paid, a siege that did not really take
 * place (peace, stopped, called off) all of it, a taken city nothing (it brings its own manpower). The own soldiers of a player that joined
 * the assault are given back one for one.
 */
public final class WarLevies {
	private WarLevies() {}

	public static boolean isEnabled() {
		return AWDiplomacyStatics.siegeLeviesEnabled;
	}

	private static List<Settlement> getCities(World world, String faction, int dimension) {
		List<Settlement> cities = new ArrayList<>();
		for (Settlement city : Settlements.getData(world).getAll()) {
			if (city.isActive() && city.getType() == Settlement.Type.FACTION_CITY && city.getDimension() == dimension && faction.equals(city.getFaction())) {
				cities.add(city);
			}
		}
		return cities;
	}

	public static int getTotal(World world, String faction, int dimension) {
		int total = 0;
		for (Settlement city : getCities(world, faction, dimension)) {
			total += city.getManpower();
		}
		return total;
	}

	public static int getMax(World world, String faction, int dimension) {
		int max = 0;
		for (Settlement city : getCities(world, faction, dimension)) {
			max += city.getMaxManpower();
		}
		return max;
	}

	/**
	 * @return the manpower under which the faction cannot pay full sieges
	 */
	public static int getFloor(World world, String faction, int dimension) {
		return (int) Math.ceil(getMax(world, faction, dimension) * AWDiplomacyStatics.siegeLevyFloor);
	}

	/**
	 * @return the manpower the faction can spend on full sieges now (0 when it is at or under the floor)
	 */
	public static int getAvailable(World world, String faction, int dimension) {
		return Math.max(0, getTotal(world, faction, dimension) - getFloor(world, faction, dimension));
	}

	/**
	 * @return true if the faction can lay a full siege of at least the minimum size
	 */
	public static boolean canLaySiege(World world, String faction, int dimension) {
		return !isEnabled() || getAvailable(world, faction, dimension) >= AWDiplomacyStatics.fullSiegeMinAttackers;
	}

	/**
	 * Takes up to cost manpower (never below the floor) from the cities of the faction, in proportion to their manpower.
	 *
	 * @return the manpower taken
	 */
	public static int pay(World world, String faction, int dimension, int cost) {
		if (!isEnabled() || cost <= 0) {
			return 0;
		}
		int amount = Math.min(cost, getAvailable(world, faction, dimension));
		if (amount <= 0) {
			return 0;
		}
		List<Settlement> cities = getCities(world, faction, dimension);
		int total = getTotal(world, faction, dimension);
		int[] shares = new int[cities.size()];
		int given = 0;
		for (int i = 0; i < cities.size(); i++) {
			shares[i] = Math.min(cities.get(i).getManpower(), (int) Math.floor((double) amount * cities.get(i).getManpower() / Math.max(1, total)));
			given += shares[i];
		}
		// what the rounding left is taken from the cities with the most manpower left
		while (given < amount) {
			int best = -1;
			for (int i = 0; i < cities.size(); i++) {
				if (cities.get(i).getManpower() - shares[i] > 0 && (best < 0 || cities.get(i).getManpower() - shares[i] > cities.get(best).getManpower() - shares[best])) {
					best = i;
				}
			}
			if (best < 0) {
				break;
			}
			shares[best]++;
			given++;
		}
		for (int i = 0; i < cities.size(); i++) {
			cities.get(i).setManpower(cities.get(i).getManpower() - shares[i]);
		}
		Settlements.getData(world).markDirty();
		AncientWarfareNPC.LOG.info("The {} pay {} manpower for a full siege ({} asked), {} of {} left over {} cities", faction, given, cost,
				getTotal(world, faction, dimension), getMax(world, faction, dimension), cities.size());
		return given;
	}

	/**
	 * Gives back manpower to the cities of the faction, in proportion to what each one misses, never above their maximum.
	 *
	 * @return the manpower given back
	 */
	public static int refund(World world, String faction, int dimension, int amount, String reason) {
		if (amount <= 0) {
			return 0;
		}
		List<Settlement> cities = getCities(world, faction, dimension);
		int missing = 0;
		for (Settlement city : cities) {
			missing += Math.max(0, city.getMaxManpower() - city.getManpower());
		}
		amount = Math.min(amount, missing);
		if (amount <= 0) {
			return 0;
		}
		int given = 0;
		int[] shares = new int[cities.size()];
		for (int i = 0; i < cities.size(); i++) {
			int gap = Math.max(0, cities.get(i).getMaxManpower() - cities.get(i).getManpower());
			shares[i] = Math.min(gap, (int) Math.floor((double) amount * gap / missing));
			given += shares[i];
		}
		while (given < amount) {
			int best = -1;
			for (int i = 0; i < cities.size(); i++) {
				int gap = cities.get(i).getMaxManpower() - cities.get(i).getManpower() - shares[i];
				if (gap > 0 && (best < 0 || gap > cities.get(best).getMaxManpower() - cities.get(best).getManpower() - shares[best])) {
					best = i;
				}
			}
			if (best < 0) {
				break;
			}
			shares[best]++;
			given++;
		}
		for (int i = 0; i < cities.size(); i++) {
			cities.get(i).setManpower(cities.get(i).getManpower() + shares[i]);
		}
		Settlements.getData(world).markDirty();
		AncientWarfareNPC.LOG.info("The {} get back {} manpower ({}), {} of {}", faction, given, reason, getTotal(world, faction, dimension),
				getMax(world, faction, dimension));
		return given;
	}

	/**
	 * A full siege ended: what was paid comes back according to the outcome (see the class comment).
	 */
	static void onSiegeEnded(World world, SettlementData data, Siege siege, Sieges.Outcome outcome) {
		int paid = siege.getPaid();
		if (paid <= 0) {
			return;
		}
		String faction = siege.getAttackerFaction();
		int dimension = siege.getDimension();
		int own = Math.min(paid, siege.getOwnSoldiers());
		if (own > 0) {
			refund(world, faction, dimension, own, own + " own soldiers of players took part");
			paid -= own;
		}
		switch (outcome) {
			case PEACE:
			case STOPPED:
			case UNSUPPORTED:
				refund(world, faction, dimension, paid, "the siege did not take place: " + outcome);
				break;
			case DEFENDED:
			case LIFTED:
				Random rand = world.rand;
				int back = (int) Math.round(paid * rand.nextDouble() * AWDiplomacyStatics.siegeLevyLossRefund);
				refund(world, faction, dimension, back, "survivors of the lost siege " + siege.getId());
				break;
			default:
				// a taken city brings its own manpower
				break;
		}
	}
}
