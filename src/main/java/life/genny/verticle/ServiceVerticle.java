package life.genny.verticle;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;
import io.vertx.rxjava.core.shareddata.AsyncMap;
import io.vertx.rxjava.core.shareddata.SharedData;
import life.genny.channels.Routers;
import life.genny.cluster.Cluster;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.rules.RulesLoader;
import life.genny.rules.RulesUtils;

public class ServiceVerticle extends AbstractVerticle {

	static String rulesDir = System.getenv("RULES_DIR");

	
	  @Override
	  public void start() {
	    System.out.println("Loading initial Rules");
	    final Future<Void> startFuture = Future.future();
	    Cluster.joinCluster(vertx).compose(res -> {
	      final Future<Void> fut = Future.future();
	      if (rulesDir == null) {
	    	  	rulesDir = "rules";
	      }
			     
	      RulesLoader.loadInitialRules(vertx,rulesDir).compose(p -> {
	    	  	Routers.routers(vertx);
	        fut.complete();
	      }, fut);
	    }, startFuture);
	  
	  }
}
