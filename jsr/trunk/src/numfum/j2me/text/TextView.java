package numfum.j2me.text;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.text.effect.TextEffect;


/**
 *	A text viewer implementation of <code>ScrollingList</code>.
 */
public class TextView extends ScrollingList {
	/**
	 *	Zero width space character. Used to mark safe, invisible points to
	 *	wrap text.
	 */
	public static final char ZERO_SPACE = '\u200B';
	
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
	 *	Effect associated with each visible line.
	 */
	private final TextEffect[] effect;
	
	/**
	 *	Tab postions in pixels.
	 */
	private int[] tab = null;
	
	/**
	 *	Creates a new text viewer to cover a specified area, scrolling if
	 *	necessary to show the required number of lines.
	 *
	 *	@param font bitmap font with which to draw the text
	 *	@param lines maximum number of text rows
	 *	@param viewW width of the visible area
	 *	@param viewH height of the visible area
	 *	@param wrapV whether the scrolling list wraps once it reaches the end
	 *	@param gapH spacing between each row
	 */
	public TextView(BitmapFont font, int lines, int viewW, int viewH, boolean wrapV, int gapH) {
		super(font, lines, viewW, viewH, wrapV, gapH);
		this.gapH = gapH;
		line = Line.createLines(font, viewW, visibleRows);
		content = new char[lines][];
		contentOff = new int[lines];
		contentLen = new int[lines];
		effect = new TextEffect[lines];
	}
	
	/**
	 *	Creates a new text viewer to cover a specified number of lines.
	 *
	 *	@param font bitmap font with which to draw the text
	 *	@param lines maximum number of text rows
	 *	@param viewW width of the visible area
	 *	@param wrapV whether the scrolling list wraps once it reaches the end
	 *	@param gapH spacing between each row
	 */
	public TextView(BitmapFont font, int lines, int viewW, boolean wrapV, int gapH) {
		this(font, lines, viewW, calcViewHeight(lines, font.height, gapH), wrapV, gapH);
	}
	
	/**
	 *	Removes all of the content.
	 */
	public void clear() {
		for (int n = 0; n < usedRows; n++) {
			content[n] = null;
			effect[n]  = null;
		}
		super.clear();
	}
	
	
	/**
	 *	Sets the tab positions used when laying out the individual lines. This
	 *	is only really useful when the text is aligned to the left.
	 */
	public void setTabs(int[] tab) {
		this.tab = tab;
	}
	
	/**
	 *	Adds a new single line of text.
	 *
	 *	@see #addLine(char[] text, int offset, int length)
	 */
	public int addLine(String text) {
		return addLine(text.toCharArray());
	}
	
	/**
	 *	Adds a new single line of text.
	 *
	 *	@see #addLine(char[] text, int offset, int length)
	 */
	public int addLine(char[] text) {
		return addLine(text, 0, text.length);
	}
	
	/**
	 *	Adds a new line of text to the end. The text isn't measured so any
	 *	part outside of view will be clipped off (the clipped region depends
	 *	on what justification is in use).
	 *
	 *	@return index of the added line or -1 if no more can be added
	 */
	public int addLine(char[] text, int offset, int length) {
		if (usedRows < rows) {
			content[usedRows] = text;
			contentOff[usedRows] = offset;
			contentLen[usedRows] = length;
			return ++usedRows - 1;
		} else {
			return -1;
		}
	}
	
	/**
	 *	Sets the text effect of a given line number.
	 */
	public void setEffect(int line, TextEffect lineEffect) {
		effect[line] = lineEffect;
	}
	
	
	/**
	 *	Appends new content, spanning new lines and wrapping the text where
	 *	necessary.
	 */
	public void add(String text) {
		add(text.toCharArray());
	}
	
	
	/**
	 *	Appends new content, spanning new lines and wrapping the text where
	 *	necessary.
	 */
	public void add(char[] text) {
		add(text, 0, text.length);
	}
	
