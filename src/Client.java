import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Client {

	static String hostAddress;
	static int tcpPort;
	static int numServer;
	// For tcp transfers
	static Socket tcpSocket = null;
	static Scanner inStream = null;
	static PrintStream outStream = null;
	// For udp transfers
	static byte[] sBuffer;
	static ArrayList<String> ipAddresses = null;
	static ArrayList<Integer> ports = null;
	static List<Boolean> serverDown = new ArrayList<Boolean>();
	
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		numServer = sc.nextInt();
		sc.nextLine();
		ipAddresses = new ArrayList<String>();
		ports = new ArrayList<Integer>();

		for (int i = 0; i < numServer; i++) {
			// parse inputs to get the ips and ports of servers
			String port = sc.nextLine();
			String[] ipPort = port.split(":");
			ipAddresses.add(ipPort[0]);
			ports.add(Integer.parseInt(ipPort[1]));
			serverDown.add(false);
		}

		while (sc.hasNextLine()) {
			String cmd = sc.nextLine();
			String[] tokens = cmd.split(" ");
			if (tokens[0].equals("purchase") || tokens[0].equals("cancel") || tokens[0].equals("list") || tokens[0].equals("search")) {
				// send appropriate command to the server and display the
				// appropriate responses form the server
				for(int i = 0; true; i = (i+1)%numServer){
					i = getTcpSocket(); //automatically loops to the first open socket
					if(sendTcpRequest(cmd)){ //returns true if there is an exception in the printstream, server is down, try the next
						serverDown.set(i, true);
						continue;
					}
					if(echoTcpResponse(i)){
						serverDown.set(i, true);
						continue;
					}
					break;
				}
				try {
					tcpSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} 
			else {
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
			//System.out.println("[DEBUG] ping failed");
			return false;
		}
		return true;
	}

	private static boolean sendTcpRequest(String message) {
		outStream.println(message);
		return outStream.checkError(); //function returns true if an exception occurred
	}

	private static boolean echoTcpResponse(int i) {
		//System.out.println("[DEBUG]Receiving message");
		while(!inStream.hasNext()){
			//System.out.println("[DEBUG] timed out, pinging");
				try {
					inStream = new Scanner(tcpSocket.getInputStream());
				} catch (IOException e) {
					e.printStackTrace();
				}
			if(!pingSuccessful(i)){
				return true;
			}
			else{
				//System.out.println("[DEBUG] ping successful");
			}
		}
		while (inStream.hasNextLine()) { 
			String str = inStream.nextLine(); //will automatically return a blank line after 100ms
			System.out.println(str);
		}
		//System.out.println("[DEBUG]That's it");
		return false;
	}

	private static int getTcpSocket() {
		for (int i = 0; true; i = (i + 1) % numServer) {
			if(serverDown.get(i)){
				continue;
			}
			try {
				//System.out.println("[DEBUG]trying server " + (i));
				tcpSocket = new Socket();
				tcpSocket.setSoTimeout(500);
				tcpSocket.connect(new InetSocketAddress(ipAddresses.get(i), ports.get(i)), 100);
				outStream = new PrintStream(tcpSocket.getOutputStream());
				inStream = new Scanner(tcpSocket.getInputStream());
				//System.out.println("[DEBUG]successful connection");
				return i;
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				//System.out.println("[DEBUG]server " + i + " timed out");
				serverDown.set(i, true);
				continue;
			}
		}

	}
}
