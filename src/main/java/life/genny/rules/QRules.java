package life.genny.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.Logger;
import org.drools.core.spi.KnowledgeHelper;
import org.javamoney.moneta.Money;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;
import com.hazelcast.util.collection.ArrayUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.channel.Producer;
import life.genny.qwanda.Answer;
import life.genny.qwanda.Ask;
import life.genny.qwanda.GPS;
import life.genny.qwanda.Layout;
import life.genny.qwanda.Link;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.attribute.AttributeBoolean;
import life.genny.qwanda.attribute.AttributeInteger;
import life.genny.qwanda.attribute.AttributeMoney;
import life.genny.qwanda.attribute.AttributeText;
import life.genny.qwanda.attribute.EntityAttribute;
import life.genny.qwanda.datatype.DataType;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.entity.EntityEntity;
import life.genny.qwanda.entity.SearchEntity;
import life.genny.qwanda.exception.BadDataException;
import life.genny.qwanda.exception.PaymentException;
import life.genny.qwanda.message.QBaseMSGAttachment;
import life.genny.qwanda.message.QBaseMSGAttachment.AttachmentType;
import life.genny.qwanda.message.QBulkMessage;
import life.genny.qwanda.message.QCmdFormMessage;
import life.genny.qwanda.message.QCmdGeofenceMessage;
import life.genny.qwanda.message.QCmdLayoutMessage;
import life.genny.qwanda.message.QCmdMessage;
import life.genny.qwanda.message.QCmdReloadMessage;
import life.genny.qwanda.message.QCmdViewMessage;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataAskMessage;
import life.genny.qwanda.message.QDataAttributeMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwanda.message.QDataGPSMessage;
import life.genny.qwanda.message.QDataMessage;
import life.genny.qwanda.message.QDataQSTMessage;
import life.genny.qwanda.message.QDataSubLayoutMessage;
import life.genny.qwanda.message.QDataToastMessage;
import life.genny.qwanda.message.QEventAttributeValueChangeMessage;
import life.genny.qwanda.message.QEventBtnClickMessage;
import life.genny.qwanda.message.QEventLinkChangeMessage;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwanda.message.QMSGMessage;
import life.genny.qwanda.message.QMessage;
import life.genny.qwanda.payments.QMakePayment;
import life.genny.qwanda.payments.QPaymentAuthorityForBankAccount;
import life.genny.qwanda.payments.QPaymentMethod;
import life.genny.qwanda.payments.QPaymentMethod.PaymentType;
import life.genny.qwanda.payments.QPaymentsAuthorizationToken;
import life.genny.qwanda.payments.QPaymentsAuthorizationToken.AuthorizationPaymentType;
import life.genny.qwanda.payments.QPaymentsCompany;
import life.genny.qwanda.payments.QPaymentsCompanyContactInfo;
import life.genny.qwanda.payments.QPaymentsDisbursement;
import life.genny.qwanda.payments.QPaymentsErrorResponse;
import life.genny.qwanda.payments.QPaymentsFee;
import life.genny.qwanda.payments.QPaymentsItem;
import life.genny.qwanda.payments.QPaymentsItem.PaymentTransactionType;
import life.genny.qwanda.payments.QPaymentsLocationInfo;
import life.genny.qwanda.payments.QPaymentsUser;
import life.genny.qwanda.payments.QPaymentsUserContactInfo;
import life.genny.qwanda.payments.QPaymentsUserInfo;
import life.genny.qwanda.payments.QReleasePayment;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyItemResponse;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyUserResponse;
import life.genny.qwanda.payments.assembly.QPaymentsAssemblyUserSearchResponse;
import life.genny.qwandautils.GPSUtils;
import life.genny.qwandautils.KeycloakUtils;
import life.genny.qwandautils.MessageUtils;
import life.genny.qwandautils.QwandaUtils;
import life.genny.qwandautils.SecurityUtils;
import life.genny.security.SecureResources;
import life.genny.utils.MoneyHelper;
import life.genny.utils.PaymentEndpoint;
import life.genny.utils.PaymentUtils;
import life.genny.utils.StringFormattingUtils;
import life.genny.utils.VertxUtils;
import life.genny.qwandautils.JsonUtils;

public class QRules {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	public static final String qwandaServiceUrl = System.getenv("REACT_APP_QWANDA_API_URL");
	public static final Boolean devMode = System.getenv("GENNY_DEV") == null ? false : true;

	final static String DEFAULT_STATE = "NEW";

	private String token;
	private EventBus eventBus;
	private Boolean started = false;
	private Map<String, Object> decodedTokenMap;
	private Map<String, Boolean> stateMap;

	private long ruleStartMs = 0;

	KnowledgeHelper drools;

	public void setDrools(KnowledgeHelper drools) {
		this.drools = drools;
	}

	public KnowledgeHelper getDrools() {
		return this.drools;
	}

	/* Utils */
	public BaseEntityUtils baseEntity;
	public LayoutUtils layoutUtils;
  public CacheUtils cacheUtils;
	public PaymentUtils paymentUtils;

	public QRules(final EventBus eventBus, final String token, final Map<String, Object> decodedTokenMap,
			String state) {
		super();
		this.eventBus = eventBus;
		this.token = token;
		this.decodedTokenMap = decodedTokenMap;
		this.stateMap = new HashMap<String, Boolean>();
		stateMap.put(DEFAULT_STATE, true);
		setStarted(false);

		this.initUtils();
	}

	public QRules(final EventBus eventBus, final String token, final Map<String, Object> decodedTokenMap) {
		this(eventBus, token, decodedTokenMap, DEFAULT_STATE);
	}

	public void initUtils() {

		try {

			/* initialising utils */
			this.baseEntity = new BaseEntityUtils(QRules.qwandaServiceUrl, this.token, decodedTokenMap, realm());
			this.layoutUtils = new LayoutUtils(QRules.qwandaServiceUrl, this.token, decodedTokenMap, realm());
			this.cacheUtils = new CacheUtils(QRules.qwandaServiceUrl, this.token, decodedTokenMap, realm());
			this.paymentUtils = new PaymentUtils(QRules.qwandaServiceUrl, this.token, decodedTokenMap, realm());
		}
		catch(Exception e) {

		}
	}

	/**
	 * @return the token
	 */
	public String getToken() {
		return token;
	}

	/**
	 * @param token
	 *            the token to set
	 */
	public void setToken(String token) {
		this.token = token;
	}

	/**
	 * @return the eventBus
	 */
	public EventBus getEventBus() {
		return eventBus;
	}

	/**
	 * @param eventBus
	 *            the eventBus to set
	 */
	public void setEventBus(EventBus eventBus) {
		this.eventBus = eventBus;
	}

	/**
	 * @return the decodedTokenMap
	 */
	public Map<String, Object> getDecodedTokenMap() {
		return decodedTokenMap;
	}

	/**
	 * @param decodedTokenMap
	 *            the decodedTokenMap to set
	 */
	public void setDecodedTokenMap(Map<String, Object> decodedTokenMap) {
		this.decodedTokenMap = decodedTokenMap;
	}

	/**
	 * @return current realm
	 */
	public String realm() {

		String str = getAsString("realm");
		// if(str == null) {
		// str = "genny";
		// }

		return str.toLowerCase();
	}

	/**
	 * @return the realm
	 */
	public boolean isRealm(final String realm) {
		return this.realm().equalsIgnoreCase(realm);
	}

	/**
	 * @return the state
	 */
	public boolean isState(final String key) {
		if (stateMap.containsKey(key)) {
			return stateMap.get(key);
		} else {
			return false;
		}
	}

	/**
	 * @param state
	 *            the state to set
	 */
	public void setState(String key) {
		stateMap.put(key.toUpperCase(), true);
		// println("STATE " + key + " SET", RulesUtils.ANSI_RED);
		update();
	}

	/**
	 * @param state
	 *            the state to set
	 */
	/* added by anish */
	public void setState(Boolean key) {
		stateMap.put(key.toString().toUpperCase(), true);
		// println("STATE " + key + " SET", RulesUtils.ANSI_RED);
		update();
	}

	/**
	 * @param state
	 *            the state to clear
	 */
	public void clearState(String key) {
		stateMap.remove(key);
		println("STATE " + key + " CLEARED", RulesUtils.ANSI_PURPLE);
		update();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "QRules [decodedTokenMap=" + decodedTokenMap + ", state=" + stateMap + "]";
	}

	public Object get(final String key) {
		return decodedTokenMap.get(key);
	}

	public Boolean is(final String key) {
		return decodedTokenMap.containsKey(key);
	}

	@SuppressWarnings("unchecked")
	public List<Object> getAsList(final String key) {
		return (List<Object>) get(key);
	}

	@SuppressWarnings("unchecked")
	public Object[] getAsArray(final String key) {
		return (Object[]) get(key);
	}

	public String getAsString(final String key) {
		return (String) get(key);
	}

	public BaseEntity getAsBaseEntity(final String key) {
		return (BaseEntity) get(key);
	}

	public List<BaseEntity> getAsBaseEntitys(final String key) {
		return (List<BaseEntity>) get(key);
	}

	public Attribute getAsAttribute(final String key) {
		return (Attribute) get(key);
	}

	public Integer getAsInteger(final String key) {
		return (Integer) get(key);
	}

	public Double getAsDouble(final String key) {
		return (Double) get(key);
	}

	public Boolean getAsBoolean(final String key) {
		return (Boolean) get(key);
	}

	public Boolean isTrue(final String key) {
		return getAsBoolean(key);
	}

	public Boolean isFalse(final String key) {
		return !getAsBoolean(key);
	}

	public Boolean isNull(final String key) {
		if (is(key)) {
			if (get(key) == null) {
				return true;
			} else {
				return false;
			}
		} else {
			return true;
		}
	}

	/* TODO: to remove */
	public void setKey(final String key, Object value) {
		VertxUtils.putObject(this.realm(), "", key, value);
	}

	public Object getKey(final String key) {
		Object value = VertxUtils.getObject(this.realm(), "", key, Object.class);
		return value;
	}

	public void set(final String key, Object value) {
		decodedTokenMap.put(key, value);
	}

	public String encodeToBase64(String str) {
		return Base64.getEncoder().encodeToString(str.getBytes());
	}

	public String decodeBase64(byte[] base64str) {
		return new String(Base64.getDecoder().decode(base64str));
	}

