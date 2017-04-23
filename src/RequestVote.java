import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
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
		Socket sock = new Socket();
		try {
			sock.setSoTimeout(100);
			System.out.println("[DEBUG]attempting to connect");
			sock.connect(new InetSocketAddress(recipientIP, recipientPort), 100);
			System.out.println("[DEBUG]got connection");
			PrintStream pout = new PrintStream(sock.getOutputStream());
			pout.println(message);

			Scanner sc = new Scanner(sock.getInputStream());
			while (!sc.hasNextLine()) {
			}
			// expects single line response, in space-delimited form:
			// voteGranted returnTerm
			voteGranted = sc.nextBoolean();
			returnTerm = sc.nextInt();

			Server.updateVotes(voteGranted);
			Server.updateTerm(returnTerm);

			pout.close();
			sc.close();
			sock.close();
			System.out.println("\n[DEBUG] sent done message to " + recipientIP + ": " + message);
			break;
			// TODO crash after sending first one
		} catch (Exception e) {

			/* time out on receiving the response as */
			System.out.println("Server at IP " + recipientIP + " has timed out or experienced a problem.");
		}

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
