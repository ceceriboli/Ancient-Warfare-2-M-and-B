package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
import net.shadowmage.ancientwarfare.npc.ai.faction.NpcAIFactionAssault;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelations;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Sieges of faction cities by factions at war with them. See {@link Siege}.
 */
public class Sieges {
	private Sieges() {}

	public enum Outcome {
		DEFENDED, TAKEN, LIFTED, PEACE, STOPPED
	}

	private static final String BAR_PREFIX = "siege:";
	private static final double MAX_ATTACKER_DISTANCE = 128D;
	private static final int RECONNECT_GRACE_TICKS = 200;
	private static final int DAY_TICKS = 24000;
	private static final int SPAWN_ATTEMPTS = 20;
	private static final int SPAWN_SPREAD = 3;
	private static final String UNIT_PREFIX = "ancientwarfarenpc";
	private static final String FALLBACK_UNIT = "faction.soldier";
	private static final String[] HORNS = {"horn_1", "horn_3", "horn_5", "horn_6", "horn_7", "horn_8"};

	public static boolean isBesieged(Settlement settlement) {
		return settlement.getActiveSiegeId() != 0;
	}

	@Nullable
	public static Siege getSiegeOf(World world, Settlement settlement) {
		return isBesieged(settlement) ? Settlements.getData(world).getSiege(settlement.getActiveSiegeId()) : null;
	}

