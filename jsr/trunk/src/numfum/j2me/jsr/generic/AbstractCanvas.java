package numfum.j2me.jsr.generic;

import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;

/**
 *	Does absolutely nothing except ease porting between different APIs.
 */
public abstract class AbstractCanvas extends Canvas {
	/**
	 *	Midlet's display object (used for <code>vibrate()</code>.
	 */
	private final Display display;
	
	/**
	 *	Creates a new <code>AbstractCanvas</code> with the specified midlet as
	 *	its parent.
	 */
	public AbstractCanvas(MIDlet parent, boolean fullscreen) {
		if (fullscreen) {
			setFullScreenMode(true);
		}
		display = Display.getDisplay(parent);
	}
	
	/**
	 *	Calls <code>setFullScreenMode()</code> if the parent class has such a
	 *	method. Originally the full screen call was made in the constructor
	 *	but that causes problems with the SE P9xx series.
	 */
	public final void setFullScreen(boolean mode) {
		setFullScreenMode(mode);
	}
	
	/**
	 *	Opperates the device's vibrator.
	 *
	 *	@return <code>true</code> if the request was successful
	 */
	public final boolean buzz(int duration) {
		return display.vibrate(duration);
	}
}