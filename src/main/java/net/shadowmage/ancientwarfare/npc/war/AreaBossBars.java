package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server side boss bars shown to the players inside an area (city manpower, sieges). Not saved, rebuilt every second.
 */
public class AreaBossBars {
	private AreaBossBars() {}

	private static final Map<String, AreaBar> bars = new HashMap<>();

	/**
	 * Shows the bar with the given key to the players in the area (and hides it from the others), creating it if needed.
	 * Hides it completely if no player is in the area.
	 */
	public static void show(WorldServer world, String key, AxisAlignedBB area, ITextComponent name, BossInfo.Color color, BossInfo.Overlay overlay, float percent) {
		List<EntityPlayerMP> players = getPlayersIn(world, area);
		if (players.isEmpty()) {
			hide(key);
			return;
		}
		AreaBar areaBar = bars.get(key);
		if (areaBar == null || areaBar.world.get() != world) {
			areaBar = new AreaBar(world, new BossInfoServer(name, color, overlay));
			bars.put(key, areaBar);
		}
		BossInfoServer bar = areaBar.bar;
		bar.setName(name);
		bar.setColor(color);
		bar.setOverlay(overlay);
		bar.setPercent(Math.max(0F, Math.min(1F, percent)));
		for (EntityPlayerMP player : new ArrayList<>(bar.getPlayers())) {
			if (!players.contains(player)) {
				bar.removePlayer(player);
			}
		}
		players.forEach(bar::addPlayer);
	}

	public static void hide(String key) {
		AreaBar areaBar = bars.remove(key);
		if (areaBar != null) {
			for (EntityPlayerMP player : new ArrayList<>(areaBar.bar.getPlayers())) {
				areaBar.bar.removePlayer(player);
			}
		}
	}

	/**
	 * Hides the bars of this world whose key is not in the given set, and drops the bars of worlds that are no longer loaded
	 * (e.g. after leaving a single player world, their keys could belong to another world).
	 */
	public static void retainOnly(WorldServer world, String prefix, Set<String> keys) {
		Iterator<Map.Entry<String, AreaBar>> it = bars.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, AreaBar> entry = it.next();
			World barWorld = entry.getValue().world.get();
			if (barWorld == null || barWorld != world && barWorld.provider.getDimension() == world.provider.getDimension()) {
				it.remove();
			} else if (barWorld == world && entry.getKey().startsWith(prefix) && !keys.contains(entry.getKey())) {
				for (EntityPlayerMP player : new ArrayList<>(entry.getValue().bar.getPlayers())) {
					entry.getValue().bar.removePlayer(player);
				}
				it.remove();
			}
		}
	}

	private static List<EntityPlayerMP> getPlayersIn(WorldServer world, AxisAlignedBB area) {
		List<EntityPlayerMP> players = new ArrayList<>();
		for (EntityPlayer player : world.playerEntities) {
			if (player instanceof EntityPlayerMP && player.isEntityAlive() && area.contains(player.getPositionVector())) {
				players.add((EntityPlayerMP) player);
			}
		}
		return players;
	}

	private static class AreaBar {
		private final WeakReference<World> world;
		private final BossInfoServer bar;

		private AreaBar(World world, BossInfoServer bar) {
			this.world = new WeakReference<>(world);
			this.bar = bar;
		}
	}
}
