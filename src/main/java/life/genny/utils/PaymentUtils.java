package life.genny.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.Logger;
import org.javamoney.moneta.Money;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.internal.LinkedTreeMap;

import life.genny.qwanda.Answer;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.exception.PaymentException;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.payments.QMakePayment;
import life.genny.qwanda.payments.QPaymentMethod;
import life.genny.qwanda.payments.QPaymentMethod.PaymentType;
import life.genny.qwanda.payments.QPaymentsCompany;
import life.genny.qwanda.payments.QPaymentsCompanyContactInfo;
import life.genny.qwanda.payments.QPaymentsFee;
import life.genny.qwanda.payments.QPaymentsFee.FEETYPE;
import life.genny.qwanda.payments.QPaymentsFee.PAYMENT_TO;
import life.genny.qwanda.payments.QPaymentsLocationInfo;
import life.genny.qwanda.payments.QPaymentsUser;
import life.genny.qwanda.payments.QPaymentsUserContactInfo;
import life.genny.qwanda.payments.QPaymentsUserInfo;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyItemResponse;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyItemSearchResponse;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyUser;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyUserResponse;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyUserSearchResponse;
import life.genny.qwandautils.JsonUtils;

public class PaymentUtils {

	/* Create a new logger for this class */
	protected static final Logger log = org.apache.logging.log4j.LogManager.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	/* Define constants */
	public static final String DEFAULT_PAYMENT_TYPE = "escrow";
	public static final String PROVIDER_TYPE_BANK = "bank";

	/**
	* Returns the authentication key for the payments service - key is generated as per documentation here
	* https://github.com/genny-project/payments/blob/master/docs/auth-tokens.md
	*/
	@SuppressWarnings("unchecked")
	public static String getAssemblyAuthKey() {
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
		return base64Encoder(authObj.toJSONString());
	}

