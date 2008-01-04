package numfum.j2me.jsr;

import numfum.j2me.util.ByteUtils;
import numfum.j2me.util.Comparable;
import numfum.j2me.util.Fixed;
import numfum.j2me.util.Vector2D;

/**
 *	Holds the data for a kart, calculates its position, handles collision
 *	detection, etc.
 */
public final class Kart implements Comparable {
	/**
	 *	The kart object's index in the array of karts.
	 */
	public final int index;
	
	/**
	 *	Current position.
	 */
	public final Vector2D pos = new Vector2D();
	
	/**
	 *	Current angle in fixed point format, scaled to a fixed point number.
	 */
	public int posA;
	
	/**
	 *	Previous frame's position.
	 */
	public final Vector2D pre = new Vector2D();
	
	/**
	 *	Last map column. A value of -1 denotes off the map.
	 */
	public int lastCol = -1;
	
	/**
	 *	Last map row. A value of -1 denotes off the map.
	 */
	public int lastRow = -1;
	
	/**
	 *	Whether this kart is in view at the last render.
	 *	
	 *	NOTE: the value is only valid for one game tick.
	 */
	public boolean view = false;
	
	/**
	 *	Which of the AI paths this kart is currently on.
	 */
	public int path =  0;
	
	/**
	 *	Index of the AI point this kart is driving towards.
	 */
	public int next = -1;
	
	/**
	 *	Bump counter for when the kart has left the track.
	 */
	public int bump = 0;
	
	/**
	 *	Previous tick's track segment.
	 */
	public int pseg = -1;
	
	/**
	 *	Counts in ticks how long this kart has been in a segment.
	 */
	public int segt = 0;
	
	/**
	 *	Flag to indicate whether the current lap is valid or not.
	 */
	public boolean flag = false;
	
	/**
	 *	Lap counter;
	 */
	public int laps = 0;
	
	/**
	 *	Distance around the track in the current lap.
	 */
	public int dist = 0;
	
	/**
	 *	Position in the race.
	 */
	public int posn = 0;
	
	/**
	 *	Position in the race on crossing the line. A value less that zero
	 *	means the kart has yet to finish.
	 */
	public int done = -1;
	
	/**
	 *	Index of the 'track effect' blended with the kart sprite at render
	 *	time.
	 *	
	 *	NOTE: the value is only valid for one game tick.
	 */
	public transient int trfx = 0;
	
	/**
	 *	Whether the kart has collided with anthing in the current frame. The
	 *	individual bits denote collision with different object types.
	 *	
	 *	NOTE: the value is only valid for one game tick.
	 *
	 *	@see #BANG_KART
	 */
	public transient int bang = 0;
	
	public int pick = 0;
	
	/**
	 *	Current velocity vector, derived from the speed, traction and steering
	 *	angle. Used to calculate the kart's position for the next frame.
	 */
	public final Vector2D vel = new Vector2D();
	
	/**
	 *	Current kart speed derived from the acceleration.
	 */
	public int speed = 0;
	
	/**
	 *	Countdown timer to reverse the kart away from an obstacle.
	 *
	 *	@see #move(Vector2D target, int distSquared)
	 */
	private int revt = 0;
	
	/**
	 *	Alternates the steering direction when the kart is stuck.
	 *
	 *	@see #revt
	 */
	private boolean oppo = true;
	
	/**************************** Characteristics ***************************/
	
	/**
	 *	The sprite reference used when drawing the kart.
	 */
	public int spRef;
	
	/**
	 *	The kart's acceleration constant. Values around 1.0 are average, with
	 *	anything less being for tractors or old ladies, and anything above 2.0
	 *	pretty much all feels the same.
	 *
	 *	@see #setup
	 */
	private int accel;
	
	/**
	 *	Actual acceleration after power-ups have been applied.
	 */
	private int actAccel;
	
