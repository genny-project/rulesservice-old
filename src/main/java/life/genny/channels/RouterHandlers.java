package life.genny.channels;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.Logger;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.http.HttpServerRequest;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.CorsHandler;
import life.genny.utils.VertxUtils;



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
		          VertxUtils.writeCachedJson(param1, param2);
		         
		                  JsonObject ret = new JsonObject().put("status", "ok");
		                  context.request().response().headers().set("Content-Type", "application/json");
		                  context.request().response().end(ret.encode());

		        }
		    });

		    
		    

		  }

		  public static void apiMapGetHandler(final RoutingContext context) {
		    final HttpServerRequest req = context.request();
		    String param1 = req.getParam("param1");

		    JsonObject json = VertxUtils.readCachedJson(param1);
		      if (json.getString("status").equals("error")) {
		        JsonObject err = new JsonObject().put("status", "error");
		        req.response().headers().set("Content-Type", "application/json");
		        req.response().end(err.encode());
		      } else {
		            req.response().headers().set("Content-Type", "application/json");
		            req.response().end(json.encode());
		          }

		  }



}
