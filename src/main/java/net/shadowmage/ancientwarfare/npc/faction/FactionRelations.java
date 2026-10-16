package net.shadowmage.ancientwarfare.npc.faction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.core.gamedata.AWGameData;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.gamedata.FactionRelationData;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;

import javax.annotation.Nullable;

/**
 * Hostility between factions: relations set during the game first, faction registry as fallback.
 */
public class FactionRelations {
	private FactionRelations() {}

	public static boolean isHostile(World world, String faction, String otherFaction) {
		FactionRelation relation = getSetRelation(world, faction, otherFaction);
		if (relation != null) {
			return relation.isHostile();
		}
		return FactionRegistry.getFaction(faction).isHostileTowards(otherFaction);
	}

	/**
	 * @return the relation set in this world, or null if the pair uses the faction registry. On the client, the copy sent by the server.
	 */
	@Nullable
	public static FactionRelation getSetRelation(World world, String faction, String otherFaction) {
		return world.isRemote ? ClientFactionDiplomacy.getRelation(faction, otherFaction) : getData(world).get(faction, otherFaction);
	}

	/**
	 * Sets the relation between two factions, or resets it to the faction registry when relation is null.
	 * Clears the targets of loaded npcs that are no longer hostile to each other and announces the change to all players.
	 */
	public static void setRelation(MinecraftServer server, String faction, String otherFaction, @Nullable FactionRelation relation) {
		World overworld = server.getEntityWorld();
		FactionRelationData data = getData(overworld);
		if (relation == null) {
			data.reset(faction, otherFaction);
		} else {
			data.set(faction, otherFaction, relation);
		}

		for (WorldServer world : server.worlds) {
			clearTargetsIfNotHostile(world, faction, otherFaction);
		}

		if (AWDiplomacyStatics.announceRelationChanges) {
			String messageKey = relation == null ? "command.aw.relation.announce.reset" : "command.aw.relation.announce." + relation.getName();
			server.getPlayerList().sendMessage(new TextComponentTranslation(messageKey, faction, otherFaction));
		}
		FactionMembership.sendDiplomacyToAll(server);
	}

	private static void clearTargetsIfNotHostile(World world, String faction, String otherFaction) {
		boolean hostile = isHostile(world, faction, otherFaction);
		boolean otherHostile = isHostile(world, otherFaction, faction);
		if (hostile && otherHostile) {
			return;
		}
		for (Entity entity : world.loadedEntityList) {
			if (!(entity instanceof NpcFaction)) {
				continue;
			}
			NpcFaction npc = (NpcFaction) entity;
			if (faction.equals(npc.getFaction()) && !hostile) {
				clearTargetsOfFaction(npc, otherFaction);
			} else if (otherFaction.equals(npc.getFaction()) && !otherHostile) {
				clearTargetsOfFaction(npc, faction);
			}
		}
	}

	private static void clearTargetsOfFaction(NpcFaction npc, String targetFaction) {
		boolean attackTargetOfFaction = isOfFaction(npc.getAttackTarget(), targetFaction);
		boolean revengeTargetOfFaction = isOfFaction(npc.getRevengeTarget(), targetFaction);
		if (attackTargetOfFaction || revengeTargetOfFaction) {
			// a running EntityAITarget keeps its own copy of the target and sets it again on the next tick
			// (EntityAITarget.shouldContinueExecuting), resetTask clears that copy as well
			for (EntityAITasks.EntityAITaskEntry entry : npc.targetTasks.taskEntries) {
				if (entry.using && entry.action instanceof EntityAITarget) {
					entry.action.resetTask();
				}
			}
		}
		if (attackTargetOfFaction) {
			npc.setAttackTarget(null);
		}
		if (revengeTargetOfFaction) {
			npc.setRevengeTarget(null);
		}
	}

	private static boolean isOfFaction(@Nullable EntityLivingBase entity, String faction) {
		return entity instanceof NpcFaction && faction.equals(((NpcFaction) entity).getFaction());
	}

	private static FactionRelationData getData(World world) {
		return AWGameData.INSTANCE.getData(world, FactionRelationData.class);
	}
}