	/**
	 *	The kart's deceleration constant, used only when the kart is steering.
	 *	Should be 1.0 or higher, with 1.0 causing no deceleration. Less than
	 *	1.0 speeds up the kart on cornering.
	 *
	 *	@see #accel
	 *	@see #setup
	 */
	private int decel;
	
	/**
	 *	The kart's steering constant. Values around 1.0 work best, closer to
	 *	2.0 and the kart badly oversteers (even worse than normal!), and less
	 *	than 0.6 is like driving a boat.
	 *
	 *	@see #setup
	 */
	private int steer;
	
	/**
	 *	Maximum speed.
	 *
	 *	@see #setup
	 */
	private int maxSp;
	
	/**
	 *	Actual maximum speed after power-ups have been applied.
	 */
	private int actMaxSp;
	
	/**
	 *	Minimum speed, or reversing speed.
	 *
	 *	@see #setup
	 */
	private int minSp;
	
	/**
	 *	Maximum turning speed. When the kart is travelling above this speed
	 *	turning will cause a slowdown.
	 *
	 *	@see #setup
	 */
	private int maxTn;
	
	/**
	 *	The kart's mass. Values between 1 and 8 work best.
	 *
	 *	@see #setup
	 */
	private int mass;
	
	/**
	 *	Precalculated maximum speed squared.
	 *
	 *	@see #setup
	 */
	private int maxSpSquared;
	
	private int aiDistance;
	private int aiCorrectR;
	private int aiCorrectL;
	
	/************************************************************************/
	
	/**
	 *	Surface traction for the kart.
	 */
	private int tract =  Fixed.ONE * 5;
	
	/**
	 *	Actual traction after power-ups have been applied.
	 */
	private int actTract;
	
	/**
	 *	Surface traction for the kart.
	 */
	private int frict = (Fixed.ONE * 90) / 100;
	
	/**
	 *	Air resistance for the kart.
	 */
	private int aires = (Fixed.ONE * 95) / 100;

	private int ud, lr;
	
	/**
	 *	Scratch vector for temporary calculations.
	 */
	private final Vector2D scratch = new Vector2D();
	
	public Kart(int index) {
		this.index = index;
		setAISkill(1 << Fixed.FIXED_POINT, 4);
	}
	
	public Kart setup(int spRef, byte[] props) {
		this.spRef = spRef;
		accel = (props[PROPS_ACCEL] & 0xFF) << (Fixed.FIXED_POINT - PROPS_FIXED_POINT);
		decel = (props[PROPS_DECEL] & 0xFF) << (Fixed.FIXED_POINT - PROPS_FIXED_POINT);
		steer = (props[PROPS_STEER] & 0xFF) << (Fixed.FIXED_POINT - PROPS_FIXED_POINT);
		mass  =  props[PROPS_MASS ];
		maxSp = (props[PROPS_MAXSP] & 0xFF) << (Fixed.FIXED_POINT - PROPS_FIXED_POINT);
		minSp =  props[PROPS_MINSP]         << (Fixed.FIXED_POINT - PROPS_FIXED_POINT);
		maxTn = (props[PROPS_MAXTN] & 0xFF) << (Fixed.FIXED_POINT - PROPS_FIXED_POINT);
		maxSpSquared = Fixed.mul(maxSp, maxSp);
		return this;
	}
	
	/**
	 *	Sets the 'skill' level for the AI. Lower values tighten the AI's
	 *	adherence to the poly-line defining a path through the track. The
	 *	correct values is a fine balance between overcompensating and sloppy
	 *	driving. Good known working values in the current incarnation are:
	 *
	 *		Easy:	aiDistance = 4, aiCorrect = 8
	 *		Medium:	aiDistance = 2, aiCorrect = 6
	 *		Hard:	aiDistance = 1, aiCorrect = 4
	 *
	 *	'aiDistance' is a fixed point value
	 */
	public void setAISkill(int aiDistance, int aiCorrect) {
		this.aiDistance = aiDistance;
		aiCorrectR = aiCorrect;
		aiCorrectL = Fixed.QUARTER_CIRCLE * 4 - aiCorrect;
	}
	
