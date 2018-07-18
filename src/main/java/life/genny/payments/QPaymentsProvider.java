package life.genny.payments;

import java.util.List;

import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.payments.QMakePayment;
import life.genny.qwanda.payments.QPaymentMethod;
import life.genny.qwanda.payments.QPaymentsAuthorizationToken.AuthorizationPaymentType;
import life.genny.qwanda.payments.QPaymentsLocationInfo;
import life.genny.qwanda.payments.QPaymentsUserContactInfo;
import life.genny.qwanda.payments.QPaymentsUserInfo;

public interface QPaymentsProvider {
	
	public String getPaymentsAuthKey();
	
	/*
	 * Get payments user details - firstname, lastname, DOB ; set in PaymentUserInfo
	 * POJO
	 */
	public QPaymentsUserInfo getPaymentsUserInfo(BaseEntity projectBe, BaseEntity userBe) throws IllegalArgumentException;
	
	
	
	/* Get payments user email details, set in PaymentUserContact POJO */
	public QPaymentsUserContactInfo getPaymentsUserContactInfo(BaseEntity projectBe, BaseEntity userBe) throws IllegalArgumentException;
	
	
	
	public QPaymentsLocationInfo getPaymentsUserLocationInfo(BaseEntity projectBe, BaseEntity userBe) throws IllegalArgumentException;
	
	
	
	public String paymentUserCreation(String paymentsUserId, String assemblyAuthToken) throws IllegalArgumentException;
	
	
	
	/* Payments - user search method */
	public String findExistingPaymentsUserAndSetAttribute(String authKey) throws IllegalArgumentException;
	
	
	
	/* Payments user updation */
	public void updatePaymentsUserInfo(String paymentsUserId, String attributeCode, String value,
			String paymentsAuthToken) throws IllegalArgumentException;
	
	
	
	/*
	 * Converts payments error into Object and formats into a string error message
	 */
	public String getPaymentsErrorResponseMessage(String paymentsErrorResponseStr) throws IllegalArgumentException;
	
	
	
	/* Create payments company */
	public String createCompany(BaseEntity companyBe, String paymentsUserId, String authtoken) throws IllegalArgumentException;
	
	
	
	/* Payments company updation */
	/* Independent attribute value update. Not bulk */
	public void updatePaymentsCompany(String paymentsUserId, String companyId, String attributeCode, String value,
			String paymentsAuthToken) throws IllegalArgumentException;
	
	
	
	/* Bulk update of payments user info */
	/*
	 * If some information update is lost due to Payments-service-downtime, they
	 * will updated with this
	 */
	public void bulkPaymentsUserUpdate(BaseEntity userBe, String paymentsUserId, String paymentsAuthKey) throws IllegalArgumentException;
	
	
	
	/* Bulk update for payments company info */
	/*
	 * If some information update is lost due to Payments-service-downtime, they
	 * will updated with this
	 */
	public void bulkPaymentsCompanyUpdate(BaseEntity userBe, BaseEntity companyBe, String paymentsUserId,
			String assemblyAuthKey) throws IllegalArgumentException;
	
	
	
	/* Payment process 20 - item creation */
	/*
	 *  Creation of insurance/normal payment item
	 *  @param srcBe- has attribute which has amount for the transaction
	 *         buyerBe & sellerBe - are the buyer and seller
	 *         amountIncludingGSTAttributeCode - is the attribute code that has the amount including GST for payment
	 *         paymentsToken - is the assembly payments token
	 */
	public String createPaymentItem(BaseEntity srcBe, BaseEntity buyerBe, BaseEntity sellerBe, String amountIncludingGSTAttributeCode,
	          String paymentTitle, boolean hasFee, String paymentsToken) throws IllegalArgumentException;
	
	
	
	/* Payment process 10 - creation of payments fee */
	/**
	 * 
	 * @param srcBe -> the Baseentity which has the fee-related amounts
	 * @param projectBe
	 * @param paymentsToken
	 * @return
	 * @throws IllegalArgumentException
	 */
	public List<String> createPaymentFee(BaseEntity srcBe, BaseEntity projectBe, String paymentsToken) throws IllegalArgumentException;
	
	
	
	/* Payment process 30 - make payment */
	/*
	 *  @param: buyerBe - is the buyer BE, sellerBe - is the seller BE, srcBe - is the BaseEntity that has paymentAmount, 
	 *          paymentItemIdAttributeCode - is the Assembly Payment item code and
	 *          paymentAmountAttributeCode - is the attribute code that holds the paymentAmount in the srcBe
	 *          authToken - assembly token string
	 */
	public Boolean makePayment(BaseEntity buyerBe, BaseEntity sellerBe, BaseEntity srcBe, String paymentItemIdAttributeCode,
			String paymentAmountAttributeCode, String paymentTitle,  String authToken);
	
	
	
	
	/* bank accounts needs to be authorized in some of the payment-methods like Assembly; 
	 * bank account payments needs to go through one more API call - Debit authority */
	/**
	 * 
	 * @param srcBe -> BaseEntity which contains the related money attributes
	 * @param makePaymentObj
	 * @param priceAttributeCode
	 * @param authToken
	 * @throws IllegalArgumentException
	 */
	public void debitAuthorityForBankAccount(BaseEntity srcBe, QMakePayment makePaymentObj, String priceAttributeCode, String authToken)
			throws IllegalArgumentException;
	
	
	
	/* Fetch the one time use Payments card and bank tokens for a user */
	public String fetchOneTimePaymentsToken(String paymentsUserId, String paymentToken, AuthorizationPaymentType type);
	
	
	
	/* release payment */
	public Boolean releasePayment(BaseEntity begBe, BaseEntity buyerBe, BaseEntity sellerBe, String authToken);
	
	
	
	/* disbursement of bank account for a user */
	public Boolean disburseAccount(String paymentsUserId, QPaymentMethod paymentMethodObj, String authToken);
	
	
	
	/* Deletes a bank account */
	public Boolean deleteBankAccount(String bankAccountId, String authKey);
	
	
	
	/* Deletes a credit card */
	public Boolean deleteCard(String cardAccountId, String authKey);
		
	
}

