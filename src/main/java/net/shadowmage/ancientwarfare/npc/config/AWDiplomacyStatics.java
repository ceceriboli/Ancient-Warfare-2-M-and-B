package net.shadowmage.ancientwarfare.npc.config;

import net.shadowmage.ancientwarfare.core.config.ModConfiguration;

/**
 * Settings for faction relations and faction membership, stored in config/ancientwarfare/AncientWarfareDiplomacy.cfg
 */
public class AWDiplomacyStatics extends ModConfiguration {
	/* ********************************************SERVER SETTINGS************************************************ */
	public static boolean membershipEnabled = true;
	public static int joinMinStanding = 0;
	public static int memberStanding = 100;
	public static boolean memberInheritStandings = true;
	public static int memberEnemyStanding = -100;
	public static boolean newPlayerNeutralStandings = true;
	public static String kingdomDefaultCulture = "empire";
	public static boolean kingdomsEnabled = true;
	public static double kingdomTributeRatio = 0.5;
	public static String[] kingdomCultures = new String[0];
	public static int kingdomRadius = 64;
	public static int kingdomStartManpower = 20;
	public static int kingdomGarrison = 6;
	public static int kingdomGraceDays = 10;
	public static int memberAllyStanding = 50;
	public static boolean leaveWhenFactionGone = true;
	public static boolean expelOnKillingOwnFaction = true;
	public static int expelStandingPenalty = 50;
	public static int leaderJoinRange = 8;
	public static int leaderDirectionsRange = 64;
	public static double leaderDirectionsChance = 0.25;
	public static boolean announceRelationChanges = true;
	public static boolean announceMembershipChanges = true;
	public static boolean armiesEnabled = true;
	public static int armyOwnedNpcRange = 32;
	public static boolean serviceEnabled = true;
	public static int serviceCheckInterval = 2400;
	public static double campaignChance = 0.3;
	public static double campaignOffenseShare = 0.5;
	public static int serviceCallRange = 1500;
	public static int serviceMusterTicks = 2400;
	public static int serviceMusterTicksPerBlock = 6;
	public static int serviceMusterMaxTicks = 36000;
	public static int serviceMusterRange = 96;
	public static int serviceReminderTicks = 2400;
	public static int servicePresenceRange = 96;
	public static int serviceMinPresenceTicks = 1200;
	public static int serviceRewardStanding = 10;
	public static int serviceMissPenalty = 25;
	public static int serviceMaxStrikes = 3;
	public static int serviceStrikeDecayDays = 7;
	public static boolean serviceExcuseOffline = true;
	public static int serviceKillRewardStanding = 1;
	public static int serviceKillRewardCap = 10;
	public static int chestTheftPenalty = 5;
	public static int chestTheftCooldownTicks = 600;
	public static double servicePlunderRatio = 0.5;
	public static int exhaustionCityPlundered = 15;
	public static int campaignProposalStanding = 120;
	public static int campaignProposalSiegeStanding = 180;
	public static int campaignProposalCooldownDays = 1;
	public static int campaignProposalSiegeCooldownDays = 1;
	public static double campaignProposalFailureRatio = 0.5;
	public static boolean dialogueGui = true;
	public static boolean factionChestsEnabled = true;
	public static int chestTrustStanding = 150;
	public static int chestPlunderTicks = 12000;
	public static boolean diplomacyEnabled = true;
	public static String[] eternalEnemies = {"brigand", "pirate", "monster", "beast", "undead", "demon", "vampire"};
	public static int exhaustionUnitKilled = 1;
	public static int exhaustionLeaderKilled = 10;
	public static int exhaustionCityLost = 40;
	public static int exhaustionAttackFailed = 10;
	public static int exhaustionDecayPerDay = 1;
	public static int backgroundExhaustionPerDay = 2;
	public static int minWarDays = 3;
	public static int peaceExhaustion = 30;
	public static double peaceChancePerDay = 0.25;
	public static int truceDays = 5;
	public static double warChancePerDay = 0.05;
	public static int maxWars = 3;
	public static double allianceChancePerDay = 0.05;
	public static int maxAlliances = 2;
	public static double allianceJoinWarChance = 0.2;
	public static double allianceBreakChancePerDay = 0.1;
	public static int allianceMinDays = 5;
	public static int spawnerMinPlayerRange = 48;
	public static boolean spawnersTriggerInCreative = false;
	public static boolean settlementsEnabled = true;
	public static int settlementDefaultRadius = 48;
	public static boolean settlementUseStructureBounds = true;
	public static int settlementFoundRange = 192;
	public static boolean manpowerBarEnabled = true;
	public static int manpowerBarRange = 16;
	public static double manpowerPerUnit = 2.0;
	public static int manpowerMin = 10;
	public static int manpowerRegenPerDay = 1;
	public static int respawnDelayTicks = 600;
	public static boolean announceSettlementChanges = true;
	public static String fallenGarrison = "flee";
	public static int occupationGarrison = 6;
	public static int conqueredManpowerGrowthPerDay = 1;
	public static int reinforcementIntervalTicks = 1200;
	public static String[] occupationUnitWeights = {"faction.soldier=3", "faction.archer=2"};
	public static String banditFaction = "brigand";
	public static int banditFleeTicks = 6000;
	public static boolean siegesEnabled = true;
	public static int siegeCheckInterval = 2400;
	public static double siegeChance = 0.25;
	public static int siegePlayerRange = 96;
	public static int siegeCooldownDays = 2;
	public static int maxActiveSieges = 1;
	public static int siegeWarmupTicks = 300;
	public static int siegeMaxTicks = 24000;
	public static int siegeWaveStallTicks = 6000;
	public static String attackerBreach = "siege";
	public static String attackerBuild = "siege";
	public static double breachMaxHardness = 5.0;
	public static int breachTicksPerHardness = 40;
	public static String[] breachBlockBlacklist = {"minecraft:bedrock", "minecraft:obsidian", "minecraft:beacon"};
	public static int defenderPerimeterMargin = 24;
	public static int defenderGuardRadius = 10;
	public static int groupAlertRange = 16;
	public static int siegeSpawnMinDistance = 40;
	public static int siegeSpawnMaxDistance = 60;
	public static boolean announceSieges = true;
	public static double raidStrengthRatio = 0.1;
	public static double raidAttackerRatio = 0.05;
	public static double raidAttackerShare = 0.2;
	public static int raidMinAttackers = 10;
	public static int raidMaxAttackers = 30;
	public static int raidWaves = 2;
	public static String[] raidUnitWeights = {"faction.soldier=5", "faction.archer=3", "faction.cavalry=2"};
	public static double fullSiegeShare = 0.1;
	public static int fullSiegeCooldownDays = 20;
	public static double fullSiegeStrengthRatio = 0.5;
	public static double fullSiegeAttackerRatio = 0.3;
	public static double fullSiegeAttackerShare = 0.5;
	public static int fullSiegeMinAttackers = 10;
	public static int fullSiegeMaxAttackers = 60;
	public static int fullSiegeWaves = 4;
	public static String[] fullSiegeUnitWeights = {"faction.soldier=3", "faction.archer=3", "faction.soldier.elite=3", "faction.archer.elite=2",
			"faction.cavalry=2", "faction.mounted_archer=1"};
	public static int fullSiegeEngines = 4;
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

