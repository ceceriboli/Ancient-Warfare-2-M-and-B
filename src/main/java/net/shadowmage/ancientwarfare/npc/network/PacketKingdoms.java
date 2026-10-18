package net.shadowmage.ancientwarfare.npc.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.shadowmage.ancientwarfare.core.network.PacketBase;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;

import java.io.IOException;

/**
 * Server to client: the player kingdoms (id, display name, culture, color), so that the client can register their factions.
 */
public class PacketKingdoms extends PacketBase {
	private NBTTagCompound packetData;

	public PacketKingdoms(NBTTagCompound tag) {
		this.packetData = tag;
	}

	public PacketKingdoms() {}

	@Override
	protected void writeToStream(ByteBuf data) {
		if (packetData != null) {
			try (ByteBufOutputStream outputStream = new ByteBufOutputStream(data)) {
				CompressedStreamTools.writeCompressed(packetData, outputStream);
			}
			catch (IOException e) {
				AncientWarfareNPC.LOG.error("Error writing kingdoms packet data: ", e);
			}
		}
	}

	@Override
	protected void readFromStream(ByteBuf data) {
		try (ByteBufInputStream inputStream = new ByteBufInputStream(data)) {
			packetData = CompressedStreamTools.readCompressed(inputStream);
		}
		catch (IOException e) {
			AncientWarfareNPC.LOG.error("Error reading kingdoms packet data: ", e);
		}
	}

	@Override
	protected void execute(EntityPlayer player) {
		if (packetData != null) {
			Kingdoms.handleClientPacket(packetData);
		}
	}
}
