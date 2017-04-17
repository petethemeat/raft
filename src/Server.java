
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
	
	

}
