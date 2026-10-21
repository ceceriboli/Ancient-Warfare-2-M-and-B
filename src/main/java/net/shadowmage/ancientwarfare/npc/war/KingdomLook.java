package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcCombat;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;
import net.shadowmage.ancientwarfare.npc.gamedata.KingdomData;
import net.shadowmage.ancientwarfare.npc.registry.FactionNpcDefault;
import net.shadowmage.ancientwarfare.npc.registry.FactionRegistry;
import net.shadowmage.ancientwarfare.npc.registry.NpcDefaultsRegistry;
import net.shadowmage.ancientwarfare.npc.skin.NpcSkinSettings;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The soldiers of the owner of a kingdom look like the soldiers of the kingdom's culture: its skins, and (for the soldiers the kingdom gives:
 * recruits, reinforcements, trained veterans) its weapons and armour, also after a change of culture. A soldier with a skin chosen by the
 * player keeps it.
 */
public final class KingdomLook {
	private static final String TAG_CULTURE_SKIN = "awCultureSkin";
	// the culture whose weapons and armour the soldier was given
	private static final String TAG_CULTURE_GEAR = "awCultureGear";
	private static final String[] GEAR_TYPES = {"soldier", "archer", "soldier.elite", "archer.elite"};
	// soldiers loaded with the gear of an old culture, re-equipped in the next world tick (items dropped while a chunk loads would be spawned
	// in the middle of the loading)
	private static final List<NpcCombat> PENDING_GEAR = new ArrayList<>();
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
	 * slots is dropped (see replace).
	 *
	 * @return false if the culture has no equipment for that role (the caller uses the recruit_tier_N_equipment of the config)
	 */
	public static boolean equip(NpcCombat soldier, String culture, String role, int tier, boolean discardKingdomGear) {
		FactionNpcDefault defaults = tier >= 2 ? find(culture, role + ".elite") : null;
		if (defaults == null) {
			defaults = find(culture, role);
		}
		if (defaults == null || defaults.getEquipment().isEmpty()) {
			return false;
		}
		for (Map.Entry<Integer, Item> entry : defaults.getEquipment().entrySet()) {
			EntityEquipmentSlot slot = slotOf(entry.getKey());
			if (slot != null) {
				replace(soldier, slot, entry.getValue(), discardKingdomGear);
			}
		}
		applySkin(soldier, culture);
		return true;
	}

	/**
	 * Puts the item in the slot. What the soldier wore there is dropped, unless discardKingdomGear and it is gear the kingdoms give
	 * (of any culture, or of the recruit tiers): a change of culture would otherwise leave piles of armour around every army.
	 */
	static void replace(NpcCombat soldier, EntityEquipmentSlot slot, Item item, boolean discardKingdomGear) {
		ItemStack old = soldier.getItemStackFromSlot(slot);
		if (!old.isEmpty() && !soldier.world.isRemote && !(discardKingdomGear && isKingdomGear(old.getItem()))) {
			soldier.entityDropItem(old.copy(), 0.5F);
		}
		soldier.setItemStackToSlot(slot, new ItemStack(item));
	}

	private static boolean isKingdomGear(Item item) {
		for (String culture : FactionRegistry.getFactionNames()) {
			for (String type : GEAR_TYPES) {
				FactionNpcDefault defaults = find(culture, type);
				if (defaults != null && defaults.getEquipment().containsValue(item)) {
					return true;
				}
			}
		}
		return Recruits.isRecruitGear(item);
	}

	static void setGearCulture(NpcCombat soldier, String culture) {
		soldier.getEntityData().setString(TAG_CULTURE_GEAR, culture);
	}

	/**
	 * @return true for a soldier that wears the gear of another culture than the kingdom's: recruits, trained soldiers and free reinforcements
	 * (also the ones from before the tag, once), never the soldiers equipped by the player
	 */
	private static boolean needsNewGear(NpcCombat soldier, String culture) {
		NBTTagCompound data = soldier.getEntityData();
		return (data.hasKey(TAG_CULTURE_GEAR) || data.hasKey(Recruits.TAG_TIER) || data.hasKey(Settlements.TAG_KINGDOM_GARRISON))
				&& !culture.equals(data.getString(TAG_CULTURE_GEAR));
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
	 * A soldier of a kingdom owner is loaded: it takes the skins of the culture (also after a change of culture), and its gear in the next tick.
	 */
	public static void onEntityJoinWorld(Entity entity) {
		if (entity.world.isRemote || !(entity instanceof NpcCombat)) {
			return;
		}
		NpcCombat soldier = (NpcCombat) entity;
		String culture = getCulture(soldier);
		if (culture != null) {
			applySkin(soldier, culture);
			if (needsNewGear(soldier, culture)) {
				PENDING_GEAR.add(soldier);
			}
		}
	}

	public static void tick(WorldServer world) {
		Iterator<NpcCombat> it = PENDING_GEAR.iterator();
		while (it.hasNext()) {
			NpcCombat soldier = it.next();
			if (soldier.isDead) {
				it.remove();
			} else if (soldier.world == world) {
				it.remove();
				String culture = getCulture(soldier);
				if (culture != null && needsNewGear(soldier, culture)) {
					Recruits.reequip(soldier);
				}
			}
		}
	}

	/**
	 * The culture of a kingdom changed: its owner's loaded soldiers take the new skins at once, and the ones the kingdom equipped its gear.
	 */
	public static void onCultureChanged(World world, KingdomData.Kingdom kingdom) {
		List<NpcCombat> soldiers = new ArrayList<>();
		for (Entity entity : world.loadedEntityList) {
			if (entity instanceof NpcCombat && kingdom.ownerId.equals(((NpcCombat) entity).getOwner().getUUID())) {
				soldiers.add((NpcCombat) entity);
			}
		}
		// outside of the loop on loadedEntityList: the dropped items join it
		for (NpcCombat soldier : soldiers) {
			if (needsNewGear(soldier, kingdom.culture)) {
				Recruits.reequip(soldier);
			}
			applySkin(soldier, kingdom.culture);
			if (world instanceof WorldServer) {
				// the skin travels with the spawn data: the clients get the entity again to see the new one
				net.minecraft.entity.EntityTracker tracker = ((WorldServer) world).getEntityTracker();
				tracker.untrack(soldier);
				tracker.track(soldier);
			}
		}
	}
}