	/**
	 *	Steer the kart towards an AI point.
	 */
	public void update(Vector2D target, int distSquared) {
		boolean l = false;
		boolean r = false;
		
		if (distSquared > aiDistance) {
			scratch.setToDif(target, pos);
			scratch.set(scratch.x, -scratch.y);
			int dir = (scratch.dir() - (posA >> Fixed.FIXED_POINT) - Fixed.QUARTER_CIRCLE) & 0xFF;
			if (dir < Fixed.QUARTER_CIRCLE * 2) {
				if (dir > aiCorrectR) {
					r = true;
				}
			} else {
				if (dir < aiCorrectL) {
					l = true;
				}
			}
		}
		
		/*
		 *	If the kart hasn't moved then it's more than likely stuck behind
		 *	a static object, in which case reverse rather than trying to drive
		 *	forward.
		 */
		if (pos.equals(pre)) {
			revt = 8;
			oppo = !oppo;
		}
		if (revt > 0) {
			revt--;
			if (oppo) {
				if (l || r) {
					update(false, true, r, l);
				} else {
					update(false, true, true, false);
				}
			} else {
				if (l || r) {
					update(false, true, l, r);
				} else {
					update(false, true, false, true);
				}
			}
		} else {
			update(true, false, l, r);
		}
	}
	
	/**
	 *	Steer the kart manually.
	 */
	public void update(boolean u, boolean d, boolean l, boolean r) {
		if (puTime > 0) {
			puTime--;
			if (puTime == 0) {
				puType = POWERUP_NOTHING;
				puProp = 0;
				pufx   = 0;
			}
		}
		
		actAccel = accel;
		actMaxSp = maxSp;
		actTract = tract;
		
		lr = (r ? 1 : 0) - (l ? 1 : 0);
		
		switch (puType) {
		case POWERUP_NITROUS:
			actAccel = 2 * accel;
			actMaxSp = 8 * Fixed.ONE;
			break;
		case POWERUP_MISFIRE:
			u = false;
			break;
		case POWERUP_SPINOUT:
			actTract = 512 * Fixed.ONE;
			if (puProp != 0) {
				lr = puProp;
			} else {
				if (lr != 0) {
					puProp = lr * 2;
				}
			}
			break;
		}
		
		ud = (u ? 1 : 0) - (d ? 1 : 0);
		
		if (lr == 0 || puType == POWERUP_NITROUS || bump > 1) {
			speed += ud * actAccel;
			speed = Fixed.min(Fixed.max(speed, minSp), actMaxSp);
		} else {
			if (speed > maxTn) {
				speed = Fixed.div(speed, decel);
			} else {
				speed += ud * accel;
				speed = Fixed.min(Fixed.max(speed, minSp), maxTn);
			}
		}
		
		vel.x += Fixed.div(Fixed.mul(Fixed.cos(posA >> Fixed.FIXED_POINT), speed) - vel.x, actTract);
		vel.y += Fixed.div(Fixed.mul(Fixed.sin(posA >> Fixed.FIXED_POINT), speed) - vel.y, actTract);
		if (CLAMP_VELOCITY_PER_HIT) {
			clampVelocity();
		}
		
		if (lr != 0) {
			if (bump > 1) {
				posA += lr * (Fixed.mul(steer, speed) >> 1);
			} else {
				posA += lr *  Fixed.mul(steer, speed);
			}
		}
		
		if (bump < 2) {
			speed = Fixed.mul(speed, frict);
		} else {
			speed = Fixed.mul(speed, aires);
		}
		
		if (Fixed.abs(speed) < accel / 2) {
			speed = 0;
		}
		
		pre.set(pos);
		pos.x += (vel.x >> 3);
		pos.y += (vel.y >> 3);
	}
	
	private int puType = 0;
	private int puTime = 0;
	private int puProp = 0;
	public int pufx = 0;
	
