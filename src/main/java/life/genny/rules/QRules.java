package life.genny.rules;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.drools.core.WorkingMemory;
import org.drools.core.base.DefaultKnowledgeHelper;
import org.drools.core.base.SequentialKnowledgeHelper;
import org.drools.core.spi.KnowledgeHelper;
import org.kie.api.runtime.process.ProcessInstance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.qwanda.Answer;
import life.genny.qwanda.Ask;
import life.genny.qwanda.DateTimeDeserializer;
import life.genny.qwanda.Link;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.attribute.EntityAttribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.message.QCmdGeofenceMessage;
import life.genny.qwanda.message.QCmdLayoutMessage;
import life.genny.qwanda.message.QCmdMessage;
import life.genny.qwanda.message.QCmdViewMessage;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataAskMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwanda.message.QMSGMessage;
import life.genny.qwandautils.GPSUtils;
import life.genny.qwandautils.MessageUtils;
import life.genny.qwandautils.QwandaUtils;

public class QRules {

	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	public static final String qwandaServiceUrl = System.getenv("REACT_APP_QWANDA_API_URL");
	public static final Boolean devMode = System.getenv("GENNY_DEV") == null ? false : true;

	final static String DEFAULT_STATE = "NEW";

	private String token;
	private EventBus eventBus;
	private Map<String, Object> decodedTokenMap;
	private Map<String, Boolean> stateMap;
	
	KnowledgeHelper drools;
	
	public void setDrools(KnowledgeHelper drools) {
		this.drools = drools;
	}

	public QRules(final EventBus eventBus, final String token, final Map<String, Object> decodedTokenMap,
			String state) {
		super();
		this.eventBus = eventBus;
		this.token = token;
		this.decodedTokenMap = decodedTokenMap;
		this.stateMap = new HashMap<String, Boolean>();
		stateMap.put(DEFAULT_STATE, true); 
	
	}

	public QRules(final EventBus eventBus, final String token, final Map<String, Object> decodedTokenMap) {
		this(eventBus, token, decodedTokenMap, DEFAULT_STATE);
	}

	/**
	 * @return the token
	 */
	public String getToken() {
		return token;
	}

	/**
	 * @param token
	 *            the token to set
	 */
	public void setToken(String token) {
		this.token = token;
	}

	/**
	 * @return the eventBus
	 */
	public EventBus getEventBus() {
		return eventBus;
	}

	/**
	 * @param eventBus
	 *            the eventBus to set
	 */
	public void setEventBus(EventBus eventBus) {
		this.eventBus = eventBus;
	}

	/**
	 * @return the decodedTokenMap
	 */
	public Map<String, Object> getDecodedTokenMap() {
		return decodedTokenMap;
	}

	/**
	 * @param decodedTokenMap
	 *            the decodedTokenMap to set
	 */
	public void setDecodedTokenMap(Map<String, Object> decodedTokenMap) {
		this.decodedTokenMap = decodedTokenMap;
	}

	/**
	 * @return the state
	 */
	public boolean isState(final String key) {
		if (stateMap.containsKey(key)) {
			return stateMap.get(key);
		} else {
			return false;
		}
	}

	/**
	 * @param state
	 *            the state to set
	 */
	public void setState(String key) {
		stateMap.put(key.toUpperCase(), true);
		println("STATE "+key+" SET",RulesUtils.ANSI_RED);
		update();
	}

	/**
	 * @param state
	 *            the state to clear
	 */
	public void clearState(String key) {
		stateMap.remove(key);
		println("STATE "+key+" CLEARED",RulesUtils.ANSI_PURPLE);
		update();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "QRules [decodedTokenMap=" + decodedTokenMap + ", state=" + stateMap + "]";
	}

	public Object get(final String key) {
		return decodedTokenMap.get(key);
	}

	public Boolean is(final String key) {
		return decodedTokenMap.containsKey(key);
	}

