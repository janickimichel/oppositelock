package numfum.j2me.text;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.jsr.generic.Positionable;
import numfum.j2me.text.effect.TextEffect;

/**
 *	Base class for (optionally) scrolling multi-line text display.
 */
public abstract class ScrollingList implements Positionable {
	/**
	 *	Unicode value of the up arrow.
	 */
	public static final char ARROW_ABOVE = '\u2191';
	
	/**
	 *	Unicode value of the down arrow.
	 */
	public static final char ARROW_BELOW = '\u2193';
	
	/**
	 *	Number of ticks between scolling each line.
	 */
	public static final int KEY_DELAY = 3;
	
	/**
	 *	Font used to draw the list.
	 */
	protected final BitmapFont font;
	
	/**
	 *	Width of the theoretical panel where the list is drawn.
	 */
	protected final int viewW;
	
	/**
	 *	Height of the theoretical panel where the list is drawn.
	 */
	protected final int viewH;
	
	/**
	 *	Maximum number of rows in the list.
	 */
	protected final int rows;
	
	/**
	 *	Number of rows possibly showing at any one time.
	 */
	protected final int visibleRows;
	
	/**
	 *	Number of rows used out of the maximum available.
	 */
	protected int usedRows;
	
	/**
	 *	Whether scrolling wraps back to the beginning.
	 */
	protected final boolean wrapV;
	
	/**
	 *	Height of the gap between rows.
	 */
	protected final int gapH;
	
	/**
	 *	Which grid row the icon display starts from.
	 */
	protected int showingFrom = 0;
	
	/**
	 *	Whether to display the arrow denoting more menu options are available
	 *	above the current showing set.
	 */
	protected boolean moreAbove = false;
	
	/**
	 *	Whether to display the arrow denoting more menu options are available
	 *	below the current showing set.
	 */
	protected boolean moreBelow = false;
	
	/**
	 *	X-axis position (before the anchor calculation).
	 */
	protected int x = 0;
	
	/**
	 *	Y-axis position (before the anchor calculation).
	 */
	protected int y = 0;
	
	/**
	 *	X-axis position (calculated from the size and anchor).
	 */
	protected int transX = 0;
	
	/**
	 *	X-axis position (calculated from the size and anchor).
	 */
	protected int transY = 0;
	
	/**
	 *	Anchor used for the drawing transforms.
	 */
	protected int anchor = Graphics.LEFT | Graphics.TOP;
	
	/**
	 *	The x-axis position of the up and down arrows taking into account the
	 *	anchor point.
	 */
	protected int arrowX;
	
	/**
	 *	Y-axis position of the animated arrow above the text.
	 */
	protected int arrowAboveY = 0;
	
	/**
	 *	Y-axis position of the animated arrow below the text.
	 */
	protected int arrowBelowY = 0;
	
	/**
	 *	BitmapFont index of the up arrow.
	 */
	protected int arrowAboveIdx;
	
	/**
	 *	BitmapFont index of the down arrow.
	 */
	protected int arrowBelowIdx;
	
	/**
	 *	Delay time used to slow down key input.
	 */
	private int delay = 0;
	
	/**
	 *	Effect applied to the arrows indicating further content is available.
	 */
	protected TextEffect moreArrowFX = null;
	
	/**
	 *	Alignment constant to centre the item vertically on the text content.
	 */
	public static final int TEXT_CENTER = 32768;
	
	/**
	 *	Creates a new <code>ScrollingList</code> to fill the specified size.
	 *	This is the usual constructor where the number of visible rows is to
	 *	be arrived at from the font size.
	 *
	 *	@param font bitmap font with which to draw the text
	 *	@param rows maximum number of text rows
	 *	@param viewW width of the visible area (used for anchor calculations)
	 *	@param viewH height of the visible area (used for anchor calculations)
	 *	@param wrapV whether the scrolling list wraps once it reaches the end
	 *	@param gapH spacing between each row
	 */
	protected ScrollingList(BitmapFont font, int rows, int viewW, int viewH, boolean wrapV, int gapH) {
		this(font, rows, calcVisibleRows(viewH, font.height, gapH), viewW, calcViewHeight(calcVisibleRows(viewH, font.height, gapH), font.height, gapH), wrapV, gapH);
	}
	
