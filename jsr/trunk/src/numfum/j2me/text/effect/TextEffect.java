package numfum.j2me.text.effect;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.text.BitmapFont;

/**
 *	Implemented by classes performing effects on text.
 */
public interface TextEffect {
	/**
	 *	Draws a single glyph using this effect.
	 */
	public void paint(BitmapFont font, Graphics g, int n, int x, int y);
	
	/**
	 *	Draws a single glyph using this effect from the specified anchor.
	 */
	public void paint(BitmapFont font, Graphics g, int n, int x, int y, int anchor);
	
	/**
	 *	Resets this effect to its start values.
	 */
	public void reset();
	
	/**
	 *	Updates this effect ready to draw the next glyph.
	 */
	public void cycle();
}