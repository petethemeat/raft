import java.util.Hashtable;

public class Inventory {

	private Hashtable<String, Integer> inventory;
	private Hashtable<Integer, Order> orders;
	private Integer orderID;

	public Inventory(Hashtable<String, Integer> inv) {
		inventory = inv;
		orders = new Hashtable<Integer, Order>();
		orderID = 0;
	}

	synchronized String purchase(String user, String product, Integer amount) {

		Order newOrder = new Order(user, product, amount);
		if (!inventory.containsKey(product)) {
			return ("Not Available - We do not sell this product");
		}
		if (inventory.get(product) < amount) {
			return ("Not Available - Not enough items");
		}
		orderID += 1;
		inventory.replace(product, inventory.get(product) - amount);
		orders.put(orderID, newOrder);
		return ("Your order has been placed, " + orderID + " " + user + " " + product + " " + amount);
	}

	synchronized String cancel(Integer ID) {

		if (!orders.containsKey(ID)) {
			return (ID + " not found, no such order");
		}
		String product = orders.get(ID).getProduct();
		Integer amount = orders.get(ID).getAmount();
		inventory.replace(product, inventory.get(product) + amount);
		orders.remove(ID);

		return ("Order " + ID + " is canceled");
	}

	synchronized String search(String user) {

		String notFound = "No order found for " + user;
		boolean ordered = false;
		String userOrders = "";

		for (Integer ID : orders.keySet()) {
			if (orders.get(ID).getUser().equals(user)) {
				ordered = true;
				userOrders += (ID + ", " + orders.get(ID).toString() + "\n");
			}
		}
		if (!ordered) {
			return notFound;
		}

		return userOrders;
	}

	synchronized String list() {
		String products = "";
		for (String prod : inventory.keySet()) {
			products += (prod + " " + inventory.get(prod) + "\n");
		}
		return products;
	}
}
