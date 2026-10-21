package net.shadowmage.ancientwarfare.npc.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.shadowmage.ancientwarfare.core.network.PacketBase;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.gui.GuiMarket;

import java.io.IOException;

/**
 * Server to client: the market window of a city (its goods with their prices, the player's goods, the coins of both), or the order to
 * close it. See Markets.sendView for the content of the tag.
 */
public class PacketMarket extends PacketBase {
	private NBTTagCompound packetData;

	public PacketMarket(NBTTagCompound tag) {
		this.packetData = tag;
	}

	public PacketMarket() {}

	@Override
	protected void writeToStream(ByteBuf data) {
		if (packetData != null) {
			try (ByteBufOutputStream outputStream = new ByteBufOutputStream(data)) {
				CompressedStreamTools.writeCompressed(packetData, outputStream);
			}
			catch (IOException e) {
				AncientWarfareNPC.LOG.error("Error writing market packet data: ", e);
			}
		}
	}

	@Override
	protected void readFromStream(ByteBuf data) {
		try (ByteBufInputStream inputStream = new ByteBufInputStream(data)) {
			packetData = CompressedStreamTools.readCompressed(inputStream);
		}
		catch (IOException e) {
			AncientWarfareNPC.LOG.error("Error reading market packet data: ", e);
		}
	}

	@Override
	protected void execute(EntityPlayer player) {
		// received on the client only
		if (packetData != null && player.world.isRemote) {
			GuiMarket.show(packetData);
		}
	}
}
