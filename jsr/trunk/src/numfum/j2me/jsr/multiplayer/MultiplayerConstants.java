package numfum.j2me.jsr.multiplayer;

import javax.bluetooth.DiscoveryAgent;

/**
 *	Constants shared by both Bluetooth client and server implementations. The
 *	compile time options of interest are <code>USE_L2CAP</code> to choose
 *	which protocol and <code>DISCOVERY_MODE</code> to choose between LIAC and
 *	GIAC.
 */
public interface MultiplayerConstants {
	/**
	 *	Print conntection details to stdout.
	 */
	public static final boolean DEBUG = false;
	
	/**
	 *	Use L2CAP instead of SPP. L2CAP is theoretically faster but SPP
	 *	appears to be compitible with more handsets.
	 */
	public static final boolean USE_L2CAP = false;
	
	/**
	 *	Sets the discovery mode used by the devices. LIAC is faster but GIAC
	 *	appears to be compatible with more handsets.
	 */
	public static final int DISCOVERY_MODE = DiscoveryAgent.GIAC;
	
	/**
	 *	Allow pre-known devices as well as performing a search.
	 */
	public static final boolean USE_PREKNOWN = true;
	
	/**
	 *	Maximum connections supported by the server.
	 */
	public static final int MAX_CONNECTIONS = 7;
	
	/**
	 *	Whether to add the 'public browse' info to the service record.
	 */
	public static final boolean PUBLIC_BROWSE = false;
	
	/**
	 *	Number of acceptable errors before dropping the connection.
	 */
	public static final int MAX_ERRORS = 5;
	
	/**
	 *	Client/server state. The link isn't active.
	 */
	public static final int STATUS_INACTIVE = 0;
	
	/**
	 *	Client/server state. The link is either not yet ready, negotiating,
	 *	or otherwise waiting to finalise the connection.
	 */
	public static final int STATUS_WAITING = 1;
	
	/**
	 *	Client/server state. The link is up and ready.
	 */
	public static final int STATUS_CONNECTED = 2;
	
	/**
	 *	Service record data. UUID by which the multiplayer service is found.
	 */
	public static final String SERVICE_UUID = "000000000000100080006E666A6A7372";
	
	/**
	 *	Service record data. Service name available to browsers.
	 */
	public static final String SERVICE_NAME = "JSR";
	
	/**
	 *	Service record data. Service description.
	 */
	public static final String SERVICE_DESC = "Multiplayer Server";
	
	/**
	 *	Service record data. Service vendor name.
	 */
	public static final String SERVICE_VEND = "Numfum Ltd";
}
