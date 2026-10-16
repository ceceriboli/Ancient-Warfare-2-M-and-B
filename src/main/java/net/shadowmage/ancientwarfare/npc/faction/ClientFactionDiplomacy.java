package net.shadowmage.ancientwarfare.npc.faction;

import net.minecraft.nbt.NBTTagCompound;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionRelationData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Client copy of the faction relations and of the local player's faction, sent by the server in PacketFactionDiplomacy.
 * Static so that it survives dimension changes, replaced every time the player logs in.
 */
public class ClientFactionDiplomacy {
	private ClientFactionDiplomacy() {}

	@Nullable
	private static UUID playerId = null;
	@Nullable
	private static String playerFaction = null;
	private static Map<String, FactionRelation> relations = Collections.emptyMap();

	public static void handlePacketData(NBTTagCompound tag) {
		playerId = tag.hasUniqueId("player") ? tag.getUniqueId("player") : null;
		playerFaction = tag.hasKey("faction") ? tag.getString("faction") : null;
		relations = FactionRelationData.readRelations(tag);
	}

	@Nullable
	public static String getFaction(UUID player) {
		return player.equals(playerId) ? playerFaction : null;
	}

	@Nullable
	public static FactionRelation getRelation(String factionA, String factionB) {
		return relations.get(FactionRelationData.getKey(factionA, factionB));
	}
}
