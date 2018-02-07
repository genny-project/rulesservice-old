package life.genny.utils;

import javax.money.Monetary;
import org.javamoney.moneta.Money;

public class MoneyHelper {
	
	public static final String DEFAULT_CURRENCY = "AUD";
	
	/* ARITHMETIC FUNCTIONS FOR INPUTS IN NUMBER DATATYPE */
	
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
	
	/* ARITHMETIC FUNCTIONS FOR 1 INPUT IN MONEY DATATYPE, 1 INPUT IN NUMBER DATATYPE */
	
	public static Money add(Money inputMoney, Number number) {
		
		Money result = Money.of(0, DEFAULT_CURRENCY);
		if(inputMoney != null && number != null) {
			Number numberFromMoney = inputMoney.getNumber();
			Number resultNumber = add(number, numberFromMoney);
			result = Money.of(resultNumber, inputMoney.getCurrency());
		}
		
		return result;
	}
	
	public static Money sub(Money inputMoney, Number number) {
		
		Money result = Money.of(0, DEFAULT_CURRENCY);
		if(inputMoney != null && number != null) {
			Number numberFromMoney = inputMoney.getNumber();
			Number resultNumber = sub(number, numberFromMoney);
			result = Money.of(resultNumber, inputMoney.getCurrency());
		}
		
		return result;
	}
	
	public static Money mul(Money inputMoney, Number number) {
		
		Money result = Money.of(0, DEFAULT_CURRENCY);
		if(inputMoney != null && number != null) {
			Number numberFromMoney = inputMoney.getNumber();
			Number resultNumber = mul(number, numberFromMoney);
			result = Money.of(resultNumber, inputMoney.getCurrency());
		}
		
		return result;
	}
	
	public static Money div(Money inputMoney, Number number) {
		
		Money result = Money.of(0, DEFAULT_CURRENCY);
		if(inputMoney != null && number != null) {
			Number numberFromMoney = inputMoney.getNumber();
			Number resultNumber = div(number, numberFromMoney);
			result = Money.of(resultNumber, inputMoney.getCurrency());
		}
		
		return result;
	}
	
	/* ARITHMETIC FUNCTIONS FOR BOTH INPUTS IN MONEY DATATYPE */
	
	public static Money add(Money inputMoney1, Money inputMoney2) {
		
		Money result = Money.of(0, DEFAULT_CURRENCY);
		if(inputMoney1 != null && inputMoney2 != null) {
			
			result = inputMoney1.add(inputMoney2);
		}
		
		return result;
	}
	
	public static Money sub(Money inputMoney1, Money inputMoney2) {
		
		Money result = Money.of(0, DEFAULT_CURRENCY);
		if(inputMoney1 != null && inputMoney2 != null) {
			
			result = inputMoney1.subtract(inputMoney2);
		}
		
		return result;
	}
	
	public static Money mul(Money inputMoney1, Money inputMoney2) {
		
		Money result = Money.of(0, DEFAULT_CURRENCY);
		if(inputMoney1 != null && inputMoney2 != null) {
			
			Number numberFromMoney = inputMoney2.getNumber();
			result = inputMoney1.multiply(numberFromMoney);
		}
		
		return result;
	}
	
	public static Money div(Money inputMoney1, Money inputMoney2) {
		
		Money result = Money.of(0, DEFAULT_CURRENCY);
		if(inputMoney1 != null && inputMoney2 != null) {
			
			Number numberFromMoney = inputMoney2.getNumber();
			result = inputMoney1.divide(numberFromMoney);
		}
		
		return result;
	}
	
	
	/* BASIC ROUNDING OFF FUNCTION */
	
	public static Money round(Money inputMoney) {
		Money result = Money.of(0, DEFAULT_CURRENCY);
		
		if(inputMoney != null) {
			
			result = inputMoney.with(Monetary.getDefaultRounding());
		}
		
		return result;
	}

}
