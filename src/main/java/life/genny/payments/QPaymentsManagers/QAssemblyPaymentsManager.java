package life.genny.payments.QPaymentsManagers;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.drools.core.spi.KnowledgeHelper;
import org.javamoney.moneta.Money;
import org.json.simple.JSONObject;

import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.payments.QPaymentsProvider;
import life.genny.qwanda.Answer;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.exception.PaymentException;
import life.genny.qwanda.payments.QMakePayment;
import life.genny.qwanda.payments.QPaymentAuthorityForBankAccount;
import life.genny.qwanda.payments.QPaymentMethod;
import life.genny.qwanda.payments.QPaymentsAuthorizationToken;
import life.genny.qwanda.payments.QPaymentsCompany;
import life.genny.qwanda.payments.QPaymentsCompanyContactInfo;
import life.genny.qwanda.payments.QPaymentsDisbursement;
import life.genny.qwanda.payments.QPaymentsErrorResponse;
import life.genny.qwanda.payments.QPaymentsFee;
import life.genny.qwanda.payments.QPaymentsItem;
import life.genny.qwanda.payments.QPaymentsAuthorizationToken.AuthorizationPaymentType;
import life.genny.qwanda.payments.QPaymentsItem.PaymentTransactionType;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyItemResponse;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyUserResponse;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyUserSearchResponse;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.StringFormattingUtils;
import life.genny.qwanda.payments.QPaymentsLocationInfo;
import life.genny.qwanda.payments.QPaymentsUser;
import life.genny.qwanda.payments.QPaymentsUserContactInfo;
import life.genny.qwanda.payments.QPaymentsUserInfo;
import life.genny.qwanda.payments.QReleasePayment;
import life.genny.qwanda.payments.QPaymentMethod.PaymentType;
import life.genny.rules.QRules;
import life.genny.rules.RulesUtils;
import life.genny.utils.PaymentEndpoint;
import life.genny.utils.PaymentUtils;

public class QAssemblyPaymentsManager implements QPaymentsProvider {
	
	protected static final Logger log = org.apache.logging.log4j.LogManager 
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
	
	public static final Boolean devMode = System.getenv("GENNYDEV") == null ? false : true;
	
	private String token;
	private QRules rules;

	public QAssemblyPaymentsManager(QRules rules) {

		this.rules = rules;
	}
	
	/**
	* Returns the authentication key for the payments service - key is generated as per documentation here
	* https://github.com/genny-project/payments/blob/master/docs/auth-tokens.md
	*/
	@Override
	public String getPaymentsAuthKey() {
		/* Fetch marketplace information from environmnt variables */
		String paymentMarketPlace = System.getenv("PAYMENT_MARKETPLACE_NAME");
		String paymentToken = System.getenv("PAYMENT_TOKEN");
		String paymentSecret = System.getenv("PAYMENT_SECRET");

		/* Create the data for the authentication token */
		JSONObject authObj = new JSONObject();
		authObj.put("tenant", paymentMarketPlace);
		authObj.put("token", paymentToken);
		authObj.put("secret", paymentSecret);

		/* Return the base 64 encoded authentication token */
		return PaymentUtils.base64Encoder(authObj.toJSONString());
	}
	
	@Override
	public QPaymentsUserInfo getPaymentsUserInfo(BaseEntity projectBe, BaseEntity userBe)
			throws IllegalArgumentException {


		QPaymentsUserInfo userInfo = null;

		/* Getting userInfo POJO -> handling errors with slack webhook reporting */
		try {

			/* Fetch data from BE and set in POJO */
			userInfo = PaymentUtils.getPaymentsUserInfo(userBe);

			/* If instance creation fails, throw exception */
			if (userInfo == null) {
				throw new IllegalArgumentException("QPaymentsUserInfo instance creation failed");
			}

		} catch (IllegalArgumentException e) {

			log.error(e.getMessage());
			String message = "Payments user creation would fail, since user information during registration is incomplete :"
					+ e.getMessage() + ", for USER: " + userBe.getCode();

			/* send toast to user */
			String toastMessage = "User information during registration is incomplete : " + e.getMessage()
					+ ". Please complete it for payments to get through.";
			String[] recipientArr = { userBe.getCode() };
			rules.sendToastNotification(recipientArr, toastMessage, "warning");

			/* send slack message */
			rules.sendSlackNotification(message);

		}
		return userInfo;
	}

