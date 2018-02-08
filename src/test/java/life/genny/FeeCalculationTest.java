package life.genny;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import org.javamoney.moneta.Money;
import org.junit.Test;

import life.genny.rules.QRules;

public class FeeCalculationTest {
	
	private static final CurrencyUnit DEFAULT_CURRENCY_AUD = Monetary.getCurrency("AUD");
	
	
	@Test
	public void ownerTest() {
		Money ownerPrice = Money.of(15000, DEFAULT_CURRENCY_AUD);
		
		Money ownerFeeExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerFeeIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerPriceExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerPriceIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverPriceIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverPriceExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
	
		QRules rules = new QRules(null, null, null);
		
		/* fee calculation */
		ownerFeeExcGST = rules.calcOwnerFee(ownerPrice);
		ownerFeeIncGST = rules.includeGSTMoney(ownerFeeExcGST);
		
		/* price calculation */
		ownerPriceExcGST = ownerPrice;
		ownerPriceIncGST = rules.includeGSTMoney(ownerPriceExcGST);
		driverPriceIncGST = ownerPriceIncGST.subtract(ownerFeeIncGST);
		driverPriceExcGST = ownerPriceExcGST.subtract(ownerFeeExcGST);
		
		System.out.println("-------------------------------------------");
		System.out.println("OWNER");
		System.out.println("-------------------------------------------");
		System.out.println("FEES");
		System.out.println("ownerFeeExcGST  ::  "+ String.valueOf(ownerFeeExcGST.getNumber().doubleValue()));	
		System.out.println("ownerFeeIncGST  ::  "+ownerFeeIncGST.getNumber().doubleValue());
		System.out.println("\n");
	
		System.out.println("PRICES");
		System.out.println("ownerPrice         ::  "+ownerPrice.getNumber().doubleValue());
		System.out.println("ownerPriceExcGST   ::  "+ownerPriceExcGST.getNumber().doubleValue());
		System.out.println("ownerPriceIncGST   ::  "+ownerPriceIncGST.getNumber().doubleValue());
		System.out.println("driverPriceExcGST  ::  "+driverPriceExcGST.getNumber().doubleValue());
		System.out.println("driverPriceIncGST  ::  "+driverPriceIncGST.getNumber().doubleValue());
	}
	
	//@Test
	public void driverTest() {
		Money driverPrice = Money.of(5500, DEFAULT_CURRENCY_AUD);
		
		Money driverFeeExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverFeeIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerPriceExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerPriceIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverPriceIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverPriceExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
	
		QRules rules = new QRules(null, null, null);
		
		/* fee calculation */
		driverFeeExcGST = rules.calcDriverFee(driverPrice);
		driverFeeIncGST = rules.includeGSTMoney(driverFeeExcGST);
		
		/* price calculation */
		driverPriceExcGST = driverPrice;
		driverPriceIncGST = rules.includeGSTMoney(driverPriceExcGST);
		ownerPriceExcGST = driverPriceExcGST.add(driverFeeExcGST);
		ownerPriceIncGST = rules.includeGSTMoney(ownerPriceExcGST);

		
		System.out.println("-------------------------------------------");
		System.out.println("DRIVER");
		System.out.println("-------------------------------------------");
		System.out.println("FEES");
		System.out.println("driverFeeExcGST  ::  "+driverFeeExcGST.getNumber().doubleValue() );	
		System.out.println("driverFeeIncGST  ::  "+driverFeeIncGST.getNumber().doubleValue() );
		System.out.println("\n");
	
		System.out.println("PRICES");
		System.out.println("driverPrice         ::  "+driverPrice.getNumber().doubleValue() );
		System.out.println("ownerPriceExcGST   ::  "+ownerPriceExcGST.getNumber().doubleValue() );
		System.out.println("ownerPriceIncGST   ::  "+ownerPriceIncGST.getNumber().doubleValue() );
		System.out.println("driverPriceExcGST  ::  "+driverPriceExcGST.getNumber().doubleValue() );
		System.out.println("driverPriceIncGST  ::  "+driverPriceIncGST.getNumber().doubleValue() );
	}

}
