package numfum.j2me.jsr;

import java.io.*;

import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.AnimTileController;
import numfum.j2me.util.ByteUtils;
import numfum.j2me.util.Fixed;
import numfum.j2me.util.Joystick;
import numfum.j2me.util.QuickSort;
import numfum.j2me.util.Vector2D;

/**
 *	Guts of the game. Controls the race, updates the cars, etc.
 */
public final class RaceCore implements Constants {
	
	/******************************* AI Data ********************************/
	
	/**
	 *	Points around the track for the AI car to drive towards. As most of
	 *	the driving code needs precalculated values these serve as an origin.
	 *	Each track has a choice of driving lines.
	 */
	private final Vector2D[][] aiOrig = new Vector2D[NUM_RACING_LINES + 1][MAX_AI_POINTS];
	
	/**
	 *	The above points but as unit vectors.
	 */
	private final Vector2D[][] aiUnit = new Vector2D[NUM_RACING_LINES + 1][MAX_AI_POINTS];
	
	/**
	 *	The above points but scaled using:
	 *
	 *		setToDiv(aiUnit[i][i], aiUnit[i][i].magSquared());
	 */
	private final Vector2D[][] aiCalc = new Vector2D[NUM_RACING_LINES + 1][MAX_AI_POINTS];
	
	/**
	 *	The cumulative distance around the track measured at each point on the
	 *	last line. This line, which is not followed by the AI, marks out an
	 *	average of the other lines and is used for calculating the approximate
	 *	order of the cars in a lap.
	 *
	 *	@see #aiLineTotal
	 */
	private final int[] aiLineLen = new int[MAX_AI_POINTS + 1];
	
	/**
	 *	Total length of the last AI line.
	 */
	private int aiLineTotal = -1;
	
	/**
	 *	A scratch array used during track AI data loading. The track segments
	 *	are loaded into here before being 'rasterised'.
	 *
	 *	@see #aiQuad
	 */
	private final Vector2D[] scratchQuad = new Vector2D[4];
	
	/**
	 *	Grid in which the track segment quads are 'drawn'. 128x128 tiles.
	 */
	private final byte[] aiQuad = new byte[TrackRenderer.MAP_ROWS * TrackRenderer.MAP_COLS];
	
	/**
	 *	Indices into the Vector2D array. Lookup a segment from the map, then
	 *	get an index to the three available points from here.
	 */
	private final byte[][] aiNext = new byte[NUM_RACING_LINES + 1][MAX_AI_SEGMENTS];
	
	/**
	 *	Same as the previous array of indices but pointing to the current
	 *	point. Used to get an index to the origin.
	 */
	private final byte[][] aiThis = new byte[NUM_RACING_LINES + 1][MAX_AI_SEGMENTS];
	
	private final byte[][] marker = new byte[NUM_RACING_LINES + 1][MAX_AI_MARKERS];
	private final byte[] usedMarkers = new byte[NUM_RACING_LINES + 1];
	
	/************************************************************************/
	
	/**
	 *	Array of sprite references used for 'track effects' - the animation
	 *	running under the player's kart when driving over grass, gravel, etc.
	 *	For convenience the zero entry represents no effect, hence the array
	 *	size being one larger than the maximum number of effects used.
	 *
	 *	TODO: zero effect is now collision? Nitrous & misfire effects.
	 */
	private final int[] trfxSpriteRef = new int[MAX_EFFECTS + 3];
	
	/**
	 *	Array of sprite references for each character/kart.
	 */
	private final int[] kartSpriteRef = new int[TOTAL_KARTS];
	
	private final int powrSpriteRef;
	private final int sCamSpriteRef; // currently not used - why was it added?
	
	private int powerUpState  = POWERUP_STATE_READY;
	private int powerUpPlayer = 0;
	private int powerUpPayout = 0;
	
	/**
	 *	Number of pick-ups on the track.
	 */
	private int numPickups = 0;
	
	/************************************************************************/
	
	private final Vector2D[] staticCam = new Vector2D[MAX_STATIC_CAMERAS];
	private final Vector2D[] staticCamOrder = new Vector2D[MAX_STATIC_CAMERAS];
	private int numStatCams = 0;
	
	private int cameraX = 0, cameraY = 0, cameraA;
	private boolean blendKartEffect;
	
	/************************************************************************/
	
	/**
	 *	Current karts actually racing.
	 */
	private final Kart[] kart = new Kart[MAX_KARTS];
	
	/**
	 *	Shortcut to each kart's position. The position is the most frequently
	 *	accessed member variable.
	 */
	private final Vector2D[] kartPos = new Vector2D[MAX_KARTS];
	
	/**
	 *	Number of current karts actually racing.
	 */
	private int numKartsRacing = 0;
	
	/**
	 *	Holds references to where each seeded kart will start.
	 */
	private final Vector2D kartStart[] = new Vector2D[MAX_KARTS];
	
	/**
	 *	A calculation of where races featuring only one kart will start.
	 */
	private final Vector2D kartStartSingle = new Vector2D();
	
	/**
	 *	Driving characteristics for each kart.
	 *
	 *	@see Kart#setup
	 */
	private final byte[][] kartProps;
	
	/**
	 *	Animation data for each kart. Due to the displayed sprites all being
	 *	references to one master sprite it is necessary to store their
	 *	original state in order to repurpose them to draw multiple instances.
	 */
	private final byte[][] kartAnims = new byte[TOTAL_KARTS][AnimTile.STORAGE_REQUIRED];
	
	/**
	 *	Sprites to be blended with the track render.
	 */
	private final Sprite[] blend = new Sprite[MAX_BLENDED_SPRITES];
	
	/**
	 *	Number of sprites to be blended with the render.
	 */
	private int numToBlend = 0;
	
	/**
	 *	Array of karts sorted per frame by the race order.
	 */
	private final Kart[] order = new Kart[MAX_KARTS];
	
	/**
	 *	Bucket-brigade style delay for smoothing out the camera.
	 */
	private final Vector2D[] camDelay = new Vector2D[CAMERA_DELAY_SIZE];
	
	/**
	 *	The current frame's camera.
	 */
	private final Vector2D thisCam = new Vector2D();
	
	/**
	 *	Player index. Used to decide which kart the camera follows.
	 */
	private int playerIdx = 0;
	
	/**
	 *	Point the current kart is driving towards. Used as a scratch Vector2D.
	 */
	private Vector2D whereTo = null;
	
	/**
	 *	Point nearest the current kart's AI line. Used as a scratch Vector2D.
	 */
	private final Vector2D nearest = new Vector2D();
	
	/**
	 *	Start direction converted to fixed point.
	 */
	private int startDir = 0;
	
	/**
	 *	Traction characteristics for each road surface.
	 */
	private final int[] tract = new int[SURFACE_TYPES];
	
	/**
	 *	Friction characteristics for each road surface.
	 */
	private final int[] frict = new int[SURFACE_TYPES];
	
	/**
	 *	Number of ticks a kart spends in the air after hitting a bump.
	 */
	private int bumpTime = 4;
	
	/**
	 *	Holds the auto acceleration value for each kart.
	 */
	private int[] autoAccl = new int[MAX_KARTS];
	private boolean joyU, joyD;
	
	/**
	 *	Number of game ticks elapsed.
	 */
	private int ticks = 0;
	
	/**
	 *	Lap timing per car, saving each lap for the records.
	 */
	private int[][] lapTime = new int[MAX_KARTS][MAX_LAPS];
	
	/**
	 *	A Vector2D for each possible sprite position.
	 */
	private final Vector2D[][] spritePos = new Vector2D[32][32];
	