	@Override
	public QPaymentsUserContactInfo getPaymentsUserContactInfo(BaseEntity projectBe, BaseEntity userBe)
			throws IllegalArgumentException {
		QPaymentsUserContactInfo userContactInfo = null;

		/* Getting userContactInfo -> handling errors with slack webhook reporting */
		try {
			userContactInfo = PaymentUtils.getPaymentsUserContactInfo(userBe);

			if (userContactInfo == null) {
				throw new IllegalArgumentException("QPaymentsUserContactInfo instance creation failed");
			}

		} catch (IllegalArgumentException e) {

			log.error(e.getMessage());
			String message = "Payments user creation would fail, since user email is missing or null : "
					+ e.getMessage() + ", for USER: " + userBe.getCode();

			/* send toast to user */
			String toastMessage = "User information during registration is incomplete : " + e.getMessage()
					+ ". Please complete it for payments to get through.";
			String[] recipientArr = { userBe.getCode() };
			rules.sendToastNotification(recipientArr, toastMessage, "warning");

			/* send slack message */
			rules.sendSlackNotification(message);
		}
		return userContactInfo;
	}

	@Override
	public QPaymentsLocationInfo getPaymentsUserLocationInfo(BaseEntity projectBe, BaseEntity userBe)
			throws IllegalArgumentException {
		QPaymentsLocationInfo userLocationInfo = null;

		/* Getting userLocationInfo -> handling errors with slack webhook reporting */
		try {
			userLocationInfo = PaymentUtils.getPaymentsLocationInfo(userBe);

			if (userLocationInfo == null) {
				throw new IllegalArgumentException("QPaymentsLocationInfo instance creation failed");
			}

		} catch (IllegalArgumentException e) {

			log.error(e.getMessage());
			String message = "Payments user creation would fail, since user address info is missing or null : "
					+ e.getMessage() + ", for USER: " + userBe.getCode();

			/* send toast to user */
			String toastMessage = "User information during registration is incomplete : " + e.getMessage()
					+ ". Please complete it for payments to get through.";
			String[] recipientArr = { userBe.getCode() };
			rules.sendToastNotification(recipientArr, toastMessage, "warning");

			/* send slack message */
			rules.sendSlackNotification(message);
		}
		return userLocationInfo;
	}

	@Override
	public String paymentUserCreation(String paymentsUserId, String assemblyAuthToken) throws IllegalArgumentException {
		
		BaseEntity userBe = rules.getUser();
		BaseEntity project = rules.getProject();

		/* user - firstname, lastname, dob info */
		QPaymentsUserInfo userInfo = getPaymentsUserInfo(project, userBe);

		/* user email info */
		QPaymentsUserContactInfo userContactInfo = getPaymentsUserContactInfo(project, userBe);

		/* user address info */
		QPaymentsLocationInfo userLocationInfo = getPaymentsUserLocationInfo(project, userBe);

		String paymentUserCreationResponse = null;
		String paymentUserId = null;

		try {

			/* create the entire payments-user object */
			QPaymentsUser paymentsUser = new QPaymentsUser(paymentsUserId, userInfo, userContactInfo, userLocationInfo);

			try {

				/* converting user object into stringifies json and hitting create user API */
				paymentUserCreationResponse = PaymentEndpoint.createPaymentsUser(JsonUtils.toJson(paymentsUser),
						assemblyAuthToken);

				if (!paymentUserCreationResponse.contains("error") && paymentUserCreationResponse != null) {

					/* response string converted to user response object */
					QPaymentsAssemblyUserResponse responseUserPojo = JsonUtils.fromJson(paymentUserCreationResponse,
							QPaymentsAssemblyUserResponse.class);
					System.out.println("response user pojo ::" + responseUserPojo);

					paymentUserId = responseUserPojo.getId();

				}
			} catch (PaymentException e) {

				/*
				 * Payments creation will fail if user already exists. In this case we check if
				 * the user is already available for the email ID, and fetch the userId
				 */
				this.rules.setState("PAYMENTS_CREATION_FAILURE_CHECK_USER_EXISTS");
				KnowledgeHelper drool1 = this.rules.getDrools();
				drool1.setFocus("payments");

			}

		} catch (IllegalArgumentException e) {

			/* send slack message */
			log.error(e.getMessage());
			String message = "Payments user creation failed : " + e.getMessage() + ", for USER: " + userBe.getCode();
			rules.sendSlackNotification(message);

		}
		return paymentUserId;
	}

