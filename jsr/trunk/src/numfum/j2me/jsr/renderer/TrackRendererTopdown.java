package numfum.j2me.jsr.renderer;

import java.io.DataInput;
import java.io.InputStream;
import java.io.IOException;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import numfum.j2me.jsr.Sprite;
import numfum.j2me.jsr.TrackRenderer;
import numfum.j2me.jsr.generic.bkgnd.BufferedCompositeTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.BufferedTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.ContinuousTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.ShuffledTiledOverlay;
import numfum.j2me.jsr.generic.bkgnd.TiledOverlay;
import numfum.j2me.util.Fixed;

/**
 *	Track renderer implementation that draws the floor and sprites in 2D.
 */
public final class TrackRendererTopdown extends TrackRenderer {
	/**
	 *	Furthest visible x-coord in fixed point format.
	 */
	private final int fixedFarX;
	
	/**
	 *	Furthest visible y-coord in fixed point format.
	 */
	private final int fixedFarY;
	
	/**
	 *	Holds a single tile to be transformed before placing on the tile
	 *	sheet.
	 */
	private final int[] tileBuf = new int[TILE_W * TILE_H];
	
	/**
	 *	The tile sheet used by the tiled layer.
	 */
	private final Image tileImg;
	
	/**
	 *	Graphics context for the tile sheet.
	 */
	private final Graphics tileGfx;
	
	private final ContinuousTiledLayer layer;
	
	private final short[] spriteTrans;
	
	private final byte[] spriteOriginX;
	private final byte[] spriteOriginY;
	
	private final int numSpriteFrames;
	
	private final byte[] clipX;
	private final byte[] clipY;
	private final byte[] clipW;
	private final byte[] clipH;
	
	private final Image sprites;
	
	private final int spriteCols;
	private final int spriteRows;
	
	private int lastX, lastY;
	
	/**
	 *	Queue of sorted sprites ready to render.
	 */
	private final Sprite[] spritesToPaint;
	
	/**
	 *	Number of sprites in the render queue.
	 */
	private int numSpritesToPaint = 0;
	
	public TrackRendererTopdown(int viewW, int viewH, int bkgndType, DataInput in, boolean close) throws IOException {
		super(viewW, viewH, in);
		if (DEBUG) {
			System.out.println("Inited Track super class");
		}
		
		fixedFarX = (MAP_COLS * TILE_W - viewW) << Fixed.FIXED_POINT;
		fixedFarY = (MAP_ROWS * TILE_H - viewH) << Fixed.FIXED_POINT;
		
		in.skipBytes(4); // skip bytes required
		
		spriteTrans = defaultSpriteLoader(in, animsprite);
		
		int numTrans = spriteTrans.length;
		spriteOriginX = new byte[numTrans];
		in.readFully(spriteOriginX);
		spriteOriginY = new byte[numTrans];
		in.readFully(spriteOriginY);
		
		numSpriteFrames = in.readShort();
		clipX = new byte[numSpriteFrames];
		in.readFully(clipX);
		clipY = new byte[numSpriteFrames];
		in.readFully(clipY);
		clipW = new byte[numSpriteFrames];
		in.readFully(clipW);
		clipH = new byte[numSpriteFrames];
		in.readFully(clipH);
		
		sprites = loadImage(in);
		
		if (close && in instanceof InputStream) {
			((InputStream) in).close();
		}
		
		tileImg = Image.createImage(TILE_IMG_COLS * TILE_W, TILE_IMG_ROWS * TILE_H);
		tileGfx = tileImg.getGraphics();
		
		switch (bkgndType) {
		case BACKGROUND_DIRECT:
			layer = new TiledOverlay(MAP_COLS, MAP_ROWS, viewW, viewH, tileImg, animtile, this, TiledOverlay.PEER_EXACT);
			break;
		case BACKGROUND_SHUFFLED:
			if (ENABLE_BKGND_SHUFFLED) {
				layer = new ShuffledTiledOverlay(MAP_COLS, MAP_ROWS, viewW, viewH, tileImg, animtile, this);
			} else {
				layer = null;
			}
			break;
		default:
			if (ENABLE_BKGND_BUFFERED) {
				layer = new BufferedTiledLayer(MAP_COLS, MAP_ROWS, viewW, viewH, tileImg, animtile, false, this, false);
			} else {
				layer = null;
			}
		}
		
		if (DEBUG) {
			System.out.println("Created tiled layer");
		}
		
		/*
		 *	Calculate the maximum possible number of sprites on screen per
		 *	frame, then create the buckets to hold them. The sprites are queued
		 *	in the render() method, ready to be drawn in paint().
		 */
		int calcCols = viewW / GRID_W;
		if (viewW % TILE_W != 0) {
			calcCols++;
		}
		spriteCols = calcCols + 3;
		
		int calcRows = viewH / GRID_H;
		if (viewH % TILE_H != 0) {
			calcRows++;
		}
		spriteRows = calcRows + 3;
		
		spritesToPaint = new Sprite[spriteCols * spriteRows + MAX_BLEND_SPRITES];
		for (int n = spritesToPaint.length - 1; n >= 0; n--) {
			spritesToPaint[n] = new Sprite();
		}
	}
	