	private final byte[] randomRacingLine = new byte[MAX_RANDOM_RACING_LINES];
	private int randomRacingLineIdx = 0;
	
	private final byte[] randomLineChange = new byte[MAX_RANDOM_LINE_CHANGES];
	private int randomLineChangeIdx = 0;
	
	/**
	 *	As karts cross the finish line they are placed from this value, which
	 *	is then incremented. The race is over when the value is equal to the
	 *	number of karts in the race.
	 */
	private int nextFinish = 0;
	
	/**
	 *	How many laps in the current race.
	 */
	private int lapsThisRace = 2;
	
	/**
	 *	Denotes whether events in the race will need the order to be
	 *	recalculated. The order is only calculated a few times per second
	 *	otherwise.
	 */
	private boolean recalcOrder = false;
	
	private final boolean[] humanInput = new boolean[MAX_KARTS];
	private final int[] kartChoice = new int[MAX_KARTS];
	
	/**
	 *	Holds the sprite and surface data, as well as performing the render.
	 */
	private final TrackRenderer track;
	
	private final RaceChrome chrome;
	
	/**
	 *	Camera distance for the TrackRenderer.
	 */
	private final int cdist;
	
	/**
	 *	Whether the TrackRenderer uses a perspective view.
	 */
	private final boolean perspView;
	
	/**
	 *	The current race has pick-ups.
	 */
	private boolean hasPickups = false;
	
	/**
	 *	The current race has power-ups.
	 */
	//private boolean hasPowerups = false;
	
	/**
	 *	A counter per kart denoting which network times need updating.
	 */
	private final byte[] networkLapTimeFlag = new byte[MAX_KARTS];
	
	/**
	 *	Used as a temporary kart instead of allocating space for a new one, or
	 *	constantly having an array lookup.
	 */
	private Kart workKart = null;
	
	/**
	 *	Whether the other karts on the track are ghosts (i.e. they don't
	 *	collide with the player).
	 */
	private boolean ghostMode = true;
	
	public RaceCore(TrackRenderer track, RaceChrome chrome, byte[][] kartProps) {
		this.track  = track;
		this.chrome = chrome;
		
		cdist = track.getCameraDistance();
		perspView = track.isPerspectiveView();
		
		for (int n = 0; n < NUM_RACING_LINES + 1; n++) {
			for (int i = 0; i < MAX_AI_POINTS; i++) {
				aiOrig[n][i] = new Vector2D();
				aiUnit[n][i] = new Vector2D();
				aiCalc[n][i] = new Vector2D();
			}
		}
		for (int n = 0; n < 4; n++) {
			scratchQuad[n] = new Vector2D();
		}
		
		for (int n = 0; n < MAX_BLENDED_SPRITES; n++) {
			blend[n] = new Sprite();
		}
		
		for (int n = 0; n < CAMERA_DELAY_SIZE; n++) {
			camDelay[n] = new Vector2D();
		}
		
		for (int row = 0; row < 32; row++) {
			for (int col = 0; col < 32; col++) {
				spritePos[row][col] = new Vector2D();
			}
		}
		
		/*
		 *	Go through the sprite data and pull out which are used for karts,
		 *	effects, power ups, etc.
		 *
		 *	There should only be one sprite assigned to be the camera and
		 *	power-up, so we'll only take the last ones.
		 */
		int kartN = 0;
		int trfxN = 0;
		int lastSCam = -1;
		int lastPowr = -1;
		for (int n = 0; n < TrackRenderer.MAX_SPRITES; n++) {
			switch ((track.getSpriteData(n) >> SPRITE_DATA_ROTL_TYPE) & SPRITE_DATA_MASK_TYPE) {
			case SP_TYPE_POWR:
				lastPowr = n;
				break;
			case SP_TYPE_KART:
				kartSpriteRef[kartN++] = n;
				break;
			case SP_TYPE_TRFX:
				trfxSpriteRef[trfxN++] = n;
				break;
			case SP_TYPE_SCAM:
				lastSCam = n;
				break;
			}
		}
		
		for (int n = 0; n < MAX_KARTS; n++) {
			kart[n] = new Kart(n);
			kartPos[n] = kart[n].pos;
		}
		for (int n = 0; n < TOTAL_KARTS; n++) {
			track.getSpriteAnimTile(kartSpriteRef[n]).save(kartAnims[n], 0);
		}
		this.kartProps = kartProps;
		
		sCamSpriteRef = lastSCam;
		powrSpriteRef = lastPowr;
		
		for (int n = 0; n < MAX_STATIC_CAMERAS; n++) {
			staticCamOrder[n] = new Vector2D();
		}
		
		for (int n = 0; n < SURFACE_TYPES; n++) {
			tract[n] = Fixed.ONE;
			frict[n] = Fixed.ONE;
		}
	}
	
	/**
	 *	@see Kart#setAISkill
	 */
	public void setAISkill(int aiDistance, int aiCorrect) {
		for (int n = 0; n < MAX_KARTS; n++) {
			kart[n].setAISkill(aiDistance, aiCorrect);
		}
	}
	
	private int getNextRacingLine() {
		if (randomRacingLineIdx >= MAX_RANDOM_RACING_LINES) {
			randomRacingLineIdx = 0;
		}
		return randomRacingLine[randomRacingLineIdx++];
	}
	
	private int getNextLineChange() {
		if (randomLineChangeIdx >= MAX_RANDOM_LINE_CHANGES) {
			randomLineChangeIdx = 0;
		}
		return randomLineChange[randomLineChangeIdx++];
	}
	
	/*
	 * Currently sending 74 bytes per frame for 8 karts.
	 */
	public synchronized int saveNetworkPacket(byte[] data, int n) {
		ByteUtils.shortToBytes(data, n, ticks);
		n += 2;
		for (int i = 0; i < numKartsRacing; i++) {
			n = kart[i].saveNetworkPacket(data, n);
		}
		data[n++] = (byte) nextFinish;
		
		data[n++] = (byte) powerUpState;
		data[n++] = (byte) powerUpPayout;
		data[n++] = (byte) powerUpPlayer;
		
		boolean performedUpdate = false;
		for (int i = 0; i < numKartsRacing; i++) {
			if (networkLapTimeFlag[i] > 0) {
				int lastLap = kart[i].laps - 1;
				if (lastLap >= 0) {
					data[n++] = (byte) ((i << 4) | (lastLap & 0x0F));
					
					ByteUtils.shortToBytes(data, n, lapTime[i][lastLap]);
					
					performedUpdate = true;
				}
				networkLapTimeFlag[i]--;
				break;
			}
		}
		if (!performedUpdate) {
			data[n++] = -1;
		}
		n += 2;
		
		
		return n;
	}
	
	public synchronized int loadNetworkPacket(byte[] data, int n) {
		ticks = ByteUtils.bytesToUnsignedShort(data, n);
		n += 2;
		for (int i = 0; i < numKartsRacing; i++) {
			n = kart[i].loadNetworkPacket(data, n);
			order[kart[i].posn] = kart[i]; // set the order so getKartIndex() works
		}
		nextFinish = data[n++];
		
		powerUpState  = data[n++];
		powerUpPayout = data[n++] & 0xFF;
		powerUpPlayer = data[n++];
		
		int lastLap = data[n++];
		if (lastLap != -1) {
			lapTime[(lastLap & 0xF0) >> 4][lastLap & 0x0F] = ByteUtils.bytesToUnsignedShort(data, n);
		}
		n += 2;
		
		
		return n;
	}
	
