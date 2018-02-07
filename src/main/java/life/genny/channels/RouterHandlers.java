package life.genny.channels;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.Logger;

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


	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	public static CorsHandler cors() {
		return CorsHandler.create("*").allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.POST)
				.allowedMethod(HttpMethod.OPTIONS).allowedHeader("X-PINGARUNER").allowedHeader("Content-Type")
				.allowedHeader("X-Requested-With");
	}
	



	 public static void apiMapPutHandler(final RoutingContext context) {
		    
		    
		    //handle the body here and assign it to wifiPayload to process the data 
		    final HttpServerRequest req = context.request().bodyHandler(boddy -> {
		   //   System.out.println(boddy.toJsonObject());
		    	  JsonObject wifiPayload = boddy.toJsonObject();
		      if (wifiPayload == null) {
		    	  context.request().response().headers().set("Content-Type", "application/json");
		          JsonObject err = new JsonObject().put("status", "error");
		          context.request().response().headers().set("Content-Type", "application/json");
		          context.request().response().end(err.encode());
		        } 
		      else {
		          // a JsonObject wraps a map and it exposes type-aware getters
		          String param1 = wifiPayload.getString("key");
		          System.out.println("CACHE KEY:"+param1);
		          String param2 = wifiPayload.getString("json");
		          SharedData sd = Vertx.currentContext().owner().sharedData();
		          sd.getClusterWideMap("shared_data", (AsyncResult<AsyncMap<String, String>> res) -> {
		            if (res.failed() || param1 == null || param2 == null) {
		              JsonObject err = new JsonObject().put("status", "error");
		              context.request().response().headers().set("Content-Type", "application/json");
		              context.request().response().end(err.encode());
		            } else {
		              AsyncMap<String, String> amap = res.result();
		              
		              amap.put(param1, param2, (AsyncResult<Void> comp) -> {
		                if (comp.failed()) {
		                  JsonObject err =
		                      new JsonObject().put("status", "error").put("description", "write failed");
		                  context.request().response().headers().set("Content-Type", "application/json");
		                  context.request().response().end(err.encode());
		                } else {
		                  JsonObject err = new JsonObject().put("status", "ok");
		                  context.request().response().headers().set("Content-Type", "application/json");
		                  context.request().response().end(err.encode());
		                }
		              });
		            }
		          });
		        }
		    });

		    
		    

		  }

		  public static void apiMapGetHandler(final RoutingContext context) {
		    final HttpServerRequest req = context.request();
		    String param1 = req.getParam("param1");

		    SharedData sd =  Vertx.currentContext().owner().sharedData();
		    sd.getClusterWideMap("shared_data", (AsyncResult<AsyncMap<String, String>> res) -> {
		      if (res.failed()) {
		        JsonObject err = new JsonObject().put("status", "error");
		        req.response().headers().set("Content-Type", "application/json");
		        req.response().end(err.encode());
		      } else {
		        AsyncMap<String, String> amap = res.result();
		        amap.get(param1, (AsyncResult<String> comp) -> {
		          if (comp.failed()) {
		            JsonObject err =
		                new JsonObject().put("status", "error").put("description", "write failed");
		            req.response().headers().set("Content-Type", "application/json");
		            req.response().end(err.encode());
		          } else {
		            JsonObject err = new JsonObject().put("status", "ok").put("value", comp.result());
		            req.response().headers().set("Content-Type", "application/json");
		            req.response().end(err.encode());
		          }
		        });
		      }
		    });

		  }



}