	/**
	 *	Appends new content, spanning new lines and wrapping the text where
	 *	necessary. The text content is referenced and not copied, so care must
	 *	be taken not to change it (unless that's the desired effect).
	 *
	 *	Note: when breaking the strings down into words it's possible that
	 *	a single word could wider than the space allocated, in which case
	 *	this word is still added to the lines but overflows to the right.
	 *	Ideally these words would be flagged in a preprocessor.
	 *
	 *	@see #textOverflows
	 */
	public void add(char[] text, int offset, int length) {
		int beg = offset;
		int len = length;
		/*
		 *	Remove any whitespace at the end in order to easily tell that the
		 *	end has been reached, otherwise 'end' will not equal 'len' given
		 *	how words are split.
		 */
		for (; len >= 0; len--) {
			if (text[len - 1] != ' ' && text[len - 1] != ZERO_SPACE) {
				break;
			}
		}
		int end = beg;
		
		int lastBeg = 0;
		int lastEnd = 0;
		while (beg < len) {
		 	end = beg;
			for (; end < len; end++) {
				/*
				 *	Advance one word at at time. A word is determined to be
				 *	split by a space, zero width space or new line. No other
				 *	whitespace characters are considered (such as 'thin space'
				 *	or other similar characters). The non-breaking space is
				 *	also ignored, which is the correct behavior (note that
				 *	this would need adding to the font).
				 */
				char current = text[end];
				if (current == ' ' || current == ZERO_SPACE || current == '\n') {
					break;
				}
			}
			if (font.getW(text, lastBeg, end - lastBeg) >= viewW && lastBeg < beg) {
				/*
				 *	The accumulated text has gone over the allocated width so
				 *	add a new line up to the previous word (taking care that
				 *	this content isn't just overflow from the previous text,
				 *	which would cause a blank line to be added).
				 */
				addLine(text, lastBeg, lastEnd - lastBeg);
				lastBeg = beg;
			}
			if (end < len && text[end] == '\n') {
				/*
				 *	Not enough text to fill a line has accumulated but a
				 *	newline has been reached. The content so far is added to
				 *	the lines.
				 */
				addLine(text, lastBeg, end - lastBeg);
				lastBeg = end + 1;
			}
			beg = end + 1; // Note: does what? Steps over whitespace?
			lastEnd = end;
		}
		if (end == len && end - lastBeg > 0) {
			/*
			 *	The end of the content has been reached and some of the
			 *	accumulated text remains. It is given a line of its own.
			 */
			addLine(text, lastBeg, end - lastBeg); // Note: only one without a newline?
		}
	}
	
	/**
	 *	Clears any existing content and sets the view to the text specified.
	 *
	 *	@return number of lines created
	 */
	public int set(String text) {
		return set(text.toCharArray());
	}
	
	/**
	 *	Clears any existing content and sets the view to the text specified.
	 *
	 *	@return number of lines created
	 */
	public int set(char[] text) {
		return set(text, 0, text.length);
	}
	
	/**
	 *	Clears any existing content and sets the view to the text specified.
	 *
	 *	@return number of lines created
	 */
	public int set(char[] text, int offset, int length) {
		clear();
		add(text, offset, length);
		reset();
		return usedRows;
	}
	
	/**
	 *	Performs the updating of the <code>Line</code>s that back this viewer.
	 */
	public void updateList() {
		super.updateList();
		for (int n = 0, i = showingFrom; n < visibleRows; n++, i++) {
			if (i < usedRows) {
				line[n].set(content[i], contentOff[i], contentLen[i], tab);
				line[n].setTextEffect(effect[i]).setPosition(x, transY + n * (font.height + gapH), anchor | Graphics.BASELINE);
			} else {
				line[n].clear();
			}
		}
	}
	
	/**
	 *	Returns whether any of the lines overflow the bounds of the text view.
	 */
	public boolean textOverflows() {
		for (int n = 0; n < usedRows; n++) {
			if (font.getW(content[n], contentOff[n], contentLen[n]) >= viewW) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 *	Handles scrolling one line at a time.
	 */
	protected boolean pressImpl(boolean u, boolean d, boolean l, boolean r) {
		if (u) {
			if (showingFrom > 0 || wrapV) {
				showingFrom--;
				if (showingFrom < 0) {
					showingFrom = usedRows - 1;
				}
			}
		}
		if (d) {
			if (showingFrom + visibleRows < usedRows || wrapV) {
				showingFrom++;
				if (showingFrom >= usedRows) {
					showingFrom  = 0;
				}
			}
		}
		updateList();
		return u || d;
	}
	
	/**
	 *	Handles mouse or pointer input. A click in the top half of the text
	 *	view scrolls up, the bottom half scrolls down.
	 */
	protected boolean clickImpl(int x, int y, boolean allowOwnActions) {
		if (allowOwnActions) {
			if (y < transY + (visibleRows / 2) * (font.height + gapH)) {
				pressImpl(true, false, false, false);
			} else {
				pressImpl(false, true, false, false);
			}
		}
		return true;
	}
	
	/**
	 *	Draws the text viewer and scroll arrows, if required.
	 */
	public void paint(Graphics g, int offsetX, int offsetY) {
		super.paint(g, offsetX, offsetY);
		for (int n = (usedRows < visibleRows ? usedRows : visibleRows) - 1; n >= 0; n--) {
			line[n].paint(g, offsetX, offsetY);
		}
	}
	
}