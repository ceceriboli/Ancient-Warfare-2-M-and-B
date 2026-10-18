package net.shadowmage.ancientwarfare.npc.faction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
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
		register(kingdom.id, kingdom.name, kingdom.culture, kingdom.color);
		sync();
		AncientWarfareNPC.LOG.info("Founded kingdom {} ({}, culture {}) for {}", kingdom.id, kingdom.name, culture, owner.getName());
		return kingdom;
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
