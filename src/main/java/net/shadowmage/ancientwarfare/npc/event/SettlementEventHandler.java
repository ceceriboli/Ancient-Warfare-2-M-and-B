package net.shadowmage.ancientwarfare.npc.event;

import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.shadowmage.ancientwarfare.npc.war.Settlements;

public class SettlementEventHandler {
	public static final SettlementEventHandler INSTANCE = new SettlementEventHandler();

	private SettlementEventHandler() {}

	@SubscribeEvent
	public void onEntityJoinWorld(EntityJoinWorldEvent evt) {
		Settlements.onEntityJoinWorld(evt.getEntity());
	}

	@SubscribeEvent
	public void onLivingDeath(LivingDeathEvent evt) {
		Settlements.onLivingDeath(evt.getEntityLiving(), evt.getSource());
	}

	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent evt) {
		if (evt.phase == TickEvent.Phase.END && evt.world instanceof WorldServer) {
			Settlements.tick((WorldServer) evt.world);
		}
	}
}
