package numfum.j2me.util;

import javax.microedition.lcdui.Canvas;

/**
 *	Maps key presses to a standard 4-way joystick, with the option to record
 *	and playback the player's input.
 */
public final class Joystick {
	/**
	 *	State of the joystick.
	 */
	public int state;
	
	/**
	 *	Size of the record buffer.
	 */
	private final int size;
	
	/**
	 *	Record buffer for the joystick state.
	 */
	private final byte[] memory;
	
	/**
	 *	Position in record buffer.
	 */
	private int tick = 0;
	
	/**
	 *	Creates a new joystick without recording capabilities.
	 */
	public Joystick() {
		this(0);
	}
	
	/**
	 *	Creates a new joystick capabable of recording the specified number of
	 *	game ticks..
	 */
	public Joystick(int size) {
		this.size = size;
		memory = new byte[size];
	}
	
	/**
	 *	Returns whether a button is pressed.
	 */
	public boolean isPressed(int button) {
		return (state & button) != 0;
	}
	
	/**
	 *	Sets recording or playback to the start.
	 */
	public void rewind() {
		tick = 0;
	}
	
	/**
	 *	Returns whether the previous recording was within the buffer's bounds.	
	 */
	public boolean isValid() {
		return tick < size;
	}
	
	/**
	 *	Note: this doesn't return a complete joystick state, just the buttons
	 *	that have been recorded. Softkeys and the like will still need testing
	 *	using the 'state' member variable.
	 */
	public int grabFrame() {
		if (tick < size) {
			return memory[tick++] = (byte) (state & RECORD_MASK);
		} else {
			return state;
		}
	}
	
	/**
	 *	Advances playback to the next frame.
	 */
	public int nextFrame() {
		if (tick < size) {
			return state = memory[tick++] & 0xFF;
		} else {
			return 0;
		}
	}
	
	/**
	 *	Resets the all the key states to off.
	 */
	public void reset() {
		state = 0;
	}
	
	/**
	 *	Loads the memory contents from another Joystick.
	 */
	public void load(Joystick that) {
		System.arraycopy(that.memory, 0, memory, 0, memory.length);
	}
	
	/**
	 *	Saves the contents of the memory plus the current recording position.
	 *
	 *	TODO: save only the number of bytes necessary
	 */
	public int save(byte[] data, int n) {
		ByteUtils.shortToBytes(data, n, tick);
		n += 2;
		System.arraycopy(memory, 0, data, n, memory.length);
		return n + memory.length;
	}
	
	/**
	 *	Loads the contents of the memory plus the current recording position.
	 *
	 *	TODO: load only the number of bytes necessary (see 'save')
	 */
	public int load(byte[] data, int n) {
		tick = ByteUtils.bytesToUnsignedShort(data, n);
		n += 2;
		System.arraycopy(data, n, memory, 0, memory.length);
		return n + memory.length;
	}
	
	/**
	 *	Called by the canvas with its key events.
	 */
	public boolean press(int action, int keycode, boolean status) {
		int flag = 0;
		switch (action) {
		case Canvas.LEFT:
			flag = BUTTON_L;
			break;
		case Canvas.RIGHT:
			flag = BUTTON_R;
			break;
		case Canvas.UP:
			flag = BUTTON_U;
			break;
		case Canvas.DOWN:
			flag = BUTTON_D;
			break;
		case Canvas.FIRE:
			flag = BUTTON_F;
			break;
		case Canvas.GAME_A:
			flag = BUTTON_A;
			break;
		case Canvas.GAME_B:
			flag = BUTTON_B;
			break;
		case Canvas.GAME_C:
			flag = BUTTON_X;
			break;
		case Canvas.GAME_D:
			flag = BUTTON_Y;
			break;
		default:
			switch (keycode) {
			case Canvas.KEY_NUM4:
				flag = BUTTON_L;
				break;
			case Canvas.KEY_NUM6:
				flag = BUTTON_R;
				break;
			case Canvas.KEY_NUM2:
				flag = BUTTON_U;
				break;
			case Canvas.KEY_NUM8:
				flag = BUTTON_D;
				break;
			case Canvas.KEY_NUM5:
				flag = BUTTON_F;
				break;
			case Canvas.KEY_NUM1:
				flag = BUTTON_A;
				break;
			case Canvas.KEY_NUM3:
				flag = BUTTON_B;
				break;
			case Canvas.KEY_STAR:
				flag = BUTTON_X;
				break;
			case Canvas.KEY_POUND:
				flag = BUTTON_Y;
				break;
			default:
				return false;
			}
		}
		if (status) {
			state |=  flag;
		} else {
			state &= ~flag;
		}
		return true;
	}
	
	public static final int BUTTON_L =   1;
	public static final int BUTTON_R =   2;
	public static final int BUTTON_U =   4;
	public static final int BUTTON_D =   8;
	public static final int BUTTON_F =  16;
	
	public static final int BUTTON_A =  32;
	public static final int BUTTON_B =  64;
	public static final int BUTTON_X = 128;
	public static final int BUTTON_Y = 256;
	
	/**
	 *	Mask for button states actually recorded.
	 */
	private static final int RECORD_MASK = BUTTON_L | BUTTON_R | BUTTON_U | BUTTON_D | BUTTON_F | BUTTON_A | BUTTON_B;
}