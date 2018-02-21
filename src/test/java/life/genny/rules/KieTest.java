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
import io.vavr.Tuple3;
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
	  String rule = "";
	    rule += "package org.kie.test\n";
	    rule += "global java.util.List list\n";
	    rule += "rule rule1\n";
	    rule += "when\n";
	    rule += "  Integer(intValue > 0)\n";
	    rule += "then\n";
	    rule += "  list.add( 1 );\n";
	    rule += "  System.out.println(\"Added!\");\n";
	    rule += "end\n";
	    rule += "\n";
	    
	String rulesGroup = "GRP_RULES_TEST";
	List<Tuple3<String,String,String>> rules = new ArrayList<Tuple3<String,String,String>>();
	
	rules.add(Tuple.of("genny","rule1.drl",rule));
	
	List<Tuple2<String,Object>> globals = new ArrayList<Tuple2<String,Object>>();
	 List<?> list = new ArrayList<Object>();
	 globals.add(Tuple.of("list",list));
	 
	RulesLoader.setupKieRules(rulesGroup, rules) ;
	
	
	Map<String, KieBase> cache = RulesLoader.getKieBaseCache();
	Integer count = cache.size();
	
	System.out.println("Loaded Test Kie Session with "+count+" ruleGroups");
	
	EventBus eb = null;

	 List<Object> facts = new ArrayList<Object>();
	 facts.add(1);
	 facts.add(2);
	 facts.add(3);
	 
	 Map<String, String> keyvalue = new HashMap<String, String>();
     keyvalue.put("token", null);
     
	RulesLoader.executeStatefull(rulesGroup,eb , globals, facts, keyvalue) ;

	
	    assertThat( list.size(), is(3) );
		
}

   @Test
   public void drlWhenConditionTest()
   {
	   String rule = "";
	    rule += "package org.kie.test\n";
	    rule += "import java.util.Map;\n";
	    rule += "global java.util.List list\n";
	    rule += "rule rule2\n";
	    rule += "when\n";
	    rule += "  $a : Integer(intValue > 1)\n";
	    rule += "  $map : Map($value: this[\"token\"] != null)\n";
	    rule += "then\n";
	    rule += "  System.out.println(\"value a=\"+$a);\n";
	    rule += "  list.add( $a );\n";
	    rule += "end\n";
	    rule += "\n";
	    
	String rulesGroup = "GRP_RULES_TEST";
	List<Tuple3<String,String,String>> rules = new ArrayList<Tuple3<String,String,String>>();
	List<Tuple2<String,Object>> globals = new ArrayList<Tuple2<String,Object>>();
	EventBus eb = null;
	
	rules.add(Tuple.of("genny","rule2.drl",rule));
	
	 List<?> list = new ArrayList<Object>();
	 globals.add(Tuple.of("list",list));
	RulesLoader.setupKieRules(rulesGroup, rules) ;
	
	
	Map<String, KieBase> cache = RulesLoader.getKieBaseCache();
	Integer count = cache.size();
	
	System.out.println("Loaded Test Kie Session with "+count+" ruleGroups");
	
		 
	 List<Object> facts = new ArrayList<Object>();
	 facts.add(1);
	 facts.add(2);
	 facts.add(3);
	 
     Map<String, String> keyvalue = new HashMap<String, String>();
          keyvalue.put("token", "TOKEN");
	 
	RulesLoader.executeStatefull(rulesGroup, eb , globals, facts, keyvalue) ;

	
	   assertThat( list.size(), is(2) );
  }



}