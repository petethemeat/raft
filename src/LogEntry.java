public class LogEntry {
	//TODO: make constructor
			/* Possible entries:
			 * purchase <user-name> <product-name> <quantity>
			 * cancel <order-id>
			 * search <user-name>
			 * list
			 */
			public int term;
			public int newState; 
			
			public LogEntry(int term, int state)
			{
				this.term = term;
				this.newState = state;
			}
			
			@Override
			public String toString()
			{
				return Integer.toString(term) + ":" + Integer.toString(newState);
			}
			/*int cmd; //0 = purchase, 1 = cancel, 2 = search, 3 = list, -1 = null
			String clientIP; //formatted "IpAddress:Port"
			int serialNum; //unique serial number, provided by the client
			String arg1; //a username if needed, null otherwise
			String arg2; //product name if needed, null otherwise
			int arg; //quantity or order ID if needed, -1 otherwise
			
			@Override
			public String toString(){
				String ret = cmd + " ";
				ret = ret + clientIP + " ";
				ret = ret + serialNum + " ";
				if(arg1 != null){
					ret = ret + arg1 + " ";
				}
				if(arg2 != null){
					ret = ret + arg2 + " ";
				}
				ret = ret + arg + " ";
				return ret;
			}*/
}