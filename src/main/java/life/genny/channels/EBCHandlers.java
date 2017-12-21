package life.genny.channels;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.qwanda.Answer;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QEventAttributeValueChangeMessage;
import life.genny.qwanda.message.QEventLinkChangeMessage;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwanda.rule.Rule;
import life.genny.qwandautils.KeycloakUtils;
import life.genny.rules.RulesLoader;

import life.genny.facts.*;

public class EBCHandlers {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	static Map<String, Object> decodedToken = null;
	static Set<String> userRoles = null;
	private static Map<String, User> usersSession = new HashMap<String, User>();

	static String rulesDir = System.getenv("RULES_DIR");
	static String projectRealm = System.getenv("PROJECT_REALM");

	static Gson gson = new GsonBuilder()
			.registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
				@Override
				public LocalDateTime deserialize(final JsonElement json, final Type type,
						final JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
					return LocalDateTime.parse(json.getAsJsonPrimitive().getAsString(),
							DateTimeFormatter.ISO_LOCAL_DATE_TIME);
				}

				public JsonElement serialize(final LocalDateTime date, final Type typeOfSrc,
						final JsonSerializationContext context) {
					return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)); // "yyyy-mm-dd"
				}
			}).create();

	static String token;

	public static void registerHandlers(final EventBus eventBus) {
		EBConsumers.getFromCmds().subscribe(arg -> {
			JsonObject payload = processMessage("Command", arg);

			if ("CMD_RELOAD_RULES".equals(payload.getString("cmd_type"))) {
				if ("RELOAD_RULES_FROM_FILES".equals(payload.getString("code"))) {
					String rulesDir = payload.getString("rulesDir");
					RulesLoader.loadInitialRules(Vertx.vertx(), rulesDir);
				}
			}

		});

		EBConsumers.getFromEvents().subscribe(arg -> {
			JsonObject payload = processMessage("Event", arg);

			QEventMessage eventMsg = null;
			if (payload.getString("event_type").equals("EVT_ATTRIBUTE_VALUE_CHANGE")) {
				eventMsg = gson.fromJson(payload.toString(), QEventAttributeValueChangeMessage.class);
			} else if (payload.getString("event_type").equals("EVT_LINK_CHANGE")) {
				eventMsg = gson.fromJson(payload.toString(), QEventLinkChangeMessage.class);
			} else {
				eventMsg = gson.fromJson(payload.toString(), QEventMessage.class);
			}
			processMsg("Event", eventMsg, eventBus, payload.getString("token"));

		});

		EBConsumers.getFromData().subscribe(arg -> {

			JsonObject payload = processMessage("Data", arg);

			if (payload.getString("msg_type").equalsIgnoreCase("DATA_MSG")) { // should always be data if coming through
																				// this channel
				QDataAnswerMessage dataMsg = null;

				// Is it a Rule?
				if (payload.getString("data_type").equals(Rule.class.getSimpleName())) {
					JsonArray ja = payload.getJsonArray("items");
					String ruleText = ja.getJsonObject(0).getString("rule");
					String ruleCode = ja.getJsonObject(0).getString("code");
					// QDataRuleMessage ruleMsg = gson3.fromJson(json, QDataRuleMessage.class);
					System.out.println("Incoming Rule :" + ruleText);
					if (rulesDir == null) {
						rulesDir = "rules";
					}

					String rulesGroup = rulesDir;
					List<Tuple2<String, String>> rules = new ArrayList<Tuple2<String, String>>();
					rules.add(Tuple.of(ruleCode, ruleText));

					RulesLoader.setupKieRules(rulesGroup, rules);
				} else if (payload.getString("data_type").equals(Answer.class.getSimpleName())) {
					try {
						dataMsg = gson.fromJson(payload.toString(), QDataAnswerMessage.class);
						processMsg("Data", dataMsg, eventBus, payload.getString("token"));
					} catch (com.google.gson.JsonSyntaxException e) {
						log.error("BAD Syntax converting to json from " + dataMsg);
						JsonObject json = new JsonObject(payload.toString());
						JsonObject answerData = json.getJsonObject("items");
						JsonArray jsonArray = new JsonArray();
						jsonArray.add(answerData);
						json.put("items", jsonArray);
						dataMsg = gson.fromJson(json.toString(), QDataAnswerMessage.class);
						processMsg("Data", dataMsg, eventBus, payload.getString("token"));
					}
				}
			}
		});
	}

	private static JsonObject processMessage(String messageType, io.vertx.rxjava.core.eventbus.Message<Object> arg) {
		log.info("EVENT-BUS >> " + messageType.toUpperCase() + " :" + projectRealm);

		final JsonObject payload = new JsonObject(arg.body().toString());
		return payload;
	}

	public static void processMsg(final String msgType,final Object msg, final EventBus eventBus, final String token) {
		Vertx.vertx().executeBlocking(future -> {
			Map<String,Object> adecodedToken = RulesLoader.getDecodedTokenMap(token);
			Set<String> auserRoles = KeycloakUtils.getRoleSet(adecodedToken.get("realm_access").toString());
			User userInSession = usersSession.get(adecodedToken.get("preferred_username").toString());
			
			String preferredUName = adecodedToken.get("preferred_username").toString();
			String fullName = adecodedToken.get("name").toString();
			String realm = adecodedToken.get("realm").toString();
			String accessRoles = adecodedToken.get("realm_access").toString();
			
			List<Tuple2<String, Object>> globals = RulesLoader.getStandardGlobals();

			List<Object> facts = new ArrayList<Object>();
			facts.add(msg);
			facts.add(adecodedToken);
			facts.add(auserRoles);
			if(userInSession!=null)
				facts.add(usersSession.get(preferredUName));
			else {
	            User currentUser = new User(preferredUName, fullName, realm, accessRoles);
	            //currentUser.setIsAvailable(QwandaUtils.checkUserTokenExists(qwandaServiceUrl,tokenString));
				usersSession.put(adecodedToken.get("preferred_username").toString(), currentUser);
				facts.add(currentUser);
			}
					

			Map<String, String> keyvalue = new HashMap<String, String>();
			keyvalue.put("token", token);

			try {
				RulesLoader.executeStatefull("rules", eventBus, globals, facts, keyvalue);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			future.complete();
		}, res -> {
			if (res.succeeded()) {
				System.out.println("Processed "+msgType+" Msg");
			}
		});

	}

}
