package life.genny.utils;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import life.genny.qwandautils.KeycloakUtils;
import life.genny.qwandautils.QwandaUtils;
import life.genny.qwandautils.SecurityUtils;
import life.genny.security.SecureResources;;

public class BaseEntityUtils2 {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

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

	/**
	 *
	 * @param qwandaServiceUrl
	 * @param decodedToken
	 * @param token
	 * @return baseEntity user for the decodedToken passed
	 */
	public static String getBaseEntitysByAttributeAndValue(final String qwandaServiceUrl,
			 final String attributeCode, final String value, final String token) {


		try {
			String beJson = null;
			beJson = QwandaUtils.apiGet(
					qwandaServiceUrl + "/qwanda/baseentitys/test2?pageSize="+1000+"&" + attributeCode + "=" + value, token);

			return beJson;

		} catch (IOException e) {
			log.error("Error in fetching Base Entity from Qwanda Service");
		}
		return null;

	}

	public static String generateServiceToken(String realm) {
		return RulesUtils.generateServiceToken(realm);
	}

	public static void println(final Object obj, final String colour) {
		Date date = new Date();
		if (devMode) {
			System.out.println(date+": "+obj);
		} else {
			System.out.println((devMode ? "" : colour) + date+": " + obj + (devMode ? "" : ANSI_RESET));
		}

	}

	public static void println(final Object obj) {
		println(obj, ANSI_RESET);
	}
}
