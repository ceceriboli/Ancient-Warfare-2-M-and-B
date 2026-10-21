package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.shadowmage.ancientwarfare.npc.AncientWarfareNPC;
import net.shadowmage.ancientwarfare.npc.config.AWDiplomacyStatics;
import net.shadowmage.ancientwarfare.npc.entity.NpcCombat;
import net.shadowmage.ancientwarfare.npc.entity.NpcLevelingStats;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;
import net.shadowmage.ancientwarfare.npc.faction.FactionDialogue;
import net.shadowmage.ancientwarfare.npc.faction.Kingdoms;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Recruits and veterans (on probation: the whole feature is this class, the recruiting_enabled switch, the gui.aw.recruits.* texts and
 * the two buttons of the governor's window, so that it can be removed by reverting its commit).
 * At the governor of a city of his kingdom the owner recruits his own Ancient Warfare combat npcs for coins and manpower of the city,
 * and trains the ones that gained levels in battle into better equipped tiers. The tier is the persistent tag awTier (1 to 3).
 */
public class Recruits {
	private Recruits() {}

	public static final String RECRUIT = "recruit";
	public static final String TRAIN = "train";
	private static final String RECRUIT_PREFIX = "recruit:";
	private static final String TRAIN_ONE_PREFIX = "train_one:";
	private static final String TRAIN_ALL = "train_all";
	static final String TAG_TIER = "awTier";
	private static final int MAX_TIER = 3;
	private static final double TRAIN_RANGE = 16D;
	private static final int PAGE_SIZE = 6;

	public static boolean isEnabled() {
		return AWDiplomacyStatics.recruitingEnabled;
	}

	/**
	 * @return true for the actions of this feature (after the kingdom prefix)
	 */
	public static boolean isAction(String action) {
		return isEnabled() && (action.equals(RECRUIT) || action.startsWith(RECRUIT_PREFIX) || action.equals(TRAIN) || action.startsWith(TRAIN_ONE_PREFIX)
				|| action.equals(TRAIN_ALL));
	}

	/**
	 * @return the city of the governor, if the player owns its kingdom
	 */
	@Nullable
	private static Settlement getCity(EntityPlayerMP player, NpcFaction governor) {
		Settlement city = Settlements.getSettlementOf(governor);
		return city != null && city.isActive() && Kingdoms.isOwner(player.world, player.getUniqueID(), city.getFaction()) ? city : null;
	}

	public static void handle(EntityPlayerMP player, NpcFaction governor, String action) {
		Settlement city = getCity(player, governor);
		if (city == null) {
			return;
		}
		if (action.equals(RECRUIT)) {
			sendRecruitPage(player, governor, city, null);
		} else if (action.startsWith(RECRUIT_PREFIX)) {
			int count = parse(action.substring(RECRUIT_PREFIX.length()));
			sendRecruitPage(player, governor, city, recruit(player, governor, city, count));
		} else if (action.equals(TRAIN)) {
			sendTrainPage(player, governor, null);
		} else if (action.startsWith(TRAIN_ONE_PREFIX)) {
			NpcCombat soldier = findSoldier(player, governor, parse(action.substring(TRAIN_ONE_PREFIX.length())));
			sendTrainPage(player, governor, soldier == null ? null : train(player, soldier));
		} else if (action.equals(TRAIN_ALL)) {
			int trained = 0;
			for (NpcCombat soldier : getTrainable(player, governor)) {
				if (getCost(soldier) > Coins.count(player)) {
					break;
				}
				train(player, soldier);
				trained++;
			}
			sendTrainPage(player, governor, new TextComponentTranslation("gui.aw.recruits.trained_all", trained));
		}
	}

	private static void sendRecruitPage(EntityPlayerMP player, NpcFaction governor, Settlement city, @Nullable ITextComponent answer) {
		ITextComponent text = new TextComponentTranslation("gui.aw.recruits.offer", Settlements.displayName(city), city.getManpower(), AWDiplomacyStatics.recruitCost,
				Coins.getName(), Coins.count(player));
		List<FactionDialogue.PageButton> buttons = new ArrayList<>();
		// 1, 5 and the most allowed in one order, without repeats
		java.util.TreeSet<Integer> counts = new java.util.TreeSet<>();
		for (int count : new int[] {1, 5, AWDiplomacyStatics.recruitMaxPerOrder}) {
			if (count > 0 && count <= AWDiplomacyStatics.recruitMaxPerOrder) {
				counts.add(count);
			}
		}
		for (int count : counts) {
			boolean affordable = city.getManpower() >= count && Coins.count(player) >= count * AWDiplomacyStatics.recruitCost;
			buttons.add(new FactionDialogue.PageButton(new TextComponentTranslation("gui.aw.recruits.recruit", count, count * AWDiplomacyStatics.recruitCost, Coins.getName()),
					RECRUIT_PREFIX + count, affordable, new TextComponentTranslation("gui.aw.recruits.recruit.hover", count)));
		}
		buttons.add(FactionDialogue.PageButton.back());
		FactionDialogue.showKingdomPage(player, governor, withAnswer(answer, text), buttons);
	}

	/**
	 * @return the answer of the governor
	 */
	private static ITextComponent recruit(EntityPlayerMP player, NpcFaction governor, Settlement city, int count) {
		if (count <= 0 || count > AWDiplomacyStatics.recruitMaxPerOrder) {
			return new TextComponentString("");
		}
		int cost = count * AWDiplomacyStatics.recruitCost;
		if (city.getManpower() < count) {
			return new TextComponentTranslation("gui.aw.recruits.no_manpower", Settlements.displayName(city), city.getManpower());
		}
		if (!Coins.take(player, cost)) {
			return new TextComponentTranslation("gui.aw.recruits.no_coins", cost, Coins.getName());
		}
		city.setManpower(city.getManpower() - count);
		Settlements.getData(player.world).markDirty();
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			NpcCombat recruit = new NpcCombat(player.world);
			recruit.setOwner(player);
			BlockPos pos = findStandable(player.world, governor.getPosition());
			recruit.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.world.rand.nextFloat() * 360F, 0F);
			recruit.setHomeAreaAtCurrentPosition();
			equip(recruit, 1);
			recruit.setHealth(recruit.getMaxHealth());
			recruit.getEntityData().setInteger(TAG_TIER, 1);
			recruit.setFollowingEntity(player);
			if (player.world.spawnEntity(recruit)) {
				spawned++;
			}
		}
		AncientWarfareNPC.LOG.info("{} recruited {} soldiers at city {} for {} coins and {} manpower", player.getName(), spawned, city.getId(), cost, count);
		return new TextComponentTranslation("gui.aw.recruits.recruited", spawned);
	}

	private static List<NpcCombat> getTrainable(EntityPlayerMP player, NpcFaction governor) {
		List<NpcCombat> ret = new ArrayList<>();
		for (NpcCombat soldier : player.world.getEntitiesWithinAABB(NpcCombat.class, governor.getEntityBoundingBox().grow(TRAIN_RANGE))) {
			if (soldier.isEntityAlive() && player.getUniqueID().equals(soldier.getOwner().getUUID()) && getTier(soldier) < MAX_TIER
					&& soldier.getLevelingStats().getBaseLevel() >= getRequiredLevel(getTier(soldier) + 1)) {
				ret.add(soldier);
			}
		}
		ret.sort(Comparator.comparingInt(Recruits::getCost));
		return ret;
	}

	private static void sendTrainPage(EntityPlayerMP player, NpcFaction governor, @Nullable ITextComponent answer) {
		List<NpcCombat> trainable = getTrainable(player, governor);
		ITextComponent text = new TextComponentTranslation(trainable.isEmpty() ? "gui.aw.recruits.none_to_train" : "gui.aw.recruits.train_intro",
				getRequiredLevel(2), AWDiplomacyStatics.upgradeCostTier2, getRequiredLevel(3), AWDiplomacyStatics.upgradeCostTier3, Coins.getName(), Coins.count(player), AWDiplomacyStatics.upgradeHealthTier3);
		List<FactionDialogue.PageButton> buttons = new ArrayList<>();
		int total = 0;
		for (NpcCombat soldier : trainable) {
			total += getCost(soldier);
			if (buttons.size() < PAGE_SIZE) {
				buttons.add(new FactionDialogue.PageButton(new TextComponentTranslation("gui.aw.recruits.train_one", soldier.getDisplayName(), soldier.getLevelingStats().getBaseLevel(),
						getTier(soldier), getTier(soldier) + 1, getCost(soldier), Coins.getName()), TRAIN_ONE_PREFIX + soldier.getEntityId(), Coins.count(player) >= getCost(soldier),
						null));
			}
		}
		if (trainable.size() > 1) {
			buttons.add(new FactionDialogue.PageButton(new TextComponentTranslation("gui.aw.recruits.train_all", trainable.size(), total, Coins.getName()), TRAIN_ALL,
					Coins.count(player) >= getCost(trainable.get(0)), null));
		}
		buttons.add(FactionDialogue.PageButton.back());
		FactionDialogue.showKingdomPage(player, governor, withAnswer(answer, text), buttons);
	}

	@Nullable
	private static NpcCombat findSoldier(EntityPlayerMP player, NpcFaction governor, int entityId) {
		for (NpcCombat soldier : getTrainable(player, governor)) {
			if (soldier.getEntityId() == entityId) {
				return soldier;
			}
		}
		return null;
	}

	private static ITextComponent train(EntityPlayerMP player, NpcCombat soldier) {
		int next = getTier(soldier) + 1;
		int cost = getCost(soldier);
		if (!Coins.take(player, cost)) {
			return new TextComponentTranslation("gui.aw.recruits.no_coins", cost, Coins.getName());
		}
		equip(soldier, next);
		soldier.getEntityData().setInteger(TAG_TIER, next);
		if (next >= MAX_TIER && AWDiplomacyStatics.upgradeHealthTier3 > 0) {
			// on the base value (saved with the npc), not as a modifier: NpcBase saves the max health with its modifiers as the base value
			int bonus = AWDiplomacyStatics.upgradeHealthTier3;
			soldier.getEntityData().setInteger(NpcLevelingStats.TAG_HEALTH_BONUS, bonus);
			IAttributeInstance maxHealth = soldier.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
			maxHealth.setBaseValue(maxHealth.getBaseValue() + bonus);
			soldier.heal(bonus);
		}
		AncientWarfareNPC.LOG.info("{} trained a soldier to tier {} for {} coins", player.getName(), next, cost);
		return new TextComponentTranslation("gui.aw.recruits.trained", soldier.getDisplayName(), next);
	}

	private static int getTier(NpcCombat soldier) {
		return Math.max(1, soldier.getEntityData().getInteger(TAG_TIER));
	}

	private static int getRequiredLevel(int tier) {
		return tier >= 3 ? AWDiplomacyStatics.upgradeLevelTier3 : AWDiplomacyStatics.upgradeLevelTier2;
	}

	private static int getCost(NpcCombat soldier) {
		return getTier(soldier) + 1 >= 3 ? AWDiplomacyStatics.upgradeCostTier3 : AWDiplomacyStatics.upgradeCostTier2;
	}

	static void equip(NpcCombat soldier, int tier) {
		equip(soldier, tier, KingdomLook.getRole(soldier), false);
	}

	static void equip(NpcCombat soldier, int tier, String role) {
		equip(soldier, tier, role, false);
	}

	/**
	 * The culture of the owner's kingdom changed: the soldier takes the gear of its tier in the new culture, and the gear of the old one is
	 * not dropped.
	 */
	static void reequip(NpcCombat soldier) {
		equip(soldier, getTier(soldier), KingdomLook.getRole(soldier), true);
	}

	/**
	 * Gives the soldier the equipment of the tier: the one of the soldiers (or archers) of the culture of the owner's kingdom (KingdomLook),
	 * otherwise recruit_tier_N_equipment ("slot=item" entries); what it wore in those slots is dropped (KingdomLook.replace).
	 */
	private static void equip(NpcCombat soldier, int tier, String role, boolean discardKingdomGear) {
		String culture = KingdomLook.getCulture(soldier);
		if (culture == null || !KingdomLook.equip(soldier, culture, role, tier, discardKingdomGear)) {
			equipFromConfig(soldier, tier, discardKingdomGear);
		}
		if (culture != null) {
			KingdomLook.setGearCulture(soldier, culture);
		}
	}

	static boolean isRecruitGear(Item item) {
		for (String[] entries : new String[][] {AWDiplomacyStatics.recruitTier1Equipment, AWDiplomacyStatics.recruitTier2Equipment, AWDiplomacyStatics.recruitTier3Equipment}) {
			for (String entry : entries) {
				String[] parts = entry.trim().split("=", 2);
				if (parts.length == 2 && item == Item.getByNameOrId(parts[1].trim())) {
					return true;
				}
			}
		}
		return false;
	}

	private static void equipFromConfig(NpcCombat soldier, int tier, boolean discardKingdomGear) {
		String[] entries = tier >= 3 ? AWDiplomacyStatics.recruitTier3Equipment : tier == 2 ? AWDiplomacyStatics.recruitTier2Equipment : AWDiplomacyStatics.recruitTier1Equipment;
		for (String entry : entries) {
			String[] parts = entry.trim().split("=", 2);
			if (parts.length != 2) {
				continue;
			}
			EntityEquipmentSlot slot;
			try {
				slot = EntityEquipmentSlot.fromString(parts[0].trim().toLowerCase(Locale.ROOT));
			}
			catch (IllegalArgumentException e) {
				AncientWarfareNPC.LOG.warn("Unknown equipment slot in recruit equipment: {}", entry);
				continue;
			}
			Item item = Item.getByNameOrId(parts[1].trim());
			if (item == null) {
				AncientWarfareNPC.LOG.warn("Unknown item in recruit equipment: {}", entry);
				continue;
			}
			KingdomLook.replace(soldier, slot, item, discardKingdomGear);
		}
	}

	private static BlockPos findStandable(World world, BlockPos around) {
		for (int attempt = 0; attempt < 24; attempt++) {
			BlockPos column = around.add(world.rand.nextInt(9) - 4, 0, world.rand.nextInt(9) - 4);
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos candidate = column.up(dy);
				if (world.isAirBlock(candidate) && world.isAirBlock(candidate.up()) && world.getBlockState(candidate.down()).isFullBlock()) {
					return candidate;
				}
			}
		}
		return around;
	}

	private static ITextComponent withAnswer(@Nullable ITextComponent answer, ITextComponent text) {
		if (answer == null || answer.getUnformattedText().isEmpty()) {
			return text;
		}
		ITextComponent colored = answer.createCopy();
		colored.getStyle().setColor(net.minecraft.util.text.TextFormatting.YELLOW);
		return new TextComponentString("").appendSibling(colored).appendText("\n\n").appendSibling(text);
	}

	private static int parse(String text) {
		try {
			return Integer.parseInt(text);
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}
}
