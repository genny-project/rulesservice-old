package life.genny.verticle;


import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.Logger;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;
import life.genny.channel.Routers;
import life.genny.channels.EBCHandlers;
import life.genny.cluster.Cluster;
import life.genny.cluster.CurrentVtxCtx;
import life.genny.eventbus.EventBusInterface;
import life.genny.qwandautils.GennySettings;
import life.genny.rules.EventBusVertx;
import life.genny.rules.RulesLoader;
import life.genny.security.SecureResources;

public class ServiceVerticle extends AbstractVerticle {
	
	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	static EventBusInterface eventBus;


  @Override
  public void start() {

    System.out.println("Loading initial Rules");
    final Future<Void> startFuture = Future.future();
    Cluster.joinCluster().compose(res -> {
      final Future<Void> fut = Future.future();
       RulesLoader.loadInitialRules(GennySettings.rulesDir).compose(p -> {
  //      Routers.routers(vertx);
        
        // Load in realm data
        final Future<Void> rfut = Future.future();
        SecureResources.setKeycloakJsonMap().compose(r -> {
     	    final Future<Void> startupfut = Future.future();
    	     RulesLoader.triggerStartupRules(GennySettings.rulesDir).compose(q -> {
    	        startupfut.complete();
    	        
    	    }, startupfut);
    	  if (GennySettings.isRulesManager) {
     		  Routers.routers(vertx);
    		  Routers.activate(vertx);
    	  }
          rfut.complete();
        }, rfut);
        
        eventBus = new EventBusVertx(CurrentVtxCtx.getCurrentCtx().getClusterVtx().eventBus());
        EBCHandlers.registerHandlers(eventBus);
        

        fut.complete();
      }, fut);
       
  
    }, startFuture);
   

  }
}