		memberStanding = config.get(serverOptions, "member_standing", memberStanding, "Member standing\nDefault=" + memberStanding + "\n" + "When a player joins a faction, its standing with it is raised to at least this value. When it renounces the faction,\n" + "a positive standing goes back to 0 (not hostile); a negative one stays as it is.", 0, 10000).getInt();
		memberInheritStandings = config.get(serverOptions, "member_inherit_standings", memberInheritStandings, "Members inherit standings\nDefault=" + memberInheritStandings + "\n" + "If true, a member takes the relations of its faction as standings, on joining and whenever they change: at most\n" + "member_enemy_standing with its enemies, at least member_ally_standing with its allies, at least 0 with the others.\n" + "Leaving the faction takes them back: each standing returns to its value before joining, plus the player's own changes.").getBoolean();
		kingdomsEnabled = config.get(serverOptions, "kingdoms_enabled", kingdomsEnabled, "Player kingdoms\nDefault=" + kingdomsEnabled + "\n" + "The first town hall a player places founds their kingdom: a faction of their own with a city around the town hall and a governor. Needs settlements_enabled.").getBoolean();
		kingdomCultures = config.get(serverOptions, "kingdom_cultures", kingdomCultures, "Kingdom cultures\n" + "Factions a kingdom owner can choose as the culture of their kingdom (look, units, dialogues, natural rivals). Empty = every faction that is not one of the eternal_enemies.").getStringList();
		kingdomTributeRatio = config.get(serverOptions, "kingdom_tribute_ratio", kingdomTributeRatio, "Kingdom tribute\nDefault=" + kingdomTributeRatio + "\n" + "The cities a kingdom conquered add this fraction of their maximum manpower to the maximum manpower of its capital (and the same amount as manpower when it grows). The capital pays the campaigns of the kingdom.", 0.0D, 10.0D).getDouble();
		kingdomRadius = config.get(serverOptions, "kingdom_radius", kingdomRadius, "Kingdom city radius\nDefault=" + kingdomRadius + "\n" + "Half the side of the square city founded around a town hall, in blocks. It must not overlap another city.", 8, 256).getInt();
		kingdomStartManpower = config.get(serverOptions, "kingdom_start_manpower", kingdomStartManpower, "Kingdom starting manpower\nDefault=" + kingdomStartManpower + "\n" + "Manpower of a new kingdom city, plus manpower_per_unit for each soldier of the player inside it.", 0, 10000).getInt();
		kingdomGarrison = config.get(serverOptions, "kingdom_garrison", kingdomGarrison, "Kingdom garrison\nDefault=" + kingdomGarrison + "\n" + "Recruits of the kingdom (occupation_unit_weights) come to a kingdom city every reinforcement_interval_ticks until its garrison has this many units. 0 = none.", 0, 200).getInt();
		kingdomGraceDays = config.get(serverOptions, "kingdom_grace_days", kingdomGraceDays, "Kingdom grace days\nDefault=" + kingdomGraceDays + "\n" + "A new kingdom starts at peace with every faction (except the eternal enemies hostile to its culture), with a truce of this many days.", 0, 1000).getInt();
		kingdomDefaultCulture = config.get(serverOptions, "kingdom_default_culture", kingdomDefaultCulture, "Kingdom default culture\nDefault=" + kingdomDefaultCulture + "\n" + "Faction whose look, units and dialogues a new player kingdom uses until its owner chooses another culture.").getString();
		newPlayerNeutralStandings = config.get(serverOptions, "new_player_neutral_standings", newPlayerNeutralStandings, "New player neutral standings\nDefault=" + newPlayerNeutralStandings + "\n" + "A player joining a world for the first time starts with standing 0 with every faction whose standing can change (like /awfaction setall <player> 0).").getBoolean();
		memberEnemyStanding = config.get(serverOptions, "member_enemy_standing", memberEnemyStanding, "Member standing with enemies\nDefault=" + memberEnemyStanding + "\n" + "Highest standing a member keeps with the enemies of its faction.", -10000, -1).getInt();
		memberAllyStanding = config.get(serverOptions, "member_ally_standing", memberAllyStanding, "Member standing with allies\nDefault=" + memberAllyStanding + "\n" + "Lowest standing a member has with the allies of its faction.", 0, 10000).getInt();
		// replaced by member_standing (renouncing resets the standing to 0 instead of taking a penalty)
		config.getCategory(serverOptions).remove("leave_standing_penalty");

		leaveWhenFactionGone = config.get(serverOptions, "leave_when_faction_gone", leaveWhenFactionGone, "Leave a faction that is gone\nDefault=" + leaveWhenFactionGone + "\n" + "If true, when a faction loses its last city (fallen, conquered, dropped) in the world, its members leave it at once, as with\n" + "/awmembership leave but without changing their standing. Only cities known to the game (the ones of /awsettlement list) count.").getBoolean();

		expelOnKillingOwnFaction = config.get(serverOptions, "expel_on_killing_own_faction", expelOnKillingOwnFaction, "Expel members who kill their own faction\nDefault=" + expelOnKillingOwnFaction + "\n" + "If true, a player who kills a member of their own faction (or whose npcs do) is expelled from it.").getBoolean();

		expelStandingPenalty = config.get(serverOptions, "expel_standing_penalty", expelStandingPenalty, "Standing lost when expelled\nDefault=" + expelStandingPenalty + "\n" + "Standing the player loses with the faction that expels them.", 0, 10000).getInt();

		leaderJoinRange = config.get(serverOptions, "leader_join_range", leaderJoinRange, "Leader range to join\nDefault=" + leaderJoinRange + "\n" + "Maximum distance in blocks from a leader of the faction to pledge allegiance to it.", 1, 256).getInt();

