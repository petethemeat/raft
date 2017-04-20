
public class Append { //not a runnable anymore, make your own thread to run in parallel
	private int term; //leader's term
	private int leaderID; //ID of leader
	private int prevLogIndex; //index of log entry immediately preceding new ones
	private int prevLogTerm; //term of prevLogIndex entry
	private Server.LogEntry[] entries; //empty for heartbeat, may send more tan one for efficiency
	private int leaderCommit; //leader's commitIndex
	private String recipient;
	
	private int returnTerm; //returned term, for the leader to updatte itself
	private Boolean success; //true iff follower contained entry matching prevLogIndex and prevLogTerm
							//null if not finished
	public Append(int term, int leaderID, int prevLogIndex, 
				int prevLogTerm, int leaderCommit, Server.LogEntry[] entries, String recipient)
	{
		this.term = term;
		this.leaderID = leaderID;
		this.prevLogIndex = prevLogIndex;
		this.prevLogTerm = prevLogTerm;
		this.leaderCommit = leaderCommit;
		this.entries = entries; //WARNING, does not copy array
		this.recipient = recipient;
	}
	
	public Boolean run() { 
		//Connect to recipient, receive and update response

	}
	
	public int getReturnTerm(){
		return returnTerm;
	}
	
	public Boolean getReturnSuccess(){
		return success;
	}

}
