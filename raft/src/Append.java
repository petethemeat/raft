import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Append implements Runnable { // not a runnable anymore, make your
											// own thread to run in parallel
	private int term; // leader's term
	private int leaderID; // ID of leader
	private int prevLogIndex; // index of log entry immediately preceding new
								// ones
	private int prevLogTerm; // term of prevLogIndex entry
	private List<LogEntry> entries; // empty for heartbeat, may send more tan
									// one for efficiency
	private int leaderCommit; // leader's commitIndex
	private String recipientIP;
	private int recipientPort;
	private int recipientId;
	private Socket dataSocket;
	private int localIndex;
	
	private int returnTerm; // returned term, for the leader to update itself
	private Boolean success; // true iff follower contained entry matching
								// prevLogIndex and prevLogTerm
								// null if not finished

	public Append(int term, int leaderID, int prevLogIndex, int prevLogTerm, int leaderCommit,
			ArrayList<LogEntry> entries, String recipientIP, int recipientPort, int recipientId, int localIndex, Socket dataSocket) {
		this.term = term;
		this.leaderID = leaderID;
		this.prevLogIndex = prevLogIndex;
		this.prevLogTerm = prevLogTerm;
		this.leaderCommit = leaderCommit;
		this.entries = entries; // WARNING, does not copy array
		this.recipientIP = recipientIP;
		this.recipientPort = recipientPort;
		this.recipientId = recipientId;
		this.localIndex = localIndex;
		this.dataSocket =dataSocket;
	}

	// Attempts to send once, with timeout of one second
	public void run() {

		// Implementation of retry logic
		while (term == Server.getTerm()) {
			Socket sock = new Socket();
			String message = "append " + term + " " + leaderID + " " + prevLogIndex + " " + prevLogTerm + " "
					+ leaderCommit;

				List<LogEntry> subEntries = entries.subList(prevLogIndex + 1, localIndex + 1);
				for (int i = 0; i < subEntries.size(); ++i) {
					message = message + " " + subEntries.get(i).toString();
				}
			
			try {
				sock.setSoTimeout(30000);
				sock.connect(new InetSocketAddress(recipientIP, recipientPort), 100);
				PrintStream pout = new PrintStream(sock.getOutputStream());
				pout.println(message);
				
				if(subEntries.size() > 0)
					{System.out.println("This is what I want");}

				Scanner sc = new Scanner(sock.getInputStream());

				//System.out.println("[DEBUG] Before while loop");
				while (!sc.hasNextLine()) {
				}
				//System.out.println("[DEBUG] After while loop");
				// expects single line response, in space-delimited form:
				// success returnTerm

				String replyAppend = "";
				if (sc.hasNextLine())
					replyAppend = sc.nextLine();

				String[] tags = replyAppend.trim().split(" ");
				System.out.println("[DEBUG] replyAppend : " + Arrays.toString(tags));
				success = Boolean.parseBoolean(tags[0]);
				returnTerm = Integer.parseInt(tags[1]);

				Server.updateNextAndMatch(success, recipientId, localIndex);
				Server.checkForCommit(localIndex);
				
				 if(Server.checkForCommit(localIndex)) 
				 { 
					 //this could happen during a routine heartbeat
			  
					 //TODO run state machine from lastapplied to commitindex
					 String reply = Server.runMachine(localIndex);				  
				  //Write to client PrintWriter out = new
					 pout.println(reply);
					 pout.flush();
					 pout.close();
					 dataSocket.close();
					 return;
					 
				 }
				 
				 
				

				Server.updateTerm(returnTerm);

				sock.close();
				pout.close();
				sc.close();

				return;
			} catch (Exception e) {
				//System.out.println("[DEBUG] Exception: " + e.getMessage());
				if (Server.getTerm() != term) {
					return;
				}
			}
		}

	}

}
