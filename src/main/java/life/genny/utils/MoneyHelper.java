package life.genny.utils;

public class MoneyHelper {
	
	public static Number addNumbers(Number num1, Number num2) {
		
		Number result = 0;
		if(num1 != null && num2 != null) {
			result = num1.doubleValue() + num2.doubleValue();
		}
		
		return result;
	}
	
	public static Number subtractNumber(Number num1, Number num2) {
		
		Number result = 0;
		if(num1 != null && num2 != null) {
			result = num1.doubleValue() - num2.doubleValue();
		}
		
		return result;
		
	}
	
	public static Number nultiplyNumber(Number num1, Number num2) {
		
		Number result = 0;
		if(num1 != null && num2 != null) {
			result = num1.doubleValue() * num2.doubleValue();
		}
		
		return result;
		
	}
	
	public static Number divideNumber(Number num1, Number num2) {
		
		Number result = 0;
		if(num1 != null && num2 != null) {
			result = num1.doubleValue() / num2.doubleValue();
		}
		
		return result;
		
	}

}
