package numfum.j2me.jsr.generic.bkgnd;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.AnimTileController;

import java.io.DataInput;
import java.io.IOException;

/**
 *	An implementation of <code>ContinuousTiledLayer</code> which uses either
 *	a single or dual buffers to cut down on the drawing. A single buffer
 *	obviously uses less resources but doesn't work width some MIDP2.0
 *	implementations.
 */
public class BufferedTiledLayer extends ContinuousTiledLayer {
	/**
	 *	Mask to extract the tile index from the tilemap.
	 *
	 *	@see #map
	 */
	protected static final int TILE_MASK = 0x7FFF;
	
	/**
	 *	Value used to denote the tilemap entry is animated.
	 *
	 *	@see #map
	 */
	protected static final int ANIM_FLAG = 0x8000;
	
	/**
	 *	Array where the tile indices are stored.
	 */
	protected final short[][] map;
	
	/**
	 *	Width of the tile buffer.
	 */
	public final int tileCols;
	
	/**
	 *	Height of the tile buffer.
	 */
	public final int tileRows;
	
	/**
	 *	Width of the backing buffer(s).
	 */
	private final int bufW;
	
	/**
	 *	Height of the backing buffer(s).
	 */
	private final int bufH;
	
	/**
	 *	Current back buffer. 
	 */
	private Image buf;
	
	/**
	 *	Current graphics context for the back buffer.
	 */
	private Graphics gfx;
	
	/**
	 *	Whether dual buffers are being used or just one.
	 */
	private final boolean dual;
	
	/**
	 *	Back buffer 'a' (when using dual buffers).
	 */
	private final Image bufA;
	
	/**
	 *	Graphics context 'a' (when using dual buffers).
	 */
	private final Graphics gfxA;
	
	/**
	 *	Back buffer 'b' (when using dual buffers).
	 */
	private final Image bufB;
	
	/**
	 *	Graphics context 'b' (when using dual buffers).
	 */
	private final Graphics gfxB;
	
	/**
	 *	Whether 'a' is the current buffer (when using dual buffers).
	 */
	private boolean useA = true;
	
	/**
	 *	Previous tilemap x-position.
	 */
	protected int lastX = 0;
	
	/**
	 *	Previous tilemap x-position.
	 */
	protected int lastY = 0;
	
	/**
	 *	Where the tiles start drawing from.
	 */
	protected int tileStartX = 0;
	
	/**
	 *	Where the tiles start drawing from.
	 */
	protected int tileStartY = 0;
	
	/**
	 *	Tile sheet containing the entire tileset.
	 */
	private final Image tileset;
	
	/**
	 *	Precalculated offset into the sheet for each tile.
	 */
	private final short tileOffsetX[];
	
	/**
	 *	Precalculated offset into the sheet for each tile.
	 */
	private final short tileOffsetY[];
	
	/**
	 *	Whether all animated tiles are updated or only priority tiles.
	 */
	protected final boolean allAnims;
	
	/**
	 *	A flag to denote whether a row has active tile animations.
	 */
	protected final byte[] activeRow;
	
	public BufferedTiledLayer(int cols, int rows, int viewW, int viewH, Image tileset, AnimTile[] tile, boolean allAnims, AnimTileController controller, boolean exclusive) {
		this(cols, rows, viewW, viewH, tileset, tile, allAnims, controller, exclusive, FORCE_DOUBLE_BUFFER || copyAreaHasBugs());
	}
	