	@Override
	public String findExistingPaymentsUserAndSetAttribute(String authKey) throws IllegalArgumentException {
		BaseEntity userBe = rules.getUser();
		String paymentsUserId = null;

		if (userBe != null && authKey != null) {

			String email = userBe.getValue("PRI_EMAIL", null);
			try {

				/* Get all payments users for search criteria based on email */
				String paymentUsersResponse = PaymentEndpoint.searchPaymentsUser(email, authKey);

				/* converting response into Object */
				QPaymentsAssemblyUserSearchResponse userSearchObj = JsonUtils.fromJson(paymentUsersResponse,
						QPaymentsAssemblyUserSearchResponse.class);

				/* use util to get the payments user id from search results based on email */
				paymentsUserId = PaymentUtils.getPaymentsUserIdFromSearch(userSearchObj, email);
				return paymentsUserId;

			} catch (PaymentException e) {

				String message = "Payments user creation failed as well as existing user search has failed : "
						+ e.getMessage() + ", for USER: " + userBe.getCode();

				/* send toast to user */
				/*
				 * String toastMessage = "Payments user creation failed : " + e.getMessage() ;
				 * String[] recipientArr = { userBe.getCode() }; this.sendToastNotification(recipientArr,
				 * toastMessage, "warning");
				 */
				rules.sendSlackNotification(message);
			}

		}
		return paymentsUserId;
	}

	@Override
	public void updatePaymentsUserInfo(String paymentsUserId, String attributeCode, String value,
			String paymentsAuthToken) throws IllegalArgumentException {
		try {

			if (attributeCode != null && value != null) {

				/* Get payments user after setting to-be-updated fields in the object */
				QPaymentsUser paymentsUser = PaymentUtils.updateUserInfo(paymentsUserId, attributeCode, value);

				/* Make the request to Assembly and update */
				if (paymentsUser != null && paymentsUserId != null) {
					try {
						/* Hitting payments-service API for updating */
						String userUpdateResponseString = PaymentEndpoint.updatePaymentsUser(paymentsUserId,
								JsonUtils.toJson(paymentsUser), paymentsAuthToken);
						QPaymentsAssemblyUserResponse userResponsePOJO = JsonUtils.fromJson(userUpdateResponseString,
								QPaymentsAssemblyUserResponse.class);
						rules.println("User updation response :: " + userResponsePOJO);

						// TODO We get user out-payment method ID in update-user response, we may have
						// to save this as an attribute

					} catch (PaymentException e) {
						log.error("Exception occured user updation : " + e.getMessage());
						String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
						throw new IllegalArgumentException(
								"User payments profile updation has not succeeded for the field : "
										+ attributeCode.replace("PRI_", "") + ". " + getFormattedErrorMessage
										+ ". Kindly give valid information for payments to get through.");
					}
				}
			} else {
				if (value == null || value.trim().isEmpty()) {
					throw new IllegalArgumentException(
							"Updated value for the field " + attributeCode.replace("PRI_", "") + " is empty/invalid");
				}
			}

		} catch (IllegalArgumentException e) {

			/*
			 * Send toast message the payments-user updation failed when the field updated
			 * is invalid
			 */
			String toastMessage = e.getMessage();
			String[] recipientArr = { rules.getUser().getCode() };
			rules.sendToastNotification(recipientArr, toastMessage, "warning");
		}
		
	}

	@Override
	public String getPaymentsErrorResponseMessage(String paymentsErrorResponseStr) throws IllegalArgumentException {
		QPaymentsErrorResponse errorResponse = JsonUtils.fromJson(paymentsErrorResponseStr,
				QPaymentsErrorResponse.class);

		StringBuilder errorMessage = new StringBuilder();
		List<Map<String, Object>> errorMapList = new ArrayList<Map<String, Object>>();
		errorMapList.add(errorResponse.getError());
		errorMapList.add(errorResponse.getErrors());

		/* getErrors -> errors from external payment service */
		/* getError -> error from payments service */
		/*
		 * Iterating through Assembly errors and payment-service errors and formatting
		 * them
		 */
		for (Map<String, Object> errorMap : errorMapList) {
			if (errorMap != null && errorMap.size() > 0) {
				for (Map.Entry<String, Object> entry : errorMap.entrySet()) {
					String errVar = entry.getKey();
					List<String> errVal = (List<String>) entry.getValue();

					StringBuilder errValBuilder = new StringBuilder();
					for (String err : errVal) {
						errValBuilder.append(err);
					}

					System.out.println("Error Key = " + errVar + ", Value = " + errValBuilder);

					/* appending and formatting error messages */
					errorMessage.append(errVar + " : " + errVal.toString());
				}
			}
		}
		return errorMessage.toString();
	}

