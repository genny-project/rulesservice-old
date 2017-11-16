package life.genny.rules;

import java.util.HashMap;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import io.vertx.core.json.JsonObject;
import life.genny.qwanda.message.QEventLinkChangeMessage;
import life.genny.qwanda.message.QEventMessage;

public class RuleTest {
	
	static KieServices ks = KieServices.Factory.get();
	 static KieContainer kContainer;
	 static KieSession kSession;

	
	 @Before
	 public void init() {	   
	    kContainer = ks.getKieClasspathContainer();
	    kSession = kContainer.newKieSession("ksession-rules");

	    	
	 }
	 
	 
	 @Test
     public void bucket_view_drag_drop_test() {
		 final Map<String, String> keyValue = new HashMap<String, String>();
		 QEventLinkChangeMessage evtMsg = new 
			      QEventLinkChangeMessage("GRP_QUOTES","GRP_COMPLETED", "BEG_0000002", "LNK_CORE", null);
		 keyValue.put("token", null);
		 kSession.insert(keyValue);	 
		 kSession.fireAllRules();
	 
	 }
		
	

}
