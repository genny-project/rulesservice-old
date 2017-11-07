package life.genny.routes;

import java.io.IOException;
import java.util.Properties;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.vertx.rxjava.ext.web.RoutingContext;


final class VersionHandler {
	 private static final Gson gson = new GsonBuilder()
		        .registerTypeAdapter(Properties.class, PropertiesJsonDeserializer.getPropertiesJsonDeserializer())
		        .create();


	 
	  public static void apiGetVersionHandler(final RoutingContext routingContext) {
		    routingContext.request().bodyHandler(body -> {
		            Properties properties = new Properties();
		            try {
		              properties.load(Thread.currentThread().getContextClassLoader().getResource("git.properties")
		                  .openStream());
		            } catch (IOException e) {
		              // TODO Auto-generated catch block
		              e.printStackTrace();
		            }

		          routingContext.response().putHeader("Content-Type", "application/json");
		          routingContext.response().end(gson.toJson(properties));

		    });
		  }
}
