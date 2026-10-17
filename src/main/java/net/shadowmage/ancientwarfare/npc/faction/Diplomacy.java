package net.shadowmage.ancientwarfare.npc.faction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFactionLeader;
import net.shadowmage.ancientwarfare.npc.gamedata.DiplomacyData;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionRelationData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.Settlements;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Dynamic diplomacy, after Mount &amp; Blade: rival factions (hostile in factions.json) start at war; each side of a war gathers war
 * exhaustion from its losses (units, leaders, cities, failed attacks) and from the fighting away from players; once per Minecraft day an
 * exhausted war can end in a truce, rivals at peace can go to war again after the truce, factions with a common enemy can ally, allies
 * join each other's wars, and alliances without common enemies end. Eternal enemies (monsters, bandits...) never make peace nor ally.
 * The relation itself is kept in {@link FactionRelationData}, the rest in {@link DiplomacyData}.
 */
public class Diplomacy {
	private Diplomacy() {}

	private static final int DAY_TICKS = 24000;
	private static final double PEACE_CHANCE_PER_POINT = 0.01D;
	private static final double MAX_PEACE_CHANCE = 0.9D;

	public static DiplomacyData getData(World world) {
		return AWGameData.INSTANCE.getData(world, DiplomacyData.class);
	}

	private static FactionRelationData getRelations(World world) {
		return AWGameData.INSTANCE.getData(world, FactionRelationData.class);
	}

	/**
	 * @return the current Minecraft day (follows /time and sleeping, like the days of the diplomacy)
	 */
	public static long getDay(World world) {
		return world.getWorldTime() / DAY_TICKS;
	}

	public static boolean isEternalEnemy(String faction) {
		return Arrays.asList(AWDiplomacyStatics.eternalEnemies).contains(faction);
	}

	/**
	 * @return true if the two factions are hostile to each other in factions.json (in one direction or the other)
	 */
	public static boolean areRivals(String faction, String otherFaction) {
		return FactionRegistry.getFaction(faction).isHostileTowards(otherFaction) || FactionRegistry.getFaction(otherFaction).isHostileTowards(faction);
	}

	/**
	 * Called by FactionRelations.setRelation (also for /awrelation): the new relation starts today, exhaustion is cleared outside of war.
	 */
	static void onRelationChanged(World world, String faction, String otherFaction, @Nullable FactionRelation relation) {
		if (world.isRemote) {
			return;
		}
		DiplomacyData data = getData(world);
		DiplomacyData.Entry entry = data.get(faction, otherFaction);
		entry.setSince(getDay(world));
		if (relation != FactionRelation.WAR) {
			entry.resetExhaustion();
		}
		data.markDirty();
	}

	/**
	 * A faction npc died: if killed by the enemy in a war (an npc of the enemy faction, a player member of it or one of its npcs),
	 * its faction gets war exhaustion.
	 */
	public static void onNpcKilled(NpcFaction victim, DamageSource source) {
		if (!AWDiplomacyStatics.diplomacyEnabled || victim.world.isRemote) {
			return;
		}
		String killerFaction = getFactionOf(victim.world, source.getTrueSource());
		if (killerFaction != null) {
			addExhaustion(victim.world, victim.getFaction(), killerFaction,
					victim instanceof NpcFactionLeader ? AWDiplomacyStatics.exhaustionLeaderKilled : AWDiplomacyStatics.exhaustionUnitKilled);
		}
	}

	public static void onCityLost(World world, String loser, String winner) {
		if (AWDiplomacyStatics.diplomacyEnabled) {
			addExhaustion(world, loser, winner, AWDiplomacyStatics.exhaustionCityLost);
		}
	}

	public static void onAttackFailed(World world, String attacker, String defender) {
		if (AWDiplomacyStatics.diplomacyEnabled) {
			addExhaustion(world, attacker, defender, AWDiplomacyStatics.exhaustionAttackFailed);
		}
	}

