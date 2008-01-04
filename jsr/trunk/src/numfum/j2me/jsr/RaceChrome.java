package numfum.j2me.jsr;

import numfum.j2me.util.Vector2D;

/**
 *	Adds the polish when displaying a race.
 */
public interface RaceChrome {
	/**
	 *	
	 */
	public void init(int maxLaps, int mapIdx, int playerIdx);
	
	/**
	 *	Sets up the kart blips on the map.
	 */
	public void setMapBlips(Vector2D[] kartPos, int numKarts);
	
	/**
	 *	Sets the frequently changing HUD data.
	 */
	public void setStats(int time, int posn, int laps, int pick, int powrState, int powrPayout, int powrCharIdx);
	
	/**
	 *	Sets the best time.
	 */
	public void setBest(int best);
}