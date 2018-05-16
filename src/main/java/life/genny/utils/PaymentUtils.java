package life.genny.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
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

import io.vertx.core.json.JsonObject;
import life.genny.qwanda.Answer;
import life.genny.qwanda.PaymentsResponse;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.exception.PaymentException;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.payments.QPaymentMethod;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.MergeUtil;
import life.genny.qwandautils.QwandaUtils;

public class PaymentUtils {

	/* Create a new logger for this class */
	protected static final Logger log = org.apache.logging.log4j.LogManager.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	/* Define constants */
	public static final String DEFAULT_PAYMENT_TYPE = "escrow";
	public static final String PROVIDER_TYPE_BANK = "bank";
	public static final String CONTACT_ADMIN_TEXT = "Please contact support for assistance"; /* TODO Use an environment variable to include support contact info */

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
			throw new PaymentException("Payment exception, " + retJson);
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
				assemblyUserString = PaymentEndpoint.getAssemblyUserById(assemblyUserId, authToken);
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
	@SuppressWarnings("unchecked")
	public static String createAssemblyUser(String assemblyUserId, String authToken, String token) {
		/* Get user information */
		String userCode = QwandaUtils.getUserCode(token);
		BaseEntity be = MergeUtil.getBaseEntityForAttr(userCode, token);
		String assemblyId = null;

		/* Create objects to store the information we'll send to Assembly */
		JSONObject userobj = new JSONObject();
		JSONObject personalInfoObj = new JSONObject();
		JSONObject contactInfoObj = new JSONObject();
		JSONObject locationObj = new JSONObject();

		if(be != null && assemblyUserId != null) {
			/* Get all of the users information from their base entity */
			Object firstName = be.getValue("PRI_FIRSTNAME", null);
			Object lastName = be.getValue("PRI_LASTNAME", null);
			Object dobString = be.getValue("PRI_DOB", null);
			Object email = be.getValue("PRI_EMAIL", null);
			Object addressLine1 = be.getValue("PRI_ADDRESS_ADDRESS1", null);
			
			Object city = null;
			city = be.getValue("PRI_ADDRESS_CITY", null);
			if(city == null) {
				city = be.getValue("PRI_ADDRESS_SUBURB", null);
			}
			
			
			Object state = be.getValue("PRI_ADDRESS_STATE", null);
			Object country = be.getValue("PRI_ADDRESS_COUNTRY", null);
			Object postCode = be.getValue("PRI_ADDRESS_POSTCODE", null);
			// Object mobile = be.getValue("PRI_MOBILE", null);

			/* Check a bunch of fields and store them in the object we send to Assembly if they exist */
			if(firstName != null) {
				personalInfoObj.put("firstName", firstName.toString());
			}

			if(lastName != null) {
				personalInfoObj.put("lastName", lastName.toString());
			}

			/* If the date of birth is provided format it correctly */
			if(dobString != null) {
				System.out.println("dob string ::"+dobString);
				DateTimeFormatter assemblyDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
				LocalDate dobDate = (LocalDate) dobString;
				String formattedDOBString = assemblyDateFormatter.format(dobDate);
				System.out.println("another formatted dob ::"+formattedDOBString);
				personalInfoObj.put("dob", formattedDOBString.toString());
			}

			if(email != null) {
				contactInfoObj.put("email", email.toString());
			}

			// if(mobile != null) {
			// 	contactInfoObj.put("mobile", mobile.toString());
			// }

			if(addressLine1 != null) {
				locationObj.put("addressLine1", addressLine1.toString());
			}

			if(city != null) {
				locationObj.put("city", city.toString());
			}

			if(state != null) {
				locationObj.put("state", state.toString());
			}

			/* If a country code is defined then use that, otherwise use Australia */
			if(country != null) {
				locationObj.put("country", country.toString());
			} else {
				locationObj.put("country", "AU");
			}

			if(postCode != null) {
				locationObj.put("postcode", postCode.toString());
			}
		}

		/* Combine all of the individual objects into one object that'll well send off */
		userobj.put("personalInfo", personalInfoObj);
		userobj.put("contactInfo", contactInfoObj);
		userobj.put("location", locationObj);
		userobj.put("id", assemblyUserId);

		System.out.println("user obj ::"+userobj);

		/* Attempt creating the user in Assembly */
		String paymentUserCreationResponse;
		try {
			paymentUserCreationResponse = PaymentEndpoint.createAssemblyUser(JsonUtils.toJson(userobj), authToken);
			if(!paymentUserCreationResponse.contains("error") && paymentUserCreationResponse != null) {
				assemblyId = assemblyUserId;
			}
		} catch (PaymentException e) {
			log.error("Assembly user not found, returning null in exception handler");
			assemblyId = null;
		}

		/* The user creation worked, return the user ID */
		return assemblyId;
	}

	/* Returns a users information based upon their user ID */
	public static String getPaymentsUser(String assemblyUserId, String authToken){
		String responseString = null;
		try {
			responseString = PaymentEndpoint.getAssemblyUserById(assemblyUserId, authToken);
		} catch (PaymentException e) {
			e.printStackTrace();
		}
		return responseString;
	}

	/* Called when a particular attribute is updated for a user */
	@SuppressWarnings({ "unchecked"})
	public static String updateUserInfo(String assemblyUserId, String attributeCode, String value, String assemblyAuthToken) {
		/* Log the attribute for debugging purposes */
		System.out.println("attributeCode ::" + attributeCode + ", value ::" + value);
		String responseString = null;

		/* Personal Info Update Objects  */
		JSONObject userobj = null;
		JSONObject personalInfoObj = null;
		JSONObject personalContactInfoObj = null;
		JSONObject locationObj = null;

		/* Check which value was updated and update in Assembly accordingly */
		switch (attributeCode) {
			case "PRI_FIRSTNAME":
			personalInfoObj = new JSONObject();
			personalInfoObj.put("firstName", value);
			break;

			case "PRI_LASTNAME":
			personalInfoObj = new JSONObject();
			personalInfoObj.put("lastName", value);
			break;

			case "PRI_DOB":
			/* Format the date of birth */
			personalInfoObj = new JSONObject();
			DateTimeFormatter assemblyDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			LocalDate date = LocalDate.parse(value.toString(), formatter);
			String formattedDOBString = assemblyDateFormatter.format(date);
			System.out.println("another formatted dob ::" + formattedDOBString);
			personalInfoObj.put("dob", formattedDOBString.toString());
			break;

			case "PRI_EMAIL":
			personalContactInfoObj = new JSONObject();
			personalContactInfoObj.put("email", value);
			break;

			case "PRI_MOBILE":
			personalContactInfoObj = new JSONObject();
			personalContactInfoObj.put("mobile", value);
			break;


			case "PRI_ADDRESS_ADDRESS1":
			locationObj = new JSONObject();
			locationObj.put("addressLine1", value);
			break;

			case "PRI_ADDRESS_SUBURB":
			locationObj = new JSONObject();
			locationObj.put("city", value);
			break;

			case "PRI_ADDRESS_CITY":
			locationObj = new JSONObject();
			locationObj.put("city", value);
			break;

			case "PRI_ADDRESS_STATE":
			locationObj = new JSONObject();
			locationObj.put("state", value);
			break;
			case "PRI_ADDRESS_COUNTRY":
			locationObj = new JSONObject();
			locationObj.put("country", value);
			break;
			case "PRI_ADDRESS_POSTCODE":
			locationObj = new JSONObject();
			locationObj.put("postcode", value);
			break;
		}

		/* For Assembly Personal Information Update */
		if(personalInfoObj != null  && assemblyUserId != null) {
			userobj = new JSONObject();
			userobj.put("personalInfo", personalInfoObj);
			userobj.put("id", assemblyUserId);
		}

		if (personalContactInfoObj != null && assemblyUserId != null) {
			userobj = new JSONObject();
			userobj.put("contactInfo", personalContactInfoObj);
			userobj.put("id", assemblyUserId);
		}

		if (locationObj != null && assemblyUserId != null) {
			userobj = new JSONObject();
			userobj.put("location", locationObj);
			userobj.put("id", assemblyUserId);
		}

		/* Make the request to Assembly and update */
		if(userobj != null && assemblyUserId!= null) {
			try {
				responseString = PaymentEndpoint.updateAssemblyUser(assemblyUserId, JsonUtils.toJson(userobj), assemblyAuthToken);
				System.out.println("response string from payments user updation ::"+responseString);
			} catch (PaymentException e) {
				log.error("Exception occured user updation");
				e.printStackTrace();
			}
		}

		/* Return the response */
		return responseString;
	}

