package life.genny.verticle;


import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;
import life.genny.channels.EBCHandlers;
import life.genny.channels.Routers;
import life.genny.cluster.Cluster;
import life.genny.cluster.CurrentVtxCtx;
import life.genny.rules.RulesLoader;

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
        Routers.routers(vertx);
        EBCHandlers.registerHandlers(CurrentVtxCtx.getCurrentCtx().getClusterVtx().eventBus());
        
        final Future<Void> reportfut = Future.future();
        if (rulesDir == null) {
          rulesDir = "rules";
        }
         RulesLoader.generateReports(rulesDir).compose(q -> {
            reportfut.complete();
            
        }, reportfut);
        fut.complete();
      }, fut);
    }, startFuture);
   
  }
}
