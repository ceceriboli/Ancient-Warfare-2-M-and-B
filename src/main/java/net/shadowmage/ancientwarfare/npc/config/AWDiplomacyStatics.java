package net.shadowmage.ancientwarfare.npc.config;

import net.shadowmage.ancientwarfare.core.config.ModConfiguration;

/**
 * Settings for faction relations and faction membership, stored in config/ancientwarfare/AncientWarfareDiplomacy.cfg
 */
public class AWDiplomacyStatics extends ModConfiguration {
	/* ********************************************SERVER SETTINGS************************************************ */
	public static boolean membershipEnabled = true;
	public static int joinMinStanding = 0;
	public static int leaveStandingPenalty = 50;
	public static boolean expelOnKillingOwnFaction = true;
	public static int expelStandingPenalty = 50;
	public static int leaderJoinRange = 8;
	public static int leaderDirectionsRange = 64;
	public static double leaderDirectionsChance = 0.25;
	public static boolean announceRelationChanges = true;
	public static boolean announceMembershipChanges = true;
	public static int spawnerMinPlayerRange = 48;
	public static boolean spawnersTriggerInCreative = false;
	public static boolean settlementsEnabled = true;
	public static int settlementDefaultRadius = 48;
	public static boolean settlementUseStructureBounds = true;
	public static boolean manpowerBarEnabled = true;
	public static int manpowerBarRange = 16;
	public static double manpowerPerUnit = 2.0;
	public static int manpowerMin = 10;
	public static int manpowerRegenPerDay = 1;
	public static int respawnDelayTicks = 600;
	public static boolean announceSettlementChanges = true;
	public static String fallenGarrison = "bandits";
	public static String banditFaction = "brigand";
	public static boolean siegesEnabled = true;
	public static int siegeCheckInterval = 2400;
	public static double siegeChance = 0.25;
	public static int siegePlayerRange = 96;
	public static int siegeCooldownDays = 2;
	public static int maxActiveSieges = 1;
	public static int siegeWarmupTicks = 300;
	public static int siegeMaxTicks = 24000;
	public static int siegeWaveStallTicks = 6000;
	public static int siegeSpawnMinDistance = 40;
	public static int siegeSpawnMaxDistance = 60;
	public static boolean announceSieges = true;
	public static double raidStrengthRatio = 0.4;
	public static int raidMinAttackers = 3;
	public static int raidMaxAttackers = 12;
	public static int raidWaves = 2;
	public static String[] raidUnitWeights = {"faction.soldier=5", "faction.archer=3", "faction.cavalry=2"};
	public static double fullSiegeShare = 0.2;
	public static int fullSiegeCooldownDays = 7;
	public static double fullSiegeStrengthRatio = 1.0;
	public static int fullSiegeMinAttackers = 8;
	public static int fullSiegeMaxAttackers = 32;
	public static int fullSiegeWaves = 3;
	public static String[] fullSiegeUnitWeights = {"faction.soldier=4", "faction.archer=3", "faction.soldier.elite=2", "faction.archer.elite=1",
			"faction.cavalry=2", "faction.mounted_archer=1"};
	public static int fullSiegeEngines = 2;
	public static String[] fullSiegeEngineTypes = {"catapult_mobile=3", "trebuchet_mobile=1", "ballista_mobile=2"};
	public static int fullSiegeEngineLevel = 1;
	public static int fullSiegeEngineDistance = 40;

	/* ********************************************CLIENT SETTINGS************************************************ */
	public static boolean renderLeaderLabel = true;
	public static int leaderLabelRange = 24;

	public AWDiplomacyStatics(String configName) {
		super(configName);
	}

	@Override
	protected void initializeCategories() {
		config.addCustomCategoryComment(serverOptions, "Server Options\n" + "Faction relations and faction membership. Affect only server-side operations. Will need to be set for dedicated servers,\n" + "and single player (or LAN worlds). Changes are read when the game starts.");
		config.addCustomCategoryComment(clientOptions, "Client Options\n" + "Affect only client-side rendering. Changes are read when the game starts.");
	}

