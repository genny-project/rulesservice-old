package life.genny.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.UnknownHostException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
import org.keycloak.representations.AccessTokenResponse;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.channel.Producer;
import life.genny.qwanda.Answer;
import life.genny.qwanda.GPS;
import life.genny.qwanda.Layout;
import life.genny.qwanda.Link;
import life.genny.qwanda.PaymentsResponse;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.attribute.AttributeBoolean;
import life.genny.qwanda.attribute.AttributeInteger;
import life.genny.qwanda.attribute.AttributeMoney;
import life.genny.qwanda.attribute.AttributeText;
import life.genny.qwanda.attribute.EntityAttribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.entity.EntityEntity;
import life.genny.qwanda.entity.SearchEntity;
import life.genny.qwanda.exception.BadDataException;
import life.genny.qwanda.message.QBaseMSGAttachment;
import life.genny.qwanda.message.QBaseMSGAttachment.AttachmentType;
import life.genny.qwanda.message.QBulkMessage;
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
import life.genny.qwanda.message.QEventAttributeValueChangeMessage;
import life.genny.qwanda.message.QEventBtnClickMessage;
import life.genny.qwanda.message.QEventLinkChangeMessage;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwanda.message.QMSGMessage;
import life.genny.qwanda.message.QMessage;
import life.genny.qwanda.payments.QPaymentMethod;
import life.genny.qwanda.payments.QPaymentMethod.PaymentType;
import life.genny.qwandautils.GPSUtils;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.KeycloakUtils;
import life.genny.qwandautils.MessageUtils;
import life.genny.qwandautils.QwandaUtils;
import life.genny.qwandautils.SecurityUtils;
import life.genny.security.SecureResources;
import life.genny.utils.MoneyHelper;
import life.genny.utils.PaymentUtils;
import life.genny.utils.StringFormattingUtils;
import life.genny.utils.VertxUtils;

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

	public QRules(final EventBus eventBus, final String token, final Map<String, Object> decodedTokenMap,
			String state) {
		super();
		this.eventBus = eventBus;
		this.token = token;
		this.decodedTokenMap = decodedTokenMap;
		this.stateMap = new HashMap<String, Boolean>();
		stateMap.put(DEFAULT_STATE, true);
		setStarted(false);

	}

	public QRules(final EventBus eventBus, final String token, final Map<String, Object> decodedTokenMap) {
		this(eventBus, token, decodedTokenMap, DEFAULT_STATE);
	}

	public BaseEntity getBaseEntityByCode2(final String code) {

		CompletableFuture<BaseEntity> fut = new CompletableFuture<BaseEntity>();
		RulesUtils.baseEntityMap.get(code, resGet -> {
			if (resGet.succeeded()) {
				// Successfully got the value
				fut.complete(resGet.result());
			} else {
				// Something went wrong!
				fut.complete(RulesUtils.getBaseEntityByCode(qwandaServiceUrl, getDecodedTokenMap(), getToken(), code));

				try {
					RulesUtils.baseEntityMap.put(code, fut.get(), resPut -> {
						if (resPut.succeeded()) {
							// Successfully put the value
							println("BaseEntity " + code + " stored in cache");
						} else {
							// Something went wrong!
							println("ERROR: BaseEntity " + code + " NOT stored in cache");
						}
					});
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

		});

		try {
			return fut.get();
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

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
		return getAsString("realm").toLowerCase();
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

	public void set(final String key, Object value) {
		decodedTokenMap.put(key, value);

	}

	public BaseEntity getProject() {

		BaseEntity be = null;
		String projectCode = "PRJ_" + getAsString("realm").toUpperCase();
		be = getBaseEntityByCode(projectCode);

		if (isNull("PROJECT") && be != null) {
			set("PROJECT", be);
		}

		return be;
	}

	public BaseEntity getUser() {

		BaseEntity be = null;
		String username = (String) getDecodedTokenMap().get("preferred_username");
		String code = "PER_" + QwandaUtils.getNormalisedUsername(username).toUpperCase();
		be = getBaseEntityByCode(code);

		/*
		 * if this situation happens it just means that QRules has not registered the
		 * user yet. setting it.
		 */
		if (isNull("USER") && be != null) {
			set("USER", be);
		} else {
			// try again

		}

		return be;
	}

	public String getFullName(final BaseEntity be) {
		String fullName = be.getLoopValue("PRI_FIRSTNAME", "") + " " + be.getLoopValue("PRI_LASTNAME", "");
		fullName = fullName.trim();
		return fullName;
	}

	public Boolean isUserPresent() {

		if (isNull("USER")) {
			return false;
		} else {
			return true;
		}
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

	public void updateBaseEntityAttribute(final String sourceCode, final String beCode, final String attributeCode,
			final String newValue) {
		List<Answer> answers = new ArrayList<Answer>();
		answers.add(new Answer(sourceCode, beCode, attributeCode, newValue));
		saveAnswers(answers);
	}

	public BaseEntity getBaseEntityByCode(final String code) {
		BaseEntity be = null;

		be = VertxUtils.readFromDDT(code, getToken());
		if (be == null) {
			println("ERROR - be (" + code + ") fetched is NULL ");
		} else {
			addAttributes(be);
		}
		return be;
	}

	public BaseEntity getBaseEntityByCode(final String code, Boolean withAttributes) {
		BaseEntity be = null;

		be = VertxUtils.readFromDDT(code, withAttributes, getToken());
		if (be == null) {
			println("ERROR - be (" + code + ") fetched is NULL ");
		} else {
			addAttributes(be);
		}
		return be;
	}

	public BaseEntity getBaseEntityByCodeWithAttributes(final String code) {
		BaseEntity be = null;

		be = VertxUtils.readFromDDT(code, true, getToken());
		if (be == null) {
			println("ERROR - be (" + code + ") fetched is NULL ");
		} else {
			if (be.getBaseEntityAttributes().isEmpty()) {
				try {
					be = QwandaUtils.getBaseEntityByCodeWithAttributes(code, getToken());
					VertxUtils.writeCachedJson(code, JsonUtils.toJson(be));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			addAttributes(be);
		}
		return be;
	}

	public BaseEntity getBaseEntityByAttributeAndValue(final String attributeCode, final String value) {

		BaseEntity be = null;
		be = RulesUtils.getBaseEntityByAttributeAndValue(qwandaServiceUrl, getDecodedTokenMap(), getToken(),
				attributeCode, value);
		addAttributes(be);
		return be;
	}

	public List<BaseEntity> getBaseEntitysByAttributeAndValue(final String attributeCode, final String value) {

		List<BaseEntity> bes = null;
		bes = RulesUtils.getBaseEntitysByAttributeAndValue(qwandaServiceUrl, getDecodedTokenMap(), getToken(),
				attributeCode, value);

		return bes;
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode) {
		return getBaseEntitysByParentAndLinkCode(parentCode, linkCode, 0, 10, false);
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize) {

		return getBaseEntitysByParentAndLinkCode(parentCode, linkCode, pageStart, pageSize, false);
	}

	public void clearBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart,
			Integer pageSize) {
		String key = parentCode + linkCode + "-" + pageStart + "-" + pageSize;

		VertxUtils.putObject(this.realm(), "LIST", key, null);
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize, Boolean cache) {
		cache = false;
		List<BaseEntity> bes = new ArrayList<BaseEntity>();
		String key = parentCode + linkCode + "-" + pageStart + "-" + pageSize;
		if (cache) {
			Type listType = new TypeToken<List<BaseEntity>>() {
			}.getType();
			List<String> beCodes = VertxUtils.getObject(this.realm(), "LIST", key, (Class) listType);
			if (beCodes == null) {
				bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes(qwandaServiceUrl, getDecodedTokenMap(),
						getToken(), parentCode, linkCode, pageStart, pageSize);
				beCodes = new ArrayList<String>();
				for (BaseEntity be : bes) {
					VertxUtils.putObject(realm(), "", be.getCode(), JsonUtils.toJson(be));
					beCodes.add(be.getCode());
				}
				VertxUtils.putObject(this.realm(), "LIST", key, beCodes);
			} else {
				for (String beCode : beCodes) {
					BaseEntity be = VertxUtils.readFromDDT(beCode, true, getToken());
					bes.add(be);
				}
			}
		} else {

			bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes(qwandaServiceUrl, getDecodedTokenMap(),
					getToken(), parentCode, linkCode, pageStart, pageSize);
		}

		return bes;
	}

	public List<BaseEntity> getBaseEntitysByParentLinkCodeAndLinkValue(final String parentCode, final String linkCode,
			final String linkValue, Integer pageStart, Integer pageSize, Boolean cache) {

		List<BaseEntity> bes = null;

		bes = RulesUtils.getBaseEntitysByParentAndLinkCodeAndLinkValueWithAttributes(qwandaServiceUrl,
				getDecodedTokenMap(), getToken(), parentCode, linkCode, linkValue, pageStart, pageSize);

		/*
		 * bes =
		 * RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes(qwandaServiceUrl,
		 * getDecodedTokenMap(), getToken(), parentCode, linkCode, pageStart, pageSize);
		 */

		return bes;
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize, Boolean cache, final String stakeholderCode) {
		List<BaseEntity> bes = null;
		if (getUser().is("PRI_DRIVER")) {
			RulesUtils.println("Is True");
		}
		// if (isNull("BES_" + parentCode.toUpperCase() + "_" + linkCode)) {
		bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributesAndStakeholderCode(qwandaServiceUrl,
				getDecodedTokenMap(), getToken(), parentCode, linkCode, stakeholderCode);
		if (cache) {
			set("BES_" + parentCode.toUpperCase() + "_" + linkCode, bes); // WATCH THIS!!!
		}
		// } else {
		// bes = getAsBaseEntitys("BES_" + parentCode.toUpperCase() + "_" + linkCode);
		// }
		return bes;
	}

	public String moveBaseEntity(final String baseEntityCode, final String sourceCode, final String targetCode,
			final String linkCode) {

		// JsonObject begEntity = new JsonObject();
		// begEntity.put("sourceCode", sourceCode);
		// begEntity.put("targetCode", baseEntityCode);
		// begEntity.put("attributeCode", linkCode);

		Link link = new Link(sourceCode, baseEntityCode, linkCode);

		try {

			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/move/" + targetCode,
					JsonUtils.toJson(link), getToken());

		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public String moveBaseEntitySetLinkValue(final String baseEntityCode, final String sourceCode,
			final String targetCode, final String linkCode, final String linkValue) {

		Link link = new Link(sourceCode, baseEntityCode, linkCode, linkValue);

		try {

			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/move/" + targetCode,
					JsonUtils.toJson(link), getToken());

		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public void publishBaseEntityByCode(final String be) {
		String[] recipientArray = new String[1];
		recipientArray[0] = be;
		publishBaseEntityByCode(be, null, null, recipientArray);
	}

	public void publishBaseEntityByCode(final String be, final String parentCode, final String linkCode,
			final String[] recipientCodes) {

		BaseEntity item = getBaseEntityByCode(be);
		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = item;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode, linkCode);
		msg.setRecipientCodeArray(recipientCodes);
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

		// String json = JsonUtils.toJson(msg);
		// JsonObject obj = JsonUtils.fromJson(json, JsonObject.class);
		// obj.put("token", getToken());
		msg.setToken(getToken());
		publish("cmds", JsonUtils.toJson(msg));
	}

	public <T extends QMessage> void publishData(T msg, final String[] recipientCodes) {

		// String json = JsonUtils.toJson(msg);
		// JsonObject obj = JsonUtils.fromJson(json, JsonObject.class);
		// obj.put("token", getToken());
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
	}

	public <T extends QMessage> void publish(final String busChannel, T msg, final String[] recipientCodes) {

		// String json = JsonUtils.toJson(msg);
		// JsonObject obj = JsonUtils.fromJson(json, JsonObject.class);
		// obj.put("token", getToken());
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

		publish("cmds", RulesUtils.toJsonObject(msg));

	}

	public Object getBaseEntityValue(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getObject();
		} else {
			return null;
		}
	}

	/**
	 *
	 * @param BaseEntity
	 *            object
	 * @param attributeCode
	 * @return The attribute value for the BaseEntity attribute code passed
	 */
	public static String getBaseEntityAttrValueAsString(BaseEntity be, String attributeCode) {

		String attributeVal = null;
		for (EntityAttribute ea : be.getBaseEntityAttributes()) {
			try {
				if (ea.getAttributeCode().equals(attributeCode)) {
					attributeVal = ea.getObjectAsString();
				}
			} catch (Exception e) {
			}
		}

		return attributeVal;
	}

	public String getBaseEntityValueAsString(final String baseEntityCode, final String attributeCode) {

		String attrValue = null;

		if (baseEntityCode != null) {

			BaseEntity be = getBaseEntityByCode(baseEntityCode);
			attrValue = getBaseEntityAttrValueAsString(be, attributeCode);
		}

		return attrValue;
	}

	public LocalDateTime getBaseEntityValueAsLocalDateTime(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getValueDateTime();
		} else {
			return null;
		}
	}

	public LocalDate getBaseEntityValueAsLocalDate(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getValueDate();
		} else {
			return null;
		}
	}

	public LocalTime getBaseEntityValueAsLocalTime(final String baseEntityCode, final String attributeCode) {

		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getValueTime();
		} else {
			return null;
		}
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

	public void sendSlackNotification(String webhookURL, JsonObject message) throws IOException {

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

			String internValue = QRules.getBaseEntityAttrValueAsString(user, "PRI_IS_INTERN");
			Boolean isIntern = internValue != null && (internValue.equals("TRUE") || user.is("PRI_MENTOR"));

			/* Show loading indicator */
			showLoading("Loading your interface...");

			if (isIntern) {

				List<BaseEntity> root = getBaseEntitysByParentAndLinkCode("GRP_ROOT", "LNK_CORE", 0, 20, false);
				publishCmd(root, "GRP_ROOT", "LNK_CORE");

				List<BaseEntity> dashboard = getBaseEntitysByParentAndLinkCode("GRP_DASHBOARD", "LNK_CORE", 0, 20,
						false);
				publishCmd(dashboard, "GRP_DASHBOARD", "LNK_CORE");

				List<BaseEntity> internships = getBaseEntitysByParentAndLinkCode("GRP_INTERNSHIPS", "LNK_CORE", 0, 50,
						false);
				publishCmd(internships, "GRP_INTERNSHIPS", "LNK_CORE");

				List<BaseEntity> companies = getBaseEntitysByParentAndLinkCode("GRP_COMPANYS", "LNK_CORE", 0, 50,
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

	public void sendMentorMatchLayoutsAndData() {
		this.sendMentorMatchLayoutsAndData(false);
	}

	public void sendMentorMatchLayoutsAndData(Boolean forceQuestions) {

		BaseEntity user = getUser();

		if (user != null) {

			String mentorValue = QRules.getBaseEntityAttrValueAsString(user, "PRI_MENTOR");
			String menteeValue = QRules.getBaseEntityAttrValueAsString(user, "PRI_MENTEE");

			Boolean isMentor = mentorValue != null && (mentorValue.equals("TRUE") || mentorValue.equals("true"));
			Boolean isMentee = menteeValue != null && (menteeValue.equals("TRUE") || menteeValue.equals("true"));

			String profile_completed = QRules.getBaseEntityAttrValueAsString(user, "PRI_MENTORMATCH_PROFILE_COMPLETED");

			if (profile_completed == null && !isMentor && !isMentee) {

				this.sendSelections("GRP_USER_ROLE", "LNK_CORE", 10);
				this.askQuestions(getUser().getCode(), getUser().getCode(), "QUE_NEW_USER_PROFILE_GRP_MENTORMATCH");

			} else {

				if (isMentor || isMentee) {

					/* Show loading indicator */
					showLoading("Loading your interface...");

					String mentor_profile_status = QRules.getBaseEntityAttrValueAsString(user,
							"PRI_MENTORMATCH_PROFILE_COMPLETED");
					Boolean hasCompletedProfile = mentor_profile_status != null
							&& (mentor_profile_status.equals("TRUE") || user.is("PRI_MENTORMATCH_PROFILE_COMPLETED"));

					if (!hasCompletedProfile || (forceQuestions != null && forceQuestions == true)) {

						// we send questions for mentors
						if (isMentor && !isMentee) {

							this.sendSelections("GRP_USER_ROLE", "LNK_CORE", 20);
							this.sendSelections("GRP_YEARS_RANGE", "LNK_CORE", 20);
							this.sendSelections("GRP_MEANS_CONTACT", "LNK_CORE", 20);
							this.sendSelections("GRP_GUIDANCE_GRP", "LNK_CORE", 20);
							this.sendSelections("GRP_MENTEES_ATTRIBUTES", "LNK_CORE", 20);
							this.sendSelections("GRP_INDUSTRY_SELECTION", "LNK_CORE", 20);
							this.sendSelections("GRP_FIELD_OF_WORK", "LNK_CORE", 20);
							this.sendSelections("GRP_WORK_LOCATION_MELBOURNE", "LNK_CORE", 20);
							this.sendSelections("GRP_WORKING_STATUS", "LNK_CORE", 20);
							this.sendSelections("GRP_GENDERS", "LNK_CORE", 20);
							this.askQuestions(getUser().getCode(), getUser().getCode(), "QUE_MENTOR_GRP");
						}
						// we send questions for mentees
						else if (isMentee && !isMentor) {

							this.sendSelections("GRP_USER_ROLE", "LNK_CORE", 20);
							this.sendSelections("GRP_YEARS_RANGE", "LNK_CORE", 20);
							this.sendSelections("GRP_MEANS_CONTACT", "LNK_CORE", 20);
							this.sendSelections("GRP_GUIDANCE_GRP", "LNK_CORE", 20);
							this.sendSelections("GRP_MENTORS_ATTRIBUTES", "LNK_CORE", 20);
							this.sendSelections("GRP_INDUSTRY_SELECTION", "LNK_CORE", 20);
							this.sendSelections("GRP_FIELD_OF_WORK", "LNK_CORE", 20);
							this.sendSelections("GRP_WORK_LOCATION_MELBOURNE", "LNK_CORE", 20);
							this.sendSelections("GRP_WORKING_STATUS", "LNK_CORE", 20);
							this.sendSelections("GRP_GENDERS", "LNK_CORE", 20);
							this.askQuestions(getUser().getCode(), getUser().getCode(), "QUE_MENTEE_GRP");
						}
						// we send the super form
						else if (isMentee && isMentor) {

							this.sendSelections("GRP_MENTEES_ATTRIBUTES", "LNK_CORE", 20);
							this.sendSelections("GRP_USER_ROLE", "LNK_CORE", 20);
							this.sendSelections("GRP_YEARS_RANGE", "LNK_CORE", 20);
							this.sendSelections("GRP_MEANS_CONTACT", "LNK_CORE", 20);
							this.sendSelections("GRP_GUIDANCE_GRP", "LNK_CORE", 20);
							this.sendSelections("GRP_MENTORS_ATTRIBUTES", "LNK_CORE", 20);
							this.sendSelections("GRP_INDUSTRY_SELECTION", "LNK_CORE", 20);
							this.sendSelections("GRP_FIELD_OF_WORK", "LNK_CORE", 20);
							this.sendSelections("GRP_WORK_LOCATION_MELBOURNE", "LNK_CORE", 20);
							this.sendSelections("GRP_WORKING_STATUS", "LNK_CORE", 20);
							this.sendSelections("GRP_GENDERS", "LNK_CORE", 20);
							this.askQuestions(getUser().getCode(), getUser().getCode(), "QUE_MENTOR_MENTEE_GRP");
						}

					} else {
						this.sendSublayout("finish", "dashboard_mentormatch.json");
					}
				} else {

					this.sendSelections("GRP_USER_ROLE", "LNK_CORE", 10);
					this.askQuestions(getUser().getCode(), getUser().getCode(), "QUE_NEW_USER_PROFILE_GRP_MENTORMATCH");
				}
			}
		}
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
		if (root != null) {
			cmdJobSublayoutJson.put("root", root);
		}

		publish("cmds", cmdJobSublayoutJson);
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

		publish("cmds", JsonUtils.toJson(msg));
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
		publish("data", RulesUtils.toJsonObject(msg));
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
		publish("cmds", RulesUtils.toJsonObject(msg));
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
		logoutCleanup();
	}

	public void logoutCleanup() {
		// Remove the session from the sessionstates
		Set<String> userSessions = VertxUtils.getSetString("", "SessionStates", getUser().getCode());
		userSessions.remove((String) getDecodedTokenMap().get("session_state"));

		VertxUtils.putSetString("", "SessionStates", getUser().getCode(), userSessions);

		if (userSessions.isEmpty()) {
			updateBaseEntityAttribute(getUser().getCode(), getUser().getCode(), "PRI_ONLINE", "FALSE");
		} else {
			updateBaseEntityAttribute(getUser().getCode(), getUser().getCode(), "PRI_ONLINE", "TRUE");

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

	// public void publishUpdatedLink(final String parentCode, final String
	// linkCode) {
	// QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beList.toArray(new
	// BaseEntity[0]));
	// msg.setParentCode(parentCode);
	// msg.setLinkCode(linkCode);
	// msg.setToken(getToken());
	// publish("cmds", RulesUtils.toJsonObject(msg));
	// }

	public Link[] getUpdatedLink(String parentCode, String linkCode) {
		List<Link> links = getLinks(parentCode, linkCode);
		Link[] items = new Link[links.size()];
		items = (Link[]) links.toArray(items);
		return items;
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

		JsonObject json = new JsonObject(JsonUtils.toJson(cmdMsg));
		json.put("recipientCodeArray", recipients);

		publish("data", json);
	}

	public void publishMsg(final QMSGMessage msg) {

		msg.setToken(getToken());
		publish("messages", RulesUtils.toJsonObject(msg));
	}

	public void publish(String channel, final Object payload) {
		// Actually Send ....
		switch (channel) {
		case "event":
		case "events":
			Producer.getToEvents().send(payload).end();
			;
			break;
		case "data":
			Producer.getToWebData().write(payload).end();
			;
			break;
		case "cmds":
			Producer.getToWebCmds().write(payload).end();
			break;
		case "services":
			Producer.getToServices().send(payload).end();
			break;
		case "messages":
			Producer.getToMessages().send(payload).end();
			break;
		default:
			println("Channel does not exist: " + channel);
		}
	}

	/*
	 * Get user's company code
	 */
	public BaseEntity getParent(final String targetCode, final String linkCode) {

		try {
			String beJson = QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/entityentitys/" + targetCode
					+ "/linkcodes/" + linkCode + "/parents", getToken());
			Link[] linkArray = RulesUtils.fromJson(beJson, Link[].class);
			if (linkArray.length > 0) {
				ArrayList<Link> arrayList = new ArrayList<Link>(Arrays.asList(linkArray));
				Link first = arrayList.get(0);
				RulesUtils.println("The parent code is   ::  " + first.getSourceCode());
				return RulesUtils.getBaseEntityByCode(getQwandaServiceUrl(), getDecodedTokenMap(), getToken(),
						first.getSourceCode(), false);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	/*
	 * Get children of the source code with the linkcode and linkValue
	 */
	public BaseEntity getChildren(final String sourceCode, final String linkCode, final String linkValue) {

		try {
			String beJson = QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/entityentitys/" + sourceCode
					+ "/linkcodes/" + linkCode + "/children/" + linkValue, getToken());
			Link[] linkArray = RulesUtils.fromJson(beJson, Link[].class);
			if (linkArray.length > 0) {
				ArrayList<Link> arrayList = new ArrayList<Link>(Arrays.asList(linkArray));
				Link first = arrayList.get(0);
				RulesUtils.println("The Child BaseEnity code is   ::  " + first.getTargetCode());
				return RulesUtils.getBaseEntityByCode(getQwandaServiceUrl(), getDecodedTokenMap(), getToken(),
						first.getTargetCode(), false);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	/*
	 * Get all childrens for the given source code with the linkcode and linkValue
	 */
	public List<BaseEntity> getAllChildrens(final String sourceCode, final String linkCode, final String linkValue) {
		List<BaseEntity> childList = new ArrayList<BaseEntity>();
		try {
			String beJson = QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/entityentitys/" + sourceCode
					+ "/linkcodes/" + linkCode + "/children/" + linkValue, getToken());
			Link[] linkArray = RulesUtils.fromJson(beJson, Link[].class);
			if (linkArray.length > 0) {
				ArrayList<Link> arrayList = new ArrayList<Link>(Arrays.asList(linkArray));
				for (Link link : arrayList) {
					// RulesUtils.println("The Child BaseEnity code is :: " + link.getTargetCode());
					childList.add(RulesUtils.getBaseEntityByCode(getQwandaServiceUrl(), getDecodedTokenMap(),
							getToken(), link.getTargetCode(), false));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		return childList;
	}

	public List<Link> getLinks(final String parentCode, final String linkCode) {
		List<Link> links = RulesUtils.getLinks(getQwandaServiceUrl(), getDecodedTokenMap(), getToken(), parentCode,
				linkCode);
		return links;
	}

	public QDataAskMessage getAskQuestions(final QDataQSTMessage qstMsg) {
		JsonObject questionJson = null;
		QDataAskMessage msg = null;
		try {
			String json = QwandaUtils.apiPostEntity(getQwandaServiceUrl() + "/qwanda/asks/qst",
					RulesUtils.toJson(qstMsg), getToken());
			msg = RulesUtils.fromJson(json, QDataAskMessage.class);

			RulesUtils.println(qstMsg.getRootQST().getQuestionCode() + " SENT TO FRONTEND");

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
						RulesUtils.toJson(qstMsg), getToken());

				msg = RulesUtils.fromJson(json, QDataAskMessage.class);

				publishData(msg);

				QCmdViewMessage cmdFormView = new QCmdViewMessage(cmd_view, qstMsg.getRootQST().getQuestionCode());
				publishCmd(cmdFormView);
			} else {
				questionJson = new JsonObject(QwandaUtils.apiPostEntity(getQwandaServiceUrl() + "/qwanda/asks/qst",
						RulesUtils.toJson(qstMsg), getToken()));
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

			RulesUtils.println(qstMsg.getRootQST().getQuestionCode() + " SENT TO FRONTEND");

			return msg;
		} catch (IOException e) {
			return msg;
		}
	}

	public void sendQuestions(final String sourceCode, final String targetCode, final String questionCode,
			final boolean autoPushSelections) throws ClientProtocolException, IOException {

		String json = QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/baseentitys/" + sourceCode + "/asks2/"
				+ questionCode + "/" + targetCode, getToken());

		QDataAskMessage msg = null;
		msg = RulesUtils.fromJson(json, QDataAskMessage.class);
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

			RulesUtils.header(drools.getRule().getName() + " - "
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

	public void println(final Object str) {
		RulesUtils.println(str);
	}

	public void println(final Object str, final String colour) {
		RulesUtils.println(str, colour);
	}

	public void update() {
		this.drools.update(this);
	}

	public String removeLink(final String parentCode, final String childCode, final String linkCode) {
		Link link = new Link(parentCode, childCode, linkCode);
		try {
			return QwandaUtils.apiDelete(getQwandaServiceUrl() + "/qwanda/entityentitys", link.toString(), getToken());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;

	}

	/*
	 * @param BaseEntity
	 *
	 * @param token
	 *
	 * @return status
	 */
	public String updateBaseEntity(BaseEntity be) {
		try {
			VertxUtils.writeCachedJson(be.getCode(), JsonUtils.toJson(be));
			return QwandaUtils.apiPutEntity(getQwandaServiceUrl() + "/qwanda/baseentitys", RulesUtils.toJson(be),
					getToken());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
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

			Answer[] newAnswers = new Answer[50];
			Answer[] answers = m.getItems();
			List<Answer> newAnswerList = new ArrayList<Answer>();

			for (Answer answer : answers) {

				String targetCode = answer.getTargetCode();
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
							String jsonAnswer = RulesUtils.toJson(answer);
							Answer answerObj = RulesUtils.fromJson(jsonAnswer, Answer.class);
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
						String jsonAnswer = RulesUtils.toJson(answer);
						Answer answerObj = RulesUtils.fromJson(jsonAnswer, Answer.class);
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
						String jsonAnswer = RulesUtils.toJson(answer);
						Answer answerObj = RulesUtils.fromJson(jsonAnswer, Answer.class);
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
					// String json = RulesUtils.toJson(m);
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

	public BaseEntity updateCachedBaseEntity(final Answer answer) {
		BaseEntity cachedBe = this.getBaseEntityByCode(answer.getTargetCode());
		// Add an attribute if not already there
		try {
			answer.setAttribute(RulesUtils.attributeMap.get(answer.getAttributeCode()));
			if (answer.getAttribute() == null) {
				log.error("Null Attribute");
			} else
				cachedBe.addAnswer(answer);
			VertxUtils.writeCachedJson(answer.getTargetCode(), JsonUtils.toJson(cachedBe));
		} catch (BadDataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return cachedBe;
	}

	public BaseEntity updateCachedBaseEntity(final List<Answer> answers) {
		Answer firstanswer = null;
		if (answers != null) {
			if (!answers.isEmpty()) {
				firstanswer = answers.get(0);
			}
		}
		BaseEntity cachedBe = null;

		if (firstanswer != null) {
			cachedBe = this.getBaseEntityByCodeWithAttributes(firstanswer.getTargetCode());
		} else {
			return null;
		}

		for (Answer answer : answers) {

			// Add an attribute if not already there
			try {
				answer.setAttribute(RulesUtils.attributeMap.get(answer.getAttributeCode()));
				if (answer.getAttribute() == null) {
					log.error("Null Attribute");
				} else
					cachedBe.addAnswer(answer);

			} catch (BadDataException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		VertxUtils.writeCachedJson(cachedBe.getCode(), JsonUtils.toJson(cachedBe));
		return cachedBe;
	}

	public void saveAnswer(Answer answer) {

		try {
			updateCachedBaseEntity(answer);
			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/answers", RulesUtils.toJson(answer), getToken());
			// Now update the Cache

		} catch (IOException e) {
			e.printStackTrace();
		}
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

				List<BaseEntity> stakeholders = getBaseEntitysByParentAndLinkCode(chatCode, "LNK_USER");
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
				saveAnswers(answers);
				/* Add current date-time to char as */
				saveAnswer(new Answer(chatCode, chatCode, "PRI_DATE_LAST_MESSAGE",
						QwandaUtils.getZonedCurrentLocalDateTime()));

				System.out.println("The recipients are :: " + Arrays.toString(msgReceiversCodeArray));
				/* Publish chat to Receiver */
				publishData(getBaseEntityByCode(chatCode), msgReceiversCodeArray);
				/* Publish message to Receiver */
				publishData(getBaseEntityByCode(newMessage.getCode()), msgReceiversCodeArray); // Had to use getCode()
																								// to get data from DB,
																								// it was missing
																								// attribute
				QwandaUtils.createLink(chatCode, newMessage.getCode(), "LNK_MESSAGES", "message", 1.0, getToken());// Creating
																													// link
				// after sending both chat
				// and msg BE as front-end wants
				// BE's before linkChange event message

				/* Sending Messages */
				HashMap<String, String> contextMap = new HashMap<String, String>();
				contextMap.put("SENDER", getUser().getCode());
				contextMap.put("CONVERSATION", newMessage.getCode());

				/* Sending toast message to all the beg frontends */
				sendMessage("", msgReceiversCodeArray, contextMap, "MSG_CH40_NEW_MESSAGE_RECIEVED", "TOAST");// TODO:
																												// TOAST
																												// needs
																												// to be
																												// removed
																												// when
																												// notification
																												// is
																												// implemented
				sendMessage("", msgReceiversCodeArray, contextMap, "MSG_CH40_NEW_MESSAGE_RECIEVED", "SMS");// TODO: SMS
																											// needs to
																											// be
																											// removed
																											// when push
																											// notification
																											// in mobile
																											// is
																											// implemented
				sendMessage("", msgReceiversCodeArray, contextMap, "MSG_CH40_NEW_MESSAGE_RECIEVED", "EMAIL");
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
						this.updateBaseEntityAttribute(sourceCode, targetCode, finalAttributeCode, jsonStringImage);
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
				String currentRatingString = getBaseEntityValueAsString(targetCode, finalAttributeCode);
				String numberOfRatingString = getBaseEntityValueAsString(targetCode, "PRI_NUMBER_RATING");

				if (currentRatingString == null)
					currentRatingString = "0";
				if (numberOfRatingString == null)
					numberOfRatingString = "0";

				if (currentRatingString != null && numberOfRatingString != null) {

					Double currentRating = Double.parseDouble(currentRatingString);
					Double numberOfRating = Double.parseDouble(numberOfRatingString);
					Double newRating = Double.parseDouble(value);

					/* we increment the number of current ratings */
					numberOfRating += 1;
					answerList.add(
							new Answer(sourceCode, targetCode, "PRI_NUMBER_RATING", Double.toString(numberOfRating)));

					/* we compute the new rating */

					/*
					 * because for now we are not storing ALL the previous ratings, we calculate a
					 * rolling average
					 */

					Double newRatingAverage = currentRating / numberOfRating;
					newRatingAverage += newRating / numberOfRating;
					answerList.add(
							new Answer(sourceCode, targetCode, finalAttributeCode, Double.toString(newRatingAverage)));

				}
				saveAnswers(answerList);
				/* publishData(answer); */
			}
		}
	}

	public List<Answer> processAnswer(QDataAnswerMessage m) {

		/* extract answers */
		List<Answer> answerList = new ArrayList<Answer>(Arrays.asList(m.getItems()));
		;

		// /* extract answers */
		// Answer[] answers = m.getItems();
		// for (Answer answer : answers) {
		// answerList.add(answer);
		// // println("ANSWER:" + answer);
		//
		// }

		// saveAnswers(answerList);
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

		saveAnswers(answerList);

	}

	/**
	 * @param answers
	 */
	public void saveAnswers(List<Answer> answers, final boolean changeEvent) {
		if (!changeEvent) {
			for (Answer answer : answers) {
				answer.setChangeEvent(false);
			}
		}
		Answer items[] = new Answer[answers.size()];
		items = answers.toArray(items);

		QDataAnswerMessage msg = new QDataAnswerMessage(items);

		updateCachedBaseEntity(answers);

		String jsonAnswer = RulesUtils.toJson(msg);
		jsonAnswer.replace("\\\"", "\"");

		try {
			QwandaUtils.apiPostEntity(getQwandaServiceUrl() + "/qwanda/answers/bulk2", jsonAnswer, token);
		} catch (IOException e) {
			log.error("Socket error trying to post answer");
		}
	}

	/**
	 * @param answers
	 */
	public void saveAnswers(List<Answer> answers) {
		saveAnswers(answers, true);
	}

	public void startWorkflow(final String id) {
		startWorkflow(id, new HashMap<String, Object>());
	}

	public void startWorkflow(final String id, Map<String, Object> parms) {
		println("Starting process " + id);
		if (drools != null) {
			drools.getKieRuntime().startProcess(id, parms);
		}
	}

	public void askQuestionFormViewPublish(String sourceCode, String targetCode, String questionCode) {

		String json;
		try {
			json = QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/baseentitys/" + sourceCode + "/asks2/"
					+ questionCode + "/" + targetCode, getToken());

			QDataAskMessage msg = RulesUtils.fromJson(json, QDataAskMessage.class);

			println("QDataAskMessage for payments question group ::" + msg);

			msg.setToken(getToken());
			publish("cmds", RulesUtils.toJsonObject(msg));

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

	public Link createLink(String groupCode, String targetCode, String linkCode, String linkValue, Double weight) {

		log.info("CREATING LINK between " + groupCode + "and" + targetCode + "with LINK VALUE = " + linkValue);
		Link link = new Link(groupCode, targetCode, linkCode, linkValue);
		link.setWeight(weight);
		try {
			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/entityentitys", RulesUtils.toJson(link), getToken());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return link;
	}

	public Link updateLink(String groupCode, String targetCode, String linkCode, String linkValue, Double weight) {

		log.info("UPDATING LINK between " + groupCode + "and" + targetCode + "with LINK VALUE = " + linkValue);
		Link link = new Link(groupCode, targetCode, linkCode, linkValue);
		link.setWeight(weight);
		try {
			QwandaUtils.apiPutEntity(qwandaServiceUrl + "/qwanda/links", RulesUtils.toJson(link), getToken());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return link;
	}

	public Link updateLink(String groupCode, String targetCode, String linkCode, Double weight) {

		log.info("UPDATING LINK between " + groupCode + "and" + targetCode);
		Link link = new Link(groupCode, targetCode, linkCode);
		link.setWeight(weight);
		try {
			QwandaUtils.apiPutEntity(qwandaServiceUrl + "/qwanda/links", RulesUtils.toJson(link), getToken());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return link;
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

		Layout notificationLayout = new Layout(text, style);
		QDataSubLayoutMessage data = new QDataSubLayoutMessage(notificationLayout, getToken());
		data.setRecipientCodeArray(recipientCodes);
		publishCmd(data);
	}

	private void sendSublayouts(final String realm) throws ClientProtocolException, IOException {

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

						String subLayoutString = null;

						try {
							subLayoutString = QwandaUtils.apiGet(url, null);
						} catch (UnknownHostException e1) {
							log.error("Unknown layout host " + url + ":" + sublayoutData);
						}
						if (subLayoutString != null) {

							try {
								layoutArray[i] = new Layout(name, subLayoutString);
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
	}

	public void sendSubLayouts() throws ClientProtocolException, IOException {
		this.sendSublayouts("shared");
		this.sendSublayouts(realm());
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

	/*
	 * Gets all the attribute and their value for the given basenentity code
	 */
	public Map<String, String> getMapOfAllAttributesValuesForBaseEntity(String beCode) {
		BaseEntity be = getBaseEntityByCode(beCode);
		println("The load is ::" + be);
		Set<EntityAttribute> eaSet = be.getBaseEntityAttributes();
		println("The set of attributes are  :: " + eaSet);
		Map<String, String> attributeValueMap = new HashMap<String, String>();
		for (EntityAttribute ea : eaSet) {
			String attributeCode = ea.getAttributeCode();
			println("The attribute code  is  :: " + attributeCode);
			String value = ea.getAsLoopString();
			attributeValueMap.put(attributeCode, value);
		}

		return attributeValueMap;
	}

	public void processDimensions(QEventAttributeValueChangeMessage msg) {
		Answer newAnswer = msg.getAnswer();
		BaseEntity load = getBaseEntityByCode(newAnswer.getTargetCode());
		println("The laod value is " + load.toString());

		String value = newAnswer.getValue();
		println("The load " + msg.getData().getCode() + " is    ::" + value);

		/* Get the sourceCode(Job code) for this LOAD */
		BaseEntity job = getParent(newAnswer.getTargetCode(), "LNK_BEG");

		Answer jobDimensionAnswer = new Answer(getUser().getCode(), job.getCode(), msg.getData().getCode(), value);
		saveAnswer(jobDimensionAnswer);
	}

	public String getCurrentLocalDateTime() {
		LocalDateTime date = LocalDateTime.now();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");
		Date datetime = Date.from(date.atZone(ZoneId.systemDefault()).toInstant());
		String dateString = df.format(datetime);

		return dateString;

	}

	public void publishBE(final BaseEntity be) {
		addAttributes(be);
		String[] recipientCodes = new String[1];
		recipientCodes[0] = be.getCode();
		publishBE(be, recipientCodes);
	}

	public void publishBE(final BaseEntity be, String[] recipientCodes) {
		addAttributes(be);
		if (recipientCodes == null || recipientCodes.length == 0) {
			recipientCodes = new String[1];
			recipientCodes[0] = getUser().getCode();
		}
		println("PUBLISHBE:" + be.getCode() + " with " + be.getBaseEntityAttributes().size() + " attribute changes");
		// for (EntityAttribute ea : be.getBaseEntityAttributes()) {
		//
		// if
		// (ea.getAttribute().getDataType().getTypeName().equals("org.javamoney.moneta.Money"))
		// {
		// // Money mon = JsonUtils.fromJson(ea.getValueString(), Money.class);
		// println("Money=" + ea.getValueMoney());
		// // BigDecimal bd = new BigDecimal(mon.getNumber().toString());
		// // Money hacked = Money.of(bd, mon.getCurrency());
		// // ea.setValueMoney(hacked);
		// break;
		// }
		// }
		BaseEntity[] itemArray = new BaseEntity[1];
		itemArray[0] = be;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, null, null);
		msg.setRecipientCodeArray(recipientCodes);
		// String json = JsonUtils.toJson(msg);
		publishData(msg, recipientCodes);
	}

	public BaseEntity createBaseEntityByCode(final String userCode, final String bePrefix, final String name) {
		BaseEntity beg = QwandaUtils.createBaseEntityByCode(
				QwandaUtils.getUniqueId(userCode, null, bePrefix, getToken()), name, qwandaServiceUrl, getToken());
		addAttributes(beg);
		VertxUtils.writeCachedJson(beg.getCode(), JsonUtils.toJson(beg));
		return beg;
	}

	public BaseEntity createBaseEntityByCode2(final String beCode, final String name) {
		BaseEntity beg = QwandaUtils.createBaseEntityByCode(beCode, name, qwandaServiceUrl, getToken());
		addAttributes(beg);
		VertxUtils.writeCachedJson(beg.getCode(), JsonUtils.toJson(beg));
		return beg;
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
		}
		results = (String[]) FluentIterable.from(recipientCodesSet).toArray(String.class);
		return results;
	}

	public void subscribeUserToBaseEntityCode(String userCode, String beCode) {
		VertxUtils.subscribe(realm(), beCode, userCode);
	}

	public void subscribeUserToBaseEntity(String userCode, BaseEntity be) {
		VertxUtils.subscribe(realm(), be, userCode);
	}

	public void subscribeUserToBaseEntities(String userCode, List<BaseEntity> bes) {
		VertxUtils.subscribe(realm(), bes, userCode);
	}

	public void sendLayoutsAndData() {

		BaseEntity user = getUser();

		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();

		if (!getUser().is("PRI_OWNER")) {
		QBulkMessage newItems = VertxUtils.getObject(realm(), "SEARCH", "SBE_NEW_ITEMS", QBulkMessage.class);

		if (newItems != null) {
			showLoading("Loading Cached new jobs...");
			if (newItems.getMessages() != null) {
				for (QDataBaseEntityMessage msg : newItems.getMessages()) {
					if (msg instanceof QDataBaseEntityMessage) {
						msg.setToken(getToken());
						publishCmd(JsonUtils.toJson(msg));
					}
				}
			}
		}
		}
		
		QBulkMessage items = fetchStakeholderBucketItems(getUser());

		if (items != null) {
			showLoading("Loading the rest of the jobs...");
			if (items.getMessages() != null) {
				for (QDataBaseEntityMessage msg : items.getMessages()) {
				
					if (msg instanceof QDataBaseEntityMessage) {
						msg.setToken(getToken());
						publishCmd(JsonUtils.toJson(msg));
					}
				}
			}
		}


		// List<BaseEntity> buckets = getBaseEntitysByParentAndLinkCode("GRP_DASHBOARD",
		// "LNK_CORE", 0, 20, false);
		// bulkmsg.add(publishCmd(buckets, "GRP_DASHBOARD", "LNK_CORE"));
		// // println(buckets);
		//
		// if (true) {
		// for (BaseEntity bucket : buckets) {
		// // println(bucket);
		// List<BaseEntity> begs = new ArrayList<BaseEntity>();
		//
		// if (hasRole("admin")) {
		// List<BaseEntity> driverbegs =
		// getBaseEntitysByParentAndLinkCode(bucket.getCode(), "LNK_CORE", 0,
		// 500, false);
		// begs.addAll(driverbegs);
		// } else {
		//
		// if (getUser().is("PRI_DRIVER") && bucket.getCode().equals("GRP_NEW_ITEMS")) {
		// List<BaseEntity> driverbegs =
		// getBaseEntitysByParentAndLinkCode(bucket.getCode(), "LNK_CORE", 0,
		// 500, false);
		// begs.addAll(driverbegs);
		// VertxUtils.subscribe(realm(), bucket, user.getCode()); /* monitor anything in
		// first bucket */
		// } else {
		// if (user.is("PRI_DRIVER") && !bucket.getCode().equals("GRP_NEW_ITEMS")) {
		// List<BaseEntity> driverbegs =
		// getBaseEntitysByParentAndLinkCode(bucket.getCode(),
		// "LNK_CORE", 0, 500, false, user.getCode());
		// for (BaseEntity beg : driverbegs) {
		// if ("BEG_TOCAD79753DCC0214BCE87A8863F74F4BAEE".equals(beg.getCode())) {
		// log.info("ANNOYING BEG BEG_TOCAD79753DCC0214BCE87A8863F74F4BAEE");
		// }
		// /* Getting begs related to this driver only */
		// String driverCode = beg.getValue("STT_IN_TRANSIT", null);
		// if (driverCode != null && driverCode.equals(user.getCode())) {
		// VertxUtils.subscribe(realm(), beg.getCode(), user.getCode());
		// begs.add(beg);
		// } else {
		// /*
		// * Another check to handle loads which misses STT_IN_TRANSIT attribute in
		// * Production due to bug (It was not saving STT_IN_TRANSIT attribute while
		// * saving in bulk)
		// */
		// BaseEntity driver = getChildren(beg.getCode(), "LNK_BEG", "DRIVER");
		// if (driver != null && driver.getCode().equals(user.getCode())) {
		// VertxUtils.subscribe(realm(), beg.getCode(), user.getCode());
		// begs.add(beg);
		// }
		//
		// }
		//
		// }
		//
		// // begs.addAll(driverbegs);
		// // VertxUtils.subscribe(realm(), driverbegs, user.getCode());
		// }
		// }
		//
		// if (user.is("PRI_OWNER")) {
		// if ("GRP_NEW_ITEMS".equals(bucket.getCode())) {
		// log.debug("Check owner debug");
		// }
		// List<BaseEntity> ownerbegs =
		// getBaseEntitysByParentAndLinkCode(bucket.getCode(), "LNK_CORE", 0,
		// 500, false, user.getCode());
		// begs.addAll(ownerbegs);
		// VertxUtils.subscribe(realm(), ownerbegs, user.getCode());
		// }
		// }
		// println("FETCHED " + begs.size() + " JOBS FOR " + user.getCode());
		// bulkmsg.add(publishCmd(begs, bucket.getCode(), "LNK_CORE"));
		//
		// for (BaseEntity beg : begs) {
		// List<BaseEntity> begKids = getBaseEntitysByParentAndLinkCode(beg.getCode(),
		// "LNK_BEG", 0, 20,
		// false);
		// List<BaseEntity> filteredKids = new ArrayList<BaseEntity>();
		// for (BaseEntity begKid : begKids) {
		// if (begKid.getCode().startsWith("OFR_")) {
		// if (user.is("PRI_OWNER")) {
		// filteredKids.add(begKid);
		// VertxUtils.subscribe(realm(), begKid.getCode(), user.getCode());
		// }
		// if (user.is("PRI_DRIVER")) {
		// Optional<String> quoterCode = begKid.getLoopValue("PRI_QUOTER_CODE");
		// if (quoterCode.isPresent()) {
		// if (user.getCode().equals(quoterCode.get())) {
		// filteredKids.add(begKid);
		// VertxUtils.subscribe(realm(), begKid.getCode(), user.getCode());
		// }
		// }
		// }
		// } else {
		// filteredKids.add(begKid);
		// }
		// // println(bucket.getCode() + ":" + begKid.getCode());
		// }
		// bulkmsg.add(publishCmd(filteredKids, beg.getCode(), "LNK_BEG"));
		// }
		// // bulkmsg.add(publishCmd(filteredKids, beg.getCode(), "LNK_BEG"));
		// }
		// } else {
		// // fetch all the jobs related to this person
		//
		// }
		// /* Sending Draft Datas for the Owners */
		// if (user.is("PRI_OWNER")) {
		// List<BaseEntity> ownerDraftBegs =
		// getBaseEntitysByParentAndLinkCode("GRP_DRAFTS", "LNK_CORE", 0, 500, false,
		// user.getCode());
		// bulkmsg.add(publishCmd(ownerDraftBegs, "GRP_DRAFTS", "LNK_BEG"));
		// }

	}

	/**
	 * @param bulkmsg
	 * @return
	 */
	private void sendTreeViewData(List<QDataBaseEntityMessage> bulkmsg, BaseEntity user) {

		List<BaseEntity> root = getBaseEntitysByParentAndLinkCode("GRP_ROOT", "LNK_CORE", 0, 20, false);
		List<BaseEntity> toRemove = new ArrayList<BaseEntity>();
		/* Removing GRP_DRAFTS be if user is a Driver */
		if (((user.is("PRI_DRIVER")))) {
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

		List<BaseEntity> reportsHeader = getBaseEntitysByParentAndLinkCode("GRP_REPORTS", "LNK_CORE", 0, 20, false);
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
			if ((user.is("PRI_DRIVER"))) {
				for (BaseEntity be : reportsHeader) {
					if (be.getCode().equalsIgnoreCase("GRP_REPORTS_OWNER")) {
						reportsHeaderToRemove.add(be);
					}
				}
			}
			// Checking for owner role
			else if ((user.is("PRI_OWNER"))) {
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

		List<BaseEntity> admin = getBaseEntitysByParentAndLinkCode("GRP_ADMIN", "LNK_CORE", 0, 20, false);
		bulkmsg.add(publishCmd(admin, "GRP_ADMIN", "LNK_CORE"));

		/*
		 * if(hasRole("admin")){ List<BaseEntity> reports =
		 * getBaseEntitysByParentAndLinkCode("GRP_REPORTS", "LNK_CORE", 0, 20, false);
		 * publishCmd(reports, "GRP_REPORTS", "LNK_CORE"); }
		 */
		if (!user.is("PRI_DRIVER")) {
			List<BaseEntity> bin = getBaseEntitysByParentLinkCodeAndLinkValue("GRP_BIN", "LNK_CORE", user.getCode(), 0,
					20, false);
			bulkmsg.add(publishCmd(bin, "GRP_BIN", "LNK_CORE"));
		}

	}

	static QBulkMessage cache = null;
	static String cache2 = null;

	public void addAttributes(BaseEntity be) {
		if (!be.getCode().startsWith("SBE_")) { // don't bother with search be
			for (EntityAttribute ea : be.getBaseEntityAttributes()) {
				if (ea != null) {
					Attribute attribute = RulesUtils.attributeMap.get(ea.getAttributeCode());
					if (attribute != null) {
						ea.setAttribute(attribute);
					} else {
						RulesUtils.loadAllAttributesIntoCache(getToken());
						attribute = RulesUtils.attributeMap.get(ea.getAttributeCode());
						if (attribute != null) {
							ea.setAttribute(attribute);
						} else {
							log.error("Cannot get Attribute - " + ea.getAttributeCode());
							Attribute dummy = new AttributeText(ea.getAttributeCode(), ea.getAttributeCode());
							ea.setAttribute(dummy);

						}
					}
				}
			}
		}
	}

	/*
	 * Method to send All the Chats for the current user
	 */
	public void sendAllChats(final int pageStart, final int pageSize) {
		BaseEntity currentUser = getUser();
		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();
		QDataBaseEntityMessage qMsg;
		SearchEntity sendAllChats = new SearchEntity("SBE_AllMYCHAT", "All My Chats").addColumn("PRI_TITLE", "Title")
				.addColumn("PRI_DATE_LAST_MESSAGE", "Last Message On")

				.setStakeholder(getUser().getCode())

				.addSort("PRI_DATE_LAST_MESSAGE", "Recent Message", SearchEntity.Sort.DESC) // Sort doesn't work in
																							// local, need
																							// to be tested in prod
																							// before deploying
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
							users.add(getBaseEntityByCode(links.getLink().getTargetCode()));
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
		/* Save Payment-related answers as user/BEG attributes */
		String userCode = getUser().getCode();
		// String begCode = PaymentUtils.processPaymentAnswers(getQwandaServiceUrl(), m,
		// getToken());

		String begCode = null;
		Answer[] dataAnswers = m.getItems();
		for (Answer answer : dataAnswers) {

			String targetCode = answer.getTargetCode();
			String sourceCode = answer.getSourceCode();
			String attributeCode = answer.getAttributeCode();
			String value = answer.getValue();

			begCode = targetCode;

			log.debug("Payments value ::" + value + "attribute code ::" + attributeCode);
			System.out.println("Payments value ::" + value + "attribute code ::" + attributeCode);
			System.out.println("Beg code ::" + begCode);

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
					saveAnswer(ipAnswer);
					// saveAnswer(qwandaServiceUrl, ipAnswer, tokenString);
				}

				if (accountId != null) {
					Answer accountIdAnswer = new Answer(sourceCode, begCode, "PRI_ACCOUNT_ID", accountId);
					userSpecificAnswers.add(accountIdAnswer);
					saveAnswer(accountIdAnswer);
					// saveAnswer(qwandaServiceUrl, accountIdAnswer, tokenString);
				}

				if (deviceId != null) {
					Answer deviceIdAnswer = new Answer(sourceCode, userCode, "PRI_DEVICE_ID", deviceId);
					userSpecificAnswers.add(deviceIdAnswer);
					saveAnswer(deviceIdAnswer);
					// saveAnswer(qwandaServiceUrl, deviceIdAnswer, tokenString);
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
			BaseEntity beg = getBaseEntityByCode(begCode);
			String offerCode = beg.getLoopValue("STT_HOT_OFFER", null);

			if (offerCode != null) {

				/* Make payment */
				showLoading("Processing payment...");

				BaseEntity offer = getBaseEntityByCode(offerCode);
				/*
				 * makePaymentWithResponse(BaseEntity userBe, BaseEntity offerBe, BaseEntity
				 * begBe, String authToken)
				 */
				PaymentsResponse makePaymentResponseObj = PaymentUtils.makePaymentWithResponse(userBe, offer, beg,
						assemblyAuthKey);

				println("isMakePaymentSucceeded ::" + makePaymentResponseObj.toString());

				/* GET offer Base Entity */

				String quoterCode = offer.getLoopValue("PRI_QUOTER_CODE", null);

				if (!makePaymentResponseObj.getIsSuccess()) {
					/* TOAST :: FAIL */
					println("Sending error toast since make payment failed");
					HashMap<String, String> contextMap = new HashMap<String, String>();
					contextMap.put("DRIVER", quoterCode);
					contextMap.put("JOB", begCode);
					contextMap.put("QUOTER", quoterCode);
					String[] recipientArr = { userCode };

					/* Need to display error toast if make payment fails */
					sendMessage(null, recipientArr, contextMap, "MSG_CH40_MAKE_PAYMENT_FAILED", "TOAST");
					/* sending cmd BUCKETVIEW */
					drools.setFocus("SendLayoutsAndData");
				}

				if (makePaymentResponseObj.getIsSuccess()) {
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
					answers.add(new Answer(begCode, begCode, "PRI_OWNER_PRICE_EXC_GST",
							JsonUtils.toJson(ownerPriceExcGST)));
					answers.add(new Answer(begCode, begCode, "PRI_OWNER_PRICE_INC_GST",
							JsonUtils.toJson(ownerPriceIncGST)));
					answers.add(new Answer(begCode, begCode, "PRI_DRIVER_PRICE_EXC_GST",
							JsonUtils.toJson(driverPriceExcGST)));
					answers.add(new Answer(begCode, begCode, "PRI_DRIVER_PRICE_INC_GST",
							JsonUtils.toJson(driverPriceIncGST)));
					answers.add(new Answer(begCode, begCode, "PRI_FEE_EXC_GST", JsonUtils.toJson(feePriceExcGST)));
					answers.add(new Answer(begCode, begCode, "PRI_FEE_INC_GST", JsonUtils.toJson(feePriceIncGST)));

					answers.add(new Answer(begCode, begCode, "PRI_DEPOSIT_REFERENCE_ID",
							makePaymentResponseObj.getResponseMap().get("depositReferenceId")));

					// fetch the job to ensure the cache has caught up
					/*
					 * BaseEntity begBe = null; try { begBe =
					 * QwandaUtils.getBaseEntityByCode(begCode, getToken()); } catch (IOException e)
					 * { e.printStackTrace(); }
					 */

					/* Update BEG to have DRIVER_CODE as an attribute */
					answers.add(new Answer(begCode, begCode, "STT_IN_TRANSIT", quoterCode));
					saveAnswers(answers);

					BaseEntity loadBe = getChildren(begCode, "LNK_BEG", "LOAD");

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
					// sendMessage("", recipientArr, contextMap, "MSG_CH40_CONFIRM_QUOTE_OWNER",
					// "TOAST");
					sendMessage("", recipientArr, contextMap, "MSG_CH40_CONFIRM_QUOTE_OWNER", "EMAIL");

					/* QUOTER config */
					HashMap<String, String> contextMapForDriver = new HashMap<String, String>();
					contextMapForDriver.put("JOB", begCode);
					contextMapForDriver.put("OWNER", userCode);
					contextMapForDriver.put("OFFER", offer.getCode());
					contextMapForDriver.put("LOAD", loadBe.getCode());

					String[] recipientArrForDriver = { quoterCode };

					/* Sending messages to DRIVER - Email and sms enabled */
					sendMessage("", recipientArrForDriver, contextMapForDriver, "MSG_CH40_CONFIRM_QUOTE_DRIVER",
							"TOAST");
					sendMessage("", recipientArrForDriver, contextMapForDriver, "MSG_CH40_CONFIRM_QUOTE_DRIVER", "SMS");
					sendMessage("", recipientArrForDriver, contextMapForDriver, "MSG_CH40_CONFIRM_QUOTE_DRIVER",
							"EMAIL");

					/* Update link between BEG and OFFER to weight= 0 */
					// updateLink(begCode, offerCode, "LNK_BEG", "OFFER", 1.0);

					/* Allocate QUOTER as Driver */
					updateLink(begCode, quoterCode, "LNK_BEG", "DRIVER", 1.0);

					/* Update link between BEG and Accepted OFFER to weight = 100 */
					updateLink(begCode, offerCode, "LNK_BEG", "ACCEPTED_OFFER", 100.0);

					/* Set PRI_NEXT_ACTION to Disabled for all other Offers */
					// get all offers
					List<BaseEntity> offers = getAllChildrens(begCode, "LNK_BEG", "OFFER");
					println("All the Offers for the load " + begCode + " are: " + offers.toString());
					for (BaseEntity be : offers) {
						if (!(be.getCode().equals(offerCode))) {
							println("The BE is : " + be.getCode());
							/* Update PRI_NEXT_ACTION to Disabled */
							updateBaseEntityAttribute(getUser().getCode(), be.getCode(), "PRI_NEXT_ACTION", "DISABLED");
						}
					}

					/* SEND (OFFER, QUOTER, BEG) BaseEntitys to recipients */
					String[] offerRecipients = VertxUtils.getSubscribers(realm(), offer.getCode());
					println("OFFER subscribers   ::   " + Arrays.toString(offerRecipients));
					publishBaseEntityByCode(userCode, begCode, "LNK_BEG", offerRecipients); /* OWNER */
					publishBaseEntityByCode(quoterCode, begCode, "LNK_BEG", offerRecipients);
					publishBaseEntityByCode(offerCode, begCode, "LNK_BEG", offerRecipients);
					publishBaseEntityByCode("GRP_NEW_ITEMS", begCode, "LNK_CORE", offerRecipients);

					/* Set progression of LOAD delivery to 0 */
					Answer updateProgressAnswer = new Answer(begCode, begCode, "PRI_PROGRESS", Double.toString(0.0));
					saveAnswer(updateProgressAnswer);

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

						println("unsubscribe arr ::" + Arrays.toString(unsubscribeArr));

						/* Move BEG to GRP_APPROVED */
						fastClearBaseEntity(begCode, unsubscribeArr);

						BaseEntity begbe = getBaseEntityByCode(begCode);
						println("be   ::   " + begbe);

						Set<EntityAttribute> attributes = begbe.getBaseEntityAttributes();
						begbe.setBaseEntityAttributes(attributes);

						QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(begbe);
						beMsg.setDelete(true);
						publishData(beMsg, unsubscribeArr);

						VertxUtils.unsubscribe(realm(), "GRP_NEW_ITEMS", unsubscribeSet);
					}

					// moveBaseEntity(begCode, "GRP_NEW_ITEMS", "GRP_APPROVED", "LNK_CORE");
					moveBaseEntitySetLinkValue(begCode, "GRP_NEW_ITEMS", "GRP_APPROVED", "LNK_CORE", "BEG");

					/* Update PRI_NEXT_ACTION = OWNER */
					Answer begNextAction = new Answer(userCode, offerCode, "PRI_NEXT_ACTION", "NONE");
					saveAnswer(begNextAction);

					/* Update the Job status */
					updateBaseEntityAttribute(getUser().getCode(), begCode, "STA_" + quoterCode,
							Status.NEEDS_ACTION.value());
					updateBaseEntityAttribute(getUser().getCode(), begCode, "STA_" + getUser().getCode(),
							Status.NEEDS_NO_ACTION.value());

					/* sending cmd BUCKETVIEW */
					drools.setFocus("SendLayoutsAndData");

					/*
					 * List<BaseEntity> listBe = new ArrayList<>(); listBe.add(getUser());
					 * listBe.add(getBaseEntityByCode(quoterCode));
					 * listBe.add(getBaseEntityByCode(offerCode));
					 * listBe.add(getBaseEntityByCode(begCode)); publishCmd(listBe, "GRP_ROOT",
					 * "LNK_CORE"); sendSublayout("BUCKET_DASHBOARD", "dashboard_channel40.json",
					 * "GRP_DASHBOARD"); setLastLayout( "BUCKET_DASHBOARD", "GRP_DASHBOARD" );
					 */

				}
				setState("PAYMENT_DONE");

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

				List<BaseEntity> jobsInTransit = getBaseEntitysByAttributeAndValue("STT_IN_TRANSIT",
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
					saveAnswers(answers);
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
		saveAnswer(verificationCodeAns);

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
				+ Integer.parseInt(getBaseEntityValueAsString(userCode, "PRI_VERIFICATION_CODE")));
		println("User Entered Passcode is ::" + Integer.parseInt(userPassCode));

		if (getBaseEntityValueAsString(userCode, "PRI_VERIFICATION_CODE").equals(userPassCode)) {
			return true;
		} else
			return false;
	}

	public void clearBaseEntityAttr(String userCode) {
		BaseEntity be = getBaseEntityByCode(userCode);
		println("be   ::   " + be);

		Set<EntityAttribute> attributes = be.getBaseEntityAttributes();
		println("Size all   ::   " + attributes.size());
		Set<EntityAttribute> removeAttributes = new HashSet<EntityAttribute>();

		for (EntityAttribute attribute : attributes) {

			switch (attribute.getAttributeCode()) {

			case ("PRI_UUID"):
				removeAttributes.add(attribute);
				break;

			case ("PRI_FIRSTNAME"):
				removeAttributes.add(attribute);
				break;

			case ("PRI_LASTNAME"):
				removeAttributes.add(attribute);
				break;

			case ("PRI_EMAIL"):
				removeAttributes.add(attribute);
				break;

			case ("PRI_USERNAME"):
				removeAttributes.add(attribute);
				break;

			case ("PRI_KEYCLOAK_UUID"):
				removeAttributes.add(attribute);
				break;

			case ("PRI_FB_BASIC"):
				removeAttributes.add(attribute);
				break;
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
		publishData(beMsg);

	}

	/* sets delete field to true so that FE removes the BE from their store */
	public void clearBaseEntity(String baseEntityCode, String[] recipients) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
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
		BaseEntity beg = getBaseEntityByCode(m.getItemCode()); // Get Baseentity once so we don't need to keep
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
		String linkQuoter = "QUOTER";
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
		BaseEntity offer = createBaseEntityByCode(getUser().getCode(), "OFR", "Offer");
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

		saveAnswers(answerList);
		/* Update the number of offers for BEG */
		Integer offerCount = beg.getLoopValue("PRI_OFFER_COUNT", 0);
		offerCount = offerCount + 1;
		println("Offer Count is   ::   " + offerCount);

		saveAnswer(new Answer(beg.getCode(), beg.getCode(), "PRI_OFFER_COUNT", offerCount.toString()));

		/* Determine the recipient code */
		String[] recipients = VertxUtils.getSubscribers(realm(), beg.getCode());
		println("BEG subscribers   ::   " + Arrays.toString(recipients));

		/* link BEG and OFFER BE || OFFER */
		createLink(beg.getCode(), offer.getCode(), linkCode, linkOffer, 1.0);
		/* link BEG and QUOTER BE || QUOTER */
		createLink(beg.getCode(), getUser().getCode(), linkCode, linkQuoter, 1.0);
		/* link OFFER and QUOTER BE || CREATOR */
		createLink(offer.getCode(), getUser().getCode(), "LNK_OFR", linkCreator, 1.0);

		/* set Status of the job */
		/* get Owner of the job */
		BaseEntity owner = getChildren(beg.getCode(), "LNK_BEG", "OWNER");
		// updateBaseEntityAttribute(getUser().getCode(), beg.getCode(), "STA_STATUS",
		// "#FFA500");
		answerList = new ArrayList<Answer>();
		answerList.add(new Answer(getUser().getCode(), beg.getCode(), "STA_" + getUser().getCode(),
				Status.NEEDS_ACTION.value()));
		answerList.add(
				new Answer(getUser().getCode(), beg.getCode(), "STA_" + owner.getCode(), Status.NEEDS_ACTION.value()));
		saveAnswers(answerList);
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

		BaseEntity loadType = getBaseEntityByCode(loadCategoryCode, false); // no attributes

		/* creating new Answer */
		Answer newAnswer = new Answer(answer.getSourceCode(), answer.getTargetCode(), "PRI_LOAD_TYPE",
				loadType.getName());
		newAnswer.setInferred(true);

		saveAnswer(newAnswer);
	}

	public void sendRating(String data) throws ClientProtocolException, IOException {

		if (data != null) {

			JsonObject dataJson = new JsonObject(data);
			String begCode = dataJson.getString("itemCode");
			String userCode = getUser().getCode();
			String driverCode = null;

			/* we get all the linked BEs to beg */
			List<Link> links = getLinks(begCode, "LNK_BEG");
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
				updateBaseEntityAttribute(userCode, userCode, "STT_JOB_IS_RATING", begCode);

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

		BaseEntity updatedJob = this.getBaseEntityByCode(job.getCode());
		Long jobId = updatedJob.getId();
		answers.add(new Answer(getUser().getCode(), jobCode, "PRI_JOB_ID", jobId + ""));
		saveAnswers(answers);

		/* Determine the recipient code */
		String[] recipientCodes = VertxUtils.getSubscribers(realm(), "GRP_NEW_ITEMS");
		println("Recipients for Job/Load " + Arrays.toString(recipientCodes));

		/*
		 * Send newly created job with its attributes to all drivers so that it exists
		 * before link change
		 */
		BaseEntity newJobDetails = getBaseEntityByCode(jobCode);
		println("The newly submitted Job details     ::     " + newJobDetails.toString());
		publishData(newJobDetails, recipientCodes);
		/* publishing to Owner */
		publishBE(newJobDetails);

		/* Moving the BEG to GRP_NEW_ITEMS */
		/*
		 * The moveBaseEntity without linkValue sets the linkValue to default value,
		 * "LINK". So using moveBaseEntitySetLinkValue()
		 */
		moveBaseEntitySetLinkValue(jobCode, "GRP_DRAFTS", "GRP_NEW_ITEMS", "LNK_CORE", "BEG");

		/* Get the sourceCode(Company code) for this User */
		BaseEntity company = getParent(getUser().getCode(), "LNK_STAFF");

		/* link newly created Job to GRP_LOADS */
		BaseEntity load = getChildren(jobCode, "LNK_BEG", "LOAD");
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
		publishBE(getBaseEntityByCode(jobCode));
		publishBE(getBaseEntityByCode(loadCode));

		if (!newJobDetails.getValue("PRI_JOB_IS_SUBMITTED", false)) {

			/* Sending Messages */

			println("new job");

			HashMap<String, String> contextMap = new HashMap<String, String>();
			contextMap.put("JOB", jobCode);
			contextMap.put("OWNER", getUser().getCode());

			println("The String Array is ::" + Arrays.toString(recipientCodes));

			/* Getting all people */
			List<BaseEntity> people = getBaseEntitysByParentAndLinkCode("GRP_PEOPLE", "LNK_CORE", 0, 100, false);
			System.out.println("size ::" + people.size());
			List<BaseEntity> driversBe = new ArrayList<>();

			/* Getting all driver BEs */
			for (BaseEntity stakeholderBe : people) {
				Boolean isDriver = stakeholderBe.getValue("PRI_DRIVER", false);
				if (isDriver) {
					driversBe.add(stakeholderBe);
				}
			}

			int i = 0;
			String[] stakeholderArr = new String[driversBe.size()];
			for (BaseEntity stakeholderBe : driversBe) {
				stakeholderArr[i] = stakeholderBe.getCode();
				i++;
			}

			println("recipient array - drivers ::" + Arrays.toString(stakeholderArr));

			/* Sending toast message to owner frontend */
			sendMessage("", stakeholderArr, contextMap, "MSG_CH40_NEW_JOB_POSTED", "TOAST");

			/* Sending message to BEG OWNER */
			sendMessage("", stakeholderArr, contextMap, "MSG_CH40_NEW_JOB_POSTED", "EMAIL");

		}

	}

	public void listenAttributeChange(QEventAttributeValueChangeMessage m) {
		// if ((m.getData() != null)&&(m.getData().getCode()!=null)) {
		// println(m.getData().getCode());
		// }
		if ((m.getData() != null) && ("MULTI_EVENT".equals(m.getData().getCode()))) {
			/* rules.publishData(new QDataAnswerMessage($m.getAnswer())); */
			String[] recipientCodes = getRecipientCodes(m);
			println(m);
			addAttributes(m.getBe());
			publishBE(m.getBe(), recipientCodes);
			setState("ATTRIBUTE_CHANGE2");
			fireAttributeChanges(m);
		} else if ((m.getData() != null) && (m.getData().getCode() != null)) {
			/* publishData(new QDataAnswerMessage(m.getAnswer())); */
			String[] recipientCodes = getRecipientCodes(m);
			println(m);
			addAttributes(m.getBe());
			publishBE(m.getBe(), recipientCodes);
			setState("ATTRIBUTE_CHANGE2");
		}
	}

	public List getLinkList(String groupCode, String linkCode, String linkValue, String token) {

		// String qwandaServiceUrl = "http://localhost:8280";
		String qwandaServiceUrl = System.getenv("REACT_APP_QWANDA_API_URL");
		List linkList = null;

		try {
			String attributeString = QwandaUtils.apiGet(qwandaServiceUrl + "/qwanda/entityentitys/" + groupCode
					+ "/linkcodes/" + linkCode + "/children/" + linkValue, token);
			if (attributeString != null) {
				linkList = JsonUtils.fromJson(attributeString, List.class);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return linkList;

	}

	public BaseEntity getOfferBaseEntity(String groupCode, String linkCode, String linkValue, String quoterCode,
			String token) {

		List linkList = getLinkList(groupCode, linkCode, linkValue, token);
		String quoterCodeForOffer = null;
		BaseEntity offer = null;

		if (linkList != null) {

			for (Object linkObj : linkList) {
				Link link = JsonUtils.fromJson(linkObj.toString(), Link.class);

				BaseEntity offerBe = getBaseEntityByCode(link.getTargetCode());

				if (offerBe != null) {
					quoterCodeForOffer = offerBe.getValue("PRI_QUOTER_CODE", null);

					if (quoterCode.equals(quoterCodeForOffer)) {
						offer = offerBe;
						return offerBe;
					}
				}

			}

		}

		return offer;
	}

	public boolean hasRole(final String role) {
		LinkedHashMap rolesMap = (LinkedHashMap) getDecodedTokenMap().get("realm_access");
		ArrayList roles = (ArrayList) rolesMap.get("roles");
		if (roles.contains(role)) {
			return true;
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
				.addColumn("PRI_CREATOR", "Creater ID")

				.setSourceCode(chatCode).setSourceStakeholder(getUser().getCode())

				.addSort("PRI_CREATED", "Created", SearchEntity.Sort.DESC)
				.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "MSG_%")

				.setPageStart(pageStart).setPageSize(pageSize);

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
		BaseEntity searchBE = new BaseEntity(searchBeCode, "Get All Users"); // createBaseEntityByCode2(searchBeCode,
																				// "Get All Users");
		JsonArray columnsArray = new JsonArray();
		JsonObject columns = new JsonObject();
		// if( getBaseEntityByCode(searchBeCode) == null ) {
		// searchBE = createBaseEntityByCode2(searchBeCode, "Get All Users");
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
		BaseEntity searchBE = new BaseEntity(searchBeCode, "Get All Drivers"); // createBaseEntityByCode2(searchBeCode,
																				// "Get All Users");

		JsonArray columnsArray = new JsonArray();
		JsonObject columns = new JsonObject();
		// if( getBaseEntityByCode(searchBeCode) == null ) {
		// searchBE = createBaseEntityByCode2(searchBeCode, "Get All Users");
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
		AttributeBoolean attributeIsDriver = new AttributeBoolean("PRI_DRIVER", "=");
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
		println("Get Layout:- The Session Id is ::" + sessionId);
		String[] previousLayout = VertxUtils.getStringArray(realm(), "PreviousLayout", sessionId);
		println("The layout is :: " + previousLayout[0] + " and " + previousLayout[1]);
		return previousLayout;
	}

	/* Sorting Offers of a beg as per the price, lowest price being on top */
	public void sortOffersInBeg(final String begCode) {
		List<BaseEntity> offers = getAllChildrens(begCode, "LNK_BEG", "OFFER");
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
				updateLink(begCode, be.getCode(), "LNK_BEG", "OFFER", linkWeight);
				linkWeight++;
			}
		}

	}

	public void triggerEmailForJobUpdate(String jobCode) {

		println("Job is already submitted, so the job is getting edited");

		List<Link> links = getLinks(jobCode, "LNK_BEG");
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
				BaseEntity offerBe = getBaseEntityByCode(offer);
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

	/*
	 * Redirecting to the Home/Landing Page based on the user role:OWNER or DRIVER
	 */
	/* TODO: refactor this. */
	public void redirectToHomePage() {
		// if (getUser().is("PRI_OWNER")) { //removing user role check as all the users
		// needs to see BucketView
		sendSublayout("BUCKET_DASHBOARD", "dashboard_channel40.json", "GRP_DASHBOARD");
		setLastLayout("BUCKET_DASHBOARD", "GRP_DASHBOARD");
		// } else if (getUser().is("PRI_DRIVER")) {
		// sendViewCmd("LIST_VIEW", "GRP_NEW_ITEMS");
		// setLastLayout("LIST_VIEW", "GRP_NEW_ITEMS");
		// }
	}

	public void add(final String keyPrefix, final String parentCode, final BaseEntity be) {
		// Add this be to the static
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

	public JsonObject generateLayout(final String reportGroupCode) {
		Map<String, String> map = getMap("GRP", reportGroupCode);
		println(map);

		Integer cols = 1;
		Integer rows = map.size();

		JsonObject layout = new JsonObject();
		JsonObject grid = new JsonObject();
		grid.put("cols", cols);
		grid.put("rows", rows);

		layout.put("Grid", grid);

		JsonArray children = new JsonArray();
		grid.put("children", children);
		for (String key : map.keySet()) {
			BaseEntity searchBe = JsonUtils.fromJson(map.get(key), BaseEntity.class);
			JsonObject button = generateGennyButton(key, searchBe.getName());
			children.add(button);
		}
		return layout;
	}

	public JsonObject generateGennyButton(final String code, final String name) {
		JsonObject gennyButton = new JsonObject();

		JsonObject ret = new JsonObject();
		JsonArray position = new JsonArray();
		position.add(0);
		position.add(0);
		ret.put("position", position);
		ret.put("buttonCode", code);
		ret.put("children", name);
		ret.put("value", new JsonObject());

		JsonObject buttonStyle = new JsonObject();
		buttonStyle.put("background", "PROJECT.PRI_COLOR");
		ret.put("buttonStyle", buttonStyle);

		JsonObject style = new JsonObject();
		style.put("width", "150px");
		style.put("height", "50px");
		style.put("margin", "10px");
		style.put("border", "1px solid");
		style.put("borderColor", "PROJECT.PRI_COLOR");
		ret.put("style", style);

		gennyButton.put("GennyButton", ret);

		return gennyButton;
	}

	/*
	 * Send Report based on the SearchBE
	 */
	public void sendReport(String reportCode) throws IOException {

		System.out.println("The report code is :: " + reportCode);
		// BaseEntity searchBE = getBaseEntityByCode(reportCode);
		String jsonSearchBE = null;

		if (reportCode.equalsIgnoreCase("SBE_OWNERJOBS") || reportCode.equalsIgnoreCase("SBE_DRIVERJOBS")) {
			// srchBE.setStakeholder(getUser().getCode());
			SearchEntity srchBE = new SearchEntity(reportCode, "List of all My Loads")
					.addColumn("PRI_NAME", "Load Name").addColumn("PRI_JOB_ID", "Job ID")
					.addColumn("PRI_PICKUP_ADDRESS_FULL", "Pickup Address").addColumn("PRI_DESCRIPTION", "Description")

					.setStakeholder(getUser().getCode())

					.addSort("PRI_NAME", "Name", SearchEntity.Sort.ASC)

					.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "BEG_%")

					.setPageStart(0).setPageSize(10000);

			jsonSearchBE = JsonUtils.toJson(srchBE);

		} else {
			BaseEntity searchBE = getBaseEntityByCode(reportCode);
			jsonSearchBE = JsonUtils.toJson(searchBE);
		}

		System.out.println("The search BE is :: " + jsonSearchBE);
		// String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());

		QDataBaseEntityMessage msg = JsonUtils.fromJson(resultJson, QDataBaseEntityMessage.class);
		// System.out.println("The result :: " + JsonUtils.toJson(msg));
		publishData(new JsonObject(resultJson));

		// JsonArray columnHeaders = new JsonArray();
		// List<EntityAttribute> columnAttributes = new ArrayList<EntityAttribute>();
		//
		// for (EntityAttribute ea : searchBE.getBaseEntityAttributes()) {
		// if (ea.getAttributeCode().startsWith("COL_")) {
		// columnAttributes.add(ea);
		// }
		// }
		//
		// columnAttributes.sort(Comparator.comparing(EntityAttribute::getWeight));
		// for (EntityAttribute ea : columnAttributes) {
		// columnHeaders.add(ea.getAttributeCode().substring("COL_".length()));
		// }

		// sendTableViewWithHeaders(reportCode, columnHeaders);

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
	 * Get search Results returns QDataBaseEntityMessage
	 */
	public QDataBaseEntityMessage getSearchResults(SearchEntity searchBE) throws IOException {
		System.out.println("The search BE is :: " + JsonUtils.toJson(searchBE));
		String jsonSearchBE = JsonUtils.toJson(searchBE);
		String resultJson = QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/search", jsonSearchBE,
				getToken());
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
		List<BaseEntity> chats = getBaseEntitysByParentAndLinkCode("GRP_MESSAGES", "LNK_CHAT", 0, 500, true);

		if (chats != null) {

			for (BaseEntity chat : chats) {

				List<BaseEntity> users = getBaseEntitysByParentAndLinkCode(chat.getCode(), "LNK_USER", 0, 500, true);
				if (users != null) {
					if (users.contains(getBaseEntityByCode(sender)) && users.contains(getBaseEntityByCode(receiver))) {
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
		List<BaseEntity> chats = getBaseEntitysByParentAndLinkCode("GRP_MESSAGES", "LNK_CHAT", 0, 100, true);
		if (chats != null) {

			for (BaseEntity chat : chats) {

				List<BaseEntity> users = getBaseEntitysByParentAndLinkCode(chat.getCode(), "LNK_USER", 0, 500, true);
				if (users != null) {
					if (users.contains(getBaseEntityByCode(sender)) && users.contains(getBaseEntityByCode(receiver))) {
						return chat;
					}
				}
			}
		}
		return null;
	}

	public void processRoles(QDataAnswerMessage m) {
		Answer[] answers = m.getItems();

		if (answers != null && answers.length > 0) {

			for (Answer answer : answers) {
				String attributeCode = answer.getAttributeCode();

				if (attributeCode.equals("LNK_USER_ROLE_LISTS")) {
					List<Answer> answersToSave = new ArrayList<Answer>();
					// So save the SEL_OWNER as PRI_OWNER
					if ("SEL_OWNER".equals(answer.getValue())) {
						answersToSave.add(new Answer(getUser().getCode(), getUser().getCode(), "PRI_OWNER", "TRUE"));
						answersToSave.add(new Answer(getUser().getCode(), getUser().getCode(), "PRI_DRIVER", "FALSE"));
						println("OWNER SET!");
					} else {
						if ("SEL_DRIVER".equals(answer.getValue())) {
							answersToSave
									.add(new Answer(getUser().getCode(), getUser().getCode(), "PRI_DRIVER", "TRUE"));
							answersToSave
									.add(new Answer(getUser().getCode(), getUser().getCode(), "PRI_OWNER", "FALSE"));
							println("DRIVER SET!");
						}
					}
					saveAnswers(answersToSave);
					// force the roles to go out to the user
					BaseEntity user = getBaseEntityByCode(getUser().getCode(), true);
					publishBE(user);
					break;
				}
			}

		}

	}

	/*
	 * Publish Search BE TODO: Refactor
	 */
	public void publishSearhBE(final String reportGroupCode) {
		BaseEntity user = getUser();
		List<BaseEntity> results = new ArrayList<BaseEntity>();
		String dataMsgParentCode = reportGroupCode;
		if (reportGroupCode.equalsIgnoreCase("GRP_REPORTS")) {
			if (user.is("PRI_DRIVER")) {
				dataMsgParentCode = "GRP_REPORTS_DRIVER";
				Map<String, String> map = getMap("GRP", "GRP_REPORTS_DRIVER");
				for (Map.Entry<String, String> entry : map.entrySet()) {
					String key = entry.getKey();
					BaseEntity searchBe = JsonUtils.fromJson(entry.getValue(), BaseEntity.class);
					System.out.println("The Search BE is :: " + searchBe);
					results.add(searchBe);
				}
			} else if (user.is("PRI_OWNER")) {
				dataMsgParentCode = "GRP_REPORTS_OWNER";
				Map<String, String> map = getMap("GRP", "GRP_REPORTS_OWNER");
				for (Map.Entry<String, String> entry : map.entrySet()) {
					String key = entry.getKey();
					BaseEntity searchBe = JsonUtils.fromJson(entry.getValue(), BaseEntity.class);
					System.out.println("The Search BE is :: " + searchBe);
					results.add(searchBe);
				}
			}
			// Checking Admin - TODO: In future when role is seperated then this need toi be
			// modified
			if (hasRole("admin")) {
				dataMsgParentCode = "GRP_REPORTS_ADMIN";
				Map<String, String> map = getMap("GRP", "GRP_REPORTS_ADMIN");
				for (Map.Entry<String, String> entry : map.entrySet()) {
					String key = entry.getKey();
					BaseEntity searchBe = JsonUtils.fromJson(entry.getValue(), BaseEntity.class);
					System.out.println("The Search BE is :: " + searchBe);
					results.add(searchBe);
				}
			}
		} else {
			Map<String, String> map = getMap("GRP", reportGroupCode);
			// List<BaseEntity> results = new ArrayList<BaseEntity>();
			dataMsgParentCode = reportGroupCode;
			for (Map.Entry<String, String> entry : map.entrySet()) {
				String key = entry.getKey();
				BaseEntity searchBe = JsonUtils.fromJson(entry.getValue(), BaseEntity.class);
				System.out.println("The Search BE is :: " + searchBe);
				results.add(searchBe);
			}
		}
		BaseEntity[] beArr = new BaseEntity[results.size()];
		beArr = results.toArray(beArr);

		// if ( hasRole("admin") ) { //TODO: Refactor - Only for Tuesday 24th April's
		// demonstration to Josh
		// dataMsgParentCode = "GRP_REPORTS_ADMIN";
		// }
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beArr, dataMsgParentCode, null);
		msg.setToken(getToken());
		System.out.println("The QDataBaseEntityMessage for " + reportGroupCode + " is :: " + JsonUtils.toJson(msg));
		String msgJson = JsonUtils.toJson(msg);
		System.out.println("The Json value of data msg is :: " + msgJson);
		publish("cmds", msgJson);

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
			BaseEntity searchBE = getBaseEntityByCode(searchBECode);
			// List<String> columnsAttribute = new ArrayList<String>();
			List<EntityAttribute> eaList = new ArrayList<EntityAttribute>();

			for (EntityAttribute ea : searchBE.getBaseEntityAttributes()) {
				if (ea.getAttributeCode().startsWith("COL_")) {
					// String attributeCode = ea.getAttributeCode();
					// Double weight = ea.getWeight();
					// String attributeName = ea.getAttributeName();
					// String header = StringUtils.remove(attributeCode, "COL_");
					// columnsAttribute.add(header);
					eaList.add(ea);
				}
			}
			List<String> sortedColumns = sortEntityAttributeBasedOnWeight(eaList, "ASC");
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

	/*
	 * Sorting Columns of a SearchEntity as per the weight in either Ascening or
	 * descending order
	 */
	public List<String> sortEntityAttributeBasedOnWeight(final List<EntityAttribute> ea, final String sortOrder) {

		if (ea.size() > 1) {
			Collections.sort(ea, new Comparator<EntityAttribute>() {
				@Override
				public int compare(EntityAttribute ea1, EntityAttribute ea2) {
					// println("The weight value of " + ea1.getAttributeCode() + " is "
					// + ea1.getWeight());
					// println("The weight value of " + ea2.getAttributeCode() + " is "
					// + ea2.getWeight());
					if (ea1.getWeight() != null && ea2.getWeight() != null) {
						if (sortOrder.equalsIgnoreCase("ASC"))
							return (ea1.getWeight()).compareTo(ea2.getWeight());
						else
							return (ea2.getWeight()).compareTo(ea1.getWeight());

					} else
						return 0;
				}
			});
		}
		List<String> searchHeader = new ArrayList<String>();
		for (EntityAttribute ea1 : ea) {
			// String code = ea1.getAttributeCode();
			// String name = ea1.getAttributeName();
			// Double weight = ea1.getWeight();
			searchHeader.add(ea1.getAttributeCode().substring("COL_".length()));
		}

		return searchHeader;
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
				BaseEntity driverCompanyBe = getParent(driverBe.getCode(), "LNK_STAFF");

				if (driverCompanyBe != null) {
					contextMap.put("DRIVER_COMPANY", driverCompanyBe.getCode());
				}
			}

			if (ownerBe != null) {
				BaseEntity ownerCompanyBe = getParent(ownerBe.getCode(), "LNK_STAFF");

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
						"application/pdf", driverInvoiceLayoutUrl, true, "INVOICE_PDF");
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

				String webhookURL = project.getLoopValue("PRI_SLACK_SIGNUP_WEBHOOK", null);
				if (webhookURL != null) {

					String message = "Tax invoice generation failed, offer is null, BEG :" + begBe.getCode()
							+ ", LOAD :" + loadBe;
					JsonObject payload = new JsonObject();
					payload.put("text", message);

					try {
						sendSlackNotification(webhookURL, payload);
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

	public void updateBaseEntityStatus(BaseEntity be, String userCode, String status) {
		this.updateBaseEntityStatus(be.getCode(), userCode, status);
	}

	public void updateBaseEntityStatus(String beCode, String userCode, String status) {

		String attributeCode = "STA_" + userCode;
		this.updateBaseEntityAttribute(userCode, beCode, attributeCode, status);
	}

	public void updateBaseEntityStatus(BaseEntity be, List<String> userCodes, String status) {
		this.updateBaseEntityStatus(be.getCode(), userCodes, status);
	}

	public void updateBaseEntityStatus(String beCode, List<String> userCodes, String status) {

		for (String userCode : userCodes) {
			this.updateBaseEntityStatus(beCode, userCode, status);
		}
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

			if (realm().equals(realm)) {

				// fetch token from keycloak
				String key = null;
				String initVector = "PRJ_" + realm().toUpperCase();
				initVector = StringUtils.rightPad(initVector, 16, '*');
				String encryptedPassword = null;

				try {
					key = System.getenv("ENV_SECURITY_KEY"); // TODO , Add each realm as a prefix
				} catch (Exception e) {
					log.error("PRJ_" + realm().toUpperCase() + " ENV ENV_SECURITY_KEY  is missing!");
				}

				try {
					encryptedPassword = System.getenv("ENV_SERVICE_PASSWORD");
				} catch (Exception e) {
					log.error("PRJ_" + realm().toUpperCase() + " attribute ENV_SECURITY_KEY  is missing!");
				}

				String password = SecurityUtils.decrypt(key, initVector, encryptedPassword);

				// Now ask the bridge for the keycloak to use
				String keycloakurl = realmJson.getString("auth-server-url").substring(0,
						realmJson.getString("auth-server-url").length() - ("/auth".length()));

				try {
					AccessTokenResponse accessToken = KeycloakUtils.getAccessToken(keycloakurl, realm(), realm(),
							secret, "service", password);
					String token = accessToken.getToken();

					Map<String, Object> serviceDecodedTokenMap = KeycloakUtils.getJsonMap(token);

					this.setDecodedTokenMap(serviceDecodedTokenMap);
					this.setToken(token);
					String dev = System.getenv("GENNYDEV");
					String proj_realm = System.getenv("PROJECT_REALM");
					if ((dev != null) && ("TRUE".equalsIgnoreCase(dev))) {
						this.set("realm", proj_realm);
					} else {
						this.set("realm", realm);
					}

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			}
		}

		return false;
	}

	public void generateTree() {
		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();

		List<BaseEntity> root = getBaseEntitysByParentAndLinkCode("GRP_ROOT", "LNK_CORE", 0, 50, false);
		bulkmsg.add(new QDataBaseEntityMessage(root.toArray(new BaseEntity[0]), "GRP_ROOT", "LNK_CORE"));
		// println(root);

		List<BaseEntity> reportsHeader = getBaseEntitysByParentAndLinkCode("GRP_REPORTS", "LNK_CORE", 0, 50, false);
		bulkmsg.add(new QDataBaseEntityMessage(reportsHeader.toArray(new BaseEntity[0]), "GRP_REPORTS", "LNK_CORE"));

		List<BaseEntity> admin = getBaseEntitysByParentAndLinkCode("GRP_ADMIN", "LNK_CORE", 0, 20, false);
		bulkmsg.add(new QDataBaseEntityMessage(admin.toArray(new BaseEntity[0]), "GRP_ADMIN", "LNK_CORE"));

		// Now get the buckets
		List<BaseEntity> buckets = getBaseEntitysByParentAndLinkCode("GRP_DASHBOARD", "LNK_CORE", 0, 20, false);
		// Save the buckets for future use
		QDataBaseEntityMessage bucketMsg = new QDataBaseEntityMessage(buckets.toArray(new BaseEntity[0]),"GRP_DASHBOARD","LNK_CORE");
		VertxUtils.putObject(realm(), "BUCKETS", realm(), bucketMsg);
		
		
		bulkmsg.add(new QDataBaseEntityMessage(buckets.toArray(new BaseEntity[0]), "GRP_DASHBOARD", "LNK_CORE"));

		QBulkMessage bulk = new QBulkMessage(bulkmsg);

		VertxUtils.putObject(realm(), "BASE_TREE", realm(), bulk);
	}

	public void sendTreeData() {
		
		println("treedata realm is " + realm());
		QBulkMessage bulk = VertxUtils.getObject(realm(), "BASE_TREE", realm(), QBulkMessage.class);
		
		if(bulk != null) {
			
			for (QDataBaseEntityMessage msg : bulk.getMessages()) {
				if (msg instanceof QDataBaseEntityMessage) {
					msg.setToken(getToken());
					publishCmd(JsonUtils.toJson(msg));
				}
			}
		}
	}

	public void generateNewItemsCache() {
		println("GENERATING NEW ITEMS  Cache realm is " + realm());

		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();
		QDataBaseEntityMessage results = null;

		/* Searches */
		BaseEntity searchNewItems = getBaseEntityByCode("SBE_NEW_ITEMS");

		try {
			results = QwandaUtils.fetchResults(searchNewItems, getToken());
			if (results != null) {
				results.setParentCode("GRP_NEW_ITEMS");
				results.setLinkCode("LNK_CORE");
				bulkmsg.add(results);

				QBulkMessage bulk = new QBulkMessage(bulkmsg);
			}

			for (BaseEntity beg : results.getItems()) {
				List<BaseEntity> begKids = getBaseEntitysByParentAndLinkCode(beg.getCode(), "LNK_BEG", 0, 100, false);
				bulkmsg.add(new QDataBaseEntityMessage(begKids.toArray(new BaseEntity[0]), beg.getCode(), "LNK_BEG"));
			}

			QBulkMessage bulk = new QBulkMessage(bulkmsg);
			VertxUtils.putObject(realm(), "SEARCH", "SBE_NEW_ITEMS", bulk);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public QBulkMessage fetchStakeholderBucketItems(final BaseEntity stakeholder) {
		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();


		QDataBaseEntityMessage bucketsMsg = VertxUtils.getObject(realm(), "BUCKETS", realm(), QDataBaseEntityMessage.class);
		
		
		BaseEntity searchStakeholderBucketItems = getBaseEntityByCode("SBE_STAKEHOLDER_ITEMS");
		SearchEntity search = new SearchEntity(searchStakeholderBucketItems);
		if (!stakeholder.is("PRI_IS_ADMIN")) {
			search.setStakeholder(stakeholder.getCode());
		}
		try {
			QDataBaseEntityMessage results = QwandaUtils.fetchResults(search, getToken());
			bulkmsg.add(results);
			
			// Create bucket Buckets!
		 Map<String,List<BaseEntity>> bucketListMap = new HashMap<String,List<BaseEntity>>();
		 for (BaseEntity bucket : bucketsMsg.getItems()) {
			 bucketListMap.put(bucket.getCode(), new ArrayList<BaseEntity>());
		 }
		 
		 
			//Now place each beg into the proper buckets
			for (BaseEntity beg : results.getItems()) {
				// Now search through the BEG links and match to bucket, and their laods, main offer, owner
				 List<BaseEntity> begKids = new ArrayList<BaseEntity>();

				for (EntityEntity link : beg.getLinks()) {
					BaseEntity linkedBE = getBaseEntityByCode(link.getLink().getTargetCode());
					begKids.add(linkedBE);
				}
				bulkmsg.add(new QDataBaseEntityMessage(begKids.toArray(new BaseEntity[0]), beg.getCode(), "LNK_BEG"));
		
				
			}
	
			for (BaseEntity bucket : bucketsMsg.getItems()) {
				if (!bucketListMap.get(bucket.getCode()).isEmpty()) {
				bulkmsg.add(new QDataBaseEntityMessage(bucketListMap.get(bucket.getCode()).toArray(new BaseEntity[0]), bucket.getCode(), "LNK_CORE"));
				}
				}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		QBulkMessage bulk = new QBulkMessage(bulkmsg);
		return bulk;
	}

}