	public void powerUp(int puType, int puTime, int pufx) {
		this.puType = puType;
		this.puTime = puTime;
		this.puProp = 0;
		this.pufx   = pufx;
	}
	
	/**
	 *	Saves the kart data for network play. Not all the data is saved, only
	 *	the bare minimum. Note: speed is limited to a 4-bit number, which
	 *	doesn't cover the full range a kart might have but is enough (as we
	 *	only need to check whether karts moving slower than a crawl in the
	 *	current game engine).
	 */
	public int saveNetworkPacket(byte[] data, int n) {
		ByteUtils.shortToBytes(data, n, pos.x >> 8);
		n += 2;
		ByteUtils.shortToBytes(data, n, pos.y >> 8);
		n += 2;
		data[n++] = (byte) (posA >> Fixed.FIXED_POINT);
		data[n++] = (byte) ((posn << 4) | (laps & 0x0F));
		data[n++] = (byte) ((bump << 4) | (trfx & 0x0F));
		data[n++] = (byte) (((speed >> Fixed.FIXED_POINT) << 4) | (bang & 0x0F));
		return n;
	}
	
	/**
	 *	Loads the kart data for network play.
	 *
	 *	@see #saveNetworkPacket
	 */
	public int loadNetworkPacket(byte[] data, int n) {
		pos.x = ByteUtils.bytesToUnsignedShort(data, n) << 8;
		n += 2;
		pos.y = ByteUtils.bytesToUnsignedShort(data, n) << 8;
		n += 2;
		posA  = (data[n++] & 0xFF) << Fixed.FIXED_POINT;
		posn  = (data[n]   & 0xF0) >> 4;
		laps  =  data[n++] & 0x0F;
		bump  = (data[n]   & 0xF0) >> 4;
		trfx  =  data[n++] & 0x0F;
		bang  =  data[n]   & 0x0F;
		speed = (data[n++] >> 4) << Fixed.FIXED_POINT;
		return n;
	}
	
	/**
	 *	Saves all of the kart data when storing the game state.
	 */
	public int save(byte[] data, int n) {
		n = pos.save(data, n);
		ByteUtils.intToBytes(data, n, posA);
		n += 4;
		n = pre.save(data, n);
		
		data[n++] = (byte) lastCol;
		data[n++] = (byte) lastRow;
		
		// don't save 'view'?
		data[n++] = (byte) path;
		data[n++] = (byte) next;
		data[n++] = (byte) bump;
		data[n++] = (byte) pseg;
		data[n++] = (byte) segt;
		ByteUtils.booleanToByte(data, n++, flag);
		
		data[n++] = (byte) laps;
		ByteUtils.shortToBytes(data, n, dist);
		n += 2;
		data[n++] = (byte) posn;
		data[n++] = (byte) done;
		
		data[n++] = (byte) pick;
		
		// don't save 'trfx' or 'bang'?
		n = vel.save(data, n);
		
		ByteUtils.intToBytes(data, n, speed);
		n += 4;
		
		data[n++] = (byte) revt;
		ByteUtils.booleanToByte(data, n++, oppo);
		
		//TODO: pickup bits and bobs
		
		return n;
	}
	
	/**
	 *	Loads all of the kart data from a stored game state.
	 */
	public int load(byte[] data, int n) {
		n = pos.load(data, n);
		posA = ByteUtils.bytesToInt(data, n);
		n += 4;
		n = pre.load(data, n);
		
		lastCol = data[n++];
		lastRow = data[n++];
		
		path = data[n++];
		next = data[n++];
		bump = data[n++];
		pseg = data[n++];
		segt = data[n++];
		flag = ByteUtils.byteToBoolean(data, n++);
		
		laps = data[n++];
		dist = ByteUtils.bytesToShort(data, n);
		n += 2;
		posn = data[n++];
		done = data[n++];
		
		pick = data[n++];
		
		n = vel.load(data, n);
		
		speed = ByteUtils.bytesToInt(data, n);
		n += 4;
		
		revt = data[n++];
		oppo = ByteUtils.byteToBoolean(data, n++);
		
		return n;
	}
	
