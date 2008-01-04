package numfum.j2me.text.effect;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.text.BitmapFont;

/**
 *	A simple effect that controls whether a glyph is on or off.
 */
public final class BlinkEffect implements TextEffect {
	/**
	 *	Number of ticks between each state change.
	 */
	private final int time;
	
	/**
	 *	Number of ticks left until the state changes.
	 */
	private int delay = 0;
	
	/**
	 *	Whether a glyph is drawn or not.
	 */
	private boolean showing = true;
	
	/**
	 *	Creates a new effect with the specified duration.
	 */
	public BlinkEffect(int time) {
		this.time = time;
	}
	
	/**
	 *	Draws a single glyph using this effect.
	 */
	public void paint(BitmapFont font, Graphics g, int n, int x, int y) {
		if (showing) {
			font.paint(g, n, x, y);
		}
	}
	
	/**
	 *	Draws a single glyph using this effect from the specified anchor.
	 */
	public void paint(BitmapFont font, Graphics g, int n, int x, int y, int anchor) {
		if (showing) {
			font.paint(g, n, x, y, anchor);
		}
	}
	
	/**
	 *	Resets this effect to its start values.
	 */
	public void reset() {
		delay = 0;
		showing = true;
	}
	
	/**
	 *	Updates this effect ready to draw the next glyph.
	 */
	public void cycle() {
		if (delay > 0) {
			delay--;
		} else {
			delay = time;
			showing = !showing;
		}
	}
}