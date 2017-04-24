import java.util.Scanner;

public class LogEntry {
	//TODO: make constructor
			/* Possible entries:
			 * purchase <user-name> <product-name> <quantity>
			 * cancel <order-id>
			 * search <user-name>
			 * list
			 */
			public LogEntry(int term, String command)
			{
				this.command = command;
				this.term = term;
			}
			
			public String command;
			public Integer term;
			
			@Override
			public String toString()
			{
				//split at ; to get term and command
				//split at : to get command in pieces
				return term.toString() + ";" + command.replaceAll(" ", ":");
			}
}