package numfum.j2me.text;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.jsr.generic.Positionable;
import numfum.j2me.text.effect.TextEffect;

/**
 *	A scrolling menu built from a grid of icons.
 */
public final class IconMenu extends ScrollingList {
	/**
	 *	Number of cols in the icon grid.
	 */
	private final int cols;
	
	/**
	 *	Width allotted to each icon.
	 */
	private final int iconW;
	
	/**
	 *	Height allotted to each icon.
	 */
	private final int iconH;
	
	/**
	 *	Width of the gap between icons.
	 */
	private final int gapW;
	
	/**
	 *	Whether scrolling wraps horizontally.
	 */
	private final boolean wrapH;
	
	/**
	 *	Indices of all the available icons. A negative value indicates no icon
	 *	is to be shown at this grid position.
	 */
	private final int[][] grid;
	
	/**
	 *	Indices of the icons overlaying the normal grid icons. A negative
	 *	value indicates no overlay is to be shown at this grid position. Used
	 *	to show a extra data about each icon, such as stats or whether it is
	 *	available.
	 */
	private final int[][] over;
	
	/**
	 *	Value associated with each icon. Besides returning the selected index
	 *	an icon can have an arbitrary value assigned to it.
	 */
	private final int[][] value;
	
	/**
	 *	Current selected icon's column,
	 */
	private int selectedX = 0;
	
	/**
	 *	Current selected icon's row,
	 */
	private int selectedY = 0;
	
	/**
	 *	BitmapFont index of glyph used for highlighting the chosen option.
	 */
	private int highlightIdx;
	
	/**
	 *	Effect applied to the highlight icon placed over the current selection.
	 */
	private TextEffect highlightFX = null;
	
	/**
	 *	Creates an icon menu at the specified size.
	 *
	 *	@param font bitmap font with which to draw the text
	 *	@param cols icon grid columns
	 *	@param rows icon grid rows
	 *	@param iconW width of each grid cell
	 *	@param iconH height of each grid cell
	 *	@param gapW width of the gap between icons
	 *	@param gapH height of the gap between icons
	 *	@param visibleRows number of icon rows showing at once
	 *	@param wrapH whether horizontal scrolling wraps around
	 *	@param wrapH whether vertical scrolling wraps around
	 */
	public IconMenu(BitmapFont font, int cols, int rows, int iconW, int iconH, int gapW, int gapH, int visibleRows, boolean wrapH, boolean wrapV) {
		super(font, rows, visibleRows, calcViewHeight(cols, iconW, gapW), calcViewHeight(visibleRows, iconH, gapH), wrapV, gapH);
		
		this.cols  = cols;
		this.iconW = iconW;
		this.iconH = iconH;
		this.gapW  = gapW;
		this.wrapH = wrapH;
		grid  = new int[rows][cols];
		over  = new int[rows][cols];
		value = new int[rows][cols];
		highlightIdx = 0;
		clear();
	}
	
	/**
	 *	Creates an icon menu at the specified size.
	 *
	 *	@param font bitmap font with which to draw the text
	 *	@param cols icon grid columns
	 *	@param rows icon grid rows
	 *	@param iconW width of each grid cell
	 *	@param iconH height of each grid cell
	 *	@param visibleRows number of icon rows showing at once
	 */
	public IconMenu(BitmapFont font, int cols, int rows, int iconW, int iconH, int visibleRows) {
		this(font, cols, rows, iconW, iconH, 0, 0, visibleRows, false, false);
	}
	
	/**
	 *	Clears the current set of icons in the menu,
	 */
	public void clear() {
		for (int gridY = 0; gridY < rows; gridY++) {
			for (int gridX = 0; gridX < cols; gridX++) {
				grid[gridY][gridX] = -1;
				over[gridY][gridX] = -1;
			}
		}
		usedRows = 0;
	}
	
	/**
	 *	Resets the menu to the first selection.
	 */
	public void reset() {
		selectedX = 0;
		selectedY = 0;
		for (int n = 0; n < cols; n++) {
			if (grid[0][n] >= 0) {
				selectedX = n;
				break;
			}
		}
		super.reset();
	}
	
	/**
	 *	Sets the char used to highlight the current selection.
	 */
	public void setHighlight(char icon) {
		highlightIdx = font.getIndex(icon);
	}
	
	/**
	 *	Sets the effects for highlighting the selection and the scroll arrows.
	 */
	public void setEffect(TextEffect highlightFX, TextEffect moreArrowFX) {
		this.highlightFX = highlightFX;
		this.moreArrowFX = moreArrowFX;
	}
	
	/**
	 *	Sets the menu contents to the given text (where each icon in the grid
	 *	is represented by a letter). The value assigned to each icon is its
	 *	index.
	 */
	public void set(String icons) {
		set(icons.toCharArray());
	}
	
