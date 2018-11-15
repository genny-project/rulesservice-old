package life.genny;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
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

	@Test
	public void generatePasscodeTest() {
		QRules rules = new QRules(null, null, null);
		for(int i=0; i<= 10; i++) {
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
	
	@Test
	public void generateUTCDateTimeTest() {
		ZonedDateTime now = ZonedDateTime.now( ZoneOffset.UTC );
		String dateTimeString = now.toString();
		System.out.println("UTC datetime is ::" + dateTimeString);
	}

}