		leaderDirectionsRange = config.get(serverOptions, "leader_directions_range", leaderDirectionsRange, "Leader directions range\nDefault=" + leaderDirectionsRange + "\n" + "Friendly faction npcs tell the player where their leader is, if a leader is within this many blocks.\n" + "0 disables the directions.", 0, 512).getInt();

		leaderDirectionsChance = config.get(serverOptions, "leader_directions_chance", leaderDirectionsChance, "Leader directions chance\nDefault=" + leaderDirectionsChance + "\n" + "Chance (0.0 to 1.0) that a friendly faction npc tells the player where their leader is when spoken to.\n" + "0 never, 1 every time.", 0.0D, 1.0D).getDouble();

		announceRelationChanges = config.get(serverOptions, "announce_relation_changes", announceRelationChanges, "Announce relation changes\nDefault=" + announceRelationChanges + "\n" + "If true, all players are told in chat when two factions go to war, make peace or form an alliance.").getBoolean();

		announceMembershipChanges = config.get(serverOptions, "announce_membership_changes", announceMembershipChanges, "Announce membership changes\nDefault=" + announceMembershipChanges + "\n" + "If true, all players are told in chat when a player joins, leaves or is expelled from a faction.").getBoolean();

		armiesEnabled = config.get(serverOptions, "armies_enabled", armiesEnabled, "Own soldiers in the attacks of the faction\nDefault=" + armiesEnabled + "\n" + "If true, when a member of a faction takes part in an attack of its faction, its own Ancient Warfare combat npcs that\n" + "follow it join the assault on the city leader, and follow it again when the attack is over (/awarmy status).").getBoolean();

		armyOwnedNpcRange = config.get(serverOptions, "army_owned_npc_range", armyOwnedNpcRange, "Own soldiers range\nDefault=" + armyOwnedNpcRange + "\n" + "The player's own Ancient Warfare combat npcs within this many blocks that follow it (shift + right click with an\n" + "empty hand) join the assault when it takes part in an attack of its faction.", 0, 256).getInt();

		// the levy raised from a ruler was replaced by the military service
		for (String obsolete : new String[] {"army_size", "army_unit_weights", "army_charge_range"}) {
			config.getCategory(serverOptions).remove(obsolete);
		}

		serviceEnabled = config.get(serverOptions, "service_enabled", serviceEnabled, "Military service\nDefault=" + serviceEnabled + "\n" + "If true, a member of a faction serves it as in Mount & Blade: when its faction attacks an enemy city, or a city of\n" + "its faction is attacked, the player is called to arms and must go there before the muster is over. The attack waits\n" + "for a called player (state muster). Missing a call gives a strike and a growing standing penalty with the faction,\n" + "service_max_strikes strikes get the player expelled. Attacks that start near players call the members there too.").getBoolean();

		serviceCheckInterval = config.get(serverOptions, "service_check_interval", serviceCheckInterval, "Campaign check interval\nDefault=" + serviceCheckInterval + "\n" + "Ticks (20 per second) between two checks for a new campaign (an attack of the faction of an online member, or on a\n" + "city of its faction, anywhere within service_call_range).", 20, 720000).getInt();

		campaignChance = config.get(serverOptions, "campaign_chance", campaignChance, "Campaign chance\nDefault=" + campaignChance + "\n" + "Chance (0.0 to 1.0), at each check, that a campaign starts for an online member without a call. Limited by\n" + "max_active_sieges and by the cooldown of the cities.", 0.0D, 1.0D).getDouble();

		campaignOffenseShare = config.get(serverOptions, "campaign_offense_share", campaignOffenseShare, "Offensive campaigns\nDefault=" + campaignOffenseShare + "\n" + "Chance (0.0 to 1.0) that a campaign is an attack of the member's faction on an enemy city (at war), otherwise it\n" + "is an attack of an enemy on a city of the member's faction.", 0.0D, 1.0D).getDouble();

		serviceCallRange = config.get(serverOptions, "service_call_range", serviceCallRange, "Call range\nDefault=" + serviceCallRange + "\n" + "Campaigns are on cities within this many blocks of the called member, and members within this distance are called.", 16, 100000).getInt();

		serviceMusterTicks = config.get(serverOptions, "service_muster_ticks", serviceMusterTicks, "Muster time (base)\nDefault=" + serviceMusterTicks + "\n" + "Ticks (20 per second) a campaign waits for a called player, plus service_muster_ticks_per_block for each block between\n" + "the city and the farthest called player, at most service_muster_max_ticks. Then an attack of the player's faction is\n" + "called off (unsupported) and an attack on a city of the player's faction plunders it (it loses manpower, it never falls).", 200, 720000).getInt();

		serviceMusterTicksPerBlock = config.get(serverOptions, "service_muster_ticks_per_block", serviceMusterTicksPerBlock, "Muster time per block\nDefault=" + serviceMusterTicksPerBlock + "\n" + "Ticks added to the muster time for each block of distance (6 = 0.3 seconds a block, a walk with some margin).", 0, 200).getInt();

		serviceMusterMaxTicks = config.get(serverOptions, "service_muster_max_ticks", serviceMusterMaxTicks, "Muster time (maximum)\nDefault=" + serviceMusterMaxTicks + "\n" + "Longest muster time in ticks, whatever the distance.", 200, 720000).getInt();

		serviceMusterRange = config.get(serverOptions, "service_muster_range", serviceMusterRange, "Muster range\nDefault=" + serviceMusterRange + "\n" + "A campaign begins when a called player comes within this many blocks of the city leader.", 16, 512).getInt();

		serviceReminderTicks = config.get(serverOptions, "service_reminder_ticks", serviceReminderTicks, "Call reminder\nDefault=" + serviceReminderTicks + "\n" + "Ticks (20 per second) between two reminders (distance, direction, time left) to a called player during the muster.\n" + "0 = no reminders.", 0, 720000).getInt();

		servicePresenceRange = config.get(serverOptions, "service_presence_range", servicePresenceRange, "Presence range\nDefault=" + servicePresenceRange + "\n" + "A called player within this many blocks of the city leader takes part in the attack.", 16, 512).getInt();

		serviceMinPresenceTicks = config.get(serverOptions, "service_min_presence_ticks", serviceMinPresenceTicks, "Service time\nDefault=" + serviceMinPresenceTicks + "\n" + "Ticks (20 per second) a called player must take part in the attack to have served (or be there when it ends).", 0, 720000).getInt();

