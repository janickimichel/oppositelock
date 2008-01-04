package numfum.j2me.jsr;

import javax.microedition.lcdui.Graphics;

import numfum.j2me.jsr.generic.PositionableContainer;
import numfum.j2me.text.BitmapFont;
import numfum.j2me.text.Line;
import numfum.j2me.util.Vector2D;

/**
 *	Implementation of <code>RaceChrome</code> which displays the stats on a
 *	heads-up type display.
 */
public final class RaceChromeImpl extends PositionableContainer implements Constants, RaceChrome {
	private final int[] timerMsTens = new int[FRAMES_PER_SEC];
	private final int[] timerMsHuns = new int[FRAMES_PER_SEC];
	
	/**
	 *	Indices of the timer digits from '0' to '9'.
	 */
	private final int[] timerGlyph = new int[10];
	
	private final int[] timerGlyphMsTens = new int[FRAMES_PER_SEC];
	private final int[] timerGlyphMsHuns = new int[FRAMES_PER_SEC];
	
	/**
	 *	Chars for a time of "00:00:00".
	 */
	private final char[] zeroedTime = new char[8];
	
	private final int[] playerTimeStore = new int[3];
	private final int[] playerBestStore = new int[3];
	
	private final Line timeTextLine;
	private final Line bestTextLine;
	
	private final Line playerTimeLine;
	private final Line playerBestLine;
	
	private final int[] playerTime = new int[8];
	private final int[] playerBest = new int[8];
	
	private final Line playerPosnLine;
	private final int iconPosnOneIdx;
	private int iconPosnOrdIdx;
	private final int[] playerPosn = new int[2];
	
	private final Line playerLapsLine;
	private final int[] playerLaps = new int[3];
	
	private final Line playerPickLine;
	private final int[] playerPick = new int[2];
	
	private final Line playerPowrLine;
	private final int[] playerPowr = new int[4];
	
	private final int[] zeroedPowr = new int[4];
	
	private final int posnIndex;
	
	private final int lapsIndex;
	private final int pickIndex;
	private final int powrIndex;
	
	private int pickTimer = 0;
	
	private int lastBest = 0;
	private int lastPosn = 0;
	private int lastLaps = 0;
	private int lastPick = 0;
	
	private final Line playerTMapLine;
	
	private int active = ACTIVE_NONE;
	
	private int maxLaps = 1;
	
	private int lapsDelayLap = 0;
	private int lapsDelay = 0;
	
	private int playerIdx = 0;
	
	private Vector2D[] kartPos;
	private int numKarts = 0;
	
	private final int[] drawKartPosX = new int[RaceCore.MAX_KARTS];
	private final int[] drawKartPosY = new int[RaceCore.MAX_KARTS];
	