	/**
	 *	Reset the kart to the specified world position. Used when a new level
	 *	is loaded.
	 */
	public void reset(Vector2D whereTo, int posA, int path, int posn) {
		warp(whereTo);
		this.posA = posA;
		this.path = path;
		this.posn = posn;
		
		next = -1;
		bump =  0;
		pseg = -1;
		
		flag = false;
		laps = 0;
		dist = 0;
		done = -1;
		
		trfx = 0;
		bang = 0;
		
		pick = 0;
		
		speed = 0;
		
		revt = 0;
		oppo = false;
		
		puType = 0;
		puTime = 0;
		puProp = 0;
		pufx   = 0;
	}
	
	/**
	 *	Move the kart to the specified world position. Used to correct the AI
	 *	if the kart gets stuck.
	 */
	public void warp(Vector2D whereTo) {
		pos.set(whereTo);
		pre.set(whereTo);
		pre.add(1, 1); // as not to trigger the reverse timer
		vel.set(0, 0);
		lastCol = -1;
		lastRow = -1;
		segt =  0;
	}
	
	public void setAirResistance(int aires) {
		this.aires = aires;
	}
	
	public void setTrackType(int tract, int frict) {
		this.tract = tract;
		this.frict = frict;
	}
	
	public void clampVelocity() {
		if (this.vel.magSquared() > maxSpSquared) {
			this.vel.setPolar(maxSp, this.vel.dir());
		}
	}
	
	/**
	 *	Performs a collision between this kart and another.
	 *
	 *	@return whether a collision actually occurred
	 */
	public boolean collide(Kart that) {
		scratch.setToDif(this.pos, that.pos);
		int mag = Fixed.hyp(0, 0, scratch.x, scratch.y);
		if (mag < COLLISION_DIAMETER) {
			if (mag > 0) {
				scratch.div(mag);
				int relVelDot = this.vel.dot(scratch) - that.vel.dot(scratch);
				if (relVelDot <= 0) {
					scratch.mul((COLLISION_STRENGTH * relVelDot) / (this.mass + that.mass));
					this.vel.sub(that.mass * scratch.x, that.mass * scratch.y);
					that.vel.add(this.mass * scratch.x, this.mass * scratch.y);
					if (CLAMP_VELOCITY_PER_HIT) {
						this.clampVelocity();
						that.clampVelocity();
					}
				}
			}
			if (FORCE_SEPARATION) {
				if (SLIDE_INSTEAD_OF_STOP) {
					scratch.setToDif(this.pos, that.pos);
				} else {
					scratch.setToDif(this.pre, that.pos);
				}
				this.pos.setToSum(that.pos, TOUCH_TABLE[scratch.dir() & Fixed.FULL_CIRCLE_MASK]);
			}
			return true;
		}
		return false;
	}
	
	/**
	 *	Performs a collision between this kart and an object.
	 *
	 *	@param objPos position of the object
	 *	@param objMass object mass
	 */
	public boolean collide(Vector2D objPos, int objMass) {
		scratch.setToDif(pos, objPos);
		int mag = Fixed.hyp(0, 0, scratch.x, scratch.y);
		if (mag < COLLISION_DIAMETER) {
			if (objMass > 0) {
				if (mag > 0) {
					scratch.div(mag);
					int relVelDot = vel.dot(scratch);
					if (relVelDot <= 0) {
						scratch.mul((COLLISION_STRENGTH * relVelDot) / (mass + objMass));
						vel.sub(objMass * scratch.x, objMass * scratch.y);
						if (CLAMP_VELOCITY_PER_HIT) {
							clampVelocity();
						}
					}
				}
				if (FORCE_SEPARATION_ON_SCENERY) {
					if (SLIDE_INSTEAD_OF_STOP) {
						scratch.setToDif(this.pos, objPos);
					} else {
						scratch.setToDif(this.pre, objPos);
					}
					this.pos.setToSum(objPos, TOUCH_TABLE[scratch.dir() & Fixed.FULL_CIRCLE_MASK]);
				}
			}
			return true;
		}
		return false;
	}
	
