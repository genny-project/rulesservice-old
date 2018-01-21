package life.genny.rules;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.attribute.EntityAttribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.message.QCmdGeofenceMessage;
import life.genny.qwanda.message.QCmdMessage;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwandautils.GPSUtils;
import life.genny.qwandautils.QwandaUtils;

public class QRules {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	public static final String qwandaServiceUrl = System.getenv("REACT_APP_QWANDA_API_URL");
	public static final Boolean devMode = System.getenv("GENNY_DEV") == null ? false : true;

	final static String DEFAULT_STATE = "NEW";

	private String token;
	private EventBus eventBus;
	private Map<String, Object> decodedTokenMap;
	private Map<String, Boolean> state;

	public QRules(final EventBus eventBus, final String token, final Map<String, Object> decodedTokenMap,
			String state) {
		super();
		this.eventBus = eventBus;
		this.token = token;
		this.decodedTokenMap = decodedTokenMap;
		this.state = new HashMap<String, Boolean>();
		setState(DEFAULT_STATE);
	}

	public QRules(final EventBus eventBus, final String token, final Map<String, Object> decodedTokenMap) {
		this(eventBus, token, decodedTokenMap, DEFAULT_STATE);
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
	 * @return the state
	 */
	public Boolean isState(final String key) {
		if (state.containsKey(key)) {
			return state.get(key);
		} else {
			return false;
		}
	}

	/**
	 * @param state
	 *            the state to set
	 */
	public void setState(String key) {
		state.put(key.toUpperCase(), true);
		RulesUtils.println("STATE "+key+" SET",RulesUtils.ANSI_RED);
	}

	/**
	 * @param state
	 *            the state to clear
	 */
	public void clearState(String key) {
		state.remove(key);
		RulesUtils.println("STATE "+key+" CLEARED",RulesUtils.ANSI_PURPLE);

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "QRules [decodedTokenMap=" + decodedTokenMap + ", state=" + state + "]";
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
		if (isNull("USER")) {
			be = RulesUtils.getUser(qwandaServiceUrl, getDecodedTokenMap(), getToken());
			set("USER", be); // WATCH THIS!!!
		} else {
			be = getAsBaseEntity("USER");
		}
		return be;
	}

	public Boolean isUserPresent()
	{
		if (isNull("USER")) {
			return false;
		} else {
			return true;
		}
	}

	public BaseEntity getBaseEntityByCode(final String code) {
		BaseEntity be = null;
		if (isNull("BE_" + code.toUpperCase())) {
			be = RulesUtils.getBaseEntityByCode(qwandaServiceUrl, getDecodedTokenMap(), getToken(), code);
			set("BE_" + code.toUpperCase(), be); // WATCH THIS!!!
		} else {
			be = getAsBaseEntity("BE_" + code.toUpperCase());
		}
		return be;
	}

	public BaseEntity getBaseEntityByAttributeAndValue(final String attributeCode, final String value) {

		BaseEntity be = null;
		if (isNull("BE_" + attributeCode.toUpperCase()+"_"+value)) {
			be = RulesUtils.getBaseEntityByAttributeAndValue(qwandaServiceUrl, getDecodedTokenMap(), getToken(), attributeCode, value);
			set("BE_" + attributeCode.toUpperCase()+"_"+value, be); // WATCH THIS!!!
		} else {
			be = getAsBaseEntity("BE_" + attributeCode.toUpperCase()+"_"+value);
		}
		return be;
	}

	public List<BaseEntity> getBaseEntitysByAttributeAndValue(final String attributeCode, final String value) {

		List<BaseEntity> bes = null;
		if (isNull("BE_" + attributeCode.toUpperCase()+"_"+value)) {
			bes = RulesUtils.getBaseEntitysByAttributeAndValue(qwandaServiceUrl, getDecodedTokenMap(), getToken(), attributeCode, value);
			set("BE_" + attributeCode.toUpperCase()+"_"+value, bes); // WATCH THIS!!!
		}

		return bes;
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode) {
		return getBaseEntitysByParentAndLinkCode(parentCode, linkCode, 0, 10, false);
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart, Integer pageSize) {
		return getBaseEntitysByParentAndLinkCode(parentCode, linkCode, pageStart, pageSize, false);
	}


	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart, Integer pageSize, Boolean cache) {
		List<BaseEntity> bes = null;
		if (isNull("BES_" + parentCode.toUpperCase()+"_"+linkCode)) {
			bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes(qwandaServiceUrl, getDecodedTokenMap(), getToken(), parentCode, linkCode);
			if (cache) {
				set("BES_" + parentCode.toUpperCase()+"_"+linkCode,bes); // WATCH THIS!!!
			}
		} else {
			bes = getAsBaseEntitys("BES_" + parentCode.toUpperCase()+"_"+linkCode);
		}
		return bes;
	}

