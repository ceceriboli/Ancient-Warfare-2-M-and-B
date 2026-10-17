package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.nbt.NBTTagCompound;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * A siege of a faction city: after a warm up, waves of attackers spawn near the city and march on its leader.
 * The next wave comes when the previous one is wiped out. The attackers are not saved here: each of them carries the siege id
 * in its persistent entity data and joins the siege again when it is loaded.
 */
public class Siege {
	public enum State {
		WARMUP, WAVE;

		public String getName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * RAID: a small attack of infantry and cavalry. SIEGE: a full siege, bigger and rarer, with siege engines manned by siege engineers.
	 */
	public enum Kind {
		RAID, SIEGE;

		public String getName() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Kind fromName(String name) {
			for (Kind kind : values()) {
				if (kind.getName().equals(name)) {
					return kind;
				}
			}
			return null;
		}
	}

	private final int id;
	private Kind kind = Kind.RAID;
	private int settlementId;
	private int dimension;
	private String attackerFaction = "";
	private State state = State.WARMUP;
	private int wavesSpawned;
	private int totalWaves;
	private int attackersPerWave;
	private int stateTicks;
	private int totalTicks;
	private float waveMaxHealth;

	// not saved
	private final List<WeakReference<NpcFaction>> attackers = new ArrayList<>();
	boolean suspended = true;
	long reconnectGraceUntil;

	public Siege(int id) {
		this.id = id;
	}

	public static Siege create(int id, Kind kind, Settlement settlement, String attackerFaction, int totalWaves, int attackersPerWave) {
		Siege siege = new Siege(id);
		siege.kind = kind;
		siege.settlementId = settlement.getId();
		siege.dimension = settlement.getDimension();
		siege.attackerFaction = attackerFaction;
		siege.totalWaves = totalWaves;
		siege.attackersPerWave = attackersPerWave;
		return siege;
	}

	public int getId() {
		return id;
	}

	public Kind getKind() {
		return kind;
	}

	public int getSettlementId() {
		return settlementId;
	}

	public int getDimension() {
		return dimension;
	}

	public String getAttackerFaction() {
		return attackerFaction;
	}

	public State getState() {
		return state;
	}

	void setState(State state) {
		this.state = state;
		stateTicks = 0;
	}

	public int getWavesSpawned() {
		return wavesSpawned;
	}

	void setWavesSpawned(int wavesSpawned) {
		this.wavesSpawned = wavesSpawned;
	}

	public int getTotalWaves() {
		return totalWaves;
	}

	public int getAttackersPerWave() {
		return attackersPerWave;
	}

	int getStateTicks() {
		return stateTicks;
	}

	int getTotalTicks() {
		return totalTicks;
	}

	void addTicks(int ticks) {
		stateTicks += ticks;
		totalTicks += ticks;
	}

	float getWaveMaxHealth() {
		return waveMaxHealth;
	}

	void setWaveMaxHealth(float waveMaxHealth) {
		this.waveMaxHealth = waveMaxHealth;
	}

	void addAttacker(NpcFaction npc) {
		for (WeakReference<NpcFaction> ref : attackers) {
			if (ref.get() == npc) {
				return;
			}
		}
		attackers.add(new WeakReference<>(npc));
	}

	/**
	 * @return the attackers that are alive and loaded, forgetting the others
	 */
	public List<NpcFaction> getLoadedAttackers() {
		List<NpcFaction> ret = new ArrayList<>();
		Iterator<WeakReference<NpcFaction>> it = attackers.iterator();
		while (it.hasNext()) {
			NpcFaction npc = it.next().get();
			if (npc == null || !npc.isEntityAlive() || !npc.isAddedToWorld()) {
				it.remove();
			} else {
				ret.add(npc);
			}
		}
		return ret;
	}

	void removeAttacker(NpcFaction npc) {
		attackers.removeIf(ref -> ref.get() == null || ref.get() == npc);
	}

	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag.setInteger("id", id);
		tag.setString("kind", kind.getName());
		tag.setInteger("settlement", settlementId);
		tag.setInteger("dimension", dimension);
		tag.setString("attacker", attackerFaction);
		tag.setString("state", state.getName());
		tag.setInteger("wavesSpawned", wavesSpawned);
		tag.setInteger("totalWaves", totalWaves);
		tag.setInteger("attackersPerWave", attackersPerWave);
		tag.setInteger("stateTicks", stateTicks);
		tag.setInteger("totalTicks", totalTicks);
		tag.setFloat("waveMaxHealth", waveMaxHealth);
		return tag;
	}

	public static Siege readFromNBT(NBTTagCompound tag) {
		Siege siege = new Siege(tag.getInteger("id"));
		Kind kind = Kind.fromName(tag.getString("kind"));
		siege.kind = kind == null ? Kind.RAID : kind;
		siege.settlementId = tag.getInteger("settlement");
		siege.dimension = tag.getInteger("dimension");
		siege.attackerFaction = tag.getString("attacker");
		siege.state = State.WAVE.getName().equals(tag.getString("state")) ? State.WAVE : State.WARMUP;
		siege.wavesSpawned = tag.getInteger("wavesSpawned");
		siege.totalWaves = tag.getInteger("totalWaves");
		siege.attackersPerWave = tag.getInteger("attackersPerWave");
		siege.stateTicks = tag.getInteger("stateTicks");
		siege.totalTicks = tag.getInteger("totalTicks");
		siege.waveMaxHealth = tag.getFloat("waveMaxHealth");
		return siege;
	}
}
