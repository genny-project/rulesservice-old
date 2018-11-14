package life.genny.rules;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;

import org.apache.commons.lang.StringUtils;
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
// import life.genny.qwanda.entity.NavigationType;
import life.genny.qwanda.message.QBaseMSGAttachment;
import life.genny.qwanda.message.QBaseMSGMessageTemplate;
import life.genny.qwanda.message.QBulkMessage;
import life.genny.qwanda.message.QCmdGeofenceMessage;
import life.genny.qwanda.message.QCmdLayoutMessage;
import life.genny.qwanda.message.QCmdMessage;
import life.genny.qwanda.message.QCmdReloadMessage;
import life.genny.qwanda.message.QCmdViewFormMessage;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataAskMessage;
import life.genny.qwanda.message.QDataAttributeMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwanda.message.QDataGPSMessage;
import life.genny.qwanda.message.QDataMessage;
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
import life.genny.qwandautils.GennySettings;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.KeycloakUtils;
import life.genny.qwandautils.MessageUtils;
import life.genny.qwandautils.QwandaMessage;
import life.genny.qwandautils.QwandaUtils;
import life.genny.security.SecureResources;
import life.genny.utils.BaseEntityUtils;
import life.genny.utils.CacheUtils;
import life.genny.utils.DateUtils;
import life.genny.utils.MoneyHelper;
import life.genny.utils.PaymentEndpoint;
import life.genny.utils.PaymentUtils;
import life.genny.utils.QDataJsonMessage;
import life.genny.utils.QuestionUtils;
import life.genny.utils.RulesUtils;
import life.genny.utils.VertxUtils;
import life.genny.utils.Layout.LayoutUtils;
//import life.genny.rules.Layout.LayoutUtils;
import life.genny.utils.Layout.LayoutViewData;

public class QRules {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	public static final String projectUrl = System.getenv("PROJECT_URL");

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
			/* TODO: to update so it is static */
			this.baseEntity = new BaseEntityUtils(GennySettings.qwandaServiceUrl, this.token, decodedTokenMap, realm());
			this.layoutUtils = new LayoutUtils(GennySettings.qwandaServiceUrl, this.token, decodedTokenMap, realm());
			this.cacheUtils = new CacheUtils(GennySettings.qwandaServiceUrl, this.token, decodedTokenMap, realm());
			this.cacheUtils.setBaseEntityUtils(this.baseEntity);