	public void publishBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart, Integer pageSize, Boolean cache) {

			String json = RulesUtils.getBaseEntitysJsonByParentAndLinkCode(qwandaServiceUrl, getDecodedTokenMap(), getToken(), parentCode, linkCode);
			JsonObject obj = new  JsonObject(json);
			obj.put("token", getToken());
			publish("cmds",obj);
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

	public String getBaseEntityValueAsString(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getObjectAsString();
		} else {
			return null;
		}
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
	
	public void geofenceJob(final String begCode, final String driverCode, Double radius) {
			
		BaseEntity be = RulesUtils.getBaseEntityByCode(QRules.getQwandaServiceUrl(), this.getDecodedTokenMap(), this.getToken(), begCode);
		QCmdGeofenceMessage[] cmds = GPSUtils.geofenceJob(be, driverCode, radius, QRules.getQwandaServiceUrl(), this.getToken(), this.getDecodedTokenMap());
		
		for(QCmdGeofenceMessage cmd: cmds) {
			this.publishCmd(cmd);
		}
	}

	public BaseEntity createUser()
	{
		BaseEntity be = null;

		String username = getAsString("preferred_username").toLowerCase();
		String firstname = StringUtils.capitaliseAllWords(getAsString("given_name").toLowerCase());
		String lastname = StringUtils.capitaliseAllWords(getAsString("family_name").toLowerCase());
		String realm = StringUtils.capitaliseAllWords(getAsString("realm").toLowerCase());
		String name = StringUtils.capitaliseAllWords(getAsString("name").toLowerCase());
		String email = getAsString("email").toLowerCase();
		String keycloakId = getAsString("sub").toLowerCase();

		try {
			be = QwandaUtils.createUser(qwandaServiceUrl,getToken(), username, firstname, lastname, email, realm,name, keycloakId);
			set("USER",be);

		} catch (IOException e) {
			log.error("Error in Creating User ");
		}
		return be;
	}

	/**
	 * @return the state
	 */
	public Map<String, Boolean> getState() {
		return state;
	}

	/**
	 * @param state the state to set
	 */
	public void setState(Map<String, Boolean> state) {
		this.state = state;
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

	public void publish(String channel, final Object payload)
	{
		if (channel.startsWith("debug")) {
			channel = channel.substring("debug".length());
		}
		
		this.getEventBus().publish(channel, payload);
	}

	public void send(final String channel, final Object payload)
	{
		this.getEventBus().send(channel, payload);
	}

	public void publishCmd(final BaseEntity be, final String aliasCode)
	{
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be,aliasCode);
		msg.setToken(getToken());
	    publish("cmds", RulesUtils.toJsonObject(msg));
	}
	
	public void publishData(final QDataAnswerMessage msg)
	{
		msg.setToken(getToken());
	    publish("data", RulesUtils.toJsonObject(msg));
	}
	
	public void publishCmd(final List<BaseEntity> beList, final String parentCode, final String linkCode)
	{
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beList.toArray(new BaseEntity[0]));
		msg.setParentCode(parentCode);
		msg.setLinkCode(linkCode);
		msg.setToken(getToken());
	    publish("cmds", RulesUtils.toJsonObject2(msg));
	}

	public void publishCmd(final QCmdMessage cmdMsg)
	{
		cmdMsg.setToken(getToken());
	    publish("cmds", RulesUtils.toJsonObject(cmdMsg));
	}

}
