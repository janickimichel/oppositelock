package numfum.j2me.jsr.renderer;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import javax.microedition.m3g.*;

import numfum.j2me.jsr.Sprite;
import numfum.j2me.jsr.TrackRenderer;
import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.bkgnd.ContinuousTiledLayer;
import numfum.j2me.util.Fixed;
import numfum.j2me.util.QuickSort;

/**
 *	Track renderer implementation that maps the floor and sprites to quads
 *	and draws them using JSR-184.
 */
public final class TrackRendererM3G extends TrackRenderer {
	private final int floorH;
	
	/**
	 *	Generate the many large textures for drawing the floor. Useful to turn
	 *	off to find where the drawing bottleneck is.
	 */
	private static final boolean USE_FLOOR_TEXTURE = true;
	
	/**
	 *	Uses the depth buffer during the render. Should really be turned off,
	 *	otherwise the billboard sprites register transparent areas when
	 *	setting their depth (and causing items behind them to not be drawn).
	 *
	 *	Manually sorting the sprite depth, like in the Mode-7 renderer, is the
	 *	better option.
	 */
	private static final boolean USE_DEPTH_BUFFER = false;
	
	/**
	 *	A scale shift used when drawing the sprites.
	 */
	public static final int SPRITE_SCALE = 1;
	
	/**
	 *	Transform applied to sprite frames.
	 */
	private final short[] spriteTrans;
	
	/**
	 *	Total number of sprite frames (shared by all animations).
	 */
	private final int numSpriteFrames;
	
	/**
	 *	Textures holding the individual sprite frames. Multiple texture
	 *	objects are used rather than swapping a texture's image due to there
	 *	being fewer overheads this way round (or at least that's the case in
	 *	Nokia's M3G reference implementation).
	 */
	private final Texture2D[] spriteFrameTex;
	
	/**
	 *	Scale factor for the floor textures. A value of 1 stores the images at
	 *	full size, 2 at 50%, 4 at 25%.
	 */
	private final int texScale;
	
	/**
	 *	Tile palette as stored as bytes.
	 */
	private final byte[][] texTilePalette = new byte[256][3];
	
	/**
	 *	Tile frames stored in a format ready for use in M3G textures.
	 */
	private final byte[][] texTileFrames;
	
	/**
	 *	Images used on the floor textures. The floor is built from a few large
	 *	textures rather than many small ones.
	 */
	private final Image2D[][] floorTexImg = new Image2D[TEXTURE_ROWS][TEXTURE_COLS];
	
	/**
	 *	Mesh for the entire track floor.
	 */
	private Node floorMesh;
	
	private final VertexBuffer[] spriteVertBufL2R = new VertexBuffer[MAX_SPRITES];
	private final VertexBuffer[] spriteVertBufR2L = new VertexBuffer[MAX_SPRITES];
	
	/**
	 *	A single index buffer used by all sprites (given that they're all the
	 *	same size).
	 */
	private final IndexBuffer spriteIndexBuf;
	
	/**
	 *	Single appearance used for drawing all billboard sprites (its texture
	 *	reference is changed for each sprite).
	 */
	private final Appearance billboardApp;
	
	/**
	 *	Single transform used to possition all sprites.
	 */
	private final Transform spTransform = new Transform();
	
	/*****/

	/**
	 *	Single instance of the 3D draw context.
	 */
	private final Graphics3D g3d = Graphics3D.getInstance();
	
	/**
	 *	Camera used to render the scene.
	 */
	private final Camera camera;
	
	/**
	 *	Transform used to position the camera.
	 */
	private final Transform camTransform;
	
	/**
	 *	Current camera angle (in degrees). Used for aligning the all of the
	 *	billboard sprites with the camera.
	 */
	private float camDegrees = 0.0f;
	
	/**
	 *	Previous camera angle (in fixed point format). Used to calculate the
	 *	number of pixels to shift the background layers.
	 */
	private int lastA = 0;
	
	/**
	 *	M3G background object used to clear the screen and/or depth buffer.
	 */
	private final Background background;
	
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
	
	private int[] drawBkgndAtX = new int[BACKGROUND_LAYERS];
	private int[] drawBkgndAtY = new int[BACKGROUND_LAYERS];
	
