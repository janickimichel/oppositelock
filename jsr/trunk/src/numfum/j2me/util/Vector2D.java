package numfum.j2me.util;

/**
 * Fixed point 2D vector with common maths functions. Some operations will
 * work with a vector of integer values, but unless otherwise stated all are
 * designed with fixed point in mind.
 * 
 * @author cw
 */
public final class Vector2D implements Comparable {
	/**
	 * The x-axis fixed fixed point value.
	 */
	public int x;
	
	/**
	 * The y-axis fixed fixed point value.
	 */
	public int y;
	
	/**
	 * Creates a new zero vector.
	 */
	public Vector2D() {
		x = 0;
		y = 0;
	}
	
	/**
	 * Creates a new vector with the specified values.
	 * 
	 * @param x x-axis fixed fixed point value
	 * @param y y-axis fixed fixed point value
	 */
	public Vector2D(int x, int y) {
		set(x, y);
	}
	
	/**
	 * Creates a new vector by copying the values from another.
	 * 
	 * @param that vector to copy
	 */
	public Vector2D(Vector2D that) {
		set(that);
	}
	
	/**
	 * Sets the values of this vector.
	 * 
	 * @param x x-axis fixed fixed point value
	 * @param y y-axis fixed fixed point value
	 */
	public void set(int x, int y) {
		this.x = x;
		this.y = y;
	}
	
	/**
	 * Sets the values of this vector from another.
	 * 
	 * @param that vector to copy
	 */
	public void set(Vector2D that) {
		this.x = that.x;
		this.y = that.y;
	}
	
	/**
	 * Sets the values of this vector from the specified polar coordinates.
	 * 
	 * @param mag magnitude
	 * @param dir direction (in fixed point format)
	 */
	public void setPolar(int mag, int dir) {
		x = Fixed.mul(mag, Fixed.cos(dir));
		y = Fixed.mul(mag, Fixed.sin(dir));
	}
	
	/**
	 * Sets the value of this vector to the sum of two others.
	 * 
	 * @param a LHS of the vector addition
	 * @param b RHS of the vector addition
	 */
	public void setToSum(Vector2D a, Vector2D b) {
		x = a.x + b.x;
		y = a.y + b.y;
	}
	
	/**
	 * Sets the value of this vector to the difference of two others.
	 * 
	 * @param a LHS of the vector subtraction
	 * @param b RHS of the vector subtraction
	 */
	public void setToDif(Vector2D a, Vector2D b) {
		x = a.x - b.x;
		y = a.y - b.y;
	}
	
	/**
	 * Sets the value of this vector by dividing another.
	 * 
	 * @param that vector to divide
	 * @param n fixed point value to divide by
	 */
	public void setToDiv(Vector2D that, int n) {
		this.x = Fixed.div(that.x, n);
		this.y = Fixed.div(that.y, n);
	}
	
	/**
	 * Sets the value of this vector by multiplying another.
	 * 
	 * @param that vector to multiply
	 * @param n fixed point value to multiply by
	 */
	public void setToMul(Vector2D that, int n) {
		this.x = Fixed.mul(that.x, n);
		this.y = Fixed.mul(that.y, n);
	}
	
	/**
	 * Adds to this vector.
	 * 
	 * @param x x-axis fixed fixed point value to add
	 * @param y y-axis fixed fixed point value to add
	 */
	public void add(int x, int y) {
		this.x += x;
		this.y += y;
	}
	
	/**
	 * Adds to this vector.
	 * 
	 * @param that vector to add
	 */
	public void add(Vector2D that) {
		this.x += that.x;
		this.y += that.y;
	}
	
	/**
	 * Adds a vector specified by polar coordinates to this one.
	 * 
	 * @param mag magnitude
	 * @param dir direction (in fixed point format)
	 */
	public void addPolar(int mag, int dir) {
		x += Fixed.mul(mag, Fixed.cos(dir));
		y += Fixed.mul(mag, Fixed.sin(dir));
	}
	
	/**
	 * Subtracts from this vector.
	 * 
	 * @param x x-axis fixed fixed point value to subtract
	 * @param y y-axis fixed fixed point value to subtract
	 */
	public void sub(int x, int y) {
		this.x -= x;
		this.y -= y;
	}
	
	/**
	 * Subtracts from this vector.
	 * 
	 * @param that vector to subtract
	 */
	public void sub(Vector2D that) {
		this.x -= that.x;
		this.y -= that.y;
	}
	
