package life.genny.channels;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;



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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.qwanda.Answer;
import life.genny.qwanda.GPS;
import life.genny.qwanda.entity.User;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataGPSMessage;
import life.genny.qwanda.message.QEventAttributeValueChangeMessage;
import life.genny.qwanda.message.QEventBtnClickMessage;
import life.genny.qwanda.message.QEventLinkChangeMessage;
import life.genny.qwanda.message.QEventMessage;
import life.genny.qwanda.rule.Rule;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.KeycloakUtils;
import life.genny.rules.QRules;
import life.genny.rules.RulesLoader;

public class EBCHandlers {

//	protected static final Logger log = org.apache.logging.log4j.LogManager
//			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
	
	static Logger log = LoggerFactory.getLogger(EBCHandlers.class);

	static Map<String, Object> decodedToken = null;
	static Set<String> userRoles = null;
	private static Map<String, User> usersSession = new HashMap<String, User>();

	static String rulesDir = System.getenv("RULES_DIR");
	static String projectRealm = System.getenv("PROJECT_REALM");


	static String token;

	public static void registerHandlers(final EventBus eventBus) {
		EBConsumers.getFromCmds().subscribe(arg -> {
			JsonObject payload = processMessage("Service Command", arg);

			if ("CMD_RELOAD_RULES".equals(payload.getString("cmd_type"))) {
				if ("RELOAD_RULES_FROM_FILES".equals(payload.getString("code"))) {
					String rulesDir = payload.getString("rulesDir");
					RulesLoader.loadInitialRules(Vertx.currentContext().owner(), rulesDir);
				}
			}

		});

		EBConsumers.getFromEvents().subscribe(arg -> {
			JsonObject payload = processMessage("Event", arg);

			QEventMessage eventMsg = null;
			String evtMsg = "Event:";
			if (payload.getString("event_type").equals("EVT_ATTRIBUTE_VALUE_CHANGE")) {
				eventMsg = JsonUtils.fromJson(payload.toString(), QEventAttributeValueChangeMessage.class);
			} else if (payload.getString("event_type").equals("BTN_CLICK")) {
				eventMsg = JsonUtils.fromJson(payload.toString(), QEventBtnClickMessage.class);
			} else if (payload.getString("event_type").equals("EVT_LINK_CHANGE")) {
				eventMsg = JsonUtils.fromJson(payload.toString(), QEventLinkChangeMessage.class);
			} else {
				try {
					eventMsg = JsonUtils.fromJson(payload.toString(), QEventMessage.class);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			processMsg("Event:"+payload.getString("event_type"), eventMsg, eventBus, payload.getString("token"));

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
						dataMsg = JsonUtils.fromJson(payload.toString(), QDataAnswerMessage.class);
						processMsg("Data:"+dataMsg.getData_type(), dataMsg, eventBus, payload.getString("token"));
					} catch (com.google.gson.JsonSyntaxException e) {
						log.error("BAD Syntax converting to json from " + dataMsg);
						JsonObject json = new JsonObject(payload.toString());
						JsonObject answerData = json.getJsonObject("items");
						JsonArray jsonArray = new JsonArray();
						jsonArray.add(answerData);
						json.put("items", jsonArray);
						dataMsg = JsonUtils.fromJson(json.toString(), QDataAnswerMessage.class);
						processMsg("Data:"+dataMsg.getData_type(), dataMsg, eventBus, payload.getString("token"));
					}
				}
				else if (payload.getString("data_type").equals(GPS.class.getSimpleName())) {
						
					QDataGPSMessage dataGPSMsg = null;
					try {
						dataGPSMsg = JsonUtils.fromJson(payload.toString(), QDataGPSMessage.class);
						processMsg("GPS", dataGPSMsg, eventBus, payload.getString("token"));
					} 
					catch (com.google.gson.JsonSyntaxException e) {
						
						log.error("BAD Syntax converting to json from " + dataGPSMsg);
						JsonObject json = new JsonObject(payload.toString());
						JsonObject answerData = json.getJsonObject("items");
						JsonArray jsonArray = new JsonArray();
						jsonArray.add(answerData);
						json.put("items", jsonArray);
						dataGPSMsg = JsonUtils.fromJson(json.toString(), QDataGPSMessage.class);
						processMsg("GPS:"+dataGPSMsg.getData_type(), dataGPSMsg, eventBus, payload.getString("token"));
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
			Map<String,Object> adecodedTokenMap = RulesLoader.getDecodedTokenMap(token);
			Set<String> auserRoles = KeycloakUtils.getRoleSet(adecodedTokenMap.get("realm_access").toString());
			User userInSession = usersSession.get(adecodedTokenMap.get("preferred_username").toString());
			
			QRules qRules = new QRules(eventBus, token, adecodedTokenMap);
			
			String preferredUName = adecodedTokenMap.get("preferred_username").toString();
			String fullName = adecodedTokenMap.get("name").toString();
			String realm = adecodedTokenMap.get("realm").toString();
			String accessRoles = adecodedTokenMap.get("realm_access").toString();
			
			List<Tuple2<String, Object>> globals = RulesLoader.getStandardGlobals();
	

			List<Object> facts = new ArrayList<Object>();
			facts.add(qRules);
			facts.add(msg);
			facts.add(adecodedTokenMap);
			facts.add(auserRoles);
			if(userInSession!=null)
				facts.add(usersSession.get(preferredUName));
			else {
	            User currentUser = new User(preferredUName, fullName, realm, accessRoles);
				usersSession.put(adecodedTokenMap.get("preferred_username").toString(), currentUser);
				facts.add(currentUser);
			}
					

			Map<String, String> keyvalue = new HashMap<String, String>();
			keyvalue.put("token", token);

			System.out.println("FIRE RULES "+msgType);

			try {
				RulesLoader.executeStatefull("rules", eventBus, globals, facts, keyvalue);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			future.complete();
		}, res -> {
			if (res.succeeded()) {
				//System.out.println("Processed "+msgType+" Msg");
			}
		});

	}
   
	  public static class Message {

	        public static final int HELLO = 0;
	        public static final int GOODBYE = 1;
	        public static final int SEEYA = 2;

	        private String message;

	        private int status;

	        public String getMessage() {
	            return this.message;
	        }

	        public void setMessage(String message) {
	            this.message = message;
	        }

	        public int getStatus() {
	            return this.status;
	        }

	        public void setStatus(int status) {
	            this.status = status;
	        }

	    }

}
