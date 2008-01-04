package numfum.j2me.text.effect;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.text.BitmapFont;

/**
 *	Animates glyphs, offsetting each between specified amounts. The animation
 *	is determined by its speed (in game ticks), the number of offsets per
 *	tick, and its number of frames.
 */
public final class AnimEffect implements TextEffect {
	/**
	 *	Number of ticks between updates.
	 */
	private final int speed;
	
	/**
	 *	Number of glyphs between each frame.
	 */
	private final int shift;
	
	/**
	 *	Upper limit of the glyph offset.
	 */
	private final int limit;
	
	/**
	 *	Offset added to each glyph index.
	 */
	private int offset;
	
	/**
	 *	Number of ticks before the next update
	 */
	private int delay;
	
	/**
	 *	Creates an <code>AnimEffect</code>.
	 *
	 *	@param speed number of ticks between updates
	 *	@param shift number of glyphs between each frame
	 *	@param frames number of frames the animation runs over
	 */
	public AnimEffect(int speed, int shift, int frames) {
		this.speed = speed;
		this.shift = shift;
		limit = shift * frames;
	}
	
	/**
	 *	Draws a single glyph using this effect.
	 */
	public void paint(BitmapFont font, Graphics g, int n, int x, int y) {
		font.paint(g, n + offset, x, y);
	}
	
	/**
	 *	Draws a single glyph using this effect from the specified anchor.
	 */
	public void paint(BitmapFont font, Graphics g, int n, int x, int y, int anchor) {
		font.paint(g, n + offset, x, y, anchor);
	}
	
	/**
	 *	Resets this effect to its start values.
	 */
	public void reset() {
		offset = 0;
		delay  = 0;
	}
	
	/**
	 *	Updates this effect ready to draw the next glyph.
	 */
	public void cycle() {
		delay--;
		if (delay < 0) {
			delay = speed;
			if (offset <  limit) {
				offset += shift;
			} else {
				offset = 0;
			}
		}
	}
}