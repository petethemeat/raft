import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Append implements Runnable{ //not a runnable anymore, make your own thread to run in parallel
	private int term; //leader's term
	private int leaderID; //ID of leader
	private int prevLogIndex; //index of log entry immediately preceding new ones
	private int prevLogTerm; //term of prevLogIndex entry
	private List<LogEntry> entries; //empty for heartbeat, may send more tan one for efficiency
	private int leaderCommit; //leader's commitIndex
	private String recipientIP;
	private int recipientPort;
	private int recipientId;
	private Socket dataSocket;
	private int localIndex;
	
	private int returnTerm; //returned term, for the leader to update itself
	private Boolean success; //true iff follower contained entry matching prevLogIndex and prevLogTerm
							//null if not finished
	public Append(int term, int leaderID, int prevLogIndex, 
				int prevLogTerm, int leaderCommit, ArrayList<LogEntry> entries, String recipientIP, int recipientPort, int recipientId, Socket dataSocket, int localIndex)
	{
		this.term = term;
		this.leaderID = leaderID;
		this.prevLogIndex = prevLogIndex;
		this.prevLogTerm = prevLogTerm;
		this.leaderCommit = leaderCommit;
		this.entries = entries; //WARNING, does not copy array
		this.recipientIP = recipientIP;
		this.recipientPort = recipientPort;
		this.recipientId = recipientId;
		this.dataSocket = dataSocket;
		this.localIndex = localIndex;
		
	}
	
	//Attempts to send once, with timeout of one second
	public void run() { 
		System.out.println("starting appendRPC");
		//Implementation of retry logic
		Socket sock = null;
		while(Server.getTerm() == term)
		{
			List<LogEntry> subEntries = entries.subList(prevLogIndex + 1, localIndex + 1);
			sock = new Socket();
			String message = "append " + term + " " + leaderID + " " + prevLogIndex + " " + prevLogTerm + " " + leaderCommit;
			for(int i = 0; i < subEntries.size(); ++i){
				message = message + " " + subEntries.get(i).toString();
			}
			try{
				sock.connect(new InetSocketAddress(recipientIP, recipientPort), 1000);
				PrintStream pout = new PrintStream(sock.getOutputStream());
				pout.println(message);
				Scanner sc = new Scanner(sock.getInputStream());
				
				//expects single line response, in space-delimited form: success returnTerm
				success = sc.nextBoolean();
				returnTerm = sc.nextInt();
				
				Server.updateNextAndMatch(success, recipientId, leaderCommit);
				if(Server.checkForCommit(localIndex)) 
				{
					//TODO run state machine from lastapplied to commitindex
					String reply = Server.runMachine();
					
					//this could happen during a routine heartbeat
				
					if(dataSocket == null) return;
					
					//Write to client 
					PrintWriter out = new PrintWriter(dataSocket.getOutputStream());
					out.println(reply);
					return;
				}
				Server.updateTerm(returnTerm);
				
				sock.close();
				pout.close();
				sc.close();
				
				if(success) return;
			}
			catch(Exception e){
				if(Server.getTerm() != term){
					return;
				}
			}
		}


	}
	


}