	/* Called when a particular attribute is updated for a company */
	@SuppressWarnings({ "unchecked" })
	public static String updateCompanyInfo(String userId, String companyId, String attributeCode, String value, String authToken) {
		System.out.println("attributeCode ::" + attributeCode + ", value ::" + value);

		/* Company Info Update Objects */
		String responseString = null;
		JSONObject companyObj = new JSONObject();
		companyObj.put("id", companyId);

		JSONObject userobj = null;
		JSONObject companyContactInfoObj = null;
		JSONObject locationObj = null;

		/* Check what attribute was updated and update accordingly */
		switch (attributeCode) {
			case "PRI_CPY_NAME":
			companyObj.put("name", value);
			companyObj.put("legalName", value);
			break;

			case "PRI_ABN":
			companyObj.put("taxNumber", value);
			break;

			case "PRI_ACN":
			companyObj.put("taxNumber", value);
			break;

			case "PRI_GST":
			companyObj.put("chargesTax", Boolean.valueOf(value));
			break;

			case "PRI_LANDLINE":
			companyContactInfoObj = new JSONObject();
			companyContactInfoObj.put("phone", value);
			break;

			case "PRI_ADDRESS_ADDRESS1":
			locationObj = new JSONObject();
			locationObj.put("addressLine1", value);
			break;

			case "PRI_ADDRESS_SUBURB":
			locationObj = new JSONObject();
			locationObj.put("city", value);
			break;

			case "PRI_ADDRESS_CITY":
			locationObj = new JSONObject();
			locationObj.put("city", value);
			break;

			case "PRI_ADDRESS_STATE":
			locationObj = new JSONObject();
			locationObj.put("state", value);
			break;
			case "PRI_ADDRESS_COUNTRY":
			locationObj = new JSONObject();
			locationObj.put("country", value);
			break;
			case "PRI_ADDRESS_POSTCODE":
			locationObj = new JSONObject();
			locationObj.put("postcode", value);
			break;
		}

		/* For Assembly User Company Information Update */
		if(companyContactInfoObj != null && companyId != null) {
			companyObj.put("contactInfo", companyContactInfoObj);

			if(userId != null) {
				userobj = new JSONObject();
				userobj.put("id", userId);
				companyObj.put("user", userobj);
			}

		}

		if (locationObj != null) {
			companyObj.put("location", locationObj);

			if(userId != null) {
				userobj = new JSONObject();
				userobj.put("id", userId);
				companyObj.put("user", userobj);
			}
		}

		/* Send updated details to Assembly */
		if(companyId != null && companyObj != null) {
			try {
				responseString = PaymentEndpoint.updateCompany(companyId, JsonUtils.toJson(companyObj), authToken);
			} catch (PaymentException e) {
				log.error("Exception occured company updation");
				e.printStackTrace();
			}
		}

		/* Return response */
		return responseString;
	}

	/* Creates a new company in Assembly */
	@SuppressWarnings("unchecked")
	public static String createCompany(BaseEntity companyBe, String assemblyUserId, String authtoken) {

		String createCompanyResponse = null;
		String companyCode = null;

		/* Create objects to store the data we'll send to Assembly */
		JSONObject companyObj = new JSONObject();
		JSONObject userObj = new JSONObject();
		JSONObject contactObj = new JSONObject();
		JSONObject locationObj = new JSONObject();

		/* Get the provided company information from the base entity */
		String companyName = companyBe.getValue("PRI_CPY_NAME", null);
		String taxNumber = companyBe.getValue("PRI_ABN", null);
		Boolean chargeTax = companyBe.getValue("PRI_GST", false);
		String companyPhoneNumber = companyBe.getValue("PRI_LANDLINE", null);
		String countryName = companyBe.getValue("PRI_ADDRESS_COUNTRY", null);
		

		/* Check if each field is provided and add to request object if so */
		if (companyName != null) {
			companyObj.put("name", companyName);
		}

		if (taxNumber != null) {
			companyObj.put("taxNumber", taxNumber);
		}

		if (chargeTax != null) {
			companyObj.put("chargesTax",chargeTax);
		}

		if (companyPhoneNumber != null) {
			contactObj.put("phone", companyPhoneNumber);
		}

		if (assemblyUserId != null) {
			userObj.put("id", assemblyUserId);
		}

		/* If a country name was provided use that, otherwise use Australia */
		if (countryName != null) {
			locationObj.put("country", countryName.toString());
		} else {
			locationObj.put("country", "AU");
		}

		/* Combine all of the objects into one */
		companyObj.put("contactInfo", contactObj);
		companyObj.put("user", userObj);
		companyObj.put("location", locationObj);

		log.info("Company object ::" + companyObj);

		/* Make the request to Assembly */
		if (companyObj != null && userObj != null) {
			System.out.println("company obj is not null, company object ::"+companyObj);
			try {
				createCompanyResponse = PaymentEndpoint.createCompany(JsonUtils.toJson(companyObj), authtoken);
				if(!createCompanyResponse.contains("error")) {
					JSONObject companyResponseObj = JsonUtils.fromJson(createCompanyResponse, JSONObject.class);

					if(companyResponseObj.get("id") != null) {
						companyCode = companyResponseObj.get("id").toString();
					}
				}
			} catch (PaymentException e) {
				companyCode = null;
			}
		}

		/* Return the ID of the created company */
		return companyCode;
	}

