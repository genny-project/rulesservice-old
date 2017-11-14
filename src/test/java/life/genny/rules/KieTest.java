package life.genny.rules;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.channels.EBCHandlers;

public class KieTest {
	
	  private static final Logger log = org.apache.logging.log4j.LogManager
		      .getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	
	@Test
	public void drlSyntaxTest()
	{
		  String str = "";
		    str += "package org.kie.test\n";
		    str += "global java.util.List list\n";
		    str += "rule rule1\n";
		    str += "when\n";
		    str += "  Integer(intValue > 0)\n";
		    str += "then\n";
		    str += "  list.add( 1 );\n";
		    str += "end\n";
		    str += "\n";
		    
		    Map<String,String> drls = new HashMap<String,String>();
		    drls.put("rule1", str);
		    
		KieHelper kieHelper = new KieHelper();
		for (String ruleId : drls.keySet()) {
		    kieHelper.addContent(drls.get(ruleId), ResourceType.DRL);
		}
		Results results = kieHelper.verify();
		for (Message message : results.getMessages()) {
		    log.error(">> Message ({}): {}", message.getLevel(), message.getText());
		}

		if (results.hasMessages(Message.Level.ERROR)) {
		    throw new IllegalStateException("There are errors in the KB.");
		}

		KieSession ksession = kieHelper.build().newKieSession();
	}
	
@Test
public  void setupKieSessionTest()
{
	  String str = "";
	    str += "package org.kie.test\n";
	    str += "global java.util.List list\n";
	    str += "rule rule1\n";
	    str += "when\n";
	    str += "  Integer(intValue > 0)\n";
	    str += "then\n";
	    str += "  list.add( 1 );\n";
	    str += "  System.out.println(\"Added!\");\n";
	    str += "end\n";
	    str += "\n";
	    
	String rulesGroup = "GRP_RULES_TEST";
	List<Tuple2<String,String>> rules = new ArrayList<Tuple2<String,String>>();
	List<Tuple2<String,Object>> globals = new ArrayList<Tuple2<String,Object>>();
	EventBus eb = null;
	
	rules.add(Tuple.of("rule1",str));
	
	 List<?> list = new ArrayList<Object>();
	 globals.add(Tuple.of("list",list));
	 
	EBCHandlers.setupKieSession(rulesGroup, rules) ;
	
	
	Map<String, KieBase> cache = EBCHandlers.getKieBaseCache();
	Integer count = cache.size();
	
	System.out.println("Loaded Test Kie Session with "+count+" ruleGroups");
	
		 
	 List<Object> facts = new ArrayList<Object>();
	 facts.add(1);
	 facts.add(2);
	 facts.add(3);
	 
	EBCHandlers.executeStatefull(rulesGroup, eb , globals, facts) ;

	
	    assertThat( list.size(), is(3) );
	
	
}

}