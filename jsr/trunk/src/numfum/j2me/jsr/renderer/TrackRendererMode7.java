package numfum.j2me.jsr.renderer;

import java.io.DataInput;
import java.io.InputStream;
import java.io.IOException;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import numfum.j2me.jsr.Constants;
import numfum.j2me.jsr.Sprite;
import numfum.j2me.jsr.TrackRenderer;
import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.bkgnd.BufferedCompositeTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.BufferedTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.ContinuousTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.ShuffledTiledOverlay;
import numfum.j2me.jsr.generic.bkgnd.TiledOverlay;
import numfum.j2me.util.Fixed;
import numfum.j2me.util.QuickSort;
import numfum.j2me.util.Vector2D;

/**
 *	A track renderer implementation that uses a mode-7 style view.
 */
public final class TrackRendererMode7 extends TrackRenderer {
	private final int floorW;
	private final int floorH;
	private final int midway;
	private final int bkgndH;
	
	/**
	 *	Width of the buffer rounded up to the nearest DRAW_CHUNK.
	 */
	private final int totalW;
	
	/**
	 *	Height of the buffer (floorH + bkgndH);
	 */
	private final int totalH;
	
	/**
	 *	Number of pixels not used at the side of the buffer (totalW - floorW).
	 */
	private final int remain;
	
	private final int[] buffer;
	
	private final int[] bufferLookup;
	
	private final int bkgndOffset;
	
	private final boolean lowResRender;
	
	private final short[] spriteTrans;
	
	/**
	 *	Colour palette for the sprites in ARGB format.
	 */
	private final int[] spritePalette = new int[256];
	
	private final int numSpriteFrames;
	private final byte[]   spriteOffsetY;
	private final byte[]   spriteLengthY;
	private final byte[][] spriteOffsetX;
	private final byte[][] spriteLengthX;
	private final int[][] spriteDataOffset;
	private final byte[] spriteData;
	
	/**
	 *	Precalculated offsets per pixel column for each screen row. Using
	 *	a byte for storage is adequate for most phone screen sizes, although
	 *	going up beyond 240x320 this would need changing to a short.
	 */
	private final short[][] spriteScreenOffsetX;
	
	private final int[] pixelSize;
	private final short[] revRowTbl;
	private final int revRowTblShift;
	private final int revRowTblLength;
	
	private final byte[][] fieldOfViewCols = new byte[32][];
	private final byte[][] fieldOfViewRows = new byte[32][];
	
	/**
	 *	Scratch array used for sorting the displayed sprites. The maximum
	 *	number of sprites in any 32x32 is the total karts, a track object
	 *	per kart, and one static track object.
	 */
	private final Sprite[] sorter = new Sprite[MAX_BLEND_SPRITES + 1];
	
	private final int viewPitch;
	private final int viewAngle;
	private final int viewVdist;
	private final int viewCdist;
	private final int viewScale;
	private final int viewHorzn;
	
	/****************************** Background ******************************/
	
	/**
	 *	Whether the background is one of the composite types.
	 */
	private final boolean compBkgnd;
	
	/**
	 *	The offset used when drawing the background layers in order to line
	 *	them up with the floor.
	 */
	private final int bkgndY;
	
	private final ContinuousTiledLayer[] bkgnd;
	private final int numBkgndLayers;
	
	private int drawFloorAtY = 0;
	
	private int[] drawBkgndAtY = new int[BACKGROUND_LAYERS];
	
	private int[] bkgndOffsetY = new int[BACKGROUND_LAYERS];
	
	/**
	 *	Previous camera angle (in fixed point format). Used to calculate the
	 *	number of pixels to shift the background layers.
	 */
	private int lastA = 0;
	
