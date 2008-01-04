package numfum.j2me.jsr.generic;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.jsr.Constants;
import numfum.j2me.text.Line;
import numfum.j2me.text.ScrollingList;

/**
 *	A container for <code>Positionable</code>s. Each item is added to here
 *	where it is then drawn from this container's offset. It's a simple way of
 *	laying out and drawing items. The containers can be added to other
 *	containers and so on.
 */
public class PositionableContainer implements Constants, Positionable {
	/**
	 *	Default number of items that can be stored.
	 */
	public static final int DEFAULT_SIZE = 16;
	
	/**
	 *	Width of the container.
	 */
	protected int w;
	
	/**
	 *	Height of the container.
	 */
	protected int h;
	
	/**
	 *	Array of items to be displayed.
	 */
	private final Positionable[] bucket;
	
	/**
	 *	Number of used items.
	 */
	private int used = 0;
	
	/**
	 *	Whether an item is visible or not.
	 */
	private final boolean[] active;
	
	/**
	 *	X-axis position (calculated from the size and anchor).
	 */
	protected int x = 0;
	
	/**
	 *	Y-axis position (calculated from the size and anchor).
	 */
	protected int y = 0;
	
	/**
	 *	Creates an empty container of the default number of slots.
	 */
	public PositionableContainer() {
		this(0, 0, DEFAULT_SIZE);
	}
	
	/**
	 *	Creates an empty container of the specified number of slots.
	 */
	public PositionableContainer(int size) {
		this(0, 0, size);
	}
	
	/**
	 *	Creates an empty container of the specified size and number of slots.
	 */
	public PositionableContainer(int w, int h, int size) {
		bucket = new Positionable[size];
		active = new boolean[size];
		setSize(w, h);
	}
	
	/**
	 *	Adds a new item to the container.
	 */
	public int add(Positionable item) {
		int n = 0;
		for (; n < used; n++) {
			if (bucket[n] == item) {
				break;
			}
		}
		active[n] = true;
		if (used == n) {
			used++;
			bucket[n] = item;
		}
		if (DEBUG) {
			System.out.println("Adding: " + item + " at: " + n + " (total: " + used + ")");
		}
		return n;
	}
	
	/**
	 *	Finds the slot containing <code>itemA</code> and puts
	 *	<code>itemB</code> in its place.
	 *
	 *	@return whether the exchange was successful
	 */
	public boolean replace(Positionable itemA, Positionable itemB) {
		for (int n = 0; n < used; n++) {
			if (bucket[n] == itemA) {
				bucket[n]  = itemB;
				active[n]  = true;
				return true;
			}
		}
		return false;
	}
	
	/**
	 *	Returns <code>true</code> if the container already holds an item. Any
	 *	sub-containers are also optionally checked.
	 */
	public boolean contains(Positionable item, boolean recursive) {
		for (int n = 0; n < used; n++) {
			if (bucket[n] == item) {
				return true;
			}
		}
		if (recursive) {
			for (int n = 0; n < used; n++) {
				if (bucket[n] instanceof PositionableContainer && ((PositionableContainer) bucket[n]).contains(item, true)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 *	Removes an item from the container.
	 */
	public void remove(Positionable item) {
		int n = used - 1;
		for (; n >= 0; n--) {
			if (bucket[n] == item) {
				break;
			}
		}
		
		if (n >= 0) {
			remove(n);
		}
	}
	
	/**
	 *	Removes an item from the container.
	 */
	public void remove(int index) {
		bucket[index] = null;
		if (index == used - 1) {
			used--;
		}
	}
	
	/**
	 *	Empties the container.
	 */
	public void clear() {
		for (int n = 0; n < used; n++) {
			bucket[n] = null;
		}
		used = 0;
	}
	
	/**
	 *	Sets whether an item is active or not.
	 */
	public void setActive(int index, boolean state) {
		active[index] = state;
	}
	
	/**
	 *	Sets the width and height of this container. Used when calculating
	 *	the position from an anchor.
	 */
	public PositionableContainer setSize(int w, int h) {
		this.w = w;
		this.h = h;
		return this;
	}
	
	/**
	 *	Returns the container width.
	 */
	public int getW() {
		return w;
	}
	
	/**
	 *	Returns the container height.
	 */
	public int getH() {
		return h;
	}
	
	/**
	 *	Sets the container's position using one of the anchors from
	 *	<code>Graphics</code>.
	 */
	public Positionable setPosition(int x, int y, int anchor) {
		if ((anchor & Graphics.LEFT) == 0) {
			if ((anchor & Graphics.HCENTER) != 0) {
				x -= w / 2;
			} else if ((anchor & Graphics.RIGHT) != 0) {
				x -= w;
			}
		}
		if ((anchor & Graphics.TOP) == 0) {
			if ((anchor & Graphics.VCENTER) != 0) {
				y -= h / 2;
			} else if ((anchor & Graphics.BOTTOM) != 0) {
				y -= h;
			}
		}
		this.x = x;
		this.y = y;
		return this;
	}
	
	/**
	 *	Returns which <code>Positionable</code> was clicked on.
	 *
	 *	@param allowOwnActions implementations can act on clicks
	 */
	public Positionable getClicked(int x, int y, boolean allowOwnActions) {
		x -= this.x;
		y -= this.y;
		for (int n = used - 1; n >= 0; n--) {
			if (active[n] && bucket[n] != null) {
				if (bucket[n] instanceof Line) {
					if (((Line) bucket[n]).click(x, y)) {
						return bucket[n];
					}
				} else {
					if (bucket[n] instanceof ScrollingList) {
						if (((ScrollingList) bucket[n]).click(x, y, allowOwnActions)) {
							return bucket[n];
						}
					} else {
						if (bucket[n] instanceof PositionableContainer) {
							Positionable child = ((PositionableContainer) bucket[n]).getClicked(x, y, allowOwnActions);
							if (child != null) {
								return child;
							}
						}
					}
				}
			}
		}
		return null;
	}
	
	/*
	 *	Clip used by MIDP1.0 implementations.
	 */
	private int clipX, clipY, clipH, clipW;
	
	/**
	 *	Draws the container's visible elements.
	 */
	public void paint(Graphics g, int offsetX, int offsetY) {
		if (ENABLE_RESTORE_CLIP) {
			clipX = g.getClipX();
			clipY = g.getClipY();
			clipW = g.getClipWidth();
			clipH = g.getClipHeight();
		}
		offsetX += x;
		offsetY += y;
		for (int n = 0; n < used; n++) {
			if (active[n] && bucket[n] != null) {
				if (ENABLE_RESTORE_CLIP) {
					/*
					 *	Restores the original clip values for MIDP1 phones.
					 *	These could have been left in an undetermined state
					 *	after painting certain objects (such as bitmap fonts).
					 */
					if (bucket[n] instanceof PositionableContainer) {
						g.setClip(clipX, clipY, clipW, clipH);
					}
				}
				bucket[n].paint(g, offsetX, offsetY);
			}
		}
		if (ENABLE_RESTORE_CLIP) {
			g.setClip(clipX, clipY, clipW, clipH);
		}
	}
	
	public String toString() {
		return getClass().getName() + " [used: " + used + "]";
	}
}