	/**
	 *	Creates a new <code>ScrollingList</code> to fill the specified size
	 *	and number of lines. This constructor would be used where the number
	 *	of visible rows is known in advance and the view height has been
	 *	calculated from that.
	 *
	 *	@param font bitmap font with which to draw the text
	 *	@param rows maximum number of text rows
	 *	@param visibleRows number of rows visible at any one time
	 *	@param viewW width of the visible area (used for anchor calculations)
	 *	@param viewH height of the visible area (used for anchor calculations)
	 *	@param wrapV whether the scrolling list wraps once it reaches the end
	 */
	protected ScrollingList(BitmapFont font, int rows, int visibleRows, int viewW, int viewH, boolean wrapV, int gapH) {
		this.font = font;
		this.rows = rows;
		
		if (visibleRows < rows) {
			this.visibleRows = visibleRows;
		} else {
			this.visibleRows = rows;
		}
		
		this.viewW = viewW;
		this.viewH = viewH;
		
		this.wrapV = wrapV;
		this.gapH  = gapH;
		
		setArrows(ARROW_ABOVE, ARROW_BELOW);
	}
	
	/**
	 *	Empties the content.
	 */
	public void clear() {
		usedRows = 0;
	}
	
	/**
	 *	Resets the scrolling to the top.
	 */
	public void reset() {
		delay = 0;
		showingFrom = 0;
		
		updateList();
	}
	
	/**
	 *	Sets the chars used for the top and bottom arrows.
	 */
	public void setArrows(char arrowAbove, char arrowBelow) {
		arrowAboveIdx = font.getIndex(arrowAbove);
		arrowBelowIdx = font.getIndex(arrowBelow);
	}
	
	/**
	 *	Sets the text effect used by the arrows to show more content is
	 *	available.
	 */
	public void setEffect(TextEffect moreArrowFX) {
		this.moreArrowFX = moreArrowFX;
	}
	
	/**
	 *	Returns the list width.
	 */
	public int getW() {
		return viewW;
	}
	
	/**
	 *	Returns the list height.
	 */
	public int getH() {
		return viewH;
	}
	
	/**
	 *	Returns the number of used rows in the list.
	 */
	public int getUsedRows() {
		return usedRows;
	}
	
	/**
	 *	Sets the list's position using one of the anchors from
	 *	<code>Graphics</code>.
	 */
	public Positionable setPosition(int x, int y, int anchor) {
		this.x = x;
		this.y = y;
		this.anchor = anchor;
		
		transX = x;
		transY = y;
		
		if ((anchor & Graphics.LEFT) == 0) {
			if ((anchor & Graphics.HCENTER) != 0) {
				transX -= viewW / 2;
			} else if ((anchor & Graphics.RIGHT) != 0) {
				transX -= viewW;
			}
		}
		
		if ((anchor & Graphics.BASELINE) == 0) {
			if ((anchor & Graphics.TOP) != 0) {
				transY -= font.ascent;
			} else if ((anchor & Graphics.BOTTOM) != 0) {
				transY -= viewH - font.descent;
			} else if ((anchor & Graphics.VCENTER) != 0) {
				transY -= viewH / 2 + font.ascent;
			} else if ((anchor & TEXT_CENTER) != 0) {
				transY -= calcViewHeight(usedRows, font.height, gapH) / 2 + font.ascent;
			}
		}
		
		arrowX = transX + viewW / 2;
		
		arrowAboveY = transY + font.ascent;
		arrowBelowY = transY + viewH;
		
		updateList();
		return this;
	}
	
	/**
	 *	Mouse or pointer click.
	 *
	 *	@param allowOwnActions implementations can act on clicks
	 *	@return whether the item was clicked on
	 */
	public boolean click(int x, int y, boolean allowOwnActions) {
		int w = getW();
		if (allowOwnActions) {
			if (x >= arrowX - 4 && x < arrowX + 4) {
				if (moreAbove && y >= arrowAboveY - 8 && y < arrowAboveY) { // 8 being some magic number...
					press(true, false, false, false);
				} else {
					if (moreBelow && y >= arrowBelowY - 8 && y < arrowBelowY) {
						press(false, true, false, false);
					}
				}
			}
		}
		if (x >= transX && x < transX + w && y >= transY + font.ascent && y < transY + font.ascent + (usedRows * (font.height + gapH))) {
			return clickImpl(x, y, allowOwnActions);
		} else {
			return false;
		}
	}
	
