package life.genny.rules;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;

import org.kie.api.executor.CommandContext;
import org.kie.api.executor.ExecutionResults;

import org.kie.api.executor.RequestInfo;
import org.kie.api.executor.STATUS;
import org.kie.api.runtime.query.QueryContext;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.conf.TimedRuleExecutionOption;
import org.kie.api.runtime.conf.TimerJobFactoryOption;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.utils.KieHelper;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vertx.core.json.DecodeException;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.qwanda.Link;
import life.genny.qwanda.entity.User;
import life.genny.qwanda.message.QEventLinkChangeMessage;
import life.genny.qwandautils.GennySettings;
import life.genny.qwandautils.KeycloakUtils;
import life.genny.qwandautils.SecurityUtils;

public class RuleTest {
	private static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	static KieServices ks = KieServices.Factory.get();
	static KieContainer kContainer;
	static KieSession kSession;
	
	  

	@Before
	public void init() {
		
		// Display Drools version
		
		if (checkAllRules()) {
		
		RulesLoader.setKieBaseCache(new HashMap<String, KieBase>()); // clear


		List<Tuple3<String, String, String>> rules = RulesLoader.processFileRealms("genny", "src/main/resources/rules");

		Set<String> realms = RulesLoader.getRealms(rules);
		realms.stream().forEach(System.out::println);
		RulesLoader.setupKieRules("genny", rules); // run genny rules first
		} else {
			System.out.println("Errors in rules");
		}

	}
	
	/*@Test
	public void jwtTest()
	{
		Map<String,Object> adecodedTokenMap = new HashMap<String,Object>();
		adecodedTokenMap.put("preferred_username", "user1");
		adecodedTokenMap.put("name", "Ginger Meggs");
		adecodedTokenMap.put("realm", "genny");
		adecodedTokenMap.put("realm_access", "[user]");

		String jwtToken=null;

			jwtToken = SecurityUtils.createJwt("ABBCD", "Genny Project", "Test JWT", 100000, "IamASecret",adecodedTokenMap);
			System.out.println("jwtToken = "+jwtToken);
	}*/
	
	@Test
	public void calendarTest()
	{
		//in this case I'm using a DailyCalendar but you can use whatever implementation of Calendar you want
//		org.quartz.impl.calendar.DailyCalendar businessHours = new org.quartz.impl.calendar.DailyCalendar("business-hours", 8, 0, 0, 0, 16, 0, 0, 0);
//		businessHours.setInvertTimeRange(true);
//
//		//convert the calendar into a org.drools.time.Calendar
//		org.drools.time.Calendar businessHoursCalendar = QuartzHelper.quartzCalendarAdapter(businessHours);
//
//		//Register the calendar in the session with a name. You must use this name in your rules.
//		ksession.getCalendars().set( "business-hours", businessHoursCalendar );
//
//		Then in your rule you have to write something like this:
//
//		rule "Rule X"
//		    calendars "business-hours"
//		when
//		    ...
//		then
//		    ...
//		end
	}
	
