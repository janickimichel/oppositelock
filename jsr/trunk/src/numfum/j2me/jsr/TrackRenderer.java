package numfum.j2me.jsr;

import java.io.DataInput;
import java.io.IOException;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.AnimTileController;
import numfum.j2me.jsr.generic.Positionable;
import numfum.j2me.util.Fixed;

/**
 *	Abstract track renderer. Subclasses will perform the actual drawing, in
 *	either topdown 2D, pseudo 3D or real 3D.	
 */
public abstract class TrackRenderer extends AnimTileController implements Constants, Positionable {
	/**
	 *	Width of rendered view.
	 */
	protected final int viewW;
	
	/**
	 *	Height of rendered view.
	 */
	protected final int viewH;
	
	/******************************** Floor *********************************/
	
	/**
	 *	Scratch array where tile transforms are stored before being carried
	 *	out. The transforms describe the rotations and mirrors required to
	 *	reconstitute a tileset.
	 *
	 *	@see toolbox.tile#TransTile
	 */
	private final byte[] transTileTran = new byte[MAX_TILES];
	
	/**
	 *	Scratch array holding the indices to untransformed tiles.
	 *
	 *	@see #transTileTran
	 */
	private final byte[] transTileUDGs = new byte[MAX_TILES];
	
	/**
	 *	Scratch array holding a reverse lookup to untransformed tiles.
	 */
	private final byte[] transTileUDGsRev = new byte[MAX_TILES];
	
	/**
	 *	Colour palette for the tiles in ARGB format.
	 */
	protected final int[] tilePalette = new int[256];
	
	/**
	 *	Tileset master. The actual raw tile frames.
	 *
	 *	***** HERE'S WHERE THE TILES ARE KEPT!!!! *****
	 */
	protected final byte[][] tileFrames = new byte[MAX_TILES][TILE_H * TILE_W];
	
	/**************************** Main floor bits ***************************/
	
	/**
	 *	References to the current tile frames. After cycling the animated
	 *	tiles a reference to the master tile is held here to save on lookups.
	 *
	 *	***** HERE'S WHERE THE TILES ARE ANIMATED!!!! *****
	 */
	protected final byte[][] tileset = new byte[MAX_ANIMS][];
	
	/*
	 *	Number of used tiles in <code>tileset</code>.
	 */
	protected int numTiles = 0;
	
	/**
	 *	Tilemap for the track.
	 */
	protected final byte[] tilemap = new byte[MAP_ROWS * MAP_COLS];
	
	/************************************************************************/
	
	/**
	 *	Animated sprite instances.
	 */
	protected final AnimTile[] animsprite = new AnimTile[MAX_SPRITES];
	
	/**
	 *	Controller for the animated sprites.
	 */
	protected final AnimTileController spritecontrol = new AnimTileController(animsprite);
	
	/**
	 *	'Tilemap' for the sprites.
	 */
	protected final byte[] spritemap = new byte[GRID_ROWS * GRID_COLS];
	
	/**
	 *	Sprite x-coords.
	 */
	protected final int[] spritePosX = new int[GRID_ROWS * GRID_COLS];
	
	/**
	 *	Sprite y-coords.
	 */
	protected final int[] spritePosY = new int[GRID_ROWS * GRID_COLS];
	
	/************************************************************************/
	
	/**
	 *	Creates a new track renderer.
	 *
	 *	@param viewW width of the rendered view
	 *	@param viewH height of the rendered view
	 *	@param in stream from which other render params are read
	 */
	public TrackRenderer(int viewW, int viewH, DataInput in) throws IOException {
		super(new AnimTile[MAX_ANIMS]);
		
		this.viewW = viewW;
		this.viewH = viewH;
		
		for (int n = 0; n < MAX_SPRITES; n++) {
			animsprite[n] = new AnimTile();
		}
		for (int n = 0; n < MAX_ANIMS; n++) {
			animtile[n] = new AnimTile();
		}
	}
	
