package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFactionLeader;
import net.shadowmage.ancientwarfare.npc.faction.Diplomacy;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelations;
import net.shadowmage.ancientwarfare.npc.faction.FactionTracker;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionMembershipData;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Military service of the members of a faction, as the vassals of Mount & Blade: when the faction of a player attacks an enemy city, or a
 * city of its faction is attacked, the player is called to arms and must go there.
 * <p>
 * Nothing is simulated away from players (unloaded areas are not updated), so a campaign (a {@link Siege} of origin OFFENSIVE or
 * DEFENSIVE) starts mustering: it waits until a called player comes near the city, then the attack runs as any other. When the muster is
 * over and nobody came, an attack of the player's faction is called off and an attack on a city of its faction plunders it (manpower lost,
 * the city never falls away from players). Attacks started near players (origin AMBIENT) call the members already there.
 * <p>
 * When an attack ends, each called player has served (it took part for service_min_presence_ticks, or was there at the end): standing
 * with its faction and one strike forgiven; otherwise it gets a strike and a standing penalty growing with its strikes, and is expelled
 * as a deserter at service_max_strikes. Players who go offline, peace and attacks stopped by an operator excuse the calls.
 */
public class Service {
	private Service() {}

	private static final int TICKS_PER_MINUTE = 1200;
	private static final int STRIKE_CHECK_TICKS = 1200;
	private static final int CAMPAIGN_TARGET_CHOICES = 3;
	private static final int PROPOSAL_LIST_SIZE = 8;
	private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	private static FactionMembershipData getMembership(World world) {
		return AWGameData.INSTANCE.getData(world, FactionMembershipData.class);
	}

	private static boolean isEnabled() {
		return AWDiplomacyStatics.serviceEnabled && AWDiplomacyStatics.membershipEnabled;
	}