	private int[] bkgndOffsetY = new int[BACKGROUND_LAYERS];
	
	private final int viewPitch;
	private final int viewAngle;
	private final int viewVdist;
	private final int viewCdist;
	private final int viewScale;
	private final int viewHorzn;
	
	/**
	 *	Downward tilt to get the horizon and clip to match the Mode-7 renderer.
	 */
	private final float viewTilt;
	
	private final byte[][] fieldOfViewCols = new byte[32][];
	private final byte[][] fieldOfViewRows = new byte[32][];
	
	/**
	 *	Scratch array used for sorting the displayed sprites. The maximum
	 *	number of sprites in any 32x32 is the total karts, a track object
	 *	per kart, and one static track object.
	 */
	private final Sprite[] sorter = new Sprite[TrackRendererMode7.MAX_BLEND_SPRITES + 1];
	
	/**
	 *	Queue of sorted sprites ready to render.
	 */
	private final Sprite[] spritesToPaint;
	
	/**
	 *	Number of sprites in the render queue.
	 */
	private int numSpritesToPaint = 0;
	
	public TrackRendererM3G(int viewW, int viewH, int ratio, int bkgndType, DataInput in, boolean close) throws IOException {
		super(viewW, viewH, in);
		
		floorH = viewH / 2 - 1; // -1 due to slight differences in where the floor draws to
		
		switch (ratio) {
		case 0:
			texScale = 1;
			break;
		case 1:
			texScale = 2;
			break;
		case 2:
			texScale = 4;
			break;
		default:
			texScale = 8;
		}
		
		in.skipBytes(in.readInt() + 4); // skip any 2D data
		
		spriteTrans = defaultSpriteLoader(in, animsprite);
		byte[] spritePalette = new byte[256 * 4];
		loadPalette(in, spritePalette);
		
		numSpriteFrames = in.readUnsignedShort();
		spriteFrameTex  = new Texture2D[numSpriteFrames];
		
		if (DEBUG) {
			System.out.println("Number of sprite frames: " + numSpriteFrames);
		}
		
		byte[]   spriteOffsetY = new byte[numSpriteFrames];
		byte[]   spriteLengthY = new byte[numSpriteFrames];
		byte[][] spriteOffsetX = new byte[numSpriteFrames][];
		byte[][] spriteLengthX = new byte[numSpriteFrames][];
		
		for (int n = 0; n < numSpriteFrames; n++) {
			spriteOffsetY[n] = in.readByte();
			int lengthY = in.readUnsignedByte();
			spriteLengthY[n] = (byte) lengthY;
			
			spriteOffsetX[n] = new byte[lengthY];
			spriteLengthX[n] = new byte[lengthY];
			for (int i = 0; i < lengthY; i++) {
				spriteOffsetX[n][i] = in.readByte();
				spriteLengthX[n][i] = in.readByte();
			}
		}
		
		byte[] frameBuf = new byte[SPRITE_W * SPRITE_H];
		byte[] lineBuf  = new byte[SPRITE_W];
		for (int n = 0; n < numSpriteFrames; n++) {
			for (int i = SPRITE_W * SPRITE_H - 1; i >= 0; i--) {
				frameBuf[i] = 0;
			}
			for (int posY = spriteOffsetY[n], lenY = spriteLengthY[n]; lenY > 0; posY++, lenY--) {
				int offY = posY * SPRITE_W;
				int posX = spriteOffsetX[n][posY - spriteOffsetY[n]];
				int lenX = spriteLengthX[n][posY - spriteOffsetY[n]];
				int bufX = 0;
				in.readFully(lineBuf, 0, lenX);
				while (lenX > 0) {
					frameBuf[offY + posX] = lineBuf[bufX++];
					posX++;
					lenX--;
				}
			}
			spriteFrameTex[n] = new Texture2D(new Image2D(Image2D.RGBA, SPRITE_W, SPRITE_H, frameBuf, spritePalette));
			spriteFrameTex[n].setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
			spriteFrameTex[n].setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
		}
		
		spriteOffsetY = null;
		spriteLengthY = null;
		spriteOffsetX = null;
		spriteLengthX = null;
		
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
		
		float viewAngleF = in.readFloat();
		float viewVdistF = in.readFloat();
		viewTilt = in.readFloat();
		
		if (close && in instanceof DataInputStream) {
			((DataInputStream) in).close();
		}
		
		
		createMesh();
		
		camera = new Camera();
		camera.setPerspective(viewAngleF, (float) viewW / (float) viewH, 32.0f, viewVdistF);
		//g3d.setCamera(camera, null);
		
		camTransform = new Transform();
		
		/*
		 *	The game's background is displayed in exactly the same way as the
		 *	Mode-7 renderer, using normal MIDP draw methods, so the colour
		 *	buffer isn't cleared.
		 */
		background = new Background();
		background.setDepthClearEnable(USE_DEPTH_BUFFER);
		background.setColorClearEnable(false);
		
		/*****/
		
		texTileFrames = new byte[MAX_ANIMS][(TILE_H / texScale) * (TILE_W / texScale) * 3];
		
		/*
		 *	One quad is stored per sprite (not individual frame), and it's
		 *	created already offset by its origin, so rendering is just a
		 *	matter drawing using a transform that affects its placement on the
		 *	track.
		 */
		VertexArray spriteVerts = new VertexArray(4, 3, 1);
		spriteVerts.set(0, 4, new byte[] {
			                        0,         	               0,  0,
			                        0, -SPRITE_H >> SPRITE_SCALE,  0,
			 SPRITE_W >> SPRITE_SCALE,                         0,  0,
			 SPRITE_W >> SPRITE_SCALE, -SPRITE_H >> SPRITE_SCALE,  0});
			 
		VertexArray spriteTextureL2R = new VertexArray(4, 2, 1);
		spriteTextureL2R.set(0, 4, new byte[] {
			0, 0,
			0, 1,
			1, 0,
			1, 1});
		
		VertexArray spriteTextureR2L = new VertexArray(4, 2, 1);
		spriteTextureR2L.set(0, 4, new byte[] {
			1, 0,
			1, 1,
			0, 0,
			0, 1});
		
		for (int n = 0; n < MAX_SPRITES; n++) {
			if (n > 0 && animsprite[n].isZeroTile()) {
				/*
				 *	Sets the vertex buffer for unused sprites to that of the
				 *	first entry (which should be created as a blank texture).
				 */
				spriteVertBufL2R[n] = spriteVertBufL2R[0];
				spriteVertBufR2L[n] = spriteVertBufR2L[0];
			} else {
				int data = animsprite[n].data;
				spriteVertBufL2R[n] = new VertexBuffer();
				spriteVertBufL2R[n].setPositions(spriteVerts, 1.0f, new float[] {
					-((data >> SPRITE_DATA_ROTL_OGNX) & SPRITE_DATA_MASK_OGNX) >> SPRITE_SCALE,
					 ((data >> SPRITE_DATA_ROTL_OGNY) & SPRITE_DATA_MASK_OGNY) >> SPRITE_SCALE, 0f});
				spriteVertBufL2R[n].setTexCoords(0, spriteTextureL2R, 1.0f, null);
				
				spriteVertBufR2L[n] = (VertexBuffer) spriteVertBufL2R[n].duplicate();
				spriteVertBufR2L[n].setTexCoords(0, spriteTextureR2L, 1.0f, null);
			}
		}
		
		spriteIndexBuf = new TriangleStripArray(0, new int[] {4});
		
		/*
		 *	Because the billboard sprites are always angled towards the player
		 *	there's no need to perspective correct the texture maps, which
		 *	should speed up the render.
		 */
		PolygonMode pm = new PolygonMode();
		pm.setPerspectiveCorrectionEnable(false);
		pm.setCulling(PolygonMode.CULL_BACK);
		CompositingMode cm = new CompositingMode();
		cm.setBlending(CompositingMode.ALPHA);
		
		billboardApp = new Appearance();
		billboardApp.setPolygonMode(pm);
		billboardApp.setCompositingMode(cm);
		
		/*****/
		
		int maxTrackObjs = TrackRendererMode7.createFieldOfView(viewPitch, viewAngle, viewVdist, viewCdist, viewScale, viewHorzn, floorH, fieldOfViewCols, fieldOfViewRows);
		
		spritesToPaint = new Sprite[maxTrackObjs + TrackRendererMode7.MAX_BLEND_SPRITES];
		for (int n = spritesToPaint.length - 1; n >= 0; n--) {
			spritesToPaint[n] = new Sprite();
		}
		for (int n = sorter.length - 1; n >= 0; n--) {
			sorter[n] = new Sprite();
		}
		
		bkgnd = TrackRendererMode7.createBackgrounds(bkgndType, viewW, viewH - floorH + ContinuousTiledLayer.TILE_H, image, anims);
		numBkgndLayers = bkgnd.length;
		bkgndY = (BKGND_ROWS - 1) * ContinuousTiledLayer.TILE_H - viewH + floorH;
		compBkgnd = bkgndType == BACKGROUND_COMPOSITE;
	}
	
