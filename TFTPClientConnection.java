/* 
 * TFTPClientConnection.java
 * This class is the client connection side of a multi-threaded TFTP 
 * server based on UDP/IP. It receives a data or ack packet from the
 * server listener and sends it on a newly created socket to the port
 * provided in the packet. The socket is closed after the packet is sent.
 */

import java.io.*;
import java.net.*;
import java.util.Arrays;

public class TFTPClientConnection extends Thread {

	// types of requests we can receive
	public static enum Request { READ, WRITE, ERROR};
	// responses for valid requests
	public static final byte[] readResp = {0, 3, 0, 1};
	public static final byte[] writeResp = {0, 4, 0, 0};

	// UDP datagram packet and socket used to send 
	private DatagramSocket sendReceiveSocket;
	private DatagramPacket sendPacket, receivePacket;
	private String outputMode;

	public TFTPClientConnection(String name, DatagramPacket packet, String outputMode) {
		super(name); // Name the thread
		receivePacket = packet;
		this.outputMode = outputMode;

		// Construct a datagram socket and bind it to any available port
		// on the local host machine. This socket will be used to
		// send a UDP Datagram packet.
		try {
			sendReceiveSocket = new DatagramSocket();
			//sendReceiveSocket.setSoTimeout(10000);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	public void run() {
		Controller controller = TFTPServer.controller;
		byte[] data = receivePacket.getData();
		byte[] response = new byte[4];

		Request req; // READ, WRITE or ERROR

		String filename = "";
		String mode;
		int len = receivePacket.getLength();
		int j=0, k=0;


		// If it's a read, send back DATA (03) block 1
		// If it's a write, send back ACK (04) block 0
		// Otherwise, ignore it
		if (data[0]!=0) req = Request.ERROR; // bad
		else if (data[1]==1) req = Request.READ; // could be read
		else if (data[1]==2) req = Request.WRITE; // could be write
		else req = Request.ERROR; // bad

		if (req!=Request.ERROR) { // check for filename
			// search for next all 0 byte
			for(j=2;j<len;j++) {
				if (data[j] == 0) break;
			}
			if (j==len) req=Request.ERROR; // didn't find a 0 byte
			if (j==2) req=Request.ERROR; // filename is 0 bytes long
			// otherwise, extract filename
			filename = new String(data,2,j-2);
		}

		if(req!=Request.ERROR) { // check for mode
			// search for next all 0 byte
			for(k=j+1;k<len;k++) { 
				if (data[k] == 0) break;
			}
			if (k==len) req=Request.ERROR; // didn't find a 0 byte
			if (k==j+1) req=Request.ERROR; // mode is 0 bytes long
			mode = new String(data,j,k-j-1);
		}

		if(k!=len-1) req=Request.ERROR; // other stuff at end of packet        

		//Create instance to handle file operations
		TFTPReadWrite fileHandler;
		if (req==Request.WRITE) {
			fileHandler = new TFTPReadWrite(filename, "WRITE", this, "Client Connection");
		} else {
			fileHandler = new TFTPReadWrite(filename, "READ", this, "Client Connection");
		}

		// Create a response.
		if (req==Request.READ) { // for Read it's 0301
			response = new byte[516];
			System.arraycopy(readResp, 0, response, 0, 4);
			System.arraycopy(fileHandler.readFileBytes(512), 0, response, 4, 512);
		} else if (req==Request.WRITE) { // for Write it's 0400
			response = writeResp;
		} else { // it was invalid, just quit
			throw new RuntimeException("Not yet implemented");
		}

		// Construct a datagram packet that is to be sent to a specified port
		// on a specified host.
		// The arguments are:
		//  data - the packet data (a byte array). This is the response.
		//  receivePacket.getLength() - the length of the packet data.
		//     This is the length of the msg we just created.
		//  receivePacket.getAddress() - the Internet address of the
		//     destination host. Since we want to send a packet back to the
		//     client, we extract the address of the machine where the
		//     client is running from the datagram that was sent to us by
		//     the client.
		//  receivePacket.getPort() - the destination port number on the
		//     destination host where the client is running. The client
		//     sends and receives datagrams through the same socket/port,
		//     so we extract the port that the client used to send us the
		//     datagram, and use that as the destination port for the TFTP
		//     packet.
		sendPacket = new DatagramPacket(response, response.length,
				receivePacket.getAddress(), receivePacket.getPort());

		System.out.println("Server: Sending packet:");
		if (outputMode.equals("verbose")){
			System.out.println("To host: " + sendPacket.getAddress());
			System.out.println("Destination host port: " + sendPacket.getPort());
			len = sendPacket.getLength();
			System.out.println("Length: " + len);
			System.out.println("Containing: ");
			if (req==Request.WRITE) {
				for (j=0;j<len;j++) {
					System.out.println("byte " + j + " " + response[j]);
				}
			}
		}

		int i = 1;
		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		if (outputMode.equals("verbose")){
			System.out.println("Server: packet sent using port " + sendReceiveSocket.getLocalPort());
			System.out.println();
		}
		boolean quit = false;
		sendAndReceive:
			while (true) {

				try {
					// Block until a datagram is received via sendReceiveSocket.
					sendReceiveSocket.receive(receivePacket);
				} catch (SocketTimeoutException e) {
					if(controller.quit) {
						sendReceiveSocket.close();
						System.exit(0);
					}
				} catch(IOException e) {
					e.printStackTrace();
					System.exit(1);
				}

				// Process the received datagram.
				System.out.println("Server: Packet received:");
				if (controller.getOutputMode().equals("verbose")){
					System.out.println("From host: " + receivePacket.getAddress());
					System.out.println("Host port: " + receivePacket.getPort());
					len = receivePacket.getLength();
					System.out.println("Length: " + len);
					int packetNo = (int) ((response[2] << 8) & 0xff) | (response[3] & 0xff);
					System.out.println("Packet No.: " + packetNo);
					for (j=0;j<len;j++) {
						System.out.println("byte " + j + " " + receivePacket.getData()[j]);
					}
				}
				
				 if (req == Request.READ && quit) 
			    	   break sendAndReceive;

				if(req == Request.READ) {
					int length = 512;
					if (i == fileHandler.getNumSections())
						length = fileHandler.getFileLength() - ((fileHandler.getNumSections()-1) * 512);
					response = new byte[516];
					response[0] = 0;
					response[1] = 3;
					response[2] = (byte) ((i >> 8)& 0xff);
					response[3] = (byte) (i & 0xff);
					System.arraycopy(fileHandler.readFileBytes(512), 0, response, 4, length);
					len = length+4;
					if(i+1 >= fileHandler.getNumSections() )
			    		   quit = true;
				} else if(req == Request.WRITE) {
					response = new byte[4];
					response[0] = 0;
					response[1] = 4;
					response[2] = data[2];
					response[3] = data[3];
					len = 4;
					fileHandler.writeFilesBytes(Arrays.copyOfRange(receivePacket.getData(), 4, receivePacket.getLength()));
					if (receivePacket.getLength() < 516)
						quit = true;
				}

				sendPacket = new DatagramPacket(response, response.length,
						receivePacket.getAddress(), receivePacket.getPort());

				System.out.println("Server: Sending packet:");
				if (outputMode.equals("verbose")){
					System.out.println("To host: " + sendPacket.getAddress());
					System.out.println("Destination host port: " + sendPacket.getPort());
					len = sendPacket.getLength();
					System.out.println("Length: " + len);
					System.out.println("Containing: ");
					for (j=0;j<len;j++) {
						System.out.println("byte " + j + " " + response[j]);
					}
				}

				// Send the datagram packet to the server via the send socket.
				try {
					sendReceiveSocket.send(sendPacket);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				if (outputMode.equals("verbose")){
					System.out.println("Server: packet sent using port " + sendReceiveSocket.getLocalPort());
					System.out.println();
				}

				if (quit) break sendAndReceive;
				i++;
			}
		System.out.println("File transfer complete.");
		// We're finished with this socket, so close it.
		sendReceiveSocket.close();
	}
}