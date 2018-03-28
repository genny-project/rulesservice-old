package life.genny.rules;

import java.io.File;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
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
import io.vavr.Tuple3;
import io.vertx.core.json.DecodeException;
import io.vertx.rxjava.core.Future;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.channels.EBCHandlers;
import life.genny.cluster.CurrentVtxCtx;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwandautils.KeycloakUtils;

public class RulesLoader {
	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	final static String qwandaApiUrl = System.getenv("REACT_APP_QWANDA_API_URL");
	final static String vertxUrl = System.getenv("REACT_APP_VERTX_URL");
	final static String hostIp = System.getenv("HOSTIP");

	private static Map<String, KieBase> kieBaseCache = null;
	static {
		setKieBaseCache(new HashMap<String, KieBase>());
	}

	static KieServices ks = KieServices.Factory.get();
	
	public static Set<String> realms = new HashSet<String>();

	/**
	 * @param vertx
	 * @return
	 */
	public static Future<Void> loadInitialRules(final String rulesDir) {
		System.out.println("Loading Rules and workflows!!!");
		final Future<Void> fut = Future.future();
		Vertx.currentContext().owner().executeBlocking(exec -> {
			setKieBaseCache(new HashMap<String, KieBase>()); // clear
			// List<Tuple2<String, String>> rules = processFile(rulesDir);
			// setupKieRules("rules", rules);

			List<Tuple3<String, String, String>> rules = processFileRealms("genny", rulesDir);

			realms = getRealms(rules);
			for (String realm : realms) {
				setupKieRules(realm, rules);
			}

			fut.complete();
		}, failed -> {
		});

		return fut;
	}
	
	/**
	 * @param vertx
	 * @return
	 */
	public static Future<Void> generateReports(final String rulesDir) {
		System.out.println("Generating Reports for all realms");
		final Future<Void> fut = Future.future();
		Vertx.currentContext().owner().executeBlocking(exec -> {

			for (String realm : realms) {
		        // Generate the reports
				System.out.println("---- Realm:"+realm+"----------");
		        EBCHandlers.initMsg("Event:GEN_REPORTS", realm,new QEventMessage("EVT_MSG","GEN_REPORTS"), CurrentVtxCtx.getCurrentCtx().getClusterVtx().eventBus());
			}
			fut.complete();
		}, failed -> {
		});

		return fut;
	}

	private static List<Tuple2<String, String>> processFile(String inputFileStr) {
		File file = new File(inputFileStr);
		String fileName = inputFileStr.replaceFirst(".*/(\\w+).*", "$1");
		String fileNameExt = inputFileStr.replaceFirst(".*/\\w+\\.(.*)", "$1");
		List<Tuple2<String, String>> rules = new ArrayList<Tuple2<String, String>>();

		if (!file.isFile()) {
			if (!fileName.startsWith("XX")) {
				final List<String> filesList = Vertx.currentContext().owner().fileSystem()
						.readDirBlocking(inputFileStr);

				for (final String dirFileStr : filesList) {
					List<Tuple2<String, String>> childRules = processFile(dirFileStr); // use directory name as
																						// rulegroup
					rules.addAll(childRules);
				}
			}
			return rules;
		} else {
			Buffer buf = Vertx.currentContext().owner().fileSystem().readFileBlocking(inputFileStr);
			try {
				if ((!fileName.startsWith("XX")) && (fileNameExt.equalsIgnoreCase("drl"))) { // ignore files that start
																								// with XX
					final String ruleText = buf.toString();

					Tuple2<String, String> rule = (Tuple.of(fileName + "." + fileNameExt, ruleText));
					System.out.println("Loading in Rule:" + rule._1 + " of " + inputFileStr);
					rules.add(rule);
				} else if ((!fileName.startsWith("XX")) && (fileNameExt.equalsIgnoreCase("bpmn"))) { // ignore files
																										// that start
																										// with XX
					final String bpmnText = buf.toString();

					Tuple2<String, String> bpmn = (Tuple.of(fileName + "." + fileNameExt, bpmnText));
					System.out.println("Loading in BPMN:" + bpmn._1 + " of " + inputFileStr);
					rules.add(bpmn);
				} else if ((!fileName.startsWith("XX")) && (fileNameExt.equalsIgnoreCase("xls"))) { // ignore files that
																									// start with XX
					final String xlsText = buf.toString();

					Tuple2<String, String> xls = (Tuple.of(fileName + "." + fileNameExt, xlsText));
					System.out.println("Loading in XLS:" + xls._1 + " of " + inputFileStr);
					rules.add(xls);
				}
				return rules;
			} catch (final DecodeException dE) {

			}

		}
		return null;
	}