	/**
	 *	Sets the menu contents to the given text (where each icon in the grid
	 *	is represented by a letter). The value assigned to each icon is its
	 *	index.
	 */
	public void set(char[] icon) {
		set(icon, 0, icon.length);
	}
	
	/**
	 *	Sets the menu contents to the given text (where each icon in the grid
	 *	is represented by a letter). The value assigned to each icon is its
	 *	index.
	 */
	public void set(char[] icon, int offset, int length) {
		for (int n = 0, i = offset; n < length; n++, i++) {
			set(n, icon[i], n);
		}
	}
	
	/**
	 *	Sets an individual icon in the grid.
	 *
	 *	@param index icon's index counting from the top left
	 *	@param icon glyph in the icon's bitmap font
	 *	@param selVal value associated with the icon
	 */
	public void set(int index, char icon, int selVal) {
		set(index % cols, index / cols, icon, selVal);
	}
	
	/**
	 *	Sets an individual icon in the grid.
	 *
	 *	@param col icon column
	 *	@param row icon row
	 *	@param icon glyph in the icon's bitmap font
	 *	@param selVal value associated with the icon
	 */
	public void set(int col, int row, char icon, int selVal) {
		grid[row][col] = font.getIndex(icon);
		if (row >= usedRows) {
			usedRows = row + 1;
		}
		value[row][col] = selVal;
	}
	
	/**
	 *	Sets the value assigned to an icon.
	 */
	public IconMenu setValue(int index, int selVal) {
		value[index / cols][index % cols] = selVal;
		return this;
	}
	
	/**
	 *	Returns the index of the currently selected icon.
	 */
	public int getSelected() {
		return value[selectedY][selectedX];
	}
	
	/**
	 *	Sets the current selection using its index.
	 */
	public IconMenu setSelected(int index) {
		selectedX = index % cols;
		selectedY = index / cols;
		return this;
	}
	
	/**
	 *	Returns the assigned value for a given index. If more than one icon
	 *	has the same value then the first one is returned.
	 *
	 *	@return an icon's value given an index, or -1 if none is found
	 */
	public int getIndexFromValue(int val) {
		for (int gridY = 0; gridY < rows; gridY++) {
			for (int gridX = 0; gridX < cols; gridX++) {
				if (value[gridY][gridX] == val) {
					return gridY * cols + gridX;
				}
			}
		}
		return -1;
	}
	
	/**
	 *	Sets the current selection given its assigned value. If more than one
	 *	icon has the same value then the first one is selected.
	 */
	public IconMenu setSelectedByValue(int val) {
		int index = getIndexFromValue(val);
		if (index > -1) {
			setSelected(index);
			if (usedRows > visibleRows && selectedY - visibleRows + 1 > showingFrom) {
				showingFrom = selectedY - visibleRows + 1;
			}
			updateList();
		}
		return this;
	}
	
	/**
	 *	Clears any icons overlaid on the grid.
	 *
	 *	@see #setOverlayByValue
	 */
	public IconMenu clearOverlay() {
		for (int gridY = 0; gridY < rows; gridY++) {
			for (int gridX = 0; gridX < cols; gridX++) {
				over[gridY][gridX] = -1;
			}
		}
		return this;
	}
	
	/**
	 *	Overlays another icon on top of the grid.
	 *
	 *	@param val assigned value of the icon to draw over
	 *	@param icon glyph in the icon's bitmap font
	 */
	public IconMenu setOverlayByValue(int val, char icon) {
		for (int gridY = 0; gridY < rows; gridY++) {
			for (int gridX = 0; gridX < cols; gridX++) {
				if (value[gridY][gridX] == val) {
					over[gridY][gridX] = font.getIndex(icon);
					break;
				}
			}
		}
		return this;
	}
	