	/**
	 *	Loads a track and sprites from a stream.
	 *
	 *	@param in stream from which to read the track
	 */
	public final void load(DataInput in) throws IOException {
		int numTrans = in.readUnsignedByte();
		if (DEBUG) {
			System.out.println("Number of tile transforms: " + numTrans);
		}
		in.readFully(transTileTran, 0, numTrans);
		in.readFully(transTileUDGs, 0, numTrans);
		
		for (int n = 0; n < numTrans; n++) {
			if (transTileTran[n] == TRANS_NONE) {
				transTileUDGsRev[transTileUDGs[n]] = (byte) n;
			}
		}
		
		loadPalette(in, tilePalette);
		
		int numTileFrames = in.readUnsignedByte();
		if (DEBUG) {
			System.out.println("Number of tile frames: " + numTileFrames);
		}
		if (in.readUnsignedByte() != TILE_W | in.readUnsignedByte() != TILE_H) {
			if (DEBUG) {
				throw new IOException("Wrong tile size");
			} else {
				throw new IOException();
			}
		}
		for (int n = 0; n < numTileFrames; n++) {
			in.readFully(tileFrames[transTileUDGsRev[n] & 0xFF]);
		}
		
		for (int n = 0; n < numTrans; n++) {
			int trans = transTileTran[n];
			byte[] src = tileFrames[transTileUDGsRev[transTileUDGs[n] & 0xFF] & 0xFF];
			byte[] dst = tileFrames[n];
			if (src != dst) {
				transformTile(src, dst,
					(trans & TRANS_FLIP_Y ) != 0,
					(trans & TRANS_FLIP_X ) != 0,
					(trans & TRANS_SWAP_XY) != 0);
			}
		}
		
		numTiles = in.readUnsignedByte();
		if (DEBUG) {
			System.out.println("Number of tiles: " + numTiles);
		}
		
		reset();
		for (int n = 0; n < numTiles; n++) {
			animtile[n].set(in.readInt(), 0);
			addAnimTile(n);
			tileset[n] = tileFrames[animtile[n].getTileIndex()];
		}
		
		if (in.readUnsignedByte() != MAP_COLS || in.readUnsignedByte() != MAP_ROWS || in.readByte() != 0) { // 0 being map encoded as bytes
			if (DEBUG) {
				throw new IOException("Wrong map size");
			} else {
				throw new IOException();
			}
		}
		in.readFully(tilemap);
		
		for (int row = 0; row < GRID_ROWS; row++) {
			for (int col = 0; col < GRID_COLS; col++) {
				spritemap[row << GRID_ROWS_BITS | col] = 0;
			}
		}
		
		spritecontrol.reset();
		
		int numSprites = in.readUnsignedShort();
		for (int n = 0; n < numSprites; n++) {
			int data = in.readInt();
			int spriteN = (data >> SPRITEMAP_ROTL_UDGS) & SPRITEMAP_MASK_UDGS;
			int spriteX = (data >> SPRITEMAP_ROTL_POSX) & SPRITEMAP_MASK_POSX;
			int spriteY = (data >> SPRITEMAP_ROTL_POSY) & SPRITEMAP_MASK_POSY;
			int col = spriteX >> GRID_W_BITS;
			int row = spriteY >> GRID_H_BITS;
			spritemap [row << GRID_ROWS_BITS | col] = (byte) spriteN;
			spritePosX[row << GRID_ROWS_BITS | col] = spriteX << Fixed.FIXED_POINT;
			spritePosY[row << GRID_ROWS_BITS | col] = spriteY << Fixed.FIXED_POINT;
			spritecontrol.addAnimTile(spriteN);
		}
		
		loaded(in);
	}
	
	/**
	 *	Called after the track data has been loaded.
	 */
	protected void loaded(DataInput in) throws IOException {}
	
	/**
	 *	Updates screen buffers and other shared resources. Call after resuming a
	 *	game to ensure everything looks as it should (instead of reloading the
	 *	track from scratch).
	 */
	public void refresh() {}
	
	/**
	 *	Returns the implementation specific data attached to a track tile.
	 */
	public final int getTileData(int col, int row) {
		return animtile[tilemap[(row & (MAP_ROWS - 1)) << MAP_ROWS_BITS | (col & (MAP_COLS - 1))] & 0xFF].data;
	}
	
	/**
	 *	Returns the index of the sprite at the specified grid location.
	 */
	public final int getSpriteIndex(int col, int row) {
		return spritemap[(row & (GRID_ROWS - 1)) << GRID_ROWS_BITS | (col & (GRID_COLS - 1))];
	}
	
	/**
	 *	Sets the index of the sprite at the specified grid location.
	 */
	public final void setSpriteIndex(int col, int row, int index) {
		spritemap[(row & (GRID_ROWS - 1)) << GRID_ROWS_BITS | (col & (GRID_COLS - 1))] = (byte) index;
	}
	
	/**
	 *	Returns the implementation specific data attached to a sprite.
	 */
	public final int getSpriteData(int index) {
		if (index < MAX_SPRITES) {
			return animsprite[index].data;
		} else {
			return 0;
		}
	}
	
