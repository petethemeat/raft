
public class RequestVote implements Runnable {
	private Integer term;
	private Integer candidateId;
	private Integer lastLogIndex;
	private Integer lastLogTerm;
	private int numServers;
	private static int myIDD;
	private static List<String> serverList = new ArrayList<String>();
	private static List<Integer> portList = new ArrayList<Integer>();
	private static List<Boolean> serverDown = new ArrayList<Boolean>();
	private static ReentrantLock crit = new ReentrantLock();

	public void receive(String message) {
		
	}

	@Override
	public void run() {
		String message = "requestVote " + term + " " + myIDD + " " + lastLogIndex + " " + lastLogTerm;
		// Send request to all other servers
		for (int i = 1; i <= numServers; ++i) {
			if (i == myIDD || serverDown.get(i)) {
				continue;
			}
			System.out.println("[DEBUG]server id " + i);
			Socket sock = new Socket();
			try {
				sock.setSoTimeout(100);
				System.out.println("[DEBUG]attempting to connect");
				sock.connect(new InetSocketAddress(serverList.get(i), portList.get(i)), 100);
				System.out.println("[DEBUG]got connection");
				PrintStream pout = new PrintStream(sock.getOutputStream());
				pout.println(message);
				pout.flush();
				System.out.println("\n[DEBUG] sent done message to " + i + ": " + message);
				sock.close();
				// TODO crash after sending first one
			} catch (Exception e) { // time out on receiving the response as
									// well
				System.out.println("Server " + i + " has timed out or experienced a problem.");
				// indicate that the server has crashed, and this counts as an
				// acknowledgement
				try {
					crit.lock();
					serverDown.set(i, true);
				} catch (Exception f) {
					f.printStackTrace();
				} finally {
					crit.unlock();
				}
			}
		}

	}

}
