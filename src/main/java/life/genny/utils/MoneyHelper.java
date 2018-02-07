package life.genny.utils;

import org.javamoney.moneta.Money;

public class MoneyHelper {
	
	public static final String DEFAULT_CURRENCY = "AUD";
	
	public static Number add(Number num1, Number num2) {
		
		Number result = 0;
		if(num1 != null && num2 != null) {
			result = num1.doubleValue() + num2.doubleValue();
		}
		
		return result;
	}
	
	public static Number sub(Number num1, Number num2) {
		
		Number result = 0;
		if(num1 != null && num2 != null) {
			result = num1.doubleValue() - num2.doubleValue();
		}
		
		return result;
		
	}
	
	public static Number mul(Number num1, Number num2) {
		
		Number result = 0;
		if(num1 != null && num2 != null) {
			result = num1.doubleValue() * num2.doubleValue();
		}
		
		return result;
		
	}
	
	public static Number div(Number num1, Number num2) {
		
		Number result = 0;
		if(num1 != null && num2 != null) {
			result = num1.doubleValue() / num2.doubleValue();
		}
		
		return result;
		
	}
	
	public static Money add(Money inputMoney, Number number) {
		
		Money result = Money.of(0, DEFAULT_CURRENCY);
		if(inputMoney != null && number != null) {
			Number numberFromMoney = inputMoney.getNumber();
			Number resultNumber = add(number, numberFromMoney);
			result = Money.of(resultNumber, inputMoney.getCurrency());
		}
		
		return result;
	}

}
