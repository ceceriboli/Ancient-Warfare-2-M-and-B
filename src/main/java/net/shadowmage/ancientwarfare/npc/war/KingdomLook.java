package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcCombat;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;
import net.shadowmage.ancientwarfare.npc.gamedata.KingdomData;
import net.shadowmage.ancientwarfare.npc.registry.FactionNpcDefault;
import net.shadowmage.ancientwarfare.npc.registry.NpcDefaultsRegistry;
import net.shadowmage.ancientwarfare.npc.skin.NpcSkinSettings;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * The soldiers of the owner of a kingdom look like the soldiers of the kingdom's culture: its skins, and (for the soldiers the kingdom gives:
 * recruits, reinforcements, trained veterans) its weapons and armour. A soldier with a skin chosen by the player keeps it.
 */
public final class KingdomLook {
	private static final String TAG_CULTURE_SKIN = "awCultureSkin";
	public static final String ROLE_SOLDIER = "soldier";
	public static final String ROLE_ARCHER = "archer";

	private KingdomLook() {}

	/**
	 * @return the culture of the kingdom owned by the owner of the soldier, or null
	 */
	@Nullable
	public static String getCulture(NpcCombat soldier) {
		if (!AWDiplomacyStatics.kingdomsEnabled || soldier.getOwner().getUUID() == null) {
			return null;
		}
		KingdomData.Kingdom kingdom = Kingdoms.getOwnedBy(soldier.world, soldier.getOwner().getUUID());
		return kingdom == null ? null : kingdom.culture;
	}

	public static String getRole(NpcCombat soldier) {
		return "archer".equals(soldier.getNpcSubType()) ? ROLE_ARCHER : ROLE_SOLDIER;
	}

	/**
	 * Gives the soldier the skins of the culture for its role (random among them, fixed for this soldier), unless the player chose a skin.
	 */
	public static void applySkin(NpcCombat soldier, String culture) {
		NpcSkinSettings current = soldier.getSkinSettings();
		boolean ours = soldier.getEntityData().hasKey(TAG_CULTURE_SKIN);
		if (current.getSkinType() != NpcSkinSettings.SkinType.DEFAULT && !ours) {
			return; // a skin chosen by the player
		}
		String type = culture + "." + getRole(soldier);
		if (ours && current.getSkinType() == NpcSkinSettings.SkinType.NPC_TYPE && type.equals(current.getNpcTypeName())) {
			return;
		}
		NpcSkinSettings skin = new NpcSkinSettings();
		skin.setSkinType(NpcSkinSettings.SkinType.NPC_TYPE);
		skin.setRandom(true);
		skin.setNpcTypeName(type);
		soldier.setSkinSettings(skin);
		soldier.getEntityData().setString(TAG_CULTURE_SKIN, culture);
	}

	/**
	 * Gives the soldier the weapons and armour of the culture's soldiers or archers (their elite ones from tier 2); what it wore in those
	 * slots is dropped.
	 *
	 * @return false if the culture has no equipment for that role (the caller uses the recruit_tier_N_equipment of the config)
	 */
	public static boolean equip(NpcCombat soldier, String culture, String role, int tier) {
		FactionNpcDefault defaults = tier >= 2 ? find(culture, role + ".elite") : null;
		if (defaults == null) {
			defaults = find(culture, role);
		}
		if (defaults == null || defaults.getEquipment().isEmpty()) {
			return false;
		}
		for (Map.Entry<Integer, Item> entry : defaults.getEquipment().entrySet()) {
			EntityEquipmentSlot slot = slotOf(entry.getKey());
			if (slot == null) {
				continue;
			}
			ItemStack old = soldier.getItemStackFromSlot(slot);
			if (!old.isEmpty() && !soldier.world.isRemote) {
				soldier.entityDropItem(old.copy(), 0.5F);
			}
			soldier.setItemStackToSlot(slot, new ItemStack(entry.getValue()));
		}
		applySkin(soldier, culture);
		return true;
	}

	@Nullable
	private static FactionNpcDefault find(String culture, String type) {
		try {
			FactionNpcDefault defaults = NpcDefaultsRegistry.getFactionNpcDefault(culture, type);
			return defaults != null && defaults.isEnabled() ? defaults : null;
		}
		catch (RuntimeException e) {
			return null;
		}
	}

	@Nullable
	private static EntityEquipmentSlot slotOf(int index) {
		// the slot numbers of Ancient Warfare npc defaults: 0 main hand, 1 off hand, 2 feet, 3 legs, 4 chest, 5 head (EntityEquipmentSlot order)
		EntityEquipmentSlot[] slots = EntityEquipmentSlot.values();
		return index >= 0 && index < slots.length ? slots[index] : null;
	}

	/**
	 * A soldier of a kingdom owner is loaded: it takes the skins of the culture (also after a change of culture).
	 */
	public static void onEntityJoinWorld(Entity entity) {
		if (entity.world.isRemote || !(entity instanceof NpcCombat)) {
			return;
		}
		NpcCombat soldier = (NpcCombat) entity;
		String culture = getCulture(soldier);
		if (culture != null) {
			applySkin(soldier, culture);
		}
	}

	/**
	 * The culture of a kingdom changed: its owner's loaded soldiers take the new skins at once.
	 */
	public static void onCultureChanged(World world, KingdomData.Kingdom kingdom) {
		for (Entity entity : world.loadedEntityList) {
			if (entity instanceof NpcCombat && kingdom.ownerId.equals(((NpcCombat) entity).getOwner().getUUID())) {
				applySkin((NpcCombat) entity, kingdom.culture);
				if (world instanceof net.minecraft.world.WorldServer) {
					// the skin travels with the spawn data: the clients get the entity again to see the new one
					net.minecraft.entity.EntityTracker tracker = ((net.minecraft.world.WorldServer) world).getEntityTracker();
					tracker.untrack(entity);
					tracker.track(entity);
				}
			}
		}
	}
}