	@Override
	public String createCompany(BaseEntity companyBe, String paymentsUserId, String authtoken)
			throws IllegalArgumentException {
		String companyId = null;
		BaseEntity userBe = rules.getUser();
		if (companyBe != null) {

			// Get the provided company information from the base entity
			String companyName = companyBe.getValue("PRI_CPY_NAME", null);

			String taxNumber = companyBe.getValue("PRI_ABN", null);
			if (taxNumber == null) {
				taxNumber = companyBe.getValue("PRI_ACN", null);
			}

			Boolean isChargeTax = companyBe.getValue("PRI_GST", false);

			/* Gets basic company contact info object */
			QPaymentsCompanyContactInfo companyContactObj = PaymentUtils.getPaymentsCompanyContactInfo(companyBe);

			/* Get company location object */
			QPaymentsLocationInfo companyLocationObj = PaymentUtils.getPaymentsLocationInfo(companyBe);

			/* Get assembly user ID */
			QPaymentsUser user = new QPaymentsUser(paymentsUserId);

			try {

				/* Create complete packed company object */
				QPaymentsCompany companyObj = new QPaymentsCompany(companyName, companyName, taxNumber, isChargeTax,
						companyLocationObj, user, companyContactObj);
				try {
					String createCompanyResponse = PaymentEndpoint.createCompany(JsonUtils.toJson(companyObj),
							authtoken);

					QPaymentsCompany createCompanyResponseObj = JsonUtils.fromJson(createCompanyResponse,
							QPaymentsCompany.class);
					rules.println("payments company creation response ::" + createCompanyResponse);
					rules.println("payments company obj : " + createCompanyResponseObj);
					companyId = createCompanyResponseObj.getId();

				} catch (PaymentException e) {
					throw new IllegalArgumentException(e);
				}

			} catch (IllegalArgumentException e) {

				/*
				 * Send toast message if company creation misses arguments or if API call
				 * returns error response
				 */
				if (userBe != null) {
					/* send toast to user */
					String[] recipientArr = { userBe.getCode() };
					String toastMessage = "Company information during registration is incomplete : " + e.getMessage()
							+ ". Please complete it for payments to get through.";
					rules.sendToastNotification(recipientArr, toastMessage, "warning");
				}
			}

		}
		return companyId;
	}

	@Override
	public void updatePaymentsCompany(String paymentsUserId, String companyId, String attributeCode, String value,
			String paymentsAuthToken) throws IllegalArgumentException {
		try {

			if (attributeCode != null && value != null) {

				/* Get payments company after setting to-be-updated fields in the object */
				QPaymentsCompany paymentsCompany = PaymentUtils.updateCompanyInfo(paymentsUserId, companyId,
						attributeCode, value);

				/* Make the request to Assembly and update */
				if (paymentsCompany != null && paymentsUserId != null) {
					try {
						/* Hitting payments-service API for updating */
						String companyUpdateResponseString = PaymentEndpoint.updateCompany(companyId,
								JsonUtils.toJson(paymentsCompany), paymentsAuthToken);
						QPaymentsCompany userResponsePOJO = JsonUtils.fromJson(companyUpdateResponseString,
								QPaymentsCompany.class);
						rules.println("Company updation response :: " + userResponsePOJO);

					} catch (PaymentException e) {
						log.error("Exception occured user updation : " + e.getMessage());
						String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
						throw new IllegalArgumentException(
								"User payments profile updation has not succeeded for the field : "
										+ attributeCode.replace("PRI_", "") + ". " + getFormattedErrorMessage
										+ ". Kindly give valid information for payments to get through.");
					}
				}
			} else {
				if (value == null || value.trim().isEmpty()) {
					throw new IllegalArgumentException(
							"Updated value for the field " + attributeCode.replace("PRI_", "") + " is empty/invalid");
				}
			}

		} catch (IllegalArgumentException e) {
			/*
			 * Send toast message the payments-user updation failed when the field updated
			 * is invalid
			 */
			String toastMessage = e.getMessage();
			String[] recipientArr = { rules.getUser().getCode() };
			rules.sendToastNotification(recipientArr, toastMessage, "warning");
		}
		
	}

	@Override
	public void bulkPaymentsUserUpdate(BaseEntity userBe, String paymentsUserId, String paymentsAuthKey)
			throws IllegalArgumentException {
		try {
			QPaymentsUser user = PaymentUtils.getCompleteUserObj(userBe, paymentsUserId);
			/* Attempt to update the user in Assembly */
			if (user != null && paymentsUserId != null) {
				try {
					PaymentEndpoint.updatePaymentsUser(paymentsUserId, JsonUtils.toJson(user), paymentsAuthKey);
				} catch (PaymentException e) {
					String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
					throw new IllegalArgumentException(getFormattedErrorMessage);
				}
			}

		} catch (IllegalArgumentException e) {
			log.error("Exception occured user updation" + e.getMessage());
		}

	}

