package numfum.j2me.text;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.jsr.generic.Positionable;
import numfum.j2me.text.effect.TextEffect;

/**
 *	Represents a line of text drawn with a bitmap font. To speed up the
 *	drawing each of the glyphs is determined from the text, along with its
 *	on-screen position. Most of the methods return <code>this</code> in order
 *	to chain calls.
 */
public final class Line implements Positionable {
	/**
	 *	A debug option to draw the bounding boxes for lines.
	 */
	public static final boolean DRAW_BOUNDING_BOX = false;
	
	/**
	 *	A debug option to dump missing glyphs to <code>stdout</code>.
	 */
	public static final boolean FLAG_MISSING_GLYPHS = false;
	
	/**
	 *	Bitmap font with which this line is drawn.
	 */
	final BitmapFont font;
	
	/**
	 *	Maximum number of glyph 'slots' available in this line.
	 */
	final int size;
	
	/**
	 *	Index of each glyph in the font.
	 */
	final short[] drawN;
	
	/**
	 *	Offset from which each glyph is drawn.
	 */
	final short[] drawX;
	
	/**
	 *	How many visible characters are in this line.
	 */
	int used = 0;
	
	/**
	 *	Total width in pixels of this line. The width calculation doesn't take
	 *	into account pixels outside of the glyph margins.
	 */
	int totalW = 0;
	
	/**
	 *	The text effect applied to this line.
	 */
	TextEffect effect = null;
	
	/**
	 *	X-axis position where this line is drawn from.
	 */
	int x;
	
	/**
	 *	Y-axis position where this line is drawn from.
	 */
	int y;
	
	/**
	 *	A char array used for conveniently setting a line without having to
	 *	create a new temporary array.
	 */
	private static final char[] CHAR_BUILDER = new char[8];
	
	/**
	 *	Creates a new line of the specified number of chars.
	 */
	public Line(BitmapFont font, int size) {
		this.font = font;
		this.size = size;
		drawN = new short[size];
		drawX = new short[size];
	}
	
	/**
	 * Returns the <code>BitmapFont</code> this line has associated with it.
	 * 
	 * @return <code>BitmapFont</code> used by this line
	 */
	public BitmapFont getFont() {
		return font;
	}
	
	/**
	 *	Sets the content of this line.
	 */
	public Line set(String text) {
		return set(text.toCharArray());
	}
	
	/**
	 *	Sets the content of this line.
	 */
	public Line set(char c) {
		used   = 0;
		totalW = 0;
		return append(c);
	}
	
	/**
	 *	Sets the content of this line.
	 *
	 *	@param text array containing source text
	 */
	public Line set(char[] text) {
		return set(text, 0, text.length);
	}
	
	/**
	 *	Sets the content of this line.
	 *
	 *	@param text array containing source text
	 *	@param tab position in pixels of each tab (for formatting)
	 */
	public Line set(char[] text, int[] tab) {
		return set(text, 0, text.length, tab);
	}
	
	/**
	 *	Sets the content of this line.
	 *
	 *	@param text array containing source text
	 *	@param offset where the text starts in the array
	 *	@param length how many chars are used
	 */
	public Line set(char[] text, int offset, int length) {
		used   = 0;
		totalW = 0;
		return append(text, offset, length);
	}
	
	/**
	 *	Sets the content of this line.
	 *
	 *	@param text array containing source text
	 *	@param offset where the text starts in the array
	 *	@param length how many chars are used
	 *	@param tab position in pixels of each tab (for formatting)
	 */
	public Line set(char[] text, int offset, int length, int[] tab) {
		if (tab == null) {
			return set(text, offset, length);
		}
		
		used   = 0;
		totalW = 0;
		
		int tabN = 0;
		int last = offset;
		for (int n = 0, i = offset; n < length; n++, i++) {
			if (text[i] == '\t') {
				totalW = tab[tabN++];
				append(text, last, i - last);
				last = i + 1;
			}
		}
		if (last < offset + length) {
			totalW = tab[tabN++];
			append(text, last, offset + length - last);
		}
		return this;
	}
	
	/**
	 *	Appends content to this line.
	 *	
	 *	@param c character to append
	 */
	public Line append(char c) {
		synchronized (CHAR_BUILDER) {
			CHAR_BUILDER[0] = c;
			append(CHAR_BUILDER, 0, 1);
		}
		return this;
	}
	
	/**
	 *	Appends content to this line.
	 *
	 *	@param text array containing source text
	 */
	public Line append(char[] text) {
		return append(text, 0, text.length);
	}
	
	/**
	 *	Appends content to this line.
	 *
	 *	@param text array containing source text
	 *	@param offset where the text starts in the array
	 *	@param length how many chars are used
	 */
	public Line append(char[] text, int offset, int length) {
		if (used + length > size) {
			length = size - used;
		}
		for (int n = 0, i = offset; n < length; n++, i++) {
			int idx = font.getIndex(text[i]);
			if (idx != -1) {
				if (font.hasContent(idx)) {
					drawN[used]   = (short) idx;
					drawX[used++] = (short) totalW;
				}
				totalW += font.advance[idx];
			} else {
				if (FLAG_MISSING_GLYPHS) {
					System.out.println("Missing glyph: " + text[i] + " (" + Integer.toString(text[i], 16) + ")");
				}
			}
		}
		return this;
	}
	
	/**
	 *	Sets the content of this line to a numeric value.
	 */
	public synchronized Line set(int val) {
		synchronized (CHAR_BUILDER) {
			used = Math.min(countDigits(val), CHAR_BUILDER.length);
			if (val >= 0) {
				for (int n = used - 1; n >= 0; n--) {
					CHAR_BUILDER[n] = (char) (val % 10 + '0');
					val /= 10;
				}
			} else {
				if (used < CHAR_BUILDER.length) {
					used++;
				}
				for (int n = used - 1; n > 0; n--) {
					CHAR_BUILDER[n] = (char) ('0' - val % 10);
					val /= 10;
				}
				CHAR_BUILDER[0] = '-';
			}
			set(CHAR_BUILDER, 0, used);
		}
		return this;
	}
	