	/* Creates a new item in Assembly from the provided information */
	@SuppressWarnings("unchecked")
	public static String createPaymentItem(BaseEntity loadBe, BaseEntity offerBe, BaseEntity begBe, BaseEntity ownerBe, BaseEntity driverBe, String assemblyauthToken) {
		/* Get the base entity information */
		String itemId = null;

		/* Create objects to store the request */
		JSONObject itemObj = new JSONObject();
		JSONObject buyerObj = null;
		JSONObject sellerObj = null;

		itemObj.put("paymentType", DEFAULT_PAYMENT_TYPE);

		if (begBe != null) {
			/* Get the fees for this item */
			String feeId = getPaymentFeeId(offerBe, assemblyauthToken);
			System.out.println("fee Id ::" + feeId);

			/* Get the title, description and job ID for this item from the base entity group */
			String begTitle = loadBe.getValue("PRI_TITLE", null);
			// Hack to stop undefined name
			if (StringUtils.isBlank(begTitle)) {
				begTitle = loadBe.getValue("PRI_NAME", null);
				if (StringUtils.isBlank(begTitle)) {
					begTitle = loadBe.getName();
					if (StringUtils.isBlank(begTitle)) {
						begTitle = "Job #"+loadBe.getId();
						log.error("Job Name and Title are emoty , using job id");
					}
				}
			}
			String begDescription = loadBe.getValue("PRI_DESCRIPTION", null);
			String begJobId = begBe.getValue("PRI_JOB_ID", null);

			/* Check that values are provided and if they are include them in the request */
			if (begTitle != null) {
				if(begJobId != null) {
					itemObj.put("name", begTitle + ", Job #"+begJobId);
				} else {
					itemObj.put("name", begTitle);
				}
			}

			if (begDescription != null) {
				if(begJobId != null) {
					itemObj.put("description", begDescription + ", Job #" + begJobId);
				} else {
					itemObj.put("description", begDescription);
				}
			}

			if (feeId != null) {
				String[] feeArr = { feeId };
				itemObj.put("fees", feeArr);
			}

			/* Add the amount to the item */

			/*
			* driverPriceIncGST = ownerPriceIncGST.subtract(feePriceIncGST),
			* Creating Payments Fee with feePriceIncGST
			*/
			String offerOwnerPriceString = MergeUtil.getBaseEntityAttrValueAsString(offerBe, "PRI_OFFER_DRIVER_PRICE_INC_GST");

			System.out.println("begpriceString ::" + offerOwnerPriceString);

			String amount = null;
			String currency = null;
			if(offerOwnerPriceString != null) {
				System.out.println("begPriceString is not null");
				JSONObject moneyobj = JsonUtils.fromJson(offerOwnerPriceString, JSONObject.class);
				amount = moneyobj.get("amount").toString();
				currency = moneyobj.get("currency").toString();

				if(amount != null) {
					System.out.println("amount is not null");
					BigDecimal begPrice = new BigDecimal(amount);

					// 350 Dollars sent to Assembly as 3.50$, so multiplying with 100
					BigDecimal finalPrice = begPrice.multiply(new BigDecimal(100));
					itemObj.put("amount", finalPrice.toString());
				} else {
					log.error("AMOUNT IS NULL");
				}

				if(currency != null) {
					System.out.println("currency is not null");
					itemObj.put("currency", currency);
				} else {
					log.error("CURRENCY IS NULL");
				}

			} else {
				log.error("PRI_DRIVER_PRICE_INC_GST IS NULL");
			}

		} else {
			log.error("BEG BASEENTITY IS NULL");
			try {
				throw new PaymentException("Payment Item creation will not succeed since Beg baseentity is null");
			} catch (PaymentException e) {
			}
		}

		/* Set the buyer for the item */
		/* OWNER -> Buyer */
		if(ownerBe != null) {
			System.out.println("Context map contains OWNER");

			/* Check that an owner is actually set and if so use their Assembly user ID */
			if(ownerBe != null) {
				buyerObj = new JSONObject();
				buyerObj.put("id", ownerBe.getValue("PRI_ASSEMBLY_USER_ID", null));
			}
		} else {
			/* No owner was specified, throw an error */
			log.error("BEG CONTEXT MAP HAS NO OWNER LINK, SO BUYER OBJECT IS NULL");
			try {
				throw new PaymentException("Payment Item creation will not succeed since owner BE returned null");
			} catch (PaymentException e) {
			}
		}

		/* DRIVER -> Seller */
		if(driverBe != null ) {

			sellerObj = new JSONObject();
			sellerObj.put("id", driverBe.getValue("PRI_ASSEMBLY_USER_ID",null));
		} else {
			/* No driver was specified, throw an error */
			log.error("SELLER OBJECT IS NULL");
			try {
				throw new PaymentException("Payment Item creation will not succeed since driver BE returned null");
			} catch (PaymentException e) {
			}
		}

		/* If both buyer and seller is available for a particular BEG, Create Payment Item */
		if(itemObj != null && buyerObj != null && sellerObj != null) {
			/* Create the request object */
			itemObj.put("buyer", buyerObj);
			itemObj.put("seller", sellerObj);
			itemObj.put("id", UUID.randomUUID().toString());

			System.out.println("Item object ::"+itemObj);

			/* Make the request to Assembly to create the item */
			String itemCreationResponse;
			try {
				itemCreationResponse = PaymentEndpoint.createItem(JsonUtils.toJson(itemObj), assemblyauthToken);
				if(!itemCreationResponse.contains("error")) {

					log.info("Item object ::" + itemObj);
					log.info( itemObj.get("id") );
					itemId = itemObj.get("id").toString();
					log.info("Item ID found ::" + itemId);
					return itemId;
				}
			} catch (PaymentException e) {
				log.error("PAYMENT ITEM CREATION FAILED, ITEM/BUYER/SELLER OBJECT IS NULL, exception is handled");
				itemId = null;
			}
		}

		return itemId;
	}

	public static String getBegCode(String offerCode, String tokenString) {
		return MergeUtil.getAttrValue(offerCode, "PRI_BEG_CODE", tokenString);
	}

