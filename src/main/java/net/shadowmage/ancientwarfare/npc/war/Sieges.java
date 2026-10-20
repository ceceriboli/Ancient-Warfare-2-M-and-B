package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.ai.NpcAI;
import net.shadowmage.ancientwarfare.npc.ai.faction.NpcAIFactionAssault;
import net.shadowmage.ancientwarfare.npc.ai.faction.NpcAIFactionRangedAttack;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFactionSiegeEngineer;
import net.shadowmage.ancientwarfare.npc.faction.Diplomacy;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelations;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;
import net.shadowmage.ancientwarfare.npc.registry.NpcDefaultsRegistry;
import net.shadowmage.ancientwarfare.vehicle.config.AWVehicleStatics;
import net.shadowmage.ancientwarfare.vehicle.entity.IVehicleType;
import net.shadowmage.ancientwarfare.vehicle.entity.VehicleBase;
import net.shadowmage.ancientwarfare.vehicle.entity.types.VehicleType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Raids and full sieges of faction cities by factions at war with them. See {@link Siege}.
 */
public class Sieges {
	private Sieges() {}

	/**
	 * UNSUPPORTED: a campaign of the faction of a player called off because no called player came. PLUNDERED: a campaign on a city of the
	 * faction of a player, nobody came to defend it: the city loses manpower (it never falls away from players).
	 */
	public enum Outcome {
		DEFENDED, TAKEN, LIFTED, PEACE, STOPPED, UNSUPPORTED, PLUNDERED;

		/**
		 * @return true if the attackers leave in order and take their siege engines with them
		 */
		private boolean removesEngines() {
			return this == LIFTED || this == PEACE || this == STOPPED || this == UNSUPPORTED || this == PLUNDERED;
		}
	}

	private static final String BAR_PREFIX = "siege:";
	private static final String TAG_POST = "awPost";
	private static final String TAG_POST_RANGE = "awPostRange";
	private static final double MAX_ATTACKER_DISTANCE = 128D;
	private static final int LEASH_MARGIN = 48;
	private static final int OUTSIDE_SPAWN_MIN_MARGIN = 8;
	private static final int OUTSIDE_SPAWN_EXTRA = 16;
	private static final int OUTSIDE_SPAWN_MAX_DISTANCE = 240;
	private static final int OUTSIDE_SPAWN_PATIENCE_TICKS = 1200;
	private static final int OUTSIDE_SPAWN_ATTEMPTS = 48;
	private static final double ENGINE_TARGET_RANGE = 96D;
	private static final int ENGINE_SPACING = 6;
	private static final int RECONNECT_GRACE_TICKS = 200;
	private static final int IDLE_RESET_LOG_TICKS = 1200;
	private static final int DAY_TICKS = 24000;
	private static final int SPAWN_ATTEMPTS = 20;
	private static final int SPAWN_SPREAD = 3;
	private static final String UNIT_PREFIX = "ancientwarfarenpc";
	private static final String FALLBACK_UNIT = "faction.soldier";
	private static final String ENGINEER_UNIT = "faction.siege_engineer";
	private static final String FALLBACK_ENGINE = "catapult_mobile";
	private static final String[] HORNS = {"horn_1", "horn_3", "horn_5", "horn_6", "horn_7", "horn_8"};

	public static boolean isBesieged(Settlement settlement) {
		return settlement.getActiveSiegeId() != 0;
	}

	@Nullable
	public static Siege getSiegeOf(World world, Settlement settlement) {
		return isBesieged(settlement) ? Settlements.getData(world).getSiege(settlement.getActiveSiegeId()) : null;
	}

	/**
	 * @return null if the faction can attack the settlement, otherwise the translation key of the reason why not
	 */
	@Nullable
	public static String canStart(World world, Settlement settlement, String attackerFaction) {
		if (!settlement.isActive() || settlement.getType() != Settlement.Type.FACTION_CITY) {
			return "command.aw.siege.error.not_active";
		}
		if (isBesieged(settlement)) {
			return "command.aw.siege.error.already";
		}
		if (attackerFaction.equals(settlement.getFaction())) {
			return "command.aw.siege.error.same_faction";
		}
		if (!FactionRegistry.getFactionNames().contains(attackerFaction)) {
			return "command.aw.siege.error.unknown_faction";
		}
		if (!FactionRelations.isHostile(world, attackerFaction, settlement.getFaction())) {
			return "command.aw.siege.error.not_hostile";
		}
		if (Kingdoms.isProtected(world, settlement)) {
			return "command.aw.siege.error.protected";
		}
		return null;
	}

	/**
	 * Starts a raid or a full siege, the size depends on the strength of the city (garrison units loaded and manpower).
	 * Check {@link #canStart} first.
	 */
	public static Siege start(WorldServer world, SettlementData data, Settlement settlement, String attackerFaction, Siege.Kind kind) {
		return start(world, data, settlement, attackerFaction, kind, Siege.Origin.AMBIENT);
	}

	/**
	 * Like {@link #start(WorldServer, SettlementData, Settlement, String, Siege.Kind)}: a campaign (origin OFFENSIVE or DEFENSIVE) starts
	 * mustering, the players of the two factions are called (see {@link Service}).
	 */
	public static Siege start(WorldServer world, SettlementData data, Settlement settlement, String attackerFaction, Siege.Kind kind, Siege.Origin origin) {
		int[] size = computeSize(world, settlement, kind, attackerFaction);
		int strength = size[0];
		int waves = size[1];
		int perWave = size[2];
		int attackerManpower = size[3];
		int defenderManpower = size[4];
		Siege siege = Siege.create(data.getNextSiegeId(), kind, origin, settlement, attackerFaction, waves, perWave);
		data.addSiege(siege);
		if (kind == Siege.Kind.SIEGE) {
			// a full siege is paid with the manpower of the attacking faction (WarLevies)
			siege.setPaid(WarLevies.pay(world, attackerFaction, settlement.getDimension(), waves * perWave));
		}
		settlement.setActiveSiegeId(siege.getId());
		if (origin == Siege.Origin.AMBIENT) {
			announce(world, siege, "start", settlement);
			playHorn(world, settlement.getCenter());
		} else {
			// announced when a called player arrives
			siege.startMuster(world.getTotalWorldTime() + AWDiplomacyStatics.serviceMusterTicks);
		}
		data.markDirty();
		AncientWarfareNPC.LOG.info("{} {} of city {} ({}) by {} started ({}): strength {}, faction manpower {} against {}, {} waves of {} attackers",
				kind.getName(), siege.getId(), settlement.getId(), settlement.getFaction(), attackerFaction, origin.getName(), strength, attackerManpower,
				defenderManpower, waves, perWave);
		Service.onSiegeStarted(world, siege, settlement);
		return siege;
	}