	/*@Test
	public void fireTimerTest()
	{
		System.out.println("Firing Timer Test");
		List<Tuple2<String, Object>> globals = new ArrayList<Tuple2<String, Object>>();
		Map<String, String> keyValueMap = new HashMap<String, String>();
		
		globals = RulesLoader.getStandardGlobals();
		
		Map<String,Object> adecodedTokenMap = new HashMap<String,Object>();
		adecodedTokenMap.put("preferred_username", "user1");
		adecodedTokenMap.put("name", "Ginger Meggs");
		adecodedTokenMap.put("realm", "genny");
		adecodedTokenMap.put("realm_access", "[user,dev]");
	
		
		
		Set<String> auserRoles = KeycloakUtils.getRoleSet(adecodedTokenMap.get("realm_access").toString());

		String realm = adecodedTokenMap.get("realm").toString();
		if ("genny".equalsIgnoreCase(realm)) {
			realm = GennySettings.mainrealm;
			adecodedTokenMap.put("realm", GennySettings.mainrealm);
		}
		String jwtToken=null;

			jwtToken  = SecurityUtils.createJwt("ABBCD", "Genny Project", "Test JWT", 100000, "IamASecret",adecodedTokenMap);

			QRules qRules = new QRules(null, jwtToken, adecodedTokenMap);
			qRules.set("realm", realm);


			List<Object> facts = new ArrayList<Object>();
			facts.add(qRules);

			facts.add(adecodedTokenMap);
			facts.add(auserRoles);

			
			try {
			//	 KieSession  kieSession = null;
					
				KieSessionConfiguration ksconf = KieServices.Factory.get().newKieSessionConfiguration();
				ksconf.setOption( TimedRuleExecutionOption.YES );
				ksconf.setOption(TimerJobFactoryOption.get("trackable"));
				//ksconf.setOption(ClockTypeOption.get("realtime"));

				final KieSession kieSession = RulesLoader.getKieBaseCache().get("genny").newKieSession(ksconf, null);

	
				// Load globals
				for (final Tuple2<String, Object> t : globals) {
					try {
						kieSession.setGlobal(t._1, t._2);
					} catch (java.lang.RuntimeException e) {
						log.info(e.getMessage());
					}
				}
				for (final Object fact : facts) {
					kieSession.insert(fact);
				}

				kieSession.insert(keyValueMap);

				// Set the focus on the Init agenda group to force proper startup
			//	kieSession.getAgenda().getAgendaGroup("Init").setFocus();

			//	  SessionPseudoClock  clock = kieSession.getSessionClock();
				  // insert some facts ... 
			//	  clock.advanceTime(1, TimeUnit.HOURS);
				  
		//		int rulesFired = kieSession.fireAllRules();
				
		        ExecutorService threadPool = Executors.newFixedThreadPool(4);
		     //   ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

//		        Runnable task = () -> {
//		            System.out.println("Executing Task At " + System.nanoTime());
//		          };
		     //   scheduledExecutorService.scheduleAtFixedRate(task, 0,2, TimeUnit.SECONDS);
		        class DoThread implements Runnable {

		            private   KieSession  kSession = null;

		            DoThread(KieSession kSession) {
		                this.kSession = kSession;
		            }

		            public void run() {
		                kSession.fireAllRules();
		            }
		        }

		        class DAEL extends DefaultAgendaEventListener
		        {
		        	public   KieSession  kSession = null;

		            DAEL(KieSession kSession) {
		            	super();
		                this.kSession = kSession;
		            }

		        }
		        
				kieSession.addEventListener(new DAEL(kieSession) {

		            @Override

		            public void afterMatchFired(final AfterMatchFiredEvent event) {

		                final RuleTerminalNodeLeftTuple match = (RuleTerminalNodeLeftTuple) event.getMatch();

		                final RuleTerminalNode sink = (RuleTerminalNode) match.getTupleSink();

		                if (sink.getEnabledDeclarations() != null && sink.getEnabledDeclarations().length > 0) {

		                    threadPool.submit(new DoThread(this.kSession) {

		                    });

		                } else {
		                	threadPool.shutdown();
		                }

		            }

		        });
//
//				threadPool.shutdown();
//				final KieSession threadSession = kieSession;
//				 new Thread(new Runnable() {
//			            public void run() {
//			                kieSession.fireUntilHalt();
//			            }
//			        }).start();
				
		//		 Thread.sleep(120000);
				 
		//		System.out.println("Fired "+rulesFired+" rules");
				kieSession.fireAllRules();
				while (!threadPool.isShutdown()) {
					Thread.sleep(5000);
				}
				 System.out.println("finished rules");
		//		kieSession.dispose();
			} catch (final Throwable t) {
				t.printStackTrace();
			}


	}*/

	// @Test
	public void msgTest() {
		String test = new String("hello");
		kSession.insert(test);
		kSession.fireAllRules();
	}

