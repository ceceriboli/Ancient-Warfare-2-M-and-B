package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;
import net.shadowmage.ancientwarfare.npc.network.PacketCities;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the active cities are and whose they are, on both sides: the server reads the settlement data, the client a copy sent with
 * PacketCities (at login and when a city is founded, falls, changes hands or grows). Needed by things both sides compute, like the
 * digging speed in protected structures.
 */
public final class CityMap {
	private static final int SYNC_INTERVAL = 100;
	private static List<Area> clientCities = Collections.emptyList();
	private static String lastSent = "";

	private CityMap() {}

	/**
	 * @return true if pos is inside an active city of the faction the player belongs to (his faction's cities, the cities it conquered, the
	 * cities of his own kingdom)
	 */
	public static boolean isOwnCity(World world, EntityPlayer player, BlockPos pos) {
		String faction = FactionMembership.getFaction(world, player.getUniqueID());
		return faction != null && faction.equals(getCityFactionAt(world, pos));
	}

	@Nullable
	public static String getCityFactionAt(World world, BlockPos pos) {
		int dimension = world.provider.getDimension();
		if (!world.isRemote) {
			Settlement city = Settlements.getData(world).getActiveAt(dimension, pos, Settlement.Type.FACTION_CITY);
			return city == null ? null : city.getFaction();
		}
		for (Area area : clientCities) {
			if (area.contains(dimension, pos)) {
				return area.faction;
			}
		}
		return null;
	}

	public static void sendTo(EntityPlayer player) {
		if (player instanceof EntityPlayerMP) {
			NetworkHandler.sendToPlayer((EntityPlayerMP) player, new PacketCities(writeAll(player.world)));
		}
	}

	/**
	 * Every few seconds: the cities are sent again to all players when anything changed since the last time.
	 */
	public static void tick(World world, long now) {
		if (now % SYNC_INTERVAL != 0 || world.provider.getDimension() != 0) {
			return;
		}
		NBTTagCompound tag = writeAll(world);
		String signature = tag.toString();
		if (!signature.equals(lastSent)) {
			lastSent = signature;
			NetworkHandler.sendToAllPlayers(new PacketCities(tag));
		}
	}

	private static NBTTagCompound writeAll(World world) {
		NBTTagList list = new NBTTagList();
		for (Settlement city : Settlements.getData(world).getAll()) {
			if (city.isActive() && city.getType() == Settlement.Type.FACTION_CITY) {
				NBTTagCompound entry = new NBTTagCompound();
				entry.setInteger("dim", city.getDimension());
				entry.setLong("min", city.getMin().toLong());
				entry.setLong("max", city.getMax().toLong());
				entry.setString("faction", city.getFaction());
				list.appendTag(entry);
			}
		}
		NBTTagCompound tag = new NBTTagCompound();
		tag.setTag("cities", list);
		return tag;
	}

	public static void handleClientPacket(NBTTagCompound tag) {
		List<Area> cities = new ArrayList<>();
		NBTTagList list = tag.getTagList("cities", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound entry = list.getCompoundTagAt(i);
			cities.add(new Area(entry.getInteger("dim"), BlockPos.fromLong(entry.getLong("min")), BlockPos.fromLong(entry.getLong("max")),
					entry.getString("faction")));
		}
		clientCities = cities;
	}

	private static final class Area {
		private final int dimension;
		private final BlockPos min;
		private final BlockPos max;
		private final String faction;

		private Area(int dimension, BlockPos min, BlockPos max, String faction) {
			this.dimension = dimension;
			this.min = min;
			this.max = max;
			this.faction = faction;
		}

		private boolean contains(int dim, BlockPos pos) {
			return dim == dimension && pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
					&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
		}
	}
}
