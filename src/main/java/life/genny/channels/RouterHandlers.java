package life.genny.channels;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;

import io.vertx.core.AsyncResult;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpServerRequest;
import io.vertx.rxjava.core.shareddata.AsyncMap;
import io.vertx.rxjava.core.shareddata.SharedData;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.CorsHandler;


public class RouterHandlers {

	private static String vertxUrl = System.getenv("REACT_APP_VERTX_URL");
	private static String hostIP = System.getenv("HOSTIP") != null ? System.getenv("HOSTIP") : "127.0.0.1";

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	public static CorsHandler cors() {
		return CorsHandler.create("*").allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.POST)
				.allowedMethod(HttpMethod.OPTIONS).allowedHeader("X-PINGARUNER").allowedHeader("Content-Type")
				.allowedHeader("X-Requested-With");
	}
	
	static Gson gson = new GsonBuilder()
			.registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
				@Override
				public LocalDateTime deserialize(final JsonElement json, final Type type,
						final JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
					return LocalDateTime.parse(json.getAsJsonPrimitive().getAsString(),
							DateTimeFormatter.ISO_LOCAL_DATE_TIME);
				}

				public JsonElement serialize(final LocalDateTime date, final Type typeOfSrc,
						final JsonSerializationContext context) {
					return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)); // "yyyy-mm-dd"
				}
			}).create();

	static public Vertx vertx;

	public static void apiMapPutHandler(final RoutingContext context) {
        final HttpServerRequest req = context.request();
        String param1 = req.getParam("param1");
        String param2 = req.getParam("param2");

        SharedData sd = vertx.sharedData();
        sd.getClusterWideMap("shared_data", (AsyncResult<AsyncMap<String,String>> res) -> {
            if (res.failed()) {
                JsonObject err = new JsonObject().put("status", "error");
                req.response().headers().set("Content-Type", "application/json");
                req.response().end(err.encode());
            } else {
                AsyncMap<String,String> amap = res.result();
                amap.put(param1, param2, (AsyncResult<Void> comp) -> {
                    if (comp.failed()) {
                        JsonObject err = new JsonObject()
                            .put("status", "error")
                            .put("description", "write failed");
                        req.response().headers().set("Content-Type", "application/json");
                        req.response().end(err.encode());
                    }else {
                        JsonObject err = new JsonObject().put("status", "ok");
                        req.response().headers().set("Content-Type", "application/json");
                        req.response().end(err.encode());
                    }
                });
            }
        });
 
	}

	public static void apiMapGetHandler(final RoutingContext context) {
        final HttpServerRequest req = context.request();
        String param1 = req.getParam("param1");

        SharedData sd = vertx.sharedData();
        sd.getClusterWideMap("shared_data", (AsyncResult<AsyncMap<String,String>> res) -> {
            if (res.failed()) {
                JsonObject err = new JsonObject().put("status", "error");
                req.response().headers().set("Content-Type", "application/json");
                req.response().end(err.encode());
            } else {
                AsyncMap<String,String> amap = res.result();
                amap.get(param1, (AsyncResult<String> comp) -> {
                    if (comp.failed()) {
                        JsonObject err = new JsonObject()
                            .put("status", "error")
                            .put("description", "write failed");
                        req.response().headers().set("Content-Type", "application/json");
                        req.response().end(err.encode());
                    }else{
                        JsonObject err = new JsonObject()
                            .put("status", "ok")
                            .put("value", comp.result());
                        req.response().headers().set("Content-Type", "application/json");
                        req.response().end(err.encode());
                    }
                });
            }
        });

	}


}
