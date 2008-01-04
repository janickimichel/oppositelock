package numfum.j2me.jsr.multiplayer;

import java.util.Vector;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

/**
 *	Multiplayer server. It searches and connects to waiting clients (running
 *	the corresponding Bluetooth service). Data is exchanged by calling
 *	<code>update()</code> or <code>updateAll()</code>.
 */
public final class MultiplayerServer implements MultiplayerConstants, DiscoveryListener, Runnable {
	/**
	 *	This phone's <code>LocalDevice</code>. Initialised once when the
	 *	service is first run.
	 */
	private LocalDevice dev;
	
	/**
	 *	Bluetooth <code>DiscoveryAgent</code> created every time the service
	 *	is run.
	 */
	private DiscoveryAgent agent;
	
	/**
	 *	Maximum number of connected devices supported by the BT stack.
	 */
	private final int maxCons;
	
	/**
	 *	Devices found during the discovery phase. All BT devices in the local
	 *	area should be here.
	 *
	 *	@see MultiplayerConstants.DISCOVERY_MODE
	 */
	private final Vector foundDevice;
	
	/**
	 *	Devices with the corresponding multiplayer service running.
	 */
	private final Vector foundRecord;
	
	/**
	 *	Pool of connection objects to handle each client.
	 */
	private final MultiplayerServerConnection[] con;
	
	/**
	 *	Number of the connection objects in use.
	 */
	private int numCons = 0;
	
	/**
	 *	UUID of the client service.
	 */
	private final UUID[] uuid = new UUID[1];
	
	/**
	 *	Attribute set to retrieve from the clients.
	 */
	private static final int[] ATTR_SET = new int[] {0x0000, 0x0004};
	
	/**
	 *	ID associated with the current service search. Used to cancel current
	 *	operations if the server is closed.
	 */
	private int searchID = 0;
	
	/**
	 *	Status of the server.
	 */
	private int status = STATUS_INACTIVE;
	
	/**
	 *	Initialise the server with send and receive buffers per client. Data
	 *	in these buffers is synchronised between the connected devices. It's
	 *	designed so the same service object can be reused when required.
	 *
	 *	@param sendBuffer one array per client of data to send
	 *	@param recvBuffer one array per client filled with received data
	 *
	 *	@see #start
	 */
	public MultiplayerServer(byte[][] sendBuffer, byte[][] recvBuffer) {
		maxCons = Math.min(Math.min(sendBuffer.length, recvBuffer.length),
			getIntProperty("bluetooth.connected.devices.max", MAX_CONNECTIONS));
		
		foundDevice = new Vector(32);
		foundRecord = new Vector(32);
		
		con = new MultiplayerServerConnection[maxCons];
		for (int n = 0; n < maxCons; n++) {
			con[n] = new MultiplayerServerConnection(sendBuffer[n], recvBuffer[n]);
		}
		
		uuid[0] = new UUID(SERVICE_UUID, false); 
	}
	
	/**
	 *	Clears the known devices (but doesn't close any connections).
	 */
	private void clear() {
		status = STATUS_INACTIVE;
		foundDevice.removeAllElements();
		foundRecord.removeAllElements();
		numCons = 0;
	}
	
