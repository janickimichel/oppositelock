package numfum.j2me.text;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.text.effect.TextEffect;


/**
 *	A scrolling menu implementation of <code>ScrollingList</code>.
 */
public class ListMenu extends ScrollingList {
	/**
	 *	Default char for the LHS arrow highlighting the current selection.
	 */
	public static final char ARROW_LHS  = '\u21A0';
	
	/**
	 *	Default char for the RHS arrow highlighting the current selection.
	 */
	public static final char ARROW_RHS  = '\u219E';
	
	/**
	 *	The visible lines drawn on screen.
	 */
	private final Line[] line;
	
	/**
	 *	Pixels between each line.
	 */
	private final int gapH;
	
	/**
	 *	Text content broken in to line sized chunks ready.
	 */
	private final char[][] content;
	
	/**
	 *	Offset to where each line starts.
	 */
	private final int[] contentOff;
	
	/**
	 *	Length of each line.
	 */
	private final int[] contentLen;
	
	/**
	 *	Value associated with each menu item. Instead of simply returning the
	 *	selected index a more meaningful value can be assigned to each item.
	 */
	private final int[] value;
	
	/**
	 *	Index of the currently selected item.
	 */
	private int selected;
	
	/**
	 *	X-axis position of the LHS highlighting icon.
	 */
	private int arrowLHSX;
	
	/**
	 *	X-axis position of the RHS highlighting icon.
	 */
	private int arrowRHSX;
	
	/**
	 *	Y-axis position of the two highlighting icons.
	 */
	private int arrowSideY;
	
	/**
	 *	BitmapFont index of the left arrow.
	 */
	private int arrowLIdx;
	
	/**
	 *	BitmapFont index of the right arrow.
	 */
	private int arrowRIdx;
	
	/**
	 *	Effect applied to the highlight the current selection.
	 */
	private TextEffect highlightFX = null;
	
	/**
	 *	Effect applied to the highlight the highlight arrows.
	 */
	private TextEffect highArrowFX = null;
	
	/**
	 *	Creates a new menu to cover a specified area.
	 *
	 *	@param font bitmap font with which to draw the text
	 *	@param rows maximum number menu entries
	 *	@param viewW width of the visible area
	 *	@param viewH height of the visible area
	 *	@param wrapV whether the scrolling list wraps once it reaches the end
	 *	@param gapH spacing between each row
	 */
	public ListMenu(BitmapFont font, int rows, int viewW, int viewH, boolean wrapV, int gapH) {
		super(font, rows, viewW, viewH, wrapV, gapH);
		this.gapH = gapH;
		
		line = Line.createLines(font, viewW, visibleRows);
		
		content = new char[rows][];
		contentOff = new int[rows];
		contentLen = new int[rows];
		
		value = new int[rows];
		
		setArrows(ARROW_ABOVE, ARROW_BELOW, ARROW_LHS, ARROW_RHS);
	}
	
	/**
	 *	Removes all of the menu entries.
	 */
	public void clear() {
		for (int n = 0; n < usedRows; n++) {
			content[n] = null;
		}
		super.clear();
	}
	
	/**
	 *	Resets the menu to its first option.
	 */
	public void reset() {
		selected = 0;
		for (int n = 0; n < visibleRows; n++) {
			line[n].setTextEffect(null);
		}
		line[0].setTextEffect(highlightFX);
		
		showingFrom = 0;
		
		updateList();
	}
	
	/**
	 *	Adds a new menu item with its index its associated return value.
	 */
	public ListMenu add(String text) {
		return add(text.toCharArray(), usedRows);
	}
	
	/**
	 *	Adds a new menu item with the specified associated return value.
	 */
	public ListMenu add(String text, int selVal) {
		return add(text.toCharArray(), selVal);
	}
	
	/**
	 *	Adds a new menu item with its index its associated return value.
	 */
	public ListMenu add(char[] text) {
		return add(text, 0, text.length, usedRows);
	}
	
	/**
	 *	Adds a new menu item with the specified associated return value.
	 */
	public ListMenu add(char[] text, int selVal) {
		return add(text, 0, text.length, selVal);
	}
	
	/**
	 *	Adds a new menu item with the specified associated return value.
	 */
	public ListMenu add(char[] text, int offset, int length, int selVal) {
		if (usedRows < rows) {
			content[usedRows] = text;
			contentOff[usedRows] = offset;
			contentLen[usedRows] = length;
			value[usedRows] = selVal;
			usedRows++;
		}
		return this;
	}
	
	/**
	 *	Returns the value associated with the selected menu item.
	 */
	public int getSelected() {
		return value[selected];
	}
	
	public void setSelected(int selVal) {
		setSelected(selVal, true);
	}
	
