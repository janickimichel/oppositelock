package numfum.j2me.util;

/**
 *	Common utilities for manipulating byte arrays.
 */
public final class ByteUtils {
	/**
	 *	Will never need to instantiate this class.
	 */
	private ByteUtils() {}
	
	/**
	 *	Creates a new byte array containing an int.
	 *
	 *	@see intToBytes(byte[] data, int offset, long value)
	 */
	public static byte[] intToBytes(int value) {
		return intToBytes(new byte[4], 0, value);
	}
	
	/**
	 *	Stores an int at the specfied offset in a byte array. Ints are stored
	 *	in four consecutive bytes.
	 */
	public static byte[] intToBytes(byte[] data, int offset, int value) {
		data[offset++] = (byte) (value >>> 24);
		data[offset++] = (byte) (value >>> 16);
		data[offset++] = (byte) (value >>>  8);
		data[offset++] = (byte) (value >>>  0);
		return data;
	}
	
	/**
	 *	Reads an int from the specfied offset in a byte array. Ints are stored
	 *	in four consecutive bytes.
	 */
	public static int bytesToInt(byte[] data, int offset) {
		return ((data[offset++] & 0xFF) << 24)
			|  ((data[offset++] & 0xFF) << 16)
			|  ((data[offset++] & 0xFF) <<  8)
			|  ((data[offset++] & 0xFF) <<  0);
	}
	
	/**
	 *	Creates a new byte array containing a short.
	 *
	 *	@see shortToBytes(byte[] data, int offset, long value)
	 */
	public static byte[] shortToBytes(int value) {
		return shortToBytes(new byte[2], 0, value);
	}
	
	/**
	 *	Stores a short at the specfied offset in a byte array. Shorts are
	 *	stored in two consecutive bytes.
	 */
	public static byte[] shortToBytes(byte[] data, int offset, int value) {
		data[offset++] = (byte) (value >>>  8);
		data[offset++] = (byte) (value >>>  0);
		return data;
	}
	
	/**
	 *	Reads an unsigned short, returned as an int, from the specfied offset
	 *	in a byte array. Shorts are stored in two consecutive bytes.
	 */
	public static int bytesToUnsignedShort(byte[] data, int offset) {
		return ((data[offset++] & 0xFF) <<  8)
			|  ((data[offset++] & 0xFF) <<  0);
	}
	
	/**
	 *	Reads a short from the specfied offset in a byte array. Shorts are
	 *	stored in two consecutive bytes.
	 */
	public static short bytesToShort(byte[] data, int offset) {
		return (short) bytesToUnsignedShort(data, offset);
	}
	
	/**
	 *	Creates a new byte array containing a long.
	 *
	 *	@see longToBytes(byte[] data, int offset, long value)
	 */
	public static byte[] longToBytes(long value) {
		return longToBytes(new byte[8], 0, value);
	}
	
	/**
	 *	Stores a long at the specfied offset in a byte array. Longs are
	 *	stored in eight consecutive bytes.
	 */
	public static byte[] longToBytes(byte[] data, int offset, long value) {
		intToBytes(data, offset + 0, (int) (value >>> 32));
		intToBytes(data, offset + 4, (int) (value >>>  0));
		return data;
	}
	
	/**
	 *	Reads a long from the specfied offset in a byte array. Longs are
	 *	stored in eight consecutive bytes.
	 */
	public static long bytesToLong(byte[] data, int offset) {
		return (long) ((bytesToInt(data, offset + 0) & 0xFFFFFFFFL) << 32)
			|  (long) ((bytesToInt(data, offset + 4) & 0xFFFFFFFFL) <<  0);
	}
	
	/**
	 *	Stores a boolean at the specfied offset in a byte array.
	 */
	public static void booleanToByte(byte[] data, int offset, boolean value) {
		data[offset] = (byte) (value ? 1 : 0);
	}
	
	/**
	 *	Reads a boolean from the specfied offset in a byte array.
	 */
	public static boolean byteToBoolean(byte[] data, int offset) {
		return data[offset] != 0;
	}
	
	/**
	 *	Stores an array of booleans encoded in a byte array.
	 */
	public static byte[] booleansToBytes(byte[] data, int offset, boolean[] value, int length) {
		for (int n = 0; n < length; n++) {
			booleanToByte(data, offset++, value[n]);
		}
		return data;
	}
	
	/**
	 *	Reads an array of booleans encoded in a byte array.
	 */
	public static byte[] bytesToBooleans(byte[] data, int offset, boolean[] value, int length) {
		for (int n = 0; n < length; n++) {
			value[n] = byteToBoolean(data, offset++);
		}
		return data;
	}
	
	/**
	 *	Stores an array of ints encoded in a byte array.
	 */
	public static byte[] intsToBytes(byte[] data, int offset, int[] value, int length) {
		for (int n = 0; n < length; n++) {
			intToBytes(data, offset, value[n]);
			offset += 4;
		}
		return data;
	}
	
	/**
	 *	Stores an array of shorts encoded in a byte array.
	 */
	public static byte[] shortsToBytes(byte[] data, int offset, int[] value, int length) {
		for (int n = 0; n < length; n++) {
			shortToBytes(data, offset, value[n]);
			offset += 2;
		}
		return data;
	}
	
	/**
	 *	Reads an array of shorts encoded in a byte array.
	 */
	public static byte[] bytesToShorts(byte[] data, int offset, int[] value, int length) {
		for (int n = 0; n < length; n++) {
			value[n] = bytesToShort(data, offset);
			offset += 2;
		}
		return data;
	}
	
	/**
	 *	Stores the values from a multidimensional array of ints in a byte
	 *	array. No conversion is performed, with the values being truncated
	 *	if they fall outside of the byte range.
	 */
	public static byte[] multiBytesToBytes(byte[] data, int offset, int[][] value, int lengthA, int lengthB) {
		for (int a = 0; a < lengthA; a++) {
			for (int b = 0; b < lengthB; b++) {
				data[offset++] = (byte) value[a][b];
			}
		}
		return data;
	}
	
	/**
	 *	Reads the values from a multidimensional array of ints stored in a
	 *	byte array.
	 *
	 *	@see #multiBytesToBytes
	 */
	public static byte[] bytesToMultiBytes(byte[] data, int offset, int[][] value, int lengthA, int lengthB) {
		for (int a = 0; a < lengthA; a++) {
			for (int b = 0; b < lengthB; b++) {
				value[a][b] = data[offset++]; // & 0xFF;
			}
		}
		return data;
	}
}