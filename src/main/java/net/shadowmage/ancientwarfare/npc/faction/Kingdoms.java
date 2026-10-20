package net.shadowmage.ancientwarfare.npc.faction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.war.Settlement;
import net.shadowmage.ancientwarfare.npc.war.Settlements;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionMembershipData;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionRelationData;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.gamedata.KingdomData;
import net.shadowmage.ancientwarfare.npc.network.PacketKingdoms;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player kingdoms are factions registered during the game. Their id ("kingdom_N") is short enough for the npc spawn packet (20
 * characters); players see their display name, and their npcs use the skins, names, stats and dialogues of the kingdom's culture
 * (an Ancient Warfare faction).
 * The registered kingdoms are kept in a static map read on both sides: the server fills it when the overworld loads, a remote client
 * from {@link PacketKingdoms}. In single player both sides share it.
 */
public class Kingdoms {
	public static final String ID_PREFIX = "kingdom_";

	private static final Map<String, Info> REGISTERED = new HashMap<>();

	private Kingdoms() {}

	private static class Info {
		private final String name;
		private final String culture;

		private Info(String name, String culture) {
			this.name = name;
			this.culture = culture;
		}
	}

	public static boolean isKingdom(@Nullable String faction) {
		return faction != null && REGISTERED.containsKey(faction);
	}

	/**
	 * The Ancient Warfare faction whose look and defaults a faction uses: the culture of a kingdom, the faction itself otherwise.
	 */
	public static String cultureOf(String faction) {
		Info info = REGISTERED.get(faction);
		return info == null ? faction : info.culture;
	}

	/**
	 * The name players see for a kingdom, null for other factions (they use their translation key).
	 */
	@Nullable
	public static String displayName(@Nullable String faction) {
		Info info = faction == null ? null : REGISTERED.get(faction);
		return info == null ? null : info.name;
	}

	/**
	 * @return the title of the ruler of a city: "Governor" in a player kingdom, "Leader of the ..." otherwise
	 */
	public static ITextComponent rulerTitle(String faction) {
		return isKingdom(faction) ? new TextComponentTranslation("gui.aw.kingdom.governor_title")
				: new TextComponentTranslation("gui.ancientwarfarenpc.leader_title", FactionMembership.factionName(faction));
	}

	public static void onWorldLoad(World world) {
		clearRegistered();
		KingdomData data = getData(world);
		for (KingdomData.Kingdom kingdom : data.getAll()) {
			register(kingdom.id, kingdom.name, kingdom.culture, kingdom.color);
		}
		if (!data.getAll().isEmpty()) {
			AncientWarfareNPC.LOG.info("Registered {} player kingdom(s)", data.getAll().size());
		}
	}

	public static KingdomData.Kingdom create(World world, EntityPlayer owner, String culture) {
		int color = FactionRegistry.getFaction(culture).getColor();
		KingdomData.Kingdom kingdom = getData(world).create(owner.getUniqueID(), owner.getName(), owner.getName() + " Kingdom", culture, color);
		kingdom.graceUntil = Diplomacy.getDay(world) + AWDiplomacyStatics.kingdomGraceDays;
		register(kingdom.id, kingdom.name, kingdom.culture, kingdom.color);
		sync();
		AncientWarfareNPC.LOG.info("Founded kingdom {} ({}, culture {}) for {}", kingdom.id, kingdom.name, culture, owner.getName());
		return kingdom;
	}