		serviceRewardStanding = config.get(serverOptions, "service_reward_standing", serviceRewardStanding, "Service reward\nDefault=" + serviceRewardStanding + "\n" + "Standing a player gains with its faction for answering a call (and one strike is forgiven).", 0, 10000).getInt();

		serviceMissPenalty = config.get(serverOptions, "service_miss_penalty", serviceMissPenalty, "Missed call penalty\nDefault=" + serviceMissPenalty + "\n" + "Standing a player loses with its faction for a missed call, times its strikes (25, 50, 75...).", 0, 10000).getInt();

		serviceMaxStrikes = config.get(serverOptions, "service_max_strikes", serviceMaxStrikes, "Strikes before expulsion\nDefault=" + serviceMaxStrikes + "\n" + "A player who misses this many calls (strikes) is expelled from its faction as a deserter (expel_standing_penalty).\n" + "0 = never expelled.", 0, 100).getInt();

		serviceStrikeDecayDays = config.get(serverOptions, "service_strike_decay_days", serviceStrikeDecayDays, "Strike forgiveness\nDefault=" + serviceStrikeDecayDays + "\n" + "Minecraft days without missed calls after which one strike is forgiven. 0 = never.", 0, 10000).getInt();

		serviceExcuseOffline = config.get(serverOptions, "service_excuse_offline", serviceExcuseOffline, "Offline players excused\nDefault=" + serviceExcuseOffline + "\n" + "If true, a called player who goes offline is excused (no strike). Players offline when a campaign starts are\n" + "never called.").getBoolean();

		serviceKillRewardStanding = config.get(serverOptions, "service_kill_reward_standing", serviceKillRewardStanding, "Standing for a kill in service\nDefault=" + serviceKillRewardStanding + "\n" + "Standing a member gains with its faction for each enemy it (or one of its own soldiers) kills while serving in an attack of\n" + "its faction (called, near the city, the attack going on): the defenders if its faction attacks, the attackers if it defends.\n" + "0 = no reward.", 0, 1000).getInt();

		serviceKillRewardCap = config.get(serverOptions, "service_kill_reward_cap", serviceKillRewardCap, "Rewarded kills per attack\nDefault=" + serviceKillRewardCap + "\n" + "Kills that give standing to a member in one attack (at most service_kill_reward_cap x service_kill_reward_standing).\n" + "0 = no reward.", 0, 10000).getInt();

		chestTheftPenalty = config.get(serverOptions, "chest_theft_penalty", chestTheftPenalty, "Caught stealing from its own faction\nDefault=" + chestTheftPenalty + "\n" + "Standing a member loses with its faction when a faction npc sees it try to open a loot chest of the faction without the\n" + "trust needed (chest_trust_standing). 0 = no penalty.", 0, 10000).getInt();

		chestTheftCooldownTicks = config.get(serverOptions, "chest_theft_cooldown_ticks", chestTheftCooldownTicks, "Caught stealing: pause\nDefault=" + chestTheftCooldownTicks + "\n" + "Ticks (20 per second) between two penalties for the same player, so that clicking again does not take all its standing.", 0, 72000).getInt();

		servicePlunderRatio = config.get(serverOptions, "service_plunder_ratio", servicePlunderRatio, "Plunder\nDefault=" + servicePlunderRatio + "\n" + "Manpower a city loses when nobody comes to defend it = attackers of the campaign x this value.", 0.0D, 100.0D).getDouble();

		exhaustionCityPlundered = config.get(serverOptions, "exhaustion_city_plundered", exhaustionCityPlundered, "Exhaustion: city plundered\nDefault=" + exhaustionCityPlundered + "\n" + "War exhaustion a faction gets when one of its cities is plundered because nobody came to defend it.", 0, 10000).getInt();

		campaignProposalStanding = config.get(serverOptions, "campaign_proposal_standing", campaignProposalStanding, "Campaign proposal: raid\nDefault=" + campaignProposalStanding + "\n" + "A member can propose a campaign to the ruler of a city of its faction ([Propose a campaign] in the dialogue): an\n" + "attack on an enemy city at war, known and within service_call_range. The ruler accepts a raid if the member's standing\n" + "is at least this value and its faction is not exhausted by that war (exhaustion below peace_exhaustion).", -10000, 10000).getInt();

		campaignProposalSiegeStanding = config.get(serverOptions, "campaign_proposal_siege_standing", campaignProposalSiegeStanding, "Campaign proposal: full siege\nDefault=" + campaignProposalSiegeStanding + "\n" + "Standing a member needs for the ruler to accept a full siege (only if the city had no full siege for\n" + "full_siege_cooldown_days).", -10000, 10000).getInt();

		campaignProposalCooldownDays = config.get(serverOptions, "campaign_proposal_cooldown_days", campaignProposalCooldownDays, "Campaign proposal cooldown\nDefault=" + campaignProposalCooldownDays + "\n" + "Minecraft days after an accepted proposal before the same member can propose another campaign.", 0, 1000).getInt();

		campaignProposalSiegeCooldownDays = config.get(serverOptions, "campaign_proposal_siege_cooldown_days", campaignProposalSiegeCooldownDays, "Proposal: full siege pause\nDefault=" + campaignProposalSiegeCooldownDays + "\n" + "Minecraft days a city must have gone without a full siege (counted on the real ticks of the world) before a member can\n" + "propose one to its ruler. Automatic full sieges keep waiting full_siege_cooldown_days.", 0, 1000).getInt();

		campaignProposalFailureRatio = config.get(serverOptions, "campaign_proposal_failure_ratio", campaignProposalFailureRatio, "Proposal: penalty for a lost campaign\nDefault=" + campaignProposalFailureRatio + "\n" + "A campaign proposed by a member that is lost (the city repels it or it is lifted) costs the proposer this fraction of the\n" + "standing needed to propose it: with 120 for a raid and 180 for a full siege and 0.5, 60 and 90. 0 = no penalty.", 0.0D, 10.0D).getDouble();

		dialogueGui = config.get(serverOptions, "dialogue_gui", dialogueGui, "Dialogue window\nDefault=" + dialogueGui + "\n" + "If true, a ruling leader that has something to offer (pledge allegiance, propose a campaign, renounce) opens a\n" + "dialogue window with buttons. If false, it speaks in chat with clickable texts.").getBoolean();

		factionChestsEnabled = config.get(serverOptions, "faction_chests_enabled", factionChestsEnabled, "Faction chests for members\nDefault=" + factionChestsEnabled + "\n" + "If true, the npcs of a faction let its members open the loot chests of its structures (otherwise locked while a\n" + "faction npc sees the player) once their standing reaches chest_trust_standing, and the players who served in the\n" + "attack that conquered a city plunder its chests freely for chest_plunder_ticks.").getBoolean();

