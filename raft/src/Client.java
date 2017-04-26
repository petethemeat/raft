import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

//
public class Client {
	static int numServer;
	// For tcp transfers
	static Socket tcpSocket = null;
	static Scanner inStream = null;
	static PrintStream outStream = null;
	// For udp transfers
	static byte[] sBuffer;
	static ArrayList<String> ipAddresses = null;
	static ArrayList<Integer> ports = null;

	public static void main(String[] args) {
		Scanner sc = null;
		try {
			sc = new Scanner(new File(args[0]));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		sc.nextLine();
		numServer = 0;
		ipAddresses = new ArrayList<String>();
		ports = new ArrayList<Integer>();

		while (sc.hasNextLine()) {
			// parse inputs to get the ips and ports of servers
			String port = sc.nextLine();
			String[] ipPort = port.split(":");
			ipAddresses.add(ipPort[0]);
			ports.add(Integer.parseInt(ipPort[1]));
			numServer++;
		}
		sc.close();
		sc = new Scanner(System.in);
		int initServer = 0;
		while (sc.hasNextLine()) {
			String cmd = sc.nextLine();
			cmd = "client " + cmd;
			String[] tokens = cmd.split(" ");
			if(tokens.length < 1){
				continue;
			}
			if (tokens[1].equals("purchase") || tokens[1].equals("cancel") || tokens[1].equals("list")
					|| tokens[1].equals("search")) {
				// send appropriate command to the server and display the
				// appropriate responses form the server
				for (int i = initServer; true; i = (i + 1) % numServer) {
					i = getTcpSocket(i); // automatically loops to the first open
										// socket
					//System.out.println("[DEBUG] function returned " + i);
					if (sendTcpRequest(cmd) == -1) { // returns -1 if there is
														// an exception in the
														// printstream, server
														// is down, try the next
						continue;
					}
					//System.out.println("[DEBUG] sent request");
					int resp = echoTcpResponse(i);
					//System.out.println("Response: " + resp);
					if (resp == -1) { // indicates failure
						continue;
					} else if (resp >= 0) {
						System.out.println("Server said that leader is " + resp);
						i = resp - 1;
						initServer = resp - 1;
						continue;
					}

					break;
				}
				try {
					tcpSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("ERROR: No such command");
			}
		}
		sc.close();
	}

	private static boolean pingSuccessful(int i) {
		try {
			Socket s = new Socket();
			s.connect(new InetSocketAddress(ipAddresses.get(i), ports.get(i)), 100);
			s.close();
			return true;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// System.out.println("[DEBUG] ping failed");
			return false;
		}
		return true;
	}

	private static int sendTcpRequest(String message) {
		outStream.println(message);
		if (outStream.checkError()) { // function returns true if an exception
										// occurred
			//System.out.println("[DEBUG] Message sending failed, yo");
			return -1;
		} else
			//System.out.println("[DEBUG] Message sending success, returning 0");
			return 0;
	}

	private static int echoTcpResponse(int i) {
		// System.out.println("[DEBUG]Receiving message");
		while (!inStream.hasNext()) {
			//System.out.println("[DEBUG] timed out, pinging");
			try {
				inStream = new Scanner(tcpSocket.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (!pingSuccessful(i)) {
				return -1;
			} else {
				// System.out.println("[DEBUG] ping successful");
			}
		}
		boolean firstline = true;
		while (inStream.hasNextLine()) {
											// if a "nope" message
			String str = inStream.nextLine().replaceAll("null", "").replaceAll(" +"," "); // will automatically return a
												// blank line after 100ms
			if (firstline) {
				String[] test = str.split(" ");
				if (test[0].equals("fail")) {
					return Integer.parseInt(test[1]);
				}
			}
			if(str != null){
				System.out.println(str);
			}
			
		}
		// System.out.println("[DEBUG]That's it");
		return -2; // -2 encodes a successful read
	}

	private static int getTcpSocket(int start) {
		for (int i = start; true; i = (i + 1) % numServer) {
			try {
				//System.out.println("[DEBUG]trying server " + (i));
				tcpSocket = new Socket();
				tcpSocket.setSoTimeout(500);
				tcpSocket.connect(new InetSocketAddress(ipAddresses.get(i), ports.get(i)), 500);
				outStream = new PrintStream(tcpSocket.getOutputStream());
				inStream = new Scanner(tcpSocket.getInputStream());
				//System.out.println("[DEBUG]successful connection");
				return i;
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				//System.out.println("[DEBUG]server " + i + " timed out");
				continue;
			}
		}

	}
}
