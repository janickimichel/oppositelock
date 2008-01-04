package numfum.j2me.jsr.generic.bkgnd;

import javax.microedition.lcdui.Image;

import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.AnimTileController;
import numfum.j2me.util.Fixed;

/**
 *	An extension of <code>TiledOverlay</code> with a cut-down backing
 *	<code>TiledLayer</code>. Phones such as the D500 experience a huge speed
 *	increase when using this;
 */
public class ShuffledTiledOverlay extends TiledOverlay {
	protected final short[][] map;
	
	/**
	 *	@param cols number of cols in the tilemap
	 *	@param rows number of rows in the tilemap
	 *	@param viewW width in pixels of the area this tiled layer covers
	 *	@param viewH height in pixels of the area this tiled layer covers
	 *	@param tileset all the tiles on a single sheet
	 */
	public ShuffledTiledOverlay(int cols, int rows, int viewW, int viewH, Image tileset, AnimTile[] tile, AnimTileController controller) {
		super(cols, rows, viewW, viewH, tileset, tile, controller, PEER_TRUNCATE);
		
		map = new short[rows][cols];
	}
	
	/**
	 *	Empty implementation. No cells to fill.
	 */
	protected void fillMissingCells() {}
	
	/**
	 *	Redraws all the tiles from the given coords.
	 */
	public void layout(int startCol, int startRow) {
		int peerRow = 0;
		for (int y = startRow, numRows = viewRows; numRows >= 0; y++, numRows--) {
			int peerCol = 0;
			for (int x = startCol, numCols = viewCols; numCols >= 0; x++, numCols--) {
				peer.setCell(peerCol++, peerRow, map[getTileY(y)][getTileX(x)]);
			}
			peerRow++;
		}
	}
	
	/**
	 *	Sets the index of a particular cell.
	 */
	public void setCell(int x, int y, int index) {
		map[y][x] = (short) (-index - 1);
		addAnimTile(index);
		if (controller != null) {
			controller.addAnimTile(index);
		}
	}
	
	/**
	 *	Puts the tilemap at the origin and draws the tiles into peer.
	 */
	public void reset() {
		super.reset();
		layout(0, 0);
	}
	
	/**
	 *	Moves the tiled layer to the specfied position.
	 */
	public void moveTo(int x, int y) {
		_x = x;
		_y = y;
		int wrapX = Fixed.wrap(x, colsPixels);
		int wrapY = Fixed.wrap(y, rowsPixels);
		
		originX = wrapX % TILE_W;
		originY = wrapY % TILE_H;
		layout(wrapX / TILE_W, wrapY / TILE_H);
	}
}