	/**
	 * Size of an attack (raid or full siege, each with its own parameters): the strength of the city (garrison units loaded + manpower) x
	 * strength_ratio, plus a fraction (attacker_ratio) of the manpower of all the known cities of the attacking faction in the dimension (the
	 * ones of /awsettlement list); at most a share (attacker_share) of that manpower, however big the city is, and between the minimum and
	 * the maximum number of attackers of the kind.
	 *
	 * @return strength of the city, number of waves, attackers per wave, manpower of the attacking and of the defending faction
	 */
	/**
	 * @return how many attackers a raid or full siege of the city by the faction would have now
	 */
	public static int estimateAttackers(World world, Settlement settlement, Siege.Kind kind, String attacker) {
		int[] size = computeSize(world, settlement, kind, attacker);
		return size[1] * size[2];
	}

	private static int[] computeSize(World world, Settlement settlement, Siege.Kind kind, String attacker) {
		return computeSize(world, settlement, kind, attacker, 0);
	}

	/**
	 * @param alreadyPaid manpower the attacker already paid for this siege (a campaign sized again), counted as available
	 */
	private static int[] computeSize(World world, Settlement settlement, Siege.Kind kind, String attacker, int alreadyPaid) {
		boolean full = kind == Siege.Kind.SIEGE;
		int strength = countGarrison(world, settlement) + settlement.getManpower();
		// a strong faction attacks with more than a weak one on the same city
		int attackerManpower = getFactionManpower(world, attacker, settlement.getDimension());
		int defenderManpower = getFactionManpower(world, settlement.getFaction(), settlement.getDimension());
		double total = strength * (full ? AWDiplomacyStatics.fullSiegeStrengthRatio : AWDiplomacyStatics.raidStrengthRatio)
				+ attackerManpower * (full ? AWDiplomacyStatics.fullSiegeAttackerRatio : AWDiplomacyStatics.raidAttackerRatio);
		int min = full ? AWDiplomacyStatics.fullSiegeMinAttackers : AWDiplomacyStatics.raidMinAttackers;
		int max = full ? AWDiplomacyStatics.fullSiegeMaxAttackers : AWDiplomacyStatics.raidMaxAttackers;
		// a faction cannot send more than a share of the manpower it has (but not less than the minimum)
		max = Math.min(max, Math.max(min, (int) (attackerManpower * (full ? AWDiplomacyStatics.fullSiegeAttackerShare : AWDiplomacyStatics.raidAttackerShare))));
		if (full && WarLevies.isEnabled()) {
			// a full siege cannot cost more than the faction can spend above the floor of its manpower (WarLevies)
			max = Math.min(max, Math.max(min, WarLevies.getAvailable(world, attacker, settlement.getDimension()) + alreadyPaid));
		}
		int attackers = Math.min(max, Math.max(min, (int) Math.round(total)));
		int waves = Math.max(1, Math.min(full ? AWDiplomacyStatics.fullSiegeWaves : AWDiplomacyStatics.raidWaves, attackers));
		return new int[] {strength, waves, (int) Math.ceil((double) attackers / waves), attackerManpower, defenderManpower};
	}

	/**
	 * @return the manpower of all the active cities of the faction known in the dimension (the ones of /awsettlement list)
	 */
	private static int getFactionManpower(World world, String faction, int dimension) {
		int manpower = 0;
		for (Settlement city : Settlements.getData(world).getAll()) {
			if (city.isActive() && city.getType() == Settlement.Type.FACTION_CITY && city.getDimension() == dimension && faction.equals(city.getFaction())) {
				manpower += city.getManpower();
			}
		}
		return manpower;
	}

	/**
	 * @return a full siege if the city had none for full_siege_cooldown_days, the attacker has a known city and the full_siege_share roll
	 * says so, otherwise a raid
	 */
	static Siege.Kind chooseKind(World world, Settlement settlement, String attacker, long now) {
		// full sieges come only from factions with a known city (in the settlement list), the others only raid
		boolean fullSiegeAllowed = sinceRest(world, settlement.getLastFullSiegeEnd()) >= (long) AWDiplomacyStatics.fullSiegeCooldownDays * DAY_TICKS
				&& Diplomacy.getFactionsWithCities(world).contains(attacker) && WarLevies.canLaySiege(world, attacker, settlement.getDimension());
		return fullSiegeAllowed && world.rand.nextDouble() < AWDiplomacyStatics.fullSiegeShare ? Siege.Kind.SIEGE : Siege.Kind.RAID;
	}

	/**
	 * @return true if the city has had no attack for siege_cooldown_days
	 */
	static boolean isCooledDown(World world, Settlement settlement) {
		return sinceRest(world, settlement.getLastSiegeEnd()) >= (long) AWDiplomacyStatics.siegeCooldownDays * DAY_TICKS;
	}

	/**
	 * The clock of the rests between attacks (siege_cooldown_days, full_siege_cooldown_days): the world time, so that they follow /time and
	 * sleeping like the days of manpower and diplomacy.
	 */
	public static long restClock(World world) {
		return world.getWorldTime();
	}

	/**
	 * @return ticks of the rest clock since mark; a mark ahead of the clock (counted on the total world time before, or time set back)
	 * counts as a rest long over
	 */
	public static long sinceRest(World world, long mark) {
		long elapsed = restClock(world) - mark;
		return elapsed < 0 ? Long.MAX_VALUE : elapsed;
	}

	public static void stop(World world, SettlementData data, Siege siege) {
		end(world, data, siege, Outcome.STOPPED);
	}

	/**
	 * Called every second for each server world.
	 */
	static void tick(WorldServer world, SettlementData data, long now) {
		int dimension = world.provider.getDimension();
		Set<String> barKeys = new HashSet<>();
		for (Siege siege : new ArrayList<>(data.getSieges())) {
			if (siege.getDimension() == dimension) {
				process(world, data, siege, now, barKeys);
			}
		}
		AreaBossBars.retainOnly(world, BAR_PREFIX, barKeys);
		Service.tick(world, data, now);
		if (AWDiplomacyStatics.siegesEnabled && (now / 20) % Math.max(1, AWDiplomacyStatics.siegeCheckInterval / 20) == 0) {
			tryStartSiege(world, data, now);
		}
		if (AWDiplomacyStatics.siegesEnabled && AWDiplomacyStatics.serviceEnabled
				&& (now / 20) % Math.max(1, AWDiplomacyStatics.serviceCheckInterval / 20) == 0) {
			Service.tryStartCampaigns(world, data, now);
		}
	}

	static void onSettlementFallen(World world, Settlement settlement) {
		if (!isBesieged(settlement)) {
			return;
		}
		SettlementData data = Settlements.getData(world);
		Siege siege = data.getSiege(settlement.getActiveSiegeId());
		if (siege != null) {
			end(world, data, siege, Outcome.TAKEN);
		} else {
			settlement.setActiveSiegeId(0);
			data.markDirty();
		}
	}