		chestTrustStanding = config.get(serverOptions, "chest_trust_standing", chestTrustStanding, "Chest trust\nDefault=" + chestTrustStanding + "\n" + "Standing a member needs with its faction to open its chests in front of its npcs (joining gives member_standing,\n" + "each answered call to arms service_reward_standing).", -10000, 10000).getInt();

		chestPlunderTicks = config.get(serverOptions, "chest_plunder_ticks", chestPlunderTicks, "War booty\nDefault=" + chestPlunderTicks + "\n" + "Ticks (20 per second) the players who served in the attack that conquered a city can open all its chests, whatever\n" + "their standing and whoever sees them. 0 = no war booty.", 0, 720000).getInt();

		diplomacyEnabled = config.get(serverOptions, "diplomacy_enabled", diplomacyEnabled, "Dynamic diplomacy\nDefault=" + diplomacyEnabled + "\n" + "If true, factions hostile in factions.json start at war (once per world), and once per Minecraft day wars can end\n" + "in a truce when one side is exhausted by its losses, rivals can go to war again after the truce, and factions\n" + "with a common enemy can ally (allies may join each other's wars). If false, relations change only with /awrelation.").getBoolean();

		eternalEnemies = config.get(serverOptions, "eternal_enemies", eternalEnemies, "Eternal enemies\n" + "Factions that never make peace nor ally (they stay at war with every faction hostile to them).").getStringList();

		exhaustionUnitKilled = config.get(serverOptions, "exhaustion_unit_killed", exhaustionUnitKilled, "Exhaustion: unit killed\nDefault=" + exhaustionUnitKilled + "\n" + "War exhaustion a faction gets when one of its npcs is killed by the enemy (an npc of the enemy faction, a player\n" + "member of it or an npc of such a player).", 0, 10000).getInt();

		exhaustionLeaderKilled = config.get(serverOptions, "exhaustion_leader_killed", exhaustionLeaderKilled, "Exhaustion: leader killed\nDefault=" + exhaustionLeaderKilled + "\n" + "War exhaustion a faction gets when one of its leaders is killed by the enemy.", 0, 10000).getInt();

		exhaustionCityLost = config.get(serverOptions, "exhaustion_city_lost", exhaustionCityLost, "Exhaustion: city lost\nDefault=" + exhaustionCityLost + "\n" + "War exhaustion a faction gets when the enemy conquers one of its cities.", 0, 10000).getInt();

		exhaustionAttackFailed = config.get(serverOptions, "exhaustion_attack_failed", exhaustionAttackFailed, "Exhaustion: failed attack\nDefault=" + exhaustionAttackFailed + "\n" + "War exhaustion the attacking faction gets when its raid or siege is repelled or lifted.", 0, 10000).getInt();

		exhaustionDecayPerDay = config.get(serverOptions, "exhaustion_decay_per_day", exhaustionDecayPerDay, "Exhaustion recovery\nDefault=" + exhaustionDecayPerDay + "\n" + "War exhaustion each side of a war recovers every Minecraft day.", 0, 10000).getInt();

		backgroundExhaustionPerDay = config.get(serverOptions, "background_exhaustion_per_day", backgroundExhaustionPerDay, "Background war exhaustion\nDefault=" + backgroundExhaustionPerDay + "\n" + "Every Minecraft day, each side of a war between two factions that both have cities gets 0 to this much exhaustion\n" + "(the fighting away from players), so that every war can end sooner or later.", 0, 10000).getInt();

		minWarDays = config.get(serverOptions, "min_war_days", minWarDays, "Minimum war length\nDefault=" + minWarDays + "\n" + "Minecraft days a war lasts at least before peace is possible.", 0, 10000).getInt();

		peaceExhaustion = config.get(serverOptions, "peace_exhaustion", peaceExhaustion, "Peace threshold\nDefault=" + peaceExhaustion + "\n" + "Peace becomes possible when the war exhaustion of one side reaches this value.", 0, 100000).getInt();

		peaceChancePerDay = config.get(serverOptions, "peace_chance_per_day", peaceChancePerDay, "Peace chance\nDefault=" + peaceChancePerDay + "\n" + "Chance per Minecraft day of a truce once peace is possible, +0.01 for each point of exhaustion above the threshold\n" + "(at most 0.9).", 0.0D, 1.0D).getDouble();

		truceDays = config.get(serverOptions, "truce_days", truceDays, "Truce length\nDefault=" + truceDays + "\n" + "Minecraft days after a peace (or the end of an alliance) during which the two factions cannot go to war or ally.", 0, 10000).getInt();

		warChancePerDay = config.get(serverOptions, "war_chance_per_day", warChancePerDay, "War chance\nDefault=" + warChancePerDay + "\n" + "Chance per Minecraft day that two rival factions (hostile in factions.json) at peace, with cities and with the truce\n" + "over, go to war again. Factions friendly in factions.json never go to war with each other.", 0.0D, 1.0D).getDouble();

		maxWars = config.get(serverOptions, "max_wars", maxWars, "Maximum fronts\nDefault=" + maxWars + "\n" + "A faction already at war with this many factions that have cities does not start a new war (allies can still drag\n" + "it into one).", 0, 100).getInt();

		allianceChancePerDay = config.get(serverOptions, "alliance_chance_per_day", allianceChancePerDay, "Alliance chance\nDefault=" + allianceChancePerDay + "\n" + "Chance per Minecraft day that two factions at peace, with cities and a common enemy, form an alliance.", 0.0D, 1.0D).getDouble();

		maxAlliances = config.get(serverOptions, "max_alliances", maxAlliances, "Maximum alliances\nDefault=" + maxAlliances + "\n" + "Maximum number of alliances of a faction.", 0, 100).getInt();

		allianceJoinWarChance = config.get(serverOptions, "alliance_join_war_chance", allianceJoinWarChance, "Call to arms\nDefault=" + allianceJoinWarChance + "\n" + "Chance per Minecraft day that a faction joins the war of an ally against an enemy it is at peace with.", 0.0D, 1.0D).getDouble();

		allianceBreakChancePerDay = config.get(serverOptions, "alliance_break_chance_per_day", allianceBreakChancePerDay, "Alliance end\nDefault=" + allianceBreakChancePerDay + "\n" + "Chance per Minecraft day that an alliance without common enemies ends.", 0.0D, 1.0D).getDouble();