	@Override
	public void bulkPaymentsCompanyUpdate(BaseEntity userBe, BaseEntity companyBe, String paymentsUserId,
			String assemblyAuthKey) throws IllegalArgumentException {
		try {

			/* Get the companies assembly ID */
			String companyId = userBe.getValue("PRI_ASSEMBLY_COMPANY_ID", null);
			QPaymentsCompany company = PaymentUtils.getCompleteCompanyObj(userBe, companyBe, paymentsUserId);

			/* Attempt to update the company in Assembly */
			if (companyId != null && company != null) {
				try {
					PaymentEndpoint.updateCompany(companyId, JsonUtils.toJson(company), assemblyAuthKey);
				} catch (PaymentException e) {
					String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
					throw new IllegalArgumentException(getFormattedErrorMessage);
				}
			}
		} catch (IllegalArgumentException e) {
			log.error("Exception occured company updation" + e.getMessage());
		}

	}

	@Override
	public String createPaymentItem(BaseEntity srcBe, BaseEntity buyerBe, BaseEntity sellerBe,
			String amountIncludingGSTAttributeCode, String paymentTitle, boolean hasFee, String paymentsToken)
			throws IllegalArgumentException {
		String itemId = null;
		BaseEntity begBe = null;
		BaseEntity loadBe = null;
		if ( srcBe != null && amountIncludingGSTAttributeCode != null ) {
			try {
				
				if(srcBe.getCode().startsWith("BEG_")) {
					begBe = srcBe;					
				} else if (srcBe.getCode().startsWith("OFR_")) {
					begBe = rules.baseEntity.getParent(srcBe.getCode(), "LNK_BEG", "OFFER");
				}
				
				loadBe =	 rules.baseEntity.getLinkedBaseEntities(begBe.getCode(), "LNK_BEG", "LOAD").get(0);
				
				Money amountIncludingGST = srcBe.getValue(amountIncludingGSTAttributeCode, null); 
				/* If pricing calculation fails */
				if (amountIncludingGST == null) {
					throw new IllegalArgumentException(
							"Something went wrong during pricing calculations. Price for item cannot be empty");
				}

				/* Convert dollars into cents */
				Money roundedItemPriceInCents = PaymentUtils.getRoundedMoneyInCents(amountIncludingGST);
				/* Owner => Buyer */
				QPaymentsUser buyer = PaymentUtils.getPaymentsUser(buyerBe, devMode);
				//Passing devMode boolean as the PRJ's account have different Payment User ID for production and dev
				/* Ch40 => Seller */
				QPaymentsUser seller = PaymentUtils.getPaymentsUser(sellerBe, devMode);

				/* get item name */
				String paymentsItemName = null;
				if(paymentTitle.equalsIgnoreCase("JOB_OFFER")) {
					paymentsItemName ="Payment for "+PaymentUtils.getPaymentsItemName(loadBe, begBe);
				}else {
					paymentsItemName = paymentTitle+" Payment for "+PaymentUtils.getPaymentsItemName(loadBe, begBe);					
				}
				rules.println("payments item name ::" + paymentsItemName);
				 

				/* Not mandatory */
				String begDescription = loadBe.getValue("PRI_DESCRIPTION", null);

				try {
					
					/* bundling all the info into Item object */
					QPaymentsItem item = null;
					if(hasFee) {						
						/* get fees */
						BaseEntity projectBe = rules.getProject();
						if(projectBe != null) {
							List<String> feesList = createPaymentFee(srcBe, projectBe, paymentsToken);
							//String paymentFeeId = createPaymentFee(srcBe, feeAttributeCode,  paymentsToken);
							System.out.println("payment fee Id ::" + feesList.toString());
							if(feesList.size() != 0) {
								//String[] feeArr = { paymentFeeId };
								String[] feesArr = new String[feesList.size()];
								feesArr = feesList.toArray(feesArr);
							    item = new QPaymentsItem(paymentsItemName, begDescription,
										PaymentTransactionType.escrow, roundedItemPriceInCents.getNumber().doubleValue(),
										amountIncludingGST.getCurrency(), feesArr, buyer, seller);
							}else {
								rules.println("Error!! Cannot create payment fee. The fees list is empty.");
							}
						}else {
							rules.println("Error!! Cannot create payment fee. The Project BaseEntity is null.");
					    }
					}
					
					if(!hasFee) {					
					
						 item = new QPaymentsItem(paymentsItemName, begDescription,
								PaymentTransactionType.express, roundedItemPriceInCents.getNumber().doubleValue(),
								amountIncludingGST.getCurrency(), null, buyer, seller);
					 }
					/* Hitting payments item creation API */
					String itemCreationResponse = PaymentEndpoint.createPaymentItem(JsonUtils.toJson(item),
							paymentsToken);

					if (itemCreationResponse != null) {
						QPaymentsAssemblyItemResponse itemResponsePojo = JsonUtils.fromJson(itemCreationResponse,
								QPaymentsAssemblyItemResponse.class);
						itemId = itemResponsePojo.getId();
					}

				} catch (PaymentException e) {
					String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
					throw new IllegalArgumentException(getFormattedErrorMessage);
				}

			} catch (IllegalArgumentException e) {

				/* Redirect to home if item creation fails */
				rules.redirectToHomePage();

				String jobId = begBe.getValue("PRI_JOB_ID", null);
				BaseEntity userBe = rules.getUser();

				/* Send toast */
				String toastMessage = paymentTitle+" Payment item creation failed for the job with ID : #" + jobId + ", "
						+ e.getMessage();

				if (userBe != null) {
					String[] recipientArr = { userBe.getCode() };
					rules.sendToastNotification(recipientArr, toastMessage, "warning");
				}

				/* Send slack notification */
				rules.sendSlackNotification(toastMessage);
			}
		} else {
			String slackMessage = paymentTitle+" Payment item creation would fail since begCode is null.";
			rules.sendSlackNotification(slackMessage);
		}

		return itemId;
	}