	/**
	 *	Called by the <code>DiscoveryAgent</code> for each device encountered.
	 */
	public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
		if(!foundDevice.contains(btDevice)) {
			foundDevice.addElement(btDevice);
		}
	}
	
	/**
	 *	Called by the <code>DiscoveryAgent</code> at the end of device
	 *	descovery. Should return <code>INQUIRY_COMPLETED</code> if all went
	 *	well.
	 */
	public void inquiryCompleted(int discType) {
		synchronized(this) {
			notify();
		}
		if (DEBUG) {
			System.out.println(getInquiryDebugString(discType));
		}
	}
	
	/**
	 *	Called by the <code>DiscoveryAgent</code> for each multiplayer client
	 *	service found. Only the multiplayer service is being searched for, so
	 *	it's assumed only the first record is of interest.
	 */
	public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
		foundRecord.addElement(servRecord[0]);
	}
	
	/**
	 *	Called by the <code>DiscoveryAgent</code> at the end of service
	 *	descovery. Should return <code>SERVICE_SEARCH_COMPLETED</code> if all
	 *	went well.
	 */
	public void serviceSearchCompleted(int transID, int respCode) {
		synchronized(this) {
			notify();
		}
		if (DEBUG) {
			System.out.println(getServiceSearchDebugString(respCode));
		}
	}
	
	/**
	 *	Returns the number of clients found capable of connections.
	 */
	public int getDiscoveredSize() {
		return foundRecord.size();
	}
	
	/**
	 *	Returns the number of connections this server could handle.
	 */
	public int getMaxConnections() {
		return maxCons;
	}
	
	/**
	 *	Returns the number of clients currently connected to.
	 */
	public int getConnectionSize() {
		return numCons;
	}
	
	/**
	 *	Starts a new multiplayer server (in its own thread). The first phase
	 *	is the device discovery, where any local BT devices are found,
	 *	followed by a service search, where the previously found devices are
	 *	queried to see if the corresponding client service is running. Each
	 *	client then has a pooled worker thread devoted to it.
	 *	
	 *	@return <code>true</code> if starting the server was successful
	 *
	 *	@see MultiplayerServerConnection
	 */
	public boolean start() {
		if (status != STATUS_INACTIVE) {
			return false;
		}
		
		clear();
		try {
			if (dev == null) {
				dev = LocalDevice.getLocalDevice();
			}
			agent = dev.getDiscoveryAgent();
		} catch (Exception e) {
			if (DEBUG) {
				System.out.println("Error starting server thread: " + e);
			}
			return false;
		}
		
		new Thread(this).start();
		return true;
	}
	
	/**
	 *	Cancels any existing device or service search and closes any open
	 *	client connections.
	 */
	public void close() {
		if (agent != null) {
			agent.cancelInquiry(this);
			agent.cancelServiceSearch(searchID);
			agent = null;
		}
		clear();
		for (int n = 0; n < maxCons; n++) {
			con[n].close();
		}
	}
	
	/**
	 *	Returns the current status of the server. All server operations run in
	 *	its own thread, so once started this should be polled regularly to
	 *	check the connection progress.
	 */
	public int getStatus() {
		return status;
	}
	
	/**
	 *	Updates a single connected client. The data sent over the connection
	 *	is taken from the array <code>sendBuffer</code> specified at
	 *	instatiation.
	 *
	 *	@return <code>true</code> if the update was successful
	 */
	public boolean update(int index) {
		if (index < numCons) {
			return con[index].update();
		}
		return false;
	}
	
	/**
	 *	Updates all connected clients.
	 *
	 *	@return the number of successful updates
	 */
	public int updateAll() {
		int received = 0;
		for (int n = numCons - 1; n >= 0; n--) {
			if (con[n].update()) {
				received++;
			}
		}
		return received;
	}
	
	/**
	 *	Device and service discovery thread. Performs the lengthy searches in
	 *	the background.
	 */
	public void run() {
		status = STATUS_WAITING;
		try {
			dev.setDiscoverable(DiscoveryAgent.NOT_DISCOVERABLE);
			
			if (USE_PREKNOWN) {
				RemoteDevice[] preknown = agent.retrieveDevices(DiscoveryAgent.PREKNOWN);
				if (preknown != null) {
					for (int n = preknown.length - 1; n >= 0; n--) {
						foundDevice.addElement(preknown[n]);
					}
					if (DEBUG) {
						System.out.println("Found preknown devices: " + preknown.length);
					}
				}
			}
			
			synchronized(this) {
				agent.startInquiry(DISCOVERY_MODE, this);
				wait();
			}
			if (DEBUG) {
				System.out.println("Finished device discovery: " + foundDevice.size());
			}
			
			for (int n = foundDevice.size() - 1; n >= 0; n--) {
				synchronized(this) {
					searchID = agent.searchServices(ATTR_SET, uuid, (RemoteDevice) foundDevice.elementAt(n), this);
					wait();
				}
			}
			if (DEBUG) {
				System.out.println("Finished service discovery");
			}
			
			numCons = 0;
			for (int n = foundRecord.size() - 1; n >= 0; n--) {
				if (numCons < maxCons && con[numCons].open((ServiceRecord) foundRecord.elementAt(n))) {
					numCons++;
				}
			}
		} catch (Exception e) {
			if (DEBUG) {
				System.out.println("Error occurred during discovery: " + e);
			}
		}
		if (numCons > 0) {
			status = STATUS_CONNECTED;
		} else {
			status = STATUS_INACTIVE;
		}
	}
	
	/**
	 *	Retrieves a value from the local Bluetooth properties.
	 *
	 *	@param key property key
	 *	@param def default value for the property if it doesn't exist
	 */
	static int getIntProperty(String key, int def) {
		int val = def;
		try {
			val = Integer.parseInt(LocalDevice.getProperty(key));
		} catch (Exception e) {}
		return val;
	}
	
	/**
	 *	Returns a string corresponding with the
	 *	<code>inquiryCompleted()</code> device descovery result.
	 */
	static String getInquiryDebugString(int discType) {
		switch (discType) {
		case DiscoveryListener.INQUIRY_COMPLETED:
			return "Inquiry: completed";
		case DiscoveryListener.INQUIRY_TERMINATED:
			return "Inquiry: terminated";
		case DiscoveryListener.INQUIRY_ERROR:
			return "Inquiry: error";
		default:
			return "Inquiry: unknown response (" + discType + ")";
		}
	}
	
	/**
	 *	Returns a string corresponding with the
	 *	<code>serviceSearchCompleted()</code> result.
	 */
	static String getServiceSearchDebugString(int respCode) {
		switch (respCode) {
		case DiscoveryListener.SERVICE_SEARCH_DEVICE_NOT_REACHABLE:
			return "Service search: device not reachable";
		case DiscoveryListener.SERVICE_SEARCH_NO_RECORDS:
			return "Service search: no records";
		case DiscoveryListener.SERVICE_SEARCH_COMPLETED:
			return "Service search: complete";
		case DiscoveryListener.SERVICE_SEARCH_TERMINATED:
			return "Service search: terminated";
		case DiscoveryListener.SERVICE_SEARCH_ERROR:
			return "Service search: error";
		default:
			return "Service search: unknown response (" + respCode + ")";
		}
	}
}