import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class Server {

	private static enum Role {
		leader, candidate, follower
	}

	private static Inventory inventory;

	private static Role role;
	private static Integer myId;
	private static Integer leaderId;

	private static Integer currentTerm = 0;
	private static Integer votedFor = null;
	private static Integer votes = 0;

	private static Integer commitIndex = 0;
	private static Integer lastApplied = 0;

	private static ArrayList<LogEntry> log = new ArrayList<>();
	private static ArrayList<Integer> nextIndex = new ArrayList<>();
	private static ArrayList<Integer> matchIndex = new ArrayList<>();

	private static ArrayList<Connection> connections = new ArrayList<>();

	public static ExecutorService executor = Executors.newFixedThreadPool(10);

	private static final int minTimeOut = 6000;
	private static final int maxTimeOut = 10000;

	public static void main(String[] args) {
		try {
			System.out.println("Starting server in ten seconds");
			Thread.sleep(10000);
		} catch (InterruptedException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		Hashtable<String, Integer> input = new Hashtable<String, Integer>();

		try {
			Scanner invSc = new Scanner(new File(args[0]));
			while (invSc.hasNext()) {
				String[] parts = invSc.nextLine().split(" ");
				input.put(parts[0], Integer.parseInt(parts[1]));
			}
			invSc.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		inventory = new Inventory(input);

		try {
			Scanner ipSc = new Scanner(new File(args[1]));
			myId = Integer.parseInt(ipSc.nextLine());
			while (ipSc.hasNext()) {
				String[] parts = ipSc.nextLine().split(":");
				connections.add(new Connection(parts[0], Integer.parseInt(parts[1])));
			}
			ipSc.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// TODO read in ip address and ports for other servers

		// TODO set personal id

		role = Role.follower; // set initial role to follower
		log.add(new LogEntry(0, "_")); // add initial log entry "_" means no-op

		ServerSocket tcpListener = null;
		try {
			tcpListener = new ServerSocket(connections.get(myId).port);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Server ID: " + myId);

		while (true) {
			System.out.println(role.toString() + " " + currentTerm);

			try {
				if (role == Role.follower) {
					tcpListener.setSoTimeout(ThreadLocalRandom.current().nextInt(minTimeOut, maxTimeOut + 1));
				}

				// check to see if candidate is now leader
				else
					tcpListener.setSoTimeout(3000); // needs to be shorter for
													// candidates and leaders

				Socket dataSocket = new Socket();
				dataSocket = tcpListener.accept(); // Throws exception when
													// waiting to long

				// Read in message
				Scanner sc = new Scanner(dataSocket.getInputStream());
				// Figure out the type of message
				while (!sc.hasNext()) {
				}

				String token = sc.next();

				PrintStream tcpOutput = new PrintStream(dataSocket.getOutputStream(), true);

				// handles append message
				if (token.equals("append")) {

					/*if (role == Role.candidate) {
						role = Role.follower;
					}
					This doesn't work. What if this is a heartbeat from an earlier term?
					*/

					String appendMessage = "";

					if (sc.hasNextLine()) {
						appendMessage = sc.nextLine();
					}

					String message = handleAppend(appendMessage);

					tcpOutput.println(message);
					tcpOutput.flush();
					tcpOutput.close();

					sc.close();
					dataSocket.close();
				}

				// handles requests for votes
				else if (token.equals("requestVote")) {
					String message = handleRequest(sc);
					//
					tcpOutput.println(message);
					tcpOutput.flush();
					tcpOutput.close();

					sc.close();
					dataSocket.close();
				}

				// handles client requests
				else if (token.equals("client")) {
					// redirect if not the current leader
					if (role != Role.leader) {
						/*
						System.out.println("fail: not a leader!!"); // +
																	// leaderId.toString());
						continue;
						this doesn't work either. We need to send back the id of the leader.*/
					}

					String cmd = "";
					if (sc.hasNextLine())
						cmd = sc.nextLine();

					handleClient(cmd);
					append(dataSocket);

					//String replyToClient = runMachine(log.get(log.size() - 1)); 
					//We have to reply to the client once the command is replicated succesfully

				}

				if (role == Role.candidate) {
					// Majority of votes?
					if (votes > connections.size() / 2) {
						role = Role.leader;

						// initialization for matchindex and next index
						matchIndex = new ArrayList<Integer>();
						nextIndex = new ArrayList<Integer>();
						for (int i = 0; i < connections.size(); i++) {
							matchIndex.add(0);
							nextIndex.add(log.size());
						}
						// Call and empty heart beat
						append(null);
						System.out.println(" ");
					}
				}

			} catch (InterruptedIOException e) {
				switch (role) {
				case leader:
					// When leader does not receive a message, it will send a
					// heartbeat to follows

					append(null);
					break;

				case follower:
					// when follower does not receive a heartbeat, they will
					// become a candidate
					role = Role.candidate;
					// Update term
					currentTerm += 1;

					// vote for self
					votedFor = myId;
					votes = 1;

					// Request votes from everyone else
					request();
					break;

				case candidate:

					// Majority of votes?
					System.out.println(votes);
					if (votes > connections.size() / 2) {
						role = Role.leader;
						System.out.println("I'm the leader"); // initialization
																// for
																// matchindex
																// and next
																// index
						matchIndex = new ArrayList<Integer>();
						nextIndex = new ArrayList<Integer>();
						for (int i = 0; i < connections.size(); i++) {
							matchIndex.add(0);
							nextIndex.add(log.size());
						} // Call and empty heart beat
						append(null);
						System.out.println(" ");
					}

					else {

						currentTerm += 1;

						votedFor = myId;
						votes = 1;

						request();

						break;
					}

				}
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	private static void append(Socket dataSocket) {
			System.out.println("I'm sending a heartbeat");
		
		// index of most recently
		int localIndex = nextIndex.get(myId) - 1;
		for (int i = 0; i < connections.size(); i++) {
			if (i == myId)
				continue;

			// get prevlog index for server
			int currentIndex = Server.nextIndex.get(i) - 1; // CurrentIndex
			// get ip and port
			Connection currentConnection = connections.get(i);

			// if(dataSocket != null)
			// System.out.println("[DEBUG] "+myId+" is sending entry to " +
			// currentConnection.ip+", "+currentConnection.port);
			// start append RPC
			Append current = new Append(currentTerm, myId, currentIndex, log.get(currentIndex).term, commitIndex,
					log, currentConnection.ip, currentConnection.port, i, localIndex, dataSocket);
			Thread t = new Thread(current);
			t.start();
			System.out.println(" ");
		}
	}

	private static String handleAppend(String appendMessage) {

		System.out.println("Handling append RPCs");

		String[] tags = appendMessage.trim().split(" ");
		System.out.println(Arrays.toString(tags));
		int term = Integer.parseInt(tags[0]);
		if (term < currentTerm)
			return "false " + currentTerm.toString(); // Message sent from out
														// of date leader

		int leader = Integer.parseInt(tags[1]); // Message sent from new term
		if (term > currentTerm) {
			updateTerm(term);
			leaderId = leader;
		}

		int prevTerm = Integer.parseInt(tags[2]);
		int prevIndex = Integer.parseInt(tags[3]);

		// Message sent is much to far ahead
		if (prevIndex >= log.size())
			return "false " + currentTerm.toString();

		// Message has wrong term in prevIndex
		if ((log.get(prevIndex).term != prevTerm))
			return "false " + currentTerm.toString();

		int leaderCommit = Integer.parseInt(tags[4]);

		int currentIndex = prevIndex + 1;

		// System.out.println("[DEBUG] Got message in append rpc: " + term + " "
		// + leader + " " + prevTerm + " " + prevIndex + " " + leaderCommit);
		// System.out.println("[DEBUG] While loop starts in append rpc");

		if (tags.length > 5) // Is there anything to append?
		{
			for (int i = 5; i < tags.length; i++) {
				System.out.println("[DEBUG] I am receiving entry");
				String[] tokens = tags[i].split(";");
				//System.out.println("[DEBUG] newEntry: " + tokens[0] + ", " + tokens[1]);
				LogEntry newEntry = new LogEntry(Integer.parseInt(tokens[0]), tokens[1]);

				// adding new entry to the log
				if (currentIndex >= log.size()) {
					log.add(newEntry);
				}
				// overwriting old entry
				else {
					log.set(currentIndex, newEntry);
				}
				currentIndex++;// update the current index;
			}
		}

		// System.out.println("[DEBUG] While loop ends in append rpc");
		// index of last new entry
		if (leaderCommit > commitIndex) {
			commitIndex = Math.min(leaderCommit, currentIndex);
		}

		//System.out.println("[DEBUG] Return from append RPC : " + "true " + currentTerm.toString());
		return "true " + currentTerm.toString();

	}

	/*
	 * This method handles sending a requestVoteRPC to all servers
	 */
	private static void request() {
		System.out.println("I'm sending vote requests");
		for (int i = 0; i < connections.size(); i++) {
			if (i == myId)
				continue;

			Connection currentConnection = connections.get(i);
			int lastLogIndex = log.size() - 1;

			RequestVote current = new RequestVote(currentTerm, myId, lastLogIndex, log.get(lastLogIndex).term,
					currentConnection.port, currentConnection.ip);
			Thread t = new Thread(current);
			t.start();
		}
	}

	/*
	 * This method handles receiving a requestVoteRPC
	 */

	private static String handleRequest(Scanner sc) {
		System.out.println("I'm handling a vote request");
		int term = Integer.parseInt(sc.next());
		if (term < currentTerm) {
			return "false " + currentTerm.toString();
		}
		updateTerm(term);

		int candidateId = Integer.parseInt(sc.next());
		int prevTerm = Integer.parseInt(sc.next());
		int prevIndex = Integer.parseInt(sc.next());

		// Message sent does not line up with current log
		if (log.get(prevIndex).term != prevTerm) {
			System.out.println("[DEBUG] false because prevIndex term != prevTerm");
			return "false " + currentTerm.toString();
		}
		if (votedFor == null) {
			votedFor = candidateId;
			return "true " + currentTerm.toString();
		}

		else
			return "false " + currentTerm.toString();
	}

	/*
	 * This method handles new messages from clients
	 */
	private static void handleClient(String command) {
		//System.out.println("[DEBUG] Adding term: " + currentTerm + ", command: " + command);
		command = command.trim().replaceAll(" ", ":");
		log.add(new LogEntry(currentTerm, command));
		// Setting personal next index. This is used to reference where the log
		// should be replicated to.
		int next = nextIndex.get(myId);
		matchIndex.set(myId, next);
		nextIndex.set(myId, next + 1);
	}

	public static synchronized void updateTerm(Integer otherTerm) {
		if (otherTerm > currentTerm) {
			currentTerm = otherTerm;
			role = Role.follower;
			votedFor = null;
		}
	}

	public static synchronized void updateNextAndMatch(Boolean success, Integer recipientId, Integer localIndex) {
		if (success) {
			Server.nextIndex.set(recipientId, localIndex + 2);
			Server.matchIndex.set(recipientId, localIndex + 1);
		} else {
			int currentIndex = Server.nextIndex.get(recipientId);
			Server.nextIndex.set(recipientId, currentIndex - 1);
		}

	}

	public static synchronized Boolean checkForCommit(Integer localIndex) {
		int count = 0;
		for (Integer index : Server.matchIndex)
			if (index >=localIndex)
				count++;

		if (count >= connections.size() / 2 + 1) {

			commitIndex = localIndex;
			return true;
		}

		return false;
	}

	public static synchronized void updateVotes(Boolean result) {
		if (result)
			votes++;
		}

	/*
	 * Code for running the statemachine
	 */

	public static synchronized String runMachine(int end) {
		String reply = "";
		while(lastApplied < end)
		{
			String[] parts = log.get(lastApplied + 1).command.split(":");
			for(int i = 0; i<parts.length; i++){
				parts[i] = parts[i].trim();
			}
			
			System.out.println(Arrays.toString(parts));
			switch (parts[0]) {
			case "purchase":
				reply = inventory.purchase(parts[1], parts[2], Integer.parseInt(parts[3]));
				lastApplied++;
				break;
			case "cancel":
				reply = inventory.cancel(Integer.parseInt(parts[1]));
				lastApplied++;
				break;
			case "list":
				reply = inventory.list();
				lastApplied++;
				break;
			case "search":
				reply = inventory.search(parts[1]);
				lastApplied++;
				break;
			case "_":
				break;
			}
		}
		return reply;
	}

	public static Integer getTerm() {
		return currentTerm;
	}

}