		allianceMinDays = config.get(serverOptions, "alliance_min_days", allianceMinDays, "Minimum alliance length\nDefault=" + allianceMinDays + "\n" + "Minecraft days an alliance lasts at least.", 0, 10000).getInt();

		spawnerMinPlayerRange = config.get(serverOptions, "spawner_min_player_range", spawnerMinPlayerRange, "Structure spawner range\nDefault=" + spawnerMinPlayerRange + "\n" + "Ancient Warfare structure spawners create their npcs (leaders included, so also cities) when a player is within\n" + "their own range, set in each structure template (often 16 blocks, a cube around the spawner). A player within this\n" + "distance (a sphere) also triggers every spawner. 0 = only the range of each template. At most 128: npcs that can\n" + "despawn vanish at once when no player is within 128 blocks.", 0, 128).getInt();

		spawnersTriggerInCreative = config.get(serverOptions, "spawners_trigger_in_creative", spawnersTriggerInCreative, "Structure spawners in creative\nDefault=" + spawnersTriggerInCreative + "\n" + "If true, players in creative mode also make structure spawners create their npcs (and so found cities), useful\n" + "for testing. If false (Ancient Warfare behaviour), only players in survival or adventure mode do. Players in\n" + "spectator mode never do.").getBoolean();

		settlementsEnabled = config.get(serverOptions, "settlements_enabled", settlementsEnabled, "Faction cities and manpower\nDefault=" + settlementsEnabled + "\n" + "If true, every faction leader founds a city with the faction npcs around it. While the leader lives, killed units respawn\n" + "using the city's manpower; when the leader dies the city falls and is abandoned.").getBoolean();

		settlementDefaultRadius = config.get(serverOptions, "settlement_default_radius", settlementDefaultRadius, "City radius outside structures\nDefault=" + settlementDefaultRadius + "\n" + "Cities take the bounds of the Ancient Warfare town or structure their leader is in. A leader outside of any\n" + "town or structure founds a city of this radius (in blocks) around itself. With settlement_use_structure_bounds=false\n" + "every city uses this radius. Applies to cities founded after the change.", 8, 256).getInt();

		settlementUseStructureBounds = config.get(serverOptions, "settlement_use_structure_bounds", settlementUseStructureBounds, "Cities use structure bounds\nDefault=" + settlementUseStructureBounds + "\n" + "If true, a city takes the bounds of the Ancient Warfare town or structure its leader is in. If false, every city is\n" + "a square of settlement_default_radius blocks around its leader.").getBoolean();

		settlementFoundRange = config.get(serverOptions, "settlement_found_range", settlementFoundRange, "Cities born from their structure\nDefault=" + settlementFoundRange + "\n" + "A structure that has the spawner of a faction leader becomes a city as soon as it is fully loaded within this many blocks of a\n" + "player (about the distance up to which the world is loaded and structures are generated: 12 chunks), without waiting for\n" + "the leader to appear, which needs a player closer (spawner_min_player_range). The leader is linked to the city when it\n" + "comes. 0 = cities only appear when their leader does.", 0, 512).getInt();

		manpowerBarEnabled = config.get(serverOptions, "manpower_bar_enabled", manpowerBarEnabled, "Manpower bar\nDefault=" + manpowerBarEnabled + "\n" + "If true, players inside a city (or near it) see a boss bar with the city's remaining manpower.").getBoolean();

		manpowerBarRange = config.get(serverOptions, "manpower_bar_range", manpowerBarRange, "Manpower bar range\nDefault=" + manpowerBarRange + "\n" + "The manpower bar is shown to players within this many blocks of the city bounds (0 = only inside the city).", 0, 256).getInt();

		manpowerPerUnit = config.get(serverOptions, "manpower_per_unit", manpowerPerUnit, "Manpower per garrison unit\nDefault=" + manpowerPerUnit + "\n" + "Starting manpower of a city = number of units in its garrison when founded x this value (at least manpower_min).\n" + "Each respawn of a killed unit costs 1 manpower.", 0.0D, 100.0D).getDouble();

		manpowerMin = config.get(serverOptions, "manpower_min", manpowerMin, "Minimum manpower\nDefault=" + manpowerMin + "\n" + "Minimum starting manpower of a city.", 0, 10000).getInt();

		manpowerRegenPerDay = config.get(serverOptions, "manpower_regen_per_day", manpowerRegenPerDay, "Manpower regeneration\nDefault=" + manpowerRegenPerDay + "\n" + "Manpower a city regains every Minecraft day, up to its starting manpower, while it is not under siege.", 0, 10000).getInt();

		respawnDelayTicks = config.get(serverOptions, "respawn_delay_ticks", respawnDelayTicks, "Respawn delay\nDefault=" + respawnDelayTicks + "\n" + "Ticks (20 per second) before a killed garrison unit respawns in its city.", 20, 72000).getInt();

		announceSettlementChanges = config.get(serverOptions, "announce_settlement_changes", announceSettlementChanges, "Announce fallen cities\nDefault=" + announceSettlementChanges + "\n" + "If true, all players are told in chat when a city falls because its leader died.").getBoolean();

		fallenGarrison = config.get(serverOptions, "fallen_garrison", fallenGarrison, "Garrison of a fallen city\nDefault=" + fallenGarrison + "\n" + "What the remaining units of a city do when its leader dies (or the city is conquered):\n" + "flee = they keep their faction and run away for bandit_flee_ticks without attacking, then stay where they are, without city\n" + "bandits = like flee, but they join bandit_faction (they keep their equipment)\n" + "stay = they stay where they are in their own faction, without city and without respawn\n" + "disband = they vanish", new String[] {"flee", "bandits", "stay", "disband"}).getString();

		occupationGarrison = config.get(serverOptions, "occupation_garrison", occupationGarrison, "Occupation garrison\nDefault=" + occupationGarrison + "\n" + "When a city is conquered, the attackers inside it become its garrison. If they are fewer than this, units of the\n" + "winning faction appear at once around the new governor up to this number. 0 = none.", 0, 100).getInt();

		conqueredManpowerGrowthPerDay = config.get(serverOptions, "conquered_manpower_growth_per_day", conqueredManpowerGrowthPerDay, "Conquered city growth\nDefault=" + conqueredManpowerGrowthPerDay + "\n" + "A conquered city starts with the manpower of its new garrison. Every Minecraft day it is not under attack, its\n" + "maximum manpower grows by this much, up to the maximum the city had before the conquest. 0 = it never grows.", 0, 10000).getInt();

