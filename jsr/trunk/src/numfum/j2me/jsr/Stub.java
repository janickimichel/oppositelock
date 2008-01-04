package numfum.j2me.jsr;

import java.io.IOException;
import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Display;
import java.util.Timer;
import java.util.TimerTask;

/**
 *	Generic midlet stub. This is designed to maintain a constant framerate on
 *	a <code>GameCanvas</code>.
 */
public final class Stub extends MIDlet implements Constants, Runnable {
	/**
	 *	Whether to build the midlet using two threads to control the framerate
	 *	or a single Timer. Older phones (S60 DP1) seemed to get a smoother
	 *	result with two threads.
	 */
	private static final boolean BUILD_USING_DUAL_THREADS = false;
	
	/**
	 *	The midlet's Display object.
	 */
	private Display display;
	
	/**
	 *	A single canvas which both controls the game and displays it.
	 */
	private GameCanvas game;
	
	/**
	 *	Used to control the framerate.
	 *
	 *	@see #BUILD_USING_DUAL_THREADS
	 */
	private Timer kick;
	
	/**
	 *	A thread whose task is to wake the game thread every frame.
	 *
	 *	@see #BUILD_USING_DUAL_THREADS
	 */
	private final Thread kickThread;
	
	/**
	 *	Thread in which the game runs.
	 *
	 *	@see #BUILD_USING_DUAL_THREADS
	 */
	private Thread gameThread;
	
	/**
	 *	TimerTask (used as a Runnable with dual threads) for the frame update.
	 */
	private GameTask gt;
	
	/**
	 *	Number of milliseconds between each frame.
	 */
	private final int frameDelay;
	
	/**
	 *	Number of milliseconds elapsed before it's decided that the game
	 *	should pause. The actual amound depends on the phone (the value should
	 *	be longer that it takes the game to load). Set to zero to disable.
	 *
	 *	@see #frameTime
	 */
	private final int idleLength;
	
	/**
	 *	Stores thetime in milliseconds per frame. Used to work around some
	 *	phones not calling <code>pauseApp()</code> on incoming calls.
	 */
	private long frameTime;
	
	/**
	 *	Previous value of <code>frameTime</code>.
	 */
	private long frameLast;
	
	/**
	 *	Creates the new midlet stub instance.
	 */
	public Stub() {
		if (BUILD_USING_DUAL_THREADS) {
			kickThread = new Thread(this);
			kickThread.setPriority(Thread.MAX_PRIORITY);
		} else {
			kickThread = null;
		}
		
		frameDelay = getAppInt("Timer-Tweak", 0) + FRAME_DELAY;
		idleLength = getAppInt("Pause-Fix", 0);
	}
	
	/**
	 *	Wrapper around <code>getAppProperty</code>.
	 *
	 *	@param key property key
	 *	@param def default value to return if no property exists
	 *	@return the requested application property or a default value
	 */
	private int getAppInt(String key, int def) {
		int val = def;
		try {
			val = Integer.parseInt(getAppProperty(key));
		} catch (Exception e) {}
		return val;
	}
	
	/*
	 *	Initialises the game instance and timers.
	 */
	public void startApp() {
		if (game == null) {
			int forcedW = getAppInt("Forced-viewW", 0);
			int forcedH = getAppInt("Forced-viewH", 0);
			try {
				game = new GameCanvas(forcedW, forcedH, this);
			} catch (IOException e) {}
		}
		game.start();
		
		display = Display.getDisplay(this);
		display.setCurrent(game);
		
		if (BUILD_USING_DUAL_THREADS) {
			if (!kickThread.isAlive()) {
				 kickThread.start();
			}
		} else {
			if (kick == null) {
				kick = new Timer();
				kick.schedule(new GameTask(), 0, frameDelay);
			}
		}
	}
	
	/*
	 *	Cancels the timers and stops the game.
	 */
	public void destroyApp(boolean unconditional) {
		if (!BUILD_USING_DUAL_THREADS) {
			kick.cancel();
		}
		
		if (game.isRunning()) {
			game.stop();
		}
		notifyDestroyed();
	}
	
	/*
	 *	Controls the frame rate.
	 */
	public void run() {
		if (BUILD_USING_DUAL_THREADS) {
			gt = new GameTask();
			
			gameThread = new Thread(gt);
			gameThread.setPriority(Thread.NORM_PRIORITY);
			gameThread.start();
			
			while (game.isRunning()) {
				try {
					Thread.sleep(frameDelay);
					synchronized (gt) {
						gt.notify();
					}
				} catch (Exception e) {}
			}
			
			destroyApp(true);
		}
	}
	
	/**
	 *	Signals the game to pause. It stops the <code>Timer</code> updating,
	 *	if one is being used, and calls the canvas's <code>hideNotify</code>.
	 */
	public void pauseApp() {
		if (!BUILD_USING_DUAL_THREADS) {
			kick.cancel();
			kick = null;
		}
		game.hideNotify();
	}
	
	/**
	 *	Handles maintaining the framerate.
	 */
	private final class GameTask extends TimerTask {
		/**
		 *	Flag used to ensure <code>setFullScreen()</code> is called once.
		 */
		private boolean fullscreen = DELAY_FULLSCREEN_SETTING;
		
		public void run() {
			/******** Note: workaround doesn't work with dual threads *******/
			
			/*
			 *	Work around for phones which call neither pauseApp() nor
			 *	hideNotify() when receiving calls or messages. If it's decided
			 *	that the idle time between frame updates exceeds a set amount
			 *	the game is paused. LG and Samsung phones usually need this.
			 */
			if (idleLength > 0) {
				frameLast = frameTime;
				frameTime = System.currentTimeMillis();
				if (frameTime - frameLast > idleLength && frameLast != 0) {
					/*
					 *	Note that we can't call pauseApp() ourselves because
					 *	the matching startApp() will never be called when the
					 *	control is returned to the application.
					 */
					game.hideNotify();
				}
			}
			
			/*
			 *	Workaround for a bug in the P9xx series that causes the
			 *	application to exit if the full screen mode is set in the
			 *	canvas constructor.
			 */
			if (fullscreen) {
				fullscreen = false;
				game.setFullScreen(true);
			}
			
			if (BUILD_USING_DUAL_THREADS) {
				while (game.isRunning()) {
					game.run();
					try {
						synchronized (gt) {
							wait();
						}
					} catch (Exception e) {}
				}
			} else {
				if (game.isRunning()) {
					game.run();
				} else {
					destroyApp(true);
				}
			}
		}
	}
}


