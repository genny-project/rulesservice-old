package life.genny.verticle;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;
import life.genny.cluster.Cluster;
import life.genny.rules.RulesLoader;

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
	        fut.complete();
	      }, fut);
	      startFuture.complete();
	    }, startFuture);
	  
	  }
}