	public TrackRendererMode7(int viewW, int viewH, int floorH, boolean lowResRender, int bkgndType, DataInput in, boolean close) throws IOException {
		super(viewW, viewH, in);
		
		this.floorW = viewW;//floorW;
		this.floorH = floorH;
		midway = floorW / 2;
		
		this.lowResRender = lowResRender;
		
		in.skipBytes(in.readInt() + 4); // skip any 2D data
		
		spriteTrans = defaultSpriteLoader(in, animsprite);
		loadPalette(in, spritePalette);
		
		if (DEBUG) {
			System.out.println("Sprite palette loaded");
			System.gc();
		}
		
		numSpriteFrames = in.readUnsignedShort();
		
		spriteOffsetY = new byte[numSpriteFrames];
		spriteLengthY = new byte[numSpriteFrames];
		spriteOffsetX = new byte[numSpriteFrames][];
		spriteLengthX = new byte[numSpriteFrames][];
		
		spriteDataOffset = new int[numSpriteFrames][];
		int spriteDataCount = 0;
		
		if (DEBUG) {
			System.out.println("Created sprite offset buckets");
			System.gc();
		}
		
		for (int n = 0; n < numSpriteFrames; n++) {
			spriteOffsetY[n] = in.readByte();
			int lengthY = in.readUnsignedByte();
			spriteLengthY[n] = (byte) lengthY;
			
			spriteOffsetX[n] = new byte[lengthY];
			spriteLengthX[n] = new byte[lengthY];
			spriteDataOffset[n] = new int[lengthY];
			for (int i = 0; i < lengthY; i++) {
				spriteOffsetX[n][i] = in.readByte();
				int lengthX = in.readUnsignedByte();
				spriteLengthX[n][i] = (byte) lengthX;
				spriteDataOffset[n][i] = spriteDataCount;
				spriteDataCount += lengthX;
			}
		}
		
		spriteData = new byte[spriteDataCount];
		in.readFully(spriteData);
		
		/*
		 *	Background tiles.
		 */
		AnimTile[] anims = AnimTile.load(in, false);
		Image image = loadImage(in);
		
		if (in.readByte() != 7 + 12) {
			if (DEBUG) {
				throw new IOException("Render settings not found");
			} else {
				throw new IOException();
			}
		}
		viewPitch = in.readUnsignedByte();
		viewAngle = in.readUnsignedByte();
		viewVdist = in.readShort();
		viewCdist = in.readUnsignedByte();
		viewScale = in.readUnsignedByte();
		viewHorzn = in.readUnsignedByte();
		
		if (close && in instanceof InputStream) {
			((InputStream) in).close();
		}
		
		/********************************************************************/
		
		if (DEBUG) {
			System.out.println("Initialising Floor");
			System.gc();
		}
		
		pixelSize = new int[floorH + ODRAW];
		/*
		 *	The extreme left and right points of the view frustrum are
		 *	calculated from a zero origin using the same fixed maths
		 *	function used to render the view.
		 */
		int extremeLX = Fixed.mul(Fixed.cos(Fixed.QUARTER_CIRCLE - viewAngle), viewVdist);
		int extremeLY = Fixed.mul(Fixed.sin(Fixed.QUARTER_CIRCLE - viewAngle), viewVdist);
		int extremeRX = Fixed.mul(Fixed.cos(Fixed.QUARTER_CIRCLE + viewAngle), viewVdist);
		
		/*
		 *	Rows start at the horizon and work their way down the screen.
		 *	For each row the y position on the floor map is calculated -
		 *	this will be used for determining the screen row of game
		 *	objects. The pixel size per row is then calculated from the
		 *	left and right frustrum extremes.
		 */
		int[] rowLookup = new int[floorH + ODRAW];
		for (int row = floorH + ODRAW - 1; row >= 0; row--) {
			int oneOverZ = (Fixed.ONE << viewScale) / (row + viewPitch);
			rowLookup[row] =   extremeLY              * oneOverZ;
			pixelSize[row] = ((extremeLX - extremeRX) * oneOverZ) / floorW;
		}
		
		int bestBkgndH = 0;
		for (int row = floorH - 1; row >= viewHorzn; row--) {
			int limit = (((SPRITE_H << Fixed.FIXED_POINT) / pixelSize[row]) >> SPRITE_SCALE) - row;
			if (limit > bestBkgndH) {
				bestBkgndH = limit;
			}
		
		}
		bkgndH = bestBkgndH;
		
		if (DEBUG) {
			System.out.println("floorH: " + floorH + " (bkgndH: " + bkgndH + ")");
		}
		
		/*
		 *	A reverse row lookup needs creating but before doing so the
		 *	values have to be scaled to keep the table small. Decreasing
		 *	values are used for the left shift until one is found that
		 *	maintains a unique value per point.
		 */
		int shift;
		for (shift = Fixed.FIXED_POINT; shift > 0; shift--) {
			int n = rowLookup.length - 2;
			while (n >= 0) {
				if ((rowLookup[n] >> shift) == (rowLookup[n + 1] >> shift)) {
					break;
				}
				n--;
			}
			if (n < 0) {
				break;
			}
		}
		
		/*
		 *	The reverse lookup then maps floor y coords to screen rows.
		 */
		int revRowTblIdx = rowLookup[0] >> shift;
		revRowTblLength = revRowTblIdx + 1;
		revRowTbl = new short[revRowTblLength];
		for (int n = 0; n < rowLookup.length; n++) {
			do {
				/*
				 *	The table uses bytes for screen coords so the maximum
				 *	view height can be 255.
				 */
				revRowTbl[revRowTblIdx] = (short) n;
				revRowTblIdx--;
			} while ((rowLookup[n] >> shift) < revRowTblIdx);
		}
		/*
		 *	Any points on the floor not accounted for are set to a row
		 *	beyond those actually drawn.
		 */
		for (;revRowTblIdx >= 0; revRowTblIdx--) {
			revRowTbl[revRowTblIdx] = (short) (floorH + ODRAW);
		}
		
		revRowTblShift = shift;
		
		/********************************************************************/
		
		totalW = (floorW / DRAW_CHUNK + (floorW % DRAW_CHUNK != 0 ? 1 : 0)) * DRAW_CHUNK;
		totalH =  floorH + bkgndH;
		
		remain = totalW - floorW;
		
		/*
		 *	Note the buffer is one line larger than required due to potential
		 *	problems with the Siemens implementation of drawRGB().
		 */
		buffer = new int[totalW * totalH + totalW];
		bufferLookup = new int[totalH];
		for (int n = 0; n < totalH; n++) {
			bufferLookup[n] = n * totalW;
		}
		
		bkgndOffset = bufferLookup[bkgndH];
		
		/********************************************************************/
		
		createFieldOfView(viewPitch, viewAngle, viewVdist, viewCdist, viewScale, viewHorzn, floorH, fieldOfViewCols, fieldOfViewRows);
		
		spriteScreenOffsetX = new short[floorH + ODRAW][SPRITE_W];
		for (int row = floorH + ODRAW - 1; row >= 0; row--) {
			int scaleX = pixelSize[row] << SPRITE_SCALE;
			for (int offsetX = 0; offsetX < SPRITE_W; offsetX++) {
				int screenX = 0;
				for (int pixelX = 0; pixelX < SPRITE_W << Fixed.FIXED_POINT; pixelX += scaleX) {
					if ((pixelX >> Fixed.FIXED_POINT) >= offsetX) {
						break;
					}
					screenX++;
				}
				spriteScreenOffsetX[row][offsetX] = (short) screenX;
			}
		}
		
		/********************************************************************/
		
		if (DEBUG) {
			System.out.println("End of Floor initialisation");
			System.gc();
		}
		
		for (int n = sorter.length - 1; n >= 0; n--) {
			sorter[n] = new Sprite();
		}
		
		bkgnd = createBackgrounds(bkgndType, viewW, viewH - floorH + ContinuousTiledLayer.TILE_H, image, anims);
		numBkgndLayers = bkgnd.length;
		bkgndY = (BKGND_ROWS - 1) * ContinuousTiledLayer.TILE_H - viewH + floorH;
		compBkgnd = bkgndType == BACKGROUND_COMPOSITE;
	}
	
