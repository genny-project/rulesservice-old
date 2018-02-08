package life.genny.cluster;

import com.hazelcast.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

/**
 * @author Adam Crow
 * @author Byron Aguirre
 */
public class ClusterConfig {
  private static final Logger logger = LoggerFactory.getLogger(ClusterConfig.class);


  private static String hostIP =
      System.getenv("HOSTIP") != null ? System.getenv("HOSTIP") : "127.0.0.1";
  
  private static String systemUser =
    	      System.getenv("USER") != null ? System.getenv("USER") : "genny";

  private static String privateIP = System.getenv("MYIP");
  private final static int portHazelcastCluster = 5702;
  private final static int portEBCluster = 15702;

  /**
   * @param toClientOutbount the toClientOutbount to set
   */
  public static Config configHazelcastCluster() {
    final Config hazelcastConfig = new Config();
    hazelcastConfig.getGroupConfig().setName(systemUser).setPassword(systemUser);

    hazelcastConfig.getNetworkConfig().setPort(portHazelcastCluster);
    hazelcastConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
    hazelcastConfig.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);
    hazelcastConfig.getNetworkConfig().setPublicAddress(hostIP);
    hazelcastConfig.getNetworkConfig().getJoin().getTcpIpConfig().addMember(hostIP);
    
    return hazelcastConfig;
  }

  /**
   * @param toClientOutbount the toClientOutbount to set
   */
  public static EventBusOptions configEBCluster() {
    final EventBusOptions eb = new EventBusOptions();
    if (privateIP != null)
      eb.setPort(portEBCluster).setHost(privateIP);
    return eb;
  }

  /**
   * @param toClientOutbount the toClientOutbount to set
   */
  public static VertxOptions configCluster() {

    final VertxOptions options = new VertxOptions();

    if (System.getenv("GENNY_DEV") == null) {
      final ClusterManager mgr = new HazelcastClusterManager();
//      final ClusterManager mgr = new HazelcastClusterManager();
      options.setClusterManager(mgr);
      options.setEventBusOptions(configEBCluster());
      options.setClustered(true);
    } else {
      logger.info("Running DEV mode,");
      ClusterManager mgr = null;
      final Config hazelcastConfig = new Config();
      hazelcastConfig.getGroupConfig().setName( hostIP ).setPassword( "app1-pass" );

      mgr = new HazelcastClusterManager(hazelcastConfig); // standard docker
      System.out.println("Starting Clustered Vertx");
      options.setClusterManager(mgr);
      options.setBlockedThreadCheckInterval(200000000);
      options.setMaxEventLoopExecuteTime(Long.MAX_VALUE);
      options.setClustered(true);
    }
    return options;

  }
}