	/**
	 * @return null if the faction can besiege the settlement, otherwise the translation key of the reason why not
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
		return null;
	}

	/**
	 * Starts a siege, the size depends on the strength of the city (garrison units loaded and manpower). Check {@link #canStart} first.
	 */
	public static Siege start(WorldServer world, SettlementData data, Settlement settlement, String attackerFaction) {
		int strength = countGarrison(world, settlement) + settlement.getManpower();
		int total = (int) Math.round(strength * AWDiplomacyStatics.siegeStrengthRatio);
		total = Math.min(AWDiplomacyStatics.siegeMaxAttackers, Math.max(AWDiplomacyStatics.siegeMinAttackers, total));
		int waves = Math.max(1, Math.min(AWDiplomacyStatics.siegeWaves, total));
		int perWave = (int) Math.ceil((double) total / waves);
		Siege siege = Siege.create(data.getNextSiegeId(), settlement, attackerFaction, waves, perWave);
		data.addSiege(siege);
		settlement.setActiveSiegeId(siege.getId());
		data.markDirty();
		announce(world, "command.aw.siege.announce.start", settlement, attackerFaction);
		playHorn(world, settlement.getCenter());
		AncientWarfareNPC.LOG.info("Siege {} of city {} ({}) by {} started: strength {}, {} waves of {} attackers", siege.getId(), settlement.getId(),
				settlement.getFaction(), attackerFaction, strength, waves, perWave);
		return siege;
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
		if (AWDiplomacyStatics.siegesEnabled && (now / 20) % Math.max(1, AWDiplomacyStatics.siegeCheckInterval / 20) == 0) {
			tryStartSiege(world, data, now);
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
	 * An attacker was loaded (or spawned): it joins its siege again, or leaves if the siege is over.
	 */
	static void onAttackerJoinWorld(NpcFaction npc) {
		Siege siege = Settlements.getData(npc.world).getSiege(npc.getEntityData().getInteger(Settlements.TAG_SIEGE));
		if (siege == null) {
			Settlements.disband(npc, npc.world.getTotalWorldTime() + 20 + npc.world.rand.nextInt(100));
			return;
		}
		siege.addAttacker(npc);
		getOrAddAssaultAI(npc);
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
		for (NpcFaction npc : siege.getLoadedAttackers()) {
			if (npc.getDistanceSq(center) > MAX_ATTACKER_DISTANCE * MAX_ATTACKER_DISTANCE) {
				siege.removeAttacker(npc);
				Settlements.disband(npc, now + 20);
			} else {
				aim(npc, target);
			}
		}

		if (siege.getTotalTicks() >= AWDiplomacyStatics.siegeMaxTicks) {
			end(world, data, siege, Outcome.LIFTED);
			return;
		}
		if (siege.getState() == Siege.State.WARMUP) {
			if (siege.getStateTicks() >= AWDiplomacyStatics.siegeWarmupTicks && spawnWave(world, siege, settlement, target)) {
				siege.setWavesSpawned(siege.getWavesSpawned() + 1);
				siege.setState(Siege.State.WAVE);
				playHorn(world, center);
			}
		} else if (siege.getLoadedAttackers().isEmpty() && now >= siege.reconnectGraceUntil) {
			if (siege.getWavesSpawned() >= siege.getTotalWaves()) {
				end(world, data, siege, Outcome.DEFENDED);
				return;
			}
			siege.setState(Siege.State.WARMUP);
		}
		showBar(world, siege, settlement, barKeys);
	}

	private static void showBar(WorldServer world, Siege siege, Settlement settlement, Set<String> barKeys) {
		String key = BAR_PREFIX + siege.getId();
		barKeys.add(key);
		ITextComponent city = Settlements.displayName(settlement);
		ITextComponent attacker = FactionMembership.factionName(siege.getAttackerFaction());
		ITextComponent name;
		float percent;
		if (siege.getState() == Siege.State.WARMUP) {
			name = new TextComponentTranslation("command.aw.siege.bar.warmup", city, attacker, siege.getWavesSpawned() + 1, siege.getTotalWaves());
			percent = (float) siege.getStateTicks() / AWDiplomacyStatics.siegeWarmupTicks;
		} else {
			name = new TextComponentTranslation("command.aw.siege.bar.wave", city, attacker, siege.getWavesSpawned(), siege.getTotalWaves());
			float health = 0;
			for (NpcFaction npc : siege.getLoadedAttackers()) {
				health += npc.getHealth();
			}
			percent = siege.getWaveMaxHealth() <= 0 ? 0F : health / siege.getWaveMaxHealth();
		}
		AxisAlignedBB area = new AxisAlignedBB(settlement.getCenter()).grow(AWDiplomacyStatics.siegePlayerRange);
		AreaBossBars.show(world, key, area, name, BossInfo.Color.RED, BossInfo.Overlay.NOTCHED_10, percent);
	}

	private static void end(World world, SettlementData data, Siege siege, Outcome outcome) {
		data.removeSiege(siege);
		Settlement settlement = data.get(siege.getSettlementId());
		if (settlement != null && settlement.getActiveSiegeId() == siege.getId()) {
			settlement.setActiveSiegeId(0);
			settlement.setLastSiegeEnd(world.getTotalWorldTime());
		}
		data.markDirty();
		AreaBossBars.hide(BAR_PREFIX + siege.getId());
		long now = world.getTotalWorldTime();
		for (NpcFaction npc : siege.getLoadedAttackers()) {
			npc.setAttackTarget(null);
			Settlements.disband(npc, now + 100 + world.rand.nextInt(300));
		}
		announce(world, "command.aw.siege.announce." + outcome.name().toLowerCase(java.util.Locale.ROOT), settlement, siege.getAttackerFaction());
		AncientWarfareNPC.LOG.info("Siege {} of city {} by {} ended: {}", siege.getId(), siege.getSettlementId(), siege.getAttackerFaction(), outcome);
	}

	private static void tryStartSiege(WorldServer world, SettlementData data, long now) {
		if (data.getSieges().size() >= AWDiplomacyStatics.maxActiveSieges) {
			return;
		}
		int dimension = world.provider.getDimension();
		List<Settlement> candidates = new ArrayList<>();
		for (Settlement settlement : data.getAll()) {
			if (settlement.getDimension() == dimension && settlement.isActive() && settlement.getType() == Settlement.Type.FACTION_CITY
					&& !isBesieged(settlement) && now - settlement.getLastSiegeEnd() >= (long) AWDiplomacyStatics.siegeCooldownDays * DAY_TICKS
					&& isPlayerNear(world, settlement.getCenter())) {
				candidates.add(settlement);
			}
		}
		Collections.shuffle(candidates, world.rand);
		for (Settlement settlement : candidates) {
			List<String> enemies = new ArrayList<>(FactionRelations.getDeclaredEnemies(world, settlement.getFaction()));
			enemies.removeIf(enemy -> canStart(world, settlement, enemy) != null);
			if (enemies.isEmpty() || world.rand.nextDouble() >= AWDiplomacyStatics.siegeChance) {
				continue;
			}
			start(world, data, settlement, enemies.get(world.rand.nextInt(enemies.size())));
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
		return count;
	}

	/**
	 * @return the position of the city leader if it is loaded, otherwise where it was last seen
	 */
	private static BlockPos getTarget(WorldServer world, Settlement settlement) {
		if (settlement.getLeaderId() != null) {
			Entity leader = world.getEntityFromUuid(settlement.getLeaderId());
			if (leader != null && leader.isEntityAlive()) {
				return leader.getPosition();
			}
		}
		return settlement.getLastLeaderPos();
	}

	private static void aim(NpcFaction npc, BlockPos target) {
		// the home is saved with the npc (Ancient Warfare sets one when saving an npc without home), keep it on the target
		// so that the move home AI pushes the attacker towards the city as well
		npc.setHomePosAndDistance(target, npc.getHomeRange());
		getOrAddAssaultAI(npc).setTarget(target);
	}

	private static NpcAIFactionAssault getOrAddAssaultAI(NpcFaction npc) {
		for (EntityAITasks.EntityAITaskEntry entry : npc.tasks.taskEntries) {
			if (entry.action instanceof NpcAIFactionAssault) {
				return (NpcAIFactionAssault) entry.action;
			}
		}
		NpcAIFactionAssault ai = new NpcAIFactionAssault(npc);
		npc.tasks.addTask(NpcAIFactionAssault.PRIORITY, ai);
		return ai;
	}

	private static boolean spawnWave(WorldServer world, Siege siege, Settlement settlement, BlockPos target) {
		BlockPos origin = findSpawnPos(world, settlement);
		if (origin == null) {
			return false; // no loaded place found, try again in a second
		}
		List<String> types = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		parseUnitWeights(types, weights);
		float health = 0;
		int spawned = 0;
		for (int i = 0; i < siege.getAttackersPerWave(); i++) {
			NpcFaction npc = createUnit(world, pickUnit(world.rand, types, weights), siege.getAttackerFaction());
			if (npc == null) {
				continue;
			}
			BlockPos pos = world.getTopSolidOrLiquidBlock(origin.add(world.rand.nextInt(SPAWN_SPREAD * 2 + 1) - SPAWN_SPREAD, 0,
					world.rand.nextInt(SPAWN_SPREAD * 2 + 1) - SPAWN_SPREAD));
			npc.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360F, 0F);
			npc.getEntityData().setInteger(Settlements.TAG_SIEGE, siege.getId());
			npc.setHealth(npc.getMaxHealth());
			if (world.spawnEntity(npc)) {
				siege.addAttacker(npc);
				aim(npc, target);
				health += npc.getMaxHealth();
				spawned++;
			}
		}
		siege.setWaveMaxHealth(health);
		siege.reconnectGraceUntil = world.getTotalWorldTime() + 40;
		if (spawned == 0) {
			AncientWarfareNPC.LOG.warn("Siege {}: no attacker could be spawned, check siege_unit_weights", siege.getId());
		}
		AncientWarfareNPC.LOG.info("Siege {}: wave {}/{} of {} attackers at {}", siege.getId(), siege.getWavesSpawned() + 1, siege.getTotalWaves(), spawned, origin);
		return true;
	}

	@Nullable
	private static NpcFaction createUnit(World world, String type, String faction) {
		Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(UNIT_PREFIX, type), world);
		if (!(entity instanceof NpcFaction)) {
			AncientWarfareNPC.LOG.warn("Siege unit type {} is not a faction npc, using {}", type, FALLBACK_UNIT);
			entity = EntityList.createEntityByIDFromName(new ResourceLocation(UNIT_PREFIX, FALLBACK_UNIT), world);
			if (!(entity instanceof NpcFaction)) {
				return null;
			}
		}
		NpcFaction npc = (NpcFaction) entity;
		npc.setFactionNameAndDefaults(faction);
		return npc;
	}

	private static void parseUnitWeights(List<String> types, List<Integer> weights) {
		for (String entry : AWDiplomacyStatics.siegeUnitWeights) {
			String[] parts = entry.split("=");
			int weight = 1;
			if (parts.length > 1) {
				try {
					weight = Integer.parseInt(parts[1].trim());
				}
				catch (NumberFormatException e) {
					AncientWarfareNPC.LOG.warn("Invalid weight in siege_unit_weights: {}", entry);
				}
			}
			if (!parts[0].trim().isEmpty() && weight > 0) {
				types.add(parts[0].trim());
				weights.add(weight);
			}
		}
		if (types.isEmpty()) {
			types.add(FALLBACK_UNIT);
			weights.add(1);
		}
	}

	private static String pickUnit(Random rand, List<String> types, List<Integer> weights) {
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
	 * @return a loaded surface position outside the city (siege_spawn_min/max_distance from the center), or closer if none is found
	 */
	@Nullable
	private static BlockPos findSpawnPos(World world, Settlement settlement) {
		BlockPos center = settlement.getCenter();
		double halfSize = Math.max(settlement.getMax().getX() - settlement.getMin().getX(), settlement.getMax().getZ() - settlement.getMin().getZ()) / 2D;
		int minDistance = AWDiplomacyStatics.siegeSpawnMinDistance;
		int maxDistance = Math.max(minDistance, AWDiplomacyStatics.siegeSpawnMaxDistance);
		for (double scale : new double[] {1D, 0.5D}) {
			for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
				double distance = Math.max(minDistance + world.rand.nextDouble() * (maxDistance - minDistance), halfSize + 8D);
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

	private static void announce(World world, String key, @Nullable Settlement settlement, String attackerFaction) {
		MinecraftServer server = world.getMinecraftServer();
		if (!AWDiplomacyStatics.announceSieges || server == null) {
			return;
		}
		ITextComponent city = settlement == null ? new TextComponentString("?") : Settlements.displayName(settlement);
		ITextComponent message = new TextComponentTranslation(key, city, FactionMembership.factionName(attackerFaction));
		message.getStyle().setColor(TextFormatting.RED);
		server.getPlayerList().sendMessage(message);
	}
}
