package numfum.j2me.jsr;

import numfum.j2me.util.Comparable;
import numfum.j2me.util.Fixed;

/**
 *	Holds details about a sprite for either drawing or sorting.
 */
public class Sprite implements Comparable {
	/**
	 *	Sprite reference.
	 */
	public int n;
	
	/**
	 *	X-coordinate.
	 */
	public int x;
	
	/**
	 *	Y-coordinate.
	 */
	public int y;
	
	/**
	 *	Whether the sprite is in view or not.
	 */
	public boolean view;
	
	/**
	 *	Length of [x,y] squared. Used when sorting the sprite draw order.
	 *	Alternatively, other values that determin the draw order, such as
	 *	height from the floor for a topdown view, should be stored here.
	 */
	public int d;
	
	/**
	 *	Any additional info to be carried with the sprite. This would be
	 *	implementation specific.
	 */
	public int data;
	
	/**
	 *	A temporary col assigned to the sprite during rendering or sorting.
	 */
	public int col;
	
	/**
	 *	A temporary row assigned to the sprite during rendering or sorting.
	 */
	public int row;
	
	
	/**
	 *	Sets multiple sprite parameters in one go.
	 *
	 *	@param n sprite reference
	 *	@param x x-coordinate
	 *	@param y y-coordinate
	 *	@param data additional info carried with the sprite
	 */
	public void set(int n, int x, int y, int data) {
		this.n = n;
		this.x = x;
		this.y = y;
		d = Fixed.mul(x, x) + Fixed.mul(y, y);
		this.data = data;
	}
	
	/**
	 *	Sets multiple sprite parameters in one go.
	 *
	 *	@param n sprite reference
	 *	@param x x-coordinate
	 *	@param y y-coordinate
	 *	@param d precalculated [x,y] squared
	 *	@param data additional info carried with the sprite
	 */
	public void set(int n, int x, int y, int d, int data) {
		this.n = n;
		this.x = x;
		this.y = y;
		this.d = d;
		this.data = data;
	}
	
	/**
	 *	Copies all the passed sprite properties.
	 */
	public void set(Sprite sprite) {
		n = sprite.n;
		x = sprite.x;
		y = sprite.y;
		d = sprite.d;
		
		data = sprite.data;
	}
	
	/**
	 *	Copies all the passed sprite properties apart from the reference and
	 *	data. Used to place multiple sprites in the same location (JSR's track
	 *	effects being an example).
	 */
	public void set(Sprite sprite, int n, int data) {
		x = sprite.x;
		y = sprite.y;
		d = sprite.d;
		
		this.n    = n;
		this.data = data;
	}
	
	/**
	 *	Used when sorting sprites into draw order.
	 */
	public int compareTo(Object obj) {
		return d - ((Sprite) obj).d;
	}
	
	/**
	 *	How high the sprite is off the ground.
	 */
	private static final int DATA_BITS_BUMP_Y = 4;
	
	/**
	 *	A boolean vibration value.
	 */
	private static final int DATA_BITS_BUZZ_Y = 1;
	
	/**
	 *	An internal reference given to this sprite.
	 */
	private static final int DATA_BITS_OBJREF = 7;
	
	/**
	 *	The sprite index (not the same as the sprite reference).
	 */
	private static final int DATA_BITS_SPRITE = 9;
	
	public static final int DATA_MASK_BUMP_Y = (1 << DATA_BITS_BUMP_Y) - 1;
	public static final int DATA_MASK_BUZZ_Y = (1 << DATA_BITS_BUZZ_Y) - 1;
	public static final int DATA_MASK_OBJREF = (1 << DATA_BITS_OBJREF) - 1;
	public static final int DATA_MASK_SPRITE = (1 << DATA_BITS_SPRITE) - 1;
	
	public static final int DATA_ROTL_BUMP_Y = 0;
	public static final int DATA_ROTL_BUZZ_Y = DATA_ROTL_BUMP_Y + DATA_BITS_BUMP_Y;
	public static final int DATA_ROTL_OBJREF = DATA_ROTL_BUZZ_Y + DATA_BITS_BUZZ_Y;
	public static final int DATA_ROTL_SPRITE = DATA_ROTL_OBJREF + DATA_BITS_OBJREF;
}