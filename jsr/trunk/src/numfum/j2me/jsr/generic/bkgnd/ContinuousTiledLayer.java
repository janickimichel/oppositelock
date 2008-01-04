package numfum.j2me.jsr.generic.bkgnd;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import javax.microedition.lcdui.Graphics;

import numfum.j2me.jsr.Constants;
import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.AnimTileController;
import numfum.j2me.jsr.generic.Positionable;

/**
 *	Base class for all tiled layers. A continuous tiled layer is, as its name
 *	suggests, a tiled layer with the ability to scroll indefinitely in any
 *	direction, regardless of the size of its tilemap. The view size of the
 *	layer determines how much is visible and how the much the tilemap needs to
 *	repeat in order to fill it.
 *
 *	Individual implementations handle the various APIs and phone differences.
 */
public abstract class ContinuousTiledLayer extends AnimTileController implements Constants, Positionable {
	/**
	 *	Tile width (and for all subclasses).
	 */
	public static final int TILE_W = 8;
	
	/**
	 *	Tile height (and for all subclasses).
	 */
	public static final int TILE_H = 8;
	
	/**
	 *	Magic number denoting the map is saved in a compact form. It lets the
	 *	map editor load the files that the phones use.
	 *
	 *	@see toolbox.tile.TileMap
	 */
	protected static final int MAGIC_BYTE = 0xB17E;
	
	/**
	 *	Signifies the <code>DataInput</code>'s tile indices is as bytes.
	 */
	public static final int TILEMAP_AS_BYTES  = 0;
	
	/**
	 *	Signifies the <code>DataInput</code>'s tile indices is as shorts.
	 */
	public static final int TILEMAP_AS_SHORTS = 1;
	
	/**
	 *	Signifies the <code>DataInput</code>'s tile indices are a further
	 *	index into a table.
	 *
	 *	@see #indexTable
	 */
	public static final int TILEMAP_AS_INDEX = 2;
	
	/**
	 *	Number of cols in the backing tilemap.
	 */
	protected final int cols;
	
	/**
	 *	Number of rows in the backing tilemap.
	 */
	protected final int rows;
	
	/**
	 *	How many pixels the cols cover.
	 */
	protected final int colsPixels;
	
	/**
	 *	How many pixels the rows cover.
	 */
	protected final int rowsPixels;
	
	/**
	 *	Visible width in pixels.
	 */
	protected final int viewW;
	
	/**
	 *	Visible height in pixels.
	 */
	protected final int viewH;
	
	/**
	 *	Width of the view, or the maximum number of cols showing at once.
	 */
	protected final int viewCols;
	
	/**
	 *	Height of the view, or the maximum number of rows showing at once.
	 */
	protected final int viewRows;
	
	/**
	 *	X-axis position where the tiles were being drawn from the last update.
	 *
	 *	@see #moveTo
	 */
	protected int _x = 0;
	
	/**
	 *	Y-axis position where the tiles were being drawn from the last update.
	 *
	 *	@see #moveTo
	 */
	protected int _y = 0;
	
	/**
	 *	Implementation specific value to maintain the draw offset.
	 */
	protected int originX = 0;
	
	/**
	 *	Implementation specific value to maintain the draw offset.
	 */
	protected int originY = 0;
	
	/**
	 *	Any external animation controller associated with this tiled layer.
	 */
	protected final AnimTileController controller;
	
	/**
	 *	Current x-axis position from where this tiled layer is drawn from.
	 */
	protected int posX = 0;
	
	/**
	 *	Current y-axis position from where this tiled layer is drawn from.
	 */
	protected int posY = 0;
	
	/**
	 *	Reusable lookup table for expanding maps.
	 *
	 *	@see #TILEMAP_AS_INDEX
	 */
	private short[] indexTable = null;
	
	/**
	 *	@param cols number of cols in the tilemap
	 *	@param rows number of rows in the tilemap
	 *	@param viewW width in pixels of the area this tiled layer covers
	 *	@param viewH height in pixels of the area this tiled layer covers
	 *	@param tile the animated tile references
	 *	@param controller controller for the animated tiles
	 */
	public ContinuousTiledLayer(int cols, int rows, int viewW, int viewH, AnimTile[] tile, AnimTileController controller) {
		super(tile);
		
		this.cols = cols;
		this.rows = rows;
		
		colsPixels = cols * TILE_W;
		rowsPixels = rows * TILE_H;
		
		this.viewW = viewW;
		this.viewH = viewH;
		
		int calcCols = viewW / TILE_W;
		if (viewW % TILE_W != 0) {
			calcCols++;
		}
		viewCols = calcCols;
		
		int calcRows = viewH / TILE_H;
		if (viewH % TILE_H != 0) {
			calcRows++;
		}
		viewRows = calcRows;
		
		this.controller = controller;
	}
	
