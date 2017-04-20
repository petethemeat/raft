
public class Server
{
	
	private enum Role
	{
		leader, 
		candidate,
		follower
	}
	
	private Role role;
	private int term;
	
	public Server(Role role)
	{
		this.role = role;
	}
	
	//An entry for the log. I didn't form separate classes for each command because
	//We need to send over TCP anyway
	public class LogEntry
	{
		/* Possible entries:
		 * purchase <user-name> <product-name> <quantity>
		 * cancel <order-id>
		 * search <user-name>
		 * list
		 */
		int cmd; //0 = purchase, 1 = cancel, 2 = search, 3 = list, -1 = null
		String clientIP; //formatted "IpAddress:Port"
		int serialNum; //unique serial number, provided by the client
		String arg1; //a username, if needed
		String arg2; //product name, if needed
		int arg; //quantity or order ID, if needed
	}
	
	

}