	/**
	 * A city was plundered (an attack nobody came to defend): its faction gets war exhaustion.
	 */
	public static void onCityPlundered(World world, String defender, String attacker) {
		if (AWDiplomacyStatics.diplomacyEnabled) {
			addExhaustion(world, defender, attacker, AWDiplomacyStatics.exhaustionCityPlundered);
		}
	}

	@Nullable
	private static String getFactionOf(World world, @Nullable Entity entity) {
		if (entity instanceof NpcFaction) {
			return ((NpcFaction) entity).getFaction();
		} else if (entity instanceof EntityPlayer) {
			return FactionMembership.getFaction(world, entity.getUniqueID());
		} else if (entity instanceof NpcPlayerOwned) {
			return FactionMembership.getFaction(world, ((NpcPlayerOwned) entity).getOwner().getUUID());
		}
		return null;
	}

	private static void addExhaustion(World world, String faction, String enemy, int amount) {
		if (amount <= 0 || faction.equals(enemy) || getRelations(world).get(faction, enemy) != FactionRelation.WAR) {
			return;
		}
		DiplomacyData data = getData(world);
		DiplomacyData.Entry entry = data.get(faction, enemy);
		entry.setExhaustion(faction, enemy, entry.getExhaustion(faction, enemy) + amount);
		data.markDirty();
	}

	public static void setExhaustion(World world, String faction, String enemy, int value) {
		DiplomacyData data = getData(world);
		data.get(faction, enemy).setExhaustion(faction, enemy, value);
		data.markDirty();
	}

	/**
	 * Called every tick for the overworld: seeds the wars once, then runs a diplomatic day at each new Minecraft day.
	 */
	public static void tick(WorldServer world) {
		if (!AWDiplomacyStatics.diplomacyEnabled || world.provider.getDimension() != 0 || world.getTotalWorldTime() % 20 != 0) {
			return;
		}
		DiplomacyData data = getData(world);
		long day = getDay(world);
		if (!data.isInitialized()) {
			seedWars(world);
			data.setInitialized();
			data.setLastDay(day);
			return;
		}
		if (day < data.getLastDay()) {
			data.setLastDay(day); // the time was set back
		} else if (day > data.getLastDay()) {
			data.setLastDay(day);
			runDay(world);
		}
	}

	/**
	 * Every pair of rivals without a relation set in this world goes to war (no announcement).
	 */
	private static void seedWars(WorldServer world) {
		FactionRelationData relations = getRelations(world);
		DiplomacyData data = getData(world);
		List<String> factions = new ArrayList<>(new TreeSet<>(FactionRegistry.getFactionNames()));
		long day = getDay(world);
		int count = 0;
		for (int i = 0; i < factions.size(); i++) {
			for (int j = i + 1; j < factions.size(); j++) {
				String a = factions.get(i);
				String b = factions.get(j);
				if (relations.get(a, b) == null && areRivals(a, b)) {
					relations.set(a, b, FactionRelation.WAR);
					data.get(a, b).setSince(day);
					count++;
				}
			}
		}
		data.markDirty();
		MinecraftServer server = world.getMinecraftServer();
		if (server != null) {
			FactionMembership.sendDiplomacyToAll(server);
		}
		AncientWarfareNPC.LOG.info("Dynamic diplomacy: seeded {} wars between rival factions", count);
	}