	/**
	 * Subtracts a vector specified by polar coordinates from this one.
	 * 
	 * @param mag magnitude
	 * @param dir direction (in fixed point format)
	 */
	public void subPolar(int mag, int dir) {
		x -= Fixed.mul(mag, Fixed.cos(dir));
		y -= Fixed.mul(mag, Fixed.sin(dir));
	}
	
	/**
	 * Multiplies this vector by a fixed point value.
	 * 
	 * @param n fixed point multiplier
	 */
	public void mul(int n) {
		x = Fixed.mul(x, n);
		y = Fixed.mul(y, n);
	}
	
	/**
	 * Divides this vector by a fixed point value.
	 * 
	 * @param n fixed point divisor
	 */
	public void div(int n) {
		x = Fixed.div(x, n);
		y = Fixed.div(y, n);
	}
	
	/**
	 * Multiplies this vector by an integer.
	 * 
	 * @param n integer multiplier
	 */
	public void mulScalar(int n) {
		x *= n;
		y *= n;
	}
	
	/**
	 * Divides this vector by an integer.
	 * 
	 * @param n integer divisor
	 */
	public void divScalar(int n) {
		x /= n;
		y /= n;
	}
	
	/**
	 * Returns the magnitude of this vector (which is an expensive operation,
	 * due to the fixed point square root).
	 * 
	 * @return the magnitude of this vector
	 */
	public int mag() {
		return Fixed.sqrt(magSquared());
	}
	
	/**
	 * Returns the squared magnitude of this vector.
	 * 
	 * @return the squared magnitude of this vector
	 */
	public int magSquared() {
		return Fixed.mul(x, x) + Fixed.mul(y, y);
	}
	
	/**
	 * Returns the polar direction of this vector.
	 * 
	 * @return the polar direction of this vector
	 */
	public int dir() {
		return Fixed.atan(x, y);
	}
	
	/**
	 * Normalises this vector.
	 */
	public void normalise() {
		int mag = mag();
		if (mag > 0) {
			div(mag);
		} else {
			set(0, 0);
		}
	}
	
	/**
	 *	Clamps this vector's magnitude.
	 */
	public void clampMag(int n) {
		if (mag() > n) {
			setPolar(n, dir());
		}
	}
	
	/**
	 * Returns the dot product of this and another vector.
	 * 
	 * @param that other vector to use for calculation
	 * @return the dot product of this and another vector
	 */
	public int dot(Vector2D that) {
		return Fixed.mul(this.x, that.x) + Fixed.mul(this.y, that.y);
	}
	
	/**
	 * Whether this vector equals another object.
	 * 
	 * @param obj object to compare with
	 * @return <code>true</code> if they match
	 */
	public boolean equals(Object obj) {
		try {
			return equals((Vector2D) obj);
		} catch (ClassCastException e) {
			return false;
		}
	}
	
	/**
	 * Tests this Vector2D's axes against another for equality.
	 * 
	 * @param that Vector2D to compare with
	 * @return <code>true</code> if they match
	 */
	public boolean equals(Vector2D that) {
		return this == that || (this.x == that.x && this.y == that.y);
	}
	
	/**
	 * Tests this Vector2D's axes against another for equality.
	 * 
	 * @param x x-axis fixed fixed point value
	 * @param y y-axis fixed fixed point value
	 * @return <code>true</code> if they match
	 */
	public boolean equals(int x, int y) {
		return this.x == x && this.y == y;
	}
	
	/**
	 * Returns a hash code for this vector. The hash is calculated from the
	 * whole part of the fixed point value. Results with a fraction part of
	 * less than 16 is unspecified.
	 * 
	 * @return hash code for this vector
	 */
	public int hashCode() {
		return (((y >> Fixed.FIXED_POINT) << 16) | (x >> Fixed.FIXED_POINT));
	}
	
