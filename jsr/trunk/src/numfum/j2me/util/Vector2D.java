package numfum.j2me.util;

/**
 *	Fixed point 2D vector class.
 */
public final class Vector2D implements Comparable {
	public int x = 0;
	public int y = 0;
	
	public Vector2D() {}
	
	public Vector2D(int x, int y) {
		set(x, y);
	}
	
	public Vector2D(Vector2D that) {
		set(that);
	}
	
	public void set(int x, int y) {
		this.x = x;
		this.y = y;
	}
	
	public void set(Vector2D that) {
		this.x = that.x;
		this.y = that.y;
	}
	
	public void setPolar(int mag, int dir) {
		x = Fixed.mul(mag, Fixed.cos(dir));
		y = Fixed.mul(mag, Fixed.sin(dir));
	}
	
	public void setToSum(Vector2D a, Vector2D b) {
		x = a.x + b.x;
		y = a.y + b.y;
	}
	
	public void setToDif(Vector2D a, Vector2D b) {
		x = a.x - b.x;
		y = a.y - b.y;
	}
	
	public void setToDiv(Vector2D that, int n) {
		this.x = Fixed.div(that.x, n);
		this.y = Fixed.div(that.y, n);
	}
	
	public void setToMul(Vector2D that, int n) {
		this.x = Fixed.mul(that.x, n);
		this.y = Fixed.mul(that.y, n);
	}
	
	public void add(int x, int y) {
		this.x += x;
		this.y += y;
	}
	
	public void add(Vector2D that) {
		this.x += that.x;
		this.y += that.y;
	}
	
	public void addPolar(int mag, int dir) {
		x += Fixed.mul(mag, Fixed.cos(dir));
		y += Fixed.mul(mag, Fixed.sin(dir));
	}
	
	public void sub(int x, int y) {
		this.x -= x;
		this.y -= y;
	}
	
	public void sub(Vector2D that) {
		this.x -= that.x;
		this.y -= that.y;
	}
	
	public void subPolar(int mag, int dir) {
		x -= Fixed.mul(mag, Fixed.cos(dir));
		y -= Fixed.mul(mag, Fixed.sin(dir));
	}
	
	public void mul(int n) {
		x = Fixed.mul(x, n);
		y = Fixed.mul(y, n);
	}
	
	public void div(int n) {
		x = Fixed.div(x, n);
		y = Fixed.div(y, n);
	}
	
	public void mulScalar(int n) {
		x *= n;
		y *= n;
	}
	
	public void divScalar(int n) {
		x /= n;
		y /= n;
	}
	
	public int mag() {
		return Fixed.sqrt(magSquared());
	}
	
	public int magSquared() {
		return Fixed.mul(x, x) + Fixed.mul(y, y);
	}
	
	public int dir() {
		return Fixed.atan(x, y);
	}
	
	public void normalise() {
		int mag = mag();
		if (mag > 0) {
			div(mag);
		} else {
			set(0, 0);
		}
	}
	
	/**
	 *	Clamps the vector's magnitude.
	 */
	public void clampMag(int n) {
		if (mag() > n) {
			setPolar(n, dir());
		}
	}
	
	public int dot(Vector2D that) {
		return Fixed.mul(this.x, that.x) + Fixed.mul(this.y, that.y);
	}
	
	public boolean equals(Object obj) {
		try {
			return equals((Vector2D) obj);
		} catch (ClassCastException e) {
			return false;
		}
	}
	
	/**
	 *	Checks this Vector2D's axes against another for equality.
	 */
	public boolean equals(Vector2D that) {
		return this == that || (this.x == that.x && this.y == that.y);
	}
	
	public boolean equals(int x, int y) {
		return this.x == x && this.y == y;
	}
	
	public int hashCode() {
		return (((y >> Fixed.FIXED_POINT) << 16) | (x >> Fixed.FIXED_POINT));
	}
	
	public int compareTo(Object obj) {
		return this.magSquared() - ((Vector2D) obj).magSquared();
	}
	
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
	 *	Deducts whether this Vector2D, taken as a point, is inside the polygon
	 *	represented by the passed array of points.
	 *
	 *	The algorthim is adapted from code by Wm. Randolph Franklin in the
	 *	comp.graphics.algorithms FAQ, section 2.03.
	 */
	public boolean isInPoly(Vector2D[] poly) {
		return isInPoly(poly, poly.length);
	}
	
	/**
	 *	As per isInPoly(Vector2D[] poly) but the number of actual used points
	 *	in the poly is specified.
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
	
	/*public String toString() {
		return "[x: " + x + ", y: " + y + "]";
	}*/
	
	/*
	 *	Temporary variables used in point nearest line calculations.
	 */
	private static final Vector2D unitB = new Vector2D();
	private static final Vector2D tempB = new Vector2D();
	private static final Vector2D unitP = new Vector2D();
	private static final Vector2D tempP = new Vector2D();
	
	/**
	 *	Given a line AB, and a point P, calculates the nearest point on that
	 *	line, storing the result in R and returning the distance squared
	 *	between PR. Not thread safe due to the static Vector2D objects used
	 *	during the calculations.
	 *
	 *	Uses eight multiplies and two divides.
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
	 *	As above but with B as a unit vector and scaledB precalculated as:
	 *	
	 *		scaledB.setToDiv(B, B.magSquared());
	 *
	 *	O is the origin (point A above), R is used to store the result.
	 *
	 *	Uses six multiplies.
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
	 *	A Vector2D at 0,0.
	 */
	public static final Vector2D ZERO = new Vector2D();
}