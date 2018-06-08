package life.genny.utils;

import javax.money.CurrencyUnit;

import org.javamoney.moneta.Money;

public class FeeCalculator {
	
	public static Money calculateOwnerFeeForJaybro(Money input) {
		CurrencyUnit DEFAULT_CURRENCY_TYPE = input.getCurrency();
		Number inputNum = input.getNumber();

		Money ownerFee = Money.of(0, DEFAULT_CURRENCY_TYPE);

		Number RANGE_1 = 2999.99;
		Number RANGE_2 = 4999.99;

		Number FEE_1 = 0.10;
		Number FEE_2 = 0.075;
		Number FEE_3 = 0.05;

		Number RANGE_1_COMPONENT = MoneyHelper.mul(inputNum, FEE_1);
		Number RANGE_2_COMPONENT = MoneyHelper.mul(RANGE_1, FEE_1);
		;
		Number RANGE_3_COMPONENT = MoneyHelper.mul(MoneyHelper.sub(RANGE_2, RANGE_1), FEE_2);

		if (inputNum.doubleValue() <= RANGE_1.doubleValue()) {
			// RANGE_1_COMPONENT
			ownerFee = Money.of(RANGE_1_COMPONENT, DEFAULT_CURRENCY_TYPE);

			System.out.println("range 1 ");
		}

		if (inputNum.doubleValue() > RANGE_1.doubleValue() && inputNum.doubleValue() <= RANGE_2.doubleValue()) {
			// RANGE_2_COMPONENT + (input - RANGE_1) * FEE_2
			System.out.println(input);
			Money subtract = MoneyHelper.sub(input, RANGE_1);
			System.out.println(subtract);
			Money multiply = MoneyHelper.mul(subtract, FEE_2);
			System.out.println(multiply);
			ownerFee = MoneyHelper.add(multiply, RANGE_2_COMPONENT);
			System.out.println(ownerFee);

			System.out.println("range 2 ");
		}

		if (inputNum.doubleValue() > RANGE_2.doubleValue() ) {
			// RANGE_2_COMPONENT + RANGE_3_COMPONENT + (input - RANGE_2) * FEE_3
			Number addition1 = MoneyHelper.add(RANGE_2_COMPONENT, RANGE_3_COMPONENT);
			Money subtract = MoneyHelper.sub(input, RANGE_2);
			Money multiply = MoneyHelper.mul(subtract, FEE_3);
			Money addition2 = MoneyHelper.add(multiply, addition1);
			ownerFee = addition2;

			System.out.println("range 3 ");
		}

		/*
		 * To prevent exponential values from appearing in amount. Not 1.7E+2, We need
		 * 170
		 */
		ownerFee = MoneyHelper.round(ownerFee);
		ownerFee = Money.of(ownerFee.getNumber().doubleValue(), DEFAULT_CURRENCY_TYPE);

		Number amount = ownerFee.getNumber().doubleValue();
		Money fee = Money.of(amount.doubleValue(), DEFAULT_CURRENCY_TYPE);
		System.out.println("From QRules " + fee);
		return fee;
	}
	
	
	public static Money calcDriverFeeForJayBro(Money input) { 

		CurrencyUnit DEFAULT_CURRENCY_TYPE = input.getCurrency();
		Number inputNum = input.getNumber();

		Money driverFee = Money.of(0, DEFAULT_CURRENCY_TYPE);

		Number RANGE_1 = 2999.99;
		Number RANGE_2 = 4999.99;

		Number FEE_1 = 0.10;
		Number FEE_2 = 0.075;
		Number FEE_3 = 0.05;

		Number ONE = 1;

		Number subtract01 = MoneyHelper.sub(RANGE_2, RANGE_1);
		Number REVERSE_FEE_MULTIPLIER_1 = MoneyHelper.mul(subtract01, FEE_2);

		Number multiply01 = MoneyHelper.mul(RANGE_1, FEE_1);
		Number REVERSE_FEE_BOUNDARY_1 = MoneyHelper.sub(RANGE_1, multiply01);

		Number subtract03 = MoneyHelper.sub(RANGE_2, REVERSE_FEE_MULTIPLIER_1);
		Number REVERSE_FEE_BOUNDARY_2 = MoneyHelper.sub(subtract03, multiply01);


		if (inputNum.doubleValue() < REVERSE_FEE_BOUNDARY_1.doubleValue()) {
			// return calcOwnerFee( inputNum * (1 / (1 - FEE_1)));
			Number subtract = MoneyHelper.sub(ONE, FEE_1);
			Number divide = MoneyHelper.div(ONE, subtract);
			Money multiply = MoneyHelper.mul(input, divide);
			driverFee = calculateOwnerFeeForJaybro(multiply);

			System.out.println("zone 1 ");
		}

		if (inputNum.doubleValue() >= REVERSE_FEE_BOUNDARY_1.doubleValue()
				&& inputNum.doubleValue() < REVERSE_FEE_BOUNDARY_2.doubleValue()) {
			// calcFee(( input ) * (1 / (1 - (( REVERSE_FEE_BOUNDARY_1 * FEE_1 ) + (( input
			// - REVERSE_FEE_BOUNDARY_1 ) * FEE_2 )) / input )));
			Money subtract1 = MoneyHelper.sub(input, REVERSE_FEE_BOUNDARY_1);
			Money multiply1 = MoneyHelper.mul(subtract1, FEE_2);
			Number multiply2 = MoneyHelper.mul(FEE_1, REVERSE_FEE_BOUNDARY_1);
			Money addition1 = MoneyHelper.add(multiply1, multiply2);
			Money divide1 = MoneyHelper.div(addition1, input);
			Money subtract2 = MoneyHelper.sub(ONE, divide1);
			Money divide2 = MoneyHelper.div(ONE, subtract2);

			Money multiply3 = MoneyHelper.mul(input, divide2);
			driverFee = calculateOwnerFeeForJaybro(multiply3);

			System.out.println("zone 2 ");
		}

		if (inputNum.doubleValue() >= REVERSE_FEE_BOUNDARY_2.doubleValue()) {
			// calcFee(( input ) * (1 / (1 - (( REVERSE_FEE_BOUNDARY_1 * FEE_1 ) +
			// REVERSE_FEE_MULTIPLIER_1 + (( input - REVERSE_FEE_BOUNDARY_2 ) * FEE_3 )) /
			// input )))
			Money subtract1 = MoneyHelper.sub(input, REVERSE_FEE_BOUNDARY_2);
			Money multiply1 = MoneyHelper.mul(subtract1, FEE_3);
			Number multiply2 = MoneyHelper.mul(REVERSE_FEE_BOUNDARY_1, FEE_1);
			Number addition1 = MoneyHelper.add(multiply2, REVERSE_FEE_MULTIPLIER_1);
			Money addition2 = MoneyHelper.add(multiply1, addition1);
			Money divide1 = MoneyHelper.div(addition2, input);
			Money subtract2 = MoneyHelper.sub(ONE, divide1);
			Money divide2 = MoneyHelper.div(ONE, subtract2);

			Money multiply3 = MoneyHelper.mul(input, divide2);
			driverFee = calculateOwnerFeeForJaybro(multiply3);

			System.out.println("zone 3 ");
		}

		return driverFee;
	}

}
