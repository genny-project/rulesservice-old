package life.genny.utils;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.eventbus.MessageProducer;
import life.genny.channel.DistMap;
import life.genny.channel.Producer;
import life.genny.qwanda.attribute.EntityAttribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.message.QBulkMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwandautils.GennySettings;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.QwandaUtils;;

public class VertxUtils {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	static boolean cachedEnabled = true;
	
	static final String DEFAULT_TOKEN = "DUMMY";
	static final String[] DEFAULT_FILTER_ARRAY = { "PRI_FIRSTNAME", "PRI_LASTNAME", "PRI_MOBILE",
			"PRI_IMAGE_URL", "PRI_CODE", "PRI_NAME", "PRI_USERNAME" };



	public enum ESubscriptionType {
		DIRECT, TRIGGER;

	}

	static Map<String, String> localCache = new ConcurrentHashMap<String, String>();
	static Map<String, MessageProducer<JsonObject>> localMessageProducerCache = new ConcurrentHashMap<String, MessageProducer<JsonObject>>();

	static public void setRealmFilterArray(final String realm, final String[] filterArray)
	{
		 putStringArray(realm, "FILTER", "PRIVACY",
					filterArray);
	}
	
	static public String[] getRealmFilterArray(final String realm)
	{
		String[] result = getStringArray(realm, "FILTER", "PRIVACY");
		if (result == null) {
			return DEFAULT_FILTER_ARRAY;
		} else {
			return result;
		}
}

	static public <T> T getObject(final String realm, final String keyPrefix, final String key, final Class clazz) {
		return getObject(realm, keyPrefix, key, clazz, DEFAULT_TOKEN);
	}

	static public <T> T getObject(final String realm, final String keyPrefix, final String key, final Class clazz,
			final String token) {
		T item = null;
		JsonObject json = readCachedJson(realm + ":" + keyPrefix + ":" + key, token);
		if (json.getString("status").equalsIgnoreCase("ok")) {
			String data = json.getString("value");
			item = (T) JsonUtils.fromJson(data, clazz);
			return item;
		} else {
			return null;
		}

	}

	static public <T> T getObject(final String realm, final String keyPrefix, final String key, final Type clazz) {
		return getObject(realm, keyPrefix, key, clazz, DEFAULT_TOKEN);
	}

	static public <T> T getObject(final String realm, final String keyPrefix, final String key, final Type clazz,
			final String token) {
		T item = null;
		JsonObject json = readCachedJson(realm + ":" + keyPrefix + ":" + key, token);
		if (json.getString("status").equalsIgnoreCase("ok")) {
			String data = json.getString("value");
			item = (T) JsonUtils.fromJson(data, clazz);
			return item;
		} else {
			return null; 
		}
	}

	static public void putObject(final String realm, final String keyPrefix, final String key, final Object obj) {
		putObject(realm, keyPrefix, key, obj, DEFAULT_TOKEN);
	}

	static public void putObject(final String realm, final String keyPrefix, final String key, final Object obj,
			final String token) {
		String data = JsonUtils.toJson(obj);
		data = data.replaceAll("\\\"", "\"");
		data = data.replaceAll("\\n", "\n");
		writeCachedJson(realm + ":" + keyPrefix + ":" + key, data, token);
	}

	static public JsonObject readCachedJson(final String key) {
		return readCachedJson(key, DEFAULT_TOKEN);
	}