	protected void loaded(DataInput in) throws IOException {
		BufferedCompositeTiledLayer.skip(in);
		
		int tileIdx = 0;
		for (int row = 0; row < TILE_IMG_ROWS; row++) {
			for (int col = 0; col < TILE_IMG_COLS; col++) {
				for (int y = 0; y < TILE_H; y++) {
					int tileN = y * TILE_W;
					for (int x = 0; x < TILE_W; x++) {
						tileBuf[tileN] = tilePalette[tileFrames[tileIdx][tileN] & 0xFF];
						tileN++;
					}
				}
				tileGfx.drawRGB(tileBuf, 0, TILE_W, col * TILE_W, row * TILE_H, TILE_W, TILE_H, false);
				if (tileIdx < MAX_TILES - 1) {
					tileIdx++;
				}
			}
		}
		
		if (layer instanceof BufferedTiledLayer) {
			((BufferedTiledLayer) layer).resetActiveTiles();
		}
		for (int y = 0; y < MAP_ROWS; y++) {
			for (int x = 0; x < MAP_COLS; x++) {
				layer.setCell(x, y, tilemap[(y & (MAP_ROWS - 1)) << MAP_ROWS_BITS | (x & (MAP_COLS - 1))] & 0xFF);
			}
		}
		if (layer instanceof BufferedTiledLayer) {
			((BufferedTiledLayer) layer).scanActiveRows();
		}
		layer.reset();
		
		lastX = 0;
		lastY = 0;
	}
	
	public void refresh() {
		layer.reset();
		lastX = 0;
		lastY = 0;
	}
	
