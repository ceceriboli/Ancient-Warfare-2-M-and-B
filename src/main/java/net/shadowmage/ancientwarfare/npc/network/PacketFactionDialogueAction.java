package net.shadowmage.ancientwarfare.npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.shadowmage.ancientwarfare.core.network.PacketBase;
import net.shadowmage.ancientwarfare.npc.faction.FactionDialogue;

/**
 * Client to server: the player pressed a button of the dialogue window of a faction npc. The server checks everything again.
 */
public class PacketFactionDialogueAction extends PacketBase {
	private static final int MAX_ACTION_LENGTH = 64;
	private int entityId;
	private String action = "";

	public PacketFactionDialogueAction(int entityId, String action) {
		this.entityId = entityId;
		this.action = action;
	}

	public PacketFactionDialogueAction() {}

	@Override
	protected void writeToStream(ByteBuf data) {
		PacketBuffer buffer = new PacketBuffer(data);
		buffer.writeInt(entityId);
		buffer.writeString(action);
	}

	@Override
	protected void readFromStream(ByteBuf data) {
		PacketBuffer buffer = new PacketBuffer(data);
		entityId = buffer.readInt();
		action = buffer.readString(MAX_ACTION_LENGTH);
	}

	@Override
	protected void execute(EntityPlayer player) {
		if (player instanceof EntityPlayerMP) {
			FactionDialogue.handleAction((EntityPlayerMP) player, entityId, action);
		}
	}
}
