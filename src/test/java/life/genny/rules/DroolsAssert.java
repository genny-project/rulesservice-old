package life.genny.rules;

import static java.lang.String.format;
import static java.lang.System.out;
import static java.util.Collections.sort;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Equivalence;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;

import org.drools.core.SessionConfiguration;
import org.drools.core.common.DefaultAgenda;
import org.drools.core.event.DefaultAgendaEventListener;
import org.drools.core.event.DefaultRuleRuntimeEventListener;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.impl.KnowledgeBaseFactory;

import org.drools.core.time.SessionPseudoClock;
import org.kie.api.command.Command;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.rule.Agenda;
import org.kie.api.runtime.rule.FactHandle;

import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.StatefulKnowledgeSession;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for any drools unit/business tests.
 */
public class DroolsAssert {

    private class LoggingAgendaEventListener extends DefaultAgendaEventListener {

        @Override
        public void beforeMatchFired(BeforeMatchFiredEvent event) {
            String ruleName = event.getMatch().getRule().getName();
            out.println(format("==> '%s' has been activated by the tuple %s", ruleName, event.getMatch().getObjects()));

            Integer ruleActivations = rulesActivations.get(ruleName);
            if (ruleActivations == null) {
                rulesActivations.put(ruleName, 1);
            } else {
                rulesActivations.put(ruleName, ruleActivations + 1);
            }
        }
    }

    private class LoggingWorkingMemoryEventListener extends DefaultRuleRuntimeEventListener {
        @Override
        public void objectInserted(ObjectInsertedEvent event) {
            Object fact = event.getObject();
            if (!factsInsertionOrder.containsKey(fact)) {
                factsInsertionOrder.put(fact, factsInsertionOrder.size());
            }
            out.println(format("--> inserted '%s'", fact));
        }

        @Override
        public void objectDeleted(ObjectDeletedEvent event) {
            out.println(format("--> retracted '%s'", event.getOldObject()));
        }

        @Override
        public void objectUpdated(ObjectUpdatedEvent event) {
            out.println(format("--> updated '%s' \nto %s", event.getOldObject(), event.getObject()));
        }
    }

    private final class FactsInsertionOrderComparator implements Comparator<Object> {
        @Override
        public int compare(Object o1, Object o2) {
            return factsInsertionOrder.get(o1).compareTo(factsInsertionOrder.get(o2));
        }
    }

    public static final StatefulKnowledgeSession newStatefulKnowladgeSession(Class<?> clazz, String drl, Map<String, String> properties) {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newClassPathResource(drl, clazz), ResourceType.DRL);

        if (kbuilder.hasErrors()) {
            throw new Error(kbuilder.getErrors().toString());
        }

        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages(kbuilder.getKnowledgePackages());