	@Override
	protected void initializeValues() {
		membershipEnabled = config.get(serverOptions, "membership_enabled", membershipEnabled, "Faction membership\nDefault=" + membershipEnabled + "\n" + "If true, players can pledge allegiance to a faction by talking to its leader. Members are attacked by the enemies\n" + "of their faction and left alone by its allies. If false, membership has no effect and leaders don't offer it.").getBoolean();

		joinMinStanding = config.get(serverOptions, "join_min_standing", joinMinStanding, "Minimum standing to join a faction\nDefault=" + joinMinStanding + "\n" + "Standing the player needs with a faction to pledge allegiance to it.", -10000, 10000).getInt();

		leaveStandingPenalty = config.get(serverOptions, "leave_standing_penalty", leaveStandingPenalty, "Standing lost when leaving a faction\nDefault=" + leaveStandingPenalty + "\n" + "Standing the player loses with the faction they renounce.", 0, 10000).getInt();

		expelOnKillingOwnFaction = config.get(serverOptions, "expel_on_killing_own_faction", expelOnKillingOwnFaction, "Expel members who kill their own faction\nDefault=" + expelOnKillingOwnFaction + "\n" + "If true, a player who kills a member of their own faction (or whose npcs do) is expelled from it.").getBoolean();

		expelStandingPenalty = config.get(serverOptions, "expel_standing_penalty", expelStandingPenalty, "Standing lost when expelled\nDefault=" + expelStandingPenalty + "\n" + "Standing the player loses with the faction that expels them.", 0, 10000).getInt();

		leaderJoinRange = config.get(serverOptions, "leader_join_range", leaderJoinRange, "Leader range to join\nDefault=" + leaderJoinRange + "\n" + "Maximum distance in blocks from a leader of the faction to pledge allegiance to it.", 1, 256).getInt();

		leaderDirectionsRange = config.get(serverOptions, "leader_directions_range", leaderDirectionsRange, "Leader directions range\nDefault=" + leaderDirectionsRange + "\n" + "Friendly faction npcs tell the player where their leader is, if a leader is within this many blocks.\n" + "0 disables the directions.", 0, 512).getInt();

		leaderDirectionsChance = config.get(serverOptions, "leader_directions_chance", leaderDirectionsChance, "Leader directions chance\nDefault=" + leaderDirectionsChance + "\n" + "Chance (0.0 to 1.0) that a friendly faction npc tells the player where their leader is when spoken to.\n" + "0 never, 1 every time.", 0.0D, 1.0D).getDouble();

		announceRelationChanges = config.get(serverOptions, "announce_relation_changes", announceRelationChanges, "Announce relation changes\nDefault=" + announceRelationChanges + "\n" + "If true, all players are told in chat when two factions go to war, make peace or form an alliance.").getBoolean();

		announceMembershipChanges = config.get(serverOptions, "announce_membership_changes", announceMembershipChanges, "Announce membership changes\nDefault=" + announceMembershipChanges + "\n" + "If true, all players are told in chat when a player joins, leaves or is expelled from a faction.").getBoolean();

		spawnerMinPlayerRange = config.get(serverOptions, "spawner_min_player_range", spawnerMinPlayerRange, "Structure spawner range\nDefault=" + spawnerMinPlayerRange + "\n" + "Ancient Warfare structure spawners create their npcs (leaders included, so also cities) when a player is within\n" + "their own range, set in each structure template (often 16 blocks, a cube around the spawner). A player within this\n" + "distance (a sphere) also triggers every spawner. 0 = only the range of each template. At most 128: npcs that can\n" + "despawn vanish at once when no player is within 128 blocks.", 0, 128).getInt();

		spawnersTriggerInCreative = config.get(serverOptions, "spawners_trigger_in_creative", spawnersTriggerInCreative, "Structure spawners in creative\nDefault=" + spawnersTriggerInCreative + "\n" + "If true, players in creative mode also make structure spawners create their npcs (and so found cities), useful\n" + "for testing. If false (Ancient Warfare behaviour), only players in survival or adventure mode do. Players in\n" + "spectator mode never do.").getBoolean();

		settlementsEnabled = config.get(serverOptions, "settlements_enabled", settlementsEnabled, "Faction cities and manpower\nDefault=" + settlementsEnabled + "\n" + "If true, every faction leader founds a city with the faction npcs around it. While the leader lives, killed units respawn\n" + "using the city's manpower; when the leader dies the city falls and is abandoned.").getBoolean();

		settlementDefaultRadius = config.get(serverOptions, "settlement_default_radius", settlementDefaultRadius, "City radius outside structures\nDefault=" + settlementDefaultRadius + "\n" + "Cities take the bounds of the Ancient Warfare town or structure their leader is in. A leader outside of any\n" + "town or structure founds a city of this radius (in blocks) around itself. With settlement_use_structure_bounds=false\n" + "every city uses this radius. Applies to cities founded after the change.", 8, 256).getInt();

		settlementUseStructureBounds = config.get(serverOptions, "settlement_use_structure_bounds", settlementUseStructureBounds, "Cities use structure bounds\nDefault=" + settlementUseStructureBounds + "\n" + "If true, a city takes the bounds of the Ancient Warfare town or structure its leader is in. If false, every city is\n" + "a square of settlement_default_radius blocks around its leader.").getBoolean();

		manpowerBarEnabled = config.get(serverOptions, "manpower_bar_enabled", manpowerBarEnabled, "Manpower bar\nDefault=" + manpowerBarEnabled + "\n" + "If true, players inside a city (or near it) see a boss bar with the city's remaining manpower.").getBoolean();

		manpowerBarRange = config.get(serverOptions, "manpower_bar_range", manpowerBarRange, "Manpower bar range\nDefault=" + manpowerBarRange + "\n" + "The manpower bar is shown to players within this many blocks of the city bounds (0 = only inside the city).", 0, 256).getInt();

		manpowerPerUnit = config.get(serverOptions, "manpower_per_unit", manpowerPerUnit, "Manpower per garrison unit\nDefault=" + manpowerPerUnit + "\n" + "Starting manpower of a city = number of units in its garrison when founded x this value (at least manpower_min).\n" + "Each respawn of a killed unit costs 1 manpower.", 0.0D, 100.0D).getDouble();

		manpowerMin = config.get(serverOptions, "manpower_min", manpowerMin, "Minimum manpower\nDefault=" + manpowerMin + "\n" + "Minimum starting manpower of a city.", 0, 10000).getInt();

		manpowerRegenPerDay = config.get(serverOptions, "manpower_regen_per_day", manpowerRegenPerDay, "Manpower regeneration\nDefault=" + manpowerRegenPerDay + "\n" + "Manpower a city regains every Minecraft day, up to its starting manpower, while it is not under siege.", 0, 10000).getInt();

		respawnDelayTicks = config.get(serverOptions, "respawn_delay_ticks", respawnDelayTicks, "Respawn delay\nDefault=" + respawnDelayTicks + "\n" + "Ticks (20 per second) before a killed garrison unit respawns in its city.", 20, 72000).getInt();

		announceSettlementChanges = config.get(serverOptions, "announce_settlement_changes", announceSettlementChanges, "Announce fallen cities\nDefault=" + announceSettlementChanges + "\n" + "If true, all players are told in chat when a city falls because its leader died.").getBoolean();

		fallenGarrison = config.get(serverOptions, "fallen_garrison", fallenGarrison, "Garrison of a fallen city\nDefault=" + fallenGarrison + "\n" + "What the remaining units of a city do when its leader dies:\n" + "bandits = they stay where they are as npcs of bandit_faction (no city, no respawn), to be cleared out\n" + "stay = they stay in their own faction, without city and without respawn\n" + "disband = they vanish", new String[] {"bandits", "stay", "disband"}).getString();

		banditFaction = config.get(serverOptions, "bandit_faction", banditFaction, "Bandit faction\nDefault=" + banditFaction + "\n" + "Faction the remaining units of a fallen city join with fallen_garrison=bandits. They keep their equipment.").getString();

		siegesEnabled = config.get(serverOptions, "sieges_enabled", siegesEnabled, "Raids and sieges\nDefault=" + siegesEnabled + "\n" + "If true, factions at war (declared with /awrelation war) attack each other's cities near players: waves of\n" + "attackers spawn near the city and march on its leader. Most attacks are raids (raid_* settings), some are full\n" + "sieges with siege engines (full_siege_* settings). If false, attacks start only with /awsiege start.").getBoolean();

		siegeCheckInterval = config.get(serverOptions, "siege_check_interval", siegeCheckInterval, "Attack check interval\nDefault=" + siegeCheckInterval + "\n" + "Ticks (20 per second) between two checks for a new raid or siege. At each check at most one attack starts.", 20, 720000).getInt();

		siegeChance = config.get(serverOptions, "siege_chance", siegeChance, "Attack chance\nDefault=" + siegeChance + "\n" + "Chance (0.0 to 1.0) that a city that can be attacked is attacked (raid or full siege) at a check.", 0.0D, 1.0D).getDouble();

		siegePlayerRange = config.get(serverOptions, "siege_player_range", siegePlayerRange, "Siege player range\nDefault=" + siegePlayerRange + "\n" + "Only cities with a player within this many blocks of their center are besieged. Players within this range\n" + "also see the siege bar.", 16, 512).getInt();

		siegeCooldownDays = config.get(serverOptions, "siege_cooldown_days", siegeCooldownDays, "Siege cooldown\nDefault=" + siegeCooldownDays + "\n" + "Minecraft days after a raid or siege ends (or after the city is founded) before the city can be attacked again.", 0, 1000).getInt();

		maxActiveSieges = config.get(serverOptions, "max_active_sieges", maxActiveSieges, "Maximum sieges\nDefault=" + maxActiveSieges + "\n" + "Maximum number of sieges at the same time started by the automatic check (not counting /awsiege start).", 0, 100).getInt();

		siegeWarmupTicks = config.get(serverOptions, "siege_warmup_ticks", siegeWarmupTicks, "Siege warm up\nDefault=" + siegeWarmupTicks + "\n" + "Ticks (20 per second) of warning before the first wave and between two waves.", 20, 72000).getInt();

		siegeMaxTicks = config.get(serverOptions, "siege_max_ticks", siegeMaxTicks, "Siege duration\nDefault=" + siegeMaxTicks + "\n" + "Ticks (20 per second) after which a siege is lifted and its attackers leave. Time only runs while the city is loaded.", 1200, 720000).getInt();

		siegeWaveStallTicks = config.get(serverOptions, "siege_wave_stall_ticks", siegeWaveStallTicks, "Stalled wave\nDefault=" + siegeWaveStallTicks + "\n" + "Ticks (20 per second) a wave can go on without any attacker or defender killed. Then the wave withdraws and the\n" + "next one comes, or, after the last wave, the raid or siege is lifted. Time only runs while the city is loaded. 0 = never.", 0, 720000).getInt();

		siegeSpawnMinDistance = config.get(serverOptions, "siege_spawn_min_distance", siegeSpawnMinDistance, "Wave distance (minimum)\nDefault=" + siegeSpawnMinDistance + "\n" + "Minimum distance in blocks from the city leader where waves spawn.", 8, 112).getInt();

		siegeSpawnMaxDistance = config.get(serverOptions, "siege_spawn_max_distance", siegeSpawnMaxDistance, "Wave distance (maximum)\nDefault=" + siegeSpawnMaxDistance + "\n" + "Maximum distance in blocks from the city leader where waves spawn.", 8, 112).getInt();

		announceSieges = config.get(serverOptions, "announce_sieges", announceSieges, "Announce raids and sieges\nDefault=" + announceSieges + "\n" + "If true, all players are told in chat when a raid or siege starts and ends.").getBoolean();

		String unitTypes = "Units of the attacking faction in a wave, as npc type=weight. Types are the faction npc names without the\n" + "'ancientwarfarenpc:' prefix: faction.soldier, faction.soldier.elite, faction.archer, faction.archer.elite,\n" + "faction.cavalry (mounted soldier, spawns with its horse), faction.mounted_archer.";

		raidStrengthRatio = config.get(serverOptions, "raid_strength_ratio", raidStrengthRatio, "Raid size\nDefault=" + raidStrengthRatio + "\n" + "Total attackers of a raid = (garrison units loaded + manpower of the city) x this value,\n" + "limited by raid_min_attackers and raid_max_attackers, and split into raid_waves waves.", 0.0D, 100.0D).getDouble();

		raidMinAttackers = config.get(serverOptions, "raid_min_attackers", raidMinAttackers, "Raid minimum attackers\nDefault=" + raidMinAttackers + "\n" + "Minimum total number of attackers of a raid.", 1, 1000).getInt();

		raidMaxAttackers = config.get(serverOptions, "raid_max_attackers", raidMaxAttackers, "Raid maximum attackers\nDefault=" + raidMaxAttackers + "\n" + "Maximum total number of attackers of a raid.", 1, 1000).getInt();

		raidWaves = config.get(serverOptions, "raid_waves", raidWaves, "Raid waves\nDefault=" + raidWaves + "\n" + "Number of waves of a raid. The next wave comes when the previous one is wiped out.", 1, 100).getInt();

		raidUnitWeights = config.get(serverOptions, "raid_unit_weights", raidUnitWeights, "Raid units\n" + unitTypes).getStringList();

		fullSiegeShare = config.get(serverOptions, "full_siege_share", fullSiegeShare, "Full siege share\nDefault=" + fullSiegeShare + "\n" + "Chance (0.0 to 1.0) that an automatic attack is a full siege instead of a raid, if the city had no full siege\n" + "for full_siege_cooldown_days.", 0.0D, 1.0D).getDouble();

		fullSiegeCooldownDays = config.get(serverOptions, "full_siege_cooldown_days", fullSiegeCooldownDays, "Full siege cooldown\nDefault=" + fullSiegeCooldownDays + "\n" + "Minecraft days after a full siege ends (or after the city is founded) before the city can have another full siege.\n" + "Raids can still come in the meantime.", 0, 1000).getInt();

		fullSiegeStrengthRatio = config.get(serverOptions, "full_siege_strength_ratio", fullSiegeStrengthRatio, "Full siege size\nDefault=" + fullSiegeStrengthRatio + "\n" + "Total attackers of a full siege (siege engineers not counted) = (garrison units loaded + manpower) x this value,\n" + "limited by full_siege_min_attackers and full_siege_max_attackers, and split into full_siege_waves waves.", 0.0D, 100.0D).getDouble();

		fullSiegeMinAttackers = config.get(serverOptions, "full_siege_min_attackers", fullSiegeMinAttackers, "Full siege minimum attackers\nDefault=" + fullSiegeMinAttackers + "\n" + "Minimum total number of attackers of a full siege.", 1, 1000).getInt();

		fullSiegeMaxAttackers = config.get(serverOptions, "full_siege_max_attackers", fullSiegeMaxAttackers, "Full siege maximum attackers\nDefault=" + fullSiegeMaxAttackers + "\n" + "Maximum total number of attackers of a full siege.", 1, 1000).getInt();

		fullSiegeWaves = config.get(serverOptions, "full_siege_waves", fullSiegeWaves, "Full siege waves\nDefault=" + fullSiegeWaves + "\n" + "Number of waves of a full siege.", 1, 100).getInt();

		fullSiegeUnitWeights = config.get(serverOptions, "full_siege_unit_weights", fullSiegeUnitWeights, "Full siege units\n" + unitTypes).getStringList();

		fullSiegeEngines = config.get(serverOptions, "full_siege_engines", fullSiegeEngines, "Siege engines\nDefault=" + fullSiegeEngines + "\n" + "Siege engines brought by a full siege, each manned by a siege engineer of the attacking faction. They come with\n" + "the first wave and stay until the siege ends. Their shots break blocks if shots_destroys_blocks is true in the\n" + "Ancient Warfare vehicle config.", 0, 20).getInt();

		fullSiegeEngineTypes = config.get(serverOptions, "full_siege_engine_types", fullSiegeEngineTypes, "Siege engine types\n" + "Siege engines of a full siege, as vehicle type=weight. Types (Ancient Warfare vehicle config names): catapult_mobile,\n" + "catapult_mobile_turret, catapult_stand, catapult_stand_turret, trebuchet_mobile, trebuchet_stand, trebuchet_stand_turret,\n" + "ballista_mobile, ballista_mobile_turret, ballista_stand, ballista_stand_turret, cannon_mobile, cannon_stand, hwacha.").getStringList();

		fullSiegeEngineLevel = config.get(serverOptions, "full_siege_engine_level", fullSiegeEngineLevel, "Siege engine material level\nDefault=" + fullSiegeEngineLevel + "\n" + "Material level of the siege engines (0 = weakest).", 0, 10).getInt();

		fullSiegeEngineDistance = config.get(serverOptions, "full_siege_engine_distance", fullSiegeEngineDistance, "Siege engine distance\nDefault=" + fullSiegeEngineDistance + "\n" + "Distance in blocks from the city leader where siege engines are placed, on the side the first wave comes from.", 8, 112).getInt();

		// replaced by the raid_* and full_siege_* settings
		for (String obsolete : new String[] {"siege_strength_ratio", "siege_min_attackers", "siege_max_attackers", "siege_waves", "siege_unit_weights"}) {
			config.getCategory(serverOptions).remove(obsolete);
		}

		renderLeaderLabel = config.get(clientOptions, "render_leader_label", renderLeaderLabel, "Render leader label\nDefault=" + renderLeaderLabel + "\n" + "If true, faction leaders show a 'Leader of the ...' label above their head, even when nameplates are disabled.").getBoolean();

		leaderLabelRange = config.get(clientOptions, "leader_label_range", leaderLabelRange, "Leader label range\nDefault=" + leaderLabelRange + "\n" + "Maximum distance in blocks at which the leader label is shown.", 1, 64).getInt();
	}
}