	private static List<Tuple3<String, String, String>> processFileRealms(final String realm, String inputFileStr) {
		File file = new File(inputFileStr);
		String fileName = inputFileStr.replaceFirst(".*/(\\w+).*", "$1");
		String fileNameExt = inputFileStr.replaceFirst(".*/\\w+\\.(.*)", "$1");
		List<Tuple3<String, String, String>> rules = new ArrayList<Tuple3<String, String, String>>();

		if (!file.isFile()) { // DIRECTORY
			if (!fileName.startsWith("XX")) {
				String localRealm = realm;
				if (fileName.startsWith("prj_") || fileName.startsWith("PRJ_")) {
					localRealm = fileName.substring("prj_".length()).toLowerCase(); // extract realm name
				}
				final List<String> filesList = Vertx.currentContext().owner().fileSystem()
						.readDirBlocking(inputFileStr);

				for (final String dirFileStr : filesList) {
					List<Tuple3<String, String, String>> childRules = processFileRealms(localRealm, dirFileStr); // use
																													// directory
																													// name
																													// as
					// rulegroup
					rules.addAll(childRules);
				}
			}
			return rules;
		} else {
			Buffer buf = Vertx.currentContext().owner().fileSystem().readFileBlocking(inputFileStr);
			try {
				if ((!fileName.startsWith("XX")) && (fileNameExt.equalsIgnoreCase("drl"))) { // ignore files that start
																								// with XX
					final String ruleText = buf.toString();

					Tuple3<String, String, String> rule = (Tuple.of(realm, fileName + "." + fileNameExt, ruleText));
					System.out.println(realm + " Loading in Rule:" + rule._1 + " of " + inputFileStr);
					rules.add(rule);
				} else if ((!fileName.startsWith("XX")) && (fileNameExt.equalsIgnoreCase("bpmn"))) { // ignore files
																										// that start
																										// with XX
					final String bpmnText = buf.toString();

					Tuple3<String, String, String> bpmn = (Tuple.of(realm, fileName + "." + fileNameExt, bpmnText));
					System.out.println(realm + " Loading in BPMN:" + bpmn._1 + " of " + inputFileStr);
					rules.add(bpmn);
				} else if ((!fileName.startsWith("XX")) && (fileNameExt.equalsIgnoreCase("xls"))) { // ignore files that
																									// start with XX
					final String xlsText = buf.toString();

					Tuple3<String, String, String> xls = (Tuple.of(realm, fileName + "." + fileNameExt, xlsText));
					System.out.println(realm + " Loading in XLS:" + xls._1 + " of " + inputFileStr);
					rules.add(xls);
				}
				return rules;
			} catch (final DecodeException dE) {

			}

		}
		return null;
	}

	public static Set<String> getRealms(final List<Tuple3<String, String, String>> rules) {
		Set<String> realms = new HashSet<String>();

		for (Tuple3<String, String, String> rule : rules) {
			String realm = rule._1;
			realms.add(realm);
		}
		return realms;
	}