	/**
	 *	Loads a tilemap from a resource file.
	 */
	public void load(String resource) throws IOException {
		DataInputStream in = new DataInputStream(getClass().getResourceAsStream(resource));
		load(in);
		in.close();
	}
	
	/**
	 *	Loads a tilemap from the data stream.
	 *
	 *	Note: doesn't fully support the new tilemap format used in FMQuiz.
	 */
	public void load(DataInput in) throws IOException {
		clear();
		int mapCols = in.readUnsignedByte();
		int mapRows = in.readUnsignedByte();
		if (DEBUG) {
			System.out.println("Map cols: " + mapCols + ", rows: " + mapRows);
		}
		
		int indexType = in.readByte();
		
		for (int y = 0; y < mapRows; y++) {
			for (int x = 0; x < mapCols; x++) {
				switch (indexType) {
				case TILEMAP_AS_BYTES:
					setCell(x, y, in.readUnsignedByte());
					break;
				case TILEMAP_AS_SHORTS:
					setCell(x, y, in.readUnsignedShort());
					break;
				}
			}
		}
		reset();
	}
	
	/**
	 *	Skips over a tilemap stored in the data stream.
	 */
	public static void skip(DataInput in) throws IOException {
		int cells = in.readUnsignedByte() * in.readUnsignedByte();
		switch (in.readByte()) {
		case TILEMAP_AS_BYTES:
			in.skipBytes(cells);
			break;
		case TILEMAP_AS_SHORTS:
			in.skipBytes(cells * 2);
			break;
		case TILEMAP_AS_INDEX:
			in.skipBytes(cells);
			break;
		default:
			throw new IOException();
		}
	}
	
	/**
	 *	Called when the image backing the tileset has been updated.
	 *	Implementations override this if they need to perform transformations
	 *	or calculations on the tile data before using again.
	 */
	public void tilesetUpdated() {}
	
	/**
	 *	Sets the index of a particular cell in the backing tilemap.
	 */
	public abstract void setCell(int x, int y, int index);
	
	
	/**
	 *	Sets the index of a particular cell in the backing tilemap and ensures
	 *	the on-screen representation is updated instantly (buffered tilemaps,
	 *	for instance, don't update until they need to).
	 */
	public void setAndUpdateCell(int x, int y, int index) {
		if (x >= 0 && x < cols && y >= 0 && y < rows) {
			setCell(x, y, index);
		}
	}
	
	/**
	 *	Given a col returns the actual col to be drawn, taking into account
	 *	the wraparound that occurs.
	 */
	protected final int getTileX(int x) {
		return ((x %= cols) < 0) ? cols + x : x;
	}
	
	/**
	 *	Given a row returns the actual row to be drawn, taking into account
	 *	the wraparound that occurs.
	 */
	protected final int getTileY(int y) {
		return ((y %= rows) < 0) ? rows + y : y;
	}
	
	/**
	 *	Resets to the default tile frames (if an external controller is used
	 *	it's expected to look after its own tiles) and puts the tilemap at the
	 *	origin.
	 */
	public void reset() {
		super.reset();
		_x = 0;
		_y = 0;
		originX = 0;
		originY = 0;
	}
	
	/**
	 *	Moves the tiled layer by the specified number of pixels.
	 *
	 *	@see moveTo
	 */
	public final void moveBy(int x, int y) {
		if (x != 0 || y != 0) {
			moveTo(_x + x, _y + y);
		}
	}
	
	/**
	 *	Moves the tiled layer to the specified postion (wrapping round and
	 *	repeating where necessary).
	 */
	public abstract void moveTo(int x, int y);
	
	/**
	 *	Cycles the layer's animated tiles if no external controller is in
	 *	charge of the anims.
	 */
	public void cycle() {
		if (controller == null) {
			super.cycle();
		}
	}
	
	/**
	 *	Returns the visible width.
	 */
	public int getW() {
		return viewW;
	}
	
	/**
	 *	Returns the visible height.
	 */
	public int getH() {
		return viewH;
	}
	
	/**
	 *	Draws this tiled layer at the specified postion.
	 */
	public Positionable setPosition(int x, int y, int anchor) {
		if ((anchor & Graphics.LEFT) == 0) {
			if ((anchor & Graphics.HCENTER) != 0) {
				x -= viewW / 2;
			} else if ((anchor & Graphics.RIGHT) != 0) {
				x -= viewW;
			}
		}
		if ((anchor & Graphics.TOP) == 0) {
			if ((anchor & Graphics.VCENTER) != 0) {
				y -= viewH / 2;
			} else if ((anchor & Graphics.BOTTOM) != 0) {
				y -= viewH;
			}
		}
		posX = x;
		posY = y;
		return this;
	}
	
	/**
	 *	To be implemented by subclasses.
	 *
	 *	@see Positionable.paint
	 */
	public abstract void paint(Graphics g, int x, int y);
	
	public String toString() {
		return getClass().getName() + " [viewW: " + viewW + ", viewH: " + viewH + "]";
	}
}