	public String urlEncode(String str) {
		try {
			return URLEncoder.encode(str, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	public BaseEntity getProject() {

		BaseEntity be = null;
		String projectCode = "PRJ_" + getAsString("realm").toUpperCase();
		be = this.baseEntity.getBaseEntityByCode(projectCode);

		if (isNull("PROJECT") && be != null) {
			set("PROJECT", be);
		}

		return be;
	}

	public BaseEntity getUser() {

		BaseEntity be = null;
		String username = (String) getDecodedTokenMap().get("preferred_username");
		String code = "PER_" + QwandaUtils.getNormalisedUsername(username).toUpperCase();
		try {
			be = this.baseEntity.getBaseEntityByCode(code);
		} catch (Exception e) {

		}

		return be;
	}

	/* TODO: to remove */
	public Boolean isUserRole(BaseEntity user, String role) {

		Boolean isRole = false;

		Object uglyRole = user.getValue(role, null);
		if (uglyRole instanceof Boolean) {
			isRole = user.is(role);
		} else {
			String uglyRoleString = (String) uglyRole;
			isRole = "TRUE".equalsIgnoreCase(uglyRoleString);
		}

		return isRole;
	}

	public Boolean isUserRole(String role) {
		return this.isUserRole(this.getUser(), role);
	}

	public Boolean isUserBuyer(BaseEntity user) {
		return this.isUserRole(user, "PRI_IS_BUYER");
	}

	public Boolean isUserBuyer() {
		return this.isUserBuyer(this.getUser());
	}

	public Boolean isUserSeller() {
		return !this.isUserBuyer();
	}

	public Boolean isUserSeller(BaseEntity user) {
		return !this.isUserBuyer(user);
	}

	public String getFullName(final BaseEntity be) {
		String fullName = be.getLoopValue("PRI_FIRSTNAME", "") + " " + be.getLoopValue("PRI_LASTNAME", "");
		fullName = fullName.trim();
		return fullName;
	}

	public Boolean isUserPresent() {

		try {

			/*
			 * if this situation happens it just means that QRules has not registered the
			 * user yet. setting it.
			 */

			BaseEntity be = this.getUser();
			if (be != null) {

				if (isNull("USER")) {
					set("USER", be);
				}

				return true;

			}
		} catch (Exception e) {

		}

		return false;

		// if (isNull("USER")) {
		// return false;
		// } else {
		// return true;
		// }
	}

	/*
	 * Checks if Mandatory fields in the given Question Groups are filled
	 */
	public Boolean isMandatoryFieldsEntered(final String targertCode, final String questionCode) {
		Boolean status = false;
		if (getUser() != null) {
			status = QwandaUtils.isMandatoryFieldsEntered(getUser().getCode(), targertCode, questionCode, getToken());
		}

		return status;
	}

	// Check if Mobile Verification has been completed
	public Boolean isMobileVerificationCompleted() {
		Boolean status = true;
		Boolean value = null;
		try {
			value = getUser().getValue("PRI_MOBILE_VERIFICATION_COMPLETED", null);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (value == null || !value) {
			status = false;
		}

		return status;
	}


	public void publishBaseEntityByCode(final String be) {
		String[] recipientArray = new String[1];
		recipientArray[0] = be;
		publishBaseEntityByCode(be, null, null, recipientArray);
	}

	public void publishBaseEntityByCode(final String be, final Boolean delete) {
		String[] recipientArray = new String[1];
		recipientArray[0] = be;
		publishBaseEntityByCode(be, null, null, recipientArray, delete);
	}

	public void publishBaseEntityByCode(final String be, final String parentCode, final String linkCode,
			final String[] recipientCodes) {

		BaseEntity item = this.baseEntity.getBaseEntityByCode(be);
		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		publishData(msg, recipientCodes);

	}

	public void publishBaseEntityByCode(final String be, final String parentCode, final String linkCode,
			final String[] recipientCodes, final Boolean delete) {

		BaseEntity item = this.baseEntity.getBaseEntityByCode(be);
		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setDelete(delete);
		publishData(msg, recipientCodes);

	}

	public void publishBaseEntityByCode(final BaseEntity item, final String parentCode, final String linkCode,
			final String[] recipientCodes) {

		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		publishData(msg, recipientCodes);

	}

	public <T extends QMessage> void publishCmd(T msg, final String[] recipientCodes) {
		msg.setToken(getToken());
		publish("cmds", JsonUtils.toJson(msg));
	}

	public <T extends QMessage> void publishData(T msg, final String[] recipientCodes) {
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
	}

	public <T extends QMessage> void publish(final String busChannel, T msg, final String[] recipientCodes) {
		msg.setToken(getToken());
		publish(busChannel, JsonUtils.toJson(msg));
	}

	public void publishBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart,
			Integer pageSize, Boolean cache) {

		String json = RulesUtils.getBaseEntitysJsonByParentAndLinkCode(qwandaServiceUrl, getDecodedTokenMap(),
				getToken(), parentCode, linkCode);
		publish("cmds", json);
	}

	public void publishBaseEntitysByParentAndLinkCodeWithAttributes(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize, Boolean cache) {

		BaseEntity[] beArray = RulesUtils.getBaseEntitysArrayByParentAndLinkCodeWithAttributes(qwandaServiceUrl,
				getDecodedTokenMap(), getToken(), parentCode, linkCode, pageStart, pageSize);
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beArray, parentCode, linkCode);
		msg.setToken(getToken());

		publish("cmds", msg);

	}

	public void geofenceJob(final String begCode, final String driverCode) {
		geofenceJob(begCode, driverCode, 100.0);
	}

	public void geofenceJob(final String begCode, final String driverCode, Double radius) {

		BaseEntity be = RulesUtils.getBaseEntityByCode(QRules.getQwandaServiceUrl(), this.getDecodedTokenMap(),
				this.getToken(), begCode);
		if (be != null) {

			QCmdGeofenceMessage[] cmds = GPSUtils.geofenceJob(be, driverCode, radius, QRules.getQwandaServiceUrl(),
					this.getToken(), this.getDecodedTokenMap());

			if (cmds != null) {
				for (QCmdGeofenceMessage cmd : cmds) {
					if (cmd != null) {
						this.publishCmd(cmd);
					}
				}
			}
		}
	}

	public void showInternship(QEventBtnClickMessage m) {

		/* this answer will always have 1 item */
		String value = m.getData().getValue();
		if (value != null) {

			JsonObject data = new JsonObject(value);
			if (data != null) {

				String itemCode = data.getString("itemCode");
				if (itemCode != null) {
					this.sendSublayout("INTERNSHIP_DETAILS", "internships/details.json", itemCode);
				}
			}
		}
	}

	public void archivePaidProducts() {

		/* we get the list of products marked as "PAID" */

		/* we generate a service token */
		String proj_realm = System.getenv("PROJECT_REALM");
		String token = RulesUtils.generateServiceToken(proj_realm);
		if (token != null) {

			List<BaseEntity> paidProducts = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes(qwandaServiceUrl,
					getDecodedTokenMap(), token, "GRP_PAID", "LNK_CORE", 0, 1000);

			if (paidProducts != null) {

				this.println("Archiving " + paidProducts.size() + " products.");

				/* we loop through each BE and get their created date */
				for (BaseEntity be : paidProducts) {

					LocalDateTime now = LocalDateTime.now();
					LocalDateTime lastWeek = now.minusWeeks(1);

					/* we grab the paid date */
					Optional<EntityAttribute> paidDate = be.findEntityAttribute("PRI_IS_RELEASE_PAYMENT_DONE");
					if (paidDate.isPresent()) {

						LocalDateTime created = paidDate.get().getCreated();
						if (created.isBefore(lastWeek)) {

							/* BEG was paid >1 week - we archive it */
							this.baseEntity.moveBaseEntitySetLinkValue(be.getCode(), "GRP_PAID", "GRP_HISTORY", "LNK_CORE", "BEG");
						}
					}
				}
			}

			this.println("Archiving done.");
			this.reloadCache();

		} else {
			this.println("Could not get token.");
		}
	}

	public void postSlackNotification(String webhookURL, JsonObject message) throws IOException {

		try {

			// String payload = "payload=" + message.toString();

			final HttpClient client = HttpClientBuilder.create().build();

			final HttpPost post = new HttpPost(webhookURL);
			final StringEntity input = new StringEntity(message.toString());

			input.setContentType("application/json");
			post.setEntity(input);

			final HttpResponse response = client.execute(post);

			String retJson = "";
			final BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line = "";
			while ((line = rd.readLine()) != null) {
				retJson += line;
				;
			}

			int responseCode = response.getStatusLine().getStatusCode();
		} catch (IOException e) {
			this.println(e);
		}
	}

	public void sendInternMatchLayoutsAndData() {

		BaseEntity user = getUser();

		if (user != null) {

			String internValue = this.baseEntity.getBaseEntityAttrValueAsString(user, "PRI_IS_INTERN");
			Boolean isIntern = internValue != null && (internValue.equals("TRUE") || user.is("PRI_MENTOR"));

			/* Show loading indicator */
			showLoading("Loading your interface...");

			if (isIntern) {

				List<BaseEntity> root = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_ROOT", "LNK_CORE", 0, 20, false);
				publishCmd(root, "GRP_ROOT", "LNK_CORE");

				List<BaseEntity> dashboard = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_DASHBOARD", "LNK_CORE", 0, 20,
						false);
				publishCmd(dashboard, "GRP_DASHBOARD", "LNK_CORE");

				List<BaseEntity> internships = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_INTERNSHIPS", "LNK_CORE", 0, 50,
						false);
				publishCmd(internships, "GRP_INTERNSHIPS", "LNK_CORE");

				List<BaseEntity> companies = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_COMPANYS", "LNK_CORE", 0, 50,
						false);
				publishCmd(companies, "GRP_COMPANYS", "LNK_CORE");

				this.sendSublayout("intern-homepage", "homepage/dashboard_intern.json");
			}
		}
	}

	public void sendReloadPage() {

		QCmdReloadMessage cmdReload = new QCmdReloadMessage();
		this.publishCmd(cmdReload);
	}

	public void sendMessage(String begCode, String[] recipientArray, HashMap<String, String> contextMap,
			String templateCode, String messageType) {

		if (recipientArray != null && recipientArray.length > 0) {

			/* Adding project code to context */
			String projectCode = "PRJ_" + getAsString("realm").toUpperCase();
			contextMap.put("PROJECT", projectCode);

			JsonObject message = MessageUtils.prepareMessageTemplate(templateCode, messageType, contextMap,
					recipientArray, getToken());
			publish("messages", message);
		} else {
			log.error("Recipient array is null and so message cant be sent");
		}

	}

	public BaseEntity createUser() {

		BaseEntity be = null;

		String username = getAsString("preferred_username").toLowerCase();
		String firstname = StringUtils.capitaliseAllWords(getAsString("given_name").toLowerCase());
		String lastname = StringUtils.capitaliseAllWords(getAsString("family_name").toLowerCase());
		String realm = StringUtils.capitaliseAllWords(getAsString("realm").toLowerCase());
		String name = StringUtils.capitaliseAllWords(getAsString("name").toLowerCase());
		String email = getAsString("email").toLowerCase();
		String keycloakId = getAsString("sub").toLowerCase();

		try {
			be = QwandaUtils.createUser(qwandaServiceUrl, getToken(), username, firstname, lastname, email, realm, name,
					keycloakId);
			VertxUtils.writeCachedJson(be.getCode(), JsonUtils.toJson(be));
			be = getUser();
			set("USER", be);
			println("New User Created " + be);
			this.setState("DID_CREATE_NEW_USER");

		} catch (IOException e) {
			log.error("Error in Creating User ");
		}
		return be;
	}

	public void sendLayout(final String layoutCode, final String layoutPath) {
		this.sendLayout(layoutCode, layoutPath, realm());
	}

	public void sendLayout(final String layoutCode, final String layoutPath, final String folderName) {

		println("Loading layout: " + folderName + "/" + layoutPath);
		String layout = RulesUtils.getLayout(folderName + "/" + layoutPath);
		QCmdMessage layoutCmd = new QCmdLayoutMessage(layoutCode, layout);
		publishCmd(layoutCmd);
		println(layoutCode + " SENT TO FRONTEND");
	}

	public void sendPopupCmd(final String cmd_view, final String root) {

		QCmdMessage cmdJobSublayout = new QCmdMessage("CMD_POPUP", cmd_view);
		JsonObject cmdJobSublayoutJson = JsonObject.mapFrom(cmdJobSublayout);
		cmdJobSublayoutJson.put("token", getToken());
		if (root != null) {
			cmdJobSublayoutJson.put("root", root);
		}

		publish("cmds", cmdJobSublayoutJson);
	}

	public void sendViewCmd(final String cmd_view, final String root) {

		QCmdMessage cmdJobSublayout = new QCmdMessage("CMD_VIEW", cmd_view);
		JsonObject cmdJobSublayoutJson = JsonObject.mapFrom(cmdJobSublayout);
		cmdJobSublayoutJson.put("token", getToken());
		if (root != null) {
			cmdJobSublayoutJson.put("root", root);
		}

		publish("cmds", cmdJobSublayoutJson);
	}

	public void sendPopupLayout(final String layoutCode, final String sublayoutPath, final String root) {

		QCmdMessage cmdJobSublayout = new QCmdMessage("CMD_POPUP", layoutCode);
		JsonObject cmdJobSublayoutJson = JsonObject.mapFrom(cmdJobSublayout);
		String sublayoutString = RulesUtils.getLayout(sublayoutPath);
		cmdJobSublayoutJson.put("items", sublayoutString);
		cmdJobSublayoutJson.put("token", getToken());
		if (root != null) {
			cmdJobSublayoutJson.put("root", root);
		}

		publish("cmds", cmdJobSublayoutJson);
	}

	public void sendSublayout(final String layoutCode, final String sublayoutPath) {
		sendSublayout(layoutCode, sublayoutPath, null, false);
	}

	public void sendSublayout(final String layoutCode, final String sublayoutPath, final String root) {
		sendSublayout(layoutCode, sublayoutPath, root, false);
	}

	public void sendSublayout(final String layoutCode, final String sublayoutPath, final boolean isPopup) {
		sendSublayout(layoutCode, sublayoutPath, null, isPopup);
	}

	public void sendSublayout(final String layoutCode, final String sublayoutPath, final String root,
			final boolean isPopup) {

		String cmd_view = isPopup ? "CMD_POPUP" : "CMD_SUBLAYOUT";
		QCmdMessage cmdJobSublayout = new QCmdMessage(cmd_view, layoutCode);
		JsonObject cmdJobSublayoutJson = JsonObject.mapFrom(cmdJobSublayout);
		println("Loading url: " + realm() + "/" + sublayoutPath);
		String sublayoutString = RulesUtils.getLayout(realm() + "/" + sublayoutPath);
		cmdJobSublayoutJson.put("items", sublayoutString);
		cmdJobSublayoutJson.put("token", getToken());
		cmdJobSublayoutJson.put("root", root != null ? root : "test");

		publish("cmds", cmdJobSublayoutJson);
	}

	public void navigateTo(String newRoute) {

		if (newRoute == null) {
			return;
		}

		QCmdMessage cmdNavigate = new QCmdMessage("ROUTE_CHANGE", newRoute);
		JsonObject json = JsonObject.mapFrom(cmdNavigate);
		json.put("token", getToken());
		publish("cmds", json);
	}

	public void showLoading(String text) {

		if (text == null) {
			text = "Loading...";
		}

		QCmdMessage cmdLoading = new QCmdMessage("CMD_VIEW", "LOADING");
		JsonObject json = JsonObject.mapFrom(cmdLoading);
		json.put("root", text);
		json.put("token", getToken());
		publish("cmds", json);
	}

	public void sendParentLinks(final String targetCode, final String linkCode) {

		JsonArray latestLinks;

		try {

			latestLinks = new JsonArray(QwandaUtils.apiGet(
					getQwandaServiceUrl() + "/qwanda/entityentitys/" + targetCode + "/linkcodes/" + linkCode,
					getToken()));

			// Creating a data msg
			QDataJsonMessage msg = new QDataJsonMessage("LINK_CHANGE", latestLinks);

			msg.setToken(getToken());
			final JsonObject json = RulesUtils.toJsonObject(msg);
			json.put("items", latestLinks);
			publishData(json);
			// publish("cmds",json);
			// Send to all
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * @return the state
	 */
	public Map<String, Boolean> getState() {
		return stateMap;
	}

	/**
	 * @param state
	 *            the state to set
	 */
	public void setState(Map<String, Boolean> state) {
		this.stateMap = state;
	}

	/**
	 * @return the qwandaserviceurl
	 */
	public static String getQwandaServiceUrl() {
		return qwandaServiceUrl;
	}

	/**
	 * @return the devmode
	 */
	public static Boolean getDevmode() {
		return devMode;
	}

	public void send(final String channel, final Object payload) {
		send(channel, payload);
	}

	public void publishCmd(final BaseEntity be, final String aliasCode, final String[] recipientsCode) {

		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be, aliasCode);
		msg.setToken(getToken());
		if (recipientsCode != null) {
			msg.setRecipientCodeArray(recipientsCode);
		}
		System.out.println("Publishing Cmd " + be.getCode() + " with alias " + aliasCode);
		publish("cmds", msg);
	}

	public void publishData(final BaseEntity be, final String aliasCode, final String[] recipientsCode) {

		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be, aliasCode);
		msg.setToken(getToken());
		if (recipientsCode != null) {
			msg.setRecipientCodeArray(recipientsCode);
		}
		publish("cmds", RulesUtils.toJsonObject(msg));
	}

	public void publishCmd(final BaseEntity be, final String aliasCode) {
		this.publishCmd(be, aliasCode, null);
	}

	public QDataBaseEntityMessage publishData(final BaseEntity be, final String[] recipientsCode) {
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be, null);
		msg.setRecipientCodeArray(recipientsCode);
		msg.setToken(getToken());
		publish("data", msg);
		return msg;
	}

	public QMessage publishData(final Answer answer, final String[] recipientsCode) {
		QDataAnswerMessage msg = new QDataAnswerMessage(answer);
		msg.setRecipientCodeArray(recipientsCode);
		msg.setToken(getToken());
		publish("data", RulesUtils.toJsonObject(msg));
		return msg;
	}

	public QMessage publishCmdToRecipients(final BaseEntity be, final String[] recipientsCode) {
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be, null);
		msg.setRecipientCodeArray(recipientsCode);
		msg.setToken(getToken());
		publish("cmds", msg);
		return msg;
	}

	public void publishData(final JsonObject msg) {
		msg.put("token", getToken());
		publish("data", msg);
	}

	public void publishCmd(final JsonObject msg) {
		msg.put("token", getToken());
		Producer.getToWebCmds().write(msg).end();
	}

	public void publishCmd(final String jsonString) {
		Producer.getToWebCmds().write(jsonString).end();
	}

	public QMessage publishCmd(final QDataMessage msg) {
		msg.setToken(getToken());
		publish("cmds", msg);
		return msg;
	}

	public void publishCmd(final QBulkMessage msg) {
		msg.setToken(getToken());
		publish("cmds", msg);
	}

	public QMessage publishCmd(final QDataSubLayoutMessage msg) {
		msg.setToken(getToken());
		String json = JsonUtils.toJson(msg);
		publish("cmds", json);
		return msg;
	}

	public QMessage publishData(final QDataMessage msg) {
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
		return msg;
	}

	public void logout() {
		QCmdMessage msg = new QCmdMessage("CMD_LOGOUT", "LOGOUT");
		msg.setToken(getToken());
		String[] recipientCodes = new String[1];
		recipientCodes[0] = getUser().getCode();
		String json = JsonUtils.toJson(msg);
		JsonObject jsonObj = new JsonObject(json);
		JsonArray jsonArr = new JsonArray();
		jsonArr.add(getUser().getCode());
		jsonObj.put("recipientCodes", jsonArr);

		publishCmd(jsonObj);

	}

	public void logoutCleanup() {
		// Remove the session from the sessionstates
		Set<String> userSessions = VertxUtils.getSetString("", "SessionStates", getUser().getCode());
		userSessions.remove((String) getDecodedTokenMap().get("session_state"));

		VertxUtils.putSetString("", "SessionStates", getUser().getCode(), userSessions);

		if (userSessions.isEmpty()) {
			this.baseEntity.updateBaseEntityAttribute(getUser().getCode(), getUser().getCode(), "PRI_ONLINE", "FALSE");
		} else {
			this.baseEntity.updateBaseEntityAttribute(getUser().getCode(), getUser().getCode(), "PRI_ONLINE", "TRUE");
		}
	}

	public void publishData(final QDataAnswerMessage msg) {
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
	}

	public void publishData(final Answer answer) {
		QDataAnswerMessage msg = new QDataAnswerMessage(answer);
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
	}

	public void publishData(final List<Answer> answerList) {
		Answer[] answerArray = answerList.toArray(new Answer[answerList.size()]);
		QDataAnswerMessage msg = new QDataAnswerMessage(answerArray);
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
	}

	public void publishCmd(final Answer answer) {
		QDataAnswerMessage msg = new QDataAnswerMessage(answer);
		msg.setToken(getToken());
		publish("cmds", JsonUtils.toJson(msg));
	}

	public void publishData(final QDataAskMessage msg) {
		msg.setToken(getToken());
		publish("data", RulesUtils.toJsonObject(msg));
	}

	public void publishData(final QDataAttributeMessage msg) {
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
	}

	public QDataBaseEntityMessage publishCmd(final List<BaseEntity> beList, final String parentCode,
			final String linkCode) {
		return this.publishCmd(beList, parentCode, linkCode, null);
	}

	public QDataBaseEntityMessage publishCmd(final List<BaseEntity> beList, final String parentCode,
			final String linkCode, String[] recipientCodes) {
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beList.toArray(new BaseEntity[0]));
		msg.setParentCode(parentCode);
		msg.setLinkCode(linkCode);

		msg.setToken(getToken());
		if (recipientCodes != null) {
			msg.setRecipientCodeArray(recipientCodes);
		}

		publish("cmds", RulesUtils.toJsonObject(msg));
		return msg;

	}

	public void publishData(final List<BaseEntity> beList, final String parentCode, final String linkCode) {
		this.publishData(beList, parentCode, linkCode, null);
	}

	public void publishData(final BaseEntity be, final String parentCode, final String linkCode,
			String[] recipientCodes) {
		List<BaseEntity> beList = new ArrayList<BaseEntity>();
		beList.add(be);
		publishData(beList, parentCode, linkCode, recipientCodes);
	}

	public void publishData(final List<BaseEntity> beList, final String parentCode, final String linkCode,
			String[] recipientCodes) {
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beList.toArray(new BaseEntity[0]));
		msg.setParentCode(parentCode);
		msg.setLinkCode(linkCode);
		QBulkMessage bigMsg = new QBulkMessage(msg);

		msg.setToken(getToken());
		bigMsg.setToken(getToken());

		if (recipientCodes != null) {
			msg.setRecipientCodeArray(recipientCodes);
			bigMsg.setRecipientCodeArray(recipientCodes);

		}
		publish("data", RulesUtils.toJsonObject(msg));
	}

	public void publishCmd(final QCmdMessage cmdMsg) {
		cmdMsg.setToken(getToken());
		publish("cmds", RulesUtils.toJsonObject(cmdMsg));
	}

	public void publishCmd(final QEventLinkChangeMessage cmdMsg, final String[] recipientsCode) {

		cmdMsg.setToken(getToken());
		String jsonString = JsonUtils.toJson(cmdMsg);
		JsonObject json = new JsonObject(jsonString);

		JsonArray recipients = new JsonArray();
		for (String recipientCode : recipientsCode) {
			recipients.add(recipientCode);
		}

		json.put("recipientCodeArray", recipients);
		publish("data", json);

	}

	public void publishData(final QEventLinkChangeMessage cmdMsg, final String[] recipientsCode) {

		JsonArray recipients = new JsonArray();
		if (recipientsCode != null) {
			for (String recipientCode : recipientsCode) {
				if (recipientCode != null) {
					recipients.add(recipientCode);
				}
			}
		}

		cmdMsg.setToken(getToken());
		JsonObject json = new JsonObject(JsonUtils.toJson(cmdMsg));
		json.put("recipientCodeArray", recipients);

		publish("data", json);
	}

	public void publishMsg(final QMSGMessage msg) {

		msg.setToken(getToken());
		publish("messages", RulesUtils.toJsonObject(msg));
	}

	public void publish(String channel, Object payload) {
		VertxUtils.publish(getUser(), channel, payload);
	}

	public void loadUserRole() {

		BaseEntity user = this.getUser();
		if (user != null) {

			Boolean has_role_been_found = false;

			if (user != null) {

				List<EntityAttribute> roles = user.getBaseEntityAttributes().stream()
						.filter(x -> (x.getAttributeCode().startsWith("PRI_IS"))).collect(Collectors.toList());

				for (EntityAttribute role : roles) {

					if (role != null && role.getValue() != null
							&& !role.getAttributeCode().equals("PRI_IS_PROFILE_COMPLETED")
							&& !role.getAttributeCode().equals("PRI_IS_ADMIN")) {

						Boolean isRole = (role.getValueBoolean() != null && role.getValueBoolean() == true)
								|| (role.getValueString() != null && role.getValueString().equals("TRUE"));

						if (isRole) {
							this.setState(role.getAttributeCode());
							has_role_been_found = true;
						}
					}
				}
			}

			if (has_role_been_found) {
				this.setState("ROLE_FOUND");
			} else {
				this.setState("ROLE_NOT_FOUND");
			}
		}
	}

	public QDataAskMessage getAskQuestions(final QDataQSTMessage qstMsg) {

		JsonObject questionJson = null;
		QDataAskMessage msg = null;
		try {
			String json = QwandaUtils.apiPostEntity(getQwandaServiceUrl() + "/qwanda/asks/qst",
					JsonUtils.toJson(qstMsg), getToken());
			msg = JsonUtils.fromJson(json, QDataAskMessage.class);
			return msg;
		} catch (IOException e) {
			return msg;
		}
	}

	public QDataAskMessage askQuestions(final QDataQSTMessage qstMsg, final boolean isPopup) {
		return askQuestions(qstMsg, false);
	}

	public QDataAskMessage askQuestions(final QDataQSTMessage qstMsg, final boolean autoPushSelections,
			final boolean isPopup) {

		JsonObject questionJson = null;
		QDataAskMessage msg = null;
		String cmd_view = isPopup ? "CMD_POPUP" : "CMD_VIEW";
		try {
			if (autoPushSelections) {
				String json = QwandaUtils.apiPostEntity(getQwandaServiceUrl() + "/qwanda/asks/qst",
						JsonUtils.toJson(qstMsg), getToken());

				msg = JsonUtils.fromJson(json, QDataAskMessage.class);

				publishData(msg);

				QCmdViewMessage cmdFormView = new QCmdViewMessage(cmd_view, qstMsg.getRootQST().getQuestionCode());
				publishCmd(cmdFormView);

			} else {
				questionJson = new JsonObject(QwandaUtils.apiPostEntity(getQwandaServiceUrl() + "/qwanda/asks/qst",
						JsonUtils.toJson(qstMsg), getToken()));
				/* QDataAskMessage */
				questionJson.put("token", getToken());
				publish("data", questionJson);

				// Now auto push any selection data

				QCmdMessage cmdFormView = new QCmdMessage(cmd_view, "FORM_VIEW");
				JsonObject json = JsonObject.mapFrom(cmdFormView);
				json.put("root", qstMsg.getRootQST().getQuestionCode());
				json.put("token", getToken());
				publish("cmds", json);

			}

			/* layouts V2 */
			this.navigateTo("/questions/" + qstMsg.getRootQST().getQuestionCode());

			RulesUtils.println(qstMsg.getRootQST().getQuestionCode() + " SENT TO FRONTEND");

			return msg;
		} catch (IOException e) {
			return msg;
		}
	}

	public Boolean doesQuestionGroupExist(final String questionCode) {

		/* we grab the question group using the questionCode */
		QDataAskMessage questions = this.getQuestions(this.getUser().getCode(), this.getUser().getCode(), questionCode);

		/* we check if the question payload is not empty */
		if (questions != null) {

			/* we check if the question group contains at least one question */
			if (questions.getItems() != null && questions.getItems().length > 0) {

				Ask firstQuestion = questions.getItems()[0];

				/* we check if the question is a question group */
				if (firstQuestion.getAttributeCode().contains("QQQ_QUESTION_GROUP_BUTTON_SUBMIT")) {

					/* we see if this group contains at least one question */
					return firstQuestion.getChildAsks().length > 0;
				} else {

					/* if it is an ask we return true */
					return true;
				}
			}
		}

		/* we return false otherwise */
		return false;
	}

	public QDataAskMessage getQuestions(final String sourceCode, final String targetCode, final String questionCode) {

		String json;
		try {
			json = QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/baseentitys/" + sourceCode + "/asks2/"
					+ questionCode + "/" + targetCode, getToken());
			QDataAskMessage msg = JsonUtils.fromJson(json, QDataAskMessage.class);
			;
			return msg;

		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public void sendQuestions(final String sourceCode, final String targetCode, final String questionCode,
			final boolean autoPushSelections) throws ClientProtocolException, IOException {

		QDataAskMessage msg = this.getQuestions(sourceCode, targetCode, questionCode);
		if (msg != null) {
			publishData(msg);
		} else {
			log.error("Questions Msg is null " + sourceCode + "/asks2/" + questionCode + "/" + targetCode);
		}
	}

	public QDataAskMessage askQuestions(final String sourceCode, final String targetCode, final String questionCode) {
		return askQuestions(sourceCode, targetCode, questionCode, false);
	}

	public QDataAskMessage askQuestions(final String sourceCode, final String targetCode, final String questionCode,
			final boolean autoPushSelections) {
		return askQuestions(sourceCode, targetCode, questionCode, autoPushSelections, false);
	}

	public QDataAskMessage askQuestions(final String sourceCode, final String targetCode, final String questionCode,
			final boolean autoPushSelections, final boolean isPopup) {

		QDataAskMessage msg = null;
		String cmd_view = isPopup ? "CMD_POPUP" : "CMD_VIEW";

		try {

			this.sendQuestions(sourceCode, targetCode, questionCode, autoPushSelections);

			if (autoPushSelections) {

				// Now auto push any selection data
				// for (Ask ask : msg.getItems()) {
				// if (ask.getAttributeCode().startsWith("LNK_")) {
				//
				// // sendSelections(ask.getQuestion().getDataType(), "LNK_CORE", 10);
				// }
				// }

				QCmdViewMessage cmdFormView = new QCmdViewMessage(cmd_view, questionCode);
				publishCmd(cmdFormView);

			} else {

				QCmdMessage cmdFormView = new QCmdMessage(cmd_view, "FORM_VIEW");
				JsonObject json = JsonObject.mapFrom(cmdFormView);
				json.put("root", questionCode);
				json.put("token", getToken());
				publish("cmds", json);
			}

			/* layouts V2 */
			this.navigateTo("/questions/" + questionCode);

			RulesUtils.println(questionCode + " SENT TO FRONTEND");

			return msg;
		} catch (IOException e) {
			return msg;
		}
	}

	public boolean sendSelections(final String selectionRootCode, final String linkCode, final Integer maxItems) {

		JsonObject selectionLists;
		try {
			selectionLists = new JsonObject(QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/baseentitys/"
					+ selectionRootCode + "/linkcodes/" + linkCode + "?pageStart=0&pageSize=" + maxItems, getToken()));
			selectionLists.put("token", getToken());
			publish("cmds", selectionLists);
			return true;
		} catch (IOException e) {
			log.error("Unable to fetch selections");
			return false;
		}

	}

	public boolean sendSelections(final String selectionRootCode, final String linkCode, final String stakeholderCode,
			final Integer maxItems) {

		JsonObject selectionLists;
		try {
			selectionLists = new JsonObject(
					QwandaUtils.apiGet(
							getQwandaServiceUrl() + "/qwanda/baseentitys/" + selectionRootCode + "/linkcodes/"
									+ linkCode + "/attributes/" + stakeholderCode + "?pageStart=0&pageSize=" + maxItems,
							getToken()));
			selectionLists.put("token", getToken());
			publish("cmds", selectionLists);
			return true;
		} catch (IOException e) {
			log.error("Unable to fetch selections");
			return false;
		}

	}

	public boolean sendSelectionsWithLinkValue(final String selectionRootCode, final String linkCode,
			final String linkValue, final Integer maxItems) {

		JsonObject selectionList;
		try {

			selectionList = new JsonObject(
					QwandaUtils.apiGet(
							getQwandaServiceUrl() + "/qwanda/baseentitys2/" + selectionRootCode + "/linkcodes/"
									+ linkCode + "/linkValue/" + linkValue + "?pageStart=0&pageSize=" + maxItems,
							getToken()));

			selectionList.put("token", getToken());
			publish("cmds", selectionList);
			return true;
		} catch (IOException e) {
			log.error("Unable to fetch selections");
			return false;
		}

	}

	public void header() {
		try {

			RulesUtils.header(realm() + ":" + drools.getRule().getName() + " - "
					+ ((drools.getRule().getAgendaGroup() != null) ? drools.getRule().getAgendaGroup() : "") + " "
					+ this.decodedTokenMap.get("preferred_username") // This is faster than calling getUser()
					+ showStates());
		} catch (NullPointerException e) {
			println("Error in rules: ", "ANSI_RED");
		}
		ruleStartMs = System.nanoTime();
	}

	public void footer() {
		long endTime = System.nanoTime();
		double difference = (endTime - ruleStartMs) / 1e6; // get ms

		try {
			RulesUtils.footer(difference + " ms :" + drools.getRule().getName() + " - "
					+ ((drools.getRule().getAgendaGroup() != null) ? drools.getRule().getAgendaGroup() : "") + " "
					+ this.decodedTokenMap.get("preferred_username") // This is faster than calling getUser()
					+ showStates());
		} catch (NullPointerException e) {
			println("Error in rules: ", "ANSI_RED");
		}
	}

	/* Prints list of codes of a BaseEntity List */
	public void printList(final String text, final List<BaseEntity> beList) {
		Integer i = 1;
		if (beList != null) {
			RulesUtils.println(text + "   ::   ");
			RulesUtils.println("__________________________________");
			for (BaseEntity be : beList) {
				RulesUtils.println(i + "   ::   " + be.getCode());
				i++;
			}
			RulesUtils.println("__________________________________");
		} else {
			RulesUtils.println(text + "   ::   No Kids found");
		}
	}

	public void println(final Object str) {
		RulesUtils.println(str);
	}

	public void println(final Object str, final String colour) {
		RulesUtils.println(str, colour);
	}

	public void update() {
		this.drools.update(this);
	}


	public void debug() {
		println("");
	}

	public void debug(QEventLinkChangeMessage m) {
		println(m);
	}

	public List<Answer> processAddressAnswers(QDataAnswerMessage m) {

		// Put this in to stop bad User null error.... TODO
		if (getUser() == null) {
			return new ArrayList<Answer>();
		}
		try {
			List<Answer> resultAnswers = new ArrayList<Answer>();

			Answer[] answers = m.getItems();
			List<Answer> newAnswerList = new ArrayList<Answer>();

			for (Answer answer : answers) {

				answer.setSourceCode(answer.getTargetCode());
				String attributeCode = answer.getAttributeCode();
				String value = answer.getValue();

				println("value ::" + value + "attribute code ::" + attributeCode);

				/* if this answer is actually an address another rule will be triggered */
				if (attributeCode.contains("ADDRESS_JSON")) {

					JsonObject addressDataJson = new JsonObject(value);

					println("The Address Json is  :: " + addressDataJson);

					Map<String, String> availableKeys = new HashMap<String, String>();
					availableKeys.put("full_address", "FULL");
					availableKeys.put("street_address", "ADDRESS1");
					availableKeys.put("suburb", "SUBURB");
					availableKeys.put("state", "STATE");
					availableKeys.put("postal_code", "POSTCODE");
					availableKeys.put("country", "COUNTRY");

					int i = 0;
					for (Map.Entry<String, String> entry : availableKeys.entrySet()) {

						String key = entry.getKey();
						String valueEntry = entry.getValue();

						if (addressDataJson.containsKey(key)) {

							String newAttributeCode = attributeCode.replace("JSON", valueEntry);
							answer.setAttributeCode(newAttributeCode);
							answer.setValue(addressDataJson.getString(key));
							String jsonAnswer = JsonUtils.toJson(answer);
							Answer answerObj = JsonUtils.fromJson(jsonAnswer, Answer.class);
							newAnswerList.add(answerObj);

						}

					}

					/* Store latitude */
					String newAttCode = attributeCode.replace("JSON", "LATITUDE");
					answer.setAttributeCode(newAttCode);
					Double latitude = addressDataJson.getDouble("latitude");
					println(" The latitude value after conversion is  :: " + latitude);

					if (latitude != null) {
						answer.setValue(Double.toString(latitude));
						String jsonAnswer = JsonUtils.toJson(answer);
						Answer answerObj = JsonUtils.fromJson(jsonAnswer, Answer.class);
						println("The answer object for latitude attribute is  :: " + answerObj.toString());
						newAnswerList.add(answerObj);

						// println("The answer object for latitude attribute added to Answer array ");
					}

					/* Store longitude */
					newAttCode = attributeCode.replace("JSON", "LONGITUDE");
					answer.setAttributeCode(newAttCode);
					Double longitude = addressDataJson.getDouble("longitude");
					println(" The longitude value after conversion is  :: " + longitude);

					if (longitude != null) {
						answer.setValue(Double.toString(longitude));
						String jsonAnswer = JsonUtils.toJson(answer);
						Answer answerObj = JsonUtils.fromJson(jsonAnswer, Answer.class);
						newAnswerList.add(answerObj);
						i++;
					}

					// ArrayList<Answer> list = new ArrayList<Answer>();
					// for (Answer s : newAnswers) {
					// if (s != null)
					// list.add(s);
					// }

					println("---------------------------");
					// println(list);
					// newAnswers = list.toArray(new Answer[list.size()]);

					// println(newAnswers);

					/* set new answers */
					// m.setItems(newAnswers);
					// String json = JsonUtils.toJson(m);
					// println("updated answer json string ::" + json);

					/* send new answers to api */
					/*
					 * QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/answers/bulk2", json,
					 * getToken());
					 */
					// for (Answer an : newAnswers) {
					// publishData(an);
					resultAnswers.addAll(newAnswerList);
					// }
				}
			}
			return resultAnswers;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new ArrayList<Answer>();
	}

	public void processAnswerMessage(QDataAnswerMessage m) {
		publishData(m);
	}

	public void processChat(QEventMessage m) {

		String data = m.getData().getValue();
		JsonObject dataJson = new JsonObject(data);
		String text = dataJson.getString("value");
		String chatCode = dataJson.getString("itemCode");

		if (text != null && chatCode != null) {

			/* creating new message */
			BaseEntity newMessage = QwandaUtils.createBaseEntityByCode(
					QwandaUtils.getUniqueId(getUser().getCode(), null, "MSG", getToken()), "message",
					getQwandaServiceUrl(), getToken());
			if (newMessage != null) {

				List<BaseEntity> stakeholders = this.baseEntity.getParents(chatCode, "LNK_USER");
				String[] recipientCodeArray = new String[stakeholders.size()];
				/* List of receivers except current user */
				String[] msgReceiversCodeArray = new String[stakeholders.size() - 1];
				int counter = 0;
				for (BaseEntity stakeholder : stakeholders) {
					recipientCodeArray[counter] = stakeholder.getCode();
					if (!stakeholder.getCode().equals(getUser().getCode())) {
						msgReceiversCodeArray[counter] = stakeholder.getCode();
						counter += 1;
					}
				}
				List<Answer> answers = new ArrayList<Answer>();
				answers.add(new Answer(newMessage.getCode(), newMessage.getCode(), "PRI_MESSAGE", text));
				answers.add(new Answer(newMessage.getCode(), newMessage.getCode(), "PRI_CREATOR", getUser().getCode()));
				this.baseEntity.saveAnswers(answers);
				/* Add current date-time to char as */
				this.baseEntity.saveAnswer(new Answer(chatCode, chatCode, "PRI_DATE_LAST_MESSAGE",
						QwandaUtils.getZonedCurrentLocalDateTime()));

				System.out.println("The recipients are :: " + Arrays.toString(msgReceiversCodeArray));
				/* Publish chat to Receiver */
				publishData(this.baseEntity.getBaseEntityByCode(chatCode), msgReceiversCodeArray);
				/* Publish message to Receiver */
				publishData(this.baseEntity.getBaseEntityByCode(newMessage.getCode()), msgReceiversCodeArray); // Had to use getCode()

				QwandaUtils.createLink(chatCode, newMessage.getCode(), "LNK_MESSAGES", "message", 1.0, getToken());// Creating

				/* Sending Messages */
				HashMap<String, String> contextMap = new HashMap<String, String>();
				contextMap.put("SENDER", getUser().getCode());
				contextMap.put("CONVERSATION", newMessage.getCode());

				/* Sending toast message to all the beg frontends */
				sendMessage("", msgReceiversCodeArray, contextMap, "MSG_CH40_NEW_MESSAGE_RECIEVED", "TOAST");// TODO:
				sendMessage("", msgReceiversCodeArray, contextMap, "MSG_CH40_NEW_MESSAGE_RECIEVED", "SMS");// TODO: SMS
				sendMessage("", msgReceiversCodeArray, contextMap, "MSG_CH40_NEW_MESSAGE_RECIEVED", "EMAIL");
			}
		}
	}

	public void processChat2(QEventMessage m) {

		String data = m.getData().getValue();
		JsonObject dataJson = new JsonObject(data);
		String text = dataJson.getString("value");
		String chatCode = dataJson.getString("itemCode");

		if (text != null && chatCode != null) {

			/* creating new message */
			BaseEntity newMessage = QwandaUtils.createBaseEntityByCode(
					QwandaUtils.getUniqueId(getUser().getCode(), null, "MSG", getToken()), "message",
					getQwandaServiceUrl(), getToken());
			if (newMessage != null) {

				List<BaseEntity> stakeholders = this.baseEntity.getParents(chatCode, "LNK_USER");
				String[] recipientCodeArray = new String[stakeholders.size()];

				int counter = 0;
				for (BaseEntity stakeholder : stakeholders) {
					recipientCodeArray[counter] = stakeholder.getCode();
					counter += 1;
				}

				/*
				 * publishBaseEntityByCode(newMessage.getCode(), chatCode, "LNK_MESSAGES",
				 * recipientCodeArray);
				 */
				this.baseEntity.updateBaseEntityAttribute(newMessage.getCode(), newMessage.getCode(), "PRI_MESSAGE", text);
				this.baseEntity.updateBaseEntityAttribute(newMessage.getCode(), newMessage.getCode(), "PRI_CREATOR",
						getUser().getCode());
				QwandaUtils.createLink(chatCode, newMessage.getCode(), "LNK_MESSAGES", "message", 1.0, getToken());
				BaseEntity chatBE = this.baseEntity.getBaseEntityByCode(newMessage.getCode());
				publishBE(chatBE);
			}
		}
	}

	public void processImageUpload(QDataAnswerMessage m, final String finalAttributeCode) {

		/* we save the first photo as the icon of the BaseEntity */
		Answer[] answers = m.getItems();
		if (answers.length > 0) {

			Answer answer = answers[0];
			String sourceCode = answer.getSourceCode();
			String targetCode = answer.getTargetCode();
			answer.setSourceCode(answer.getTargetCode());
			String value = answer.getValue();
			if (value != null) {

				JsonArray imagesJson = new JsonArray(value);
				if (imagesJson != null) {

					JsonObject firstImage = imagesJson.getJsonObject(0);
					if (firstImage != null) {

						this.println(firstImage);
						String jsonStringImage = firstImage.getString("uploadURL");
						this.baseEntity.updateBaseEntityAttribute(sourceCode, targetCode, finalAttributeCode, jsonStringImage);
					}
				}
			}
		}
	}

	public void processAnswerRating(QDataAnswerMessage m, final String finalAttributeCode) {

		/* extract answers */
		Answer[] answers = m.getItems();
		for (Answer answer : answers) {

			String sourceCode = answer.getSourceCode();
			String targetCode = answer.getTargetCode();
			answer.setSourceCode(answer.getTargetCode());
			String attributeCode = answer.getAttributeCode();
			String value = answer.getValue();

			if (attributeCode.equals("PRI_RATING_RAW")) {
				List<Answer> answerList = new ArrayList<Answer>();

				/* Saving PRI_RATING attribute */
				answerList.add(new Answer(sourceCode, targetCode, "PRI_RATING", value));

				/* we grab the old value of the rating as well as the current rating */
				String currentRatingString = this.baseEntity.getBaseEntityValueAsString(targetCode, finalAttributeCode);
				String numberOfRatingString = this.baseEntity.getBaseEntityValueAsString(targetCode, "PRI_NUMBER_RATING");

				if (currentRatingString == null)
					currentRatingString = "0";
				if (numberOfRatingString == null)
					numberOfRatingString = "0";

				if (currentRatingString != null && numberOfRatingString != null) {

					Double currentRating = Double.parseDouble(currentRatingString);
					Double numberOfRating = Double.parseDouble(numberOfRatingString);
					Double newRating = Double.parseDouble(value);

					/* we compute the new rating */

					/*
					 * because for now we are not storing ALL the previous ratings, we calculate a
					 * rolling average
					 */

					Double newRatingAverage = ((currentRating * numberOfRating) + newRating) / (numberOfRating += 1);

					/* we increment the number of current ratings */
					// numberOfRating += 1;
					answerList.add(
							new Answer(sourceCode, targetCode, "PRI_NUMBER_RATING", Double.toString(numberOfRating)));

					answerList.add(
							new Answer(sourceCode, targetCode, finalAttributeCode, Double.toString(newRatingAverage)));

				}

				this.baseEntity.saveAnswers(answerList);
				/* publishData(answer); */
			}
		}
	}

	public List<Answer> processAnswer(QDataAnswerMessage m) {

		List<Answer> answerList = new ArrayList<Answer>(Arrays.asList(m.getItems()));;
		return answerList;
	}

	public void processAnswer2(QDataAnswerMessage m) {

		/* extract answers */
		List<Answer> answerList = new ArrayList<Answer>();

		Answer[] answers2 = m.getItems();
		for (Answer answer : answers2) {
			if (answer != null) {
				String attributeCode = answer.getAttributeCode();

				/* if this answer is actually an address another rule will be triggered */
				if (!attributeCode.contains("ADDRESS_FULL") && !attributeCode.contains("PRI_PAYMENT_METHOD")) {
					answerList.add(answer);
				}
			} else {
				println("Answer was null ");
			}
		}

		this.baseEntity.saveAnswers(answerList);

	}

	public void startWorkflow(final String id) {

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("rules", this);

		startWorkflow(id, params);
	}

	public void startWorkflow(final String id, Map<String, Object> parms) {

		println("Starting process " + id);
		if (drools != null) {

			parms.put("rules", this);
			drools.getKieRuntime().startProcess(id, parms);
		}
	}

	public void askQuestionFormViewPublish(String sourceCode, String targetCode, String questionCode) {

		String json;
		try {
			json = QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/baseentitys/" + sourceCode + "/asks2/"
					+ questionCode + "/" + targetCode, getToken());

			QDataAskMessage msg = JsonUtils.fromJson(json, QDataAskMessage.class);

			println("QDataAskMessage for payments question group ::" + msg);

			msg.setToken(getToken());
			publish("cmds", JsonUtils.toJson(msg));

			QCmdViewMessage cmdFormView = new QCmdViewMessage("FORM_VIEW", questionCode);
			publishCmd(cmdFormView);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * @return the started
	 */
	public Boolean isStarted() {
		return this.started;
	}

	/**
	 * @param started
	 *            the started to set
	 */
	public void setStarted(Boolean started) {
		this.started = started;
	}

	public BigDecimal includeGST(BigDecimal price) {

		BigDecimal gstPrice = price;

		/* Including 10% of price for GST */

		if (price.compareTo(BigDecimal.ZERO) > 0) {

			BigDecimal priceToBeIncluded = price.multiply(new BigDecimal("0.1"));
			gstPrice = price.add(priceToBeIncluded);
		}

		return gstPrice;

	}

	public BigDecimal excludeGST(BigDecimal price) {

		/* Excluding 10% of price for GST */

		BigDecimal gstPrice = price;

		if (price.compareTo(BigDecimal.ZERO) > 0) {

			BigDecimal priceToBeIncluded = price.multiply(new BigDecimal("0.1"));
			gstPrice = price.subtract(priceToBeIncluded);
		}

		return gstPrice;
	}

	public Money includeGSTMoney(Money price) {

		Money gstPrice = Money.of(0, price.getCurrency());

		/* Including 10% of price for GST */

		if (price.compareTo(gstPrice) > 0) {

			Money priceToBeIncluded = price.multiply(0.1);
			gstPrice = price.add(priceToBeIncluded);
		}

		return Money.of(gstPrice.getNumber().doubleValue(), price.getCurrency());
		// return gstPrice;

	}

	public Money excludeGSTMoney(Money price) {

		Money gstPrice = Money.of(0, price.getCurrency());

		/* Including 10% of price for GST */

		if (price.compareTo(gstPrice) > 0) {

			Money priceToBeIncluded = price.multiply(0.1);
			gstPrice = price.subtract(priceToBeIncluded);
		}

		return gstPrice;

	}

	public String showStates() {
		String states = "  ";
		for (String key : stateMap.keySet()) {
			states += key + ":";
		}
		return states;
	}

	public void sendNotification(final String text, final String[] recipientCodes) {
		sendNotification(text, recipientCodes, "info");
	}

	public void sendNotification(final String text, final String[] recipientCodes, final String style) {

		Layout notificationLayout = new Layout(text, style, null, null, null);
		QDataSubLayoutMessage data = new QDataSubLayoutMessage(notificationLayout, getToken());
		data.setRecipientCodeArray(recipientCodes);
		publishCmd(data);
	}

	private void sendSublayouts(final String realm) {

		try {

			String subLayoutMap = RulesUtils.getLayout(realm + "/sublayouts");
			if (subLayoutMap != null) {

				JsonArray subLayouts = new JsonArray(subLayoutMap);
				if (subLayouts != null) {

					Layout[] layoutArray = new Layout[subLayouts.size()];
					for (int i = 0; i < subLayouts.size(); i++) {
						JsonObject sublayoutData = null;

						try {
							sublayoutData = subLayouts.getJsonObject(i);
						} catch (Exception e1) {
							e1.printStackTrace();
						}

						String url = sublayoutData.getString("download_url");
						String name = sublayoutData.getString("name");
						name = name.replace(".json", "");
						name = name.replaceAll("\"", "");

						if (url != null) {

							/* grab sublayout from github */
							println(i + ":" + url);

							String subLayoutString = QwandaUtils.apiGet(url, null);
							if (subLayoutString != null) {

								try {
									layoutArray[i] = new Layout(name, subLayoutString, null, null, null);
								} catch (Exception e) {
								}
							}
						}
					}
					/* send sublayout to FE */
					QDataSubLayoutMessage msg = new QDataSubLayoutMessage(layoutArray, getToken());
					publishCmd(msg);
				}
			}
		} catch (Exception e) {

		}
	}

	private void sendAllSublayouts() {

		this.sendSublayouts("genny");
		this.sendSublayouts(realm());
	}

	public void sendAllLayouts() {

		/* Layouts v1 */
		this.sendAllSublayouts();

		/* Layouts V2 */
		List<BaseEntity> beLayouts = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_LAYOUTS", "LNK_CORE", 0, 500, false);
		this.publishCmd(beLayouts, "GRP_LAYOUTS", "LNK_CORE");

		/*List<BaseEntity> beLayouts = this.getAllLayouts(); */
	}

	/*
	 * Gets all the attributes and Publishes to the DATA channel
	 */
	public void sendAllAttributes() {
		println("Sending all the attributes");
		try {

			QDataAttributeMessage msg = RulesUtils.loadAllAttributesIntoCache(getToken());
			publishData(msg);
			println("All the attributes sent");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void processDimensions(QEventAttributeValueChangeMessage msg) {
		Answer newAnswer = msg.getAnswer();
		BaseEntity load = this.baseEntity.getBaseEntityByCode(newAnswer.getTargetCode());
		println("The laod value is " + load.toString());

		String value = newAnswer.getValue();
		println("The load " + msg.getData().getCode() + " is    ::" + value);

		/* Get the sourceCode(Job code) for this LOAD */
		BaseEntity job = this.baseEntity.getParent(newAnswer.getTargetCode(), "LNK_BEG");

		Answer jobDimensionAnswer = new Answer(getUser().getCode(), job.getCode(), msg.getData().getCode(), value);
		this.baseEntity.saveAnswer(jobDimensionAnswer);
	}

	public String getCurrentLocalDateTime() {
		LocalDateTime date = LocalDateTime.now();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");
		Date datetime = Date.from(date.atZone(ZoneId.systemDefault()).toInstant());
		String dateString = df.format(datetime);

		return dateString;
	}

	public void publishBE(final BaseEntity be) {

		this.baseEntity.addAttributes(be);
		String[] recipientCodes = new String[1];
		recipientCodes[0] = be.getCode();
		publishBE(be, recipientCodes);
	}

	public void publishBE(final BaseEntity be, String[] recipientCodes) {

		this.baseEntity.addAttributes(be);

		if (recipientCodes == null || recipientCodes.length == 0) {
			recipientCodes = new String[1];
			recipientCodes[0] = getUser().getCode();
		}

		println("PUBLISHBE:" + be.getCode() + " with " + be.getBaseEntityAttributes().size() + " attribute changes");
		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = be;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, null, null);
		msg.setRecipientCodeArray(recipientCodes);
		// String json = JsonUtils.toJson(msg);
		publishData(msg, recipientCodes);
	}

	public Money calcOwnerFee(Money input) {

		CurrencyUnit DEFAULT_CURRENCY_TYPE = input.getCurrency();
		Number inputNum = input.getNumber();

		Money ownerFee = Money.of(0, DEFAULT_CURRENCY_TYPE);

		Number RANGE_1 = 999.99;
		Number RANGE_2 = 2999.99;
		Number RANGE_3 = 4999.99;

		Number FEE_1 = 0.15;
		Number FEE_2 = 0.125;
		Number FEE_3 = 0.09;
		Number FEE_4 = 0.05;

		Number RANGE_1_COMPONENT = MoneyHelper.mul(inputNum, FEE_1);
		Number RANGE_2_COMPONENT = MoneyHelper.mul(RANGE_1, FEE_1);
		;
		Number RANGE_3_COMPONENT = MoneyHelper.mul(MoneyHelper.sub(RANGE_2, RANGE_1), FEE_2);
		Number RANGE_4_COMPONENT = MoneyHelper.mul(MoneyHelper.sub(RANGE_3, RANGE_2), FEE_3);

		if (inputNum.doubleValue() <= RANGE_1.doubleValue()) {
			// RANGE_1_COMPONENT
			ownerFee = Money.of(RANGE_1_COMPONENT, DEFAULT_CURRENCY_TYPE);

			println("range 1 ");
		}

		if (inputNum.doubleValue() > RANGE_1.doubleValue() && inputNum.doubleValue() <= RANGE_2.doubleValue()) {
			// RANGE_2_COMPONENT + (input - RANGE_1) * FEE_2
			println(input);
			Money subtract = MoneyHelper.sub(input, RANGE_1);
			println(subtract);
			Money multiply = MoneyHelper.mul(subtract, FEE_2);
			println(multiply);
			ownerFee = MoneyHelper.add(multiply, RANGE_2_COMPONENT);
			println(ownerFee);

			println("range 2 ");
		}

		if (inputNum.doubleValue() > RANGE_2.doubleValue() && inputNum.doubleValue() <= RANGE_3.doubleValue()) {
			// RANGE_2_COMPONENT + RANGE_3_COMPONENT + (input - RANGE_2) * FEE_3
			Number addition1 = MoneyHelper.add(RANGE_2_COMPONENT, RANGE_3_COMPONENT);
			Money subtract = MoneyHelper.sub(input, RANGE_2);
			Money multiply = MoneyHelper.mul(subtract, FEE_3);
			Money addition2 = MoneyHelper.add(multiply, addition1);
			ownerFee = addition2;

			println("range 3 ");
		}

		if (inputNum.doubleValue() > RANGE_3.doubleValue()) {
			// RANGE_2_COMPONENT + RANGE_3_COMPONENT + RANGE_4_COMPONENT + ( input - RANGE_3
			// ) * FEE_4
			Number addition1 = MoneyHelper.add(RANGE_2_COMPONENT, RANGE_3_COMPONENT);
			Number addition2 = MoneyHelper.add(addition1, RANGE_4_COMPONENT);
			Money subtract = MoneyHelper.sub(input, RANGE_3);
			Money multiply = MoneyHelper.mul(subtract, FEE_4);
			Money addition3 = MoneyHelper.add(multiply, addition2);
			ownerFee = addition3;

			println("range 4 ");
		}

		/*
		 * To prevent exponential values from appearing in amount. Not 1.7E+2, We need
		 * 170
		 */
		ownerFee = MoneyHelper.round(ownerFee);
		ownerFee = Money.of(ownerFee.getNumber().doubleValue(), DEFAULT_CURRENCY_TYPE);

		Number amount = ownerFee.getNumber().doubleValue();
		Money fee = Money.of(amount.doubleValue(), DEFAULT_CURRENCY_TYPE);
		println("From QRules " + fee);
		return fee;

	}

	public Money calcDriverFee(Money input) { // TODO, why is this here?

		CurrencyUnit DEFAULT_CURRENCY_TYPE = input.getCurrency();
		Number inputNum = input.getNumber();

		Money driverFee = Money.of(0, DEFAULT_CURRENCY_TYPE);

		Number RANGE_1 = 999.99;
		Number RANGE_2 = 2999.99;
		Number RANGE_3 = 4999.99;

		Number FEE_1 = 0.15;
		Number FEE_2 = 0.125;
		Number FEE_3 = 0.09;
		Number FEE_4 = 0.05;

		Number ONE = 1;

		// const REVERSE_FEE_MULTIPLIER_1 = ( RANGE_2 - RANGE_1 ) * FEE_2;
		// const REVERSE_FEE_MULTIPLIER_2 = ( RANGE_3 - RANGE_2 ) * FEE_3;

		Number subtract01 = MoneyHelper.sub(RANGE_2, RANGE_1);
		Number subtract02 = MoneyHelper.sub(RANGE_3, RANGE_2);
		Number REVERSE_FEE_MULTIPLIER_1 = MoneyHelper.mul(subtract01, FEE_2);
		Number REVERSE_FEE_MULTIPLIER_2 = MoneyHelper.mul(subtract02, FEE_3);

		// const REVERSE_FEE_BOUNDARY_1 = RANGE_1 - ( RANGE_1 * FEE_1 );
		// const REVERSE_FEE_BOUNDARY_2 = RANGE_2 - REVERSE_FEE_MULTIPLIER_1 - ( RANGE_1
		// * FEE_1 );
		// const REVERSE_FEE_BOUNDARY_3 = RANGE_3 - REVERSE_FEE_MULTIPLIER_2 -
		// REVERSE_FEE_MULTIPLIER_1 - ( RANGE_1 * FEE_1 );

		Number multiply01 = MoneyHelper.mul(RANGE_1, FEE_1);
		Number REVERSE_FEE_BOUNDARY_1 = MoneyHelper.sub(RANGE_1, multiply01);

		Number subtract03 = MoneyHelper.sub(RANGE_2, REVERSE_FEE_MULTIPLIER_1);
		Number REVERSE_FEE_BOUNDARY_2 = MoneyHelper.sub(subtract03, multiply01);

		Number subtract04 = MoneyHelper.sub(RANGE_3, REVERSE_FEE_MULTIPLIER_2);
		Number subtract05 = MoneyHelper.sub(subtract04, REVERSE_FEE_MULTIPLIER_1);
		Number REVERSE_FEE_BOUNDARY_3 = MoneyHelper.sub(subtract05, multiply01);

		if (inputNum.doubleValue() < REVERSE_FEE_BOUNDARY_1.doubleValue()) {
			// return calcOwnerFee( inputNum * (1 / (1 - FEE_1)));
			Number subtract = MoneyHelper.sub(ONE, FEE_1);
			Number divide = MoneyHelper.div(ONE, subtract);
			Money multiply = MoneyHelper.mul(input, divide);
			driverFee = calcOwnerFee(multiply);

			println("zone 1 ");
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
			driverFee = calcOwnerFee(multiply3);

			println("zone 2 ");
		}

		if (inputNum.doubleValue() >= REVERSE_FEE_BOUNDARY_2.doubleValue()
				&& inputNum.doubleValue() < REVERSE_FEE_BOUNDARY_3.doubleValue()) {
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
			driverFee = calcOwnerFee(multiply3);

			println("zone 3 ");
		}

		if (inputNum.doubleValue() >= REVERSE_FEE_BOUNDARY_3.doubleValue()) {
			// calcFee(( input ) * (1 / (1 - (( REVERSE_FEE_BOUNDARY_1 * FEE_1 ) +
			// REVERSE_FEE_MULTIPLIER_1 + REVERSE_FEE_MULTIPLIER_2 + (( input -
			// REVERSE_FEE_BOUNDARY_3 ) * FEE_4 )) / input )))

			Money subtract1 = MoneyHelper.sub(input, REVERSE_FEE_BOUNDARY_3);
			Money multiply1 = MoneyHelper.mul(subtract1, FEE_4);

			Number multiply2 = MoneyHelper.mul(REVERSE_FEE_BOUNDARY_1, FEE_1);
			Number addition1 = MoneyHelper.add(multiply2, REVERSE_FEE_MULTIPLIER_1);
			Number addition2 = MoneyHelper.add(addition1, REVERSE_FEE_MULTIPLIER_2);

			Money addition3 = MoneyHelper.add(multiply1, addition2);
			Money divide1 = MoneyHelper.div(addition3, input);
			Money subtract2 = MoneyHelper.sub(ONE, divide1);
			Money divide2 = MoneyHelper.div(ONE, subtract2);

			Money multiply3 = MoneyHelper.mul(input, divide2);
			driverFee = calcOwnerFee(multiply3);

			println("zone 4 ");
		}
		return driverFee;
	}

	public String[] getRecipientCodes(final QEventAttributeValueChangeMessage msg) {
		String[] results = null;

		Set<EntityEntity> links = msg.getBe().getLinks();
		Set<String> recipientCodesSet = new HashSet<String>();
		for (EntityEntity ee : links) {
			Link link = ee.getLink();
			String[] recipientArray = VertxUtils.getSubscribers(realm(), link.getTargetCode());
			if (recipientArray != null) {
				recipientCodesSet.addAll(Sets.newHashSet(recipientArray));
			}
			String[] recipientArray2 = VertxUtils.getSubscribers(realm(), link.getSourceCode());
			if (recipientArray2 != null) {
				recipientCodesSet.addAll(Sets.newHashSet(recipientArray2));
			}

			if ((link.getTargetCode().startsWith("PER_"))) {
				// Assume that any change has been done by someone actually logged on! So assume
				// we can spit out push
				recipientCodesSet.add(link.getTargetCode());
			}
			if ((link.getSourceCode().startsWith("PER_"))) {
				// Assume that any change has been done by someone actually logged on! So assume
				// we can spit out push
				recipientCodesSet.add(link.getSourceCode());
			}
			if ((link.getSourceCode().startsWith("GRP_"))) {
				String[] recipientArray3 = VertxUtils.getSubscribers(realm(), link.getSourceCode());
				if (recipientArray3 != null) {
					recipientCodesSet.addAll(Sets.newHashSet(recipientArray3));
				}
			}
			if ((link.getTargetCode().startsWith("GRP_"))) {
				String[] recipientArray3 = VertxUtils.getSubscribers(realm(), link.getTargetCode());
				if (recipientArray3 != null) {
					recipientCodesSet.addAll(Sets.newHashSet(recipientArray3));
				}
			}

		}
		// Add the original token holder
		if (!getUser().getCode().equals("PER_SERVICE")) {
			recipientCodesSet.add(getUser().getCode());
		}
		results = (String[]) FluentIterable.from(recipientCodesSet).toArray(String.class);
		return results;
	}

	public String[] getRecipientCodes(final QEventLinkChangeMessage msg) {
		String[] results = null;

		Link link = msg.getLink();
		Set<String> recipientCodesSet = new HashSet<String>();

		if (link != null) {
			String[] recipientArray = VertxUtils.getSubscribers(realm(), link.getTargetCode());
			if (recipientArray != null) {
				recipientCodesSet.addAll(Sets.newHashSet(recipientArray));
			}
			String[] recipientArray2 = VertxUtils.getSubscribers(realm(), link.getSourceCode());
			if (recipientArray2 != null) {
				recipientCodesSet.addAll(Sets.newHashSet(recipientArray2));
			}

			if ((link.getTargetCode().startsWith("PER_"))) {
				// Assume that any change has been done by someone actually logged on! So assume
				// we can spit out push
				recipientCodesSet.add(link.getTargetCode());
			}
			if ((link.getSourceCode().startsWith("PER_"))) {
				// Assume that any change has been done by someone actually logged on! So assume
				// we can spit out push
				recipientCodesSet.add(link.getSourceCode());
			}
			if ((link.getSourceCode().startsWith("GRP_"))) {
				String[] recipientArray3 = VertxUtils.getSubscribers(realm(), link.getSourceCode());
				if (recipientArray3 != null) {
					recipientCodesSet.addAll(Sets.newHashSet(recipientArray3));
				}
			}
			if ((link.getTargetCode().startsWith("GRP_"))) {
				String[] recipientArray3 = VertxUtils.getSubscribers(realm(), link.getTargetCode());
				if (recipientArray3 != null) {
					recipientCodesSet.addAll(Sets.newHashSet(recipientArray3));
				}
			}

		}

		Link oldlink = msg.getOldLink();
		if (oldlink != null) {
			String[] recipientArrayOld = VertxUtils.getSubscribers(realm(), oldlink.getTargetCode());
			if (recipientArrayOld != null) {
				recipientCodesSet.addAll(Sets.newHashSet(recipientArrayOld));
			}
			String[] recipientArray2Old = VertxUtils.getSubscribers(realm(), oldlink.getSourceCode());
			if (recipientArray2Old != null) {
				recipientCodesSet.addAll(Sets.newHashSet(recipientArray2Old));
			}
			if ((oldlink.getTargetCode().startsWith("PER_"))) {
				// Assume that any change has been done by someone actually logged on! So assume
				// we can spit out push
				recipientCodesSet.add(oldlink.getTargetCode());
			}
			if ((oldlink.getSourceCode().startsWith("PER_"))) {
				// Assume that any change has been done by someone actually logged on! So assume
				// we can spit out push
				recipientCodesSet.add(oldlink.getSourceCode());
			}
			if ((oldlink.getSourceCode().startsWith("GRP_"))) {
				String[] recipientArray = VertxUtils.getSubscribers(realm(), oldlink.getSourceCode());
				if (recipientArray != null) {
					recipientCodesSet.addAll(Sets.newHashSet(recipientArray));
				}
			}
			if ((oldlink.getTargetCode().startsWith("GRP_"))) {
				String[] recipientArray = VertxUtils.getSubscribers(realm(), oldlink.getTargetCode());
				if (recipientArray != null) {
					recipientCodesSet.addAll(Sets.newHashSet(recipientArray));
				}
			}

		}

		// Add the original token holder
		if (getUser() != null)
			recipientCodesSet.add(getUser().getCode());
		results = (String[]) FluentIterable.from(recipientCodesSet).toArray(String.class);
		return results;
	}

	public void subscribeUserToBaseEntity(String userCode, String beCode) {
		VertxUtils.subscribe(realm(), beCode, userCode);
	}

	public void subscribeUserToBaseEntity(String userCode, BaseEntity be) {
		VertxUtils.subscribe(realm(), be, userCode);
	}

	public void subscribeUserToBaseEntities(String userCode, List<BaseEntity> bes) {
		VertxUtils.subscribe(realm(), bes, userCode);
	}

	public void subscribeUserToBaseEntityAndChildren(String userCode, String beCode, String linkCode) {
		List<BaseEntity> beList = new ArrayList<BaseEntity>();
		BaseEntity parent = this.baseEntity.getBaseEntityByCode(beCode);
		if (parent != null) {
			beList = this.baseEntity.getBaseEntitysByParentAndLinkCode(beCode, linkCode, 0, 500, false);
			beList.add(parent);
		}
		println("parent and child List ::  " + beList);
		subscribeUserToBaseEntities(userCode, beList);
	}

	public void unsubscribeUserToBaseEntity(final String userCode, String beCode) {
		final String SUB = "SUB";
		// Subscribe to a code
		Set<String> unsubscriberSet = new HashSet<String>();

		unsubscriberSet.add(userCode);
		println("unsubscriber is   ::   " + unsubscriberSet.toString());

		Set<String> subscriberSet = VertxUtils.getSetString(realm(), SUB, beCode);
		println("all subscribers   ::   " + subscriberSet.toString());

		subscriberSet.removeAll(unsubscriberSet);
		println("after removal, subscriber is   ::   " + subscriberSet.toString());

		VertxUtils.putSetString(realm(), SUB, beCode, subscriberSet);

	}

	/**
	 * @param bulkmsg
	 * @return
	 */
	private void sendTreeViewData(List<QDataBaseEntityMessage> bulkmsg, BaseEntity user) {

		List<BaseEntity> root = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_ROOT", "LNK_CORE", 0, 20, false);
		List<BaseEntity> toRemove = new ArrayList<BaseEntity>();
		/* Removing GRP_DRAFTS be if user is a Driver */

		if (this.isUserSeller()) {

			for (BaseEntity be : root) {
				if (be.getCode().equalsIgnoreCase("GRP_DRAFTS") || be.getCode().equalsIgnoreCase("GRP_BIN")) {
					toRemove.add(be);
					println("GRP_DRAFTS & GRP_BIN has been added to remove list");
				}

			}
			root.removeAll(toRemove);
			// println("GRP_DRAFTS & GRP_BIN have been removed from root");
		}
		bulkmsg.add(publishCmd(root, "GRP_ROOT", "LNK_CORE"));
		// println(root);

		List<BaseEntity> reportsHeader = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_REPORTS", "LNK_CORE", 0, 20, false);
		List<BaseEntity> reportsHeaderToRemove = new ArrayList<BaseEntity>();
		// println("User is Admin " + hasRole("admin"));
		if (reportsHeader != null) {
			if (isRealm("channel40")) { // Removing USER Reports for channel40
				for (BaseEntity be : reportsHeader) {
					if (be.getCode().equalsIgnoreCase("GRP_REPORTS_USER")) {
						reportsHeaderToRemove.add(be);
					}
				}
			}
			// Checking for driver role

			if (this.isUserSeller()) {

				for (BaseEntity be : reportsHeader) {
					if (be.getCode().equalsIgnoreCase("GRP_REPORTS_OWNER")) {
						reportsHeaderToRemove.add(be);
					}
				}
			}
			// Checking for owner role

			else if (this.isUserBuyer()) {

				for (BaseEntity be : reportsHeader) {
					if (be.getCode().equalsIgnoreCase("GRP_REPORTS_DRIVER")) {
						reportsHeaderToRemove.add(be);
					}
				}
			}
			// checking for admin role
			if (!(hasRole("admin"))) {
				for (BaseEntity be : reportsHeader) {
					if (be.getCode().equalsIgnoreCase("GRP_REPORTS_ADMIN")) {
						reportsHeaderToRemove.add(be);
					}
				}
			}
			// Removing reports not related to the user based on their role
			reportsHeader.removeAll(reportsHeaderToRemove);
		} else {
			println("The group GRP_REPORTS doesn't have any child");
		}
		// println("Unrelated reports have been removed ");
		bulkmsg.add(publishCmd(reportsHeader, "GRP_REPORTS", "LNK_CORE"));

		List<BaseEntity> admin = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_ADMIN", "LNK_CORE", 0, 20, false);
		bulkmsg.add(publishCmd(admin, "GRP_ADMIN", "LNK_CORE"));

		/*
		 * if(hasRole("admin")){ List<BaseEntity> reports =
		 * getBaseEntitysByParentAndLinkCode("GRP_REPORTS", "LNK_CORE", 0, 20, false);
		 * publishCmd(reports, "GRP_REPORTS", "LNK_CORE"); }
		 */

		if (this.isUserBuyer()) {

			List<BaseEntity> bin = this.baseEntity.getBaseEntitysByParentLinkCodeAndLinkValue("GRP_BIN", "LNK_CORE", user.getCode(), 0,
					20, false);
			bulkmsg.add(publishCmd(bin, "GRP_BIN", "LNK_CORE"));
		}

	}

	static QBulkMessage cache = null;
	static String cache2 = null;

	/*
	 * Method to send All the Chats for the current user
	 */
	public void sendAllChats(final int pageStart, final int pageSize) {

		BaseEntity currentUser = getUser();

		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();
		QDataBaseEntityMessage qMsg;

		SearchEntity sendAllChats = new SearchEntity("SBE_AllMYCHAT", "All My Chats").addColumn("PRI_TITLE", "Title")
				.addColumn("PRI_DATE_LAST_MESSAGE", "Last Message On").setStakeholder(getUser().getCode())
				.addSort("PRI_DATE_LAST_MESSAGE", "Recent Message", SearchEntity.Sort.DESC) // Sort doesn't work in
				.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "CHT_%").setPageStart(pageStart)
				.setPageSize(pageSize);
		try {
			qMsg = getSearchResults(sendAllChats);
		} catch (IOException e) {
			System.out.println("Error! Unable to get Search Rsults");
			qMsg = null;
			e.printStackTrace();
		}
		if (qMsg != null) {
			List<BaseEntity> conversations = Arrays.asList(qMsg.getItems());
			List<BaseEntity> userConversations = new ArrayList<BaseEntity>();

			if (conversations != null) {
				for (BaseEntity convo : conversations) {
					// Getting list of the users- sender and receiver of the chat
					List<BaseEntity> users = new ArrayList<BaseEntity>();
					Set<EntityEntity> chatUsers = convo.getLinks();
					for (EntityEntity links : chatUsers) {
						// String linkCode = links.getLink().getAttributeCode();
						if (links.getLink().getAttributeCode().equalsIgnoreCase("LNK_USER")) {
							// String userCode = links.getLink().getTargetCode();
							users.add(this.baseEntity.getBaseEntityByCode(links.getLink().getTargetCode()));
						}
					}
					if (users != null) {
						if (users.contains(getUser())) {
							for (BaseEntity linkedUser : users) {
								/* if user is a stackholder of this conversation we send it */
								if (linkedUser.getCode().equals(currentUser.getCode())) {
									VertxUtils.subscribe(realm(), convo, currentUser.getCode());
									userConversations.add(convo);
								}
								/* Sending the messages recipient User BE */
								if (!linkedUser.getCode().equals(currentUser.getCode())) {
									VertxUtils.subscribe(realm(), convo, linkedUser.getCode());
									String[] senderCodeInArray = { currentUser.getCode() };
									publishData(linkedUser, senderCodeInArray);
									// bulkmsg.add(publishData(linkedUser, senderCodeInArray));
								}
							}
						}
					}
				}
				publishCmd(userConversations, "GRP_MESSAGES", "LNK_CHAT");
				// bulkmsg.add(publishCmd(userConversations, "GRP_MESSAGES", "LNK_CHAT"));
			} else {
				println("There are not chats for the current user");
			}
		} else {
			println("Unable to get the list of chats using searchBE");
		}

	}

public void makePayment(QDataAnswerMessage m) {

        String userCode = getUser().getCode();
        String begCode = null;
        Answer[] dataAnswers = m.getItems();
        for (Answer answer : dataAnswers) {
            String targetCode = answer.getTargetCode();
            String sourceCode = answer.getSourceCode();
            String attributeCode = answer.getAttributeCode();
            String value = answer.getValue();
            begCode = targetCode;
            println("Payments value ::" + value + "attribute code ::" + attributeCode);
            /* if this answer is actually an Payment_method, this rule will be triggered */
            if (attributeCode.contains("PRI_PAYMENT_METHOD")) {
                JsonObject paymentValues = new JsonObject(value);
                /* { ipAddress, deviceID, accountID } */
                String ipAddress = paymentValues.getString("ipAddress");
                String accountId = paymentValues.getString("accountID");
                String deviceId = paymentValues.getString("deviceID");
                List<Answer> userSpecificAnswers = new ArrayList<>();
                if (ipAddress != null) {
                    Answer ipAnswer = new Answer(sourceCode, userCode, "PRI_IP_ADDRESS", ipAddress);
                    userSpecificAnswers.add(ipAnswer);
                    this.baseEntity.saveAnswer(ipAnswer);
                }
                if (accountId != null) {
                    Answer accountIdAnswer = new Answer(sourceCode, begCode, "PRI_ACCOUNT_ID", accountId);
                    userSpecificAnswers.add(accountIdAnswer);
                    this.baseEntity.saveAnswer(accountIdAnswer);
                }
                if (deviceId != null) {
                    Answer deviceIdAnswer = new Answer(sourceCode, userCode, "PRI_DEVICE_ID", deviceId);
                    userSpecificAnswers.add(deviceIdAnswer);
                    this.baseEntity.saveAnswer(deviceIdAnswer);
                }
                /* bulk answer not working currently, so using individual answers */
                // saveAnswers(userSpecificAnswers);
            }
        }
        String assemblyAuthKey = PaymentUtils.getAssemblyAuthKey();
        BaseEntity userBe = getUser();
        String assemblyId = userBe.getValue("PRI_ASSEMBLY_USER_ID", null);
        if (begCode != null && assemblyId != null) {
            /* GET beg Base Entity */
            BaseEntity beg =this.baseEntity.getBaseEntityByCode(begCode);
            String offerCode = beg.getLoopValue("STT_HOT_OFFER", null);
            if (offerCode != null) {
                /* Make payment */
                showLoading("Processing payment...");
                BaseEntity offer = this.baseEntity.getBaseEntityByCode(offerCode);
                String quoterCode = offer.getLoopValue("PRI_QUOTER_CODE", null);
                BaseEntity driverBe = this.baseEntity.getBaseEntityByCode(quoterCode);

                /* make payment API */
                Boolean isMakePaymentSuccess = makePayment(userBe, driverBe, offer, beg, assemblyAuthKey);

                /* if make payment succeeds, move bucket and send notifications */
                if(isMakePaymentSuccess) {
                		/* GET attributes of OFFER BE */
                    Money offerPrice = offer.getLoopValue("PRI_OFFER_PRICE", null);
                    Money ownerPriceExcGST = offer.getLoopValue("PRI_OFFER_OWNER_PRICE_EXC_GST", null);
                    Money ownerPriceIncGST = offer.getLoopValue("PRI_OFFER_OWNER_PRICE_INC_GST", null);
                    Money driverPriceExcGST = offer.getLoopValue("PRI_OFFER_DRIVER_PRICE_EXC_GST", null);
                    Money driverPriceIncGST = offer.getLoopValue("PRI_OFFER_DRIVER_PRICE_INC_GST", null);
                    Money feePriceExcGST = offer.getLoopValue("PRI_OFFER_FEE_EXC_GST", null);
                    Money feePriceIncGST = offer.getLoopValue("PRI_OFFER_FEE_INC_GST", null);
                    List<Answer> answers = new ArrayList<Answer>();
                    answers.add(new Answer(begCode, begCode, "PRI_PRICE", JsonUtils.toJson(offerPrice)));
                    answers.add(
                            new Answer(begCode, begCode, "PRI_OWNER_PRICE_EXC_GST", JsonUtils.toJson(ownerPriceExcGST)));
                    answers.add(
                            new Answer(begCode, begCode, "PRI_OWNER_PRICE_INC_GST", JsonUtils.toJson(ownerPriceIncGST)));
                    answers.add(
                            new Answer(begCode, begCode, "PRI_DRIVER_PRICE_EXC_GST", JsonUtils.toJson(driverPriceExcGST)));
                    answers.add(
                            new Answer(begCode, begCode, "PRI_DRIVER_PRICE_INC_GST", JsonUtils.toJson(driverPriceIncGST)));
                    answers.add(new Answer(begCode, begCode, "PRI_FEE_EXC_GST", JsonUtils.toJson(feePriceExcGST)));
                    answers.add(new Answer(begCode, begCode, "PRI_FEE_INC_GST", JsonUtils.toJson(feePriceIncGST)));
                    /* Update BEG to have DRIVER_CODE as an attribute */
                    answers.add(new Answer(begCode, begCode, "STT_IN_TRANSIT", quoterCode));
                    answers.add(new Answer(begCode, begCode, "PRI_SELLER_CODE", quoterCode));
                    this.baseEntity.saveAnswers(answers);

                    BaseEntity loadBe = this.baseEntity.getLinkedBaseEntities(begCode, "LNK_BEG", "LOAD").get(0);

                    /* Allocate QUOTER as Driver */
                    this.baseEntity.createLink(begCode, quoterCode, "LNK_BEG", "DRIVER", 1.0);

                    /* Update link between BEG and Accepted OFFER to weight = 100 */
                    this.baseEntity.updateLink(begCode, offerCode, "LNK_BEG", "ACCEPTED_OFFER", 100.0);

                    /* Set PRI_NEXT_ACTION to Disabled for all other Offers */
                    // get all offers
                    List<BaseEntity> offers = this.baseEntity.getLinkedBaseEntities(begCode, "LNK_BEG", "OFFER");
                    if (offers != null) {
                        for (BaseEntity be : offers) {
                            if (!(be.getCode().equals(offerCode))) {
                                println("The BE is : " + be.getCode());
                                /* Update PRI_NEXT_ACTION to Disabled */
                                this.baseEntity.updateBaseEntityAttribute(getUser().getCode(), be.getCode(), "PRI_NEXT_ACTION", "DISABLED");
                            }
                        }
                    }
                    answers = new ArrayList<Answer>();
                    answers.add(new Answer(getUser().getCode(), begCode, "STA_" + quoterCode, Status.NEEDS_ACTION.value()));
                    answers.add(new Answer(getUser().getCode(), begCode, "STA_" + getUser().getCode(),
                            Status.NEEDS_NO_ACTION.value()));
                    /* SEND (OFFER, QUOTER, BEG) BaseEntitys to recipients */
                    String[] offerRecipients = VertxUtils.getSubscribers(realm(), offer.getCode());
                    println("OFFER subscribers   ::   " + Arrays.toString(offerRecipients));
                    publishBaseEntityByCode(userCode, begCode, "LNK_BEG", offerRecipients); /* OWNER */
                    publishBaseEntityByCode(quoterCode, begCode, "LNK_BEG", offerRecipients);
                    publishBaseEntityByCode(offerCode, begCode, "LNK_BEG", offerRecipients);
                    /* Set progression of LOAD delivery to 0 */
                    Answer updateProgressAnswer = new Answer(begCode, begCode, "PRI_PROGRESS", Double.toString(0.0));
                    answers.add(updateProgressAnswer);
                    this.baseEntity.saveAnswers(answers);
                    /* We ask FE to monitor GPS */
                    geofenceJob(begCode, getUser().getCode(), 10.0);
                    /* GET all the driver subsribers */
                    String[] begRecipients = VertxUtils.getSubscribers(realm(), "GRP_NEW_ITEMS");
                    if (begRecipients != null) {
                        println("ALL BEG subscribers   ::   " + Arrays.toString(begRecipients));
                        println("quoter code ::" + quoterCode);
                        Set<String> unsubscribeSet = new HashSet<>();
                        for (String begRecipient : begRecipients) {
                            if (!begRecipient.equals(quoterCode)) {
                                unsubscribeSet.add(begRecipient);
                            }
                        }
                        println("unsubscribe set ::" + unsubscribeSet);
                        String[] unsubscribeArr = new String[unsubscribeSet.size()];
                        int i = 0;
                        for (String code : unsubscribeSet) {
                            unsubscribeArr[i++] = code;
                        }
                        /* sending cmd BUCKETVIEW */
                        this.redirectToHomePage();
                        println("unsubscribe arr ::" + Arrays.toString(unsubscribeArr));
                        VertxUtils.unsubscribe(realm(), "GRP_NEW_ITEMS", unsubscribeSet);
                    }
                    // moveBaseEntity(begCode, "GRP_NEW_ITEMS", "GRP_APPROVED", "LNK_CORE");
                    this.baseEntity.moveBaseEntitySetLinkValue(begCode, "GRP_NEW_ITEMS", "GRP_APPROVED", "LNK_CORE", "BEG");
                    publishBaseEntityByCode(begCode, "GRP_APPROVED", "LNK_CORE", offerRecipients);

                    this.reloadCache();

                    /* Update PRI_NEXT_ACTION = OWNER */
                    Answer begNextAction = new Answer(userCode, offerCode, "PRI_NEXT_ACTION", "NONE");
                    this.baseEntity.saveAnswer(begNextAction);
                    /* sending cmd BUCKETVIEW */
                    // this.setState("TRIGGER_HOMEPAGE");
                    this.redirectToHomePage();

                    /* TOAST :: SUCCESS */
                    println("Sending success toast since make payment succeeded");
                    HashMap<String, String> contextMap = new HashMap<String, String>();
                    contextMap.put("DRIVER", quoterCode);
                    contextMap.put("JOB", begCode);
                    contextMap.put("QUOTER", quoterCode);
                    contextMap.put("OFFER", offer.getCode());
                    contextMap.put("LOAD", loadBe.getCode());
                    String[] recipientArr = { userCode };
                    /* TOAST :: PAYMENT SUCCESS */
                    sendMessage("", recipientArr, contextMap, "MSG_CH40_MAKE_PAYMENT_SUCCESS", "TOAST");
                    sendMessage("", recipientArr, contextMap, "MSG_CH40_CONFIRM_QUOTE_OWNER", "EMAIL");
                    /* QUOTER config */
                    HashMap<String, String> contextMapForDriver = new HashMap<String, String>();
                    contextMapForDriver.put("JOB", begCode);
                    contextMapForDriver.put("OWNER", userCode);
                    contextMapForDriver.put("OFFER", offer.getCode());
                    contextMapForDriver.put("LOAD", loadBe.getCode());
                    String[] recipientArrForDriver = { quoterCode };
                    /* Sending messages to DRIVER - Email and sms enabled */
                    sendMessage("", recipientArrForDriver, contextMapForDriver, "MSG_CH40_CONFIRM_QUOTE_DRIVER", "TOAST");
                    sendMessage("", recipientArrForDriver, contextMapForDriver, "MSG_CH40_CONFIRM_QUOTE_DRIVER", "SMS");
                    sendMessage("", recipientArrForDriver, contextMapForDriver, "MSG_CH40_CONFIRM_QUOTE_DRIVER", "EMAIL");
                }
            }
        }
    }

	public void updateGPS(QDataGPSMessage m) {

		GPS driverPosition = m.getItems()[0];
		Double driverLatitude = driverPosition.getLatitude();
		Double driverLongitude = driverPosition.getLongitude();

		if (driverLatitude != null && driverLongitude != null) {

			if (getUser() == null) {
				String username = (String) getDecodedTokenMap().get("preferred_username");
				String code = "PER_" + QwandaUtils.getNormalisedUsername(username).toUpperCase();

				log.error("Code is not in Database " + code);
				return;
			}
			try {

				List<BaseEntity> jobsInTransit = this.baseEntity.getBaseEntitysByAttributeAndValue("STT_IN_TRANSIT",
						getUser().getCode());
				if (!jobsInTransit.isEmpty()) {
					println("###### GPS: for user " + getUser().getCode() + ":" + m);
					println(jobsInTransit.toString());
				}
				for (BaseEntity be : jobsInTransit) {

					Double deliveryLatitude = be.getValue("PRI_DROPOFF_ADDRESS_LATITUDE", 0.0);
					Double deliveryLongitude = be.getValue("PRI_DROPOFF_ADDRESS_LONGITUDE", 0.0);
					Double totalDistance = be.getValue("PRI_TOTAL_DISTANCE_M", 0.0);

					/* Call Google Maps API to know how far the driver is */
					Double distance = GPSUtils.getDistance(driverLatitude, driverLongitude, deliveryLatitude,
							deliveryLongitude);
					Double percentage = 100.0 * (totalDistance - distance) / (totalDistance);
					percentage = percentage < 0 ? 0 : percentage;

					/* update position of the beg */
					List<Answer> answers = new ArrayList<Answer>();
					answers.add(new Answer(be.getCode(), be.getCode(), "PRI_PROGRESS", percentage.toString()));

					answers.add(new Answer(be.getCode(), be.getCode(), "PRI_POSITION_LATITUDE", driverLatitude + ""));
					answers.add(new Answer(be.getCode(), be.getCode(), "PRI_POSITION_LONGITUDE", driverLongitude + ""));
					this.baseEntity.saveAnswers(answers);
				}
			} catch (NumberFormatException e) {
				println("GPS Error " + m);
			}
		}

	}

	/* Send Mobile Verification Code */
	public void sendMobileVerificationPasscode(final String userCode) {

		String[] recipients = { userCode };
		String verificationCode = generateVerificationCode();
		if ("TRUE".equalsIgnoreCase(System.getenv("DEV_MODE"))) {
			println("The verification code is ::" + verificationCode);
		}

		Answer verificationCodeAns = new Answer(userCode, userCode, "PRI_VERIFICATION_CODE", verificationCode);
		this.baseEntity.saveAnswer(verificationCodeAns);

		HashMap<String, String> contextMap = new HashMap<String, String>();
		contextMap.put("USER", userCode);

		println("The String Array is ::" + Arrays.toString(recipients));

		/* Sending sms message to user */
		sendMessage("", recipients, contextMap, "GNY_USER_VERIFICATION", "SMS");

	}

	/* Generate 4 digit random passcode */
	public String generateVerificationCode() {
		return String.format("%04d", (new Random()).nextInt(10000));
	}

	/* Verify the user entered passcode with the one in DB */
	public boolean verifyPassCode(final String userCode, final String userPassCode) {

		println("The Passcode in DB is ::"
				+ Integer.parseInt(this.baseEntity.getBaseEntityValueAsString(userCode, "PRI_VERIFICATION_CODE")));
		println("User Entered Passcode is ::" + Integer.parseInt(userPassCode));

		if (this.baseEntity.getBaseEntityValueAsString(userCode, "PRI_VERIFICATION_CODE").equals(userPassCode)) {
			return true;
		} else
			return false;
	}

	public boolean didUserVerifyPhoneNumber() {

		String enteredCode = this.getUser().getLoopValue("PRI_VERIFICATION_CODE_USER", null);
		String realCode = this.getUser().getLoopValue("PRI_VERIFICATION_CODE", null);
		Boolean enteredRightPasscode = false;
		if (enteredCode != null && realCode != null && enteredCode.equals(realCode)) {
			enteredRightPasscode = true;
		}

		return enteredRightPasscode;
	}

	public void clearBaseEntityAttr(String userCode) {
		BaseEntity be = this.baseEntity.getBaseEntityByCode(userCode);
		println("be   ::   " + be);

		Set<EntityAttribute> attributes = be.getBaseEntityAttributes();
		println("Size all   ::   " + attributes.size());
		Set<EntityAttribute> removeAttributes = new HashSet<EntityAttribute>();

		for (EntityAttribute attribute : attributes) {

			switch (attribute.getAttributeCode()) {

			case "PRI_UUID":
			case "PRI_USERNAME":
			case "FBK_ID":
				break;
			default:
				removeAttributes.add(attribute);
			}

		}
		println("before removing   ::   " + attributes.toString());
		println("Size toRemove   ::   " + removeAttributes.size());
		println("Removing attrs   ::   " + removeAttributes.toString());
		attributes.removeAll(removeAttributes);
		println("after removing   ::   " + attributes.toString());
		println("Size afterRemoved   ::   " + attributes.size());

		be.setBaseEntityAttributes(attributes);

		QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(be);
		beMsg.setDelete(true);
		publishCmd(beMsg);

		be.setBaseEntityAttributes(attributes);

		String jsonBE = JsonUtils.toJson(be);
		String result = null;
		System.out.println("Forcing BE update to " + qwandaServiceUrl + "/qwanda/baseentitys/force");
		try {
			result = QwandaUtils.apiPutEntity(qwandaServiceUrl + "/qwanda/baseentitys/force", jsonBE, getToken());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("The result   ::  " + result);

	}

	/* sets delete field to true so that FE removes the BE from their store */
	public void clearBaseEntity(String baseEntityCode, String[] recipients) {
		BaseEntity be = this.baseEntity.getBaseEntityByCode(baseEntityCode);
		QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(be);
		beMsg.setDelete(true);
		publishData(beMsg, recipients);

	}

	/* sets delete field to true so that FE removes the BE from their store */
	public void fastClearBaseEntity(String baseEntityCode, String[] recipients) {
		BaseEntity be = new BaseEntity(baseEntityCode, "FastBE");
		QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(be);
		beMsg.setDelete(true);
		publishData(beMsg, recipients);

	}

	public void acceptJob(QEventBtnClickMessage m) {

		/* Get beg.getCode(), username, userCode, userFullName */
		BaseEntity beg = this.baseEntity.getBaseEntityByCode(m.getItemCode()); // Get Baseentity once so we don't need to keep
		// fetching...
		println("beg.getCode()  ::   " + beg.getCode());

		String userCode = getUser().getCode();
		println("usercode   ::   " + getUser().getCode());
		String userName = getAsString("preferred_username");
		println("username   ::   " + userName);
		String userFullName = getFullName(getUser());
		println("user fullName   ::   " + userFullName);

		String linkCode = "LNK_BEG";
		String linkOffer = "OFFER";
		String linkOwner = "OWNER";
		String linkCreator = "CREATOR";

		Optional<String> optOwnerCode = beg.getValue("PRI_AUTHOR");
		String ownerCode = null;

		if (optOwnerCode.isPresent()) {
			ownerCode = optOwnerCode.get();
		} else {
			ownerCode = QwandaUtils.getSourceOrTargetForGroupLink("GRP_NEW_ITEMS", linkCode, beg.getCode(), linkOwner,
					false, getToken());
		}

		/* get BEG PRICEs */
		println("BEG Prices   ::   ");

		Money begPrice = beg.getLoopValue("PRI_PRICE", Money.of(0.00, "AUD"));
		Money ownerPriceExcGST = beg.getLoopValue("PRI_OWNER_PRICE_EXC_GST", Money.of(0.00, "AUD"));
		Money ownerPriceIncGST = beg.getLoopValue("PRI_OWNER_PRICE_INC_GST", Money.of(0.00, "AUD"));
		Money driverPriceExcGST = beg.getLoopValue("PRI_DRIVER_PRICE_EXC_GST", Money.of(0.00, "AUD"));
		Money driverPriceIncGST = beg.getLoopValue("PRI_DRIVER_PRICE_INC_GST", Money.of(0.00, "AUD"));
		Money feePriceExcGST = beg.getLoopValue("PRI_FEE_EXC_GST", Money.of(0.00, "AUD"));
		Money feePriceIncGST = beg.getLoopValue("PRI_FEE_INC_GST", Money.of(0.00, "AUD"));

		/* Create Offer BE */
		BaseEntity offer = this.baseEntity.create(getUser().getCode(), "OFR", "Offer");
		println("OFFER CODE   ::   " + offer.getCode());
		RulesUtils.ruleLogger("OFFER Base Entity", offer);

		/* Send beg to driver and owner should see it as part of beg link */
		VertxUtils.subscribe(realm(), offer, getUser().getCode());
		VertxUtils.subscribe(realm(), offer, ownerCode);

		/* Save attributes for OFFER as answer */
		List<Answer> answerList = new ArrayList<Answer>();
		answerList.add(new Answer(getUser(), offer, "PRI_OFFER_PRICE", JsonUtils.toJson(begPrice)));
		answerList
				.add(new Answer(getUser(), offer, "PRI_OFFER_OWNER_PRICE_EXC_GST", JsonUtils.toJson(ownerPriceExcGST)));
		/*
		 * answerList .add(new Answer(getUser(), offer, "PRI_OFFER_OWNER_PRICE_INC_GST",
		 * JsonUtils.toJson(ownerPriceIncGST)));
		 */
		Answer owIncGST = new Answer(getUser(), offer, "PRI_OFFER_OWNER_PRICE_INC_GST",
				JsonUtils.toJson(ownerPriceIncGST));
		owIncGST.setChangeEvent(false);
		answerList.add(owIncGST);

		answerList.add(
				new Answer(getUser(), offer, "PRI_OFFER_DRIVER_PRICE_EXC_GST", JsonUtils.toJson(driverPriceExcGST)));
		answerList.add(
				new Answer(getUser(), offer, "PRI_OFFER_DRIVER_PRICE_INC_GST", JsonUtils.toJson(driverPriceIncGST)));
		answerList.add(new Answer(getUser(), offer, "PRI_OFFER_FEE_EXC_GST", JsonUtils.toJson(feePriceExcGST)));
		answerList.add(new Answer(getUser(), offer, "PRI_OFFER_FEE_INC_GST", JsonUtils.toJson(feePriceIncGST)));

		answerList.add(new Answer(getUser(), offer, "PRI_OFFER_CODE", offer.getCode()));
		answerList.add(new Answer(getUser(), offer, "PRI_QUOTER_CODE", getUser().getCode()));
		answerList.add(new Answer(getUser(), offer, "PRI_QUOTER_USERNAME", userName));
		answerList.add(new Answer(getUser(), offer, "PRI_QUOTER_FULLNAME", userFullName));
		answerList.add(new Answer(getUser(), offer, "PRI_BEG_CODE", beg.getCode()));
		answerList.add(new Answer(getUser(), offer, "PRI_NEXT_ACTION", linkOwner));
		answerList.add(new Answer(getUser(), offer, "PRI_OFFER_DATE", getCurrentLocalDateTime()));

		this.baseEntity.saveAnswers(answerList);
		/* Update the number of offers for BEG */
		Integer offerCount = beg.getLoopValue("PRI_OFFER_COUNT", 0);
		offerCount = offerCount + 1;
		println("Offer Count is   ::   " + offerCount);

		this.baseEntity.saveAnswer(new Answer(beg.getCode(), beg.getCode(), "PRI_OFFER_COUNT", offerCount.toString()));

		/* Determine the recipient code */
		String[] recipients = VertxUtils.getSubscribers(realm(), beg.getCode());
		println("BEG subscribers   ::   " + Arrays.toString(recipients));

		/* link BEG and OFFER BE || OFFER */
		this.baseEntity.createLink(beg.getCode(), offer.getCode(), linkCode, linkOffer, 1.0);

		/* link OFFER and QUOTER BE || CREATOR */
		this.baseEntity.createLink(offer.getCode(), getUser().getCode(), "LNK_OFR", linkCreator, 1.0);

		/* set Status of the job */
		/* get Owner of the job */
		BaseEntity owner = this.baseEntity.getLinkedBaseEntities(beg.getCode(), "LNK_BEG", "OWNER").get(0);
		// updateBaseEntityAttribute(getUser().getCode(), beg.getCode(), "STA_STATUS",
		// "#FFA500");
		answerList = new ArrayList<Answer>();
		answerList.add(new Answer(getUser().getCode(), beg.getCode(), "STA_" + getUser().getCode(),
				Status.NEEDS_ACTION.value()));
		answerList.add(
				new Answer(getUser().getCode(), beg.getCode(), "STA_" + owner.getCode(), Status.NEEDS_ACTION.value()));
		this.baseEntity.saveAnswers(answerList);
		/* SEND (OFFER, QUOTER, BEG) BaseEntitys to recipients */
		String[] offerRecipients = VertxUtils.getSubscribers(realm(), offer.getCode());
		println("OFFER subscribers   ::   " + Arrays.toString(offerRecipients));

		publishBaseEntityByCode(offer.getCode(), beg.getCode(), "LNK_BEG", offerRecipients);
		publishBaseEntityByCode(getUser().getCode(), beg.getCode(), "LNK_BEG", offerRecipients);
		publishBaseEntityByCode(beg.getCode(), "GRP_NEW_ITEMS", "LNK_CORE", offerRecipients);

		/* Messages */

		/* OWNER config */
		HashMap<String, String> contextMap = new HashMap<String, String>();
		contextMap.put("QUOTER", getUser().getCode());

		contextMap.put("JOB", beg.getCode());
		contextMap.put("OFFER", offer.getCode());

		RulesUtils.println("owner code ::" + ownerCode);
		String[] recipientArr = { ownerCode };

		/* Sending toast message to owner frontend */
		sendMessage("", recipientArr, contextMap, "MSG_CH40_ACCEPT_QUOTE_OWNER", "TOAST");

		/* QUOTER config */
		HashMap<String, String> contextMapForDriver = new HashMap<String, String>();
		contextMapForDriver.put("JOB", beg.getCode());
		contextMapForDriver.put("OWNER", ownerCode);
		contextMapForDriver.put("OFFER", offer.getCode());
		contextMapForDriver.put("QUOTER", getUser().getCode());

		String[] recipientArrForDriver = { getUser().getCode() };

		/* Sending toast message to driver frontend */
		sendMessage("", recipientArrForDriver, contextMapForDriver, "MSG_CH40_ACCEPT_QUOTE_DRIVER", "TOAST");
	}

	public void processLoadTypeAnswer(QEventAttributeValueChangeMessage m) {
		/* Collect load code from answer */
		Answer answer = m.getAnswer();
		println("The created value  ::  " + answer.getCreatedDate());
		println("Answer from QEventAttributeValueChangeMessage  ::  " + answer.toString());
		String targetCode = answer.getTargetCode();
		String sourceCode = answer.getSourceCode();
		String loadCategoryCode = answer.getValue();
		String attributeCode = m.data.getCode();
		println("The target BE code is   ::  " + targetCode);
		println("The source BE code is   ::  " + sourceCode);
		println("The attribute code is   ::  " + attributeCode);
		println("The load type code is   ::  " + loadCategoryCode);

		BaseEntity loadType = this.baseEntity.getBaseEntityByCode(loadCategoryCode, false); // no attributes

		/* creating new Answer */
		Answer newAnswer = new Answer(answer.getSourceCode(), answer.getTargetCode(), "PRI_LOAD_TYPE",
				loadType.getName());
		newAnswer.setInferred(true);

		this.baseEntity.saveAnswer(newAnswer);
	}

	public void sendRating(String data) throws ClientProtocolException, IOException {

		if (data != null) {

			JsonObject dataJson = new JsonObject(data);
			String begCode = dataJson.getString("itemCode");
			String userCode = getUser().getCode();
			String driverCode = null;

			/* we get all the linked BEs to beg */
			List<Link> links = this.baseEntity.getLinks(begCode, "LNK_BEG");
			if (links != null) {

				for (Link link : links) {

					String linkValue = link.getLinkValue();
					if (linkValue != null && linkValue.equals("DRIVER")) {
						driverCode = link.getTargetCode();
					}
				}
			}

			if (begCode != null && driverCode != null) {

				/*
				 * we save the BEG code as an attribute in order to track what job a driver is
				 * being currently rated against. if this comment does not make sense please
				 * refer to the french man.
				 */
				this.baseEntity.updateBaseEntityAttribute(userCode, userCode, "STT_JOB_IS_RATING", begCode);

				/* we send the questions */
				sendQuestions(userCode, driverCode, "QUE_USER_RATING_GRP", true);

				/* we send the layout */
				sendSublayout("driver-rating", "rate-driver.json", driverCode, true);
			}
		}
	}

	public void fireAttributeChanges(QEventAttributeValueChangeMessage m) {
		Answer a = m.getAnswer();
		BaseEntity be = m.getBe();
		for (EntityAttribute ea : be.getBaseEntityAttributes()) {
			Answer pojo = new Answer(a.getSourceCode(), a.getTargetCode(), ea.getAttributeCode(), ea.getAsLoopString());
			pojo.setWeight(ea.getWeight());
			pojo.setInferred(ea.getInferred());
			pojo.setExpired(a.getExpired());
			pojo.setRefused(a.getRefused());
			// pojo.setAskId(answer.getAskId());

			QEventAttributeValueChangeMessage msg = new QEventAttributeValueChangeMessage(pojo, m.getOldValue(),
					m.getToken());
			msg.getData().setCode(ea.getAttributeCode());
			msg.getData().setId(-1L);
			msg.setBe(be);
			publish("events", JsonUtils.toJson(msg));
		}
	}

	public void saveJob(BaseEntity job) {

		String jobCode = job.getCode();
		/*
		 * We create a new attribute "PRI_TOTAL_DISTANCE" for this BEG. TODO: should be
		 * triggered in another rule
		 */
		Double pickupLatitude = job.getValue("PRI_PICKUP_ADDRESS_LATITUDE", 0.0);
		Double pickupLongitude = job.getValue("PRI_PICKUP_ADDRESS_LONGITUDE", 0.0);
		Double deliveryLatitude = job.getValue("PRI_DROPOFF_ADDRESS_LATITUDE", 0.0);
		Double deliveryLongitude = job.getValue("PRI_DROPOFF_ADDRESS_LONGITUDE", 0.0);

		/* Add author to the load */
		List<Answer> answers = new ArrayList<Answer>();
		answers.add(new Answer(getUser().getCode(), jobCode, "PRI_POSITION_LATITUDE", pickupLatitude + ""));
		answers.add(new Answer(getUser().getCode(), jobCode, "PRI_POSITION_LONGITUDE", pickupLongitude + ""));

		Double totalDistance = GPSUtils.getDistance(pickupLatitude, pickupLongitude, deliveryLatitude,
				deliveryLongitude);
		if (totalDistance > 0) {
			Answer totalDistanceAnswer = new Answer(jobCode, jobCode, "PRI_TOTAL_DISTANCE_M", totalDistance + "");
			answers.add(totalDistanceAnswer);
		}

		/* Adding Offer Count to 0 */
		Answer offerCountAns = new Answer(getUser().getCode(), jobCode, "PRI_OFFER_COUNT", "0");
		/* Publish Answer */
		answers.add(offerCountAns);

		/* set Status of the job */
		answers.add(new Answer(getUser().getCode(), jobCode, "STA_STATUS", Status.NEEDS_NO_ACTION.value()));
		// Setting color to green for new jobs for both driver and owner
		/*
		 * answers.add(new Answer(getUser().getCode(), jobCode, "STA_" +
		 * getUser().getCode(), Status.NEEDS_NO_ACTION.value()));
		 */

		BaseEntity updatedJob = this.baseEntity.getBaseEntityByCode(job.getCode());
		Long jobId = updatedJob.getId();
		answers.add(new Answer(getUser().getCode(), jobCode, "PRI_JOB_ID", jobId + ""));
		this.baseEntity.saveAnswers(answers);

		/* Determine the recipient code */
		String[] recipientCodes = VertxUtils.getSubscribers(realm(), "GRP_NEW_ITEMS");
		println("Recipients for Job/Load " + Arrays.toString(recipientCodes));

		/*
		 * Send newly created job with its attributes to all drivers so that it exists
		 * before link change
		 */
		BaseEntity newJobDetails = this.baseEntity.getBaseEntityByCode(jobCode);
		println("The newly submitted Job details     ::     " + newJobDetails.toString());
		publishData(newJobDetails, recipientCodes);
		/* publishing to Owner */
		publishBE(newJobDetails);

		/* Moving the BEG to GRP_NEW_ITEMS */
		/*
		 * The moveBaseEntity without linkValue sets the linkValue to default value,
		 * "LINK". So using moveBaseEntitySetLinkValue()
		 */
		this.baseEntity.moveBaseEntitySetLinkValue(jobCode, "GRP_DRAFTS", "GRP_NEW_ITEMS", "LNK_CORE", "BEG");

		/* Get the sourceCode(Company code) for this User */
		BaseEntity company = this.baseEntity.getParent(getUser().getCode(), "LNK_STAFF");

		/* link newly created Job to GRP_LOADS */
		BaseEntity load = this.baseEntity.getLinkedBaseEntities(jobCode, "LNK_BEG", "LOAD").get(0);
		String loadCode = load.getCode();
		Link newLoadLinkToLoadList = QwandaUtils.createLink("GRP_LOADS", loadCode, "LNK_LOAD", company.getCode(),
				(double) 1, getToken());
		println("The load has been added to the GRP_LOADS ");

		QEventLinkChangeMessage msgLnkBegLoad = new QEventLinkChangeMessage(
				new Link(jobCode, load.getCode(), "LNK_BEG"), null, getToken());
		publishData(msgLnkBegLoad, recipientCodes);

		publishBaseEntityByCode(jobCode, "GRP_NEW_ITEMS", "LNK_CORE", recipientCodes);
		/* SEND LOAD BE */
		publishBaseEntityByCode(loadCode, jobCode, "LNK_BEG", recipientCodes);
		/* publishing to Owner */
		publishBE(this.baseEntity.getBaseEntityByCode(jobCode));
		publishBE(this.baseEntity.getBaseEntityByCode(loadCode));

		if (!newJobDetails.getValue("PRI_JOB_IS_SUBMITTED", false)) {

			/* Sending Messages */

			println("new job");

			HashMap<String, String> contextMap = new HashMap<String, String>();
			contextMap.put("JOB", jobCode);
			contextMap.put("OWNER", getUser().getCode());

			println("The String Array is ::" + Arrays.toString(recipientCodes));

			/* Getting all people */
			List<BaseEntity> people = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_PEOPLE", "LNK_CORE", 0, 100, false);
			System.out.println("size ::" + people.size());
			List<BaseEntity> sellersBe = new ArrayList<>();

			/* Getting all driver BEs */
			for (BaseEntity stakeholderBe : people) {

				try {

					if (this.isUserSeller(stakeholderBe)) {
						sellersBe.add(stakeholderBe);
					}

				} catch (Exception e) {

				}
			}

			int i = 0;
			String[] stakeholderArr = new String[sellersBe.size()];
			for (BaseEntity stakeholderBe : sellersBe) {
				stakeholderArr[i] = stakeholderBe.getCode();
				i++;
			}

			println("recipient array - drivers ::" + Arrays.toString(stakeholderArr));

			/* Sending toast message to owner frontend */
			sendMessage("", stakeholderArr, contextMap, "MSG_CH40_NEW_JOB_POSTED", "TOAST");

			/* Sending message to BEG OWNER */
			sendMessage("", stakeholderArr, contextMap, "MSG_CH40_NEW_JOB_POSTED", "EMAIL");

		}

	     this.reloadCache();
	}

	public void listenAttributeChange(QEventAttributeValueChangeMessage m) {
		// if ((m.getData() != null)&&(m.getData().getCode()!=null)) {
		// println(m.getData().getCode());
		// }
		if ((m.getData() != null) && ("MULTI_EVENT".equals(m.getData().getCode()))) {
			/* rules.publishData(new QDataAnswerMessage($m.getAnswer())); */
			String[] recipientCodes = getRecipientCodes(m);
			println(m);
			this.baseEntity.addAttributes(m.getBe());
			publishBE(m.getBe(), recipientCodes);
			setState("ATTRIBUTE_CHANGE2");
			fireAttributeChanges(m);
		} else if ((m.getData() != null) && (m.getData().getCode() != null)) {
			/* publishData(new QDataAnswerMessage(m.getAnswer())); */
			String[] recipientCodes = getRecipientCodes(m);
			println(m);
			this.baseEntity.addAttributes(m.getBe());
			publishBE(m.getBe(), recipientCodes);
			setState("ATTRIBUTE_CHANGE2");
		}
	}

	public boolean hasRole(final String role) {

		LinkedHashMap rolesMap = (LinkedHashMap) getDecodedTokenMap().get("realm_access");
		if (rolesMap != null) {

			try {

				Object rolesObj = rolesMap.get("roles");
				if (rolesObj != null) {
					ArrayList roles = (ArrayList) rolesObj;
					if (roles.contains(role)) {
						return true;
					}
				}
			} catch (Exception e) {
			}
		}

		return false;
	}

	public String getZonedCurrentLocalDateTime() {

		LocalDateTime ldt = LocalDateTime.now();
		ZonedDateTime zdt = ldt.atZone(ZoneOffset.systemDefault());
		String iso8601DateString = ldt.toString(); // zdt.toString(); MUST USE UMT!!!!

		System.out.println("datetime ::" + iso8601DateString);

		return iso8601DateString;

	}

	public void sendCmdView(final String viewType, final String parentCode) {

		QCmdMessage cmdView = new QCmdMessage("CMD_VIEW", viewType);
		JsonObject cmdViewJson = JsonObject.mapFrom(cmdView);
		cmdViewJson.put("root", parentCode);
		cmdViewJson.put("token", getToken());

		publish("cmds", cmdViewJson);
	}

	/*
	 * Chat Message:- Send cmd_msg SPLIT_VIEW for the chat message display
	 */
	public void sendCmdSplitView(final String parentCode, final String chatCode) {
		QCmdMessage cmdView = new QCmdMessage("CMD_VIEW", "SPLIT_VIEW");
		JsonObject cmdViewJson = JsonObject.mapFrom(cmdView);

		JsonObject codeListView = new JsonObject();
		codeListView.put("code", "MESSAGE_VIEW");
		codeListView.put("root", parentCode);
		// Adding selectedItem info to make this chat selected
		if (chatCode == null || chatCode.isEmpty()) {
			codeListView.put("selectedItem", "null");
		} else
			codeListView.put("selectedItem", chatCode);
		JsonObject convListView = new JsonObject();
		convListView.put("code", "CONVERSATION_VIEW");
		if (chatCode == null || chatCode.isEmpty()) {
			convListView.put("root", "null");
		} else
			convListView.put("root", chatCode);

		JsonArray msgCodes = new JsonArray();
		msgCodes.add(codeListView);
		msgCodes.add(convListView);
		System.out.println("The JsonArray is :: " + msgCodes);
		cmdViewJson.put("root", msgCodes);
		cmdViewJson.put("token", getToken());
		System.out.println(" The cmd msg is :: " + cmdViewJson);

		publishCmd(cmdViewJson);
	}

	/*
	 * Publish all the messages that belongs to the given chat
	 */
	public void sendChatMessages(final String chatCode, final int pageStart, final int pageSize) {
		// publishBaseEntitysByParentAndLinkCodeWithAttributes(chatCode, "LNK_MESSAGES",
		// 0, 100, true);

		SearchEntity sendAllMsgs = new SearchEntity("SBE_CHATMSGS", "Chat Messages").addColumn("PRI_MESSAGE", "Message")
				.addColumn("PRI_CREATOR", "Creater ID").setSourceCode(chatCode)
				.setSourceStakeholder(getUser().getCode()).addSort("PRI_CREATED", "Created", SearchEntity.Sort.DESC)
				.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "MSG_%").setPageStart(pageStart)
				.setPageSize(pageSize);

		try {
			sendSearchResults(sendAllMsgs);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Error! Unable to get Search Rsults");
			e.printStackTrace();
		}

	}

	public void sendTableViewWithHeaders(final String parentCode, JsonArray columnHeaders) {
		QCmdMessage cmdView = new QCmdMessage("CMD_VIEW", "TABLE_VIEW");
		// JsonArray columnsArray = new JsonArray();

		JsonObject cmdViewJson = JsonObject.mapFrom(cmdView);
		cmdViewJson.put("root", parentCode);
		cmdViewJson.put("token", getToken());
		cmdViewJson.put("columns", columnHeaders);
		publish("cmds", cmdViewJson);
	}

	/* Search the text value in all jobs */
	public void sendAllUsers(String searchBeCode) throws ClientProtocolException, IOException {
		println("Get All Users - The search BE is  :: " + searchBeCode);
		BaseEntity searchBE = new BaseEntity(searchBeCode, "Get All Users");
		JsonArray columnsArray = new JsonArray();
		JsonObject columns = new JsonObject();
		AttributeText attributeTextImage = new AttributeText("COL_PRI_IMAGE_URL", "Image");
		JsonObject image = new JsonObject();
		image.put("code", "PRI_IMAGE_URL");
		columnsArray.add(image);
		AttributeText attributeTextUserName = new AttributeText("COL_PRI_USERNAME", "User Name");
		JsonObject userName = new JsonObject();
		userName.put("code", "PRI_USERNAME");
		columnsArray.add(userName);
		AttributeText attributeTextFirstName = new AttributeText("COL_PRI_FIRSTNAME", "First Name");
		JsonObject firstName = new JsonObject();
		firstName.put("code", "PRI_FIRSTNAME");
		columnsArray.add(firstName);
		AttributeText attributeTextLastName = new AttributeText("COL_PRI_LASTNAME", "Last Name");
		JsonObject lastName = new JsonObject();
		lastName.put("code", "PRI_LASTNAME");
		columnsArray.add(lastName);
		AttributeText attributeTextMobile = new AttributeText("COL_PRI_MOBILE", "Mobile Number");
		JsonObject mobile = new JsonObject();
		mobile.put("code", "PRI_MOBILE");
		columnsArray.add(mobile);
		AttributeText attributeTextEmail = new AttributeText("COL_PRI_EMAIL", "Email");
		JsonObject email = new JsonObject();
		email.put("code", "PRI_EMAIL");
		columnsArray.add(email);
		println("The columnsArray is ::" + columnsArray);
		// Sort Attribute
		AttributeText attributeTextSortFirstName = new AttributeText("SRT_PRI_FIRSTNAME", "Sort By FirstName");

		// Pagination Attribute
		AttributeInteger attributePageStart = new AttributeInteger("SCH_PAGE_START", "PageStart");
		AttributeInteger attributePageSize = new AttributeInteger("SCH_PAGE_SIZE", "PageSize");

		try {
			searchBE.addAttribute(attributeTextImage, 10.0);
			searchBE.addAttribute(attributeTextUserName, 9.0);
			searchBE.addAttribute(attributeTextFirstName, 8.0);
			searchBE.addAttribute(attributeTextLastName, 7.0);
			searchBE.addAttribute(attributeTextMobile, 6.0);
			searchBE.addAttribute(attributeTextEmail, 5.0);
			searchBE.addAttribute(attributeTextSortFirstName, 4.0, "ASC");
			searchBE.addAttribute(attributePageStart, 3.0, "0");
			searchBE.addAttribute(attributePageSize, 2.0, "20");
		} catch (BadDataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// }else {
		// searchBE = getBaseEntityByCodeWithAttributes(searchBeCode);
		// }
		println("The search BE is  :: " + searchBE);
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String result = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());
		System.out.println("The result   ::  " + result);
		publishData(new JsonObject(result));
		sendTableViewWithHeaders("SBE_GET_ALL_USERS", columnsArray);
		// sendCmdView("TABLE_VIEW", "SBE_GET_ALL_USERS" );
		// publishCmd(result, grpCode, "LNK_CORE");

	}

	/* Get All the jobs */
	public void sendAllLoads(String searchBeCode) throws ClientProtocolException, IOException {
		// println("Get all Loads - The search BE is :: "+searchBeCode );
		BaseEntity searchBE = new BaseEntity(searchBeCode, "Get All Loads");
		// BaseEntity searchBE;
		JsonArray columnsArray = new JsonArray();
		JsonObject columns = new JsonObject();
		// Creating attributes
		AttributeText attributeBECode = new AttributeText("PRI_CODE", "LIKE");
		JsonObject beCode = new JsonObject();
		beCode.put("code", "PRI_CODE");
		AttributeText attributeTextName = new AttributeText("COL_PRI_NAME", "Load Name");
		JsonObject name = new JsonObject();
		name.put("code", "PRI_NAME");
		columnsArray.add(name);
		AttributeText attributeTextJobId = new AttributeText("COL_PRI_JOB_ID", "Job ID");
		JsonObject description = new JsonObject();
		description.put("code", "PRI_JOB_ID");
		columnsArray.add(description);
		AttributeText attributeTextPickupAddress = new AttributeText("COL_PRI_PICKUP_ADDRESS_FULL", "Pickup Address");
		JsonObject pickUpAddress = new JsonObject();
		pickUpAddress.put("code", "PRI_PICKUP_ADDRESS_FULL");
		columnsArray.add(pickUpAddress);
		AttributeMoney attributeDescription = new AttributeMoney("COL_PRI_DESCRIPTION", "Description");
		JsonObject ownerPrice = new JsonObject();
		ownerPrice.put("code", "PRI_DESCRIPTION");
		columnsArray.add(ownerPrice);

		// Sort Attribute
		AttributeText attributeTextSortName = new AttributeText("SRT_PRI_NAME", "Sort By Name");

		// Pagination Attribute
		AttributeInteger attributePageStart = new AttributeInteger("SCH_PAGE_START", "PageStart");
		AttributeInteger attributePageSize = new AttributeInteger("SCH_PAGE_SIZE", "PageSize");

		try {
			searchBE.addAttribute(attributeBECode, 10.0, "BEG_%%");
			searchBE.addAttribute(attributeTextName, 9.0);
			searchBE.addAttribute(attributeTextJobId, 8.0);
			searchBE.addAttribute(attributeTextPickupAddress, 7.0);
			// searchBE.addAttribute(attributeTextDropOffAddress, 6.0);
			searchBE.addAttribute(attributeDescription, 5.0);
			// searchBE.addAttribute(attributeDriverPrice, 4.0);
			searchBE.addAttribute(attributeTextSortName, 3.0, "ASC");
			searchBE.addAttribute(attributePageStart, 2.0, "0");
			searchBE.addAttribute(attributePageSize, 1.0, "500");

		} catch (BadDataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// println("The search BE is :: "+JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String loadsList = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());
		// System.out.println("The result :: "+loadsList);
		publishData(new JsonObject(loadsList));
		sendTableViewWithHeaders("SBE_GET_ALL_LOADS", columnsArray);
	}

	// Search and send all the Drivers
	public void sendAllDrivers(String searchBeCode) throws ClientProtocolException, IOException {
		println("Get All Drivers - The search BE is  :: " + searchBeCode);
		BaseEntity searchBE = new BaseEntity(searchBeCode, "Get All Drivers");

		JsonArray columnsArray = new JsonArray();
		JsonObject columns = new JsonObject();
		AttributeText attributeTextImage = new AttributeText("COL_PRI_IMAGE_URL", "Image");
		JsonObject image = new JsonObject();
		image.put("code", "PRI_IMAGE_URL");
		columnsArray.add(image);
		AttributeText attributeTextUserName = new AttributeText("COL_PRI_USERNAME", "User Name");
		JsonObject userName = new JsonObject();
		userName.put("code", "PRI_USERNAME");
		columnsArray.add(userName);
		AttributeText attributeTextFirstName = new AttributeText("COL_PRI_FIRSTNAME", "First Name");
		JsonObject firstName = new JsonObject();
		firstName.put("code", "PRI_FIRSTNAME");
		columnsArray.add(firstName);
		AttributeText attributeTextLastName = new AttributeText("COL_PRI_LASTNAME", "Last Name");
		JsonObject lastName = new JsonObject();
		lastName.put("code", "PRI_LASTNAME");
		columnsArray.add(lastName);
		AttributeText attributeTextMobile = new AttributeText("COL_PRI_MOBILE", "Mobile Number");
		JsonObject mobile = new JsonObject();
		mobile.put("code", "PRI_MOBILE");
		columnsArray.add(mobile);
		AttributeText attributeTextEmail = new AttributeText("COL_PRI_EMAIL", "Email");
		JsonObject email = new JsonObject();
		email.put("code", "PRI_EMAIL");
		columnsArray.add(email);
		println("The columnsArray is ::" + columnsArray);
		// Sort Attribute
		AttributeText attributeTextSortFirstName = new AttributeText("SRT_PRI_FIRSTNAME", "Sort By FirstName");
		AttributeBoolean attributeIsDriver = new AttributeBoolean("PRI_IS_SELLER", "=");
		// Pagination Attribute
		AttributeInteger attributePageStart = new AttributeInteger("SCH_PAGE_START", "PageStart");
		AttributeInteger attributePageSize = new AttributeInteger("SCH_PAGE_SIZE", "PageSize");

		try {
			searchBE.addAttribute(attributeTextImage, 10.0);
			searchBE.addAttribute(attributeTextUserName, 9.0);
			searchBE.addAttribute(attributeTextFirstName, 8.0);
			searchBE.addAttribute(attributeTextLastName, 7.0);
			searchBE.addAttribute(attributeTextMobile, 6.0);
			searchBE.addAttribute(attributeTextEmail, 5.0);
			searchBE.addAttribute(attributeTextSortFirstName, 4.0, "ASC");
			searchBE.addAttribute(attributeIsDriver, 3.0, "TRUE");
			searchBE.addAttribute(attributePageStart, 3.0, "0");
			searchBE.addAttribute(attributePageSize, 2.0, "20");
		} catch (BadDataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// }else {
		// searchBE = getBaseEntityByCodeWithAttributes(searchBeCode);
		// }
		// println("The search BE is :: " + JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String result = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());
		System.out.println("The result   ::  " + result);
		publishData(new JsonObject(result));
		sendTableViewWithHeaders("SBE_GET_ALL_DRIVERS", columnsArray);
		// sendCmdView("TABLE_VIEW", "SBE_GET_ALL_USERS" );
		// publishCmd(result, grpCode, "LNK_CORE");

	}

	// Search and send all the Owners
	public void sendAllOwners(String searchBeCode) throws ClientProtocolException, IOException {
		println("Get All Drivers - The search BE is  :: " + searchBeCode);
		BaseEntity searchBE = new BaseEntity(searchBeCode, "Get All Owners");
		JsonArray columnsArray = new JsonArray();
		JsonObject columns = new JsonObject();
		AttributeText attributeTextImage = new AttributeText("COL_PRI_IMAGE_URL", "Image");
		JsonObject image = new JsonObject();
		image.put("code", "PRI_IMAGE_URL");
		columnsArray.add(image);
		AttributeText attributeTextUserName = new AttributeText("COL_PRI_USERNAME", "User Name");
		JsonObject userName = new JsonObject();
		userName.put("code", "PRI_USERNAME");
		columnsArray.add(userName);
		AttributeText attributeTextFirstName = new AttributeText("COL_PRI_FIRSTNAME", "First Name");
		JsonObject firstName = new JsonObject();
		firstName.put("code", "PRI_FIRSTNAME");
		columnsArray.add(firstName);
		AttributeText attributeTextLastName = new AttributeText("COL_PRI_LASTNAME", "Last Name");
		JsonObject lastName = new JsonObject();
		lastName.put("code", "PRI_LASTNAME");
		columnsArray.add(lastName);
		AttributeText attributeTextMobile = new AttributeText("COL_PRI_MOBILE", "Mobile Number");
		JsonObject mobile = new JsonObject();
		mobile.put("code", "PRI_MOBILE");
		columnsArray.add(mobile);
		AttributeText attributeTextEmail = new AttributeText("COL_PRI_EMAIL", "Email");
		JsonObject email = new JsonObject();
		email.put("code", "PRI_EMAIL");
		columnsArray.add(email);
		println("The columnsArray is ::" + columnsArray);
		// Sort Attribute
		AttributeText attributeTextSortFirstName = new AttributeText("SRT_PRI_FIRSTNAME", "Sort By FirstName");
		AttributeBoolean attributeIsDriver = new AttributeBoolean("PRI_OWNER", "=");
		// Pagination Attribute
		AttributeInteger attributePageStart = new AttributeInteger("SCH_PAGE_START", "PageStart");
		AttributeInteger attributePageSize = new AttributeInteger("SCH_PAGE_SIZE", "PageSize");

		try {
			searchBE.addAttribute(attributeTextImage, 10.0);
			searchBE.addAttribute(attributeTextUserName, 9.0);
			searchBE.addAttribute(attributeTextFirstName, 8.0);
			searchBE.addAttribute(attributeTextLastName, 7.0);
			searchBE.addAttribute(attributeTextMobile, 6.0);
			searchBE.addAttribute(attributeTextEmail, 5.0);
			searchBE.addAttribute(attributeTextSortFirstName, 4.0, "ASC");
			searchBE.addAttribute(attributeIsDriver, 3.0, "TRUE");
			searchBE.addAttribute(attributePageStart, 3.0, "0");
			searchBE.addAttribute(attributePageSize, 2.0, "20");
		} catch (BadDataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// println("The search BE is :: " + JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String result = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());
		System.out.println("The result   ::  " + result);
		publishData(new JsonObject(result));
		sendTableViewWithHeaders("SBE_GET_ALL_OWNERS", columnsArray);

	}

	public void setLastLayout(final String layoutViewCode, final String layoutViewGroupBECode) {
		String sessionId = getAsString("session_state");
		String[] layoutArray = { layoutViewCode, layoutViewGroupBECode };
		println("Set Layout:- The Session Id is ::" + sessionId);
		println("The layout is :: " + layoutArray[0] + " and " + layoutArray[1]);
		VertxUtils.putStringArray(realm(), "PreviousLayout", sessionId, layoutArray);
	}

	public String[] getLastLayout() {
		String sessionId = getAsString("session_state");
		String[] previousLayout = VertxUtils.getStringArray(realm(), "PreviousLayout", sessionId);
		return previousLayout;
	}

	/* Sorting Offers of a beg as per the price, lowest price being on top */
	public void sortOffersInBeg(final String begCode) {

		List<BaseEntity> offers = this.baseEntity.getLinkedBaseEntities(begCode, "LNK_BEG", "OFFER");
		// println("All the Offers for the load " + begCode + " are: " +
		// offers.toString());
		if (offers.size() > 1) {
			Collections.sort(offers, new Comparator<BaseEntity>() {
				@Override
				public int compare(BaseEntity offer1, BaseEntity offer2) {
					println("The price value of " + offer1.getCode() + " is "
							+ offer1.getValue("PRI_OFFER_OWNER_PRICE_INC_GST", null));
					println("The price value of " + offer2.getCode() + " is "
							+ offer2.getValue("PRI_OFFER_OWNER_PRICE_INC_GST", null));
					Money offer1Money = offer1.getValue("PRI_OFFER_OWNER_PRICE_INC_GST", null);
					Money offer2Money = offer2.getValue("PRI_OFFER_OWNER_PRICE_INC_GST", null);
					if (offer1Money != null && offer2Money != null) {
						Number offer1MoneyValue = offer1Money.getNumber().doubleValue();
						Number offer2MoneyValue = offer2Money.getNumber().doubleValue();

						return ((Double) offer1MoneyValue).compareTo((Double) (offer2MoneyValue));
					} else
						return 0;

				}
			});
		}
		// println("The offers in the descendinng order :: " + offers.toString());
		// println("The size of list is :: " + offers.size());
		double maxLinkWeightValue = offers.size();
		double linkWeight = 1;
		for (BaseEntity be : offers) {
			if (linkWeight <= maxLinkWeightValue) {
				this.baseEntity.updateLink(begCode, be.getCode(), "LNK_BEG", "OFFER", linkWeight);
				linkWeight++;
			}
		}

	}

	public void triggerEmailForJobUpdate(String jobCode) {

		println("Job is already submitted, so the job is getting edited");

		List<Link> links = this.baseEntity.getLinks(jobCode, "LNK_BEG");
		List<String> offerList = new ArrayList<String>();
		String ownerCode = null;
		String loadCode = null;

		if (links != null) {

			for (Link link : links) {

				String linkValue = link.getLinkValue();
				if (linkValue != null && linkValue.equals("OFFER")) {
					offerList.add(link.getTargetCode());
				}

				if (linkValue != null && linkValue.equals("OWNER")) {
					ownerCode = link.getTargetCode();
				}

				if (linkValue != null && linkValue.equals("LOAD")) {
					loadCode = link.getTargetCode();
				}

			}
		}

		/*
		 * Iterating over each offer and sending email to the recipient individually,
		 * since each offer will have different owner-quoted price
		 */
		if (offerList.size() > 0) {
			for (String offer : offerList) {
				BaseEntity offerBe = this.baseEntity.getBaseEntityByCode(offer);
				String quoterCode = offerBe.getValue("PRI_QUOTER_CODE", null);

				String[] recipientArr = { quoterCode };

				HashMap<String, String> contextMap = new HashMap<String, String>();
				contextMap.put("JOB", jobCode);
				contextMap.put("OWNER", ownerCode);
				contextMap.put("LOAD", loadCode);
				contextMap.put("OFFER", offer);

				println("sending edit-mail to driver : " + quoterCode + ", with offer : " + offer);

				sendMessage("", recipientArr, contextMap, "MSG_CH40_JOB_EDITED", "EMAIL");
			}
		}

	}

	public void setSessionState(final String key, final Object value) {
		Map<String, String> map = VertxUtils.getMap(realm(), "STATE", key);
		if (value == null) {
			map.remove(key);
		} else {
			map.put(key, JsonUtils.toJson(value));
		}
		VertxUtils.putObject(realm(), "STATE", getDecodedTokenMap().get("session_state").toString(), map);
	}

	public Object getSessionState(final String key) {
		Type type = new TypeToken<Map<String, Object>>() {
		}.getType();
		Map<String, Object> myMap = VertxUtils.getObject(realm(), "STATE",
				getDecodedTokenMap().get("session_state").toString(), type);
		Object ret = myMap.get(key);
		return ret;
	}

	public Map<String, Object> getSessionStateMap() {
		Type type = new TypeToken<Map<String, Object>>() {
		}.getType();
		Map<String, Object> myMap = VertxUtils.getObject(realm(), "STATE",
				getDecodedTokenMap().get("session_state").toString(), type);
		return myMap;

	}

	/* TODO: refactor this. */
	public void redirectToHomePage() {
		sendSublayout("BUCKET_DASHBOARD", "dashboard_channel40.json", "GRP_DASHBOARD");
		setLastLayout("BUCKET_DASHBOARD", "GRP_DASHBOARD");
	}

	public void add(final String keyPrefix, final String parentCode, final BaseEntity be) {
		// Add this be to the static
		if ("GRP_REPORTS".equals(parentCode)) {
			System.out.println("GRP_REPORTS being added to");
		}
		Map<String, String> map = VertxUtils.getMap(this.realm(), keyPrefix, parentCode);
		if (map == null) {
			map = new HashMap<String, String>();
		}
		map.put(be.getCode(), JsonUtils.toJson(be));
		VertxUtils.writeCachedJson(be.getCode(), JsonUtils.toJson(be));
		VertxUtils.putMap(this.realm(), keyPrefix, parentCode, map);
	}

	public Map<String, String> getMap(final String keyPrefix, final String parentCode) {
		// Add this be to the static
		Map<String, String> map = VertxUtils.getMap(this.realm(), keyPrefix, parentCode);
		if (map == null) {
			map = new HashMap<String, String>();
		}
		return map;
	}

	public void remove(final String keyPrefix, final String parentCode, final String beCode) {
		// Add this be to the static
		Map<String, String> map = VertxUtils.getMap(this.realm(), keyPrefix, parentCode);
		if (map != null) {
			map.remove(beCode);
			VertxUtils.putMap(this.realm(), keyPrefix, parentCode, map);
		}
	}

	public void remove(final String keyPrefix, final String parentCode) {
		VertxUtils.putMap(this.realm(), keyPrefix, parentCode, null);
	}

	/*
	 * Send Report based on the SearchBE
	 */
	public void sendReport(String reportCode) throws IOException {

		System.out.println("The report code is :: " + reportCode);
		// BaseEntity searchBE = getBaseEntityByCode(reportCode);

		String jsonSearchBE = null;
		SearchEntity srchBE = null;

		if (reportCode.equalsIgnoreCase("SBE_OWNERJOBS") || reportCode.equalsIgnoreCase("SBE_DRIVERJOBS")) {
			// srchBE.setStakeholder(getUser().getCode());
			srchBE = new SearchEntity(reportCode, "List of all My Loads").addColumn("PRI_NAME", "Load Name")
					.addColumn("PRI_JOB_ID", "Job ID").addColumn("PRI_PICKUP_ADDRESS_FULL", "Pickup Address")
					.addColumn("PRI_DESCRIPTION", "Description")

					.setStakeholder(getUser().getCode())

					.addSort("PRI_NAME", "Name", SearchEntity.Sort.ASC)

					.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "BEG_%")

					.setPageStart(0).setPageSize(10000);

			jsonSearchBE = JsonUtils.toJson(srchBE);

		} else {
			BaseEntity searchBE = this.baseEntity.getBaseEntityByCode(reportCode);
			jsonSearchBE = JsonUtils.toJson(searchBE);
		}

		System.out.println("The search BE is :: " + jsonSearchBE);
		// String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());

		this.println(resultJson);

		QDataBaseEntityMessage msg = JsonUtils.fromJson(resultJson, QDataBaseEntityMessage.class);
		try {
			if (msg.getItems()!=null) {

				// Now work out sums
				boolean first = true;
				Map<String,Object> sums = new HashMap<String,Object>(); // store the column sums
				Map<String,DataType> dtypes = new HashMap<String,DataType>();
				Map<String,Attribute> attributes = new HashMap<String,Attribute>();


				for (BaseEntity row : msg.getItems()) {
					if (first) {
						this.baseEntity.addAttributes(row);
						for (EntityAttribute col1 : row.getBaseEntityAttributes()) {
							if (DataType.summable(col1.getAttribute().getDataType())) {
								sums.put(col1.getAttribute().getCode(),
										DataType.Zero(col1.getAttribute().getDataType()));
								dtypes.put(col1.getAttribute().getCode(), col1.getAttribute().getDataType());
								attributes.put(col1.getAttribute().getCode(), col1.getAttribute());
							}
						}
						first = false;
					}
					// for every column determine the data type
					for (EntityAttribute col : row.getBaseEntityAttributes()) {
						if (sums.containsKey(col.getAttributeCode())) {
							Object currentSum = sums.get(col.getAttributeCode());
							Object sum = DataType.add(dtypes.get(col.getAttributeCode()), currentSum,
									(Object) col.getValue());
							sums.put(col.getAttribute().getCode(), sum);
						}
					}
				}

				// Now append a sum baseentity
				BaseEntity sumBe = new BaseEntity("SUM_" + reportCode.substring("SBE_".length()),
						"Summary " + srchBE.getName());
				for (String sumKey : sums.keySet()) {
					Attribute sumAttribute = attributes.get(sumKey);
					Object sum = sums.get(sumKey);
					sumBe.addAttribute(sumAttribute, 0.0, sum.toString());

				}
				msg.setSum(sumBe);
				publishCmd(msg);
			}

		} catch (Exception e) {

		}
	}

	/*
	 * Publish Search BE results
	 */
	public void sendSearchResults(SearchEntity searchBE) throws IOException {
		System.out.println("The search BE is :: " + JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());
		QDataBaseEntityMessage msg = JsonUtils.fromJson(resultJson, QDataBaseEntityMessage.class);
		System.out.println("The result   ::  " + msg);
		publishData(new JsonObject(resultJson));
	}

	/*
	 * Publish Search BE results setting the parentCode in QDataBaseEntityMessage
	 */
	public void sendSearchResults(SearchEntity searchBE, String parentCode) throws IOException {
		System.out.println("The search BE is :: " + JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());
		QDataBaseEntityMessage msg = JsonUtils.fromJson(resultJson, QDataBaseEntityMessage.class);
		msg.setParentCode(parentCode);
		System.out.println("The result   ::  " + msg);
		publishData(new JsonObject(resultJson));
	}

	/*
	 * Get search Results returns QDataBaseEntityMessage
	 */
	public QDataBaseEntityMessage getSearchResults(SearchEntity searchBE) throws IOException {
		return getSearchResults(searchBE, getToken());
	}

	/*
	 * Get search Results returns QDataBaseEntityMessage
	 */
	public QDataBaseEntityMessage getSearchResults(SearchEntity searchBE, final String token) throws IOException {
		System.out.println("The search BE is :: " + JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				token);
		QDataBaseEntityMessage msg = JsonUtils.fromJson(resultJson, QDataBaseEntityMessage.class);
		System.out.println("The result   ::  " + msg);

		return msg;
	}

	/*
	 * Get search Results return String
	 */
	public String getSearchResultsString(SearchEntity searchBE) throws IOException {
		System.out.println("The search BE is :: " + JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());

		return resultJson;
	}

	/*
	 * Check if conversation between sender and receiver already exists
	 */
	public Boolean checkIfChatAlreadyExists(final String sender, final String receiver) {
		List<BaseEntity> chats = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_MESSAGES", "LNK_CHAT", 0, 500, true);

		if (chats != null) {

			for (BaseEntity chat : chats) {

				List<BaseEntity> users = this.baseEntity.getBaseEntitysByParentAndLinkCode(chat.getCode(), "LNK_USER", 0, 500, true);
				if (users != null) {
					if (users.contains(this.baseEntity.getBaseEntityByCode(sender)) && users.contains(this.baseEntity.getBaseEntityByCode(receiver))) {
						return true;
					}
				}
			}
		}
		return false;

	}

	/*
	 * Give oldChat for the given sender and receiver
	 */
	public BaseEntity getOldChatForSenderReceiver(final String sender, final String receiver) {
		List<BaseEntity> chats = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_MESSAGES", "LNK_CHAT", 0, 100, true);
		if (chats != null) {

			for (BaseEntity chat : chats) {

				List<BaseEntity> users = this.baseEntity.getBaseEntitysByParentAndLinkCode(chat.getCode(), "LNK_USER", 0, 500, true);
				if (users != null) {
					if (users.contains(this.baseEntity.getBaseEntityByCode(sender)) && users.contains(this.baseEntity.getBaseEntityByCode(receiver))) {
						return chat;
					}
				}
			}
		}
		return null;
	}

	/*
	 * Chat Message:- Send cmd_msg SPLIT_VIEW for the chat message display TODO:
	 * Refactor
	 */
	public void sendCmdReportsSplitView(final String parentCode, final String searchBECode) {

		QCmdMessage cmdView = new QCmdMessage("CMD_VIEW", "SPLIT_VIEW");
		JsonObject cmdViewJson = JsonObject.mapFrom(cmdView);

		JsonObject codeListView = new JsonObject();
		codeListView.put("code", "LIST_VIEW");
		codeListView.put("root", parentCode);

		JsonObject reportListView = new JsonObject();
		reportListView.put("code", "TABLE_VIEW");
		if (searchBECode == null || searchBECode.isEmpty()) {
			reportListView.put("data", "null");
			reportListView.put("root", "null");
		} else {
			JsonObject columns = new JsonObject();
			BaseEntity searchBE = this.baseEntity.getBaseEntityByCode(searchBECode);
			List<EntityAttribute> eaList = new ArrayList<EntityAttribute>();

			for (EntityAttribute ea : searchBE.getBaseEntityAttributes()) {
				if (ea.getAttributeCode().startsWith("COL_")) {
					eaList.add(ea);
				}
			}
			List<String> sortedColumns = this.baseEntity.sortEntityAttributeBasedOnWeight(eaList, "ASC");
			String[] beArr = new String[sortedColumns.size()];
			beArr = sortedColumns.toArray(beArr);

			JsonArray tColumns = new JsonArray();
			JsonArray colHeaderArr = new JsonArray();
			for (int i = 0; i < beArr.length; i++) {
				String colS = beArr[i];
				colHeaderArr.add(colS);
				JsonObject obj = new JsonObject();
				obj.put("code", colS);
				tColumns.add(obj);
			}

			columns.put("columns", colHeaderArr);
			reportListView.put("data", columns);
			reportListView.put("root", searchBECode);
		}

		JsonArray msgCodes = new JsonArray();
		msgCodes.add(codeListView);
		msgCodes.add(reportListView);
		System.out.println("The JsonArray is :: " + msgCodes);
		cmdViewJson.put("root", msgCodes);
		cmdViewJson.put("token", getToken());
		System.out.println(" The cmd msg is :: " + cmdViewJson);

		publishCmd(cmdViewJson);
	}

	// attachments
	public void sendMessage(String[] recipientArray, HashMap<String, String> contextMap, String templateCode,
			String messageType, List<QBaseMSGAttachment> attachmentList) {

		if (recipientArray != null && recipientArray.length > 0) {

			/* Adding project code to context */
			String projectCode = "PRJ_" + getAsString("realm").toUpperCase();
			contextMap.put("PROJECT", projectCode);

			JsonObject message = MessageUtils.prepareMessageTemplateWithAttachments(templateCode, messageType,
					contextMap, recipientArray, attachmentList, getToken());
			publish("messages", message);
		} else {
			log.error("Recipient array is null and so message cant be sent");
		}

	}

	public void triggerReleasePaymentMailWithAttachment(BaseEntity ownerBe, BaseEntity driverBe, BaseEntity offerBe,
			BaseEntity loadBe, BaseEntity begBe) {

		Money ownerIncGST = null;
		Money ownerExcGST = null;
		Money driverExcGST = null;
		Money driverIncGST = null;

		// String offerCode = begBe.getValue("STT_HOT_OFFER", null);
		if (offerBe != null) {

			ownerIncGST = offerBe.getValue("PRI_OFFER_OWNER_PRICE_INC_GST", null);
			ownerExcGST = offerBe.getValue("PRI_OFFER_OWNER_PRICE_EXC_GST", null);

			driverIncGST = offerBe.getValue("PRI_OFFER_DRIVER_PRICE_INC_GST", null);
			driverExcGST = offerBe.getValue("PRI_OFFER_DRIVER_PRICE_EXC_GST", null);

			/* invoice attachments(buyer and seller) are attributes of ProjectBe */
			BaseEntity projectBe = getProject();
			String ownerInvoiceLayoutUrl = projectBe.getValue("PRI_INVOICE_LAYOUT_BUYER", null);
			String driverInvoiceLayoutUrl = projectBe.getValue("PRI_INVOICE_LAYOUT_SELLER", null);

			/* context map for sending email */
			HashMap<String, String> contextMap = new HashMap<String, String>();

			/* start of ---> merge with key and baseEntity code as value */
			contextMap.put("JOB", begBe.getCode());
			contextMap.put("OFFER", offerBe.getCode());
			contextMap.put("OWNER", ownerBe.getCode());
			contextMap.put("DRIVER", driverBe.getCode());
			contextMap.put("LOAD", loadBe.getCode());

			/* We get the driver & owner company */
			if (driverBe != null) {
				BaseEntity driverCompanyBe = this.baseEntity.getParent(driverBe.getCode(), "LNK_STAFF");

				if (driverCompanyBe != null) {
					contextMap.put("DRIVER_COMPANY", driverCompanyBe.getCode());
				}
			}

			if (ownerBe != null) {
				BaseEntity ownerCompanyBe = this.baseEntity.getParent(ownerBe.getCode(), "LNK_STAFF");

				if (ownerCompanyBe != null) {
					contextMap.put("OWNER_COMPANY", ownerCompanyBe.getCode());
				}
			}
			/* end of ---> merge with key and baseEntity code as value */

			/* start of ----> direct merging with no baseentities */
			QwandaUtils.getZonedCurrentLocalDateTime();
			contextMap.put("INVOICE_DATE", getFormattedCurrentLocalDateTime());

			Double gstDoubleValue_owner = ownerIncGST.getNumber().doubleValue() - ownerExcGST.getNumber().doubleValue();
			Double gstDoubleValue_driver = driverIncGST.getNumber().doubleValue()
					- driverExcGST.getNumber().doubleValue();

			/* rounding off GST amount to 2 decimal points */
			String roundedGstValue_owner = String.format("%.2f", gstDoubleValue_owner);
			String roundedGstValue_driver = String.format("%.2f", gstDoubleValue_driver);

			contextMap.put("PRI_GST_OWNER", roundedGstValue_owner);
			contextMap.put("PRI_GST_DRIVER", roundedGstValue_driver);

			/* Channel40's ABN - Project attribute */
			String projectCompanyABN = projectBe.getValue("PRI_ABN", null);
			if (projectCompanyABN != null) {
				contextMap.put("PROJECT_ABN", projectCompanyABN);
			} else {
				contextMap.put("PROJECT_ABN", "-");
			}

			/* we get the payment method the freight owner selected for the job */
			QPaymentMethod selectedOwnerPaymentMethod = PaymentUtils.getPaymentMethodSelectedByOwner(begBe, ownerBe);
			if (selectedOwnerPaymentMethod != null) {

				/* Getting owner-selected PaymentMethod in POJO */
				PaymentType paymentMethodType = selectedOwnerPaymentMethod.getType();
				contextMap.put("PAYMENT_TYPE", paymentMethodType.toString());

				Character[] toBeIgnoreCharacterArr = { '-' };
				if (paymentMethodType.equals(PaymentType.CARD)) {

					String creditCardNumber = selectedOwnerPaymentMethod.getNumber();

					if (creditCardNumber != null) {

						/* Replacing all blabk spaces in credit-card with "-" */
						creditCardNumber = creditCardNumber.replaceAll("\\s+", "-");

						/* Masking credit card number */
						String maskedCreditCardNumber = StringFormattingUtils.maskWithRange(creditCardNumber, 0, 15,
								"x", toBeIgnoreCharacterArr);

						if (maskedCreditCardNumber != null) {
							contextMap.put("PAYMENT_ACCOUNTNUMBER", maskedCreditCardNumber);
						} else {
							contextMap.put("PAYMENT_ACCOUNTNUMBER", "");
						}

					}

				} else if (paymentMethodType.equals(PaymentType.BANK_ACCOUNT)) {

					String bsb = selectedOwnerPaymentMethod.getBsb();
					String accountNumber = selectedOwnerPaymentMethod.getAccountNumber();

					if (bsb != null && accountNumber != null) {

						bsb = bsb.replaceAll("\\s+", "-");
						accountNumber = accountNumber.replaceAll("\\s+", "-");

						/* Masking bsb and account number */
						String maskedBsb = StringFormattingUtils.maskWithRange(bsb, 0, 5, "x", toBeIgnoreCharacterArr);
						String maskedAccountNumber = StringFormattingUtils.maskWithRange(accountNumber, 0, 4, "x",
								toBeIgnoreCharacterArr);

						if (maskedAccountNumber != null && maskedBsb != null) {
							contextMap.put("PAYMENT_ACCOUNTNUMBER", maskedAccountNumber + ", BSB:" + maskedBsb);
						} else {
							contextMap.put("PAYMENT_ACCOUNTNUMBER", "");
						}
					}
				}
			}

			/* end of ----> direct merging with no baseentities */

			List<QBaseMSGAttachment> ownerAttachmentList = null;
			List<QBaseMSGAttachment> driverAttachmentList = null;

			/* invoice attachment for owner */
			if (ownerInvoiceLayoutUrl != null) {
				ownerAttachmentList = new ArrayList<>();
				QBaseMSGAttachment ownerInvoiceAttachment = new QBaseMSGAttachment(AttachmentType.INLINE,
						"application/pdf", ownerInvoiceLayoutUrl, true, "INVOICE_PDF");
				ownerAttachmentList.add(ownerInvoiceAttachment);
			}

			/* invoice attachment for driver */
			if (driverInvoiceLayoutUrl != null) {
				driverAttachmentList = new ArrayList<>();

				QBaseMSGAttachment driverInvoiceAttachment = new QBaseMSGAttachment(AttachmentType.INLINE,
						"application/pdf", driverInvoiceLayoutUrl, true, "RECEIPT_PDF");

				driverAttachmentList.add(driverInvoiceAttachment);
			}

			String[] messageToOwnerRecipients = new String[1];
			messageToOwnerRecipients[0] = ownerBe.getCode();
			sendMessage(begBe.getCode(), messageToOwnerRecipients, contextMap, "MSG_CH40_PAYMENT_RELEASED_OWNER",
					"TOAST");
			sendMessage(messageToOwnerRecipients, contextMap, "MSG_CH40_PAYMENT_RELEASED_OWNER", "EMAIL",
					ownerAttachmentList);

			String[] messageToDriverRecipients = new String[1];
			messageToDriverRecipients[0] = driverBe.getCode();
			sendMessage(begBe.getCode(), messageToDriverRecipients, contextMap, "MSG_CH40_PAYMENT_RELEASED_DRIVER",
					"TOAST");
			sendMessage(messageToDriverRecipients, contextMap, "MSG_CH40_PAYMENT_RELEASED_DRIVER", "EMAIL",
					driverAttachmentList);

		} else {
			BaseEntity project = getProject();

			if (project != null) {

				String webhookURL = project.getLoopValue("PRI_SLACK_NOTIFICATION_URL", null);
				if (webhookURL != null) {

					String message = "Tax invoice generation failed, offer is null, BEG :" + begBe.getCode()
							+ ", LOAD :" + loadBe;
					JsonObject payload = new JsonObject();
					payload.put("text", message);

					try {
						postSlackNotification(webhookURL, payload);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

			}
		}
	}

	/* format date in format 10 May 2010 */
	public String getFormattedCurrentLocalDateTime() {
		LocalDateTime date = LocalDateTime.now();
		DateFormat df = new SimpleDateFormat("dd MMM yyyy");
		Date datetime = Date.from(date.atZone(ZoneId.systemDefault()).toInstant());
		String dateString = df.format(datetime);

		return dateString;

	}

	private void setNewTokenAndDecodedTokenMap(String token) {

		Map<String, Object> serviceDecodedTokenMap = KeycloakUtils.getJsonMap(token);
		this.setDecodedTokenMap(serviceDecodedTokenMap);
		this.println(serviceDecodedTokenMap);
		this.setToken(token);
		this.set("realm", serviceDecodedTokenMap.get("azp"));

		/* we reinit utils */
		this.initUtils();
	}

	public boolean loadRealmData() {

		println("PRE_INIT_STARTUP Loading in keycloak data and setting up service token for " + realm());

		for (String jsonFile : SecureResources.getKeycloakJsonMap().keySet()) {

			String keycloakJson = SecureResources.getKeycloakJsonMap().get(jsonFile);
			if (keycloakJson == null) {
				System.out.println("No keycloakMap for " + realm());
				return false;
			}

			JsonObject realmJson = new JsonObject(keycloakJson);
			JsonObject secretJson = realmJson.getJsonObject("credentials");
			String secret = secretJson.getString("secret");
			String realm = realmJson.getString("realm");

			if (realm != null) {

				String token = RulesUtils.generateServiceToken(realm);
				this.println(token);
				if (token != null) {

					this.setNewTokenAndDecodedTokenMap(token);

					String dev = System.getenv("GENNYDEV");
					String proj_realm = System.getenv("PROJECT_REALM");
					if ((dev != null) && ("TRUE".equalsIgnoreCase(dev))) {
						this.set("realm", proj_realm);
					} else {
						this.set("realm", realm);
					}

					return true;
				}
			}

		}

		return false;
	}

	public void generateTree() {

		String token = RulesUtils.generateServiceToken(realm());
		if(token != null) {
			this.setNewTokenAndDecodedTokenMap(token);
		}

		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();

		BaseEntity root = this.baseEntity.getBaseEntityByCode("GRP_ROOT");
		BaseEntity[] bes = new BaseEntity[1];
		bes[0] = root;
		bulkmsg.add(new QDataBaseEntityMessage(bes, "GRP_ROOT", "LNK_CORE"));

		List<BaseEntity> rootGrp = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_ROOT", "LNK_CORE", 0, 50, false);
		bulkmsg.add(new QDataBaseEntityMessage(rootGrp.toArray(new BaseEntity[0]), "GRP_ROOT", "LNK_CORE"));
		// println(root);

		List<BaseEntity> reportsHeader = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_REPORTS", "LNK_CORE", 0, 50, false);
		bulkmsg.add(new QDataBaseEntityMessage(reportsHeader.toArray(new BaseEntity[0]), "GRP_REPORTS", "LNK_CORE"));

		List<BaseEntity> admin = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_ADMIN", "LNK_CORE", 0, 20, false);
		bulkmsg.add(new QDataBaseEntityMessage(admin.toArray(new BaseEntity[0]), "GRP_ADMIN", "LNK_CORE"));

		// Now get the buckets
		List<BaseEntity> buckets = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_DASHBOARD", "LNK_CORE", 0, 20, false);
		// Save the buckets for future use
		QDataBaseEntityMessage bucketMsg = new QDataBaseEntityMessage(buckets.toArray(new BaseEntity[0]),
				"GRP_DASHBOARD", "LNK_CORE");
		VertxUtils.putObject(realm(), "BUCKETS", realm(), bucketMsg);

		bulkmsg.add(new QDataBaseEntityMessage(buckets.toArray(new BaseEntity[0]), "GRP_DASHBOARD", "LNK_CORE"));

		QBulkMessage bulk = new QBulkMessage(bulkmsg);

		VertxUtils.putObject(realm(), "BASE_TREE", realm(), bulk);
	}

	public void sendTreeData() {

		println("treedata realm is " + realm());
		QBulkMessage bulk = VertxUtils.getObject(realm(), "BASE_TREE", realm(), QBulkMessage.class);

		if ((bulk == null) || (bulk.getMessages() == null) || (bulk.getMessages().length == 0)) {
			log.error("Tree Data NOT in cache - forcing a cache load");
			startupEvent("Send Tree data");
			bulk = VertxUtils.getObject(realm(), "BASE_TREE", realm(), QBulkMessage.class);
		}
		if ((bulk != null) && (bulk.getMessages() != null) && (bulk.getMessages().length > 0)) {

			List<QDataBaseEntityMessage> baseEntityMsgs = new ArrayList<QDataBaseEntityMessage>();

			for (QDataBaseEntityMessage msg : bulk.getMessages()) {

				if (msg instanceof QDataBaseEntityMessage) {

					String grpCode = msg.getParentCode();
					BaseEntity parent = VertxUtils.readFromDDT(grpCode, getToken());

					List<BaseEntity> allowedChildren = new ArrayList<BaseEntity>();

					/* GRP_ROOT does not have any parent, so we create its own message */
					if (grpCode.equalsIgnoreCase("GRP_ROOT")) {

						BaseEntity[] roots = new BaseEntity[1];
						roots[0] = parent;
						QDataBaseEntityMessage rootMessage = new QDataBaseEntityMessage(roots, "GRP_ROOT_ROOT", "LNK_CORE");
						rootMessage.setToken(getToken());
						baseEntityMsgs.add(rootMessage);
					}

					for (BaseEntity child : msg.getItems()) {

						String childCode = child.getCode();

						// Getting the attributes GRP_XX of parent that has roles not allowed
						Optional<EntityAttribute> roleAttribute = parent.findEntityAttribute(childCode);
						if (roleAttribute.isPresent()) {

							// Getting the value of
							String rolesAllowedStr = roleAttribute.get().getValue();

							// creating array as it can have multiple roles
							String[] rolesAllowed = rolesAllowedStr.split(",");
							Boolean match = false;

							for (EntityAttribute ea : getUser().getBaseEntityAttributes()) {
								if (ea.getAttributeCode().startsWith("PRI_IS_")) {
									try { // handling exception when the value is not saved as valueBoolean
										if (ea.getValueBoolean()) {
											for (String role : rolesAllowed) {
												match = role.equalsIgnoreCase(ea.getAttributeCode());
												if (match) {
													allowedChildren.add(child);
												}
											}
										}
									} catch (Exception e) {
										System.out.println("Error!! The attribute value is not in boolean format");
									}
								}

							}
						} else {
							allowedChildren.add(child);
						}
					}

					QDataBaseEntityMessage filteredMsg = new QDataBaseEntityMessage(
							allowedChildren.toArray(new BaseEntity[allowedChildren.size()]), grpCode, "LNK_CORE");
					filteredMsg.setToken(getToken());
					baseEntityMsgs.add(filteredMsg);
				}
			}

			QBulkMessage newBulkMsg = new QBulkMessage(baseEntityMsgs);
			try {
				String str = JsonUtils.toJson(newBulkMsg);
				JsonObject bulkJson = new JsonObject(str);
				this.publishData(bulkJson);
			} catch (Exception e) {
				System.out.println("Error in JSON conversion");
			}

		}
	}

	public void startupEvent(String caller) {

		println("Startup Event called from " + caller);
		if (!isState("GENERATE_STARTUP")) {
			this.loadRealmData();
			this.generateTree();
			this.reloadCache();
		}
	}

	public void reloadCache() {

		/* we check if the search BEs have been created */
		BaseEntity searchNewItems = this.baseEntity.getBaseEntityByCode("SBE_NEW_ITEMS");
		if (searchNewItems == null) {
			drools.setFocus("GenerateSearches");
		}

		this.cacheUtils.refresh(this.realm(), "BUCKETS");
		this.cacheUtils.refresh(this.realm(), "ARCHIVED_PRODUCTS"); /* TODO: that might not be necessary */
	}

	public void sendApplicationData() {

		Boolean isLogin = isState("LOOP_AUTH_INIT_EVT") || isState("AUTH_INIT");
		Boolean isRegistration = isState("DID_REGISTER");

		/* no need to send data again if the user is not logging in or registering */
		if (!isLogin && !isRegistration) {
			this.setState("DATA_SENT_FINISHED");
			return;
		}

		System.out.println("Entering new send application data ");

		showLoading("Loading data...");

		/* we set all the buckets we would like user to subscribe to */
		HashMap<String, String> subscriptions = new HashMap<String, String>();
		subscriptions.put("PRI_IS_SELLER", "GRP_NEW_ITEMS");

		this.sendCachedItem("BUCKETS", subscriptions);

		/* end of process, tell rules to show layouts */
		this.setState("DATA_SENT_FINISHED");
	}

	public void sendCachedItem(final String cachedItemKey) {
		this.sendCachedItem(cachedItemKey, null);
	}

	public void sendCachedItem(final String cachedItemKey, final HashMap<String, String> subscriptions) {

		long startTime = System.nanoTime();
		BaseEntity user = this.getUser();
		QBulkMessage items = this.cacheUtils.fetchAndSubscribeCachedItemsForStakeholder(realm(), cachedItemKey, user, subscriptions);
		if (items != null) {

			System.out.println("Number of items found in " + cachedItemKey + ": " + items.getMessages().length);

			if (items.getMessages() != null) {

				startTime = System.nanoTime();

				/* if the user is not an admin we not need to filter out data */
				if (!user.is("PRI_IS_ADMIN")) {
					items = this.cacheUtils.filterBucketItemsForStakeholder(items, user);
				}

				println("filtering fetched db Begs takes " + ((System.nanoTime() - startTime) / 1e6) + "ms");
			}

			/* we publish the data */
			try {
				publishCmd(items);
			} catch (Exception e) {

			}

			println("fetch all from api " + ((System.nanoTime() - startTime) / 1e6) + "ms");
			println("publishing takes " + ((System.nanoTime() - startTime) / 1e6) + "ms");

		}
	}

	public void sendBucketLayouts() {
		String viewCode = "BUCKET_DASHBOARD";
		String grpBE = "GRP_DASHBOARD";

		/* sending cmd BUCKET_VIEW */
		sendSublayout(viewCode, "dashboard_" + realm() + ".json", grpBE);
		setLastLayout(viewCode, grpBE);
	}

	public List<BaseEntity> sendBaseEntityWithChildren(BaseEntity be, String linkCode, Integer pageStart,
			Integer pageSize, Boolean cache) {

		List<BaseEntity> children = this.baseEntity.getBaseEntitysByParentAndLinkCode2(be.getCode(), linkCode, pageStart, pageSize,
				cache);

		if (children != null && children.size() > 0) {

			publishCmd(children, be.getCode(), linkCode);
			println("FETCHED        ::   " + children.size() + " BEs ");
			println("FETCHED BES   ::   " + children.toString());
		}

		return children;
	}

	/* returns subscribers of a baseEntity Code */
	public String[] getSubscribers(final String subscriptionCode) {

		final String SUB = "SUB";

		// Subscribe to a code
		String[] resultArray = VertxUtils.getObject(realm(), SUB, subscriptionCode, String[].class);

		String[] resultAdmins = VertxUtils.getObject(realm(), "SUBADMIN", "ADMINS", String[].class);
		String[] result = new String[resultArray.length + resultAdmins.length];
		ArrayUtils.concat(resultArray, resultAdmins, result);
		return result;
	}

	public void createServiceUser() {

		BaseEntity be = null;

		String username = "service";
		String firstname = "Service";
		String lastname = "User";
		String realm = realm();
		String name = "Service User";
		String email = "adamcrow63@gmail.com";
		String keycloakId = getAsString("sub").toLowerCase();

		// Check if already exists
		BaseEntity existing = this.baseEntity.getBaseEntityByCode("PER_SERVICE");
		if (existing == null) {

			try {
				be = QwandaUtils.createUser(qwandaServiceUrl, getToken(), username, firstname, lastname, email, realm,
						name, keycloakId);
				VertxUtils.writeCachedJson(be.getCode(), JsonUtils.toJson(be));
				be = getUser();
				set("USER", be);
				println("New User Created " + be);
				this.setState("DID_CREATE_NEW_USER");

			} catch (IOException e) {
				log.error("Error in Creating User ");
			}
		}
	}

	/*
	 * Sets "PRI_IS_ADMIN" attribute to TRUE if the token from the keycloak has the
	 * role "admin" and set to FALSE if the attribute existed in DB but the role has
	 * been removed from keycloak.
	 */
	public void setAdminRoleIfAdmin() {
		String attributeCode = "PRI_IS_ADMIN";
		BaseEntity user = getUser();
		if (user != null) {
			try {
				Boolean isAdmin = user.getValue(attributeCode, null);
				Answer isAdminAnswer;
				if (hasRole("admin")) {
					if (isAdmin == null || !isAdmin) {
						isAdminAnswer = new Answer(user.getCode(), user.getCode(), attributeCode, "TRUE");
						isAdminAnswer.setWeight(1.0);
						this.baseEntity.saveAnswer(isAdminAnswer);
						setState("USER_ROLE_ADMIN_SET");
					}
				} else if (!hasRole("admin")) {
					if (isAdmin != null && isAdmin) {
						isAdminAnswer = new Answer(user.getCode(), user.getCode(), attributeCode, "FALSE");
						isAdminAnswer.setWeight(1.0);
						this.baseEntity.saveAnswer(isAdminAnswer);
					}
				}

			} catch (Exception e) {
				System.out.println("Error!! while updating " + attributeCode + " attribute value");
			}
		} else {
			System.out.println("Error!! User BaseEntity is null");
		}
	}

	/*
	 * Get payments user details - firstname, lastname, DOB ; set in PaymentUserInfo
	 * POJO
	 */
	public QPaymentsUserInfo getPaymentsUserInfo(BaseEntity projectBe, BaseEntity userBe) {

		QPaymentsUserInfo userInfo = null;

		/* Getting userInfo POJO -> handling errors with slack webhook reporting */
		// TODO display user-info related question group
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
			sendDirectToast(recipientArr, toastMessage, "warning");

			/* send slack message */
			sendSlackNotification(message);

		}
		return userInfo;
	}

	/* Get payments user email details, set in PaymentUserContact POJO */
	public QPaymentsUserContactInfo getPaymentsUserContactInfo(BaseEntity projectBe, BaseEntity userBe) {

		QPaymentsUserContactInfo userContactInfo = null;

		// TODO display user-email related question group
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
			sendDirectToast(recipientArr, toastMessage, "warning");

			/* send slack message */
			sendSlackNotification(message);
		}
		return userContactInfo;

	}

	public QPaymentsLocationInfo getPaymentsUserLocationInfo(BaseEntity projectBe, BaseEntity userBe) {

		QPaymentsLocationInfo userLocationInfo = null;

		// TODO display user-email related question group
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
			sendDirectToast(recipientArr, toastMessage, "warning");

			/* send slack message */
			sendSlackNotification(message);
		}
		return userLocationInfo;

	}

	public String paymentUserCreation(String paymentsUserId, String assemblyAuthToken) {

		BaseEntity userBe = getUser();
		BaseEntity project = getProject();

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
				setState("PAYMENTS_CREATION_FAILURE_CHECK_USER_EXISTS");
				drools.setFocus("payments");

			}

		} catch (IllegalArgumentException e) {

			/* send slack message */
			log.error(e.getMessage());
			String message = "Payments user creation failed : " + e.getMessage() + ", for USER: " + userBe.getCode();
			sendSlackNotification(message);

		}
		return paymentUserId;
	}

	/* Payments - user search method */
	public String findExistingPaymentsUserAndSetAttribute(String authKey) {

		BaseEntity userBe = getUser();
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
				 * String[] recipientArr = { userBe.getCode() }; sendDirectToast(recipientArr,
				 * toastMessage, "warning");
				 */
				sendSlackNotification(message);
			}

		}
		return paymentsUserId;
	}

	/* To send critical slack message to slack channel */
	public void sendSlackNotification(String message) {

		/* send critical slack notifications only for production mode */
		System.out.println("dev mode ::" + devMode);
		BaseEntity project = getProject();
		if (project != null && !devMode) {
			String webhookURL = project.getLoopValue("PRI_SLACK_NOTIFICATION_URL", null);
			if (webhookURL != null) {

				JsonObject payload = new JsonObject();
				payload.put("text", message);

				try {
					postSlackNotification(webhookURL, payload);
				} catch (IOException io) {
					io.printStackTrace();
				}
			}
		}

	}

	// TODO Priority field needs to be made as enum : error,info, warning
	/* To send direct toast messages to the front end without templates */
	public void sendDirectToast(String[] recipientArr, String toastMsg, String priority) {

		/* create toast */
		/* priority can be "info" or "error or "warning" */
		QDataToastMessage toast = new QDataToastMessage(priority, toastMsg);
		toast.setToken(getToken());
		toast.setRecipientCodeArray(recipientArr);

		String toastJson = JsonUtils.toJson(toast);

		publish("data", toastJson);

	}

	public QDataBaseEntityMessage getMappedBEs(final String parentCode) {
		List<BaseEntity> searches = new ArrayList<BaseEntity>();
		Map<String, String> searchBes = getMap("GRP", parentCode);
		for (String searchBeCode : searchBes.keySet()) {
			String searchJson = searchBes.get(searchBeCode);
			BaseEntity searchBE = JsonUtils.fromJson(searchJson, BaseEntity.class);
			searches.add(searchBE);
		}
		QDataBaseEntityMessage ret = new QDataBaseEntityMessage(searches.toArray(new BaseEntity[searches.size()]),
				parentCode, "LNK_CORE");

		return ret;
	}

	public void selectReport(String data) {
		if (data != null) {

			JsonObject dataJson = new JsonObject(data);
			String grpCode = dataJson.getString("hint");
			println("Grp Code = " + grpCode);
			if (grpCode != null && grpCode.startsWith("GRP_REPORTS")) {
				String searchBE = dataJson.getString("itemCode");
				if (searchBE != null) {
					println("Search BE = " + searchBE);
					BaseEntity search = this.baseEntity.getBaseEntityByCode(searchBE);
					QDataBaseEntityMessage msg = null;
					try {
						msg = QwandaUtils.fetchResults(search, getToken());
						if (msg != null) {
							msg.setParentCode(search.getCode());
							try {

								String str = JsonUtils.toJson(msg);
								JsonObject obj = new JsonObject(str);
								publishData(
										obj); /* send the reports to the frontend that are linked to this tree branch */
							} catch (Exception e) {
							}
						}
						sendCmdReportsSplitView(grpCode, searchBE);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} // send back the list of cached children associated wirth this report branch

				} else {
					println("Error!! The search BE is empty.");
				}
			} else {
				println("This is not related to Reports");
			}

		}
	}

	public void generateReport(final String parentCode) {
		//String grpCode = m.getData().getValue();
		String grpCode = parentCode;
		println("The Group Id is :: " + grpCode);
		if (grpCode != null) {
			BaseEntity user = getUser();
			if (user != null) {
				QDataBaseEntityMessage msg;
				// List<String> grpCodes = new ArrayList<String>();
				List<BaseEntity> searchEntityList = new ArrayList<BaseEntity>();
				if (grpCode.equalsIgnoreCase("GRP_REPORTS")) {
					for (EntityAttribute ea : user.getBaseEntityAttributes()) {
						// loop through all the "PRI_IS_" attributes
						if (ea.getAttributeCode().startsWith("PRI_IS")) {
							try { // handling exception when the value is not saved as valueBoolean
								if (ea.getValueBoolean()) {
									// get the role
									String role = ea.getAttributeCode().substring("PRI_IS_".length());
									// create GRP_REPORTS_ with role
									String reportGrp = "GRP_REPORTS_" + role;
									// Get stored QDataBaseEntityMessage for that report group
									QDataBaseEntityMessage storedMsg = getMappedBEs(reportGrp);
									if (storedMsg != null) {
										// Add all the searchEntity's available
										for (BaseEntity be : storedMsg.getItems()) {
											searchEntityList.add(be);
										}
									}
								}
							} catch (Exception e) {
								System.out.println("Error!! The attribute value is not in boolean format");
							}
						}
					}
					// Create virtual reports grp adding all the available search BEs for this user
					// based on the user role
					msg = new QDataBaseEntityMessage(searchEntityList.toArray(new BaseEntity[searchEntityList.size()]),
							"GRP_REPORTS_VIRTUAL", "LNK_CORE");
					grpCode = "GRP_REPORTS_VIRTUAL";
				} else {
					msg = getMappedBEs(grpCode); // send back the list of cached children associated with
					// this report branch
				}

				if (msg != null) {

					try {
						String str = JsonUtils.toJson(msg);
						JsonObject obj = new JsonObject(str);
						publishData(obj); /* send the reports to the frontend that are linked to this tree branch */
					} catch (Exception e) {
					}
				}
				sendCmdReportsSplitView(grpCode, null);
			} else {
				System.out.println("Error!!The user BE is null");
			}
		} else {
			System.out.println("Error!!The reports group code is null");
		}
	}

	/* Payments user updation */
	public void updatePaymentsUserInfo(String paymentsUserId, String attributeCode, String value,
			String paymentsAuthToken) {

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
						println("User updation response :: " + userResponsePOJO);

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
			String[] recipientArr = { getUser().getCode() };
			sendDirectToast(recipientArr, toastMessage, "warning");
		}
	}

	/*
	 * Converts payments error into Object and formats into a string error message
	 */
	public String getPaymentsErrorResponseMessage(String paymentsErrorResponseStr) {

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

	/* Create payments company */
	public String createCompany(BaseEntity companyBe, String paymentsUserId, String authtoken) {

		String companyId = null;
		BaseEntity userBe = getUser();
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
					println("payments company creation response ::" + createCompanyResponse);
					println("payments company obj : " + createCompanyResponseObj);
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
					sendDirectToast(recipientArr, toastMessage, "warning");
				}
			}

		}
		return companyId;
	}

	/* Payments company updation */
	/* Independent attribute value update. Not bulk */
	public void updatePaymentsCompany(String paymentsUserId, String companyId, String attributeCode, String value,
			String paymentsAuthToken) {

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
						println("Company updation response :: " + userResponsePOJO);

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
			String[] recipientArr = { getUser().getCode() };
			sendDirectToast(recipientArr, toastMessage, "warning");
		}
	}

	/* Bulk update of payments user info */
	/*
	 * If some information update is lost due to Payments-service-downtime, they
	 * will updated with this
	 */
	public void bulkPaymentsUserUpdate(BaseEntity userBe, String paymentsUserId, String paymentsAuthKey) {

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

	/* Bulk update for payments company info */
	/*
	 * If some information update is lost due to Payments-service-downtime, they
	 * will updated with this
	 */
	public void bulkPaymentsCompanyUpdate(BaseEntity userBe, BaseEntity companyBe, String paymentsUserId,
			String assemblyAuthKey) {

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

	/*
	 * Generic method to publish CMD_VIEW Message
	 *
	 */
	public void publishViewCmdMessage(final String viewType, final String rootCode) {
		QCmdMessage cmdViewMessage = new QCmdMessage("CMD_VIEW", viewType);
		JsonObject cmdViewMessageJson = new JsonObject().mapFrom(cmdViewMessage);
		cmdViewMessageJson.put("root", rootCode);
		publishCmd(cmdViewMessageJson);
		setLastLayout("LIST_VIEW", rootCode);
	}

	/* Creation of payment item */
	public String createPaymentItem(BaseEntity loadBe, BaseEntity offerBe, BaseEntity begBe, BaseEntity buyerBe,
			BaseEntity sellerBe, String paymentsToken) {
		String itemId = null;

		if (offerBe != null && begBe != null) {
			try {

				/* driverPriceIncGST = ownerPriceIncGST.subtract(feePriceIncGST) */
				Money buyerAmountWithoutFee = offerBe.getValue("PRI_OFFER_DRIVER_PRICE_INC_GST", null);
				/* If pricing calculation fails */
				if (buyerAmountWithoutFee == null) {
					throw new IllegalArgumentException(
							"Something went wrong during pricing calculations. Price for item cannot be empty");
				}

				/* Convert dollars into cents */
				Money roundedItemPriceInCents = PaymentUtils.getRoundedMoneyInCents(buyerAmountWithoutFee);
				/* Owner => Buyer */
				QPaymentsUser buyer = PaymentUtils.getPaymentsUser(buyerBe);

				/* Driver => Seller */
				QPaymentsUser seller = PaymentUtils.getPaymentsUser(sellerBe);

				/* get item name */
				String paymentsItemName = PaymentUtils.getPaymentsItemName(loadBe, begBe);
				println("payments item name ::" + paymentsItemName);

				/* Not mandatory */
				String begDescription = loadBe.getValue("PRI_DESCRIPTION", null);

				try {
					/* get fee */
					String paymentFeeId = createPaymentFee(offerBe, paymentsToken);
					System.out.println("payment fee Id ::" + paymentFeeId);
					String[] feeArr = { paymentFeeId };

					/* bundling all the info into Item object */
					QPaymentsItem item = new QPaymentsItem(paymentsItemName, begDescription,
							PaymentTransactionType.escrow, roundedItemPriceInCents.getNumber().doubleValue(),
							buyerAmountWithoutFee.getCurrency(), feeArr, buyer, seller);
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
				redirectToHomePage();

				String jobId = begBe.getValue("PRI_JOB_ID", null);
				BaseEntity userBe = getUser();

				/* Send toast */
				String toastMessage = "Payments item creation failed for the job with ID : #" + jobId + ", "
						+ e.getMessage();

				if (userBe != null) {
					String[] recipientArr = { userBe.getCode() };
					sendDirectToast(recipientArr, toastMessage, "warning");
				}

				/* Send slack notification */
				sendSlackNotification(toastMessage);
			}
		} else {
			String slackMessage = "Payment item creation would fail since begCode or offerCode is null. BEG CODE : "
					+ begBe.getCode() + ", OFFER CODE :" + offerBe.getCode();
			sendSlackNotification(slackMessage);
		}

		return itemId;
	}

	/* Creates a new fee in external payments-service from a offer baseEntity */
	private String createPaymentFee(BaseEntity offerBe, String paymentsToken) throws IllegalArgumentException {

		String paymentFeeId = null;
		try {
			/* get fee object with all fee-info */
			QPaymentsFee feeObj = PaymentUtils.getFeeObject(offerBe);
			if (feeObj != null) {
				try {
					/* Hit the fee creation API */
					String feeResponse = PaymentEndpoint.createFees(JsonUtils.toJson(feeObj), paymentsToken);
					QPaymentsFee feePojo = JsonUtils.fromJson(feeResponse, QPaymentsFee.class);

					/* Get the fee ID */
					paymentFeeId = feePojo.getId();
				} catch (PaymentException e) {
					String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
					throw new IllegalArgumentException(getFormattedErrorMessage);
				}
			}
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
		return paymentFeeId;
	}

	public Boolean makePayment(BaseEntity buyerBe, BaseEntity sellerBe, BaseEntity offerBe, BaseEntity begBe,
			String authToken) {

		Boolean isMakePaymentSuccess = false;
		if (begBe != null && offerBe != null && buyerBe != null && sellerBe != null) {

			try {
				String itemId = begBe.getValue("PRI_ITEM_ID", null);
				QMakePayment makePaymentObj = PaymentUtils.getMakePaymentObj(buyerBe, begBe);

				/* To get the type of payment (Bank account / card) */
				PaymentType paymentType = PaymentUtils.getPaymentMethodType(buyerBe,
						makePaymentObj.getAccount().getId());

				/*
				 * if the payment type is bank account, there is another step of debit
				 * authorization
				 */
				/* Step 1 for bankaccount : DEBIT AUTHORIZATION */
				if (paymentType != null && paymentType.equals(PaymentType.BANK_ACCOUNT)) {
					debitAuthorityForBankAccount(offerBe, makePaymentObj, authToken);
				}

				/* Step 2 for bankaccount : Make payment API call */
				/* Step 1 for card : Make payment API call */
				try {

					String paymentResponse = PaymentEndpoint.makePayment(itemId, JsonUtils.toJson(makePaymentObj),
							authToken);
					isMakePaymentSuccess = true;
					QPaymentsAssemblyItemResponse makePaymentResponseObj = JsonUtils.fromJson(paymentResponse,
							QPaymentsAssemblyItemResponse.class);

					/* save deposit reference as an attribute to beg */
					Answer depositReferenceAnswer = new Answer(begBe.getCode(), begBe.getCode(), "PRI_DEPOSIT_REFERENCE_ID", makePaymentResponseObj.getDepositReference());
					this.baseEntity.saveAnswer(depositReferenceAnswer);

				} catch (PaymentException e) {
					String getFormattedErrorMessage = getPaymentsErrorResponseMessage(e.getMessage());
					throw new IllegalArgumentException(getFormattedErrorMessage);
				}

			} catch (IllegalArgumentException e) {
				redirectToHomePage();
				String begTitle = begBe.getValue("PRI_TITLE", null);
				String sellerFirstName = sellerBe.getValue("PRI_FIRSTNAME", null);
				String[] recipientArr = { buyerBe.getCode() };
				String toastMessage = "Unfortunately, processing payment into " + sellerFirstName
						+ "'s account for the job - " + begTitle + " has failed. " + e.getMessage();
				sendDirectToast(recipientArr, toastMessage, "warning");
				sendSlackNotification(
						toastMessage + ". Job code : " + begBe.getCode() + ", offer code : " + offerBe.getCode());
			}
		} else {
			redirectToHomePage();
			String slackMessage = "Processing payment into driver's account for the job - " + begBe.getCode()
					+ " has failed. UserBE/BegBE/offerBE is null. User code :" + buyerBe.getCode() + ", Offer code :"
					+ offerBe.getCode();
			sendSlackNotification(slackMessage);
		}
		return isMakePaymentSuccess;
	}

	/*
	 * bank account payments needs to go through one more API call - Debit authority
	 */
	private void debitAuthorityForBankAccount(BaseEntity offerBe, QMakePayment makePaymentObj, String authToken)
			throws IllegalArgumentException {

		Money offerBuyerPriceString = offerBe.getValue("PRI_OFFER_DRIVER_PRICE_INC_GST", null);

		/* if price calculation fails, we handle it */
		if (offerBuyerPriceString == null) {
			throw new IllegalArgumentException(
					"Something went wrong during pricing calculation. Item price cannot be empty");
		}

		try {
			/* Get the rounded money in cents */
			Money offerPriceStringInCents = PaymentUtils.getRoundedMoneyInCents(offerBuyerPriceString);

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

	/* Fetch the one time use Payments card and bank tokens for a user */
	public String fetchOneTimePaymentsToken(String paymentsUserId, String paymentToken, AuthorizationPaymentType type) {
		String token = null;

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
			log.error("Exception occured during one-time payments token creation for user : " + getUser().getCode()
					+ ", Error message : " + e.getMessage());
		}
		return token;
	}

	/* release payment */
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
				Answer releasePaymentDisbursementAns = new Answer(getUser().getCode(), begBe.getCode(),
						"PRI_PAYMENTS_DISBURSEMENT_ID", depositReferenceId);
				answers.add(releasePaymentDisbursementAns);

				 this.baseEntity.saveAnswers(answers);
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
			sendDirectToast(recipientArr, toastMessage, "warning");

			/* send slack notification */
			sendSlackNotification(toastMessage + ". Job code : " + begBe.getCode());
		}
		return isReleasePayment;
	}

	/* disbursement of bank account for a user */
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

	/* Deletes a bank account */
	public Boolean deleteBankAccount(String bankAccountId, String authKey) {
		Boolean isDeleted = false;
		try {
			PaymentEndpoint.deleteBankAccount(bankAccountId, authKey);
			isDeleted = true;
		} catch (PaymentException e) {
		}
		return isDeleted;
	}

	/* Deletes a credit card */
	public Boolean deleteCard(String cardAccountId, String authKey) {
		Boolean isDeleted = false;
		try {
			PaymentEndpoint.deleteCardAccount(cardAccountId, authKey);
			isDeleted = true;
		} catch (PaymentException e) {
		}
		return isDeleted;
	}

	public void processAddresses(String realm)
	{
		// load in all the people in the db
		QDataBaseEntityMessage qMsg=null;
		String token = RulesUtils.generateServiceToken(realm);
		SearchEntity allPeople = new SearchEntity("SBE_ALL_PEOPLE", "All People")
				.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "PER_%")
				.setPageStart(0)
				.setPageSize(100000);
		try {
			qMsg = getSearchResults(allPeople,token);
		} catch (IOException e) {
			System.out.println("Error! Unable to get Search All People");

		}
		// check their addresses
		System.out.println("Processing "+qMsg.getReturnCount()+" people ");
		for (BaseEntity person : qMsg.getItems()) {
			System.out.println(person.getCode());
			if (!isAddressPresent(person.getCode())) {
				System.out.println("Bad one...");
			}
		}

	}


	public boolean isAddressPresent(final String personCode) {
		boolean ret = false;

		BaseEntity person = this.baseEntity.getBaseEntityByCode(personCode);

		if (person.containsEntityAttribute("PRI_ADDRESS_ADDRESS1")) {
			if (person.containsEntityAttribute("PRI_ADDRESS_COUNTRY")) {
				if (person.containsEntityAttribute("PRI_ADDRESS_FULL")) {
					if (person.containsEntityAttribute("PRI_ADDRESS_LATITUDE")) {
						if (person.containsEntityAttribute("PRI_ADDRESS_LONGITUDE")) {
							if (person.containsEntityAttribute("PRI_ADDRESS_POSTCODE")) {
								if (person.containsEntityAttribute("PRI_ADDRESS_SUBURB")) {
									return true;
								}
							}
						}
					}
				}
			}
		}

		// okay, need to regenerate address attributes
		String jsonAddress = person.getValue("PRI_ADDRESS_JSON", null);
		if (jsonAddress != null) {

			QDataAnswerMessage msg = new QDataAnswerMessage(
					new Answer(personCode, personCode, "PRI_ADDRESS_JSON", jsonAddress));
			List<Answer> answers = processAddressAnswers(msg);
			this.baseEntity.saveAnswers(answers);
			System.out.println(person.getCode()+" fixed using existing JSON");
		} else {
			String jsonFull = person.getValue("PRI_ADDRESS_FULL", null);
			if (jsonFull != null) {
				String[] fullAddress = jsonFull.split(",");
				String city = fullAddress[1];
				List<Answer> answers = new ArrayList<Answer>();
				answers.add(new Answer(personCode, personCode, "PRI_ADDRESS_CITY", city));
				answers.add(new Answer(personCode, personCode, "PRI_ADDRESS_SUBURB", city));
				this.baseEntity.saveAnswers(answers);
				System.out.println(person.getCode()+" fixed using existing Full Address");
			} else {
				log.error("ERROR!! " + personCode + " does not contain Address Full");
			}
		}
		return ret;
	}

}