	/**
	 *	Creates the race chrome with the specified fonts.
	 */
	public RaceChromeImpl(BitmapFont textFont, BitmapFont iconFont, int viewW, int viewH) {
		super(viewW, viewH, 16);
		
		/*
		 *	Generate the milliseconds digits based on the frame rate.
		 */
		for (int n = 0; n < FRAMES_PER_SEC; n++) {
			int ms = (100 * 100 / FRAMES_PER_SEC * n + 50) / 100;
			timerMsTens[n] = ms % 10;
			timerMsHuns[n] = ms / 10;
		}
		
		for (int n = 0; n < 10; n++) {
			timerGlyph[n] = textFont.getIndex((char) (TIMER_ZERO + n));
		}
		for (int n = 0; n < FRAMES_PER_SEC; n++) {
			timerGlyphMsTens[n] = timerGlyph[timerMsTens[n]];
			timerGlyphMsHuns[n] = timerGlyph[timerMsHuns[n]];
		}
		zeroTimeLine(zeroedTime, 0, TIMER_ZERO);
		
		
		timeTextLine = new Line(iconFont, 1);
		timeTextLine.set(TIME_TEXT).setPosition(3, 31, Graphics.BASELINE | Graphics.LEFT);
		bestTextLine = new Line(iconFont, 1);
		bestTextLine.set(BEST_TEXT).setPosition(3, 11, Graphics.BASELINE | Graphics.LEFT);
		
		playerTimeLine = new Line(textFont, 8);
		playerBestLine = new Line(textFont, 8);
		playerTimeLine.set(zeroedTime);
		playerBestLine.set(zeroedTime);
		playerTimeLine.getGlyphs(playerTime, 0, 8);
		playerBestLine.getGlyphs(playerBest, 0, 8);
		
		playerPosnLine = Line.createLine(iconFont, PLAYER_POSITION_DEFAULT);
		iconPosnOneIdx = iconFont.getIndex(POSITION_ONE);
		iconPosnOrdIdx = iconFont.getIndex(POSITION_ORD);
		
		char[] zeroedLaps = new char[3];
		zeroedLaps[0] = TIMER_ZERO;
		zeroedLaps[1] = (char) (TIMER_ZERO - 1);
		zeroedLaps[2] = TIMER_ZERO;
		
		playerLapsLine = new Line(textFont, 3);
		playerLapsLine.set(zeroedLaps);
		playerLapsLine.getGlyphs(playerLaps, 0, 3);
		
		char[] zeroedPick = new char[2];
		zeroedPick[0] = PICK_SYMBOL;
		zeroedPick[1] = TIMER_ZERO;
		
		playerPickLine = new Line(textFont, 2);
		playerPickLine.set(zeroedPick);
		playerPickLine.getGlyphs(playerPick, 0, 2);
		
		playerPowrLine = Line.createLine(textFont, FRUIT_MACHINE_DEFAULT);
		playerPowrLine.getGlyphs(playerPowr, 0, 4);
		playerPowrLine.getGlyphs(zeroedPowr, 0, 4);
		
		playerTMapLine = new Line(iconFont, 1);
		playerTMapLine.set(FIRST_MAP_ICON);
		
		add(bestTextLine);
		add(playerBestLine.set(zeroedTime).setPosition(3, 21, Graphics.BASELINE | Graphics.LEFT));
		add(timeTextLine);
		add(playerTimeLine.set(zeroedTime).setPosition(3, 41, Graphics.BASELINE | Graphics.LEFT));
		posnIndex = add(playerPosnLine.setPosition(viewW - (32 + 3), 32 + 3, Graphics.BASELINE | Graphics.LEFT));
		lapsIndex = add(playerLapsLine.setPosition(viewW - 3, viewH - 3, Graphics.BASELINE | Graphics.RIGHT));
		pickIndex = add(playerPickLine.setPosition(viewW - 3, viewH - 3, Graphics.BASELINE | Graphics.RIGHT));
		powrIndex = add(playerPowrLine.setPosition(viewW - 3, viewH - 3, Graphics.BOTTOM | Graphics.RIGHT));
		add(playerTMapLine.setPosition(3, viewH - 3, Graphics.BOTTOM | Graphics.LEFT));
		
		reset();
	}
	
	/**
	 *	Resets all of the display items to zero.
	 */
	public void reset() {
		lapsDelay = 0;
		lapsDelayLap = 0;
		setStats(0, 0, 1, 0, RaceCore.POWERUP_STATE_READY, 0, 0);
		setBest(0);
		pickTimer = 0;
		setActivePosn(false);
		setActive(ACTIVE_LAPS);
	}
	
	/**
	 *	Allows for localising of the HUD.
	 */
	public void localise(char timeChar, char bestChar, char ordinalChar) {
		timeTextLine.set(timeChar);
		bestTextLine.set(bestChar);
		iconPosnOrdIdx = playerPosnLine.getFont().getIndex(ordinalChar);
	}
	
	/**
	 *	Sets the displayed best time.
	 */
	public void setBest(int best) {
		if (lastBest != best) {
			setTimeLine(playerBest, playerBestStore, 0, best);
			playerBestLine.setGlyphs(playerBest, 0, 8);
			lastBest = best;
		}
	}
	
