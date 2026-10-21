package net.shadowmage.ancientwarfare.npc.container;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.SlotItemHandler;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcBase;
import net.shadowmage.ancientwarfare.npc.entity.NpcCombat;
import net.shadowmage.ancientwarfare.npc.entity.NpcLevelingStats;
import net.shadowmage.ancientwarfare.npc.inventory.NpcEquipmentHandler;
import net.shadowmage.ancientwarfare.npc.skin.NpcSkinSettings;
import net.shadowmage.ancientwarfare.npc.war.Recruits;

public class ContainerNpcInventory extends ContainerNpcBase<NpcBase> implements ISkinSettingsContainer {
	public boolean doNotPursue; //if the npc should not pursue targets away from its position/route
	public boolean isArcher = entity.getNpcSubType().equals("archer");
	public final int guiHeight;
	private String name;
	public NpcSkinSettings skinSettings;
	// sent by the server when the gui opens: the base level, its xp, the xp to the next level (0 at the highest), the training tier (0 = none)
	public int level = -1;
	public int xp;
	public int xpToNextLevel;
	public int tier;
	public int tier2Level;
	public int healthBonus;

	public ContainerNpcInventory(EntityPlayer player, int x, int y, int z) {
		super(player, x);
		NpcEquipmentHandler inventory = new NpcEquipmentHandler(entity);
		addSlotToContainer(new SlotItemHandler(inventory, 0, 8, 8)); //weapon slot
		addSlotToContainer(new SlotItemHandler(inventory, 1, 8, 8 + 18));//shield slot
		addSlotToContainer(new SlotItemHandler(inventory, 2, 8, 8 + 18 * 5));//boots
		addSlotToContainer(new SlotItemHandler(inventory, 3, 8, 8 + 18 * 4));//legs
		addSlotToContainer(new SlotItemHandler(inventory, 4, 8, 8 + 18 * 3));//chest
		addSlotToContainer(new SlotItemHandler(inventory, 5, 8, 8 + 18 * 2));//helm
		addSlotToContainer(new SlotItemHandler(inventory, 6, 8 + 18 * 2, 8 + 18 * 3));//work/combat/route orders slot
		addSlotToContainer(new SlotItemHandler(inventory, 7, 8 + 18 * 2, 8 + 18 * 2));//upkeep orders slot

		guiHeight = addPlayerSlots(8 + 5 * 18 + 8 + 18) + 8;

		name = entity.getCustomNameTag();
		doNotPursue = entity.getDoNotPursue();
		skinSettings = entity.getSkinSettings();
	}