	/* Encoded a UTF8 string in Base64 */
	public static String base64Encoder(String plainString) {
		String encodedString = null;

		try {
			encodedString = Base64.getEncoder().encodeToString(plainString.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return encodedString;
	}

	/* Decodes a Base64 encoded string into a JSONObject */
	public static JSONObject base64Decoder(String plainString) {
		String decodedString = null;

		/* Create a parser */
		JSONParser parser = new JSONParser();
		JSONObject authobj = new JSONObject();

		/* Attempt to decode the base64 string into a UTF8 string */
		decodedString = new String(Base64.getDecoder().decode(plainString));

		/* Attempt to parse the JSON string into a JSON object */
		try {
			authobj = (JSONObject) parser.parse(decodedString);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return authobj;
	}

	public static String apiCall(final String method, final String url, final String body, final String authToken) throws ClientProtocolException, IOException, PaymentException {
		/* Create a string to store the response */
		String retJson = "";

		/* Create a HTTP Client */
		final HttpClient client = HttpClientBuilder.create().build();

		/* Log that we are making a request */
		System.out.println( "http request payments ::" + url );

		/* Create an object to store the http response */
		HttpResponse response = null;

		/* Check what HTTP method we are calling */
		if ( method.equals( "POST" )) {
			/* Create a new post request */
			final HttpPost post = new HttpPost(url);
			/* If a auth token was provided use it */
			if ( authToken != null ) {
				post.addHeader("Authorization", authToken);
			}

			/* If a request body was provided use it */
			if ( body != null ) {
				final StringEntity input = new StringEntity(body);
				input.setContentType("application/json");
				post.setEntity(input);
			}

			/* Execute the request */
			response = client.execute(post);
		}

		if ( method.equals( "PUT" )) {
			/* Create a new put request */
			final HttpPut put = new HttpPut(url);
			/* If a auth token was provided use it */
			if ( authToken != null ) {
				put.addHeader("Authorization", authToken);
			}

			/* If a request body was provided use it */
			if ( body != null ) {
				final StringEntity input = new StringEntity(body);
				input.setContentType("application/json");
				put.setEntity(input);
			}

			/* Execute the request */
			response = client.execute(put);
		}

		if ( method.equals( "GET" )) {
			/* Create a new get request */
			final HttpGet get = new HttpGet(url);
			/* If a auth token was provided use it */
			if ( authToken != null ) {
				get.addHeader("Authorization", authToken);
			}

			/* Execute the request */
			response = client.execute(get);
		}

		if ( method.equals( "DELETE" )) {
			/* Create a new delete request */
			final HttpDelete delete = new HttpDelete(url);
			/* If a auth token was provided use it */
			if ( authToken != null ) {
				delete.addHeader("Authorization", authToken);
			}

			/* Execute the request */
			response = client.execute(delete);
		}


		/* Read in the response */
		final BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		String line = "";
		while ((line = rd.readLine()) != null) {
			retJson += line;
			;
		}

		/* Get the response code */
		int responseCode = response.getStatusLine().getStatusCode();

		/* Log some information about the response */
		System.out.println("response code ::"+responseCode);
		System.out.println("response ::"+response.getStatusLine());
		System.out.println("response body::"+retJson);

		/* If the response code isn't a valid one throw an error */
		if(responseCode > 299) {
			throw new PaymentException(retJson);
		}

		/* Return the response JSON */
		return retJson;
	}

	/* Creates a new post request */
	public static String apiPostPaymentEntity(final String postUrl, final String entityString, final String authToken) throws ClientProtocolException, IOException, PaymentException {
		return apiCall( "POST", postUrl, entityString, authToken );
	}

	/* Creates a new post request */
	public static String apiPostPaymentEntity(final String postUrl, final String authToken) throws ClientProtocolException, IOException, PaymentException {
		return apiCall( "POST", postUrl, null, authToken );
	}

	/* Creates a new get request */
	public static String apiGetPaymentResponse(final String getUrl, final String authToken) throws ClientProtocolException, IOException, PaymentException {
		return apiCall( "GET", getUrl, null, authToken );
	}

	/* Creates a new put request */
	public static String apiPutPaymentEntity(final String putUrl, final String entityString, final String authToken) throws ClientProtocolException, IOException, PaymentException {
		return apiCall( "PUT", putUrl, entityString, authToken );
	}

	/* Creates a new delete request */
	public static String apiDeletePaymentEntity(final String deleteUrl, final String authToken) throws ClientProtocolException, IOException, PaymentException {
		return apiCall( "DELETE", deleteUrl, null, authToken );
	}

	/* Generates a random ID / UUID for a new Assembly user */
	public static String getAssemblyId(String token) {
		return UUID.randomUUID().toString();
	}

	/* Checks whether a Assembly user with the provided ID exists already */
	public static Boolean checkIfAssemblyUserExists(String assemblyUserId) {
		Boolean isExists = false;

		/* If a user ID was provided then... */
		if(assemblyUserId != null) {
			/* Get an authentication token for the API */
			String authToken = getAssemblyAuthKey();
			String assemblyUserString = null;

			/* Attempt to get the user with the specified ID, if it returns an error we know the user doesn't exist */
			try {
				assemblyUserString = PaymentEndpoint.getPaymentsUserById(assemblyUserId, authToken);
				if(assemblyUserString != null && !assemblyUserString.contains("error")) {
					System.out.println("assembly user string ::"+assemblyUserString);
					isExists = true;
				}
			} catch (PaymentException e) {
				log.error("Assembly user not found, returning isExists=false in exception handler");
				isExists = false;
			}
		}

		return isExists;
	}

	/* Creates a new user in Assembly */
	public static QPaymentsUserInfo getPaymentsUserInfo(BaseEntity userBe) throws IllegalArgumentException {

		QPaymentsUserInfo personalInfo = null;

		if(userBe != null) {

			String formattedDOBString = null;
			String firstName = userBe.getValue("PRI_FIRSTNAME", null);
			String lastName = userBe.getValue("PRI_LASTNAME", null);
			LocalDate dob = userBe.getValue("PRI_DOB", null);

			/* If the date of birth is provided format it correctly */
			if(dob != null) {
				System.out.println("dob string ::"+dob);
				DateTimeFormatter assemblyDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
				formattedDOBString = assemblyDateFormatter.format(dob);
				System.out.println("another formatted dob ::"+formattedDOBString);

				dob = LocalDate.parse(formattedDOBString, assemblyDateFormatter);
			}

			personalInfo = new QPaymentsUserInfo(firstName, lastName, dob);
		}

		return personalInfo;

	}

	public static QPaymentsUserContactInfo getPaymentsUserContactInfo(BaseEntity userBe) throws IllegalArgumentException {

		QPaymentsUserContactInfo userContactInfo = null;

		if(userBe != null) {

			String email = userBe.getValue("PRI_EMAIL", null);
			userContactInfo = new QPaymentsUserContactInfo(email);
		}

		return userContactInfo;

	}

	public static QPaymentsLocationInfo getPaymentsLocationInfo(BaseEntity be) throws IllegalArgumentException {

		QPaymentsLocationInfo userLocationInfo = null;
		String city = null;
		String country = null;

		if(be != null) {

			String addressLine1 = be.getValue("PRI_ADDRESS_ADDRESS1", null);
			String addressLine2 = be.getValue("PRI_ADDRESS_ADDRESS2", null);

			city = be.getValue("PRI_ADDRESS_CITY", null);
			if(city == null) {
				city = be.getValue("PRI_ADDRESS_SUBURB", null);
			}

			country = be.getValue("PRI_ADDRESS_COUNTRY", null);
			if(country == null) {
				city = "AU";
			}

			String state = be.getValue("PRI_ADDRESS_STATE", null);
			String postCode = be.getValue("PRI_ADDRESS_POSTCODE", null);

			userLocationInfo = new QPaymentsLocationInfo(addressLine1, city, state, postCode, country);

			/* address line2 is not mandatory. If available, assembly will be updated */
			if(addressLine2 != null) {
				userLocationInfo.setAddressLine2(addressLine2);
			}
		}

		return userLocationInfo;

	}

	public static QPaymentsCompanyContactInfo getPaymentsCompanyContactInfo(BaseEntity companyBe) throws IllegalArgumentException {

		QPaymentsCompanyContactInfo companyObj = null;

		String companyPhoneNumber = companyBe.getValue("PRI_LANDLINE", null);
		companyObj = new QPaymentsCompanyContactInfo(companyPhoneNumber);

		return companyObj;
	}


	/* Returns a users information based upon their user ID */
	public static String getPaymentsUser(String assemblyUserId, String authToken){
		String responseString = null;
		try {
			responseString = PaymentEndpoint.getPaymentsUserById(assemblyUserId, authToken);
		} catch (PaymentException e) {
			e.printStackTrace();
		}
		return responseString;
	}

	/* Called when a particular attribute is updated for a user */
	public static QPaymentsUser updateUserInfo(String paymentsUserId, String attributeCode, String value) {

		QPaymentsUserInfo personalInfo = null;
		QPaymentsLocationInfo locationInfo = null;
		QPaymentsUserContactInfo userContactInfo = null;
		QPaymentsUser user = null;

		/* Personal Info Update Objects  */

		/* Check which value was updated and update in Assembly accordingly */
		switch (attributeCode) {
			case "PRI_FIRSTNAME":
				personalInfo = new QPaymentsUserInfo();
				personalInfo.setFirstName(value);
				break;

			case "PRI_LASTNAME":
				personalInfo = new QPaymentsUserInfo();
				personalInfo.setLastName(value);
				break;

			case "PRI_DOB":
			/* Format the date of birth */
				personalInfo = new QPaymentsUserInfo();
				DateTimeFormatter assemblyDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
				LocalDate date = LocalDate.parse(value.toString(), formatter);
				String formattedDOBString = assemblyDateFormatter.format(date);
				System.out.println("another formatted dob ::" + formattedDOBString);
				personalInfo.setDob(LocalDate.parse(formattedDOBString, assemblyDateFormatter));
				break;

			case "PRI_EMAIL":
				userContactInfo = new QPaymentsUserContactInfo();
				userContactInfo.setEmail(value);
				break;

			case "PRI_MOBILE":
				userContactInfo = new QPaymentsUserContactInfo();
				userContactInfo.setMobile(value);
				break;

			case "PRI_ADDRESS_ADDRESS1":
				locationInfo = new QPaymentsLocationInfo();
				locationInfo.setAddressLine1(value);
				break;

			case "PRI_ADDRESS_ADDRESS2":
				locationInfo = new QPaymentsLocationInfo();
				locationInfo.setAddressLine2(value);
				break;

			case "PRI_ADDRESS_SUBURB":
				locationInfo = new QPaymentsLocationInfo();
				locationInfo.setAddressLine2(value);
				locationInfo.setCity(value);
				break;

			case "PRI_ADDRESS_CITY":
				locationInfo = new QPaymentsLocationInfo();
				locationInfo.setCity(value);
				break;

			case "PRI_ADDRESS_STATE":
				locationInfo = new QPaymentsLocationInfo();
				locationInfo.setState(value);
				break;

			case "PRI_ADDRESS_COUNTRY":
				locationInfo = new QPaymentsLocationInfo();
				locationInfo.setCountry(value);
				break;

			case "PRI_ADDRESS_POSTCODE":
				locationInfo = new QPaymentsLocationInfo();
				locationInfo.setPostcode(value);
				break;
		}

		/* For Assembly Personal Information Update */
		if(personalInfo != null  && paymentsUserId != null) {
			user = new QPaymentsUser();
			user.setId(paymentsUserId);
			user.setPersonalInfo(personalInfo);
		}

		if (userContactInfo != null && paymentsUserId != null) {
			user = new QPaymentsUser();
			user.setId(paymentsUserId);
			user.setContactInfo(userContactInfo);
		}

		if (locationInfo != null && paymentsUserId != null) {
			user = new QPaymentsUser();
			user.setId(paymentsUserId);
			user.setLocation(locationInfo);
		}

		/* Return the response */
		return user;
	}

	/* Called when a particular attribute is updated for a company */
	public static QPaymentsCompany updateCompanyInfo(String userId, String companyId, String attributeCode, String value) {

		/* Company Info Update Objects */
		QPaymentsCompany company = new QPaymentsCompany();
		company.setId(companyId);

		QPaymentsCompanyContactInfo companyContactObj = null;
		QPaymentsLocationInfo locationObj = null;

		/* Check what attribute was updated and update accordingly */
		switch (attributeCode) {
			case "PRI_CPY_NAME":
				company.setLegalName(value);
				company.setName(value);
				break;

			case "PRI_ABN":
				company.setTaxNumber(value);
				break;

			case "PRI_ACN":
				company.setTaxNumber(value);
				break;

			case "PRI_GST":
				company.setChargesTax(Boolean.valueOf(value));
				break;

			case "PRI_LANDLINE":
				companyContactObj = new QPaymentsCompanyContactInfo();
				companyContactObj.setPhone(value);
				break;

			case "PRI_ADDRESS_ADDRESS1":
				locationObj = new QPaymentsLocationInfo();
				locationObj.setAddressLine1(value);
				break;

			case "PRI_ADDRESS_ADDRESS2":
				locationObj = new QPaymentsLocationInfo();
				locationObj.setAddressLine2(value);
				break;

			case "PRI_ADDRESS_SUBURB":
				locationObj = new QPaymentsLocationInfo();
				locationObj.setCity(value);
				break;

			case "PRI_ADDRESS_CITY":
				locationObj = new QPaymentsLocationInfo();
				locationObj.setCity(value);
				break;

			case "PRI_ADDRESS_STATE":
				locationObj = new QPaymentsLocationInfo();
				locationObj.setState(value);
				break;

			case "PRI_ADDRESS_COUNTRY":
				locationObj = new QPaymentsLocationInfo();
				locationObj.setCountry(value);
				break;

			case "PRI_ADDRESS_POSTCODE":
				locationObj = new QPaymentsLocationInfo();
				locationObj.setPostcode(value);
				break;
		}

		/* For Assembly User Company Information Update */
		if (companyContactObj != null && companyId != null) {
			company.setContactInfo(companyContactObj);
			QPaymentsUser user = new QPaymentsUser(userId);
			company.setUser(user);

		}

		if (locationObj != null) {
			company.setLocation(locationObj);
			QPaymentsUser user = new QPaymentsUser(userId);
			company.setUser(user);

		}
		return company;
	}

	/* To get payments user with the paymentsId field */
	public static QPaymentsUser getPaymentsUser(BaseEntity userBe) throws IllegalArgumentException{

		String paymentsUserId  = userBe.getValue("PRI_ASSEMBLY_USER_ID", null);
		QPaymentsUser user = new QPaymentsUser(paymentsUserId);
		return user;
	}

	/* Get the name of the job with job id AS payments items name */
	public static String getPaymentsItemName(BaseEntity loadBe, BaseEntity begBe) {

		String paymentsItemName = null;
		if (loadBe != null) {

			/* Get the title, description and job ID for this item from the base entity group */
			paymentsItemName = loadBe.getValue("PRI_TITLE", null);
			// Hack to stop undefined name
			if (StringUtils.isBlank(paymentsItemName)) {
				paymentsItemName = loadBe.getValue("PRI_NAME", null);
				if (StringUtils.isBlank(paymentsItemName)) {
					paymentsItemName = loadBe.getName();
					if (StringUtils.isBlank(paymentsItemName)) {
						paymentsItemName = "Job #"+loadBe.getId();
						log.error("Job Name and Title are empty , using job id");
					}
				}
			}

			/* If job ID is present for beg, concat it to the itemName */
			String begJobId = null;
			if(begBe != null) {
				begJobId = begBe.getValue("PRI_JOB_ID", null);
				paymentsItemName = paymentsItemName.concat(", Job #" + begJobId);
			}
		}

		return paymentsItemName;
	}

	public static Money getRoundedMoneyInCents(Money money) {

		Money roundedMoneyInCents = null;
		if(money != null) {
			BigDecimal begPrice = new BigDecimal(money.getNumber().doubleValue());

			// 350 Dollars sent to Assembly as 3.50$, so multiplying with 100
			/* Convert dollars into cents */
			BigDecimal finalFee = begPrice.multiply(new BigDecimal(100));

			/* Round off to 2 decimal places */
			String roundedMoneyString = String.format("%.2f", finalFee);

			/* Converting string back to money */
			roundedMoneyInCents = Money.of(Double.valueOf(roundedMoneyString), money.getCurrency());
		}
		return roundedMoneyInCents;
	}

	/* Set all the known information in the fee object */
	public static QPaymentsFee getFeeObject(BaseEntity offerBe) throws IllegalArgumentException {

		Money begFee = offerBe.getValue("PRI_OFFER_FEE_INC_GST", null);
		QPaymentsFee feeObj = null;

		if(begFee != null) {

			Money feeInCents = getRoundedMoneyInCents(begFee);
			System.out.println("money in in cents ::"+feeInCents);

			if(feeInCents == null) {
				throw new IllegalArgumentException("Something went wrong during pricing calculations. Fee for item cannot be empty");
			}

			feeObj = new QPaymentsFee("Channel40 fee", FEETYPE.FIXED, feeInCents.getNumber().doubleValue(), PAYMENT_TO.buyer);
		}
		return feeObj;

	}


	public static Boolean checkIfAnswerContainsPaymentAttribute(QDataAnswerMessage m) {
		Boolean isAnswerContainsPaymentAttribute = false;

		if(m != null) {
			Answer[] answers = m.getItems();
			for(Answer answer : answers) {
				String attributeCode = answer.getAttributeCode();
				if(attributeCode.contains("PRI_PAYMENT_METHOD")) {
					isAnswerContainsPaymentAttribute = true;
					break;
				}
			}
		}

		return isAnswerContainsPaymentAttribute;
	}

	/* Returns the payment method for a user and account ID */
	public static PaymentType getPaymentMethodType(BaseEntity userBe, String accountId) {

		System.out.println("in getPaymentMethodType method");

		PaymentType paymentType = null;
		String paymentMethods = userBe.getValue("PRI_USER_PAYMENT_METHODS", null);
			System.out.println("all payment methods of owners ::"+paymentMethods);

		if (paymentMethods != null) {

			JSONArray paymentMethodArr = JsonUtils.fromJson(paymentMethods, JSONArray.class);
			/* iterating through all owner payment methods */
			for (Object paymentMethodObj : paymentMethodArr) {

				/* converting the individual payment method types to POJO */
				LinkedTreeMap<String, String> paymentMethod = (LinkedTreeMap<String, String>) paymentMethodObj;
				Gson gson = new Gson();
				JsonElement jsonElement = gson.toJsonTree(paymentMethod);
				QPaymentMethod paymentMethodPojo = gson.fromJson(jsonElement, QPaymentMethod.class);

				String paymentMethodId = paymentMethodPojo.getId();
				System.out.println("type ::" + paymentMethodPojo.getType());
				System.out.println("id ::" + paymentMethodId );

				/* Return the payment type when the account id matches with the user payment method */
				if(accountId.equals(paymentMethodId)) {
					paymentType = paymentMethodPojo.getType();
				}
			}
		}
		return paymentType;

	}

	/* Bulk updates a user in Assembly */
	public static QPaymentsUser getCompleteUserObj(BaseEntity userBe, String paymentsUserId) throws IllegalArgumentException {

		/* Get all of the users information */
		String firstName = userBe.getValue("PRI_FIRSTNAME", null);
		String lastName = userBe.getValue("PRI_LASTNAME", null);
		LocalDate dob = userBe.getValue("PRI_DOB", null);
		String email = userBe.getValue("PRI_EMAIL", null);
		String addressLine1 = userBe.getValue("PRI_ADDRESS_ADDRESS1", null);
		String city = userBe.getValue("PRI_ADDRESS_SUBURB", null);
		String state = userBe.getValue("PRI_ADDRESS_STATE", null);
		String country = userBe.getValue("PRI_ADDRESS_COUNTRY", null);
		String postCode = userBe.getValue("PRI_ADDRESS_POSTCODE", null);

		/* Create objects to store the request */
		QPaymentsUserInfo personalInfoObj = new QPaymentsUserInfo(firstName, lastName, dob);
		QPaymentsLocationInfo locationObj = new QPaymentsLocationInfo(addressLine1, city, state, postCode, country);
		QPaymentsUserContactInfo contactInfoObj = new QPaymentsUserContactInfo(email);
		QPaymentsUser userObj = new QPaymentsUser(paymentsUserId, personalInfoObj, contactInfoObj, locationObj);

		return userObj;

	}

	/* Bulk Update a complete company profile */
	public static QPaymentsCompany getCompleteCompanyObj(BaseEntity userBe, BaseEntity companyBe, String paymentsUserId) throws IllegalArgumentException {

		/* Get the companies information */
		String companyName = companyBe.getValue("PRI_CPY_NAME", null);
		String abn = companyBe.getValue("PRI_ABN", null);
		if(abn == null) {
			abn = companyBe.getValue("PRI_ACN", null);
		}
		Boolean gst = companyBe.getValue("PRI_GST", false);
		String companyPhone = companyBe.getValue("PRI_LANDLINE", null);
		String addressLine1 = companyBe.getValue("PRI_ADDRESS_ADDRESS1", null);
		String city = companyBe.getValue("PRI_ADDRESS_SUBURB", null);
		String state = companyBe.getValue("PRI_ADDRESS_STATE", null);
		String country = companyBe.getValue("PRI_ADDRESS_COUNTRY", null);
		String postCode = companyBe.getValue("PRI_ADDRESS_POSTCODE", null);

		/* Create objects to store the request */
		QPaymentsLocationInfo locationObj = new QPaymentsLocationInfo(addressLine1, city, state, postCode, country);
		QPaymentsCompanyContactInfo contactInfoObj = new QPaymentsCompanyContactInfo(companyPhone);
		QPaymentsUser userObj = new QPaymentsUser(paymentsUserId);
		QPaymentsCompany companyObj = new QPaymentsCompany(companyName, companyName, abn, gst, locationObj, userObj, contactInfoObj);

		return companyObj;

	}

	/* Returns true of false depending on whether the payment method provided is a bank account */
	public static Boolean isBankAccount(QPaymentMethod paymentMethod) {
		Boolean isBankAccount = false;

		if(paymentMethod != null) {

			if(paymentMethod.getType().equals(PaymentType.BANK_ACCOUNT)) {
				isBankAccount = true;
			}
		}

		return isBankAccount;
	}

	public static void updateUserPhoneNumber(BaseEntity userBe, String assemblyUserId, String assemblyAuthKey) {

		String responseString = null;

		String phoneNumber = userBe.getValue("PRI_MOBILE", null);

		QPaymentsUser user = new QPaymentsUser(assemblyUserId);
		QPaymentsUserContactInfo contactObj = new QPaymentsUserContactInfo();
		contactObj.setMobile(phoneNumber);
		user.setContactInfo(contactObj);
		try {
			responseString = PaymentEndpoint.updatePaymentsUser(assemblyUserId, JsonUtils.toJson(user),
					assemblyAuthKey);
			System.out.println("response string from payments user mobile-number updation ::" + responseString);
		} catch (PaymentException e) {
			log.error("Exception occured during phone updation");
		}

	}

	//offerBe, begBe, ownerBe, driverBe, assemblyAuthKey
	public static Boolean checkForAssemblyItemValidity(String itemId, BaseEntity offerBe, BaseEntity ownerBe, BaseEntity driverBe, String assemblyAuthKey) {

    Boolean isAssemblyItemValid = false;

		try {
			String itemResponse = PaymentEndpoint.getPaymentItem(itemId, assemblyAuthKey);

			/* convert string into item-search object */
			QPaymentsAssemblyItemSearchResponse itemObj = JsonUtils.fromJson(itemResponse, QPaymentsAssemblyItemSearchResponse.class);
      if(itemObj == null) return false;

			QPaymentsAssemblyItemResponse items = itemObj.getItems();

			String ownerEmail = ownerBe.getValue("PRI_EMAIL", null);
			String driverEmail = driverBe.getValue("PRI_EMAIL", null);
			/* Since itemprice = PRI_OFFER_DRIVER_PRICE_EXC_GST + PRI_OFFER_FEE_EXC_GST */
			Money driverPriceIncGST = offerBe.getValue("PRI_OFFER_DRIVER_PRICE_INC_GST", null);

			Boolean isOwnerEmail = false;
			Boolean isDriverEmail = false;
			Boolean isPriceEqual = false;

			if(items != null) {
				Double itemPrice = items.getAmount();
				System.out.println("item price ::"+itemPrice);

				QPaymentsAssemblyUser buyerOwnerInfo = items.getBuyer();

				if(buyerOwnerInfo != null && ownerEmail != null) {
					QPaymentsUserContactInfo ownerContactInfo = buyerOwnerInfo.getContactInfo();

					/* compare if item-owner is same as offer-owner */
					isOwnerEmail = ownerContactInfo.getEmail().equals(ownerEmail);
					System.out.println("Is email attribute for owner equal ?"+isOwnerEmail);

				}

				QPaymentsAssemblyUser sellerDriverInfo = items.getSeller();

				if(sellerDriverInfo != null && driverEmail != null) {
					QPaymentsUserContactInfo driverContactInfo = sellerDriverInfo.getContactInfo();

					/* compare if item-driver is same as offer-driver */
					isDriverEmail = driverContactInfo.getEmail().equals(driverEmail);
					System.out.println("Is email attribute for driver equal ?"+isDriverEmail);
				}

				if(driverPriceIncGST != null) {
					Double calculatedItemPriceInCents = driverPriceIncGST.getNumber().doubleValue() * 100;

					String str = String.format("%.2f",calculatedItemPriceInCents);
					calculatedItemPriceInCents = Double.parseDouble(str);
					/* convert into cents */
					System.out.println("calculated item price in cents ::"+calculatedItemPriceInCents);

					/* compare if item-price is same as offer-price */
					isPriceEqual = (Double.compare(calculatedItemPriceInCents, itemPrice) == 0);
					System.out.println("Is price attribute for item equal ?"+isPriceEqual);
				}

			}

			/* if comparison succeeds, no need to create new item */
			if(isOwnerEmail && isDriverEmail && isPriceEqual) {
				isAssemblyItemValid = true;
			} else {
				isAssemblyItemValid = false;
			}

		} catch (PaymentException e) {
			isAssemblyItemValid = false;
		}

		return isAssemblyItemValid;
	}

	public static QPaymentMethod getPaymentMethodSelectedByOwner(BaseEntity begBe, BaseEntity ownerBe) {

		QPaymentMethod selectedOwnerPaymentMethod = null;

		/* Gives the payment method that is selected for payment by owner */
 		String paymentMethodSelected = begBe.getValue("PRI_ACCOUNT_ID", null);
 		System.out.println("payment method selected ::" + paymentMethodSelected);

 		if(paymentMethodSelected != null) {

 			/* give all payment methods of owner */
 			String paymentMethodsOfOwner = ownerBe.getValue("PRI_USER_PAYMENT_METHODS", null);
 			System.out.println("all payment methods of owners ::"+paymentMethodsOfOwner);

			if (paymentMethodsOfOwner != null) {

				JSONArray paymentMethodArr = JsonUtils.fromJson(paymentMethodsOfOwner, JSONArray.class);

				/* iterating through all owner payment methods */
				for (Object paymentMethodObj : paymentMethodArr) {

					/* converting the individual payment method types to POJO */
					LinkedTreeMap<String, String> paymentMethod = (LinkedTreeMap<String, String>) paymentMethodObj;
					Gson gson = new Gson();
					JsonElement jsonElement = gson.toJsonTree(paymentMethod);
					QPaymentMethod paymentMethodPojo = gson.fromJson(jsonElement, QPaymentMethod.class);

					System.out.println("type ::" + paymentMethodPojo.getType());
					System.out.println("number" + paymentMethodPojo.getNumber());
					System.out.println("id ::" + paymentMethodPojo.getId());

					/*
					 * if the payment method = payment method selected by owner to make payment, we
					 * fetch that paymentMethod and save all values
					 */
					if (paymentMethodSelected.equals(paymentMethodPojo.getId())) {

						System.out.println("payment method selected is same");
						selectedOwnerPaymentMethod = new QPaymentMethod();
						selectedOwnerPaymentMethod = paymentMethodPojo;
					}
				}
			}
 		}

 		return selectedOwnerPaymentMethod;
	}

	/* Utils to get the payments user from search results based on email */
	public static String getPaymentsUserIdFromSearch(QPaymentsAssemblyUserSearchResponse response, String searchEmail) throws PaymentException{

		String paymentsUserId = null;
		int searchCount = response.getMeta().getTotal();

		/* if response is greater than 0 */
		if(searchCount > 0) {

			/* Iterate through all users in search response */
			for(QPaymentsAssemblyUserResponse userResponseObj : response.getUsers()) {

				/* If the email matches, fetch the assembly ID of the user and return it */
				String userResponseEmail = userResponseObj.getContactInfo().getEmail();
				if(userResponseEmail != null && userResponseEmail.equals(searchEmail)) {
					paymentsUserId = userResponseObj.getId();
					return paymentsUserId;
				}
			}
		} else {
			throw new PaymentException("No user found in Payments-service for the emailId :"+searchEmail);
		}
		return paymentsUserId;
	}

	public static QMakePayment getMakePaymentObj(BaseEntity userBe, BaseEntity begBe) throws IllegalArgumentException {
		String ipAddress = userBe.getValue("PRI_IP_ADDRESS", null);
		String deviceId = userBe.getValue("PRI_DEVICE_ID", null);
		String itemId = begBe.getValue("PRI_ITEM_ID", null);
		String accountId = begBe.getValue("PRI_ACCOUNT_ID", null);

		QPaymentMethod account = new QPaymentMethod(accountId);
		QMakePayment makePaymentObj = new QMakePayment(itemId, account, ipAddress, deviceId);

		return makePaymentObj;
	}

	public static QPaymentMethod getMaskedPaymentMethod(QPaymentMethod paymentMethod) {

		Character[] toBeIgnoreCharacterArr = { '-' };

		if(paymentMethod.getType().equals(PaymentType.CARD) && paymentMethod.getNumber() != null ) {
			String maskedCreditCardNumber = StringFormattingUtils.maskWithRange(paymentMethod.getNumber().replaceAll("\\s+", "-") , 0, 15,
					"X", toBeIgnoreCharacterArr);

			paymentMethod.setNumber(maskedCreditCardNumber);
		}

		if(paymentMethod.getType().equals(PaymentType.BANK_ACCOUNT) && paymentMethod.getBsb() != null && paymentMethod.getAccountNumber() != null) {
			String bsb = paymentMethod.getBsb().replaceAll("\\s+", "-");
			String accountNumber = paymentMethod.getAccountNumber().replaceAll("\\s+", "-");

			String maskedBsb = StringFormattingUtils.maskWithRange(bsb, 0, 5, "X", toBeIgnoreCharacterArr);
			String maskedAccountNumber = StringFormattingUtils.maskWithRange(accountNumber, 0, 4, "X",

					toBeIgnoreCharacterArr);

			paymentMethod.setAccountNumber(maskedAccountNumber);
			paymentMethod.setBsb(maskedBsb);
		}

		return paymentMethod;
	}


}