	/**
	 * A player placed a town hall: if it is their first and it is not inside a city, it founds their kingdom (the owner of a kingdom founds
	 * another city of it instead, see foundNewCity). A member of a faction leaves it
	 * first (like renouncing), then the kingdom is created with its starting relations, its city and governor, and the player becomes its
	 * member.
	 */
	public static void onTownHallPlaced(World world, EntityPlayer player, BlockPos pos) {
		if (world.isRemote || !(world instanceof WorldServer) || !(player instanceof EntityPlayerMP) || !AWDiplomacyStatics.kingdomsEnabled
				|| !AWDiplomacyStatics.settlementsEnabled) {
			return;
		}
		MinecraftServer server = world.getMinecraftServer();
		if (server == null) {
			return;
		}
		KingdomData.Kingdom owned = getOwnedBy(world, player.getUniqueID());
		if (owned != null) {
			foundNewCity(server, (WorldServer) world, (EntityPlayerMP) player, owned, pos);
			return;
		}
		if (!Settlements.canFoundKingdomCity(world, pos)) {
			player.sendMessage(colored(new TextComponentTranslation("command.aw.kingdom.too_close", AWDiplomacyStatics.kingdomRadius), TextFormatting.YELLOW));
			return;
		}
		if (FactionMembership.getFaction(world, player.getUniqueID()) != null) {
			FactionMembership.leave(server, player.getUniqueID(), player.getName(), FactionMembership.RENOUNCE, "command.aw.membership.announce.leave");
		}
		String culture = isValidCulture(AWDiplomacyStatics.kingdomDefaultCulture) ? AWDiplomacyStatics.kingdomDefaultCulture : "empire";
		KingdomData.Kingdom kingdom = create(world, player, culture);
		setStartingRelations(server, kingdom.id, culture);
		Settlement city = Settlements.foundKingdomCity((WorldServer) world, kingdom.id, player, pos, true);
		if (city != null) {
			kingdom.cityId = city.getId();
			getData(world).markDirty();
		}
		FactionMembership.join(server, (EntityPlayerMP) player, kingdom.id, false);
		FactionMembership.sendDiplomacyToAll(server);
		ITextComponent announcement = colored(new TextComponentTranslation("command.aw.kingdom.announce.founded", player.getName(), kingdom.name,
				city == null ? "?" : city.getName()), TextFormatting.GOLD);
		server.getPlayerList().sendMessage(announcement);
		player.sendMessage(colored(new TextComponentTranslation("command.aw.kingdom.founded_help"), TextFormatting.GRAY));
	}

	/**
	 * A town hall placed by the owner of a kingdom far enough (kingdom_city_min_distance) from every city of the kingdom founds another city of
	 * it, with its governor: no new kingdom. Like a conquered city it pays tribute to the capital and its taxes are collected at its governor
	 * or at the capital. Closer, or overlapping another city, the town hall is only an Ancient Warfare town hall.
	 */
	private static void foundNewCity(MinecraftServer server, WorldServer world, EntityPlayerMP player, KingdomData.Kingdom kingdom, BlockPos pos) {
		int minDistance = AWDiplomacyStatics.kingdomCityMinDistance;
		for (Settlement city : getCities(world, kingdom)) {
			if (city.getDimension() != world.provider.getDimension()) {
				continue;
			}
			BlockPos center = city.getCenter();
			int distance = (int) Math.sqrt(Math.pow(center.getX() - pos.getX(), 2) + Math.pow(center.getZ() - pos.getZ(), 2));
			if (distance < minDistance) {
				player.sendMessage(colored(new TextComponentTranslation("command.aw.kingdom.city_too_close", minDistance, Settlements.displayName(city), distance),
						TextFormatting.YELLOW));
				return;
			}
		}
		if (!Settlements.canFoundKingdomCity(world, pos)) {
			player.sendMessage(colored(new TextComponentTranslation("command.aw.kingdom.too_close", AWDiplomacyStatics.kingdomRadius), TextFormatting.YELLOW));
			return;
		}
		Settlement city = Settlements.foundKingdomCity(world, kingdom.id, player, pos, false);
		if (city == null) {
			return;
		}
		server.getPlayerList().sendMessage(colored(new TextComponentTranslation("command.aw.kingdom.announce.new_city", player.getName(), city.getName(),
				kingdom.name), TextFormatting.GOLD));
	}

	/**
	 * Peace with every faction and a truce of kingdom_grace_days, war with the eternal enemies hostile to the culture.
	 */
	private static void setStartingRelations(MinecraftServer server, String kingdom, String culture) {
		World world = server.getEntityWorld();
		long truceUntil = Diplomacy.getDay(world) + AWDiplomacyStatics.kingdomGraceDays;
		for (String other : new ArrayList<>(FactionRegistry.getFactionNames())) {
			if (other.equals(kingdom)) {
				continue;
			}
			boolean eternalEnemy = Diplomacy.isEternalEnemy(other)
					&& (FactionRegistry.getFaction(other).isHostileTowards(culture) || FactionRegistry.getFaction(culture).isHostileTowards(other));
			FactionRelations.setRelationQuietly(server, kingdom, other, eternalEnemy ? FactionRelation.WAR : FactionRelation.PEACE);
			if (!eternalEnemy) {
				Diplomacy.getData(world).get(kingdom, other).setTruceUntil(truceUntil);
			}
		}
		Diplomacy.getData(world).markDirty();
	}