	public int save(byte[] data, int n) {
		data[n++] = (byte) randomRacingLineIdx;
		data[n++] = (byte) randomLineChangeIdx;
		System.arraycopy(randomRacingLine, 0, data, n, MAX_RANDOM_RACING_LINES);
		n += MAX_RANDOM_RACING_LINES;
		System.arraycopy(randomLineChange, 0, data, n, MAX_RANDOM_LINE_CHANGES);
		n += MAX_RANDOM_LINE_CHANGES;
		
		ByteUtils.shortToBytes(data, n, ticks);
		n += 2;
		
		for (int i = 0; i < MAX_KARTS; i++) {
			n = kart[i].save(data, n);
		}
		for (int i = 0; i < CAMERA_DELAY_SIZE; i++) {
			n = camDelay[i].save(data, n);
		}
		
		return n;
	}
	
	public int load(byte[] data, int n) {
		randomRacingLineIdx = data[n++];
		randomLineChangeIdx = data[n++];
		System.arraycopy(data, n, randomRacingLine, 0, MAX_RANDOM_RACING_LINES);
		n += MAX_RANDOM_RACING_LINES;
		System.arraycopy(data, n, randomLineChange, 0, MAX_RANDOM_LINE_CHANGES);
		n += MAX_RANDOM_LINE_CHANGES;
		
		ticks = ByteUtils.bytesToUnsignedShort(data, n);
		n += 2;
		
		for (int i = 0; i < MAX_KARTS; i++) {
			n = kart[i].load(data, n);
		}
		for (int i = 0; i < CAMERA_DELAY_SIZE; i++) {
			n = camDelay[i].load(data, n);
		}
		
		return n;
	}
	
	/**
	 *	Sets up the race and resets the variables used throughout (lap times,
	 *	auto-acceleration, race ticks, random number generators, etc.).
	 */
	public void init(int numKartsRacing, Player[] playerObj, int playerIdx, int lapsThisRace, boolean hasPickups, boolean hasPowerups) {
		this.numKartsRacing = numKartsRacing;
		
		randomRacingLineIdx = 0;
		randomLineChangeIdx = 0;
		for (int n = 0; n < MAX_RANDOM_RACING_LINES; n++) {
			randomRacingLine[n] = (byte) Fixed.rand(NUM_RACING_LINES);
		}
		for (int n = 0; n < MAX_RANDOM_LINE_CHANGES; n++) {
			randomLineChange[n] = (byte) Fixed.rand(16);
		}
		
		for (int n = 0; n < numKartsRacing; n++) {
			int which = playerObj[n].kartIdx;
			int grPos = playerObj[n].gridPos;
			int spRef = kartSpriteRef[n];
			
			kart[n].setup(spRef, kartProps[which]);
			kart[n].reset((grPos < 0) ? kartStartSingle : kartStart[grPos], startDir, getNextRacingLine(), grPos);
			
			track.getSpriteAnimTile(spRef).load(kartAnims[which], 0);
			
			order[n] = kart[n];
			
			this.kartChoice[n] = which;
			this.humanInput[n] = playerObj[n].isHuman;
		}
		this.playerIdx = playerIdx;
		
		this.lapsThisRace = lapsThisRace;
		this.hasPickups   = hasPickups;
		if (!hasPickups) {
			removeTrackSprites(SP_TYPE_PICK);
		}
		//this.hasPowerups  = hasPowerups;
		if (!hasPowerups) {
			removeTrackSprites(SP_TYPE_POWR);
		}
		
		chrome.setMapBlips(kartPos, numKartsRacing);
		
		for (int n = 0; n < MAX_KARTS; n++) {
			networkLapTimeFlag[n] = 0;
			for (int i = 0; i < MAX_LAPS; i++) {
				lapTime[n][i] = 0;
			}
			autoAccl[n] = 0;
		}
		ticks = 0;
		
		powerUpState  = POWERUP_STATE_READY;
		powerUpPlayer = 0;
		powerUpPayout = 0;
		
		nextFinish = 0;
		
		camDelay[0].setPolar(Fixed.ONE >> 3, startDir >> Fixed.FIXED_POINT);
		for (int n = 1; n < CAMERA_DELAY_SIZE; n++) {
			camDelay[n].set(camDelay[0]);
		}
		
		render(USE_FOLLOW_CAM);
	}
	
	public void load(String filename) throws IOException {
		DataInputStream in = new DataInputStream(getClass().getResourceAsStream(filename));
		load(in);
		in.close();
		
		in = null;
		
		if (DEBUG) {
			System.out.println("Loaded: " + filename + " (" + Runtime.getRuntime().freeMemory() + "/" + Runtime.getRuntime().totalMemory() + ")");
		}
	}
	
	public void load(DataInput in) throws IOException {
		track.load(in);
		
		/*
		 *	Initialises the AI 'pixel' map then 'draws' the quads which mark
		 *	out the track segments.
		 */
		for (int row = 0; row < TrackRenderer.MAP_ROWS; row++) {
			for (int col = 0; col < TrackRenderer.MAP_COLS; col++) {
				aiQuad[row << TrackRenderer.MAP_ROWS_BITS | col] = -1;
			}
		}
		int numQuads = in.readUnsignedByte();
		for (int n = 0; n < numQuads; n++) {
			for (int i = 0; i < 4; i++) {
				scratchQuad[i].set(in.readUnsignedByte(), in.readUnsignedByte());
			}
			Vector2D.fillQuad(scratchQuad, aiQuad, TrackRenderer.MAP_COLS, TrackRenderer.MAP_ROWS, (byte) n, true);
		}
		
		/*
		 *	Markers signify that an AI point is part of a group of points and
		 *	a kart should maintain its course.
		 */
		for (int n = 0; n < NUM_RACING_LINES + 1; n++) {
			usedMarkers[n] = 0;
		}
		int numMarkerSections = in.readUnsignedByte();
		for (int n = 0; n < numMarkerSections; n++) {
			usedMarkers[n] = in.readByte();
			in.readFully(marker[n], 0, usedMarkers[n]);
		}
		
		/*
		 *	Steps through each of the racing line points and assigns them to
		 *	their corresponding quad.
		 */
		int numLines = in.readUnsignedByte();
		for (int i = 0; i < numLines; i++) {
			int points = in.readUnsignedByte();
			for (int n = 0; n < points; n++) {
				aiOrig[i][n].set(in.readUnsignedByte(), in.readUnsignedByte());
			}
			/*
			 *	The first point is wrapped around to close the line.
			 */
			aiOrig[i][points].set(aiOrig[i][0]);
			
			assignAIPoints(i, points, numQuads);
			
			/*
			 *	Special case code. The last line is used to calculate the
			 *	distance around the track.
			 */
			if (i == NUM_RACING_LINES) {
				aiLineLen[0] = 0;
				for (int n = 1; n <= points; n++) {
					aiLineLen[n] = aiLineLen[n - 1] + (aiUnit[NUM_RACING_LINES][n].mag() >> Fixed.FIXED_POINT);
				}
				aiLineTotal = aiLineLen[points];
			}
		}
		
		startDir = in.readUnsignedByte() << Fixed.FIXED_POINT;
		
		int numSufaces = in.readUnsignedByte();
		for (int n = 0; n < numSufaces; n++) {
			tract[n] = in.readInt();
			frict[n] = in.readInt();
		}
		bumpTime = in.readUnsignedByte();
		
		int airRes = in.readInt();
		for (int n = 0; n < MAX_KARTS; n++) {
			kart[n].setAirResistance(airRes);
		}
		
		if (DEBUG) {
			System.out.println("End of track load");
		}
		
		numStatCams = 0;
		numPickups  = 0;
		
		for (int row = 0; row < TrackRenderer.GRID_ROWS; row++) {
			for (int col = 0; col < TrackRenderer.GRID_COLS; col++) {
				int objN = track.getSpriteIndex(col, row);
				
				spritePos[row][col].set(
					track.getSpriteFineX(col, row) >> 3,
					track.getSpriteFineY(col, row) >> 3);
				
				switch ((track.getSpriteData(objN) >> SPRITE_DATA_ROTL_TYPE) & SPRITE_DATA_MASK_TYPE) {
				case SP_TYPE_PICK:
					numPickups++;
					break;
				case SP_TYPE_KART:
					for (int n = 0; n < MAX_KARTS; n++) {
						if (objN == kartSpriteRef[n]) {
							kartStart[n] = spritePos[row][col];
							break;
						}
					}
					removeTrackSprite(col, row);
					break;
				case SP_TYPE_SCAM:
					staticCam[numStatCams++] = spritePos[row][col];
					removeTrackSprite(col, row);
					break;
				}
			}
		}
		
		kartStartSingle.setToSum(kartStart[0], kartStart[1]);
		kartStartSingle.divScalar(2);
		kartStartSingle.addPolar(Fixed.ONE * 2, startDir >> Fixed.FIXED_POINT);
		
		if (DEBUG) {
			System.out.println("Num fixed cams: " + numStatCams);
		}
		
		/*
		 *	Go through the track tiles looking for track effects that will
		 *	need animating. Also add the reserved effects for collisions
		 *	and power-ups.
		 */
		AnimTileController ctl = track.getSpriteAnimController();
		boolean[] trfxAdded = new boolean[MAX_EFFECTS + 3];
		for (int row = 0; row < TrackRenderer.MAP_ROWS; row++) {
			for (int col = 0; col < TrackRenderer.MAP_COLS; col++) {
				int trfx = (track.getTileData(col, row) >> TILE_DATA_ROTL_TRFX) & TILE_DATA_MASK_TRFX;
				if (!trfxAdded[trfx]) {
					ctl.addAnimTile(trfxSpriteRef[trfx]);
					trfxAdded[trfx] = true;
				}
			}
		}
		ctl.addAnimTile(trfxSpriteRef[EFFECT_COLLIDE]);
		ctl.addAnimTile(trfxSpriteRef[EFFECT_NITROUS]);
		ctl.addAnimTile(trfxSpriteRef[EFFECT_MISFIRE]);
	}
	