	public static void setupKieRules(final String realm, final List<Tuple3<String, String, String>> rules) {

		try {
			// load up the knowledge base
			final KieFileSystem kfs = ks.newKieFileSystem();

			// final String content =
			// new
			// String(Files.readAllBytes(Paths.get("src/main/resources/validateApplicant.drl")),
			// Charset.forName("UTF-8"));
			// System.out.println("Read New Rules set from File");

			for (final Tuple3<String, String, String> rule : rules) {
				if (rule._1.equalsIgnoreCase("genny") || rule._1.equalsIgnoreCase(realm)) {
					if (rule._2.endsWith(".drl")) {
						final String inMemoryDrlFileName = "src/main/resources/" + rule._2;
						kfs.write(inMemoryDrlFileName, ks.getResources().newReaderResource(new StringReader(rule._3))
								.setResourceType(ResourceType.DRL));
					}
					if (rule._2.endsWith(".bpmn")) {
						final String inMemoryDrlFileName = "src/main/resources/" + rule._2;
						kfs.write(inMemoryDrlFileName, ks.getResources().newReaderResource(new StringReader(rule._3))
								.setResourceType(ResourceType.BPMN2));
					} else if (rule._2.endsWith(".xls")) {
						final String inMemoryDrlFileName = "src/main/resources/" + rule._2;
						// Needs t handle byte[]
						// kfs.write(inMemoryDrlFileName, ks.getResources().newReaderResource(new
						// FileReader(rule._2))
						// .setResourceType(ResourceType.DTABLE));

					} else {
						final String inMemoryDrlFileName = "src/main/resources/" + rule._2;
						kfs.write(inMemoryDrlFileName, ks.getResources().newReaderResource(new StringReader(rule._3))
								.setResourceType(ResourceType.DRL));
					}
				}

			}

			final KieBuilder kieBuilder = ks.newKieBuilder(kfs).buildAll();
			if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
				System.out.println(kieBuilder.getResults().toString());
			}

			final KieContainer kContainer = ks.newKieContainer(kieBuilder.getKieModule().getReleaseId());
			final KieBaseConfiguration kbconf = ks.newKieBaseConfiguration();
			final KieBase kbase = kContainer.newKieBase(kbconf);

			System.out.println("Put rules KieBase into Custom Cache");
			if (getKieBaseCache().containsKey(realm)) {
				getKieBaseCache().remove(realm);
				System.out.println(realm + " removed");
			}
			getKieBaseCache().put(realm, kbase);
			System.out.println(realm + " rules installed");

		} catch (final Throwable t) {
			t.printStackTrace();
		}
	}

	// fact = gson.fromJson(msg.toString(), QEventMessage.class)
	public static void executeStatefull(final String rulesGroup, final EventBus bus,
			final List<Tuple2<String, Object>> globals, final List<Object> facts,
			final Map<String, String> keyValueMap) {

		try {
			KieSession kieSession = null;
			if (getKieBaseCache().get(rulesGroup) == null) {
				log.error("The rulesGroup kieBaseCache is null, not loaded "+rulesGroup);
				return;
			}
			kieSession = getKieBaseCache().get(rulesGroup).newKieSession();

			/*
			 * kSession.addEventListener(new DebugAgendaEventListener());
			 * kSession.addEventListener(new DebugRuleRuntimeEventListener());
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

			kieSession.insert(keyValueMap);

			// Set the focus on the Init agenda group to force proper startup
			kieSession.getAgenda().getAgendaGroup("Init").setFocus();

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

	public static Map<String, Object> getDecodedTokenMap(final String token) {
		Map<String, Object> decodedToken = null;
		if ((token != null) && (!token.isEmpty())) {
			// Getting decoded token in Hash Map from QwandaUtils
			decodedToken = KeycloakUtils.getJsonMap(token);
			/*
			 * Getting Prj Realm name from KeyCloakUtils - Just cheating the keycloak realm
			 * names as we can't add multiple realms in genny keyclaok as it is open-source
			 */
			final String projectRealm = KeycloakUtils.getPRJRealmFromDevEnv();
			if ((projectRealm != null) && (!projectRealm.isEmpty())) {
				decodedToken.put("realm", projectRealm);
			} else {
				// Extracting realm name from iss value
				final String realm = (decodedToken.get("iss").toString()
						.substring(decodedToken.get("iss").toString().lastIndexOf("/") + 1));
				// Adding realm name to the decoded token
				decodedToken.put("realm", realm);
			}
			//log.info("######  The realm name is:  #####  " + decodedToken.get("realm"));
			// Printing Decoded Token values
			// for (final Map.Entry entry : decodedToken.entrySet()) {
			// log.info(entry.getKey() + ", " + entry.getValue());
			// }
		}
		return decodedToken;
	}

	public static List<Tuple2<String, Object>> getStandardGlobals() {
		List<Tuple2<String, Object>> globals = new ArrayList<Tuple2<String, Object>>();
		String RESET = "\u001B[0m";
		String RED = "\u001B[31m";
		String GREEN = "\u001B[32m";
		String YELLOW = "\u001B[33m";
		String BLUE = "\u001B[34m";
		String PURPLE = "\u001B[35m";
		String CYAN = "\u001B[36m";
		String WHITE = "\u001B[37m";
		String BOLD = "\u001b[1m";

		globals.add(Tuple.of("LOG_RESET", RESET));
		globals.add(Tuple.of("LOG_RED", RED));
		globals.add(Tuple.of("LOG_GREEN", GREEN));
		globals.add(Tuple.of("LOG_YELLOW", YELLOW));
		globals.add(Tuple.of("LOG_BLUE", BLUE));
		globals.add(Tuple.of("LOG_PURPLE", PURPLE));
		globals.add(Tuple.of("LOG_CYAN", CYAN));
		globals.add(Tuple.of("LOG_WHITE", WHITE));
		globals.add(Tuple.of("LOG_BOLD", BOLD));
		globals.add(Tuple.of("REACT_APP_QWANDA_API_URL", qwandaApiUrl));
//		globals.add(Tuple.of("REACT_APP_VERTX_URL", vertxUrl));
//		globals.add(Tuple.of("KEYCLOAKIP", hostIp));
		return globals;
	}
}