	/**
	 * A diplomatic day: exhaustion recovery and background fighting, truces, new wars, alliances, calls to arms, ends of alliances.
	 */
	public static void runDay(WorldServer world) {
		MinecraftServer server = world.getMinecraftServer();
		if (server == null) {
			return;
		}
		FactionRelationData relations = getRelations(world);
		DiplomacyData data = getData(world);
		Random rand = world.rand;
		long day = getDay(world);
		Set<String> withCities = getFactionsWithCities(world);
		List<String> factions = new ArrayList<>(new TreeSet<>(FactionRegistry.getFactionNames()));

		// exhaustion recovery and background fighting
		for (int i = 0; i < factions.size(); i++) {
			for (int j = i + 1; j < factions.size(); j++) {
				String a = factions.get(i);
				String b = factions.get(j);
				if (relations.get(a, b) != FactionRelation.WAR) {
					continue;
				}
				DiplomacyData.Entry entry = data.get(a, b);
				boolean fighting = withCities.contains(a) && withCities.contains(b);
				int background = AWDiplomacyStatics.backgroundExhaustionPerDay;
				entry.setExhaustion(a, b, entry.getExhaustion(a, b) - AWDiplomacyStatics.exhaustionDecayPerDay + (fighting ? rand.nextInt(background + 1) : 0));
				entry.setExhaustion(b, a, entry.getExhaustion(b, a) - AWDiplomacyStatics.exhaustionDecayPerDay + (fighting ? rand.nextInt(background + 1) : 0));
			}
		}
		data.markDirty();

		List<String> active = new ArrayList<>(new TreeSet<>(withCities));
		// truces
		for (int i = 0; i < active.size(); i++) {
			for (int j = i + 1; j < active.size(); j++) {
				String a = active.get(i);
				String b = active.get(j);
				if (relations.get(a, b) == FactionRelation.WAR && canMakePeace(data, a, b, day) && rand.nextDouble() < getPeaceChance(data, a, b)) {
					makePeace(server, world, a, b, "command.aw.diplomacy.announce.peace");
				}
			}
		}
		// new wars between rivals
		for (int i = 0; i < active.size(); i++) {
			for (int j = i + 1; j < active.size(); j++) {
				String a = active.get(i);
				String b = active.get(j);
				if (!isHostile(relations, a, b) && relations.get(a, b) != FactionRelation.ALLIANCE && areRivals(a, b) && isTruceOver(data, a, b, day)
						&& countWars(relations, a, withCities) < AWDiplomacyStatics.maxWars && countWars(relations, b, withCities) < AWDiplomacyStatics.maxWars
						&& rand.nextDouble() < AWDiplomacyStatics.warChancePerDay) {
					FactionRelations.setRelation(server, a, b, FactionRelation.WAR, "command.aw.diplomacy.announce.war");
				}
			}
		}
		// alliances against a common enemy
		for (int i = 0; i < active.size(); i++) {
			for (int j = i + 1; j < active.size(); j++) {
				String a = active.get(i);
				String b = active.get(j);
				if (isEternalEnemy(a) || isEternalEnemy(b) || isHostile(relations, a, b) || relations.get(a, b) == FactionRelation.ALLIANCE
						|| !isTruceOver(data, a, b, day) || countAlliances(relations, a, factions) >= AWDiplomacyStatics.maxAlliances
						|| countAlliances(relations, b, factions) >= AWDiplomacyStatics.maxAlliances) {
					continue;
				}
				String enemy = findCommonEnemy(relations, a, b, withCities);
				if (enemy != null && rand.nextDouble() < AWDiplomacyStatics.allianceChancePerDay) {
					FactionRelations.setRelation(server, a, b, FactionRelation.ALLIANCE, "command.aw.diplomacy.announce.alliance", FactionMembership.factionName(enemy));
				}
			}
		}
		// calls to arms and ends of alliances
		for (int i = 0; i < factions.size(); i++) {
			for (int j = i + 1; j < factions.size(); j++) {
				String a = factions.get(i);
				String b = factions.get(j);
				if (relations.get(a, b) != FactionRelation.ALLIANCE) {
					continue;
				}
				callToArms(server, world, relations, data, a, b, withCities, day);
				callToArms(server, world, relations, data, b, a, withCities, day);
				if (day - data.get(a, b).getSince() >= AWDiplomacyStatics.allianceMinDays && findCommonEnemy(relations, a, b, withCities) == null
						&& rand.nextDouble() < AWDiplomacyStatics.allianceBreakChancePerDay) {
					FactionRelations.setRelation(server, a, b, FactionRelation.PEACE, "command.aw.diplomacy.announce.alliance_end");
					data.get(a, b).setTruceUntil(day + AWDiplomacyStatics.truceDays);
					data.markDirty();
				}
			}
		}
	}