	public String getAsString(final String key) {
		return (String) get(key);
	}

	public BaseEntity getAsBaseEntity(final String key) {
		return (BaseEntity) get(key);
	}

	public List<BaseEntity> getAsBaseEntitys(final String key) {
		return (List<BaseEntity>) get(key);
	}

	public Attribute getAsAttribute(final String key) {
		return (Attribute) get(key);
	}

	public Double getAsDouble(final String key) {
		return (Double) get(key);
	}

	public Boolean getAsBoolean(final String key) {
		return (Boolean) get(key);
	}

	public Boolean isTrue(final String key) {
		return getAsBoolean(key);
	}

	public Boolean isFalse(final String key) {
		return !getAsBoolean(key);
	}

	public Boolean isNull(final String key) {
		if (is(key)) {
			if (get(key) == null) {
				return true;
			} else {
				return false;
			}
		} else {
			return true;
		}
	}

	public void set(final String key, Object value) {
		decodedTokenMap.put(key, value);

	}

	public BaseEntity getUser() {
		BaseEntity be = null;
		if (isNull("USER")) {
			String username = (String) getDecodedTokenMap().get("preferred_username");
			String uname = QwandaUtils.getNormalisedUsername(username);
			String code = "PER_" + uname.toUpperCase();

			be = RulesUtils.getBaseEntityByCode(qwandaServiceUrl,  getDecodedTokenMap(), getToken(), code);
			if (be != null) {
				set("USER", be); // WATCH THIS!!!
			}
		} else {
			be = getAsBaseEntity("USER");
		}


		return be;
	}

	public Boolean isUserPresent()
	{
		if (isNull("USER")) {
			return false;
		} else {
			return true;
		}
	}

	public BaseEntity getBaseEntityByCode(final String code) {
		BaseEntity be = null;
		if (isNull("BE_" + code.toUpperCase())) {
			be = RulesUtils.getBaseEntityByCode(qwandaServiceUrl, getDecodedTokenMap(), getToken(), code);
			set("BE_" + code.toUpperCase(), be); // WATCH THIS!!!
		} else {
			be = getAsBaseEntity("BE_" + code.toUpperCase());
		}
		return be;
	}

	public BaseEntity getBaseEntityByAttributeAndValue(final String attributeCode, final String value) {

		BaseEntity be = null;
		if (isNull("BE_" + attributeCode.toUpperCase()+"_"+value)) {
			be = RulesUtils.getBaseEntityByAttributeAndValue(qwandaServiceUrl, getDecodedTokenMap(), getToken(), attributeCode, value);
			set("BE_" + attributeCode.toUpperCase()+"_"+value, be); // WATCH THIS!!!
		} else {
			be = getAsBaseEntity("BE_" + attributeCode.toUpperCase()+"_"+value);
		}
		return be;
	}

