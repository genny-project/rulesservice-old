package life.genny.rulesservice;

import io.vertx.rxjava.core.AbstractVerticle;
import life.genny.cluster.Cluster;

public class ServiceVerticle extends AbstractVerticle {

	@Override
	public void start() {
		Cluster.joinCluster(vertx);
	}
}