	@Override
	public List<String> createPaymentFee(BaseEntity srcBe, BaseEntity projectBe, String paymentsToken)
			throws IllegalArgumentException {
		List<String> paymentFeeIds = new ArrayList<String>();
		if(srcBe != null && projectBe != null && paymentsToken != null ) {
			/* This attribute is being setup in the startup of the rulesservice. It includes only then mandatory fees 
			 * to be included with the product transactions */
			String mandatoryFees = rules.baseEntity.getBaseEntityValueAsString(projectBe.getCode(), "PRI_MANDATORY_PRODUCT_FEES");
			if(mandatoryFees != null) {
				List<String> feesList = StringFormattingUtils.splitCharacterSeperatedStringToList(mandatoryFees, ",");
				if(feesList.size() > 0) {
					for(String feeAttributeCode : feesList) {
						Attribute attribute = RulesUtils.getAttribute(feeAttributeCode, rules.getToken());
						if(attribute != null) {
							try {
								rules.println("The fee title is  :: "+attribute.getName());
								/* get fee object with all fee-info */
								QPaymentsFee feeObj = PaymentUtils.getFeeObject(srcBe, feeAttributeCode, attribute.getName());
								if (feeObj != null) {
									try {
										/* Hit the fee creation API */
										String feeResponse = PaymentEndpoint.createFees(JsonUtils.toJson(feeObj), paymentsToken);
										QPaymentsFee feePojo = JsonUtils.fromJson(feeResponse, QPaymentsFee.class);
					
										/* Get the fee ID */
										paymentFeeIds.add(feePojo.getId());
									} catch (PaymentException e) {
										String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
										throw new IllegalArgumentException(getFormattedErrorMessage);
									}
								}
							} catch (IllegalArgumentException e) {
								throw new IllegalArgumentException(e.getMessage());
							}
						}
					}
				}else {
					rules.println("Error!! The Mandatory fees list is empty.");
				}
			}else {
				rules.println("Error!! The Mandatory fees values is not assigned to the project.");
			}
			
		}
		return paymentFeeIds;
	}

