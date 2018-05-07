package life.genny.channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.channel.Consumer;
import life.genny.qwanda.Answer;
import life.genny.qwanda.GPS;
import life.genny.qwanda.entity.User;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataPaymentsCallbackMessage;
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


	static String token;

	public static void registerHandlers(final EventBus eventBus) {
		Consumer.getFromCmds().subscribe(arg -> {
			JsonObject payload = processMessage("Service Command", arg);

			if ("CMD_RELOAD_RULES".equals(payload.getString("cmd_type"))) {
				if ("RELOAD_RULES_FROM_FILES".equals(payload.getString("code"))) {
					String rulesDir = payload.getString("rulesDir");
					RulesLoader.loadInitialRules( rulesDir);
				}
			}

		});

		Consumer.getFromEvents().subscribe(arg -> {
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
				} catch (NoClassDefFoundError e) {
					log.error("No class def found ["+payload.toString()+"]");
				}
			}
			processMsg("Event:"+payload.getString("event_type"), payload.getString("ruleGroup"),eventMsg, eventBus, payload.getString("token"));

		});

		Consumer.getFromData().subscribe(arg -> {

			JsonObject payload = processMessage("Data", arg);

			if (payload.getString("msg_type").equalsIgnoreCase("DATA_MSG")) { // should always be data if coming through
																				// this channel
				QDataAnswerMessage dataMsg = null;

				// Is it a Rule?
				if (payload.getString("data_type").equals(Rule.class.getSimpleName())) {
					JsonArray ja = payload.getJsonArray("items");
					String ruleGroup = ja.getJsonObject(0).getString("ruleGroup");
					String ruleText = ja.getJsonObject(0).getString("rule");
					String ruleCode = ja.getJsonObject(0).getString("code");
					// QDataRuleMessage ruleMsg = gson3.fromJson(json, QDataRuleMessage.class);
					System.out.println("Incoming Rule :" + ruleText);
					if (rulesDir == null) {
						rulesDir = "rules";
					}

					String rulesGroup = rulesDir;
					List<Tuple3<String,String, String>> rules = new ArrayList<Tuple3<String,String, String>>();
					rules.add(Tuple.of(ruleGroup,ruleCode, ruleText));

					RulesLoader.setupKieRules(rulesGroup, rules);
				} else if (payload.getString("data_type").equals(Answer.class.getSimpleName())) {
					try {
						dataMsg = JsonUtils.fromJson(payload.toString(), QDataAnswerMessage.class);
						processMsg("Data:"+dataMsg.getData_type(), payload.getString("ruleGroup"),dataMsg, eventBus, payload.getString("token"));
					} catch (com.google.gson.JsonSyntaxException e) {
						log.error("BAD Syntax converting to json from " + dataMsg);
						JsonObject json = new JsonObject(payload.toString());
						JsonObject answerData = json.getJsonObject("items");
						JsonArray jsonArray = new JsonArray();
						jsonArray.add(answerData);
						json.put("items", jsonArray);
						dataMsg = JsonUtils.fromJson(json.toString(), QDataAnswerMessage.class);
						processMsg("Data:"+dataMsg.getData_type(), payload.getString("ruleGroup"), dataMsg, eventBus, payload.getString("token"));
					}
				}
				else if (payload.getString("data_type").equals(GPS.class.getSimpleName())) {

					QDataGPSMessage dataGPSMsg = null;
					try {
						dataGPSMsg = JsonUtils.fromJson(payload.toString(), QDataGPSMessage.class);
						processMsg("GPS", payload.getString("ruleGroup"), dataGPSMsg, eventBus, payload.getString("token"));
					}
					catch (com.google.gson.JsonSyntaxException e) {

						log.error("BAD Syntax converting to json from " + dataGPSMsg);
						JsonObject json = new JsonObject(payload.toString());
						JsonObject answerData = json.getJsonObject("items");
						JsonArray jsonArray = new JsonArray();
						jsonArray.add(answerData);
						json.put("items", jsonArray);
						dataGPSMsg = JsonUtils.fromJson(json.toString(), QDataGPSMessage.class);
						processMsg("GPS:"+dataGPSMsg.getData_type(), payload.getString("ruleGroup"), dataGPSMsg, eventBus, payload.getString("token"));
					}
				} else if(payload.getString("data_type").equals(QDataPaymentsCallbackMessage.class.getSimpleName())) {
					QDataPaymentsCallbackMessage dataCallbackMsg = null;
					try {
						dataCallbackMsg = JsonUtils.fromJson(payload.toString(), QDataPaymentsCallbackMessage.class);
						processMsg("Data:"+dataCallbackMsg.getData_type(), payload.getString("ruleGroup"), dataCallbackMsg, eventBus, payload.getString("token"));
					}
					catch (com.google.gson.JsonSyntaxException e) {

						log.error("BAD Syntax converting to json from " + dataCallbackMsg);
						JsonObject json = new JsonObject(payload.toString());
						dataCallbackMsg = JsonUtils.fromJson(json.toString(), QDataPaymentsCallbackMessage.class);
						processMsg("Callback:"+dataCallbackMsg.getData_type(), payload.getString("ruleGroup"), dataCallbackMsg, eventBus, payload.getString("token"));
					}
				}
			}
		});
	}

	private static JsonObject processMessage(String messageType, io.vertx.rxjava.core.eventbus.Message<Object> arg) {
	//	log.info("EVENT-BUS >> " + messageType.toUpperCase() );

		final JsonObject payload = new JsonObject(arg.body().toString());
		return payload;
	}

	public static void processMsg(final String msgType,String ruleGroup,final Object msg, final EventBus eventBus, final String token) {
		Vertx.currentContext().owner().executeBlocking(future -> {
			Map<String,Object> adecodedTokenMap = RulesLoader.getDecodedTokenMap(token);
			Set<String> auserRoles = KeycloakUtils.getRoleSet(adecodedTokenMap.get("realm_access").toString());
			User userInSession = usersSession.get(adecodedTokenMap.get("preferred_username").toString());

			String preferredUName = adecodedTokenMap.get("preferred_username").toString();
			String fullName = adecodedTokenMap.get("name").toString();
			String realm = adecodedTokenMap.get("realm").toString();
			String accessRoles = adecodedTokenMap.get("realm_access").toString();

			QRules qRules = new QRules(eventBus, token, adecodedTokenMap);
			qRules.set("realm", realm);


			List<Tuple2<String, Object>> globals = new ArrayList<Tuple2<String, Object>>();
			RulesLoader.getStandardGlobals();

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

			if (!"GPS".equals(msgType)) { System.out.println("FIRE RULES ("+realm+") "+msgType); }

		//	String ruleGroupRealm = realm + (StringUtils.isBlank(ruleGroup)?"":(":"+ruleGroup));
			try {
				RulesLoader.executeStatefull(realm, eventBus, globals, facts, keyvalue);
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

	public static void initMsg(final String msgType,String ruleGroup,final Object msg, final EventBus eventBus) {
		Vertx.currentContext().owner().executeBlocking(future -> {
			Map<String,Object> adecodedTokenMap = new HashMap<String,Object>();
			Set<String> auserRoles = new HashSet<String>();
			auserRoles.add("admin");
			auserRoles.add("user");

			QRules qRules = new QRules(eventBus, token, adecodedTokenMap);
			qRules.set("realm", ruleGroup);

			List<Tuple2<String, Object>> globals = RulesLoader.getStandardGlobals();

			List<Object> facts = new ArrayList<Object>();
			facts.add(qRules);
			facts.add(msg);
			facts.add(adecodedTokenMap);
			facts.add(auserRoles);
	            User currentUser = new User("user1", "User1", ruleGroup, "admin");
				usersSession.put("user", currentUser);
				facts.add(currentUser);



			Map<String, String> keyvalue = new HashMap<String, String>();
			keyvalue.put("token", token);

		//	if (!"GPS".equals(msgType)) { System.out.println("FIRE RULES ("+ruleGroup+") "+msgType); }

		//	String ruleGroupRealm = realm + (StringUtils.isBlank(ruleGroup)?"":(":"+ruleGroup));
			try {
				RulesLoader.executeStatefull(ruleGroup, eventBus, globals, facts, keyvalue);
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