	/**
	 * The city was taken by the attackers: those inside the defended perimeter stay as its new garrison (returned, no longer attackers),
	 * the siege ends and the others leave.
	 */
	static List<NpcFaction> onSettlementConquered(World world, Settlement settlement) {
		List<NpcFaction> garrison = new ArrayList<>();
		if (!isBesieged(settlement)) {
			return garrison;
		}
		SettlementData data = Settlements.getData(world);
		Siege siege = data.getSiege(settlement.getActiveSiegeId());
		if (siege == null) {
			settlement.setActiveSiegeId(0);
			return garrison;
		}
		AxisAlignedBB perimeter = settlement.getAABB().grow(AWDiplomacyStatics.defenderPerimeterMargin);
		for (NpcFaction npc : siege.getLoadedAttackers()) {
			if (perimeter.contains(npc.getPositionVector()) && npc.getFaction().equals(siege.getAttackerFaction())) {
				siege.removeAttacker(npc);
				npc.getEntityData().removeTag(Settlements.TAG_SIEGE);
				npc.setAttackTarget(null);
				for (EntityAITasks.EntityAITaskEntry entry : npc.tasks.taskEntries) {
					if (entry.action instanceof NpcAIFactionAssault) {
						((NpcAIFactionAssault) entry.action).setTarget(null);
					}
				}
				garrison.add(npc);
			}
		}
		end(world, data, siege, Outcome.TAKEN);
		return garrison;
	}

	/**
	 * An attacker was loaded (or spawned): it joins its siege again, or leaves if the siege is over.
	 */
	static void onAttackerJoinWorld(NpcFaction npc) {
		Siege siege = Settlements.getData(npc.world).getSiege(npc.getEntityData().getInteger(Settlements.TAG_SIEGE));
		if (siege == null) {
			Settlements.disband(npc, npc.world.getTotalWorldTime() + 20 + npc.world.rand.nextInt(100));
			return;
		}
		siege.addAttacker(npc);
		if (!isEngineer(npc)) {
			getOrAddAssaultAI(npc);
		}
	}

	/**
	 * A faction npc died: if it was an attacker, or a unit of a city under attack, the current wave is not stalled.
	 */
	static void onNpcDeath(NpcFaction npc) {
		SettlementData data = Settlements.getData(npc.world);
		Siege siege = null;
		if (npc.getEntityData().hasKey(Settlements.TAG_SIEGE)) {
			siege = data.getSiege(npc.getEntityData().getInteger(Settlements.TAG_SIEGE));
		} else {
			Settlement settlement = Settlements.getSettlementOf(npc);
			if (settlement != null && isBesieged(settlement)) {
				siege = data.getSiege(settlement.getActiveSiegeId());
			}
		}
		if (siege != null && siege.getState() == Siege.State.WAVE) {
			if (siege.getIdleTicks() >= IDLE_RESET_LOG_TICKS) {
				AncientWarfareNPC.LOG.info("{} {}: stalled wave timer reset after {}s by the death of {} ({}, {}) at {}", siege.getKind().getName(), siege.getId(),
						siege.getIdleTicks() / 20, npc.getName(), npc.getFaction(), npc.getEntityData().hasKey(Settlements.TAG_SIEGE) ? "attacker" : "defender",
						npc.getPosition());
			}
			siege.resetIdleTicks();
			data.markDirty();
		}
	}

	private static boolean isEngineer(NpcFaction npc) {
		return npc instanceof NpcFactionSiegeEngineer;
	}

	/**
	 * @return the loaded attackers of the current wave (siege engineers stay for the whole siege and are not counted)
	 */
	private static List<NpcFaction> getWaveAttackers(Siege siege) {
		List<NpcFaction> ret = new ArrayList<>();
		for (NpcFaction npc : siege.getLoadedAttackers()) {
			if (!isEngineer(npc)) {
				ret.add(npc);
			}
		}
		return ret;
	}

	private static void process(WorldServer world, SettlementData data, Siege siege, long now, Set<String> barKeys) {
		Settlement settlement = data.get(siege.getSettlementId());
		if (settlement == null || !settlement.isActive()) {
			end(world, data, siege, Outcome.TAKEN);
			return;
		}
		if (!FactionRelations.isHostile(world, siege.getAttackerFaction(), settlement.getFaction())) {
			end(world, data, siege, Outcome.PEACE);
			return;
		}
		if (siege.getState() == Siege.State.MUSTER) {
			// a campaign waits for a called player, also while the city is far away and not loaded
			if (Service.hasCalledPlayerNear(world, siege, settlement)) {
				// sized again now that the city is loaded and its garrison counted, as for an attack started near a player
				int[] size = computeSize(world, settlement, siege.getKind(), siege.getAttackerFaction(), siege.getPaid());
				siege.setSize(size[1], size[2]);
				if (siege.getKind() == Siege.Kind.SIEGE && WarLevies.isEnabled()) {
					// the new size is paid, or the difference given back
					int difference = size[1] * size[2] - siege.getPaid();
					if (difference > 0) {
						siege.setPaid(siege.getPaid() + WarLevies.pay(world, siege.getAttackerFaction(), settlement.getDimension(), difference));
					} else if (difference < 0) {
						siege.setPaid(siege.getPaid() - WarLevies.refund(world, siege.getAttackerFaction(), settlement.getDimension(), -difference,
								"smaller campaign " + siege.getId()));
					}
				}
				siege.setState(Siege.State.WARMUP);
				data.markDirty();
				announce(world, siege, "start", settlement);
				playHorn(world, settlement.getCenter());
				AncientWarfareNPC.LOG.info("{} {}: a called player arrived, the attack begins: strength {}, faction manpower {} against {}, {} waves of {} attackers",
						siege.getKind().getName(), siege.getId(), size[0], size[3], size[4], size[1], size[2]);
			} else {
				if (now >= siege.getMusterUntil()) {
					Service.resolveUnattended(world, data, siege, settlement);
				}
				return;
			}
		}
		if (!world.isBlockLoaded(settlement.getCenter())) {
			// nobody around: the siege waits
			siege.suspended = true;
			return;
		}
		if (siege.suspended) {
			// give the attackers time to be loaded again before counting them
			siege.suspended = false;
			siege.reconnectGraceUntil = now + RECONNECT_GRACE_TICKS;
		}
		siege.addTicks(20);
		data.markDirty();

		BlockPos target = getTarget(world, settlement);
		BlockPos center = settlement.getCenter();
		boolean breach = isBreachAllowed(siege);
		boolean build = isBuildAllowed(siege);
		double leash = Math.max(MAX_ATTACKER_DISTANCE, siege.getLeash());
		for (NpcFaction npc : siege.getLoadedAttackers()) {
			if (npc.getDistanceSq(target) > leash * leash) {
				siege.removeAttacker(npc);
				Settlements.disband(npc, now + 20);
			} else if (isEngineer(npc)) {
				commandEngineer(world, npc, settlement);
			} else {
				dropForeignTarget(npc);
				aim(npc, target, breach, build);
			}
		}
		commandDefenders(world, siege, settlement, target);

		if (siege.getTotalTicks() >= AWDiplomacyStatics.siegeMaxTicks) {
			end(world, data, siege, Outcome.LIFTED);
			return;
		}
		if (siege.getState() == Siege.State.WARMUP) {
			if (siege.getStateTicks() >= AWDiplomacyStatics.siegeWarmupTicks) {
				BlockPos origin = spawnWave(world, siege, settlement, target);
				if (origin != null) {
					if (siege.getKind() == Siege.Kind.SIEGE && siege.getWavesSpawned() == 0) {
						spawnEngines(world, siege, settlement, target, origin);
					}
					siege.setWavesSpawned(siege.getWavesSpawned() + 1);
					siege.setState(Siege.State.WAVE);
					playHorn(world, center);
				}
			}
		} else if (getWaveAttackers(siege).isEmpty() && now >= siege.reconnectGraceUntil) {
			if (siege.getWavesSpawned() >= siege.getTotalWaves()) {
				end(world, data, siege, Outcome.DEFENDED);
				return;
			}
			siege.setState(Siege.State.WARMUP);
		} else {
			siege.addIdleTicks(20);
			if (AWDiplomacyStatics.siegeWaveStallTicks > 0 && siege.getIdleTicks() >= AWDiplomacyStatics.siegeWaveStallTicks) {
				// nobody killed for too long (attackers stuck or unable to reach the city): the wave withdraws
				AncientWarfareNPC.LOG.info("{} {}: wave {}/{} stalled, it withdraws", siege.getKind().getName(), siege.getId(), siege.getWavesSpawned(),
						siege.getTotalWaves());
				if (siege.getWavesSpawned() >= siege.getTotalWaves()) {
					end(world, data, siege, Outcome.LIFTED);
					return;
				}
				for (NpcFaction npc : getWaveAttackers(siege)) {
					siege.removeAttacker(npc);
					npc.setAttackTarget(null);
					Settlements.disband(npc, now + 20 + world.rand.nextInt(100));
				}
				siege.setState(Siege.State.WARMUP);
			}
		}
		showBar(world, siege, settlement, barKeys);
	}