	/**
	 *	Returns precise x-position of a sprite in the coarse grid.
	 */
	public final int getSpriteFineX(int col, int row) {
		return spritePosX[row << GRID_ROWS_BITS | col];
	}
	
	/**
	 *	Returns precise y-position of a sprite in the coarse grid.
	 */
	public final int getSpriteFineY(int col, int row) {
		return spritePosY[row << GRID_ROWS_BITS | col];
	}
	
	public final AnimTile getSpriteAnimTile(int index) {
		return animsprite[index];
	}
	
	/**
	 *	Returns the AnimTileController associated with the sprites. Used when
	 *	adding or deleting sprites that need animating but are not part of the
	 *	static track.
	 */
	public final AnimTileController getSpriteAnimController() {
		return spritecontrol;
	}
	
	/**
	 *	Sets the current frame of a sprite.
	 */
	public final void setSpriteFrame(int index, int frame) {
		if (index < MAX_SPRITES) {
			animsprite[index].reset(frame);
		}
	}
	
	/**
	 *	Updates all the track and sprite animations.
	 */
	public void cycle() {
		super.cycle();
		for (int n = numActiveAnims - 1; n >= 0; n--) {
			tileset[activeAnims[n]] = tileFrames[animtile[activeAnims[n]].getTileIndex()];
		}
		spritecontrol.cycle();
	}
	
	/**
	 *	Returns whether the renderer implementation is viewed from behind the
	 *	player. Otherwise a topdown view is assumed.
	 */
	public abstract boolean isPerspectiveView();
	
	/**
	 *	Camera distance from the player.
	 */
	public abstract int getCameraDistance();
	
	/**
	 *	Renders, or prepares the render, depending on the implementation.
	 *
	 *	@param blend sprites to be combined at draw time
	 *	@param size  number of sprites being combined
	 *	@param bump  visual effect of the camera shaking
	 */
	public abstract void render(int x, int y, int a, Sprite[] blend, int size, int bump);
	
	public int getW() {
		return viewW;
	}
	public int getH() {
		return viewH;
	}
	
	public Positionable setPosition(int x, int y, int anchor) {
		return this;
	}
	
	public abstract void paint(Graphics g, int offsetX, int offsetY);
	
	public String toString() {
		return getClass().getName() + " [viewW: " + viewW + ", viewH: " + viewH + "]";
	}
	
	/**
	 *	Performs a transform on the raw tile data.	
	 */
	protected static final void transformTile(byte[] src, byte[] dst, boolean flipY, boolean flipX, boolean swapXY) {
		int dstX, dstY;
		for (int srcY = 0; srcY < TILE_H; srcY++) {
			if (flipY) {
				dstY = TILE_H - srcY - 1;
			} else {
				dstY = srcY;
			}
			for (int srcX = 0; srcX < TILE_W; srcX++) {
				if (flipX) {
					dstX = TILE_W - srcX - 1;
				} else {
					dstX = srcX;
				}
				if (swapXY) { 
					dst[dstX << 3 | dstY] = src[srcY << 3 | srcX];
				} else {
					dst[dstY << 3 | dstX] = src[srcY << 3 | srcX];
				}
			}
		}
	}
	
	/**
	 *	Loads a palette. Note: the palette size is stored as a byte, with the
	 *	number of entries ranging from 1 to 256.
	 */
	protected static final void loadPalette(DataInput in, int[] palette) throws IOException {
		int entries = in.readUnsignedByte() + 1;
		for (int n = 0; n < entries; n++) {
			palette[n] = (0xFF << 24)
				| (in.readUnsignedByte() << 16)
				| (in.readUnsignedByte() <<  8)
				| (in.readUnsignedByte() <<  0);
		}
		if (DEBUG) {
			System.out.println("Palette size: " + entries);
		}
	}
	
	/**
	 *	Loads a palette and stores it as shorts in 4444 format. Used with
	 *	12-bit Nokia phones.
	 */
	protected static final void loadPalette(DataInput in, short[] palette) throws IOException {
		int entries = in.readUnsignedByte() + 1;
		for (int n = 0; n < entries; n++) {
			palette[n] = (short) (0xF000
				| ((in.readUnsignedByte() >> 4) << 8)
				| ((in.readUnsignedByte() >> 4) << 4)
				| ((in.readUnsignedByte() >> 4) << 0));
		}
		if (DEBUG) {
			System.out.println("Palette size: " + entries);
		}
	}
	