	/**
	 *	Removes a sprite of the given position from the track.
	 */
	private void removeTrackSprite(int col, int row) {
		track.getSpriteAnimController().removeAnimTile(track.getSpriteIndex(col, row));
		track.setSpriteIndex(col, row, 0);
	}
	
	/**
	 *	Removes a sprite of the given type from the track.
	 */
	public void removeTrackSprites(int type) {
		for (int row = 0; row < TrackRenderer.GRID_ROWS; row++) {
			for (int col = 0; col < TrackRenderer.GRID_COLS; col++) {
				if (type == ((track.getSpriteData(track.getSpriteIndex(col, row)) >> SPRITE_DATA_ROTL_TYPE) & SPRITE_DATA_MASK_TYPE)) {
					removeTrackSprite(col, row);
				}
			}
		}
	}
	
	/**
	 *	Once the aiOrig array has initially been filled the track map indices
	 *	are assigned. The points are then converted to fixed point format and
	 *	the related aiUnit and aiCalc arrays are filled.
	 *
	 *	@param index  which of the racing lines to operate on
	 *	@param points how many points in this racing line
	 *	@param quads  number of quads marking out the track map
	 */
	private void assignAIPoints(int index, int points, int quads) {
		/*
		 *	Step through each of the racing line points to find the one in the
		 *	lowest track segment. This will be the first point the AI drives
		 *	towards.
		 */
		Vector2D[] aiOrig = this.aiOrig[index];
		int i = 0;
		for (int n = 0; n < points; n++) {
			int q = aiQuad[aiOrig[n].y << TrackRenderer.MAP_ROWS_BITS | aiOrig[n].x];
			if (q >= 0 && q < aiQuad[aiOrig[i].y << TrackRenderer.MAP_ROWS_BITS | aiOrig[i].x]) {
				i = n;
			}
		}
		
		/*
		 *	Now find the point behind the first.
		 */
		int j = i - 1;
		if (j < 0) {
			j = points - 1;
		}
		
		/*
		 *	Then go through each of the indices pointers assigning the
		 *	the current and next points, wrapping back round if required.
		 */
		byte[] aiNext = this.aiNext[index];
		byte[] aiThis = this.aiThis[index];
		byte[] marker = this.marker[index];
		int markerLen = usedMarkers[index];
		for (int n = 0; n < quads; n++) {
			if (n == aiQuad[aiOrig[i].y << TrackRenderer.MAP_ROWS_BITS | aiOrig[i].x]) {
				i++;
				if (i == points) {
					i  = 0;
				}
				j++;
				if (j == points) {
					j  = 0;
				}
			}
			aiNext[n] = (byte)  i;
			aiThis[n] = (byte)  j;
			
			for (int m = markerLen - 1; m >= 0; m--) {
				if (marker[m] == j) {
					aiThis[n] |= 0x80;
					break;
				}
			}
		}
		
		/*
		 *	Convert the points to fixed point format (the Vector2D data is
		 *	originally as integers) then create the precalculated vectors used
		 *	in the AI code.
		 */
		for (int n = 0; n <= points; n++) {
			aiOrig[n].x <<= Fixed.FIXED_POINT;
			aiOrig[n].y <<= Fixed.FIXED_POINT;
		}
		Vector2D[] aiUnit = this.aiUnit[index];
		Vector2D[] aiCalc = this.aiCalc[index];
		for (int n = 0; n <= points; n++) {
			i = n - 1;
			if (i < 0) {
				i = points - 1;
			}
			aiUnit[n].setToDif(aiOrig[n], aiOrig[i]);
			aiCalc[n].setToDiv(aiUnit[n], aiUnit[n].magSquared());
		}
	}
	
	private void doWallCollision(Kart k) {
		int kartCol = (k.pos.x >> Fixed.FIXED_POINT) & (TrackRenderer.MAP_COLS - 1);
		int kartRow = (k.pos.y >> Fixed.FIXED_POINT) & (TrackRenderer.MAP_ROWS - 1);
		
		int hitCol = 0;
		int hitRow = 0;
		
		if (k.lastCol < 0) {
			k.lastCol = kartCol;
		}
		if (k.lastRow < 0) {
			k.lastRow = kartRow;
		}
		if (kartCol != k.lastCol) {
			if (((track.getTileData(kartCol, k.lastRow) >> TILE_DATA_ROTL_TYPE) & TILE_DATA_MASK_TYPE) == TILE_TYPE_WALL) {
				int colX = kartCol << Fixed.FIXED_POINT;
				if (kartCol > k.lastCol) {
					k.pos.x = colX - Fixed.ONE - 1;
					hitCol = -1;
				} else {
					k.pos.x = colX + Fixed.ONE;
					hitCol =  1;
				}
				kartCol = k.lastCol;
			} else {
				k.lastCol = kartCol;
			}
		}
		
		if (kartRow != k.lastRow) {
			if (((track.getTileData(kartCol, kartRow) >> TILE_DATA_ROTL_TYPE) & TILE_DATA_MASK_TYPE) == TILE_TYPE_WALL) {
				int rowY = kartRow << Fixed.FIXED_POINT;
				if (kartRow > k.lastRow) {
					k.pos.y = rowY - Fixed.ONE - 1;
					hitRow = -1;
				} else {
					k.pos.y = rowY + Fixed.ONE;
					hitRow =  1;
				}
				kartRow = k.lastRow;
			} else {
				k.lastRow = kartRow;
			}
		}

		if (hitCol != 0) {
			k.vel.x += hitCol * Fixed.ONE * 6;
		}
		if (hitRow != 0) {
			k.vel.y += hitRow * Fixed.ONE * 6;
		}
	}
	
