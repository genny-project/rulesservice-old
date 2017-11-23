package life.genny.rules;

import java.util.HashMap;

import java.util.Map;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
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
	 
//	 @Test
	 public void msgTest() {
	   String test = new String("hello");
       kSession.insert(test);
       kSession.fireAllRules();
	 }
	 

//	@Test
     public void bucket_view_drag_drop_test() {
		System.out.println("Hello");
		 final Map<String, String> keyValue = new HashMap<String, String>();
//		 final Keycloak kc = KeycloakBuilder.builder()
//		          .serverUrl("http://10.1.120.89:8180/auth")
//		          .realm("genny")
//		          .username("user1")
//		          .password("password1")
//		          .clientId("curl")
//		          .resteasyClient(new ResteasyClientBuilder().connectionPoolSize(10).build())
//		          .build();
//		 String token = kc.tokenManager().getAccessToken().getToken();
//		 String token1 = kc.tokenManager().getAccessTokenString();
//		 System.out.println("The token is: "+token);
//		 System.out.println("The token is: "+token1);
		 
		 QEventLinkChangeMessage evtMsg = new 
			      QEventLinkChangeMessage("GRP_QUOTES","GRP_COMPLETED", "BEG_0000002", "LNK_CORE", null);
		 keyValue.put("token", "DUMB TOKEN");
		 kSession.insert(keyValue);
		 kSession.insert(evtMsg);
		 kSession.fireAllRules();
	 
	 }



}