	/**
	 *	@param cols number of cols in the tilemap
	 *	@param rows number of rows in the tilemap
	 *	@param viewW width in pixels of the area this tiled layer covers
	 *	@param viewH height in pixels of the area this tiled layer covers
	 *	@param tileset all the tiles on a single sheet
	 *	@param tile the animated tile references
	 *	@param allAnims whether all animations should run or priority ones
	 *	@param controller controller for the animated tiles
	 *	@param exclusive whether the buffer should be exclusive or shared
	 *	@param dual whether to use single or dual back buffers
	 */
	public BufferedTiledLayer(int cols, int rows, int viewW, int viewH, Image tileset, AnimTile[] tile, boolean allAnims, AnimTileController controller, boolean exclusive, boolean dual) {
		super(cols, rows, viewW, viewH, tile, controller);
		
		map = new short[rows][cols];
		
		tileCols = viewCols + 2;
		tileRows = viewRows + 2;
		
		bufW = tileCols * TILE_W;
		bufH = tileRows * TILE_H;
		
		this.dual = dual;
		
		if (dual) {
			bufA = createBuffer(bufW, bufH, exclusive, true);
			gfxA = bufA.getGraphics();
			bufB = createBuffer(bufW, bufH, exclusive, false);
			gfxB = bufB.getGraphics();
			
			buf = bufA;
			gfx = gfxA;
		} else {
			bufA = null;
			gfxA = null;
			bufB = null;
			gfxB = null;
			
			buf = createBuffer(bufW, bufH, exclusive, true);
			gfx = buf.getGraphics();
		}
		
		this.tileset = tileset;
		
		int tilesPerRow = tileset.getWidth()  / TILE_W;
		int tilesPerCol = tileset.getHeight() / TILE_H;
		int numTiles = tilesPerRow * tilesPerCol;
		tileOffsetX = new short[numTiles];
		tileOffsetY = new short[numTiles];
		if (DEBUG) {
			System.out.println("numTiles: " + numTiles);
		}
		for (int n = 0; n < numTiles; n++) {
			tileOffsetX[n] = (short) ((n % tilesPerRow) * TILE_W);
			tileOffsetY[n] = (short) ((n / tilesPerRow) * TILE_H);
		}
		
		this.allAnims = allAnims;
		
		activeRow = new byte[rows];
	}
	
	/**
	 *	Disables all animated tile updates.
	 */
	public final void resetActiveTiles() {
		for (int y = rows - 1; y >= 0; y--) {
			for (int x = cols - 1; x >= 0; x--) {
				map[y][x] &= TILE_MASK;
			}
		}
	}
	
	/**
	 *	Flags rows in which tile updates are required.
	 */
	public final void scanActiveRows() {
		for (int y = 0; y < rows; y++) {
			boolean isActiveRow = false;
			for (int x = 0; x < cols; x++) {
				if (map[y][x] < 0) {
					isActiveRow = true;
					break;
				}
			}
			activeRow[y] = (byte) (isActiveRow ? 1 : 0);
		}
	}
	
	/**
	 *	Adds a tile to the list of tiles to be updated. If an external
	 *	controller is in use the tile is purposely removed from its list
	 *	if the tiled layer isn't explicitly animating (otherwise different
	 *	states of that tile will get drawn).
	 */
	protected final void setActiveTile(int x, int y, int index) {
		if (animtile[index].isAnimated()) {
			if (allAnims || ((animtile[index].data >> AnimTile.DATA_ROTL_PRTY) & AnimTile.DATA_MASK_PRTY) != 0) {
				map[y][x] |= ANIM_FLAG;
				if (controller != null) {
					controller.addAnimTile(index);
				} else {
					addAnimTile(index);
				}
			} else {
				if (controller != null) {
					controller.removeAnimTile(index);
				}
			}
		}
	}
	
	/**
	 *	Loads a tilemap from the data stream. Before loading existing flagged
	 *	animated rows and tiles are reset, and after loading each tile is
	 *	checked to see if it will need updating.
	 */
	public void load(DataInput in) throws IOException {
		resetActiveTiles();
		super.load(in);
		scanActiveRows();
	}
	
	/**
	 *	Sets the index of a particular cell.
	 */
	public void setCell(int x, int y, int index) {
		map[y][x] = (short) (index & TILE_MASK);
		setActiveTile(x, y, index);
	}
	
	public void setAndUpdateCell(int x, int y, int index) {
		super.setAndUpdateCell(x, y, index);
		/*
		 *	Simply calling layout() is not the most efficient way of updating
		 *	the buffer but it's doubtful this will cause a problem.
		 */
		layout(x, y, 1, 1);
	}
	
	/**
	 *	Given coords in the tilemap returns the tile index, taking into
	 *	account the wraparound that occurs.
	 */
	protected final int getTileAt(int x, int y) {
		return map[getTileY(y)][getTileX(x)] & TILE_MASK;
	}
	
	/**
	 *	Resets to the default tile frames (if an external controller is used
	 *	it's expected to look after its own tiles) and puts the tilemap at the
	 *	origin.
	 */
	public final void reset() {
		super.reset();
		lastX = -TILE_W;
		lastY = -TILE_H;
		tileStartX = -TILE_W;
		tileStartY = -TILE_H;
		layout(-1, -1, tileCols, tileRows);
	}
	