	/**
	 *	Sets the selected menu item using its associated value.
	 */
	public void setSelected(int selVal, boolean update) {
		line[selected - showingFrom].setTextEffect(null);
		for (int n = 0; n < usedRows; n++) {
			if (value[n] == selVal) {
				selected = n;
			}
		}
		/*
		 *	TODO: write this properly, perhaps not in the night...! How does
		 *	handle moving back up the list? I'm assuming this is fixed but
		 *	it's not been tested in all cases.
		 */
		if (update) {
			showingFrom = 0;
			while (selected > showingFrom && showingFrom + visibleRows < usedRows) {
				showingFrom++;
			}
		}
		updateList();
		line[selected - showingFrom].setTextEffect(highlightFX);
	}
	
	/**
	 *	Sets the chars used for the top and bottom arrows, and the arrows for
	 *	highlighting the current selection.
	 */
	public void setArrows(char arrowAbove, char arrowBelow, char arrowL, char arrowR) {
		super.setArrows(arrowAbove, arrowBelow);
		arrowLIdx = font.getIndex(arrowL);
		arrowRIdx = font.getIndex(arrowR);
	}
	
	/**
	 *	Sets the text effect for the current selection and all four arrow
	 *	icons (above and below, for scrolling, and at the sides for
	 *	higlighting).
	 */
	public void setEffect(TextEffect highlightFX, TextEffect highArrowFX, TextEffect moreArrowFX) {
		super.setEffect(moreArrowFX);
		this.highlightFX = highlightFX;
		this.highArrowFX = highArrowFX;
	}
	
	/**
	 *	Performs the updating of the <code>Line</code>s that back this viewer,
	 *	and position of the arrow icons.
	 */
	public void updateList() {
		super.updateList();
		
		for (int n = 0, i = showingFrom; n < visibleRows; n++, i++) {
			if (content[i] != null) {
				line[n].set(content[i], contentOff[i], contentLen[i]);
				line[n].setPosition(x, transY + n * (font.height + gapH), anchor | Graphics.BASELINE);
			}
		}
		
		int arrowLine = selected - showingFrom;
		
		arrowLHSX = 0;
		arrowRHSX = line[arrowLine].totalW;
		if ((anchor & Graphics.LEFT) == 0) {
			if ((anchor & Graphics.HCENTER) != 0) {
				arrowRHSX /= 2;
				arrowLHSX  = -arrowRHSX;
			} else if ((anchor & Graphics.RIGHT) != 0) {
				arrowLHSX  = -arrowRHSX;
				arrowRHSX  = 0;
			}
		}
		arrowLHSX += x - 4;
		arrowRHSX += x + 4;
		
		arrowSideY = transY + arrowLine * (font.height + gapH);
	}
	
	/**
	 *	Handles menu selection.
	 *
	 *	TODO: defaultUpDownAction() could be replaced with code that uses
	 *	setSelected(). It'll be simpler and smaller.
	 */
	protected boolean pressImpl(boolean u, boolean d, boolean l, boolean r) {
		if (u || d) {
			line[selected - showingFrom].setTextEffect(null);
			selected = defaultUpDownAction(u, d, selected);
			updateList();
			line[selected - showingFrom].setTextEffect(highlightFX);
			
			return true;
		}
		return false;
	}
	
	protected boolean clickImpl(int x, int y, boolean allowOwnActions) {
		for (int n = (usedRows < visibleRows ? usedRows : visibleRows) - 1; n >= 0; n--) {
			if (line[n].click(x, y)) {
				if (allowOwnActions) {
					setSelected(value[n + showingFrom], false);
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 *	Animates the item selection.
	 */
	public void cycle() {
		super.cycle();
		if (highlightFX != null) {
			highlightFX.cycle();
		}
		if (highArrowFX != null) {
			highArrowFX.cycle();
		}
	}
	
	/**
	 *	Draws the menu and arrow icons.
	 */
	public void paint(Graphics g, int offsetX, int offsetY) {
		super.paint(g, offsetX, offsetY);
		for (int n = (usedRows < visibleRows ? usedRows : visibleRows) - 1; n >= 0; n--) {
			line[n].paint(g, offsetX, offsetY);
		}
		if (arrowLIdx >= 0 && arrowRIdx >= 0) {
			if (highArrowFX != null) {
				highArrowFX.paint(font, g, arrowLIdx, offsetX + arrowLHSX, offsetY + arrowSideY, Graphics.BASELINE | Graphics.RIGHT);
				highArrowFX.paint(font, g, arrowRIdx, offsetX + arrowRHSX, offsetY + arrowSideY, Graphics.BASELINE | Graphics.LEFT);
			} else {
				font.paint(g, arrowLIdx, offsetX + arrowLHSX, offsetY + arrowSideY, Graphics.BASELINE | Graphics.RIGHT);
				font.paint(g, arrowRIdx, offsetX + arrowRHSX, offsetY + arrowSideY, Graphics.BASELINE | Graphics.LEFT);
			}
		}
	}
}