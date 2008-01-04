package numfum.j2me.jsr;

import numfum.j2me.util.ByteUtils;
import numfum.j2me.util.Comparable;
import numfum.j2me.util.Fixed;

/**
 *	Holds player data for the scoreboard.
 *
 *	TODO: store laps and use when comparing players with equal points
 */
public final class Player implements Comparable {
	/**
	 *	Which physical player is associated with this object.
	 */
	public final int index;
	
	/**
	 *	Index of the kart chosen by this player or -1 if the player has still
	 *	to pick.
	 */
	public int kartIdx = -1;
	
	/**
	 *	Whether this player is controlled by a human, either on the phone or
	 *	via Bluetooth, or by the AI.
	 */
	public boolean isHuman = false;
	
	/**
	 *	Position of this player on the starting grid. Setting this to -1 puts
	 *	the kart in a special single player position.
	 */
	public int gridPos = 0;
	
	/**
	 *	Total of points awarded for racing.
	 */
	public int points = 0;
	
	/**
	 *	Creates a new player instance and associates it with a physical player.
	 */
	public Player(int index) {
		this.index = index;
	}
	
	/**
	 *	A complete reset creates a completely black player with zero points,
	 *	otherwise only the per race settings are cleared.
	 */
	public void reset() {
		points = 0;
	}
	
	/**
	 *	A complete reset followed by race param setting.
	 */
	public void reset(int kartIdx, boolean isHuman, int gridPos) {
		reset();
		this.kartIdx = kartIdx;
		this.isHuman = isHuman;
		this.gridPos = gridPos;
	}
	
	/**
	 *	Increases the player's points.
	 */
	public void addPoints(int amount) {
		points += amount;
	}
	
	/**
	 *	Stores this player ready for network transmission.
	 */
	public synchronized int saveNetworkPacket(byte[] data, int n) {
		data[n++] = (byte) kartIdx;
		ByteUtils.booleanToByte(data, n++, isHuman);
		data[n++] = (byte) gridPos;
		return n;
	}
	
	/**
	 *	Retrieves the player params from a network transmission.
	 */
	public synchronized int loadNetworkPacket(byte[] data, int n) {
		kartIdx = data[n++];
		isHuman = ByteUtils.byteToBoolean(data, n++);
		gridPos = data[n++];
		return n;
	}
	
	public int compareTo(Object obj) {
		return ((Player) obj).points - this.points;
	}
	
	public String toString() {
		return "Player [index: " + index + ", points: " + points + "]";
	}
	
	/**
	 *	Fills the supplied array from the given offset with exclusive values up
	 *	to and including maxValue.
	 *
	 *	TODO: rewrite the above comment
	 */
	public static void randomExclusiveFill(Player[] dest, int type, int offset, int maxValue) {
		int next = 0;
		for (int n = offset; n < dest.length; n++) {
			while (true) {
				next = Fixed.rand(maxValue);
				boolean unused = true;
				search:for (int i = 0; i < n; i++) {
					int member;
					switch (type) {
					case FILL_KART_IDX:
						member = dest[i].kartIdx;
						break;
					case FILL_GRID_POS:
						member = dest[i].gridPos;
						break;
					default:
						return;
					}
					if (member == next) {
						unused = false;
						break search;
					}
				}
				if (unused) {
					break;
				}
			}
			switch (type) {
			case FILL_KART_IDX:
				dest[n].kartIdx = next;
				break;
			case FILL_GRID_POS:
				dest[n].gridPos = next;
				break;
			}
		}
	}
	
	/**
	 *	Resets multiple players.
	 */
	public static void reset(Player[] dest, int offset) {
		for (int n = offset; n < dest.length; n++) {
			dest[n].reset(-1, false, -1);
		}
	}
	
	/**
	 *	Resets the array of players, and assigns a random kart and position.
	 */
	public static void reset(Player[] dest, int offset, int maxKartIdx, int maxGridPos) {
		reset(dest, offset);
		randomExclusiveFill(dest, FILL_KART_IDX, offset, maxKartIdx);
		randomExclusiveFill(dest, FILL_GRID_POS, offset, maxGridPos);
	}
	
	public static final int FILL_KART_IDX = 0;
	public static final int FILL_GRID_POS = 1;
}