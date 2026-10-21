package net.shadowmage.ancientwarfare.npc.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.shadowmage.ancientwarfare.core.network.PacketBase;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.minecraft.entity.player.EntityPlayerMP;
import net.shadowmage.ancientwarfare.npc.war.Markets;

import java.io.IOException;

/**
 * Client to server: a button of the market window (close, the old trades, or confirm a deal with its rows and quantities).
 * The server computes everything again (Markets.handleAction).
 */
public class PacketMarketAction extends PacketBase {
	private NBTTagCompound packetData;

	public PacketMarketAction(NBTTagCompound tag) {
		this.packetData = tag;
	}

	public PacketMarketAction() {}

	@Override
	protected void writeToStream(ByteBuf data) {
		if (packetData != null) {
			try (ByteBufOutputStream outputStream = new ByteBufOutputStream(data)) {
				CompressedStreamTools.writeCompressed(packetData, outputStream);
			}
			catch (IOException e) {
				AncientWarfareNPC.LOG.error("Error writing market action packet data: ", e);
			}
		}
	}

	@Override
	protected void readFromStream(ByteBuf data) {
		try (ByteBufInputStream inputStream = new ByteBufInputStream(data)) {
			packetData = CompressedStreamTools.readCompressed(inputStream);
		}
		catch (IOException e) {
			AncientWarfareNPC.LOG.error("Error reading market action packet data: ", e);
		}
	}

	@Override
	protected void execute(EntityPlayer player) {
		if (packetData != null && player instanceof EntityPlayerMP) {
			Markets.handleAction((EntityPlayerMP) player, packetData);
		}
	}
}
