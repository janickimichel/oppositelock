package numfum.j2me.jsr;

import numfum.j2me.jsr.generic.bkgnd.BufferedCompositeTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.BufferedTiledLayer;
import numfum.j2me.jsr.generic.bkgnd.ShuffledTiledOverlay;

public interface Constants {
	/**
	 *	Build only a demo version of the game.
	 */
	public static final boolean DEMO_MODE = false;
	
	/**
	 *	Total number of tournaments in the game. The final tournament will
	 *	always consist of one single race.
	 */
	public static final int TOTAL_TOURNAMENTS = 4;
	
	/**
	 *	Number of tracks in each tournament level (except the last).
	 */
	public static final int TRACKS_PER_LEVEL = 3;
	
	/**
	 *	Total number of tracks in the game.
	 */
	public static final int TOTAL_TRACKS = (TOTAL_TOURNAMENTS - 1) * TRACKS_PER_LEVEL + 1;
	
	/**
	 *	Number of laps per race.
	 */
	public static final int LAPS_PER_RACE = 3;
	
	/**
	 *	Number of difficulty levels in the game.
	 */
	public static final int DIFFICULTY_LEVELS = 3;
	
	/**
	 *	Build the game with multiplayer capabilities.
	 */
	public static final boolean ENABLE_MULTIPLAYER = true;
	
	/************************************************************************/
	
	/**
	 *	Dump text of the game's running status to stdout.
	 */
	public static final boolean DEBUG = true;
	
	/**
	 *	Whether the advanced menu is enabled by default (doesn't unlock the
	 *	soak test option).
	 */
	public static final boolean ENABLE_ADVANCED_MENU = true;
	
	/**
	 *	Start the game directly at the race screen rather than the menu.
	 */
	public static final boolean QUICK_START = false;
	
	/************************************************************************/
	
	/**
	 *	Build the graphics routines with extra code to restore the clipping
	 *	region. MIDP1.0 doesn't have Graphics.drawRegion() so drawing a
	 *	partial image needs to clip the Graphics context, which, depending on
	 *	what is next in the draw order, needs restoring.
	 */
	public static final boolean ENABLE_RESTORE_CLIP = false;
	
	/**
	 *	Delay calling <code>setFullScreenMode()</code> until after the
	 *	canvas has been initialised (works around a bug in the P9xx series).
	 */
	public static final boolean DELAY_FULLSCREEN_SETTING = false;
	
	/**
	 *	Build with support for pointer or mouse presses.
	 */
	public static final boolean ENABLE_POINTER = true;
	
	/**
	 *	Use the phone's own suspend feature instead of pausing the game. The
	 *	Aplix KVM in Motorola (and other  handsets) benefits from this. Note:
	 *	this differs from ENABLE_INTERRUPTED_MENU as it enabled throughout the
	 *	entire game.
	 */
	public static final boolean USE_MIDLET_SUSPEND = false;
	
	/**
	 *	If no render defaults are found in the manifest or jad properties
	 *	then this becomes the default render settings. Some phones (notably
	 *	Samsung) don't pick up the manifest entry so this is then used.
	 */
	public static final String FALLBACK_RENDER_DEFAULTS = "1000";
	
	/************************************************************************/
	
	/**
	 *	Build the game with the Mode-7 style renderer.
	 */
	public static final boolean ENABLE_RENDERER_MODE7 = true;
	
	/**
	 *	Build the game with the top-down 2D renderer.
	 */
	public static final boolean ENABLE_RENDERER_TOPDOWN = true;
	
	/**
	 *	Build the game with a JSR-184 renderer.
	 */
	public static final boolean ENABLE_RENDERER_M3G = true;
	
	/************************************************************************/
	
	/**
	 *	Build the game with the 'buffered' background renderer.
	 *
	 *	@see BufferedTiledLayer
	 */
	public static final boolean ENABLE_BKGND_BUFFERED = true;
	
	/**
	 *	Instead of testing for a good single buffer background implementation,
	 *	force a double buffer to be used. This shouldn't need resorting to but
	 *	it might not always be possible to detect all copyArea() bugs. Only
	 *	applicable to MIDP2.0.
	 *
	 *	@see BufferedTiledLayer
	 */
	public static final boolean FORCE_DOUBLE_BUFFER = false;
	
	/**
	 *	Build the game with the 'composite' background renderer. The composite
	 *	renderer composes multiple tilemaps together on one buffer.
	 *
	 *	@see BufferedCompositeTiledLayer
	 */
	public static final boolean ENABLE_BKGND_COMPOSITE = true;
	
	/**
	 *	Build the game with the 'shuffled' background renderer. The shuffled
	 *	renderer uses a <code>TiledOverlay</code> the size of the screen but
	 *	draws into it per frame the visible tiles from a larger tilemap.
	 *
	 *	@see ShuffledTiledOverlay
	 */
	public static final boolean ENABLE_BKGND_SHUFFLED = true;
	
	/************************************************************************/
	
	/**
	 *	Build the game with music playback.
	 */
	public static final boolean ENABLE_MUSIC = true;
	
	/**
	 *	Build the game with sound effects.
	 */
	public static final boolean ENABLE_SNDFX = true;
	
	/**
	 *	Whether the sound effects are independent of the music playback, for
	 *	example MIDI effects aren't but PCM effects are.
	 */
	public static final boolean SNDFX_ARE_INDEPENDENT = false;
	
	/**
	 *	The beeps and such in the front-end menus.
	 */
	public static final boolean ENABLE_INTERFACE_FX = false;
	
	/**
	 *	Specific option to disable the in-game music. Should be very rarely
	 *	needed (Samsung phones only, possibly).
	 */
	public static final boolean DISABLE_IN_GAME_MUSIC = false;
	
	/************************************************************************/
	
	public static final int FRAME_DELAY = 62;
	public static final int FRAMES_PER_SEC = 1000 / FRAME_DELAY;
	public static final int FRAMES_PER_MIN = FRAMES_PER_SEC * 60;
	
	public static final int BACKGROUND_BUFFERED = 0;
	public static final int BACKGROUND_COMPOSITE = 1;
	public static final int BACKGROUND_DIRECT = 2;
	public static final int BACKGROUND_SHUFFLED = 3;
}