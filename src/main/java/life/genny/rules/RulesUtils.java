package life.genny.rules;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Logger;
import org.javamoney.moneta.Money;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.reflect.TypeToken;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.shareddata.AsyncMap;
import life.genny.qwanda.Answer;
import life.genny.qwanda.DateTimeDeserializer;
import life.genny.qwanda.Link;
import life.genny.qwanda.MoneyDeserializer;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataAttributeMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.QwandaUtils;
import life.genny.utils.VertxUtils;

public class RulesUtils {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	static public AsyncMap<String, BaseEntity> baseEntityMap;

	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_BLUE = "\u001B[34m";
	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_YELLOW = "\u001B[33m";
	public static final String ANSI_PURPLE = "\u001B[35m";
	public static final String ANSI_CYAN = "\u001B[36m";
	public static final String ANSI_WHITE = "\u001B[37m";
	public static final String ANSI_BOLD = "\u001b[1m";

	public static final String qwandaServiceUrl = System.getenv("REACT_APP_QWANDA_API_URL");
	public static final Boolean devMode = System.getenv("DEV_MODE") == null ? false : true;

	static public Map<String, Attribute> attributeMap = new ConcurrentHashMap<String, Attribute>();
	static public QDataAttributeMessage attributesMsg = null;

	public static String executeRuleLogger(final String status, final String module, final String topColour,
			final String bottomColour) {
		String moduleLogger = "\n" + (devMode ? "" : bottomColour) + status + " ::  " + module
				+ (devMode ? "" : ANSI_RESET);
		return moduleLogger;
	}

	public static String terminateRuleLogger(String module) {
		return executeRuleLogger("END RULE", module, ANSI_YELLOW, ANSI_GREEN) + "\n" + (devMode ? "" : ANSI_YELLOW)
				+ "======================================================================================================="
				+ (devMode ? "" : ANSI_RESET);

	}

	public static String headerRuleLogger(String module) {
		return "======================================================================================================="
				+ executeRuleLogger("START RULE", module, ANSI_RED, ANSI_GREEN) + "\n" + (devMode ? "" : ANSI_RED)
				+ (devMode ? "" : ANSI_RESET);
	}

	public static void header(final String module) {
		println(headerRuleLogger(module));
	}

	public static void footer(final String module) {
		println(terminateRuleLogger(module));
	}

	public static String jsonLogger(String module, Object data) {
		String initialLogger = "------------------------------------------------------------------------\n";
		String moduleLogger = ANSI_YELLOW + module + "   ::   " + ANSI_RESET + data + "\n";
		String finalLogger = "------------------------------------------------------------------------\n";
		return initialLogger + moduleLogger + finalLogger;
	}

	public static void ruleLogger(String module, Object data) {
		println(jsonLogger(module, data));
	}

	public static void println(final Object obj, final String colour) {
		if (devMode) {
			System.out.println(obj);
		} else {
			System.out.println((devMode ? "" : colour) + obj + (devMode ? "" : ANSI_RESET));
		}

	}

	public static void println(final Object obj) {
		println(obj, ANSI_RESET);
	}

	public static String getLayoutCacheURL(final String path) {

		// https://raw.githubusercontent.com/genny-project/layouts/master
		// System.getenv("LAYOUT_CACHE_HOST");
		// http://localhost:2223

		Boolean activateCache = null;
		if (System.getenv("GENNY_DEV") == null) {
			activateCache = true;
		} else {
			activateCache = false;
		}

		String host = System.getenv("LAYOUT_CACHE_HOST");
		if (host == null) {
			if (System.getenv("HOSTIP") == null) {
				host = "http://localhost:2223";
			} else {
				host = "http://" + System.getenv("HOSTIP") + ":2223";
			}
		}

		if (activateCache == false) {
			if (path.contains(".json")) {
				host = "https://raw.githubusercontent.com/genny-project/layouts/master";
			} else {
				host = "https://api.github.com/repos/genny-project/layouts/contents"; // TODO: this has a rate limit
			}
		}

		return String.format("%s/%s", host, path);
	}

	public static String getTodaysDate(final String dateFormat) {

		DateFormat dateFormatter = new SimpleDateFormat(dateFormat);
		Date date = new Date();
		return dateFormatter.format(date);
	}

