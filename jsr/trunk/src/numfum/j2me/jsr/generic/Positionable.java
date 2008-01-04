package numfum.j2me.jsr.generic;

import javax.microedition.lcdui.Graphics;

/**
 *	Defines an on-screen element which can be positioned by use of an anchor.
 */
public interface Positionable {
	/**
	 *	Element's width in pixels.
	 */
	public int getW();
	
	/**
	 *	Element's height in pixels.
	 */
	public int getH();
	
	/**
	 *	Sets the element's position using one of the anchors from
	 *	<code>Graphics</code>.
	 */
	public Positionable setPosition(int x, int y, int anchor);
	
	/**
	 *	Draws this element from the given offset.
	 */
	public void paint(Graphics g, int offsetX, int offsetY);
}