package life.genny.channels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.vavr.Tuple2;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.qwanda.Answer;
import life.genny.qwanda.Ask;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataAskMessage;
import life.genny.qwanda.message.QDataRuleMessage;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwanda.rule.Rule;
import life.genny.qwandautils.KeycloakUtils;

public class EBCHandlers {

  private static final Logger logger = LoggerFactory.getLogger(EBCHandlers.class);

  static Map<String, KieBase> kieBaseCache = null;
  static {
    kieBaseCache = new HashMap<String, KieBase>();
  }

  static Gson gson = new GsonBuilder()
      .registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
        @Override
        public LocalDateTime deserialize(final JsonElement json, final Type type,
            final JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
          return ZonedDateTime.parse(json.getAsJsonPrimitive().getAsString()).toLocalDateTime();
        }

        public JsonElement serialize(final LocalDateTime date, final Type typeOfSrc,
            final JsonSerializationContext context) {
          return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)); // "yyyy-mm-dd"
        }
      }).create();

  static KieServices ks = KieServices.Factory.get();
  static KieContainer kContainer;
  final static String qwandaApiUrl = System.getenv("REACT_APP_QWANDA_API_URL");
  final static String vertxUrl = System.getenv("REACT_APP_VERTX_URL");
  final static String hostIp = System.getenv("HOSTIP");
  static KieSession kSession;
  static String token;



  public static void registerHandlers(final EventBus eventBus) {
    EBConsumers.getFromEvents().subscribe(arg -> {
      logger.info("Received EVENT :" + (System.getenv("PROJECT_REALM") == null ? "tokenRealm"
          : System.getenv("PROJECT_REALM")));
      if (System.getenv("PROJECT_REALM") != null) {
        System.out
            .println("###########    The project realm from system env is : ################    "
                + System.getenv("PROJECT_REALM"));
      }

      final JsonObject payload = new JsonObject(arg.body().toString());
      final String token = payload.getString("token");
      System.out.println(payload);
      final QEventMessage eventMsg = gson.fromJson(payload.toString(), QEventMessage.class);
      processEvent(eventMsg, eventBus, token);
    });

    EBConsumers.getFromData().subscribe(arg -> {

      logger.info("Received DATA :" + (System.getenv("PROJECT_REALM") == null ? "tokenRealm"
          : System.getenv("PROJECT_REALM")));
      final JsonObject payload = new JsonObject(arg.body().toString());
      payload.getString("token");
      System.out.println(payload);

      // final JsonObject a = Buffer.buffer(payload.toString()).toJsonObject();
      if (payload.getString("msg_type").equalsIgnoreCase("DATA_MSG")) {
        if (payload.getString("data_type").equals(Rule.class.getSimpleName())) {
          gson.fromJson(payload.toString(), QDataRuleMessage.class);
        } else {
          allRules(payload, eventBus);
        }
      }
    });
  }


  public static void processEvent(final QEventMessage eventMsg, final EventBus bus,
      final String token) {
    Vertx.vertx().executeBlocking(future -> {
      // kSession = createSession(bus, token);

      // Getting decoded token in Hash Map from QwandaUtils
      final Map<String, Object> decodedToken = KeycloakUtils.getJsonMap(token);
      // Getting Set of User Roles from QwandaUtils
      final Set<String> userRoles =
          KeycloakUtils.getRoleSet(decodedToken.get("realm_access").toString());

      System.out.println("The Roles value are: " + userRoles.toString());

      /*
       * Getting Prj Realm name from KeyCloakUtils - Just cheating the keycloak realm names as we
       * can't add multiple realms in genny keyclaok as it is open-source
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
      System.out.println("######  The realm name is:  #####  " + decodedToken.get("realm"));
      // Printing Decoded Token values
      for (final Map.Entry entry : decodedToken.entrySet()) {
        System.out.println(entry.getKey() + ", " + entry.getValue());
      }

      try {
        kSession = createSession(bus, token, decodedToken, userRoles);
        kSession.insert(eventMsg);
        kSession.fireAllRules();

      } catch (final Throwable t) {
        t.printStackTrace();
      }
      future.complete();
    }, res -> {
      if (res.succeeded()) {
        System.out.println("ProcessedEvent");
      }
    });

  }

  public static KieSession createSession(final EventBus bus, final String token,
      final Map<String, Object> tokenDecoded, final Set<String> roles) {
    // ks = KieServices.Factory.get();
    if (ks == null) {
      System.out.println("ks is NULL!!!");
    }
    kContainer = ks.getKieClasspathContainer();
    final KieSession kSession = kContainer.newKieSession("ksession-rules");

    kSession.setGlobal("REACT_APP_QWANDA_API_URL", qwandaApiUrl);
    kSession.setGlobal("REACT_APP_VERTX_URL", vertxUrl);
    kSession.setGlobal("KEYCLOAKIP", hostIp);
    final Map<String, String> keyValue = new HashMap<String, String>();
    keyValue.put("token", token);
    kSession.insert(keyValue);
    kSession.insert(tokenDecoded);
    kSession.insert(roles);
    kSession.insert(bus);
    return kSession;
  }

  public static void allRules(final JsonObject msg, final EventBus bus) {

    Vertx.vertx().executeBlocking(future -> {
      try {
        // load up the knowledge base
        final KieContainer kContainer = ks.getKieClasspathContainer();

        final KieSession kSession = kContainer.newKieSession("ksession-rules");
        kSession.insert(bus);

        kSession.setGlobal("REACT_APP_QWANDA_API_URL", qwandaApiUrl);
        kSession.setGlobal("REACT_APP_VERTX_URL", vertxUrl);
        kSession.setGlobal("KEYCLOAKIP", hostIp);
        final Map<String, String> keyValue = new HashMap<String, String>();
        final String token = msg.getString("token");
        keyValue.put("token", token);
        kSession.insert(keyValue);

        kSession.getGlobals();
        // System.out.println("Globals:" + globals.getGlobalKeys());
        if (msg.getString("msg_type").equalsIgnoreCase("EVT_MSG")) {

          kSession.insert(gson.fromJson(msg.toString(), QEventMessage.class));
          kSession.fireAllRules();
          System.out.println("EVNT MSG FIRED: ");

        } else if (msg.getString("msg_type").equalsIgnoreCase("DATA_MSG")) {
          if (msg.getString("data_type").equals(Answer.class.getSimpleName())) {
            final String msgString = msg.toString();
            System.out.println(msgString);
            kSession.insert(gson.fromJson(msg.toString(), QDataAnswerMessage.class));
          } else if (msg.getString("data_type").equals(Ask.class.getSimpleName())) {
            kSession.insert(gson.fromJson(msg.toString(), QDataAskMessage.class));
          }
          kSession.fireAllRules();
          System.out.println("DATA MSG FIRED: ");
        }
      } catch (final Throwable t) {
        t.printStackTrace();
      }
      future.complete();
    }, res -> {
      if (res.succeeded()) {
      }
    });

  }


  public static void setupKieSession(final String rulesGroup,
      final List<Tuple2<String, String>> rules, final EventBus bus,
      final List<Tuple2<String, Object>> globals) {

    try {
      KieSession kieSession = null;
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
      kieSession = kbase.newKieSession();

      kieSession.insert(bus);

      // Load globals
      for (final Tuple2<String, Object> t : globals) {
        kieSession.setGlobal(t._1, t._2);
      }

      System.out.println("Put rules KieBase into Custom Cache");
      kieBaseCache.put(rulesGroup, kbase);

    } catch (final Throwable t) {
      t.printStackTrace();
    }
  }

  // fact = gson.fromJson(msg.toString(), QEventMessage.class)
  public static void executeStatefull(final String rulesGroup, final List<Object> facts) {

    try {
      final KieSession kieSession = kieBaseCache.get(rulesGroup).newKieSession();
      /*
       * kSession.addEventListener(new DebugAgendaEventListener()); kSession.addEventListener(new
       * DebugRuleRuntimeEventListener());
       */
      for (final Object fact : facts) {
        kieSession.insert(fact);
      }

      kieSession.fireAllRules();

      kieSession.dispose();
    } catch (final Throwable t) {
      t.printStackTrace();
    }
  }
}