	protected void loaded(DataInput in) throws IOException {
		if (compBkgnd) {
			bkgnd[0].load(in);
		} else {
			int numLayers = in.readUnsignedByte();
			for (int n = 0; n < numLayers; n++) {
				bkgndOffsetY[n] = in.readByte();
				bkgnd[n].load(in);
			}
		}
		bkgnd[0].moveTo(0, bkgndY);
	}
	
	public void refresh() {
		for (int n = 0; n < numBkgndLayers; n++) {
			bkgnd[n].reset();
		}
		bkgnd[0].moveTo(0, bkgndY);
	}
	
	private final void renderFloorInOnes(int cameraX, int cameraY, int cameraA) {
  		final int[] buffer = this.buffer;
		final byte[] tilemap = this.tilemap;
		final byte[][] tileset = this.tileset;
		final int[] tilePalette = this.tilePalette;
		
		int extremeLX = Fixed.mul(Fixed.cos(cameraA - viewAngle), viewVdist);
		int extremeLY = Fixed.mul(Fixed.sin(cameraA - viewAngle), viewVdist);
		int extremeRX = Fixed.mul(Fixed.cos(cameraA + viewAngle), viewVdist);
		int extremeRY = Fixed.mul(Fixed.sin(cameraA + viewAngle), viewVdist);
		
		int pixel = 0;
		for (int col = bkgndH * totalW; col > 0; col -= DRAW_CHUNK) {
			buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = 0;
		}
		
		pixel = bufferLookup[totalH - 1] + (floorW - 1);
		
		for (int row = floorH - 1; row >= 0; row--) {
			int oneOverZ = (Fixed.ONE << viewScale) / (row + viewPitch);
			
			int xl = extremeLX * oneOverZ + cameraX;
			int yl = extremeLY * oneOverZ + cameraY;
			int xr = extremeRX * oneOverZ + cameraX;
			int yr = extremeRY * oneOverZ + cameraY;
			
			int deltaX = (xr - xl) / floorW;
			int deltaY = (yr - yl) / floorW;
			
			for (int col = floorW; col > 0; col -= DRAW_CHUNK) {
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
			}
		}
	}
	