	/**
	 *	Loads a palette and stores it as bytes in RGBA format. Used with M3G
	 *	phones. It's assumed the first entry is transparent.
	 */
	protected static final void loadPalette(DataInput in, byte[] palette) throws IOException {
		int entries = in.readUnsignedByte() + 1;
		int i = 0;
		for (int n = 0; n < entries; n++) {
			palette[i++] = in.readByte();
			palette[i++] = in.readByte();
			palette[i++] = in.readByte();
			palette[i++] = (byte) 0xFF;
		}
		palette[3] = 0x00;
		if (DEBUG) {
			System.out.println("Palette size: " + entries);
		}
	}
	
	/**
	 *	Skips over a palette in a stream.
	 */
	protected static final void skipPalette(DataInput in) throws IOException {
		in.skipBytes((in.readUnsignedByte() + 1) * 3);
	}
	
	/**
	 *	Loads animated sprites from a stream.
	 */
	public static final void loadAnimSprites(DataInput in, AnimTile[] animsprite) throws IOException {
		int numSprites = in.readUnsignedByte();
		if (DEBUG) {
			System.out.println("Number of sprites: " + numSprites);
		}
		for (int n = 0; n < numSprites; n++) {
			if (n < MAX_SPRITES) {
				animsprite[n].set(in.readInt(), in.readInt());
			} else {
				if (DEBUG) {
					System.out.println("Ignoring extra sprite!");
				}
				in.skipBytes(8);
			}
		}
	}
	
	/**
	 *	Skips over animated sprites in a stream.
	 */
	public static final void skipAnimSprites(DataInput in) throws IOException {
		in.skipBytes(in.readUnsignedByte() * 8);
	}
	
	/**
	 *	Loads sprite transforms from a stream.
	 */
	protected static final short[] loadSpriteTrans(DataInput in) throws IOException {
		int numSpriteTrans = in.readUnsignedShort();
		if (DEBUG) {
			System.out.println("Number of sprite transforms: " + numSpriteTrans);
		}
		short[] spriteTrans = new short[numSpriteTrans];
		for (int n = 0; n < numSpriteTrans; n++) {
			spriteTrans[n] = in.readShort();
		}
		return spriteTrans;
	}
	
	/**
	 *	Skips over sprite transforms in a stream.
	 */
	protected static final int skipSpriteTrans(DataInput in) throws IOException {
		int numTrans = in.readUnsignedShort();
		in.skipBytes(numTrans * 2);
		return numTrans;
	}
	
	/**
	 *	Default sprite loader implementation.
	 */
	protected static final short[] defaultSpriteLoader(DataInput in, AnimTile[] animsprite) throws IOException {
		loadAnimSprites(in, animsprite);
		/*
		 *	Go through the sprites replacing unused ones with a reference to
		 *	the first, which should be blank.
		 */
		for (int n = 1; n < MAX_SPRITES; n++) {
			if (animsprite[n].isZeroTile()) {
				animsprite[n] = animsprite[0];
				if (DEBUG) {
					System.out.println("Freeing up sprite: " + n);
				}
			}
		}
		
		return loadSpriteTrans(in);
	}
	
	/**
	 *	Image loader implementation.
	 *
	 *	Note: this creates and discards a byte array the size of the image file.
	 */
	protected static final Image loadImage(DataInput in) throws IOException {
		byte[] data = new byte[in.readUnsignedShort()];
		in.readFully(data);
		return Image.createImage(data, 0, data.length);
	}
	
	/************************************************************************/
	
	/**
	 *	Maximum number of tile frames ('physical' tiles).
	 *
	 *	Increased from 128 to 184 to accommodate Opposite Lock.
	 */
	public static final int MAX_TILES = 256;
	
	/**
	 *	Maximum number of animated tiles ('virtual' tiles). The animated tiles
	 *	reference the raw sprite frames.
	 *
	 *	Increased from 144 to 200 to accommodate Opposite Lock.
	 */
	public static final int MAX_ANIMS = 256;
	
	/************************************************************************/
	
	public static final int MAP_COLS_BITS = 7;
	public static final int MAP_ROWS_BITS = 7;
	public static final int MAP_COLS = 1 << MAP_COLS_BITS; // 128
	public static final int MAP_ROWS = 1 << MAP_ROWS_BITS; // 128
	
	public static final int MAX_SPRITES = 128;
	
	public static final int TILE_W_BITS = 3;
	public static final int TILE_H_BITS = 3;
	public static final int TILE_W = 1 << TILE_W_BITS; // 8
	public static final int TILE_H = 1 << TILE_H_BITS; // 8
	