        SessionConfiguration config = (SessionConfiguration) KnowledgeBaseFactory.newKnowledgeSessionConfiguration();
        for (Map.Entry<String, String> property : properties.entrySet()) {
            config.setProperty(property.getKey(), property.getValue());
        }

        
        //return kbase.newStatefulKnowledgeSession(config, null);
        return kbase.getCachedSession(config, null);
    }

    private StatefulKnowledgeSession session;
    private DefaultAgenda agenda;
    private SessionPseudoClock clock;
    private Map<String, Integer> rulesActivations = new ConcurrentHashMap<>();
    private Map<Object, Integer> factsInsertionOrder = new IdentityHashMap<>();

    public DroolsAssert(Class<?> clazz, String drl) {
        this(newStatefulKnowladgeSession(clazz, drl, ImmutableMap.of(
                "drools.eventProcessingMode", "stream",
                "drools.clockType", "pseudo")));
    }

    public DroolsAssert(StatefulKnowledgeSession session) {
        this.session = session;
        agenda = (DefaultAgenda) session.getAgenda();
        clock = session.getSessionClock();
        session.addEventListener(new LoggingAgendaEventListener());
        session.addEventListener(new LoggingWorkingMemoryEventListener());
    }

    public void dispose() {
        session.dispose();
    }

    public void advanceTime(long amount, TimeUnit unit) {
        clock.advanceTime(amount, unit);
    }

    /**
     * Asserts the only rules listed have been activated no more no less.
     */
    public void assertActivations(String... expected) {
        Map<String, Integer> expectedMap = new HashMap<>();
        for (String rule : expected) {
            expectedMap.put(rule, 1);
        }
        assertActivations(expectedMap);
    }

    /**
     * Asserts the only rules listed have been activated no more no less.<br>
     * Accepts the number of activations to assert.
     */
    public void assertActivations(Map<String, Integer> expectedActivations) {
        Map<String, Integer> expected = new HashMap<>(expectedActivations);
        synchronized (session.getSessionClock()) {
            for (Map.Entry<String, Integer> actual : rulesActivations.entrySet()) {
                if (!expected.containsKey(actual.getKey())) {
                    fail(format("'%s' should not be activated", actual.getKey()));
                } else if (!expected.get(actual.getKey()).equals(actual.getValue())) {
                    fail(format("'%s' should be activated %s time(s) but actially it was activated %s time(s)", actual.getKey(), expected.get(actual.getKey()), actual.getValue()));
                } else {
                    expected.remove(actual.getKey());
                }
            }

            if (!expected.isEmpty()) {
                fail(format("These should be activated: %s", expected.keySet()));
            }
        }
    }

    /**
     * Asserts the only rules listed will be activated no more no less.<br>
     * Waits for scheduled rules if any.
     */
    public void awaitForActivations(String... expected) {
        Map<String, Integer> expectedMap = new HashMap<>();
        for (String rule : expected) {
            expectedMap.put(rule, 1);
        }
        awaitForActivations(expectedMap);
    }

    /**
     * Asserts the only rules listed will be activated no more no less.<br>
     * Waits for scheduled rules if any.<br>
     * Accepts the number of activations to assert.
     */
    public void awaitForActivations(Map<String, Integer> expected) {
        // awaitForScheduledActivations();
        assertActivations(expected);
    }

    /**
     * Await for all scheduled activations to be activated to {@link #printFacts()} thereafter for example.
     */
    public void awaitForScheduledActivations() {
        if (agenda.getActivations().length != 0) {
            out.println("awaiting for scheduled activations");
        }
        while (agenda.getActivations().length != 0) {
            advanceTime(50, MILLISECONDS);
        }
    }

    public void assertNoScheduledActivations() {
        assertTrue("There few more scheduled activations.", agenda.getActivations().length == 0);
    }

    /**
     * Asserts object was successfully inserted to knowledge base.
     */
    public void assertExists(Object objectToMatch) {
        synchronized (session.getSessionClock()) {
            Collection<? extends Object> sessionObjects = session.getObjects();
            Collection<? extends Object> exists = Collections2.filter(sessionObjects, Equivalence.identity().equivalentTo(objectToMatch));
            assertFalse("Object was not found in the session " + objectToMatch, exists.isEmpty());
        }
    }

    /**
     * Asserts object was successfully retracted from knowledge base.
     * 
     * @param obj
     */
    public void assertRetracted(Object retracted) {
        synchronized (session.getSessionClock()) {
            Collection<? extends Object> sessionObjects = session.getObjects();
            Collection<? extends Object> exists = Collections2.filter(sessionObjects, Equivalence.identity().equivalentTo(retracted));
            assertTrue("Object was not retracted from the session " + exists, exists.isEmpty());
        }
    }

    /**
     * Asserts all objects were successfully retracted from knowledge base.
     */
    public void assertAllRetracted() {
        synchronized (session.getSessionClock()) {
            List<Object> facts = new LinkedList<>(session.getObjects());
            assertTrue("Objects were not retracted from the session " + facts, facts.isEmpty());
        }
    }

    /**
     * Asserts exact count of facts in knowledge base.
     * 
     * @param factCount
     */
    public void assertFactCount(long factCount) {
        synchronized (session.getSessionClock()) {
            assertEquals(factCount, session.getFactCount());
        }
    }

    public void setGlobal(String identifier, Object value) {
        session.setGlobal(identifier, value);
    }

    public <T> T execute(Command<T> command) {
        return session.execute(command);
    }

    public List<FactHandle> insert(Object... objects) {
        List<FactHandle> factHandles = new LinkedList<>();
        for (Object object : objects) {
            out.println("inserting " + object);
            factHandles.add(session.insert(object));
        }
        return factHandles;
    }

    public int fireAllRules() {
        out.println("fireAllRules");
        return session.fireAllRules();
    }

    public List<FactHandle> insertAndFire(Object... objects) {
        List<FactHandle> result = new LinkedList<>();
        for (Object object : objects) {
            result.addAll(insert(object));
            fireAllRules();
        }
        return result;
    }

    public void printFacts() {
        synchronized (session.getSessionClock()) {
            List<Object> sortedFacts = new LinkedList<>(session.getObjects());
            sort(sortedFacts, new FactsInsertionOrderComparator());
            out.println(format("Here are %s session facts in insertion order: ", session.getFactCount()));
            for (Object fact : sortedFacts) {
                out.println(fact);
            }
        }
    }
}


