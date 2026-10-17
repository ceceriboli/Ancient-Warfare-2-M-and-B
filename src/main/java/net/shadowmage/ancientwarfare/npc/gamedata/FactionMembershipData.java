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

		public Map<String, Integer> getInherited() {
			return Collections.unmodifiableMap(inherited);
		}

		private Member(UUID playerId, String playerName, String faction) {
			this.playerId = playerId;
			this.playerName = playerName;
			this.faction = faction;
		}
	}
}