	/* Saves an answer */
	public static void saveAnswer(String qwandaServiceUrl, Answer answer, String token) {
		try {
			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/answers", JsonUtils.toJson(answer), token);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* Fetch the one time use Assembly card and bank tokens for a user */
	public static String fetchOneTimeAssemblyToken(String qwandaServiceUrl, String userId, String tokenString, String assemblyId, String assemblyAuthToken, String type) {
		String transactionToken = null;
		JSONParser parser = new JSONParser();
		JSONObject authenticationEntityObj = new JSONObject();

		/* Check that an Assembly ID was provided before we continue */
		if (assemblyId != null) {
			String tokenResponse = null;

			try {
				authenticationEntityObj.put("type", type);
				JSONObject userObj = new JSONObject();
				userObj.put("id", assemblyId);
				authenticationEntityObj.put("user", userObj);

				tokenResponse  = PaymentEndpoint.authenticatePaymentProvider(JsonUtils.toJson(authenticationEntityObj), assemblyAuthToken);

				if (!tokenResponse.contains("error")) {

					try {
						JSONObject tokenObj = (JSONObject) parser.parse(tokenResponse);
						System.out.println("token object ::" + tokenObj);

						String providerToken = tokenObj.get("token").toString();

						return providerToken;
					} catch (ParseException e) {
						e.printStackTrace();
					}
				}
			} catch (Exception e) {
				log.error("PaymentUtils Exception occured during Payment authentication Token provision");
			}
		} else {
			log.error("ASSEMBLY USER ID IS NULL");
		}

		return transactionToken;
	}

	@SuppressWarnings("unchecked")
	private static String authenticatePaymentProvider(String assemblyId, String assemblyAuthToken) throws PaymentException {
		JSONObject paymentProviderObj = new JSONObject();
		JSONObject userObj = new JSONObject();
		userObj.put("id", assemblyId);
		paymentProviderObj.put("type", PROVIDER_TYPE_BANK);
		paymentProviderObj.put("user", userObj);
		String tokenResponse = null;
		tokenResponse = PaymentEndpoint.authenticatePaymentProvider(JsonUtils.toJson(paymentProviderObj), assemblyAuthToken);
		return tokenResponse;
	}

	/* Creates a new fee in Assembly from a offer base entity */
	@SuppressWarnings("unchecked")
	public static String getPaymentFeeId(BaseEntity offerBe, String assemblyAuthToken) {
		/* Create a new JSON parser */
		JSONParser parser = new JSONParser();
		String feeId = null;

		/* Get the fee amount from the base entity */
		String begFeeString = MergeUtil.getBaseEntityAttrValueAsString(offerBe, "PRI_OFFER_FEE_INC_GST");

		/* If the fee amount is empty don't do anything */
		if (begFeeString != null) {
			System.out.println("begpriceString ::" + begFeeString);

			/* Convert the amount to a string and then to a BigDecimal */
			String amount = QwandaUtils.getAmountAsString(begFeeString);
			BigDecimal begPrice = new BigDecimal(amount);

			// 350 Dollars sent to Assembly as 3.50$, so multiplying with 100
			BigDecimal finalFee = begPrice.multiply(new BigDecimal(100));
			System.out.println("fees for feeId creation in Assembly::" + finalFee);

			/* Create the request object */
			JSONObject feeObj = new JSONObject();
			feeObj.put("name", "Channel40 fee");
			feeObj.put("type", 1);
			feeObj.put("amount", finalFee);
			feeObj.put("cap", null);
			feeObj.put("min", null);
			feeObj.put("max", null);
			feeObj.put("to", "buyer");

			/* Attempt to create the fee in Assembly */
			String feeResponse;
			try {
				feeResponse = PaymentEndpoint.createFees(JsonUtils.toJson(feeObj), assemblyAuthToken);
				if (feeResponse != null) {
					JSONObject feeResponseObj;

					feeResponseObj = (JSONObject) parser.parse(feeResponse);

					if (feeResponseObj.get("id") != null) {
						feeId = feeResponseObj.get("id").toString();
						return feeId;
					}
				}
			} catch (PaymentException e1) {
				log.error("Exception occured during Payment Fee creation");
				e1.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}

		/* Return the fee ID */
		return feeId;
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

	public static String processPaymentAnswers(String qwandaServiceUrl, QDataAnswerMessage m, String tokenString) {
		String begCode = null;

		try {
			System.out.println("----> Payments attributes Answers <------");

			String userCode = QwandaUtils.getUserCode(tokenString);

			Answer[] answers = m.getItems();
			for (Answer answer : answers) {

				String targetCode = answer.getTargetCode();
				String sourceCode = answer.getSourceCode();
				String attributeCode = answer.getAttributeCode();
				String value = answer.getValue();

				begCode = targetCode;

				log.debug("Payments value ::" + value + "attribute code ::" + attributeCode);
				System.out.println("Payments value ::" + value + "attribute code ::" + attributeCode);
				System.out.println("Beg code ::"+begCode);

				/* if this answer is actually an Payment_method, this rule will be triggered */
				if (attributeCode.contains("PRI_PAYMENT_METHOD")) {

					JsonObject paymentValues = new JsonObject(value);

					/*{ ipAddress, deviceID, accountID }*/
					String ipAddress = paymentValues.getString("ipAddress");
					String accountId = paymentValues.getString("accountID");
					String deviceId = paymentValues.getString("deviceID");

					if(ipAddress != null){
						Answer ipAnswer = new Answer(sourceCode, userCode, "PRI_IP_ADDRESS", ipAddress);
						saveAnswer(qwandaServiceUrl, ipAnswer, tokenString);
					}

					if(accountId != null) {
						Answer accountIdAnswer = new Answer(sourceCode, begCode, "PRI_ACCOUNT_ID", accountId);
						saveAnswer(qwandaServiceUrl, accountIdAnswer, tokenString);
					}

					if(deviceId != null) {
						Answer deviceIdAnswer = new Answer(sourceCode, userCode, "PRI_DEVICE_ID", deviceId);
						saveAnswer(qwandaServiceUrl, deviceIdAnswer, tokenString);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return begCode;
	}

	/* Returns the payment method for a user and account ID */
	private static String getPaymentMethodType(BaseEntity userBe, Object accountId) {

		System.out.println("in getPaymentMethodType method");

		/*Object paymentMethods = MergeUtil.getBaseEntityAttrObjectValue(userBe, "PRI_USER_PAYMENT_METHODS");*/
		Object paymentMethods = userBe.getValue("PRI_USER_PAYMENT_METHODS", null);
		JSONArray array = JsonUtils.fromJson(paymentMethods.toString(), JSONArray.class);
		String paymentType = null;

		if (paymentMethods != null) {
			for (int i = 0; i < array.size(); i++) {
				Map<String, String> methodObj = (Map<String, String>) array.get(i);
				if (accountId.equals(methodObj.get("id"))) {
					paymentType = methodObj.get("type");
				}

			}
		}
		System.out.println("payment method type is ::"+paymentType);
		return paymentType;

	}

	/* Releases a payment from escrow */
	public static Boolean releasePayment(String begCode, String authToken, String tokenString) {
		Boolean isReleasePaymentSuccess = false;
		System.out.println("BEG Code for release payment ::"+begCode);
		BaseEntity begBe = MergeUtil.getBaseEntityForAttr(begCode, tokenString);
		String paymentResponse = null;

		/* Get the Assembly item ID */
		Object itemId = MergeUtil.getBaseEntityAttrObjectValue(begBe, "PRI_ITEM_ID");

		JSONObject releasePaymentObj = new JSONObject();
		releasePaymentObj.put("singleItemDisbursement", true);

		if(itemId != null) {
			try {
				paymentResponse = PaymentEndpoint.releasePayment(itemId.toString(), JsonUtils.toJson(releasePaymentObj), authToken);
				if(!paymentResponse.contains("error")) {
					log.debug("release payment response ::"+paymentResponse);
					isReleasePaymentSuccess = true;
				}
			} catch (PaymentException e) {
				log.error("Exception occured during release payment");
				isReleasePaymentSuccess = false;
				e.printStackTrace();
			}

		} else {
			try {
				log.error("Exception occured during release payment");
				throw new PaymentException("Item ID is null or invalid, hence payment cannot be released");
			} catch (PaymentException e) {
				isReleasePaymentSuccess = false;
			}
		}

		return isReleasePaymentSuccess;
	}

	/* Sets the disbursement account for a user */
	@SuppressWarnings("unchecked")
	public static String disburseAccount(String assembyUserId, String paymentMethodString, String authToken) {

		String disburseAccountResponse = null;

		/* Check that both an Assembly user ID and payment method details are provided */
		if(assembyUserId != null && paymentMethodString != null) {

			System.out.println( "Payment account method string is not null");

			/* Convert the JSON string into a JSON Object */
			JSONObject paymentMethodObj = JsonUtils.fromJson(paymentMethodString, JSONObject.class);

			/* Get the ID of the payment method (that's all we'll need) */
			String paymentAccountId = paymentMethodObj.get("id").toString();

			/* Create the request body */
			JSONObject disburseAccObj = new JSONObject();
			JSONObject accObj = new JSONObject();
			accObj.put("id", paymentAccountId);
			disburseAccObj.put("account", accObj);

			/* Attempt to set the disbursement account for this user */
			try {
				disburseAccountResponse = PaymentEndpoint.disburseAccount(assembyUserId, JsonUtils.toJson(disburseAccObj), authToken);
				System.out.println("disburse payment response ::"+disburseAccountResponse);

			} catch (PaymentException e) {
				log.error("disburse payment response ::"+disburseAccountResponse);
				e.printStackTrace();
			}
		} else {
			/* No Assembly user ID was provided, throw an error */
			try {
				throw new PaymentException("Payment Disimbursement failed because of null values, assemblyUserId ::"+assembyUserId+", payment method string ::"+paymentMethodString);
			} catch (PaymentException e) {
				log.error("Payment exception caught during payment disimbursement");
				e.printStackTrace();
			}
		}

		return disburseAccountResponse;
	}

	public static String findExistingAssemblyUserAndSetAttribute(String userId, String tokenString, String authToken) {

		BaseEntity userBe = MergeUtil.getBaseEntityForAttr(userId, tokenString);
		Object email = MergeUtil.getBaseEntityAttrObjectValue(userBe, "PRI_EMAIL");
		String assemblyUserId = null;

		if (email != null) {

			String paymentUsersResponse;
			try {
				paymentUsersResponse = PaymentEndpoint.searchUser(email.toString(), authToken);

				if (!paymentUsersResponse.contains("error")) {

					System.out.println("payment user search response ::" + paymentUsersResponse);
					JSONObject userObj = JsonUtils.fromJson(paymentUsersResponse, JSONObject.class);

					Map<String, Object> metaInfo = (Map<String, Object>) userObj.get("meta");
					Double total = (Double) metaInfo.get("total");

					if(total > 0) {
						ArrayList<Map> userList = (ArrayList<Map>) userObj.get("users");

						if (userList.size() > 0) {
							for (Map userDetails : userList) {

								Map<String, Object> contactInfoMap = (Map<String, Object>) userDetails.get("contactInfo");
								Object contactEmail = contactInfoMap.get("email");

								if (contactEmail != null && contactEmail.equals(email)) {

									assemblyUserId = userDetails.get("id").toString();
									return assemblyUserId;

								} else {
									log.error("USER HAS NOT SET ASSEMBLY EMAIL ID");
									assemblyUserId = null;
								}
							}
						} else {
							log.error("No user found in assembly user");
							assemblyUserId = null;
						}
					} else {
						assemblyUserId = null;
					}

				}

			} catch (PaymentException e) {
				log.error("Payment user search has returned a null response and handling by returning null");
				assemblyUserId = null;
				e.printStackTrace();
			}


		} else {
			log.error("BASEENTITY HAS NULL EMAIL ATTRIBUTE");
			assemblyUserId = null;
		}

		return assemblyUserId;
	}

	/* Fully updates a user in Assembly */
	public static String updateCompleteUserProfile(BaseEntity userBe, String assemblyUserId, String assemblyAuthKey) {
		String responseString = null;

		/* Get all of the users information */
		String firstName = userBe.getValue("PRI_FIRSTNAME", null);
		String lastName = userBe.getValue("PRI_LASTNAME", null);
		Object dob = userBe.getValue("PRI_DOB", null);
		String addressLine1 = userBe.getValue("PRI_ADDRESS_ADDRESS1", null);
		String city = userBe.getValue("PRI_ADDRESS_SUBURB", null);
		String state = userBe.getValue("PRI_ADDRESS_STATE", null);
		String country = userBe.getValue("PRI_ADDRESS_COUNTRY", null);
		String postCode = userBe.getValue("PRI_ADDRESS_POSTCODE", null);

		/* Create objects to store the request */
		JSONObject userObj = new JSONObject();
		JSONObject personalInfoObj = new JSONObject();
		JSONObject contactInfoObj = null;
		JSONObject locationObj = null;

		/* Set the ID and other fields if they are provided and not null */
		userObj.put("id", assemblyUserId);

		if(firstName != null) {
			personalInfoObj.put("firstName", firstName);
		}

		if(lastName != null) {
			personalInfoObj.put("lastName", lastName);
		}

		/* If the date of birth is provided format it correctly */
		if(dob != null) {
			DateTimeFormatter assemblyDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			LocalDate date = LocalDate.parse(dob.toString(), formatter);
			String formattedDOBString = assemblyDateFormatter.format(date);
			System.out.println("formatted dob for updation ::" + formattedDOBString);
			personalInfoObj.put("dob", formattedDOBString);
		}

		/* If the address is provided set it */
		if(addressLine1 != null) {
			locationObj = new JSONObject();
			locationObj.put("addressLine1", addressLine1);

			if(city != null) {
				locationObj.put("city", city);
			}

			if(state != null) {
				locationObj.put("state", state);
			}

			if(country != null) {
				locationObj.put("country", country);
			}

			if(postCode != null) {
				locationObj.put("postcode", postCode);
			}

			userObj.put("location", locationObj);
		}

		userObj.put("personalInfo", personalInfoObj);

		/* Attempt to update the user in Assembly */
		if(userObj != null && assemblyUserId != null) {
			try {
				responseString = PaymentEndpoint.updateAssemblyUser(assemblyUserId, JsonUtils.toJson(userObj), assemblyAuthKey);
				System.out.println("response string from payments user updation ::"+responseString);
			} catch (PaymentException e) {
				log.error("Exception occured user updation");
				e.printStackTrace();
			}
		}

		return responseString;
	}

	/* Update a complete company profile */
	public static String updateCompleteCompanyProfile(BaseEntity userBe, BaseEntity companyBe, String assemblyUserId, String assemblyAuthKey) {
		/* Create objects to store the request */
		String responseString = null;
		JSONObject companyObj = new JSONObject();
		JSONObject userObj = new JSONObject();
		JSONObject contactInfoObj = null;
		JSONObject locationObj = null;

		/* Get the companies assembly ID */
		String companyId = userBe.getValue("PRI_ASSEMBLY_COMPANY_ID", null);

		/* Get the companies information */
		String companyName = companyBe.getValue("PRI_CPY_NAME", null);
		String abn = companyBe.getValue("PRI_ABN", null);
		Object acn = companyBe.getValue("PRI_ACN", null);
		Object gst = companyBe.getValue("PRI_GST", null);
		String companyPhone = companyBe.getValue("PRI_LANDLINE", null);
		String addressLine1 = companyBe.getValue("PRI_ADDRESS_ADDRESS1", null);
		String city = companyBe.getValue("PRI_ADDRESS_SUBURB", null);
		String state = companyBe.getValue("PRI_ADDRESS_STATE", null);
		String country = companyBe.getValue("PRI_ADDRESS_COUNTRY", null);
		String postCode = companyBe.getValue("PRI_ADDRESS_POSTCODE", null);

		/* Check that the Assembly ID was provided */
		if(assemblyUserId != null) {
			System.out.println("assembly user id is not null, user ::"+companyObj);
			userObj.put("id", assemblyUserId);
			companyObj.put("user", userObj);
		}

		/* Updated all the other provided fields */
		if(companyName != null) {
			companyObj.put("name", companyName);
			companyObj.put("legalName", companyName);
		}

		if(abn != null) {
			companyObj.put("taxNumber", abn);
		} else if(acn != null){
			companyObj.put("taxNumber", acn);
		}

		if(gst != null) {
			companyObj.put("chargesTax", gst);
		}


		if(companyPhone != null) {
			contactInfoObj = new JSONObject();
			contactInfoObj.put("phone", companyPhone);
			companyObj.put("contactInfo", contactInfoObj);
		}

		/* If an address was provided set all the address fields */
		if(addressLine1 != null) {
			locationObj = new JSONObject();
			locationObj.put("addressLine1", addressLine1);

			if(city != null) {
				locationObj.put("city", city);
			}

			if(state != null) {
				locationObj.put("state", state);
			}

			if(country != null) {
				locationObj.put("country", country);
			}

			if(postCode != null) {
				locationObj.put("postcode", postCode);
			}

			companyObj.put("location", locationObj);
		}

		/* Attempt to update the company in Assembly */
		if(companyId != null && companyObj != null) {
			System.out.println("updating company object in assembly ::"+companyObj);
			try {
				responseString = PaymentEndpoint.updateCompany(companyId, JsonUtils.toJson(companyObj), assemblyAuthKey);
			} catch (PaymentException e) {
				log.error("Exception occured company updation");
				e.printStackTrace();
			}
		}

		return responseString;
	}

	/* Returns true of false depending on whether the payment method provided is a bank account */
	public static Boolean isBankAccount(String bankPaymentString) {
		Boolean isBankAccount = false;
		JSONObject paymentMethodObj = JsonUtils.fromJson(bankPaymentString, JSONObject.class);

		String paymentType = paymentMethodObj.get("type").toString();
		if(paymentType.equals("BANK_ACCOUNT")) {
			isBankAccount = true;
		}

		return isBankAccount;
	}

	/* Deletes a bank account */
	public static void deleteBankAccount(String bankAccountId, String authKey) {
		try {
			PaymentEndpoint.deleteBankAccount(bankAccountId, authKey);
		} catch (PaymentException e) {
			e.printStackTrace();
		}
	}

	/* Deletes a credit card */
	public static void deleteCard(String cardAccountId, String authKey) {
		try {
			PaymentEndpoint.deleteCardAccount(cardAccountId, authKey);
		} catch (PaymentException e) {
			e.printStackTrace();
		}
	}

	/* Makes a payment */
	@SuppressWarnings("unchecked")
	public static PaymentsResponse makePaymentWithResponse(BaseEntity userBe, BaseEntity offerBe, BaseEntity begBe, String authToken) {
		System.out.println("inside make payment");

		/* Get the required fields */
		String ipAddress = userBe.getValue("PRI_IP_ADDRESS", null);
		String deviceId = userBe.getValue("PRI_DEVICE_ID", null);
		String itemId = begBe.getValue("PRI_ITEM_ID", null);
		String accountId = begBe.getValue("PRI_ACCOUNT_ID", null);

		PaymentsResponse makepaymentResponse = new PaymentsResponse();
		Map<String, String> responseMap = new HashMap<>();

		JSONObject paymentObj = new JSONObject();
		JSONObject accountObj = null;
		String paymentType = null;

		/* Check that an item ID was provided */
		if (itemId != null) {
			/* Set the fields in the request if they were provided */
			paymentObj.put("id", itemId);

			if(deviceId != null) {
				paymentObj.put("deviceID", deviceId);
			} else {
				System.out.println("device ID is null");
			}

			if(ipAddress != null) {
				paymentObj.put("ipAddress", ipAddress);
			} else {
				System.out.println("IP address is null");
			}

			if(accountId != null) {
				accountObj = new JSONObject();
				accountObj.put("id", accountId);
				paymentObj.put("account", accountObj);

				/* To get the type of payment (Bank account / card) */
				paymentType = getPaymentMethodType(userBe, accountId);
				System.out.println("payment type ::" +paymentType);

			} else {
				System.out.println("account Id is null");
			}

			/* Check the type of the account that has been selected and pay with that */
			if (paymentType != null && paymentType.equals("BANK_ACCOUNT")) {
				makepaymentResponse = makePaymentWithBankAccount(paymentType, userBe, offerBe, begBe, paymentObj, authToken);
			} else if (paymentType != null && paymentType.equals("CARD")) {
				makepaymentResponse = makePaymentWithCardResponse(paymentType, userBe, offerBe, begBe, paymentObj, authToken);
			} else {
				makepaymentResponse.setIsSuccess(false);
				makepaymentResponse.setMessage("Unknown payment method type");
				responseMap.put("depositReferenceId", null);
				makepaymentResponse.setResponseMap(responseMap);
			}

			return makepaymentResponse;
		} else {
			/* An item ID wasn't provided throw an error */
			makepaymentResponse.setIsSuccess(false);
			makepaymentResponse.setMessage("Item creation for transaction has failed. "+CONTACT_ADMIN_TEXT);
			responseMap.put("depositReferenceId", null);
			makepaymentResponse.setResponseMap(responseMap);
		}

		return makepaymentResponse;
	}

	/* Attempts to make a payment with a card */
	private static PaymentsResponse makePaymentWithCardResponse(String paymentType, BaseEntity userBe,
	BaseEntity offerBe, BaseEntity begBe, JSONObject paymentObj, String authToken) {

		System.out.println("Credit card payment");
		PaymentsResponse makepaymentResponse = new PaymentsResponse();
		Map<String, String> responseMap = new HashMap<>();
		String paymentResponse = null;
		Boolean isMakePaymentSuccess = false;
		String itemId = begBe.getValue("PRI_ITEM_ID", null);

		try {
			paymentResponse = PaymentEndpoint.makePayment(itemId, JsonUtils.toJson(paymentObj),
			authToken);
			log.debug("Make payment response ::" + paymentResponse);
			if (!paymentResponse.contains("error")) {
				isMakePaymentSuccess = true;

				/* Save deposit reference as an answer to beg */
				String referenceId = getDepositReference(paymentResponse, userBe.getCode(), begBe.getCode());

				makepaymentResponse.setIsSuccess(isMakePaymentSuccess);
				makepaymentResponse.setMessage("Making payment has succeeded");
				responseMap.put("depositReferenceId", referenceId);
				responseMap.put("makePaymentResponse", paymentResponse);
				makepaymentResponse.setResponseMap(responseMap);

			}

		} catch (PaymentException e) {

			log.error("Exception occured during making payment with " + paymentType);
			String errorMessage = e.getMessage();
			log.error("error message ::"+errorMessage);

			/* When make payment API is getting accessed more than once for an item */
			if(errorMessage.contains("payment is already made")) {
				isMakePaymentSuccess = true;

				makepaymentResponse.setIsSuccess(isMakePaymentSuccess);
				makepaymentResponse.setMessage(e.getMessage());
				responseMap.put("depositReferenceId", begBe.getValue("PRI_DEPOSIT_REFERENCE_ID", null));
				responseMap.put("makePaymentResponse", paymentResponse);
				makepaymentResponse.setResponseMap(responseMap);

			} else {
				isMakePaymentSuccess = false;


				makepaymentResponse.setIsSuccess(isMakePaymentSuccess);
				makepaymentResponse.setMessage(e.getMessage());
				responseMap.put("depositReferenceId", null);
				makepaymentResponse.setResponseMap(responseMap);
			}

		}
		return makepaymentResponse;
	}

	/* Attempts to make a payment with a bank account */
	private static PaymentsResponse makePaymentWithBankAccount(String paymentType, BaseEntity userBe,
	BaseEntity offerBe, BaseEntity begBe, JSONObject paymentObj, String authToken) {

		System.out.println("Bank account..Need to be authorized to make payment");

		String itemId = begBe.getValue("PRI_ITEM_ID", null);
		String accountId = begBe.getValue("PRI_ACCOUNT_ID", null);
		PaymentsResponse makepaymentResponse = new PaymentsResponse();
		String paymentResponse = null;
		Boolean isMakePaymentSuccess = false;

		/* Payment with bank account will proceed only if debit authority succeeds */
		PaymentsResponse debitAuthorityResponse = getDebitAuthorityWithResponse(offerBe, begBe, accountId, authToken);

		if (debitAuthorityResponse.getIsSuccess()) {
			log.debug("Make payment object ::" + paymentObj.toJSONString());
			try {
				paymentResponse = PaymentEndpoint.makePayment(itemId, JsonUtils.toJson(paymentObj),
				authToken);
				log.debug("Make payment response ::" + paymentResponse);
				if (!paymentResponse.contains("error")) {
					isMakePaymentSuccess = true;

					/* getting deposit reference to add as an attribute of beg */
					String referenceId = getDepositReference(paymentResponse, userBe.getCode(), begBe.getCode());

					makepaymentResponse.setIsSuccess(isMakePaymentSuccess);
					makepaymentResponse.setMessage("Making payment has succeeded");

					Map<String, String> responseMap = debitAuthorityResponse.getResponseMap();
					responseMap.put("depositReferenceId", referenceId);

					makepaymentResponse.setResponseMap(responseMap);

				}

			} catch (PaymentException e) {

				log.error("Exception occured during making payment with " + paymentType);
				String errorMessage = e.getMessage();
				log.error("error message ::" + errorMessage);

				/* When make payment API is getting accessed more than once for an item */
				if (errorMessage.contains("payment is already made")) {

					isMakePaymentSuccess = true;

					makepaymentResponse.setIsSuccess(isMakePaymentSuccess);
					makepaymentResponse.setMessage(e.getMessage());

					Map<String, String> responseMap = debitAuthorityResponse.getResponseMap();
					responseMap.put("depositReferenceId", begBe.getValue("PRI_DEPOSIT_REFERENCE_ID", null));

					makepaymentResponse.setResponseMap(responseMap);

				} else {
					isMakePaymentSuccess = false;

					makepaymentResponse.setIsSuccess(isMakePaymentSuccess);
					makepaymentResponse.setMessage(e.getMessage());

					Map<String, String> responseMap = debitAuthorityResponse.getResponseMap();
					responseMap.put("depositReferenceId", null);

					makepaymentResponse.setResponseMap(responseMap);

				}

			}
		} else {
			isMakePaymentSuccess = false;

			makepaymentResponse.setIsSuccess(isMakePaymentSuccess);
			makepaymentResponse.setMessage(debitAuthorityResponse.getMessage());

			Map<String, String> responseMap = debitAuthorityResponse.getResponseMap();
			responseMap.put("depositReferenceId", null);

			makepaymentResponse.setResponseMap(responseMap);

		}

		return makepaymentResponse;
	}

	/* Returns the despoit reference from a payment response */
	private static String getDepositReference(String paymentResponse, String userCode, String begCode) {
		JSONObject depositReference = JsonUtils.fromJson(paymentResponse, JSONObject.class);
		return depositReference.get("depositReference").toString();
	}

	/* Creates a direct debit authority */
	private static PaymentsResponse getDebitAuthorityWithResponse(BaseEntity offerBe, BaseEntity begBe, Object accountId, String authToken) {
		Boolean isDebitAuthority = false;
		String getDebitAuthorityResponse = null;
		String offerOwnerPriceString = MergeUtil.getBaseEntityAttrValueAsString(offerBe, "PRI_OFFER_DRIVER_PRICE_INC_GST");

		PaymentsResponse debitAuthorityResponse = new PaymentsResponse();
		Map<String, String> responseMap = new HashMap<>();

		System.out.println("begpriceString ::" + offerOwnerPriceString);

		String amount = null;
		if(offerOwnerPriceString != null) {
			JSONObject moneyobj = JsonUtils.fromJson(offerOwnerPriceString, JSONObject.class);
			amount = moneyobj.get("amount").toString();

			if(amount != null) {
				BigDecimal begPrice = new BigDecimal(amount);

				// 350 Dollars sent to Assembly as 3.50$, so multiplying with 100
				BigDecimal finalPrice = begPrice.multiply(new BigDecimal(100));

				JSONObject debitAuthorityObj = new JSONObject();
				JSONObject accountObj = new JSONObject();

				accountObj.put("id", accountId);
				debitAuthorityObj.put("amount", finalPrice);
				debitAuthorityObj.put("account", accountObj);

				try {
					getDebitAuthorityResponse = PaymentEndpoint.getdebitAuthorization(JsonUtils.toJson(debitAuthorityObj), authToken);
					if(!getDebitAuthorityResponse.contains("error")) {
						isDebitAuthority = true;

						debitAuthorityResponse.setIsSuccess(isDebitAuthority);
						debitAuthorityResponse.setMessage("debit authority for bank account has succeeded");
						responseMap.put("debitAuthorizationResponse", getDebitAuthorityResponse);
						debitAuthorityResponse.setResponseMap(responseMap);

					}
				} catch (PaymentException e) {
					isDebitAuthority = false;
					log.error("Exception occured during debit authorization, Make Payment will not succeed");

					debitAuthorityResponse.setIsSuccess(isDebitAuthority);
					debitAuthorityResponse.setMessage(e.getMessage());
					responseMap.put("debitAuthorizationResponse", e.getMessage());
					debitAuthorityResponse.setResponseMap(responseMap);

				}
			}
		} else {
			isDebitAuthority = false;
			log.error("PRI_DRIVER_PRICE_INC_GST IS NULL");

			debitAuthorityResponse.setIsSuccess(isDebitAuthority);
			debitAuthorityResponse.setMessage("Amount for transaction not specified");
			responseMap.put("debitAuthorizationResponse", "Amount for transaction not specified");
			debitAuthorityResponse.setResponseMap(responseMap);

		}

		return debitAuthorityResponse;
	}

	/* Releases a payment */
	public static PaymentsResponse releasePaymentWithResponse(BaseEntity begBe, String authToken) {

		Map<String, String> releasePaymentResponseMap = null;

		System.out.println("BEG Code for release payment ::"+begBe.getCode());
		PaymentsResponse releasePaymentResponse = new PaymentsResponse();

		String paymentResponse = null;
		String itemId = begBe.getValue("PRI_ITEM_ID", null);

		JSONObject releasePaymentObj = new JSONObject();
		releasePaymentObj.put("singleItemDisbursement", true);

		if(itemId != null) {
			try {
				paymentResponse = PaymentEndpoint.releasePayment(itemId, JsonUtils.toJson(releasePaymentObj), authToken);
				if(!paymentResponse.contains("error")) {
					log.debug("release payment response ::"+paymentResponse);

					JSONObject releasePaymentResponseObj = JsonUtils.fromJson(paymentResponse, JSONObject.class);
					Map<String, Object> disbursementMap = (Map<String, Object>) releasePaymentResponseObj.get("disbursement");
					String disbursementId = (String) disbursementMap.get("id");
					System.out.println("disbursement id ::"+disbursementId);

					releasePaymentResponseMap = new HashMap<String, String>();
					releasePaymentResponseMap.put("disbursementId", disbursementId);

					releasePaymentResponse.setIsSuccess(true);
					releasePaymentResponse.setMessage("Release payment has succeeded");
					releasePaymentResponse.setResponseMap(releasePaymentResponseMap);
				}
			} catch (PaymentException e) {
				log.error("Exception occured during release payment");

				releasePaymentResponse.setIsSuccess(false);
				releasePaymentResponse.setMessage(e.getMessage());
				releasePaymentResponse.setResponseMap(releasePaymentResponseMap);
			}

		} else {
			releasePaymentResponse.setIsSuccess(false);
			releasePaymentResponse.setMessage("Item creation has failed, hence payment cannot be released. "+CONTACT_ADMIN_TEXT);
		}

		return releasePaymentResponse;
	}

	public static String updateUserPhoneNumber(BaseEntity userBe, String assemblyUserId, String assemblyAuthKey) {

		String responseString = null;

		String phoneNumber = userBe.getValue("PRI_MOBILE", null);

		JSONObject userObj = new JSONObject();
		JSONObject contactInfoObj = null;

		userObj.put("id", assemblyUserId);

		if(phoneNumber != null) {
			contactInfoObj = new JSONObject();
				;
			userObj.put("contactInfo", contactInfoObj);
		}

		if(userObj != null && assemblyUserId != null) {
			try {
				responseString = PaymentEndpoint.updateAssemblyUser(assemblyUserId, JsonUtils.toJson(userObj), assemblyAuthKey);
				System.out.println("response string from payments user mobile-number updation ::"+responseString);
			} catch (PaymentException e) {
				log.error("Exception occured during phone updation");
			}
		}

		return responseString;

	}

	//offerBe, begBe, ownerBe, driverBe, assemblyAuthKey
	public static Boolean checkForAssemblyItemValidity(String itemId, BaseEntity offerBe, BaseEntity ownerBe, BaseEntity driverBe, String assemblyAuthKey) {
		Boolean isAssemblyItemValid = false;
		String itemResponse = null;

		try {
			itemResponse = PaymentEndpoint.getAssemblyPaymentItem(itemId, assemblyAuthKey);

			JSONObject itemResponseObj = JsonUtils.fromJson(itemResponse, JSONObject.class);

			//Get all values for "items" key
			Map<String, Object> itemDescObj = (Map<String, Object>) itemResponseObj.get("items");

			Double itemPrice = (Double) itemDescObj.get("amount");
			System.out.println("item price ::"+itemPrice);

			String ownerEmail = ownerBe.getValue("PRI_EMAIL", null);
			String driverEmail = driverBe.getValue("PRI_EMAIL", null);

			/* Since itemprice = PRI_OFFER_DRIVER_PRICE_EXC_GST + PRI_OFFER_FEE_EXC_GST */
			Money driverPriceIncGST = offerBe.getValue("PRI_OFFER_DRIVER_PRICE_INC_GST", null);
			Double calculatedItemPriceInCents = driverPriceIncGST.getNumber().doubleValue() * 100;

			String str = String.format("%.2f",calculatedItemPriceInCents);
			calculatedItemPriceInCents = Double.parseDouble(str);

			/* convert into cents */
			System.out.println("calculated item price in cents ::"+calculatedItemPriceInCents);

			Map<String, Object> buyerOwnerInfo = (Map<String, Object>) itemDescObj.get("buyer");
			Map<String, Object> ownerContactInfo = (Map<String, Object>) buyerOwnerInfo.get("contactInfo");

			Map<String, Object> sellerDriverInfo = (Map<String, Object>) itemDescObj.get("seller");
			Map<String, Object> driverContactInfo = (Map<String, Object>) sellerDriverInfo.get("contactInfo");

			Boolean isOwnerEmail = ownerContactInfo.get("email").equals(ownerEmail);
			System.out.println("Is email attribute for owner equal ?"+isOwnerEmail);

			Boolean isDriverEmail = driverContactInfo.get("email").equals(driverEmail);
			System.out.println("Is email attribute for driver equal ?"+isDriverEmail);

			Boolean isPriceEqual = (Double.compare(calculatedItemPriceInCents, itemPrice) == 0);
			System.out.println("Is price attribute for item equal ?"+isPriceEqual);

			if(ownerContactInfo.get("email").equals(ownerEmail) && driverContactInfo.get("email").equals(driverEmail) && Double.compare(calculatedItemPriceInCents, itemPrice) == 0) {

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

}
