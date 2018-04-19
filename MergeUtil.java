// package life.genny.qwandautils;

// import java.io.IOException;
// import java.lang.invoke.MethodHandles;
// import java.text.DateFormat;
// import java.text.DecimalFormat;
// import java.time.LocalDateTime;
// import java.time.format.DateTimeFormatter;
// import java.time.format.DateTimeFormatterBuilder;
// import java.time.temporal.ChronoField;
// import java.util.Map;
// import java.util.Map.Entry;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;

// import org.apache.logging.log4j.Logger;
// import org.javamoney.moneta.Money;

// import life.genny.qwanda.Link;
// import life.genny.qwanda.attribute.EntityAttribute;
// import life.genny.qwanda.entity.BaseEntity;

// public class MergeUtil {
	
// 	protected static final Logger log = org.apache.logging.log4j.LogManager
// 			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

// 	public static final String ANSI_RESET = "\u001B[0m";
//     public static final String ANSI_BLUE = "\u001B[34m";
//     public static final String ANSI_RED = "\u001B[31m";
	
//     /* [VARIABLENAME.ATTRIBUTE] pattern */
// 	public static final String REGEX_START = "[";
// 	public static final String REGEX_END = "]";
// 	public static final String REGEX_START_PATTERN = Pattern.quote(REGEX_START);
//     public static final String REGEX_END_PATTERN = Pattern.quote(REGEX_END);
//     public static final Pattern PATTERN = Pattern.compile(REGEX_START_PATTERN + "(?s)(.*?)" + REGEX_END_PATTERN);
//     public static final String DEFAULT = "";
//     public static final String PATTERN_BASEENTITY = REGEX_START_PATTERN + "(?s)(.*?)" + REGEX_END_PATTERN;
//     public static final Pattern PATTEN_MATCHER = Pattern.compile(PATTERN_BASEENTITY);
    
//     /* ${VARIABLE} pattern */
//     public static final String VARIABLE_REGEX_START = "${";
//     public static final String VARIABLE_REGEX_END = "}";
//     public static final Pattern PATTERN_VARIABLE = Pattern.compile(Pattern.quote(VARIABLE_REGEX_START) + "(.*)" + Pattern.quote(VARIABLE_REGEX_END));    
    
// 	public static String merge(String mergeStr, Map<String, Object> templateEntityMap) {
		
// 		/* matching [OBJECT.ATTRIBUTE] patterns */
// 		Matcher match = PATTEN_MATCHER.matcher(mergeStr);
// 		Matcher matchVariables = PATTERN_VARIABLE.matcher(mergeStr);
		
// 		while (match.find()) {	
			
// 			Object mergedtext = wordMerge(templateEntityMap, match.group(1));
// 			log.info("merge text ::"+mergedtext);
// 			System.out.println("merge text ::"+mergedtext);
// 			if(mergedtext != null) {
// 				mergeStr = mergeStr.replace(REGEX_START + match.group(1) + REGEX_END, mergedtext.toString());
// 			} else {
// 				mergeStr = mergeStr.replace(REGEX_START + match.group(1) + REGEX_END, "");
// 			}			
// 		}
		
// 		/* duplicating this for now. ideally wordMerge should be a bit more flexible and allows all kind of data to be passed */
// 		while(matchVariables.find()) {
			
// 			Object mergedtext = wordMerge(templateEntityMap, matchVariables.group(1));
// 			log.info("merge text ::"+mergedtext);
// 			System.out.println("merge text ::"+mergedtext);
// 			if(mergedtext != null) {
// 				mergeStr = mergeStr.replace(VARIABLE_REGEX_START + matchVariables.group(1) + VARIABLE_REGEX_END, mergedtext.toString());
// 			} else {
// 				mergeStr = mergeStr.replace(VARIABLE_REGEX_START + matchVariables.group(1) + VARIABLE_REGEX_END, "");
// 			}	
// 		}
		
// 		return mergeStr;
// 	}
	
// 	private static String wordMerge(Map<String, Object> entitymap, String mergeText) {
		
// 		if(mergeText != null && !mergeText.isEmpty()) {
			
// 			try {
				
// 				/* we split the text to merge into 2 components: BE.PRI... becomes [BE, PRI...] */
// 				String[] entityArr = mergeText.split("\\.");
// 				String keyCode = entityArr[0];
				
// 				if((entityArr.length == 0))
// 					return DEFAULT;
				
// 				if(entitymap.containsKey(keyCode)) {
					
