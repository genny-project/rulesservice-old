package life.genny.utils;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.QwandaUtils;;

public class VertxUtils {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	private static String hostIP = System.getenv("HOSTIP") != null ? System.getenv("HOSTIP") : "127.0.0.1";

	static boolean cachedEnabled = true;

	public enum ESubscriptionType {
		DIRECT, TRIGGER;

	}

	static Map<String, String> localCache = new ConcurrentHashMap<String, String>();
	static Map<String, MessageProducer<JsonObject>> localMessageProducerCache = new ConcurrentHashMap<String, MessageProducer<JsonObject>>();





	static public <T> T getObject(final String realm, final String keyPrefix, final String key, final Class clazz) {
		T item = null;
		JsonObject json = readCachedJson(realm + ":" + keyPrefix + ":" + key);
		if (json.getString("status").equalsIgnoreCase("ok")) {
			String data = json.getString("value");
			item = (T) JsonUtils.fromJson(data, clazz);
			return item;
		} else {
			return null;
		}
	}
	
	static public <T> T getObject(final String realm, final String keyPrefix, final String key, final Type clazz) {
		T item = null;
		JsonObject json = readCachedJson(realm + ":" + keyPrefix + ":" + key);
		if (json.getString("status").equalsIgnoreCase("ok")) {
			String data = json.getString("value");
			item = (T) JsonUtils.fromJson(data, clazz);
			return item;
		} else {
			return null;
		}
	}

	static public void putObject(final String realm, final String keyPrefix, final String key, final Object obj) {
		String data = JsonUtils.toJson(obj);
		writeCachedJson(realm + ":" + keyPrefix + ":" + key, data);
	}

	static public JsonObject readCachedJson(final String key) {
		String ret = (String) DistMap.getDistBE().get(key);
		JsonObject result = null;
		if (ret != null) {
			result = new JsonObject().put("status", "ok").put("value", ret);
		} else {
			result = new JsonObject().put("status", "error").put("value", ret);
		}
		return result;

	}

	static public JsonObject writeCachedJson(final String key, final String value) {

		if (value == null) {
			DistMap.getDistBE().delete(key);
		} else {
			DistMap.getDistBE().put(key, value);
		}
		JsonObject ok = new JsonObject().put("status", "ok");
		return ok;

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
		 if ((cachedEnabled)||(System.getenv("GENNY_DEV") != null) ) {
		 writeCachedJson(code, JsonUtils.toJson(be));
		 }
		 }
		return be;
	}

	static public BaseEntity readFromDDT(final String code, final String token) {
		BaseEntity be = null;
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
		 if ((cachedEnabled)||(System.getenv("GENNY_DEV") != null) ) {
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

	public static void putMessageProducer(String sessionState, MessageProducer<JsonObject> toSessionChannel) {

		localMessageProducerCache.put(sessionState, toSessionChannel);

	}

	public static MessageProducer<JsonObject> getMessageProducer(String sessionState) {

		return localMessageProducerCache.get(sessionState);

	}
	
	static public void putMap(final String realm, final String keyPrefix, final String key, final Map<String,Object> map) {
		putObject(realm, keyPrefix, key, map);
	}
	
	static public Map<String,Object> getMap(final String realm, final String keyPrefix, final String key) {
		Type type = new TypeToken<Map<String, Object>>(){}.getType();
		Map<String, Object> myMap = getObject(realm, keyPrefix, key, type);
		return myMap;
	}
	
	
}