		reinforcementIntervalTicks = config.get(serverOptions, "reinforcement_interval_ticks", reinforcementIntervalTicks, "Conquered city reinforcements\nDefault=" + reinforcementIntervalTicks + "\n" + "Ticks (20 per second) between two new recruits in a conquered city, until its garrison is back to the size it had\n" + "before the conquest (units of occupation_unit_weights, no manpower cost). Only while the whole city is loaded and\n" + "not under attack. 0 = no reinforcements.", 0, 720000).getInt();

		occupationUnitWeights = config.get(serverOptions, "occupation_unit_weights", occupationUnitWeights, "Occupation units\n" + "Units of the occupation garrison, as npc type=weight (same types as raid_unit_weights).").getStringList();

		banditFaction = config.get(serverOptions, "bandit_faction", banditFaction, "Bandit faction\nDefault=" + banditFaction + "\n" + "Faction the remaining units of a fallen city join with fallen_garrison=bandits. They keep their equipment.").getString();

		banditFleeTicks = config.get(serverOptions, "bandit_flee_ticks", banditFleeTicks, "Survivors flee\nDefault=" + banditFleeTicks + "\n" + "Ticks (20 per second) the survivors of a fallen city (fallen_garrison flee or bandits) run away from the city and\n" + "from anyone close, without attacking anyone. Then they settle where they are. 0 = they never flee.", 0, 720000).getInt();

		siegesEnabled = config.get(serverOptions, "sieges_enabled", siegesEnabled, "Raids and sieges\nDefault=" + siegesEnabled + "\n" + "If true, factions at war (declared with /awrelation war) attack each other's cities near players: waves of\n" + "attackers spawn near the city and march on its leader. Most attacks are raids (raid_* settings), some are full\n" + "sieges with siege engines (full_siege_* settings). If false, attacks start only with /awsiege start.").getBoolean();

		siegeCheckInterval = config.get(serverOptions, "siege_check_interval", siegeCheckInterval, "Attack check interval\nDefault=" + siegeCheckInterval + "\n" + "Ticks (20 per second) between two checks for a new raid or siege. At each check at most one attack starts.", 20, 720000).getInt();

		siegeChance = config.get(serverOptions, "siege_chance", siegeChance, "Attack chance\nDefault=" + siegeChance + "\n" + "Chance (0.0 to 1.0) that a city that can be attacked is attacked (raid or full siege) at a check.", 0.0D, 1.0D).getDouble();

		siegePlayerRange = config.get(serverOptions, "siege_player_range", siegePlayerRange, "Siege player range\nDefault=" + siegePlayerRange + "\n" + "Only cities with a player within this many blocks of their center are besieged. Players within this range\n" + "also see the siege bar.", 16, 512).getInt();

		siegeCooldownDays = config.get(serverOptions, "siege_cooldown_days", siegeCooldownDays, "Siege cooldown\nDefault=" + siegeCooldownDays + "\n" + "Minecraft days after a raid or siege ends (or after the city is founded) before the city can be attacked again.", 0, 1000).getInt();

		maxActiveSieges = config.get(serverOptions, "max_active_sieges", maxActiveSieges, "Maximum attacks\nDefault=" + maxActiveSieges + "\n" + "The automatic check starts no new raid or siege while this many are going on in the whole world (on any city,\n" + "those started with /awsiege start included). /awsiege start itself is not limited. A city has one attack at a time.", 0, 100).getInt();

		siegeWarmupTicks = config.get(serverOptions, "siege_warmup_ticks", siegeWarmupTicks, "Siege warm up\nDefault=" + siegeWarmupTicks + "\n" + "Ticks (20 per second) of warning before the first wave and between two waves.", 20, 72000).getInt();

		siegeMaxTicks = config.get(serverOptions, "siege_max_ticks", siegeMaxTicks, "Siege duration\nDefault=" + siegeMaxTicks + "\n" + "Ticks (20 per second) after which a siege is lifted and its attackers leave. Time only runs while the city is loaded.", 1200, 720000).getInt();

		siegeWaveStallTicks = config.get(serverOptions, "siege_wave_stall_ticks", siegeWaveStallTicks, "Stalled wave\nDefault=" + siegeWaveStallTicks + "\n" + "Ticks (20 per second) a wave can go on without any attacker or defender killed. Then the wave withdraws and the\n" + "next one comes, or, after the last wave, the raid or siege is lifted. Time only runs while the city is loaded. 0 = never.", 0, 720000).getInt();

		attackerBreach = config.get(serverOptions, "attacker_breach", attackerBreach, "Attackers break blocks\nDefault=" + attackerBreach + "\n" + "Melee attackers that cannot get closer to the city leader for a few seconds break the block in front of them\n" + "(walls, iron doors, gates), within breach_max_hardness and breach_block_blacklist, if the mobGriefing gamerule allows it.\n" + "none = never, siege = only in full sieges, all = in raids and full sieges.", new String[] {"none", "siege", "all"}).getString();

		attackerBuild = config.get(serverOptions, "attacker_build", attackerBuild, "Attackers build ladders and bridges\nDefault=" + attackerBuild + "\n" + "Attackers that cannot get closer to the city leader for a few seconds place ladders to climb a wall (when the leader is\n" + "higher) or planks to bridge a gap (as the raiders of MineColonies), if the mobGriefing gamerule allows it. Everything is\n" + "taken away when the siege ends. none = never, siege = only in full sieges, all = in raids and full sieges.", new String[] {"none", "siege", "all"}).getString();

		breachMaxHardness = config.get(serverOptions, "breach_max_hardness", breachMaxHardness, "Breach hardness\nDefault=" + breachMaxHardness + "\n" + "Hardest block attackers can break (planks 2, cobblestone 2, stone bricks 1.5, iron door 5, obsidian 50).", 0.0D, 100.0D).getDouble();

		breachTicksPerHardness = config.get(serverOptions, "breach_ticks_per_hardness", breachTicksPerHardness, "Breach time\nDefault=" + breachTicksPerHardness + "\n" + "Ticks (20 per second) to break a block = its hardness x this value (at least 1 second).", 1, 2000).getInt();

		breachBlockBlacklist = config.get(serverOptions, "breach_block_blacklist", breachBlockBlacklist, "Unbreakable blocks\n" + "Blocks attackers never break (registry names). Blocks with contents (chests, spawners...) are never broken anyway.").getStringList();

		defenderPerimeterMargin = config.get(serverOptions, "defender_perimeter_margin", defenderPerimeterMargin, "Defended perimeter\nDefault=" + defenderPerimeterMargin + "\n" + "During a raid or siege the garrison goes after the attackers inside the city bounds grown by this many blocks.", 0, 128).getInt();

