import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server
{
	
	private static enum Role
	{
		leader, 
		candidate,
		follower
	}
	
	private static Role role;
	private static Integer myId;
	private static Integer leaderId;
	
	private static Integer currentTerm = 0;
	private static Integer votedFor = null;
	private static Integer votes = 0;
	
	private static Integer commitIndex = null;
	private static Integer lastApplied = null;
	
	private static ArrayList<LogEntry> log = new ArrayList<>();
	private static ArrayList<Integer> nextIndex = new ArrayList<>();
	private static ArrayList<Integer> matchIndex = new ArrayList<>();
	
	private static ArrayList<Connection> connections= new ArrayList<>();
	
	public static ExecutorService executor = Executors.newFixedThreadPool(10);
	
	
	public static void main(String[] args)
	{
		//TODO read in ip adress and ports for other servers
		
		role = Role.follower;	//set initial role to follower
		while(true)
		{
			try
			{
				ServerSocket tcpListener = new ServerSocket(connections.get(myId).port);
				if(role == Role.follower)
				{
					tcpListener.setSoTimeout(500); //needs to be random
				}
				
				else tcpListener.setSoTimeout(100); //needs to be shorter for candidates and leaders
				
				Socket dataSocket = new Socket();
				dataSocket = tcpListener.accept();	//Throws exception when waiting to long
				
				Scanner sc = new Scanner(dataSocket.getInputStream());
				String token = sc.next();
				
				PrintWriter tcpOutput = new PrintWriter(dataSocket.getOutputStream());
				
				if(token.equals("append"))
				{
					String message = handleAppend(sc);
					
					sc.close();
					tcpOutput.println(message);
				}
				
				else if(token.equals("requestVote"))
				{
					String message = handleRequest(sc);
					
					sc.close();
					tcpOutput.println(message);
				}
				
				else if(token.equals("client"))
				{
					String message = handleClient(sc);
					sc.close();
					tcpOutput.println(message);
				}
				if(role == Role.candidate)
				{
					if(votes > connections.size()/2){
						role = Role.leader;
						append();
					}
				}
				
			}
			catch(InterruptedIOException e)
			{
				switch(role)
				{
				case leader: 
					append();
					break;
					
				case follower:
					role = Role.candidate;
					currentTerm += 1;
					
					votedFor = myId;
					votes = 1;
					
					request();
					
					break;
				
				case candidate: 
					if(votes > connections.size()/2){
						role = Role.leader;
						append();
					}
					
				}
					
			
			} 
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		
	}

	private static void append() {
		for(int i =0; i < connections.size(); i++)
		{
			if(i == myId) continue;

			
			LogEntry[] entries = null;
			int currentIndex = Server.nextIndex.get(i) - 1; //CurrentIndex
			 
			if(currentIndex < commitIndex)
			{
				List<LogEntry> sub = log.subList(currentIndex + 1, commitIndex + 1);
				entries = sub.toArray(new LogEntry[sub.size()]);
			}
			
			Connection currentConnection = connections.get(i);
			
			Append current = new Append(currentTerm, myId, currentIndex, log.get(currentIndex).term, commitIndex, entries, 
					currentConnection.ip.toString(), currentConnection.port, i);
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
		
		//Message sent does not line up with current log
		if((prevIndex != commitIndex) || (log.get(commitIndex).term != prevTerm)) return "false " + currentTerm.toString(); 
		
		int leaderCommit = Integer.parseInt(sc.next());
		
		if(sc.hasNext())	//Is there anything to append?
		{
			String[] tokens = sc.next().split(":");
			LogEntry newEntry = new LogEntry(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
			log.add(newEntry);	//add new log entries
		}
		
		commitIndex = leaderCommit;
		
		return "true " + currentTerm.toString();
				
	}
	
	private static void request()
	{
		for(int i =0; i < connections.size(); i++)
		{
			if(i == myId) continue;
			
			Connection currentConnection = connections.get(i);
			
			RequestVote current = new RequestVote(currentTerm, myId, commitIndex, log.get(i).term, 
						currentConnection.port, currentConnection.ip.toString());
			executor.submit(current);
			
		}
	}
	
	private static String handleRequest(Scanner sc)
	{
		int term = Integer.parseInt(sc.next());
		if(term < currentTerm) return "false " + currentTerm.toString();
		
		int candidateId = Integer.parseInt(sc.next());
		int prevTerm = Integer.parseInt(sc.next());
		int prevIndex = Integer.parseInt(sc.next());
		
		//Message sent does not line up with current log
		if((prevIndex != commitIndex) || (log.get(commitIndex).term != prevTerm)) return "false " + currentTerm.toString(); 
		
		if((term >= currentTerm) && (votedFor == null))
		{
			votedFor = candidateId;
			updateTerm(term);
			return "true " + currentTerm.toString();
		}
		
		else return "false " + currentTerm.toString();	
	}
	
	private static String handleClient(Scanner sc)
	{
		if(role != Role.leader) return leaderId.toString();
		int newState = Integer.parseInt(sc.next());
		log.add(new LogEntry(currentTerm, newState));
		
		return "success";
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
	
	public static synchronized void updateNextAndMatch(Boolean success, Integer recipientId, Integer leaderCommit)
	{
		if(success)
		{
			Server.nextIndex.set(recipientId, leaderCommit);
			Server.matchIndex.set(recipientId, leaderCommit);
		}
		else
		{
			int currentIndex = Server.nextIndex.get(recipientId);
			Server.nextIndex.set(recipientId, currentIndex - 1);
		}
	}
	
	public static synchronized void updateVotes(Boolean result)
	{
		if(result) votes++;
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
