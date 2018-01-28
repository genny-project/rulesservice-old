package life.genny.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import life.genny.qwanda.Link;
import life.genny.qwanda.message.QEventLinkChangeMessage;
import life.genny.qwanda.message.QEventMessage;

public class RuleTest {
	private static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	static KieServices ks = KieServices.Factory.get();
	static KieContainer kContainer;
	static KieSession kSession;

	@Before
	public void init() {
		kContainer = ks.getKieClasspathContainer();
		kSession = kContainer.newKieSession("ksession-rules");

	}

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

	@Test
	public void checkAllRules() {
		Boolean allCompiled = readFilenamesFromDirectory("src/main/resources/rules");
		if (!allCompiled) {
			// This forces us to fix Rules!
			assertTrue("Drools Compile Error!", false);
		}
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
				System.out.println("Directory " + listOfFiles[i].getName());
				readFilenamesFromDirectory(rootFilePath + "/" + listOfFiles[i].getName());
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