	/**
	 *	Used for sorting the race order. Karts are sorted by their finishing
	 *	position, if they have already completed the race, or by the distance
	 *	travelled otherwise.
	 */
	public int compareTo(Object obj) {
		Kart that = (Kart) obj;
		if (that.done >= 0) {
			if (this.done >= 0) {
				return this.done - that.done;
			} else {
				return  1;
			}
		} else {
			if (this.done >= 0) {
				return -1;
			} else {
				return that.dist - this.dist;
			}
		}
	}
	
	/*public String toString() {
		return "Kart ["
			+ "index: " + index + ", "
			+ "spRef: " + spRef + ", "
			+ "accel: " + Fixed.toString(accel) + ", "
			+ "decel: " + Fixed.toString(decel) + ", "
			+ "steer: " + Fixed.toString(steer) + ", "
			+ "mass: "  + mass  + ", "
			+ "maxSp: " + Fixed.toString(maxSp) + ", "
			+ "minSp: " + Fixed.toString(minSp) + ", "
			+ "maxTn: " + Fixed.toString(maxTn)
			+ "]";
	}*/
	
	public static final int COLLISION_DIAMETER = 12 << (Fixed.FIXED_POINT - 3);
	
	private static final boolean CLAMP_VELOCITY_PER_HIT = false;
	private static final boolean FORCE_SEPARATION = true;
	private static final boolean FORCE_SEPARATION_ON_SCENERY = true;
	private static final boolean SLIDE_INSTEAD_OF_STOP = true;
	
	public static final int COLLISION_STRENGTH = 3;
	
	private static final Vector2D[] TOUCH_TABLE;
	static {
		if (FORCE_SEPARATION) {
			TOUCH_TABLE = new Vector2D[Fixed.QUARTER_CIRCLE * 4];
			for (int n = 0; n < Fixed.QUARTER_CIRCLE * 4; n++) {
				TOUCH_TABLE[n] = new Vector2D(
					Fixed.mul(COLLISION_DIAMETER, Fixed.sin(n)),
					Fixed.mul(COLLISION_DIAMETER, Fixed.cos(n)));
			}
		} else {
			TOUCH_TABLE = null;
		}
	}
	
	/**
	 *	The kart properties are don't need the precision of the fixed point
	 *	maths class so are stored using fewer bits (4 + 4 should be enough).
	 */
	public static final int PROPS_FIXED_POINT = 4;
	
	/**
	 *	Number of properties to describe a kart.
	 */
	public static final int TOTAL_PROPS = 10;
	
	public static final int PROPS_ACCEL = 0;
	public static final int PROPS_DECEL = 1;
	public static final int PROPS_STEER = 2;
	public static final int PROPS_MASS  = 3;
	public static final int PROPS_MAXSP = 4;
	public static final int PROPS_MINSP = 5;
	public static final int PROPS_MAXTN = 6;
	
	public static final int PROPS_STATS_0 = 7;
	public static final int PROPS_STATS_1 = 8;
	public static final int PROPS_STATS_2 = 9;
	
	/**
	 *	Collision with another kart.
	 *
	 *	@see #bang
	 */
	public static final int BANG_KART = 1;
	
	/**
	 *	Collision with static scenery.
	 *
	 *	@see #bang
	 */
	public static final int BANG_SCENERY = 2;
	
	/**
	 *	Collision with a collectable item.
	 *
	 *	@see #bang
	 */
	public static final int BANG_PICKUP = 4;
	
	/**
	 *	Collision with a power-up.
	 *
	 *	@see #bang
	 */
	public static final int BANG_POWERUP = 8;
	
	public static final int POWERUP_NOTHING = 0;
	public static final int POWERUP_NITROUS = 1;
	public static final int POWERUP_MISFIRE = 2;
	public static final int POWERUP_SPINOUT = 3;
}