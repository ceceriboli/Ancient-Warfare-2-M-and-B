package net.shadowmage.ancientwarfare.npc.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.shadowmage.ancientwarfare.core.network.PacketBase;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.faction.ClientFactionDiplomacy;

import java.io.IOException;

/**
 * Server to client: faction relations set in the world and the faction of the receiving player.
 */
public class PacketFactionDiplomacy extends PacketBase {
	private NBTTagCompound packetData;

	public PacketFactionDiplomacy(NBTTagCompound tag) {
		this.packetData = tag;
	}

	public PacketFactionDiplomacy() {}

	@Override
	protected void writeToStream(ByteBuf data) {
		if (packetData != null) {
			try (ByteBufOutputStream outputStream = new ByteBufOutputStream(data)) {
				CompressedStreamTools.writeCompressed(packetData, outputStream);
			}
			catch (IOException e) {
				AncientWarfareNPC.LOG.error("Error writing faction diplomacy packet data: ", e);
			}
		}
	}

	@Override
	protected void readFromStream(ByteBuf data) {
		try (ByteBufInputStream inputStream = new ByteBufInputStream(data)) {
			packetData = CompressedStreamTools.readCompressed(inputStream);
		}
		catch (IOException e) {
			AncientWarfareNPC.LOG.error("Error reading faction diplomacy packet data: ", e);
		}
	}

	@Override
	protected void execute(EntityPlayer player) {
		if (packetData != null) {
			ClientFactionDiplomacy.handlePacketData(packetData);
		}
	}
}
