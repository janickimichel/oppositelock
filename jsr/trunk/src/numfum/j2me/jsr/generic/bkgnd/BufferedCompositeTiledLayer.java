package numfum.j2me.jsr.generic.bkgnd;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.AnimTileController;

import java.io.DataInput;
import java.io.IOException;

/**
 *	A buffered tile implementation which draws multiple tiles into the same
 *	space on the grid.
 */
public class BufferedCompositeTiledLayer extends BufferedTiledLayer {
	/**
	 *	Number of overlays on top of the background layer.
	 */
	protected final int overlays;
	
	/**
	 *	Tile indices for the overlay layers.
	 */
	protected final short[][][] overlayMap;
	
	/**
	 *	@param cols number of cols in the tilemap
	 *	@param rows number of rows in the tilemap
	 *	@param viewW width in pixels of the area this tiled layer covers
	 *	@param viewH height in pixels of the area this tiled layer covers
	 *	@param overlays number of layers on top of the background layer
	 *	@param tileset all the tiles on a single sheet
	 *	@param tile the animated tile references
	 *	@param allAnims whether all animations should run or priority ones
	 *	@param controller controller for the animated tiles
	 *	@param exclusive whether the buffer should be exclusive or shared
	 *	@param dual whether to use single or dual back buffers
	 */
	public BufferedCompositeTiledLayer(int cols, int rows, int viewW, int viewH, int overlays, Image tileset, AnimTile[] tile, boolean allAnims, AnimTileController controller, boolean exclusive) {
		this(cols, rows, viewW, viewH, overlays, tileset, tile, allAnims, controller, exclusive, BufferedTiledLayer.copyAreaHasBugs());
	}
	
	/**
	 *	@param cols number of cols in the tilemap
	 *	@param rows number of rows in the tilemap
	 *	@param viewW width in pixels of the area this tiled layer covers
	 *	@param viewH height in pixels of the area this tiled layer covers
	 *	@param overlays number of layers on top of the background layer
	 *	@param tileset all the tiles on a single sheet
	 *	@param tile the animated tile references
	 *	@param allAnims whether all animations should run or priority ones
	 *	@param controller controller for the animated tiles
	 *	@param exclusive whether the buffer should be exclusive or shared
	 */
	public BufferedCompositeTiledLayer(int cols, int rows, int viewW, int viewH, int overlays, Image tileset, AnimTile[] tile, boolean allAnims, AnimTileController controller, boolean exclusive, boolean dual) {
		super(cols, rows, viewW, viewH, tileset, tile, allAnims, controller, exclusive, dual);
		
		this.overlays = overlays;
		overlayMap = new short[overlays][rows][cols];
	}
	
	/**
	 *	Loads a tilemap from the data stream.
	 *
	 *	Note: doesn't fully support the new tilemap format used in FMQuiz.
	 */
	public void load(DataInput in) throws IOException {
		for (int n = 0; n < overlays; n++) {
			for (int y = 0; y < rows; y++) {
				for (int x = 0; x < cols; x++) {
					overlayMap[n][y][x] = 0;
				}
			}
		}
		int numLayers = in.readUnsignedByte();
		for (int n = 0; n < numLayers; n++) {
			int offset = in.readByte();
			if (n == 0) {
				super.load(in);
			} else {
				if (offset < 0) {
					offset += rows - 2;
				}
				load(in, n - 1, offset);
			}
		}
		scanActiveRows();
		reset();
	}
	
	/**
	 *	Loads an overlay layer.
	 *
	 *	@param layer overlay index
	 *	@param offset number of rows to skip before inserting the layer
	 */
	protected void load(DataInput in, int layer, int offset) throws IOException {
		int mapCols = in.readUnsignedByte();
		int mapRows = in.readUnsignedByte();
		int indexType = in.readByte();
		for (int y = 0; y < mapRows; y++) {
			int row = getTileY(y + offset);
			for (int x = 0; x < mapCols; x++) {
				int col = getTileX(x);
				switch (indexType) {
				case TILEMAP_AS_BYTES:
					setCell(layer, col, row, in.readUnsignedByte());
					break;
				case TILEMAP_AS_SHORTS:
					setCell(layer, col, row, in.readUnsignedShort());
					break;
				}
			}
		}
	}
	
	/**
	 *	Skips over a tilemap stored in the data stream.
	 */
	public static void skip(DataInput in) throws IOException {
		for (int n = in.readUnsignedByte(); n > 0; n--) {
			in.readByte(); // offset
			ContinuousTiledLayer.skip(in);
		}
	}
	
	/**
	 *	Sets the index of a particular cell.
	 */
	protected void setCell(int layer, int x, int y, int index) {
		overlayMap[layer][y][x] = (short) (index & TILE_MASK);
		setActiveTile(x, y, index);
	}
	
	/**
	 *	Performs the drawing of multiple tiles into the buffer.
	 */
	protected void drawTile(Graphics g, int col, int row, int x, int y) {
		drawTile(g, animtile[map[row][col] & TILE_MASK].getTileIndex(), x, y);
		for (int n = 0; n < overlays; n++) {
			int index = overlayMap[n][row][col];
			if (index > 0) {
				drawTile(g, animtile[index].getTileIndex(), x, y);
			}
		}
	}
}