	/**
	 *	Sets the current race time.
	 *
	 *	@param text
	 */
	public void setTimeLine(int[] text, int[] store, int offset, int ticks) {
		if (ticks < 0) {
			ticks = 0;
		}
		int huns = ticks % FRAMES_PER_SEC;
		int secs = ticks / FRAMES_PER_SEC;
		int mins = ticks / FRAMES_PER_MIN;
		if (store[0] != huns) {
			store[0]  = huns;
			text[offset + 7] = timerGlyphMsTens[huns];
			text[offset + 6] = timerGlyphMsHuns[huns];
		}
		if (store[1] != secs) {
			store[1]  = secs;
			text[offset + 4] = timerGlyph[secs % 10];
			secs /= 10;
			text[offset + 3] = timerGlyph[secs %  6];
		}
		if (store[2] != mins) {
			store[2]  = mins;
			text[offset + 1] = timerGlyph[mins % 10];
			mins /= 10;
			text[offset + 0] = timerGlyph[mins %  6];
		}
	}
	
	/**
	 *	Sets all of the race stats in one call.
	 *	
	 *	@param time current race time
	 *	@param posn player's race position
	 *	@param pick number of pick-ups collected
	 *	@param powrState power-up state
	 *	@param powrPayout payout on the power-up reels
	 *	@param powrCharIdx which player icon was the payout recipient
	 */
	public void setStats(int time, int posn, int laps, int pick, int powrState, int powrPayout, int powrCharIdx) {
		if (lapsDelayLap != laps) {
			if (lapsDelayLap > 0) {
				lapsDelay = FRAMES_PER_SEC * 2;
			}
			lapsDelayLap  = laps;
		}
		if (lapsDelay == 0) {
			setTimeLine(playerTime, playerTimeStore, 0, time);
			playerTimeLine.setGlyphs(playerTime, 0, 8);
		} else {
			lapsDelay--;
		}
		
		if (lastPosn != posn) {
			lastPosn  = posn;
			playerPosn[0] = iconPosnOneIdx + posn;
			if (posn < 3) {
				playerPosn[1] = iconPosnOrdIdx + posn;
			} else {
				playerPosn[1] = iconPosnOrdIdx + 3;
			}
			playerPosnLine.setGlyphs(playerPosn, 0, 2);
		}
		
		switch (powrState) {
		case RaceCore.POWERUP_STATE_READY:
			if (lastLaps != laps) {
				lastLaps  = laps;
				if (laps <= maxLaps) {
					playerLaps[0] = timerGlyph[laps];
					playerLapsLine.setGlyphs(playerLaps, 0, 1);
				}
			}
			if (lastPick != pick) {
				lastPick  = pick;
				if (pick < 10) {
					playerPick[1] = timerGlyph[pick];
					playerPickLine.setGlyphs(playerPick, 0, 2);
				}
				pickTimer = FRAMES_PER_SEC * 2;
				setActive(ACTIVE_PICK);
			}
			if (pickTimer > 0) {
				pickTimer--;
				/*
				 *	It's possible that pickTimer hadn't reached zero before
				 *	being interrupted by the fruit machine, in which case we
				 *	need to reset the counter and ensure the pickup count is
				 *	showing.
				 */
				if (active != ACTIVE_PICK) {
					pickTimer = FRAMES_PER_SEC * 2;
					setActive(ACTIVE_PICK);
				}
				if (pickTimer < FRAMES_PER_SEC / 2) {
					setActive(pickIndex, (pickTimer & 1) == 1);
				}
			} else if (active != ACTIVE_LAPS) {
				setActive(ACTIVE_LAPS);
			}
			break;
		case RaceCore.POWERUP_SEGMENT_DELAY * RaceCore.POWERUP_SEGMENTS:
			for (int n = 1; n < 4; n++) {
				playerPowr[n] = zeroedPowr[n];
			}
			playerPowr[0] = zeroedPowr[0] + powrCharIdx;
			break;
		default:
			int reel = RaceCore.POWERUP_SEGMENTS - 1 - powrState / RaceCore.POWERUP_SEGMENT_DELAY;
			int tick = ((powrState & 1) == 0 ? 0 : 1);
			switch (reel) {
			case 0:
				playerPowr[1] = zeroedPowr[1] + tick;
			case 1:
				playerPowr[2] = zeroedPowr[2] + tick;
			case 2:
				playerPowr[3] = zeroedPowr[3] + tick;
			case 3:
				break;
			case 4:
				setActive(powrIndex, tick == 0);
			}
			if (powrState % RaceCore.POWERUP_SEGMENT_DELAY == 0) {
				switch (reel) {
				case 0:
					playerPowr[1] = zeroedPowr[1] +  (powrPayout %  RaceCore.TOTAL_POWERUPS) + 2;
					break;
				case 1:
					playerPowr[2] = zeroedPowr[2] + ((powrPayout /  RaceCore.TOTAL_POWERUPS) % RaceCore.TOTAL_POWERUPS) + 2;
					break;
				case 2:
					playerPowr[3] = zeroedPowr[3] + ((powrPayout / (RaceCore.TOTAL_POWERUPS  * RaceCore.TOTAL_POWERUPS)) % RaceCore.TOTAL_POWERUPS) + 2;
					break;
				}
			}
			playerPowrLine.setGlyphs(playerPowr, 0, 4);
			if (active != ACTIVE_POWR) {
				setActive(ACTIVE_POWR);
			}
		}
	}
	