	/*// experimental height map
	private static short[][] HEIGHT_MAP = new short[][] {
		{4, 2, 1, 4},
		{2, 2, 4, 2},
		{1, 1, 3, 1},
		{4, 2, 1, 4}
	};*/
	
	/**
	 *	Creates a mesh over the desired size, using degenerate triangles to
	 *	join up the rows. Requires <code>(cols * 6 + 18) * rows - 12</code>
	 *	available bytes from <code>off</code>.
	 */
	private int createMeshPos(int posX, int posY, int cols, int rows, byte[] buf, int off) {
		int x = 0;
		for (int z = 0; z < rows; z++) {
			// degenerate tris
			if (z > 0) {
				// same as last value
				buf[off++] = (byte) (posX + x + 0); 
				buf[off++] = 0;
				buf[off++] = (byte) (posY + z + 0);
				
				x = 0;
				
				// first value, twice
				buf[off++] = (byte) (posX + x + 0);
				buf[off++] = (byte) 0;
				buf[off++] = (byte) (posY + z + 0);
				
				buf[off++] = (byte) (posX + x + 0);
				buf[off++] = (byte) 0;
				buf[off++] = (byte) (posY + z + 0);
				
				// second value (to reverse winding)
				buf[off++] = (byte) (posX + x + 1);
				buf[off++] = 0;
				buf[off++] = (byte) (posY + z + 1);
			}
			
			buf[off++] = (byte) (posX + x + 0);
			buf[off++] = (byte) 0;
			buf[off++] = (byte) (posY + z + 0);
			
			for (; x < cols; x++) {
				buf[off++] = (byte) (posX + x + 0);
				buf[off++] = 0;
				buf[off++] = (byte) (posY + z + 1);
				
				buf[off++] = (byte) (posX + x + 1);
				buf[off++] = 0;
				buf[off++] = (byte) (posY + z + 0);
			}
			
			buf[off++] = (byte) (posX + x + 0); // note that x has already been incremented here
			buf[off++] = 0;
			buf[off++] = (byte) (posY + z + 1);
			
			
		}
		return off;
	}
	
