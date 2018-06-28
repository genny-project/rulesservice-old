package life.genny.utils;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.Logger;

import life.genny.qwanda.exception.PaymentException;

public class PaymentEndpoint {
	
	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	public static final String PAYMENT_SERVICE_URL = System.getenv("PAYMENT_SERVICE_API_URL");
	public static final String PAYMENT_PROVIDER = System.getenv("PAYMENT_PROVIDER");

	public static String createPaymentsUser(final String entityString, final String authToken) throws PaymentException {

		String newpaymentsUserResponse = null;
		try {
			
			System.out.println("Request entity ::"+entityString);
			newpaymentsUserResponse = PaymentUtils.apiPostPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/users", entityString, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}

		return newpaymentsUserResponse;
	}

	public static String getPaymentsUserById(final String paymentsUserId, final String authToken) throws PaymentException {

		String userResponse = null;
		try {
			userResponse = PaymentUtils.apiGetPaymentResponse(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/users/" + paymentsUserId, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		} 
		return userResponse;
	}

	public static String updatePaymentsUser(final String paymentsUserId, final String entityString,
			final String authToken) throws PaymentException {

		String editPaymentResponse = null;
		try {
			
			editPaymentResponse = PaymentUtils.apiPutPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/users/" + paymentsUserId, entityString, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
		return editPaymentResponse;
	}

	public static String createCompany(String companyEntityString, String authToken) throws PaymentException {

		String createCompanyResponse = null;

		try {
			createCompanyResponse = PaymentUtils.apiPostPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/companies", companyEntityString, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
		
		return createCompanyResponse;
	}

	public static String updateCompany(String companyId, String companyEntityString, String authToken) throws PaymentException {

		String updateCompanyResponse = null;

		try {
			updateCompanyResponse = PaymentUtils.apiPutPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/companies/" + companyId, companyEntityString, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
		
		return updateCompanyResponse;

	}

	public static String createPaymentItem(String itemEntity, String authToken) throws PaymentException {
	
		String createItemResponse = null;
		
		try {
			System.out.println("Request Entity ::"+itemEntity);
			createItemResponse = PaymentUtils.apiPostPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/items",  itemEntity, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		} 	
		return createItemResponse;
	}
	
	public static String authenticatePaymentProvider(String paymentProviderEntity, String authToken) throws PaymentException {
		String authenticateResponse = null;
		
		try {
			System.out.println("Request Entity ::"+paymentProviderEntity);
			authenticateResponse = PaymentUtils.apiPostPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/tokens",  paymentProviderEntity, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
		
		return authenticateResponse;
	}
	
	public static String createFees(String feeEntity, String authToken) throws PaymentException {
		
		String feeResponse = null;
		
		try {
			System.out.println("Request Entity ::"+feeEntity);
			feeResponse = PaymentUtils.apiPostPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/fees",  feeEntity, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
				
		return feeResponse;
	}
	
	
	public static String makePayment(String paymentItemId, String paymentEntity, String authToken) throws PaymentException {
		String makePaymentResponse = null;
		
		try {
			makePaymentResponse = PaymentUtils.apiPostPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/items/" + paymentItemId + "/payment",  paymentEntity, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
				
		return makePaymentResponse;
	}
	
	
	public static String releasePayment(String paymentItemId, String releaseEntity, String authToken) throws PaymentException {
		String releasePaymentResponse = null;
		
		try {
			System.out.println("Release Payment entity ::"+releaseEntity);
			releasePaymentResponse = PaymentUtils.apiPostPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/items/" + paymentItemId + "/release-payment", releaseEntity, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		} 
				
		return releasePaymentResponse;
	}
	
	
	public static String disburseAccount(String paymentsUserId, String disburseEntity, String authToken) throws PaymentException {
		
		String disbursementResponse = null;
		try {
			System.out.println("Request Entity ::"+disburseEntity);
			disbursementResponse = PaymentUtils.apiPutPaymentEntity(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/users/" + paymentsUserId + "/disbursement-account", disburseEntity, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
				
		return disbursementResponse;
		
	}
	
	public static String searchPaymentsUser(String emailId, String authToken) throws PaymentException {
		
		String searchUserResponse = null;
		
		try {
			searchUserResponse = PaymentUtils.apiGetPaymentResponse(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/users?search=" + emailId + "&limit=500", authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
				
		return searchUserResponse;
		
	}
	
	public static String getdebitAuthorization(String debitEntity, String authToken) throws PaymentException {
		
		String searchUserResponse = null;
		
		try {
			System.out.println("Request Entity ::"+debitEntity);
			searchUserResponse = PaymentUtils.apiPostPaymentEntity(PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/payment-authority", debitEntity, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
				
		return searchUserResponse;
	}
	
	public static String deleteBankAccount(String bankAccountId, String authToken) throws PaymentException {
		
		String deleteAccountResponse = null;
		
		try {
			deleteAccountResponse = PaymentUtils.apiDeletePaymentEntity(PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/bank-accounts/" + bankAccountId, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
				
		return deleteAccountResponse;
	}
	
	public static String deleteCardAccount(String cardAccountId, String authToken) throws PaymentException {
		
		String deleteAccountResponse = null;
		
		try {
			deleteAccountResponse = PaymentUtils.apiDeletePaymentEntity(PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/card-accounts/" + cardAccountId, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}
				
		return deleteAccountResponse;
		
	}
	
	public static String getPaymentItem(final String itemId, final String authToken) throws PaymentException {

		String itemResponse = null;
		try {
			itemResponse = PaymentUtils.apiGetPaymentResponse(
					PAYMENT_SERVICE_URL + "/" + PAYMENT_PROVIDER + "/items/" + itemId, authToken);
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		} 
		
		return itemResponse;
	}
}
