package life.genny.cluster;

import io.vertx.rxjava.core.Future;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.channels.EBCHandlers;
import life.genny.channels.EBConsumers;
import life.genny.channels.EBProducers;
import rx.functions.Action1;

public class Cluster {

  public static Action1<? super Vertx> registerAllChannels = vertx -> {
    final EventBus eb = vertx.eventBus();
    EBConsumers.registerAllConsumer(eb);
    EBProducers.registerAllProducers(eb);
    EBCHandlers.registerHandlers(eb);
  };

  static Action1<Throwable> clusterError = error -> {
    System.out.println("error in the cluster: " + error.getMessage());
  };

  public static Future<Void> joinCluster(final Vertx vertx) {
    final Future<Void> fut = Future.future();
    vertx.rxClusteredVertx(ClusterConfig.configCluster()).subscribe(registerAllChannels,
        clusterError);
    fut.complete();
    return fut;
  }

}
