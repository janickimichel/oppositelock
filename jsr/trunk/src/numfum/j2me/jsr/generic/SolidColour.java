package numfum.j2me.jsr.generic;

import javax.microedition.lcdui.Graphics;


/**
 *	Simple <code>Positionable</code implementation that draws a solid colour
 *	block of a set size.
 */
public final class SolidColour implements Positionable {
	/**
	 *	Colour to draw,
	 */
	private final int rgb;
	
	/**
	 *	Width of the block.
	 */
	private int w;
	
	/**
	 *	Height of the block.
	 */
	private int h;
	
	/**
	 *	X-axis position after calculating the anchor's offset.
	 */
	private int x;
	
	/**
	 *	Y-axis position after calculating the anchor's offset.
	 */
	private int y;
	
	/**
	 *	Creates a new block of the specified colour and size.
	 */
	public SolidColour(int rgb, int w, int h) {
		this.rgb = rgb;
		setSize(w, h);
	}
	
	/**
	 *	Sets the size of this block. Note the position is not recalculated.
	 */
	public void setSize(int w, int h) {
		this.w = w;
		this.h = h;
	}
	
	/**
	 *	Returns the width of the block.
	 */
	public int getW() {
		return w;
	}
	
	/**
	 *	Returns the width of the block.
	 */
	public int getH() {
		return h;
	}
	
	/**
	 *	Sets the blocks's position using one of the anchors from
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
	 *	Draws the block at the specified offset.
	 */
	public void paint(Graphics g, int offsetX, int offsetY) {
		g.setColor(rgb);
		g.fillRect(x + offsetX, y + offsetY, w, h);
	}
	
	public String toString() {
		return getClass().getName() + " [rgb: " + Integer.toString(rgb, 16) + "]";
	}
}