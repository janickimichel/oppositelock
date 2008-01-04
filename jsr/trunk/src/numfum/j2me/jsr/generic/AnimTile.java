package numfum.j2me.jsr.generic;

import java.io.DataInput;
import java.io.IOException;

import numfum.j2me.util.ByteUtils;

/**
 *	Manages animated sequences for tile (or anything else using indices).
 */
public final class AnimTile {
	/**
	 *	Index of the physical tile at the start of the animation sequence.
	 */
	protected int udgs = 0;
	
	/**
	 *	Number of frames in the sequence.
	 */
	protected int anim = 0;
	
	/**
	 *	Whether the sequence runs backwards or not.
	 */
	protected boolean bkwd = false;
	
	/**
	 *	Delay in frames between each update of the sequence.
	 */
	protected int time = 0;
	
	/**
	 *	Any additional implementation specific data.
	 */
	public int data = 0;
	
	/**
	 *	Current frame of the sequence.
	 */
	private int idx = 0;
	
	/**
	 *	Number of ticks remaining before updating the sequence.
	 */
	private int dla = 0;
	
	/**
	 *	Whether the sequence is currently running or not.
	 */
	private boolean updt = true;
	
	/**
	 *	Creates a new blank <code>AnimTile</code>.
	 */
	public AnimTile() {
		clear();
	}
	
	/**
	 *	Creates an <code>AnimTile</code> with the required params.
	 */
	public AnimTile(int udgs, int anim, boolean bkwd, int time, int data) {
		set(udgs, anim, bkwd, time, data);
	}
	
	/**
	 *	Creates an <code>AnimTile</code> from data stored in a pair of ints.
	 *
	 *	@see set(int tile, int xtra)
	 */
	public AnimTile(int tile, int xtra) {
		set(tile, xtra);
	}
	
	/**
	 *	Sets this tile to a blank state.
	 */
	public void clear() {
		set(0, 0);
	}
	
	/**
	 *	Reset the sequence to the beginning, restarting the running of updates
	 *	it the animation had been stopped.
	 *
	 *	@see #halt
	 */
	public void reset() {
		if (!bkwd) {
			reset(0);
		} else {
			reset(anim);
		}
		updt = true;
	}
	
	/**
	 *	Resets the sequence to the requested frame.
	 */
	public void reset(int idx) {
		this.idx = idx;
		this.dla = 0;
	}
	
	/**
	 *	Sets the sequence from the data stored encoded in a pair of ints.
	 */
	public void set(int tile, int xtra) {
		udgs =  (tile >> ROTL_UDGS) & MASK_UDGS;
		anim =  (tile >> ROTL_ANIM) & MASK_ANIM;
		bkwd = ((tile >> ROTL_BKWD) & MASK_BKWD) != 0;
		time =  (tile >> ROTL_TIME) & MASK_TIME;
		data = ((tile >> ROTL_DATA) & MASK_DATA) | xtra;
		reset();
	}
	
	/**
	 *	Sets the sequence from the individual params.
	 */
	public void set(int udgs, int anim, boolean bkwd, int time, int data) {
		this.udgs = udgs;
		this.anim = anim;
		this.bkwd = bkwd;
		this.time = time;
		this.data = data;
		reset();
	}
	
	/**
	 *	Saves the tile properties (but not the transient data). To be used
	 *	when storing the state of tiles during a save game or similar feature.
	 */
	public int save(byte[] store, int n) {
		int temp = 0;
		temp |= (udgs & MASK_UDGS) << ROTL_UDGS;
		temp |= (anim & MASK_ANIM) << ROTL_ANIM;
		if (bkwd) {
			temp |= MASK_BKWD << ROTL_BKWD;
		}
		temp |= (time & MASK_TIME) << ROTL_TIME;
		ByteUtils.intToBytes(store, n + 0, temp);
		ByteUtils.intToBytes(store, n + 4, data);
		return n + 8;
	}
	
