package net.shadowmage.ancientwarfare.npc.entity.faction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.EntityAIWatchClosest2;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.npc.ai.NpcAIDoor;
import net.shadowmage.ancientwarfare.npc.ai.NpcAIFollowPlayer;
import net.shadowmage.ancientwarfare.npc.ai.NpcAIMoveHome;
import net.shadowmage.ancientwarfare.npc.ai.NpcAIWander;
import net.shadowmage.ancientwarfare.npc.item.ItemCommandBaton;
import net.shadowmage.ancientwarfare.npc.trade.FactionTradeList;
import net.shadowmage.ancientwarfare.npc.war.Goods;
import net.shadowmage.ancientwarfare.npc.war.Markets;

import java.util.List;


public class NpcFactionTrader extends NpcFaction {
	// the first good of its trade in a city market ("" for none), sent to the clients for the title above its head
	private static final DataParameter<String> SPECIALTY = EntityDataManager.createKey(NpcFactionTrader.class, DataSerializers.STRING);
	private static final int SPECIALTY_CHECK_TICKS = 100;

	private FactionTradeList tradeList = new FactionTradeList();
	private EntityPlayer trader;
	private boolean noTradesDespawn = false;

	@SuppressWarnings("unused")
	public NpcFactionTrader(World world) {
		super(world);
		addAI();
	}

	@SuppressWarnings("unused")
	public NpcFactionTrader(World world, String factionName) {
		super(world, factionName);
		addAI();
	}

	private void addAI() {
		tasks.addTask(0, new EntityAISwimming(this));
		tasks.addTask(0, new EntityAIRestrictOpenDoor(this));
		tasks.addTask(0, new NpcAIDoor(this, true));
		tasks.addTask(1, new NpcAIFollowPlayer(this));
		tasks.addTask(2, new NpcAIMoveHome(this, 50F, 5F, 30F, 5F));

		tasks.addTask(101, new EntityAIWatchClosest2(this, EntityPlayer.class, 3.0F, 1.0F));
		tasks.addTask(102, new NpcAIWander(this));
		tasks.addTask(103, new EntityAIWatchClosest(this, EntityLiving.class, 8.0F));
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		dataManager.register(SPECIALTY, "");
	}

	public FactionTradeList getTradeList() {
		return tradeList;
	}

	/**
	 * @return the id of the first good of its trade (see Markets.getSpecialty), "" for a trader with none
	 */
	public String getSpecialty() {
		return dataManager.get(SPECIALTY);
	}

	private void updateSpecialty() {
		List<Goods.Good> specialty = Markets.getSpecialty(this);
		String id = specialty.isEmpty() ? "" : specialty.get(0).getId();
		if (!id.equals(dataManager.get(SPECIALTY))) {
			dataManager.set(SPECIALTY, id);
		}
	}

	public void startTrade(EntityPlayer player) {
		trader = player;
	}

	public void closeTrade() {
		trader = null;
	}

	@Override
	public void onUpdate() {
		super.onUpdate();
		if (!world.isRemote) {
			if (noTradesDespawn && tradeList.isEmpty()) {
				setDead();
			}
			tradeList.tick(world);
			// the list can change (trade setup, a reloaded goods catalog)
			if (ticksExisted % SPECIALTY_CHECK_TICKS == 1) {
				updateSpecialty();
			}
		}
	}

	@Override
	protected boolean processInteract(EntityPlayer player, EnumHand hand) {
		boolean baton = !player.getHeldItem(hand).isEmpty() && player.getHeldItem(hand).getItem() instanceof ItemCommandBaton;
		if (!baton && isEntityAlive()) {
			if (!player.world.isRemote && !player.isSneaking() && player instanceof net.minecraft.entity.player.EntityPlayerMP
					&& net.shadowmage.ancientwarfare.npc.war.Markets.tryOpen((net.minecraft.entity.player.EntityPlayerMP) player, this)) {
				// a trader in a city opens the city's market
				return true;
			}
			if (!player.world.isRemote && trader == null && !player.isSneaking()) {
				startTrade(player);
				NetworkHandler.INSTANCE.openGui(player, NetworkHandler.GUI_NPC_FACTION_TRADE_VIEW, getEntityId(), 0, 0);
			}
			return true;
		}
		return false;
	}

	@Override
	public String getNpcType() {
		return "trader";
	}

	@Override
	public boolean isHostileTowards(Entity e) {
		return false;
	}

	@Override
	public boolean canTarget(Entity e) {
		return false;
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound tag) {
		super.readEntityFromNBT(tag);
		tradeList.deserializeNBT(tag.getCompoundTag("tradeList"));
		noTradesDespawn = true;
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound tag) {
		super.writeEntityToNBT(tag);
		tag.setTag("tradeList", tradeList.serializeNBT());
	}
}