	private static String keyPrefix(Siege siege) {
		return siege.getKind() == Siege.Kind.SIEGE ? "command.aw.siege." : "command.aw.raid.";
	}

	private static void showBar(WorldServer world, Siege siege, Settlement settlement, Set<String> barKeys) {
		String key = BAR_PREFIX + siege.getId();
		barKeys.add(key);
		ITextComponent city = Settlements.displayName(settlement);
		ITextComponent attacker = FactionMembership.factionName(siege.getAttackerFaction());
		ITextComponent name;
		float percent;
		if (siege.getState() == Siege.State.WARMUP) {
			name = new TextComponentTranslation(keyPrefix(siege) + "bar.warmup", city, attacker, siege.getWavesSpawned() + 1, siege.getTotalWaves());
			percent = (float) siege.getStateTicks() / AWDiplomacyStatics.siegeWarmupTicks;
		} else {
			name = new TextComponentTranslation(keyPrefix(siege) + "bar.wave", city, attacker, siege.getWavesSpawned(), siege.getTotalWaves());
			float health = 0;
			for (NpcFaction npc : getWaveAttackers(siege)) {
				health += npc.getHealth();
			}
			percent = siege.getWaveMaxHealth() <= 0 ? 0F : health / siege.getWaveMaxHealth();
		}
		// the whole city and around it (the center alone left most of a large city without the bar)
		AxisAlignedBB area = settlement.getAABB().grow(AWDiplomacyStatics.manpowerBarRange);
		AreaBossBars.show(world, key, area, name, BossInfo.Color.RED, BossInfo.Overlay.NOTCHED_10, percent);
	}