	@Override
	public Boolean makePayment(BaseEntity buyerBe, BaseEntity sellerBe, BaseEntity srcBe,
			String paymentItemIdAttributeCode, String paymentAmountAttributeCode, String paymentTitle,
			String authToken) {
		Boolean isMakePaymentSuccess = false;
		if (srcBe != null && buyerBe != null && sellerBe != null && paymentItemIdAttributeCode != null && 
				paymentAmountAttributeCode != null ) {
			String itemId = null;
			//String paymentTitleComplete = null;
			BaseEntity begBe = null;
			try {
				if(srcBe.getCode().startsWith("BEG_")) {
					begBe = srcBe;	
					//String paymentTitleIncomplete = paymentItemIdAttributeCode.replace("PRI_", "");					
					//paymentTitleComplete = paymentTitleIncomplete.replace("_ITEM_ID", "");
					//System.out.println("The payment title is  :: "+paymentTitleComplete);
				 } else if (srcBe.getCode().startsWith("OFR_")) {
					begBe = rules.baseEntity.getParent(srcBe.getCode(), "LNK_BEG", "OFFER");
					//paymentTitleComplete = "JOB PRICE";
				 }
				
				itemId = srcBe.getValue(paymentItemIdAttributeCode, null);
				QMakePayment makePaymentObj = PaymentUtils.getMakePaymentObj(buyerBe, begBe, paymentItemIdAttributeCode);

				/* To get the type of payment (Bank account / card) */
				PaymentType paymentType = PaymentUtils.getPaymentMethodType(buyerBe,
						makePaymentObj.getAccount().getId());

				/*
				 * if the payment type is bank account, there is another step of debit
				 * authorization
				 */
				/* Step 1 for bankaccount : DEBIT AUTHORIZATION */
				if (paymentType != null && paymentType.equals(PaymentType.BANK_ACCOUNT)) {
					//debitAuthorityForBankAccount(srcBe, makePaymentObj, authToken);
					debitAuthorityForBankAccount(srcBe, makePaymentObj, paymentAmountAttributeCode, authToken);
				}

				/* Step 2 for bankaccount : Make payment API call */
				/* Step 1 for card : Make payment API call */
				try {

					String paymentResponse = PaymentEndpoint.makePayment(itemId, JsonUtils.toJson(makePaymentObj),
							authToken);
					isMakePaymentSuccess = true;
					QPaymentsAssemblyItemResponse makePaymentResponseObj = JsonUtils.fromJson(paymentResponse,
							QPaymentsAssemblyItemResponse.class);
					String paymentDepositRefIdAttribute = null;
                      if(!paymentTitle.equalsIgnoreCase("JOB_OFFER")) {
                    	  	paymentDepositRefIdAttribute = "PRI_"+paymentTitle+"_DEPOSIT_REFERENCE_ID";
                      }else {
                    	  paymentDepositRefIdAttribute = "PRI_DEPOSIT_REFERENCE_ID";
                      }
					/* save deposit reference as an attribute to beg */
					Answer depositReferenceAnswer = new Answer(begBe.getCode(), begBe.getCode(), paymentDepositRefIdAttribute, makePaymentResponseObj.getDepositReference());
					rules.baseEntity.saveAnswer(depositReferenceAnswer);

				} catch (PaymentException e) {
					String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
					throw new IllegalArgumentException(getFormattedErrorMessage);
				}

			} catch (IllegalArgumentException e) {
				//redirectToHomePage();
				String begTitle = srcBe.getValue("PRI_TITLE", null);
				String sellerFirstName = sellerBe.getValue("PRI_FIRSTNAME", null);
				String[] recipientArr = { buyerBe.getCode() };
				String toastMessage = "Unfortunately, processing payment into " + sellerFirstName
						+ "'s account for the job - " + begTitle + " has failed. " + e.getMessage();
				rules.sendToastNotification(recipientArr, toastMessage, "warning");
				rules.sendSlackNotification(
						toastMessage + ". Job code : " + srcBe.getCode());
			}
		} else {
			//redirectToHomePage();
			String slackMessage = "Processing payment for the job - " + srcBe.getCode()
					+ " has failed. UserBE/BegBE is null. User code :" + buyerBe.getCode();
			rules.sendSlackNotification(slackMessage);
		}
		return isMakePaymentSuccess;
	}

	@Override
	public void debitAuthorityForBankAccount(BaseEntity srcBe, QMakePayment makePaymentObj, String priceAttributeCode,
			String authToken) throws IllegalArgumentException {
		Money buyerPriceString = srcBe.getValue(priceAttributeCode, null);

		/* if price calculation fails, we handle it */
		if (buyerPriceString == null) {
			throw new IllegalArgumentException(
					"Something went wrong during pricing calculation. Item price cannot be empty");
		}

		try {
			/* Get the rounded money in cents */
			Money offerPriceStringInCents = PaymentUtils.getRoundedMoneyInCents(buyerPriceString);

			/* bundling the debit-authority object */
			QPaymentAuthorityForBankAccount paymentAuthorityObj = new QPaymentAuthorityForBankAccount(
					makePaymentObj.getAccount(), offerPriceStringInCents.getNumber().doubleValue());

			/* API call for debit authorization for bank-account */
			PaymentEndpoint.getdebitAuthorization(JsonUtils.toJson(paymentAuthorityObj), authToken);

		} catch (PaymentException e) {
			String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
			throw new IllegalArgumentException(getFormattedErrorMessage);
		}

	}