	/**
	 *	Retrieves the tile properties.
	 *
	 *	@see #save
	 */
	public int load(byte[] data, int n) {
		set(ByteUtils.bytesToInt(data, n), ByteUtils.bytesToInt(data, n + 4));
		return n + 8;
	}
	
	/**
	 *	Returns the current showing tile index.
	 */
	public int getTileIndex() {
		return udgs + idx;
	}
	
	/**
	 *	Halts the animation.
	 */
	public void halt() {
		updt = false;
	}
	
	/**
	 *	Halts the animation, setting the current frame.
	 */
	public void halt(int idx) {
		this.idx = idx;
		halt();
	}
	
	/**
	 *	Updates the animation sequence. This should be called once per frame.
	 *
	 *	@return the new tile index
	 */
	public int cycle() {
		if (updt) {
			dla--;
			if (dla < 0) {
				dla = time;
				if (!bkwd) {
					if (idx < anim) {
						idx++;
					} else {
						idx = 0;
					}
				} else {
					if (idx > 0) {
						idx--;
					} else {
						idx = anim;
					}
				}
			}
		}
		return getTileIndex();
	}
	
	/**
	 *	Returns <code>true</code> if the tile has animation frames, i.e.
	 *	<code>anim</code> is greater than zero.
	 *	
	 *	@return <code>true</code> if the tile has animation frames
	 */
	public boolean isAnimated() {
		return anim > 0;
	}
	
	/**
	 *	Returns <code>true</code> if both <code>udgs</code> and
	 *	<code>anim</code> are zero. Both being zero is for the reserved blank
	 *	tile.
	 *
	 *	@return <code>true</code> if both <code>udgs</code> and <code>anim</code> are zero
	 */
	public boolean isZeroTile() {
		return udgs == 0 && anim == 0;
	}
	
	/*public String toString() {
		return "AnimTile [udgs: " + udgs + ", idx: " + idx + "]";
	}*/
	
	/**
	 *	Creates an array of <code>AnimTile</code>s from a data stream.
	 *
	 *	@param extended whether the tiles are stored in one or two ints
	 */
	public static AnimTile[] load(DataInput in, boolean extended) throws IOException {
		int size = in.readUnsignedShort();
		AnimTile[] tile = new AnimTile[size];
		for (int n = 0; n < size; n++) {
			tile[n] = new AnimTile(in.readInt(), extended ? in.readInt() : 0);
		}
		return tile;
	}
	
	public static final int BITS_UDGS = 10;
	public static final int BITS_ANIM =  5;
	public static final int BITS_BKWD =  1;
	public static final int BITS_TIME =  4;
	public static final int BITS_DATA = 12;
	
	public static final int MASK_UDGS = (1 << BITS_UDGS) - 1;
	public static final int MASK_ANIM = (1 << BITS_ANIM) - 1;
	public static final int MASK_BKWD = (1 << BITS_BKWD) - 1;
	public static final int MASK_TIME = (1 << BITS_TIME) - 1;
	public static final int MASK_DATA = (1 << BITS_DATA) - 1;
	
	public static final int ROTL_UDGS = 0;
	public static final int ROTL_ANIM = ROTL_UDGS + BITS_UDGS;
	public static final int ROTL_BKWD = ROTL_ANIM + BITS_ANIM;
	public static final int ROTL_TIME = ROTL_BKWD + BITS_BKWD;
	public static final int ROTL_DATA = ROTL_TIME + BITS_TIME;
	
	// TODO: what's this. Document it!
	// only tiles not using the extended int will support this
	public static final int DATA_BITS_PRTY = 1;
	public static final int DATA_MASK_PRTY = 1;
	public static final int DATA_ROTL_PRTY = BITS_DATA - 1;
	
	/**
	 *	Number of bytes required to store the tile state.
	 *
	 *	@see #save
	 */
	public static final int STORAGE_REQUIRED = 8;
}