	/**
	 * @return true while the kingdom is in its starting grace period: its capital cannot be attacked (see Sieges.canStart) and it can break its
	 * truces to declare war, which ends the protection
	 */
	public static boolean isInGrace(World world, String kingdomId) {
		KingdomData.Kingdom kingdom = isKingdom(kingdomId) ? get(world, kingdomId) : null;
		return kingdom != null && !kingdom.destroyed && Diplomacy.getDay(world) < kingdom.graceUntil;
	}

	/**
	 * The kingdom gives up its protection (it declared war during its grace period).
	 */
	public static void endGrace(World world, String kingdomId) {
		KingdomData.Kingdom kingdom = isKingdom(kingdomId) ? get(world, kingdomId) : null;
		if (kingdom != null && kingdom.graceUntil > 0) {
			kingdom.graceUntil = 0;
			getData(world).markDirty();
		}
	}

	/**
	 * @return true if the city is the capital of a kingdom in its grace period
	 */
	public static boolean isProtected(World world, Settlement city) {
		return city.getOwnerId() != null && isInGrace(world, city.getFaction());
	}

	/**
	 * @return true if the player owns the kingdom (a kingdom faction not destroyed)
	 */
	public static boolean isOwner(World world, UUID playerId, @Nullable String faction) {
		KingdomData.Kingdom kingdom = faction == null || !isKingdom(faction) ? null : get(world, faction);
		return kingdom != null && !kingdom.destroyed && playerId.equals(kingdom.ownerId);
	}

	/**
	 * @return the cultures a kingdom can choose: kingdom_cultures, or every faction that is not an eternal enemy when the list is empty
	 */
	public static List<String> getCultures() {
		List<String> ret = new ArrayList<>();
		for (String culture : AWDiplomacyStatics.kingdomCultures) {
			if (isValidCulture(culture.trim())) {
				ret.add(culture.trim());
			}
		}
		if (ret.isEmpty()) {
			for (String faction : new TreeSet<>(FactionRegistry.getFactionNames())) {
				if (isValidCulture(faction) && !Diplomacy.isEternalEnemy(faction)) {
					ret.add(faction);
				}
			}
		}
		return ret;
	}

	/**
	 * The kingdom has lost its city (conquered, fallen, disbanded): it is marked destroyed (its faction stays registered, so that its npcs
	 * can still be loaded), its relations are removed and its owner and members leave it.
	 */
	public static void destroy(MinecraftServer server, String id) {
		World world = server.getEntityWorld();
		KingdomData.Kingdom kingdom = get(world, id);
		if (kingdom == null || kingdom.destroyed) {
			return;
		}
		kingdom.destroyed = true;
		getData(world).markDirty();
		FactionRelationData relations = AWGameData.INSTANCE.getData(world, FactionRelationData.class);
		for (String other : new ArrayList<>(FactionRegistry.getFactionNames())) {
			if (!other.equals(id) && relations.get(id, other) != null) {
				relations.reset(id, other);
			}
		}
		for (FactionMembershipData.Member member : new ArrayList<>(AWGameData.INSTANCE.getData(world, FactionMembershipData.class).getMembers())) {
			if (member.faction.equals(id)) {
				FactionMembership.leave(server, member.playerId, member.playerName, 0, null);
			}
		}
		FactionMembership.sendDiplomacyToAll(server);
		server.getPlayerList().sendMessage(colored(new TextComponentTranslation("command.aw.kingdom.announce.destroyed", kingdom.name), TextFormatting.GOLD));
		AncientWarfareNPC.LOG.info("Kingdom {} ({}) of {} is destroyed", id, kingdom.name, kingdom.ownerName);
	}

	/**
	 * The owner gives up the kingdom: its city is abandoned (the governor and garrison leave) and the kingdom is destroyed.
	 */
	public static void disband(MinecraftServer server, KingdomData.Kingdom kingdom) {
		World world = server.getEntityWorld();
		for (Settlement city : getCities(world, kingdom)) {
			Settlements.collapse(server.getWorld(city.getDimension()), Settlements.getData(world), city);
		}
		destroy(server, kingdom.id);
	}