	/**
	 *	Handles keyboard input.
	 *
	 *	@return whether a key was pressed
	 */
	protected boolean pressImpl(boolean u, boolean d, boolean l, boolean r) {
		if (u || d) {
			selectedY = defaultUpDownAction(u, d, selectedY);
			
			int[] line = grid[selectedY];
			int   test = selectedX;
			while (test > 0 && line[test] < 0) {
				test--;
			}
			while (test < cols - 1 && line[test] < 0) {
				test++;
			}
			if (line[test] < 0) {
				pressImpl(u, d, l, r);
			} else {
				selectedX = test;
			}
		}
		if (l) {
			int testX = selectedX;
			if (testX > 0 || wrapH) {
				testX--;
				if (testX < 0) {
					testX = cols - 1;
				}
			}
			for (int n = 0; n <= cols; n++) {
				if (grid[selectedY][testX] < 0) {
					testX--;
					if (testX < 0) {
						if (wrapH) {
							testX = cols - 1;
						} else {
							testX = selectedX;
						}
					}
				} else {
					break;
				}
			}
			selectedX = testX;
		}
		if (r) {
			int testX = selectedX;
			if (testX < cols - 1 || wrapH) {
				testX++;
				if (testX >= cols) {
					testX = 0;
				}
			}
			for (int n = 0; n <= cols; n++) {
				if (grid[selectedY][testX] < 0) {
					testX++;
					if (testX >= cols) {
						if (wrapH) {
							testX = 0;
						} else {
							testX = selectedX;
						}
					}
				} else {
					break;
				}
			}
			selectedX = testX;
		}
		updateList();
		return l || r || u || d;
	}
	
	/**
	 *	Handles mouse or pointer input.
	 */
	protected boolean clickImpl(int x, int y, boolean allowOwnActions) {
		int stepW = iconW + gapW;
		int stepH = iconH + gapH;
		for (int gridY = showingFrom, drawY = transY - iconH; gridY < showingFrom + visibleRows; gridY++, drawY += stepH) {
			for (int gridX = 0, drawX = transX; gridX < cols; gridX++, drawX += stepW) {
				if (x >= drawX && x < drawX + stepW && y >= drawY && y < drawY + stepH && grid[gridY][gridX] >= 0) {
					if (allowOwnActions) {
						setSelected(gridY * cols + gridX);
					}
					return true;
				}
			}
		}
		if (allowOwnActions) {
			if (y < transY + (visibleRows / 2) * stepH) {
				pressImpl(true, false, false, false);
			} else {
				pressImpl(false, true, false, false);
			}
		}
		return false;
	}
	
	/**
	 *	Updates an animations.
	 */
	public void cycle() {
		super.cycle();
		if (highlightFX != null) {
			highlightFX.cycle();
		}
	}
	
	/**
	 *	Draws a <code>Positionable</code> at a given icon position.
	 *
	 *	@param item what to draw
	 *	@param index which icon position (from the top left)
	 *	@param anchor <code>Graphics</code> anchor to draw from
	 */
	public boolean putItemAt(Positionable item, int index, int anchor) {
		int chosenY = index / cols;
		if (chosenY < showingFrom || chosenY >= showingFrom + visibleRows) {
			return false;
		}
		int chosenX = index % cols;
		
		item.setPosition(transX + chosenX * (iconW + gapW), transY - iconH + (chosenY - showingFrom) * (iconH + gapH), anchor);
		
		return true;
	}
	
	/**
	 *	Draws the menu and any associated items.
	 */
	public void paint(Graphics g, int offsetX, int offsetY) {
		int highX = 0;
		int highY = 0;
		int drawY = offsetY + transY;
		for (int gridY = showingFrom; gridY < showingFrom + visibleRows; gridY++) {
			for (int gridX = 0, drawX = offsetX + transX; gridX < cols; gridX++, drawX += iconW + gapW) {
				if (grid[gridY][gridX] >= 0) {
					font.paint(g, grid[gridY][gridX], drawX, drawY);
				}
				if (over[gridY][gridX] >= 0) {
					font.paint(g, over[gridY][gridX], drawX, drawY);
				}
				if (selectedY == gridY && selectedX == gridX) {
					highX = drawX;
					highY = drawY;
				}
			}
			drawY += iconH + gapH;
		}
		
		super.paint(g, offsetX, offsetY);
		
		if (highlightFX != null) {
			highlightFX.paint(font, g, highlightIdx, highX, highY);
		} else {
			font.paint(g, highlightIdx, highX, highY);
		}
	}
	
	/**
	 *	Creates an icon menu to fill the specified space.
	 */
	public static IconMenu createIconMenu(BitmapFont font, int cols, int rows, char highlight, int viewW, int viewH) {
		int iconW = font.getW(highlight);
		int iconH = font.height;
		
		int rawW = cols * iconW;
		int gapW = 0;
		if (viewW > rawW && cols > 1) {
			gapW = (viewW - rawW) / (cols + 1);
		}
		
		int rawH = rows * iconH;
		int visibleRows = rows;
		if (viewH < rawH) {
			visibleRows = viewH / iconH;
			rawH = visibleRows * iconH;
		}
		
		int gapH = 0;
		if (viewH > rawH && visibleRows > 1) {
			gapH = (viewH - rawH) / (visibleRows + 1);
		}
		
		IconMenu menu = new IconMenu(font, cols, rows, iconW, iconH, gapW, gapH, visibleRows, false, false);
		menu.setHighlight(highlight);
		return menu;
	}
}