		defenderGuardRadius = config.get(serverOptions, "defender_guard_radius", defenderGuardRadius, "Leader guards\nDefault=" + defenderGuardRadius + "\n" + "Garrison units whose post (home) is within this many blocks of the leader stay to guard it instead of going out.", 0, 128).getInt();

		groupAlertRange = config.get(serverOptions, "group_alert_range", groupAlertRange, "Group alert\nDefault=" + groupAlertRange + "\n" + "When an attacker or a city unit is hurt, its companions within this many blocks without an enemy go after the one\n" + "who hurt it. 0 = off.", 0, 64).getInt();

		siegeSpawnMinDistance = config.get(serverOptions, "siege_spawn_min_distance", siegeSpawnMinDistance, "Wave distance (minimum)\nDefault=" + siegeSpawnMinDistance + "\n" + "Minimum distance in blocks from the city leader where waves spawn.", 8, 112).getInt();

		siegeSpawnMaxDistance = config.get(serverOptions, "siege_spawn_max_distance", siegeSpawnMaxDistance, "Wave distance (maximum)\nDefault=" + siegeSpawnMaxDistance + "\n" + "Maximum distance in blocks from the city leader where waves spawn.", 8, 112).getInt();

		announceSieges = config.get(serverOptions, "announce_sieges", announceSieges, "Announce raids and sieges\nDefault=" + announceSieges + "\n" + "If true, all players are told in chat when a raid or siege starts and ends.").getBoolean();

		String unitTypes = "Units of the attacking faction in a wave, as npc type=weight. Types are the faction npc names without the\n" + "'ancientwarfarenpc:' prefix: faction.soldier, faction.soldier.elite, faction.archer, faction.archer.elite,\n" + "faction.cavalry (mounted soldier, spawns with its horse), faction.mounted_archer.";

		raidStrengthRatio = config.get(serverOptions, "raid_strength_ratio", raidStrengthRatio, "Raid size\nDefault=" + raidStrengthRatio + "\n" + "Total attackers of a raid = (garrison units loaded + manpower of the city) x this value\n" + "+ (manpower of all the known cities of the attacking faction in the dimension) x raid_attacker_ratio, at most\n" + "raid_attacker_share of that manpower, limited by raid_min_attackers and raid_max_attackers, and split into raid_waves waves.", 0.0D, 100.0D).getDouble();

		raidAttackerRatio = config.get(serverOptions, "raid_attacker_ratio", raidAttackerRatio, "Raid size: attacking faction\nDefault=" + raidAttackerRatio + "\n" + "A raid also grows with the strength of the attacking faction: its manpower (all its known cities in the dimension, the ones\n" + "of /awsettlement list) x this value is added to the attackers from raid_strength_ratio.", 0.0D, 100.0D).getDouble();

		raidAttackerShare = config.get(serverOptions, "raid_attacker_share", raidAttackerShare, "Raid size: most of the manpower\nDefault=" + raidAttackerShare + "\n" + "A raid has at most this share of the manpower of the attacking faction as attackers (never less than raid_min_attackers),\n" + "whatever the strength of the besieged city: a weak faction cannot send a big raid.", 0.0D, 100.0D).getDouble();

		raidMinAttackers = config.get(serverOptions, "raid_min_attackers", raidMinAttackers, "Raid minimum attackers\nDefault=" + raidMinAttackers + "\n" + "Minimum total number of attackers of a raid.", 1, 1000).getInt();

		raidMaxAttackers = config.get(serverOptions, "raid_max_attackers", raidMaxAttackers, "Raid maximum attackers\nDefault=" + raidMaxAttackers + "\n" + "Maximum total number of attackers of a raid.", 1, 1000).getInt();

		raidWaves = config.get(serverOptions, "raid_waves", raidWaves, "Raid waves\nDefault=" + raidWaves + "\n" + "Number of waves of a raid. The next wave comes when the previous one is wiped out.", 1, 100).getInt();

		raidUnitWeights = config.get(serverOptions, "raid_unit_weights", raidUnitWeights, "Raid units\n" + unitTypes).getStringList();

		fullSiegeShare = config.get(serverOptions, "full_siege_share", fullSiegeShare, "Full siege share\nDefault=" + fullSiegeShare + "\n" + "Chance (0.0 to 1.0) that an automatic attack is a full siege instead of a raid, if the city had no full siege\n" + "for full_siege_cooldown_days. Only factions with at least one known city (in /awsettlement list) lay full sieges,\n" + "the others only raid.", 0.0D, 1.0D).getDouble();

		fullSiegeCooldownDays = config.get(serverOptions, "full_siege_cooldown_days", fullSiegeCooldownDays, "Full siege cooldown\nDefault=" + fullSiegeCooldownDays + "\n" + "Minecraft days after a full siege ends (or after the city is founded) before the city can have another full siege.\n" + "Raids can still come in the meantime.", 0, 1000).getInt();

		fullSiegeStrengthRatio = config.get(serverOptions, "full_siege_strength_ratio", fullSiegeStrengthRatio, "Full siege size\nDefault=" + fullSiegeStrengthRatio + "\n" + "Total attackers of a full siege (siege engineers not counted) = (garrison units loaded + manpower) x this value,\n" + "limited by full_siege_min_attackers and full_siege_max_attackers, and split into full_siege_waves waves.", 0.0D, 100.0D).getDouble();

		fullSiegeAttackerRatio = config.get(serverOptions, "full_siege_attacker_ratio", fullSiegeAttackerRatio, "Full siege size: attacking faction\nDefault=" + fullSiegeAttackerRatio + "\n" + "A full siege also grows with the strength of the attacking faction: (manpower of all its known cities in the dimension,\n" + "the ones of /awsettlement list) x this value is added to the attackers from full_siege_strength_ratio, before the limits\n" + "full_siege_min_attackers and full_siege_max_attackers. 0 = only the strength of the city counts. Raids are not affected.", 0.0D, 100.0D).getDouble();

		fullSiegeAttackerShare = config.get(serverOptions, "full_siege_attacker_share", fullSiegeAttackerShare, "Full siege size: most of the manpower\nDefault=" + fullSiegeAttackerShare + "\n" + "A full siege has at most this share of the manpower of all the known cities of the attacking faction in the dimension as\n" + "attackers (but never less than full_siege_min_attackers), whatever the strength of the besieged city: a weak faction cannot\n" + "send a big army. 0.5 = half of its manpower. Raids are not affected.", 0.0D, 100.0D).getDouble();

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
