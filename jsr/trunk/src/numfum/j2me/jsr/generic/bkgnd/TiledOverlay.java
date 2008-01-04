package numfum.j2me.jsr.generic.bkgnd;

import java.io.DataInput;
import java.io.IOException;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.TiledLayer;

import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.AnimTileController;
import numfum.j2me.util.Fixed;

/**
 *	An implementation of <code>ContinuousTiledLayer</code> which uses a shared
 *	<code>TiledLayer</code> to perform its drawing (shared in order to reduce
 *	the overhead).
 */
public class TiledOverlay extends ContinuousTiledLayer {
	/**
	 *	Constant for extending the backing <code>TiledLayer</code> to cover
	 *	the extra required tiles for continuous scrolling.
	 */
	public static final int PEER_EXTEND = 0;
	
	/**
	 *	Constant for extending the backing <code>TiledLayer</code> to cover
	 *	the entire view width and height.
	 */
	public static final int PEER_TRUNCATE = 1;
	
	/**
	 *	Constant for extending the backing <code>TiledLayer</code> to cover
	 *	only the requested cols and rows.
	 */
	public static final int PEER_EXACT = 2;
	
	/**
	 *	<code>TiledLayer</code> backing the drawing.
	 */
	protected final TiledLayer peer;
	
	/**
	 *	Cols used by the peer to fill the required space.
	 */
	protected final int peerCols;
	
	/**
	 *	Rows used by the peer to fill the required space.
	 */
	protected final int peerRows;
	
	/**
	 *	@param cols number of cols in the tilemap
	 *	@param rows number of rows in the tilemap
	 *	@param viewW width in pixels of the area this tiled layer covers
	 *	@param viewH height in pixels of the area this tiled layer covers
	 *	@param tileset all the tiles on a single sheet
	 *	@param tile the animated tile references
	 *	@param controller controller for the animated tiles
	 *	@param peerFit how the backing tiled layer is extended
	 *
	 *	@see #PEER_EXTEND
	 */
	public TiledOverlay(int cols, int rows, int viewW, int viewH, Image tileset, AnimTile[] tile, AnimTileController controller, int peerFit) {
		super(cols, rows, viewW, viewH, tile, controller);
		
		switch (peerFit) {
		case PEER_EXTEND:
			peerCols = cols + viewCols;
			peerRows = rows + viewRows;
			break;
		case PEER_TRUNCATE:
			peerCols = viewCols + 1;
			peerRows = viewRows + 1;
			break;
		default:
			peerCols = cols;
			peerRows = rows;
		}
		peer = new TiledLayer(peerCols, peerRows, tileset, TILE_W, TILE_H);
		
		for (int n = 0; n < maxAnims; n++) {
			peer.createAnimatedTile(tile[n].getTileIndex() + 1);
		}
	}
	
	/**
	 *	Loads a tilemap from the data stream.
	 *
	 *	Note: doesn't fully support the new tilemap format used in FMQuiz.
	 */
	public final void load(DataInput in) throws IOException {
		super.load(in);
		fillMissingCells();
	}
	
	/**
	 *	Sets tile indices in the backing TiledLayer to ensure a constant
	 *	coverage when wrapping.
	 */
	protected void fillMissingCells() {
		for (int y = 0; y < peerRows; y++) {
			for (int x = 0; x < peerCols; x++) {
				if (x >= cols || y >= rows) {
					peer.setCell(x, y, peer.getCell(x % cols, y % rows));
				}
			}
		}
	}
	
	/**
	 *	Sets the index of a particular cell.
	 */
	public void setCell(int x, int y, int index) {
		if (animtile[index].isAnimated()) {
			peer.setCell(x, y, -index - 1);
		} else {
			peer.setCell(x, y, animtile[index].getTileIndex() + 1);
		}
		addAnimTile(index); // used by cycle to update the peer, hence adding this twice
		if (controller != null) {
			controller.addAnimTile(index);
		}
	}
	
	/**
	 *	Puts the tilemap at the origin and draws the tiles into peer.
	 */
	public void reset() {
		super.reset();
		for (int n = 0; n < maxAnims; n++) {
			peer.setAnimatedTile(-n - 1, animtile[n].getTileIndex() + 1);
		}
	}
	
	/**
	 *	Moves the tiled layer to the specfied position.
	 */
	public void moveTo(int x, int y) {
		_x = x;
		_y = y;
		originX = Fixed.wrap(x, colsPixels);
		originY = Fixed.wrap(y, rowsPixels);
	}
	
	public final void cycle() {
		super.cycle();
		for (int n = numActiveAnims - 1; n >= 0; n--) {
			int index = activeAnims[n];
			peer.setAnimatedTile(-index - 1, animtile[index].getTileIndex() + 1);
		}
	}
	
	/**
	 *	Draws the tiled layer.
	 */
	public final void paint(Graphics g, int x, int y) {
		int clipX = g.getClipX();
		int clipY = g.getClipY();
		int clipW = g.getClipWidth();
		int clipH = g.getClipHeight();
		
		x += posX;
		y += posY;
		g.clipRect(x, y, viewW, viewH);
		peer.setPosition(x - originX, y - originY);
		peer.paint(g);
		
		g.setClip(clipX, clipY, clipW, clipH);
	}
}