	private final void renderFloorInTwos(int cameraX, int cameraY, int cameraA) {
  		final int[] buffer = this.buffer;
		final int[] tilePalette = this.tilePalette;
		final byte[][] tileset = this.tileset;
		final byte[] tilemap = this.tilemap;
		
		int extremeLX = Fixed.mul(Fixed.cos(cameraA - viewAngle), viewVdist);
		int extremeLY = Fixed.mul(Fixed.sin(cameraA - viewAngle), viewVdist);
		int extremeRX = Fixed.mul(Fixed.cos(cameraA + viewAngle), viewVdist);
		int extremeRY = Fixed.mul(Fixed.sin(cameraA + viewAngle), viewVdist);
		
		int pixel = 0;
		for (int col = bkgndH * totalW; col > 0; col -= DRAW_CHUNK) {
			buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = buffer[pixel++] = buffer[pixel++]
				= buffer[pixel++] = 0;
		}
		
		pixel = bufferLookup[totalH - 1] + (floorW - 1);
		
		for (int row = floorH - 1; row >= 0; row -= 2) {
			int oneOverZ = (Fixed.ONE << viewScale) / (row + viewPitch);
			
			int xl = extremeLX * oneOverZ + cameraX;
			int yl = extremeLY * oneOverZ + cameraY;
			int xr = extremeRX * oneOverZ + cameraX;
			int yr = extremeRY * oneOverZ + cameraY;
			
			int deltaX = ((xr - xl) << 1) / floorW;
			int deltaY = ((yr - yl) << 1) / floorW;
			
			for (int col = floorW; col > 0; col -= DRAW_CHUNK) {
				buffer[pixel--] = buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				
				buffer[pixel--] = buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				
				buffer[pixel--] = buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				
				buffer[pixel--] = buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				
				buffer[pixel--] = buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				
				buffer[pixel--] = buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				
				buffer[pixel--] = buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
				
				buffer[pixel--] = buffer[pixel--] = tilePalette[tileset[tilemap[
					(((yr -= deltaY) >> (Fixed.FIXED_POINT + TILE_H_BITS)) & (MAP_ROWS - 1)) << MAP_COLS_BITS |
					(((xr -= deltaX) >> (Fixed.FIXED_POINT + TILE_W_BITS)) & (MAP_COLS - 1))] & 0xFF][
					((yr >> Fixed.FIXED_POINT) & (TILE_H - 1)) << TILE_W_BITS |
					((xr >> Fixed.FIXED_POINT) & (TILE_W - 1))] & 0xFF];
			}
			if (row != 0) {
				System.arraycopy(buffer, pixel + remain + 1, buffer, (pixel -= totalW) + remain + 1, floorW);
			}
			
		}
	}
	
