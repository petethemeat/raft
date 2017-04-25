//add
public class Order {
	
	private String username;
	private String productName;
	private Integer quantity;
	
	public Order(String user, String product, Integer amount){
		username = user;
		productName = product;
		quantity = amount;
	}
	
	public String getUser(){
		return username;
	}
	
	public String getProduct(){
		return productName;
	}
	
	public Integer getAmount(){
		return quantity;
	}
	
	@Override
	public String toString(){
		return (productName + ", " + quantity);
	}
}