	/**
	 *	Updates the glyphs in an existing line. Used only when the indices are
	 *	known in advance, and a faster method than set() is required. None of
	 *	the font metrics is recalculated making this useful for animating
	 *	icons or quickly changing score text.
	 */
	public Line setGlyphs(int[] text, int offset, int length) {
		for (int n = 0, i = offset; n < length; n++, i++) {
			int idx = text[i];
			if (idx != -1) {
				drawN[n] = (short) idx;
			}
		}
		return this;
	}
	
	/**
	 *	Returns the glyphs that make up this line.
	 *
	 *	@see #setGlyphs
	 */
	public Line getGlyphs(int[] text, int offset, int length) {
		for (int n = 0, i = offset; n < length; n++, i++) {
			text[i] = drawN[n];
		}
		return this;
	}
	
	/**
	 *	Empties this line.
	 */
	public Line clear() {
		used   = 0;
		totalW = 0;
		effect = null;
		return this;
	}
	
	/**
	 *	Associates a text efect with this line.
	 */
	public Line setTextEffect(TextEffect effect) {
		this.effect = effect;
		return this;
	}
	
	/**
	 * Returns the calculated x-position.
	 * 
	 * @return the calculated x-position
	 * @see #setPosition(int, int, int)
	 */
	public int getX() {
		return x;
	}
	
	/**
	 * Returns the calculated y-position.
	 * 
	 * @return the calculated y-position
	 * @see #setPosition(int, int, int)
	 */
	public int getY() {
		return y;
	}
	
	/**
	 *	Returns the line width.
	 */
	public int getW() {
		return totalW;
	}
	
	/**
	 *	Returns the line height.
	 */
	public int getH() {
		return font.height;
	}
	
	/**
	 *	Sets the line's position using one of the anchors from
	 *	<code>Graphics</code>.
	 */
	public Positionable setPosition(int x, int y, int anchor) {
		if ((anchor & Graphics.LEFT) == 0) {
			if ((anchor & Graphics.HCENTER) != 0) {
				/*
				 *	note: the +1 here was added to round up - it just looks
				 *	better, more centred.
				 */
				x -= (totalW + 1) / 2;
			} else if ((anchor & Graphics.RIGHT) != 0) {
				x -= totalW;
			}
		}
		if ((anchor & Graphics.BASELINE) == 0) {
			if ((anchor & Graphics.TOP) != 0) {
				y -= font.ascent;
			} else if ((anchor & Graphics.BOTTOM) != 0) {
				y -= font.descent;
			} else if ((anchor & Graphics.VCENTER) != 0) {
				y += font.height / 2;
			}
		}
		this.x = x;
		this.y = y;
		return this;
	}
	
	/**
	 *	Moves the draw position by the specified pixels.
	 */
	public Line nudge(int x, int y) {
		this.x += x;
		this.y += y;
		return this;
	}
	
	/**
	 *	Returns whether a mouse or pointer click (or any coordinate) is
	 *	within the bounds of this line. No other action is performed (unlike
	 *	clicking a menu, for example).
	 */
	public boolean click(int x, int y) {
		return x >= this.x && x < this.x + totalW && y >= this.y + font.ascent && y < this.y + font.descent;
	}
	
	/**
	 *	Draws the line at the given offset.
	 */
	public void paint(Graphics g, int offsetX, int offsetY) {
		offsetX += x;
		offsetY += y;
		if (effect != null) {
			for (int n = 0; n < used; n++) {
				effect.paint(font, g, drawN[n], offsetX + drawX[n], offsetY);
			}
		} else {
			for (int n = 0; n < used; n++) {
				font.paint(g, drawN[n], offsetX + drawX[n], offsetY);
			}
		}
		if (DRAW_BOUNDING_BOX) {
			g.drawRect(offsetX, offsetY + font.ascent, getW() - 1, getH() - 1);
		}
	}
	
	public String toString() {
		return getClass().getName() + " [used: " + used + "]";
	}
	
	/**
	 *	Creates a line with enough chars allocated to fill the specified
	 *	number of pixels.
	 */
	public static Line createLine(BitmapFont font, int pixels) {
		int minAdvance = font.minAdvance;
		if (minAdvance < 1) {
			minAdvance = 1;
		}
		return new Line(font, pixels / minAdvance);
	}
	
	/**
	 *	Creates a line containing the specified string.
	 */
	public static Line createLine(BitmapFont font, String text) {
		Line line = new Line(font, text.length());
		line.set(text.toCharArray());
		return line;
	}
	
	/**
	 *	Creates a line containing the specified text.
	 */
	public static Line createLine(BitmapFont font, char[] text) {
		Line line = new Line(font, text.length);
		line.set(text);
		return line;
	}
	
	/**
	 *	Creates an array of lines with enough chars allocated to fill the
	 *	specified number of pixels.
	 */
	public static Line[] createLines(BitmapFont font, int pixels, int numLines) {
		Line[] line = new Line[numLines];
		for (int n = 0; n < numLines; n++) {
			line[n] = createLine(font, pixels);
		}
		return line;
	}
	
	/**
	 *	Counts the number of decimal digits in a given integer. Doesn't take
	 *  into account any sign.
	 */
	public static int countDigits(int val) {
		int digits = 0;
		do {
			val /= 10;
			digits++;
		} while (val != 0);
		return digits;
	}
}