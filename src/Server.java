import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class Server
{
	
	private static enum Role
	{
		leader, 
		candidate,
		follower
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
	
	private static ArrayList<Connection> connections= new ArrayList<>();
	
	public static ExecutorService executor = Executors.newFixedThreadPool(10);
	
	private static final int minTimeOut = 300;
	private static final int maxTimeOut = 500;
	
	
	
	public static void main(String[] args)
	{
		
		
		Hashtable<String, Integer> input = new Hashtable<String,Integer>();
		//TODO read in inventory file
		inventory = new Inventory(input);
		
		//TODO read in ip adress and ports for other servers
		
		//TODO set personal id
		
		
		
		
		role = Role.follower;	//set initial role to follower
		log.add(new LogEntry(0, "_"));	//add initial log entry "_" means no-op
		
		while(true)
		{
			try
			{
				ServerSocket tcpListener = new ServerSocket(connections.get(myId).port);
				if(role == Role.follower)
				{
					tcpListener.setSoTimeout(ThreadLocalRandom.current().nextInt(minTimeOut, maxTimeOut + 1)); 
				}
				
				else tcpListener.setSoTimeout(100); //needs to be shorter for candidates and leaders
				
				Socket dataSocket = new Socket();
				dataSocket = tcpListener.accept();	//Throws exception when waiting to long
				
				
				//Read in message
				Scanner sc = new Scanner(dataSocket.getInputStream());
				//Figure out the type of message
				String token = sc.next();
				
				PrintWriter tcpOutput = new PrintWriter(dataSocket.getOutputStream());
				
				//handles append message
				if(token.equals("append"))
				{
					String message = handleAppend(sc);	
					sc.close();
					tcpOutput.println(message);
				}
				
				//handles requests for votes
				else if(token.equals("requestVote"))
				{
					String message = handleRequest(sc);
					sc.close();
					tcpOutput.println(message);
				}
				
				//handles client requests
				else if(token.equals("client"))
				{
					//redirect if not the current leader
					if(role != Role.leader)
					{
						tcpOutput.println(leaderId.toString());
						continue;
					}

					handleClient(sc);
					append(dataSocket);
					
					sc.close();
				}
				
				//check to see if candidate is now leader
				if(role == Role.candidate)
				{
					//Majority of votes?
					if(votes > connections.size()/2){
						role = Role.leader;
						
						//initialization for matchindex and next index 
						matchIndex = new ArrayList<Integer>();
						nextIndex = new ArrayList<Integer>();
						for(int i = 0; i < connections.size(); i++)
						{
							matchIndex.add(0);
							nextIndex.add(log.size());
						}
						//Call and empty heart beat
						append(null);
					}
				}
				dataSocket.close();
				tcpListener.close();
			}
			catch(InterruptedIOException e)
			{
				switch(role)
				{
				case leader: 
					//When leader does not receive a message, it will send a heartbeat to follows
					append(null);
					break;
					
				case follower:
					//when follower does not receive a heartbeat, they will become a candidate
					role = Role.candidate;
					//Update term
					currentTerm += 1;
					
					//vote for self
					votedFor = myId;
					votes = 1;
					
					//Request votes from everyone else
					request();
					
					break;
				
				case candidate: 
					
					//Majority of votes?
					if(votes > connections.size()/2){
						role = Role.leader;
						
						//initialization for matchindex and next index 
						matchIndex = new ArrayList<Integer>();
						nextIndex = new ArrayList<Integer>();
						for(int i = 0; i < connections.size(); i++)
						{
							matchIndex.add(0);
							nextIndex.add(log.size());
						}
						//Call and empty heart beat
						append(null);
					}
					
				}
					
			
			} 
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		
	}

	private static void append(Socket dataSocket) {
		
		int localIndex = nextIndex.get(myId);
		for(int i =0; i < connections.size(); i++)
		{
			if(i == myId) continue;
			
			//get prevlog index for server
			int currentIndex = Server.nextIndex.get(i) - 1; //CurrentIndex	
			//get ip and port
			Connection currentConnection = connections.get(i);
			
			//start append RPC
			Append current = new Append(currentTerm, myId, currentIndex, log.get(currentIndex).term, commitIndex, log, 
					currentConnection.ip.toString(), currentConnection.port, i, dataSocket, localIndex);
			executor.submit(current);
			
		}
	}
	
	private static String handleAppend(Scanner sc)
	{
		int term = Integer.parseInt(sc.next());
		if(term < currentTerm) return "false " + currentTerm.toString(); //Message sent from out of date leader
		
		int leader = Integer.parseInt(sc.next()); //Message sent from new term
		if(term > currentTerm)
		{
			updateTerm(term);
			leaderId = leader;
		}
		
		int prevTerm = Integer.parseInt(sc.next());
		int prevIndex = Integer.parseInt(sc.next());
		
		//Message sent is much to far ahead
		if(prevIndex >= log.size()) return "false " + currentTerm.toString();
		
		//Message has wrong term in prevIndex
		if((log.get(prevIndex).term != prevTerm)) return "false " + currentTerm.toString(); 
		
		
		int leaderCommit = Integer.parseInt(sc.next());
		
		int currentIndex = prevIndex + 1;
		if(sc.hasNext())	//Is there anything to append?
		{
			String[] tokens = sc.next().split(";");
			LogEntry newEntry = new LogEntry(Integer.parseInt(tokens[0]), tokens[1]);
			
			//adding new entry to the log
			if(currentIndex >= log.size()){
				log.add(newEntry);
			}
			//overwriting old entry
			else
			{
				log.set(currentIndex, newEntry);
			}
			currentIndex++;//update the current index;
		}
		
		//If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry
		if(leaderCommit > commitIndex)
		{
			commitIndex = Math.min(leaderCommit, currentIndex);
		}
		
		return "true " + currentTerm.toString();
				
	}
	
	/*
	 * This method handles sending a requestVoteRPC to all servers
	 */
	private static void request()
	{
		for(int i =0; i < connections.size(); i++)
		{
			if(i == myId) continue;
			
			Connection currentConnection = connections.get(i);
			int lastLogIndex = log.size() - 1;
			
			RequestVote current = new RequestVote(currentTerm, myId, lastLogIndex, log.get(lastLogIndex).term, 
						currentConnection.port, currentConnection.ip.toString());
			executor.submit(current);
			
		}
	}
	
	/*
	 * This method handles receiving a requestVoteRPC
	 */
	
	private static String handleRequest(Scanner sc)
	{
		int term = Integer.parseInt(sc.next());
		if(term < currentTerm) return "false " + currentTerm.toString();
		
		int candidateId = Integer.parseInt(sc.next());
		int prevTerm = Integer.parseInt(sc.next());
		int prevIndex = Integer.parseInt(sc.next());
		
		//Message sent does not line up with current log
		if(log.get(prevIndex).term != prevTerm) return "false " + currentTerm.toString(); 
		
		if((term >= currentTerm) && (votedFor == null))
		{
			updateTerm(term);
			votedFor = candidateId;
			return "true " + currentTerm.toString();
		}
		
		else return "false " + currentTerm.toString();	
	}
	
	/*
	 * This method handles new messages from clients
	 */
	private static void handleClient(Scanner sc)
	{
		log.add(new LogEntry(currentTerm, sc.next()));
		//Setting personal next index. This is used to reference where the log should be replicated to.
		
		nextIndex.set(myId, nextIndex.get(myId) + 1);
	}
	
	
	public static synchronized void updateTerm(Integer otherTerm)
	{
		if(otherTerm > currentTerm)
		{
			currentTerm = otherTerm; 
			role = Role.follower;
			votedFor = null;
		}
	}
	
	public static synchronized void updateNextAndMatch(Boolean success, Integer recipientId, Integer localIndex)
	{
		if(success)
		{
			Server.nextIndex.set(recipientId, localIndex + 1);
			Server.matchIndex.set(recipientId, localIndex);
		}
		else
		{
			int currentIndex = Server.nextIndex.get(recipientId);
			Server.nextIndex.set(recipientId, currentIndex - 1);
		}
	
	}
	
	public static synchronized Boolean checkForCommit(Integer localIndex)
	{
		int count = 0;
		for(Integer index : Server.matchIndex) if(index == localIndex) count++;
		
		if(count == connections.size()/2 + 1)
		{
			
			commitIndex = localIndex;
			return true;
		}
		
		return false;
	}
	public static synchronized void updateVotes(Boolean result)
	{
		if(result) votes++;
	}
	
	
	
	
	/*
	 * Code for running the statemachine
	 */
	
	public static synchronized String runMachine()
	{
		String reply = null;
		while(lastApplied < commitIndex)
		{
			String message = log.get(lastApplied + 1).command;
			String[] parts = message.split(":");
			
			switch(parts[0])
			{
			case "purchase":
				reply = inventory.purchase(parts[1], parts[2], Integer.parseInt(parts[3]));
				break;
			case "cancel":
				reply = inventory.cancel(Integer.parseInt(parts[1]));
				break;
			case "list":
				reply = inventory.list();
			case "search":
				reply = inventory.search(parts[1]);
			case "_":
				break;
			}
		}
		return reply;
	}
	
	

	
	public class Connection
	{
		public Integer ip;
		public Integer port;
		
		public Connection(int ip, int port)
		{
			this.ip = ip;
			this.port = port;
		}
	}
	
	

}
