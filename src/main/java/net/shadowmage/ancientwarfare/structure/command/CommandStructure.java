package net.shadowmage.ancientwarfare.structure.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.shadowmage.ancientwarfare.core.command.ISubCommand;
import net.shadowmage.ancientwarfare.core.command.RootCommand;
import net.shadowmage.ancientwarfare.core.command.SimpleSubCommand;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.structure.AncientWarfareStructure;
import net.shadowmage.ancientwarfare.structure.config.AWStructureStatics;
import net.shadowmage.ancientwarfare.structure.gamedata.StructureEntry;
import net.shadowmage.ancientwarfare.structure.gamedata.StructureMap;
import net.shadowmage.ancientwarfare.structure.item.ItemStructureScanner;
import net.shadowmage.ancientwarfare.structure.item.ItemStructureSettings;
import net.shadowmage.ancientwarfare.structure.network.PacketShowBoundingBoxes;
import net.shadowmage.ancientwarfare.structure.template.StructureTemplate;
import net.shadowmage.ancientwarfare.structure.template.StructureTemplateManager;
import net.shadowmage.ancientwarfare.structure.template.WorldGenStructureManager;
import net.shadowmage.ancientwarfare.structure.template.build.StructureBuilder;
import net.shadowmage.ancientwarfare.structure.template.load.TemplateLoader;
import net.shadowmage.ancientwarfare.structure.tile.ScannerTracker;
import net.shadowmage.ancientwarfare.structure.worldgen.TerritoryManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CommandStructure extends RootCommand {
	public CommandStructure() {
		registerSubCommand(new DeleteCommand());
		registerSubCommand(new BuildCommand());
		registerSubCommand(new PlaceCommand());
		registerSubCommand(new SaveCommand());
		registerSubCommand(new SimpleSubCommand("reload",
				(server, sender, args) -> {
					WorldGenStructureManager.INSTANCE.clearCachedTemplates();
					TerritoryManager.clearTerritoryCache();
					WorldGenStructureManager.INSTANCE.loadBiomeList(); //reset biome to template cache
					TemplateLoader.INSTANCE.reloadAll();
					sender.sendMessage(new TextComponentTranslation("command.aw.structure.reloaded"));
				}));
		registerSubCommand(new ReexportCommand());
		registerSubCommand(new SimpleSubCommand("scannerTp", (server, sender, args) -> {
			if (sender instanceof EntityPlayer) {
				Tuple<Integer, BlockPos> pos = ScannerTracker.getScannerPosByName(args[0]);
				ScannerTracker.teleportAboveScannerBlock((EntityPlayer) sender, pos);
			}


		}) {
			@Override
			public int getMaxArgs() {
				return 1;
			}

			@Override
			public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
				return CommandBase.getListOfStringsMatchingLastWord(args, ScannerTracker.getTrackedScannerNames().toArray(new String[0]));
			}

			@Override
			public String getUsage(ICommandSender sender) {
				return getName() + " <scannerTemplateName>";
			}
		});
		registerSubCommand(new SimpleSubCommand("name", (server, sender, args) -> {
			Optional<StructureEntry> structure = AWGameData.INSTANCE.getPerWorldData(sender.getEntityWorld(), StructureMap.class)
					.getStructureAt(sender.getEntityWorld(), sender.getPosition());

			sender.sendMessage(structure.map(structureEntry -> new TextComponentTranslation("command.aw.structure.name", structureEntry.getName()))
					.orElseGet(() -> new TextComponentTranslation("command.aw.structure.no_structure")));
		}));
		registerSubCommand(new SimpleSubCommand("showBoundingBoxes", (server, sender, args) -> {
			if (sender instanceof EntityPlayerMP) {
				EntityPlayerMP player = (EntityPlayerMP) sender;
				NetworkHandler.sendToPlayer(player, new PacketShowBoundingBoxes(true));
			}
		}));
		registerSubCommand(new SimpleSubCommand("hideBoundingBoxes", (server, sender, args) -> {
			if (sender instanceof EntityPlayerMP) {
				EntityPlayerMP player = (EntityPlayerMP) sender;
				NetworkHandler.sendToPlayer(player, new PacketShowBoundingBoxes(false));
			}
		}));
		registerSubCommand(new StatsCommand());
	}

	@Override
	public String getName() {
		return "awstructure";
	}

	@Override
	public String getUsage(ICommandSender var1) {
		return "command.aw.structure.usage";
	}

	private static class SaveCommand implements ISubCommand {
		@Override
		public String getName() {
			return "save";
		}

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] subArgs) {
			if (sender instanceof EntityLivingBase) {
				ItemStack stack = ((EntityLivingBase) sender).getHeldItemMainhand();
				if (!stack.isEmpty()) {
					ItemStructureSettings settings = ItemStructureSettings.getSettingsFor(stack);
					if (settings.hasPos1() && settings.hasPos2() && settings.hasBuildKey() && (settings.hasName() || subArgs.length > 0)) {
						scanStructure(sender, subArgs, stack, settings);
					} else {
						sender.sendMessage(new TextComponentTranslation("command.aw.structure.incomplete_data"));
					}
				}
			}
		}

		private void scanStructure(ICommandSender sender, String[] subArgs, ItemStack stack, ItemStructureSettings settings) {
			String name = settings.hasName() ? settings.name() : subArgs[0];
			ItemStructureScanner.setStructureName(stack, name);
			if (ItemStructureScanner.scanStructure(sender.getEntityWorld(), stack)) {
				sender.sendMessage(new TextComponentTranslation("command.aw.structure.exported", subArgs[0]));
			}
		}

		@Override
		public int getMaxArgs() {
			return 1;
		}

		@Override
		public String getUsage(ICommandSender sender) {
			return getName() + " [templateName]";
		}
	}

	private static class BuildCommand implements ISubCommand {
		@Override
		public String getName() {
			return "build";
		}

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] subArgs) throws CommandException {
			if (subArgs.length < 4) {
				throw new WrongUsageException(getUsage(sender));
			}

			int x = CommandBase.parseInt(subArgs[1]);
			int y = CommandBase.parseInt(subArgs[2]);
			int z = CommandBase.parseInt(subArgs[3]);
			EnumFacing face = EnumFacing.SOUTH;
			if (subArgs.length > 4) {
				EnumFacing faceArg = EnumFacing.byName(subArgs[4]);
				if (faceArg != null) {
					face = faceArg;
				}
			}
			// deprecated: /awstructure place fits the structure to the ground next to the player and records it
			sender.sendMessage(new TextComponentTranslation("command.aw.structure.build.deprecated"));
			Optional<StructureTemplate> template = StructureTemplateManager.getTemplate(subArgs[0]);
			if (template.isPresent()) {
				StructureBuilder builder = new StructureBuilder(sender.getEntityWorld(), template.get(), face, new BlockPos(x, y, z));
				builder.instantConstruction();
				sender.sendMessage(new TextComponentTranslation("command.aw.structure.built", subArgs[0], x, y, z));
			} else {
				sender.sendMessage(new TextComponentTranslation("command.aw.structure.not_found", subArgs[0]));
			}
		}

		@Override
		public int getMaxArgs() {
			return 5;
		}

		@Override
		public String getUsage(ICommandSender sender) {
			return getName() + " <templateName> <x> <y> <z> [north:east:south:west] (deprecated: use place)";
		}

		@Override
		public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
			return args.length > 4 ? CommandBase.getListOfStringsMatchingLastWord(args, "north", "east", "south", "west") : Collections.emptyList();
		}
	}

	/**
	 * Places a structure of the template database next to the player, fitted to the ground as at world generation (the template's own
	 * levelling, clearing and placement checks), whatever the biome; it is recorded like a generated structure, so it becomes a city when it
	 * has a leader or enough fighters. Tries a few spots in front of the player and around before giving up.
	 */
	private static class PlaceCommand implements ISubCommand {
		private static final int[][] SPOTS = {{0, 0}, {-1, 0}, {1, 0}, {0, 1}, {-1, 1}, {1, 1}, {0, 2}, {-2, 1}, {2, 1}};

		@Override
		public String getName() {
			return "place";
		}

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] subArgs) throws CommandException {
			if (subArgs.length < 1) {
				throw new WrongUsageException(getUsage(sender));
			}
			if (!(sender instanceof net.minecraft.entity.Entity)) {
				throw new CommandException("command.aw.structure.place.player_only");
			}
			net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) sender;
			net.minecraft.world.World world = entity.world;
			Optional<StructureTemplate> found = StructureTemplateManager.getTemplate(subArgs[0]);
			if (!found.isPresent()) {
				sender.sendMessage(new TextComponentTranslation("command.aw.structure.not_found", subArgs[0]));
				return;
			}
			StructureTemplate template = found.get();
			EnumFacing look = entity.getHorizontalFacing();
			EnumFacing face = look;
			if (subArgs.length > 1 && EnumFacing.byName(subArgs[1]) != null && EnumFacing.byName(subArgs[1]).getAxis().isHorizontal()) {
				face = EnumFacing.byName(subArgs[1]);
			}
			int size = Math.max(template.getSize().getX(), template.getSize().getZ());
			int step = size + 4;
			EnumFacing side = look.rotateY();
			StructureMap map = AWGameData.INSTANCE.getPerWorldData(world, StructureMap.class);
			net.shadowmage.ancientwarfare.structure.gamedata.TownMap towns = AWGameData.INSTANCE.getPerWorldData(world,
					net.shadowmage.ancientwarfare.structure.gamedata.TownMap.class);
			for (int[] spot : SPOTS) {
				int distance = size / 2 + 4 + spot[1] * step;
				int x = entity.getPosition().getX() + look.getFrontOffsetX() * distance + side.getFrontOffsetX() * spot[0] * step;
				int z = entity.getPosition().getZ() + look.getFrontOffsetZ() * distance + side.getFrontOffsetZ() * spot[0] * step;
				int y = net.shadowmage.ancientwarfare.structure.worldgen.WorldStructureGenerator.getTargetY(world, x, z, false) + 1;
				if (y <= 0) {
					continue;
				}
				BlockPos pos = new BlockPos(x, y, z);
				net.shadowmage.ancientwarfare.structure.template.build.StructureBB bb = new net.shadowmage.ancientwarfare.structure.template.build.StructureBB(pos, face,
						template.getSize(), template.getOffset());
				int adjustedY = template.getValidationSettings().getAdjustedSpawnY(world, x, y, z, face, template, bb);
				bb.min = bb.min.up(adjustedY - y);
				bb.max = bb.max.up(adjustedY - y);
				pos = new BlockPos(x, adjustedY, z);
				if (overlapsStructure(world, map, bb) || towns.intersectsWithTown(bb)
						|| !template.getValidationSettings().validatePlacement(world, x, adjustedY, z, face, template, bb)) {
					continue;
				}
				map.setGeneratedAt(world, x, z, new StructureEntry(x, adjustedY, z, face, template), false);
				net.shadowmage.ancientwarfare.structure.worldgen.WorldGenTickHandler.INSTANCE.addStructureForGeneration(
						new net.shadowmage.ancientwarfare.structure.template.build.StructureBuilderWorldGen(world, template, face, pos));
				AncientWarfareStructure.LOG.info("Placed structure {} at {} by command of {}", template.name, pos, sender.getName());
				sender.sendMessage(new TextComponentTranslation("command.aw.structure.built", template.name, x, adjustedY, z));
				return;
			}
			sender.sendMessage(new TextComponentTranslation("command.aw.structure.place.no_spot", template.name));
		}

		private static boolean overlapsStructure(net.minecraft.world.World world, StructureMap map,
				net.shadowmage.ancientwarfare.structure.template.build.StructureBB bb) {
			int size = Math.max(bb.getXSize(), bb.getZSize()) / 16 + 3;
			for (StructureEntry entry : map.getEntriesNear(world, bb.getCenterX(), bb.getCenterZ(), size, true, new java.util.ArrayList<>())) {
				if (bb.intersects(entry.getBB())) {
					return true;
				}
			}
			return false;
		}

		@Override
		public int getMaxArgs() {
			return 2;
		}

		@Override
		public String getUsage(ICommandSender sender) {
			return getName() + " <templateName> [north:east:south:west]";
		}

		@Override
		public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
			if (args.length == 1) {
				return CommandBase.getListOfStringsMatchingLastWord(args, StructureTemplateManager.getTemplates());
			}
			return args.length == 2 ? CommandBase.getListOfStringsMatchingLastWord(args, "north", "east", "south", "west") : Collections.emptyList();
		}
	}

	private static class DeleteCommand implements ISubCommand {
		@Override
		public String getName() {
			return "delete";
		}

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] subArgs) throws CommandException {
			if (subArgs.length < 1) {
				throw new WrongUsageException(getUsage(sender));
			}
			String name = subArgs[0];
			boolean flag = StructureTemplateManager.removeTemplate(name);
			if (flag)//check if var2.len>=3, pull string of end...if string==true, try delete template file for name
			{
				TextComponentTranslation txt = new TextComponentTranslation("command.aw.structure.template_removed", name);
				sender.sendMessage(txt);
				if (subArgs.length > 1) {
					boolean shouldDelete = subArgs[1].equalsIgnoreCase("true");
					if (shouldDelete) {
						if (deleteTemplateFile(name)) {
							txt = new TextComponentTranslation("command.aw.structure.file_deleted", name);
						} else {
							txt = new TextComponentTranslation("command.aw.structure.file_not_found", name);
						}
						sender.sendMessage(txt);
					}
				}
			} else//send template not found message
			{
				sender.sendMessage(new TextComponentTranslation("command.aw.structure.not_found", name));
			}
		}

		private boolean deleteTemplateFile(String name) {
			String path = TemplateLoader.INCLUDE_DIRECTORY + name + "." + AWStructureStatics.templateExtension;
			File file = new File(path);
			if (file.exists()) {
				try {
					Files.delete(file.toPath());
				}
				catch (IOException e) {
					AncientWarfareStructure.LOG.error("Unable to delete file ", e);
				}
				return true;
			}
			return false;
		}

		@Override
		public int getMaxArgs() {
			return 2;
		}

		@Override
		public String getUsage(ICommandSender sender) {
			return getName() + " <templateName> [true to remove the aws file]";
		}
	}

	private static class ReexportCommand implements ISubCommand {
		@Override
		public String getName() {
			return "scannersReexport";
		}

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
			if (sender instanceof EntityPlayer) {
				boolean reloadMainSettings = false;
				if (args.length == 1) {
					reloadMainSettings = Boolean.parseBoolean(args[0]);
				}
				ScannerTracker.reexportAll((EntityPlayer) sender, reloadMainSettings);
			}
		}

		@Override
		public int getMaxArgs() {
			return 1;
		}

		@Override
		public String getUsage(ICommandSender sender) {
			return getName() + " [true to reload main settings]";
		}
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}
}
