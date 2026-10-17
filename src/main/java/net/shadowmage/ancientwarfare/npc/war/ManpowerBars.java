package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.BossInfo;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;

import java.util.HashSet;
import java.util.Set;

/**
 * Boss bar with the remaining manpower of a city, shown to the players inside the city (or near it).
 */
public class ManpowerBars {
	private ManpowerBars() {}

	private static final String KEY_PREFIX = "city:";

	public static void update(WorldServer world, SettlementData data) {
		int dimension = world.provider.getDimension();
		Set<String> shown = new HashSet<>();
		if (AWDiplomacyStatics.manpowerBarEnabled) {
			for (Settlement settlement : data.getAll()) {
				if (settlement.getDimension() != dimension || !settlement.isActive() || settlement.getType() != Settlement.Type.FACTION_CITY) {
					continue;
				}
				String key = KEY_PREFIX + settlement.getId();
				shown.add(key);
				TextComponentTranslation name = new TextComponentTranslation("command.aw.settlement.bar", Settlements.displayName(settlement),
						settlement.getManpower(), settlement.getMaxManpower());
				float percent = settlement.getMaxManpower() <= 0 ? 0F : (float) settlement.getManpower() / settlement.getMaxManpower();
				AreaBossBars.show(world, key, settlement.getAABB().grow(AWDiplomacyStatics.manpowerBarRange), name, BossInfo.Color.PURPLE,
						BossInfo.Overlay.PROGRESS, percent);
			}
		}
		AreaBossBars.retainOnly(world, KEY_PREFIX, shown);
	}
}
