package life.genny.verticle;


import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;
import life.genny.channels.EBCHandlers;
import life.genny.channels.Routers;
import life.genny.cluster.Cluster;
import life.genny.cluster.CurrentVtxCtx;
import life.genny.rules.RulesLoader;
import life.genny.security.SecureResources;

public class ServiceVerticle extends AbstractVerticle {

  static String rulesDir = System.getenv("RULES_DIR");


  @Override
  public void start() {
    System.out.println("Loading initial Rules");
    final Future<Void> startFuture = Future.future();
    Cluster.joinCluster().compose(res -> {
      final Future<Void> fut = Future.future();
      if (rulesDir == null) {
        rulesDir = "rules";
      }
       RulesLoader.loadInitialRules(rulesDir).compose(p -> {
  //      Routers.routers(vertx);
        
        // Load in realm data
        final Future<Void> rfut = Future.future();
        SecureResources.setKeycloakJsonMap().compose(r -> {
     	    final Future<Void> startupfut = Future.future();
    	    if (rulesDir == null) {
    	      rulesDir = "rules";
    	    }
    	     RulesLoader.triggerStartupRules(rulesDir).compose(q -> {
    	        startupfut.complete();
    	        
    	    }, startupfut);
          Routers.routers(vertx);
          rfut.complete();
        }, rfut);
        
        EBCHandlers.registerHandlers(CurrentVtxCtx.getCurrentCtx().getClusterVtx().eventBus());
        

        fut.complete();
      }, fut);
       
  
    }, startFuture);
   

  }
}