	public void render(int cameraX, int cameraY, int cameraA, Sprite[] blend, int blendSize, int bump) {
		if (lowResRender) {
			renderFloorInTwos(cameraX, cameraY, cameraA);
		} else {
			renderFloorInOnes(cameraX, cameraY, cameraA);
		}
  		
		final int cosAngle = Fixed.cos(cameraA + Fixed.QUARTER_CIRCLE);
		final int sinAngle = Fixed.sin(cameraA + Fixed.QUARTER_CIRCLE);
		
		final int cameraCol = cameraX >> (Fixed.FIXED_POINT + GRID_W_BITS);
		final int cameraRow = cameraY >> (Fixed.FIXED_POINT + GRID_H_BITS);
		
		/*
		 *	Set where the overlay sprites are in the coarse sprite grid.
		 */
		for (int n = blendSize - 1; n >= 0; n--) {
			Sprite blendN = blend[n];
			blendN.col = blend[n].x >> (GRID_W_BITS + Fixed.FIXED_POINT);
			blendN.row = blend[n].y >> (GRID_H_BITS + Fixed.FIXED_POINT);
			blendN.view = false;
		}
		
		int angleIndex = (cameraA >> 1) & 31;
		if (cameraA < Fixed.QUARTER_CIRCLE * 2) {
			if (cameraA >= Fixed.QUARTER_CIRCLE) {
				angleIndex = 31 - angleIndex;
			}
		} else {
			if (cameraA >= Fixed.QUARTER_CIRCLE * 3) {
				angleIndex = 31 - angleIndex;
			}
		}
		final byte[] visibleCol = fieldOfViewCols[angleIndex];
		final byte[] visibleRow = fieldOfViewRows[angleIndex];
		
		for (int n = visibleCol.length - 1; n >= 0; n--) {
			int col = cameraCol;
			int row = cameraRow;
			
			if (cameraA < Fixed.QUARTER_CIRCLE * 2) {
				if (cameraA < Fixed.QUARTER_CIRCLE) {
					col += visibleCol[n];
					row += visibleRow[n];
				} else {
					col -= visibleCol[n];
					row += visibleRow[n];
				}
			} else {
				if (cameraA < Fixed.QUARTER_CIRCLE * 3) {
					col -= visibleCol[n];
					row -= visibleRow[n];
				} else {
					col += visibleCol[n];
					row -= visibleRow[n];
				}
			}
			
			int numToSort = 0;
			if (col >= 0 && col < GRID_COLS && row >= 0 && row < GRID_ROWS) {
				int spriteN = spritemap[row << GRID_ROWS_BITS | col];
				if (spriteN != 0) {
					int fineX = spritePosX[row << GRID_ROWS_BITS | col] - cameraX;
					int fineY = spritePosY[row << GRID_ROWS_BITS | col] - cameraY;
					if (((animsprite[spriteN].data >> SPRITE_DATA_ROTL_BOTM) & SPRITE_DATA_MASK_BOTM) != 0) {
						drawSprite(spriteN, fineX, fineY, sinAngle, cosAngle, 0);
					} else {
						sorter[numToSort++].set(spriteN, fineX, fineY, 0);
					}
				}
				for (int i = blendSize - 1; i >= 0; i--) {
					Sprite blendI = blend[i];
					if (col == blendI.col && row == blendI.row) {
						sorter[numToSort  ].set(blendI.n, blendI.x - cameraX, blendI.y - cameraY, blendI.data);
						sorter[numToSort++].d += blendI.d;
						blendI.view = true;
					}
				
				}
				
				if (numToSort > 0) {
					if (numToSort == 1) {
						Sprite sorterI = sorter[0];
						drawSprite(sorterI.n, sorterI.x, sorterI.y, sinAngle, cosAngle, sorterI.data);
					} else {
						QuickSort.sort(sorter, 0, numToSort - 1);
						for (int i = numToSort - 1; i >= 0; i--) {
							Sprite sorterI = sorter[i];
							drawSprite(sorterI.n, sorterI.x, sorterI.y, sinAngle, cosAngle, sorterI.data);
						}
					}
				}
			}
		}
		
		drawFloorAtY = viewH - totalH + bump;
		if (compBkgnd) {
			drawBkgndAtY[0] = bkgndOffsetY[0] * ContinuousTiledLayer.TILE_H + bump;
		} else {
			for (int n = 1; n < numBkgndLayers; n++) {
				drawBkgndAtY[n] = bkgndOffsetY[n] * ContinuousTiledLayer.TILE_H;
				if (bkgndOffsetY[n] < 0) {
					drawBkgndAtY[n] += viewH - floorH - ContinuousTiledLayer.TILE_H;
					if (n != 0 && bkgndOffsetY[n] > -3) {
						drawBkgndAtY[n] += bump / (BACKGROUND_LAYERS - n);
					}
				}
			}
		}
		
		byte camMove = (byte) (cameraA - lastA);
		if (camMove != 0) {
			for (int n = 0; n < numBkgndLayers; n++) {
				bkgnd[n].moveBy(camMove * (n + 1), 0);
			}
		}
		lastA = cameraA;
	}
	