	private int createMeshUVs(int cols, int rows, byte[] buf, int off) {
		for (int z = 0; z < rows; z++) {
			if (z > 0) {
				off += 8; // leave space for degenerate tris
			}
			int x = 0;
			
			buf[off++] = (byte) (x + 0);
			buf[off++] = (byte) (z + 0);
			
			for (; x < cols; x++) {
				buf[off++] = (byte) (x + 0);
				buf[off++] = (byte) (z + 1);
				
				buf[off++] = (byte) (x + 1);
				buf[off++] = (byte) (z + 0);
			}
			
			buf[off++] = (byte) (x + 0); // note that x has already been incremented here
			buf[off++] = (byte) (z + 1);
		}
		return off;
	}
	
	private void createMesh() {
		int vertsPerMesh = (TEXTURE_SUB * 2 + 6) * TEXTURE_SUB - 4;
		int totalVerts   = vertsPerMesh * FLOOR_QUADS;
		
		byte[] vert = new byte[totalVerts * 3];
		int vertIdx = 0;
		for (int row = -FLOOR_OVERDRAW; row < TEXTURE_ROWS + FLOOR_OVERDRAW; row++) {
			for (int col = -FLOOR_OVERDRAW; col < TEXTURE_COLS + FLOOR_OVERDRAW; col++) {
				vertIdx = createMeshPos(col * TEXTURE_SUB, row * TEXTURE_SUB, TEXTURE_SUB, TEXTURE_SUB, vert, vertIdx);
			}
		}
		VertexArray vertArray = new VertexArray(totalVerts, 3, 1);
		vertArray.set(0, totalVerts, vert);
		
		byte[] text = new byte[totalVerts * 2];
		int textIdx = 0;
		for (int row = -FLOOR_OVERDRAW; row < TEXTURE_ROWS + FLOOR_OVERDRAW; row++) {
			for (int col = -FLOOR_OVERDRAW; col < TEXTURE_COLS + FLOOR_OVERDRAW; col++) {
				textIdx = createMeshUVs(TEXTURE_SUB, TEXTURE_SUB, text, textIdx);
			}
		}
		VertexArray textArray = new VertexArray(totalVerts, 2, 1);
		textArray.set(0, totalVerts, text);
		
		VertexBuffer vertBuf = new VertexBuffer();
		vertBuf.setPositions(vertArray, TEXTURE_SUB_W, null);
		vertBuf.setTexCoords(0, textArray, 1.0f / TEXTURE_SUB, null);
		
		IndexBuffer[] idxBuf = new IndexBuffer[FLOOR_QUADS];
		for (int n = 0; n < FLOOR_QUADS; n++) {
			idxBuf[n] = new TriangleStripArray(n * vertsPerMesh, new int[] {vertsPerMesh}); // TODO: causes problem on K700i - any others?
		}
		
		PolygonMode pm = new PolygonMode();
		pm.setPerspectiveCorrectionEnable(true);
		pm.setCulling(PolygonMode.CULL_BACK);
		
		/*
		 *	The floor will always be drawn before any other objects so there's
		 *	no need to test the depth buffer. And because the 3D effect of the
		 *	sprites is faked, writing the floor depth causes the cars' bottoms
		 *	to clip off, so this too is disabled.
		 */
		CompositingMode cm = new CompositingMode();
		cm.setDepthTestEnable(false);
		cm.setDepthWriteEnable(false);
		
		Appearance[] app = new Appearance[FLOOR_QUADS];
		for (int row = 0; row < TEXTURE_ROWS; row++) {
			for (int col = 0; col < TEXTURE_COLS; col++) {
				Texture2D tex2d = null;
				if (USE_FLOOR_TEXTURE) {
					floorTexImg[row][col] = new Image2D(Image2D.RGB, TEXTURE_W / texScale, TEXTURE_H / texScale);
					tex2d = new Texture2D(floorTexImg[row][col]);
					tex2d.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
					tex2d.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
				}
				
				app[(row + FLOOR_OVERDRAW) * (TEXTURE_COLS + FLOOR_OVERDRAW * 2) + (col + FLOOR_OVERDRAW)] = new Appearance();
				app[(row + FLOOR_OVERDRAW) * (TEXTURE_COLS + FLOOR_OVERDRAW * 2) + (col + FLOOR_OVERDRAW)].setTexture(0, tex2d);
				app[(row + FLOOR_OVERDRAW) * (TEXTURE_COLS + FLOOR_OVERDRAW * 2) + (col + FLOOR_OVERDRAW)].setPolygonMode(pm);
				app[(row + FLOOR_OVERDRAW) * (TEXTURE_COLS + FLOOR_OVERDRAW * 2) + (col + FLOOR_OVERDRAW)].setCompositingMode(cm);
			}
		}
		int i = 0;
		for (int row = -FLOOR_OVERDRAW; row < TEXTURE_ROWS + FLOOR_OVERDRAW; row++) {
			for (int col = -FLOOR_OVERDRAW; col < TEXTURE_COLS + FLOOR_OVERDRAW; col++) {
				if (col < 0 || row < 0 || col >= TEXTURE_COLS || row >= TEXTURE_ROWS) {
					app[i] = app[((row + TEXTURE_ROWS) % TEXTURE_ROWS + FLOOR_OVERDRAW) * (TEXTURE_COLS + FLOOR_OVERDRAW * 2) + ((col + TEXTURE_COLS) % TEXTURE_COLS + FLOOR_OVERDRAW)];
				}
				i++;
			}
		}
		
		floorMesh = new Mesh(vertBuf, idxBuf, app);
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
		
		for (int n = 0; n < 256; n++) {
			texTilePalette[n][0] = (byte) ((tilePalette[n] >> 16) & 0xFF);
			texTilePalette[n][1] = (byte) ((tilePalette[n] >>  8) & 0xFF);
			texTilePalette[n][2] = (byte) ((tilePalette[n] >>  0) & 0xFF);
		}
		for (int n = 0; n < numTiles; n++) {
			int texOffset = 0;
			for (int y = 0; y < TILE_H; y += texScale) {
				for (int x = 0; x < TILE_W; x += texScale) {
					byte[] rgb = texTilePalette[tileset[n][y * TILE_W + x] & 0xFF];
					for (int i = 0; i < 3; i++) {
						texTileFrames[n][texOffset++] = rgb[i];
					}
				}
			}
		}
		
		int scaledTileW = TILE_W / texScale;
		int scaledTileH = TILE_H / texScale;
		
		if (USE_FLOOR_TEXTURE) {
			for (int row = 0; row < TEXTURE_ROWS; row++) {
				int rowOffset = row * TEXTURE_H / TILE_H;
				for (int col = 0; col < TEXTURE_COLS; col++) {
					int colOffset = col * TEXTURE_W / TILE_H;
					for (int y = 0; y < TEXTURE_H / TILE_H; y++) {
						for (int x = 0; x < TEXTURE_W / TILE_W; x++) {
							floorTexImg[row][col].set(x * scaledTileW, y * scaledTileH, scaledTileW, scaledTileH, texTileFrames[tilemap[((rowOffset + y) * MAP_COLS) + colOffset + x] & 0xFF]);
						}
					}
			
				}
			}
		}
	}
	
