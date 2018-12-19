package life.genny.verticle;


import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.kie.api.KieServices;

import io.vavr.Tuple3;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;
import io.vertx.rxjava.core.Vertx;
import life.genny.channel.Routers;
import life.genny.channels.EBCHandlers;
import life.genny.cluster.Cluster;
import life.genny.eventbus.EventBusInterface;
import life.genny.eventbus.EventBusVertx;
import life.genny.eventbus.VertxCache;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwandautils.GennyCacheInterface;
import life.genny.qwandautils.GennySettings;
import life.genny.rules.RulesLoader;
import life.genny.security.SecureResources;
import life.genny.utils.VertxUtils;

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
      GennyCacheInterface vertxCache = new VertxCache();
      VertxUtils.init(eventBus,vertxCache);
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
	public  Future<Void> loadInitialRules(final String rulesDir) {
		
		final Future<Void> fut = Future.future();
		Vertx.currentContext().owner().executeBlocking(exec -> {

//		vertx.executeBlocking(exec -> {
			log.info("Load Rules using Vertx 1");
			RulesLoader.loadRules(rulesDir);
//			RulesLoader.setKieBaseCache(new HashMap<String, KieBase>()); // clear
			log.info("Load Rules using Vertx 2");
//			RulesLoader.ks = KieServices.Factory.get();
//			log.info("Load Rules using Vertx 3");
//			List<Tuple3<String, String, String>> rules = RulesLoader.processFileRealms("genny", rulesDir);
//			log.info("Load Rules using Vertx 4");
//			RulesLoader.realms = RulesLoader.getRealms(rules);
//			RulesLoader.realms.stream().forEach(System.out::println);
//			RulesLoader.realms.remove("genny");
//			RulesLoader.setupKieRules("genny", rules); // run genny rules first
//			for (String realm : RulesLoader.realms) {
//				RulesLoader.setupKieRules(realm, rules);
//			}
			fut.complete();
		}, failed -> {
		});

		return fut;
	}
	
	/**
	 * @param vertx
	 * @return
	 */
	public  Future<Void> triggerStartupRules(final String rulesDir, EventBusInterface eventBus) {
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
