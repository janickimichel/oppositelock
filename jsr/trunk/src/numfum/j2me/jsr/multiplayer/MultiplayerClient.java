package numfum.j2me.jsr.multiplayer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import javax.bluetooth.DataElement;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.L2CAPConnectionNotifier;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

/**
 *	Multiplayer client. It starts up the game service and waits on incoming
 *	connections.
 */
public final class MultiplayerClient implements MultiplayerConstants, Runnable {
	/**
	 *	This phone's <code>LocalDevice</code>. Initialised once when the
	 *	service is first run.
	 */
	private LocalDevice dev;
	
	private final byte[] recvBuffer;
	private final byte[] sendBuffer;
	
	private final String server;
	
	private final DataElement pubBrowse;
	
	private boolean running = false;
	
	private Connection not = null;
	private Connection con = null;
	
	private L2CAPConnection l2capCon = null;
	
	private DataInputStream streamRecv = null;
	private DataOutputStream streamSend = null;
	
	private int errorCount = 0;
	
	public MultiplayerClient(byte[] sendBuffer, byte[] recvBuffer) {
		this.sendBuffer = sendBuffer;
		this.recvBuffer = recvBuffer;
		
		StringBuffer sb = new StringBuffer(128);
		if (USE_L2CAP) {
			sb.append("btl2cap");
		} else {
			sb.append("btspp");
		}
		sb.append("://localhost:");
		sb.append(SERVICE_UUID);
		sb.append(";name=");
		sb.append(SERVICE_NAME);
		sb.append(";master=false;encrypt=false;authenticate=false");
		if (USE_L2CAP) {
			sb.append(";TransmitMTU=");
			sb.append(Math.max(L2CAPConnection.MINIMUM_MTU, sendBuffer.length));
			sb.append(";ReceiveMTU=");
			sb.append(Math.max(L2CAPConnection.MINIMUM_MTU, recvBuffer.length));
		}
		server = sb.toString();
		
		if (PUBLIC_BROWSE) {
			pubBrowse = new DataElement(DataElement.DATSEQ);
			pubBrowse.addElement(new DataElement(DataElement.UUID, new UUID(0x1002)));
		} else {
			pubBrowse = null;
		}
	}
	
	public boolean start() {
		try {
			if (dev == null) {
				dev = LocalDevice.getLocalDevice();
			}
			/*
			 *	Set the discovery mode to the preferred one, but if it's GIAC
			 *	fall back to LIAC if it fails.
			 */
			if (!dev.setDiscoverable(DISCOVERY_MODE) && DISCOVERY_MODE == DiscoveryAgent.GIAC) {
				 dev.setDiscoverable(DiscoveryAgent.LIAC);
			}
		} catch (Exception e) {
			if (DEBUG) {
				System.out.println("Error starting client thread: " + e);
			}
			return false;
		}
		if (running) {
			return false;
		}
		
		new Thread(this).start();
		
		return true;
	}
	
	public void close() {
		running = false;
		if (!USE_L2CAP) {
			if (streamRecv != null) {
				try {
					streamRecv.close();
				} catch (Exception e) {}
				streamRecv = null;
			}
			if (streamSend != null) {
				try {
					streamSend.close();
				} catch (Exception e) {}
				streamSend = null;
			}
		}
		if (con != null) {
			try {
				con.close();
			} catch (Exception e) {}
			con = null;
		}
		if (not != null) {
			try {
				not.close();
			} catch (Exception e) {}
			not = null;
		}
		try {
			dev.setDiscoverable(DiscoveryAgent.NOT_DISCOVERABLE);
		} catch (Exception e) {}
	}
	
	public boolean update() {
		if (running && con != null) {
			try {
				if (USE_L2CAP) {
					l2capCon.send(sendBuffer);
				} else {
					streamSend.write(sendBuffer);
					streamSend.flush();
				}
				return true;
			} catch (Exception e) {
				errorCount++;
			}
		}
		return false;
	}
	
	public int getErrorCount() {
		return errorCount;
	}
	
	public int getStatus() {
		if (not != null) {
			if (running && con != null) {
				return STATUS_CONNECTED;
			}
			return STATUS_WAITING;
		}
		return STATUS_INACTIVE;
	}
	
	public void run() {
		running = false;
		
		try {
			not = Connector.open(server);
			
			if (PUBLIC_BROWSE) {
				ServiceRecord record = dev.getRecord(not);
				if (record != null) {
					record.setAttributeValue(0x0005, pubBrowse);
					record.setAttributeValue(0x0100, new DataElement(DataElement.STRING, SERVICE_NAME));
					record.setAttributeValue(0x0101, new DataElement(DataElement.STRING, SERVICE_DESC));
					record.setAttributeValue(0x0102, new DataElement(DataElement.STRING, SERVICE_VEND));
				}
				dev.updateRecord(record);
			}
			
			if (USE_L2CAP) {
				con = ((L2CAPConnectionNotifier) not).acceptAndOpen();
				
				l2capCon = (L2CAPConnection) con;
			} else {
				con = ((StreamConnectionNotifier) not).acceptAndOpen();
				
				streamRecv = ((StreamConnection) con).openDataInputStream();
				streamSend = ((StreamConnection) con).openDataOutputStream();
			}
			dev.setDiscoverable(DiscoveryAgent.NOT_DISCOVERABLE);
		} catch (Exception e) {
			if (DEBUG) {
				System.out.println("Error occurred opening connection: " + e);
			}
		}
		
		errorCount = 0;
		
		if (con != null) {
			running = true;
		}
		
		if (DEBUG) {
			if (running) {
				System.out.println("Client thread running");
			} else {
				System.out.println("Client thread NOT started");
			}
		}
		while (running) {
			try {
				if (USE_L2CAP) {
					l2capCon.receive(recvBuffer);
				} else {
					streamRecv.readFully(recvBuffer);
				}
				errorCount = 0;
			} catch (Exception e) {
				errorCount++;
			}
			if (errorCount > MAX_ERRORS) {
				running = false;
			}
		}
		close();
	}
}