	/**
	 *	Initialises the HUD for a new race.
	 */
	public void init(int maxLaps, int mapIdx, int playerIdx) {
		playerLaps[2] = timerGlyph[maxLaps];
		playerLapsLine.setGlyphs(playerLaps, 0, 3);
		this.maxLaps  = maxLaps;
		playerTMapLine.set((char) (FIRST_MAP_ICON + mapIdx));
		this.playerIdx = playerIdx;
	}
	
	/**
	 *	Sets which objects are tracked for the mini-map kart positions.
	 */
	public void setMapBlips(Vector2D[] kartPos, int numKarts) {
		this.kartPos  = kartPos;
		this.numKarts = numKarts;
	}
	
	/**
	 *	Sets whether the race position is showing or not.
	 */
	public void setActivePosn(boolean active) {
		setActive(posnIndex, active);
	}
	
	/**
	 *	Sets which one of the HUD items takes precidence. The lap counter,
	 *	pick-up item and power-ups all fight for which gets displayed in the
	 *	same area of screen.
	 */
	private void setActive(int active) {
		setActive(lapsIndex, active == ACTIVE_LAPS);
		setActive(pickIndex, active == ACTIVE_PICK);
		setActive(powrIndex, active == ACTIVE_POWR);
		this.active = active;
		if (DEBUG) {
			switch (active) {
			case ACTIVE_NONE:
				System.out.println("Showing NONE");
				break;
			case ACTIVE_LAPS:
				System.out.println("Showing LAPS");
				break;
			case ACTIVE_PICK:
				System.out.println("Showing PICK");
				break;
			case ACTIVE_POWR:
				System.out.println("Showing POWR");
				break;
			default:
				System.out.println("Showing ????");
			}
		}
	}
	
	public void paint(Graphics g, int offsetX, int offsetY) {
		super.paint(g, offsetX, offsetY);
		
		/*
		 *	The offset to draw the map blips from is calculated from the
		 *	overall size (3px), the actual blip size (1px) and the maximum
		 *	map size (32px).
		 */
		offsetX += x + (3 - 1);
		offsetY += y + (h - 3 - 32 - 1);
		
		g.setColor(BLIP_OUTLINE);
		for (int n = 0; n < numKarts; n++) {
			g.fillRect(
				drawKartPosX[n] = offsetX + (kartPos[n].x >> 16 + 2),
				drawKartPosY[n] = offsetY + (kartPos[n].y >> 16 + 2), 3, 3);
		}
		g.setColor(BLIP_MARKER);
		for (int n = 0; n < numKarts; n++) {
			g.fillRect(drawKartPosX[n] += 1, drawKartPosY[n] += 1, 1, 1);
		}
		g.setColor(BLIP_PLAYER);
		g.fillRect(drawKartPosX[playerIdx], drawKartPosY[playerIdx], 1, 1);
	}
	