	public List<BaseEntity> getBaseEntitysByAttributeAndValue(final String attributeCode, final String value) {

		List<BaseEntity> bes = null;
		if (isNull("BE_" + attributeCode.toUpperCase()+"_"+value)) {
			bes = RulesUtils.getBaseEntitysByAttributeAndValue(qwandaServiceUrl, getDecodedTokenMap(), getToken(), attributeCode, value);
			set("BE_" + attributeCode.toUpperCase()+"_"+value, bes); // WATCH THIS!!!
		}

		return bes;
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode) {
		return getBaseEntitysByParentAndLinkCode(parentCode, linkCode, 0, 10, false);
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart, Integer pageSize) {

		return getBaseEntitysByParentAndLinkCode(parentCode, linkCode, pageStart, pageSize, false);
	}


	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart, Integer pageSize, Boolean cache) {
	
		List<BaseEntity> bes = null;
		
		if (isNull("BES_" + parentCode.toUpperCase()+"_"+linkCode)) {
			
			bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes(qwandaServiceUrl, getDecodedTokenMap(), getToken(), parentCode, linkCode);
			
			if (cache) {
				set("BES_" + parentCode.toUpperCase()+"_"+linkCode,bes); // WATCH THIS!!!
			}
			
		} else {
			bes = getAsBaseEntitys("BES_" + parentCode.toUpperCase()+"_"+linkCode);
		}
		
		return bes;
	}
	

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart, Integer pageSize, Boolean cache,final String stakeholderCode) {
		List<BaseEntity> bes = null;
		if (getUser().is("PRI_DRIVER")) {
			RulesUtils.println("Is True");
		}
		if (isNull("BES_" + parentCode.toUpperCase()+"_"+linkCode)) {
			bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributesAndStakeholderCode(qwandaServiceUrl, getDecodedTokenMap(), getToken(), parentCode, linkCode,stakeholderCode);
			if (cache) {
				set("BES_" + parentCode.toUpperCase()+"_"+linkCode,bes); // WATCH THIS!!!
			}
		} else {
			bes = getAsBaseEntitys("BES_" + parentCode.toUpperCase()+"_"+linkCode);
		}
		return bes;
	}
	

	public String moveBaseEntity(final String baseEntityCode, final String sourceCode, final String targetCode, final String linkCode) {
		
		 JsonObject begEntity = new JsonObject();
         begEntity.put("sourceCode", sourceCode);
         begEntity.put("targetCode", baseEntityCode);
         begEntity.put("attributeCode", linkCode);

         try {
			
	        	 QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/move/" + targetCode, begEntity.toString(), getToken());
	        	 
	        	 JsonArray updatedLink = new JsonArray(QwandaUtils.apiGet(qwandaServiceUrl+"/qwanda/entityentitys/"+baseEntityCode+"/linkcodes/"+linkCode, getToken()));
	
	         //Creating a data msg
	         JsonObject newLink = new JsonObject();
	         newLink.put("msg_type", "DATA_MSG");
	         newLink.put("data_type", "LINK_CHANGE");
	         newLink.put("items", updatedLink);
	         newLink.put("token", getToken() );
	         System.out.println("-----------------------------------");
	         System.out.println("Updated Link : "+newLink.toString());
	         System.out.println("-----------------------------------");
	         getEventBus().publish("cmds", newLink);
        	 
		} catch (IOException e) {
			e.printStackTrace();
		}
         
         return null;
	}


	public void publishBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart, Integer pageSize, Boolean cache) {

			String json = RulesUtils.getBaseEntitysJsonByParentAndLinkCode(qwandaServiceUrl, getDecodedTokenMap(), getToken(), parentCode, linkCode);
			JsonObject obj = new  JsonObject(json);
			obj.put("token", getToken());
			publish("cmds",obj);
	}



	public Object getBaseEntityValue(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getObject();
		} else {
			return null;
		}
	}

	/**
	 * 
	 * @param BaseEntity object
	 * @param attributeCode
	 * @return The attribute value for the BaseEntity attribute code passed
	 */
	public static String getBaseEntityAttrValueAsString(BaseEntity be, String attributeCode) {
		
		String attributeVal = null;
		for(EntityAttribute ea : be.getBaseEntityAttributes()) {
			if(ea.getAttributeCode().equals(attributeCode)) {
				attributeVal = ea.getObjectAsString();
			}
		}
		
		return attributeVal;
	}
	

	public String getBaseEntityValueAsString(final String baseEntityCode, final String attributeCode) {
		
		String attrValue = null;
		
		if(baseEntityCode != null ) {
			
			BaseEntity be = getBaseEntityByCode(baseEntityCode);
			attrValue = getBaseEntityAttrValueAsString(be, attributeCode);
		}
		
		return attrValue;
	}

	public LocalDateTime getBaseEntityValueAsLocalDateTime(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getValueDateTime();
		} else {
			return null;
		}
	}
	

	public LocalDate getBaseEntityValueAsLocalDate(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getValueDate();
		} else {
			return null;
		}
	}

	public LocalTime getBaseEntityValueAsLocalTime(final String baseEntityCode, final String attributeCode) {
		
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getValueTime();
		} else {
			return null;
		}
	}
	
	public void geofenceJob(final String begCode, final String driverCode) {
		geofenceJob(begCode, driverCode, 100.0);
	}
	
	public void geofenceJob(final String begCode, final String driverCode, Double radius) {
			
		BaseEntity be = RulesUtils.getBaseEntityByCode(QRules.getQwandaServiceUrl(), this.getDecodedTokenMap(), this.getToken(), begCode);
		if(be != null) {
			
			QCmdGeofenceMessage[] cmds = GPSUtils.geofenceJob(be, driverCode, radius, QRules.getQwandaServiceUrl(), this.getToken(), this.getDecodedTokenMap());
			
			if(cmds != null) {
				for(QCmdGeofenceMessage cmd: cmds) {
					if(cmd != null) {
						this.publishCmd(cmd);
					}
				}
			}
		}
	}
	
	public void sendMessage(String begCode, String[] recipientArray, HashMap<String, String> contextMap, String templateCode, String messageType) {
		
		JsonObject message = MessageUtils.prepareMessageTemplate(templateCode, messageType, contextMap, recipientArray, getToken());
        this.getEventBus().publish("messages", message);
	}

	public BaseEntity createUser()
	{
		BaseEntity be = null;

		String username = getAsString("preferred_username").toLowerCase();
		String firstname = StringUtils.capitaliseAllWords(getAsString("given_name").toLowerCase());
		String lastname = StringUtils.capitaliseAllWords(getAsString("family_name").toLowerCase());
		String realm = StringUtils.capitaliseAllWords(getAsString("realm").toLowerCase());
		String name = StringUtils.capitaliseAllWords(getAsString("name").toLowerCase());
		String email = getAsString("email").toLowerCase();
		String keycloakId = getAsString("sub").toLowerCase();

		try {
			be = QwandaUtils.createUser(qwandaServiceUrl,getToken(), username, firstname, lastname, email, realm,name, keycloakId);
			be = RulesUtils.getBaseEntityByCode(getQwandaServiceUrl(), getDecodedTokenMap(),
					getToken(), be.getCode());
			println("New User Created "+be);
		} catch (IOException e) {
			log.error("Error in Creating User ");
		}
		return be;
	}
	
	public void sendLayout(final String layoutCode, final String layoutPath) {
		
		String layout = RulesUtils.getLayout(layoutPath);
     	
  		QCmdMessage layoutCmd = new QCmdLayoutMessage(layoutCode, layout);
        publishCmd(layoutCmd);
        
        RulesUtils.println(layoutCode+" SENT TO FRONTEND");
        
	}

	/**
	 * @return the state
	 */
	public Map<String, Boolean> getState() {
		return stateMap;
	}

	/**
	 * @param state the state to set
	 */
	public void setState(Map<String, Boolean> state) {
		this.stateMap = state;
	}

	/**
	 * @return the qwandaserviceurl
	 */
	public static String getQwandaServiceUrl() {
		return qwandaServiceUrl;
	}

	/**
	 * @return the devmode
	 */
	public static Boolean getDevmode() {
		return devMode;
	}

	public void publish(String channel, final Object payload)
	{
		if (channel.startsWith("debug")) {
			channel = channel.substring("debug".length());
		}
		
		this.getEventBus().publish(channel, payload);
	}

	public void send(final String channel, final Object payload)
	{
		this.getEventBus().send(channel, payload);
	}

	public void publishCmd(final BaseEntity be, final String aliasCode)
	{
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(be,aliasCode);
		msg.setToken(getToken());
	    publish("cmds", RulesUtils.toJsonObject(msg));
	}
	
	public void publishData(final QDataAnswerMessage msg)
	{
		msg.setToken(getToken());
	    publish("data", RulesUtils.toJsonObject(msg));
	}
	
	public void publishData(final Answer answer)
	{
		QDataAnswerMessage msg = new QDataAnswerMessage(answer);
		msg.setToken(getToken());  
	    publish("data", RulesUtils.toJsonObject(msg));
	}
	
	public void publishData(final QDataAskMessage msg)
	{
		msg.setToken(getToken());  
	    publish("data", RulesUtils.toJsonObject(msg));
	}
	
	public void publishCmd(final List<BaseEntity> beList, final String parentCode, final String linkCode)
	{
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(beList.toArray(new BaseEntity[0]));
		msg.setParentCode(parentCode);
		msg.setLinkCode(linkCode);
		msg.setToken(getToken());
	    publish("cmds", RulesUtils.toJsonObject(msg)); 
	}

	public void publishCmd(final QCmdMessage cmdMsg)
	{
		cmdMsg.setToken(getToken());
	    publish("cmds", RulesUtils.toJsonObject(cmdMsg));
	}
	
	public void publishMsg(final QMSGMessage message) {
		
		JsonObject jsonMessage = JsonObject.mapFrom(message);
		jsonMessage.put("token", getToken());
        publish("messages", jsonMessage);
	}
	
	/*
	 * Get user's company code
	 */
	public BaseEntity getParent(final String targetCode, final String linkCode) {
		
		try {			
			String beJson = QwandaUtils.apiGet(getQwandaServiceUrl()+"/qwanda/entityentitys/"+targetCode+"/linkcodes/"+linkCode+"/parents", getToken());
			Link[] linkArray = RulesUtils.fromJson(beJson, Link[].class);
 			if (linkArray.length> 0) {
			ArrayList<Link> arrayList = new ArrayList<Link>(Arrays.asList(linkArray)); 
			Link first = arrayList.get(0);
			RulesUtils.println("The Company code is   ::  "+first.getSourceCode());
			return getBaseEntityByCode(first.getSourceCode());
 			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	
      return null;
	}
	
	public List<Link> getLinks(final String parentCode, final String linkCode) 
	{
		List<Link> links = RulesUtils.getLinks(getQwandaServiceUrl(), getDecodedTokenMap(),
				getToken(), parentCode,  linkCode) ;
		return links;
	}

	public Boolean askQuestions(final String sourceCode, final String targetCode, final String questionCode)
	{
		return askQuestions(sourceCode, targetCode, questionCode, false);
	}

	public Boolean askQuestions(final String sourceCode, final String targetCode, final String questionCode, final boolean autoPushSelections)
	{
	    JsonObject questionJson = null;
	    
		try {
			if (autoPushSelections) {
				  String json = QwandaUtils.apiGet(getQwandaServiceUrl()+"/qwanda/baseentitys/"+sourceCode+"/asks2/"+questionCode+"/"+targetCode, getToken());
					
				  QDataAskMessage msg = RulesUtils.fromJson(json, QDataAskMessage.class);
				  
					  publishData(msg);
					  
					  // Now auto push any selection data
//					  for (Ask ask : msg.getItems()) {
//						  if (ask.getAttributeCode().startsWith("LNK_")) {
//							  
//							 // sendSelections(ask.getQuestion().getDataType(), "LNK_CORE", 10);
//						  }
//					  }
					  
					  QCmdViewMessage cmdFormView = new QCmdViewMessage("CMD_VIEW", questionCode);		
					  publishCmd(cmdFormView);
			} else {
				  questionJson = new JsonObject(QwandaUtils.apiGet(getQwandaServiceUrl()+"/qwanda/baseentitys/"+sourceCode+"/asks2/"+questionCode+"/"+targetCode, getToken()));
					 /* QDataAskMessage */
					  publish("data", questionJson);
					  
					  // Now auto push any selection data
					  
					  
					  QCmdMessage cmdFormView = new QCmdMessage("CMD_VIEW", "FORM_VIEW");
					  JsonObject json = JsonObject.mapFrom(cmdFormView);
					  json.put("root", questionCode);
					  json.put("token", getToken());
					  publish("cmds", json);			
			}
			  
		      RulesUtils.println(questionCode+" SENT TO FRONTEND");
		      
		      
			  return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public boolean sendSelections(final String selectionRootCode, final String linkCode, final Integer maxItems)
	{
	   
	     JsonObject selectionLists;
		try {
			selectionLists = new JsonObject( QwandaUtils.apiGet(getQwandaServiceUrl()+"/qwanda/baseentitys/"+selectionRootCode+"/linkcodes/"+linkCode+"?pageStart=0&pageSize="+maxItems, getToken()));
			   selectionLists.put("token", getToken());
			     publish("cmds",selectionLists);
			     return true;
		} catch (IOException e) {
			log.error("Unable to fetch selections");
			return false;
		}
	  

	}
	
	public void header()
	{
		try {
			RulesUtils.header(drools.getRule().getName() + " - " +  ((drools.getRule().getAgendaGroup() != null)?drools.getRule().getAgendaGroup():""));
		} catch (NullPointerException e) {
			println("Error in rules: ","ANSI_RED");
		}
	}
	
	public void footer()
	{
		try {
			RulesUtils.footer(drools.getRule().getName() + " - " +  ((drools.getRule().getAgendaGroup() != null)?drools.getRule().getAgendaGroup():""));
		} catch (NullPointerException e) {
			println("Error in rules: ","ANSI_RED");
		}	}
	
	public void println(final Object str)
	{
		RulesUtils.println(str);
	}
	
	public void println(final Object str, final String colour)
	{
		RulesUtils.println(str,colour);
	}
	
	public void update()
	{
		this.drools.update(this);
	}
	

    
	public String removeLink(final String parentCode, final String childCode, final String linkCode) {
		Link link = new Link(parentCode, childCode, linkCode);
		try {
			 return	QwandaUtils.apiDelete(getQwandaServiceUrl()+"/qwanda/entityentitys", link.toString(), getToken());
		}catch(Exception e) {
			e.printStackTrace();
		}
	return null;
	 
	}

	public void debug()
	{
		println("");
	}
	
	public void processAddressAnswers(QDataAnswerMessage m)
	{
	      GsonBuilder gsonBuilder = new GsonBuilder();
	        gsonBuilder.registerTypeAdapter(LocalDateTime.class, new DateTimeDeserializer());
	        Gson gson3 = gsonBuilder.create();

		try {		
			Answer[] newAnswers = new Answer[50];
			Answer[] answers = m.getItems();
		        
	        for (Answer answer : answers) {	        
	            answer.setSourceCode(answer.getTargetCode());
	            String value = answer.getValue();
	            
	            System.out.println("value ::"+value + "attribute code ::"+answer.getAttributeCode());

	            /* if this answer is actually an address another rule will be triggered */
	            if(answer.getAttributeCode().contains("ADDRESS_FULL")) {
	            	  JsonObject addressDataJson = new JsonObject(value);
		            	Map<String, String> availableKeys = new HashMap<String, String>();
		    			availableKeys.put("full_address", "FULL");
		    			availableKeys.put("street_address", "ADDRESS1");
		    			availableKeys.put("suburb", "SUBURB");
		    			availableKeys.put("state", "STATE");
		    			availableKeys.put("postal_code", "POSTCODE");
		    			availableKeys.put("country", "COUNTRY");
		    					    					    			
		    			int i = 0;
		    			for (Map.Entry<String, String> entry : availableKeys.entrySet())
		    			{			    				
		    				String key = entry.getKey();
		    				String valueEntry = entry.getValue();
		    						    				
		    				if(addressDataJson.containsKey(key)) {	    					
		    					String newAttributeCode = answer.getAttributeCode().replace("FULL", valueEntry);
		    					answer.setAttributeCode(newAttributeCode);
		    					answer.setValue(addressDataJson.getString(key));
			    				newAnswers[i] = answer;
		    					i++;
		    				}		    				
		    			}
		    			    					    	        
		    	        /* Store latitude */
		    	        String newAttCode = answer.getAttributeCode().replace("FULL", "LATITUDE");
		    			answer.setAttributeCode(newAttCode);
		    			Double latitude = addressDataJson.getDouble("latitude");
		    			println(" The latitude value after conversion is  :: "+latitude );
		    			
		    			if(latitude != null) {
			    			answer.setValue(Double.toString(latitude));
			    			String jsonAnswer = gson3.toJson(answer);
			    			Answer answerObj = gson3.fromJson(jsonAnswer, Answer.class);
			    			println("The answer object for latitude attribute is  :: "+answerObj.toString() );
			    			newAnswers[i] = answerObj;
			    			i++;
			    			println("The answer object for latitude attribute added to Answer array " );
		    			}
		    			
		    			/* Store longitude */
		    			newAttCode = answer.getAttributeCode().replace("FULL", "LONGITUDE");
		    			answer.setAttributeCode(newAttCode);
		    			Double longitude = addressDataJson.getDouble("longitude");
		    			println(" The longitude value after conversion is  :: "+longitude );
		    			
		    			if(longitude != null) {
			    			answer.setValue(Double.toString(longitude));
			    			String jsonAnswer = gson3.toJson(answer);
			    			Answer answerObj = gson3.fromJson(jsonAnswer, Answer.class);
			    			newAnswers[i] = answerObj;
			    			i++;
		    			}
		    			
		    			/* set new answers */
		    			m.setItems(newAnswers);
		    			String json = gson3.toJson(m);
		    			println("updated answer json string ::"+json);
		    			
		    			/* send new answers to api */ 
		    			QwandaUtils.apiPostEntity(qwandaServiceUrl+"/qwanda/answers/bulk", json, getToken());
	            }
	         }
		}
		catch (Exception e) {
	       e.printStackTrace();
		}
	}

	public void processAnswer(QDataAnswerMessage m)
	{
  
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(LocalDateTime.class, new DateTimeDeserializer());
        Gson gson = gsonBuilder.create();

        /* extract answers */
    	List<Answer> answers = new ArrayList<Answer>();

        Answer[] answers2 = m.getItems();
        for (Answer answer : answers2) {
        		if (answer != null) {
            Long askId = answer.getAskId();
            String sourceCode = answer.getSourceCode();
            String targetCode = answer.getTargetCode();
            answer.setSourceCode(answer.getTargetCode());
            String attributeCode = answer.getAttributeCode();
            String value = answer.getValue();
            Boolean inferred = answer.getInferred();
            Double weight = answer.getWeight();
            Boolean expired = answer.getExpired();
            Boolean refused = answer.getRefused();
            System.out.println("\nAskId: " +askId + "\nSource Code: " +sourceCode + "\nTarget Code: " +targetCode + "\nAttribute Code: " +attributeCode + "\nAttribute Value: " +value+" \nInferred: "+(inferred?"TRUE":"FALSE")+ " \nWeight: "+weight);
            System.out.println("------------------------------------------------------------------------");
            
            /* if this answer is actually an address another rule will be triggered */
            if(!attributeCode.contains("ADDRESS_FULL")) {
            		answers.add(answer);
              }
        		} else {
        			println("Answer was null ");
        		}
         }    
        
	
	      Answer items[] = new Answer[answers.size()];
	      items = answers.toArray(items);
		QDataAnswerMessage msg = new QDataAnswerMessage(items);
	
	      
        String jsonAnswer = gson.toJson(msg);
		try {
			QwandaUtils.apiPostEntity(getQwandaServiceUrl() + "/qwanda/answers/bulk", jsonAnswer,token);
		} catch (IOException e) {
			log.error("Socket error trying to post answer");
		}

	}
	
}
