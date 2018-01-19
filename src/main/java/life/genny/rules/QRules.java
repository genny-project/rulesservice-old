package life.genny.rules;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.attribute.EntityAttribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwandautils.RulesUtils;

public class QRules {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	public static final String qwandaServiceUrl = System.getenv("REACT_APP_QWANDA_API_URL");
	public static final Boolean devMode = System.getenv("GENNY_DEV") == null ? false : true;

	final static String DEFAULT_STATE = "NEW";

	private String token;
	private EventBus eventBus;
	private Map<String, Object> decodedTokenMap;
	private String state;

	public QRules(final EventBus eventBus, final String token, final Map<String, Object> decodedTokenMap,
			String state) {
		super();
		this.eventBus = eventBus;
		this.token = token;
		this.decodedTokenMap = decodedTokenMap;
		this.state = state;
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
	public String getState() {
		return state;
	}

	/**
	 * @param state
	 *            the state to set
	 */
	public void setState(String state) {
		this.state = state;
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

	public String getAsString(final String key) {
		return (String) get(key);
	}

	public BaseEntity getAsBaseEntity(final String key) {
		return (BaseEntity) get(key);
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
		if (get(key) == null) {
			return false;
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
	
	public  LocalDateTime getBaseEntityValueAsLocalDateTime(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);		
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);		
		if (ea.isPresent()) {
			return ea.get().getValueDateTime();
		} else {
			return null;
		}
	}
	
	public  LocalDate getBaseEntityValueAsLocalDate(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);		
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);		
		if (ea.isPresent()) {
			return ea.get().getValueDate();
		} else {
			return null;
		}
	}
	
	public  LocalTime getBaseEntityValueAsLocalTime(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);		
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);		
		if (ea.isPresent()) {
			return ea.get().getValueTime();
		} else {
			return null;
		}
	}
}