	private void checkCollisions(boolean includeKartToKart) {
		for (int n = 0; n < numKartsRacing; n++) {
			kart[n].bang = 0;
		}
		
		int kartCol, kartRow, data;
		for (int n = 0; n < numKartsRacing; n++) {
			Kart kartN = kart[n];
			
			if (kartN.bump > 0) {
				kartN.bump--;
			}
			
			/*
			 *	Kart to kart collisions.
			 */
			if (includeKartToKart) {
				for (int m = n + 1; m < numKartsRacing; m++) {
					if (kartN.collide(kart[m])) {
						kartN.bang   |= Kart.BANG_KART;
						kart[m].bang |= Kart.BANG_KART;
					}
				}
			}
			
			/*
			 *	Kart to object collisions.
			 */
			int kartX = kartN.pos.x >> Fixed.FIXED_POINT;
			int kartY = kartN.pos.y >> Fixed.FIXED_POINT;
			
			for (int shiftRow = 0; shiftRow < 2; shiftRow++) {
				kartRow = (kartY >> 2) + shiftRow;
				if ((kartY & 3) < 2) {
					kartRow--;
				}
				kartRow &= 31;
				for (int shiftCol = 0; shiftCol < 2; shiftCol++) {
					kartCol = (kartX >> 2) + shiftCol;
					if ((kartX & 3) < 2) {
						kartCol--;
					}
					kartCol &= 31;
					int objN = track.getSpriteIndex(kartCol, kartRow);
					if (objN != 0) {
						int objData = track.getSpriteData(objN);
						int objPass = (objData >> SPRITE_DATA_ROTL_PASS) & SPRITE_DATA_MASK_PASS;
						int objType = (objData >> SPRITE_DATA_ROTL_TYPE) & SPRITE_DATA_MASK_TYPE;
						if (kartN.collide(spritePos[kartRow][kartCol], objPass * 4)) {
							switch (objType) {
							case SP_TYPE_NORM:
								if (objPass > 0 && kartN.speed > Fixed.ONE * 2) {
									kartN.bang |= Kart.BANG_SCENERY;
								}
								break;
							case SP_TYPE_PICK:
								/*
								 *	Pick-ups can only be collected by human
								 *	players who have yet to finish.
								 */
								if (humanInput[n] && kartN.laps < lapsThisRace) {
									track.setSpriteIndex(kartCol, kartRow, 0);
									kartN.pick++;
									kartN.bang |= Kart.BANG_PICKUP;
									if (DEBUG) {
										System.out.println("Found pick-up! (" + kartN.pick + ")");
									}
								}
								break;
							case SP_TYPE_POWR:
								/*
								 *	Power-ups don't work with the current
								 *	ghost recording implementation (but they
								 *	don't in SMK:SS so I'm not going to loose
								 *	sleep over it!)
								 */
								if (powerUpState == POWERUP_STATE_READY) {
									powerUpState  = POWERUP_SEGMENT_DELAY * POWERUP_SEGMENTS;
									powerUpPlayer = n;
									if (Fixed.rand(2) == 0) {
										powerUpPayout = Fixed.rand(TOTAL_POWERUPS) * POWERUP_PER_BONUS;
									} else {
										powerUpPayout = Fixed.rand(POWERUP_RANDOM);
									}
									if (!hasPickups && powerUpPayout == POWERUP_PICKUP) {
										/*
										 *	Some game variants don't have
										 *	pick-ups so this quietly replaces
										 *	the payout with an alternative.
										 */
										powerUpPayout = POWERUP_NITROUS;
									}
									kartN.bang |= Kart.BANG_POWERUP;
									if (DEBUG) {
										System.out.println("Found power-up!");
									}
								}
								break;
							}
						
						}
					}
				}
			}
			
			/*
			 *	Kart to floor checks.
			 */
			data = track.getTileData(kartN.pos.x >> Fixed.FIXED_POINT, kartN.pos.y >> Fixed.FIXED_POINT);
			switch ((data >> TILE_DATA_ROTL_TYPE) & TILE_DATA_MASK_TYPE) {
			case TILE_TYPE_NORM:
				kartN.setTrackType(tract[TILE_TYPE_NORM], frict[TILE_TYPE_NORM]);
				break;
			case TILE_TYPE_SLOW:
				kartN.setTrackType(tract[TILE_TYPE_SLOW], frict[TILE_TYPE_SLOW]);
				break;
			case TILE_TYPE_FAST:
				kartN.setTrackType(tract[TILE_TYPE_FAST], frict[TILE_TYPE_FAST]);
				break;
			case TILE_TYPE_SKID:
				kartN.setTrackType(tract[TILE_TYPE_SKID], frict[TILE_TYPE_SKID]);
				break;
			case TILE_TYPE_WALL:
				// dealt with int doWallCollision()
				break;
			}
			
			/*
			 *	Track effects. Pick-up effects take priority (but are track
			 *	effects themselves).
			 */
			if (kartN.pufx == 0) {
				if (kartN.speed > Fixed.ONE * 2) {
					kartN.trfx = (data >> TILE_DATA_ROTL_TRFX) & TILE_DATA_MASK_TRFX;
				} else {
					kartN.trfx = 0;
				}
			} else {
				kartN.trfx = kartN.pufx;
			}
			
			doWallCollision(kartN);
			
			if (kartN.vel.magSquared() > Fixed.ONE * 4) {
				if (kartN.bump == 0 && (((data >> TILE_DATA_ROTL_ATTR) & TILE_DATA_MASK_ATTR) & TILE_ATTR_BUMP) != 0) {
					kartN.bump = bumpTime;
				}
			}
		}
	}
	
	public int[] getAllStats(int[] stats, int index) {
		workKart = kart[index];
		stats[STATS_TICKS]    = ticks;
		stats[STATS_POSITION] = workKart.posn;
		stats[STATS_LAPS]     = workKart.laps;
		stats[STATS_SPEED]    = workKart.speed >> Fixed.FIXED_POINT;
		stats[STATS_PICKUPS]  = workKart.pick;
		stats[STATS_KART_FX]  = workKart.bang;
		
		stats[STATS_POW_STATE]  = powerUpState;
		stats[STATS_POW_PLAYER] = powerUpPlayer;
		stats[STATS_POW_PAYOUT] = powerUpPayout;
		return stats;
	}
	
	public int getStats(int statsType, int index) {
		switch (statsType) {
		case STATS_TICKS:
			return ticks;
		case STATS_POSITION:
			return kart[index].posn;
		case STATS_LAPS:
			return kart[index].laps;
		case STATS_SPEED:
			return kart[index].speed >> Fixed.FIXED_POINT;
		case STATS_PICKUPS:
			return kart[index].pick;
		case STATS_KART_FX:
			return kart[index].bang;
		case STATS_POW_STATE:
			return powerUpState;
		case STATS_POW_PLAYER:
			return powerUpPlayer;
		case STATS_POW_PAYOUT:
			return powerUpPayout;
		default:
			throw new IllegalArgumentException();
		}
	}
	