	/**
	 *	Click implementation for subclasses.
	 *
	 *	@return whether the item was clicked on
	 */
	protected abstract boolean clickImpl(int x, int y, boolean allowOwnActions);
	
	/**
	 *	Performs tests after the text has been updated.
	 */
	public void updateList() {
		moreAbove = showingFrom > 0;
		moreBelow = showingFrom + visibleRows < usedRows;
	}
	
	/**
	 *	Default action for the key presses.
	 */
	protected int defaultUpDownAction(boolean u, boolean d, int selected) {
		if (u) {
			if (selected > 0 || wrapV) {
				selected--;
				if (selected < 0) {
					selected = usedRows - 1;
				}
			}
			if (usedRows > visibleRows) {
				if (selected == usedRows - 1) {
					showingFrom = usedRows - visibleRows;
				} else if (selected < showingFrom) {
					showingFrom = selected;
				}
			}
			
		}
		if (d) {
			if (selected < usedRows - 1 || wrapV) {
				selected++;
				if (selected > usedRows - 1) {
					selected = 0;
				}
			}
			if (usedRows > visibleRows) {
				if (selected == 0) {
					showingFrom = 0;
				} else if (selected - visibleRows + 1 > showingFrom) {
					showingFrom = selected - visibleRows + 1;
				}
			}
		}
		return selected;
	}
	
	/**
	 *	Implementation specific key actions.
	 *
	 *	@return whether the keypress was handled or not
	 *	@see #press
	 */
	protected abstract boolean pressImpl(boolean u, boolean d, boolean l, boolean r);
	
	/**
	 *	Handles key presses.
	 *
	 *	@param u whether the up key is pressed
	 *	@param d whether the down key is pressed
	 *	@param l whether the left key is pressed
	 *	@param r whether the right key is pressed
	 */
	public boolean press(boolean u, boolean d, boolean l, boolean r) {
		if (u || d || l || r) {
			if (delay > 0) {
				delay--;
			} else {
				delay = KEY_DELAY;
				return pressImpl(u, d, l, r);
			}
		} else {
			delay = 0;
		}
		return false;
	}
	
	/**
	 *	Updates the text animation (just the arrows in this base class).
	 */
	public void cycle() {
		if (moreArrowFX != null) {
			moreArrowFX.cycle();
		}
	}
	
	/**
	 *	Draws the text at the specified offset.
	 */
	public void paint(Graphics g, int offsetX, int offsetY) {
		if (moreAbove && arrowAboveIdx >= 0) {
			if (moreArrowFX != null) {
				moreArrowFX.paint(font, g, arrowAboveIdx, offsetX + arrowX, offsetY + arrowAboveY, Graphics.BASELINE | Graphics.HCENTER);
			} else {
				font.paint(g, arrowAboveIdx, offsetX + arrowX, offsetY + arrowAboveY, Graphics.BASELINE | Graphics.HCENTER);
			}
		}
		if (moreBelow && arrowBelowIdx >= 0) {
			if (moreArrowFX != null) {
				moreArrowFX.paint(font, g, arrowBelowIdx, offsetX + arrowX, offsetY + arrowBelowY, Graphics.BASELINE | Graphics.HCENTER);
			} else {
				font.paint(g, arrowBelowIdx, offsetX + arrowX, offsetY + arrowBelowY, Graphics.BASELINE | Graphics.HCENTER);
			}
		}
		if (Line.DRAW_BOUNDING_BOX) {
			g.drawRect(transX + offsetX, transY + offsetY + font.ascent, getW() - 1, getH() - 1);
		}
	}
	
	public String toString() {
		return getClass().getName() + " [rows: " + rows + ", usedRows: " + usedRows + "]";
	}
	
	/**
	 *	Calculates the height when using a gap between rows (also works for
	 *	calculating the width for cols).
	 *
	 *	@param rows number of rows (or cols)
	 *	@param fontH height of each row (or width of each col)
	 *	@param gap number of pixels between each row (or col)
	 */
	public static int calcViewHeight(int rows, int fontH, int gap) {
		int calc = rows * fontH;
		if (rows > 1) {
			calc += (rows - 1) * gap;
		}
		return calc;
	}
	
	/**
	 *	Calculates the number of visible rows given a specified view height
	 *	when using a gap between each row.
	 */
	public static int calcVisibleRows(int viewH, int fontH, int gap) {
		return (viewH + gap) / (fontH + gap);
	}
}