// 					Object value = entitymap.get(keyCode);
// 					if(value.getClass().equals(BaseEntity.class)) {
						
// 						BaseEntity be = (BaseEntity)value;

// 						String attributeCode = entityArr[1];
						
// 						/* TODO: to be removed. a good way to do that is to use getLoopValue(.., Class) of BaseEntity class and check if the value returned is null.
// 								 if it is it means the cast did not work and therefore the attribute is not a Money class and you can proceed to the second test */
						
// 						if ((attributeCode.equals("PRI_PRICE")
// 								|| attributeCode.equals("PRI_LOAD_PRICE") || attributeCode.equals("PRI_OFFER_PRICE")
// 								|| attributeCode.equals("PRI_FEE") || attributeCode.equals("PRI_OWNER_PRICE")
// 								|| attributeCode.equals("PRI_DRIVER_PRICE") || attributeCode.equals("PRI_OFFER_FEE")
// 								|| attributeCode.equals("PRI_OFFER_OWNER_PRICE")
// 								|| attributeCode.equals("PRI_OFFER_DRIVER_PRICE") || attributeCode.equals("PRI_FEE_INC_GST")
// 								|| attributeCode.equals("PRI_FEE_EXC_GST") || attributeCode.equals("PRI_OFFER_FEE_INC_GST")
// 								|| attributeCode.equals("PRI_OFFER_FEE_EXC_GST")
// 								|| attributeCode.equals("PRI_OWNER_PRICE_INC_GST")
// 								|| attributeCode.equals("PRI_OWNER_PRICE_EXC_GST")
// 								|| attributeCode.equals("PRI_OFFER_OWNER_PRICE_INC_GST")
// 								|| attributeCode.equals("PRI_OFFER_OWNER_PRICE_EXC_GST")
// 								|| attributeCode.equals("PRI_DRIVER_PRICE_INC_GST")
// 								|| attributeCode.equals("PRI_DRIVER_PRICE_EXC_GST")
// 								|| attributeCode.equals("PRI_OFFER_DRIVER_PRICE_INC_GST")
// 								|| attributeCode.equals("PRI_OFFER_DRIVER_PRICE_EXC_GST"))) {
							
// 							System.out.println("price attributes");
							
// 							Money money = be.getValue(attributeCode, null);
// 							DecimalFormat df = new DecimalFormat("#.00"); 
							
// 							return df.format(money.getNumber()) + " " + money.getCurrency();
							
// 						} else if(attributeCode.equals("PRI_DRIVER_CONFIRM_PICKUP_DATETIME")) {
													
// 							LocalDateTime dateTimeRawValue = be.getValue(attributeCode, null);
							
// 							/*To print in this format : 9am AEST, Monday, 22 January 2018*/
// 							int ampmIntVal = dateTimeRawValue.get(ChronoField.AMPM_OF_DAY);
// 							String ampm = null;
// 							if(ampmIntVal == 0) {
// 								ampm = "AM";
// 							} else {
// 								ampm = "PM";
// 							}
							
// 						   DateTimeFormatter df =  
// 						            new DateTimeFormatterBuilder().appendPattern("[HH][.mm]")
// 						            .parseDefaulting(ChronoField.HOUR_OF_AMPM, 0)
// 						            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
// 						            .toFormatter(); 
// 						   String time = df.format(dateTimeRawValue);

							
// 							String editedDateTime = time + " " + ampm + ", " + dateTimeRawValue.getDayOfWeek().toString().toLowerCase() + ", " + dateTimeRawValue.getDayOfMonth() + " " + dateTimeRawValue.getMonth() + " " + dateTimeRawValue.getYear();
// 							return editedDateTime.toLowerCase();
							
// 						} else if(attributeCode.equals("PRI_DROPOFF_DATETIME")) {
// 							LocalDateTime dateTimeRawValue = be.getValue(attributeCode, null);
// 							String formattedDate = "";
							
// 							if(dateTimeRawValue!= null) {
// 								DateTimeFormatter df =  
// 							            new DateTimeFormatterBuilder().appendPattern("dd/MM/yy HH:mm:ss").toFormatter();
// 								formattedDate = df.format(dateTimeRawValue);
// 							}
							
// 							return formattedDate;
							
// 						}else {
// 							return getBaseEntityAttrValueAsString(be, attributeCode);
// 						}
// 					}
// 					else if (value.getClass().equals(String.class)) {
// 						return (String)value;
// 					}
// 				}