	/**
	 *	Gets the kart index from the race position.
	 */
	public int getKartIndex(int pos) {
		return order[pos].index;
	}
	
	public int getLapTime(int n, int lap) {
		return lapTime[n][lap];
	}
	
	public int getNextFinishPosition() {
		return nextFinish;
	}
	
	public void loop() {
		if (chrome == null) {
			return;
		}
		workKart = kart[playerIdx];
		chrome.setStats(ticks, workKart.posn, workKart.laps + 1, workKart.pick, powerUpState, powerUpPayout, kartChoice[powerUpPlayer]);
	}
	
	/************************************************************************/
	
	public void loop(int[] joy, boolean collision) {
		recalcOrder = false;
		for (int n = 0; n < numKartsRacing; n++) {
			boolean auto = !humanInput[n] || n >= joy.length || joy[n] < 0;
			
			Kart kartN = kart[n];
			int seg = aiQuad[((kartN.pos.y >> Fixed.FIXED_POINT) & 127) << TrackRenderer.MAP_ROWS_BITS | ((kartN.pos.x >> Fixed.FIXED_POINT) & 127)];
			if (seg < 0) {
				seg = kartN.pseg; // as if nothing ever happened...
				if (seg < 0) {
					seg = 0;
				}
			}
			
			if (!auto) {
				if ((joy[n] & Joystick.BUTTON_U) != 0) {
					autoAccl[n]++;
				}
				if ((joy[n] & Joystick.BUTTON_D) != 0) {
					autoAccl[n]--;
				}
				
				joyD = false;
				if (autoAccl[n] <= -3) {
					autoAccl[n]  = -3;
					joyD = true;
				}
				joyU = false;
				if (autoAccl[n] >= +3) {
					autoAccl[n]  = +3;
					joyU = true;
				}
				kartN.update(joyU, joyD, (joy[n] & Joystick.BUTTON_L) != 0, (joy[n] & Joystick.BUTTON_R) != 0);
				
				/*
				 *	To ensure karts can switch between auto and manual
				 *	control, useful when the network connection is flakey,
				 *	their segment still needs to be known,
				 */
				kartN.next = aiNext[kartN.path][seg] & AI_POINT_MASK;
			} else {
				int path = kartN.path;
				int next = aiNext[path][seg] & AI_POINT_MASK;
				if (next != kartN.next) {
					if (aiThis[path][seg] >= 0) {
						int rand = getNextLineChange();
						if (rand < NUM_RACING_LINES) {
							path = getNextRacingLine();
							next = aiNext[path][seg] & AI_POINT_MASK;
							kartN.path = path;
						}
					}
					kartN.next = next;
				}
				whereTo = aiOrig[kartN.path][next];
				
				kartN.update(whereTo, Vector2D.pointNearestLine(
					aiOrig[path][aiThis[path][seg] & AI_POINT_MASK],
						whereTo, kartN.pos, nearest));
			}
			
			/*
			 *	Lap counter.
			 */
			if (seg == 1 && kartN.pseg == 0) {
				kartN.flag = true;
			} else if (seg == 0) {
				if (kartN.flag && kartN.pseg > 1) {
					if (kartN.laps < MAX_LAPS) {
						lapTime[n][kartN.laps] = ticks;
						networkLapTimeFlag[n]  = LAP_TIME_PROPAGATION_TICKS;
					}
					if (kartN.done < 0 && ++kartN.laps == lapsThisRace) {
						kartN.done  = nextFinish++;
					}
					if (DEBUG) {
						System.out.println("Kart " + n + " lap " + kartN.laps);
					}
					recalcOrder = true;
				} else {
					kartN.flag = false;
				}
			}
			
			
			int lineThis = aiThis[3][seg] & AI_POINT_MASK;
			int lineNext = aiNext[3][seg] & AI_POINT_MASK;
			
			Vector2D.pointNearestLine(aiUnit[3][lineNext],
				 aiCalc[3][lineNext], aiOrig[3][lineThis], kartN.pos, nearest);
			nearest.sub(aiOrig[3][lineThis]);
			int travel = aiLineLen[lineThis] + (Fixed.hyp(0, 0, nearest.x, nearest.y) >> Fixed.FIXED_POINT);
			
			kartN.dist = kartN.laps * aiLineTotal;
			if (kartN.flag || seg == 0) {
				kartN.dist += travel;
			} else {
				kartN.dist -= aiLineTotal - travel;
			}
			
			/*
			 *	Segment timer. If an AI car spends more frames than
			 *	thought necessary in one segment then it's probably stuck
			 *	behind a static object, if so, and is not currently in
			 *	view, then move it back to a point on the racing line.
			 *
			 *	@see #MAX_SEG_TIME
			 */
			if (kartN.pseg != seg) {
				kartN.segt  = 0;
			} else {
				kartN.segt++;
			}
			
			if (auto && kartN.segt > MAX_SEG_TIME && !kartN.view) {
				kartN.warp(aiOrig[kartN.path][aiThis[kartN.path][seg] & AI_POINT_MASK]);
			}
			
			kartN.pseg = seg;
		}
		
		/*
		 *	How about: a one in two chance of being definitely given an award.
		 *	Reels have cherries, bananas, sevens and grapes (four choices),
		 *	with a 12-bit random number generated, 4-bits per reel. The 1-2
		 *	chance thing forces the top 8-bits to match the lower 4.
		 *
		 *	Note: currently '0' is awarded, which is reserved for NONE.
		 */
		if (powerUpState == POWERUP_STATE_PAYOUT) {
			if (DEBUG) {
				System.out.println("powerUpPayout: " + powerUpPayout);
			}
			if ((powerUpPayout % POWERUP_PER_BONUS) == 0) {
				switch (powerUpPayout / POWERUP_PER_BONUS) {
				case POWERUP_NITROUS / POWERUP_PER_BONUS:
					kart[powerUpPlayer].powerUp(Kart.POWERUP_NITROUS, 16 * 4, EFFECT_NITROUS);
					if (DEBUG) {
						System.out.println("Awarded nitrous");
					}
					break;
				case POWERUP_MISFIRE / POWERUP_PER_BONUS:
					kart[powerUpPlayer].powerUp(Kart.POWERUP_MISFIRE, 16 * 1, EFFECT_MISFIRE);
					if (DEBUG) {
						System.out.println("Awarded misfire");
					}
					break;
				case POWERUP_PICKUP / POWERUP_PER_BONUS:
					kart[powerUpPlayer].pick++;
					if (DEBUG) {
						System.out.println("Awarded pick-up! (" + kart[powerUpPlayer].pick + ")");
					}
					break;
				case POWERUP_SPINOUT / POWERUP_PER_BONUS:
					kart[powerUpPlayer].powerUp(Kart.POWERUP_SPINOUT, 16 * 2, 0);
					if (DEBUG) {
						System.out.println("Awarded spinout");
					}
					break;
				}
			}
		}
		if (powerUpState > POWERUP_STATE_READY) {
			powerUpState--;
		}
		
		checkCollisions(collision);
		
		
		/**
		 *	The race position is recalculated either whenever one of the karts
		 *	crosses the finishe line or every sixteen ticks (one second).
		 */
		if (recalcOrder || ticks % 16 == 0) {
			QuickSort.sort(order, 0, numKartsRacing - 1);
			for (int n = 0; n < numKartsRacing; n++) {
				order[n].posn = n;
			}
		}
		
		ticks++;
		
		loop();
	}
	
