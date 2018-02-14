package life.genny.utils;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.Logger;

import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.shareddata.AsyncMap;
import io.vertx.rxjava.core.shareddata.SharedData;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.QwandaUtils;;

public class VertxUtils {
	
	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());


	static boolean cachedEnabled = false;
	
	public enum ESubscriptionType {
	    DIRECT,
	    TRIGGER;

	}


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
	  

		CompletableFuture<JsonObject> fut = new CompletableFuture<JsonObject>();

		SharedData sd =  Vertx.currentContext().owner().sharedData();

		if (System.getenv("GENNY_DEV") == null) {
			if (!cachedEnabled) {
				fut.complete(new JsonObject().put("status", "error").put("value", "Cache Disabled"));
			} else {
				sd.getClusterWideMap("shared_data", (AsyncResult<AsyncMap<String, String>> res) -> {
					if (res.failed()) {
						fut.complete(new JsonObject().put("status", "error"));
						;
					} else {
						AsyncMap<String, String> amap = res.result();
						amap.get(key, (AsyncResult<String> comp) -> {
							if (comp.failed()) {
								JsonObject err = new JsonObject().put("status", "error").put("description",
										"write failed");
								fut.complete(err);
							} else {
								JsonObject ok = new JsonObject().put("status", "ok").put("value", comp.result());
								fut.complete(ok);
							}
						});
					}
				});
			}
			try {
				return fut.get();
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			String ret = (String) sd.getLocalMap("shared_data").get(key);
			JsonObject result = null;
			if (ret != null) {
				result = new JsonObject().put("status", "ok").put("value", ret);
			} else {
				result = new JsonObject().put("status", "error").put("value", ret);
			}
			return result;
		}
		return null;
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

	static public JsonObject writeCachedJson(final String key, final String value) {
		CompletableFuture<JsonObject> fut = new CompletableFuture<JsonObject>();

		SharedData sd = Vertx.currentContext().owner().sharedData();
		if (System.getenv("GENNY_DEV") == null) {

			sd.getClusterWideMap("shared_data", (AsyncResult<AsyncMap<String, String>> res) -> {
				if (res.failed() || key == null || value == null) {
					fut.complete(new JsonObject().put("status", "error"));

				} else {
					AsyncMap<String, String> amap = res.result();

					amap.put(key, value, (AsyncResult<Void> comp) -> {
						if (comp.failed()) {
							fut.complete(new JsonObject().put("status", "error").put("description", "write failed"));
						} else {
							JsonObject ok = new JsonObject().put("status", "ok");
							fut.complete(ok);
						}
					});
				}
			});
			try {
				return fut.get();
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else {
			sd.getLocalMap("shared_data").put(key, value);
			JsonObject ok = new JsonObject().put("status", "ok");
			return ok;
		}
		return null;
	}

	
	
	public void subscribe(final String realm, final String subscriptionCode, final String userCode)
	{
		Set set = new HashSet<String>() { }; // create a specific sub-class
		final Class<? extends Set> setClass = set.getClass();
		final ParameterizedType genericSuperclass = (ParameterizedType) setClass.getGenericSuperclass();
		Class elementType = (Class) genericSuperclass.getActualTypeArguments()[0];
		// Subscribe to a code
		Set<String> subscriberSet = getObject(realm,"SUB",subscriptionCode,elementType);
		if (subscriberSet == null) {
			// create 
			subscriberSet = new HashSet<String>();
		}
		subscriberSet.add(userCode);
		putObject(realm,"SUB",subscriptionCode,subscriberSet);
	}
	
}
