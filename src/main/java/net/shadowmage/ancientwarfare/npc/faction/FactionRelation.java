package net.shadowmage.ancientwarfare.npc.faction;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Diplomatic state between two factions, set during the game (see {@link FactionRelations}).
 * PEACE and ALLIANCE are both non hostile; ALLIANCE is kept separate for features that build on it.
 */
public enum FactionRelation {
	WAR(true),
	PEACE(false),
	ALLIANCE(false);

	private final boolean hostile;

	FactionRelation(boolean hostile) {
		this.hostile = hostile;
	}

	public boolean isHostile() {
		return hostile;
	}

	public String getName() {
		return name().toLowerCase(Locale.ROOT);
	}

	@Nullable
	public static FactionRelation fromName(String name) {
		for (FactionRelation relation : values()) {
			if (relation.getName().equalsIgnoreCase(name)) {
				return relation;
			}
		}
		return null;
	}
}