	/**
	 * @return true if the player has a call it has not been excused from, in any attack
	 */
	public static boolean isCalled(SettlementData data, UUID playerId) {
		for (Siege siege : data.getSieges()) {
			Siege.Call call = siege.getCall(playerId);
			if (call != null && !call.isExcused()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Called every service_check_interval: online members without a call may get a campaign (an attack of their faction, or on a city of
	 * their faction).
	 */
	static void tryStartCampaigns(WorldServer world, SettlementData data, long now) {
		MinecraftServer server = world.getMinecraftServer();
		if (!isEnabled() || server == null) {
			return;
		}
		List<FactionMembershipData.Member> members = new ArrayList<>(getMembership(world).getMembers());
		Collections.shuffle(members, world.rand);
		for (FactionMembershipData.Member member : members) {
			if (data.getSieges().size() >= AWDiplomacyStatics.maxActiveSieges) {
				return;
			}
			EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(member.playerId);
			if (player == null || player.world != world || player.isSpectator() || isCalled(data, member.playerId)
					|| world.rand.nextDouble() >= AWDiplomacyStatics.campaignChance) {
				continue;
			}
			boolean offensive = world.rand.nextDouble() < AWDiplomacyStatics.campaignOffenseShare;
			if (startCampaign(world, data, player, member.faction, offensive, now, false, null) == null) {
				startCampaign(world, data, player, member.faction, !offensive, now, false, null);
			}
		}
	}

	/**
	 * Starts a campaign for the player: an attack of its faction on an enemy city at war with it (offensive), or an attack of an enemy at
	 * war on a city of its faction, within service_call_range.
	 *
	 * @param ignoreCooldown true to ignore the cooldown of the cities (operator command)
	 * @param forcedKind     raid or full siege chosen by an operator (no full siege cooldown), null to choose as for automatic attacks
	 * @return the campaign, or null if no city can be attacked or defended
	 */
	@Nullable
	public static Siege startCampaign(WorldServer world, SettlementData data, EntityPlayerMP player, String faction, boolean offensive, long now,
			boolean ignoreCooldown, @Nullable Siege.Kind forcedKind) {
		List<String> enemies = FactionRelations.getDeclaredEnemies(world, faction);
		if (enemies.isEmpty()) {
			return null;
		}
		double rangeSq = (double) AWDiplomacyStatics.serviceCallRange * AWDiplomacyStatics.serviceCallRange;
		int dimension = world.provider.getDimension();
		List<Settlement> candidates = new ArrayList<>();
		for (Settlement city : data.getAll()) {
			if (city.getDimension() != dimension || !city.isActive() || city.getType() != Settlement.Type.FACTION_CITY || Sieges.isBesieged(city)
					|| !ignoreCooldown && !Sieges.isCooledDown(city, now) || player.getDistanceSq(Sieges.getTarget(world, city)) > rangeSq) {
				continue;
			}
			if (offensive ? enemies.contains(city.getFaction()) && Sieges.canStart(world, city, faction) == null : faction.equals(city.getFaction())) {
				candidates.add(city);
			}
		}
		if (offensive) {
			// the army marches on one of the nearest enemy cities
			candidates.sort(Comparator.comparingDouble(city -> player.getDistanceSq(Sieges.getTarget(world, city))));
			if (candidates.isEmpty()) {
				return null;
			}
			Settlement city = candidates.get(world.rand.nextInt(Math.min(CAMPAIGN_TARGET_CHOICES, candidates.size())));
			Siege.Kind kind = forcedKind != null ? forcedKind : Sieges.chooseKind(world, city, faction, now);
			return Sieges.start(world, data, city, faction, kind, Siege.Origin.OFFENSIVE);
		}
		Collections.shuffle(candidates, world.rand);
		for (Settlement city : candidates) {
			List<String> attackers = new ArrayList<>(enemies);
			attackers.removeIf(enemy -> Sieges.canStart(world, city, enemy) != null);
			if (!attackers.isEmpty()) {
				String attacker = attackers.get(world.rand.nextInt(attackers.size()));
				Siege.Kind kind = forcedKind != null ? forcedKind : Sieges.chooseKind(world, city, attacker, now);
				return Sieges.start(world, data, city, attacker, kind, Siege.Origin.DEFENSIVE);
			}
		}
		return null;
	}

	/**
	 * A raid or siege started: the online members of the two factions are called, within service_call_range for a campaign, already near
	 * the city for an attack started near players.
	 */
	static void onSiegeStarted(WorldServer world, Siege siege, Settlement settlement) {
		MinecraftServer server = world.getMinecraftServer();
		if (!isEnabled() || server == null) {
			return;
		}
		boolean muster = siege.getState() == Siege.State.MUSTER;
		double range = muster ? AWDiplomacyStatics.serviceCallRange : AWDiplomacyStatics.serviceMusterRange;
		BlockPos target = Sieges.getTarget(world, settlement);
		List<EntityPlayerMP> called = new ArrayList<>();
		double farthest = 0;
		for (FactionMembershipData.Member member : getMembership(world).getMembers()) {
			boolean attacking = member.faction.equals(siege.getAttackerFaction());
			if (!attacking && !member.faction.equals(settlement.getFaction())) {
				continue;
			}
			EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(member.playerId);
			if (player == null || player.world != world || player.isSpectator() || player.getDistanceSq(target) > range * range) {
				continue;
			}
			Siege.Call call = new Siege.Call(member.faction, attacking);
			call.lastReminder = world.getTotalWorldTime();
			siege.addCall(member.playerId, call);
			called.add(player);
			farthest = Math.max(farthest, Math.sqrt(player.getDistanceSq(target)));
		}
		if (muster) {
			// time to get there: the base muster time plus the way of the farthest called player
			long ticks = Math.min(AWDiplomacyStatics.serviceMusterMaxTicks,
					AWDiplomacyStatics.serviceMusterTicks + (long) (farthest * AWDiplomacyStatics.serviceMusterTicksPerBlock));
			siege.setMusterUntil(world.getTotalWorldTime() + ticks);
		}
		for (EntityPlayerMP player : called) {
			Siege.Call call = siege.getCall(player.getUniqueID());
			sendCall(world, player, siege, settlement, call, target);
			AncientWarfareNPC.LOG.info("{} is called to {} city {} ({}) in {} {}, {} blocks away", player.getName(), call.isAttacking() ? "attack" : "defend",
					settlement.getId(), settlement.getFaction(), siege.getKind().getName(), siege.getId(), (int) Math.sqrt(player.getDistanceSq(target)));
		}
		Settlements.getData(world).markDirty();
	}

	private static void sendCall(World world, EntityPlayerMP player, Siege siege, Settlement settlement, Siege.Call call, BlockPos target) {
		ITextComponent city = Settlements.displayName(settlement);
		ITextComponent message;
		if (siege.getState() == Siege.State.MUSTER) {
			int minutes = minutesLeft(world, siege);
			message = call.isAttacking()
					? new TextComponentTranslation("command.aw.service.call.attack", FactionMembership.factionName(call.getFaction()), city, target.getX(), target.getY(),
					target.getZ(), minutes)
					: new TextComponentTranslation("command.aw.service.call.defend", city, FactionMembership.factionName(siege.getAttackerFaction()), target.getX(),
					target.getY(), target.getZ(), minutes);
		} else {
			message = call.isAttacking()
					? new TextComponentTranslation("command.aw.service.call.attack.here", FactionMembership.factionName(call.getFaction()), city)
					: new TextComponentTranslation("command.aw.service.call.defend.here", city, FactionMembership.factionName(siege.getAttackerFaction()));
		}
		message.getStyle().setColor(TextFormatting.GOLD).setBold(true);
		player.sendMessage(message);
	}

	private static int minutesLeft(World world, Siege siege) {
		return (int) Math.max(0, Math.ceil((siege.getMusterUntil() - world.getTotalWorldTime()) / (double) TICKS_PER_MINUTE));
	}

	/**
	 * Called every second for each server world: excuses the called players who went offline, reminds the others during the muster,
	 * counts the presence of those who take part in the attack (their own soldiers join the assault of an attack of their faction),
	 * and forgives old strikes.
	 */
	static void tick(WorldServer world, SettlementData data, long now) {
		MinecraftServer server = world.getMinecraftServer();
		if (!isEnabled() || server == null) {
			return;
		}
		int dimension = world.provider.getDimension();
		double presenceSq = (double) AWDiplomacyStatics.servicePresenceRange * AWDiplomacyStatics.servicePresenceRange;
		for (Siege siege : data.getSieges()) {
			if (siege.getDimension() != dimension || siege.getCalls().isEmpty()) {
				continue;
			}
			Settlement settlement = data.get(siege.getSettlementId());
			if (settlement == null) {
				continue;
			}
			BlockPos target = Sieges.getTarget(world, settlement);
			for (Map.Entry<UUID, Siege.Call> entry : siege.getCalls().entrySet()) {
				Siege.Call call = entry.getValue();
				if (call.isExcused()) {
					continue;
				}
				EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
				if (player == null) {
					if (AWDiplomacyStatics.serviceExcuseOffline) {
						call.setExcused();
						data.markDirty();
					}
					continue;
				}
				if (player.world != world) {
					continue;
				}
				if (siege.getState() == Siege.State.MUSTER) {
					if (AWDiplomacyStatics.serviceReminderTicks > 0 && now - call.lastReminder >= AWDiplomacyStatics.serviceReminderTicks) {
						call.lastReminder = now;
						sendReminder(world, player, siege, settlement, call, target);
					}
				} else if (player.getDistanceSq(target) <= presenceSq) {
					call.addPresenceTicks(20);
					data.markDirty();
					if (call.isAttacking() && siege.getState() == Siege.State.WAVE) {
						Armies.joinAssault(player, siege);
					}
				}
			}
		}
		if (dimension == 0 && now % STRIKE_CHECK_TICKS == 0) {
			forgiveStrikes(world, server);
		}
	}

	private static void sendReminder(World world, EntityPlayerMP player, Siege siege, Settlement settlement, Siege.Call call, BlockPos target) {
		double dx = target.getX() + 0.5D - player.posX;
		double dz = target.getZ() + 0.5D - player.posZ;
		int distance = (int) Math.sqrt(dx * dx + dz * dz);
		ITextComponent message = new TextComponentTranslation(call.isAttacking() ? "command.aw.service.reminder.attack" : "command.aw.service.reminder.defend",
				Settlements.displayName(settlement), distance, direction(dx, dz), minutesLeft(world, siege));
		message.getStyle().setColor(TextFormatting.YELLOW);
		player.sendMessage(message);
	}

	/**
	 * @return the compass direction (north = -z, east = +x) as a translated text
	 */
	public static ITextComponent direction(double dx, double dz) {
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		int index = (int) Math.round(((degrees % 360D) + 360D) % 360D / 45D) % DIRECTIONS.length;
		return new TextComponentTranslation("command.aw.service.direction." + DIRECTIONS[index]);
	}

	/**
	 * @return true if a called player (not excused) is within service_muster_range of the city leader: the campaign begins
	 */
	static boolean hasCalledPlayerNear(WorldServer world, Siege siege, Settlement settlement) {
		MinecraftServer server = world.getMinecraftServer();
		if (server == null) {
			return false;
		}
		BlockPos target = Sieges.getTarget(world, settlement);
		double rangeSq = (double) AWDiplomacyStatics.serviceMusterRange * AWDiplomacyStatics.serviceMusterRange;
		for (Map.Entry<UUID, Siege.Call> entry : siege.getCalls().entrySet()) {
			EntityPlayerMP player = entry.getValue().isExcused() ? null : server.getPlayerList().getPlayerByUUID(entry.getKey());
			if (player != null && player.world == world && !player.isSpectator() && player.getDistanceSq(target) <= rangeSq) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The muster is over and no called player came: an attack of the player's faction is called off, an attack on a city of the player's
	 * faction plunders it.
	 */
	static void resolveUnattended(WorldServer world, SettlementData data, Siege siege, Settlement settlement) {
		if (siege.getOrigin() != Siege.Origin.DEFENSIVE) {
			Sieges.end(world, data, siege, Sieges.Outcome.UNSUPPORTED);
			return;
		}
		int loss = Math.min(settlement.getManpower(), (int) Math.ceil(siege.getTotalWaves() * siege.getAttackersPerWave() * AWDiplomacyStatics.servicePlunderRatio));
		settlement.setManpower(settlement.getManpower() - loss);
		data.markDirty();
		AncientWarfareNPC.LOG.info("{} {}: nobody came to defend city {}, it loses {} manpower", siege.getKind().getName(), siege.getId(), settlement.getId(), loss);
		MinecraftServer server = world.getMinecraftServer();
		if (server != null) {
			for (Map.Entry<UUID, Siege.Call> entry : siege.getCalls().entrySet()) {
				EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
				if (player != null && !entry.getValue().isAttacking()) {
					ITextComponent message = new TextComponentTranslation("command.aw.service.plundered", Settlements.displayName(settlement), loss);
					message.getStyle().setColor(TextFormatting.RED);
					player.sendMessage(message);
				}
			}
		}
		Sieges.end(world, data, siege, Sieges.Outcome.PLUNDERED);
	}

	/**
	 * The attack is over: each called player, still a member of the faction it was called for, has served or gets a strike.
	 * Peace and attacks stopped by an operator excuse everybody.
	 */
	static void onSiegeEnded(World world, Siege siege, @Nullable Settlement settlement, Sieges.Outcome outcome) {
		MinecraftServer server = world.getMinecraftServer();
		if (server != null && isEnabled()) {
			penalizeLostProposal(server, world, siege, settlement, outcome);
		}
		if (!isEnabled() || server == null || siege.getCalls().isEmpty() || outcome == Sieges.Outcome.PEACE || outcome == Sieges.Outcome.STOPPED) {
			return;
		}
		FactionMembershipData membership = getMembership(world);
		BlockPos target = settlement == null ? null : world instanceof WorldServer ? Sieges.getTarget((WorldServer) world, settlement) : settlement.getLastLeaderPos();
		double presenceSq = (double) AWDiplomacyStatics.servicePresenceRange * AWDiplomacyStatics.servicePresenceRange;
		ITextComponent city = settlement == null ? new TextComponentString("?") : Settlements.displayName(settlement);
		for (Map.Entry<UUID, Siege.Call> entry : siege.getCalls().entrySet()) {
			Siege.Call call = entry.getValue();
			FactionMembershipData.Member member = membership.getMember(entry.getKey());
			if (call.isExcused() || member == null || !member.faction.equals(call.getFaction())) {
				continue; // excused, or no longer a member of that faction
			}
			EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
			boolean presentAtEnd = player != null && target != null && player.world == world && player.getDistanceSq(target) <= presenceSq;
			if (presentAtEnd || call.getPresenceTicks() >= AWDiplomacyStatics.serviceMinPresenceTicks) {
				serve(world, member, player, city);
				if (outcome == Sieges.Outcome.TAKEN && call.isAttacking() && settlement != null && AWDiplomacyStatics.factionChestsEnabled
						&& AWDiplomacyStatics.chestPlunderTicks > 0) {
					// war booty: the chests of the conquered city are open to those who took it
					long now = world.getTotalWorldTime();
					settlement.addPlunderer(member.playerId, now + AWDiplomacyStatics.chestPlunderTicks, now);
					Settlements.getData(world).markDirty();
					if (player != null) {
						ITextComponent message = new TextComponentTranslation("command.aw.service.war_booty", city,
								(int) Math.ceil(AWDiplomacyStatics.chestPlunderTicks / (double) TICKS_PER_MINUTE));
						message.getStyle().setColor(TextFormatting.GOLD);
						player.sendMessage(message);
					}
				}
			} else {
				strike(server, world, member, player, city);
			}
		}
	}

	/**
	 * An npc of a faction was killed: a member who is serving in an attack of its faction (called, the attack going on, near the city) and
	 * killed it, or whose own soldier did, gains standing with its faction, up to service_kill_reward_cap kills per attack. The enemies are
	 * the defenders for those who attack and the attackers for those who defend.
	 */
	public static void onEnemyKilled(NpcFaction victim, DamageSource source) {
		if (!isEnabled() || AWDiplomacyStatics.serviceKillRewardStanding <= 0 || AWDiplomacyStatics.serviceKillRewardCap <= 0
				|| !(victim.world instanceof WorldServer)) {
			return;
		}
		Entity killer = source.getTrueSource();
		UUID killerId = killer instanceof EntityPlayerMP ? killer.getUniqueID() : killer instanceof NpcPlayerOwned ? ((NpcPlayerOwned) killer).getOwner().getUUID() : null;
		if (killerId == null) {
			return;
		}
		WorldServer world = (WorldServer) victim.world;
		MinecraftServer server = world.getMinecraftServer();
		EntityPlayerMP player = server == null ? null : server.getPlayerList().getPlayerByUUID(killerId);
		FactionMembershipData.Member member = getMembership(world).getMember(killerId);
		if (player == null || player.world != world || member == null) {
			return;
		}
		SettlementData data = Settlements.getData(world);
		double presenceSq = (double) AWDiplomacyStatics.servicePresenceRange * AWDiplomacyStatics.servicePresenceRange;
		for (Siege siege : data.getSieges()) {
			Siege.Call call = siege.getCall(killerId);
			Settlement settlement = data.get(siege.getSettlementId());
			if (call == null || call.isExcused() || settlement == null || siege.getState() == Siege.State.MUSTER || siege.getDimension() != world.provider.getDimension()
					|| !member.faction.equals(call.getFaction()) || call.getKills() >= AWDiplomacyStatics.serviceKillRewardCap) {
				continue;
			}
			String enemy = call.isAttacking() ? settlement.getFaction() : siege.getAttackerFaction();
			if (!enemy.equals(victim.getFaction()) || player.getDistanceSq(Sieges.getTarget(world, settlement)) > presenceSq) {
				continue;
			}
			call.addKill();
			data.markDirty();
			FactionTracker.INSTANCE.adjustStandingFor(world, member.playerName, member.faction, AWDiplomacyStatics.serviceKillRewardStanding);
			player.sendStatusMessage(new TextComponentTranslation("command.aw.service.kill_reward", AWDiplomacyStatics.serviceKillRewardStanding,
					FactionMembership.factionName(member.faction), call.getKills(), AWDiplomacyStatics.serviceKillRewardCap), true);
			return;
		}
	}

	/**
	 * A campaign the player proposed to its ruler was lost (the city repelled it, or it was lifted): the proposer loses standing with
	 * its faction, a fraction of what proposing it required. Not when it was never fought (called off for lack of support, the proposer
	 * gets a strike then), nor when peace or an operator ended it.
	 */
	private static void penalizeLostProposal(MinecraftServer server, World world, Siege siege, @Nullable Settlement settlement, Sieges.Outcome outcome) {
		if (siege.getProposer() == null || siege.getProposerPenalty() <= 0 || outcome != Sieges.Outcome.DEFENDED && outcome != Sieges.Outcome.LIFTED) {
			return;
		}
		FactionMembershipData.Member member = getMembership(world).getMember(siege.getProposer());
		if (member == null || !member.faction.equals(siege.getAttackerFaction())) {
			return; // no longer a member of the faction it proposed the campaign to
		}
		FactionTracker.INSTANCE.adjustStandingFor(world, member.playerName, member.faction, -siege.getProposerPenalty());
		AncientWarfareNPC.LOG.info("{} proposed {} {} and it was lost ({}): standing with the {} -{}", member.playerName, siege.getKind().getName(), siege.getId(),
				outcome, member.faction, siege.getProposerPenalty());
		EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(member.playerId);
		if (player != null) {
			ITextComponent city = settlement == null ? new TextComponentString("?") : Settlements.displayName(settlement);
			ITextComponent message = new TextComponentTranslation("command.aw.service.proposal_failed", city, FactionMembership.factionName(member.faction),
					siege.getProposerPenalty());
			message.getStyle().setColor(TextFormatting.RED);
			player.sendMessage(message);
		}
	}

	private static void serve(World world, FactionMembershipData.Member member, @Nullable EntityPlayerMP player, ITextComponent city) {
		if (AWDiplomacyStatics.serviceRewardStanding > 0) {
			FactionTracker.INSTANCE.adjustStandingFor(world, member.playerName, member.faction, AWDiplomacyStatics.serviceRewardStanding);
		}
		getMembership(world).setStrikes(member.playerId, member.getStrikes() - 1, member.getLastStrikeDay());
		AncientWarfareNPC.LOG.info("{} answered the call to arms of the {} ({} strikes left)", member.playerName, member.faction, member.getStrikes());
		if (player != null) {
			ITextComponent message = new TextComponentTranslation("command.aw.service.served", city, FactionMembership.factionName(member.faction),
					AWDiplomacyStatics.serviceRewardStanding);
			message.getStyle().setColor(TextFormatting.GREEN);
			player.sendMessage(message);
		}
	}

	private static void strike(MinecraftServer server, World world, FactionMembershipData.Member member, @Nullable EntityPlayerMP player, ITextComponent city) {
		int strikes = member.getStrikes() + 1;
		getMembership(world).setStrikes(member.playerId, strikes, Diplomacy.getDay(world));
		int penalty = AWDiplomacyStatics.serviceMissPenalty * strikes;
		if (penalty > 0) {
			FactionTracker.INSTANCE.adjustStandingFor(world, member.playerName, member.faction, -penalty);
		}
		AncientWarfareNPC.LOG.info("{} missed the call to arms of the {}: strike {}/{}, standing -{}", member.playerName, member.faction, strikes,
				AWDiplomacyStatics.serviceMaxStrikes, penalty);
		if (AWDiplomacyStatics.serviceMaxStrikes > 0 && strikes >= AWDiplomacyStatics.serviceMaxStrikes) {
			FactionMembership.leave(server, member.playerId, member.playerName, AWDiplomacyStatics.expelStandingPenalty, "command.aw.service.announce.deserter");
		} else if (player != null) {
			ITextComponent message = new TextComponentTranslation("command.aw.service.strike", city, FactionMembership.factionName(member.faction), penalty, strikes,
					AWDiplomacyStatics.serviceMaxStrikes);
			message.getStyle().setColor(TextFormatting.RED);
			player.sendMessage(message);
		}
	}

	/**
	 * One strike is forgiven every service_strike_decay_days days without missed calls.
	 */
	private static void forgiveStrikes(World world, MinecraftServer server) {
		if (AWDiplomacyStatics.serviceStrikeDecayDays <= 0) {
			return;
		}
		long day = Diplomacy.getDay(world);
		FactionMembershipData membership = getMembership(world);
		for (FactionMembershipData.Member member : membership.getMembers()) {
			if (member.getStrikes() <= 0) {
				continue;
			}
			if (day < member.getLastStrikeDay()) {
				membership.setStrikes(member.playerId, member.getStrikes(), day); // time set back
			} else if (day - member.getLastStrikeDay() >= AWDiplomacyStatics.serviceStrikeDecayDays) {
				membership.setStrikes(member.playerId, member.getStrikes() - 1, day);
				EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(member.playerId);
				if (player != null) {
					player.sendMessage(new TextComponentTranslation("command.aw.service.forgiven", FactionMembership.factionName(member.faction), member.getStrikes(),
							AWDiplomacyStatics.serviceMaxStrikes));
				}
			}
		}
	}

	/**
	 * Checks that the player can propose a campaign now: a member near the ruler of a city of its faction, without a call, past the
	 * cooldown of its last accepted proposal.
	 *
	 * @return the faction of the player
	 */
	public static String checkProposer(EntityPlayerMP player) throws CommandException {
		if (!isEnabled() || !AWDiplomacyStatics.siegesEnabled) {
			throw new CommandException("command.aw.service.disabled");
		}
		World world = player.world;
		String faction = FactionMembership.getFaction(world, player.getUniqueID());
		if (faction == null) {
			throw new CommandException("command.aw.membership.not_member");
		}
		if (!FactionMembership.findNearestLeader(world, player, faction, AWDiplomacyStatics.leaderJoinRange).isPresent()) {
			throw new CommandException("command.aw.service.propose.no_ruler", FactionMembership.factionName(faction));
		}
		if (isCalled(Settlements.getData(world), player.getUniqueID())) {
			throw new CommandException("command.aw.service.propose.called");
		}
		FactionMembershipData.Member member = getMembership(world).getMember(player.getUniqueID());
		long day = Diplomacy.getDay(world);
		if (member != null && member.getLastProposalDay() != Long.MIN_VALUE && day >= member.getLastProposalDay()
				&& day - member.getLastProposalDay() < AWDiplomacyStatics.campaignProposalCooldownDays) {
			throw new CommandException("command.aw.service.propose.cooldown", AWDiplomacyStatics.campaignProposalCooldownDays - (day - member.getLastProposalDay()));
		}
		return faction;
	}

	/**
	 * @return the enemy cities (at war with the faction, known, within service_call_range of the player) that can be attacked now, nearest first
	 */
	public static List<Settlement> getProposalTargets(WorldServer world, EntityPlayerMP player, String faction, long now) {
		List<String> enemies = FactionRelations.getDeclaredEnemies(world, faction);
		double rangeSq = (double) AWDiplomacyStatics.serviceCallRange * AWDiplomacyStatics.serviceCallRange;
		int dimension = world.provider.getDimension();
		List<Settlement> targets = new ArrayList<>();
		for (Settlement city : Settlements.getData(world).getAll()) {
			if (city.getDimension() == dimension && city.isActive() && city.getType() == Settlement.Type.FACTION_CITY && enemies.contains(city.getFaction())
					&& Sieges.canStart(world, city, faction) == null && Sieges.isCooledDown(city, now)
					&& player.getDistanceSq(Sieges.getTarget(world, city)) <= rangeSq) {
				targets.add(city);
			}
		}
		targets.sort(Comparator.comparingDouble(city -> player.getDistanceSq(Sieges.getTarget(world, city))));
		return targets;
	}

	/**
	 * @return true if a member can propose a full siege of the city now: proposals wait campaign_proposal_siege_cooldown_days, not the
	 * full_siege_cooldown_days of the automatic full sieges
	 */
	public static boolean isFullSiegeAllowed(Settlement city, long now) {
		return now - city.getLastFullSiegeEnd() >= (long) AWDiplomacyStatics.campaignProposalSiegeCooldownDays * 24000L;
	}

	/**
	 * @return the standing a member needs to propose a raid or a full siege
	 */
	public static int proposalStanding(Siege.Kind kind) {
		return kind == Siege.Kind.SIEGE ? AWDiplomacyStatics.campaignProposalSiegeStanding : AWDiplomacyStatics.campaignProposalStanding;
	}

	/**
	 * @return the standing the proposer loses when the proposed campaign is lost: a fraction of the standing needed to propose it
	 */
	public static int proposalPenalty(Siege.Kind kind) {
		return (int) Math.round(proposalStanding(kind) * AWDiplomacyStatics.campaignProposalFailureRatio);
	}

	/**
	 * [Propose a campaign] in the ruler's dialogue: lists the enemy cities that can be attacked, each with clickable [raid] and [siege].
	 */
	public static void listProposals(EntityPlayerMP player) throws CommandException {
		String faction = checkProposer(player);
		WorldServer world = (WorldServer) player.world;
		long now = world.getTotalWorldTime();
		List<Settlement> targets = getProposalTargets(world, player, faction, now);
		if (targets.isEmpty()) {
			throw new CommandException("command.aw.service.propose.none", AWDiplomacyStatics.serviceCallRange);
		}
		ITextComponent header = new TextComponentTranslation("command.aw.service.propose.header", FactionMembership.factionName(faction));
		header.getStyle().setColor(TextFormatting.GOLD);
		player.sendMessage(header);
		for (Settlement city : targets.subList(0, Math.min(PROPOSAL_LIST_SIZE, targets.size()))) {
			BlockPos target = Sieges.getTarget(world, city);
			double dx = target.getX() + 0.5D - player.posX;
			double dz = target.getZ() + 0.5D - player.posZ;
			ITextComponent line = new TextComponentTranslation("command.aw.service.propose.entry", Settlements.displayName(city), (int) Math.sqrt(dx * dx + dz * dz),
					direction(dx, dz));
			line.appendText(" ");
			line.appendSibling(clickable(new TextComponentTranslation("command.aw.service.propose.raid"), TextFormatting.GREEN,
					"/awservice propose " + city.getId() + " raid", new TextComponentTranslation("command.aw.service.propose.raid.hover",
							proposalStanding(Siege.Kind.RAID), proposalPenalty(Siege.Kind.RAID))));
			if (isFullSiegeAllowed(city, now)) {
				line.appendText(" ");
				line.appendSibling(clickable(new TextComponentTranslation("command.aw.service.propose.siege"), TextFormatting.RED,
						"/awservice propose " + city.getId() + " siege",
						new TextComponentTranslation("command.aw.service.propose.siege.hover", proposalStanding(Siege.Kind.SIEGE), proposalPenalty(Siege.Kind.SIEGE))));
			}
			player.sendMessage(line);
		}
	}

	/**
	 * /awservice propose (id) (kind): {@link #propose}, the ruler answers in chat.
	 */
	public static void proposeInChat(EntityPlayerMP player, int cityId, Siege.Kind kind) throws CommandException {
		ITextComponent answer = propose(player, cityId, kind);
		String faction = FactionMembership.getFaction(player.world, player.getUniqueID());
		NpcFactionLeader ruler = faction == null ? null : FactionMembership.findNearestLeader(player.world, player, faction, AWDiplomacyStatics.leaderJoinRange).orElse(null);
		player.sendMessage(ruler == null ? answer : new TextComponentTranslation("command.aw.service.propose.says", ruler.getDisplayName(), answer));
	}

	/**
	 * The player proposes a raid or a full siege of an enemy city to the ruler, who accepts if the player's standing is high enough and its
	 * faction is not exhausted by that war: the campaign starts mustering and the player is called.
	 *
	 * @return the answer of the ruler
	 */
	public static ITextComponent propose(EntityPlayerMP player, int cityId, Siege.Kind kind) throws CommandException {
		String faction = checkProposer(player);
		WorldServer world = (WorldServer) player.world;
		SettlementData data = Settlements.getData(world);
		long now = world.getTotalWorldTime();
		Settlement city = data.get(cityId);
		if (city == null || !getProposalTargets(world, player, faction, now).contains(city)) {
			throw new CommandException("command.aw.service.propose.invalid");
		}
		if (kind == Siege.Kind.SIEGE && !isFullSiegeAllowed(city, now)) {
			throw new CommandException("command.aw.service.propose.no_siege", Settlements.displayName(city));
		}
		int standing = FactionTracker.INSTANCE.getStandingFor(world, player.getName(), faction);
		int needed = proposalStanding(kind);
		if (data.getSieges().size() >= AWDiplomacyStatics.maxActiveSieges) {
			return new TextComponentTranslation("command.aw.service.propose.refused.busy");
		}
		if (standing < needed) {
			return new TextComponentTranslation("command.aw.service.propose.refused.standing", standing, needed);
		}
		int exhaustion = Diplomacy.getData(world).get(faction, city.getFaction()).getExhaustion(faction, city.getFaction());
		if (AWDiplomacyStatics.diplomacyEnabled && exhaustion >= AWDiplomacyStatics.peaceExhaustion) {
			return new TextComponentTranslation("command.aw.service.propose.refused.exhausted", FactionMembership.factionName(city.getFaction()));
		}
		getMembership(world).setLastProposalDay(player.getUniqueID(), Diplomacy.getDay(world));
		AncientWarfareNPC.LOG.info("{} proposed a {} of city {} ({}) to the {}: accepted", player.getName(), kind.getName(), city.getId(), city.getFaction(), faction);
		ITextComponent answer = new TextComponentTranslation("command.aw.service.propose.accepted." + kind.getName(), Settlements.displayName(city));
		Siege siege = Sieges.start(world, data, city, faction, kind, Siege.Origin.OFFENSIVE);
		// the proposer answers for the result: if the campaign is lost (see onSiegeEnded) it loses standing
		siege.setProposal(player.getUniqueID(), proposalPenalty(kind));
		data.markDirty();
		return answer;
	}

	private static ITextComponent clickable(ITextComponent text, TextFormatting color, String command, ITextComponent hover) {
		text.setStyle(new Style().setColor(color).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
		return text;
	}

	/**
	 * Strikes and current calls of the player, for /awmembership status.
	 */
	public static void sendStatus(ICommandSender sender, EntityPlayerMP player) {
		FactionMembershipData.Member member = getMembership(player.world).getMember(player.getUniqueID());
		if (!isEnabled() || member == null) {
			return;
		}
		sender.sendMessage(new TextComponentTranslation("command.aw.service.status.strikes", member.getStrikes(), AWDiplomacyStatics.serviceMaxStrikes));
		SettlementData data = Settlements.getData(player.world);
		for (Siege siege : data.getSieges()) {
			Siege.Call call = siege.getCall(player.getUniqueID());
			Settlement settlement = data.get(siege.getSettlementId());
			if (call == null || call.isExcused() || settlement == null) {
				continue;
			}
			ITextComponent city = Settlements.displayName(settlement);
			String side = call.isAttacking() ? "attack" : "defend";
			if (siege.getState() == Siege.State.MUSTER) {
				BlockPos target = settlement.getLastLeaderPos();
				double dx = target.getX() + 0.5D - player.posX;
				double dz = target.getZ() + 0.5D - player.posZ;
				sender.sendMessage(new TextComponentTranslation("command.aw.service.status.muster." + side, city, (int) Math.sqrt(dx * dx + dz * dz), direction(dx, dz),
						minutesLeft(player.world, siege)));
			} else {
				sender.sendMessage(new TextComponentTranslation("command.aw.service.status.fighting." + side, city, call.getPresenceTicks() / 20,
						AWDiplomacyStatics.serviceMinPresenceTicks / 20));
			}
		}
	}
}
