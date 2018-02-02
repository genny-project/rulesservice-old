package life.genny;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import org.javamoney.moneta.Money;
import org.junit.Test;

import life.genny.rules.QRules;

public class FeeCalculationTest {
	
	private static final CurrencyUnit DEFAULT_CURRENCY_AUD = Monetary.getCurrency("AUD");
	
	@Test
	public void ownerFeeTest() {
		Money inputMoney = Money.of(1000, DEFAULT_CURRENCY_AUD);
		QRules rules = new QRules(null, null, null);
		
		Money ownerFee = rules.calcOwnerFeeInMoney(inputMoney);
		
		System.out.println("owner fee in money ::"+ownerFee);
		
		System.out.println(ownerFee.getNumber().intValue());
		assertEquals(150, ownerFee.getNumber().intValue());
		
		
				
	}
	
	@Test
	public void driverFeeTest() {
		Money inputMoney = Money.of(1000, DEFAULT_CURRENCY_AUD);
		QRules rules = new QRules(null, null, null);
		
		Money driverFee = rules.calcDriverFeeInMoney(inputMoney);
		System.out.println("driver fee ::"+driverFee.getNumber().intValue());
		System.out.println("driver fee in money ::"+driverFee);
		assertEquals(170, driverFee.getNumber().intValue());
	}

}
