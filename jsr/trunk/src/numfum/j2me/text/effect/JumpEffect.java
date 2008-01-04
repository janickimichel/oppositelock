package numfum.j2me.text.effect;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.text.BitmapFont;

/**
 *	<code>TextEffect</code> implementation for offsetting alternate glyphs.
 */
public final class JumpEffect implements TextEffect {
	/**
	 *	Constant for running the animation vertically.
	 */
	public static final int MOVE_VERTICALLY = 0;
	
	/**
	 *	Constant for running the animation horizontally.
	 */
	public static final int MOVE_HORIZONTALLY = 1;
	
	/**
	 *	Current draw tick, updated per glyph.
	 */
	private int tick = 0;
	
	/**
	 *	Number of pixels to move each glyph by (or the size of the jump).
	 */
	private int size = 0;
	
	/**
	 *	Whether this effect is currently incrementing or decrementing.
	 */
	private boolean inc = false;
	
	/**
	 *	Chosen direction of the effect.
	 */
	private final int dir;
	
	/**
	 *	Number of ticks between each update.
	 */
	private final int time;
	
	/**
	 *	Number of ticks left before performing the next update.
	 */
	private int delay = 0;
	
	/**
	 *	Creates a new horizontal effect at the fastest speed.
	 */
	public JumpEffect() {
		this(MOVE_VERTICALLY, 0);
	}
	
	/**
	 *	Creates a new effect at the fastest speed.
	 */
	public JumpEffect(int dir) {
		this(dir, 0);
	}
	
	/**
	 *	Creates a new effect at the specified speed.
	 */
	public JumpEffect(int dir, int time) {
		this.dir  = dir;
		this.time = time;
	}
	
	/**
	 *	Draws a single glyph using this effect.
	 */
	public void paint(BitmapFont font, Graphics g, int n, int x, int y) {
		switch (dir) {
		case MOVE_VERTICALLY:
			font.paint(g, n, x, y + ((tick & 1) == 0 ? - size : size));
			break;
		case MOVE_HORIZONTALLY:
			font.paint(g, n, x + ((tick & 1) == 0 ? - size : size), y);
			break;
		}
		tick++;
	}
	
	/**
	 *	Draws a single glyph using this effect from the specified anchor.
	 */
	public void paint(BitmapFont font, Graphics g, int n, int x, int y, int anchor) {
		switch (dir) {
		case MOVE_VERTICALLY:
			font.paint(g, n, x, y + ((tick & 1) == 0 ? - size : size), anchor);
			break;
		case MOVE_HORIZONTALLY:
			font.paint(g, n, x + ((tick & 1) == 0 ? - size : size), y, anchor);
			break;
		}
		tick++;
	}
	
	/**
	 *	Resets this effect to its start values.
	 */
	public void reset() {
		tick = 0;
		size = 0;
		inc  = false;
	}
	
	/**
	 *	Updates this effect ready to draw the next glyph.
	 */
	public void cycle() {
		tick = 0;
		if (delay > 0) {
			delay--;
		} else {
			delay = time;
			if (inc) {
				size++;
				if (size > 0) {
					inc = !inc;
				}
			} else {
				size--;
				if (size < 0) {
					inc = !inc;
				}
			}
		}
	}
}