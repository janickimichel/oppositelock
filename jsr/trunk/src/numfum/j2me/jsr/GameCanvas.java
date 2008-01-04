package numfum.j2me.jsr;

import java.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

import numfum.j2me.jsr.generic.AbstractCanvas;
import numfum.j2me.jsr.generic.AnimTile;
import numfum.j2me.jsr.generic.Positionable;
import numfum.j2me.jsr.generic.PositionableContainer;
import numfum.j2me.jsr.generic.SolidColour;
import numfum.j2me.jsr.generic.SoundPlayer;
import numfum.j2me.jsr.generic.bkgnd.BufferedTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.ContinuousTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.ShuffledTiledOverlay;
import numfum.j2me.jsr.generic.bkgnd.TiledOverlay;
import numfum.j2me.jsr.multiplayer.MultiplayerClient;
import numfum.j2me.jsr.multiplayer.MultiplayerServer;
import numfum.j2me.jsr.renderer.TrackRendererM3G;
import numfum.j2me.jsr.renderer.TrackRendererMode7;
import numfum.j2me.jsr.renderer.TrackRendererTopdown;
import numfum.j2me.text.BitmapFont;
import numfum.j2me.text.IconMenu;
import numfum.j2me.text.Line;
import numfum.j2me.text.ListMenu;
import numfum.j2me.text.ScrollingList;
import numfum.j2me.text.StringManager;
import numfum.j2me.text.TextView;
import numfum.j2me.text.effect.AnimEffect;
import numfum.j2me.text.effect.BlinkEffect;
import numfum.j2me.text.effect.JumpEffect;
import numfum.j2me.text.effect.TextEffect;
import numfum.j2me.util.ByteUtils;
import numfum.j2me.util.Fixed;
import numfum.j2me.util.Joystick;
import numfum.j2me.util.QuickSort;
import numfum.j2me.util.Vector2D;

public final class GameCanvas extends AbstractCanvas implements Constants, Runnable {
	/**
	 *	Midlet owning this canvas. Used to access properties.
	 */
	private final MIDlet parent;
	
	/**
	 *	Whether the screen width was set externally or the phone was left to
	 *	decide the size. Some phones, notably Nokia Series 60 DP2, don't
	 *	report the correct size until after the canvas has been painted for
	 *	the first time.
	 */
	private final boolean forcedW;
	
	/**
	 *	Whether the screen width was set externally or the phone was left to
	 *	decide the size.
	 *
	 *	@see #forcedW
	 */
	private final boolean forcedH;
	
	/**
	 *	Width of the phone screen.
	 */
	private int screenW;
	
	/**
	 *	Height of the phone screen.
	 */
	private int screenH;
	
	/**
	 *	Width of the screen area used by the game.
	 */
	private int viewW;
	
	/**
	 *	Height of the screen area used by the game.
	 */
	private int viewH;
	
	private int halfW;
	private int halfH;
	
	private int viewHQ1; // quarters
	private int viewHQ3;
	
	/**
	 *	Height of the remaining screen area when a title is showing.
	 */
	private int contentH;
	
	private int halfContentH;
	
	/**
	 *  Height of the Mode-7 floor.
	 */
	private int floorH;
	
	/**
	 *	Whether the MIDlet is running or not.
	 */
	private boolean running = true;
	
	/**
	 *	Player's current joystick. The default is to record three minutes (a
	 *	game usually lasts under two).
	 */
	private final Joystick joyPlayer = new Joystick(GHOST_SECONDS);
	
	/**
	 *	The joystick used to drive the ghost karts. Each track can have a
	 *	recording saved to the RecordStore, which is then loaded into the
	 *	joystick's 'memory'.
	 */
	private final Joystick joyRecord = new Joystick(GHOST_SECONDS);
	
	/**
	 *	General purpose buffer where the game state can stored or manipulated.
	 *
	 *	The contents are:
	 *		0: kart index
	 *		1: number of laps recorded
	 *		2: lap time as shorts * number of laps (2 * 3 ordinarily)
	 *		8: joystick data (requires 2 bytes more than GHOST_SECONDS)
	 *	
	 *	TODO: change this to a more efficient RLE'd buffer for all recordings,
	 *	loaded at startup and saved when quitting.
	 */
	private final byte[] stateBuffer = new byte[4 + RaceCore.MAX_LAPS * 2 + GHOST_SECONDS];
	
	/**
	 *	Whether the ghost car is valid for each track.
	 */
	private boolean[] validGhost = new boolean[TOTAL_TRACKS];
	
	private static final int DEFAULT_SOFTKEY_CODE_L = -6;
	private static final int DEFAULT_SOFTKEY_CODE_R = -7;
	
	private final int softKeyCodeL;
	private final int softKeyCodeR;
	private final int softKeyCodeLAbs;
	private final int softKeyCodeRAbs;
	
	private boolean softKeyL = false;
	private boolean softKeyR = false;
	
	/**
	 *	Joystick input for all players (inc. ghost and remote).
	 */
	private final int[] joyState = new int[RaceCore.MAX_KARTS];
	
	/**
	 *	Multiplayer network buffer.
	 */
	private final byte[][] clientStateBuffer = new byte[RaceCore.MAX_KARTS - 1][];
	private final byte[][] serverStateBuffer = new byte[RaceCore.MAX_KARTS - 1][];
	
	/**
	 *	Snapshot of the game state. This is sent out to all clients.
	 */
	private final byte[] serverState;
	private final byte[] clientState;
	
	/**
	 *	Container that holds all current on-screen elements.
	 */
	private PositionableContainer container;
	
	private ContinuousTiledLayer menuBkgnd;
	private TiledOverlay menuLHS;
	private TiledOverlay menuRHS;
	private TiledOverlay menuMid;
	private TiledOverlay resultBar;
	
	private BitmapFont textFont;
	private BitmapFont iconFont;
	private BitmapFont gameFont;
	
	private int iconFontH;
	private int textFontH;
	
	/**
	 *	A pool of Line objects to be used and reused whenever a generic line
	 *	of text needs displaying.
	 */
	private final Line[] textLine = new Line[MAX_LINES];
	
	/**
	 *	How many of the text lines are in use on the current screen.
	 */
	private int usedTextLines = 0;
	
	/**
	 *	A pool of Line objects to be used and reused whenever a generic line
	 *	of icons needs displaying.
	 */
	private final Line[] iconLine = new Line[MAX_LINES];
	
	/**
	 *	How many of the icon lines are in use on the current screen.
	 */
	private int usedIconLines = 0;
	
	/**
	 *	A text-based menu taking up half the screen height.
	 */
	private ListMenu mainMenu;
	
	/**
	 *	A text-based menu filling the screen.
	 */
	private ListMenu tallMenu;
	
	/**
	 *	An icon-based menu taking up half the screen height.
	 */
	private IconMenu iconMenu;
	
	/**
	 *	A scrolling text viewer filling the screen.
	 */
	private TextView textView;
	
	/**
	 *	A scrolling text viewer filling the free content area.
	 */
	private TextView miniView;
	
	/**
	 *	Collection of cars that drive in the background.
	 */
	private final Line[] passingCar = new Line[MAX_PASSING_CARS];
	
	/**
	 *	How fast each of the passing cars are travelling.
	 */
	private final int[] passingCarSpeed = new int[MAX_PASSING_CARS];
	
	/**
	 *	Which car will be displayed next.
	 */
	private int nextPassingCar = 0;
	
	/**
	 *	Whether the current screen is showing passing cars.
	 */
	private boolean hasPassingCars = false;
	
	private final TextEffect jumpFast = new JumpEffect(JumpEffect.MOVE_VERTICALLY);
	private final TextEffect jumpSlow = new JumpEffect(JumpEffect.MOVE_VERTICALLY, 1);
	private final TextEffect jumpSide = new JumpEffect(JumpEffect.MOVE_HORIZONTALLY);
	private final TextEffect blinking = new BlinkEffect(1);
	
	private final TextEffect starAnim = new AnimEffect(0, 1, 2);
	private final TextEffect bestAnim = new AnimEffect(0, 1, 1);
	
	private SolidColour solidBlack;
	private SolidColour solidWhite;
	
	/**
	 *	Holds a reference to the character or track name text.
	 */
	private Line nameLine = null;
	
	/**
	 *	Holds a reference to the character or track name preview icon text.
	 */
	private Line prevLine = null;
	
	/**
	 *	Holds references to the stars used to grade each kart.
	 */
	private final Line[] starLine = new Line[NUM_STARS];
	
	/**
	 *	Sub-container for grouping text and other content. This is to be used
	 *	before any other available sub-container.
	 */
	private final PositionableContainer subContA = new PositionableContainer(PositionableContainer.DEFAULT_SIZE);
	
	/**
	 *	Sub-container for grouping text and other content.
	 *
	 *	@see #subContA
	 */
	private final PositionableContainer subContB = new PositionableContainer(PositionableContainer.DEFAULT_SIZE);
	
	/**
	 *	A container used to work around resetting of the clip region on MIDP1
	 *	phones. It works because containers are special-cased to force the
	 *	clip back to its original value.
	 */
	private final PositionableContainer dummyCont = new PositionableContainer(0);
	
	private Line charTick;
	private Line charBack;
	private Line charBackSmall;
	private Line charOkay;
	
	/**
	 *	Hourglass icon to show the game is busy.
	 */
	private Line charHourglass;
	
	private Line iconCountdown;
	
	private TrackRenderer track = null;
	private RaceCore logic = null;
	
	/**
	 *	Current game mode.
	 */
	private int mode = MODE_STARTUP;
	
	/**
	 *	Game mode before an interruption or pause.
	 */
	private int lastMode = MODE_NONE;
	
	/**
	 *	Current selected race mode.
	 */
	private int raceMode = 0;
	
	/**
	 *	Current selected level (tournament).
	 */
	private int levelNum = 0;
	
	/**
	 *	Current playing track.
	 */
	private int trackNum = 0;
	
	/**
	 *	Counts the player into the game.
	 */
	private int countdown = 0;
	
	/**
	 *	Current selected kart from the character screen.
	 */
	private int selectedKart= 0;
	
	/**
	 *	How many of the karts have been unlocked, the default being as many
	 *	as are racing.
	 */
	private int unlockedKarts = RaceCore.MAX_KARTS + 0;
	
	/**
	 *	Whether the bonus track has been unlocked.
	 */
	private boolean unlockedTrack = false;
	
	/**
	 *	Statistics and details associated with each player.
	 */
	private final Player[] player = new Player[RaceCore.MAX_KARTS];
	
	/**
	 *	References to the Player objects sorted by race order.
	 */
	private final Player[] sorter = new Player[RaceCore.MAX_KARTS];
	
	/**
	 *	Holds the results of the race (position, player and time).
	 */
	private final char[][] raceResultLine = new char[RaceCore.MAX_KARTS][RESULTS_LINE_LENGTH];
	
	/**
	 *	Holds the lap times and total time text.
	 */
	private final char[][] lapTimeChar = new char[LAPS_PER_RACE + 1][];
	
	
	/**
	 *	The 'chrome' around the actual race portion of the game.
	 */
	private RaceChromeImpl chrome;
	
	/**
	 *	Test result of whether the phone has vibrate capabilities. Used to
	 *	decide whether to show a menu option for vibration or not. It should
	 *	be enough to call MIDP2.0's Display.vibrate() to test for vibration
	 *	support (or the Nokia API's equivalent) but some phones wrongly report
	 *	this.
	 */
	private boolean hasVibrate = false;
	
	/**
	 *	Whether the player has enabled vibrate.
	 */
	private boolean vibraEnabled = false;
	
	/**
	 *	Time (in milliseconds since epoc) when the company logo is first
	 *	displayed. It then remains on-screen for either two seconds or
	 *	until the game has loaded, whichever is the longest.
	 */
	private long logoShowTime;
	
	/**
	 *	Time in ticks before changing to the next mode. Used when flipping
	 *	through the results screens.
	 */
	private int displayTimer;
	
	/**
	 *	The 3-2-1-GO countdown to each race.
	 */
	private int countdownDigit = 0;
	
	/**
	 *	Whether a player was accelerating way in advance of the 'go' signal.
	 */
	private boolean[] jumpedGun = new boolean[RaceCore.MAX_KARTS];
	
	/**
	 *	Index of the last kart to cross the finish line.
	 */
	private int lastFinish = 0;
	
	/**
	 *	Passed to the race logic to be filled with statistics (player
	 *	position, lap time, sound effects, etc.)
	 */
	private final int[] racestats = new int[10];
	
	/**
	 *	Driving characteristics for each kart. Used by the both by the racing
	 *	logic and the front-end (in the kart chooser).
	 */
	private final byte[][] kartProps = new byte[RaceCore.TOTAL_KARTS][Kart.TOTAL_PROPS];
	
	private final MultiplayerClient mpClient;
	private final MultiplayerServer mpServer;
	
	/**
	 *	Joystick state for the multiplayer mode.
	 */
	private int[] mpJoyState = new int[RaceCore.MAX_KARTS];
	
	/**
	 *	Controls all music and sound effects.
	 */
	private final SoundPlayer sound;
	
	/**
	 *	Cycles the game through random tracks with random karts, testing the
	 *	phone's ability to handle repeated file openings (Early Symbian phones
	 *	usually fail this) and continuous running of the game in general.
	 *	Handsets crashing during the test usually have heap fragmentation
	 *	problems, which the game is designed to avoid, so these probably stem
	 *	from the phone's own Java runtime.
	 */
	private boolean soakTest = false;
	
	/**
	 *	Counts down each part of the soak test.
	 *
	 *	@see SOAK_TIME
	 */
	private int soakTime = 0;
	
	/**
	 *	Game preferences (renderer, language, etc.).
	 */
	private final byte[] prefs = new byte[PREFS_STORAGE_SIZE];
	
	/**
	 *	Holds all of the game's text.
	 */
	private final StringManager strings;
	
	/**
	 *	Next joystick position in the unlock sequence.
	 */
	private int unlockNext = Joystick.BUTTON_L;
	
	/**
	 *	Whether the advanced options are enabled in the menus.
	 */
	private boolean testOptionsEnabled = false;
	
	private final int[] pointsAwarded = new int[RaceCore.MAX_KARTS];
	
	/**
	 *	10-100% as chars for drawing in the menus.
	 */
	private final char[][] pcentChar = new char[9][];
	
	/**
	 *	Midlet version.
	 */
	private char[] versionChar;
	
	private int subContW;
	private int subContH;
	
	/**
	 *	Used to composite the overall tournament finishing position on the
	 *	results screen. 
	 */
	private final char[] finalPosn = new char[2];
	
	/**
	 *	Localised char used for ordinals.
	 *
	 *	@see #POSITION_ORD
	 */
	private char localPosnOrd = POSITION_ORD;
	
	/**
	 *	Total number of keys to collect to perform each unlock. This value is
	 *	stored with the game's frontend data. Note: setting this to zero
	 *	creates a game where no keys need collecting (and so none of the
	 *	screens show key totals) but the player is required to finish first in
	 *	each group to unlock the game.
	 */
	private int keysToCollect = 45;
	
	/**
	 *	Keys collected in the current level.
	 */
	private int collectedKeys = 0;
	
	/**
	 *	Maximum keys collected per difficulty level per tournament level.
	 */
	private final int[][] totalKeys = new int[DIFFICULTY_LEVELS][TOTAL_TOURNAMENTS];
	
	/**
	 *	Highest finishing position per track for each difficulty level. The
	 *	values recorded are one greater (see note) than the actual position,
	 *	with a zero denoting the player hasn't yet raced on this track.
	 *
	 *	NOTE: this is the only place where zero doesn't mean that player
	 *	finished in first place.
	 */
	private final int[][] finishPos = new int[DIFFICULTY_LEVELS][TOTAL_TRACKS];
	
	/**
	 *	Best time for the lap. Displayed on the track chooser screen.
	 */
	private Line bestLine = null;
	
	/**
	 *	The word 'best'. Displayed on the track chooser screen.
	 */
	private Line bestText = null;
	
	private final char[] bestLineChar = new char[12];
	
	/**
	 *	Scratch int used to temporarily hold a kart or player index.
	 */
	private int tempIdx;
	
	/**
	 *	The recorded best lap for each track.
	 */
	private final int[] best = new int[TOTAL_TRACKS];
	
	private int lastLaps = 0;
	
	/**
	 *	Last error thrown. Anything major will stop the game and display the
	 *	error screen.
	 */
	private Throwable lastError = null;
	
	/**
	 *	Number of errors occuring during sending or receiving multiplayer
	 *	data. The counter is reset at the start of a session, and on reaching
	 *	a set limit the connection is closed.
	 *
	 *	@see #MULTIPLAYER_MAX_ERRORS
	 */
	private int mpErrorCount = 0;
	
	/**
	 *	Number of connected multiplayer clients.
	 */
	private int mpClientSize = 0;
	
	/**
	 *	The player index of this phone as a multiplayer client. Used by all
	 *	parts of the code where this player's stats are displayed, not just
	 *	for multiplay.
	 */
	private int mpPlayerIdx = 0;
	
	private boolean mpClientChosen = false;
	
	private final char[] mpDiscoveredChar = new char[6];
	
	private Line mpDiscoveredLine;
	
	/**
	 *	Multiplayer state is ready.
	 */
	private boolean mpReady;
	
	/**
	 *	Used to display the total number of keys collected in a tournament.
	 */
	private final char[] tourneyKeysChar = new char[4];
	
	/**
	 *	Used to display the total points awarded in a tournament.
	 */
	private final char[] tourneyPointsChar = new char[5];
	
	/**
	 *	Finish position of each stage in a tournament.
	 */
	private final int[] tourneyFinish = new int[TRACKS_PER_LEVEL];
	
	/**
	 *	Used to display the tournament finishing positions.
	 */
	private final char[] tourneyFinishChar = new char[TRACKS_PER_LEVEL * 4 - 1];
	
	private Line resultsKeyLine;
	private Line resultsPosLine;
	
	/**
	 *	Char array containing just a single newline. Used to avoid repeatedly
	 *	passing a string with a newline, which then calls toCharArray(), to
	 *	the bitmap text routines.
	 */
	private final char[] newlineChar;
	
	private int viewOffsetX = 0;
	private int viewOffsetY = 0;
	private int viewBorderX, viewBorderW, viewBorderY, viewBorderH;
	
	/**
	 *	Number of StringManager locales.
	 */
	private final int numLocales;
	
	/**
	 *	String for the (optional) distributer line.
	 */
	private char[] distribChar;
	
	/*
	 *	Loading text as the game starts up.
	 */
	private final String[] loadingText;
	private final int loadingTextH;
	private final int loadingX;
	private final int loadingY;
	
	/**
	 *	Whether this device has Bluetooth/JSR-82 support.
	 */
	private final boolean hasBluetooth;
	
	/**
	 *	Whether this device has M3G/JSR-184 support.
	 */
	private final boolean has3D;
	
	/**
	 *	Do the cars on the menu screen run diagonally, like in Opposite Lock,
	 *	or straight, like Jet Set Racing.
	 */
	private boolean menuCarsDiag = false;
	
	/**
	 *	Are the cars on top of the background layers, like JSR, or sandwiched
	 *	between them, like OL.
	 */
	private boolean menuCarsHigh = true;
	
	/**
	 *	Whether the text label stars animated or not.
	 */
	private boolean titlehasAnim = true;
	
	/**
	 *	Char used as the LHS rotating star (or other graphic) in text labels.
	 */
	private char titleLHS = ROTATING_STAR;
	
	/**
	 *	Char used as the RHS rotating star (or other graphic) in text labels.
	 */
	private char titleRHS = ROTATING_STAR;
	
	/**
	 *	Does each track have its own tune, like JSR, or do they all use the
	 *	same one, like OL.
	 */
	private boolean trackTune = true;
	
	private final Vector2D pressedCoord;
	private final Vector2D draggedCoord;
	
	/**
	 *	Whether the game is being run for the first time (in which case the
	 *	language selection screen is shown before playing).
	 */
	private boolean firstRun = false;
	
	/**
	 *	Lock object to ensure the frontend or other assets aren't loaded by
	 *	multiple threads (this can happen if the game thread is cancelled
	 *	during the a lengthy load and another one started).
	 */
	private Object loadingLock = new Object();
	
	/**
	 *	Flag to say that the frontend graphics still need loading.
	 *
	 *	@see #loadingLock
	 */
	private boolean needFrontend = true;
	
	/**
	 *	Resource stream holding the frontend graphics.
	 */
	private DataInputStream feStream;
	
	/**
	 *	Flag to say that the track still needs initialising/loading.
	 *
	 *	@see #loadingLock
	 */
	private boolean needTrack = true;
	
	/**
	 *	Resource stream holding the karts, sprites and background data.
	 */
	private DataInputStream ksbStream;
	
	/**
	 *	Whether it's possible to resume the current race. Any race except
	 *	multiplayer is considered valid as long as the player hasn't finished.
	 */
	private boolean validResume = false;
	
	/**
	 *	Used when updating the screen to ignore a single redraw,
	 */
	private boolean skipPaint = false;
	
	/**
	 *	Whether to rotate the player input to work on the P910's thumbwheel.
	 */
	private boolean useThumbwheel = false;
	
	/**
	 *	Enable the multiplayer/debug mode.
	 */
	private static boolean multiScreen = false;
	