	static void end(World world, SettlementData data, Siege siege, Outcome outcome) {
		data.removeSiege(siege);
		Settlement settlement = data.get(siege.getSettlementId());
		long now = world.getTotalWorldTime();
		if (settlement != null && settlement.getActiveSiegeId() == siege.getId()) {
			settlement.setActiveSiegeId(0);
			settlement.setLastSiegeEnd(restClock(world));
			if (siege.getKind() == Siege.Kind.SIEGE) {
				settlement.setLastFullSiegeEnd(restClock(world));
			}
		}
		data.markDirty();
		AreaBossBars.hide(BAR_PREFIX + siege.getId());
		for (NpcFaction npc : siege.getLoadedAttackers()) {
			npc.setAttackTarget(null);
			Settlements.disband(npc, now + 100 + world.rand.nextInt(300));
		}
		for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
			if (settlement != null && entity instanceof NpcFaction && Settlements.isDefender((NpcFaction) entity, settlement)) {
				restorePost((NpcFaction) entity);
			}
			if (entity instanceof VehicleBase && entity.getEntityData().getInteger(Settlements.TAG_SIEGE) == siege.getId()) {
				entity.getEntityData().removeTag(Settlements.TAG_SIEGE);
				if (outcome.removesEngines()) {
					entity.setDead();
				}
				// otherwise the siege engines stay where they are, abandoned
			}
		}
		announce(world, siege, outcome.name().toLowerCase(Locale.ROOT), settlement);
		if (settlement != null && (outcome == Outcome.DEFENDED || outcome == Outcome.LIFTED || outcome == Outcome.UNSUPPORTED)) {
			Diplomacy.onAttackFailed(world, siege.getAttackerFaction(), settlement.getFaction());
		}
		if (settlement != null && outcome == Outcome.PLUNDERED) {
			Diplomacy.onCityPlundered(world, settlement.getFaction(), siege.getAttackerFaction());
		}
		Armies.releaseAll(world, siege.getId());
		AssaultBuilds.removeAll(world, siege.getId());
		WarLevies.onSiegeEnded(world, data, siege, outcome);
		Service.onSiegeEnded(world, siege, settlement, outcome);
		AncientWarfareNPC.LOG.info("{} {} of city {} by {} ended: {}", siege.getKind().getName(), siege.getId(), siege.getSettlementId(),
				siege.getAttackerFaction(), outcome);
	}

	private static void tryStartSiege(WorldServer world, SettlementData data, long now) {
		if (data.getSieges().size() >= AWDiplomacyStatics.maxActiveSieges) {
			return;
		}
		int dimension = world.provider.getDimension();
		List<Settlement> candidates = new ArrayList<>();
		for (Settlement settlement : data.getAll()) {
			// the cities of a player kingdom are attacked only by defensive campaigns (Service), which call its members
			if (settlement.getDimension() == dimension && settlement.isActive() && settlement.getType() == Settlement.Type.FACTION_CITY
					&& !Kingdoms.isKingdom(settlement.getFaction()) && !isBesieged(settlement) && isCooledDown(world, settlement)
					&& isPlayerNear(world, settlement.getCenter())) {
				candidates.add(settlement);
			}
		}
		Collections.shuffle(candidates, world.rand);
		for (Settlement settlement : candidates) {
			List<String> enemies = new ArrayList<>(FactionRelations.getDeclaredEnemies(world, settlement.getFaction()));
			// a player kingdom attacks only when its owner orders a campaign
			enemies.removeIf(enemy -> Kingdoms.isKingdom(enemy) || canStart(world, settlement, enemy) != null);
			if (enemies.isEmpty() || world.rand.nextDouble() >= AWDiplomacyStatics.siegeChance) {
				continue;
			}
			String attacker = enemies.get(world.rand.nextInt(enemies.size()));
			start(world, data, settlement, attacker, chooseKind(world, settlement, attacker, now));
			return;
		}
	}

	private static boolean isPlayerNear(World world, BlockPos pos) {
		double range = AWDiplomacyStatics.siegePlayerRange;
		for (EntityPlayer player : world.playerEntities) {
			if (!player.isSpectator() && player.getDistanceSq(pos) <= range * range) {
				return true;
			}
		}
		return false;
	}

	private static int countGarrison(World world, Settlement settlement) {
		int count = 0;
		for (NpcFaction npc : world.getEntitiesWithinAABB(NpcFaction.class, settlement.getAABB())) {
			Settlement npcSettlement = Settlements.getSettlementOf(npc);
			if (npc.isEntityAlive() && npcSettlement != null && npcSettlement.getId() == settlement.getId()) {
				count++;
			}
		}
		if (settlement.getOwnerId() != null) {
			// the capital of a player kingdom: the owner's own soldiers defend it too
			for (net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned npc : world.getEntitiesWithinAABB(net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned.class,
					settlement.getAABB())) {
				if (npc.isEntityAlive() && !npc.isPassive() && settlement.getOwnerId().equals(npc.getOwner().getUUID())) {
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * @return the position of the city leader if it is loaded, otherwise where it was last seen
	 */
	static BlockPos getTarget(WorldServer world, Settlement settlement) {
		if (settlement.getLeaderId() != null) {
			Entity leader = world.getEntityFromUuid(settlement.getLeaderId());
			if (leader != null && leader.isEntityAlive()) {
				return leader.getPosition();
			}
		}
		return settlement.getLastLeaderPos();
	}

	/**
	 * An attacker that took an enemy it should not fight now (another settlement on its way, taken before the siege restriction applied) lets
	 * it go: a running target task keeps its own copy of the target, so it is reset too.
	 */
	private static void dropForeignTarget(NpcFaction npc) {
		EntityLivingBase target = npc.getAttackTarget();
		if (target instanceof NpcFaction && !npc.isHostileTowards(target)) {
			npc.setAttackTarget(null);
			for (EntityAITasks.EntityAITaskEntry entry : npc.targetTasks.taskEntries) {
				if (entry.using) {
					entry.action.resetTask();
				}
			}
		}
	}

	private static void aim(NpcFaction npc, BlockPos target, boolean breach, boolean build) {
		// the home follows the attacker: Ancient Warfare shortens the attack range of an npc the farther it is from home
		// (NpcAIAttack.getAdjustedTargetDistance), with the home on the city leader attackers ignored defenders a few blocks away.
		// The assault AI takes them to the leader, and the move home AIs stay idle
		npc.setHomePosAndDistance(npc.getPosition(), npc.getHomeRange());
		NpcAIFactionAssault ai = getOrAddAssaultAI(npc);
		ai.setTarget(target);
		ai.setBreachAllowed(breach);
		ai.setBuildAllowed(build);
	}

	static boolean isBreachAllowed(Siege siege) {
		return "all".equals(AWDiplomacyStatics.attackerBreach) || "siege".equals(AWDiplomacyStatics.attackerBreach) && siege.getKind() == Siege.Kind.SIEGE;
	}

	/**
	 * @return true if the attackers of the siege may place ladders and bridges when they cannot get closer to the leader
	 */
	static boolean isBuildAllowed(Siege siege) {
		return "all".equals(AWDiplomacyStatics.attackerBuild) || "siege".equals(AWDiplomacyStatics.attackerBuild) && siege.getKind() == Siege.Kind.SIEGE;
	}

	/**
	 * The garrison goes out after the attackers inside the defended perimeter, except the guards posted close to the leader.
	 * An npc going out gets its home moved onto its enemy (Ancient Warfare npcs return home when too far, and shorten their attack range
	 * the farther they are from home), the post is saved and given back when there are no attackers left in the perimeter.
	 */
	private static void commandDefenders(WorldServer world, Siege siege, Settlement settlement, BlockPos leaderPos) {
		AxisAlignedBB perimeter = settlement.getAABB().grow(AWDiplomacyStatics.defenderPerimeterMargin);
		List<NpcFaction> enemies = new ArrayList<>();
		for (NpcFaction npc : siege.getLoadedAttackers()) {
			if (perimeter.contains(npc.getPositionVector())) {
				enemies.add(npc);
			}
		}
		double guardRadiusSq = (double) AWDiplomacyStatics.defenderGuardRadius * AWDiplomacyStatics.defenderGuardRadius;
		double responseSq = (double) AWDiplomacyStatics.defenderResponseRange * AWDiplomacyStatics.defenderResponseRange;
		// how many melee defenders already go after each attacker: only a few per attacker, so that a large garrison does not compute
		// hundreds of paths across the city every second (the others stay at their posts)
		Map<NpcFaction, Integer> assigned = new HashMap<>();
		for (NpcFaction defender : world.getEntitiesWithinAABB(NpcFaction.class, perimeter)) {
			EntityLivingBase target = defender.getAttackTarget();
			if (target instanceof NpcFaction && enemies.contains(target) && !(defender instanceof IRangedAttackMob) && Settlements.isDefender(defender, settlement)) {
				assigned.merge((NpcFaction) target, 1, Integer::sum);
			}
		}
		for (NpcFaction defender : world.getEntitiesWithinAABB(NpcFaction.class, perimeter)) {
			if (!defender.isEntityAlive() || !Settlements.isDefender(defender, settlement)) {
				continue;
			}
			if (getPost(defender).distanceSq(leaderPos) <= guardRadiusSq) {
				continue; // guards the leader
			}
			if (enemies.isEmpty()) {
				restorePost(defender);
				continue;
			}
			if (defender instanceof IRangedAttackMob) {
				commandRangedDefender(defender, enemies, perimeter);
				continue;
			}
			EntityLivingBase current = defender.getAttackTarget();
			// keeps fighting an enemy inside the perimeter (an attacker, or a player helping them), otherwise the nearest attacker
			boolean keep = current != null && current.isEntityAlive() && (enemies.contains(current) || perimeter.contains(current.getPositionVector()));
			EntityLivingBase enemy = keep ? current : nearestFree(defender, enemies, assigned, responseSq);
			if (enemy == null) {
				// no attacker close enough that does not have enough defenders on it already: stays at (or goes back to) its post
				restorePost(defender);
				continue;
			}
			if (enemy != current) {
				defender.setAttackTarget(enemy);
				if (enemy instanceof NpcFaction) {
					assigned.merge((NpcFaction) enemy, 1, Integer::sum);
				}
			}
			savePost(defender);
			defender.setHomePosAndDistance(enemy.getPosition(), defender.getHomeRange());
		}
	}

	/**
	 * Archers hold their post (walls, towers) and shoot at the nearest attacker they can see within their range.
	 */
	private static void commandRangedDefender(NpcFaction archer, List<NpcFaction> enemies, AxisAlignedBB perimeter) {
		restorePost(archer);
		EntityLivingBase current = archer.getAttackTarget();
		if (current != null && current.isEntityAlive() && perimeter.contains(current.getPositionVector()) && archer.getEntitySenses().canSee(current)) {
			return;
		}
		double range = archer.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
		NpcFaction nearest = null;
		for (NpcFaction enemy : enemies) {
			double distSq = enemy.getDistanceSq(archer);
			if (distSq <= range * range && archer.getEntitySenses().canSee(enemy) && (nearest == null || distSq < nearest.getDistanceSq(archer))) {
				nearest = enemy;
			}
		}
		if (nearest != null) {
			archer.setAttackTarget(nearest);
		}
	}

	/**
	 * @return the nearest candidate the npc has not found unreachable recently, or null
	 */
	@Nullable
	private static NpcFaction nearest(NpcBase from, List<NpcFaction> candidates) {
		NpcFaction nearest = null;
		for (NpcFaction candidate : candidates) {
			if (!from.isMarkedUnreachable(candidate) && (nearest == null || candidate.getDistanceSq(from) < nearest.getDistanceSq(from))) {
				nearest = candidate;
			}
		}
		return nearest;
	}

	/**
	 * @return the nearest attacker within defender_response_range that has fewer than defender_max_per_attacker defenders on it and that the
	 * npc has not found unreachable recently, or null
	 */
	@Nullable
	private static NpcFaction nearestFree(NpcBase from, List<NpcFaction> candidates, Map<NpcFaction, Integer> assigned, double responseSq) {
		NpcFaction nearest = null;
		double nearestSq = responseSq;
		for (NpcFaction candidate : candidates) {
			double distSq = candidate.getDistanceSq(from);
			if (distSq <= nearestSq && assigned.getOrDefault(candidate, 0) < AWDiplomacyStatics.defenderMaxPerAttacker && !from.isMarkedUnreachable(candidate)) {
				nearest = candidate;
				nearestSq = distSq;
			}
		}
		return nearest;
	}

	private static BlockPos getPost(NpcFaction npc) {
		NBTTagCompound data = npc.getEntityData();
		return data.hasKey(TAG_POST) ? BlockPos.fromLong(data.getLong(TAG_POST)) : npc.getHomePosition();
	}

	private static void savePost(NpcFaction npc) {
		NBTTagCompound data = npc.getEntityData();
		if (!data.hasKey(TAG_POST)) {
			data.setLong(TAG_POST, npc.getHomePosition().toLong());
			data.setInteger(TAG_POST_RANGE, npc.getHomeRange());
		}
	}

	/**
	 * Gives back the home a defender had before going out after the attackers.
	 */
	static void restorePost(NpcFaction npc) {
		NBTTagCompound data = npc.getEntityData();
		if (data.hasKey(TAG_POST)) {
			npc.setHomePosAndDistance(BlockPos.fromLong(data.getLong(TAG_POST)), data.getInteger(TAG_POST_RANGE));
			data.removeTag(TAG_POST);
			data.removeTag(TAG_POST_RANGE);
		}
	}

	/**
	 * An attacker or a city unit was hurt: its companions nearby without an enemy go after the one who hurt it.
	 */
	public static void onNpcHurt(NpcFaction victim, @Nullable Entity source) {
		if (AWDiplomacyStatics.groupAlertRange <= 0 || !(source instanceof EntityLivingBase) || !source.isEntityAlive()) {
			return;
		}
		EntityLivingBase aggressor = (EntityLivingBase) source;
		NBTTagCompound victimData = victim.getEntityData();
		boolean attacker = victimData.hasKey(Settlements.TAG_SIEGE);
		Settlement settlement = attacker ? null : Settlements.getSettlementOf(victim);
		if (!attacker && (settlement == null || !settlement.isActive())) {
			return;
		}
		for (NpcFaction companion : victim.world.getEntitiesWithinAABB(NpcFaction.class, victim.getEntityBoundingBox().grow(AWDiplomacyStatics.groupAlertRange))) {
			if (companion == victim || companion == aggressor || !companion.isEntityAlive() || companion.getAttackTarget() != null) {
				continue;
			}
			boolean sameSide = attacker ? companion.getEntityData().getInteger(Settlements.TAG_SIEGE) == victimData.getInteger(Settlements.TAG_SIEGE)
					&& companion.getEntityData().hasKey(Settlements.TAG_SIEGE) : Settlements.isDefender(companion, settlement);
			if (sameSide && !isEngineer(companion) && companion.isHostileTowards(aggressor) && !companion.isMarkedUnreachable(aggressor)) {
				companion.setAttackTarget(aggressor);
			}
		}
	}

	/**
	 * Siege engineers fire at their attack target: when they have none, give them the nearest npc of the besieged faction in range.
	 */
	private static void commandEngineer(WorldServer world, NpcFaction engineer, Settlement settlement) {
		EntityLivingBase current = engineer.getAttackTarget();
		if (current != null && current.isEntityAlive()) {
			return;
		}
		NpcFaction nearest = null;
		double nearestDistSq = Double.MAX_VALUE;
		for (NpcFaction npc : world.getEntitiesWithinAABB(NpcFaction.class, engineer.getEntityBoundingBox().grow(ENGINE_TARGET_RANGE))) {
			if (npc.isEntityAlive() && settlement.getFaction().equals(npc.getFaction())) {
				double distSq = npc.getDistanceSq(engineer);
				if (distSq < nearestDistSq) {
					nearestDistSq = distSq;
					nearest = npc;
				}
			}
		}
		if (nearest != null) {
			engineer.setAttackTarget(nearest);
		}
	}

	static NpcAIFactionAssault getOrAddAssaultAI(NpcBase npc) {
		for (EntityAITasks.EntityAITaskEntry entry : npc.tasks.taskEntries) {
			if (entry.action instanceof NpcAIFactionAssault) {
				return (NpcAIFactionAssault) entry.action;
			}
		}
		NpcAIFactionAssault ai = new NpcAIFactionAssault(npc);
		npc.tasks.addTask(NpcAIFactionAssault.PRIORITY, ai);
		for (EntityAITasks.EntityAITaskEntry entry : npc.tasks.taskEntries) {
			if (entry.action instanceof NpcAIFactionRangedAttack) {
				// the ranged attack of Ancient Warfare has no mutex: running together with the assault it cleared (or replaced) the path
				// of the archer every tick, so archers stood still. Now the assault interrupts it and it waits while the assault runs
				entry.action.setMutexBits(NpcAI.MOVE | NpcAI.ATTACK);
			}
		}
		return ai;
	}

	/**
	 * @return where the wave spawned, or null if no loaded place was found (try again in a second)
	 */
	@Nullable
	private static BlockPos spawnWave(WorldServer world, Siege siege, Settlement settlement, BlockPos target) {
		// outside the walls: in a large city the usual distance from the leader would still be inside it
		BlockPos origin = findSpawnPosOutside(world, target, settlement);
		if (origin == null) {
			// the land outside the city is not loaded (a player deep inside a large city): the usual place near the leader only if it is
			// outside the city too, or after waiting OUTSIDE_SPAWN_PATIENCE_TICKS (the wave is tried again every second meanwhile)
			BlockPos near = findSpawnPos(world, target);
			boolean inside = near != null && settlement.contains(settlement.getDimension(), new BlockPos(near.getX(), settlement.getMin().getY(), near.getZ()));
			if (near != null && (!inside || siege.getStateTicks() >= AWDiplomacyStatics.siegeWarmupTicks + OUTSIDE_SPAWN_PATIENCE_TICKS)) {
				origin = near;
			}
		}
		if (origin == null) {
			return null;
		}
		double dx = origin.getX() - target.getX();
		double dz = origin.getZ() - target.getZ();
		siege.setLeash(Math.max(siege.getLeash(), (int) Math.sqrt(dx * dx + dz * dz) + LEASH_MARGIN));
		List<String> types = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		parseWeights(siege.getKind() == Siege.Kind.SIEGE ? AWDiplomacyStatics.fullSiegeUnitWeights : AWDiplomacyStatics.raidUnitWeights, FALLBACK_UNIT,
				types, weights);
		float health = 0;
		int spawned = 0;
		for (int i = 0; i < siege.getAttackersPerWave(); i++) {
			NpcFaction npc = createWeightedUnit(world, types, weights, siege.getAttackerFaction());
			if (npc == null) {
				continue;
			}
			BlockPos pos = world.getTopSolidOrLiquidBlock(origin.add(world.rand.nextInt(SPAWN_SPREAD * 2 + 1) - SPAWN_SPREAD, 0,
					world.rand.nextInt(SPAWN_SPREAD * 2 + 1) - SPAWN_SPREAD));
			npc.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360F, 0F);
			npc.getEntityData().setInteger(Settlements.TAG_SIEGE, siege.getId());
			npc.getEntityData().setString(Settlements.TAG_SIEGE_ENEMY, settlement.getFaction());
			npc.setHealth(npc.getMaxHealth());
			if (world.spawnEntity(npc)) {
				siege.addAttacker(npc);
				aim(npc, target, isBreachAllowed(siege), isBuildAllowed(siege));
				health += npc.getMaxHealth();
				spawned++;
			}
		}
		siege.setWaveMaxHealth(health);
		siege.reconnectGraceUntil = world.getTotalWorldTime() + 40;
		if (spawned == 0) {
			AncientWarfareNPC.LOG.warn("{} {}: no attacker could be spawned, check the unit weights in the config", siege.getKind().getName(), siege.getId());
		}
		AncientWarfareNPC.LOG.info("{} {}: wave {}/{} of {} attackers at {}", siege.getKind().getName(), siege.getId(), siege.getWavesSpawned() + 1,
				siege.getTotalWaves(), spawned, origin);
		return origin;
	}

	/**
	 * Places the siege engines between the city leader and the first wave, facing the leader, each with a siege engineer of the attackers on it.
	 */
	private static void spawnEngines(WorldServer world, Siege siege, Settlement settlement, BlockPos center, BlockPos waveOrigin) {
		int count = AWDiplomacyStatics.fullSiegeEngines;
		if (count <= 0) {
			return;
		}
		List<String> types = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		parseWeights(AWDiplomacyStatics.fullSiegeEngineTypes, FALLBACK_ENGINE, types, weights);
		double dx = waveOrigin.getX() - center.getX();
		double dz = waveOrigin.getZ() - center.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1D) {
			dx = 1D;
			dz = 0D;
			length = 1D;
		}
		dx /= length;
		dz /= length;
		double distance = Math.min(AWDiplomacyStatics.fullSiegeEngineDistance, length);
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			double offset = (i - (count - 1) / 2D) * ENGINE_SPACING;
			BlockPos column = new BlockPos(center.getX() + dx * distance - dz * offset, 64, center.getZ() + dz * distance + dx * offset);
			if (!world.isAreaLoaded(column, 4)) {
				continue;
			}
			BlockPos pos = world.getTopSolidOrLiquidBlock(column);
			if (pos.getY() <= 0 || world.getBlockState(pos.down()).getMaterial().isLiquid()) {
				continue;
			}
			if (spawnEngine(world, siege, pickWeighted(world.rand, types, weights), pos, center)) {
				spawned++;
			}
		}
		AncientWarfareNPC.LOG.info("siege {}: {} of {} siege engines placed", siege.getId(), spawned, count);
	}

	private static boolean spawnEngine(WorldServer world, Siege siege, String type, BlockPos pos, BlockPos center) {
		Optional<VehicleBase> created = createEngine(world, type);
		if (!created.isPresent()) {
			AncientWarfareNPC.LOG.warn("Siege engine type {} is unknown, disabled or cannot be manned by soldiers", type);
			return false;
		}
		NpcFaction engineer = createUnit(world, ENGINEER_UNIT, siege.getAttackerFaction());
		if (!(engineer instanceof NpcFactionSiegeEngineer)) {
			return false;
		}
		VehicleBase vehicle = created.get();
		// same orientation as a vehicle placed by a player standing on the engine and looking at the city
		float lookYaw = (float) (MathHelper.atan2(-(center.getX() - pos.getX()), center.getZ() - pos.getZ()) * 180D / Math.PI);
		float yaw = -lookYaw + 180F;
		vehicle.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
		vehicle.prevRotationYaw = vehicle.rotationYaw = yaw;
		vehicle.localTurretDestRot = vehicle.localTurretRotation = vehicle.localTurretRotationHome = yaw;
		if (AWVehicleStatics.generalSettings.useVehicleSetupTime) {
			vehicle.setSetupState(true, 100);
		}
		vehicle.getEntityData().setInteger(Settlements.TAG_SIEGE, siege.getId());
		if (!world.spawnEntity(vehicle)) {
			return false;
		}
		engineer.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0F);
		engineer.getEntityData().setInteger(Settlements.TAG_SIEGE, siege.getId());
		engineer.setHomePosAndDistance(pos, engineer.getHomeRange());
		engineer.setHealth(engineer.getMaxHealth());
		if (world.spawnEntity(engineer)) {
			siege.addAttacker(engineer);
			((NpcFactionSiegeEngineer) engineer).setVehicle(vehicle);
			engineer.startRiding(vehicle);
		}
		return true;
	}

	private static Optional<VehicleBase> createEngine(World world, String configName) {
		for (IVehicleType type : VehicleType.vehicleTypes) {
			if (type != null && type.getConfigName().equals(configName) && type.isEnabled() && type.canSoldiersPilot() && type.getMaterialType() != null) {
				int level = Math.max(0, Math.min(AWDiplomacyStatics.fullSiegeEngineLevel, type.getMaterialType().getNumOfLevels() - 1));
				return VehicleType.getVehicleForType(world, type.getGlobalVehicleType(), level);
			}
		}
		return Optional.empty();
	}

	@Nullable
	static NpcFaction createUnit(World world, String type, String faction) {
		Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(UNIT_PREFIX, type), world);
		if (!(entity instanceof NpcFaction)) {
			AncientWarfareNPC.LOG.warn("Unit type {} is not a faction npc, using {}", type, FALLBACK_UNIT);
			entity = EntityList.createEntityByIDFromName(new ResourceLocation(UNIT_PREFIX, FALLBACK_UNIT), world);
			if (!(entity instanceof NpcFaction)) {
				return null;
			}
		}
		NpcFaction npc = (NpcFaction) entity;
		npc.setFactionNameAndDefaults(faction);
		if (!NpcDefaultsRegistry.getFactionNpcDefault(npc).isEnabled()) {
			// the faction has no such unit (e.g. giant archers): Ancient Warfare would show it with the plain player skin and the size of
			// the faction
			return null;
		}
		npc.enablePersistence();
		return npc;
	}

	/**
	 * @return a unit of one of the weighted types that the faction has (an enabled one), or a soldier, or null if it has none of them
	 */
	@Nullable
	static NpcFaction createWeightedUnit(World world, List<String> types, List<Integer> weights, String faction) {
		NpcFaction npc = createUnit(world, pickWeighted(world.rand, types, weights), faction);
		if (npc != null) {
			return npc;
		}
		for (String type : types) {
			npc = createUnit(world, type, faction);
			if (npc != null) {
				return npc;
			}
		}
		return createUnit(world, FALLBACK_UNIT, faction);
	}

	static void parseWeights(String[] entries, String fallback, List<String> types, List<Integer> weights) {
		for (String entry : entries) {
			String[] parts = entry.split("=");
			int weight = 1;
			if (parts.length > 1) {
				try {
					weight = Integer.parseInt(parts[1].trim());
				}
				catch (NumberFormatException e) {
					AncientWarfareNPC.LOG.warn("Invalid weight in config entry: {}", entry);
				}
			}
			if (!parts[0].trim().isEmpty() && weight > 0) {
				types.add(parts[0].trim());
				weights.add(weight);
			}
		}
		if (types.isEmpty()) {
			types.add(fallback);
			weights.add(1);
		}
	}

	static String pickWeighted(Random rand, List<String> types, List<Integer> weights) {
		int total = 0;
		for (int weight : weights) {
			total += weight;
		}
		int roll = rand.nextInt(total);
		for (int i = 0; i < types.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) {
				return types.get(i);
			}
		}
		return types.get(0);
	}

	/**
	 * @return a loaded surface position siege_spawn_min/max_distance from the city leader, or closer if none is found.
	 * The leader and not the center of the city bounds: Ancient Warfare towns can be hundreds of blocks wide.
	 */
	/**
	 * @return a loaded surface position outside the city bounds, a few blocks beyond the edge (and at least siege_spawn_min_distance from the
	 * leader): among several directions the closest to the leader, so that the attackers do not have to cross the whole of a large city
	 */
	@Nullable
	private static BlockPos findSpawnPosOutside(World world, BlockPos leader, Settlement settlement) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int attempt = 0; attempt < OUTSIDE_SPAWN_ATTEMPTS; attempt++) {
			double angle = world.rand.nextDouble() * Math.PI * 2D;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			// distance from the leader to the edge of the bounds along the direction
			double toEdgeX = dx > 1.0E-6 ? (settlement.getMax().getX() + 1 - leader.getX()) / dx : dx < -1.0E-6 ? (settlement.getMin().getX() - leader.getX()) / dx : Double.MAX_VALUE;
			double toEdgeZ = dz > 1.0E-6 ? (settlement.getMax().getZ() + 1 - leader.getZ()) / dz : dz < -1.0E-6 ? (settlement.getMin().getZ() - leader.getZ()) / dz : Double.MAX_VALUE;
			double distance = Math.max(0D, Math.min(toEdgeX, toEdgeZ)) + OUTSIDE_SPAWN_MIN_MARGIN + world.rand.nextInt(OUTSIDE_SPAWN_EXTRA + 1);
			distance = Math.max(distance, AWDiplomacyStatics.siegeSpawnMinDistance);
			if (distance > OUTSIDE_SPAWN_MAX_DISTANCE) {
				continue;
			}
			BlockPos column = new BlockPos(leader.getX() + dx * distance, 64, leader.getZ() + dz * distance);
			if (settlement.contains(settlement.getDimension(), new BlockPos(column.getX(), settlement.getMin().getY(), column.getZ())) || !world.isAreaLoaded(column, SPAWN_SPREAD + 2)) {
				continue;
			}
			BlockPos top = world.getTopSolidOrLiquidBlock(column);
			if (top.getY() <= 0 || world.getBlockState(top.down()).getMaterial().isLiquid() || distance >= bestDistance) {
				continue;
			}
			best = top;
			bestDistance = distance;
		}
		return best;
	}

	@Nullable
	private static BlockPos findSpawnPos(World world, BlockPos center) {
		int minDistance = AWDiplomacyStatics.siegeSpawnMinDistance;
		int maxDistance = Math.max(minDistance, AWDiplomacyStatics.siegeSpawnMaxDistance);
		for (double scale : new double[] {1D, 0.5D}) {
			for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
				double distance = minDistance + world.rand.nextDouble() * (maxDistance - minDistance);
				distance = Math.min(distance, MAX_ATTACKER_DISTANCE - 16D) * scale;
				double angle = world.rand.nextDouble() * Math.PI * 2D;
				BlockPos column = new BlockPos(center.getX() + MathHelper.cos((float) angle) * distance, 64, center.getZ() + MathHelper.sin((float) angle) * distance);
				if (!world.isAreaLoaded(column, SPAWN_SPREAD + 2)) {
					continue;
				}
				BlockPos top = world.getTopSolidOrLiquidBlock(column);
				if (top.getY() <= 0 || world.getBlockState(top.down()).getMaterial().isLiquid()) {
					continue;
				}
				return top;
			}
		}
		return null;
	}

	/**
	 * Plays a war horn to the players around the city, from the direction of the city.
	 */
	private static void playHorn(World world, BlockPos source) {
		SoundEvent sound = SoundEvent.REGISTRY.getObject(new ResourceLocation("ancientwarfarestructure", HORNS[world.rand.nextInt(HORNS.length)]));
		if (sound == null) {
			return;
		}
		double range = AWDiplomacyStatics.siegePlayerRange;
		for (EntityPlayer player : world.playerEntities) {
			if (!(player instanceof EntityPlayerMP) || player.getDistanceSq(source) > range * range) {
				continue;
			}
			Vec3d direction = new Vec3d(source.getX() + 0.5D - player.posX, 0D, source.getZ() + 0.5D - player.posZ);
			double distance = Math.min(12D, direction.lengthVector());
			direction = direction.lengthVector() < 0.001D ? Vec3d.ZERO : direction.normalize().scale(distance);
			((EntityPlayerMP) player).connection.sendPacket(new SPacketSoundEffect(sound, SoundCategory.HOSTILE, player.posX + direction.x, player.posY,
					player.posZ + direction.z, 2.0F, 1.0F));
		}
	}

	private static void announce(World world, Siege siege, String event, @Nullable Settlement settlement) {
		MinecraftServer server = world.getMinecraftServer();
		if (!AWDiplomacyStatics.announceSieges || server == null) {
			return;
		}
		ITextComponent city = settlement == null ? new TextComponentString("?") : Settlements.displayName(settlement);
		ITextComponent message = new TextComponentTranslation(keyPrefix(siege) + "announce." + event, city, FactionMembership.factionName(siege.getAttackerFaction()));
		message.getStyle().setColor(TextFormatting.RED);
		server.getPlayerList().sendMessage(message);
	}
}