	// @Test
	public void bucket_view_drag_drop_test() {
		System.out.println("Hello");
		final Map<String, String> keyValue = new HashMap<String, String>();
		// final Keycloak kc = KeycloakBuilder.builder()
		// .serverUrl("http://10.1.120.89:8180/auth")
		// .realm("genny")
		// .username("user1")
		// .password("password1")
		// .clientId("curl")
		// .resteasyClient(new ResteasyClientBuilder().connectionPoolSize(10).build())
		// .build();
		// String token = kc.tokenManager().getAccessToken().getToken();
		// String token1 = kc.tokenManager().getAccessTokenString();
		// System.out.println("The token is: "+token);
		// System.out.println("The token is: "+token1);

		Link link = new Link("GRP_QUOTES", "GRP_COMPLETED", "BEG_0000002", "LNK_CORE", null);
		QEventLinkChangeMessage evtMsg = new QEventLinkChangeMessage(link, null, "TEST");
		keyValue.put("token", "DUMB TOKEN");
		kSession.insert(keyValue);
		kSession.insert(evtMsg);
		kSession.fireAllRules();

	}


	public Boolean checkAllRules() {
		Boolean allCompiled = readFilenamesFromDirectory("src/main/resources/rules");
		if (!allCompiled) {
			// This forces us to fix Rules!
			assertTrue("Drools Compile Error!", false);
		} 
		return allCompiled;
	}

	private Boolean readFilenamesFromDirectory(String rootFilePath) {
		Boolean compileOk = true;
		final File folder = new File(rootFilePath);
		final File[] listOfFiles = folder.listFiles();
		String fileName = rootFilePath.replaceFirst(".*/(\\w+).*", "$1");
		String fileNameExt = rootFilePath.replaceFirst(".*/\\w+\\.(.*)", "$1");

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				System.out.println("File " + listOfFiles[i].getName());
					if ((!listOfFiles[i].getName().startsWith("XX")) && (listOfFiles[i].getName().equalsIgnoreCase("drl"))) { // ignore files that start
						// with XX

					try {
						String ruleText = getFileAsText(listOfFiles[i]);
						KieHelper kieHelper = new KieHelper();
						kieHelper.addContent(ruleText, ResourceType.DRL);
						
						Results results = kieHelper.verify();
						for (Message message : results.getMessages()) {
							log.error(">> Message ({}): {}", message.getLevel(), message.getText());
							compileOk = false;
							assertTrue("Drools Compile Error in " + listOfFiles[i].getName(), false);
						}

					} catch (final IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if ((!listOfFiles[i].getName().startsWith("XX")) && (listOfFiles[i].getName().equalsIgnoreCase("bpmn"))) { // ignore files that start
					// with XX

				try {
					String ruleText = getFileAsText(listOfFiles[i]);
					KieHelper kieHelper = new KieHelper();
					kieHelper.addContent(ruleText, ResourceType.BPMN2);
					Results results = kieHelper.verify();
					for (Message message : results.getMessages()) {
						log.error(">> Message ({}): {}", message.getLevel(), message.getText());
						compileOk = false;
						assertTrue("BPMN Compile Error in " + listOfFiles[i].getName(), false);
					}

				} catch (final IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			} else if (listOfFiles[i].isDirectory()) {
				if (!listOfFiles[i].getName().startsWith("XX")) {

				System.out.println("Directory " + listOfFiles[i].getName());
				readFilenamesFromDirectory(rootFilePath + "/" + listOfFiles[i].getName());
				}
			}
		}
		return compileOk;
	}

	private static List<Tuple2<String, String>> processFile(final Vertx vertx, String inputFileStr) {
		File file = new File(inputFileStr);
		String fileName = inputFileStr.replaceFirst(".*/(\\w+).*", "$1");
		String fileNameExt = inputFileStr.replaceFirst(".*/\\w+\\.(.*)", "$1");
		List<Tuple2<String, String>> rules = new ArrayList<Tuple2<String, String>>();

		if (!file.isFile()) {
			final List<String> filesList = vertx.fileSystem().readDirBlocking(inputFileStr);

			for (final String dirFileStr : filesList) {
				List<Tuple2<String, String>> childRules = processFile(vertx, dirFileStr); // use directory name as
																							// rulegroup
				rules.addAll(childRules);
			}
			return rules;
		} else {
			Buffer buf = vertx.fileSystem().readFileBlocking(inputFileStr);
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
				}
				return rules;
			} catch (final DecodeException dE) {

			}

		}
		return null;
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
}