	private final void drawSprite(int objN, int objX, int objY, int sinAngle, int cosAngle, int extra) {
		final int[] buffer = this.buffer;
		final int[] spritePalette = this.spritePalette;
		final byte[] spriteData = this.spriteData;
		
		int screenY = floorH + ODRAW;
		int lookupY = (Fixed.mul(objX, sinAngle) - Fixed.mul(objY, cosAngle)) >> revRowTblShift;
		if (lookupY >= 0 && lookupY < revRowTblLength) {
			screenY = revRowTbl[lookupY];
		}
		if (screenY >= 0 && screenY < floorH + ODRAW) {
			int pixsize = pixelSize[screenY];
			int rawdata = animsprite[objN].data;
			int sptrans = spriteTrans[animsprite[objN].getTileIndex()];
			
			int s = pixsize << SPRITE_SCALE;
			int x = (Fixed.mul(objX, cosAngle) + Fixed.mul(objY, sinAngle) - (((rawdata >> SPRITE_DATA_ROTL_OGNX) & SPRITE_DATA_MASK_OGNX) << Fixed.FIXED_POINT - SPRITE_SCALE)) / pixsize + midway;
			if (x < (-SPRITE_W << Fixed.FIXED_POINT) / s || x >= floorW) { // sprite won't appear in view
				return;
			}
			
			int bumpY = (extra >> Sprite.DATA_ROTL_BUMP_Y) & Sprite.DATA_MASK_BUMP_Y;
			int buzzY = (extra >> Sprite.DATA_ROTL_BUZZ_Y) & Sprite.DATA_MASK_BUZZ_Y;
			if (bumpY != 0 && (bumpY & 1) == buzzY) {
				buzzY = -buzzY;
			}
			
			int n = (sptrans >> SPRITETRANS_ROTL_UDGS) & SPRITETRANS_MASK_UDGS;
			int y = screenY - ((((rawdata >> SPRITE_DATA_ROTL_OGNY) & SPRITE_DATA_MASK_OGNY) + bumpY) << Fixed.FIXED_POINT - SPRITE_SCALE) / pixsize + bkgndH + (spriteOffsetY[n] << Fixed.FIXED_POINT) / s - buzzY;
			
			
			short[] screenOffsetX = spriteScreenOffsetX[screenY];
			byte[] spriteLengthX = this.spriteLengthX[n];
			byte[] spriteOffsetX = this.spriteOffsetX[n];
			
			int[]  spriteDataOffset = this.spriteDataOffset[n];
			
			int scaledY = 0;
			if (((sptrans >> SPRITETRANS_ROTL_TRAN) & SPRITETRANS_MASK_TRAN) == 0) {
				for (int countY = spriteLengthY[n] << Fixed.FIXED_POINT; countY > 0; countY -= s) {
					if (y >= totalH) {
						return; // as we're drawing top down there's no more visible pixels
					}
					if (y >= 0) { // don't draw outside the buffer
						int spriteY = scaledY >> Fixed.FIXED_POINT;
						
						int screenX = x + screenOffsetX[spriteOffsetX[spriteY]];
						int bufferX = bufferLookup[y] + screenX;
						
						int spriteL = spriteDataOffset[spriteY];
						int spriteN = 0;
						
						for (int countX = spriteLengthX[spriteY] << Fixed.FIXED_POINT; countX > 0; countX -= s) {
							if (screenX >= floorW) { // sprite is now drawing off-screen so skip to the next line
								break;
							}
							if (screenX >= 0) {
								int idx  = spriteData[spriteL + (spriteN >> Fixed.FIXED_POINT)];
								if (idx != 0) {
									buffer[bufferX] = spritePalette[idx & 0xFF];
								}
							}
							screenX++;
							bufferX++;
							spriteN += s;
						}
					}
					y++;
					scaledY += s;
				}
			} else {
				for (int countY = spriteLengthY[n] << Fixed.FIXED_POINT; countY > 0; countY -= s) {
					if (y >= totalH) {
						return; // as we're drawing top down there's no more visible pixels
					}
					if (y >= 0) { // don't draw outside the buffer
						int spriteY = scaledY >> Fixed.FIXED_POINT;
						
						int screenX = x + screenOffsetX[SPRITE_W - spriteOffsetX[spriteY] - 1];
						int bufferX = bufferLookup[y] + screenX;
						
						int spriteL = spriteDataOffset[spriteY];
						int spriteN = 0;
						
						for (int countX = spriteLengthX[spriteY] << Fixed.FIXED_POINT; countX > 0; countX -= s) {
							if (screenX < 0) {
								break;
							}
							if (screenX < floorW) {
								int idx  = spriteData[spriteL + (spriteN >> Fixed.FIXED_POINT)];
								if (idx != 0) {
									buffer[bufferX] = spritePalette[idx & 0xFF];
								}
							}
							screenX--;
							bufferX--;
							spriteN += s;
						}
					}
					y++;
					scaledY += s;
				}
			}
		}
	}
	
	public void cycle() {
		super.cycle();
		for (int n = 0; n < numBkgndLayers; n++) {
			bkgnd[n].cycle();
		}
	}
	
	public boolean isPerspectiveView() {
		return true;
	}
	
	public int getCameraDistance() {
		return viewCdist;
	}
	
	public void paint(Graphics g, int offsetX, int offsetY) {
		for (int n = 0; n < numBkgndLayers; n++) {
			bkgnd[n].paint(g, offsetX, offsetY + drawBkgndAtY[n]);
			
		}
		g.drawRGB(buffer, 0,           totalW, offsetX, offsetY + drawFloorAtY,          floorW, bkgndH, true); // MIDP2!
		g.drawRGB(buffer, bkgndOffset, totalW, offsetX, offsetY + drawFloorAtY + bkgndH, floorW, floorH, false);
	}
	
