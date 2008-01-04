package numfum.j2me.jsr.multiplayer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Enumeration;
import javax.bluetooth.DataElement;
import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

/**
 *	Worker thread for each mutltiplayer connection. These are pooled, with one
 *	being used per connected client. If sending the data encounters more
 *	errors than thought necessary the link is dropped.
 */
public final class MultiplayerServerConnection implements MultiplayerConstants, Runnable {
	/**
	 *	Buffer into which the client's data is put. This holds data placed in
	 *	the client's <code>sendBuffer</code> on the other end of the link.
	 */
	private final byte[] recvBuffer;
	
	/**
	 *	Buffer holding data sent to the client. On the other end of the link
	 *	the client's <code>recvBuffer</code> is filled with this data.
	 */
	private final byte[] sendBuffer;
	
	/**
	 *	GCF connection to the client service.
	 */
	private Connection con = null;
	
	/**
	 *	L2CAP connection, if it's the desired protocol.
	 */
	private L2CAPConnection l2capCon = null;
	
	/**
	 *	BTSPP connection input stream, if it's the desired protocol.
	 */
	private DataInputStream streamRecv = null;
	
	/**
	 *	BTSPP connection output stream, if it's the desired protocol.
	 */
	private DataOutputStream streamSend = null;
	
	/**
	 *	Whether the thread is still running.
	 */
	private boolean running = false;
	
	/**
	 *	Number of errors this session.
	 */
	private int errorCount = 0;
	
	/**
	 *	UUID for L2CAP.
	 */
	private static final UUID L2CAP_UUID = new UUID("0100", true);
	
	/**
	 *	UUID for the Serial Port Profile.
	 */
	private static final UUID BTSPP_UUID = new UUID("0003", true);
	
	/**
	 *	Used to composing the client URL.
	 */
	private static StringBuffer conBuf = new StringBuffer(128);
	
	/**
	 *	Creates server connection worker thread with the supplied buffers.
	 *
	 *	@param sendBuffer data to be sent to the client
	 *	@param recvBuffer data received from the client
	 */
	MultiplayerServerConnection(byte[] sendBuffer, byte[] recvBuffer) {
		this.sendBuffer = sendBuffer;
		this.recvBuffer = recvBuffer;
	}
	
	/**
	 *	Opens a connection (in its own thread) given the client's service
	 *	record. This tries two methods to get a connection, the first being
	 *	to simply call <code>ServiceRecord.getConnectionURL()</code> for the
	 *	client's connection string, the second by trying to construct it
	 *	directly from values in the service record. This should work around
	 *	differences in Nokia and Sony Ericsson implementations.
	 *
	 *	@see #getConnectionURL
	 *
	 *	@return <code>true</code> if the connection was successful
	 */
	public boolean open(ServiceRecord srvRec) {
		if (open(srvRec.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false))) {
			return true;
		} else {
			return open(getConnectionURL(srvRec));
		}
	}
	
	/**
	 *	Opens a connection (in its own thread) using a URL.
	 *
	 *	@return <code>true</code> if the connection was successful
	 */
	public boolean open(String url) {
		close();
		try {
			con = Connector.open(url);
			if (USE_L2CAP) {
				l2capCon = (L2CAPConnection) con;
			} else {
				streamRecv = ((StreamConnection) con).openDataInputStream();
				streamSend = ((StreamConnection) con).openDataOutputStream();
			}
		} catch (Exception e) {
			return false;
		}
		
		new Thread(this).start();
		
		return true;
	}
	
	/**
	 *	Returns <code>true</code> is a client is connected.
	 */
	public boolean isConnected() {
		return running && con != null;
	}
	
	/**
	 *	Closes the client connection.
	 */
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
	}
	
	/**
	 *	Sends data in from <code>sendBuffer</code> to the client connection.
	 *
	 *	@return <code>true</code> if sending was successful
	 */
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
	
	/**
	 *	Returns the number of errors encountered this session.
	 */
	public int getErrorCount() {
		return errorCount;
	}
	
	/**
	 *	Reads the blocking client data.
	 */
	public void run() {
		errorCount = 0;
		
		if (con != null) {
			running = true;
		}
		
		if (DEBUG) {
			if (running) {
				System.out.println("Server thread running");
			} else {
				System.out.println("Server thread NOT started");
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
	
	/**
	 *	Implementation of <code>ServiceRecord.getConnectionURL()</code> which
	 *	works around Sony Ericsson/Nokia incompatibilities.
	 *
	 *	@return URL for GCF methods
	 */
	private static String getConnectionURL(ServiceRecord srvRec) {
		synchronized (conBuf) {
			conBuf.setLength(0);
			if (USE_L2CAP) {
				conBuf.append("btl2cap");
			} else {
				conBuf.append("btspp");
			}
			conBuf.append("://");
			conBuf.append(srvRec.getHostDevice().getBluetoothAddress());
			conBuf.append(":");
			conBuf.append(findPort(srvRec.getAttributeValue(0x0004), USE_L2CAP ? L2CAP_UUID : BTSPP_UUID));
			conBuf.append(";master=false;encrypt=false;authenticate=false");
			return conBuf.toString();
		}
	}
	
	/**
	 *	Given a service record's attribute returns the relevant port number.
	 *
	 *	@param element service record attribute
	 *	@param service UUID of the required service
	 *	@return port number or -1 if none is found
	 */
	private static int findPort(DataElement element, UUID service) {
		int port = -1;
		switch (element.getDataType()) {
		case DataElement.DATALT:
		case DataElement.DATSEQ:
			Enumeration en = (Enumeration) element.getValue();
			boolean flag = false;
			while (en.hasMoreElements()) {
				DataElement child = (DataElement) en.nextElement();
				switch (child.getDataType()) {
				case DataElement.DATALT:
				case DataElement.DATSEQ:
					port = findPort(child, service);
					if (port >= 0) {
						return port;
					}
					break;
				case DataElement.UUID:
					if (service.equals(child.getValue())) {
						flag = true;
					}
					break;
				case DataElement.U_INT_1:
					if (USE_L2CAP) {
						break;
					} else {
						port = (int) child.getLong();
					}
					break;
				case DataElement.U_INT_2:
					if (USE_L2CAP) {
						port = (int) child.getLong();
					}
					break;
				}
			}
			if (flag && port >= 0) {
				return port;
			}
		default:
			return -1;
		}
	}
}