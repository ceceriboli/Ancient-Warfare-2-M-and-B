package net.shadowmage.ancientwarfare.npc.event;

import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.shadowmage.ancientwarfare.npc.ai.PathService;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.faction.Diplomacy;
import net.shadowmage.ancientwarfare.npc.war.Armies;
import net.shadowmage.ancientwarfare.npc.war.Service;
import net.shadowmage.ancientwarfare.npc.war.Sieges;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.shadowmage.ancientwarfare.npc.war.Settlements;

public class SettlementEventHandler {
	public static final SettlementEventHandler INSTANCE = new SettlementEventHandler();

	private SettlementEventHandler() {}

	@SubscribeEvent
	public void onEntityJoinWorld(EntityJoinWorldEvent evt) {
		Settlements.onEntityJoinWorld(evt.getEntity());
		Armies.onEntityJoinWorld(evt.getEntity());
		net.shadowmage.ancientwarfare.npc.war.KingdomLook.onEntityJoinWorld(evt.getEntity());
	}

	@SubscribeEvent
	public void onLivingDeath(LivingDeathEvent evt) {
		if (!evt.getEntityLiving().world.isRemote && evt.getEntityLiving() instanceof NpcFaction) {
			Diplomacy.onNpcKilled((NpcFaction) evt.getEntityLiving(), evt.getSource());
			Service.onEnemyKilled((NpcFaction) evt.getEntityLiving(), evt.getSource());
		}
		Settlements.onLivingDeath(evt.getEntityLiving(), evt.getSource());
	}

	@SubscribeEvent
	public void onLivingHurt(LivingHurtEvent evt) {
		if (!evt.getEntityLiving().world.isRemote && evt.getEntityLiving() instanceof NpcFaction) {
			Sieges.onNpcHurt((NpcFaction) evt.getEntityLiving(), evt.getSource().getTrueSource());
		}
	}

	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent evt) {
		if (evt.phase == TickEvent.Phase.END && evt.world instanceof WorldServer) {
			Settlements.tick((WorldServer) evt.world);
			Diplomacy.tick((WorldServer) evt.world);
			Armies.tick((WorldServer) evt.world);
			PathService.tick((WorldServer) evt.world);
		}
	}

	@SubscribeEvent
	public void onWorldUnload(WorldEvent.Unload evt) {
		PathService.onWorldUnload(evt.getWorld());
	}
}
