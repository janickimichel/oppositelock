package numfum.j2me.jsr.generic;

/**
 *	Controls the state and updates of animated tile sequences. By default none
 *	of the tiles is updating. Adding a tile using <code>addAnimTile()</code>
 *	causes <code>AnimTile.cycle()</code> to be called for that tile as part of
 *	the controller's own <code>cycle()</code>.
 */
public class AnimTileController {
	/**
	 *	All the AnimTiles whether animated or not.
	 */
	protected final AnimTile[] animtile;
	
	/**
	 *	Maximimum number of anims or the total number of tiles.
	 */
	protected final int maxAnims;
	
	/**
	 *	Number of active animated tiles. Tiles with two or more frames are
	 *	considered active.
	 */
	protected int numActiveAnims = 0;
	
	/**
	 *	Active tile indices.
	 */
	protected final int[] activeAnims;
	
	/**
	 *	Creates a new tile controller with using the existing tiles.
	 */
	public AnimTileController(AnimTile[] animtile) {
		this.animtile = animtile;
		maxAnims = animtile.length;
		activeAnims = new int[maxAnims];
	}
	
	/**
	 *	Clears the active animating tiles.
	 */
	protected final void clear() {
		numActiveAnims = 0;
	}
	
	/**
	 *	Resets all the tiles in under this controller.
	 */
	public void reset() {
		for (int n = maxAnims - 1; n >= 0; n--) {
			animtile[n].reset();
		}
	}
	
	/**
	 *	Adds a new tile to be maintained by this controller.
	 */
	public void addAnimTile(int index) {
		if (animtile[index].anim > 0) {
			for (int n = numActiveAnims - 1; n >= 0; n--) {
				if (activeAnims[n] == index) {
					return;
				}
			}
			activeAnims[numActiveAnims++] = index;
		}
	}
	
	/**
	 *	Removes a tile from this controller.
	 */
	public final void removeAnimTile(int index) {
		int where = -1;
		for (int n = numActiveAnims - 1; n >= 0; n--) {
			if (activeAnims[n] == index) {
				where = n;
				break;
			}
		}
		if (where != -1) {
			numActiveAnims--;
			for (int n = where; n < numActiveAnims; n++) {
				activeAnims[n] = activeAnims[n + 1];
			}
		}
	}
	
	/**
	 *	Cycles all of the active tiles under this controller.
	 */
	public void cycle() {
		for (int n = numActiveAnims - 1; n >= 0; n--) {
			animtile[activeAnims[n]].cycle();
		}
	}
}