	/**
	 *	Precalculates what part of the sprite grid is in view and sorts
	 *	the draw order accordingly.
	 *
	 *	@return maximum number of sprites visible from all of the views
	 */
	protected static int createFieldOfView(int viewPitch, int viewAngle, int viewVdist, int viewCdist, int viewScale, int viewHorzn, int floorH, byte[][] fieldOfViewCols, byte[][] fieldOfViewRows) {
		/*
		 *	A pseudo pixel buffer. Offset so that 16,16 is the origin.
		 */
		byte[][] grid = new byte[GRID_ROWS][GRID_COLS];
		
		/*
		 *	Temporary array of points from the above buffer.
		 */
		Vector2D[] point = new Vector2D[MAX_VISIBLE_SPRITES];
		for (int n = 0; n < MAX_VISIBLE_SPRITES; n++) {
			point[n] = new Vector2D(0, 0);
		}
		
		int actualMaxVisible = 0;
		
		/*
		 *	Temporary array for holding the quad's points drawn into the
		 *	above buffer. Note: these Vector2D objects aren't in fixed
		 *	point format.
		 */
		Vector2D[] quad = new Vector2D[4];
		for (int n = 0; n < 4; n++) {
			quad[n] = new Vector2D();
		}
		
		/*
		 *	A quarter circle (usually 64 points) is broken down to 32
		 *	steps.
		 */
		for (int i = 0; i < Fixed.QUARTER_CIRCLE; i += Fixed.QUARTER_CIRCLE / 32) {
			/*
			 *	Empty the buffer before each of the passes.
			 */
			for (int row = 0; row < GRID_ROWS; row++) {
				for (int col = 0; col < GRID_COLS; col++) {
					grid[row][col] = 0;
				}
			}
			/*
			 *	Using the same maths as the renderer, with the addition of
			 *	a 'wobble' to perform overdraw and compensate for the
			 *	fewer angles used, the view frustrum is plotted into the
			 *	buffer.
			 */
			final int step = (TILE_W + TILE_W / 2) << Fixed.FIXED_POINT;
			for (int wobbleY = -step; wobbleY <= step; wobbleY += step) {
				for (int wobbleX = -step; wobbleX <= step; wobbleX += step) {
					int extremeLX = Fixed.mul(Fixed.cos(i - viewAngle), viewVdist);
					int extremeLY = Fixed.mul(Fixed.sin(i - viewAngle), viewVdist);
					int extremeRX = Fixed.mul(Fixed.cos(i + viewAngle), viewVdist);
					int extremeRY = Fixed.mul(Fixed.sin(i + viewAngle), viewVdist);
					
					int scaledX = wobbleX + (1 << Fixed.FIXED_POINT + 4);
					int scaledY = wobbleY + (1 << Fixed.FIXED_POINT + 4);
					
					/*
					 *	Points at the bottom of the view.
					 */
					int oneOverZ = (Fixed.ONE << viewScale) / (floorH - 1 + viewPitch);
					quad[0].set(extremeLX * oneOverZ, extremeLY * oneOverZ);
					quad[3].set(extremeRX * oneOverZ, extremeRY * oneOverZ);
					
					/*
					 *	Points on the horizon.
					 */
					oneOverZ = (Fixed.ONE << viewScale) / (viewHorzn + viewPitch);
					quad[1].set(extremeLX * oneOverZ, extremeLY * oneOverZ);
					quad[2].set(extremeRX * oneOverZ, extremeRY * oneOverZ);
					
					for (int n = 0; n < 4; n++) {
						Vector2D q = quad[n];
						q.x = ((q.x + scaledX) >> (Fixed.FIXED_POINT + GRID_W_BITS)) + GRID_COLS / 2;
						q.y = ((q.y + scaledY) >> (Fixed.FIXED_POINT + GRID_H_BITS)) + GRID_ROWS / 2;
					}
					
					Vector2D.fillQuad(quad, grid, TrackRenderer.MAP_COLS, TrackRenderer.MAP_ROWS, (byte) 1, false);
				}
			}
			
			/*	The camera's offset is added to the values to compensate for
			 *	entries behind the view being ordered before entries in front.
			 *	The value is subtracted later when the FOV data is stored in
			 *	its final form.
			 */
			int camX = (viewCdist * Fixed.cos(i) + Fixed.ONE / 2) >> GRID_W_BITS;
			int camY = (viewCdist * Fixed.sin(i) + Fixed.ONE / 2) >> GRID_H_BITS;
			
			/*
			 *	Go through each of the 'pixels' in the buffer to find used
			 *	(or in otherwords, visible) entries. This array of points
			 *	is then sorted from the nearest to furthest (the points
			 *	must be scaled to fixed point to sort, but then scaled
			 *	back for storing).
			 */
			int visible = 0;
			for (int row = 0; row < GRID_ROWS; row++) {
				for (int col = 0; col < GRID_COLS; col++) {
					if (grid[row][col] != 0 && visible < MAX_VISIBLE_SPRITES) {
						point[visible++].set(
							((col - GRID_COLS / 2) << Fixed.FIXED_POINT) + camX,
							((row - GRID_ROWS / 2) << Fixed.FIXED_POINT) + camY
						);
					}
				}
			}
			QuickSort.sort(point, 0, visible - 1);
			
			if (DEBUG) {
				System.out.println("Angle: " + i + " = " + visible);
			}
			if (visible > actualMaxVisible) {
				actualMaxVisible = visible;
			}
			
			int j = i >> 1;
			fieldOfViewCols[j] = new byte[visible];
			fieldOfViewRows[j] = new byte[visible];
			for (int n = 0; n < visible; n++) {
				fieldOfViewCols[j][n] = (byte) ((point[n].x - camX) >> Fixed.FIXED_POINT);
				fieldOfViewRows[j][n] = (byte) ((point[n].y - camY) >> Fixed.FIXED_POINT);
			}
		}
		
		if (DEBUG) {
			System.out.println("Maximum possible track objects visible: " + actualMaxVisible);
			System.gc();
		}
		return actualMaxVisible;
	}
	