	/**
	 *	Draws the tile into the buffer.
	 *
	 *	@param col the tile's map col
	 *	@param row the tile's map row
	 */
	protected void drawTile(Graphics g, int col, int row, int x, int y) {
		drawTile(g, animtile[map[row][col] & TILE_MASK].getTileIndex(), x, y);
	}
	
	/**
	 *	Draws the tile into the buffer.
	 *
	 *	@param n the tile index
	 */
	protected final void drawTile(Graphics g, int n, int x, int y) {
		g.drawRegion(tileset,
			tileOffsetX[n], tileOffsetY[n],
			TILE_W,
			TILE_H, 0,
			x, y, Graphics.TOP | Graphics.LEFT);
	}
	
	/**
	 *	Redraws all the tiles in the given region.
	 */
	protected final void layout(int startCol, int startRow, int numCols, int numRows) {
		if (numCols < 1 || numRows < 1) {
			return;
		}
		int calcX = startCol * TILE_W - tileStartX;
		for (int y = startRow, drawY = startRow * TILE_H - tileStartY; y < startRow + numRows; y++, drawY += TILE_H) {
			if (drawY >= 0 && drawY < bufH) {
				for (int x = startCol, drawX = calcX; x < startCol + numCols; x++, drawX += TILE_W) {
					if (drawX >= 0 && drawX < bufW) {
						drawTile(gfx, getTileX(x), getTileY(y), drawX, drawY);
					}
				}
			}
		}
	}
	
	/**
	 *	Moves the tiled layer to the specfied position.
	 *
	 *	TODO: this was adapted from code written pre-MM, and, well, it isn't
	 *	very good. Whilst it does work it draws/moves more than necessary when
	 *	updating a full screen, consequently slowing things down.
	 */
	public final void moveTo(int x, int y) {
		_x = x;
		_y = y;
		/*
		 *	Take into account that the buffer has an extra column/row around
		 *	the visible portion.
		 */
		x -= TILE_W;
		y -= TILE_H;
		
		/*
		 *	Calculates the number of pixels to offset the buffer by. Going
		 *	right or down makes shift > 0, whereas left or up makes shift < 0.
		 */
		int shiftX = lastX - x;
		if (shiftX != 0) {
			originX = (originX + shiftX) % TILE_W;
		}
		int shiftY = lastY - y;
		if (shiftY != 0) {
			originY = (originY + shiftY) % TILE_H;
		}
		
		int fillCols = (tileStartX - x) / TILE_W;
		int fillRows = (tileStartY - y) / TILE_H;
		
		if (fillCols != 0 || fillRows != 0) {
			tileStartX = x + originX;
			tileStartY = y + originY;
			
			if (dual) {
				if (useA) {
					gfxB.drawImage(bufA, fillCols * TILE_W, fillRows * TILE_H, Graphics.TOP | Graphics.LEFT); 
					buf = bufB;
					gfx = gfxB;
				} else {
					gfxA.drawImage(bufB, fillCols * TILE_W, fillRows * TILE_H, Graphics.TOP | Graphics.LEFT);
					buf = bufA;
					gfx = gfxA;
				}
				useA = !useA;
			} else {
				/*int dstX = 0; // note: this implementation works great on everything except 6230i
				int dstY = 0;
				int srcX = 0;
				int srcY = 0;
				int srcW = tileCols * TILE_W;
				int srcH = tileRows * TILE_H;
				if (fillCols > 0) {
					dstX  =  fillCols * TILE_W;
					srcW -=  dstX;
				} else {
					srcX  = -fillCols * TILE_W;
					srcW -=  srcX;
				}
				if (fillRows > 0) {
					dstY  =  fillRows * TILE_H;
					srcH -=  dstY;
				} else {
					srcY  = -fillRows * TILE_H;
					srcH -=  srcY;
				}
				if (srcW > 0 && srcH > 0) {
					gfx.copyArea(srcX, srcY, srcW, srcH, dstX, dstY, Graphics.TOP | Graphics.LEFT);
				}*/
				gfx.copyArea(0, 0, bufW, bufH, fillCols * TILE_W, fillRows * TILE_H, Graphics.TOP | Graphics.LEFT);
			}
			
			int startCol = tileStartX / TILE_W;
			int startRow = tileStartY / TILE_H;
			if (fillCols != 0) {
				if (fillCols < 0) {
					fillCols = -fillCols;
					layout(startCol + tileCols - fillCols, startRow, fillCols, tileRows);
				} else {
					layout(startCol, startRow, fillCols, tileRows);
					startCol += fillCols;
				}
			}
			if (fillRows != 0) {
				if (fillRows < 0) {
					layout(startCol, startRow + tileRows + fillRows, tileCols - fillCols, -fillRows);
				} else {
					layout(startCol, startRow, tileCols - fillCols, fillRows);
				}
			}
		}
		
		lastX = x;
		lastY = y;
	}
	
