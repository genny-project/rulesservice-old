package life.genny.utils;



import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.Logger;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;

import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisOptions;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.MessageProducer;
import io.vertx.rxjava.core.shareddata.AsyncMap;
import io.vertx.rxjava.core.shareddata.SharedData;
import io.vertx.rxjava.redis.RedisClient;
import life.genny.channels.ClusterMap;
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
	    DIRECT,
	    TRIGGER;

	}

	static Map<String,String> localCache = new ConcurrentHashMap<String,String>();
	static Map<String,MessageProducer<JsonObject>> localMessageProducerCache = new ConcurrentHashMap<String,MessageProducer<JsonObject>>();

//	static RedisOptions config = null;
//	static RedisClient redis;
//
//	static public void init() {
//	config = new RedisOptions()
//			  .setHost(hostIP);
//
//	redis = RedisClient.create(ClusterMap.getVertxContext(), config);
//	}

  static public  <T>  T  getObject(final String realm, final String keyPrefix, final String key, final Class clazz)
  {
	  T item = null;
	  JsonObject json = readCachedJson(realm+":"+keyPrefix+":"+key);
	  if (json.getString("status").equalsIgnoreCase("ok")) {
	  String data = json.getString("value");
	  	item = (T) JsonUtils.fromJson(data, clazz);
	  	return item;
	  }
	  else {
		  return null;
	  }
  }

  static public  void  putObject(final String realm, final String keyPrefix, final String key, final Object obj)
  {
	String data = JsonUtils.toJson(obj);
	writeCachedJson(realm+":"+keyPrefix+":"+key,data);
  }

  static public JsonObject readCachedJson(final String key) {

//	  CompletableFuture<JsonObject> fut = new CompletableFuture<JsonObject>();
//	  redis.get(key, res -> {
//		  if (res.succeeded()) {
//			  JsonObject ok = new JsonObject().put("status", "ok").put("value", res.result());
//				fut.complete(ok);
//		  }  else {
//		        System.out.println("Connection or Operation Failed " + res.cause());
//	      }
//		});



//		SharedData sd = ClusterMap.getVertxContext().sharedData();
//
//	//	if (System.getenv("GENNY_DEV") == null) {
//			if (!cachedEnabled) {
//				fut.complete(new JsonObject().put("status", "error").put("value", "Cache Disabled"));
//			} else {
//				sd.getClusterWideMap("shared_data", (AsyncResult<AsyncMap<String, String>> res) -> {
//					if (res.failed()) {
//						fut.complete(new JsonObject().put("status", "error"));
//						;
//					} else {
//						AsyncMap<String, String> amap = res.result();
//						amap.get(key, (AsyncResult<String> comp) -> {
//							if (comp.failed()) {
//								JsonObject err = new JsonObject().put("status", "error").put("description",
//										"write failed");
//								fut.complete(err);
//							} else {
//								JsonObject ok = new JsonObject().put("status", "ok").put("value", comp.result());
//								fut.complete(ok);
//							}
//						});
//					}
//				});
//			}
//			try {
//				return fut.get();
//			} catch (InterruptedException | ExecutionException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		} else {
//			String ret = (String) sd.getLocalMap("shared_data").get(key);
//			if (ret == null) {
			String	ret = (String) localCache.get(key);
//			}
			JsonObject result = null;
			if (ret != null) {
				result = new JsonObject().put("status", "ok").put("value", ret);
			} else {
				result = new JsonObject().put("status", "error").put("value", ret);
			}
			return result;
//		}
//		return null;
	}

	static public JsonObject writeCachedJson(final String key, final String value) {
//		CompletableFuture<JsonObject> fut = new CompletableFuture<JsonObject>();
//
//	    redis.set(key, value, r -> {
//	        if (r.succeeded()) {
////	          System.out.println("key stored");
////	          client.get("key", s -> {
////	            System.out.println("Retrieved value: " + s.result());
////	          });
//	        } else {
//	          System.out.println("Connection or Operation Failed " + r.cause());
//	        }
//	      });

//		SharedData sd = ClusterMap.getVertxContext().sharedData();
//	//	if (System.getenv("GENNY_DEV") == null) {
//
//			sd.getClusterWideMap("shared_data", (AsyncResult<AsyncMap<String, String>> res) -> {
//				if (res.failed() || key == null || value == null) {
//					fut.complete(new JsonObject().put("status", "error"));
//
//				} else {
//					AsyncMap<String, String> amap = res.result();
//
//					amap.put(key, value, (AsyncResult<Void> comp) -> {
//						if (comp.failed()) {
//							fut.complete(new JsonObject().put("status", "error").put("description", "write failed"));
//						} else {
//							JsonObject ok = new JsonObject().put("status", "ok");
//							fut.complete(ok);
//						}
//					});
//				}
//			});
//			try {
//				return fut.get();
//			} catch (InterruptedException | ExecutionException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}

//		} else {
			localCache.put(key, value);
//			sd.getLocalMap("shared_data").put(key, value);
			JsonObject ok = new JsonObject().put("status", "ok");
			return ok;
//		}
//		return null;
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
				be = QwandaUtils.getBaseEntityByCode(code, token);
			} catch (Exception e) {
				// Okay, this is bad. Usually the code is not in the database but in keycloak
				// So lets leave it to the rules to sort out... (new user)
				log.error("BE "+code+" is NOT IN CACHE OR DB");
				return null;

			}
			if ((cachedEnabled)||(System.getenv("GENNY_DEV") != null) ) {
				writeCachedJson(code, JsonUtils.toJson(be));
			}
		}
		return be;
	}


	static public void subscribe(final String realm, final String subscriptionCode, final String userCode)
	{
		final String SUB = "SUB";
		// Subscribe to a code
		Set<String> subscriberSet = getSetString(realm,SUB,subscriptionCode);
		subscriberSet.add(userCode);
		putSetString(realm,SUB,subscriptionCode,subscriberSet);
	}

	static public void subscribe(final String realm, final List<BaseEntity> watchList, final String userCode)
	{
		final String SUB = "SUB";
		// Subscribe to a code
		for (BaseEntity be : watchList) {
			Set<String> subscriberSet = getSetString(realm,SUB,be.getCode());
			subscriberSet.add(userCode);
			putSetString(realm,SUB,be.getCode(),subscriberSet);
		}
	}

	static public void subscribe(final String realm, final BaseEntity be, final String userCode)
	{
		final String SUB = "SUB";
		// Subscribe to a code
			Set<String> subscriberSet = getSetString(realm,SUB,be.getCode());
			subscriberSet.add(userCode);
			putSetString(realm,SUB,be.getCode(),subscriberSet);

	}

	static public String[] getSubscribers(final String realm, final String subscriptionCode)
	{
		final String SUB = "SUB";
		// Subscribe to a code
		String[] resultArray = getObject(realm,SUB,subscriptionCode,String[].class);
		return resultArray;

	}

	static public void subscribeEvent(final String realm, final String subscriptionCode, final QEventMessage msg)
	{
		final String SUBEVT = "SUBEVT";
		// Subscribe to a code
		Set<String> subscriberSet = getSetString(realm,SUBEVT,subscriptionCode);
		subscriberSet.add(JsonUtils.toJson(msg));
		putSetString(realm,SUBEVT,subscriptionCode,subscriberSet);
	}

	static public QEventMessage[] getSubscribedEvents(final String realm, final String subscriptionCode)
	{
		final String SUBEVT = "SUBEVT";
		// Subscribe to a code
		String[] resultArray = getObject(realm,SUBEVT,subscriptionCode,String[].class);
		QEventMessage[] msgs = new QEventMessage[resultArray.length];
		int i=0;
		for (String result : resultArray) {
			msgs[i] = JsonUtils.fromJson(result, QEventMessage.class);
			i++;
		}
		return msgs;
	}

	static public Set<String> getSetString(final String realm, final String keyPrefix, final String key)
	{
		String[] resultArray = getObject(realm,keyPrefix,key,String[].class);
		if (resultArray == null) {
			return new HashSet<String>();
		}
		return Sets.newHashSet(resultArray);
	}

	  static public  void  putSetString(final String realm, final String keyPrefix, final String key, final Set set) {
		  String[] strArray = (String[]) FluentIterable.from(set).toArray(String.class);
		  putObject(realm, keyPrefix, key, strArray);

	  }

	public static void putMessageProducer(String sessionState, MessageProducer<JsonObject> toSessionChannel) {

		localMessageProducerCache.put(sessionState, toSessionChannel);

	}

	public static  MessageProducer<JsonObject> getMessageProducer(String sessionState) {

		return localMessageProducerCache.get(sessionState);

	}


	static public Set<String> getSetString(final String realm, final String keyPrefix, final String key)
	{
		String[] resultArray = getObject(realm,keyPrefix,key,String[].class);
		if (resultArray == null) {
			return new HashSet<String>();
		}
		return Sets.newHashSet(resultArray);
	}

	  static public  void  putSetString(final String realm, final String keyPrefix, final String key, final Set set) {
		  String[] strArray = (String[]) FluentIterable.from(set).toArray(String.class);
		  putObject(realm, keyPrefix, key, strArray);

	  }

	public static void putMessageProducer(String sessionState, MessageProducer<JsonObject> toSessionChannel) {

		localMessageProducerCache.put(sessionState, toSessionChannel);

	}

	public static  MessageProducer<JsonObject> getMessageProducer(String sessionState) {

		return localMessageProducerCache.get(sessionState);

	}


}