	/**
	 * @return every active city of the kingdom, the capital first
	 */
	public static List<Settlement> getCities(World world, KingdomData.Kingdom kingdom) {
		List<Settlement> ret = new ArrayList<>();
		Settlement capital = getCity(world, kingdom);
		if (capital != null) {
			ret.add(capital);
		}
		for (Settlement city : Settlements.getData(world).getAll()) {
			if (city != capital && city.isActive() && city.getType() == Settlement.Type.FACTION_CITY && kingdom.id.equals(city.getFaction())) {
				ret.add(city);
			}
		}
		return ret;
	}

	@Nullable
	public static Settlement getCity(World world, @Nullable KingdomData.Kingdom kingdom) {
		if (kingdom == null || kingdom.destroyed) {
			return null;
		}
		Settlement city = kingdom.cityId < 0 ? null : Settlements.getData(world).get(kingdom.cityId);
		return city != null && city.isActive() && kingdom.id.equals(city.getFaction()) ? city : null;
	}

	private static ITextComponent colored(ITextComponent text, TextFormatting color) {
		text.getStyle().setColor(color);
		return text;
	}

	public static void rename(World world, KingdomData.Kingdom kingdom, String name) {
		kingdom.name = name;
		getData(world).markDirty();
		register(kingdom.id, kingdom.name, kingdom.culture, kingdom.color);
		sync();
	}

	public static void setCulture(World world, KingdomData.Kingdom kingdom, String culture) {
		kingdom.culture = culture;
		kingdom.color = FactionRegistry.getFaction(culture).getColor();
		getData(world).markDirty();
		register(kingdom.id, kingdom.name, kingdom.culture, kingdom.color);
		sync();
	}

	@Nullable
	public static KingdomData.Kingdom getOwnedBy(World world, UUID ownerId) {
		return getData(world).getOwnedBy(ownerId);
	}

	@Nullable
	public static KingdomData.Kingdom get(World world, String id) {
		return getData(world).get(id);
	}

	/**
	 * A culture a kingdom can take: a loaded Ancient Warfare faction that is not a kingdom.
	 */
	public static boolean isValidCulture(String faction) {
		return FactionRegistry.getFactionNames().contains(faction) && !isKingdom(faction);
	}

	private static void register(String id, String name, String culture, int color) {
		FactionRegistry.registerRuntime(id, culture, color);
		REGISTERED.put(id, new Info(name, culture));
	}

	private static void clearRegistered() {
		REGISTERED.keySet().forEach(FactionRegistry::unregisterRuntime);
		REGISTERED.clear();
	}

	private static NBTTagCompound writeAll() {
		NBTTagList list = new NBTTagList();
		REGISTERED.forEach((id, info) -> {
			NBTTagCompound entry = new NBTTagCompound();
			entry.setString("id", id);
			entry.setString("name", info.name);
			entry.setString("culture", info.culture);
			entry.setInteger("color", FactionRegistry.getFaction(id).getColor());
			list.appendTag(entry);
		});
		NBTTagCompound tag = new NBTTagCompound();
		tag.setTag("kingdoms", list);
		return tag;
	}

	public static void sendTo(EntityPlayer player) {
		if (player instanceof EntityPlayerMP) {
			NetworkHandler.sendToPlayer((EntityPlayerMP) player, new PacketKingdoms(writeAll()));
		}
	}

	private static void sync() {
		if (FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
			NetworkHandler.sendToAllPlayers(new PacketKingdoms(writeAll()));
		}
	}

	/**
	 * Client side: replaces the registered kingdoms with the server's list. In single player the integrated server already registered
	 * them in the same maps, so the client leaves them alone (the two threads must not write the maps at the same time).
	 */
	public static void handleClientPacket(NBTTagCompound tag) {
		if (FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
			return;
		}
		clearRegistered();
		NBTTagList list = tag.getTagList("kingdoms", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound entry = list.getCompoundTagAt(i);
			register(entry.getString("id"), entry.getString("name"), entry.getString("culture"), entry.getInteger("color"));
		}
	}

	private static KingdomData getData(World world) {
		return AWGameData.INSTANCE.getData(world, KingdomData.class);
	}
}