	/**
	 *	Prematurely ends a race and estimates the finishing times. It's not an
	 *	exact reflection of how the race might end, more a guess based on the
	 *	current kart positions. 
	 */
	public void estimate() {
		if (nextFinish >= numKartsRacing) {
			return;
		}
		int raceTotal = aiLineTotal * lapsThisRace;
		for (int n = 0; n < numKartsRacing; n++) {
			workKart = kart[n];
			if (workKart.done < 0) {
				workKart.laps = lapsThisRace;
				if (workKart.dist < 1) {
					/*
					 *	No race should ever finish with karts that have yet to
					 *	cross the start line, but just in case the time is
					 *	is set from with kart travelling at half a pixel/unit
					 *	per frame, much slower than any would be for real.
					 */
					lapTime[n][lapsThisRace - 1] = raceTotal * 16 - workKart.dist;
				} else {
					lapTime[n][lapsThisRace - 1] = ticks * raceTotal / workKart.dist;
				}
			}
		}
		QuickSort.sort(order, 0, numKartsRacing - 1);
		for (int n = 0; n < numKartsRacing; n++) {
			workKart = order[n];
			if (workKart.done < 0) {
				workKart.done = nextFinish++;
			}
		}
		recalcOrder = true;
	}
	
	/**
	 *	Turns karts other than the player's into ghosts (used for time trial).
	 */
	public void setGhostMode(boolean active) {
		ghostMode = active;
	}
	
	public void render(int camType) {
		
		workKart = kart[playerIdx];
		
		/*************************** Camera position ************************/
		
		switch (camType) {
		case USE_FOLLOW_CAM:
			for (int n = CAMERA_DELAY_SIZE - 1; n > 0; n--) {
				camDelay[n].set(camDelay[n - 1]);
			}
			camDelay[0].setPolar(Fixed.ONE >> 3, workKart.posA >> Fixed.FIXED_POINT);
			
			thisCam.set(camDelay[0]);
			for (int n = CAMERA_DELAY_SIZE - 1; n > 0; n--) {
				thisCam.add(camDelay[n]);
			}
			
			cameraX = workKart.pos.x << 3;
			cameraY = workKart.pos.y << 3;
			cameraA = -thisCam.dir() + 64;
			if (cdist != 0) {
				cameraX -= cdist * Fixed.cos(cameraA);
				cameraY -= cdist * Fixed.sin(cameraA);
			}
			break;
		case USE_FINISH_CAM:
			/*
			 *	Does absolutely nothing! The camera just stops at the point
			 *	where it was at last (should be just before the finish line).
			 */
			break;
		case USE_STATIC_CAM:
			for (int n = 0; n < numStatCams; n++) {
				staticCamOrder[n].set(staticCam[n]);
				staticCamOrder[n].sub(workKart.pos);
			}
			QuickSort.sort(staticCamOrder, 0, numStatCams - 1);
			
			thisCam.set(staticCamOrder[0]);
			cameraX = (thisCam.x + workKart.pos.x) << 3;
			cameraY = (thisCam.y + workKart.pos.y) << 3;
			cameraA = -thisCam.dir() - 64;
			/*if (cdist != 0) {
				cameraX -= cdist * Fixed.cos(cameraA);
				cameraY -= cdist * Fixed.sin(cameraA);
			}*/
			break;
		}
		cameraA &= 0xFF;
		
		/********************************************************************/
		
		numToBlend = 0;
		for (int n = numKartsRacing - 1; n >= 0 ; n--) {
			if (ghostMode && n != playerIdx && (ticks & 1) == 0) {
				continue;
			}
			workKart = kart[n];
			/*
			 *	Add each kart to the array of sprites blended with the render,
			 *	passing a reference to track whether it's in view, and a bump
			 *	height (but only if the camera is tracking the player).
			 *
			 *	Note: the depth ('d') of the sprites used to be set here,
			 *	which is wrong, as the calculations should be minus the camera
			 *	distance. The value is now left at zero, to be worked out by
			 *	each renderer, with the track effect set either one above or
			 *	below that.
			 */
			blend[numToBlend].set(workKart.spRef, workKart.pos.x << 3, workKart.pos.y << 3, 0,
				(((n + 1) & Sprite.DATA_MASK_OBJREF) << Sprite.DATA_ROTL_OBJREF)
				| ((camType == USE_FOLLOW_CAM) ? ((workKart.bump & Sprite.DATA_MASK_BUMP_Y) << Sprite.DATA_ROTL_BUMP_Y) : 0));
			
			if (n == playerIdx && camType == USE_FOLLOW_CAM && workKart.speed > Fixed.ONE * 2 && (ticks & 1) == 0) {
				blend[numToBlend].data |= Sprite.DATA_MASK_BUZZ_Y << Sprite.DATA_ROTL_BUZZ_Y;
			}
			/*
			 *	Draws track effects only if the kart is being viewed from
			 *	behind or top-down. The collision effect should work from any
			 *	angle.
			 *
			 *	NOTE: this calculation only works when fixed there are 256
			 *	points in the fixed-point angles, and we're not in 2D!
			 */
			if (perspView) {
				blendKartEffect = (((cameraA - (workKart.posA >> Fixed.FIXED_POINT) + 16) & 0xFF) >> 5) == 0;
			} else {
				blendKartEffect = true;
			}
			if ((blendKartEffect && workKart.trfx != 0) || workKart.bang == Kart.BANG_KART) {
				int fx = trfxSpriteRef[(workKart.bang == Kart.BANG_KART) ? EFFECT_COLLIDE : workKart.trfx];
				blend[++numToBlend].set(blend[numToBlend - 1], fx, 0);
				if (((track.getSpriteData(fx) >> SPRITE_DATA_ROTL_BOTM) & SPRITE_DATA_MASK_BOTM) != 0) {
					blend[numToBlend].d++;
				} else {
					blend[numToBlend].d--;
				}
			}
			numToBlend++;
		}
		
		switch (powerUpState) {
		case POWERUP_STATE_READY:
			break;
		case POWERUP_STATE_PAYOUT:
			track.getSpriteAnimTile(powrSpriteRef).reset();
			break;
		default:
			track.setSpriteFrame(powrSpriteRef, 2);
		}
		
		for (int n = 0; n < numKartsRacing; n++) {
			workKart = kart[n];
			if (perspView) {
				track.setSpriteFrame(workKart.spRef, ((cameraA - (workKart.posA >> Fixed.FIXED_POINT) + (1 << KART_FRAME_SHIFT - 1)) & 0xFF) >> KART_FRAME_SHIFT);
			} else {
				track.setSpriteFrame(workKart.spRef, ((192     - (workKart.posA >> Fixed.FIXED_POINT) + (1 << KART_FRAME_SHIFT - 1)) & 0xFF) >> KART_FRAME_SHIFT);
			}
		}
		
		track.render(cameraX, cameraY, cameraA, blend, numToBlend, (camType == USE_FOLLOW_CAM) ? kart[playerIdx].bump : 0);
		track.cycle();
		
		/*
		 *	Determine which of the karts are in view. The AI uses this to
		 *	when decided whether to course-correct karts without the player
		 *	noticing.
		 */
		for (int n = numToBlend - 1; n >= 0; n--) {
			int i = (blend[n].data >> Sprite.DATA_ROTL_OBJREF) & Sprite.DATA_MASK_OBJREF;
			if (i > 0) {
				kart[i - 1].view = blend[n].view;
			}
		}
	}
	
	/************************************************************************/
	