			// this.paymentUtils = new PaymentUtils(QRules.qwandaServiceUrl, this.token,
			// decodedTokenMap, realm());
		} catch (Exception e) {

		}
	}

	/**
	 * @return the token
	 */
	public String getToken() {
		return token;
	}

	/**
	 * @param token the token to set
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
	 * @param eventBus the eventBus to set
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
	 * @param decodedTokenMap the decodedTokenMap to set
	 */
	public void setDecodedTokenMap(Map<String, Object> decodedTokenMap) {
		this.decodedTokenMap = decodedTokenMap;
	}

	/**
	 * @return current realm
	 */
	public String realm() {

		String str = getAsString("realm");
		if (GennySettings.devMode || (GennySettings.defaultLocalIP.equals(GennySettings.hostIP))) {
			str = GennySettings.mainrealm; // TODO, I don't like this, but...
		}
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
	 * @param state the state to set
	 */
	public void setState(String key) {
		stateMap.put(key.toUpperCase(), true);
		// println("STATE " + key + " SET", RulesUtils.ANSI_RED);
		update();
	}

	/**
	 * @param state the state to set
	 */
	/* added by anish */
	public void setState(Boolean key) {
		stateMap.put(key.toString().toUpperCase(), true);
		// println("STATE " + key + " SET", RulesUtils.ANSI_RED);
		update();
	}

	/**
	 * @param state the state to clear
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

	public void setPermanentObject(String key, Object obj) {
		VertxUtils.putObject(this.realm(), "CACHE", key, obj);
	}

	public <T> T getPermanentObject(String key, Type clazz) {
		return VertxUtils.getObject(this.realm(), "CACHE", key, clazz);
	}

	@SuppressWarnings("unchecked")
	public <T> T getAsList(final String key) {
		return (T) get(key);
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

	public SearchEntity getAsSearchEntity(final String key) {
		return (SearchEntity) get(key);
	}

	public HashMap<String, Object> getAsHashMap(final String key) {
		return (HashMap) get(key);
	}
	
	public HashMap<String, String> getAsHashMap1(final String key) {
		return (HashMap) get(key);
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
		// if ("service".equalsIgnoreCase(username)) {
		// println("***** SERVICE USER *********** - getUser()");
		// } else {
		// println("***** "+code+" USER *********** - getUser()");
		// }

		return be;
	}

	public BaseEntity getUserCompany() {

		BaseEntity company = this.baseEntity.getParent(this.getUser().getCode(), "LNK_STAFF");
		return company;
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
		publishBaseEntityByCode(be, null, null, null);
	}

	public void publishBaseEntityByCode(List<BaseEntity> bes) {

		BaseEntity[] itemArray = bes.toArray(new BaseEntity[0]);
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray);
		String[] recipientCodes = { this.getUser().getCode() };
		msg.setRecipientCodeArray(recipientCodes);
		publishData(msg, recipientCodes);
	}

	/* Publishes BaseEntity with replace true/false */
	public void publishBaseEntityByCode(final String be, final Boolean replace) {

		BaseEntity item = this.baseEntity.getBaseEntityByCode(be);
		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		String[] recipientCodes = { this.getUser().getCode() };
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setReplace(replace);
		publishData(msg, recipientCodes);
	}

	/* Publishes BaseEntity with replace true/false */
	public void publishBaseEntityByCode(final String be, final Boolean replace, int level) {

		BaseEntity item = this.baseEntity.getBaseEntityByCode(be);
		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		String[] recipientCodes = { this.getUser().getCode() };
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setReplace(replace);
		msg.setShouldDeleteLinkedBaseEntities(level);
		publishData(msg, recipientCodes);
	}

	public void publishBaseEntityByCode(final String be, final String parentCode, final String linkCode) {

		BaseEntity item = this.baseEntity.getBaseEntityByCode(be);
		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);

		String[] recipientCodes = { this.getUser().getCode() };
		msg.setRecipientCodeArray(recipientCodes);
		publishData(msg, recipientCodes);
	}

	public void publishBaseEntityByCode(BaseEntity[] bes, final String parentCode, final String linkCode,
			String linkValue) {

		List<BaseEntity> list = Arrays.asList(bes);
		this.publishBaseEntityByCode(list, parentCode, linkCode, linkValue);
	}

	public void publishBaseEntityByCode(List<BaseEntity> bes, final String parentCode, final String linkCode,
			String linkValue) {

		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(bes.toArray(new BaseEntity[0]), parentCode, linkCode,
				linkValue);

		String[] recipientCodes = { this.getUser().getCode() };
		msg.setRecipientCodeArray(recipientCodes);
		publishData(msg, recipientCodes);
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

	/* Publish BaseEntity with LinkValue Set */
	public void publishBaseEntityByCode(final String be, final String parentCode, final String linkCode,
			final String[] recipientCodes, final String linkValue) {

		BaseEntity item = this.baseEntity.getBaseEntityByCode(be);
		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setLinkValue(linkValue);
		publishData(msg, recipientCodes);

	}

	/* Publish BaseEntityList with LinkValue Set */
	public void publishBaseEntityByCode(final List<BaseEntity> items, final String parentCode, final String linkCode,
			final String[] recipientCodes, final String linkValue) {

		BaseEntity[] itemArray = items.toArray(new BaseEntity[0]);
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setLinkValue(linkValue);
		publishData(msg, recipientCodes);

	}

	/* Publish BaseEntityList with LinkValue Set */
	public void publishBaseEntityByCode(final List<BaseEntity> items, final String parentCode, final String linkCode,
			final String[] recipientCodes, final String linkValue, final Boolean delete) {

		BaseEntity[] itemArray = items.toArray(new BaseEntity[0]);
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setLinkValue(linkValue);
		msg.setDelete(delete);
		publishData(msg, recipientCodes);

	}

	/* Publish BaseEntityList with LinkValue Set */
	public void publishBaseEntityByCode(final List<BaseEntity> items, final String parentCode, final String linkCode,
			final String[] recipientCodes, final String linkValue, final Boolean delete, Boolean replace) {

		BaseEntity[] itemArray = items.toArray(new BaseEntity[0]);
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setLinkValue(linkValue);
		msg.setDelete(delete);
		msg.setReplace(replace);
		publishData(msg, recipientCodes);
	}

	/* Publish BaseEntityList with LinkValue Set */
	public void publishBaseEntityByCode(final List<BaseEntity> items, final String parentCode, final String linkCode,
			final String[] recipientCodes, final String linkValue, final Boolean delete, Boolean replace, int level) {

		BaseEntity[] itemArray = items.toArray(new BaseEntity[0]);
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setLinkValue(linkValue);
		msg.setDelete(delete);
		msg.setReplace(replace);
		msg.setShouldDeleteLinkedBaseEntities(level);
		publishData(msg, recipientCodes);

	}

	/* Publish BaseEntityList with LinkValue Set */
	public void publishBaseEntityByCode(BaseEntity be, final String parentCode, final String linkCode,
			final String[] recipientCodes, final String linkValue, final Boolean replace) {

		BaseEntity[] itemArray =  new BaseEntity[1];
		itemArray[0] = be;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setLinkValue(linkValue);
		msg.setReplace(replace);
		publishData(msg, recipientCodes);
	}

	/* Publish BaseEntityList with LinkValue Set */
	public void publishBaseEntityByCode(String beCode, final String parentCode, final String linkCode,
			final String[] recipientCodes, final String linkValue, final Boolean delete) {

		BaseEntity be = this.baseEntity.getBaseEntityByCode(beCode);
		BaseEntity[] itemArray =  new BaseEntity[1];
		itemArray[0] = be;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		msg.setLinkValue(linkValue);
		msg.setDelete(delete);
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

	public void publishBaseEntityByCode(final BaseEntity item, final String parentCode, final String linkCode) {

		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		String[] recipients = new String[1];
		recipients[0] = this.getUser().getCode();
		msg.setRecipientCodeArray(recipients);
		publishData(msg, recipients);
	}

	public void publishBaseEntityByCode(final List<BaseEntity> items, final String parentCode, final String linkCode) {
		this.publishBaseEntityByCode(items, parentCode, linkCode, false);
	}

	/* Publishes baseEntity with replace TRUE */
	public void publishBaseEntityByCode(final List<BaseEntity> items, final String parentCode, final String linkCode,
			Boolean replace) {

		BaseEntity[] itemArray = items.toArray(new BaseEntity[0]);
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		String[] recipients = new String[1];
		recipients[0] = this.getUser().getCode();
		msg.setRecipientCodeArray(recipients);
		msg.setReplace(replace);
		publishData(msg, recipients);
	}

	public void publishBaseEntityByCode(final BaseEntity item, final String parentCode, final String linkCode,
			final String[] recipientCodes) {

		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
		publishData(msg, recipientCodes);

	}

	public <T extends QMessage> void publishCmd(T msg) {
		msg.setToken(getToken());
		publish("cmds", JsonUtils.toJson(msg));
	}

	public <T extends QMessage> void publishCmd(T msg, final String[] recipientCodes) {
		msg.setToken(getToken());
		publish("cmds", JsonUtils.toJson(msg));
	}

	public <T extends QMessage> void publishData(T msg, final String[] recipientCodes) {
		msg.setToken(getToken());
		publish("webdata", JsonUtils.toJson(msg));
	}

	public <T extends QMessage> void publish(final String busChannel, T msg, final String[] recipientCodes) {
		msg.setToken(getToken());
		publish(busChannel, JsonUtils.toJson(msg));
	}

	public void publishBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart,
			Integer pageSize, Boolean cache) {

		String json = RulesUtils.getBaseEntitysJsonByParentAndLinkCode(GennySettings.qwandaServiceUrl,
				getDecodedTokenMap(), getToken(), parentCode, linkCode);
		publish("cmds", json);
	}

	public void publishBaseEntitysByParentAndLinkCodeWithAttributes(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize, Boolean cache) {

		BaseEntity[] beArray = RulesUtils.getBaseEntitysArrayByParentAndLinkCodeWithAttributes(
				GennySettings.qwandaServiceUrl, getDecodedTokenMap(), getToken(), parentCode, linkCode, pageStart,
				pageSize);
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beArray, parentCode, linkCode);
		msg.setToken(getToken());

		publish("cmds", msg);

	}

	public void postSlackNotification(String webhookURL, JsonObject message) throws IOException {

		try {

			final HttpClient client = HttpClientBuilder.create().build();

			final HttpPost post = new HttpPost(webhookURL);
			final StringEntity input = new StringEntity(message.toString());

			input.setContentType("application/json");
			post.setEntity(input);

			client.execute(post);
		} catch (IOException e) {
			this.println(e);
		}
	}

	public void sendReloadPage() {

		QCmdReloadMessage cmdReload = new QCmdReloadMessage();
		this.publishCmd(cmdReload);
	}

	/*
	 * TRADITIONAL WAY OF SENDING EMAIL -> send email with recipientArr, NOT direct
	 * list of emailIds
	 */
	public void sendMessage(String[] recipientArray, HashMap<String, String> contextMap, String templateCode,
			String messageType) {

		/* setting attachmentList as null, to reuse sendMessageMethod and reduce code */
		sendMessage(recipientArray, contextMap, templateCode, messageType, null);

	}

	/*
	 * TRADITIONAL WAY OF SENDING EMAIL -> send email with attachments and with
	 * recipientArr, NOT direct list of emailIds
	 */
	public void sendMessage(String[] recipientArray, HashMap<String, String> contextMap, String templateCode,
			String messageType, List<QBaseMSGAttachment> attachmentList) {

		/* setting "to" as null, to reuse sendMessageMethod and reduce code */
		sendMessage(recipientArray, contextMap, templateCode, messageType, attachmentList, null);

	}

	/* SENDING EMAIL With DIRECT ARRAY OF EMAILIDs and no attachments */
	public void sendMessage(String[] to, String templateCode, HashMap<String, String> contextMap, String messageType) {

		/*
		 * setting attachmentList and recipientArr as null, to reuse sendMessageMethod
		 * and reduce code
		 */
		sendMessage(null, contextMap, templateCode, messageType, null, to);

	}

	/* SENDING EMAIL With DIRECT ARRAY OF EMAILIDs and having attachments */
	/**
	 * @param to
	 * @param templateCode
	 * @param contextMap     : key-value map for merging
	 * @param messageType    : Can be "EMAIL","SMS"
	 * @param attachmentList : Incase of email attachments
	 * @example userBe is a user BaseEntity <br>
	 *          String userEmailId = userBe.getValue("PRI_USER_EMAIL", null); //Can
	 *          use any appropriate userEmailId AttributeCode <br>
	 *          String[] directRecipientEmailIds = { userEmailId }; <br>
	 * 
	 *          HashMap<String, String> contextMap = new HashMap<>(); <br>
	 *          contextMap.put("USER", userBe); <br>
	 * 
	 *          rules.sendMessage(directRecipientEmailIds, "MSG_USER_CONTACTED",
	 *          contextMap, "EMAIL");
	 */
	public void sendMessage(String[] to, String templateCode, HashMap<String, String> contextMap, String messageType,
			List<QBaseMSGAttachment> attachmentList) {

		/* setting recipientArr as null, to reuse sendMessageMethod and reduce code */
		sendMessage(null, contextMap, templateCode, messageType, attachmentList, to);

	}

	// MAIN METHOD FOR SENDMESSAGES
	public void sendMessage(String[] recipientArray, HashMap<String, String> contextMap, String templateCode,
			String messageType, List<QBaseMSGAttachment> attachmentList, String[] to) {

		/* unsubscribe link for the template */
		String unsubscribeUrl = getUnsubscribeLinkForEmailTemplate(GennySettings.projectUrl, templateCode);
		JsonObject message = null;

		/* Adding project code to context */
		String projectCode = "PRJ_" + GennySettings.mainrealm.toUpperCase();
		this.println("project code for messages ::" + projectCode);
		contextMap.put("PROJECT", projectCode);

		/* adding unsubscribe url */
		if (unsubscribeUrl != null) {
			contextMap.put("URL", unsubscribeUrl);
		}

		if (recipientArray != null && recipientArray.length > 0) {

			if (attachmentList == null) {
				message = MessageUtils.prepareMessageTemplate(templateCode, messageType, contextMap, recipientArray,
						getToken());
			} else {
				message = MessageUtils.prepareMessageTemplateWithAttachments(templateCode, messageType, contextMap,
						recipientArray, attachmentList, getToken());
			}

		} else {
			log.error("Recipient array is null");
		}

		if (to != null && to.length > 0) {

			if (attachmentList == null) {
				message = MessageUtils.prepareMessageTemplateForDirectRecipients(templateCode, messageType, contextMap,
						to, getToken());
			} else {
				message = MessageUtils.prepareMessageTemplateWithAttachmentForDirectRecipients(templateCode,
						messageType, contextMap, to, attachmentList, getToken());
			}

		}

		publish("messages", message);

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
			be = QwandaUtils.createUser(GennySettings.qwandaServiceUrl, getToken(), username, firstname, lastname,
					email, realm, name, keycloakId);
			VertxUtils.writeCachedJson(be.getCode(), JsonUtils.toJson(be));
			be = getUser();
			set("USER", be);
			println("New User Created " + be);

			/*
			 * we check if project requires to send slack notification on user registrtaion
			 */
			Boolean sendSlackNotification = this.getProject().getValue("PRI_SEND_SLACK_NOTIFICATION_ON_REGISTRATION",
					true);

			if (sendSlackNotification == true) {

				/* slack notification message for new registration */
				String message = "New registration: " + firstname + " " + lastname + ". Email: " + email;

				/* send registration notification on slack */
				this.sendSlackNotification(message);
			}

		} catch (IOException e) {
			log.error("Error in Creating User ");
		}
		return be;
	}

	public BaseEntity createUser(String firstname, String lastname, String name, String username, String email) {
		return this.createUser(firstname, lastname, name, username, email, null);
	}

	public BaseEntity createUser(String firstname, String lastname, String name, String username, String email,
			String keycloakId) {

		BaseEntity be = null;

		try {

			/* we capitalise the variables */
			firstname = StringUtils.capitalize(firstname);
			lastname = StringUtils.capitalize(lastname);
			name = StringUtils.capitalize(name);

			String token = RulesUtils.generateServiceToken(realm());

			String realm = realm();
			if (GennySettings.devMode) {
				realm = "genny";
			}

			/* if the keycloak id, we need to create a keycloak account for this user */
			if (keycloakId == null) {
				keycloakId = KeycloakUtils.createUser(token, realm, username, firstname, lastname, email);
			}

			/* we create the user in the system */
			be = QwandaUtils.createUser(getQwandaServiceUrl(), getToken(), username, firstname, lastname, email, realm,
					name, keycloakId);
			VertxUtils.writeCachedJson(be.getCode(), JsonUtils.toJson(be));
			// be = getUser();
			set("USER", be);
			println("New User Created " + be);
			this.setState("DID_CREATE_NEW_USER");

			/* send notification for new registration */
			String message = "New registration: " + firstname + " " + lastname + ". Email: " + email;
			this.sendSlackNotification(message);

		} catch (IOException e) {
			this.sendToastNotification(e.getMessage(), "error");
		}

		return be;
	}

	public void sendLayout(final String layoutCode, final String layoutPath) {
		this.sendLayout(layoutCode, layoutPath, realm());
	}

	public void sendLayout(final String layoutCode, final String layoutPath, final String folderName) {

		println("Loading layout: " + folderName + "/" + layoutPath);
		String layout = RulesUtils.getLayout(folderName, layoutPath);
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
		String sublayoutString = RulesUtils.getLayout(realm(), sublayoutPath);
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
		String sublayoutString = RulesUtils.getLayout(realm(), "/" + sublayoutPath);
		cmdJobSublayoutJson.put("items", sublayoutString);
		cmdJobSublayoutJson.put("token", getToken());
		cmdJobSublayoutJson.put("root", root != null ? root : "test");

		publish("cmds", cmdJobSublayoutJson);
	}

	public void navigateBack() {
		this.navigate("ROUTE_BACK");
	}

	public void navigateTo(String newRoute) {
		this.navigateTo(newRoute, null, false);
	}

	public void navigateTo(String newRoute, Boolean isModal) {
		this.navigateTo(newRoute, null, isModal);
	}

	public void navigateTo(String newRoute, JsonObject params) {
		this.navigateTo(newRoute, params, false);
	}

	public void navigateTo(String newRoute, JsonObject params, Boolean isModal) {
		this.navigate("ROUTE_CHANGE", newRoute, params, isModal);
	}

	private void navigate(String navigationType) {
		this.navigate(navigationType, null);
	}

	private void navigate(String navigationType, String newRoute) {
		this.navigate(navigationType, newRoute, null);
	}

	private void navigate(String navigationType, String newRoute, JsonObject params) {
		this.navigate(navigationType, newRoute, null, false);
	}

	private void navigate(String navigationType, String newRoute, JsonObject params, Boolean isModal) {

		this.println("NAVIGATION: " + navigationType);
		this.println("Navigating to: " + newRoute);
		QCmdMessage cmdNavigate = new QCmdMessage(navigationType, newRoute);
		JsonObject json = JsonObject.mapFrom(cmdNavigate);
		json.put("token", getToken());

		if (params != null) {
			json.put("params", params);
		}

		if (isModal != null && isModal == true) {
			json.put("modal", true);
		}

		publish("cmds", json);
	}

	public void showLoading(String text, boolean isPopup) {

		if (text == null) {
			text = "Loading...";
		}

		String viewCmd = isPopup ? "CMD_POPUP" : "CMD_VIEW";
		QCmdMessage cmdLoading = new QCmdMessage(viewCmd, "LOADING");
		JsonObject json = JsonObject.mapFrom(cmdLoading);
		json.put("root", text);
		json.put("token", getToken());
		publish("cmds", json);
	}

	public void showLoading(String text) {
		this.showLoading(text, false);
	}

	public void sendParentLinks(final String targetCode, final String linkCode) {

		JsonArray latestLinks;

		try {

			latestLinks = new JsonArray(QwandaUtils.apiGet(
					getQwandaServiceUrl() + "/qwanda/entityentitys/" + targetCode + "/linkcodes/" + linkCode,
					getToken()));

			QDataJsonMessage msg = new QDataJsonMessage("LINK_CHANGE", latestLinks);

			msg.setToken(getToken());
			final JsonObject json = RulesUtils.toJsonObject(msg);
			json.put("items", latestLinks);
			publishData(json);
		} catch (IOException e) {
			this.println(e.getMessage());
		}
	}

	/**
	 * @return the state
	 */
	public Map<String, Boolean> getState() {
		return stateMap;
	}

	/**
	 * @param state the state to set
	 */
	public void setState(Map<String, Boolean> state) {
		this.stateMap = state;
	}

	/**
	 * @return the qwandaserviceurl
	 */
	public static String getQwandaServiceUrl() {
		return GennySettings.qwandaServiceUrl;
	}

	/**
	 * @return the devmode
	 */
	public static Boolean getDevmode() {
		return GennySettings.devMode;
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
		publish("cmds", msg);
	}

	public void publishCmd(final BaseEntity be, final String aliasCode) {
		this.publishCmd(be, aliasCode, null);
	}

	public QDataBaseEntityMessage publishData(final BaseEntity be, final String[] recipientsCode) {
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be, null);
		msg.setRecipientCodeArray(recipientsCode);
		msg.setToken(getToken());
		publish("webdata", msg);
		return msg;
	}

	public QMessage publishData(final Answer answer, final String[] recipientsCode) {
		QDataAnswerMessage msg = new QDataAnswerMessage(answer);
		msg.setRecipientCodeArray(recipientsCode);
		msg.setToken(getToken());
		publish("webdata", RulesUtils.toJsonObject(msg));
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
		publish("webdata", msg);
	}

	public void publishCmd(final QwandaMessage msg) {

		if (msg.askData != null && msg.askData.getMessages().length > 0) {
			this.publish("cmds", msg.askData);
		}

		if (msg.asks != null) {
			this.publish("cmds", msg.asks);
		}
	}

	public QMessage publishCmd(final QDataMessage msg) {
		msg.setToken(getToken());
		String json = JsonUtils.toJson(msg);
		publish("cmds", json);
		return msg;
	}

	public QMessage publishCmd(final QDataAskMessage msg) {
		msg.setToken(getToken());
		String json = JsonUtils.toJson(msg);
		publish("cmds", json);
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
		publish("webdata", JsonUtils.toJson(msg));
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

		publish("cmds", jsonObj);

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

	public void publishEventBusData(final QDataAnswerMessage msg) {
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
	}

	public void publishData(final QDataAnswerMessage msg) {
		msg.setToken(getToken());
		publish("webdata", JsonUtils.toJson(msg));
	}

	public void publishData(final Answer answer) {
		QDataAnswerMessage msg = new QDataAnswerMessage(answer);
		msg.setToken(getToken());
		publish("webdata", JsonUtils.toJson(msg));
	}

	public void publishData(final List<Answer> answerList) {
		Answer[] answerArray = answerList.toArray(new Answer[answerList.size()]);
		QDataAnswerMessage msg = new QDataAnswerMessage(answerArray);
		msg.setToken(getToken());
		publish("webdata", JsonUtils.toJson(msg));
	}

	public void publishCmd(final Answer answer) {
		QDataAnswerMessage msg = new QDataAnswerMessage(answer);
		msg.setToken(getToken());
		publish("cmds", JsonUtils.toJson(msg));
	}

	public void publishData(final QDataAskMessage msg) {
		msg.setToken(getToken());
		publish("webdata", RulesUtils.toJsonObject(msg));
	}

	public void publishData(final QDataAttributeMessage msg) {
		msg.setToken(getToken());
		publish("webdata", JsonUtils.toJson(msg));
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
		publish("webdata", RulesUtils.toJsonObject(msg));
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
		publish("webdata", json);

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

		publish("webdata", json);
	}

	public void publishMsg(final QMSGMessage msg) {

		msg.setToken(getToken());
		publish("messages", RulesUtils.toJsonObject(msg));
	}

	public void publish(String channel, final QBulkMessage msg) {
		msg.setToken(getToken());
		VertxUtils.publish(getUser(), channel, msg);
	}

	public void publish(String channel, final QDataAnswerMessage msg) {
		msg.setToken(getToken());
		VertxUtils.publish(getUser(), channel, JsonUtils.toJson(msg));
	}

	public void publish(String channel, final QDataAskMessage msg) {

		msg.setToken(getToken());
		publish(channel, JsonUtils.toJson(msg));
	}

	public void publish(String channel, Object payload) {
		VertxUtils.publish(getUser(), channel, payload);
	}

	public String loadUserRole() {

		BaseEntity user = this.getUser();
		String userRole = null;

		if (user != null) {

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
							userRole = role.getAttributeCode();
						}
					}
				}
			}
		}

		if (userRole != null) {
			this.setState("ROLE_FOUND");
		} else {
			this.setState("ROLE_NOT_FOUND");
		}

		return userRole;
	}

	/*
	 * Gets all the tags from the source attribute and sets the bit value of all the
	 * tags in the new targetAttributeCode for the same userCode passed
	 */
	public void setBitMaskValueForTag(final String userCode, final String sourceAttributeCode,
			final String targetAttributeCode) {

		Long categoryTypeInBits = 0L;
		BaseEntity user = this.baseEntity.getBaseEntityByCode(userCode);
		if (user != null) {

			/* get the list of category types user has */
			List<String> productCategoryList = this.baseEntity.getBaseEntityAttrValueList(user, sourceAttributeCode);
			if (productCategoryList != null) {

				for (String loadTypeCode : productCategoryList) {

					BaseEntity loadCat = this.baseEntity.getBaseEntityByCode(loadTypeCode);

					/* get the bit value for the SEL BE */
					Long bitValueStr = loadCat.getValue("PRI_BITMASK_VALUE", null);
					if (bitValueStr != null) {

						/*
						 * Combine all the bit values to the users category type attribute using or
						 * operator
						 */
						categoryTypeInBits = categoryTypeInBits | bitValueStr; // Long.parseLong(bitValueStr);
					}
				}
			} else {
				println("Error!! The productCategoryList is null");
			}

			this.baseEntity
					.saveAnswer(new Answer(userCode, userCode, targetAttributeCode, categoryTypeInBits.toString()));
		}
	}

	/*
	 * Returns the default Bit Mapped tag of all the category tags
	 */
	public Long getDefaultBitMaskedTag(final String parentCode, final String linkCode) {
		Long defaultBitMappedTag = 0L;

		List<BaseEntity> childBE = this.baseEntity.getLinkedBaseEntities(parentCode, linkCode);
		if (childBE != null) {
			for (BaseEntity be : childBE) {
				Long bitValue = be.getValue("PRI_BITMASK_VALUE", null);

				if (bitValue != null) {
					/* Combine all the bit values to the default BitMap Tag using or operator */
					defaultBitMappedTag = defaultBitMappedTag | bitValue;
				}
			}

		} else {
			System.out.println("Error! The Tag list is empty");
		}
		return defaultBitMappedTag;

	}

	/*
	 * Get all Base Entities based on search Prefix (BE prefix) and the product type
	 * code
	 */
	public List<BaseEntity> getAllBaseEntitiesBasedOnTag(final String searchPrefix, final String tagCode) {
		BaseEntity selBE = this.baseEntity.getBaseEntityByCode(tagCode);
		if (selBE != null) {
			Long bitMaskValue = selBE.getValue("PRI_BITMASK_VALUE", null);
			// String realm = realm();
			String serviceToken = RulesUtils.generateServiceToken(realm());
			QDataBaseEntityMessage msg = null;
			List<BaseEntity> beList = new ArrayList<BaseEntity>();
			if (bitMaskValue != null) {
				SearchEntity searchBE = new SearchEntity(drools.getRule().getName(), "Get all BE")
						.addSort("PRI_CREATED", "Created", SearchEntity.Sort.DESC)
						.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, searchPrefix + "_%")
						.addFilter("PRI_PRODUCT_CATEGORY_TAG_BITMASKED", SearchEntity.Filter.BIT_MASK_POSITIVE,
								bitMaskValue)
						.setPageStart(0).setPageSize(10000);
				try {
					log.info("The search Entity :: " + JsonUtils.toJson(searchBE));
					// msg = getSearchResults(searchBE);
					msg = QwandaUtils.fetchResults(searchBE, serviceToken);
					log.info("the msg is :: " + msg);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				if (msg != null && msg.getItems().length != 0) {
					BaseEntity[] beArray = msg.getItems();
					for (BaseEntity be : beArray) {
						beList.add(be);
					}
				} else
					log.info("Error! The search result is null.");
			} else {
				log.info("Error! The bitmask value of the tagCode is null.");

			}
			return beList;
		}
		return null;
	}

	public Boolean doesQuestionGroupExist(String questionGroupCode) {
		return QuestionUtils.doesQuestionGroupExist(this.getUser().getCode(), this.getUser().getCode(),
				questionGroupCode, this.token);
	}

	public Boolean sendQuestions(String sourceCode, String targetCode, String questionGroupCode) {
		return this.sendQuestions(sourceCode, targetCode, questionGroupCode, sourceCode, true);
	}

	public Boolean sendQuestions(String sourceCode, String targetCode, String questionGroupCode,
			String stakeholderCode) {
		return this.sendQuestions(sourceCode, targetCode, questionGroupCode, stakeholderCode, true);
	}

	public Boolean sendQuestions(String sourceCode, String targetCode, String questionGroupCode,
			Boolean pushSelection) {
		return this.sendQuestions(sourceCode, targetCode, questionGroupCode, sourceCode, pushSelection);
	}

	public Boolean sendQuestions(String sourceCode, String targetCode, String questionGroupCode, String stakeholderCode,
			Boolean pushSelection) {
		
		QwandaMessage questions = QuestionUtils.askQuestions(sourceCode, targetCode, questionGroupCode, this.token,
				stakeholderCode, pushSelection);
		if (questions != null) {

			this.publishCmd(questions);
			return true;
		}

		return false;
	}

	public Ask getQuestion(String sourceCode, String targetCode, String questionCode) {

		QDataAskMessage askMessage = QuestionUtils.getAsks(sourceCode, targetCode, questionCode, this.token);
		if (askMessage != null && askMessage.getItems().length > 0) {
			return askMessage.getItems()[0];
		}

		return null;
	}

	public QwandaMessage getQuestions(String sourceCode, String targetCode, String questionGroupCode) {
		return this.getQuestions(sourceCode, targetCode, questionGroupCode, null);
	}

	private QwandaMessage getQuestions(String sourceCode, String targetCode, String questionGroupCode,
			String stakeholderCode) {
		return QuestionUtils.askQuestions(sourceCode, targetCode, questionGroupCode, this.token, stakeholderCode, true);
	}

	public void askQuestions(String sourceCode, String targetCode, String questionGroupCode) {
		this.askQuestions(sourceCode, targetCode, questionGroupCode, false);

	}

	public void askQuestions(String sourceCode, String targetCode, String questionGroupCode, Boolean isPopup) {

		if (this.sendQuestions(sourceCode, targetCode, questionGroupCode)) {

			/* Layout V1 */
			QCmdViewFormMessage cmdFormView = new QCmdViewFormMessage(questionGroupCode);
			cmdFormView.setIsPopup(isPopup);
			publishCmd(cmdFormView);

			this.navigateTo("/questions/" + questionGroupCode, isPopup);
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
		String text = dataJson.getString("message");
		String chatCode = dataJson.getString("itemCode");
		String userCode = dataJson.getString("userCode");

		if (text != null && chatCode != null && userCode != null) {

			/* creating new message */
			BaseEntity newMessage = QwandaUtils.createBaseEntityByCode(QwandaUtils.getUniqueId("MSG", userCode),
					"message", getQwandaServiceUrl(), getToken());
			if (newMessage != null) {

				List<BaseEntity> stakeholders = this.baseEntity.getLinkedBaseEntities(chatCode, "LNK_USER");
				if (stakeholders != null) {
					String[] recipientCodeArray = new String[stakeholders.size()];
					/* List of receivers except current user */
					String[] msgReceiversCodeArray = new String[stakeholders.size() - 1];
					int counter = 0;
					for (BaseEntity stakeholder : stakeholders) {
						recipientCodeArray[counter] = stakeholder.getCode();
						if (!stakeholder.getCode().equals(userCode)) {
							msgReceiversCodeArray[counter] = stakeholder.getCode();
							counter += 1;
						}
					}
					List<Answer> answers = new ArrayList<Answer>();
					answers.add(new Answer(newMessage.getCode(), newMessage.getCode(), "PRI_MESSAGE", text));
					answers.add(new Answer(newMessage.getCode(), newMessage.getCode(), "PRI_CREATOR", userCode));
					this.baseEntity.saveAnswers(answers);
					/* Add current date-time to char as */
					this.baseEntity.saveAnswer(
							new Answer(chatCode, chatCode, "PRI_DATE_LAST_MESSAGE", DateUtils.getCurrentUTCDateTime()));

					log.info("The recipients are :: " + Arrays.toString(msgReceiversCodeArray));
					/* Publish chat to Receiver */
					publishData(this.baseEntity.getBaseEntityByCode(chatCode), msgReceiversCodeArray);
					/* Publish message to Receiver */
					publishData(this.baseEntity.getBaseEntityByCode(newMessage.getCode()), msgReceiversCodeArray); // Had

					QwandaUtils.createLink(chatCode, newMessage.getCode(), "LNK_MESSAGES", "message", 1.0, getToken());// Creating

					/* Sending Messages */
					HashMap<String, String> contextMap = new HashMap<String, String>();
					contextMap.put("SENDER", userCode);
					contextMap.put("CONVERSATION", newMessage.getCode());

					/* Sending toast message to all the beg frontends */
					sendMessage(msgReceiversCodeArray, contextMap, "MSG_CH40_NEW_MESSAGE_RECIEVED", "TOAST");
					sendMessage(msgReceiversCodeArray, contextMap, "MSG_CH40_NEW_MESSAGE_RECIEVED", "SMS");
					sendMessage(msgReceiversCodeArray, contextMap, "MSG_CH40_NEW_MESSAGE_RECIEVED", "EMAIL");
				} else {
					log.info("Error! The stakeholder for given chatCode is null");
				}
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
					QwandaUtils.getUniqueId("MSG", getUser().getCode()), "message", getQwandaServiceUrl(), getToken());
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
				this.baseEntity.updateBaseEntityAttribute(newMessage.getCode(), newMessage.getCode(), "PRI_MESSAGE",
						text);
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
						this.baseEntity.updateBaseEntityAttribute(sourceCode, targetCode, finalAttributeCode,
								jsonStringImage);
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
				String numberOfRatingString = this.baseEntity.getBaseEntityValueAsString(targetCode,
						"PRI_NUMBER_RATING");

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

		List<Answer> answerList = new ArrayList<Answer>(Arrays.asList(m.getItems()));
		;
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

	/**
	 * @return the started
	 */
	public Boolean isStarted() {
		return this.started;
	}

	/**
	 * @param started the started to set
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

			String subLayoutMap = RulesUtils.getLayout(realm, "/sublayouts");

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
		List<BaseEntity> beLayouts = this.baseEntity.getBaseEntitysByParentAndLinkCode("GRP_LAYOUTS", "LNK_CORE", 0,
				500, false);
		this.publishCmd(beLayouts, "GRP_LAYOUTS", "LNK_CORE");

		/* List<BaseEntity> beLayouts = this.getAllLayouts(); */
	}

	/*
	 * Gets all the attributes and Publishes to the DATA channel
	 */
	public void sendAllAttributes() {
		println("Sending all the attributes");
		try {

			QDataAttributeMessage msg = RulesUtils.loadAllAttributesIntoCache(getToken());
			this.publishCmd(msg);
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

	public void subscribeUsersToBaseEntity(final String[] users, BaseEntity be) {
		VertxUtils.subscribe(realm(), be, users);
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

	static QBulkMessage cache = null;
	static String cache2 = null;

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
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.info("Error! Unable to get Search Rsults");
			e.printStackTrace();
		}

	}
	
	/*
	 * Method to send All the Chats for the current user
	 */
	public void sendAllChats(final int pageStart, final int pageSize) {

		BaseEntity currentUser = getUser();

		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();
		QDataBaseEntityMessage qMsg;

		SearchEntity sendAllChats = new SearchEntity("SBE_ALLMYCHAT", "All My Chats").addColumn("PRI_TITLE", "Title")
				.addColumn("PRI_DATE_LAST_MESSAGE", "Last Message On").setStakeholder(getUser().getCode())
				// .addSort("PRI_DATE_LAST_MESSAGE", "Recent Message", SearchEntity.Sort.DESC)
				// // Sort doesn't work in
				.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "CHT_%").setPageStart(pageStart)
				.setPageSize(pageSize);

		try {
			qMsg = getSearchResults(sendAllChats);
		} catch (IOException e) {
			log.info("Error! Unable to get Search Results");
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

	/* Send Mobile Verification Code */
	public void sendMobileVerificationPasscode(final String userCode) {

		String[] recipients = { userCode };
		String verificationCode = generateVerificationCode();

		Answer verificationCodeAns = new Answer(userCode, userCode, "PRI_VERIFICATION_CODE", verificationCode);
		this.baseEntity.saveAnswer(verificationCodeAns);

		HashMap<String, String> contextMap = new HashMap<String, String>();
		contextMap.put("USER", userCode);

		println("The String Array is ::" + Arrays.toString(recipients));

		/* Sending sms message to user */
		sendMessage(recipients, contextMap, "GNY_USER_VERIFICATION", "SMS");

	}

	/* Generate 4 digit random passcode */
	public String generateVerificationCode() {
		if (this.hasRole("tester")) {
			return String.format("%04d", 0000);
		}
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
		try {
			result = QwandaUtils.apiPutEntity(GennySettings.qwandaServiceUrl + "/qwanda/baseentitys/force", jsonBE,
					getToken());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		log.info("The result   ::  " + result);

	}

	/*
	 * sets delete field to true so that FE removes the BE from their store ||
	 * defaults the level to 1
	 */
	public void clearBaseEntityAndChildren(String baseEntityCode) {
		this.clearBaseEntityAndChildren(baseEntityCode, 1);
	}

	/* sets delete field to true so that FE removes the BE from their store */
	public void clearBaseEntityAndChildren(String baseEntityCode, Object level) {

		String[] recipients = { this.getUser().getCode() };
		BaseEntity be = this.baseEntity.getBaseEntityByCode(baseEntityCode);
		QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(be);
		beMsg.setDelete(true);
		beMsg.setShouldDeleteLinkedBaseEntities(level);
		publishData(beMsg, recipients);
	}

	/* sets delete field to true so that FE removes the BE from their store */
	public void clearBaseEntity(String baseEntityCode, String[] recipients) {
		BaseEntity be = this.baseEntity.getBaseEntityByCode(baseEntityCode);
		QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(be);
		beMsg.setDelete(true);
		beMsg.setRecipientCodeArray(recipients);
		publishData(beMsg, recipients);

	}

	/*
	 * sets delete field and deleteLinkedBaseEntities to true so that FE removes the
	 * BE and all its child from their store
	 */
	public void clearBaseEntity(String baseEntityCode, String[] recipients, boolean deleteAllChild) {
		BaseEntity be = this.baseEntity.getBaseEntityByCode(baseEntityCode);
		QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(be);
		beMsg.setDelete(true);
		beMsg.setShouldDeleteLinkedBaseEntities(deleteAllChild);
		publishData(beMsg, recipients);

	}

	public void clearBaseEntity(String baseEntityCode, String parentCode, String recipientCode) {
		String[] recipients = new String[1];
		recipients[0] = recipientCode;
		this.clearBaseEntity(baseEntityCode, parentCode, recipients);
	}

	/*
	 * sets delete field to true and the parentCode (required to remove the links as
	 * well) so that FE removes the BE from their store
	 */
	public void clearBaseEntity(String baseEntityCode, String parentCode, String[] recipients) {
		BaseEntity be = this.baseEntity.getBaseEntityByCode(baseEntityCode);
		QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(be);
		beMsg.setDelete(true, parentCode);
		publishData(beMsg, recipients);

	}

	/* sets delete field to true so that FE removes the BE from their store */
	public void fastClearBaseEntity(String baseEntityCode, String[] recipients) {
		BaseEntity be = new BaseEntity(baseEntityCode, "FastBE");
		QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(be);
		beMsg.setDelete(true);
		publishData(beMsg, recipients);

	}

	/* sets delete field to true so that FE removes the BE from their store */
	public void clearBaseEntity(String baseEntityCode, String recipientCode) {

		String[] recipients = new String[1];
		recipients[0] = recipientCode;
		this.clearBaseEntity(baseEntityCode, recipients);
	}

	/* sets delete field to true so that FE removes the BE from their store */
	public void clearBaseEntity(String baseEntityCode) {

		String[] recipients = new String[1];
		recipients[0] = this.getUser().getCode();
		this.clearBaseEntity(baseEntityCode, recipients);
	}

	public void clearBaseEntity(List<BaseEntity> bes, String recipientCode) {

		for (BaseEntity be : bes) {

			String[] recipients = new String[1];
			recipients[0] = recipientCode;
			this.clearBaseEntity(be.getCode(), recipients);
		}
	}

	public void clearBaseEntity(List<BaseEntity> bes, String[] recipients) {

		for (BaseEntity be : bes) {
			this.clearBaseEntity(be.getCode(), recipients);
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

	public void listenAttributeChange(QEventAttributeValueChangeMessage m) {

		String[] recipientCodes = getRecipientCodes(m);
		println(m);
		this.baseEntity.addAttributes(m.getBe());
		publishBE(m.getBe(), recipientCodes);
		setState("ATTRIBUTE_CHANGE2");

		if ((m.getData() != null) && ("MULTI_EVENT".equals(m.getData().getCode()))) {
			fireAttributeChanges(m);
		}
	}

	public boolean hasRole(final String role) {

		if (getDecodedTokenMap() == null) {
			return false;
		}

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

	public boolean hasCapability(final String capability) {

		// Fetch the roles and check the capability attributes of each role

		List<EntityAttribute> roles = getUser().findPrefixEntityAttributes("PRI_IS_");
		for (EntityAttribute role : roles) { // should store in cached map
			Boolean value = role.getValue();
			if (value) {
				String roleBeCode = "ROL_" + role.getAttributeCode().substring("PRI_".length());
				BaseEntity roleBE = VertxUtils.readFromDDT(roleBeCode, getToken());
				if (roleBE == null) {
					return false;
				}
				Optional<EntityAttribute> optEaCap = roleBE.findEntityAttribute("CAP_" + capability);
				if (optEaCap.isPresent()) {
					EntityAttribute eaCap = optEaCap.get();
					if ((eaCap.getValueBoolean() != null) && (eaCap.getValueBoolean())) {
						return true;
					}
				}

			}
		}
		return false;
	}

	public String getZonedCurrentLocalDateTime() {

		LocalDateTime ldt = LocalDateTime.now();
		ZonedDateTime zdt = ldt.atZone(ZoneOffset.systemDefault());
		String iso8601DateString = ldt.toString(); // zdt.toString(); MUST USE UMT!!!!

		log.info("datetime ::" + iso8601DateString);

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
		log.info("The JsonArray is :: " + msgCodes);
		cmdViewJson.put("root", msgCodes);
		cmdViewJson.put("token", getToken());
		log.info(" The cmd msg is :: " + cmdViewJson);

		publish("cmds", cmdViewJson);
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
		log.info("Get All Users - The search BE is  :: " + searchBeCode);
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
		String result = QwandaUtils.apiPostEntity(GennySettings.qwandaServiceUrl + "/qwanda/baseentitys/search",
				jsonSearchBE, getToken());
		log.info("The result   ::  " + result);
		publishData(new JsonObject(result));
		sendTableViewWithHeaders("SBE_GET_ALL_USERS", columnsArray);
		// sendCmdView("TABLE_VIEW", "SBE_GET_ALL_USERS" );
		// publishCmd(result, grpCode, "LNK_CORE");

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

		this.navigateTo("/home");
		sendSublayout("BUCKET_DASHBOARD", "dashboard_" + realm().toLowerCase() + ".json", "GRP_DASHBOARD");
		setLastLayout("BUCKET_DASHBOARD", "GRP_DASHBOARD");
	}

	public void add(final String keyPrefix, final String parentCode, final BaseEntity be) {
		// Add this be to the static
		if ("GRP_REPORTS".equals(parentCode)) {
			log.info("GRP_REPORTS being added to");
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

		log.info("The report code is :: " + reportCode);
		// BaseEntity searchBE = getBaseEntityByCode(reportCode);

		String jsonSearchBE = null;
		SearchEntity srchBE = null;

		if ((reportCode.equalsIgnoreCase("SBE_OWNERJOBS") || reportCode.equalsIgnoreCase("SBE_DRIVERJOBS"))
				&& this.realm().equals("PRJ_CHANNEL40")) {

			// srchBE.setStakeholder(getUser().getCode());
			srchBE = new SearchEntity(reportCode, "List of all My Loads").addColumn("PRI_NAME", "Load Name")
					.addColumn("PRI_JOB_ID", "Job ID").addColumn("PRI_PICKUP_ADDRESS_FULL", "Pickup Address")
					.addColumn("PRI_DESCRIPTION", "Description").setStakeholder(getUser().getCode())
					.addSort("PRI_NAME", "Name", SearchEntity.Sort.ASC)
					.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "BEG_%").setPageStart(0).setPageSize(10000);

			jsonSearchBE = JsonUtils.toJson(srchBE);

		} else {
			BaseEntity searchBE = this.baseEntity.getBaseEntityByCode(reportCode);
			jsonSearchBE = JsonUtils.toJson(searchBE);
		}

		log.info("The search BE is :: " + jsonSearchBE);
		// String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(GennySettings.qwandaServiceUrl + "/qwanda/baseentitys/search",
				jsonSearchBE, getToken());

		this.println(resultJson);

		QDataBaseEntityMessage msg = JsonUtils.fromJson(resultJson, QDataBaseEntityMessage.class);
		try {
			if (msg.getItems() != null) {

				// Now work out sums
				boolean first = true;
				Map<String, Object> sums = new HashMap<String, Object>(); // store the column sums
				Map<String, DataType> dtypes = new HashMap<String, DataType>();
				Map<String, Attribute> attributes = new HashMap<String, Attribute>();

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
	public void sendSearchResults(SearchEntity searchBE) {

		try {

			String serviceToken = RulesUtils.generateServiceToken(this.realm());
			String jsonSearchBE = JsonUtils.toJson(searchBE);
			String resultJson = QwandaUtils.apiPostEntity(GennySettings.qwandaServiceUrl + "/qwanda/baseentitys/search",
					jsonSearchBE, serviceToken);
			QDataBaseEntityMessage msg = JsonUtils.fromJson(resultJson, QDataBaseEntityMessage.class);
			msg.setToken(getToken());
			publish("cmds", msg);
		} catch (Exception e) {

		}
	}

	/*
	 * Publish Search BE results setting the parentCode in QDataBaseEntityMessage
	 */
	public void sendSearchResults(SearchEntity searchBE, String parentCode) throws IOException {
		this.sendSearchResults(searchBE, parentCode, "LNK_CORE", "LINK");
	}

	/*
	 * Publish Search BE results setting the parentCode in QDataBaseEntityMessage
	 */
	public void sendSearchResults(SearchEntity searchBE, String parentCode, Boolean replace) throws IOException {
		this.sendSearchResults(searchBE, parentCode, "LNK_CORE", "LINK", replace);
	}
	
	/*
	 * Publish Search BE results setting the parentCode, linkValue in
	 * QDataBaseEntityMessage
	 */
	public void sendSearchResults(SearchEntity searchBE, String parentCode, String linkCode) throws IOException {
		this.sendSearchResults(searchBE, parentCode, linkCode, "LINK");
	}

	/*
	 * Publish Search BE results setting the parentCode, linkValue in
	 * QDataBaseEntityMessage
	 */
	public void sendSearchResults(SearchEntity searchBE, String parentCode, String linkCode, String linkValue) throws IOException {
		this.sendSearchResults(searchBE, parentCode, linkCode, linkValue, false);
	}

	/*
	 * Publish Search BE results setting the parentCode, linkCode, linkValue in
	 * QDataBaseEntityMessage
	 */
	public void sendSearchResults(SearchEntity searchBE, String parentCode, String linkCode, String linkValue, Boolean replace)
			throws IOException {

		String serviceToken = RulesUtils.generateServiceToken(this.realm());
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(GennySettings.qwandaServiceUrl + "/qwanda/baseentitys/search",
				jsonSearchBE, serviceToken);

		QDataBaseEntityMessage msg = JsonUtils.fromJson(resultJson, QDataBaseEntityMessage.class);
		println("msg items size ::" + msg.getItems().length);
		if (msg != null) {
			msg.setParentCode(parentCode);
			msg.setToken(getToken());
			msg.setLinkCode(linkCode);
			msg.setLinkValue(linkValue);
			msg.setReplace(replace);
			publish("cmds", msg);
		} else {
			println("Warning: no results from search " + searchBE.getCode());
		}
	}

	/*
	 * Get search Results returns QDataBaseEntityMessage
	 */
	public QDataBaseEntityMessage getSearchResults(SearchEntity searchBE) throws IOException {
		String serviceToken = RulesUtils.generateServiceToken(this.realm());
		QDataBaseEntityMessage results = getSearchResults(searchBE, serviceToken);
		if (results == null) {
			results = new QDataBaseEntityMessage(new ArrayList<BaseEntity>());

		}
		return results;
	}

	/*
	 * Get search Results returns List<BaseEntity>
	 */
	public List<BaseEntity> getSearchResultsAsList(SearchEntity searchBE, String token) throws IOException {

		QDataBaseEntityMessage msg = getSearchResults(searchBE, token);
		if (msg != null) {	
			if(msg.getItems() != null) {
				return Arrays.asList(msg.getItems());
			}
		}

		return new ArrayList<>();
	}

	/*
	 * Get search Results returns List<BaseEntity>
	 */
	public List<BaseEntity> getSearchResultsAsList(SearchEntity searchBE) throws IOException {
		return getSearchResultsAsList(searchBE, false);
	}

	/*
	 * Get search Results returns List<BaseEntity>
	 */
	public List<BaseEntity> getSearchResultsAsList(SearchEntity searchBE, Boolean useServiceToken) throws IOException {

		String token = null;
		if (useServiceToken) {
			token = RulesUtils.generateServiceToken(this.realm());
		} else {
			token = this.getToken();
		}
		return getSearchResultsAsList(searchBE, token);
	}

	/*
	 * Get search Results returns QDataBaseEntityMessage
	 */
	public QDataBaseEntityMessage getSearchResults(SearchEntity searchBE, final String token) throws IOException {
		if (token == null) {
			log.error("TOKEN IS NULL!!! in getSearchResults");
			return new QDataBaseEntityMessage(new ArrayList<BaseEntity>());
		}
		log.info("The search BE is :: " + JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(GennySettings.qwandaServiceUrl + "/qwanda/baseentitys/search",
				jsonSearchBE, token);
		QDataBaseEntityMessage msg = JsonUtils.fromJson(resultJson, QDataBaseEntityMessage.class);
		log.info("The result   ::  " + msg);

		return msg;
	}

	/*
	 * Get search Results return String
	 */
	public String getSearchResultsString(SearchEntity searchBE) throws IOException {
		log.info("The search BE is :: " + JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(GennySettings.qwandaServiceUrl + "/qwanda/baseentitys/search",
				jsonSearchBE, getToken());

		return resultJson;
	}

	public BaseEntity getChatConversation(List<String> userCodes) {

		/* we get all the existing chats */
		List<BaseEntity> chats = this.baseEntity.getLinkedBaseEntities("GRP_MESSAGES", "LNK_CHAT");
		
		if (chats != null) {

			/* we loop through all of them */
			for (BaseEntity chat: chats) {

				/* we get the chat participants */
				List<BaseEntity> chatParticipants = this.baseEntity.getLinkedBaseEntities(chat.getCode(), "LNK_USER");
				if (chatParticipants != null) {

					List<String> participantCodes = chatParticipants.stream().map(participant -> participant.getCode()).collect(Collectors.toList());

					/* if the participants match the list of user codes, we have found the chat we were looking for */
					if(participantCodes.equals(userCodes)) {
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
			List<EntityAttribute> eaList = new ArrayList<>();

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
				JsonObject colObject = new JsonObject();
				colObject.put("code", colS);

				colHeaderArr.add(colObject);
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
		log.info("The JsonArray is :: " + msgCodes);
		cmdViewJson.put("root", msgCodes);
		cmdViewJson.put("token", getToken());
		log.info(" The cmd msg is :: " + cmdViewJson);

		publish("cmds", cmdViewJson);
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

		println(RulesUtils.ANSI_YELLOW + "*********** setting new (" + serviceDecodedTokenMap.get("azp")
				+ ") token username -> " + serviceDecodedTokenMap.get("preferred_username") + RulesUtils.ANSI_RESET);
		/* we reinit utils */
		this.initUtils();
	}

	public boolean loadRealmData() {

		println(RulesUtils.ANSI_BLUE + "PRE_INIT_STARTUP Loading in keycloak data and setting up service token for "
				+ realm() + RulesUtils.ANSI_RESET);

		for (String jsonFile : SecureResources.getKeycloakJsonMap().keySet()) {

			String keycloakJson = SecureResources.getKeycloakJsonMap().get(jsonFile);
			if (keycloakJson == null) {
				log.info("No keycloakMap for " + realm());
				if (GennySettings.devMode) {
					System.out.println("Fudging realm so genny keycloak used");
					// Use basic Genny json when project json not available
					String gennyJson = SecureResources.getKeycloakJsonMap().get("genny.json");
					SecureResources.getKeycloakJsonMap().put(jsonFile, gennyJson);
					keycloakJson = gennyJson;
				} else {
					return false;
				}
			}

			JsonObject realmJson = new JsonObject(keycloakJson);
			String realm = realmJson.getString("realm");

			if (realm != null) {

				String token = RulesUtils.generateServiceToken(GennySettings.dynamicRealm(realm()));
				this.println(token);
				if (token != null) {

					this.setNewTokenAndDecodedTokenMap(token);
					this.set("realm", GennySettings.dynamicRealm(realm()));
					return true;
				}
			}

		}

		return false;
	}

	public void sendTreeData() {

		println("treedata realm is " + realm());

		// list of QDataBaseEntityMessages
		List<QDataBaseEntityMessage> baseEntityMessages = new ArrayList<>();

		// we grab the root
		BaseEntity root = this.baseEntity.getBaseEntityByCode("GRP_ROOT");
		QDataBaseEntityMessage rootMessage = new QDataBaseEntityMessage(root);
		rootMessage.setParentCode("GRP_ROOT_ROOT");
		baseEntityMessages.add(rootMessage);

		// we grab the first branch
		List<BaseEntity> rootKids = this.baseEntity.getLinkedBaseEntities("GRP_ROOT");

		// we create the message
		QDataBaseEntityMessage rootChildrenMessage = new QDataBaseEntityMessage(rootKids);
		rootChildrenMessage.setParentCode("GRP_ROOT");
		baseEntityMessages.add(rootChildrenMessage);

		// we get the kids of the kids
		for (BaseEntity kid : rootKids) {

			// we get the kid kids
			List<BaseEntity> kidKids = this.baseEntity.getLinkedBaseEntities(kid);

			// we create the message
			QDataBaseEntityMessage kidKidMessage = new QDataBaseEntityMessage(kidKids);
			kidKidMessage.setParentCode(kid.getCode());
			baseEntityMessages.add(kidKidMessage);

		}

		// we create the bulk
		QBulkMessage newBulkMsg = new QBulkMessage();

		// we now loop through all the messages to check if the current user is allowed
		// to see them
		for (QDataBaseEntityMessage message : baseEntityMessages) {

			// list for allowed base entities
			List<BaseEntity> allowedChildren = new ArrayList<>();

			for (BaseEntity child : message.getItems()) {

				if (message.getParentCode().equals("GRP_ROOT_ROOT") == false) {

					// we get the parent
					BaseEntity parent = this.baseEntity.getBaseEntityByCode(message.getParentCode());

					// we get the kid code
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
									log.error("Error!! The attribute value is not in boolean format");
								}
							}

						}
					}
				} else {

					allowedChildren.add(child);
				}
			}

			// we create the final message
			QDataBaseEntityMessage filteredMsg = new QDataBaseEntityMessage(
					allowedChildren.toArray(new BaseEntity[allowedChildren.size()]), message.getParentCode(),
					"LNK_CORE");
			filteredMsg.setToken(getToken());
			newBulkMsg.add(filteredMsg);
		}

		this.publishCmd(newBulkMsg);
	}

	public void startupEvent(String caller) {

		// Save the existing token
		String token = this.token;
		Map<String, Object> decodedToken = this.decodedTokenMap;

		println("Startup Event called from " + caller);
		if (!isState("GENERATE_STARTUP")) {
			this.loadRealmData();
			this.reloadCache();
		}

		// restore the existing token
		this.setToken(token);
		this.setDecodedTokenMap(decodedToken);
	}

	public void reloadCache() {

		BaseEntityUtils beUtils = this.baseEntity;
		CacheUtils cacheUtils = this.cacheUtils;

		String realm = this.realm();

		/* we check if the search BEs have been created */
		BaseEntity searchNewItems = beUtils.getBaseEntityByCode("SBE_NEW_ITEMS");
		if (searchNewItems == null) {
			drools.setFocus("GenerateSearches");
		}

		cacheUtils.refresh(realm, "GRP_APPLICATIONS");
		cacheUtils.refresh(realm, "GRP_DASHBOARD");
		cacheUtils.refresh(realm, "GRP_BEGS");
		cacheUtils.refresh(realm, "ARCHIVED_PRODUCTS"); /* TODO: that might not be necessary */
	}

	/* returns subscribers of a baseEntity Code */
	public String[] getSubscribers(final String subscriptionCode) {

		final String SUB = "SUB";

		// Subscribe to a code
		String[] resultArray = VertxUtils.getObject(realm(), SUB, subscriptionCode, String[].class);

		String[] resultAdmins = VertxUtils.getObject(realm(), "SUBADMIN", "ADMINS", String[].class);

		if (resultArray != null && resultAdmins != null) {
			String[] result = new String[resultArray.length + resultAdmins.length];
			ArrayUtils.concat(resultArray, resultAdmins, result);
			return result;
		} else if (resultArray != null && resultAdmins == null) {
			return resultArray;
		} else {
			return null;
		}

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
		BaseEntity existing = this.baseEntity.getBaseEntityByAttributeAndValue("PRI_CODE", "PER_SERVICE"); // do not
		// check
		// cache!

		if (existing == null) {

			try {
				be = QwandaUtils.createUser(GennySettings.qwandaServiceUrl, getToken(), username, firstname, lastname,
						email, realm, name, keycloakId);
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
						VertxUtils.subscribeAdmin(realm(), user.getCode());
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
				log.info("Error!! while updating " + attributeCode + " attribute value");
			}
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
			this.sendToastNotification(recipientArr, toastMessage, "warning");

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
			this.sendToastNotification(recipientArr, toastMessage, "warning");

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
			this.sendToastNotification(recipientArr, toastMessage, "warning");

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
					log.info("response user pojo ::" + responseUserPojo);

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
				 * String[] recipientArr = { userBe.getCode() };
				 * this.sendToastNotification(recipientArr, toastMessage, "warning");
				 */
				sendSlackNotification(message);
			}

		}
		return paymentsUserId;
	}

	/**
	 * 
	 * @param attributeCode of the slack-notification-channel
	 * @param message       to be sent to the webhook
	 */
	public void sendSlackNotification(String attributeCode, String message) {
		/* send critical slack notifications only for production mode */
		log.info("dev mode ::" + GennySettings.devMode);
		BaseEntity project = getProject();
		if (project != null && !GennySettings.devMode) {
			String webhookURL = project.getLoopValue(attributeCode, null);
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

	/* To send critical slack message to slack channel */
	public void sendSlackNotification(String message) {
		this.sendSlackNotification("PRI_SLACK_NOTIFICATION_URL", message);
	}

	// TODO Priority field needs to be made as enum : error,info, warning
	/* To send direct toast messages to the front end without templates */
	public void sendToastNotification(String[] recipientArr, String toastMsg, String priority) {

		/* create toast */
		/* priority can be "info" or "error or "warning" */
		QDataToastMessage toast = new QDataToastMessage(priority, toastMsg);
		toast.setToken(getToken());
		toast.setRecipientCodeArray(recipientArr);

		String toastJson = JsonUtils.toJson(toast);

		publish("webdata", toastJson);
	}

	/* To send direct toast messages to the front end without templates */
	public void sendToastNotification(String toastMsg, String priority) {

		String[] recipients = new String[1];
		recipients[0] = this.getUser().getCode();
		this.sendToastNotification(recipients, toastMsg, priority);
	}

	/* To send direct toast messages to the front end without templates */
	public void sendToastNotification(String toastMsg) {
		this.sendToastNotification(toastMsg, "info");
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
		// String grpCode = m.getData().getValue();
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
								log.info("Error!! The attribute value is not in boolean format");
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
				log.info("Error!!The user BE is null");
			}
		} else {
			log.info("Error!!The reports group code is null");
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
			this.sendToastNotification(recipientArr, toastMessage, "warning");
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

					log.info("Error Key = " + errVar + ", Value = " + errValBuilder);

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
					this.sendToastNotification(recipientArr, toastMessage, "warning");
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
			this.sendToastNotification(recipientArr, toastMessage, "warning");
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
		cmdViewMessage.setToken(getToken());
		JsonObject cmdViewMessageJson = new JsonObject().mapFrom(cmdViewMessage);
		cmdViewMessageJson.put("root", rootCode);
		publish("cmds", cmdViewMessageJson);
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
					log.info("payment fee Id ::" + paymentFeeId);
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
					this.sendToastNotification(recipientArr, toastMessage, "warning");
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
					Answer depositReferenceAnswer = new Answer(begBe.getCode(), begBe.getCode(),
							"PRI_DEPOSIT_REFERENCE_ID", makePaymentResponseObj.getDepositReference());
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
				this.sendToastNotification(recipientArr, toastMessage, "warning");
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
			this.sendToastNotification(recipientArr, toastMessage, "warning");

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

	public void processAddresses(String realm) {
		// load in all the people in the db
		QDataBaseEntityMessage qMsg = null;
		String token = RulesUtils.generateServiceToken(realm);
		SearchEntity allPeople = new SearchEntity("SBE_ALL_PEOPLE", "All People")
				.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "PER_%").setPageStart(0).setPageSize(100000);
		try {
			qMsg = getSearchResults(allPeople, token);
		} catch (IOException e) {
			log.info("Error! Unable to get Search All People");

		}
		// check their addresses
		System.out.println("Processing " + qMsg.getReturnCount() + " people ");
		for (BaseEntity person : qMsg.getItems()) {
			log.info(person.getCode());
			if (!isAddressPresent(person.getCode())) {
				log.info("Bad one...");
			}
		}

	}

	public boolean isAddressPresent(final String beCode) {
		boolean ret = false;

		BaseEntity be = this.baseEntity.getBaseEntityByCode(beCode);

		if (be.containsEntityAttribute("PRI_ADDRESS_ADDRESS1")) {
			if (be.containsEntityAttribute("PRI_ADDRESS_COUNTRY")) {
				if (be.containsEntityAttribute("PRI_ADDRESS_FULL")) {
					if (be.containsEntityAttribute("PRI_ADDRESS_LATITUDE")) {
						if (be.containsEntityAttribute("PRI_ADDRESS_LONGITUDE")) {
							if (be.containsEntityAttribute("PRI_ADDRESS_POSTCODE")) {
								if (be.containsEntityAttribute("PRI_ADDRESS_SUBURB")) {
									return true;
								}
							}
						}
					}
				}
			}
		}

		// okay, need to regenerate address attributes
		String jsonAddress = be.getValue("PRI_ADDRESS_JSON", null);
		if (jsonAddress != null) {

			QDataAnswerMessage msg = new QDataAnswerMessage(
					new Answer(beCode, beCode, "PRI_ADDRESS_JSON", jsonAddress));
			List<Answer> answers = processAddressAnswers(msg);
			this.baseEntity.saveAnswers(answers);
			log.info(be.getCode() + " fixed using existing JSON");
		} else {
			String jsonFull = be.getValue("PRI_ADDRESS_FULL", null);
			if (jsonFull != null) {
				String[] fullAddress = jsonFull.split(",");
				String city = fullAddress[1];
				List<Answer> answers = new ArrayList<Answer>();
				answers.add(new Answer(beCode, beCode, "PRI_ADDRESS_CITY", city));
				answers.add(new Answer(beCode, beCode, "PRI_ADDRESS_SUBURB", city));
				this.baseEntity.saveAnswers(answers);
				log.info(be.getCode() + " fixed using existing Full Address");
			} else {
				log.error("ERROR!! " + beCode + " does not contain Address Full");
			}
		}
		return ret;
	}

	public void generateCapabilities() {

		/* get all capabilities existing */
		List<Attribute> existingCapability = new ArrayList<Attribute>();
		for (String existingAttributeCode : RulesUtils.attributeMap.keySet()) {
			if (existingAttributeCode.startsWith("CAP_")) {
				existingCapability.add(RulesUtils.attributeMap.get(existingAttributeCode));
			}
		}

		/* Force these capabilities to exist */
		List<Attribute> capabilityManifest = new ArrayList<Attribute>();

		String proj_realm = System.getenv("PROJECT_REALM");
		String token = RulesUtils.generateServiceToken(proj_realm);

		addCapability(capabilityManifest, "ADD_QUOTE", "Allowed to post a quote", token);
		addCapability(capabilityManifest, "READ_QUOTE", "Allowed to read a quote", token);
		addCapability(capabilityManifest, "DELETE_QUOTE", "Allowed to delete a quote", token);
		addCapability(capabilityManifest, "UPDATE_QUOTE", "Allowed to update a quote", token);
		addCapability(capabilityManifest, "ACCEPT_QUOTE", "Allowed to accept a quote", token);
		addCapability(capabilityManifest, "ADD_CHAT_MESSAGE", "Allowed to add a chat message", token);
		addCapability(capabilityManifest, "UPDATE_CHAT_MESSAGE", "Allowed to update a chat message", token);
		addCapability(capabilityManifest, "READ_CHAT_MESSAGE", "Allowed to read a chat message", token);
		addCapability(capabilityManifest, "DELETE_MESSAGE", "Allowed to delete a chat message", token);
		addCapability(capabilityManifest, "ADD_CALL", "Allowed to create a voice call", token);
		addCapability(capabilityManifest, "END_CALL", "Allowed to end a voice call", token);
		addCapability(capabilityManifest, "READ_NEW_ITEMS", "Allowed to read New Items Column", token);
		addCapability(capabilityManifest, "READ_PAID_ITEMS", "Allowed to read Paid Items Column", token);
		addCapability(capabilityManifest, "ADD_ARCHIVE", "Allowed to move an item to archive", token);
		addCapability(capabilityManifest, "READ_ARCHIVE", "Allowed to read archives", token);
		addCapability(capabilityManifest, "DELETE_ARCHIVE", "Allowed to delete an archived item", token);
		addCapability(capabilityManifest, "LOCATE_USER", "Allowed to locate a user", token);
		addCapability(capabilityManifest, "SEND_GPS", "Allowed to send GPS", token);
		addCapability(capabilityManifest, "ADD_ITEM", "Allowed to create a new Item", token);
		addCapability(capabilityManifest, "DELETE_ITEM", "Allowed to delete an Item", token);
		addCapability(capabilityManifest, "UPDATE_ITEM", "Allowed to update an Item", token);
		addCapability(capabilityManifest, "ADD_USER", "Allowed to create a new user", token);
		addCapability(capabilityManifest, "UPDATE_USER", "Allowed to update a user", token);
		addCapability(capabilityManifest, "DELETE_USER", "Allowed to delete a user", token);
		addCapability(capabilityManifest, "READ_USER", "Allowed to read a user", token);
		addCapability(capabilityManifest, "ADD_PAYMENT_METHOD", "Allowed to add a payment item", token);
		addCapability(capabilityManifest, "UPDATE_PAYMENT_METHOD", "Allowed to update a payment item", token);
		addCapability(capabilityManifest, "READ_PAYMENT_METHOD", "Allowed to read a payment item", token);
		addCapability(capabilityManifest, "DELETE_PAYMENT_METHOD", "Allowed to delete a payment item", token);
		addCapability(capabilityManifest, "READ_PROFILE", "Allowed to read profile", token);
		addCapability(capabilityManifest, "READ_ACCOUNT", "Allowed to read account", token);
		addCapability(capabilityManifest, "DELETE_ACCOUNT", "Allowed to delete an account", token);
		addCapability(capabilityManifest, "MARK_PICKUP", "Allowed to mark pickup on an item", token);
		addCapability(capabilityManifest, "MARK_DELIVERY", "Allowed to mark delivery on an item", token);

		/* Remove any capabilities not in this forced list from roles */
		existingCapability.removeAll(capabilityManifest);

		/*
		 * for every capability that exists that is not in the manifest , find all
		 * entityAttributes
		 */
		for (Attribute toBeRemovedCapability : existingCapability) {
			try {
				RulesUtils.attributeMap.remove(toBeRemovedCapability.getCode()); // remove from cache
				QwandaUtils.apiDelete(
						getQwandaServiceUrl() + "/qwanda/baseentitys/attributes/" + toBeRemovedCapability.getCode(),
						token);
				// update all the roles that use this attribute by reloading them into cache
				QDataBaseEntityMessage rolesMsg = VertxUtils.getObject(realm(), "ROLES", realm(),
						QDataBaseEntityMessage.class);
				if (rolesMsg != null) {

					for (BaseEntity role : rolesMsg.getItems()) {
						role.removeAttribute(toBeRemovedCapability.getCode());
						// Now update the db role to only have the attributes we want left
						QwandaUtils.apiPutEntity(getQwandaServiceUrl() + "/qwanda/baseentitys/force",
								JsonUtils.toJson(role), token);

					}
				}

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// now regenerate the roles cache
		drools.setFocus("GenerateRoles");

	}

	public Attribute addCapability(List<Attribute> capabilityManifest, final String capabilityCode, final String name,
			final String token) {
		String fullCapabilityCode = "CAP_" + capabilityCode.toUpperCase();
		println("Setting Capability : " + fullCapabilityCode + " : " + name);
		Attribute attribute = RulesUtils.attributeMap.get(fullCapabilityCode);
		if (attribute != null) {
			capabilityManifest.add(attribute);
			return attribute;
		} else {
			// create new attribute
			attribute = new AttributeBoolean(fullCapabilityCode, name);
			// save to database and cache

			try {
				baseEntity.saveAttribute(attribute, token);
				// no roles would have this attribute yet
				// return
				capabilityManifest.add(attribute);
				return attribute;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;

		}
	}

	public String generateRedirectUrl(String host, JsonObject data) {

		/* we stringify the json object */
		try {
			if (data != null) {
				/* we encode it for URL schema */
				String base64 = this.encodeToBase64(data.toString());
				return host + "?state=" + base64;
			}
		} catch (Exception e) {
		}

		return null;
	}

	public String getUnsubscribeLinkForEmailTemplate(String host, String templateCode) {

		JsonObject data = new JsonObject();
		data.put("loading", "Loading...");
		data.put("evt_type", "REDIRECT_EVENT");
		data.put("evt_code", "REDIRECT_UNSUBSCRIBE_MAIL_LIST");

		JsonObject dataObj = new JsonObject();
		dataObj.put("code", "REDIRECT_UNSUBSCRIBE_MAIL_LIST");
		dataObj.put("value", templateCode);

		data.put("data", dataObj);
		String redirectUrl = generateRedirectUrl(host, data);
		return redirectUrl;
	}

	public QBaseMSGMessageTemplate getMessageTemplate(String templateCode) {
		return QwandaUtils.getTemplate(templateCode, getToken());
	}

	public BaseEntity createNote(String contextCode, String content) {
		return this.createNote(contextCode, content, "SYSTEM");
	}

	public BaseEntity createNote(BaseEntity context, String content) {
		return this.createNote(context.getCode(), content, "SYSTEM");
	}

	public BaseEntity createNote(String contextCode, String content, String noteType) {

		/* we create the note baseEntity */
		BaseEntity note = this.baseEntity.create(this.getUser().getCode(), "NOT", "NOTE");

		/* we save the note attributes */
		List<Answer> answers = new ArrayList<>();
		answers.add(new Answer(getUser().getCode(), note.getCode(), "PRI_CREATED_DATE", getCurrentLocalDateTime()));
		answers.add(new Answer(getUser().getCode(), note.getCode(), "PRI_CREATOR_CODE", getUser().getCode()));
		answers.add(new Answer(getUser().getCode(), note.getCode(), "PRI_CREATOR_NAME", getUser().getName()));
		answers.add(new Answer(getUser().getCode(), note.getCode(), "PRI_CREATOR_TYPE", noteType));
		answers.add(new Answer(getUser().getCode(), note.getCode(), "PRI_TYPE", noteType));
		answers.add(new Answer(getUser().getCode(), note.getCode(), "PRI_CONTENT", content));
		this.baseEntity.saveAnswers(answers);

		/* we link the note to GRP_NOTES */
		this.baseEntity.createLink("GRP_NOTES", note.getCode(), "LNK_CORE", "NOTE", 1.0);

		/* we link the context and the note */
		this.linkNoteAndContext(note.getCode(), contextCode);
		return note;
	}

	public void linkNoteAndContexts(BaseEntity note, final List<BaseEntity> contextList) {
		for (BaseEntity context : contextList) {
			this.linkNoteAndContext(note, context);
		}
	}

	public void linkNoteAndContext(BaseEntity note, BaseEntity context) {
		this.baseEntity.createLink(note.getCode(), context.getCode(), "LNK_MESSAGES", "CONTEXT", 1.0);
	}

	public void linkNoteAndContext(String noteCode, String contextCode) {
		this.baseEntity.createLink(noteCode, contextCode, "LNK_MESSAGES", "CONTEXT", 1.0);
	}

	public void sendNotes(String contextCode) {

		String[] recipient = { getUser().getCode() };

		this.clearBaseEntityAndChildren("GRP_NOTES", 1);
		this.publishBaseEntityByCode("GRP_NOTES", null, null, recipient);

		SearchEntity searchBE = new SearchEntity(drools.getRule().getName(), "Notes").setSourceCode("GRP_NOTES")
				.setStakeholder(contextCode).setPageStart(0).setPageSize(10000);

		if (searchBE != null) {
			/* Send search result */
			try {
				// this.sendSearchResults(searchBE, "GRP_NOTES");
				QDataBaseEntityMessage search = this.getSearchResults(searchBE);
				search.setLinkCode("LNK_MESSAGES");
				search.setParentCode("GRP_NOTES");
				this.publishCmd(search);

			} catch (IOException e) {
			}
		}
	}

	public void sendNotes(BaseEntity context) {
		this.sendNotes(context.getCode());
	}

	/* Send View To Front End */
	public void sendView(LayoutViewData viewData) {
		this.publishCmd(this.layoutUtils.sendView(viewData));
	}

	public void sendSplitView(final String parentCode, final String bucketCode) {

		QCmdMessage cmdView = new QCmdMessage("CMD_VIEW", "SPLIT_VIEW");
		JsonObject cmdViewJson = JsonObject.mapFrom(cmdView);

		JsonObject codeListView = new JsonObject();
		codeListView.put("code", "LIST_VIEW");
		codeListView.put("root", parentCode);

		JsonObject bucketListView = new JsonObject();
		bucketListView.put("code", "BUCKET_VIEW");
		bucketListView.put("root", bucketCode);

		JsonArray msgCodes = new JsonArray();
		msgCodes.add(codeListView);
		msgCodes.add(bucketListView);
		System.out.println("The JsonArray is :: " + msgCodes);
		cmdViewJson.put("data", msgCodes);
		cmdViewJson.put("root", parentCode); /* root needs to be there */
		cmdViewJson.put("token", getToken());
		System.out.println(" The cmd msg is :: " + cmdViewJson);

		publish("cmds", cmdViewJson);
		// publishCmd(cmdViewJson);
	}

	public void setLastView(LayoutViewData viewData) {
		String sessionId = getAsString("session_state");
		if (sessionId != null) {
			this.println("sessionId" + sessionId);
			VertxUtils.putObject(realm(), "PreviousLayout", "key", viewData);
		}
	}

	public LayoutViewData getLastView() {
		String sessionId = getAsString("session_state");
		this.println("sessionId" + sessionId);
		if (sessionId != null) {
			LayoutViewData viewData = VertxUtils.getObject(realm(), "PreviousLayout", "key", LayoutViewData.class);
			return viewData;
		}
		return null;
	}

	public String updateBucketCount(String baseEntityCode, String sourceCode, String targetCode) {

		BaseEntity be = this.baseEntity.getBaseEntityByCode(baseEntityCode);
		if (be != null) {

			Integer sourceGrpCount = be.getValue("PRI_COUNT_" + sourceCode, 0);
			Integer targetGrpCount = be.getValue("PRI_COUNT_" + targetCode, 0);
			try {

				if (sourceGrpCount > 0) {
					sourceGrpCount = sourceGrpCount - 1;
					targetGrpCount = targetGrpCount + 1;

					this.baseEntity.updateBaseEntityAttribute(this.getUser().getCode(), be.getCode(),
							"PRI_COUNT_" + sourceCode, sourceGrpCount.toString());
					this.baseEntity.updateBaseEntityAttribute(this.getUser().getCode(), be.getCode(),
							"PRI_COUNT_" + targetCode, targetGrpCount.toString());
				}
			} catch (Exception e) {
			}
		}

		return null;
	}

	// TODO: What is the point of this function? ACC
	public String getUniqueCode(int numberOfDigitsForUniqueCode) {
		String uniqueCode = QwandaUtils.getUniqueCode(numberOfDigitsForUniqueCode);
		return uniqueCode;
	}

	public void processAnswers(final Answer[] answers) {
	 	Arrays.stream(answers).forEach(x -> {
	 		drools.insert(x);
	 	});
	 	drools.setFocus("AnswerProcessing");
	 }

	 public void processAnswers(final List<Answer> answers) {
	 	answers.stream().forEach(x -> {
	 		drools.insert(x);
	 	});
	 	drools.setFocus("AnswerProcessing");
	 }

	 public void processAnswers(final Answer answer) {
	 	drools.insert(answer);
	 	drools.setFocus("AnswerProcessing");
	 }

	public void processJsonAddress(final String sourceCode, final String targetCode, final String jsonAddressLine) {
		QDataAnswerMessage msg = new QDataAnswerMessage(
				new Answer(sourceCode, targetCode, "PRI_ADDRESS_JSON", jsonAddressLine));
		List<Answer> answers = processAddressAnswers(msg);
		this.baseEntity.saveAnswers(answers);

	}
	
	/* to return the name of the attribute */
	public String getAttributeName(BaseEntity parentBe, String attributeCode) {
		
		String attributeName = null;
		
		if(parentBe != null) {
			Optional<EntityAttribute> updatedAttributeOp = parentBe.getBaseEntityAttributes().stream().filter(Objects::nonNull).filter(ea -> ea.getAttributeCode().equals(attributeCode)).findFirst();
			if(updatedAttributeOp.isPresent()) {
				EntityAttribute updatedAttribute = updatedAttributeOp.get();
				attributeName = updatedAttribute.getAttributeName();
			}
		}
		
		
		return attributeName;
	}

}
