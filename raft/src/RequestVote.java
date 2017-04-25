import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

/*
 * When starting up threads to handle events like RequestingVotes, keep track of them in an ArrayList
 * or something. Sometimes those threads will need to be stopped based on another event. E.g. when requesting
 * votes if a leader with a higher or equal term sends you a heartbeat, you become a follower and stop requesting votes. 
 */
public class RequestVote implements Runnable { // 5.2
	private Integer term; // candidate's term
	private Integer candidateId; // candidate requesting vote
	private Integer lastLogIndex; // index of candidate's last log entry - 5.4
	private Integer lastLogTerm; // term of candidate's last log entry - 5.4
	private Integer recipientPort; // port of voter
	private String recipientIP; // IP of voter

	private Integer returnTerm; // currentTerm, for candidate to update itself
	private Boolean voteGranted; // true means candidate received vote

	public RequestVote(Integer term, Integer candidateId, Integer lastLogIndex, Integer lastLogTerm,
			Integer recipientPort, String recipientIP) {
		this.term = term;
		this.candidateId = candidateId;
		this.lastLogIndex = lastLogIndex;
		this.lastLogTerm = lastLogTerm;
		this.recipientPort = recipientPort;
		this.recipientIP = recipientIP;

		this.returnTerm = null;
		this.voteGranted = null;
	}

	/* reply False if term < currentTerm - 5.1 */
	/*
	 * if votedFor is null or candidateId, and candidate's log is at least as
	 * up-to-date as receiver's log, grant vote - 5.2, 5.4
	 */
	public void run() {
		String message = "requestVote " + term + " " + candidateId + " " + lastLogIndex + " " + lastLogTerm;

		// Send request to specified server
		System.out.println("[DEBUG]server at IP " + recipientIP);
		Socket sock = null;
		while (Server.getTerm() == term) {
			try {
				sock = new Socket();
				sock.setSoTimeout(30000);

				System.out.println("[DEBUG]attempting to connect");
				sock.connect(new InetSocketAddress(recipientIP, recipientPort), 100);
				System.out.println("[DEBUG]got connection");
				PrintStream pout = new PrintStream(sock.getOutputStream(), true);
				pout.flush();
				pout.println(message);

				Scanner sc = new Scanner(sock.getInputStream());
				while (!sc.hasNextLine()) {
				}

				// expects single line response, in space-delimited form:
				// voteGranted returnTerm
				while (!sc.hasNext());
//				String one = sc.next();
				voteGranted = sc.nextBoolean();
				System.out.println("RequestVote: " + voteGranted);
				returnTerm = sc.nextInt();
				System.out.println(returnTerm);

				Server.updateVotes(voteGranted);
				Server.updateTerm(returnTerm);
				pout.close();
				sc.close();
				sock.close();
				System.out.println("\n[DEBUG] sent done message to " + recipientIP + ": " + message);
				return;
				// TODO crash after sending first one

			} catch (SocketException e) {
				// TODO Auto-generated catch block
				System.out.println("Server at IP " + recipientIP + " has experienced a SocketException.");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.println("Server at IP " + recipientIP + " has experienced an IOException.");

			}

			
		}
		System.out.println("while loop terminated");
		return;

	}

	/*
	 * Check if it returns null. If so, no connection was made. However, this
	 * issue shouldn't come up.
	 */
	public Integer getReturnTerm() {
		return this.returnTerm;
	}

	/*
	 * Check if it returns null. If so, no connection was made. However, this
	 * issue shouldn't come up.
	 */
	public Boolean getVoteGranted() {
		return this.voteGranted;
	}

}
