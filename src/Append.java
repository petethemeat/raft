import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

public class Append { //not a runnable anymore, make your own thread to run in parallel
	private int term; //leader's term
	private int leaderID; //ID of leader
	private int prevLogIndex; //index of log entry immediately preceding new ones
	private int prevLogTerm; //term of prevLogIndex entry
	private Server.LogEntry[] entries; //empty for heartbeat, may send more tan one for efficiency
	private int leaderCommit; //leader's commitIndex
	private String recipientIP;
	private int recipientPort;
	
	private int returnTerm; //returned term, for the leader to update itself
	private Boolean success; //true iff follower contained entry matching prevLogIndex and prevLogTerm
							//null if not finished
	public Append(int term, int leaderID, int prevLogIndex, 
				int prevLogTerm, int leaderCommit, Server.LogEntry[] entries, String recipientIP, int recipientPort)
	{
		this.term = term;
		this.leaderID = leaderID;
		this.prevLogIndex = prevLogIndex;
		this.prevLogTerm = prevLogTerm;
		this.leaderCommit = leaderCommit;
		this.entries = entries; //WARNING, does not copy array
		this.recipientIP = recipientIP;
		this.recipientPort = recipientPort;
	}
	
	//Attempts to send once, with timeout of one second
	public Boolean send() { 
		//TODO: Connect to recipient, receive and update response
		Socket sock = new Socket();
		String message = "append " + term + " " + leaderID + " " + prevLogIndex + " " + prevLogTerm + " " + leaderCommit;
		for(int i = 0; i < entries.length; ++i){
			message = message + " " + entries[i].toString();
		}
		try{
			sock.setSoTimeout(1000);
			sock.connect(new InetSocketAddress(recipientIP, recipientPort), 1000);
			PrintStream pout = new PrintStream(sock.getOutputStream());
			pout.println(message);
			Scanner sc = new Scanner(sock.getInputStream());
			
			//expects single line response, in space-delimited form: success returnTerm
			success = sc.nextBoolean();
			returnTerm = sc.nextInt();
			sock.close();
			pout.close();
			sc.close();
			return success;
		}catch(Exception e){
			return null;
		}

	}
	
	public int getReturnTerm(){
		return returnTerm;
	}
	
	public Boolean getReturnSuccess(){
		return success;
	}

}
