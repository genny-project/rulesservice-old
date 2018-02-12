package life.genny.rules;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.money.CurrencyUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.logging.log4j.Logger;
import org.drools.core.spi.KnowledgeHelper;
import org.javamoney.moneta.Money;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.qwanda.Answer;
import life.genny.qwanda.Layout;
import life.genny.qwanda.Link;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.attribute.EntityAttribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.message.QCmdGeofenceMessage;
import life.genny.qwanda.message.QCmdLayoutMessage;
import life.genny.qwanda.message.QCmdMessage;
import life.genny.qwanda.message.QCmdViewMessage;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataAskMessage;
import life.genny.qwanda.message.QDataAttributeMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwanda.message.QDataMessage;
import life.genny.qwanda.message.QDataQSTMessage;
import life.genny.qwanda.message.QDataSubLayoutMessage;
import life.genny.qwanda.message.QEventAttributeValueChangeMessage;
import life.genny.qwanda.message.QEventLinkChangeMessage;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwanda.message.QMSGMessage;
import life.genny.qwanda.message.QMessage;
import life.genny.qwandautils.GPSUtils;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.MessageUtils;
import life.genny.qwandautils.QwandaUtils;
import life.genny.utils.MoneyHelper;
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
		//println("STATE " + key + " SET", RulesUtils.ANSI_RED);
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

	public BaseEntity getUser() {
		BaseEntity be = null;
	//	if (isNull("USER")) {
			String username = (String) getDecodedTokenMap().get("preferred_username");
			String code = "PER_"+QwandaUtils.getNormalisedUsername(username).toUpperCase();
			be = getBaseEntityByCode(code);
			//be = getBaseEntityByAttributeAndValue("PRI_USERNAME",username);
		    
			if (be != null) {
				set("USER", be); // WATCH THIS!!!
			}
	//	} else {
	//		be = getAsBaseEntity("USER");
	//	}

		return be;
	}

	public Boolean isUserPresent() {
		if (isNull("USER")) {
			return false;
		} else {
			return true;
		}
	}
	
	public Boolean isNewUserProfileCompleted() {
		Boolean status = false;
		if(getUser() != null) {
			 status =  QwandaUtils.isMandatoryFieldsEntered(getUser().getCode(), getUser().getCode(), "QUE_NEW_USER_PROFILE_GRP", getToken());
		}
		   	
		return status;
	}

	public void updateBaseEntityByCode(final String code) {
		BaseEntity be = null;
		be = RulesUtils.getBaseEntityByCode(qwandaServiceUrl, getDecodedTokenMap(), getToken(), code);

		if (System.getenv("API_PORT") != null) {
			try {
				QwandaUtils.apiPutCodedEntity(be, getToken());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {

			set("BE_" + code.toUpperCase(), be); // WATCH THIS!!!
		}

	}
	
	public void updateBaseEntityAttribute(final String sourceCode, final String beCode, final String attributeCode, final String newValue) {
		
		Answer newAnswer = new Answer(sourceCode, beCode, attributeCode, newValue);
		saveAnswer(newAnswer);
	}

	public BaseEntity getBaseEntityByCode(final String code) {
		BaseEntity be = null;
		if (System.getenv("API_PORT") != null) {
			try {
				be = QwandaUtils.apiGetCodedEntity(code, BaseEntity.class, getToken());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // remember to update upon be changes
			if (be == null) {
				be = RulesUtils.getBaseEntityByCode(qwandaServiceUrl, getDecodedTokenMap(), getToken(), code);
				try {
					QwandaUtils.apiPutCodedEntity(be, getToken());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} else {

	//		if (isNull("BE_" + code.toUpperCase())) {
			be = VertxUtils.readFromDDT(code, getToken());
			//	be = RulesUtils.getBaseEntityByCode(qwandaServiceUrl, getDecodedTokenMap(), getToken(), code);
		//		set("BE_" + code.toUpperCase(), be); // WATCH THIS!!!
	//		} 
		//else {
		//		be = getAsBaseEntity("BE_" + code.toUpperCase());
		//	}
		}
		return be;
	}

	public BaseEntity getBaseEntityByAttributeAndValue(final String attributeCode, final String value) {

		BaseEntity be = null;
			be = RulesUtils.getBaseEntityByAttributeAndValue(qwandaServiceUrl, getDecodedTokenMap(), getToken(),
					attributeCode, value);
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

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize, Boolean cache) {

		List<BaseEntity> bes = null;

	//	if (isNull("BES_" + parentCode.toUpperCase() + "_" + linkCode)) {

			bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes(qwandaServiceUrl, getDecodedTokenMap(),
					getToken(), parentCode, linkCode);

			if (cache) {
				set("BES_" + parentCode.toUpperCase() + "_" + linkCode, bes); // WATCH THIS!!!
			}

	//	} else {
	//		bes = getAsBaseEntitys("BES_" + parentCode.toUpperCase() + "_" + linkCode);
	//	}

		return bes;
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize, Boolean cache, final String stakeholderCode) {
		List<BaseEntity> bes = null;
		if (getUser().is("PRI_DRIVER")) {
			RulesUtils.println("Is True");
		}
	//	if (isNull("BES_" + parentCode.toUpperCase() + "_" + linkCode)) {
			bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributesAndStakeholderCode(qwandaServiceUrl,
					getDecodedTokenMap(), getToken(), parentCode, linkCode, stakeholderCode);
			if (cache) {
				set("BES_" + parentCode.toUpperCase() + "_" + linkCode, bes); // WATCH THIS!!!
			}
	//	} else {
	//		bes = getAsBaseEntitys("BES_" + parentCode.toUpperCase() + "_" + linkCode);
	//	}
		return bes;
	}

	public String moveBaseEntity(final String baseEntityCode, final String sourceCode, final String targetCode,
			final String linkCode) {

		JsonObject begEntity = new JsonObject();
		begEntity.put("sourceCode", sourceCode);
		begEntity.put("targetCode", baseEntityCode);
		begEntity.put("attributeCode", linkCode);

		try {

			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/move/" + targetCode, begEntity.toString(),
					getToken());

			JsonArray updatedLink = new JsonArray(QwandaUtils.apiGet(
					qwandaServiceUrl + "/qwanda/entityentitys/" + baseEntityCode + "/linkcodes/" + linkCode,
					getToken()));

			// Creating a data msg
			// JsonObject newLink = new JsonObject();
			// newLink.put("msg_type", "DATA_MSG");
			// newLink.put("data_type", "LINK_CHANGE");
			// newLink.put("items", updatedLink);
			// newLink.put("token", getToken() );
			// println("-----------------------------------");
			// println("Updated Link : "+newLink.toString());
			// println("-----------------------------------");
			// getEventBus().publish("cmds", newLink);

		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public void publishBaseEntityByCode(final String be) {
		String[] recipientArray = new String[1];
		recipientArray[0] = be;
		publishBaseEntityByCode(be, null,
			     null, recipientArray);
	}
	
	public void publishBaseEntityByCode(final String be, final String parentCode,
		      final String linkCode, final String[] recipientCodes) {

		BaseEntity item = getBaseEntityByCode(be);
		BaseEntity[]  itemArray = new BaseEntity[1];
		itemArray[0] = item;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, parentCode,
			      linkCode);
		msg.setRecipientCodeArray(recipientCodes);
			publishCmd(msg, recipientCodes);
	
	}

	public <T extends QMessage>  void publishCmd(T msg, final String[] recipientCodes) {

//		String json = JsonUtils.toJson(msg);
//		JsonObject obj = JsonUtils.fromJson(json, JsonObject.class);
//		obj.put("token", getToken());
		msg.setToken(getToken());
		publish("cmds", JsonUtils.toJson(msg));
	}
	
	public <T extends QMessage>  void publishData(T msg, final String[] recipientCodes) {

//		String json = JsonUtils.toJson(msg);
//		JsonObject obj = JsonUtils.fromJson(json, JsonObject.class);
//		obj.put("token", getToken());
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
	}
	public <T extends QMessage>  void publish(final String busChannel,T msg, final String[] recipientCodes) {

//		String json = JsonUtils.toJson(msg);
//		JsonObject obj = JsonUtils.fromJson(json, JsonObject.class);
//		obj.put("token", getToken());
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
				getDecodedTokenMap(), getToken(), parentCode, linkCode);
		
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beArray,parentCode, linkCode);
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
			if (ea.getAttributeCode().equals(attributeCode)) {
				attributeVal = ea.getObjectAsString();
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

	public void sendMessage(String begCode, String[] recipientArray, HashMap<String, String> contextMap,
			String templateCode, String messageType) {

		JsonObject message = MessageUtils.prepareMessageTemplate(templateCode, messageType, contextMap, recipientArray,
				getToken());
		this.getEventBus().publish("messages", message);
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
			be = getUser();
			println("New User Created " + be);
		} catch (IOException e) {
			log.error("Error in Creating User ");
		}
		return be;
	}
	
	public void sendLayout(final String layoutCode, final String layoutPath) {

		String layout = RulesUtils.getLayout(layoutPath);
		QCmdMessage layoutCmd = new QCmdLayoutMessage(layoutCode, layout);
		publishCmd(layoutCmd);
		RulesUtils.println(layoutCode + " SENT TO FRONTEND");
	}
	
	public void sendPopupCmd(final String cmd_view, final String root) {
		
		QCmdMessage cmdJobSublayout = new QCmdMessage("CMD_POPUP", cmd_view);
		JsonObject cmdJobSublayoutJson = JsonObject.mapFrom(cmdJobSublayout);
		cmdJobSublayoutJson.put("token", getToken());
		if (root != null) {
			cmdJobSublayoutJson.put("root", root);
		}
		
		this.getEventBus().publish("cmds", cmdJobSublayoutJson);
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
		
		this.getEventBus().publish("cmds", cmdJobSublayoutJson);
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

	public void sendSublayout(final String layoutCode, final String sublayoutPath, final String root, final boolean isPopup) {
		
		String cmd_view = isPopup ? "CMD_POPUP" : "CMD_SUBLAYOUT";
		QCmdMessage cmdJobSublayout = new QCmdMessage(cmd_view, layoutCode);
		JsonObject cmdJobSublayoutJson = JsonObject.mapFrom(cmdJobSublayout);
		String sublayoutString = RulesUtils.getLayout(sublayoutPath);
		cmdJobSublayoutJson.put("items", sublayoutString);
		cmdJobSublayoutJson.put("token", getToken());
		if (root != null) {
			cmdJobSublayoutJson.put("root", root);
		}
		this.getEventBus().publish("cmds", cmdJobSublayoutJson);
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

	public void publish(String channel, final Object payload) {

		this.getEventBus().publish(channel, payload);
	}

	public void send(final String channel, final Object payload) {
		this.getEventBus().send(channel, payload);
	}

	public void publishCmd(final BaseEntity be, final String aliasCode, final String[] recipientsCode) {
		
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be, aliasCode);
		msg.setToken(getToken());
		if (recipientsCode != null) {
			msg.setRecipientCodeArray(recipientsCode);
		}

		publish("cmds",JsonUtils.toJson(msg));
	}
	
	public void publishData(final BaseEntity be, final String aliasCode, final String[] recipientsCode) {
		
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be, aliasCode);
		msg.setToken(getToken());
		if (recipientsCode != null) {
			msg.setRecipientCodeArray(recipientsCode);
		}
		publish("cmds",  RulesUtils.toJsonObject(msg));
	}

	public void publishCmd(final BaseEntity be, final String aliasCode) {
		this.publishCmd(be, aliasCode, null);
	}

	public void publishData(final BaseEntity be, final String[] recipientsCode) {
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be, null);
		msg.setRecipientCodeArray(recipientsCode);
		msg.setToken(getToken());
		publish("data", RulesUtils.toJsonObject(msg));
	}

	public void publishData(final Answer answer, final String[] recipientsCode) {
		QDataAnswerMessage msg = new QDataAnswerMessage(answer);
		msg.setRecipientCodeArray(recipientsCode);
		msg.setToken(getToken());
		publish("data", RulesUtils.toJsonObject(msg));
	}
	public void publishCmdToRecipients(final BaseEntity be, final String[] recipientsCode) {
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be, null);
		msg.setRecipientCodeArray(recipientsCode);
		msg.setToken(getToken());
		publish("cmds", RulesUtils.toJsonObject(msg));
	}

	public void publishData(final JsonObject msg) {
		msg.put("token", getToken());
		publish("data", msg);
	}

	public void publishCmd(final JsonObject msg) {
		msg.put("token", getToken());
		publish("cmds", msg);
	}
	
	public void publishCmd(final QDataMessage msg) {
		msg.setToken(getToken());
		publish("cmds", msg);
	}

	public void publishCmd(final QDataSubLayoutMessage msg) {
		msg.setToken(getToken());
		String json = JsonUtils.toJson(msg);
		publish("cmds", json);
	}

	public void publishData(final QDataMessage msg) {
		msg.setToken(getToken());
		publish("data", JsonUtils.toJson(msg));
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

	public void publishData(final QDataAskMessage msg) {
		msg.setToken(getToken());
		publish("data",  RulesUtils.toJsonObject(msg));
	}
	
	public void publishData(final QDataAttributeMessage msg) {
		msg.setToken(getToken());
		publish("data",  JsonUtils.toJson(msg));
	}

	public void publishCmd(final List<BaseEntity> beList, final String parentCode, final String linkCode) {
		this.publishCmd(beList, parentCode, linkCode, null);
	}

	public void publishCmd(final List<BaseEntity> beList, final String parentCode, final String linkCode,
			String[] recipientCodes) {
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beList.toArray(new BaseEntity[0]));
		msg.setParentCode(parentCode);
		msg.setLinkCode(linkCode);
		msg.setToken(getToken());
		if (recipientCodes != null) {
			msg.setRecipientCodeArray(recipientCodes);
		}
		publish("cmds",  RulesUtils.toJsonObject(msg) );
	}
	
	public void publishData(final List<BaseEntity> beList, final String parentCode, final String linkCode) {
		this.publishData(beList, parentCode, linkCode, null);
	}

	public void publishData(final BaseEntity be, final String parentCode, final String linkCode,
			String[] recipientCodes) 
	{
		List<BaseEntity> beList = new ArrayList<BaseEntity>();
		beList.add(be);
		publishData(beList, parentCode, linkCode, recipientCodes);
	}
	
	public void publishData(final List<BaseEntity> beList, final String parentCode, final String linkCode,
			String[] recipientCodes) {
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beList.toArray(new BaseEntity[0]));
		msg.setParentCode(parentCode);
		msg.setLinkCode(linkCode);
		msg.setToken(getToken());
		if (recipientCodes != null) {
			msg.setRecipientCodeArray(recipientCodes);
		}
		publish("data",  RulesUtils.toJsonObject(msg) );
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

	public void publishMsg(final QMSGMessage msg) {

		msg.setToken(getToken());
		publish("messages",  RulesUtils.toJsonObject(msg));
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
				return RulesUtils.getBaseEntityByCode(getQwandaServiceUrl(), getDecodedTokenMap(), getToken(), first.getSourceCode(),false);
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
						+ "/linkcodes/" + linkCode + "/children/"+linkValue, getToken());
				Link[] linkArray = RulesUtils.fromJson(beJson, Link[].class);
				if (linkArray.length > 0) {
					ArrayList<Link> arrayList = new ArrayList<Link>(Arrays.asList(linkArray));
					Link first = arrayList.get(0);
					RulesUtils.println("The Child BaseEnity code is   ::  " + first.getTargetCode());
					return RulesUtils.getBaseEntityByCode(getQwandaServiceUrl(), getDecodedTokenMap(), getToken(), first.getTargetCode(), false);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			return null;
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
			   String json = QwandaUtils.apiPostEntity(getQwandaServiceUrl()+"/qwanda/asks/qst", RulesUtils.toJson(qstMsg), getToken());
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
	
	public QDataAskMessage askQuestions(final QDataQSTMessage qstMsg, final boolean autoPushSelections, final boolean isPopup) {
		
		JsonObject questionJson = null;
		QDataAskMessage msg = null;
		String cmd_view = isPopup ? "CMD_POPUP" : "CMD_VIEW";
		try {
			if (autoPushSelections) {
				String json = QwandaUtils.apiPostEntity(getQwandaServiceUrl()+"/qwanda/asks/qst", RulesUtils.toJson(qstMsg), getToken());

				msg = RulesUtils.fromJson(json, QDataAskMessage.class);

				publishData(msg);

				QCmdViewMessage cmdFormView = new QCmdViewMessage(cmd_view, qstMsg.getRootQST().getQuestionCode());
				publishCmd(cmdFormView);
			} else {
				questionJson = new JsonObject(QwandaUtils.apiPostEntity(getQwandaServiceUrl()+"/qwanda/asks/qst", RulesUtils.toJson(qstMsg), getToken()));
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
		publishData(msg);
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
	
	public boolean sendSelectionsWithLinkValue(final String selectionRootCode, final String linkCode, final String linkValue, final Integer maxItems) {

		JsonObject selectionList;
		try {
		
			selectionList = new JsonObject(QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/baseentitys2/"
					+ selectionRootCode + "/linkcodes/" + linkCode +"/linkValue/"+linkValue+"?pageStart=0&pageSize=" + maxItems, getToken()));
			
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
					+ ((drools.getRule().getAgendaGroup() != null) ? drools.getRule().getAgendaGroup() : "") + showStates());
		} catch (NullPointerException e) {
			println("Error in rules: ", "ANSI_RED");
		}
	}

	public void footer() {
		try {
			RulesUtils.footer(drools.getRule().getName() + " - "
					+ ((drools.getRule().getAgendaGroup() != null) ? drools.getRule().getAgendaGroup() : "") + showStates());
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

	public void processAddressAnswers(QDataAnswerMessage m) {

		// Put this in to stop bad User null error.... TODO
		if (getUser() == null) {
			return;
		}
		try {

			Answer[] newAnswers = new Answer[50];
			Answer[] answers = m.getItems();

			String qwandaServiceUrl = getQwandaServiceUrl();
			
			String userCode = getUser().getCode();

			for (Answer answer : answers) {

				String targetCode = answer.getTargetCode();
				answer.setSourceCode(answer.getTargetCode());
				String attributeCode = answer.getAttributeCode();
				String value = answer.getValue();

				println("value ::" + value + "attribute code ::" + attributeCode);

				/* if this answer is actually an address another rule will be triggered */
				if (attributeCode.contains("ADDRESS_JSON")) {

					JsonObject addressDataJson = new JsonObject(value);
					
					println("The Address Json is  :: "+addressDataJson);

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
							newAnswers[i] = answerObj;
							i++;
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
						newAnswers[i] = answerObj;
						i++;
						println("The answer object for latitude attribute added to Answer array ");
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
						newAnswers[i] = answerObj;
						i++;
					}

					ArrayList<Answer> list = new ArrayList<Answer>();
					for (Answer s : newAnswers) {
						if (s != null)
							list.add(s);
					}

					println("---------------------------");
				//	println(list);
					newAnswers = list.toArray(new Answer[list.size()]);

					println(newAnswers);

					/* set new answers */
					m.setItems(newAnswers);
					String json = RulesUtils.toJson(m);
					println("updated answer json string ::" + json);

					/* send new answers to api */
					/* QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/answers/bulk", json, getToken()); */
					for(Answer an: newAnswers) {
						//publishData(an);
						saveAnswer(an);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void saveAnswer(Answer answer) {

		try {
			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/answers", RulesUtils.toJson(answer), getToken());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void processAnswerMessage(QDataAnswerMessage m)
	{
				
				publishData(m);

	}
	
	public void processChat(QEventMessage m)
	{
		
		String data = m.getData().getValue();
		JsonObject dataJson = new JsonObject(data);
		String text = dataJson.getString("value");
		String chatCode = dataJson.getString("itemCode");
		    
		if(text != null && chatCode != null) {
	            	
			/* creating new message */
			BaseEntity newMessage = QwandaUtils.createBaseEntityByCode(QwandaUtils.getUniqueId(getUser().getCode() , null, "MSG", getToken()), "message", getQwandaServiceUrl(), getToken());
    			if(newMessage != null) {  		    			
		    				
    				List<BaseEntity> stakeholders = getBaseEntitysByParentAndLinkCode(chatCode, "LNK_USER");
	    			String[] recipientCodeArray = new String[stakeholders.size()];
	    			
	    			int counter = 0;
	    			for(BaseEntity stakeholder: stakeholders) {
	    				recipientCodeArray[counter] = stakeholder.getCode();
	    				counter += 1;
	    			}
	    			
 	    			/*publishBaseEntityByCode(newMessage.getCode(), chatCode, "LNK_MESSAGES", recipientCodeArray); */
 	    			this.updateBaseEntityAttribute(newMessage.getCode(), newMessage.getCode(), "PRI_MESSAGE", text);
 	    			this.updateBaseEntityAttribute(newMessage.getCode(), newMessage.getCode(), "PRI_CREATOR", getUser().getCode());
		    		QwandaUtils.createLink(chatCode, newMessage.getCode(), "LNK_MESSAGES", "message", 1.0, getToken()); 
		    		BaseEntity chatBE = getBaseEntityByCode(newMessage.getCode());
		    		publishBE(chatBE);
    			}
		}
	}
	
	public void processImageUpload(QDataAnswerMessage m, final String finalAttributeCode) {
		
		/* we save the first photo as the icon of the BaseEntity */
		Answer[] answers = m.getItems();
		if(answers.length > 0) {
			
			Answer answer = answers[0];
			String sourceCode = answer.getSourceCode();
			String targetCode = answer.getTargetCode();
			answer.setSourceCode(answer.getTargetCode());
			String value = answer.getValue();
			this.updateBaseEntityAttribute(sourceCode, targetCode, finalAttributeCode, value);
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
			
			if(attributeCode.equals("PRI_RATING")) {
				
				/* we grab the old value of the rating as well as the current rating */
				String currentRatingString = getBaseEntityValueAsString(targetCode, finalAttributeCode);
				String numberOfRatingString = getBaseEntityValueAsString(targetCode, "PRI_NUMBER_RATING");
				
				if(currentRatingString != null && numberOfRatingString != null) {
					
					Double currentRating = Double.parseDouble(currentRatingString);
					Double numberOfRating = Double.parseDouble(numberOfRatingString);
					Double newRating = Double.parseDouble(value);
					
					/* we increment the number of current ratings */
					numberOfRating += 1;
					Answer ratingAnswer = new Answer(sourceCode, targetCode, "PRI_NUMBER_RATING", Double.toString(numberOfRating));
			        publishData(ratingAnswer);
			        
			        /* we compute the new rating */
			        
			        /* because for now we are not storing ALL the previous ratings, 
			         * we calculate a rolling average
			         */
			        
			        Double newRatingAverage = currentRating / numberOfRating;
			        newRatingAverage += newRating / numberOfRating;
					
					Answer newRatingAnswer = new Answer(sourceCode, targetCode, finalAttributeCode, Double.toString(newRatingAverage));
			        publishData(newRatingAnswer);
				}
				
				/* publishData(answer); */ 
			}
		}
	}


	
	public void processAnswer(QDataAnswerMessage m) {

		/* extract answers */
		List<Answer> answerList = new ArrayList<Answer>();

		/* extract answers */
		Answer[] answers = m.getItems();
		for (Answer answer : answers) {
			answerList.add(answer);
		}
		
		saveAnswers(answerList);
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
	private void saveAnswers(List<Answer> answers) {
		Answer items[] = new Answer[answers.size()];
		items = answers.toArray(items);
		QDataAnswerMessage msg = new QDataAnswerMessage(items);

		String jsonAnswer = RulesUtils.toJson(msg);
		try {
			QwandaUtils.apiPostEntity(getQwandaServiceUrl() + "/qwanda/answers/bulk", jsonAnswer, token);
		} catch (IOException e) {
			log.error("Socket error trying to post answer");
		}
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
	
	public Money includeGSTMoney(Money price) {
		
		Money gstPrice = Money.of(0, price.getCurrency());

		/* Including 10% of price for GST */

		if (price.compareTo(gstPrice) > 0) {
			
			Money priceToBeIncluded = price.multiply(0.1);		
			gstPrice = price.add(priceToBeIncluded);
		}

		return Money.of(gstPrice.getNumber().doubleValue(), price.getCurrency());
		//return gstPrice;

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

	public String showStates()
	{
		String states = "  ";
		for (String key : stateMap.keySet())
		{
			states+=key+":";
		}
		return states;
	}
	
	public void sendNotification(final String text, final String[] recipientCodes) {
		sendNotification(text,  recipientCodes, "info"); 
	}
	
	public void sendNotification(final String text, final String[] recipientCodes, final String style) {
		
		Layout notificationLayout = new Layout(text, style);
		QDataSubLayoutMessage data = new QDataSubLayoutMessage(notificationLayout, getToken());
		data.setRecipientCodeArray(recipientCodes);
		publishCmd(data);
	}
	
	public void sendSubLayouts() throws ClientProtocolException, IOException
	{
		String subLayoutMap = RulesUtils.getLayout("sublayouts");
 		if(subLayoutMap != null) {
		
			JsonArray subLayouts = new JsonArray(subLayoutMap);
			if(subLayouts != null) {
				Layout[] layoutArray = new Layout[subLayouts.size()];
				for(int i = 0; i < subLayouts.size(); i++) {
					JsonObject sublayoutData = null;
					
					try {
						sublayoutData = subLayouts.getJsonObject(i);
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					String url = sublayoutData.getString("download_url");
					String name = sublayoutData.getString("name");
					name = name.replace(".json", "");
					name = name.replaceAll("\"", "");
					
					if(url != null) {
						
						/*    grab sublayout from github   */

						println(i+":"+url);
						
						String subLayoutString = QwandaUtils.apiGet(url, null);
						if(subLayoutString != null) {
							
							try {
								layoutArray[i] = new Layout(name,subLayoutString);

						        
							}
							catch(Exception e) {
							} 
						}
					}
				}
				/*    send sublayout to FE    */
				QDataSubLayoutMessage msg = new QDataSubLayoutMessage(layoutArray,getToken());								
		        publishCmd(msg);

			}
			
		
		}
	}
	
	/*
	 * Gets all the attributes and Publishes to the DATA channel
	 */
	public void sendAllAttributes() {
		println("Sending all the attributes");
		try {
			 String json = QwandaUtils.apiGet(getQwandaServiceUrl() + "/qwanda/attributes", getToken());
			 QDataAttributeMessage msg = JsonUtils.fromJson(json, QDataAttributeMessage.class);
			 publishData(msg);
			 println("All the attributes sent");
			 
		} catch(Exception e) {
			e.printStackTrace();	
        }
	}
	
	/*
	 * Gets all the attribute and their value for the given basenentity code
	 */
	public Map<String, String> getMapOfAllAttributesValuesForBaseEntity(String beCode){
		 BaseEntity be = getBaseEntityByCode(beCode);
	        println("The load is ::"+be );
	        Set<EntityAttribute> eaSet =  be.getBaseEntityAttributes();
	        println("The set of attributes are  :: " +eaSet);
	        Map<String, String> attributeValueMap = new HashMap<String, String>();
	        for(EntityAttribute ea : eaSet){
	        	   String attributeCode = ea.getAttributeCode();
	        	   println("The attribute code  is  :: " +attributeCode);
	           String value =ea.getAsLoopString();
	           attributeValueMap.put(attributeCode, value);
	        }
	        
	        return attributeValueMap;
	} 
	
	public void processDimensions(QEventAttributeValueChangeMessage msg)
	{
        Answer newAnswer = msg.getAnswer();
        BaseEntity load = getBaseEntityByCode(newAnswer.getSourceCode());
        println("The laod value is "+load.toString());
     
        RulesUtils.println(" Load Baseentity Upodated  " );
        println("The updated laod name after PUT is "+load.getName());
        RulesUtils.println(" Inside the Load Title Attribute Change  rule  "   );
        RulesUtils.println("The created value  ::  "+newAnswer.getCreatedDate());
        RulesUtils.println("Answer from QEventAttributeValueChangeMessage in Load Title Attribute Change ::  "+newAnswer.toString());
 
        String value = newAnswer.getValue();
        println("The load "+msg.getData().getCode()+ " is    ::"  +value );
          
      /* Get the sourceCode(Job code) for this LOAD */
       BaseEntity job = getParent(newAnswer.getTargetCode(), "LNK_BEG");
      
      Answer jobTitleAnswer = new Answer(getUser().getCode() ,job.getCode(),  msg.getData().getCode() ,value);             
      saveAnswer(jobTitleAnswer);
	}
	
	public String getCurrentLocalDateTime() {
		LocalDateTime date = LocalDateTime.now();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");
		Date datetime = Date.from(date.atZone(ZoneId.systemDefault()).toInstant());
		String dateString = df.format(datetime);
		
		return dateString;
	}
	
	
	public String getCurrentLocalDate() {
		LocalDate date = LocalDate.now();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		Date currentDate = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
		String dateString = df.format(currentDate);
		return dateString;
	}

	public void publishBE(final BaseEntity be)
	{
		String[] recipientCodes = new String[1];
		recipientCodes[0] = be.getCode();
		publishBE(be,recipientCodes);
	}
	public void publishBE(final BaseEntity be, String[] recipientCodes)
	{
		println(be);
		BaseEntity[]  itemArray = new BaseEntity[1];
		itemArray[0] = be;
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(itemArray, null,
			      null);
		msg.setRecipientCodeArray(recipientCodes);
//		String json = JsonUtils.toJson(msg);
			publishCmd(msg, recipientCodes);
	}
	
	public BaseEntity   createBaseEntityByCode(final String userCode, final String bePrefix, final String name) 
	{
	    BaseEntity beg = QwandaUtils.createBaseEntityByCode(QwandaUtils.getUniqueId(userCode, null, bePrefix, getToken()), name, qwandaServiceUrl, getToken());
	    return beg;
	}

	public Money calcOwnerFee(Money input) {
		
		CurrencyUnit DEFAULT_CURRENCY_TYPE = input.getCurrency();
		Number inputNum = input.getNumber();
		
		Money ownerFee = Money.of(0, DEFAULT_CURRENCY_TYPE);
		
		Number RANGE_1 = 999.99;
		Number RANGE_2 = 2999.99;
		Number RANGE_3 = 4999.99;

		Number FEE_1= 0.15;
		Number FEE_2= 0.125;
		Number FEE_3= 0.09;
		Number FEE_4= 0.05;
		
		Number RANGE_1_COMPONENT = MoneyHelper.mul(inputNum, FEE_1);
		Number RANGE_2_COMPONENT = MoneyHelper.mul(RANGE_1, FEE_1);;
		Number RANGE_3_COMPONENT = MoneyHelper.mul( MoneyHelper.sub(RANGE_2, RANGE_1), FEE_2);
		Number RANGE_4_COMPONENT = MoneyHelper.mul( MoneyHelper.sub(RANGE_3, RANGE_2), FEE_3);
		
		if (inputNum.doubleValue() <= RANGE_1.doubleValue()) {
			// RANGE_1_COMPONENT
			ownerFee = Money.of(RANGE_1_COMPONENT, DEFAULT_CURRENCY_TYPE);

			System.out.println("range 1 ");
		}
	
		if ( inputNum.doubleValue() > RANGE_1.doubleValue() && inputNum.doubleValue() <= RANGE_2.doubleValue() ){
			// RANGE_2_COMPONENT + (input - RANGE_1) * FEE_2
			System.out.println(input);
			Money subtract = MoneyHelper.sub(input, RANGE_1);
			System.out.println(subtract);
			Money multiply = MoneyHelper.mul(subtract, FEE_2 );
			System.out.println(multiply);
			ownerFee = MoneyHelper.add(multiply, RANGE_2_COMPONENT);
			System.out.println(ownerFee);

			System.out.println("range 2 ");
		}
	
		if ( inputNum.doubleValue() > RANGE_2.doubleValue() && inputNum.doubleValue() <= RANGE_3.doubleValue() ) {	
			//RANGE_2_COMPONENT + RANGE_3_COMPONENT + (input - RANGE_2) * FEE_3
			Number addition1 = MoneyHelper.add(RANGE_2_COMPONENT, RANGE_3_COMPONENT);
			Money subtract = MoneyHelper.sub(input, RANGE_2);
			Money multiply = MoneyHelper.mul(subtract, FEE_3);
			Money addition2 = MoneyHelper.add(multiply, addition1);
			ownerFee = addition2;

			System.out.println("range 3 ");
		}
	
		if ( inputNum.doubleValue() > RANGE_3.doubleValue() ) {
			// RANGE_2_COMPONENT + RANGE_3_COMPONENT + RANGE_4_COMPONENT + ( input - RANGE_3 ) * FEE_4
			Number addition1 = MoneyHelper.add(RANGE_2_COMPONENT, RANGE_3_COMPONENT);
			Number addition2 = MoneyHelper.add(addition1, RANGE_4_COMPONENT);
			Money subtract = MoneyHelper.sub(input, RANGE_3);
			Money multiply = MoneyHelper.mul(subtract, FEE_4);
			Money addition3 = MoneyHelper.add(multiply, addition2);
			ownerFee = addition3;

			System.out.println("range 4 ");
		}
		
		/* To prevent exponential values from appearing in amount. Not 1.7E+2, We need 170 */
		ownerFee = MoneyHelper.round(ownerFee);
		ownerFee = Money.of(ownerFee.getNumber().doubleValue(), DEFAULT_CURRENCY_TYPE);

		Number amount = ownerFee.getNumber().doubleValue();
		Money fee = Money.of(amount.doubleValue(), DEFAULT_CURRENCY_TYPE);
		System.out.println("From QRules " + fee);
		return fee;

	}
	public Money calcDriverFee(Money input) {

		CurrencyUnit DEFAULT_CURRENCY_TYPE = input.getCurrency();
		Number inputNum = input.getNumber();

		Money driverFee = Money.of(0, DEFAULT_CURRENCY_TYPE);
		
		Number RANGE_1 = 999.99;
		Number RANGE_2 = 2999.99;
		Number RANGE_3 = 4999.99;

		Number FEE_1= 0.15;
		Number FEE_2= 0.125;
		Number FEE_3= 0.09;
		Number FEE_4= 0.05;

		Number ONE = 1;
		
		// const REVERSE_FEE_MULTIPLIER_1 = ( RANGE_2 - RANGE_1 ) * FEE_2;
		// const REVERSE_FEE_MULTIPLIER_2 = ( RANGE_3 - RANGE_2 ) * FEE_3;

		Number subtract01 = MoneyHelper.sub( RANGE_2 ,RANGE_1 );
		Number subtract02 = MoneyHelper.sub( RANGE_3 ,RANGE_2 );
		Number REVERSE_FEE_MULTIPLIER_1 = MoneyHelper.mul( subtract01 , FEE_2);
		Number REVERSE_FEE_MULTIPLIER_2 = MoneyHelper.mul( subtract02 , FEE_3);

		// const REVERSE_FEE_BOUNDARY_1 = RANGE_1 - ( RANGE_1 * FEE_1 );
		// const REVERSE_FEE_BOUNDARY_2 = RANGE_2 - REVERSE_FEE_MULTIPLIER_1 - ( RANGE_1 * FEE_1 );
		// const REVERSE_FEE_BOUNDARY_3 = RANGE_3 - REVERSE_FEE_MULTIPLIER_2 - REVERSE_FEE_MULTIPLIER_1 - ( RANGE_1 * FEE_1 );
		
		Number multiply01 = MoneyHelper.mul( RANGE_1, FEE_1 );
			Number REVERSE_FEE_BOUNDARY_1 = MoneyHelper.sub(RANGE_1, multiply01);

		Number subtract03 = MoneyHelper.sub( RANGE_2 ,REVERSE_FEE_MULTIPLIER_1 );
			Number REVERSE_FEE_BOUNDARY_2 = MoneyHelper.sub(subtract03, multiply01);

		Number subtract04 = MoneyHelper.sub( RANGE_3 ,REVERSE_FEE_MULTIPLIER_2 );
		Number subtract05 = MoneyHelper.sub(subtract04 , REVERSE_FEE_MULTIPLIER_1);
			Number REVERSE_FEE_BOUNDARY_3 = MoneyHelper.sub(subtract05, multiply01);

		if ( inputNum.doubleValue() < REVERSE_FEE_BOUNDARY_1.doubleValue() ) {
			// return calcOwnerFee( inputNum * (1 / (1 - FEE_1)));
			Number subtract = MoneyHelper.sub(ONE, FEE_1);
			Number divide = MoneyHelper.div(ONE, subtract);
			Money multiply = MoneyHelper.mul(input, divide);
			driverFee = calcOwnerFee(multiply);

			System.out.println("zone 1 ");
		}

		if ( inputNum.doubleValue() >= REVERSE_FEE_BOUNDARY_1.doubleValue() && inputNum.doubleValue() < REVERSE_FEE_BOUNDARY_2.doubleValue() ) {
			// calcFee(( input ) * (1 / (1 - (( REVERSE_FEE_BOUNDARY_1 * FEE_1 ) + (( input - REVERSE_FEE_BOUNDARY_1 ) * FEE_2 )) / input )));
			Money subtract1 = MoneyHelper.sub(input, REVERSE_FEE_BOUNDARY_1);
			Money multiply1 = MoneyHelper.mul(subtract1, FEE_2);
			Number multiply2 = MoneyHelper.mul(FEE_1, REVERSE_FEE_BOUNDARY_1);
			Money addition1 = MoneyHelper.add(multiply1, multiply2);
			Money divide1= MoneyHelper.div(addition1, input);
			Money subtract2 = MoneyHelper.sub(ONE, divide1);
			Money divide2 = MoneyHelper.div(ONE, subtract2);

			Money multiply3 = MoneyHelper.mul(input, divide2);
			driverFee = calcOwnerFee(multiply3);

			System.out.println("zone 2 ");
		}

		if ( inputNum.doubleValue() >= REVERSE_FEE_BOUNDARY_2.doubleValue() && inputNum.doubleValue() < REVERSE_FEE_BOUNDARY_3.doubleValue() ) {
			//calcFee(( input ) * (1 / (1 - (( REVERSE_FEE_BOUNDARY_1 * FEE_1 ) + REVERSE_FEE_MULTIPLIER_1 + (( input - REVERSE_FEE_BOUNDARY_2 ) * FEE_3 )) / input )))
			Money subtract1 = MoneyHelper.sub(input, REVERSE_FEE_BOUNDARY_2);
			Money multiply1 = MoneyHelper.mul(subtract1, FEE_3);
			Number multiply2 = MoneyHelper.mul(REVERSE_FEE_BOUNDARY_1, FEE_1);
			Number addition1 = MoneyHelper.add(multiply2 , REVERSE_FEE_MULTIPLIER_1);
			Money addition2 = MoneyHelper.add(multiply1 , addition1);
			Money divide1 = MoneyHelper.div(addition2, input);
			Money subtract2 = MoneyHelper.sub(ONE, divide1);
			Money divide2 = MoneyHelper.div(ONE, subtract2);

			Money multiply3 = MoneyHelper.mul(input,divide2);
			driverFee = calcOwnerFee(multiply3);

			System.out.println("zone 3 ");
		}

		if ( inputNum.doubleValue() >= REVERSE_FEE_BOUNDARY_3.doubleValue() ) {
			//calcFee(( input ) * (1 / (1 - (( REVERSE_FEE_BOUNDARY_1 * FEE_1 ) + REVERSE_FEE_MULTIPLIER_1 + REVERSE_FEE_MULTIPLIER_2 + (( input - REVERSE_FEE_BOUNDARY_3 ) * FEE_4 )) / input )))
			
			Money subtract1 = MoneyHelper.sub(input, REVERSE_FEE_BOUNDARY_3);
			Money multiply1 = MoneyHelper.mul(subtract1, FEE_4);

			Number multiply2 = MoneyHelper.mul(REVERSE_FEE_BOUNDARY_1, FEE_1);
			Number addition1 = MoneyHelper.add(multiply2 , REVERSE_FEE_MULTIPLIER_1);
			Number addition2 = MoneyHelper.add(addition1 , REVERSE_FEE_MULTIPLIER_2);

			Money addition3 = MoneyHelper.add(multiply1 , addition2);
			Money divide1 = MoneyHelper.div(addition3, input);
			Money subtract2 = MoneyHelper.sub(ONE, divide1);
			Money divide2 = MoneyHelper.div(ONE, subtract2);

			Money multiply3 = MoneyHelper.mul(input,divide2);
			driverFee = calcOwnerFee(multiply3);

			System.out.println("zone 4 ");
		}
		return driverFee;
	}
	 
}
