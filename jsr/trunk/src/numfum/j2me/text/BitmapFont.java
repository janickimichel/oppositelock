package numfum.j2me.text;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 *	Bitmap unicode font.
 */
public final class BitmapFont {
	/**
	 *	Minimum unicode character in the font.
	 */
	private final int min;
	
	/**
	 *	Maximum unicode character in the font.
	 */
	private final int max;
	
	/**
	 *	The font's glyphs in order as a string.
	 */
	private final String charset;
	
	/**
	 *	The font's ascent (pixels above the baseline).
	 */
	public final int ascent;
	
	/**
	 *	The font's descent (pixels below the baseline).
	 */
	final int descent;
	
	/**
	 *	Total hight of the font (from the descent to the ascent).
	 */
	public final int height;
	
	/**
	 *	Number of glyphs in the font.
	 */
	private final int size;
	
	/**
	 *	Each glyph's clip rectangle on the sheet.
	 */
	private final int[] clip;
	
	/**
	 *	Each glyph's x-origin offset.
	 */
	private final byte[] relX;
	
	/**
	 *	Each glyph's y-origin offset.
	 */
	private final byte[] relY;
	
	/**
	 *	Each glyph's advance to the next character.
	 */
	final byte[] advance;
	
	/**
	 *	All the glyphs on a single sheet.
	 */
	private final Image sheet;
	
	/**
	 *	Quick character to glyph lookup.
	 */
	private final int[] lookup;
	
	/**
	 *	Minimum glyph advance. Used for calculating the maximum possible
	 *	number of characters in a given width.
	 */
	final int minAdvance;
	
	/**
	 *	Buffer used to read in the data when creating an image from a stream.
	 *	The buffer is grown as required.
	 */
	private static byte[] imgData;
	
	/**
	 *	Used to hold a reference to a temporary stream used when instantiating
	 *	using a resource file. It ensures the stream is quickly closed
	 *	afterwards and not left for the garbage collector to do so.
	 */
	private static DataInputStream stream;
	
	/**
	 *	Default sized buffer used when creting an image from a stream.
	 */
	private static final int DEFAULT_IMAGE_BUFFER_SIZE = 6144;
	
	//private static final int TYPE_PNG = 0;
	//private static final int TYPE_RAW = 1;
	
	/**
	 *	Creates a new bitmap font from the named resource.
	 */
	public BitmapFont(String resource) throws IOException {
		this(stream = new DataInputStream(CLASS.getResourceAsStream(resource)));
		stream.close();
		stream = null;
	}
	
	/**
	 *	Creates a new bitmap font from the data stream (reading from the
	 *	current position, not closing at the end).
	 *
	 *	@throws IllegalArgumentException if the image sheet is unsupported
	 */
	public BitmapFont(DataInput in) throws IOException {
		min = in.readUnsignedShort();
		max = in.readUnsignedShort();
		charset = in.readUTF();
		
		int tot = max - min;
		if (tot > MAX_LOOKUP_SIZE) {
			tot = MAX_LOOKUP_SIZE;
		}
		lookup = new int[tot];
		for (int n = 0, i = min; n < tot; n++, i++) {
			lookup[n] = charset.indexOf((char) i);
		}
		
		ascent  = in.readByte();
		descent = in.readByte();
		height  = descent - ascent;
		
		size = in.readUnsignedShort();
		
		clip = new int[size];
		for (int n = 0; n < size; n++) {
			clip[n] = in.readInt();
		}
		
		relX = new byte[size];
		in.readFully(relX);
		relY = new byte[size];
		in.readFully(relY);
		
		advance = new byte[size];
		in.readFully(advance);
		minAdvance = findMinimum(advance, size);
		
		/*if (in.readByte() != TYPE_PNG) {
			throw new IllegalArgumentException();
		}*/
		sheet = loadImageFromStream(in);
	}
	
	/**
	 *	Finds the minimum value given an array of bytes.
	 *
	 *	@param size number of array elements in use
	 */
	protected static int findMinimum(byte[] prop, int size) {
		int measure = Integer.MAX_VALUE;
		for (int n = 0; n < size; n++) {
			if (prop[n] > 0 && prop[n] < measure) {
				measure = prop[n];
			}
		}
		return measure;
	}
	
	/**
	 *	Finds the maximum value given an array of bytes.
	 *
	 *	@param size number of array elements in use
	 */
	protected static int findMaximum(byte[] prop, int size) {
		int measure = Integer.MIN_VALUE;
		for (int n = 0; n < size; n++) {
			if (prop[n] > measure) {
				measure = prop[n];
			}
		}
		return measure;
	}
	
	/**
	 *	Returns the glyph index for a given char.
	 */
	public int getIndex(char c) {
		int i = c - min;
		if (i >= 0 && i < lookup.length) {
			return lookup[i];
		} else {
			return charset.lastIndexOf(c);
		}
	}
	