	@Override
	public String fetchOneTimePaymentsToken(String paymentsUserId, String paymentToken, AuthorizationPaymentType type) {
		try {
			QPaymentsUser user = new QPaymentsUser(paymentsUserId);
			QPaymentsAuthorizationToken tokenObj = new QPaymentsAuthorizationToken(type, user);

			try {
				String stringifiedTokenObj = JsonUtils.toJson(tokenObj);
				String tokenResponse = PaymentEndpoint.authenticatePaymentProvider(stringifiedTokenObj, paymentToken);

				if (tokenResponse != null) {
					QPaymentsAuthorizationToken tokenCreationResponseObj = JsonUtils.fromJson(tokenResponse,
							QPaymentsAuthorizationToken.class);
					token = tokenCreationResponseObj.getToken();
				}

			} catch (PaymentException e) {
				String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
				throw new IllegalArgumentException(getFormattedErrorMessage);
			}

		} catch (IllegalArgumentException e) {
			log.error("Exception occured during one-time payments token creation for user : " + rules.getUser().getCode()
					+ ", Error message : " + e.getMessage());
		}
		return token;
	}

	@Override
	public Boolean releasePayment(BaseEntity begBe, BaseEntity buyerBe, BaseEntity sellerBe, String authToken) {
		Boolean isReleasePayment = false;
		try {
			String paymentItemId = begBe.getValue("PRI_ITEM_ID", null);
			QReleasePayment releasePaymentObj = new QReleasePayment(true);
			try {
				String paymentResponse = PaymentEndpoint.releasePayment(paymentItemId,
						JsonUtils.toJson(releasePaymentObj), authToken);
				QReleasePayment paymentResponseObj = JsonUtils.fromJson(paymentResponse, QReleasePayment.class);
				String depositReferenceId = paymentResponseObj.getId();

				List<Answer> answers = new ArrayList<Answer>();

				/* Adding Release Payment Done status */
				Answer paymentDoneAns = new Answer(buyerBe.getCode(), begBe.getCode(), "PRI_IS_RELEASE_PAYMENT_DONE",
						"TRUE");
				answers.add(paymentDoneAns);

				/* save disbursement id as a beg attribute */
				Answer releasePaymentDisbursementAns = new Answer(rules.getUser().getCode(), begBe.getCode(),
						"PRI_PAYMENTS_DISBURSEMENT_ID", depositReferenceId);
				answers.add(releasePaymentDisbursementAns);

				rules.baseEntity.saveAnswers(answers);
				isReleasePayment = true;

			} catch (PaymentException e) {
				String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
				throw new IllegalArgumentException(getFormattedErrorMessage);
			}

		} catch (IllegalArgumentException e) {
			String begTitle = begBe.getValue("PRI_TITLE", null);
			String sellerFirstName = sellerBe.getValue("PRI_FIRSTNAME", null);
			String[] recipientArr = { buyerBe.getCode() };
			String toastMessage = "Unfortunately, payment release to " + sellerFirstName + " for the job - " + begTitle
					+ " has failed." + e.getMessage();

			/* send error toast message */
			rules.sendToastNotification(recipientArr, toastMessage, "warning");

			/* send slack notification */
			rules.sendSlackNotification(toastMessage + ". Job code : " + begBe.getCode());
		}
		return isReleasePayment;
	}

	@Override
	public Boolean disburseAccount(String paymentsUserId, QPaymentMethod paymentMethodObj, String authToken) {
		Boolean isDisbursementSuccess = false;
		if (paymentsUserId != null && paymentMethodObj != null) {
			try {
				/* Get the ID of the payment method (that's all we'll need) */
				String paymentMethodId = paymentMethodObj.getId();

				/* set the payment ID in the object */
				QPaymentMethod requestBodyObj = new QPaymentMethod(paymentMethodId);

				/* create disbursement object */
				QPaymentsDisbursement disbursementObj = new QPaymentsDisbursement(requestBodyObj);

				try {
					/* Hit the API. This API has no response string */
					PaymentEndpoint.disburseAccount(paymentsUserId, JsonUtils.toJson(disbursementObj), authToken);
					isDisbursementSuccess = true;

				} catch (PaymentException e) {
					String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
					throw new IllegalArgumentException(getFormattedErrorMessage);
				}
			} catch (IllegalArgumentException e) {
				log.error("Exception occured during disbursement. " + e.getMessage());
			}
		} else {
			log.error("Payment disbursment will not happen. payments user ID/Payment method is null.");
		}
		return isDisbursementSuccess;
	}

	@Override
	public Boolean deleteBankAccount(String bankAccountId, String authKey) {
		Boolean isDeleted = false;
		try {
			PaymentEndpoint.deleteBankAccount(bankAccountId, authKey);
			isDeleted = true;
		} catch (PaymentException e) {
		}
		return isDeleted;
	}

	@Override
	public Boolean deleteCard(String cardAccountId, String authKey) {
		Boolean isDeleted = false;
		try {
			PaymentEndpoint.deleteCardAccount(cardAccountId, authKey);
			isDeleted = true;
		} catch (PaymentException e) {
		}
		return isDeleted;
	}


}
