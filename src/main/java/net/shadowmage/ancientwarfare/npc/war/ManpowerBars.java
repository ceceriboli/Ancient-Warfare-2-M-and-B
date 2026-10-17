package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Boss bar with the remaining manpower of a city, shown to the players inside the city (or near it).
 */
public class ManpowerBars {
	private ManpowerBars() {}

	private static final Map<Integer, CityBar> bars = new HashMap<>();

	public static void update(WorldServer world, SettlementData data) {
		int dimension = world.provider.getDimension();
		removeStale(world, dimension);
		for (Settlement settlement : data.getAll()) {
			if (settlement.getDimension() != dimension) {
				continue;
			}
			CityBar cityBar = bars.get(settlement.getId());
			if (!AWDiplomacyStatics.manpowerBarEnabled || !settlement.isActive() || settlement.getType() != Settlement.Type.FACTION_CITY) {
				if (cityBar != null) {
					cityBar.hide();
					bars.remove(settlement.getId());
				}
				continue;
			}
			List<EntityPlayerMP> players = getPlayersNear(world, settlement);
			if (players.isEmpty()) {
				if (cityBar != null) {
					cityBar.hide();
					bars.remove(settlement.getId());
				}
				continue;
			}
			if (cityBar == null) {
				cityBar = new CityBar(world, new BossInfoServer(getName(settlement), BossInfo.Color.PURPLE, BossInfo.Overlay.PROGRESS));
				bars.put(settlement.getId(), cityBar);
			}
			BossInfoServer bar = cityBar.bar;
			bar.setName(getName(settlement));
			bar.setPercent(settlement.getMaxManpower() <= 0 ? 0F : Math.min(1F, (float) settlement.getManpower() / settlement.getMaxManpower()));
			for (EntityPlayerMP player : new ArrayList<>(bar.getPlayers())) {
				if (!players.contains(player)) {
					bar.removePlayer(player);
				}
			}
			players.forEach(bar::addPlayer);
		}
	}

	private static TextComponentTranslation getName(Settlement settlement) {
		String name = settlement.getName().isEmpty() ? "" : " (" + settlement.getName() + ")";
		return new TextComponentTranslation("command.aw.settlement.bar", settlement.getId(), name, FactionMembership.factionName(settlement.getFaction()),
				settlement.getManpower(), settlement.getMaxManpower());
	}

	private static List<EntityPlayerMP> getPlayersNear(WorldServer world, Settlement settlement) {
		AxisAlignedBB area = settlement.getAABB().grow(AWDiplomacyStatics.manpowerBarRange);
		List<EntityPlayerMP> players = new ArrayList<>();
		for (EntityPlayer player : world.playerEntities) {
			if (player instanceof EntityPlayerMP && player.isEntityAlive() && area.contains(player.getPositionVector())) {
				players.add((EntityPlayerMP) player);
			}
		}
		return players;
	}

	/**
	 * Drops the bars left over by a world that is no longer loaded (e.g. after leaving a single player world),
	 * their ids could belong to another world's cities.
	 */
	private static void removeStale(WorldServer world, int dimension) {
		Iterator<CityBar> it = bars.values().iterator();
		while (it.hasNext()) {
			World barWorld = it.next().world.get();
			if (barWorld == null || barWorld != world && barWorld.provider.getDimension() == dimension) {
				it.remove();
			}
		}
	}

	private static class CityBar {
		private final WeakReference<World> world;
		private final BossInfoServer bar;

		private CityBar(World world, BossInfoServer bar) {
			this.world = new WeakReference<>(world);
			this.bar = bar;
		}

		private void hide() {
			for (EntityPlayerMP player : new ArrayList<>(bar.getPlayers())) {
				bar.removePlayer(player);
			}
		}
	}
}