	/**
	 *	Whether the glyph has a drawable content or is a spacing character.
	 */
	public boolean hasContent(int n) {
		return (clip[n] & (SHEET_MASK_CLIP_W << SHEET_ROTL_CLIP_W)) != 0;
	}
	
	/**
	 *	Returns the width of a single char.
	 */
	public int getW(char c) {
		int idx = getIndex(c);
		if (idx >= 0) {
			return advance[idx];
		} else {
			return 0;
		}
	}
	
	/**
	 *	Returns the width of the selection of text.
	 */
	public int getW(char[] text) {
		return getW(text, 0, text.length);
	}
	
	/**
	 *	Returns the width of the selection of text.
	 *
	 *	@param offset first char used in the array
	 *	@param length number of chars used
	 */
	public int getW(char[] text, int offset, int length) {
		int total = 0;
		for (int n = 0, i = offset; n < length; n++, i++) {
			int idx = getIndex(text[i]);
			if (idx >= 0) {
				total += advance[idx];
			}
		}
		return total;
	}
	
	/**
	 *	Draws a glyph using its baseline and left margin as the origin.
	 */
	public void paint(Graphics g, int n, int x, int y) {
		int clipN = this.clip[n];
		g.drawRegion(sheet,
			(clipN >> SHEET_ROTL_CLIP_X) & SHEET_MASK_CLIP_X,
			(clipN >> SHEET_ROTL_CLIP_Y) & SHEET_MASK_CLIP_Y,
			(clipN >> SHEET_ROTL_CLIP_W) & SHEET_MASK_CLIP_W,
			(clipN >> SHEET_ROTL_CLIP_H) & SHEET_MASK_CLIP_H,
			0, x + relX[n], y + relY[n], Graphics.TOP | Graphics.LEFT);
	}
	
	/**
	 *	Draws a glyph from the given anchor.
	 */
	public void paint(Graphics g, int n, int x, int y, int anchor) {
		if ((anchor & Graphics.LEFT) == 0) {
			if ((anchor & Graphics.HCENTER) != 0) {
				x -= advance[n] >> 1;
			} else if ((anchor & Graphics.RIGHT) != 0) {
				x -= advance[n];
			}
		}
		if ((anchor & Graphics.BASELINE) == 0) {
			if ((anchor & Graphics.TOP) != 0) {
				y -= ascent;
			} else if ((anchor & Graphics.BOTTOM) != 0) {
				y -= descent;
			}
		}
		paint(g, n, x, y);
	}
	
	/**
	 *	Loads an image from the current position in a stream. The first two
	 *	bytes read are the number of bytes to load, which limits the loader to
	 *	64kB.
	 *
	 *	Note: a buffer to hold the data is grown as required.
	 */
	public final static Image loadImageFromStream(DataInput in) throws IOException {
		int dataSize = in.readUnsignedShort();
		if (imgData == null || imgData.length < dataSize) {
			imgData = new byte[dataSize > DEFAULT_IMAGE_BUFFER_SIZE ? dataSize : DEFAULT_IMAGE_BUFFER_SIZE];
		}
		in.readFully(imgData, 0, dataSize);
		return Image.createImage(imgData, 0, dataSize);
	}
	
	/**
	 *	<code>Class</code> object for this class. Used when loading.
	 *
	 *	@see Class.getResourceAsStream
	 */
	private static final Class CLASS;
	static {
		Class temp = null;
		try {
			temp = Class.forName("BitmapFont");
		} catch (ClassNotFoundException e) {
			temp = new Object().getClass();
		}
		CLASS = temp;
	}
	
	/**
	 *	Maximum size of the char to glyph lookup table.
	 */
	private static final int MAX_LOOKUP_SIZE = 128;
	
	private static final int SHEET_BITS_CLIP_X = 9;
	private static final int SHEET_BITS_CLIP_Y = 9;
	private static final int SHEET_BITS_CLIP_W = 7;
	private static final int SHEET_BITS_CLIP_H = 7;
	
	private static final int SHEET_MASK_CLIP_X = (1 << SHEET_BITS_CLIP_X) - 1;
	private static final int SHEET_MASK_CLIP_Y = (1 << SHEET_BITS_CLIP_Y) - 1;
	private static final int SHEET_MASK_CLIP_W = (1 << SHEET_BITS_CLIP_W) - 1;
	private static final int SHEET_MASK_CLIP_H = (1 << SHEET_BITS_CLIP_H) - 1;
	
	private static final int SHEET_ROTL_CLIP_X = 0;
	private static final int SHEET_ROTL_CLIP_Y = SHEET_ROTL_CLIP_X + SHEET_BITS_CLIP_X;
	private static final int SHEET_ROTL_CLIP_W = SHEET_ROTL_CLIP_Y + SHEET_BITS_CLIP_Y;
	private static final int SHEET_ROTL_CLIP_H = SHEET_ROTL_CLIP_W + SHEET_BITS_CLIP_W;
}