package life.genny.verticle;


import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

import javax.naming.NamingException;

import org.apache.logging.log4j.Logger;

import com.google.common.io.Files;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.channel.Routers;
import life.genny.channels.EBCHandlers;
import life.genny.cluster.Cluster;
import life.genny.cluster.CurrentVtxCtx;
import life.genny.eventbus.EventBusInterface;
import life.genny.eventbus.EventBusVertx;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwandautils.GennySettings;
import life.genny.rules.RulesLoader;
import life.genny.security.SecureResources;

public class ServiceVerticle extends AbstractVerticle {
	
	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	

  @Override
  public void start() {

    System.out.println("Loading initial Rules");
    final Future<Void> startFuture = Future.future();
    Cluster.joinCluster().compose(res -> {
      final Future<Void> fut = Future.future();
      EventBusInterface eventBus = new EventBusVertx();
      
       loadInitialRules(GennySettings.rulesDir).compose(p -> {
  //      Routers.routers(vertx);
        
        // Load in realm data
        final Future<Void> rfut = Future.future();
        SecureResources.setKeycloakJsonMap().compose(r -> {
     	    final Future<Void> startupfut = Future.future();
    	     triggerStartupRules(GennySettings.rulesDir, eventBus).compose(q -> {
    	        startupfut.complete();
    	        
    	    }, startupfut);
    	  if (GennySettings.isRulesManager) {
     		  Routers.routers(vertx);
    		  Routers.activate(vertx);
    	  }
          rfut.complete();
        }, rfut);
        
        EBCHandlers.registerHandlers(eventBus);
        

        fut.complete();
      }, fut);
       
  
    }, startFuture);
   

  }
  
	/**
	 * @param vertx
	 * @return
	 */
	public static Future<Void> loadInitialRules(final String rulesDir) {
		
		final Future<Void> fut = Future.future();
		Vertx.currentContext().owner().executeBlocking(exec -> {
			RulesLoader.loadRules(rulesDir);

			fut.complete();
		}, failed -> {
		});

		return fut;
	}
	
	/**
	 * @param vertx
	 * @return
	 */
	public static Future<Void> triggerStartupRules(final String rulesDir, EventBusInterface eventBus) {
		log.info("Triggering Startup Rules for all realms");
		final Future<Void> fut = Future.future();
		Vertx.currentContext().owner().executeBlocking(exec -> {// Force Genny first
			log.info("---- Realm:genny Startup Rules ----------");


			RulesLoader.initMsgs("Event:INIT_STARTUP", new QEventMessage("EVT_MSG", "INIT_STARTUP"), eventBus);
			fut.complete();
		}, failed -> {
		});

		return fut;
	}

}