	/**
	 *	Number of racing lines to guide the AI. This number should be one less
	 *	than the total number of lines (in the Illustrator file) due to the
	 *	last one acting as an average of the track routes (for calculating the
	 *	race order).
	 */
	private static final int NUM_RACING_LINES = 3;
	
	/**
	 *	Number of points in the AI's racing line. This must be less than 128
	 *	as a byte arrays are used for storing point indices (with the most
	 *	significant bit as a 'no change' flag).
	 */
	private static final int MAX_AI_POINTS = 48;
	
	/**
	 *	Markers signify that an AI point is part of a group of points.
	 */
	private static final int MAX_AI_MARKERS = 16;
	
	/*
	 *	Masks the first seven bits of the AI point indices.
	 */
	private static final int AI_POINT_MASK = 0x7F;
	
	/**
	 *	Number of segments the track is broken into to for determining the
	 *	closest AI point.
	 */
	private static final int MAX_AI_SEGMENTS = 128;
	
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
	
	public static final int SP_TYPE_NORM = 0;
	public static final int SP_TYPE_PICK = 1;
	public static final int SP_TYPE_POWR = 2;
	public static final int SP_TYPE_KART = 3;
	public static final int SP_TYPE_TRFX = 4;
	public static final int SP_TYPE_SCAM = 5;
	
	public static final int SP_PASS_THRU = 0;
	
	/************************************************************************/
	
	public static final int TILE_DATA_BITS_TYPE = 4;
	public static final int TILE_DATA_BITS_ATTR = 3;
	public static final int TILE_DATA_BITS_TRFX = 4;
	
	public static final int TILE_DATA_MASK_TYPE = (1 << TILE_DATA_BITS_TYPE) - 1;
	public static final int TILE_DATA_MASK_ATTR = (1 << TILE_DATA_BITS_ATTR) - 1;
	public static final int TILE_DATA_MASK_TRFX = (1 << TILE_DATA_BITS_TRFX) - 1;
	
	public static final int TILE_DATA_ROTL_TYPE = 0;
	public static final int TILE_DATA_ROTL_ATTR = TILE_DATA_ROTL_TYPE + TILE_DATA_BITS_TYPE;
	public static final int TILE_DATA_ROTL_TRFX = TILE_DATA_ROTL_ATTR + TILE_DATA_BITS_ATTR;
	
	public static final int TILE_TYPE_NORM = 0;
	public static final int TILE_TYPE_SLOW = 1;
	public static final int TILE_TYPE_FAST = 2;
	public static final int TILE_TYPE_SKID = 3;
	public static final int TILE_TYPE_WALL = 4;
	
	public static final int TILE_ATTR_BUMP = 1;
	public static final int TILE_ATTR_STRT = 2;
	
	public static final int TILE_TRFX_NONE = 0;
	
	/************************************************************************/
	
	/**
	 *	The camera follows behind the player in a Doom style.
	 */
	public static final int USE_FOLLOW_CAM = 0;
	
	/**
	 *	The camera stops dead (on the finish line).
	 */
	public static final int USE_FINISH_CAM = 1;
	
	/**
	 *	The camera is chosen from the nearest static camera.
	 */
	public static final int USE_STATIC_CAM = 2;
	
	/**
	 *	The camera rotates around the player.
	 */
	public static final int USE_ROTATE_CAM = 2;
	
	/**
	 *	Total number of karts available to choose from.
	 */
	public static final int TOTAL_KARTS = 10;
	
	/**
	 *	Maximum karts racing.
	 */
	public static final int MAX_KARTS = 8;
	
	/**
	 *	Maximum number of laps in a race.
	 */
	public static final int MAX_LAPS = 3;
	
	/**
	 *	Maximum number of effect sprites.
	 */
	private static final int MAX_EFFECTS = 13;
	
	/**
	 *	Index of the kart collision effect.
	 */
	private static final int EFFECT_COLLIDE = 0;
	private static final int EFFECT_NITROUS = MAX_EFFECTS + 1;
	private static final int EFFECT_MISFIRE = MAX_EFFECTS + 2;
	
	/**
	 *	Number of frames to smooth the camera out over. This is quite an
	 *	important value to tweak when tuning the feel of the game. Too high it
	 *	starts to lag, too low and it's too choppy. The actual value depends
	 *	on the number of animation frames used for the cars, plus the track
	 *	materials settings.
	 */
	private static final int CAMERA_DELAY_SIZE = 8;
	
	/**
	 *	Maximum number of cameras positioned around the track.
	 */
	private static final int MAX_STATIC_CAMERAS = 16;
	
	/**
	 *	Number of game ticks an AI kart can stay in one segment before
	 *	correcting its course.
	 */
	private static final int MAX_SEG_TIME = 45;
	
	/**
	 *	Maximum number of sprites blended with the rendered view. Track
	 *	sprites are static, limited to one per grid square, so drawing is
	 *	optimised with this in mind, but the karts and 'effects' need to be
	 *	combined with this static data at render time.
	 */
	private static final int MAX_BLENDED_SPRITES = MAX_KARTS * 2;
	
	private static final int SURFACE_TYPES = 4;
	
	private static final int MAX_RANDOM_RACING_LINES = 32;
	private static final int MAX_RANDOM_LINE_CHANGES = 32;
	
	/************************************************************************/
	
	/**
	 *	How many parts to the power-up process. With the fruit machine style
	 *	we have three reels which stop individually, followed by showing the
	 *	results, then an 'arcade' flash, and finally the power-up is given.
	 */
	public static final int POWERUP_SEGMENTS = 5;
	
	/**
	 *	How long between each part of the power-up in game ticks.
	 *
	 *	Note: the fruit machine implementation relies on this being an even
	 *	number in order to perform the animation.
	 */
	public static final int POWERUP_SEGMENT_DELAY = 8;
	
	public static final int POWERUP_STATE_READY  = -1;
	public static final int POWERUP_STATE_PAYOUT =  0;
	
	public static final int TOTAL_POWERUPS = 4;
	
	private static final int POWERUP_RANDOM = TOTAL_POWERUPS * TOTAL_POWERUPS * TOTAL_POWERUPS;
	private static final int POWERUP_PER_BONUS = (POWERUP_RANDOM - 1) / (TOTAL_POWERUPS - 1);
	
	public static final int POWERUP_NITROUS = 0 * POWERUP_PER_BONUS;
	public static final int POWERUP_MISFIRE = 1 * POWERUP_PER_BONUS;
	public static final int POWERUP_PICKUP  = 2 * POWERUP_PER_BONUS;
	public static final int POWERUP_SPINOUT = 3 * POWERUP_PER_BONUS;
	
	/************************************************************************/
	
	public static final int STATS_TICKS    = 0;
	public static final int STATS_POSITION = 1;
	public static final int STATS_LAPS     = 2;
	public static final int STATS_SPEED    = 3;
	public static final int STATS_PICKUPS  = 4;
	public static final int STATS_KART_FX  = 5;
	
	public static final int STATS_POW_STATE  = 6;
	public static final int STATS_POW_PLAYER = 7;
	public static final int STATS_POW_PAYOUT = 8;
	
	/**
	 *	How many game ticks to broadcast the lap times for. This is a dirty
	 *	workaround for phones missing network packets due to timing
	 *	differences.
	 */
	private static final int LAP_TIME_PROPAGATION_TICKS = 3;
	
	/**
	 *	Bitwise shift to use when choosing the kart frame. A value of 4 should
	 *	be used for a 16 frame rotation, 3 for 32 frames.
	 */
	private static final int KART_FRAME_SHIFT = 4;
}