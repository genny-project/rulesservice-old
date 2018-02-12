package life.genny.utils;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
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
import life.genny.qwandautils.MergeUtil;
import life.genny.qwandautils.QwandaUtils;;

public class VertxUtils {
	
	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());


	static boolean cachedEnabled = false;


	private static Vertx vertxContext;
	
	/**
   * @return the vertxContext
   */
  public static Vertx getVertxContext() {
    return vertxContext;
  }

  /**
   * @param vertxContext the vertxContext to set
   */
  public static void setVertxContext(Vertx vertxContext) {
    VertxUtils.vertxContext = vertxContext;
  }

  static public JsonObject readCachedJson(final String key) {
	  

		CompletableFuture<JsonObject> fut = new CompletableFuture<JsonObject>();

		SharedData sd = getVertxContext().sharedData();

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

}