	public GameCanvas(int viewW, int viewH, MIDlet parent) throws IOException {
		super(parent, !DELAY_FULLSCREEN_SETTING);
		
		this.parent = parent;
		
		getPrefs("prefs", prefs);
		
		strings = new StringManager("/st.dat");
		numLocales = strings.getLocales();
		if (prefs[PREFS_CHECKSUM] != calculateCRC8(prefs)) {
			prefs[PREFS_LANGUAGE] = (byte) strings.use(getLocale());
			firstRun = true;
		} else {
			strings.use(prefs[PREFS_LANGUAGE]);
		}
		localiseAbout();
		
		setRenderDefaults(false);
		
		forcedW = viewW >= 96;
		forcedH = viewH >= 54;
		if (forcedW) {
			this.viewW = viewW;
		}
		if (forcedH) {
			this.viewH = viewH;
		}
		
		loadingText  = strings.getAsStrings(93);
		loadingTextH = Font.getDefaultFont().getHeight();
		loadingX = getWidth()  / 2;
		loadingY = getHeight() / 2 - (loadingText.length * loadingTextH) / 2;
		
		softKeyCodeL = getPropertyAsInt("LSK-Override", DEFAULT_SOFTKEY_CODE_L);
		softKeyCodeLAbs = Fixed.abs(softKeyCodeL);
		softKeyCodeR = getPropertyAsInt("RSK-Override", DEFAULT_SOFTKEY_CODE_R);
		softKeyCodeRAbs = Fixed.abs(softKeyCodeR);
		
		/*
		 *	Testing for Bluetooth this way since the bluetooth.api.version
		 *	system property is usually either not present or only accessible
		 *	using LocalDevice.getProperty.
		 */
		Class classExists = null;
		try {
			classExists = Class.forName("javax.bluetooth.LocalDevice");
		} catch (Throwable e) {}
		hasBluetooth = classExists != null;
		
		/*
		 *	Testing for JSR-184 is done the same way.
		 */
		classExists = null;
		try {
			classExists = Class.forName("javax.microedition.m3g.Graphics3D");
		} catch (Throwable e) {}
		has3D = classExists != null;
		
		if (ENABLE_MULTIPLAYER && hasBluetooth) {
			clientState = new byte[MULTIPLAYER_CLIENT_STATE_SIZE];
			serverState = new byte[MULTIPLAYER_SERVER_STATE_SIZE];
			for (int n = 0; n < RaceCore.MAX_KARTS - 1; n++) {
				clientStateBuffer[n] = (n > 0) ? new byte[MULTIPLAYER_CLIENT_STATE_SIZE] : clientState;
				serverStateBuffer[n] = serverState;
			}
			mpClient = new MultiplayerClient(clientState,       serverState);
			mpServer = new MultiplayerServer(serverStateBuffer, clientStateBuffer);
		} else {
			clientState = null;
			serverState = null;
			mpClient = null;
			mpServer = null;
		}
		
		/*
		 *	And so is testing for MMAPI.
		 */
		classExists = null;
		if (ENABLE_MUSIC || ENABLE_SNDFX) {
			try {
				classExists = Class.forName("javax.microedition.media.Manager");
			} catch (Throwable e) {}
		}
		if ((ENABLE_MUSIC || ENABLE_SNDFX) && classExists != null) {
			sound = SoundPlayer.getSoundPlayer();
		} else {
			sound = null;
		}
		
		for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
			player[n] = new Player(n);
			sorter[n] = player[n];
		}
		
		byte[] defaultTotalKeys = new byte[DIFFICULTY_LEVELS * TOTAL_TOURNAMENTS];
		for (int n = 0; n < DIFFICULTY_LEVELS * TOTAL_TOURNAMENTS; n += TOTAL_TOURNAMENTS) {
			/*
			 *	The default collected number of keys is chosen so that the
			 *	player has to finish overall first in the initial tournaments
			 *	to create a total of zero (in order to unlock the final track
			 *	even if the tracks have no keys to collect at all).
			 */
			defaultTotalKeys[n + 0] = -1;
			defaultTotalKeys[n + 1] = -1;
			defaultTotalKeys[n + 2] = -1;
			defaultTotalKeys[n + 3] =  0;
		}
		
		ByteUtils.bytesToBooleans(getPrefs("trials", new byte[TOTAL_TRACKS]), 0, validGhost, TOTAL_TRACKS);
		ByteUtils.bytesToShorts(getPrefs("best", new byte[TOTAL_TRACKS * 2]), 0, best, TOTAL_TRACKS);
		ByteUtils.bytesToMultiBytes(getPrefs("keys", defaultTotalKeys), 0, totalKeys, DIFFICULTY_LEVELS, TOTAL_TOURNAMENTS);
		ByteUtils.bytesToMultiBytes(getPrefs("finish", new byte[DIFFICULTY_LEVELS * TOTAL_TRACKS]), 0, finishPos, DIFFICULTY_LEVELS, TOTAL_TRACKS);
		
