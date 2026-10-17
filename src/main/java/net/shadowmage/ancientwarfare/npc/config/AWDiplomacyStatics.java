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
	public static boolean settlementsEnabled = true;
	public static int settlementDefaultRadius = 48;
	public static double manpowerPerUnit = 2.0;
	public static int manpowerMin = 10;
	public static int manpowerRegenPerDay = 1;
	public static int respawnDelayTicks = 600;
	public static boolean announceSettlementChanges = true;

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

		settlementsEnabled = config.get(serverOptions, "settlements_enabled", settlementsEnabled, "Faction cities and manpower\nDefault=" + settlementsEnabled + "\n" + "If true, every faction leader founds a city with the faction npcs around it. While the leader lives, killed units respawn\n" + "using the city's manpower; when the leader dies the city falls and is abandoned.").getBoolean();

		settlementDefaultRadius = config.get(serverOptions, "settlement_default_radius", settlementDefaultRadius, "City radius outside structures\nDefault=" + settlementDefaultRadius + "\n" + "Cities take the bounds of the Ancient Warfare town or structure their leader is in. A leader outside of any\n" + "town or structure founds a city of this radius (in blocks) around itself.", 8, 256).getInt();

		manpowerPerUnit = config.get(serverOptions, "manpower_per_unit", manpowerPerUnit, "Manpower per garrison unit\nDefault=" + manpowerPerUnit + "\n" + "Starting manpower of a city = number of units in its garrison when founded x this value (at least manpower_min).\n" + "Each respawn of a killed unit costs 1 manpower.", 0.0D, 100.0D).getDouble();

		manpowerMin = config.get(serverOptions, "manpower_min", manpowerMin, "Minimum manpower\nDefault=" + manpowerMin + "\n" + "Minimum starting manpower of a city.", 0, 10000).getInt();

		manpowerRegenPerDay = config.get(serverOptions, "manpower_regen_per_day", manpowerRegenPerDay, "Manpower regeneration\nDefault=" + manpowerRegenPerDay + "\n" + "Manpower a city regains every Minecraft day, up to its starting manpower, while it is not under siege.", 0, 10000).getInt();

		respawnDelayTicks = config.get(serverOptions, "respawn_delay_ticks", respawnDelayTicks, "Respawn delay\nDefault=" + respawnDelayTicks + "\n" + "Ticks (20 per second) before a killed garrison unit respawns in its city.", 20, 72000).getInt();

		announceSettlementChanges = config.get(serverOptions, "announce_settlement_changes", announceSettlementChanges, "Announce fallen cities\nDefault=" + announceSettlementChanges + "\n" + "If true, all players are told in chat when a city falls because its leader died.").getBoolean();

		renderLeaderLabel = config.get(clientOptions, "render_leader_label", renderLeaderLabel, "Render leader label\nDefault=" + renderLeaderLabel + "\n" + "If true, faction leaders show a 'Leader of the ...' label above their head, even when nameplates are disabled.").getBoolean();

		leaderLabelRange = config.get(clientOptions, "leader_label_range", leaderLabelRange, "Leader label range\nDefault=" + leaderLabelRange + "\n" + "Maximum distance in blocks at which the leader label is shown.", 1, 64).getInt();
	}
}
