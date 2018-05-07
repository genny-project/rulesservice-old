package life.genny;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import org.javamoney.moneta.Money;
import org.junit.Test;

import life.genny.qwandautils.QwandaUtils;
import life.genny.rules.QRules;
import life.genny.utils.StringFormattingUtils;

public class FeeCalculationTest {

	private static final CurrencyUnit DEFAULT_CURRENCY_AUD = Monetary.getCurrency("AUD");


	//@Test
	public void ownerTest() {

		Scanner scanner = new Scanner( System.in );
		System.out.print( "Enter OWNER Price   ::   " );
		String ownerPriceString = scanner.nextLine();

		Double ownerPriceDouble = Double.parseDouble(ownerPriceString);
		Money ownerPrice = Money.of(ownerPriceDouble, DEFAULT_CURRENCY_AUD);

		Money ownerFeeExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerFeeIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerPriceExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerPriceIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverPriceIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverPriceExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);

		QRules rules = new QRules(null, null, null);

		/* fee calculation */
		ownerFeeExcGST = rules.calcOwnerFee(ownerPrice);

		if(ownerPrice.compareTo(Money.of(300, DEFAULT_CURRENCY_AUD)) > 0) {
			if(ownerFeeExcGST.compareTo(Money.of(100, DEFAULT_CURRENCY_AUD)) < 0) {
				ownerFeeExcGST = Money.of(100, DEFAULT_CURRENCY_AUD);
			}
		}

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

		Scanner scanner = new Scanner( System.in );
		System.out.print( "Enter DRIVER Price   ::   " );
		String ownerPriceString = scanner.nextLine();

		Double driverPriceDouble = Double.parseDouble(ownerPriceString);
		Money driverPrice = Money.of(driverPriceDouble, DEFAULT_CURRENCY_AUD);

		Money driverFeeExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverFeeIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerPriceExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money ownerPriceIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverPriceIncGST = Money.of(0, DEFAULT_CURRENCY_AUD);
		Money driverPriceExcGST = Money.of(0, DEFAULT_CURRENCY_AUD);

		QRules rules = new QRules(null, null, null);

		/* fee calculation */
		driverFeeExcGST = rules.calcOwnerFee(driverPrice);

		if(driverPrice.compareTo(Money.of(300, DEFAULT_CURRENCY_AUD)) > 0) {
			if(driverFeeExcGST.compareTo(Money.of(100, DEFAULT_CURRENCY_AUD)) < 0) {
				driverFeeExcGST = Money.of(100, DEFAULT_CURRENCY_AUD);
			}
		}

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

	@Test
	public void generatePasscodeTest() {
		QRules rules = new QRules(null, null, null);
		for(int i=0; i<= 100; i++) {
		  System.out.println("The passcode is::"+rules.generateVerificationCode());

		}
		System.out.println(String.format("%04d", 0));
		System.out.println(String.format("%04d", 12));
		System.out.println(String.format("%04d", 123));
		System.out.println(String.format("%04d", 1234));
	}

	@Test
	public void testBankCredentialMasking() {
		String card = "4111-4111-4111-4111";
		String bsb = "313-121";
		Character[] characterToBeIgnoredArr = {'-'};


		String maskedCard1 = StringFormattingUtils.maskWithRange(card, 0, 15, "x", characterToBeIgnoredArr);
		System.out.println("masked card ::"+maskedCard1);

		String maskedBsb1 = StringFormattingUtils.maskWithRange(bsb, 0, 5, "x",  characterToBeIgnoredArr);
		System.out.println("masked BSB ::"+maskedBsb1);

		String account = "123456567";
		String account1 = "123456567123";

		String maskedAccount1 = StringFormattingUtils.maskWithRange(account, 0, 6, "x", characterToBeIgnoredArr);
		String maskedAccount3 = StringFormattingUtils.maskWithRange(account1, 0, 6, "x", null);

		System.out.println("masked account 1 ::"+maskedAccount1);
		System.out.println("masked account 1 ::"+maskedAccount3);
	}

}