	/**
	 *	Cycles the layer's animated tiles and updates them on the buffer.
	 */
	public final void cycle() {
		super.cycle();
		
		int startCol = tileStartX / TILE_W;
		int startRow = tileStartY / TILE_H;
		for (int bufRow = 0; bufRow < tileRows; bufRow++) {
			int mapRow = getTileY(startRow);
			if (activeRow[mapRow] != 0) {
				int currentBufCol = startCol;
				for (int bufCol = 0; bufCol < tileCols; bufCol++) {
					int mapCol = getTileX(currentBufCol);
					if (map[mapRow][mapCol] < 0) {
						drawTile(gfx, mapCol, mapRow, bufCol * TILE_W, bufRow * TILE_H);
					}
					currentBufCol++;
				}
			}
			startRow++;
		}
	}
	
	/**
	 *	Draws the tiled layer.
	 */
	public final void paint(Graphics g, int x, int y) {
		g.drawRegion(buf, TILE_W - originX, TILE_H - originY, viewW, viewH, 0, x + posX, y + posY, Graphics.TOP | Graphics.LEFT);
	}
	
	/*************************** copyArea() tests ***************************/
	
	/**
	 *	<code>true</code> if the <code>copyArea()</code> test has not yet run.
	 *
	 *	@see copyAreaHasBugs
	 */
	private static boolean copyAreaUntested = true;
	
	/**
	 *	Result of the <code>copyArea()</code> test (<code>true</code> if the
	 *	bug exists in this Java implementation.
	 *
	 *	@see copyAreaHasBugs
	 */
	private static boolean copyAreaResult = false;
	
	/**
	 *	Width of the temporary image used to test <code>copyArea()</code>.
	 */
	private static final int COPY_AREA_TEST_W =  4;
	
	/**
	 *	Height of the temporary image used to test <code>copyArea()</code>.
	 */
	private static final int COPY_AREA_TEST_H = 24;
	
