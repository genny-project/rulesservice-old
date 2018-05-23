package life.genny.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class DateUtils {
	
	/*
	 * Returns UTC DateTime 
	 */
	public static String getCurrentUTCDateTime() {
		
		ZonedDateTime now = ZonedDateTime.now( ZoneOffset.UTC );
		String dateTimeString = now.toString();
		System.out.println("UTC datetime is ::" + dateTimeString);

		return dateTimeString;
	}
	
	/*
	 * Returns Local systems DateTime
	 */
	public static String getZonedCurrentLocalDateTime() {

		LocalDateTime ldt = LocalDateTime.now();
		ZonedDateTime zdt = ldt.atZone(ZoneOffset.systemDefault());
		String iso8601DateString = ldt.toString(); // zdt.toString(); MUST USE UMT!!!!

		System.out.println("datetime ::" + iso8601DateString);

		return iso8601DateString;

	}

}