	/**
	 *	Fills the chars used for the clock display with all zeroes.
	 *	
	 *	NOTE: this was static but caused problems after obfuscation on S60.
	 */
	public char[] zeroTimeLine(char[] text, int offset, int base) {
		if (text == null) {
			text = new char[offset + 8];
		}
		text[offset + 2] = (char) (base + 10);
		text[offset + 5] = (char) (base + 10);
		fillTimeLine(text, offset, 0, base);
		return text;
	}
	
	/**
	 *	Fills the chars used for the clock display.
	 *	
	 *	NOTE: this was static but caused problems after obfuscation on S60.
	 */
	public char[] fillTimeLine(char[] text, int offset, int ticks, int base) {
		int huns = ticks % FRAMES_PER_SEC;
		int secs = ticks / FRAMES_PER_SEC;
		int mins = ticks / FRAMES_PER_MIN;
		text[offset + 7] = (char) (base + timerMsTens[huns]);
		text[offset + 6] = (char) (base + timerMsHuns[huns]);
		text[offset + 4] = (char) (base + secs % 10);
		secs /= 10;
		text[offset + 3] = (char) (base + secs %  6);
		text[offset + 1] = (char) (base + mins % 10);
		mins /= 10;
		text[offset + 0] = (char) (base + mins %  6);
		return text;
	}
	
	/**
	 *	Colour used to outline the player's map blip.
	 */
	private static final int BLIP_OUTLINE = 0x330099;
	
	/**
	 *	Colour used for the other karts' map blip.
	 */
	private static final int BLIP_MARKER = 0xFFFFFF;
	
	/**
	 *	Colour used for the player's map blip.
	 */
	private static final int BLIP_PLAYER = 0xFF0000;
	
	/**
	 *	Icon char for the word 'best'.
	 */
	private static final char BEST_TEXT = ':';
	
	/**
	 *	Icon char for the word 'time'.
	 */
	private static final char TIME_TEXT = '<';
	
	/**
	 *	Unicode value for '0' in the time display. The next nine digits are
	 *	'1' to '9', followed by ':', and the character before '0' is '/',
	 *	used when displaying the laps.
	 */
	private static final char TIMER_ZERO = '\uFF10';
	
	/**
	 *	Char in the icon font representing a big numeral '1'. The next eight
	 *	digits are '2' to '9', and the icon before '1' is for 'go'.
	 */
	private static final char POSITION_ONE = '1';
	
	/**
	 *	Char in the icon font used to draw 'st' over the large numbers. The
	 *	next three icons are 'nd', 'rd' and 'th'.
	 */
	private static final char POSITION_ORD = '\u2089';
	
	private static final char FIRST_MAP_ICON = 'k';
	
	/**
	 *	Char in the text font representing the collectable items on the track.
	 */
	private static final char PICK_SYMBOL = '\uFF4B';
	
	/**
	 *	Collection of chars to display the fruit machine reels. The first
	 *	char is the first kart icon, the rest are set to the first reel icon.
	 */
	private static final String FRUIT_MACHINE_DEFAULT = "\uFF21 \uFF41\uFF41\uFF41";
	
	/**
	 *	The two chars which make up the 1st icon.
	 */
	private static final String PLAYER_POSITION_DEFAULT = "1\u2089";
	
	private static final int ACTIVE_NONE = 0;
	private static final int ACTIVE_LAPS = 1;
	private static final int ACTIVE_PICK = 2;
	private static final int ACTIVE_POWR = 3;
}