// 			} catch (Exception e) {
// 				e.printStackTrace();
// 			}

			
// 		}
		
// 		return DEFAULT;	
// 	}

	
// 	/**
// 	 * 
// 	 * @param baseEntAttributeCode
// 	 * @param token
// 	 * @return Deserialized BaseEntity model object with values for a BaseEntity code that is passed
// 	 */
// 	public static BaseEntity getBaseEntityForAttr(String baseEntAttributeCode, String token) {
		
// 		//String qwandaServiceUrl = "http://localhost:8280";
// 		String qwandaServiceUrl = System.getenv("REACT_APP_QWANDA_API_URL");
// 		String attributeString = null;
// 		BaseEntity be = null;
// 		try {
// 			attributeString = QwandaUtils
// 					.apiGet(qwandaServiceUrl + "/qwanda/baseentitys/" +baseEntAttributeCode, token);
// 		//	System.out.println(ANSI_BLUE + "Base entity attribute code::"+baseEntAttributeCode + ANSI_RESET);					
// 			be = JsonUtils.fromJson(attributeString, BaseEntity.class);
// 			//be = JsonUtils.gson.fromJson(attributeString, BaseEntity.class);
			
// 		} catch (IOException e) {
// 			e.printStackTrace();
// 		}
		
		
// 		return be;
// 	}
	
// 	/**
// 	 * 
// 	 * @param BaseEntity object
// 	 * @param attributeCode
// 	 * @return The attribute value for the BaseEntity attribute code passed
// 	 */
// 	public static String getBaseEntityAttrValueAsString(BaseEntity be, String attributeCode) {
		
// 		String attributeVal = null;
// 		for(EntityAttribute ea : be.getBaseEntityAttributes()) {
// 			if(ea.getAttributeCode().equals(attributeCode)) {
// 				attributeVal = ea.getObjectAsString();
// 			}
// 		}
		
// 		return attributeVal;
		
// 		/*String value =  null;
		
// 		try {
// 			value = be.findEntityAttribute(attributeCode).get().getObjectAsString();
// 		} catch (Exception e) {
// 			log.error("Attribute not found");
// 			return null;
// 		}
		
// 		return value;*/
// 	}
	
	
// 	/**
// 	 * 
// 	 * @param baseEntityCode
// 	 * @param attributeCode
// 	 * @param token
// 	 * @return attribute value
// 	 */
// 	public static String getAttrValue(String baseEntityCode, String attributeCode, String token) {
		
// 		String attrValue = null;
		
// 		if(baseEntityCode != null && token != null) {
			
// 			BaseEntity be = getBaseEntityForAttr(baseEntityCode, token);
// 			attrValue = getBaseEntityAttrValueAsString(be, attributeCode);			
// 		}
		
// 		return attrValue;
// 	}
	
// 	public static String getFullName(String baseEntityCode, String token) {
		
// 		String fullName = null;

// 		if(baseEntityCode != null && token != null) {

// 			String firstName = MergeUtil.getAttrValue(baseEntityCode, "PRI_FIRSTNAME", token);
// 			String lastName = MergeUtil.getAttrValue(baseEntityCode, "PRI_LASTNAME", token);
// 			fullName = firstName + " " + lastName;
// 			System.out.println("PRI_FULLNAME   ::   "+ fullName);		
// 		}
		
// 		return fullName;
// 	}

// 	public static boolean createBaseEntity(String sourceCode, String linkCode, String targetCode, String name, Long id, String token) {
		
// 		BaseEntity be = new BaseEntity(targetCode, name);
// 		String qwandaServiceUrl = System.getenv("REACT_APP_QWANDA_API_URL");
		
	      
//         String jsonBE = JsonUtils.toJson(be);
//         try {
//             String output= QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys", jsonBE, token);
            
//             QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/entityentitys", JsonUtils.toJson(new Link(sourceCode, targetCode, linkCode)),token);
//             System.out.println("this is the output :: "+ output);
            
//         }catch (Exception e) {
//             e.printStackTrace();
//         }
		
// 		return true;
		
// 	}
	
// 	public static Object getBaseEntityAttrObjectValue(BaseEntity be, String attributeCode) {

// 		Object attributeVal = null;
// 		for (EntityAttribute ea : be.getBaseEntityAttributes()) {
// 			if (ea.getAttributeCode().equals(attributeCode)) {
// 				attributeVal = ea.getObject();
// 			}
// 		}

// 		return attributeVal;

// 	}

// }
