package life.genny.rules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.core.json.DecodeException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.Future;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.channels.EBCHandlers;
import life.genny.cluster.ClusterConfig;

public class RulesLoader {
	  private static final Logger logger = LoggerFactory.getLogger(EBCHandlers.class);

	  private static Map<String, KieBase> kieBaseCache = null;
	  static {
	    setKieBaseCache(new HashMap<String, KieBase>());
	  }


	  static KieServices ks = KieServices.Factory.get();


	  /**
	   * @param vertx
	   * @return
	   */
	  public static Future<Void> loadInitialRules(final Vertx vertx) {
	    final Future<Void> fut = Future.future();
	    vertx.executeBlocking(exec -> {
	      final List<String> filesList = vertx.fileSystem().readDirBlocking("rules");

	      for (final String dirFileStr : filesList) {
	        final String fileStr = new File(dirFileStr).getName();;
	          vertx.fileSystem().readFile(dirFileStr, d -> {
	            if (!d.failed()) {
	              try {
	                System.out.println("Loading in Rule [" + fileStr + "]");
	                final String ruleText =
	                    d.result().toString();
	              	String rulesGroup = "GRP_RULES_TEST";
	              	List<Tuple2<String,String>> rules = new ArrayList<Tuple2<String,String>>();
	              	rules.add(Tuple.of(fileStr,ruleText));

	                  setupKieRules(rulesGroup,
	                	      rules);
	         
	              } catch (final DecodeException dE) {

	              }
	            } else {
	              System.err.println("Error reading  file!"+fileStr);
	            }
	          });
	        
	      }
	      fut.complete();
	    }, res -> {
	    });
	    return fut;
	  }

	  private static void readFilenamesFromDirectory(final String rootFilePath,
	      final Map<String, String> keycloakJsonMap) {
	    final File folder = new File(rootFilePath);
	    final File[] listOfFiles = folder.listFiles();

	    for (int i = 0; i < listOfFiles.length; i++) {
	      if (listOfFiles[i].isFile()) {
	        System.out.println("File " + listOfFiles[i].getName());
	        try {
	          String keycloakJsonText = getFileAsText(listOfFiles[i]);
	          // Handle case where dev is in place with localhost
	          final String localIP = System.getenv("HOSTIP");
	          keycloakJsonText = keycloakJsonText.replaceAll("localhost", localIP);
	          keycloakJsonMap.put(listOfFiles[i].getName(), keycloakJsonText);
	        } catch (final IOException e) {
	          // TODO Auto-generated catch block
	          e.printStackTrace();
	        }

	      } else if (listOfFiles[i].isDirectory()) {
	        System.out.println("Directory " + listOfFiles[i].getName());
	        readFilenamesFromDirectory(listOfFiles[i].getName(), keycloakJsonMap);
	      }
	    }
	  }

	  private static String getFileAsText(final File file) throws IOException {
	    final BufferedReader in = new BufferedReader(new FileReader(file));
	    String ret = "";
	    String line = null;
	    while ((line = in.readLine()) != null) {
	      ret += line;
	    }
	    in.close();

	    return ret;
	  }
	  

	  public static void setupKieRules(final String rulesGroup,
	      final List<Tuple2<String, String>> rules) {

		     System.out.println("***** Setting up RulesGroup: "+rulesGroup);
		     
	    try {
	      // load up the knowledge base
	      final KieFileSystem kfs = ks.newKieFileSystem();

	      // final String content =
	      // new String(Files.readAllBytes(Paths.get("src/main/resources/validateApplicant.drl")),
	      // Charset.forName("UTF-8"));
	      // System.out.println("Read New Rules set from File");

	      for (final Tuple2<String, String> rule : rules) {
	        final String inMemoryDrlFileName = "src/main/resources/" + rule._1 + ".drl";
	        kfs.write(inMemoryDrlFileName, ks.getResources()
	            .newReaderResource(new StringReader(rule._2)).setResourceType(ResourceType.DRL));

	      }

	      final KieBuilder kieBuilder = ks.newKieBuilder(kfs).buildAll();
	      if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
	        System.out.println(kieBuilder.getResults().toString());
	      }
	      
	      final KieContainer kContainer = ks.newKieContainer(kieBuilder.getKieModule().getReleaseId());
	      final KieBaseConfiguration kbconf = ks.newKieBaseConfiguration();
	      final KieBase kbase = kContainer.newKieBase(kbconf);



	      System.out.println("Put rules KieBase into Custom Cache");
	     if ( getKieBaseCache().containsKey(rulesGroup)) {
	    	 getKieBaseCache().remove(rulesGroup);
	    	 System.out.println(rulesGroup+" removed");
	     }
	      getKieBaseCache().put(rulesGroup, kbase);
	      System.out.println(rulesGroup+" installed");

	    } catch (final Throwable t) {
	      t.printStackTrace();
	    }
	  }

	  // fact = gson.fromJson(msg.toString(), QEventMessage.class)
	  public static void executeStatefull(final String rulesGroup, final EventBus bus,
	      final List<Tuple2<String, Object>> globals, final List<Object> facts,
	      final Map<String, String> keyvalue) {

	    try {
	      KieSession kieSession = getKieBaseCache().get(rulesGroup).newKieSession();
	      /*
	       * kSession.addEventListener(new DebugAgendaEventListener()); kSession.addEventListener(new
	       * DebugRuleRuntimeEventListener());
	       */


	      if (bus != null) { // assist testing
	        kieSession.insert(bus);
	      }

	      // Load globals
	      for (final Tuple2<String, Object> t : globals) {
	        kieSession.setGlobal(t._1, t._2);
	      }
	      for (final Object fact : facts) {
	        kieSession.insert(fact);
	      }
	      kieSession.insert(keyvalue);

	      kieSession.fireAllRules();

	      kieSession.dispose();
	    } catch (final Throwable t) {
	      t.printStackTrace();
	    }
	  }

	  
	  public static Map<String, KieBase> getKieBaseCache() {
		    return kieBaseCache;
		  }

		  public static void setKieBaseCache(Map<String, KieBase> kieBaseCache) {
		    RulesLoader.kieBaseCache = kieBaseCache;
		  }
}