	/*
	 * (non-Javadoc)
	 * Compares this vector to another, based on their magnitude (squared).
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object obj) {
		return this.magSquared() - ((Vector2D) obj).magSquared();
	}
	
	/**
	 * Alternative compare, which further refined the comparison. Returns a
	 * negative integer, zero, or a positive integer if this vector is less
	 * than, equal to, or greater than another.
	 * 
	 * @return a negative integer, zero or a positive integer, depending on the match
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int _compareTo_(Object obj) {
		Vector2D that = (Vector2D) obj;
		/*
		 *	The initial comparison is done on the Vector's magnitude.
		 */
		int out = this.magSquared() - that.magSquared();
		if (out != 0) {
			return out;
		}
		/*
		 *	Vectors of the same length are then compared on the difference
		 *	in the y-axis, followed by the x.
		 */
		out = this.y - that.y;
		if (out != 0) {
			return out;
		}
		return this.x - that.x;
	}
	
	/**
	 * Deducts whether this vector, taken as a point, is inside the polygon
	 * represented by the array of points.
	 * 
	 * The algorithm is adapted from code by Wm. Randolph Franklin in the
	 * comp.graphics.algorithms FAQ, section 2.03.
	 * 
	 * @return <code>true</code> if this vector is inside the polygon
	 */
	public boolean isInPoly(Vector2D[] poly) {
		return isInPoly(poly, poly.length);
	}
	
	/**
	 * As per {@link #isInPoly(Vector2D[])} but the number of actual used
	 * points in the poly is specified.
	 * 
	 * @return <code>true</code> if this vector is inside the polygon
	 */
	public boolean isInPoly(Vector2D[] poly, int points) {
		boolean res = false;
		for (int i = 0, j = points - 1; i < points; j = i++) {
			if (((poly[i].y <= y && y < poly[j].y) || (poly[j].y <= y && y < poly[i].y))
				&& (x < (poly[j].x - poly[i].x) * (y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x)) {
				res = !res;
			}
		}
		return res;
	}
	
	public int save(byte[] data, int offset) {
		ByteUtils.intToBytes(data, offset + 0, x);
		ByteUtils.intToBytes(data, offset + 4, y);
		return offset + 8;
	}
	
	public int load(byte[] data, int offset) {
		x = ByteUtils.bytesToInt(data, offset + 0);
		y = ByteUtils.bytesToInt(data, offset + 4);
		return offset + 8;
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return getClass().getName() + ": [x: " + x + ", y: " + y + "]";
	}
	
	/*
	 *	Temporary variables used in point nearest line calculations.
	 */
	private static final Vector2D unitB = new Vector2D();
	private static final Vector2D tempB = new Vector2D();
	private static final Vector2D unitP = new Vector2D();
	private static final Vector2D tempP = new Vector2D();
	
	/**
	 * Given a line AB, and a point P, calculates the nearest point on that
	 * line, storing the result in R and returning the distance squared
	 * between PR. Not thread safe due to the static Vector2D objects used
	 * during the calculations.
	 * 
	 * Uses eight multiplies and two divides.
	 * 
	 * @param A part of line AB
	 * @param B part of line AB
	 * @param P point of interest
	 * @param R result of calculation
	 * @return the distance squared between PR
	 */
	public static int pointNearestLine(Vector2D A, Vector2D B, Vector2D P, Vector2D R) {
		unitB.setToDif(B, A);
		tempB.setToDiv(unitB, unitB.magSquared());
		unitP.setToDif(P, A);
		int dp = unitP.dot(tempB);
		if (dp <= 0) {
			R.set(A);
		} else {
			if (dp >= Fixed.ONE) {
				R.set(B);
			} else {
				R.setToMul(unitB, dp);
				R.add(A);
			}
		}
		tempP.setToDif(P, R);
		return tempP.magSquared();
	}
	
	/**
	 * As per {@link #pointNearestLine(Vector2D, Vector2D, Vector2D, Vector2D)}
	 * but with <code>B</code> as a unit vector and <code>scaledB</code>
	 * precalculated as:
	 * <pre>
	 *     scaledB.setToDiv(B, B.magSquared());
	 * </pre>
	 * <code>O</code> is the origin (point <code>A</code> above), <code>
	 * R</code> is used to store the result.
	 * <p>
	 * Uses six multiplies.
	 * 
	 * @param B part of line OB
	 * @param scaledB value calculated by <code>scaledB.setToDiv(B, B.magSquared())</code>
	 * @param O origin for OB
	 * @param P point of interest
	 * @param R result of calculation
	 * @return the distance squared between PR
	 */
	public static int pointNearestLine(Vector2D B, Vector2D scaledB, Vector2D O, Vector2D P, Vector2D R) {
		unitP.setToDif(P, O);
		int dp = unitP.dot(scaledB);
		if (dp <= 0) {
			R.set(O);
		} else {
			if (dp >= Fixed.ONE) {
				R.setToSum(B, O);
			} else {
				R.setToMul(B, dp);
				R.add(O);
			}
		}
		tempP.setToDif(P, R);
		return tempP.magSquared();
	}
	
	/*
	 *	Edge buffers used for plotting polys.
	 */
	private static final int[] scanLHS = new int[128];
	private static final int[] scanRHS = new int[128];
	
	/**
	 *	Given a quad (as a clockwise list of points) fills the byte buffer in
	 *	with the specified value.
	 *
	 *	@param drawFlush whether edges are drawn to butt up to one another
	 */
	public static void fillQuad(Vector2D[] quad, Object buffer, int numCols, int numRows, byte value, boolean drawFlush) {
		fillQuad(quad, buffer, numCols, numRows, value, scanLHS, scanRHS, drawFlush);
	}
	
	public static void fillQuad(Vector2D[] quad, Object buffer, int numCols, int numRows, byte value, int[] scanLHS, int[] scanRHS, boolean drawFlush) {
		for (int i = scanLHS.length - 1; i >= 0; i--) {
			scanLHS[i] = -1;
			scanRHS[i] = -1;
		}
		int[] scan;
		
		int quadXA, quadXB, quadYA, quadYB;
		for (int i = 0; i < 4; i++) {
			int a =  i      & 3;
			int b = (i + 1) & 3;
			quadYA = quad[a].y;
			quadYB = quad[b].y;
			if (quadYA != quadYB) {
				quadXA = quad[a].x;
				quadXB = quad[b].x;
				if (quadYA > quadYB) {
					scan = scanLHS;
					if (drawFlush) {
						quadYA--;
						quadYB--;
					}
				} else {
					scan = scanRHS;
					if (drawFlush && quadXA >= quadXB) {
						quadXA--;
						quadXB--;
					}
				}
				drawLine(scan, quadXA, quadYA, quadXB, quadYB);
			}
		}
		
		if (buffer instanceof byte[][]) {
			drawBuffers((byte[][]) buffer, numCols, numRows, value, scanLHS, scanRHS);
		} else {
			if (buffer instanceof byte[]) {
				drawBuffers((byte[]) buffer, numCols, numRows, value, scanLHS, scanRHS);
			} else {
				throw new IllegalArgumentException();
			}
		}
	}
	
	public static void drawBuffers(byte[][] buffer, int numCols, int numRows, byte value, int[] scanLHS, int[] scanRHS) {
		for (int row = numRows - 1; row >= 0; row--) {
			int a = scanLHS[row];
			int b = scanRHS[row];
			if (a >= 0 && b >= 0 && b < numCols) {
				for (int col = a; col <= b; col++) {
					buffer[row][col] = value;
				}
			}
		}
	}
	
	public static void drawBuffers(byte[] buffer, int numCols, int numRows, byte value, int[] scanLHS, int[] scanRHS) {
		for (int row = numRows - 1; row >= 0; row--) {
			int n = row * numCols;
			int a = scanLHS[row];
			int b = scanRHS[row];
			if (a >= 0 && b >= 0 && b < numCols) {
				for (int col = a; col <= b; col++) {
					buffer[n + col] = value;
				}
			}
		}
	}
	
	/**
	 *	Bresenham's line drawing algorithm adapted to draw into an edge
	 *	buffer. No checks are done to constrain the drawing to the buffer's
	 *	boundaries.
	 *
	 *	@throws ArrayIndexOutOfRangeException if pixels breach the bounds
	 */
	public static void drawLine(int[] scan, int x1, int y1, int x2, int y2) {
		int x  = x1;
		int y  = y1;
		int dx = x2 - x1;
		int dy = y2 - y1;
		
		int D = 0, c, M;
		
		int xInc = 1;
		int yInc = 1;
		if (dx < 0) {
			xInc = -1;
			dx = -dx;
		}
		if (dy < 0) {
			yInc = -1;
			dy = -dy;
		}
		
		if (dy <= dx) {
			c = 2 * dx;
			M = 2 * dy;
			while (true) {
				scan[y] = x;
				if (x == x2) {
					break;
				}
				x += xInc; 
				D += M;
				if (D > dx) {
					y += yInc;
					D -= c;
				}
			}
		} else {
			c = 2 * dy;
			M = 2 * dx;
			while (true) {
				scan[y] = x;
				if (y == y2) {
					break;
				}
				y += yInc;
				D += M;
				if (D > dy) {
					x += xInc;
					D -= c;
				}
			}
		}
	}
	
	/**
	 *	A zero vector.
	 */
	public static final Vector2D ZERO = new Vector2D();
}