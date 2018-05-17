package life.genny.utils;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.Logger;

import life.genny.qwanda.exception.PaymentException;

public class PaymentEndpoint {
	
	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	public static final String paymentServiceUrl = System.getenv("PAYMENT_SERVICE_API_URL");
	public static final String paymentProvider = System.getenv("PAYMENT_PROVIDER");

	public static String createPaymentsUser(final String entityString, final String authToken) throws PaymentException {

		String newpaymentsUserResponse = null;
		try {
			System.out.println("Request entity ::"+entityString);
			newpaymentsUserResponse = PaymentUtils.apiPostPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/users", entityString, authToken);

		} catch (IOException e) {
			e.printStackTrace();
		}

		return newpaymentsUserResponse;
	}

	public static String getPaymentsUserById(final String paymentsUserId, final String authToken) throws PaymentException {

		String userResponse = null;
		try {
			userResponse = PaymentUtils.apiGetPaymentResponse(
					paymentServiceUrl + "/" + paymentProvider + "/users/" + paymentsUserId, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return userResponse;
	}

	public static String updatePaymentsUser(final String paymentsUserId, final String entityString,
			final String authToken) throws PaymentException {

		String editPaymentResponse = null;
		try {
			
			System.out.println("Request Entity ::"+entityString);
			editPaymentResponse = PaymentUtils.apiPutPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/users/" + paymentsUserId, entityString, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return editPaymentResponse;
	}

	public static String createCompany(String companyEntityString, String authToken) throws PaymentException {

		String createCompanyResponse = null;

		try {
			System.out.println("Request Entity ::"+companyEntityString);
			createCompanyResponse = PaymentUtils.apiPostPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/companies", companyEntityString, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return createCompanyResponse;
	}

	public static String updateCompany(String companyId, String companyEntityString, String authToken) throws PaymentException {

		String updateCompanyResponse = null;

		try {
			System.out.println("Request Entity ::"+companyEntityString);
			updateCompanyResponse = PaymentUtils.apiPutPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/companies/" + companyId, companyEntityString, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return updateCompanyResponse;

	}

	public static String createItem(String itemEntity, String authToken) throws PaymentException {
	
		String createItemResponse = null;
		
		try {
			System.out.println("Request Entity ::"+itemEntity);
			createItemResponse = PaymentUtils.apiPostPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/items",  itemEntity, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		} 	
		return createItemResponse;
	}
	
	
	public static String authenticatePaymentProvider(String paymentProviderEntity, String authToken) throws PaymentException {
		String authenticateResponse = null;
		
		try {
			System.out.println("Request Entity ::"+paymentProviderEntity);
			authenticateResponse = PaymentUtils.apiPostPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/tokens",  paymentProviderEntity, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		return authenticateResponse;
		
	}
	
	public static String createFees(String feeEntity, String authToken) throws PaymentException {
		String feeResponse = null;
		
		try {
			System.out.println("Request Entity ::"+feeEntity);
			feeResponse = PaymentUtils.apiPostPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/fees",  feeEntity, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		return feeResponse;
	}
	
	
	public static String makePayment(String paymentItemId, String paymentEntity, String authToken) throws PaymentException {
		String makePaymentResponse = null;
		
		try {
			System.out.println("Request Entity ::"+paymentEntity);
			makePaymentResponse = PaymentUtils.apiPostPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/items/" + paymentItemId + "/payment",  paymentEntity, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		return makePaymentResponse;
	}
	
	
	public static String releasePayment(String paymentItemId, String releaseEntity, String authToken) throws PaymentException {
		String releasePaymentResponse = null;
		
		try {
			System.out.println("Release Payment entity ::"+releaseEntity);
			releasePaymentResponse = PaymentUtils.apiPostPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/items/" + paymentItemId + "/release-payment", releaseEntity, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		} 
				
		return releasePaymentResponse;
	}
	
	
	public static String disburseAccount(String assemblyUserId, String disburseEntity, String authToken) throws PaymentException {
		
		String disbursementResponse = null;
		try {
			System.out.println("Request Entity ::"+disburseEntity);
			disbursementResponse = PaymentUtils.apiPutPaymentEntity(
					paymentServiceUrl + "/" + paymentProvider + "/users/" + assemblyUserId + "/disbursement-account", disburseEntity, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		return disbursementResponse;
		
	}
	
	public static String searchPaymentsUser(String emailId, String authToken) throws PaymentException {
		
		String searchUserResponse = null;
		
		try {
			searchUserResponse = PaymentUtils.apiGetPaymentResponse(
					paymentServiceUrl + "/" + paymentProvider + "/users?search=" + emailId + "&limit=500", authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		return searchUserResponse;
		
	}
	
	public static String getdebitAuthorization(String debitEntity, String authToken) throws PaymentException {
		
		String searchUserResponse = null;
		
		try {
			System.out.println("Request Entity ::"+debitEntity);
			searchUserResponse = PaymentUtils.apiPostPaymentEntity(paymentServiceUrl + "/" + paymentProvider + "/payment-authority", debitEntity, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		return searchUserResponse;
		
	}
	
	public static String deleteBankAccount(String bankAccountId, String authToken) throws PaymentException {
		
		String deleteAccountResponse = null;
		
		try {
			deleteAccountResponse = PaymentUtils.apiDeletePaymentEntity(paymentServiceUrl + "/" + paymentProvider + "/bank-accounts/" + bankAccountId, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		return deleteAccountResponse;
		
	}
	
	public static String deleteCardAccount(String cardAccountId, String authToken) throws PaymentException {
		
		String deleteAccountResponse = null;
		
		try {
			deleteAccountResponse = PaymentUtils.apiDeletePaymentEntity(paymentServiceUrl + "/" + paymentProvider + "/card-accounts/" + cardAccountId, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		return deleteAccountResponse;
		
	}
	
	public static String getAssemblyPaymentItem(final String itemId, final String authToken) throws PaymentException {

		String itemResponse = null;
		try {
			itemResponse = PaymentUtils.apiGetPaymentResponse(
					paymentServiceUrl + "/" + paymentProvider + "/items/" + itemId, authToken);
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return itemResponse;
	}

}
