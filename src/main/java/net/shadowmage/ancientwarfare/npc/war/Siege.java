package net.shadowmage.ancientwarfare.npc.war;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.shadowmage.ancientwarfare.npc.entity.faction.NpcFaction;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A siege of a faction city: after a warm up, waves of attackers spawn near the city and march on its leader.
 * The next wave comes when the previous one is wiped out. The attackers are not saved here: each of them carries the siege id
 * in its persistent entity data and joins the siege again when it is loaded.
 * <p>
 * A campaign (an attack of the faction of a player, or on a city of its faction, see {@link Service}) starts mustering: nothing
 * happens until a called player comes near the city, or the muster time is over.
 */
public class Siege {
	public enum State {
		MUSTER, WARMUP, WAVE;

		public String getName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static State fromName(String name) {
			for (State state : values()) {
				if (state.getName().equals(name)) {
					return state;
				}
			}
			return WARMUP;
		}
	}

	/**
	 * AMBIENT: an attack started near a player, as any automatic attack. OFFENSIVE: an attack of the faction of a player, who is called
	 * to join it. DEFENSIVE: an attack on a city of the faction of a player, who is called to defend it.
	 */
	public enum Origin {
		AMBIENT, OFFENSIVE, DEFENSIVE;

		public String getName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Origin fromName(String name) {
			for (Origin origin : values()) {
				if (origin.getName().equals(name)) {
					return origin;
				}
			}
			return AMBIENT;
		}
	}

	/**
	 * A player called to fight in this attack, on the side of the faction it was a member of when called.
	 */
	public static class Call {
		private final String faction;
		private final boolean attacking;
		private int presenceTicks;
		private boolean excused;
		// not saved
		long lastReminder;

		Call(String faction, boolean attacking) {
			this.faction = faction;
			this.attacking = attacking;
		}

		public String getFaction() {
			return faction;
		}

		public boolean isAttacking() {
			return attacking;
		}

		public int getPresenceTicks() {
			return presenceTicks;
		}

		void addPresenceTicks(int ticks) {
			presenceTicks += ticks;
		}

		public boolean isExcused() {
			return excused;
		}

		void setExcused() {
			excused = true;
		}

		private NBTTagCompound writeToNBT(NBTTagCompound tag) {
			tag.setString("faction", faction);
			tag.setBoolean("attacking", attacking);
			tag.setInteger("presence", presenceTicks);
			tag.setBoolean("excused", excused);
			return tag;
		}

		private static Call readFromNBT(NBTTagCompound tag) {
			Call call = new Call(tag.getString("faction"), tag.getBoolean("attacking"));
			call.presenceTicks = tag.getInteger("presence");
			call.excused = tag.getBoolean("excused");
			return call;
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
	private int idleTicks;
	private Origin origin = Origin.AMBIENT;
	private long musterUntil;
	private final Map<UUID, Call> calls = new LinkedHashMap<>();

	// not saved
	private final List<WeakReference<NpcFaction>> attackers = new ArrayList<>();
	boolean suspended = true;
	long reconnectGraceUntil;

	public Siege(int id) {
		this.id = id;
	}

	public static Siege create(int id, Kind kind, Origin origin, Settlement settlement, String attackerFaction, int totalWaves, int attackersPerWave) {
		Siege siege = new Siege(id);
		siege.kind = kind;
		siege.origin = origin;
		siege.settlementId = settlement.getId();
		siege.dimension = settlement.getDimension();
		siege.attackerFaction = attackerFaction;
		siege.totalWaves = totalWaves;
		siege.attackersPerWave = attackersPerWave;
		return siege;
	}

	public Origin getOrigin() {
		return origin;
	}

	/**
	 * @return the total world time when the muster is over (state MUSTER)
	 */
	public long getMusterUntil() {
		return musterUntil;
	}

	void startMuster(long musterUntil) {
		this.musterUntil = musterUntil;
		setState(State.MUSTER);
	}

	void setMusterUntil(long musterUntil) {
		this.musterUntil = musterUntil;
	}

	void setSize(int totalWaves, int attackersPerWave) {
		this.totalWaves = totalWaves;
		this.attackersPerWave = attackersPerWave;
	}

	public Map<UUID, Call> getCalls() {
		return Collections.unmodifiableMap(calls);
	}

	@Nullable
	public Call getCall(UUID playerId) {
		return calls.get(playerId);
	}

	void addCall(UUID playerId, Call call) {
		calls.put(playerId, call);
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
		idleTicks = 0;
	}

	/**
	 * @return ticks of the current wave without any attacker or defender killed
	 */
	public int getIdleTicks() {
		return idleTicks;
	}

	/**
	 * @return true while the city center is not loaded: the timers of the siege do not run
	 */
	public boolean isSuspended() {
		return suspended;
	}

	void addIdleTicks(int ticks) {
		idleTicks += ticks;
	}

	void resetIdleTicks() {
		idleTicks = 0;
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

	public int getTotalTicks() {
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
		tag.setInteger("idleTicks", idleTicks);
		tag.setString("origin", origin.getName());
		tag.setLong("musterUntil", musterUntil);
		NBTTagList callList = new NBTTagList();
		for (Map.Entry<UUID, Call> entry : calls.entrySet()) {
			NBTTagCompound callTag = entry.getValue().writeToNBT(new NBTTagCompound());
			callTag.setUniqueId("player", entry.getKey());
			callList.appendTag(callTag);
		}
		tag.setTag("calls", callList);
		return tag;
	}

	public static Siege readFromNBT(NBTTagCompound tag) {
		Siege siege = new Siege(tag.getInteger("id"));
		Kind kind = Kind.fromName(tag.getString("kind"));
		siege.kind = kind == null ? Kind.RAID : kind;
		siege.settlementId = tag.getInteger("settlement");
		siege.dimension = tag.getInteger("dimension");
		siege.attackerFaction = tag.getString("attacker");
		siege.state = State.fromName(tag.getString("state"));
		siege.wavesSpawned = tag.getInteger("wavesSpawned");
		siege.totalWaves = tag.getInteger("totalWaves");
		siege.attackersPerWave = tag.getInteger("attackersPerWave");
		siege.stateTicks = tag.getInteger("stateTicks");
		siege.totalTicks = tag.getInteger("totalTicks");
		siege.waveMaxHealth = tag.getFloat("waveMaxHealth");
		siege.idleTicks = tag.getInteger("idleTicks");
		siege.origin = Origin.fromName(tag.getString("origin"));
		siege.musterUntil = tag.getLong("musterUntil");
		NBTTagList callList = tag.getTagList("calls", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < callList.tagCount(); i++) {
			NBTTagCompound callTag = callList.getCompoundTagAt(i);
			UUID playerId = callTag.getUniqueId("player");
			if (playerId != null) {
				siege.calls.put(playerId, Call.readFromNBT(callTag));
			}
		}
		return siege;
	}
}