	/**
	 *	Creates the background layers. From the many choices for background
	 *	renderers the only one guaranteed to be present is TiledOverlay, the
	 *	others might have been removed during obfuscation.
	 *
	 *	@see Constants
	 */
	protected static ContinuousTiledLayer[] createBackgrounds(int bkgndType, int w, int h, Image image, AnimTile[] anims) {
		boolean compBkgnd = bkgndType == BACKGROUND_COMPOSITE;
		
		ContinuousTiledLayer[] out = new ContinuousTiledLayer[compBkgnd ? 1 : BACKGROUND_LAYERS];
		if (compBkgnd) {
			if (ENABLE_BKGND_COMPOSITE) {
				out[0] = new BufferedCompositeTiledLayer(BKGND_COLS, BKGND_ROWS, w, h, BACKGROUND_LAYERS - 1, image, anims, false, null, false);
			} else {
				out[0] = null;
			}
		} else {
			switch (bkgndType) {
			case BACKGROUND_DIRECT:
				out[0] = new TiledOverlay(BKGND_COLS, BKGND_ROWS, w, h, image, anims, null, TiledOverlay.PEER_EXTEND);
				break;
			case BACKGROUND_SHUFFLED:
				if (ENABLE_BKGND_SHUFFLED) {
					out[0] = new ShuffledTiledOverlay(BKGND_COLS, BKGND_ROWS, w, h, image, anims, null);
				} else {
					out[0] = null;
				}
				break;
			default:
				if (ENABLE_BKGND_BUFFERED) {
					/*
					 *	TODO: this buffered layer sets allAnims to true, which
					 *	is quite slow on OL tracks like 1992 (the Rainbow Road
					 *	homage). This was probably not noticable on JSR. Look
					 *	into this.
					 */
					out[0] = new BufferedTiledLayer(BKGND_COLS, BKGND_ROWS, w, h, image, anims, true, null, false);
				} else {
					out[0] = null;
				}
			}
			for (int n = 1; n < out.length; n++) {
				out[n] = new TiledOverlay(BKGND_COLS, PARAX_ROWS, w, PARAX_ROWS * ContinuousTiledLayer.TILE_H, image, anims, out[0], TiledOverlay.PEER_EXTEND);
			}
		}
		return out;
	}
	
	/**
	 *	Maximum number of track objects visible at any one time.
	 */
	public static final int MAX_VISIBLE_SPRITES = 128;
	
	/**
	 *	Maximum number of sprites to be 'blended' with the track objects. The
	 *	figure is calculated from the number of karts and their track effects.
	 */
	public static final int MAX_BLEND_SPRITES = 16;
	
	/**
	 *	Number of lines off the bottom of the screen before a sprite should be
	 *	culled from the draw list.
	 */
	public static final int ODRAW =  32;
	
	public static final int SPRITE_W = 32;
	public static final int SPRITE_H = 32;
	
	/**
	 *	A scale shift used when drawing the sprites.
	 */
	public static final int SPRITE_SCALE = 1;
	
	/**
	 *	Number of pixels drawn together each loop of the floor code. An
	 *	optimisation specifically for Aplix VMs.
	 */
	private static final int DRAW_CHUNK = 16;
}