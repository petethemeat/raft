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
	
	private static Integer commitIndex = 0;
	private static Integer lastApplied = 0;
	
	private static ArrayList<LogEntry> log = new ArrayList<>();
	private static ArrayList<Integer> nextIndex = new ArrayList<>();
	private static ArrayList<Integer> matchIndex = new ArrayList<>();
	
	private static ArrayList<Connection> connections= new ArrayList<>();
	
	public static ExecutorService executor = Executors.newFixedThreadPool(10);
	
	
	public static void main(String[] args)
	{
		//TODO read in ip adress and ports for other servers
		
		role = Role.follower;	//set initial role to follower
		log.add(new LogEntry(0, "_"));
		
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
							nextIndex.add(commitIndex + 1);
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
					append(null);
					break;
					
				case follower:
					role = Role.candidate;
					currentTerm += 1;
					
					votedFor = myId;
					votes = 1;
					
					request();
					
					break;
				
				case candidate: 
					continue; 
					
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

			
			LogEntry[] entries = null;
			int currentIndex = Server.nextIndex.get(i) - 1; //CurrentIndex
			 
			if(currentIndex < localIndex)
			{
				List<LogEntry> sub = log.subList(currentIndex + 1, localIndex + 1);
				entries = sub.toArray(new LogEntry[sub.size()]);
			}
			
			Connection currentConnection = connections.get(i);
			
			Append current = new Append(currentTerm, myId, currentIndex, log.get(currentIndex).term, commitIndex, entries, 
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
	
	private static void handleClient(Scanner sc)
	{
		log.add(new LogEntry(currentTerm, sc.next()));
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