	public void render(int cameraX, int cameraY, int cameraA, Sprite[] blend, int blendSize, int bump) {
		int camViewX = cameraX - (viewW << (Fixed.FIXED_POINT - 1));
		int camViewY = cameraY - (viewH << (Fixed.FIXED_POINT - 1));
		
		if (camViewX < 0) {
			camViewX = 0;
		}
		if (camViewX > fixedFarX) {
			camViewX = fixedFarX;
		}
		if (camViewY < 0) {
			camViewY = 0;
		}
		if (camViewY > fixedFarY) {
			camViewY = fixedFarY;
		}
		
		int x = camViewX >> Fixed.FIXED_POINT;
		int y = camViewY >> Fixed.FIXED_POINT;
		
		if (lastX != x || lastY != y) {
			layer.moveTo(x, y);
		}
		lastX = x;
		lastY = y;
		
		int startCol = x / GRID_W - 1;
		int startRow = y / GRID_H - 1;
		int colCount = spriteCols;
		int rowCount = spriteRows;
		while (startCol < 0) {
			startCol++;
			colCount--;
		}
		while (startRow < 0) {
			startRow++;
			rowCount--;
		}
		if (startCol + colCount > GRID_COLS) {
			colCount = GRID_COLS - startCol;
		}
		if (startRow + rowCount > GRID_ROWS) {
			rowCount = GRID_ROWS - startRow;
		}
		
		numSpritesToPaint = 0;
		
		for (int row = startRow, rc = rowCount; rc > 0; row++, rc--) {
			for (int col = startCol, cc = colCount; cc > 0; col++, cc--) {
				int gridIdx = row << GRID_ROWS_BITS | col;
				int spriteN = animsprite[spritemap[gridIdx]].getTileIndex();
				if (spriteN != 0) {
					int data = animsprite[spritemap[gridIdx]].data;
					spritesToPaint[numSpritesToPaint++].set(spriteN,
						(((spritePosX[gridIdx] >> Fixed.FIXED_POINT) - x) - ((data >> SPRITE_DATA_ROTL_OGNX) & SPRITE_DATA_MASK_OGNX)) + spriteOriginX[spriteN],
						(((spritePosY[gridIdx] >> Fixed.FIXED_POINT) - y) - ((data >> SPRITE_DATA_ROTL_OGNY) & SPRITE_DATA_MASK_OGNY)) + spriteOriginY[spriteN],
						0, 0);
				}
			}
		}
		
		for (int n = 0; n < blendSize; n++) {
			blend[n].view = false;
			int drawX = (blend[n].x - camViewX) >> Fixed.FIXED_POINT;
			int drawY = (blend[n].y - camViewY) >> Fixed.FIXED_POINT;
			if (drawX > -SPRITE_W && drawX < viewW + SPRITE_W && drawY > -SPRITE_H && drawY < viewH + SPRITE_H) {
				int spriteN = animsprite[blend[n].n].getTileIndex();
				int data = animsprite[blend[n].n].data;
				spritesToPaint[numSpritesToPaint++].set(spriteN,
					(drawX - ((data >> SPRITE_DATA_ROTL_OGNX) & SPRITE_DATA_MASK_OGNX)) + spriteOriginX[spriteN],
					(drawY - ((data >> SPRITE_DATA_ROTL_OGNY) & SPRITE_DATA_MASK_OGNY)) + spriteOriginY[spriteN],
					0, 0);
				blend[n].view = true;
			}
		}
	}
	
	public void cycle() {
		super.cycle();
		layer.cycle();
	}
	
	public boolean isPerspectiveView() {
		return false;
	}
	
	public int getCameraDistance() {
		return -32;
	}
	
	public void paint(Graphics g, int offsetX, int offsetY) {
		layer.paint(g, offsetX, offsetY);
		
		for (int n = 0; n < numSpritesToPaint; n++) {
			drawSprite(g, spritesToPaint[n], offsetX, offsetY);
		}
	}
	
	private void drawSprite(Graphics g, Sprite sprite, int offsetX, int offsetY) {
		int spTrans = spriteTrans[sprite.n];
		int udgs = (spTrans >> SPRITETRANS_ROTL_UDGS) & SPRITETRANS_MASK_UDGS;
		int tran = (spTrans >> SPRITETRANS_ROTL_TRAN) & SPRITETRANS_MASK_TRAN;
		g.drawRegion(sprites, clipX[udgs] & 0xFF, clipY[udgs] & 0xFF,
			clipW[udgs], clipH[udgs], tran,
				sprite.x + offsetX, sprite.y + offsetY,
					Graphics.TOP | Graphics.LEFT);
	}
	
	private static final int TILE_IMG_COLS = MAX_TILES / 8;
	private static final int TILE_IMG_ROWS = MAX_TILES / TILE_IMG_COLS;
	static {
		if (MAX_TILES % 8 != 0) {
			if (DEBUG) {
				throw new ArithmeticException("MAX_TILES must be divisible by 8");
			} else {
				throw new ArithmeticException();
			}
		}
	}
	
	/**
	 *	Maximum number of sprites to be 'blended' with the track objects. The
	 *	figure is calculated from the number of karts and their track effects.
	 */
	public static final int MAX_BLEND_SPRITES = 16;
	
	public static final int SPRITE_W = 32;
	public static final int SPRITE_H = 32;
}