	public static final int GRID_W_BITS = 5;
	public static final int GRID_H_BITS = 5;
	public static final int GRID_W = 1 << GRID_W_BITS; // 32
	public static final int GRID_H = 1 << GRID_H_BITS; // 32
	
	public static final int GRID_COLS_BITS = MAP_COLS_BITS + TILE_W_BITS - GRID_W_BITS;
	public static final int GRID_ROWS_BITS = MAP_ROWS_BITS + TILE_H_BITS - GRID_H_BITS;
	public static final int GRID_COLS = 1 << GRID_COLS_BITS;
	public static final int GRID_ROWS = 1 << GRID_ROWS_BITS;
	
	/************************************************************************/
	
	public static final int TRANS_NONE    = 0;
	public static final int TRANS_FLIP_Y  = 1;
	public static final int TRANS_FLIP_X  = 2;
	public static final int TRANS_SWAP_XY = 4;
	
	/************************************************************************/
	
	public static final int SPRITEMAP_BITS_UDGS =  7;
	public static final int SPRITEMAP_BITS_POSX = 10;
	public static final int SPRITEMAP_BITS_POSY = 10;
	
	public static final int SPRITEMAP_MASK_UDGS = (1 << SPRITEMAP_BITS_UDGS) - 1;
	public static final int SPRITEMAP_MASK_POSX = (1 << SPRITEMAP_BITS_POSX) - 1;
	public static final int SPRITEMAP_MASK_POSY = (1 << SPRITEMAP_BITS_POSY) - 1;
	
	public static final int SPRITEMAP_ROTL_UDGS = 0;
	public static final int SPRITEMAP_ROTL_POSX = SPRITEMAP_ROTL_UDGS + SPRITEMAP_BITS_UDGS;
	public static final int SPRITEMAP_ROTL_POSY = SPRITEMAP_ROTL_POSX + SPRITEMAP_BITS_POSX;
	
	/************************************************************************/
	
	public static final int SPRITE_DATA_BITS_OGNX = 6;
	public static final int SPRITE_DATA_BITS_OGNY = 6;
	public static final int SPRITE_DATA_BITS_BOTM = 1;
	public static final int SPRITE_DATA_BITS_TYPE = 4;
	public static final int SPRITE_DATA_BITS_PASS = 4;
	
	public static final int SPRITE_DATA_MASK_OGNX = (1 << SPRITE_DATA_BITS_OGNX) - 1;
	public static final int SPRITE_DATA_MASK_OGNY = (1 << SPRITE_DATA_BITS_OGNY) - 1;
	public static final int SPRITE_DATA_MASK_BOTM = (1 << SPRITE_DATA_BITS_BOTM) - 1;
	public static final int SPRITE_DATA_MASK_TYPE = (1 << SPRITE_DATA_BITS_TYPE) - 1;
	public static final int SPRITE_DATA_MASK_PASS = (1 << SPRITE_DATA_BITS_PASS) - 1;
	
	public static final int SPRITE_DATA_ROTL_OGNX = 0;
	public static final int SPRITE_DATA_ROTL_OGNY = SPRITE_DATA_ROTL_OGNX + SPRITE_DATA_BITS_OGNX;
	public static final int SPRITE_DATA_ROTL_BOTM = SPRITE_DATA_ROTL_OGNY + SPRITE_DATA_BITS_OGNY;
	public static final int SPRITE_DATA_ROTL_TYPE = SPRITE_DATA_ROTL_BOTM + SPRITE_DATA_BITS_BOTM;
	public static final int SPRITE_DATA_ROTL_PASS = SPRITE_DATA_ROTL_TYPE + SPRITE_DATA_BITS_TYPE;
	
	/************************************************************************/
	
	public static final int SPRITETRANS_BITS_UDGS = 13;
	public static final int SPRITETRANS_BITS_TRAN =  3;
	
	public static final int SPRITETRANS_MASK_UDGS = (1 << SPRITETRANS_BITS_UDGS) - 1;
	public static final int SPRITETRANS_MASK_TRAN = (1 << SPRITETRANS_BITS_TRAN) - 1;
	
	public static final int SPRITETRANS_ROTL_UDGS = 0;
	public static final int SPRITETRANS_ROTL_TRAN = SPRITETRANS_ROTL_UDGS + SPRITETRANS_BITS_UDGS;
	
	/************************************************************************/
	
	public static final int BACKGROUND_LAYERS = 3;
	
	public static final int BKGND_COLS = 32;
	public static final int BKGND_ROWS = 32;
	public static final int PARAX_ROWS = 2;
}