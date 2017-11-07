package life.genny.verticle;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;

import life.genny.cluster.Cluster;
import life.genny.routes.Routers;
import life.genny.security.SecureResources;

public class ServiceVerticle extends AbstractVerticle {

	
	 @Override
	  public void start() {
	    System.out.println("Setting up routes");
	    final Future<Void> startFuture = Future.future();
	    Cluster.joinCluster(vertx).compose(res -> {
	      final Future<Void> fut = Future.future();
	      SecureResources.setKeycloakJsonMap(vertx).compose(p -> {
	        Routers.routers(vertx);
	        fut.complete();
	      }, fut);
	      startFuture.complete();
	    }, startFuture);
	  }
}
