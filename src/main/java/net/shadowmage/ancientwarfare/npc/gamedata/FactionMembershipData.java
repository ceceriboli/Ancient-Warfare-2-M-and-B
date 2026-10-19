package net.shadowmage.ancientwarfare.npc.gamedata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Faction each player has pledged allegiance to. A player is a member of at most one faction.
 */
public class FactionMembershipData extends WorldSavedData {
	private final Map<UUID, Member> members = new HashMap<>();

	public FactionMembershipData(String name) {
		super(name);
	}

	@Nullable
	public String getFaction(UUID playerId) {
		Member member = members.get(playerId);
		return member == null ? null : member.faction;
	}

	public void set(UUID playerId, String playerName, String faction) {
		members.put(playerId, new Member(playerId, playerName, faction));
		markDirty();
	}

	@Nullable
	public Member getMember(UUID playerId) {
		return members.get(playerId);
	}

	/**
	 * Records a change of standing with another faction inherited from the member's faction, taken back when the member leaves.
	 */
	public void addInherited(UUID playerId, String otherFaction, int change) {
		Member member = members.get(playerId);
		if (member != null && change != 0) {
			member.inherited.merge(otherFaction, change, Integer::sum);
			markDirty();
		}
	}

	/**
	 * Military service: calls to arms the member missed (see Service), and the day of the last change.
	 */
	public void setStrikes(UUID playerId, int strikes, long day) {
		Member member = members.get(playerId);
		if (member != null) {
			member.strikes = Math.max(0, strikes);
			member.lastStrikeDay = day;
			markDirty();
		}
	}

	/**
	 * Day of the last campaign the member proposed and its ruler accepted.
	 */
	public void setLastProposalDay(UUID playerId, long day) {
		Member member = members.get(playerId);
		if (member != null) {
			member.lastProposalDay = day;
			markDirty();
		}
	}

	/**
	 * Wages earned in military service (units of the wage item), collected from the ruler of the faction.
	 */
	public void addWages(UUID playerId, int amount) {
		Member member = members.get(playerId);
		if (member != null && amount != 0) {
			member.wages = Math.max(0, member.wages + amount);
			markDirty();
		}
	}

	public boolean remove(UUID playerId) {
		if (members.remove(playerId) != null) {
			markDirty();
			return true;
		}
		return false;
	}

	public Collection<Member> getMembers() {
		return Collections.unmodifiableCollection(members.values());
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		members.clear();
		NBTTagList memberList = tag.getTagList("members", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < memberList.tagCount(); i++) {
			NBTTagCompound memberTag = memberList.getCompoundTagAt(i);
			UUID playerId = memberTag.getUniqueId("player");
			if (playerId != null) {
				Member member = new Member(playerId, memberTag.getString("playerName"), memberTag.getString("faction"));
				NBTTagCompound inheritedTag = memberTag.getCompoundTag("inherited");
				for (String otherFaction : inheritedTag.getKeySet()) {
					member.inherited.put(otherFaction, inheritedTag.getInteger(otherFaction));
				}
				member.strikes = memberTag.getInteger("strikes");
				member.lastStrikeDay = memberTag.getLong("lastStrikeDay");
				member.wages = memberTag.getInteger("wages");
				member.lastProposalDay = memberTag.hasKey("lastProposalDay") ? memberTag.getLong("lastProposalDay") : Long.MIN_VALUE;
				members.put(playerId, member);
			}
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		NBTTagList memberList = new NBTTagList();
		for (Member member : members.values()) {
			NBTTagCompound memberTag = new NBTTagCompound();
			memberTag.setUniqueId("player", member.playerId);
			memberTag.setString("playerName", member.playerName);
			memberTag.setString("faction", member.faction);
			NBTTagCompound inheritedTag = new NBTTagCompound();
			member.inherited.forEach(inheritedTag::setInteger);
			memberTag.setTag("inherited", inheritedTag);
			memberTag.setInteger("strikes", member.strikes);
			memberTag.setLong("lastStrikeDay", member.lastStrikeDay);
			memberTag.setInteger("wages", member.wages);
			if (member.lastProposalDay != Long.MIN_VALUE) {
				memberTag.setLong("lastProposalDay", member.lastProposalDay);
			}
			memberList.appendTag(memberTag);
		}
		tag.setTag("members", memberList);
		return tag;
	}

	public static class Member {
		public final UUID playerId;
		public final String playerName;
		public final String faction;
		/**
		 * Standing changes inherited from the faction, per other faction
		 */
		private final Map<String, Integer> inherited = new HashMap<>();
		private int strikes;
		private long lastStrikeDay;
		private long lastProposalDay = Long.MIN_VALUE;
		private int wages;

		public int getWages() {
			return wages;
		}

		/**
		 * @return the day of the last accepted campaign proposal, Long.MIN_VALUE if none
		 */
		public long getLastProposalDay() {
			return lastProposalDay;
		}

		public Map<String, Integer> getInherited() {
			return Collections.unmodifiableMap(inherited);
		}

		public int getStrikes() {
			return strikes;
		}

		public long getLastStrikeDay() {
			return lastStrikeDay;
		}

		private Member(UUID playerId, String playerName, String faction) {
			this.playerId = playerId;
			this.playerName = playerName;
			this.faction = faction;
		}
	}
}
