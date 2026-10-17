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
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.gamedata.SettlementData;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.Settlements;
import net.shadowmage.ancientwarfare.npc.war.Siege;
import net.shadowmage.ancientwarfare.npc.war.Sieges;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /awsettlement list | info [id] | setmanpower (id) (amount) | rename (id) (name), for operators
 */
public class CommandSettlement extends CommandBase {
	private static final String LIST = "list";
	private static final String INFO = "info";
	private static final String SET_MANPOWER = "setmanpower";
	private static final String RENAME = "rename";
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
		if (settlement.getGarrisonCap() > 0) {
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.garrison_cap", settlement.getGarrisonCap()));
		}
		if (settlement.getManpowerCap() > settlement.getMaxManpower()) {
			sender.sendMessage(new TextComponentTranslation("command.aw.settlement.info.manpower_cap", settlement.getManpowerCap()));
		}
		WorldServer world = server.getWorld(settlement.getDimension());
		int garrison = 0;
		for (NpcFaction npc : world.getEntitiesWithinAABB(NpcFaction.class, settlement.getAABB())) {
			Settlement npcSettlement = Settlements.getSettlementOf(npc);
			if (npcSettlement != null && npcSettlement.getId() == settlement.getId()) {
				garrison++;
			}
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

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, LIST, INFO, SET_MANPOWER, RENAME);
		}
		return Collections.emptyList();
	}
}