	/**
	 *	Tests if <code>copyArea()</code> has bugs. Many phones have problems
	 *	with copying overlapping areas, with results ranging from garbage to
	 *	repetition.
	 */
	protected static boolean copyAreaHasBugs() {
		if (copyAreaUntested) {
			/*
			 *	The test only needs performing once so the result is cached.
			 */
			copyAreaUntested = false;
			
			/*
			 *	Creates a simple coloured pattern split into three sections.
			 */
			int[] rgb = new int[COPY_AREA_TEST_W * COPY_AREA_TEST_H + COPY_AREA_TEST_H];
			for (int n = 0; n < COPY_AREA_TEST_W * COPY_AREA_TEST_H + COPY_AREA_TEST_H; n++) {
				int val = ((n & 4) != 0) ? 0x000000 : 0xFF0000;
				val    |= ((n & 2) != 0) ? 0x000000 : 0x00FF00;
				val    |= ((n & 1) != 0) ? 0x000000 : 0x0000FF;
				rgb[n] = 0xFF000000 |  ((n >= 8 * COPY_AREA_TEST_W + 8 && n < 16 * COPY_AREA_TEST_W + 16) ? val : ~val);
			}
			
			Image img = Image.createImage(COPY_AREA_TEST_W, COPY_AREA_TEST_H);
			Graphics gfx = img.getGraphics();
			gfx.drawRGB(rgb, 0, COPY_AREA_TEST_W + 1, 0, 0, COPY_AREA_TEST_W, COPY_AREA_TEST_H, false);
			
			/*
			 *	The first copyArea moves an overlapping section from the top
			 *	to the bottom, which will fail on most phones that have the
			 *	problem. The second goes from bottom to top, which catches out
			 *	any that simply perform the copy in reverse. The third moves a
			 *	section without overlap, partly to test if that itself works,
			 *	but also to ensure we're left with an inverse of the image we
			 *	started with.
			 */
			gfx.copyArea(0, 0,  COPY_AREA_TEST_W, 16, 0, 8,  Graphics.TOP | Graphics.LEFT);
			gfx.copyArea(0, 16, COPY_AREA_TEST_W,  8, 0, 0,  Graphics.TOP | Graphics.LEFT);
			gfx.copyArea(0, 0,  COPY_AREA_TEST_W,  8, 0, 16, Graphics.TOP | Graphics.LEFT);
			
			img.getRGB(rgb, 0, COPY_AREA_TEST_W + 1, 0, 0, COPY_AREA_TEST_W, COPY_AREA_TEST_H);
			for (int n = 0; n < COPY_AREA_TEST_W * COPY_AREA_TEST_H + COPY_AREA_TEST_H; n++) {
				int val = ((n & 4) != 0) ? 0x000000 : 0xFF0000;
				val    |= ((n & 2) != 0) ? 0x000000 : 0x00FF00;
				val    |= ((n & 1) != 0) ? 0x000000 : 0x0000FF;
				if ((n % (COPY_AREA_TEST_W + 1)) != COPY_AREA_TEST_W) {
					/*
					 *	Tests every pixel in the actual image region to see if
					 *	it is a colour inverse of when it started, this being
					 *	the correct result.
					 */
					if (rgb[n] != (0xFF000000 |  ((n >= 8 * COPY_AREA_TEST_W + 8 && n < 16 * COPY_AREA_TEST_W + 16) ? ~val : val))) {
						copyAreaResult = true;
						if (DEBUG) {
							System.out.println("Note: device has copyArea() bug");
						}
						break;
					}
				}
			}
		}
		return copyAreaResult;
	}
	
	/*********************** Shared buffer creation *************************/
	
	/**
	 *	Maximum number of shared buffers.
	 */
	private static final int MAX_BUFFERS = 4;
	private static final Image[] sharedBufferA = new Image[MAX_BUFFERS];
	private static final Image[] sharedBufferB = new Image[MAX_BUFFERS];
	private static int numSharedBuffersA = 0;
	private static int numSharedBuffersB = 0;
	
	/**
	 *	Whether to override <code>createBuffer</code>'s exclusive flag.
	 */
	private static boolean forceExcl = false;
	
	/**
	 *	Enables overriding <code>createBuffer</code>'s exclusive flag. Only
	 *	useful when multiple games/apps are running in the same VM (which is
	 *	unlikely, except for testing purposes).
	 */
	static final void forceExclusiveBuffers(boolean forceExcl) {
		BufferedTiledLayer.forceExcl = forceExcl;
	}
	
	/**
	 *	By passing all buffer creation through here the tile layers can share
	 *	buffers if they're never on screen together at the same time.
	 *
	 *	@param exclusive whether exclusive use is required
	 */
	static final Image createBuffer(int w, int h, boolean exclusive) {
		return createBuffer(w, h, exclusive, true);
	}
	
	/**
	 *	@param isBufA whether this is the 'a' or 'b' buffer when two are used
	 *	@see #createBuffer(int w, int h, boolean exclusive)
	 */
	static final Image createBuffer(int w, int h, boolean exclusive, boolean isBufA) {
		if (forceExcl || exclusive) {
			if (DEBUG) {
				System.out.println("Creating exclusive buffer");
			}
			return Image.createImage(w, h);
		}
		
		Image[] sharedBuffer = (isBufA ? sharedBufferA : sharedBufferB);
		for (int n = (isBufA ? numSharedBuffersA : numSharedBuffersB) - 1; n >= 0; n--) {
			if (sharedBuffer[n].getWidth() >= w && sharedBuffer[n].getHeight() >= h) {
				if (DEBUG) {
					System.out.println("Found eligible shared buffer");
				}
				return sharedBuffer[n];
			}
		}
		if (DEBUG) {
			System.out.println("Creating new shared buffer");
		}
		return sharedBuffer[(isBufA ? numSharedBuffersA++ : numSharedBuffersB++)] = Image.createImage(w, h);
	}
}