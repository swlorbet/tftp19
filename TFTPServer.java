// TFTPServer.java
// This class is the server side of a simple TFTP server based on
// UDP/IP. The server receives a read or write packet from a client and
// sends back the appropriate response without any actual file transfer.
// One socket (69) is used to receive (it stays open) and another for each response. 

import java.io.*; 
import java.net.*;
import java.util.*;

public class TFTPServer{

	// UDP datagram packets and sockets used to send / receive
	private DatagramPacket receivePacket;
	private DatagramSocket receiveSocket;
	public static Controller controller;
	private int count;

	public TFTPServer()
	{
		try {
			// Construct a datagram socket and bind it to port 69
			// on the local host machine. This socket will be used to
			// receive UDP Datagram packets.
			receiveSocket = new DatagramSocket(69);
			receiveSocket.setSoTimeout(10000);
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
	}

	public void receiveAndSend()
	{
		byte[] data;

		int len, j=0;

		for(;;) { // loop forever

			// Construct a DatagramPacket for receiving packets up
			// to 516 bytes long (the length of the byte array).
			data = new byte[516];
			receivePacket = new DatagramPacket(data, data.length);

			// Block until a datagram packet is received from receiveSocket.
			try {
				receiveSocket.receive(receivePacket);
			} catch (SocketTimeoutException e) {
				if(controller.quit) {
					receiveSocket.close();
					System.exit(0);
				} else {
					this.receiveAndSend();
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			// Create a new client connection thread to send the DatagramPacket
			Thread clientConnection = 
					new TFTPClientConnection("Client Connection Thread", receivePacket, controller.getOutputMode());

			clientConnection.start();
			count=0;
			
		} // end of loop

	}

	public static void main( String args[] ) throws Exception{
		TFTPServer server = new TFTPServer();
		controller = new Controller(server);
		controller.start();
		server.receiveAndSend();
	}
}