		/*
		 *	The race results always follow the same format, so we set any
		 *	characters that aren't subject to change in advance.
		 */
		for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
			for (int i = 0; i < RESULTS_LINE_LENGTH; i++) {
				raceResultLine[n][i] = ' ';
			}
		}
		
		int points = 9;
		for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
			pointsAwarded[n] = points;
			points = (points * 2) / 3;
			if (points < 0) {
				points = 0;
			}
		}
		
		for (int n = 0; n < 9; n++) {
			pcentChar[n] = (((n + 1) * 10) + "%").toCharArray();
		}
		
		newlineChar = "\n".toCharArray();
		
		if (ENABLE_POINTER) {
			pressedCoord = new Vector2D();
			draggedCoord = new Vector2D();
		} else {
			pressedCoord = null;
			draggedCoord = null;
		}
	}
	
	private String getProperty(String key) {
		String prop = null;
		try {
			prop = System.getProperty(key);
		} catch (Exception e) {}
		if (prop == null) {
			prop = parent.getAppProperty(key);
		}
		return prop;
	}
	
	private int getPropertyAsInt(String key, int def) {
		int val = def;
		try {
			val = Integer.parseInt(getProperty(key));
		} catch (Exception e) {}
		return val;
	}
	
	/**
	 *	Retrieves the midlet's locale. The jad file property 'Default-Locale'
	 *	is first checked, followed by midlet properties.
	 */
	private char[] getLocale() {
		String locale = getProperty("Default-Locale");
		if (locale == null) {
			locale = getProperty("microedition.locale");
			if (locale == null) {
				locale = "en_UK";
			}
		}
		if (DEBUG) {
			System.out.println("Locale: " + locale);
		}
		return locale.toLowerCase().toCharArray();
	}
	
	/**
	 *	Localises the generated text in the about menu.
	 */
	private void localiseAbout() {
		String version = getProperty("MIDlet-Version");
		if (version != null) {
			versionChar = new StringBuffer().append(strings.get(82)).append(' ').append(version).toString().toCharArray();
		} else {
			versionChar = null;
		}
		
		String distrib = getProperty("Distributed-By");
		if (distrib != null) {
			distribChar = new StringBuffer().append(strings.get(83)).append(' ').append(distrib).toString().toCharArray();
		} else {
			distribChar =  null;
		}
	}
	
	protected void sizeChanged(int w, int h) {
		if (multiScreen) {
			screenW = viewW;
			screenH = viewH;
		} else {
			screenW = w;
			screenH = h;
		}
		
		if (!forcedW) {
			viewW = w - (w * prefs[PREFS_BORDER] * 10) / 100;
		}
		if (!forcedH) {
			viewH = h - (h * prefs[PREFS_BORDER] * 10) / 100;
		}
		
		if (viewW < 96) {
			viewW = 96;
		}
		if (viewH < 54) {
			viewH = 54;
		}
		
		viewOffsetX = (screenW - viewW) / 2;
		viewOffsetY = (screenH - viewH) / 2;
		
		viewBorderY = viewH + viewOffsetY;
		viewBorderH = screenH - viewH - viewOffsetY;
		viewBorderX = viewW + viewOffsetX;
		viewBorderW = screenW - viewW - viewOffsetX;
		
		halfW = viewW / 2;
		halfH = viewH / 2;
		
		viewHQ1 = viewH   / 4;
		viewHQ3 = viewHQ1 * 3;
		
		/*
		 *	The floor height calculation works pretty well at most screen
		 *	sizes, but is only now used for the Mode-7 renderer.
		 */
		if (viewH < 256) {
			floorH = (viewH - 128) / 5 + 64;
		} else {
			floorH = (viewH *   2) / 5;
		}
		if ((TrackRendererMode7.BKGND_ROWS - 1) * ContinuousTiledLayer.TILE_H + floorH < viewH) {
			floorH = viewH - (TrackRendererMode7.BKGND_ROWS - 1) * ContinuousTiledLayer.TILE_H;
		}
		floorH -= (floorH * prefs[PREFS_CLIP] * 10) / 100;
	}
	
	private byte getByteInString(String str, int idx, byte def) {
		try {
			def = (byte) Integer.parseInt(str.substring(idx, idx + 1), 16);
		} catch (Exception e) {}
		return def;
	}
	
	private void setRenderDefaults(boolean force) {
		force |= (prefs[PREFS_CHECKSUM] != calculateCRC8(prefs));
		if (force) {
			try {
				String renDef = getProperty("Render-Defaults");
				if (renDef == null) {
					renDef  = FALLBACK_RENDER_DEFAULTS;
					if (DEBUG) {
						System.out.println("Render-Defaults entry not found");
					}
				}
				
				prefs[PREFS_DIFFICULTY] = DIFFICULTY_MEDIUM;
				prefs[PREFS_SOUND]      = SETTINGS_OFF;
				prefs[PREFS_VIBRATE]    = SETTINGS_OFF;
				
				prefs[PREFS_RENDERER]   = getByteInString(renDef, 0, (byte) 0);
				prefs[PREFS_BACKGROUND] = getByteInString(renDef, 1, (byte) 0);
				prefs[PREFS_BORDER]     = getByteInString(renDef, 2, (byte) 0);
				prefs[PREFS_CLIP]       = getByteInString(renDef, 3, (byte) 0);
			} catch (Exception e) {}
			if (DEBUG) {
				System.out.println("Setting render defaults");
			}
		}
	}
	
	private void updatePrefs() {
		if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound != null) {
			sound.setEnabled((prefs[PREFS_SOUND] & SOUND_MUSIC) != 0, (prefs[PREFS_SOUND] & SOUND_EFFECTS) != 0);
		}
		if (logic != null) {
			switch (prefs[PREFS_DIFFICULTY]) {
			case DIFFICULTY_EASY:
				logic.setAISkill(4 << Fixed.FIXED_POINT, 8);
				break;
			case DIFFICULTY_MEDIUM:
				logic.setAISkill(2 << Fixed.FIXED_POINT, 6);
				break;
			case DIFFICULTY_HARD:
				logic.setAISkill(1 << Fixed.FIXED_POINT, 4);
				break;
			}
		}
		
		vibraEnabled = hasVibrate && (prefs[PREFS_VIBRATE] == SETTINGS_ON);
	}
	
	/**
	 *	Returns the best <code>BitmapFont</code> given the size choices.
	 *
	 *	@param minW minimum view width for the preferred choice
	 *	@param minH minimum view height for the preferred choice
	 *	@param resource preferred resource name
	 *	@param fallback resource to use if the size limits are not met
	 */
	private BitmapFont findBestFont(int minW, int minH, String resource, String fallback) throws IOException {
		try {
			if (viewW >= minW && viewH >= minH) {
				return new BitmapFont(resource);
			}
		} catch (Exception e) {
			if (DEBUG) {
				System.out.println("Failed loading: " + resource + " (" + e.getMessage() + ")");
			}
			if (fallback != null) {
				return new BitmapFont(fallback);
			}
		}
		return null;
	}
	
	/**
	 *	Loads and initialises the frontend.
	 */
	private void initFrontend() throws IOException {
		container = new PositionableContainer(viewW, viewH, 18);
		
		/*
		 *	Try to init the front-end with the correct sized fonts, but fall
		 *	back to the alternative size if it fails. The numbers are chosen
		 *	so that 128x128 screens use the 's' variants, 132x176 mix the 'm'
		 *	fonts with 's' icons, and 176x208 use the 'm' variants.
		 */
		textFont = findBestFont(132, 164, "/fm.bmf",  "/fs.bmf");
		if (textFont == null) {
			textFont = findBestFont(0, 0, "/fs.bmf",  "/fm.bmf");
		}
		iconFont = findBestFont(164, 164, "/im.bmf", "/is.bmf");
		if (iconFont == null) {
			iconFont = findBestFont(0, 0, "/is.bmf", "/im.bmf");
		}
		gameFont = new BitmapFont("/cm.bmf");
		
		textFontH = textFont.height;
		iconFontH = iconFont.height;
		
		contentH = viewH - textFontH;
		halfContentH = contentH / 2;
		
		for (int n = 0; n < MAX_PASSING_CARS; n++) {
			passingCar[n] = new Line(iconFont, 1);
		}
		for (int n = 0; n < MAX_LINES; n++) {
			iconLine[n] = Line.createLine(iconFont, viewW);
		}
		for (int n = 0; n < MAX_LINES; n++) {
			textLine[n] = Line.createLine(textFont, viewW);
		}
		
		mainMenu = new ListMenu(textFont, 8, viewW, halfH - textFont.ascent, false, 0);
		mainMenu.setEffect(jumpFast, jumpSide, jumpSlow);
		
		tallMenu = new ListMenu(textFont, 16, viewW, viewH - textFontH * 2, false, 0);
		tallMenu.setEffect(jumpFast, jumpSide, jumpSlow);
		
		iconMenu = IconMenu.createIconMenu(iconFont, 4, 3, 'y', viewW - 32, halfH - 8);
		iconMenu.setEffect(new AnimEffect(2, 1, 1), jumpSlow);
		
		textView = new TextView(textFont, 96, viewW - viewW / 7, contentH - 16, false, -1);
		textView.setEffect(jumpSlow);
		
		miniView = new TextView(textFont,  8, viewW - viewW / 7, contentH - 16, false, -1);
		miniView.setEffect(jumpSlow);
		
		solidBlack = new SolidColour(0x000000, viewW, viewH);
		solidWhite = new SolidColour(0xFFFFFF, viewW, viewH);
		
		charTick = new Line(textFont, 1).set(BUTTON_TICK);
		charTick.setPosition(2, viewH - 2, Graphics.BOTTOM | Graphics.LEFT);
		
		charBack = new Line(textFont, 1).set(BUTTON_BACK);
		charBack.setPosition(viewW - 2, viewH - 2, Graphics.BOTTOM | Graphics.RIGHT);
		
		charBackSmall = new Line(textFont, 1).set(BUTTON_BACK_SMALL);
		charBackSmall.setPosition(viewW, viewH + 1, Graphics.BASELINE | Graphics.RIGHT);
		
		charOkay = new Line(textFont, 1).set(BUTTON_OKAY).setTextEffect(blinking);
		charOkay.setPosition(halfW, viewHQ3, Graphics.BASELINE | Graphics.HCENTER);
		
		charHourglass = new Line(iconFont, 1).set(HOURGLASS_SYMBOL).setTextEffect(starAnim);
		charHourglass.setPosition(halfW, viewHQ3, Graphics.BOTTOM | Graphics.HCENTER);
		
		if (feStream != null) {
			try {
				feStream.close();
			} catch (Exception e) {}
		}
		feStream = new DataInputStream(getClass().getResourceAsStream("/fe.dat"));
		
		menuCarsDiag = feStream.readBoolean();
		menuCarsHigh = feStream.readBoolean();
		titlehasAnim = feStream.readBoolean();
		titleLHS = feStream.readChar();
		titleRHS = feStream.readChar();
		int resultOff = feStream.readByte();
		keysToCollect = feStream.readShort();
		for (int n = 0; n < DIFFICULTY_LEVELS; n++) {
			unlockBonuses(n);
		}
		trackTune = feStream.readBoolean();
		
		AnimTile[] anims = AnimTile.load(feStream, false);
		Image image = TrackRenderer.loadImage(feStream);
		
		int layers = feStream.readByte();
		
		switch (prefs[PREFS_BACKGROUND]) {
		case BACKGROUND_DIRECT:
			menuBkgnd = new TiledOverlay(8, 8, viewW, viewH, image, anims, null, TiledOverlay.PEER_EXTEND);
			break;
		case BACKGROUND_SHUFFLED:
			if (ENABLE_BKGND_SHUFFLED) {
				menuBkgnd = new ShuffledTiledOverlay(8, 8, viewW, viewH, image, anims, null);
			}
			break;
		default:
			if (ENABLE_BKGND_BUFFERED) {
				menuBkgnd = new BufferedTiledLayer(8, 8, viewW, viewH, image, anims, false, null, false);
			}
		}
		menuLHS = new TiledOverlay(2, 2, 16, viewH, image, anims, menuBkgnd, TiledOverlay.PEER_EXTEND);
		menuRHS = new TiledOverlay(2, 2, 16, viewH, image, anims, menuBkgnd, TiledOverlay.PEER_EXTEND);
		menuMid = new TiledOverlay(1, 1, viewW, 8,  image, anims, menuBkgnd, TiledOverlay.PEER_EXTEND);
		if (layers == 4) {
			resultBar = menuMid;
		} else {
			resultBar = new TiledOverlay(2, 2, viewW - resultOff * 2, 16,  image, anims, menuBkgnd, TiledOverlay.PEER_EXTEND);
		}
		
		menuBkgnd.load(feStream);
		menuLHS.load(feStream);
		menuRHS.load(feStream);
		menuMid.load(feStream);
		if (layers > 4) {
			resultBar.load(feStream);
		}
		
		feStream.close();
		
		menuLHS.setPosition(0,     0, Graphics.TOP | Graphics.LEFT);
		menuRHS.setPosition(viewW, 0, Graphics.TOP | Graphics.RIGHT);
		menuMid.setPosition(0, textFontH, Graphics.BOTTOM | Graphics.LEFT);
		if (layers > 4) {
			resultBar.setPosition(resultOff, textFontH + textFontH / 2, Graphics.BOTTOM | Graphics.LEFT);
		}
		
		iconCountdown = new Line(gameFont, 1).set((char) (COUNTDOWN_ZERO + COUNDOWN_START));
		iconCountdown.setPosition(halfW, halfH, Graphics.VCENTER | Graphics.HCENTER);
		
		bestText = new Line(gameFont, 1).set(BEST_TEXT);
		
		chrome = new RaceChromeImpl(textFont, gameFont, viewW, viewH);
		
		resultsPosLine = new Line(gameFont, 2);
		resultsPosLine.setTextEffect(jumpFast);
		
		for (int n = 0; n < LAPS_PER_RACE + 1; n++) {
			lapTimeChar[n] = chrome.zeroTimeLine(null, 3, TIMER_ZERO);
			if (n < LAPS_PER_RACE) {
				lapTimeChar[n][0] = STATS_LAPS_ICON;
				lapTimeChar[n][1] = (char) (STATS_NUMBER_ONE + n);
			} else {
				lapTimeChar[n][0] = STATS_TOTAL_ICON;
				lapTimeChar[n][1] = MONO_SPACE;
			}
			lapTimeChar[n][2] = ' ';
		}
		
		bestLineChar[ 0] = RESULTS_SPACER_CUP;
		bestLineChar[ 1] = ' ';
		bestLineChar[10] = ' ';
		bestLineChar[11] = RESULTS_SPACER_CUP;
		chrome.zeroTimeLine(bestLineChar, 2, TIMER_ZERO);
		
		tourneyKeysChar[0] = KEY_SYMBOL;
		tourneyKeysChar[1] = ' ';
		
		tourneyPointsChar[0] = STATS_TOTAL_ICON;
		tourneyPointsChar[1] = MONO_SPACE;
		tourneyPointsChar[2] = ' ';
		
		for (int n = 0; n < TRACKS_PER_LEVEL; n++) {
			tourneyFinishChar[n * 4] = '\u2007'; // 16px 'figure' space
			if (n < TRACKS_PER_LEVEL - 1) {
				tourneyFinishChar[n * 4 + 3] = MONO_SPACE; // 8px
			}
		}
		
		mpDiscoveredChar[0] = PHONE_SYMBOL;
		mpDiscoveredChar[3] = (char) (TIMER_ZERO - 1);
		
		hasVibrate = (getPropertyAsInt("Disable-Vibrate", 0) == 0) && buzz(0);
		
		testOptionsEnabled = getPropertyAsInt("Test-Menu", 0) == 1;
		
		useThumbwheel = getPropertyAsInt("Use-Thumbwheel", 0) == 1;
		
		if (SNDFX_ARE_INDEPENDENT && sound != null) {
			sound.loadEffects("/fx.dat");
		}
	}
	
	/**
	 *	Performs localisation of the HUD and results. This is a new addition
	 *	(from version 1.3.4 on). Checks are performed to ensure the string
	 *	resources have the locale 'text' and that the frontend has been
	 *	initialised, so it's safe to call this at any time.
	 */
	private void localiseOverlays() {
		char[] str = null;
		try {
			str = strings.get(109);
		} catch (ArrayIndexOutOfBoundsException e) {
			return;
		}
		if (needFrontend) {
			return;
		}
		
		chrome.localise(str[LOCALISATION_TIME], str[LOCALISATION_BEST], str[LOCALISATION_ORDINAL]);
		for (int n = 0; n < LAPS_PER_RACE + 1; n++) {
			if (n < LAPS_PER_RACE) {
				lapTimeChar[n][0] = str[LOCALISATION_LAP];
			} else {
				lapTimeChar[n][0] = str[LOCALISATION_TOTAL];
			}
		}
		bestText.set(str[LOCALISATION_BEST]);
		localPosnOrd = str[LOCALISATION_ORDINAL];
		tourneyPointsChar[0] = str[LOCALISATION_TOTAL];
	}
	
	/**
	 *	Loads and initialises the track renderer and racing logic.
	 */
	private void initTrack() throws IOException {
		if (ksbStream != null) {
			try {
				ksbStream.close();
			} catch (Exception e) {}
		}
		ksbStream = new DataInputStream(getClass().getResourceAsStream("/ksb.dat"));
		
		if (ksbStream.readByte() != RaceCore.TOTAL_KARTS || ksbStream.readByte() != Kart.TOTAL_PROPS) {
			if (DEBUG) {
				throw new IOException("Dodgy kart data");
			} else {
				throw new IOException();
			}
		}
		for (int n = 0; n < RaceCore.TOTAL_KARTS; n++) {
			ksbStream.readFully(kartProps[n]);
		}
		
		if (DEBUG) {
			System.out.println("About to create renderer");
		}
		switch (prefs[PREFS_RENDERER]) {
		case RENDERER_TOPDOWN:
			if (ENABLE_RENDERER_TOPDOWN) {
				track = new TrackRendererTopdown(viewW, viewH, prefs[PREFS_BACKGROUND], ksbStream, true);
			}
			break;
		case RENDERER_MODE7_SMOOTH:
		case RENDERER_MODE7_CHUNKY:
			if (ENABLE_RENDERER_MODE7) {
				track = new TrackRendererMode7(viewW, viewH, floorH, prefs[PREFS_RENDERER] == RENDERER_MODE7_CHUNKY, prefs[PREFS_BACKGROUND], ksbStream, true);
			}
			break;
		case RENDERER_M3G_11:
		case RENDERER_M3G_12:
		case RENDERER_M3G_14:
		case RENDERER_M3G_18:
			if (ENABLE_RENDERER_M3G) {
				track = new TrackRendererM3G(viewW, viewH,  prefs[PREFS_RENDERER] - RENDERER_M3G_11, prefs[PREFS_BACKGROUND], ksbStream, true);
			}
			break;
		}
		ksbStream = null;
		
		if (DEBUG) {
			System.out.println("Created track renderer");
		}
		
		logic = new RaceCore(track, chrome, kartProps);
		if (DEBUG) {
			System.out.println("Created racing logic");
		}
	}
	
	private Line getNextTextLine() {
		if (usedTextLines < MAX_LINES) {
			return textLine[usedTextLines++];
		} else {
			return null;
		}
	}
	
	/*private Line getNextTextLine(char c, int x, int y, int anchor) {
		return (Line) (getNextTextLine().set(c).setPosition(x, y, anchor));
	}
	
	private Line getNextTextLine(int n, int x, int y, int anchor) {
		return (Line) (getNextTextLine().set(strings.get(n)).setPosition(x, y, anchor));
	}*/
	
	private Line getNextIconLine() {
		if (usedIconLines < MAX_LINES) {
			return iconLine[usedIconLines++];
		} else {
			return null;
		}
	}
	
	/**
	 *	Clears every element that could be used for the front-end.
	 */
	private void clearFrontend() {
		container.clear();
		subContA.clear();
		subContB.clear();
		for (int n = 0; n < usedTextLines; n++) {
			textLine[n].clear();
			textLine[n].setTextEffect(null);
		}
		usedTextLines = 0;
		for (int n = 0; n < usedIconLines; n++) {
			iconLine[n].clear();
			iconLine[n].setTextEffect(null);
		}
		usedIconLines = 0;
		mainMenu.clear();
		tallMenu.clear();
		iconMenu.clear();
		textView.clear();
		miniView.clear();
		hasPassingCars = false;
	}
	
	/**
	 *	The default animation used on front-end screens.
	 */
	private void animateFrontend() {
		if (menuBkgnd != null) {
			menuBkgnd.moveBy(0, -1);
			menuBkgnd.cycle();
			menuLHS.cycle();
			menuRHS.cycle();
			menuMid.moveBy(-1, 0);
		}
		
		starAnim.cycle();
		bestAnim.cycle();
		
		blinking.cycle();
		
		if (hasPassingCars) {
			for (int n = 0; n < MAX_PASSING_CARS; n++) {
				if (passingCar[n].nudge(-passingCarSpeed[n], 0).getX() < -32) { // 32 is sprite width...
					generatePassingCar(n);
				} else {
					if (menuCarsDiag && passingCar[n].nudge(0, passingCarSpeed[n] / 2).getY() > viewH + 32) {
						generatePassingCar(n);
					}
				}
			}
		}
	}
	
	private void addPassingCars() {
		hasPassingCars = true;
		for (int n = 0; n < MAX_PASSING_CARS; n++) {
			container.add(generatePassingCar(n));
		}
	}
	
	private Line generatePassingCar(int n) {
		Line current = passingCar[n];
		
		current.set((char) (FIRST_KART_ICON + nextPassingCar));
		current.setPosition(viewW + Fixed.rand(32), (viewH / MAX_PASSING_CARS) * n + Fixed.rand(32) + 16, Graphics.LEFT | Graphics.BASELINE);
		if (menuCarsDiag) {
			current.nudge(0, -viewW / MAX_PASSING_CARS);
		}
		
		passingCarSpeed[n] = 4 + Fixed.rand(4);
			
		nextPassingCar++;
		if (nextPassingCar >= (Fixed.rand(4) == 0 ? RaceCore.TOTAL_KARTS : RaceCore.MAX_KARTS)) {
			nextPassingCar  = 0;
		}
		return current;
	}
	
	/**
	 *	Creates the elements required for a decorative screen label.
	 */
	private void createLabel(char[] label, int y) {
		Positionable content = getNextTextLine().set(label).setPosition(halfW, y, Graphics.TOP | Graphics.HCENTER);
		int labelW = content.getW();
		container.add(getNextTextLine().set(titleLHS).setTextEffect(titlehasAnim ? starAnim : null).setPosition(halfW - labelW / 2 - 4, y, Graphics.TOP | Graphics.HCENTER));
		container.add(getNextTextLine().set(titleRHS).setTextEffect(titlehasAnim ? starAnim : null).setPosition(halfW + labelW / 2 + 4, y, Graphics.TOP | Graphics.HCENTER));
		container.add(content);
	}
	
	public void paint(Graphics g) {
		g.setClip(0, 0, screenW, screenH);
		if (container != null) {
			container.paint(g, viewOffsetX, viewOffsetY);
		} else {
			g.setColor(0x000000);
			g.fillRect(viewOffsetX, viewOffsetY, viewW, viewH);
			g.setColor(0xFFFFFF);
			for (int n = 0; n < loadingText.length; n++) {
				g.drawString(loadingText[n], loadingX, loadingY + n * loadingTextH, Graphics.TOP | Graphics.HCENTER);
			}
		}
		
		/*
		 *	Draw the black borders if the screen size is greater than the view
		 *	size (due to either a border setting in the render options or the
		 *	screen size being forced in the jad).
		 */
		if (viewOffsetX != 0 || viewOffsetY != 0) {
			g.setColor(0x000000);
			if (viewOffsetX != 0) {
				g.fillRect(0,           viewOffsetY, viewOffsetX, viewH);
				g.fillRect(viewBorderX, viewOffsetY, viewBorderW, viewH);
			}
			if (viewOffsetY != 0) {
				g.fillRect(0, 0,           screenW, viewOffsetY);
				g.fillRect(0, viewBorderY, screenW, viewBorderH);
			}
		}
	}
	
	/**
	 *	Resets all keys to their unpressed state. Used after menu selection.
	 */
	private void resetKeys() {
		softKeyL = false;
		softKeyR = false;
		joyPlayer.reset();
	}
	
	/**
	 *	Performs the work of keyPressed() and keyReleased(), checking the
	 *	softkeys first (some phones use these as game actions so checking them
	 *	first, even though it's more work to do so, ensures we don't miss
	 *	them), then the standard game keys.
	 */
	private void checkKeys(int keycode, boolean status) {
		if (keycode == softKeyCodeL || keycode == softKeyCodeLAbs) {
			softKeyL = status;
		} else {
			if (keycode == softKeyCodeR || keycode == softKeyCodeRAbs) {
				softKeyR = status;
			} else {
				int action = 0;;
				try {
					action = getGameAction(keycode);
				} catch (IllegalArgumentException e) {}
				
				joyPlayer.press(action, keycode, status);
			}
		}
		if (DEBUG) {
			//System.out.println("Key pressed: " + keycode + ", " + status);
		}
	}
	
	protected void keyPressed(int keycode) {
		checkKeys(keycode, true);
	}
	
	protected void keyReleased(int keycode) {
		checkKeys(keycode, false);
	}
	
	/********************** Pointer/Mouse Integration ***********************/
	
	/**
	 *	Handles the pointer or mouse presses (and drags).
	 */
	protected void pointerPressed(int x, int y) {
		if (ENABLE_POINTER) {
			x -= viewOffsetX;
			y -= viewOffsetY;
			if (x >= 0 && y >= 0 && x < viewW && y < viewH) {
				container.getClicked(x, y, true);
			}
			
			pressedCoord.set(x << Fixed.FIXED_POINT, y << Fixed.FIXED_POINT);
		}
	}
	
	/**
	 *	Pointer/mouse drag events are passed onto the press handler.
	 */
	protected void pointerDragged(int x, int y) {
		if (ENABLE_POINTER) {
			int _x = x - viewOffsetX;
			int _y = y - viewOffsetY;
			Positionable clicked = container.getClicked(_x, _y, false);
			if (clicked instanceof ListMenu || clicked instanceof IconMenu) {
				pointerPressed(x, y);
			} else {
				draggedCoord.set(pressedCoord);
				draggedCoord.sub(_x << Fixed.FIXED_POINT, _y << Fixed.FIXED_POINT);
				
				if ((draggedCoord.magSquared() >> Fixed.FIXED_POINT) > 4) {
					int pointerDir = draggedCoord.dir() / 32;
					if (pointerDir > 0 && pointerDir < 3) {
						joyPlayer.press(useThumbwheel ? UP   : LEFT,  0, true);
						joyPlayer.press(useThumbwheel ? DOWN : RIGHT, 0, false);
					} else {
						if (pointerDir < 0 && pointerDir > -3) {
							joyPlayer.press(useThumbwheel ? UP   : LEFT,  0, false);
							joyPlayer.press(useThumbwheel ? DOWN : RIGHT, 0, true);
						}
					}
					if (pointerDir == 0) {
						joyPlayer.press(useThumbwheel ? LEFT : UP,    0, true);
						joyPlayer.press(useThumbwheel ? DOWN : RIGHT, 0, false);
					} else {
						if (pointerDir <= -3 || pointerDir >= 3) {
							joyPlayer.press(useThumbwheel ? LEFT : UP,    0, false);
							joyPlayer.press(useThumbwheel ? DOWN : RIGHT, 0, true);
						}
					}
				} else {
					joyPlayer.press(LEFT,  0, false);
					joyPlayer.press(RIGHT, 0, false);
					joyPlayer.press(UP,    0, false);
					joyPlayer.press(DOWN,  0, false);
				}
			}
		}
	}
	
	/**
	 *	Handles the pointer or mouse release. If the element under the cursor
	 *	is expecting a confirm action (the fire button) this is simulated.
	 */
	protected void pointerReleased(int x, int y) {
		if (ENABLE_POINTER) {
			x -= viewOffsetX;
			y -= viewOffsetY;
			if (x >= 0 && y >= 0 && x < viewW && y < viewH) {
				Positionable clicked = container.getClicked(x, y, false);
				if (clicked == charTick) {
					if (DEBUG) {
						System.out.println("Clicked LSK");
					}
					softKeyL = true;
				} else {
					if (clicked == charBack || clicked == charBackSmall) {
						if (DEBUG) {
							System.out.println("Clicked RSK");
						}
						softKeyR = true;
					}
				}
				if (clicked == mainMenu || clicked == tallMenu || clicked == iconMenu) {
					softKeyL = true;
				}
			}
			
			joyPlayer.press(LEFT,  0, false);
			joyPlayer.press(RIGHT, 0, false);
			joyPlayer.press(UP,    0, false);
			joyPlayer.press(DOWN,  0, false);
		}
	}
	
	/************************************************************************/
	
	/**
	 *	Creates a default front-end screen consisting of the background
	 *	elements plus optional title, passing cars, and tick and back buttons.
	 *
	 *	@param title screen title (surrounded by decorations)
	 *	@param hasCars add the passing cars
	 *	@param hasTick add the tick button
	 *	@param hasBack add the back button
	 */
	private void createDefaultScreen(char[] title, boolean hasCars, boolean hasTick, boolean hasBack) {
		boolean resetBkgnd = !container.contains(menuBkgnd, false);
		clearFrontend();
		if (menuBkgnd != null) {
			container.add(menuBkgnd);
			if (resetBkgnd) {
				menuBkgnd.reset();
			}
		} else {
			container.add(solidBlack);
		}
		if (title != null) {
			container.add(menuMid);
		}
		if (!menuCarsHigh && hasCars) {
			addPassingCars();
			/*
			 *	Ensures the two side bars are seen on MIDP1 devices.
			 */
			if (ENABLE_RESTORE_CLIP) {
				container.add(dummyCont);
			}
		}
		container.add(menuLHS);
		container.add(menuRHS);
		if (menuCarsHigh && hasCars) {
			addPassingCars();
		}
		if (title != null) {
			createLabel(title, 0);
		}
		if (hasTick) {
			container.add(charTick);
		}
		if (hasBack) {
			container.add(charBack);
		}
	}
	
	private void createDefaultKartChooser() {
		createDefaultScreen(strings.get(15), false, true, true);
		nameLine    = getNextTextLine();
		starLine[0] = getNextTextLine().set(STATS_0);
		starLine[1] = getNextTextLine().set(STATS_1);
		starLine[2] = getNextTextLine().set(STATS_2);
		prevLine    = getNextIconLine().set(FIRST_KART_ICON);
		
		subContW = starLine[0].getW() + prevLine.getW() + iconFont.getW(' ');
		subContH = NUM_STARS * (textFontH - 3) + textFontH;;
		subContA.setSize(subContW, subContH);
	
		subContA.add(nameLine);
		for (int n = 0; n < NUM_STARS; n++) {
			subContA.add(starLine[n].setPosition(0, subContH - n * (textFontH - 3), Graphics.BOTTOM | Graphics.LEFT));
		}
		subContA.add(prevLine.setPosition(subContW, (subContH + textFontH) / 2, Graphics.RIGHT | Graphics.VCENTER));
	
		subContA.setPosition(halfW, contentH / 4 - textFont.ascent, Graphics.VCENTER | Graphics.HCENTER);
		container.add(subContA);
		
		iconMenu.set(strings.get(16));
		switch (unlockedKarts) {
		case RaceCore.MAX_KARTS + 0:
			iconMenu.set( 9, '?', 8);
		case RaceCore.MAX_KARTS + 1:
			iconMenu.set(10, '?', 9);
		}
		iconMenu.setValue( 8, -1).setValue( 9,  8).setValue(10,  9).setValue(11, -1);
		iconMenu.setPosition(halfW, halfH + 4, Graphics.HCENTER | Graphics.TOP);
		iconMenu.reset();
		
		setPlayerStats(selectedKart);
		iconMenu.setSelectedByValue(selectedKart).updateList();
		
		container.add(iconMenu);
	}
	
	/**
	 *	Clears the screen content and prepares for track display.
	 */
	private void createDefaultTrackScreen() {
		clearFrontend();
		container.add(track);
	}
	
	/**
	 *	Creates a default confirmation screen.
	 *
	 *	@param strIdx string manager index
	 */
	private void createDefaultConfirmScreen(int lableIdx, int textIdx, boolean hasTick, boolean hasBack, boolean flashOkay) {
		createDefaultScreen(strings.get(lableIdx), true, hasTick, hasBack);
		textView.add(strings.get(textIdx));
		textView.setPosition(halfW, viewHQ1, Graphics.BASELINE | Graphics.HCENTER);
		textView.reset();
		container.add(textView);
		if (flashOkay) {
			container.add(charOkay);
		}
	}
	
	/**
	 *	Adds the 'tall menu' to the screen in the default position.
	 */
	private void tallMenuDefaults(int selected) {
		tallMenu.setPosition(halfW, halfH + textFontH / 2, Graphics.VCENTER | Graphics.HCENTER);
		tallMenu.reset();
		if (selected >= 0) {
			tallMenu.setSelected(selected);
		}
		container.add(tallMenu);
	}
	
	private void setCountdownChar(char digit, TextEffect effect) {
		iconCountdown.set(digit).setTextEffect(effect).setPosition(halfW, halfH, Graphics.VCENTER | Graphics.HCENTER);
	}
	
	private void addResultsCup(char[] text, int offset, int position) {
		switch (position) {
		case 0:
			text[offset] = RESULTS_GOLD_CUP;
			break;
		case 1:
			text[offset] = RESULTS_SILVER_CUP;
			break;
		case 2:
			text[offset] = RESULTS_BRONZE_CUP;
			break;
		default:
			text[offset] = RESULTS_SPACER_CUP;
		}
	}
	
	/**
	 *	Fills a char array with the race results. Shows the finish position,
	 *	which character, whether a cup was awarded, and the lap time total.
	 */
	private char[] fillRaceResults(char[] text, int position, int character, boolean withCup, int ticks) {
		text[0] = (char) (RESULTS_FIRST_POSITION + position);
		if (position < 3) {
			text[1] = (char) (localPosnOrd + position);
		} else {
			text[1] = (char) (localPosnOrd + 3);
		}
		text[3] = (char) (FIRST_MINI_HEAD_ICON + character);
		addResultsCup(text, 5, withCup ? position : -1);
		
		chrome.fillTimeLine(text, 7, ticks, TIMER_ZERO);
		text[9] = text[12] = (char) (TIMER_ZERO + 10);
		
		return text;
	}
	
	/**
	 *	Fills a char array with the race results. Shows the finish position,
	 *	which character, whether a cup was awarded, the total points, and the
	 *	number of points just awarded.
	 */
	private char[] fillRaceResults(char[] text, int position, int character, boolean withCup, int total, int awarded) {
		text[0] = (char) (RESULTS_FIRST_POSITION + position);
		if (position < 3) {
			text[1] = (char) (localPosnOrd + position);
		} else {
			text[1] = (char) (localPosnOrd + 3);
		}
		text[3] = (char) (FIRST_MINI_HEAD_ICON + character);
		addResultsCup(text, 5, withCup ? position : -1);
		
		text[ 7] = text[ 8] = MONO_SPACE;
		text[ 9] = text[12] = COLON_SPACE;
		
		fillDecimalChars(text, 10, total);
		
		if (awarded > 0) {
			text[13] = PLUS_SIGN;
			text[14] = (char) (TIMER_ZERO + awarded % 10);
		} else {
			text[13] = MONO_SPACE;
			text[14] = MONO_SPACE;
		}
		
		return text;
	}
	
	/**
	 *	Puts a decimal value from 00 to 99 at the specified point in the array.
	 */
	private char[] fillDecimalChars(char[] text, int offset, int value) {
		text[offset + 1] = (char) (TIMER_ZERO + value % 10);
		value /= 10;
		text[offset + 0] = (char) (TIMER_ZERO + value % 10);
		return text;
	}
	
	/**
	 *	Prepares the track screen for a new game.
	 */
	private void prepTrack(boolean hasGhosts, int numKarts, boolean hasPickups, boolean hasPowerups) throws IOException {
		logic.setGhostMode(hasGhosts);
		logic.init(numKarts, player, mpPlayerIdx, LAPS_PER_RACE, hasPickups, hasPowerups);
		
		chrome.reset();
		chrome.init(LAPS_PER_RACE, trackNum, mpPlayerIdx);
		chrome.setBest(best[trackNum]);
		
		prepTrack();
	}
	
	/**
	 *	Prepares the track screen for a resumed game.
	 */
	private void prepTrack() {
		container.add(charBackSmall);
		container.add(chrome);
		
		if (countdown > -FRAMES_PER_SEC) {
			setCountdownChar((char) (COUNTDOWN_ZERO + COUNDOWN_START), null);
			container.add(iconCountdown);
		}
		
		mainMenu.add(strings.get(89), PAUSE_RESUME);
		mainMenu.add(strings.get(92), PAUSE_MAIN_MENU);
		mainMenu.setPosition(halfW, halfH, Graphics.BASELINE | Graphics.HCENTER);
		mainMenu.reset();
	}
	
	/**
	 *	Sets the best time, cup, whether a ghost is available, and flashes the
	 *	word 'best' if one has been set.
	 */
	private void setBestLine(int trkIdx) {
		addResultsCup(bestLineChar, 0, finishPos[prefs[PREFS_DIFFICULTY]][trkIdx] - 1);
		bestLineChar[11] = validGhost[trkIdx] ? GHOST_ICON : RESULTS_SPACER_CUP;
		bestLine.set(chrome.fillTimeLine(bestLineChar, 2, best[trkIdx], TIMER_ZERO));
		bestText.setTextEffect((best[trkIdx] > 0) ? bestAnim :  null);
	}
	
	/**
	 *	Sets the line showing the number of keys collected.
	 */
	private void setResultsKeyLine(int levIdx) {
		int keysCollected = totalKeys[prefs[PREFS_DIFFICULTY]][levIdx];
		if (keysCollected < 0) {
			keysCollected = 0;
		}
		resultsKeyLine.set(fillDecimalChars(tourneyKeysChar, 2, keysCollected));
	}
	
	private void playTrackTune() {
		if (ENABLE_MUSIC && !DISABLE_IN_GAME_MUSIC && sound != null) {
			if (trackTune) {
				sound.play("/" + trackNum + ".mid", true);
			} else {
				sound.play("/0.mid", true);
			}
		}
	}
	
	private void prepMode(int nextMode) {
		try {
			switch (nextMode) {
			case MODE_NONE:
				break;
			case MODE_ERROR:
				if (ENABLE_MULTIPLAYER && hasBluetooth) {
					mpClient.close();
					mpServer.close();
				}
				if (ENABLE_MUSIC && sound != null) {
					sound.stop(true);
				}
				
				createDefaultScreen(strings.get(86), false, true, false);
				if (lastError instanceof OutOfMemoryError && (track == null || logic == null)) {
					textView.add(strings.get(108));
				} else {
					textView.add(strings.get(87));
					if (lastError != null) {
						String errorString = lastError.toString();
						int errorDot = errorString.lastIndexOf('.');
						if (errorDot > 0) {
							errorString = errorString.substring(errorDot + 1);
						}
						textView.add(errorString.toCharArray());
					}
				}
				textView.setPosition(halfW, halfContentH + textFontH, Graphics.VCENTER | Graphics.HCENTER);
				textView.reset();
				container.add(textView);
				break;
			case MODE_STARTUP:
				break;
			case MODE_INITIALISE:
				/*
				 *	Plays a silence to ensure the audio hardware is inited.
				 *	Otherwise some handsets have a long delay or other
				 *	artefactswhen the first game sound is played.
				 */
				if (ENABLE_MUSIC && sound != null) {
					sound.play("/d.mid", false);
				}
				break;
			case MODE_LOADING:
				clearFrontend();
				container.add(solidWhite);
				container.add(getNextIconLine().set(strings.get(0)).setPosition(halfW,  halfH, Graphics.BASELINE | Graphics.HCENTER));
				logoShowTime = System.currentTimeMillis();
				break;
			case MODE_LOGO_PAUSE:
				break;
			case MODE_MAIN_MENU:
				if (ENABLE_MULTIPLAYER && hasBluetooth) {
					mpClient.close();
					mpServer.close();
				}
				firstRun = false;
				
				createDefaultScreen(null, true, true, false);
				container.add(getNextIconLine().set(strings.get(1)).setPosition(halfW,  viewHQ1, Graphics.VCENTER | Graphics.HCENTER));
				mainMenu.add(strings.get(2), MAIN_MENU_PLAY);
				mainMenu.add(strings.get(3), MAIN_MENU_OPTIONS);
				mainMenu.add(strings.get(4), MAIN_MENU_INSTRUCTIONS);
				mainMenu.add(strings.get(5), MAIN_MENU_ABOUT);
				mainMenu.add(strings.get(6), MAIN_MENU_QUIT);
				mainMenu.setPosition(halfW, halfH, Graphics.BASELINE | Graphics.HCENTER);
				mainMenu.reset();
				container.add(mainMenu);
				
				if (ENABLE_MUSIC && sound != null) {
					sound.play("/a.mid", true);
				}
				break;
			case MODE_CHOOSE_GAME:
				createDefaultScreen(strings.get(10), true, true, true);
				mainMenu.add(strings.get(11), GAME_MENU_PRACTICE);
				if (!DEMO_MODE) {
					mainMenu.add(strings.get(12), GAME_MENU_TOURNAMENT);
					mainMenu.add(strings.get(13), GAME_MENU_TIME_TRIAL);
				}
				if (ENABLE_MULTIPLAYER && hasBluetooth) {
					mainMenu.add(strings.get(14), GAME_MENU_MULTIPLAYER);
				}
				mainMenu.setPosition(halfW, halfH, Graphics.BASELINE | Graphics.HCENTER);
				mainMenu.reset();
				container.add(mainMenu);
				
				/*
				 *	This needs setting to the default player index (zero) for
				 *	every race type apart from multiplayer, which can only be
				 *	set once the server decides the order.
				 */
				mpPlayerIdx = PLAYER_IDX;
				
				break;
			case MODE_CHOOSE_KART:
				createDefaultKartChooser();
				break;
			case MODE_CHOOSE_COURSE:
				createDefaultScreen(strings.get(28), true, true, true);
				if (DEMO_MODE) {
					mainMenu.add(strings.get(FIRST_LEVEL_NAME_IDX));
					for (int n = 0; n < TOTAL_TOURNAMENTS - 1; n++) {
						mainMenu.add(strings.get(QUESTION_MARKS_IDX));
					}
				} else {
					for (int n = 0; n < TOTAL_TOURNAMENTS - 1; n++) {
						mainMenu.add(strings.get(FIRST_LEVEL_NAME_IDX + n));
					}
					if (unlockedTrack) {
						mainMenu.add(strings.get(FIRST_LEVEL_NAME_IDX + TOTAL_TOURNAMENTS - 1));
					} else {
						mainMenu.add(strings.get(QUESTION_MARKS_IDX));
					}
				}
				mainMenu.setPosition(halfW, halfH, Graphics.BASELINE | Graphics.HCENTER);
				mainMenu.reset();
				container.add(mainMenu);
				
				levelNum = 0;
				trackNum = 0;
				
				if (keysToCollect > 0) {
					resultsKeyLine = getNextTextLine();
					setResultsKeyLine(levelNum);
					resultsKeyLine.setPosition(viewW - charBack.getW() - 6, viewH - 2, Graphics.BOTTOM | Graphics.RIGHT);
					container.add(resultsKeyLine);
				}
				
				break;
			case MODE_CHOOSE_TRACK:
				createDefaultScreen(strings.get(33), true, true, true);
				
				nameLine = getNextTextLine().set(strings.get(FIRST_LEVEL_NAME_IDX + levelNum));
				prevLine = getNextIconLine().set(strings.get(FIRST_LEVEL_PREV_IDX + levelNum));
				for (int n = 0; n < TRACKS_PER_LEVEL; n++) {
					mainMenu.add(strings.get(FIRST_TRACK_NAME_IDX + n + levelNum * TRACKS_PER_LEVEL));
				}
				
				subContW = prevLine.getW();
				subContH = prevLine.getH() + textFontH;
				int subContY = contentH / 4 - textFont.ascent;
				
				subContA.setSize(subContW, subContH);
				subContA.add(nameLine.setPosition(subContW / 2, 0, Graphics.TOP | Graphics.HCENTER));
				subContA.add(prevLine.setPosition(subContW / 2, subContH, Graphics.BOTTOM | Graphics.HCENTER));
				
				subContA.setPosition(halfW, subContY, Graphics.VCENTER | Graphics.HCENTER);
				container.add(subContA);
				
				bestLine = getNextTextLine();
				setBestLine(trackNum);
				
				subContB.setSize(viewW, textFontH * 2);
				subContB.add(bestText.setPosition(halfW, textFontH,     Graphics.HCENTER | Graphics.BOTTOM));
				subContB.add(bestLine.setPosition(halfW, textFontH * 2, Graphics.HCENTER | Graphics.BOTTOM));
				
				subContB.setPosition(halfW, halfContentH + textFontH, Graphics.VCENTER | Graphics.HCENTER);
				
				container.add(subContB);
				
				mainMenu.setPosition(halfW, viewHQ3, Graphics.BASELINE | Graphics.HCENTER);
				mainMenu.reset();
				
				container.add(mainMenu);
				
				break;
			case MODE_OPTIONS:
				createDefaultScreen(strings.get(3), true, true, true);
				if (DIFFICULTY_LEVELS > 1) {
					tallMenu.add(strings.get(47), OPTIONS_DIFFICULTY);
				}
				if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound != null) {
					tallMenu.add(strings.get(48), OPTIONS_SOUND);
				}
				if (hasVibrate) {
					tallMenu.add(strings.get(49), OPTIONS_VIBRATE);
				}
				if (numLocales > 1) {
					tallMenu.add(strings.get(50), OPTIONS_LANGUAGE);
				}
				tallMenu.add(strings.get(51), OPTIONS_ERASE_RECORDS);
				if (testOptionsEnabled || ENABLE_ADVANCED_MENU) {
					tallMenu.add(strings.get(52), OPTIONS_ADVANCED);
				}
				if (testOptionsEnabled) {
					tallMenu.add(strings.get(53), OPTIONS_SOAK);
				}
				tallMenuDefaults(-1);
				break;
			case MODE_INSTRUCTIONS:
				createDefaultScreen(strings.get(4), false, true, false);
				textView.add(strings.get(78));
				textView.setPosition(halfW, halfH + textFontH / 2, Graphics.VCENTER | Graphics.HCENTER);
				textView.reset();
				container.add(textView);
				break;
			case MODE_ABOUT:
				createDefaultScreen(strings.get(5), false, true, false);
				textView.add(strings.get(80));
				textView.add(strings.get(81));
				if (versionChar != null) {
					textView.add(versionChar);
				}
				if (distribChar != null) {
					textView.add(newlineChar);
					textView.add(distribChar);
				}
				textView.add(newlineChar);
				textView.add(strings.get(84));
				
				textView.add(newlineChar);
				System.gc();
				textView.add(new StringBuffer(32).append(Runtime.getRuntime().freeMemory() / 1024).append(strings.get(85)).toString().toCharArray());
				
				textView.setPosition(halfW, halfH + textFontH / 2, Graphics.VCENTER | Graphics.HCENTER);
				textView.reset();
				container.add(textView);
				break;
			case MODE_QUIT:
				createDefaultConfirmScreen(6, 60, true, true, true);
				break;
			case MODE_OPTIONS_DIFFICULTY:
				createDefaultScreen(strings.get(47), true, true, true);
				for (int n = 0; n < DIFFICULTY_LEVELS; n++) {
					tallMenu.add(strings.get(FIRST_DIFFICULTY_IDX + n), DIFFICULTY_EASY + n);
				}
				tallMenuDefaults(prefs[PREFS_DIFFICULTY]);
				break;
			case MODE_OPTIONS_SOUND:
				createDefaultScreen(strings.get(48), true, true, true);
				tallMenu.add(strings.get(57), SETTINGS_OFF);
				if (SNDFX_ARE_INDEPENDENT && ENABLE_MUSIC && ENABLE_SNDFX) {
					tallMenu.add(strings.get(61), SOUND_MUSIC);
					tallMenu.add(strings.get(62), SOUND_EFFECTS);
				}
				tallMenu.add(strings.get(58), SOUND_ON);
				tallMenuDefaults(prefs[PREFS_SOUND]);
				break;
			case MODE_OPTIONS_VIBRATE:
				createDefaultScreen(strings.get(49), true, true, true);
				tallMenu.add(strings.get(57), SETTINGS_OFF);
				tallMenu.add(strings.get(58), SETTINGS_ON);
				tallMenuDefaults(prefs[PREFS_VIBRATE]);
				break;
			case MODE_OPTIONS_LANGUAGE:
				createDefaultScreen(strings.get(50), true, true, true);
				if (firstRun) {
					container.remove(charBack);
				}
				for (int n = 0; n < numLocales; n++) {
					tallMenu.add(strings.getLocaleName(n));
				}
				tallMenuDefaults(prefs[PREFS_LANGUAGE]);
				break;
			case MODE_OPTIONS_ERASE_RECORDS:
				createDefaultConfirmScreen(51, 60, true, true, true);
				break;
			case MODE_OPTIONS_ADVANCED:
				createDefaultScreen(strings.get(52), true, true, true);
				tallMenu.add(strings.get(63), ADVANCED_RENDERER);
				tallMenu.add(strings.get(64), ADVANCED_BACKGROUND);
				tallMenu.add(strings.get(65), ADVANCED_BORDER);
				if (ENABLE_RENDERER_MODE7) {
					tallMenu.add(strings.get(66), ADVANCED_CLIP);
				}
				tallMenu.add(strings.get(67), ADVANCED_DEFAULTS);
				tallMenuDefaults(-1);
				break;
			case MODE_ADVANCED_RENDERER:
				createDefaultScreen(strings.get(63), true, true, true);
				if (ENABLE_RENDERER_TOPDOWN) {
					tallMenu.add(strings.get(68), RENDERER_TOPDOWN);
				}
				if (ENABLE_RENDERER_MODE7) {
					tallMenu.add(strings.get(69), RENDERER_MODE7_SMOOTH);
					tallMenu.add(strings.get(70), RENDERER_MODE7_CHUNKY);
				}
				if (ENABLE_RENDERER_M3G && has3D) {
					/*
					 *	No point adding these menu options if the phone
					 *	doesn't have JSR-184 support.
					 */
					tallMenu.add(strings.get(71 ), RENDERER_M3G_11);
					tallMenu.add(strings.get(72 ), RENDERER_M3G_12);
					tallMenu.add(strings.get(106), RENDERER_M3G_14);
					tallMenu.add(strings.get(107), RENDERER_M3G_18);
				}
				tallMenuDefaults(prefs[PREFS_RENDERER]);
				break;
			case MODE_ADVANCED_BACKGROUND:
				createDefaultScreen(strings.get(64), true, true, true);
				if (ENABLE_BKGND_BUFFERED) {
					tallMenu.add(strings.get(73), BACKGROUND_BUFFERED);
				}
				if (ENABLE_BKGND_COMPOSITE) {
					tallMenu.add(strings.get(74), BACKGROUND_COMPOSITE);
				}
				tallMenu.add(strings.get(75), BACKGROUND_DIRECT);
				if (ENABLE_BKGND_SHUFFLED) {
					tallMenu.add(strings.get(76), BACKGROUND_SHUFFLED);
				}
				tallMenuDefaults(prefs[PREFS_BACKGROUND]);
				break;
			case MODE_ADVANCED_BORDER:
				createDefaultScreen(strings.get(65), true, true, true);
				tallMenu.add(strings.get(57));
				for (int n = 0; n < 9; n++) {
					tallMenu.add(pcentChar[n]);
				}
				tallMenuDefaults(prefs[PREFS_BORDER]);
				break;
			case MODE_ADVANCED_CLIP:
				createDefaultScreen(strings.get(66), true, true, true);
				tallMenu.add(strings.get(57));
				for (int n = 0; n < 9; n++) {
					tallMenu.add(pcentChar[n]);
				}
				tallMenuDefaults(prefs[PREFS_CLIP]);
				break;
			case MODE_ADVANCED_DEFAULTS:
				createDefaultConfirmScreen(67, 77, true, true, true);
				break;
			case MODE_ADVANCED_MESSAGE:
				createDefaultConfirmScreen(52, 59, true, false, false);
				break;
			case MODE_PRACTICE:
				createDefaultTrackScreen();
				defaultResetRace();
				
				player[PLAYER_IDX].reset(selectedKart, true, RaceCore.MAX_KARTS - 1);
				Player.reset(player, 1, unlockedKarts, RaceCore.MAX_KARTS);
				
				prepTrack(false, RaceCore.MAX_KARTS, true, true);
				
				break;
			case MODE_TOURNAMENT:
				createDefaultTrackScreen();
				defaultResetRace();
				
				if (trackNum % TRACKS_PER_LEVEL == 0) {
					player[PLAYER_IDX].reset(selectedKart, true, RaceCore.MAX_KARTS - 1);
					Player.reset(player, 1, unlockedKarts, RaceCore.MAX_KARTS);
					collectedKeys = 0;
				} else {
					for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
						sorter[n].gridPos = n;
					}
					if (DEBUG) {
						for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
							System.out.println("Player: " + n + " = grid " + sorter[n].gridPos);
						}
					}
				}
				
				prepTrack(false, RaceCore.MAX_KARTS, true, true);
				
				break;
			case MODE_TIME_TRIAL:
				createDefaultTrackScreen();
				defaultResetRace();
				
				player[PLAYER_IDX].reset(selectedKart, true, -1);
				
				if (validGhost[trackNum]) {
					getPrefs("trial-" + trackNum, stateBuffer);
					/*
					 *	If for some reason filling the buffer with the TT data
					 *	fails (more than likely due to changing the data
					 *	structure between versions than corruption) we check
					 *	the recorded number of laps before using the ghost.
					 */
					if (stateBuffer[1] > 0) {
						player[RECORD_IDX].reset(stateBuffer[0], true, -1);
						joyRecord.load(stateBuffer, 8);
					} else {
						validGhost[trackNum] = false;
					}
				}
				
				prepTrack(validGhost[trackNum], validGhost[trackNum] ? 2 : 1, false, false);
				
				joyPlayer.rewind();
				joyRecord.rewind();
				
				break;
			case MODE_PAUSE:
				mainMenu.reset();
				container.add(mainMenu);
				
				if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound != null) {
					/*
					 *	Phones supporting a proper suspend (e.g. with an Aplix
					 *	VM) shouldn't have to release the audio resources.
					 */
					if (USE_MIDLET_SUSPEND) {
						sound.stop(false);
					} else {
						sound.stop(true);
					}
				}
				
				break;
			case MODE_TRACK_NAME:
				clearFrontend();
				container.add(solidBlack);
				container.add(getNextTextLine().set(strings.get(93)).setPosition(halfW, halfH, Graphics.BASELINE | Graphics.HCENTER));
				container.add(charHourglass);
				
				if (ENABLE_MULTIPLAYER && hasBluetooth) {
					/*
					 *	The best place to init the players is here, before the
					 *	tracks are loaded on both server and clients. Doing it
					 *	here ensures it only happens once.
					 */
					if (raceMode == MODE_MULTIPLAYER_SERVER) {
						for (int n = 0; n <= mpClientSize; n++) {
							player[n].reset(player[n].kartIdx, true, (RaceCore.MAX_KARTS - 1) - n);
						}
						Player.reset(player, mpClientSize + 1, unlockedKarts, RaceCore.MAX_KARTS);
					}
				}
				
				break;
			case MODE_TRACK_LOAD:
				synchronized (loadingLock) {
					if (needTrack) {
						if (DEBUG) {
							try {
								logic.load("/" + trackNum + ".trk");
							} catch (NullPointerException e) {
								/*
								 * Note: this behaviour is only here for test
								 * purposes, in case a full set of tracks
								 * isn't available. If loading fails the first
								 * track is tried.
								 */
								logic.load("/0.trk");
								System.out.println("**** Track missing: " + trackNum + " ****");
							}
						} else {
							logic.load("/" + trackNum + ".trk");
						}
						needTrack = false;
					}
					playTrackTune();
					
					System.gc();
					
					clearFrontend();
					container.add(solidBlack);
					container.add(getNextTextLine().set(strings.get(FIRST_TRACK_NAME_IDX + trackNum)).setPosition(halfW, halfH, Graphics.BASELINE | Graphics.HCENTER));
					container.add(charTick);
				}
				break;
			case MODE_STATS_LAPS:
				container.remove(charBackSmall);
				container.remove(chrome);
				container.add(resultBar);
				createLabel(strings.get(94), 0);
				
				
				miniView.clear();
				miniView.setPosition(halfW, halfContentH + textFontH * 2, Graphics.VCENTER | Graphics.HCENTER);
				container.add(miniView);
				
				int last = 0;
				for (int n = 0; n < LAPS_PER_RACE; n++) {
					int time = logic.getLapTime(mpPlayerIdx, n);
					miniView.add(chrome.fillTimeLine(lapTimeChar[n], 3, time - last, TIMER_ZERO));
					last = time;
				}
				miniView.add(newlineChar);
				miniView.add(chrome.fillTimeLine(lapTimeChar[LAPS_PER_RACE], 3, last, TIMER_ZERO));
				miniView.reset();
				
				container.add(charTick);
				
				break;
			case MODE_STATS_POSN:
				container.remove(charBackSmall);
				container.remove(chrome);
				container.add(resultBar);
				createLabel(strings.get(94), 0);
				
				/*
				 *	In multiplayer mode the server still needs to run the
				 *	race, so it can't quit until all the players are done.
				 */
				if (ENABLE_MULTIPLAYER && (raceMode == MODE_MULTIPLAYER_SERVER || raceMode == MODE_MULTIPLAYER_CLIENT)) {
					container.remove(charTick); // might be present from previous screens
					container.add(charBack);
				} else {
					container.add(charTick);
				}
				
				miniView.clear();
				
				miniView.setPosition(halfW, halfContentH + textFontH, Graphics.VCENTER | Graphics.HCENTER);
				miniView.reset();
				container.add(miniView);
				
				/*
				 *	Determin if this was the highest placing by the player.
				 */
				tempIdx = logic.getStats(RaceCore.STATS_POSITION, mpPlayerIdx);
				int storedFinish = finishPos[prefs[PREFS_DIFFICULTY]][trackNum];
				if (storedFinish == 0 || storedFinish > tempIdx) {
					finishPos[prefs[PREFS_DIFFICULTY]][trackNum] = tempIdx + 1;
				}
				
				if (ENABLE_MUSIC && sound != null) {
					if (tempIdx < 4) {
						sound.play("/b.mid", false);
					} else {
						sound.play("/c.mid", false);
					}
				}
				
				displayTimer = DEFAULT_SCREEN_TIME;
				break;
			case MODE_STATS_POINTS:
				displayTimer = DEFAULT_SCREEN_TIME;
				break;
			case MODE_STATS_TOTALS:
				for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
					player[logic.getKartIndex(n)].addPoints(pointsAwarded[n]);
				}
				QuickSort.sort(sorter);
				
				miniView.clear();
				
				collectedKeys += logic.getStats(RaceCore.STATS_PICKUPS, mpPlayerIdx);
				
				if (DEBUG) {
					System.out.println("Keys: " + collectedKeys);
				}
				
				displayTimer = DEFAULT_SCREEN_TIME;
				break;
			case MODE_STATS_RESULTS:
				clearFrontend();
				container.add(solidBlack);
				container.add(charTick);
				
				int pos = 0;
				for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
					if (sorter[n].index == mpPlayerIdx) {
						pos = n;
						break;
					}
				}
				
				container.add(getNextIconLine().set(strings.get(1)).setPosition(halfW,  viewHQ1, Graphics.VCENTER | Graphics.HCENTER));
				createLabel(strings.get(94), viewHQ1 + textFontH - 2);
				
				finalPosn[0] = (char) (POSITION_ONE + pos);
				if (pos < 3) {
					finalPosn[1] = (char) (localPosnOrd + pos);
				} else {
					finalPosn[1] = (char) (localPosnOrd + 3);
				}
				
				int textPos = halfH + textFontH + 1;
				
				fillDecimalChars(tourneyPointsChar, 3, sorter[pos].points);
				Line pointsLine = getNextTextLine().set(tourneyPointsChar);
				pointsLine.setPosition(halfW + 2, textPos - 2, Graphics.BOTTOM | Graphics.LEFT);
				container.add(pointsLine);
				
				if (keysToCollect > 0) {
					resultsKeyLine = getNextTextLine();
					resultsKeyLine.set(fillDecimalChars(tourneyKeysChar, 2, collectedKeys)).setPosition(halfW + 2, textPos - 2, Graphics.TOP | Graphics.LEFT);
					container.add(resultsKeyLine);
				}
				
				resultsPosLine.set((char) (POSITION_ONE - 1)); // using 'go' ensures the number is positioned properly
				resultsPosLine.setPosition(halfW - pointsLine.getW(), textPos, Graphics.VCENTER | Graphics.HCENTER);
				resultsPosLine.set(finalPosn);
				container.add(resultsPosLine);
				
				/*
				 *	Adds the each race result on top of an icon of that track
				 *	(the track 'preview') if there is more than one track in
				 *	this level. Different sized spaces are used to match the
				 *	numbers up with the preview (these are already present in
				 *	tourneyFinishChar).
				 */
				if (levelNum < (TOTAL_TOURNAMENTS - 1)) {
					container.add(getNextIconLine().set(strings.get(FIRST_LEVEL_PREV_IDX + levelNum)).setPosition(halfW, viewHQ3, Graphics.TOP | Graphics.HCENTER));
					for (int n = 0; n < TRACKS_PER_LEVEL; n++) {
						int position = tourneyFinish[n];
						tourneyFinishChar[n * 4 + 1] = (char) (RESULTS_FIRST_POSITION + position);
						if (position < 3) {
							tourneyFinishChar[n * 4 + 2] = (char) (localPosnOrd + position);
						} else {
							tourneyFinishChar[n * 4 + 2] = (char) (localPosnOrd + 3);
						}
					}
					container.add(getNextTextLine().set(tourneyFinishChar).setPosition(halfW + 8, viewHQ3 + iconFontH, Graphics.BASELINE | Graphics.HCENTER));
				}
				
				/*
				 *	Only update the collected keys if the player finished
				 *	overall first, which allows us to create a game where no
				 *	keys require colllecing (see keysToCollect).
				 */
				if (pos == 0) {
					if (totalKeys[prefs[PREFS_DIFFICULTY]][levelNum] < collectedKeys) {
						totalKeys[prefs[PREFS_DIFFICULTY]][levelNum] = collectedKeys;
					}
					unlockBonuses(prefs[PREFS_DIFFICULTY]);
				}
				
				displayTimer = DEFAULT_SCREEN_TIME;
				
				break;
			case MODE_MULTIPLAYER:
				if (ENABLE_MULTIPLAYER) {
					mpClient.close();
					mpServer.close();
					mpErrorCount = 0;
					
					mpPlayerIdx = PLAYER_IDX;
					
					clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_IDLE;
					serverState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_IDLE;
					for (int n = 0; n < RaceCore.MAX_KARTS - 1; n++) {
						clientStateBuffer[n][MULTIPLAYER_MODE] = MULTIPLAYER_MODE_IDLE;
						clientStateBuffer[n][MULTIPLAYER_PIDX] = -1;
					}
					
					Player.reset(player, 0);
					for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
						mpJoyState[n] = -1;
					}
					
					createDefaultScreen(strings.get(14), true, true, true);
					mainMenu.add(strings.get(95), MULTIPLAYER_CLIENT);
					mainMenu.add(strings.get(96), MULTIPLAYER_SERVER);
					mainMenu.add(strings.get(97), MULTIPLAYER_HELP);
					mainMenu.setPosition(halfW, halfH, Graphics.BASELINE | Graphics.HCENTER);
					mainMenu.reset();
					container.add(mainMenu);
				}
				break;
			case MODE_MULTIPLAYER_CLIENT:
				if (ENABLE_MULTIPLAYER) {
					createDefaultTrackScreen();
					defaultResetRace();
					
					prepTrack(false, RaceCore.MAX_KARTS, false, true);
				}
				break;
			case MODE_MULTIPLAYER_SERVER:
				if (ENABLE_MULTIPLAYER) {
					createDefaultTrackScreen();
					defaultResetRace();
					
					prepTrack(false, RaceCore.MAX_KARTS, false, true);
				}
				break;
			case MODE_MULTIPLAYER_CLIENT_DISCOVERY:
				if (ENABLE_MULTIPLAYER) {
					createDefaultConfirmScreen(95, 98, false, true, false);
					container.add(charHourglass);
					
					displayTimer = DEFAULT_SCREEN_TIME;
					
					if (!mpClient.start()) {
						prepMode(MODE_MULTIPLAYER_ERROR);
						return;
					}
				}
				break;
			case MODE_MULTIPLAYER_SERVER_DISCOVERY:
				if (ENABLE_MULTIPLAYER) {
					createDefaultConfirmScreen(96, 99, false, true, false);
					container.add(charHourglass);
					
					fillDecimalChars(mpDiscoveredChar, 1, 0);
					fillDecimalChars(mpDiscoveredChar, 4, mpServer.getMaxConnections());
					mpDiscoveredLine = getNextTextLine().set(mpDiscoveredChar);
					container.add(mpDiscoveredLine.setPosition(16 + 2, viewH - 2, Graphics.BOTTOM | Graphics.LEFT));
			
					displayTimer = DEFAULT_SCREEN_TIME;
					
					if (!mpServer.start()) {
						prepMode(MODE_MULTIPLAYER_ERROR);
						return;
					}
				}
				break;
			case MODE_MULTIPLAYER_CLIENT_CHOOSE_KART:
				if (ENABLE_MULTIPLAYER) {
					createDefaultKartChooser();
					mpClientChosen = false;
				}
				break;
			case MODE_MULTIPLAYER_SERVER_CHOOSE_KART:
				if (ENABLE_MULTIPLAYER) {
					createDefaultKartChooser();
					break;
				}
			case MODE_MULTIPLAYER_CLIENT_CONFIRM_KART:
				if (ENABLE_MULTIPLAYER) {
					container.add(charOkay);
				}
				break;
			case MODE_MULTIPLAYER_ERROR:
				if (ENABLE_MULTIPLAYER) {
					mpClient.close();
					mpServer.close();
					if (ENABLE_MUSIC && sound != null) {
						sound.stop(true);
					}
					
					createDefaultConfirmScreen(100, 101, true, false, false);
					//menuBkgnd.reset(); // TODO: reset was left in for a reason?
				}
				break;
			case MODE_MULTIPLAYER_TIMEOUT:
				if (ENABLE_MULTIPLAYER) {
					mpClient.close();
					mpServer.close();
					if (ENABLE_MUSIC && sound != null) {
						sound.stop(true);
					}
					
					createDefaultConfirmScreen(102, 103, true, false, false);
					//menuBkgnd.reset(); // TODO: reset was left in for a reason?
				}
				break;
			case MODE_MULTIPLAYER_HELP:
				if (ENABLE_MULTIPLAYER) {
					createDefaultScreen(strings.get(97), false, true, false);
					textView.add(strings.get(104));
					textView.setPosition(halfW, halfH + textFontH / 2, Graphics.VCENTER | Graphics.HCENTER);
					textView.reset();
					container.add(textView);
				}
				break;
			case MODE_MULTIPLAYER_CLIENT_WAIT:
			case MODE_MULTIPLAYER_SERVER_WAIT:
				if (ENABLE_MULTIPLAYER) {
					createDefaultConfirmScreen(14, 105, false, true, false);
				}
				break;
			case MODE_MULTIPLAYER_SERVER_PAUSE_LAPS:
			case MODE_MULTIPLAYER_SERVER_PAUSE_POSN:
			case MODE_MULTIPLAYER_SERVER_QUIT:
				break;
			case MODE_RESUME_RACE:
				createDefaultConfirmScreen(2, 7, true, true, false);
				
				mainMenu.add(strings.get(8), RESUME_CONTINUE);
				mainMenu.add(strings.get(9), RESUME_NEW_GAME);
				mainMenu.setPosition(halfW, halfH + textFontH, Graphics.TOP | Graphics.HCENTER);
				mainMenu.reset();
				container.add(mainMenu);
				
				break;
			}
			
			mode = nextMode;
		} catch (Exception ex) {
			if (DEBUG) {
				ex.printStackTrace();
			}
			lastError = ex;
			
			if (nextMode != MODE_ERROR) {
				prepMode(MODE_ERROR);
			}
		}
	}
	
	/**
	 *	Default key input for the menus.
	 *
	 *	@param backMode game mode to go when the 'back' button is pressed
	 *	@param soundResume whether to resume the sound if it was stopped
	 *	@return whether the 'okay' button was pressed
	 */
	private boolean handleStandardMenuInput(int backMode, boolean soundResume) {
		if (joyPlayer.state == unlockNext) {
			if (unlockNext == Joystick.BUTTON_B) {
				testOptionsEnabled = true;
				unlockedTrack = true;
				unlockedKarts = RaceCore.TOTAL_KARTS;
				if (DEBUG) {
					System.out.println("Game unlocked!");
				}
			}
			unlockNext <<= 1;
		} else if (joyPlayer.state != unlockNext >> 1 && joyPlayer.state != 0) {
			unlockNext = Joystick.BUTTON_L;
		}
		if (joyPlayer.isPressed(Joystick.BUTTON_A | Joystick.BUTTON_F) || softKeyL) {
			if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound != null) {
				if (soundResume) {
					sound.resume();
				}
				if (ENABLE_INTERFACE_FX && ENABLE_SNDFX) {
					sound.playEffect(0, 2, 127);
				}
			}
			resetKeys();
			return true;
		} else if (joyPlayer.isPressed(Joystick.BUTTON_B) || softKeyR) {
			if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound != null) {
				if (soundResume && backMode != MODE_NONE) {
					sound.resume();
				}
				if (ENABLE_INTERFACE_FX && ENABLE_SNDFX) {
					sound.playEffect(2, 1, 127);
				}
			}
			resetKeys();
			if (backMode != MODE_NONE) {
				prepMode(backMode);
			}
		}
		return false;
	}
	
	private boolean standardMenuLoop(ScrollingList list, boolean animate) {
		if (animate) {
			animateFrontend();
		}
		if (list.press(
			joyPlayer.isPressed(Joystick.BUTTON_U),
			joyPlayer.isPressed(Joystick.BUTTON_D),
			joyPlayer.isPressed(Joystick.BUTTON_L),
			joyPlayer.isPressed(Joystick.BUTTON_R))) {
			
			if (ENABLE_INTERFACE_FX && ENABLE_SNDFX && sound != null) {
				sound.playEffect(2, 1, 127);
			}
		}
		list.cycle();
		
		return joyPlayer.isPressed(Joystick.BUTTON_A | Joystick.BUTTON_F) || softKeyL;
	}
	
	private boolean handleStandardPrefsInput(ListMenu listmenu, int prefsIdx,  int optCancel) {
		standardMenuLoop(listmenu, true);
		if (handleStandardMenuInput(optCancel, true)) {
			prefs[prefsIdx] = (byte) listmenu.getSelected();
			return true;
		}
		return false;
	}
	
	private void defaultResetRace() {
		countdown = COUNDOWN_START * FRAMES_PER_SEC;
		countdownDigit = Integer.MAX_VALUE;
		lastFinish = 0;
		lastLaps = 0;
		for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
			jumpedGun[n] = false;
		}
	}
	
	/**
	 *	Sets the icons and stats on the kart select screen. Given the player
	 *	index this will update the character's name, kart icon and handling
	 *	stats.
	 */
	private void setPlayerStats(int playerIdx) {
		if (playerIdx < unlockedKarts) {
			nameLine.set(strings.get(FIRST_CHAR_NAME_IDX + playerIdx));
			prevLine.set((char) (FIRST_KART_ICON + playerIdx));
		} else {
			nameLine.set(strings.get(QUESTION_MARKS_IDX));
			prevLine.set(HIDDEN_KART_ICON);
		}
		nameLine.setPosition(subContA.getW() / 2, 0, Graphics.TOP | Graphics.HCENTER);
		starLine[0].set(STATS_0, 0, kartProps[playerIdx][Kart.PROPS_STATS_2] + 2);
		starLine[1].set(STATS_1, 0, kartProps[playerIdx][Kart.PROPS_STATS_1] + 2);
		starLine[2].set(STATS_2, 0, kartProps[playerIdx][Kart.PROPS_STATS_0] + 2);
	}
	
	/**
	 *	Performs the default 3-2-1-GO! countdown.
	 *
	 *	@param whether to show the race position once the countdown finishes
	 */
	private boolean defaultCountdown(boolean showRacePosn) {
		int digit = (countdown + FRAMES_PER_SEC - 1) / FRAMES_PER_SEC;
		if (countdownDigit != digit) {
			countdownDigit  = digit;
			setCountdownChar((char) (COUNTDOWN_ZERO + digit), (digit == 0) ? blinking : null);
			if (ENABLE_SNDFX && sound != null) {
				if (digit > 0) {
					sound.playEffect(5, 8, 127);
				} else {
					sound.playEffect(6, 8, 127);
				}
			}
			
		}
		/*
		 *	Note that the countdown checks now test for less-than as well as
		 *	equals due to the possibility of skipping over a network packet.
		 */
		if (countdown <= 0 && showRacePosn) {
			chrome.setActivePosn(true);
		}
		blinking.cycle();
		countdown--;
		
		if (countdown <= -FRAMES_PER_SEC) {
			container.remove(iconCountdown);
		}
		
		return countdown == 2;
	}
	
	/**
	 *	Default implementation to receive the players' laptimes over the
	 *	network.
	 */
	private void defaultPlayerLapTimes() {
		int lapTime  = logic.getLapTime(mpPlayerIdx, lastLaps);
		if (lapTime == 0) {
			return; // not yet received the network update
		}
		if (lastLaps > 0) {
			lapTime -= logic.getLapTime(mpPlayerIdx, lastLaps - 1);
		}
		if (best[trackNum] == 0 || lapTime < best[trackNum]) {
			chrome.setBest(lapTime);
			best[trackNum] = lapTime;
		}
		lastLaps = racestats[RaceCore.STATS_LAPS];
	}
	
	private void defaultRaceEffects() {
		int sfx = racestats[RaceCore.STATS_KART_FX];
		
		if (vibraEnabled && (sfx & (Kart.BANG_KART | Kart.BANG_SCENERY)) != 0) {
			buzz(120);
		}
		
		if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound == null) {
			/*
			 *	Note the early return if no sound player exists.
			 */
			return;
		}
		
		if (ENABLE_SNDFX) {
			if (sfx != 0) {
				if ((sfx & Kart.BANG_KART) != 0) {
					sound.playEffect(2, 6, 127);
				}
				if ((sfx & Kart.BANG_SCENERY) != 0) {
					sound.playEffect(3, 6, 127);
				}
				if ((sfx & Kart.BANG_PICKUP) != 0) {
					sound.playEffect(4, 6, 127);
				}
				if ((sfx & Kart.BANG_POWERUP) != 0) {
					sound.playEffect(7, RaceCore.POWERUP_SEGMENT_DELAY * (RaceCore.POWERUP_SEGMENTS - 2), 127);
				}
			}
			
			/* The engine sound is too quiet on the only phone it works on (6230)
			int engine = Fixed.abs(racestats[RaceCore.STATS_SPEED]) * 16 + 32;
			if (engine < 0) {
				engine = 0;
			}
			if (engine > 127) {
				engine = 127;
			}
			sound.playEffect(0, -1, engine);*/
		}
		
		if (ENABLE_MUSIC) {
			if (   racestats[RaceCore.STATS_POW_STATE ] == RaceCore.POWERUP_STATE_PAYOUT
				&& racestats[RaceCore.STATS_POW_PLAYER] == mpPlayerIdx
				&& racestats[RaceCore.STATS_POW_PAYOUT] == RaceCore.POWERUP_NITROUS) {
				
				sound.setTempoFactor(150000, FRAMES_PER_SEC * 4);
			}
		}
	}
	
	/**
	 *	Default race input handler.
	 */
	private void defaultRaceInputHandler(int playerIdx, int playerLap, int playerJoyState) {
		if (jumpedGun[playerIdx] && countdown < -4) {
			jumpedGun[playerIdx] = false;
		}
		if (playerLap < LAPS_PER_RACE) {
			if (useThumbwheel && playerIdx == 0) {
				int ud = playerJoyState & (Joystick.BUTTON_U | Joystick.BUTTON_D);
				int lr = playerJoyState & (Joystick.BUTTON_L | Joystick.BUTTON_R);
				joyState[playerIdx] = (playerJoyState & ~(Joystick.BUTTON_U | Joystick.BUTTON_D | Joystick.BUTTON_L | Joystick.BUTTON_R)) | (ud >> 2) | (lr << 2);
			} else {
				joyState[playerIdx] =  playerJoyState;
			}
			if (jumpedGun[playerIdx]) {
				 joyState[playerIdx] = 0;
			}
		} else {
			joyState[playerIdx] = -1;
		}
	}
	
	/**
	 *	Default race code, covers all modes apart from multiplayer.
	 */
	private void defaultRaceLoop(boolean showRacePosn, boolean recordPlayer, boolean withGhost) {
		if (countdown > -FRAMES_PER_SEC) {
			if (defaultCountdown(showRacePosn)) {
				validResume = true;
				/*
				 *	Test to see if the player is already pressing on the
				 *	joystick, in which case they're penalised by delaying
				 *	their start by a few game ticks.
				 */
				jumpedGun[mpPlayerIdx] = (recordPlayer ? joyPlayer.grabFrame() : joyPlayer.state) != 0;
				if (withGhost) {
					jumpedGun[RECORD_IDX] = joyRecord.nextFrame() != 0;
				}
			}
		}
		
		logic.getAllStats(racestats, mpPlayerIdx);
		
		if (racestats[RaceCore.STATS_LAPS] != lastLaps) {
			defaultPlayerLapTimes();
		}
		
		if (racestats[RaceCore.STATS_LAPS] < LAPS_PER_RACE) {
			if (countdown <= 0) {
				defaultRaceInputHandler(mpPlayerIdx, racestats[RaceCore.STATS_LAPS], recordPlayer ? joyPlayer.grabFrame() : joyPlayer.state);
				
				if (soakTest) {
					joyState[mpPlayerIdx] = -1;
				}
				
				if (withGhost) {
					defaultRaceInputHandler(RECORD_IDX, logic.getStats(RaceCore.STATS_LAPS, RECORD_IDX), joyRecord.nextFrame());
				}
				
				logic.loop(joyState, !withGhost);
			}
			logic.render(RaceCore.USE_FOLLOW_CAM);
		} else {
			validResume = false;
			
			joyState[PLAYER_IDX] = -1;
			if (withGhost) {
				joyState[RECORD_IDX] = -1;
			}
			prepMode(MODE_STATS_LAPS);
		}
		
		defaultRaceEffects();
		
		if ((joyPlayer.isPressed(Joystick.BUTTON_Y) || softKeyR) && mode != MODE_STATS_LAPS) {
			resetKeys();
			prepMode(MODE_PAUSE);
		}
	}
	
	/**
	 *	Saves the time trial ghost data. Data is saved only if the total time
	 *	of the new race is better than that of the recording, or no previous
	 *	ghost exists.
	 *
	 *	TODO: this would be more efficient if the joystick was RLEd.
	 */
	private void saveGhostData() {
		boolean saveMe = false;
		if (validGhost[trackNum]) {
			if (logic.getLapTime(mpPlayerIdx, LAPS_PER_RACE - 1) < ByteUtils.bytesToUnsignedShort(stateBuffer, 2 + (stateBuffer[1] - 1) * 2)) {
				saveMe = true;
			}
		} else {
			saveMe = true;
		}
		
		if (saveMe) {
			int n = 0;
			stateBuffer[n++] = (byte) selectedKart;
			stateBuffer[n++] = (byte) LAPS_PER_RACE; // actual number of laps
			for (int i = 0; i < RaceCore.MAX_LAPS; i++) {
				ByteUtils.shortToBytes(stateBuffer, n, logic.getLapTime(mpPlayerIdx, i));
				n += 2;
			}
			n = joyPlayer.save(stateBuffer, n);
			
			if (setPrefs("trial-" + trackNum, stateBuffer)) {
				validGhost[trackNum] = true;
			}
			
			/*
			 *	Once the buffer has been saved the data is invalidated by
			 *	setting the laps to zero, which fixes potential problems where
			 *	a previous recording might be considered current.
			 */
			stateBuffer[1] = 0;
		}
	}
	
	private void unlockBonuses(int level) {
		int total = 0;
		for (int n = 0; n < TOTAL_TOURNAMENTS; n++) {
			if (DEBUG) {
				System.out.println("Level " + level + " group: " + n + " keys = " + totalKeys[level][n]);
			}
			total += totalKeys[level][n];
		}
		if (total >= keysToCollect) {
			switch (level) {
			case DIFFICULTY_HARD:
				unlockedTrack = true;
				if (DEBUG) {
					System.out.println("Unlocking track!");
				}
			case DIFFICULTY_MEDIUM:
				if (unlockedKarts < RaceCore.MAX_KARTS + 2) {
					unlockedKarts = RaceCore.MAX_KARTS + 2;
				}
				if (DEBUG) {
					System.out.println("Unlocking kart!");
				}
			case DIFFICULTY_EASY:
				if (unlockedKarts < RaceCore.MAX_KARTS + 1) {
					unlockedKarts = RaceCore.MAX_KARTS + 1;
				}
				if (DEBUG) {
					System.out.println("Unlocking kart!");
				}
			}
		}
	}
	
	public final void run() {
		try {
			switch (mode) {
			case MODE_NONE:
				break;
			case MODE_ERROR:
				standardMenuLoop(textView, true);
				if (handleStandardMenuInput(MODE_NONE, true)) {
					prepMode(MODE_MAIN_MENU);
				}
				break;
			case MODE_STARTUP:
				sizeChanged(getWidth(), getHeight());
				if (displayTimer-- > -FRAMES_PER_SEC / 2) {
					if (joyPlayer.isPressed(Joystick.BUTTON_F)) {
						setRenderDefaults(true);
						prepMode(MODE_INITIALISE);
						if (DEBUG) {
							System.out.println("Resetting prefs");
						}
					}
				} else {
					prepMode(MODE_INITIALISE);
				}
				break;
			case MODE_INITIALISE:
				synchronized (loadingLock) {
					if (needFrontend) {
						initFrontend();
						needFrontend = false;
					}
					localiseOverlays();
				}
				prepMode(MODE_LOADING);
				break;
			case MODE_LOADING:
				synchronized (loadingLock) {
					if (needTrack) {
						initTrack();
						needTrack = false;
					}
				}
				updatePrefs(); // sets the difficulty level now the race core has inited
				
				prepMode(MODE_LOGO_PAUSE);
				
				break;
			case MODE_LOGO_PAUSE:
				if (System.currentTimeMillis() - logoShowTime > LOGO_SHOWING_MS) {
					if (firstRun && numLocales > 1) {
						prepMode(MODE_OPTIONS_LANGUAGE);
					} else {
						prepMode(MODE_MAIN_MENU);
					}
				}
				break;
			case MODE_MAIN_MENU:
				standardMenuLoop(mainMenu, true);
				if (soakTest) {
					if (joyPlayer.isPressed(Joystick.BUTTON_Y) || softKeyR) {
						soakTest = false;
					} else {
						soakTime--;
						if (soakTime < 0) {
							prepMode(MODE_CHOOSE_GAME);
						}
					}
				} else {
					if (handleStandardMenuInput(MODE_NONE, true)) {
						switch (mainMenu.getSelected()) {
						case MAIN_MENU_PLAY:
							if (QUICK_START) {
								raceMode = MODE_PRACTICE;
								prepMode(MODE_TRACK_NAME);
							} else {
								if (validResume) {
									prepMode(MODE_RESUME_RACE);
								} else {
									prepMode(MODE_CHOOSE_GAME);
								}
							}
							break;
						case MAIN_MENU_OPTIONS:
							prepMode(MODE_OPTIONS);
							break;
						case MAIN_MENU_INSTRUCTIONS:
							prepMode(MODE_INSTRUCTIONS);
							break;
						case MAIN_MENU_ABOUT:
							prepMode(MODE_ABOUT);
							break;
						case MAIN_MENU_QUIT:
							prepMode(MODE_QUIT);
						}
					}
				}
				break;
			case MODE_CHOOSE_GAME:
				standardMenuLoop(mainMenu, true);
				if (soakTest) {
					raceMode = MODE_PRACTICE;
					prepMode(MODE_CHOOSE_KART);
				} else {
					if (handleStandardMenuInput(validResume ? MODE_RESUME_RACE : MODE_MAIN_MENU, true)) {
						validResume = false;
						switch (mainMenu.getSelected()) {
						case GAME_MENU_PRACTICE:
							raceMode = MODE_PRACTICE;
							prepMode(MODE_CHOOSE_KART);
							break;
						case GAME_MENU_TOURNAMENT:
							raceMode = MODE_TOURNAMENT;
							prepMode(MODE_CHOOSE_KART);
							break;
						case GAME_MENU_TIME_TRIAL:
							raceMode = MODE_TIME_TRIAL;
							prepMode(MODE_CHOOSE_KART);
							break;
						case GAME_MENU_MULTIPLAYER:
							prepMode(MODE_MULTIPLAYER);
							break;
						}
					}
				}
				break;
			case MODE_CHOOSE_KART:
				standardMenuLoop(iconMenu, true);
				if (soakTest) {
					selectedKart = Fixed.rand(RaceCore.TOTAL_KARTS);
					prepMode(MODE_CHOOSE_COURSE);
				} else {
					if (selectedKart != iconMenu.getSelected()) {
						selectedKart  = iconMenu.getSelected();
						setPlayerStats(selectedKart);
					}
					if (handleStandardMenuInput(MODE_CHOOSE_GAME, true)) {
						if (selectedKart < unlockedKarts) {
							prepMode(MODE_CHOOSE_COURSE);
						}
					}
				}
				break;
			case MODE_CHOOSE_COURSE:
				standardMenuLoop(mainMenu, true);
				if (soakTest) {
					soakTime = SOAK_TIME;
					if (DEMO_MODE) {
						trackNum = Fixed.rand(TRACKS_PER_LEVEL);
					} else {
						trackNum = Fixed.rand(TOTAL_TRACKS);
					}
					levelNum = trackNum / TRACKS_PER_LEVEL;
					prepMode(MODE_TRACK_NAME);
				} else {
					if (keysToCollect > 0) {
						setResultsKeyLine(mainMenu.getSelected());
					}
					
					/*
					 *	See below in the track chooser for an explanation.
					 */
					mpReady = true;
					if (ENABLE_MULTIPLAYER) {
						if (raceMode == MODE_MULTIPLAYER_SERVER) {
							mpReady = mpIsClientMode(MULTIPLAYER_MODE_WAIT);
							mpDefaultServerLoop(MULTIPLAYER_MODE_KART);
						}
					}
					
					if (handleStandardMenuInput(raceMode == MODE_MULTIPLAYER_SERVER ? MODE_MULTIPLAYER_SERVER_CHOOSE_KART: MODE_CHOOSE_KART, true)) {
						levelNum = mainMenu.getSelected();
						trackNum = levelNum * TRACKS_PER_LEVEL;
						if (DEMO_MODE) {
							if (levelNum == 0) {
								if (raceMode == MODE_TOURNAMENT) {
									prepMode(MODE_TRACK_NAME);
								} else {
									prepMode(MODE_CHOOSE_TRACK);
								}
							}
						} else {
							if (levelNum < TOTAL_TOURNAMENTS - 1) {
								if (raceMode == MODE_TOURNAMENT) {
									prepMode(MODE_TRACK_NAME);
								} else {
									prepMode(MODE_CHOOSE_TRACK);
								}
							} else {
								if (unlockedTrack) {
									if (mpReady) {
										prepMode(MODE_TRACK_NAME);
									} else {
										if (ENABLE_MULTIPLAYER) {
											prepMode(MODE_MULTIPLAYER_SERVER_WAIT);
										}
									}
								}
							}
						}
					}
				}
				break;
			case MODE_CHOOSE_TRACK:
				standardMenuLoop(mainMenu, true);
				setBestLine(trackNum + mainMenu.getSelected());
				
				/*
				 *	When not in multiplayer server mode this is the phone is
				 *	always ready to move to the track screen after choosing.
				 */
				mpReady = true;
				
				if (ENABLE_MULTIPLAYER) {
					if (raceMode == MODE_MULTIPLAYER_SERVER) {
						mpReady = mpIsClientMode(MULTIPLAYER_MODE_WAIT);
						/*
						 *	Until all of the clients are in the wait mode we
						 *	continue with the kart selections.
						 */
						mpDefaultServerLoop(MULTIPLAYER_MODE_KART);
					}
				}
				
				if (handleStandardMenuInput(MODE_CHOOSE_COURSE, true)) {
					trackNum += mainMenu.getSelected();
					if (mpReady) {
						prepMode(MODE_TRACK_NAME);
					} else {
						if (ENABLE_MULTIPLAYER) {
							/*
							 *	As the clients aren't yet ready the server has
							 *	to go into the 'wait' state.
							 */
							prepMode(MODE_MULTIPLAYER_SERVER_WAIT);
						}
					}
				}
				
				break;
			case MODE_OPTIONS:
				standardMenuLoop(tallMenu, true);
				if (handleStandardMenuInput(MODE_MAIN_MENU, true)) {
					switch (tallMenu.getSelected()) {
					case OPTIONS_DIFFICULTY:
						prepMode(MODE_OPTIONS_DIFFICULTY);
						break;
					case OPTIONS_SOUND:
						prepMode(MODE_OPTIONS_SOUND);
						break;
					case OPTIONS_VIBRATE:
						prepMode(MODE_OPTIONS_VIBRATE);
						break;
					case OPTIONS_LANGUAGE:
						prepMode(MODE_OPTIONS_LANGUAGE);
						break;
					case OPTIONS_ERASE_RECORDS:
						prepMode(MODE_OPTIONS_ERASE_RECORDS);
						break;
					case OPTIONS_ADVANCED:
						prepMode(MODE_OPTIONS_ADVANCED);
						break;
					case OPTIONS_SOAK:
						soakTest = true;
						prepMode(MODE_CHOOSE_GAME);
						break;
					}
				}
				break;
			case MODE_INSTRUCTIONS:
			case MODE_ABOUT:
				standardMenuLoop(textView, true);
				if (handleStandardMenuInput(MODE_NONE, true)) {
					prepMode(MODE_MAIN_MENU);
				}
				break;
			case MODE_QUIT:
				animateFrontend();
				if (handleStandardMenuInput(MODE_MAIN_MENU, false)) {
					stop();
				}
				break;
			case MODE_OPTIONS_DIFFICULTY:
				if (handleStandardPrefsInput(tallMenu, PREFS_DIFFICULTY, MODE_OPTIONS)) {
					updatePrefs();
					prepMode(MODE_OPTIONS);
				}
				break;
			case MODE_OPTIONS_SOUND:
				if (handleStandardPrefsInput(tallMenu, PREFS_SOUND, MODE_OPTIONS)) {
					updatePrefs();
					if (ENABLE_MUSIC && sound != null) {
						sound.play("/a.mid", true);
					}
					prepMode(MODE_OPTIONS);
				}
				break;
			case MODE_OPTIONS_VIBRATE:
				if (handleStandardPrefsInput(tallMenu, PREFS_VIBRATE, MODE_OPTIONS)) {
					updatePrefs();
					prepMode(MODE_OPTIONS);
				}
				break;
			case MODE_OPTIONS_LANGUAGE:
				if (handleStandardPrefsInput(tallMenu, PREFS_LANGUAGE, firstRun ? MODE_NONE : MODE_OPTIONS)) {
					strings.use(prefs[PREFS_LANGUAGE]);
					localiseAbout();
					localiseOverlays();
					
					if (firstRun) {
						prepMode(MODE_MAIN_MENU);
					} else {
						prepMode(MODE_OPTIONS);
					}
				}
				break;
			case MODE_OPTIONS_ERASE_RECORDS:
				animateFrontend();
				if (handleStandardMenuInput(MODE_OPTIONS, true)) {
					resetKeys();
					for (int n = 0; n < TOTAL_TRACKS; n++) {
						validGhost[n] = false;
						best[n] = 0;
						
					}
					for (int i = 0; i < DIFFICULTY_LEVELS; i++) {
						for (int n = 0; n < 4; n++) {
							totalKeys[i][n] = -1;
						}
						for (int n = 0; n < TOTAL_TRACKS; n++) {
							finishPos[i][n] = 0;
						}
					}
					prepMode(MODE_OPTIONS);
				}
				break;
			case MODE_OPTIONS_ADVANCED:
				standardMenuLoop(tallMenu, true);
				if (handleStandardMenuInput(MODE_OPTIONS, true)) {
					switch (tallMenu.getSelected()) {
					case ADVANCED_RENDERER:
						prepMode(MODE_ADVANCED_RENDERER);
						break;
					case ADVANCED_BACKGROUND:
						prepMode(MODE_ADVANCED_BACKGROUND);
						break;
					case ADVANCED_BORDER:
						prepMode(MODE_ADVANCED_BORDER);
						break;
					case ADVANCED_CLIP:
						prepMode(MODE_ADVANCED_CLIP);
						break;
					case ADVANCED_DEFAULTS:
						prepMode(MODE_ADVANCED_DEFAULTS);
						break;
					}
				}
				break;
			case MODE_ADVANCED_RENDERER:
				if (handleStandardPrefsInput(tallMenu, PREFS_RENDERER, MODE_OPTIONS_ADVANCED)) {
					prepMode(MODE_ADVANCED_MESSAGE);
				}
				break;
			case MODE_ADVANCED_BACKGROUND:
				if (handleStandardPrefsInput(tallMenu, PREFS_BACKGROUND, MODE_OPTIONS_ADVANCED)) {
					prepMode(MODE_ADVANCED_MESSAGE);
				}
				break;
			case MODE_ADVANCED_BORDER:
				if (handleStandardPrefsInput(tallMenu, PREFS_BORDER, MODE_OPTIONS_ADVANCED)) {
					prepMode(MODE_ADVANCED_MESSAGE);
				}
				break;
			case MODE_ADVANCED_CLIP:
				if (handleStandardPrefsInput(tallMenu, PREFS_CLIP, MODE_OPTIONS_ADVANCED)) {
					prepMode(MODE_ADVANCED_MESSAGE);
				}
				break;
			case MODE_ADVANCED_DEFAULTS:
				animateFrontend();
				if (handleStandardMenuInput(MODE_OPTIONS_ADVANCED, true)) {
					resetKeys();
					setRenderDefaults(true);
					prepMode(MODE_ADVANCED_MESSAGE);
				}
				break;
			case MODE_ADVANCED_MESSAGE:
				animateFrontend();
				if (handleStandardMenuInput(MODE_NONE, true)) {
					resetKeys();
					prepMode(MODE_OPTIONS_ADVANCED);
				}
				break;
			case MODE_PRACTICE:
				defaultRaceLoop(true, false, false);
				
				if (soakTest) {
					soakTime--;
					if (soakTime < 0) {
						soakTime = SOAK_TIME;
						prepMode(MODE_STATS_POSN);
						logic.estimate();
					}
				}
				break;
			case MODE_TOURNAMENT:
				defaultRaceLoop(true, false, false);
				break;
			case MODE_TIME_TRIAL:
				defaultRaceLoop(validGhost[trackNum], true, validGhost[trackNum]);
				break;
			case MODE_PAUSE:
				standardMenuLoop(mainMenu, false);
				if (handleStandardMenuInput(MODE_NONE, false)) {
					switch (mainMenu.getSelected()) {
					case PAUSE_RESUME:
						if (ENABLE_MULTIPLAYER && raceMode == MODE_MULTIPLAYER_CLIENT) {
							clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_CONT;
						} else {
							container.remove(mainMenu);
							mode = raceMode;
						}
						if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound != null) {
							sound.resume();
						}
						break;
					case PAUSE_MAIN_MENU:
						if (ENABLE_MULTIPLAYER && raceMode == MODE_MULTIPLAYER_CLIENT) {
							clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_QUIT;
							mpClient.update();
						}
						prepMode(MODE_MAIN_MENU);
						break;
					}
				}
				if (ENABLE_MULTIPLAYER) {
					switch (raceMode) {
					case MODE_MULTIPLAYER_CLIENT:
						mpDefaultClientLoop();
						break;
					case MODE_MULTIPLAYER_SERVER:
						mpDefaultServerLoop(MULTIPLAYER_MODE_HALT);
						break;
					}
				}
				break;
			case MODE_TRACK_NAME:
				mpReady = true;
				if (ENABLE_MULTIPLAYER) {
					if (raceMode == MODE_MULTIPLAYER_SERVER) {
						if (mpIsClientMode(MULTIPLAYER_MODE_INIT)) {
							mpDefaultServerLoop(MULTIPLAYER_MODE_LOAD);
						} else {
							mpDefaultServerLoop(MULTIPLAYER_MODE_INIT);
							mpReady = false;
						}
					}
				}
				if (mpReady) {
					needTrack = true;
					
					prepMode(MODE_TRACK_LOAD);
				}
				break;
			case MODE_TRACK_LOAD:
				if (ENABLE_MULTIPLAYER) {
					if (raceMode == MODE_MULTIPLAYER_CLIENT) {
						mpDefaultClientLoop();
					}
				}
				if (soakTest || joyPlayer.isPressed(Joystick.BUTTON_A | Joystick.BUTTON_F) || softKeyL) {
					resetKeys();
					prepMode(raceMode);
					
					if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound != null) {
						sound.resume();
					}
				}
				break;
			case MODE_STATS_LAPS:
				logic.render(RaceCore.USE_FINISH_CAM);
				switch (raceMode) {
				case MODE_MULTIPLAYER_CLIENT:
					if (ENABLE_MULTIPLAYER) {
						mpDefaultClientLoop();
					}
					break;
				case MODE_MULTIPLAYER_SERVER:
					if (ENABLE_MULTIPLAYER) {
						mpDefaultServerLoop(MULTIPLAYER_MODE_RACE);
					}
					break;
				default:
					logic.loop(joyState, true);
				}
				
				menuMid.moveBy(-1, 0);
				starAnim.cycle();
				
				if (standardMenuLoop(miniView, false)) {
					resetKeys();
					
					switch (raceMode) {
					case MODE_PRACTICE:
					case MODE_TOURNAMENT:
					case MODE_MULTIPLAYER_CLIENT:
					case MODE_MULTIPLAYER_SERVER:
						prepMode(MODE_STATS_POSN);
						break;
					case MODE_TIME_TRIAL:
						saveGhostData();
						prepMode(MODE_MAIN_MENU);
						break;
					}
				}
				break;
			case MODE_STATS_POSN:
				logic.render(RaceCore.USE_STATIC_CAM);
				switch (raceMode) {
				case MODE_MULTIPLAYER_CLIENT:
					if (ENABLE_MULTIPLAYER) {
						mpDefaultClientLoop();
					}
					break;
				case MODE_MULTIPLAYER_SERVER:
					if (ENABLE_MULTIPLAYER) {
						mpDefaultServerLoop(MULTIPLAYER_MODE_RACE);
					}
					break;
				default:
					logic.loop(joyState, true);
				}
				
				/*
				 *	It's possible that we're no longer showing the kart
				 *	positions so take an early exit.
				 */
				if (ENABLE_MULTIPLAYER && mode != MODE_STATS_POSN) {
					break;
				}
					
				int thisFinish = logic.getNextFinishPosition();
				if (thisFinish > lastFinish) {
					for (int n = lastFinish; n < thisFinish; n++) {
						int kartIndex = logic.getKartIndex(n);
						fillRaceResults(raceResultLine[n], n, player[kartIndex].kartIdx,
							raceMode != MODE_TOURNAMENT, logic.getLapTime(kartIndex, LAPS_PER_RACE - 1));
						int lineIndex = miniView.addLine(raceResultLine[n]);
						if (kartIndex == mpPlayerIdx) {
							miniView.setEffect(lineIndex, jumpFast);
							if (raceMode == MODE_TOURNAMENT) {
								tourneyFinish[trackNum - levelNum * TRACKS_PER_LEVEL] = n;
							}
						}
					}
					miniView.updateList();
					lastFinish = thisFinish;
				}
				
				menuMid.moveBy(-1, 0);
				starAnim.cycle();
				jumpFast.cycle(); // animates the player's position
				
				if (soakTest) {
					miniView.cycle(); // just to stop the 'more' arrow bouncing like mad!
					
					soakTime--;
					if (soakTime < 0) {
						soakTime = SOAK_TIME / 8;
						prepMode(MODE_MAIN_MENU);
					}
				} else {
					if (ENABLE_MULTIPLAYER && (raceMode == MODE_MULTIPLAYER_CLIENT || raceMode == MODE_MULTIPLAYER_SERVER)) {
						if (raceMode == MODE_MULTIPLAYER_SERVER) {
							if (lastFinish == RaceCore.MAX_KARTS) {
								container.remove(charBack);
								container.add(charTick);
							}
							if ((standardMenuLoop(miniView, false) && lastFinish == RaceCore.MAX_KARTS) || joyPlayer.isPressed(Joystick.BUTTON_Y) || softKeyR) {
								resetKeys();
								prepMode(MODE_MULTIPLAYER_SERVER_QUIT);
							}
						} else {
							standardMenuLoop(miniView, false);
							if (joyPlayer.isPressed(Joystick.BUTTON_Y) || softKeyR) {
								resetKeys();
								prepMode(MODE_MAIN_MENU);
							}
						}
					} else {
						if (standardMenuLoop(miniView, false)) {
							resetKeys();
							if (lastFinish == RaceCore.MAX_KARTS) {
								if (raceMode == MODE_TOURNAMENT) {
									prepMode(MODE_STATS_POINTS);
								} else {
									prepMode(MODE_MAIN_MENU);
								}
							} else {
								logic.estimate();
							}
						}
					}
				}
				break;
			case MODE_STATS_POINTS:
				logic.loop(joyState, true);
				logic.render(RaceCore.USE_STATIC_CAM);
				
				tempIdx = (DEFAULT_SCREEN_TIME - displayTimer) / 2;
				if (tempIdx < RaceCore.MAX_KARTS && ((DEFAULT_SCREEN_TIME - displayTimer) % 2 == 0)) {
					int kartIndex = logic.getKartIndex(tempIdx);
					fillRaceResults(raceResultLine[tempIdx], tempIdx, player[kartIndex].kartIdx, false, player[kartIndex].points, pointsAwarded[tempIdx]);
					miniView.updateList();
				}
				
				menuMid.moveBy(-1, 0);
				starAnim.cycle();
				jumpFast.cycle(); // animates the player's position in the list
				
				displayTimer--;
				if (standardMenuLoop(miniView, false)) {
					resetKeys();
					prepMode(MODE_STATS_TOTALS);
				}
				
				break;
			case MODE_STATS_TOTALS:
				logic.loop(joyState, true);
				logic.render(RaceCore.USE_STATIC_CAM);
				
				tempIdx = (DEFAULT_SCREEN_TIME - displayTimer) / 2;
				if (tempIdx < RaceCore.MAX_KARTS && ((DEFAULT_SCREEN_TIME - displayTimer) % 2 == 0)) {
					int kartIndex = sorter[tempIdx].index;
					int lineIndex = miniView.addLine(fillRaceResults(raceResultLine[tempIdx], tempIdx, player[kartIndex].kartIdx, levelNum >= (TOTAL_TOURNAMENTS - 1) || (trackNum % TRACKS_PER_LEVEL) == (TRACKS_PER_LEVEL - 1), player[kartIndex].points, 0), 0, RESULTS_LINE_LENGTH - 3);
					if (kartIndex == mpPlayerIdx) {
						miniView.setEffect(lineIndex, jumpFast);
					}
					miniView.updateList();
				}
				
				menuMid.moveBy(-1, 0);
				starAnim.cycle();
				jumpFast.cycle(); // animates the player's position in the list
				
				displayTimer--;
				if (standardMenuLoop(miniView, false)) {
					resetKeys();
					if (levelNum < (TOTAL_TOURNAMENTS - 1) && (trackNum++ % TRACKS_PER_LEVEL) < (TRACKS_PER_LEVEL - 1)) {
						prepMode(MODE_TRACK_NAME);
					} else {
						prepMode(MODE_STATS_RESULTS);
					}
				}
				
				break;
			case MODE_STATS_RESULTS:
				animateFrontend();
				jumpFast.cycle();
				if (handleStandardMenuInput(MODE_NONE, true)) {
					resetKeys();
					prepMode(MODE_MAIN_MENU);
				}
				break;
			case MODE_MULTIPLAYER:
				if (ENABLE_MULTIPLAYER) {
					standardMenuLoop(mainMenu, true);
					if (handleStandardMenuInput(MODE_CHOOSE_GAME, true)) {
						switch (mainMenu.getSelected()) {
						case MULTIPLAYER_CLIENT:
							raceMode = MODE_MULTIPLAYER_CLIENT;
							prepMode(MODE_MULTIPLAYER_CLIENT_DISCOVERY);
							break;
						case MULTIPLAYER_SERVER:
							raceMode = MODE_MULTIPLAYER_SERVER;
							prepMode(MODE_MULTIPLAYER_SERVER_DISCOVERY);
							break;
						case MULTIPLAYER_HELP:
							prepMode(MODE_MULTIPLAYER_HELP);
							break;
						}
					}
				}
				break;
			case MODE_MULTIPLAYER_CLIENT:
				if (ENABLE_MULTIPLAYER) {
					
					logic.getAllStats(racestats, mpPlayerIdx);
					if (racestats[RaceCore.STATS_LAPS] != lastLaps) {
						defaultPlayerLapTimes();
					}
					
					logic.render(RaceCore.USE_FOLLOW_CAM);
					
					defaultRaceEffects();
					
					if (racestats[RaceCore.STATS_LAPS] >= LAPS_PER_RACE) {
						/*
						 *	TODO: this test for lastLaps is new and isn't well
						 *	verified. It was noted that occasionally in is was
						 *	zero and thus causing an exception later on. It's
						 *	more than likely due to the multiplayer network
						 *	code being asynchronous.
						 */
						if (lastLaps > 0) {
							if (logic.getLapTime(mpPlayerIdx, lastLaps - 1) > 0) {
								prepMode(MODE_STATS_LAPS);
							}
						} else {
							if (DEBUG) {
								System.out.println("lastLaps is zero!!!");
							}
						}
					}
					
					if (joyPlayer.isPressed(Joystick.BUTTON_Y) || softKeyR) {
						clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_HALT;
					} else {
						clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_RACE;
						clientState[MULTIPLAYER_DATA] = (byte) joyPlayer.state;
					}
					mpDefaultClientLoop();
				}
				break;
			case MODE_MULTIPLAYER_SERVER:
				if (ENABLE_MULTIPLAYER) {
					
					logic.getAllStats(racestats, mpPlayerIdx);
					if (racestats[RaceCore.STATS_LAPS] != lastLaps) {
						defaultPlayerLapTimes();
					}
					
					logic.render(RaceCore.USE_FOLLOW_CAM);
					
					defaultRaceEffects();
					
					if (racestats[RaceCore.STATS_LAPS] >= LAPS_PER_RACE) {
						if (logic.getLapTime(mpPlayerIdx, lastLaps - 1) > 0) {
							prepMode(MODE_STATS_LAPS);
						}
					}
		
					if (joyPlayer.isPressed(Joystick.BUTTON_Y) || softKeyR) {
						resetKeys();
						prepMode(MODE_PAUSE);
					}
					
					mpDefaultServerLoop(MULTIPLAYER_MODE_RACE);
				}
				break;
			case MODE_MULTIPLAYER_CLIENT_DISCOVERY:
				if (ENABLE_MULTIPLAYER) {
					animateFrontend();
					handleStandardMenuInput(MODE_MULTIPLAYER, true);
					
					switch (mpClient.getStatus()) {
					case MultiplayerClient.STATUS_INACTIVE:
						if (displayTimer-- < 0) {
							prepMode(MODE_MULTIPLAYER_TIMEOUT);
						}
						break;
					case MultiplayerClient.STATUS_WAITING:
						break;
					case MultiplayerClient.STATUS_CONNECTED:
						mpDefaultClientLoop();
						break;
					}
				}
				break;
			case MODE_MULTIPLAYER_SERVER_DISCOVERY:
				if (ENABLE_MULTIPLAYER) {
					animateFrontend();
					handleStandardMenuInput(MODE_MULTIPLAYER, true);
					
					switch (mpServer.getStatus()) {
					case MultiplayerServer.STATUS_INACTIVE:
						if (displayTimer-- < 0) {
							prepMode(MODE_MULTIPLAYER_TIMEOUT);
						}
						/*
						 *	Falls through to draw the number of found devices.
						 */
					case MultiplayerServer.STATUS_WAITING:
						mpDiscoveredLine.set(fillDecimalChars(mpDiscoveredChar, 1, mpServer.getDiscoveredSize()));
						break;
					case MultiplayerServer.STATUS_CONNECTED:
						mpClientSize = mpServer.getConnectionSize();
						
						serverState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_PIDX;
						/*
						 *	Checks the player index echo from the connected
						 *	devices. Once they all agree on who's who it's
						 *	safe to proceed.
						 */
						int mpActive = 0;
						for (int n =  0; n < mpClientSize; n++) {
							serverState[MULTIPLAYER_PIDX] = (byte) (n + 1);
							mpServer.update(n);
							if (clientStateBuffer[n][MULTIPLAYER_PIDX] > 0) {
								mpActive++;	
							}
						}
						if (mpActive == mpClientSize) {
							prepMode(MODE_MULTIPLAYER_SERVER_CHOOSE_KART);
						}
						break;
					}
				}
				break;
			case MODE_MULTIPLAYER_CLIENT_CHOOSE_KART:
				if (ENABLE_MULTIPLAYER) {
					
					standardMenuLoop(iconMenu, true);
					if (selectedKart != iconMenu.getSelected()) {
						selectedKart  = iconMenu.getSelected();
						setPlayerStats(selectedKart);
					}
					if (handleStandardMenuInput(MODE_MULTIPLAYER, true) && selectedKart < unlockedKarts) {
						clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_KART;
						clientState[MULTIPLAYER_DATA] = (byte) selectedKart;
						mpClientChosen = true;
					}
					
					mpDefaultClientLoop();
					mpSetKartChoiceMenu();
					
					if (mpClientChosen && player[mpPlayerIdx].kartIdx >= 0) {
						prepMode(MODE_MULTIPLAYER_CLIENT_CONFIRM_KART);
					}
				}
				break;
			case MODE_MULTIPLAYER_SERVER_CHOOSE_KART:
				if (ENABLE_MULTIPLAYER) {
					
					standardMenuLoop(iconMenu, true);
					if (selectedKart != iconMenu.getSelected()) {
						selectedKart  = iconMenu.getSelected();
						setPlayerStats(selectedKart);
					}
					if (handleStandardMenuInput(MODE_MULTIPLAYER, true) && selectedKart < unlockedKarts) {
						if (setKartChoice(mpPlayerIdx, selectedKart)) {
							prepMode(MODE_CHOOSE_COURSE);
						}
					}
					
					mpDefaultServerLoop(MULTIPLAYER_MODE_KART);
					mpSetKartChoiceMenu();
				}
				break;
			case MODE_MULTIPLAYER_CLIENT_CONFIRM_KART:
				if (ENABLE_MULTIPLAYER) {
					animateFrontend();
					iconMenu.cycle();
					
					if (handleStandardMenuInput(MODE_MULTIPLAYER_CLIENT_CHOOSE_KART, true)) {
						if (serverState[MULTIPLAYER_MODE] != MULTIPLAYER_MODE_WAIT) {
							prepMode(MODE_MULTIPLAYER_CLIENT_WAIT);
							/*
							 *	Let the server know we're now waiting on it.
							 */
							clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_WAIT;
						}
					} else {
						mpDefaultClientLoop();
					}
					
					mpSetKartChoiceMenu();
				}
				break;
			case MODE_MULTIPLAYER_ERROR:
			case MODE_MULTIPLAYER_TIMEOUT:
				if (ENABLE_MULTIPLAYER) {
					animateFrontend();
					if (handleStandardMenuInput(MODE_NONE, true)) {
						prepMode(MODE_MULTIPLAYER);
					}
				}
				break;
			case MODE_MULTIPLAYER_HELP:
				if (ENABLE_MULTIPLAYER) {
					standardMenuLoop(textView, true);
					if (handleStandardMenuInput(MODE_NONE, true)) {
						prepMode(MODE_MULTIPLAYER);
					}
				}
				break;
			case MODE_MULTIPLAYER_CLIENT_WAIT:
				if (ENABLE_MULTIPLAYER) {
					animateFrontend();
					
					mpDefaultClientLoop();
					if (mpClient.getStatus() != MultiplayerClient.STATUS_CONNECTED) {
						prepMode(MODE_MULTIPLAYER_ERROR);
					} else {
						handleStandardMenuInput(MODE_MULTIPLAYER, true);
					}
				}
				break;
			case MODE_MULTIPLAYER_SERVER_WAIT:
				if (ENABLE_MULTIPLAYER) {
					animateFrontend();
					
					if (mpIsClientMode(MULTIPLAYER_MODE_WAIT)) {
						prepMode(MODE_TRACK_NAME);
					} else {
						mpDefaultServerLoop(MULTIPLAYER_MODE_KART);
					}
					
					handleStandardMenuInput(MODE_MULTIPLAYER, true);
				}
				break;
			case MODE_MULTIPLAYER_SERVER_PAUSE_LAPS:
			case MODE_MULTIPLAYER_SERVER_PAUSE_POSN:
				if (ENABLE_MULTIPLAYER) {
					mpDefaultServerLoop(MULTIPLAYER_MODE_HALT);
					menuMid.moveBy(-1, 0);
					starAnim.cycle();
					jumpFast.cycle(); // animates the player's position
				}
				break;
			case MODE_MULTIPLAYER_SERVER_QUIT:
				if (ENABLE_MULTIPLAYER) {
					serverState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_QUIT;
					if (mpServer.updateAll() == 0) {
						prepMode(MODE_MAIN_MENU);
					}
				}
				break;
			case MODE_RESUME_RACE:
				standardMenuLoop(mainMenu, true);
				if (handleStandardMenuInput(MODE_MAIN_MENU, true)) {
					switch (mainMenu.getSelected()) {
					case RESUME_CONTINUE:
						skipPaint = true;
						createDefaultTrackScreen();
						prepTrack();
						track.refresh();
						playTrackTune();
						mode = raceMode;
						
						break;
					case RESUME_NEW_GAME:
						prepMode(MODE_CHOOSE_GAME);
						break;
					}
				}
				break;
			}
			if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound != null) {
				sound.cycle();
			}
		} catch (Throwable ex) {
			if (DEBUG) {
				ex.printStackTrace();
			}
			lastError = ex;
			
			if (mode != MODE_ERROR) {
				prepMode(MODE_ERROR);
			}
		}
		
		/*
		 *	When restoring from a saved state it can be visually jarring as
		 *	two quick updates happen in succession. This ensures the first is
		 *	ignored.
		 */
		if (!skipPaint) {
			if (!multiScreen) {
				repaint();
				serviceRepaints();
			}
		} else {
			skipPaint = false;
		}
	}
	
	private void mpSetKartChoiceMenu() {
		iconMenu.clearOverlay();
		for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
			int kartIdx = player[n].kartIdx;
			if (kartIdx >=0) {
				iconMenu.setOverlayByValue(kartIdx, (char) (n + MULTIPLAYER_OVERLAY_P1));
			}
		}
	}
	
	/**
	 *	Returns whether all clients are in the same mode.
	 *
	 *	@param mode the client state to check for
	 */
	private boolean mpIsClientMode(int mode) {
		for (int n = 0; n < mpClientSize; n++) {
			if (clientStateBuffer[n][MULTIPLAYER_MODE] != mode) {
				return false;
			}
		}
		return true;
	}
	
	private synchronized void mpDefaultClientLoop() {
		switch (serverState[MULTIPLAYER_MODE]) {
		case MULTIPLAYER_MODE_IDLE:
			break;
		case MULTIPLAYER_MODE_PIDX:
			mpPlayerIdx = serverState[MULTIPLAYER_PIDX];
			clientState[MULTIPLAYER_PIDX] = (byte) mpPlayerIdx;
			if (DEBUG) {
				System.out.println("Player index: " + mpPlayerIdx);
			}
			if (mode != MODE_MULTIPLAYER_CLIENT_CHOOSE_KART) {
				prepMode(MODE_MULTIPLAYER_CLIENT_CHOOSE_KART);
			}
			break;
		case MULTIPLAYER_MODE_KART:
			for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
				player[n].kartIdx = serverState[MULTIPLAYER_DATA + n];
			}
			break;
		case MULTIPLAYER_MODE_WAIT:
			break;
		case MULTIPLAYER_MODE_INIT:
			clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_INIT;
			int netDataPos = MULTIPLAYER_DATA;
			for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
				netDataPos = player[n].loadNetworkPacket(serverState, netDataPos);
			}
			break;
		case MULTIPLAYER_MODE_LOAD:
			clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_LOAD;
			levelNum = serverState[MULTIPLAYER_DATA + 0];
			trackNum = serverState[MULTIPLAYER_DATA + 1];
			if (DEBUG) {
				System.out.println("Network request to load: " + trackNum + "(level: " + levelNum + ")");
			}
			if (mode != MODE_TRACK_NAME && mode != MODE_TRACK_LOAD && mode != MODE_MULTIPLAYER_CLIENT) {
				prepMode(MODE_TRACK_NAME);
			}
			break;
		case MULTIPLAYER_MODE_RACE:
			if (mode == MODE_PAUSE) {
				container.remove(mainMenu);
				mode = raceMode;
			}
			if (mode != MODE_MULTIPLAYER_CLIENT && mode != MODE_STATS_LAPS && mode != MODE_STATS_POSN) {
				prepMode(MODE_MULTIPLAYER_CLIENT);
			} else {
				countdown = serverState[MULTIPLAYER_DATA];
				if (countdown > -FRAMES_PER_SEC * 2) {
					defaultCountdown(true);
				}
				logic.loadNetworkPacket(serverState, MULTIPLAYER_DATA + 1);
				logic.loop();
			}
			break;
		case MULTIPLAYER_MODE_HALT:
			if (mode == MODE_MULTIPLAYER_CLIENT) {
				prepMode(MODE_PAUSE);
			}
			break;
		case MULTIPLAYER_MODE_CONT:
			break;
		case MULTIPLAYER_MODE_QUIT:
			clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_QUIT;
			mpClient.update();
			prepMode(MODE_MAIN_MENU);
			return;
		}
		if (!mpClient.update() && mpClient.getErrorCount() > 5) {
			prepMode(MODE_MULTIPLAYER_ERROR);
		}
	}
	
	private synchronized void mpDefaultServerLoop(int mpMode) {
		for (int n = 0; n < mpClientSize; n++) {
			int data = clientStateBuffer[n][MULTIPLAYER_DATA];
			switch (clientStateBuffer[n][MULTIPLAYER_MODE]) {
			case MULTIPLAYER_MODE_IDLE:
				break;
			case MULTIPLAYER_MODE_PIDX:
				break;
			case MULTIPLAYER_MODE_KART:
				setKartChoice(n + 1, data);
				break;
			case MULTIPLAYER_MODE_WAIT:
				break;
			case MULTIPLAYER_MODE_INIT:
				break;
			case MULTIPLAYER_MODE_LOAD:
				break;
			case MULTIPLAYER_MODE_RACE:
				if (multiScreen) {
					mpJoyState[n + 1] = -1;
				} else {
					mpJoyState[n + 1] = data;
				}
				break;
			case MULTIPLAYER_MODE_HALT:
				switch (mode) {
				case MODE_STATS_LAPS:
					prepMode(MODE_MULTIPLAYER_SERVER_PAUSE_LAPS);
					break;
				case MODE_STATS_POSN:
					prepMode(MODE_MULTIPLAYER_SERVER_PAUSE_POSN);
					break;
				case MODE_PAUSE:
				case MODE_MULTIPLAYER_SERVER_PAUSE_LAPS:
				case MODE_MULTIPLAYER_SERVER_PAUSE_POSN:
					break;
				default:
					prepMode(MODE_PAUSE);
				}
				break;
			case MULTIPLAYER_MODE_CONT:
				switch (mode) {
				case MODE_PAUSE:
					container.remove(mainMenu);
					mode = raceMode;
					break;
				case MODE_MULTIPLAYER_SERVER_PAUSE_LAPS:
					mode = MODE_STATS_LAPS;
					break;
				case MODE_MULTIPLAYER_SERVER_PAUSE_POSN:
					mode = MODE_STATS_POSN;
					break;
				}
				break;
			case MULTIPLAYER_MODE_QUIT:
				mpJoyState[n + 1] = -1;
				break;
			}
		}
		
		switch (mpMode) {
		case MULTIPLAYER_MODE_IDLE:
			break;
		case MULTIPLAYER_MODE_PIDX:
			break;
		case MULTIPLAYER_MODE_KART:
			for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
				serverState[MULTIPLAYER_DATA + n] = (byte) player[n].kartIdx;
			}
			serverState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_KART;
			mpUpdateClients();
			break;
		case MULTIPLAYER_MODE_WAIT:
			break;
		case MULTIPLAYER_MODE_INIT:
			int netDataPos = MULTIPLAYER_DATA;
			for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
				netDataPos = player[n].saveNetworkPacket(serverState, netDataPos);
			}
			serverState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_INIT;
			mpUpdateClients();
			break;
		case MULTIPLAYER_MODE_LOAD:
			serverState[MULTIPLAYER_DATA + 0] = (byte) levelNum;
			serverState[MULTIPLAYER_DATA + 1] = (byte) trackNum;
			serverState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_LOAD;
			mpUpdateClients();
			break;
		case MULTIPLAYER_MODE_RACE:
			mpJoyState[0] = joyPlayer.state;
			
			if (countdown > -FRAMES_PER_SEC) {
				if (defaultCountdown(true)) {
					for (int n = 0; n <= mpClientSize; n++) {
						jumpedGun[n] = mpJoyState[n] != 0;
					}
				}
			}
			
			if (countdown <= 0) {
				for (int n = 0; n <= mpClientSize; n++) {
					defaultRaceInputHandler(n, logic.getStats(RaceCore.STATS_LAPS, n), mpJoyState[n]);
				}
				logic.loop(joyState, true);
			}
			
			serverState[MULTIPLAYER_DATA] = (byte) countdown;
			logic.saveNetworkPacket(serverState, MULTIPLAYER_DATA + 1);
			
			serverState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_RACE;
			mpUpdateClients();
			break;
		case MULTIPLAYER_MODE_HALT:
			serverState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_HALT;
			mpUpdateClients();
			break;
		case MULTIPLAYER_MODE_CONT:
			break;
		case MULTIPLAYER_MODE_QUIT:
			serverState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_QUIT;
			mpUpdateClients();
			break;
		}
	}
	
	private synchronized void mpUpdateClients() {
		if (mpServer.updateAll() > 0) {
			mpErrorCount = 0;
		} else {
			if (++mpErrorCount > MULTIPLAYER_MAX_ERRORS) {
				prepMode(MODE_MULTIPLAYER_ERROR);
			}
		}
	}
	
	private boolean setKartChoice(int playerIdx, int kartIdx) {
		if (kartIsAvailiable(kartIdx)) {
			if (DEBUG) {
				System.out.println("Player " + playerIdx + " picked: " + kartIdx);
			}
			player[playerIdx].kartIdx = kartIdx;
		}
		return player[playerIdx].kartIdx == kartIdx; // for when the player chooses the same kart as before
	}
	
	/**
	 *	Returns true if none of the other players has already selected the
	 *	kart choice.
	 */
	private boolean kartIsAvailiable(int idx) {
		boolean free = true;
		for (int n = 0; n < RaceCore.MAX_KARTS; n++) {
			if (player[n].kartIdx == idx) {
				free = false;
				break;
			}
		}
		return free;
	}
	
	public final void deletePrefs(String key) {
		try {
			RecordStore db = RecordStore.openRecordStore(key, false);
			db.deleteRecord(1);
			db.closeRecordStore();
		} catch (Exception e) {}
	}
	
	public final boolean setPrefs(String key, byte[] data) {
		try {
			RecordStore db = RecordStore.openRecordStore(key, true);
			if (db.getNumRecords() == 0) {
				db.addRecord(data, 0, data.length);
			} else {
				db.setRecord(1, data, 0, data.length);
			}
			db.closeRecordStore();
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	public final byte[] getPrefs(String key, byte[] data) {
		try {
			RecordStore db = RecordStore.openRecordStore(key, false);
			if (data == null) {
				data = new byte[db.getRecordSize(1)];
			}
			db.getRecord(1, data, 0);
			db.closeRecordStore();
		} catch (Exception e) {}
		return data;
	}
	
	public final void start() {
		running = true;
	}
	
	/**
	 *	Used to handle pausing the game. If the player is in one of the race
	 *	modes (practice, tournament, time trial or multiplayer) and is
	 *	actually racing (not viewing results screens, etc.) then the game is
	 *	paused (and displays a menu option to show this). Otherwise the game
	 *	state is unchanged but the music is stopped (none of the other screens
	 *	are considered as game screens, and all wait on player input before
	 *	proceeding).
	 */
	protected void hideNotify() {
		if (!USE_MIDLET_SUSPEND) {
			if ((ENABLE_MUSIC || ENABLE_SNDFX) && sound != null) {
				sound.stop(true);
			}
		}
		switch (mode) {
		case MODE_MULTIPLAYER_CLIENT:
			if (ENABLE_MULTIPLAYER) {
				clientState[MULTIPLAYER_MODE] = MULTIPLAYER_MODE_HALT;
				mpClient.update();
			}
		case MODE_PRACTICE:
		case MODE_TOURNAMENT:
		case MODE_TIME_TRIAL:
		case MODE_MULTIPLAYER_SERVER:
			resetKeys();
			prepMode(MODE_PAUSE);
			break;
		}
	}
	
	protected void showNotify() {
		parent.resumeRequest(); // added to stop P910 freezing
	}
	
	public final void stop() {
		running = false;
		if (DEBUG) {
			System.out.println("Stopping game");
		}
		
		if (ENABLE_MULTIPLAYER && hasBluetooth) {
			mpClient.close();
			mpServer.close();
		}
		
		if (ENABLE_MUSIC && sound != null) {
			sound.stop(true);
		}
		
		if (prefs[PREFS_CHECKSUM] != calculateCRC8(prefs)) {
			prefs[PREFS_CHECKSUM]  = calculateCRC8(prefs);
			setPrefs("prefs", prefs);
			if (DEBUG) {
				System.out.println("Saving prefs");
			}
		}
		
		// TODO: possibly only save this if they change
		setPrefs("trials", ByteUtils.booleansToBytes(new byte[TOTAL_TRACKS], 0, validGhost, TOTAL_TRACKS));
		setPrefs("best",   ByteUtils.shortsToBytes(new byte[TOTAL_TRACKS * 2], 0, best, TOTAL_TRACKS));
		setPrefs("keys",   ByteUtils.multiBytesToBytes(new byte[DIFFICULTY_LEVELS * TOTAL_TOURNAMENTS], 0, totalKeys, DIFFICULTY_LEVELS, TOTAL_TOURNAMENTS));
		setPrefs("finish", ByteUtils.multiBytesToBytes(new byte[DIFFICULTY_LEVELS * TOTAL_TRACKS], 0, finishPos, DIFFICULTY_LEVELS, TOTAL_TRACKS));
	}
	
	public final boolean isRunning() {
		return running;
	}
	
	/**
	 *	Enables the multi-screen debug mode. With this it's possible to run
	 *	multiple games in the same VM on the same screen. Paints are performed
	 *	by the parent container, the border isn't drawn (to enable many games
	 *	on a single canvas) and the driving is automatic in the multiplayer
	 *	modes.
	 */
	static void enableMultiScreen(boolean multiScreen) {
		GameCanvas.multiScreen = multiScreen;
	}
	
	/**
	 *	Ganerated values used for calculating a CRC (for checking the validity
	 *	of stored data).
	 */
	private static final byte[] CRC8 = new byte[256];
	static {
		for (int n = 0; n < 256; n++) {
			CRC8[n] = (byte) (n * n * n + n * n + n + 1);
		}
	}
	
	/**
	 *	Calculates a CRC from the data in an array, assuming the first index
	 *	is where this value is stored.
	 */
	private static byte calculateCRC8(byte[] data) {
		int crc = 0xFF;
		for (int n = data.length - 1; n > 0; n--) {
			crc = (data[n] & 0xFF) ^ (CRC8[crc] & 0xFF);
		}
		return (byte) crc;
	}
	
	/**
	 *	How many seconds for the count into the race.
	 */
	private static final int COUNDOWN_START = 3;
	
	/****************************** Game Modes ******************************/
	
	/**
	 *	Used to signify the button or menu item should have no effect.
	 */
	private static final int MODE_NONE = 0;
	
	/**
	 *	Halts the game and alerts the player of an error.
	 */
	private static final int MODE_ERROR = 1;
	
	/**
	 *	The MIDlet (or application) is just starting up.
	 */
	private static final int MODE_STARTUP = 2;
	
	/**
	 *	Essential parts of the game, such as the fonts and menus, are
	 *	being loaded and initialised.
	 */
	private static final int MODE_INITIALISE = 3;
	
	/**
	 *	Core game components are loading. This may take some time.
	 */
	private static final int MODE_LOADING = 4;
	
	/**
	 *	Displaying the company logo. This stays on screen for a specified
	 *	amount of time.
	 *
	 *	@see #LOGO_SHOWING_MS
	 */
	private static final int MODE_LOGO_PAUSE = 5;
	
	private static final int MODE_MAIN_MENU = 6;
	private static final int MODE_CHOOSE_GAME = 7;
	private static final int MODE_CHOOSE_KART = 8;
	private static final int MODE_CHOOSE_COURSE = 9;
	private static final int MODE_CHOOSE_TRACK = 10;
	private static final int MODE_OPTIONS = 11;
	private static final int MODE_INSTRUCTIONS = 12;
	private static final int MODE_ABOUT = 13;
	private static final int MODE_QUIT = 14;
	
	private static final int MODE_OPTIONS_DIFFICULTY = 15;
	private static final int MODE_OPTIONS_SOUND = 16;
	private static final int MODE_OPTIONS_VIBRATE = 17;
	private static final int MODE_OPTIONS_LANGUAGE = 18;
	private static final int MODE_OPTIONS_ERASE_RECORDS = 19;
	private static final int MODE_OPTIONS_ADVANCED = 20;
	
	private static final int MODE_ADVANCED_RENDERER = 21;
	private static final int MODE_ADVANCED_BACKGROUND = 22;
	private static final int MODE_ADVANCED_BORDER = 23;
	private static final int MODE_ADVANCED_CLIP = 24;
	private static final int MODE_ADVANCED_DEFAULTS = 25;
	
	private static final int MODE_ADVANCED_MESSAGE = 26;
	
	private static final int MODE_PRACTICE = 27;
	private static final int MODE_TOURNAMENT = 28;
	private static final int MODE_TIME_TRIAL = 29;
	
	private static final int MODE_PAUSE = 30;
	
	private static final int MODE_TRACK_NAME = 31;
	private static final int MODE_TRACK_LOAD = 32;
	
	private static final int MODE_STATS_LAPS = 33;
	private static final int MODE_STATS_POSN = 34;
	private static final int MODE_STATS_POINTS = 35;
	private static final int MODE_STATS_TOTALS = 36;
	private static final int MODE_STATS_RESULTS = 37;
	
	private static final int MODE_MULTIPLAYER = 38;
	private static final int MODE_MULTIPLAYER_CLIENT = 39;
	private static final int MODE_MULTIPLAYER_SERVER = 40;
	
	private static final int MODE_MULTIPLAYER_CLIENT_DISCOVERY = 41;
	private static final int MODE_MULTIPLAYER_SERVER_DISCOVERY = 42;
	
	private static final int MODE_MULTIPLAYER_CLIENT_CHOOSE_KART = 43;
	private static final int MODE_MULTIPLAYER_SERVER_CHOOSE_KART = 44;
	
	private static final int MODE_MULTIPLAYER_CLIENT_CONFIRM_KART = 45;
	
	private static final int MODE_MULTIPLAYER_ERROR = 46;
	private static final int MODE_MULTIPLAYER_TIMEOUT = 47;
	
	private static final int MODE_MULTIPLAYER_HELP = 48;
	
	private static final int MODE_MULTIPLAYER_CLIENT_WAIT = 49;
	private static final int MODE_MULTIPLAYER_SERVER_WAIT = 50;
	
	private static final int MODE_MULTIPLAYER_SERVER_PAUSE_LAPS = 51;
	private static final int MODE_MULTIPLAYER_SERVER_PAUSE_POSN = 52;
	
	private static final int MODE_MULTIPLAYER_SERVER_QUIT = 53;
	
	private static final int MODE_RESUME_RACE = 54;
	
	/************************************************************************/
	
	private static final int MAIN_MENU_PLAY = 0;
	private static final int MAIN_MENU_OPTIONS = 1;
	private static final int MAIN_MENU_INSTRUCTIONS = 2;
	private static final int MAIN_MENU_ABOUT = 3;
	private static final int MAIN_MENU_QUIT = 4;
	
	
	private static final int RESUME_CONTINUE = 0;
	private static final int RESUME_NEW_GAME = 1;
	
	private static final int GAME_MENU_PRACTICE = 0;
	private static final int GAME_MENU_TOURNAMENT = 1;
	private static final int GAME_MENU_TIME_TRIAL = 2;
	private static final int GAME_MENU_MULTIPLAYER = 3;
	
	private static final int OPTIONS_DIFFICULTY = 0;
	private static final int OPTIONS_SOUND = 1;
	private static final int OPTIONS_VIBRATE = 2;
	private static final int OPTIONS_LANGUAGE = 3;
	private static final int OPTIONS_ERASE_RECORDS = 4;
	private static final int OPTIONS_ADVANCED = 5;
	private static final int OPTIONS_SOAK = 6;
	
	private static final int DIFFICULTY_EASY = 0;
	private static final int DIFFICULTY_MEDIUM = 1;
	private static final int DIFFICULTY_HARD = 2;
	
	private static final int SETTINGS_OFF = 0;
	private static final int SETTINGS_ON = 1;
	
	private static final int SOUND_MUSIC = 1;
	private static final int SOUND_EFFECTS = 2;
	private static final int SOUND_ON = 3;
	
	private static final int ADVANCED_RENDERER = 0;
	private static final int ADVANCED_BACKGROUND = 1;
	private static final int ADVANCED_BORDER = 2;
	private static final int ADVANCED_CLIP = 3;
	private static final int ADVANCED_DEFAULTS = 4;
	
	private static final int RENDERER_TOPDOWN = 0;
	private static final int RENDERER_MODE7_SMOOTH = 1;
	private static final int RENDERER_MODE7_CHUNKY = 2;
	private static final int RENDERER_M3G_11 = 3;
	private static final int RENDERER_M3G_12 = 4;
	private static final int RENDERER_M3G_14 = 5;
	private static final int RENDERER_M3G_18 = 6;
	
	private static final int PAUSE_RESUME = 0;
	//private static final int PAUSE_SAVE = 1; // Load and save is now automatic
	//private static final int PAUSE_LOAD = 2;
	private static final int PAUSE_MAIN_MENU = 3;
	
	private static final int MULTIPLAYER_CLIENT = 0;
	private static final int MULTIPLAYER_SERVER = 1;
	private static final int MULTIPLAYER_HELP = 2;
	
	/************************************************************************/
	
	private static final int PREFS_CHECKSUM = 0;
	private static final int PREFS_DIFFICULTY = 1;
	private static final int PREFS_SOUND = 2;
	private static final int PREFS_VIBRATE = 3;
	private static final int PREFS_LANGUAGE = 4;
	private static final int PREFS_RENDERER = 5;
	private static final int PREFS_BACKGROUND = 6;
	private static final int PREFS_BORDER = 7;
	private static final int PREFS_CLIP = 8;
	
	private static final int PREFS_STORAGE_SIZE = 9;
	
	/************************************************************************/
	
	private static final int MULTIPLAYER_MAX_ERRORS = 5;
	
	private static final int MULTIPLAYER_MODE_IDLE = 0;
	private static final int MULTIPLAYER_MODE_PIDX = 1;
	private static final int MULTIPLAYER_MODE_KART = 2;
	private static final int MULTIPLAYER_MODE_WAIT = 3;
	private static final int MULTIPLAYER_MODE_INIT = 4;
	private static final int MULTIPLAYER_MODE_LOAD = 5;
	private static final int MULTIPLAYER_MODE_RACE = 6;
	private static final int MULTIPLAYER_MODE_HALT = 7;
	private static final int MULTIPLAYER_MODE_CONT = 8;
	private static final int MULTIPLAYER_MODE_QUIT = 9;
	
	private static final int MULTIPLAYER_MODE = 0;
	private static final int MULTIPLAYER_ACTV = 1;
	private static final int MULTIPLAYER_PIDX = 2;
	private static final int MULTIPLAYER_DATA = 4;
	
	/************************************************************************/
	
	/**
	 *	Minimum time in milliseconds the company logo is displayed.
	 *
	 *	@see #logoShowTime
	 */
	private static final int LOGO_SHOWING_MS = 1500;
	
	private static final int MAX_PASSING_CARS = 4;
	private static final int MAX_LINES = 8;
	private static final int NUM_STARS = 3;
	
	/**
	 *	Number of characters used when displaying the race results.
	 */
	private static final int RESULTS_LINE_LENGTH = 15;
	
	/**
	 *	Char used as the rotating star in text labels.
	 */
	private static final char ROTATING_STAR = '\u00BC';
	
	private static final char BUTTON_TICK = '\u2406';
	private static final char BUTTON_BACK = '\u2415';
	private static final char BUTTON_BACK_SMALL = '\u2416';
	private static final char BUTTON_OKAY = '\u2400';
	
	/**
	 *	Char used to represent the first kart in the icon font. The icon/char
	 *	is used on the picker screen and any other place where a full-sized
	 *	(probably 32x32) kart needs drawing.
	 */
	private static final char FIRST_KART_ICON = 'K';
	
	/**
	 *	Char used to represent any kart that is not yet selectable.
	 */
	private static final char HIDDEN_KART_ICON = '*';
	
	/**
	 *	Char in the text font of the first character head, used when drawing
	 *	the race results.
	 */
	private static final char FIRST_MINI_HEAD_ICON = '\uFF21';
	
	private static final char RESULTS_FIRST_POSITION  = '\u2081';
	
	private static final char RESULTS_GOLD_CUP   = '\u00B9';
	private static final char RESULTS_SILVER_CUP = '\u00B2';
	private static final char RESULTS_BRONZE_CUP = '\u00B3';
	private static final char RESULTS_SPACER_CUP = '\u2003';
	
	/**
	 *	Unicode value for '0' in the time display. The next nine digits are
	 *	'1' to '9', followed by ':', and the character before '0' is '/',
	 *	used when displaying the laps.
	 */
	private static final char TIMER_ZERO = '\uFF10';
	
	/**
	 *	Space character with the same width as the monospaced timer digits.
	 */
	private static final char MONO_SPACE = '\u2002';
	
	/**
	 *	Space character with the same width as the timer digit colon.
	 */
	private static final char COLON_SPACE = '\u2009';
	
	/**
	 *	Char in the icon font for the coundown digit zero.
	 */
	private static final char COUNTDOWN_ZERO = '0';
	
	/**
	 *	English default icon char for the word 'best'.
	 */
	private static final char BEST_TEXT = ':';
	
	/**
	 *	Unicode value for '1' in the stats display.
	 *
	 *	@see #TIMER_ZERO
	 */
	private static final char STATS_NUMBER_ONE = '\uFF2E';
	
	/**
	 *	English default 'L' character to draw before the numbers when showing
	 *	laps, e.g.: L1. Localised versions might use a different character.
	 */
	private static final char STATS_LAPS_ICON = '\uFF2C';
	
	/**
	 *	English default character to draw representing a total.
	 */
	private static final char STATS_TOTAL_ICON = '\uFF1D';
	
	/**
	 *	'P' character to draw when showing the player index, e.g.: P1.
	 *
	 *	Note: this isn't currently used.
	 */
	private static final char STATS_PLAYER_ICON = '\uFF2B';
	
	/**
	 *	Monospaced plus sign for showing points awarded.
	 */
	private static final char PLUS_SIGN = '\uFF0B';
	
	/**
	 *	Icon in the text font to show that a track has a ghost recording.
	 */
	private static final char GHOST_ICON = '\uFF4C';
	
	/**
	 *	Char in the icon font representing a big numeral '1'. The next eight
	 *	digits are '2' to '9', and the icon before '1' is for 'go'.
	 */
	private static final char POSITION_ONE = '1';
	
	/**
	 *	English default char in the icon font used to draw 'st' over the large
	 *	numbers. The next three icons are 'nd', 'rd' and 'th'.
	 */
	private static final char POSITION_ORD = '\u2089';
	
	/**
	 *	Char in the text font representing the collectable items on the track.
	 */
	private static final char KEY_SYMBOL = '\uFF4B';
	
	/**
	 *	Char in the text font representing phones on the Bluetooth network.
	 */
	private static final char PHONE_SYMBOL = '\uFF37';
	
	/**
	 *	Char in the icon font to show the game/phone is busy.
	 */
	private static final char HOURGLASS_SYMBOL = '\u005B';
	
	/**
	 *	Char in the icon font of P1's kart picker overlay. P2-P8 follow.
	 */
	private static final char MULTIPLAYER_OVERLAY_P1  = '\u2081';
	
	/************************************************************************/
	
	/**
	 *	Icon representing the general drivability of the kart.
	 */
	private static final char STATS_ICON_0 = '\u2401';
	
	/**
	 *	Icon representing the weight of the kart.
	 */
	private static final char STATS_ICON_1 = '\u2402';
	
	/**
	 *	Icon representing the speed of the kart.
	 */
	private static final char STATS_ICON_2 = '\u2403';
	
	/**
	 *	Icon for stars awarded to the kart stats.
	 */
	private static final char STATS_STAR   = '\u2404';
	
	/**
	 *	Maximum number of stars awarded to each kart property.
	 */
	private static final char STATS_MAX_STARS = 5;
	
	/**
	 *	The stats are generated from these default arrays by drawing a shorter
	 *	line than the maximum length.
	 *
	 *	@see Line#set(char[] text, int offset, int length)
	 */
	private static final char[] STATS_0 = new char[STATS_MAX_STARS + 2];
	
	/**
	 *	@see STATS_0
	 */
	private static final char[] STATS_1 = new char[STATS_MAX_STARS + 2];
	
	/**
	 *	@see STATS_0
	 */
	private static final char[] STATS_2 = new char[STATS_MAX_STARS + 2];
	static {
		STATS_0[0] = STATS_ICON_0;
		STATS_1[0] = STATS_ICON_1;
		STATS_2[0] = STATS_ICON_2;
		STATS_0[1] = STATS_1[1] = STATS_2[1] = ' ';
		for (int n = 0; n < STATS_MAX_STARS; n++) {
			STATS_0[n + 2] = STATS_1[n + 2] = STATS_2[n + 2] = STATS_STAR;
		}
	}
	
	/************************************************************************/
	
	private static final int FIRST_LEVEL_NAME_IDX = 29;
	private static final int FIRST_LEVEL_PREV_IDX = 34;
	private static final int FIRST_TRACK_NAME_IDX = 37;
	private static final int FIRST_DIFFICULTY_IDX = 54;
	
	/**
	 *	Index in the string manager of the first character name.
	 */
	private static final char FIRST_CHAR_NAME_IDX = 17;
	
	/**
	 *	Index in the string manager of the text used for locked items.
	 */
	private static final char QUESTION_MARKS_IDX = 27;
	
	/************************************************************************/
	
	/**
	 *	Default length of time to show an info screen.
	 */
	private static final int DEFAULT_SCREEN_TIME = FRAMES_PER_SEC * 5;
	
	/**
	 *	Default kart/player index used by the player on this phone.
	 */
	private static final int PLAYER_IDX = 0;
	
	/**
	 *	Kart/player index used by the recorded joystick/ghost.
	 */
	private static final int RECORD_IDX = 1;
	
	/**
	 *	Bytes required for a multiplayer client state buffer.
	 */
	private final static int MULTIPLAYER_CLIENT_STATE_SIZE =  5;
	
	/**
	 *	Bytes required for a multiplayer server state buffer.
	 */
	private final static int MULTIPLAYER_SERVER_STATE_SIZE = 78;
	
	/**
	 *	How many game ticks to run each part of the soak test.
	 *
	 *	@see #soakTest
	 */
	private static final int SOAK_TIME = 384;
	
	/**
	 *	How many seconds of ghost data to record.
	 */
	public static final int GHOST_SECONDS = FRAMES_PER_MIN * RaceCore.MAX_LAPS;
	
	/*
	 *	Indices for for individual chars in the overlay (HUD and results)
	 *	localisation string.
	 */
	private static final int LOCALISATION_TIME = 0;
	private static final int LOCALISATION_BEST = 1;
	private static final int LOCALISATION_ORDINAL = 2;
	private static final int LOCALISATION_TOTAL = 3;
	private static final int LOCALISATION_LAP = 4;
}