	static public JsonObject readCachedJson(final String key, final String token) {
		JsonObject result = null;

		if (GennySettings.isDdtHost) {
			String ret = null;
			ret = (String) DistMap.getDistBE().get(key);

			if (ret != null) {
				result = new JsonObject().put("status", "ok").put("value", ret);
			} else { 
				result = new JsonObject().put("status", "error").put("value", ret);
			}
		} else {
			String resultStr = null;
			try {
				resultStr = QwandaUtils.apiGet(GennySettings.ddtUrl + "/read/" + key, token);
				result = new JsonObject(resultStr);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		return result;
	}

	static public JsonObject writeCachedJson(final String key, final String value) {
		return writeCachedJson(key, value, "DUMMY");
	}

	static public JsonObject writeCachedJson(final String key, final String value, final String token) {
		if (GennySettings.isDdtHost) {

			if (value == null) {
				DistMap.getDistBE().delete(key);
			} else {
				DistMap.getDistBE().put(key, value);
			}

		} else {
			try {
				QwandaUtils.apiPostEntity(GennySettings.ddtUrl + "/write", value, token);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		JsonObject ok = new JsonObject().put("status", "ok");
		return ok;

	}
	
	static public JsonObject writeCachedJson(final String key, final String value, final String token, long ttl_seconds) {
		if (GennySettings.isDdtHost) {

			if (value == null) {
				DistMap.getDistBE().delete(key);
			} else {
				DistMap.getDistBE().putTransient(key, value, ttl_seconds,TimeUnit.SECONDS);
			}

		} else {
			try {
				QwandaUtils.apiPostEntity(GennySettings.ddtUrl + "/write", value, token);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		JsonObject ok = new JsonObject().put("status", "ok");
		return ok;

	}
	
	static public void clearDDT()
	{
		DistMap.clear();
	}

	static public BaseEntity readFromDDT(final String code, final boolean withAttributes, final String token) {
		BaseEntity be = null;
		JsonObject json = readCachedJson(code);
		if ("ok".equals(json.getString("status"))) {
			be = JsonUtils.fromJson(json.getString("value"), BaseEntity.class);
		} else {
			// fetch normally
			System.out.println("Cache MISS for " + code);
			try {
				if (withAttributes) {
					be = QwandaUtils.getBaseEntityByCodeWithAttributes(code, token);
				} else {
					be = QwandaUtils.getBaseEntityByCode(code, token);
				}
			} catch (Exception e) {
				// Okay, this is bad. Usually the code is not in the database but in keycloak
				// So lets leave it to the rules to sort out... (new user)
				log.error("BE " + code + " is NOT IN CACHE OR DB " + e.getLocalizedMessage());
				return null;

			}
			if ((cachedEnabled) || (System.getenv("GENNY_DEV") != null)) {
				writeCachedJson(code, JsonUtils.toJson(be));
			}
		}
		return be;
	}

	static boolean cacheDisabled = System.getenv("NO_CACHE") != null ? true : false;

	static public BaseEntity readFromDDT(final String code, final String token) {
		// if ("PER_SHARONCROW66_AT_GMAILCOM".equals(code)) {
		// System.out.println("DEBUG");
		// }
		BaseEntity be = null;
		if (cacheDisabled && (!code.startsWith("SBE_"))) {

			try {
				be = QwandaUtils.getBaseEntityByCode(code, token); // getBaseEntityByCodeWithAttributes
			} catch (Exception e) {
				// Okay, this is bad. Usually the code is not in the database but in keycloak
				// So lets leave it to the rules to sort out... (new user)
				log.error("BE " + code + " is NOT IN CACHE OR DB " + e.getLocalizedMessage());
				return null;

			}
		}

		JsonObject json = readCachedJson(code);
		if ("ok".equals(json.getString("status"))) {
			be = JsonUtils.fromJson(json.getString("value"), BaseEntity.class);
		} else {
			// fetch normally
			System.out.println("Cache MISS for " + code);
			try {
				be = QwandaUtils.getBaseEntityByCode(code, token); // getBaseEntityByCodeWithAttributes
			} catch (Exception e) {
				// Okay, this is bad. Usually the code is not in the database but in keycloak
				// So lets leave it to the rules to sort out... (new user)
				log.error("BE " + code + " is NOT IN CACHE OR DB " + e.getLocalizedMessage());
				return null;

			}
			if ((cachedEnabled) || (System.getenv("GENNY_DEV") != null)) {
				writeCachedJson(code, JsonUtils.toJson(be));
			}
		}
		return be;
}

	static public void subscribeAdmin(final String realm, final String adminUserCode) {
		final String SUBADMIN = "SUBADMIN";
		// Subscribe to a code
		Set<String> adminSet = getSetString(realm, SUBADMIN, "ADMINS");
		adminSet.add(adminUserCode);
		putSetString(realm, SUBADMIN, "ADMINS", adminSet);
	}
	
	static public void unsubscribeAdmin(final String realm, final String adminUserCode) {
		final String SUBADMIN = "SUBADMIN";
		// Subscribe to a code
		Set<String> adminSet = getSetString(realm, SUBADMIN, "ADMINS");
		adminSet.remove(adminUserCode);
		putSetString(realm, SUBADMIN, "ADMINS", adminSet);
	}

	
	static public void subscribe(final String realm, final String subscriptionCode, final String userCode) {
		final String SUB = "SUB";
		// Subscribe to a code
		Set<String> subscriberSet = getSetString(realm, SUB, subscriptionCode);
		subscriberSet.add(userCode);
		putSetString(realm, SUB, subscriptionCode, subscriberSet);
	}

	static public void subscribe(final String realm, final List<BaseEntity> watchList, final String userCode) {
		final String SUB = "SUB";
		// Subscribe to a code
		for (BaseEntity be : watchList) {
			Set<String> subscriberSet = getSetString(realm, SUB, be.getCode());
			subscriberSet.add(userCode);
			putSetString(realm, SUB, be.getCode(), subscriberSet);
		}
	}

	static public void subscribe(final String realm, final BaseEntity be, final String userCode) {
		final String SUB = "SUB";
		// Subscribe to a code
		Set<String> subscriberSet = getSetString(realm, SUB, be.getCode());
		subscriberSet.add(userCode);
		putSetString(realm, SUB, be.getCode(), subscriberSet);

	}
	
	/*
	 * Subscribe list of users to the be
	 */
	static public void subscribe(final String realm, final BaseEntity be, final String[] SubscribersCodeArray) {
		final String SUB = "SUB";
		// Subscribe to a code
		//Set<String> subscriberSet = getSetString(realm, SUB, be.getCode());
		//subscriberSet.add(userCode);
		Set<String> subscriberSet = new HashSet<String>(Arrays.asList(SubscribersCodeArray));
		putSetString(realm, SUB, be.getCode(), subscriberSet);

	}

	static public void unsubscribe(final String realm, final String subscriptionCode, final Set<String> userSet) {
		final String SUB = "SUB";
		// Subscribe to a code
		Set<String> subscriberSet = getSetString(realm, SUB, subscriptionCode);
		subscriberSet.removeAll(userSet);

		putSetString(realm, SUB, subscriptionCode, subscriberSet);
	}

	static public String[] getSubscribers(final String realm, final String subscriptionCode) {
		final String SUB = "SUB";
		// Subscribe to a code
		String[] resultArray = getObject(realm, SUB, subscriptionCode, String[].class);
		
		String[] resultAdmins = getObject(realm, "SUBADMIN", "ADMINS",String[].class);
		 String[] result = ArrayUtils.addAll(resultArray, resultAdmins);
		return result;

	}

	static public void subscribeEvent(final String realm, final String subscriptionCode, final QEventMessage msg) {
		final String SUBEVT = "SUBEVT";
		// Subscribe to a code
		Set<String> subscriberSet = getSetString(realm, SUBEVT, subscriptionCode);
		subscriberSet.add(JsonUtils.toJson(msg));
		putSetString(realm, SUBEVT, subscriptionCode, subscriberSet);
	}

	static public QEventMessage[] getSubscribedEvents(final String realm, final String subscriptionCode) {
		final String SUBEVT = "SUBEVT";
		// Subscribe to a code
		String[] resultArray = getObject(realm, SUBEVT, subscriptionCode, String[].class);
		QEventMessage[] msgs = new QEventMessage[resultArray.length];
		int i = 0;
		for (String result : resultArray) {
			msgs[i] = JsonUtils.fromJson(result, QEventMessage.class);
			i++;
		}
		return msgs;
}

	static public Set<String> getSetString(final String realm, final String keyPrefix, final String key) {
		String[] resultArray = getObject(realm, keyPrefix, key, String[].class);
		if (resultArray == null) {
			return new HashSet<String>();
		}
		return Sets.newHashSet(resultArray);
	}

	static public void putSetString(final String realm, final String keyPrefix, final String key, final Set set) {
		String[] strArray = (String[]) FluentIterable.from(set).toArray(String.class);
		putObject(realm, keyPrefix, key, strArray);
	}
	
	static public void putStringArray(final String realm, final String keyPrefix, final String key, final String[] string) {
		putObject(realm, keyPrefix, key, string);
	}
	
	static public String[] getStringArray(final String realm, final String keyPrefix, final String key) {
		String[] resultArray = getObject(realm, keyPrefix, key, String[].class);
		if (resultArray == null) {
			return null;
		}
		
		return resultArray;
	}


	static public void putMap(final String realm, final String keyPrefix, final String key, final Map<String,String> map) {
		putObject(realm, keyPrefix, key, map);
	}
	
	static public Map<String,String> getMap(final String realm, final String keyPrefix, final String key) {
		Type type = new TypeToken<Map<String, String>>(){}.getType();
		Map<String, String> myMap = getObject(realm, keyPrefix, key, type);
		return myMap;
	}

	public static void putMessageProducer(String sessionState, MessageProducer<JsonObject> toSessionChannel) {
		System.out.println("Registering SessionChannel to "+sessionState);
		localMessageProducerCache.put(sessionState, toSessionChannel);

	}

	public static MessageProducer<JsonObject> getMessageProducer(String sessionState) {

		return localMessageProducerCache.get(sessionState);

	}
	
	static public void publish(BaseEntity user, String channel, Object payload) {

		publish(user,channel,payload,DEFAULT_FILTER_ARRAY);
	}

	
	static public void publish(BaseEntity user, String channel, Object payload, final String[] filterAttributes) {
		// Actually Send ....
		switch (channel) {
		case "event":
		case "events":
			Producer.getToEvents().send(payload).end();
			;
			break;
		case "data":
			payload = privacyFilter(user, payload,filterAttributes);
			Producer.getToWebData().write(payload).end();
			;
			break;
		case "cmds":
			payload = privacyFilter(user, payload,filterAttributes);
			Producer.getToWebCmds().write(payload);
			break;
		case "services":
			Producer.getToServices().write(payload);
			break;
		case "messages":
			Producer.getToMessages().write(payload);
			break;
		default:
			log.error("Channel does not exist: " + channel);
		}
	}

	static public Object privacyFilter(BaseEntity user, Object payload, final String[] filterAttributes) {
		if (payload instanceof QDataBaseEntityMessage) {
			return JsonUtils.toJson(privacyFilter(user, (QDataBaseEntityMessage) payload,new HashMap<String, BaseEntity>(), filterAttributes));
		} else if (payload instanceof QBulkMessage) {
			return JsonUtils.toJson(privacyFilter(user, (QBulkMessage) payload,filterAttributes));
		} else
			return payload;
	}


	
	static public QDataBaseEntityMessage privacyFilter(BaseEntity user, QDataBaseEntityMessage msg,
			Map<String, BaseEntity> uniquePeople, final String[] filterAttributes) {
		ArrayList<BaseEntity> bes = new ArrayList<BaseEntity>();
		for (BaseEntity be : msg.getItems()) {
			if (!uniquePeople.containsKey(be.getCode())) {
				
				be = privacyFilter(user, be, filterAttributes);
				uniquePeople.put(be.getCode(), be);
				bes.add(be);
			}else {
				/* Avoid sending the attributes again for the same BaseEntity, so sending without attributes */
				BaseEntity slimBaseEntity = new BaseEntity(be.getCode(), be.getName());
				/* Setting the links again but Adam don't want it to be send as it increasing the size of BE.
				 * Frontend should create links based on the parentCode of baseEntity not the links. This requires work in the frontend.
				 * But currently the GRP_NEW_ITEMS are being sent without any links so it doesn't show any internships.
				 */
				slimBaseEntity.setLinks(be.getLinks());
				bes.add(slimBaseEntity);
			}
		}
		msg.setItems(bes.toArray(new BaseEntity[bes.size()]));
		return msg;
	}
	
	static public QBulkMessage privacyFilter(BaseEntity user,QBulkMessage msg, final String[] filterAttributes) {
		Map<String, BaseEntity> uniqueBes = new HashMap<String, BaseEntity>();
		for (QDataBaseEntityMessage beMsg : msg.getMessages()) {
			beMsg = privacyFilter(user,beMsg, uniqueBes,filterAttributes);
		}
		return msg;
}

	static public BaseEntity privacyFilter(BaseEntity user, BaseEntity be) {
		final String[] filterStrArray = { "PRI_FIRSTNAME", "PRI_LASTNAME", "PRI_MOBILE", "PRI_DRIVER", "PRI_OWNER",
				"PRI_IMAGE_URL", "PRI_CODE", "PRI_NAME", "PRI_USERNAME", "PRI_DRIVER_RATING" };

		return privacyFilter(user, be, filterStrArray);
	}

	static public BaseEntity privacyFilter(BaseEntity user, BaseEntity be, final String[] filterAttributes) {
		Set<EntityAttribute> allowedAttributes = new HashSet<EntityAttribute>();
		for (EntityAttribute entityAttribute : be.getBaseEntityAttributes()) {
			// System.out.println("ATTRIBUTE:"+entityAttribute.getAttributeCode()+(entityAttribute.getPrivacyFlag()?"PRIVACYFLAG=TRUE":"PRIVACYFLAG=FALSE"));
			if ((be.getCode().startsWith("PER_")) && (!be.getCode().equals(user.getCode()))) {
				String attributeCode = entityAttribute.getAttributeCode();

				if (Arrays.stream(filterAttributes).anyMatch(x -> x.equals(attributeCode))) {


					allowedAttributes.add(entityAttribute);
				} else {
					if (attributeCode.startsWith("PRI_IS_")) {
						allowedAttributes.add(entityAttribute);// allow all roles
					}
					if (attributeCode.startsWith("LNK_")) {
						allowedAttributes.add(entityAttribute);// allow attributes that starts with "LNK_"
					}
				}
			} else {
				if (!entityAttribute.getPrivacyFlag()) { // don't allow privacy flag attributes to get through
					allowedAttributes.add(entityAttribute);
				}
			}
		}
		be.setBaseEntityAttributes(allowedAttributes);

		return be;
	}

	public static Boolean checkIfAttributeValueContainsString(BaseEntity baseentity, String attributeCode,
			String checkIfPresentStr) {

		Boolean isContainsValue = false;

		if (baseentity != null && attributeCode != null && checkIfPresentStr != null) {
			String attributeValue = baseentity.getValue(attributeCode, null);

			if (attributeValue != null && attributeValue.toLowerCase().contains(checkIfPresentStr.toLowerCase())) {
				return true;
			}
		}

		return isContainsValue;
	}


	
}