	public static String getLayout(final String path) {
		String jsonStr = "";
		try {
			String url = getLayoutCacheURL(path);
			println("Trying to load url.....");
			println(url);
			jsonStr = QwandaUtils.apiGet(url, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsonStr;
	}

	public static JsonObject createDataAnswerObj(Answer answer, String token) {

		QDataAnswerMessage msg = new QDataAnswerMessage(answer);
		msg.setToken(token);

		return toJsonObject(msg);
	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @return baseEntity user for the decodedToken passed
	 */
	public static BaseEntity getUser(final String qwandaServiceUrl, Map<String, Object> decodedToken,
			final String token) {

		// try {
		String beJson = null;
		String username = (String) decodedToken.get("preferred_username");
		String uname = QwandaUtils.getNormalisedUsername(username);
		String code = "PER_" + uname.toUpperCase();
		// CHEAT TODO
		BaseEntity be = VertxUtils.readFromDDT(code, token);
		// beJson = QwandaUtils.apiGet(qwandaServiceUrl + "/qwanda/baseentitys/"+code,
		// token);
		// BaseEntity be = JsonUtils.fromJson(beJson, BaseEntity.class);

		// if (username != null) {
		// beJson = QwandaUtils.apiGet(qwandaServiceUrl +
		// "/qwanda/baseentitys/GRP_USERS/linkcodes/LNK_CORE/attributes?PRI_USERNAME=" +
		// username+"&pageSize=1", token);
		// } else {
		// String keycloakId = (String) decodedToken.get("sed");
		// beJson = QwandaUtils.apiGet(qwandaServiceUrl +
		// "/qwanda/baseentitys/GRP_USERS/linkcodes/LNK_CORE/attributes?PRI_KEYCLOAKID="
		// + keycloakId+"&pageSize=1",
		// token);
		//
		// }
		// QDataBaseEntityMessage msg = JsonUtils.fromJson(beJson,
		// QDataBaseEntityMessage.class);
		// BaseEntity be = msg.getItems()[0];
		//// List<BaseEntity> bes = Arrays.asList(JsonUtils.fromJson(beJson,
		// BaseEntity[].class));
		// BaseEntity be = bes.get(0);

		return be;

		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		// return null;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @return baseEntity user for the decodedToken passed
	 */
	public static String getBaseEntityJsonByCode(final String qwandaServiceUrl, Map<String, Object> decodedToken,
			final String token, final String code) {
		return getBaseEntityJsonByCode(qwandaServiceUrl, decodedToken, token, code, true);
	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @return baseEntity user for the decodedToken passed
	 */
	public static String getBaseEntityJsonByCode(final String qwandaServiceUrl, Map<String, Object> decodedToken,
			final String token, final String code, Boolean includeAttributes) {

		try {
			String beJson = null;
			String attributes = "";
			if (includeAttributes) {
				attributes = "/attributes";
			}
			beJson = QwandaUtils.apiGet(qwandaServiceUrl + "/qwanda/baseentitys/" + code + attributes, token);
			return beJson;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @return baseEntity user for the decodedToken passed
	 */
	public static String getBaseEntityJsonByAttributeAndValue(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String attributeCode, final String value) {

		try {
			String beJson = null;
			beJson = QwandaUtils.apiGet(
					qwandaServiceUrl + "/qwanda/baseentitys/test2?pageSize=1&" + attributeCode + "=" + value, token);
			// println("BE"+beJson);
			return beJson;

		} catch (IOException e) {
			log.error("Error in fetching Base Entity from Qwanda Service");
		}
		return null;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @return baseEntity user for the decodedToken passed
	 */
	public static List<BaseEntity> getBaseEntitysByAttributeAndValue(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String attributeCode, final String value) {

		String beJson = getBaseEntityJsonByAttributeAndValue(qwandaServiceUrl, decodedToken, token, attributeCode,
				value);
		QDataBaseEntityMessage be = fromJson(beJson, QDataBaseEntityMessage.class);

		List<BaseEntity> items = null;

		try {
			items = new ArrayList<BaseEntity>(Arrays.asList(be.getItems()));
		} catch (Exception e) {
			println("Error: items is null");
		}

		return items;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @return baseEntity user for the decodedToken passed
	 */
	public static BaseEntity getBaseEntityByAttributeAndValue(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String attributeCode, final String value) {

		List<BaseEntity> items = getBaseEntitysByAttributeAndValue(qwandaServiceUrl, decodedToken, token, attributeCode,
				value);

		if (items != null) {
			if (!items.isEmpty())
				return items.get(0);
		}

		return null;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @return baseEntity user for the decodedToken passed
	 */
	public static BaseEntity getBaseEntityByCode(final String qwandaServiceUrl, Map<String, Object> decodedToken,
			final String token, final String code) {

		// String beJson = getBaseEntityJsonByCode(qwandaServiceUrl, decodedToken,
		// token, code, true);
		// BaseEntity be = fromJson(beJson, BaseEntity.class);

		BaseEntity be = VertxUtils.readFromDDT(code, token);

		return be;
	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @return baseEntity user for the decodedToken passed
	 */
	public static BaseEntity getBaseEntityByCode(final String qwandaServiceUrl, Map<String, Object> decodedToken,
			final String token, final String code, Boolean includeAttributes) {

		// String beJson = getBaseEntityJsonByCode(qwandaServiceUrl, decodedToken,
		// token, code, includeAttributes);
		// BaseEntity be = fromJson(beJson, BaseEntity.class);

		BaseEntity be = VertxUtils.readFromDDT(code, token);

		return be;
	}

	public static <T> T fromJson(final String json, Class clazz) {
		return JsonUtils.fromJson(json, clazz);
	}

	public static String toJson(Object obj) {

		String ret = JsonUtils.toJson(obj);
		return ret;
	}

	public static JsonObject toJsonObject(Object obj) {
		String json = toJson(obj);
		JsonObject jsonObj = new JsonObject(json);
		return jsonObj;
	}

	public static JsonObject toJsonObject2(Object obj) {
		String json = JsonUtils.toJson(obj);
		JsonObject jsonObj = new JsonObject(json);
		return jsonObj;
	}

	public static String getBaseEntitysJsonByParentAndLinkCode(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode) {

		try {
			String beJson = null;
			beJson = QwandaUtils.apiGet(
					qwandaServiceUrl + "/qwanda/baseentitys/" + parentCode + "/linkcodes/" + linkCode + "/attributes",
					token);
			return beJson;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	public static String getBaseEntitysJsonByParentAndLinkCode(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode,
			final Integer pageStart, final Integer pageSize) {

		try {
			String beJson = null;
			beJson = QwandaUtils.apiGet(qwandaServiceUrl + "/qwanda/baseentitys/" + parentCode + "/linkcodes/"
					+ linkCode + "/attributes?pageStart=" + pageStart + "&pageSize=" + pageSize, token);
			return beJson;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	/* added because of the bug */	
	public static String getBaseEntitysJsonByParentAndLinkCode2(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode,
			final Integer pageStart, final Integer pageSize) {

		try {
			String beJson = null;
			beJson = QwandaUtils.apiGet(qwandaServiceUrl + "/qwanda/baseentitys/" + parentCode + "/linkcodes/"
					+ linkCode + "?pageStart=" + pageStart + "&pageSize=" + pageSize, token);
			return beJson;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	public static String getBaseEntitysJsonByParentAndLinkCodeAndLinkValue(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode,
			final String linkValue, final Integer pageStart, final Integer pageSize) {

		try {
			String beJson = null;
			beJson = QwandaUtils.apiGet(
					qwandaServiceUrl + "/qwanda/baseentitys2/" + parentCode + "/linkcodes/" + linkCode + "/linkValue/"
							+ linkValue + "/attributes?pageStart=" + pageStart + "&pageSize=" + pageSize,
					token);
			return beJson;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	public static String getBaseEntitysJsonByParentAndLinkCodeWithAttributes(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode) {

		try {
			String beJson = null;
			beJson = QwandaUtils.apiGet(
					qwandaServiceUrl + "/qwanda/baseentitys/" + parentCode + "/linkcodes/" + linkCode + "/attributes",
					token);
			return beJson;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	public static String getBaseEntitysJsonByChildAndLinkCodeWithAttributes(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String childCode, final String linkCode) {

		try {
			String beJson = null;
			beJson = QwandaUtils.apiGet(qwandaServiceUrl + "/qwanda/baseentitys/" + childCode + "/linkcodes/" + linkCode
					+ "/parents/attributes", token);
			return beJson;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	public static String getBaseEntitysJsonByParentAndLinkCodeWithAttributesAndStakeholderCode(
			final String qwandaServiceUrl, Map<String, Object> decodedToken, final String token,
			final String parentCode, final String linkCode, final String stakeholderCode) {

		try {
			String beJson = null;
			beJson = QwandaUtils.apiGet(qwandaServiceUrl + "/qwanda/baseentitys/" + parentCode + "/linkcodes/"
					+ linkCode + "/attributes/" + stakeholderCode, token);
			return beJson;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @return baseEntitys
	 */
	public static BaseEntity[] getBaseEntitysArrayByParentAndLinkCodeWithAttributes(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode) {

		String beJson = getBaseEntitysJsonByParentAndLinkCode(qwandaServiceUrl, decodedToken, token, parentCode,
				linkCode);
		QDataBaseEntityMessage msg = JsonUtils.fromJson(beJson, QDataBaseEntityMessage.class);
		return msg.getItems();

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @param pageStart
	 * @param pageSize
	 * @return baseEntitys
	 */
	public static BaseEntity[] getBaseEntitysArrayByParentAndLinkCodeWithAttributes(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode,
			final Integer pageStart, final Integer pageSize) {

		String beJson = getBaseEntitysJsonByParentAndLinkCode(qwandaServiceUrl, decodedToken, token, parentCode,
				linkCode, pageStart, pageSize);
		QDataBaseEntityMessage msg = JsonUtils.fromJson(beJson, QDataBaseEntityMessage.class);
		return msg.getItems();

	}
	/** added because of bug /
	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @param pageStart
	 * @param pageSize
	 * @return baseEntitys
	 */
	public static BaseEntity[] getBaseEntitysArrayByParentAndLinkCodeWithAttributes2(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode,
			final Integer pageStart, final Integer pageSize) {

		String beJson = getBaseEntitysJsonByParentAndLinkCode2(qwandaServiceUrl, decodedToken, token, parentCode,
				linkCode, pageStart, pageSize);
		QDataBaseEntityMessage msg = JsonUtils.fromJson(beJson, QDataBaseEntityMessage.class);
		return msg.getItems();

	}
	
	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @param linkValue
	 * @param pageStart
	 * @param pageSize
	 * @return baseEntitys
	 */
	public static BaseEntity[] getBaseEntitysArrayByParentAndLinkCodeAndLinkValueWithAttributes(
			final String qwandaServiceUrl, Map<String, Object> decodedToken, final String token,
			final String parentCode, final String linkCode, final String linkValue, final Integer pageStart,
			final Integer pageSize) {

		String beJson = getBaseEntitysJsonByParentAndLinkCodeAndLinkValue(qwandaServiceUrl, decodedToken, token,
				parentCode, linkCode, linkValue, pageStart, pageSize);
		QDataBaseEntityMessage msg = JsonUtils.fromJson(beJson, QDataBaseEntityMessage.class);
		return msg.getItems();

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @return baseEntitys
	 */
	public static List<BaseEntity> getBaseEntitysByParentAndLinkCodeWithAttributes(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode) {

		BaseEntity[] beArray = getBaseEntitysArrayByParentAndLinkCodeWithAttributes(qwandaServiceUrl, decodedToken,
				token, parentCode, linkCode);
		ArrayList<BaseEntity> arrayList = new ArrayList<BaseEntity>(Arrays.asList(beArray));
		return arrayList;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @param pageStart
	 * @param pageSize
	 * @return baseEntitys
	 */
	public static List<BaseEntity> getBaseEntitysByParentAndLinkCodeWithAttributes(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode,
			final Integer pageStart, final Integer pageSize) {

		BaseEntity[] beArray = getBaseEntitysArrayByParentAndLinkCodeWithAttributes(qwandaServiceUrl, decodedToken,
				token, parentCode, linkCode, pageStart, pageSize);
		ArrayList<BaseEntity> arrayList = new ArrayList<BaseEntity>(Arrays.asList(beArray));
		return arrayList;

	}

	/**
	* 
	* @param qwandaServiceUrl
	* @param decodedToken
	* @param token
	* @param parentCode
	* @param linkCode
	* @param pageStart
	* @param pageSize
	* @return baseEntitys
	*/

	/* added because of bug */
	public static List<BaseEntity> getBaseEntitysByParentAndLinkCodeWithAttributes2(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String parentCode, final String linkCode,
			final Integer pageStart, final Integer pageSize) {

		BaseEntity[] beArray = getBaseEntitysArrayByParentAndLinkCodeWithAttributes2(qwandaServiceUrl, decodedToken,
				token, parentCode, linkCode, pageStart, pageSize);
		ArrayList<BaseEntity> arrayList = new ArrayList<BaseEntity>(Arrays.asList(beArray));
		return arrayList;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @param linkValue
	 * @param pageStart
	 * @param pageSize
	 * @return baseEntitys
	 */
	public static List<BaseEntity> getBaseEntitysByParentAndLinkCodeAndLinkValueWithAttributes(
			final String qwandaServiceUrl, Map<String, Object> decodedToken, final String token,
			final String parentCode, final String linkCode, final String linkValue, final Integer pageStart,
			final Integer pageSize) {

		BaseEntity[] beArray = getBaseEntitysArrayByParentAndLinkCodeAndLinkValueWithAttributes(qwandaServiceUrl,
				decodedToken, token, parentCode, linkCode, linkValue, pageStart, pageSize);
		ArrayList<BaseEntity> arrayList = new ArrayList<BaseEntity>(Arrays.asList(beArray));
		return arrayList;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @return baseEntitys
	 */
	public static List<BaseEntity> getBaseEntitysByChildAndLinkCodeWithAttributes(final String qwandaServiceUrl,
			Map<String, Object> decodedToken, final String token, final String childCode, final String linkCode) {

		String beJson = getBaseEntitysJsonByChildAndLinkCodeWithAttributes(qwandaServiceUrl, decodedToken, token,
				childCode, linkCode);
		QDataBaseEntityMessage msg = JsonUtils.fromJson(beJson, QDataBaseEntityMessage.class);
		BaseEntity[] beArray = msg.getItems();
		ArrayList<BaseEntity> arrayList = new ArrayList<BaseEntity>(Arrays.asList(beArray));
		return arrayList;

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @param stakeholderCode
	 * @return baseEntitys
	 */
	public static List<BaseEntity> getBaseEntitysByParentAndLinkCodeWithAttributesAndStakeholderCode(
			final String qwandaServiceUrl, Map<String, Object> decodedToken, final String token,
			final String parentCode, final String linkCode, final String stakeholderCode) {
		if (parentCode.equalsIgnoreCase("GRP_NEW_ITEMS")) {
			println("Group New Items Debug");
		}
		String beJson = getBaseEntitysJsonByParentAndLinkCodeWithAttributesAndStakeholderCode(qwandaServiceUrl,
				decodedToken, token, parentCode, linkCode, stakeholderCode);
		QDataBaseEntityMessage msg = fromJson(beJson, QDataBaseEntityMessage.class);
		if (msg == null) {
			log.error("Error in fetching BE from Qwanda Service");
		} else {
			BaseEntity[] beArray = msg.getItems();
			ArrayList<BaseEntity> arrayList = new ArrayList<BaseEntity>(Arrays.asList(beArray));
			return arrayList;
		}
		return null; // TODO get exception =s in place

	}

	/**
	 * 
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @param parentCode
	 * @param linkCode
	 * @param stakeholderCode
	 * @return baseEntitys
	 */
	public static List<Link> getLinks(final String qwandaServiceUrl, Map<String, Object> decodedToken,
			final String token, final String parentCode, final String linkCode) {

		String linkJson = null;
		List<Link> linkList = null;

		try {

			linkJson = QwandaUtils.apiGet(
					qwandaServiceUrl + "/qwanda/entityentitys/" + parentCode + "/linkcodes/" + linkCode + "/children",
					token);
			return JsonUtils.gson.fromJson(linkJson, new TypeToken<List<Link>>() {
			}.getType());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// BaseEntity[] beArray = msg.getItems();
		// ArrayList<BaseEntity> arrayList = new
		// ArrayList<BaseEntity>(Arrays.asList(beArray));
		// return arrayList;
		return linkList;
	}

	public static QDataAttributeMessage loadAllAttributesIntoCache(final String token) {
		try {

			JsonObject json = VertxUtils.readCachedJson("attributes");
			if ("ok".equals(json.getString("status"))) {
				println("LOADING ATTRIBUTES FROM CACHE!");
				// VertxUtils.writeCachedJson("attributes", json.getString("value"));
				attributesMsg = JsonUtils.fromJson(json.getString("value"), QDataAttributeMessage.class);
				Attribute[] attributeArray = attributesMsg.getItems();

				for (Attribute attribute : attributeArray) {
					attributeMap.put(attribute.getCode(), attribute);
				}
				println("All the attributes have been loaded in "+attributeMap.size()+" attributes");

			} else {
				println("LOADING ATTRIBUTES FROM API");
				String jsonString = QwandaUtils.apiGet(qwandaServiceUrl + "/qwanda/attributes", token);
				VertxUtils.writeCachedJson("attributes", jsonString);
				attributesMsg = JsonUtils.fromJson(jsonString, QDataAttributeMessage.class);
				Attribute[] attributeArray = attributesMsg.getItems();

				for (Attribute attribute : attributeArray) {
					attributeMap.put(attribute.getCode(), attribute);
				}
				println("All the attributes have been loaded from api in"+attributeMap.size()+" attributes");

			}

			return attributesMsg;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