	public void refresh() {
		for (int n = 0; n < numBkgndLayers; n++) {
			bkgnd[n].reset();
		}
		bkgnd[0].moveTo(0, bkgndY);
	}
	
	public void render(int cameraX, int cameraY, int cameraA, Sprite[] blend, int blendSize, int bump) {
		camDegrees = (cameraA * 360f) / 256f + 90;
		
		camTransform.setIdentity();
		camTransform.postTranslate(cameraX / (float) Fixed.ONE, 32.0f, cameraY / (float) Fixed.ONE);
		camTransform.postRotate(camDegrees, 0f, -1f, 0f);
		camTransform.postRotate(viewTilt - bump / 4f, -1f, 0, 0f);
		
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
		
		numSpritesToPaint = 0;
		
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
				int gridIdx = row << GRID_ROWS_BITS | col;
				int spriteN = spritemap[gridIdx];
				int tileIdx = animsprite[spriteN].getTileIndex();
				if (tileIdx != 0) {
					int fineX = spritePosX[gridIdx];
					int fineY = spritePosY[gridIdx];
					if (((animsprite[spriteN].data >> SPRITE_DATA_ROTL_BOTM) & SPRITE_DATA_MASK_BOTM) != 0) {
						spritesToPaint[numSpritesToPaint++].set(tileIdx, fineX, fineY, 0, (spriteN & Sprite.DATA_MASK_SPRITE) << Sprite.DATA_ROTL_SPRITE);
					} else {
						int fineXCam = (fineX - cameraX) >> Fixed.FIXED_POINT;
						int fineYCam = (fineY - cameraY) >> Fixed.FIXED_POINT;
						sorter[numToSort++].set(tileIdx, fineX, fineY, fineXCam * fineXCam + fineYCam * fineYCam, (spriteN & Sprite.DATA_MASK_SPRITE) << Sprite.DATA_ROTL_SPRITE);
					}
				}
				for (int i = blendSize - 1; i >= 0; i--) {
					Sprite blendI = blend[i];
					if (col == blendI.col && row == blendI.row) {
						int blendIX = (blendI.x - cameraX) >> Fixed.FIXED_POINT;
						int blendIY = (blendI.y - cameraY) >> Fixed.FIXED_POINT;
						sorter[numToSort++].set(animsprite[blendI.n].getTileIndex(), blendI.x, blendI.y, blendIX * blendIX + blendIY * blendIY + blendI.d, blendI.data | (blendI.n & Sprite.DATA_MASK_SPRITE) << Sprite.DATA_ROTL_SPRITE);
						blendI.view = true;
					}
				
				}
				/*
				 *	Go through each grid square's array, sort it if there's
				 *	more than one sprite, then append them all to the end of
				 *	the draw list.
				 */
				if (numToSort > 0) {
					if (numToSort == 1) {
						spritesToPaint[numSpritesToPaint++].set(sorter[0]);
					} else {
						QuickSort.sort(sorter, 0, numToSort - 1);
						for (int i = numToSort - 1; i >= 0; i--) {
							spritesToPaint[numSpritesToPaint++].set(sorter[i]);
						}
					}
				}
			}
		}
		
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
	
	/**
	 *	Draws a single sprite billboard.
	 */
	private void drawSprite(Sprite sprite) {
		int spTrans = spriteTrans[sprite.n];
		
		int udgs = (spTrans >> SPRITETRANS_ROTL_UDGS) & SPRITETRANS_MASK_UDGS;
		int tran = (spTrans >> SPRITETRANS_ROTL_TRAN) & SPRITETRANS_MASK_TRAN;
		
		int bumpY = (sprite.data >> Sprite.DATA_ROTL_BUMP_Y) & Sprite.DATA_MASK_BUMP_Y;
		int buzzY = (sprite.data >> Sprite.DATA_ROTL_BUZZ_Y) & Sprite.DATA_MASK_BUZZ_Y;
		if (bumpY != 0 && (bumpY & 1) == buzzY) {
			buzzY = -buzzY;
		}
		
		billboardApp.setTexture(0, spriteFrameTex[udgs]);
		spTransform.setIdentity();
		spTransform.postTranslate(sprite.x / (float) Fixed.ONE, bumpY * 0.25f - 0.5f * buzzY, sprite.y / (float) Fixed.ONE);
		spTransform.postRotate(camDegrees, 0.0f, -1.0f, 0.0f);
		spTransform.postRotate(viewTilt, -1f, 0, 0f);
		
		g3d.render(((tran == 0) ? spriteVertBufL2R : spriteVertBufR2L)[(sprite.data >> Sprite.DATA_ROTL_SPRITE) & Sprite.DATA_MASK_SPRITE], spriteIndexBuf, billboardApp, spTransform, -1);
	}
	
	public void paint(Graphics g, int offsetX, int offsetY) {
		for (int n = 0; n < numBkgndLayers; n++) {
			bkgnd[n].paint(g, offsetX + drawBkgndAtX[n], offsetY + drawBkgndAtY[n]);
		}
		try {
			g3d.bindTarget(g, USE_DEPTH_BUFFER, 0);
			
			g3d.setViewport(offsetX, offsetY, viewW, viewH);
			
			if (USE_DEPTH_BUFFER) {
				g3d.clear(background);
			}
			
			//camera.setTransform(camTransform);
			//g3d.render(world);
			
			g3d.setCamera(camera, camTransform);
			g3d.render(floorMesh, null);
			
			for (int n = 0; n < numSpritesToPaint; n++) {
				drawSprite(spritesToPaint[n]);
			}
		} finally {
			g3d.releaseTarget();
		}
	}
	
	public static final int SPRITE_W = 32;
	public static final int SPRITE_H = 32;
	
	private static final int TEXTURE_W = 256;
	private static final int TEXTURE_H = 256;
	private static final int TEXTURE_COLS = (MAP_COLS * TILE_W) / TEXTURE_W;
	private static final int TEXTURE_ROWS = (MAP_ROWS * TILE_H) / TEXTURE_H;
	
	private static final int TEXTURE_SUB = 4;
	private static final int TEXTURE_SUB_W = TEXTURE_W / TEXTURE_SUB;
	//private static final int TEXTURE_SUB_H = TEXTURE_H / TEXTURE_SUB;
	
	private static final int FLOOR_OVERDRAW = 1;
	private static final int FLOOR_QUADS = (TEXTURE_ROWS + FLOOR_OVERDRAW * 2) * (TEXTURE_COLS + FLOOR_OVERDRAW * 2);
	
}