	public void sendChangesToServer() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setBoolean("donotpursue", doNotPursue);
		sendDataToServer(tag);
	}

	public void sendInitData() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setBoolean("donotpursue", doNotPursue);
		tag.setInteger("level", entity.getLevelingStats().getBaseLevel());
		tag.setInteger("xp", entity.getLevelingStats().getBaseExperience());
		tag.setInteger("xpToNextLevel", entity.getLevelingStats().getBaseExperienceToNextLevel());
		// combat soldiers of a player are tier 1 until trained, like the training counts them (crafted ones and reinforcements have no tag)
		int tier = entity.getEntityData().getInteger(Recruits.TAG_TIER);
		if (tier <= 0 && entity instanceof NpcCombat && entity.getOwner().getUUID() != null) {
			tier = 1;
		}
		tag.setInteger("tier", tier);
		tag.setInteger("tier2Level", AWDiplomacyStatics.upgradeLevelTier2);
		tag.setInteger("healthBonus", entity.getEntityData().getInteger(NpcLevelingStats.TAG_HEALTH_BONUS));
		sendDataToClient(tag);
	}

	@Override
	public void handlePacketData(NBTTagCompound tag) {
		if (tag.hasKey("level") && player.world.isRemote) {
			level = tag.getInteger("level");
			xp = tag.getInteger("xp");
			xpToNextLevel = tag.getInteger("xpToNextLevel");
			tier = tag.getInteger("tier");
			tier2Level = tag.getInteger("tier2Level");
			healthBonus = tag.getInteger("healthBonus");
		}
		if (tag.hasKey("customName")) {
			this.name = tag.getString("customName");
		}
		if (entity != null && !entity.isDead) {
			if (tag.hasKey("repack")) {
				entity.repackEntity(player);
			} else if (tag.hasKey("setHome")) {
				entity.setHomeAreaAtCurrentPosition();
			} else if (tag.hasKey("clearHome")) {
				entity.detachHome();
			} else if (tag.hasKey("togglefollow")) {
				if (entity.getFollowingEntity() != null && entity.getFollowingEntity().getName().equals(player.getName())) {
					entity.clearFollowingEntity();
				} else { entity.setFollowingEntity(player); }
			}
			if (tag.hasKey("donotpursue")) {
				doNotPursue = tag.getBoolean("donotpursue");
			}
			if (tag.hasKey("skinSettings")) {
				skinSettings = NpcSkinSettings.deserializeNBT(tag.getCompoundTag("skinSettings")).minimizeData();
				entity.setSkinSettings(skinSettings);
			}
		}
		refreshGui();
	}

	public void handleNpcNameUpdate(String newName) {
		name = newName;
	}

	@Override
	public void handleNpcSkinUpdate() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setTag("skinSettings", skinSettings.serializeNBT());
		sendDataToServer(tag);
	}

	@Override
	public void onContainerClosed(EntityPlayer p_75134_1_) {
		super.onContainerClosed(p_75134_1_);
		entity.setCustomNameTag(name);
		entity.setSkinSettings(skinSettings);
		entity.setDoNotPursue(doNotPursue);
	}

	public void setName() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setString("customName", name);
		sendDataToServer(tag);
	}

	@Override
	public NpcSkinSettings getSkinSettings() {
		return skinSettings;
	}

	@Override
	public void setSkinSettings(NpcSkinSettings skinSettings) {
		this.skinSettings = skinSettings;
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int slotClickedIndex) {
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = inventorySlots.get(slotClickedIndex);

		if (slot != null && slot.getHasStack()) {
			ItemStack slotStack = slot.getStack();
			itemstack = slotStack.copy();
			EntityEquipmentSlot entityequipmentslot = EntityLiving.getSlotForItemStack(itemstack);

			if (slotClickedIndex < 8) {
				if (!mergeItemStack(slotStack, 8, 44, true)) {
					return ItemStack.EMPTY;
				}
			} else if (entityequipmentslot.getSlotType() == EntityEquipmentSlot.Type.ARMOR && !inventorySlots.get(2 + entityequipmentslot.getIndex()).getHasStack()) {
				int i = 2 + entityequipmentslot.getIndex();

				if (!mergeItemStack(slotStack, i, i + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (entityequipmentslot == EntityEquipmentSlot.MAINHAND && !inventorySlots.get(0).getHasStack() && !inventorySlots.get(1).getHasStack()) {
				if (!mergeItemStack(slotStack, 0, 2, false)) {
					return ItemStack.EMPTY;
				}
			} else if (entityequipmentslot == EntityEquipmentSlot.OFFHAND && !inventorySlots.get(1).getHasStack()) {
				if (!mergeItemStack(slotStack, 1, 2, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!getSlot(6).getHasStack() && getSlot(6).isItemValid(slotStack)) {
				if (!mergeItemStack(slotStack, 6, 7, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!getSlot(7).getHasStack() && getSlot(7).isItemValid(slotStack)) {
				if (!mergeItemStack(slotStack, 7, 8, false)) {
					return ItemStack.EMPTY;
				}
			} else if (slotClickedIndex < 17) {
				if (!mergeItemStack(slotStack, 17, 44, false)) {
					return ItemStack.EMPTY;
				}
			} else if (slotClickedIndex < 44) {
				if (!mergeItemStack(slotStack, 8, 17, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!mergeItemStack(slotStack, 8, 44, false)) {
				return ItemStack.EMPTY;
			}

			if (slotStack.isEmpty()) {
				slot.putStack(ItemStack.EMPTY);
			} else {
				slot.onSlotChanged();
			}

			if (slotStack.getCount() == itemstack.getCount()) {
				return ItemStack.EMPTY;
			}

			ItemStack itemstack2 = slot.onTake(player, slotStack);

			if (slotClickedIndex == 0) {
				player.dropItem(itemstack2, false);
			}
		}
		return itemstack;
	}
}
