package net.shadowmage.ancientwarfare.npc.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.faction.Diplomacy;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelation;
import net.shadowmage.ancientwarfare.npc.faction.FactionRelations;
import net.shadowmage.ancientwarfare.npc.faction.KingdomDiplomacy;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;
import net.shadowmage.ancientwarfare.npc.gamedata.DiplomacyData;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionMembershipData;
import net.shadowmage.ancientwarfare.npc.gamedata.KingdomData;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.Settlements;
import net.shadowmage.ancientwarfare.npc.war.Siege;
import net.shadowmage.ancientwarfare.npc.war.Sieges;
import net.shadowmage.ancientwarfare.npc.war.WarLevies;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * /awsettlement list | info [id] | faction [faction] | setmanpower (id) (amount) | rename (id) (name), for operators
 */
public class CommandSettlement extends CommandBase {
	private static final String LIST = "list";
	private static final String INFO = "info";
	private static final String SET_MANPOWER = "setmanpower";
	private static final String RENAME = "rename";
	private static final String FACTION = "faction";
	private static final String RECOUNT = "recount";
	private static final int MAX_NAME_LENGTH = 32;

	@Override
	public String getName() {
		return "awsettlement";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "command.aw.settlement.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		World world = sender.getEntityWorld();
		SettlementData data = Settlements.getData(world);
		switch (args[0].toLowerCase(Locale.ROOT)) {
			case LIST:
				list(sender, data);
				break;
			case INFO:
				Settlement settlement = args.length > 1 ? get(data, args[1]) : data.getActiveAt(world.provider.getDimension(), sender.getPosition(), Settlement.Type.FACTION_CITY);
				if (settlement == null) {
					throw new CommandException("command.aw.settlement.none_here");
				}
				info(server, sender, settlement);
				break;
			case RECOUNT:
				Settlement recounted = args.length > 1 ? get(data, args[1]) : data.getActiveAt(world.provider.getDimension(), sender.getPosition(), Settlement.Type.FACTION_CITY);
				if (recounted == null) {
					throw new CommandException("command.aw.settlement.none_here");
				}
				int before = recounted.getMaxManpower();
				Settlements.recountGarrison(server.getWorld(recounted.getDimension()), data, recounted);
				sender.sendMessage(new TextComponentTranslation("command.aw.settlement.recounted", Settlements.displayName(recounted), before, recounted.getMaxManpower()));
				break;
			case FACTION:
				if (args.length > 1) {
					factionInfo(server, sender, world, args[1]);
				} else {
					factionList(sender, world);
				}
				break;
			case SET_MANPOWER:
				if (args.length < 3) {
					throw new WrongUsageException(getUsage(sender));
				}
				Settlement target = get(data, args[1]);
				int manpower = parseInt(args[2], 0);
				Settlements.setManpower(world, target, manpower);
				sender.sendMessage(new TextComponentTranslation("command.aw.settlement.manpower_set", Settlements.displayName(target), manpower));
				break;
			case RENAME:
				if (args.length < 3) {
					throw new WrongUsageException(getUsage(sender));
				}
				Settlement renamed = get(data, args[1]);
				String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
				if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
					throw new CommandException("command.aw.settlement.rename.invalid", MAX_NAME_LENGTH);
				}
				for (Settlement other : data.getAll()) {
					if (other != renamed && other.getName().equalsIgnoreCase(name)) {
						throw new CommandException("command.aw.settlement.rename.taken", name);
					}
				}
				renamed.setName(name);
				data.markDirty();
				sender.sendMessage(new TextComponentTranslation("command.aw.settlement.renamed", renamed.getId(), Settlements.displayName(renamed)));
				break;
			default:
				throw new WrongUsageException(getUsage(sender));
		}
	}

	private Settlement get(SettlementData data, String id) throws CommandException {
		Settlement settlement = data.get(parseInt(id));
		if (settlement == null) {
			throw new CommandException("command.aw.settlement.not_found", id);
		}
		return settlement;
	}

	private void list(ICommandSender sender, SettlementData data) {
		if (data.getAll().isEmpty()) {
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.list.empty"));
			return;
		}
		for (Settlement settlement : data.getAll()) {
			BlockPos center = settlement.getCenter();
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.list.entry", settlement.getId(), Settlements.displayName(settlement),
					settlement.getStatus().getName(),
					settlement.getManpower(), settlement.getMaxManpower(), center.getX(), center.getY(), center.getZ(), settlement.getDimension()));
		}
	}

	private void info(MinecraftServer server, ICommandSender sender, Settlement settlement) {
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.header", settlement.getId(), Settlements.displayName(settlement),
				settlement.getType().getName(), settlement.getStatus().getName()));
		if (!settlement.getStructureName().isEmpty()) {
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.structure", settlement.getStructureName()));
		}
		Siege siege = Sieges.getSiegeOf(server.getWorld(settlement.getDimension()), settlement);
		if (siege != null) {
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.siege", siege.getId(),
					FactionMembership.factionName(siege.getAttackerFaction()), siege.getWavesSpawned(), siege.getTotalWaves()));
		}
		BlockPos min = settlement.getMin();
		BlockPos max = settlement.getMax();
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.bounds", min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.manpower", settlement.getManpower(), settlement.getMaxManpower(),
				settlement.getPendingRespawns().size()));
		// what the city gains each Minecraft day (nothing while it is attacked) and its taxes not yet collected
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.growth", Settlements.getDailyManpowerRegen(settlement),
				Settlements.getDailyTax(settlement), settlement.getTreasury(), Settlements.getDailyTax(settlement) * net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics.cityTaxMaxDays,
				net.shadowmage.ancientwarfare.npc.war.Coins.getName()));
		WorldServer world = server.getWorld(settlement.getDimension());
		int garrison = 0;
		for (NpcFaction npc : world.getEntitiesWithinAABB(NpcFaction.class, settlement.getAABB())) {
			Settlement npcSettlement = Settlements.getSettlementOf(npc);
			if (npcSettlement != null && npcSettlement.getId() == settlement.getId()) {
				garrison++;
			}
		}
		garrison += Settlements.countOwnerSoldiers(world, settlement);
		// only when reinforcements are actually coming
		if (settlement.getGarrisonCap() > 0 && garrison + settlement.getPendingRespawns().size() < settlement.getGarrisonCap()) {
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.garrison_cap", settlement.getGarrisonCap()));
		}
		if (settlement.getManpowerCap() > settlement.getMaxManpower()) {
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.manpower_cap", settlement.getManpowerCap()));
		}
		Entity leader = settlement.getLeaderId() == null ? null : world.getEntityFromUuid(settlement.getLeaderId());
		TextComponentTranslation leaderState;
		if (!settlement.isActive()) {
			leaderState = new TextComponentTranslation("command.aw.settlement.leader.dead");
		} else if (leader != null && leader.isEntityAlive()) {
			leaderState = new TextComponentTranslation("command.aw.settlement.leader.alive", (int) Math.floor(leader.posX), (int) Math.floor(leader.posY),
					(int) Math.floor(leader.posZ));
		} else {
			leaderState = new TextComponentTranslation("command.aw.settlement.leader.not_loaded");
		}
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.garrison", garrison, leaderState));
	}

	private static String yesNo(boolean value) {
		return value ? "yes" : "no";
	}

	private static String daysSince(World world, long mark) {
		long since = Sieges.sinceRest(world, mark);
		return since == Long.MAX_VALUE ? "-" : String.valueOf(since / 24000L);
	}

	private static Set<String> factionsWithCities(SettlementData data, int dimension) {
		Set<String> factions = new TreeSet<>();
		for (Settlement city : data.getAll()) {
			if (city.isActive() && city.getType() == Settlement.Type.FACTION_CITY && city.getDimension() == dimension) {
				factions.add(city.getFaction());
			}
		}
		return factions;
	}

	private static int countSieges(SettlementData data, String faction) {
		int count = 0;
		for (Siege siege : data.getSieges()) {
			Settlement city = data.get(siege.getSettlementId());
			if (faction.equals(siege.getAttackerFaction()) || city != null && faction.equals(city.getFaction())) {
				count++;
			}
		}
		return count;
	}

	/**
	 * /awsettlement faction: one line for each faction with cities in this dimension.
	 */
	private void factionList(ICommandSender sender, World world) {
		SettlementData data = Settlements.getData(world);
		int dimension = world.provider.getDimension();
		Set<String> factions = factionsWithCities(data, dimension);
		if (factions.isEmpty()) {
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.none"));
			return;
		}
		for (String faction : factions) {
			int cities = 0;
			for (Settlement city : data.getAll()) {
				if (city.isActive() && city.getType() == Settlement.Type.FACTION_CITY && city.getDimension() == dimension && faction.equals(city.getFaction())) {
					cities++;
				}
			}
			int total = WarLevies.getTotal(world, faction, dimension);
			int max = WarLevies.getMax(world, faction, dimension);
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.row", FactionMembership.factionName(faction), faction, cities, total, max,
					max == 0 ? 0 : total * 100 / max, WarLevies.getAvailable(world, faction, dimension), yesNo(WarLevies.canLaySiege(world, faction, dimension)),
					FactionRelations.getDeclaredEnemies(world, faction).size(), countSieges(data, faction)));
		}
	}

	/**
	 * /awsettlement faction (faction): every indicator of the faction: kingdom, manpower and levies, cities, relations, sieges, members.
	 */
	private void factionInfo(MinecraftServer server, ICommandSender sender, World world, String faction) throws CommandException {
		if (!FactionRegistry.getFactionNames().contains(faction)) {
			throw new CommandException("command.aw.settlement.faction.unknown", faction);
		}
		SettlementData data = Settlements.getData(world);
		int dimension = world.provider.getDimension();
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.header", FactionMembership.factionName(faction), faction));
		if (Kingdoms.isKingdom(faction)) {
			KingdomData.Kingdom kingdom = Kingdoms.get(world, faction);
			if (kingdom != null) {
				sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.kingdom", kingdom.ownerName, FactionMembership.factionName(kingdom.culture)));
			}
		}
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.manpower", WarLevies.getTotal(world, faction, dimension),
				WarLevies.getMax(world, faction, dimension), WarLevies.getFloor(world, faction, dimension), WarLevies.getAvailable(world, faction, dimension),
				yesNo(WarLevies.canLaySiege(world, faction, dimension))));
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.cities"));
		for (Settlement city : data.getAll()) {
			if (city.getType() != Settlement.Type.FACTION_CITY || city.getDimension() != dimension || !faction.equals(city.getFaction())) {
				continue;
			}
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.city", city.getId(), Settlements.displayName(city), city.getStatus().getName(),
					city.getManpower(), city.getMaxManpower(), city.getGarrisonCap(), city.getTreasury(), daysSince(world, city.getLastSiegeEnd()),
					daysSince(world, city.getLastFullSiegeEnd()), Sieges.isBesieged(city) ? new TextComponentTranslation("command.aw.settlement.faction.city.besieged") : ""));
		}
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.relations"));
		long day = Diplomacy.getDay(world);
		for (String other : factionsWithCities(data, dimension)) {
			if (other.equals(faction)) {
				continue;
			}
			FactionRelation relation = KingdomDiplomacy.getRelation(world, faction, other);
			DiplomacyData.Entry entry = Diplomacy.getData(world).get(faction, other);
			StringBuilder details = new StringBuilder();
			if (relation == FactionRelation.WAR) {
				details.append(", ").append(Math.max(0L, day - entry.getSince())).append(" days, exhaustion ").append(entry.getExhaustion(faction, other))
						.append(" (theirs ").append(entry.getExhaustion(other, faction)).append(")");
			}
			long truce = KingdomDiplomacy.getTruceDaysLeft(world, faction, other);
			if (truce > 0) {
				details.append(", truce for ").append(truce).append(" days");
			}
			if (Diplomacy.isEternalEnemy(other) || Diplomacy.isEternalEnemy(faction)) {
				details.append(", eternal enemies");
			}
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.relation", FactionMembership.factionName(other),
					relation.name().toLowerCase(Locale.ROOT), details.toString()));
		}
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.sieges", countSieges(data, faction)));
		for (Siege siege : data.getSieges()) {
			Settlement city = data.get(siege.getSettlementId());
			if (city != null && (faction.equals(siege.getAttackerFaction()) || faction.equals(city.getFaction()))) {
				sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.siege", siege.getId(), siege.getKind().getName(), Settlements.displayName(city),
						FactionMembership.factionName(siege.getAttackerFaction()), siege.getWavesSpawned(), siege.getTotalWaves(), siege.getPaid()));
			}
		}
		StringBuilder members = new StringBuilder();
		for (FactionMembershipData.Member member : AWGameData.INSTANCE.getData(world, FactionMembershipData.class).getMembers()) {
			if (faction.equals(member.faction)) {
				members.append(members.length() > 0 ? ", " : "").append(member.playerName).append(" (").append(member.getStrikes()).append(" strikes)");
			}
		}
		sender.sendMessage(new TextComponentTranslation("command.aw.settlement.faction.members", members.length() == 0 ? "-" : members.toString()));
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, LIST, INFO, FACTION, SET_MANPOWER, RENAME, RECOUNT);
		}
		if (args.length == 2 && FACTION.equals(args[0])) {
			return CommandBase.getListOfStringsMatchingLastWord(args, FactionRegistry.getFactionNames());
		}
		return Collections.emptyList();
	}
}
