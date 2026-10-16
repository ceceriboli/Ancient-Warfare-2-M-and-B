package net.shadowmage.ancientwarfare.npc.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.shadowmage.ancientwarfare.core.owner.Owner;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcPlayerOwned;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.faction.FactionMembership;

import java.util.UUID;

public class FactionMembershipEventHandler {
	public static final FactionMembershipEventHandler INSTANCE = new FactionMembershipEventHandler();

	private FactionMembershipEventHandler() {}

	@SubscribeEvent
	public void onPlayerLogin(PlayerLoggedInEvent evt) {
		if (evt.player instanceof EntityPlayerMP) {
			FactionMembership.sendDiplomacy((EntityPlayerMP) evt.player);
		}
	}

	@SubscribeEvent
	public void onLivingDeath(LivingDeathEvent evt) {
		if (!(evt.getEntityLiving() instanceof NpcFaction) || evt.getEntityLiving().world.isRemote
				|| !AWDiplomacyStatics.membershipEnabled || !AWDiplomacyStatics.expelOnKillingOwnFaction) {
			return;
		}
		NpcFaction victim = (NpcFaction) evt.getEntityLiving();
		Entity killer = evt.getSource().getTrueSource();
		UUID playerId;
		String playerName;
		if (killer instanceof EntityPlayer) {
			playerId = killer.getUniqueID();
			playerName = killer.getName();
		} else if (killer instanceof NpcPlayerOwned) {
			Owner owner = ((NpcPlayerOwned) killer).getOwner();
			playerId = owner.getUUID();
			playerName = owner.getName();
		} else {
			return;
		}
		//noinspection ConstantConditions - owner UUID can be null for npcs without an owner
		if (playerId == null) {
			return;
		}
		String playerFaction = FactionMembership.getFaction(victim.world, playerId);
		if (playerFaction == null || !playerFaction.equals(victim.getFaction())) {
			return;
		}
		MinecraftServer server = victim.world.getMinecraftServer();
		if (server != null) {
			FactionMembership.leave(server, playerId, playerName, AWDiplomacyStatics.expelStandingPenalty, "command.aw.membership.announce.expel");
		}
	}
}