	/**
	 * The ally joins, against the enemies of the faction it is at peace with, the wars of the faction.
	 */
	private static void callToArms(MinecraftServer server, World world, FactionRelationData relations, DiplomacyData data, String faction, String ally,
			Set<String> withCities, long day) {
		for (String enemy : new ArrayList<>(withCities)) {
			if (enemy.equals(faction) || enemy.equals(ally) || relations.get(faction, enemy) != FactionRelation.WAR || isHostile(relations, ally, enemy)
					|| relations.get(ally, enemy) == FactionRelation.ALLIANCE || !isTruceOver(data, ally, enemy, day)) {
				continue;
			}
			if (world.rand.nextDouble() < AWDiplomacyStatics.allianceJoinWarChance) {
				FactionRelations.setRelation(server, ally, enemy, FactionRelation.WAR, "command.aw.diplomacy.announce.join_war", FactionMembership.factionName(faction));
			}
		}
	}

	private static void makePeace(MinecraftServer server, World world, String a, String b, String announceKey) {
		FactionRelations.setRelation(server, a, b, FactionRelation.PEACE, announceKey);
		DiplomacyData data = getData(world);
		data.get(a, b).setTruceUntil(getDay(world) + AWDiplomacyStatics.truceDays);
		data.markDirty();
	}

	private static boolean canMakePeace(DiplomacyData data, String a, String b, long day) {
		DiplomacyData.Entry entry = data.get(a, b);
		return !isEternalEnemy(a) && !isEternalEnemy(b) && day - entry.getSince() >= AWDiplomacyStatics.minWarDays
				&& entry.getMaxExhaustion() >= AWDiplomacyStatics.peaceExhaustion;
	}

	private static double getPeaceChance(DiplomacyData data, String a, String b) {
		int over = data.get(a, b).getMaxExhaustion() - AWDiplomacyStatics.peaceExhaustion;
		return Math.min(MAX_PEACE_CHANCE, AWDiplomacyStatics.peaceChancePerDay + over * PEACE_CHANCE_PER_POINT);
	}

	private static boolean isTruceOver(DiplomacyData data, String a, String b, long day) {
		return day >= data.get(a, b).getTruceUntil();
	}

	/**
	 * @return true if the pair is at war, or hostile in factions.json without a relation set in this world
	 */
	private static boolean isHostile(FactionRelationData relations, String a, String b) {
		FactionRelation relation = relations.get(a, b);
		return relation == null ? areRivals(a, b) : relation.isHostile();
	}

	private static int countWars(FactionRelationData relations, String faction, Set<String> withCities) {
		int count = 0;
		for (String other : withCities) {
			if (!other.equals(faction) && relations.get(faction, other) == FactionRelation.WAR) {
				count++;
			}
		}
		return count;
	}

	private static int countAlliances(FactionRelationData relations, String faction, List<String> factions) {
		int count = 0;
		for (String other : factions) {
			if (!other.equals(faction) && relations.get(faction, other) == FactionRelation.ALLIANCE) {
				count++;
			}
		}
		return count;
	}

	@Nullable
	private static String findCommonEnemy(FactionRelationData relations, String a, String b, Set<String> withCities) {
		for (String enemy : new TreeSet<>(withCities)) {
			if (!enemy.equals(a) && !enemy.equals(b) && relations.get(a, enemy) == FactionRelation.WAR && relations.get(b, enemy) == FactionRelation.WAR) {
				return enemy;
			}
		}
		return null;
	}

	/**
	 * @return the factions that hold at least one active city (wars between them are actually fought)
	 */
	public static Set<String> getFactionsWithCities(World world) {
		Set<String> ret = new HashSet<>();
		for (Settlement settlement : Settlements.getData(world).getAll()) {
			if (settlement.isActive() && settlement.getType() == Settlement.Type.FACTION_CITY) {
				ret.add(settlement.getFaction());
			}
		}
		return ret;
	}
}
