package life.genny.channels;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.MessageProducer;

public class EBProducers {
	
	private static MessageProducer<JsonObject> toCmds;
	private static MessageProducer<JsonObject> toData;
	private static MessageProducer<JsonObject> toEvents;


	/**
	 * @return the toCmds
	 */
	public static MessageProducer<JsonObject> getToCmds() {
		return toCmds;
	}
	

	/**
	 * @param toCmds the toCmds to set
	 */
	public static void setToCmds(MessageProducer<JsonObject> toCmds) {
		EBProducers.toCmds = toCmds;
	}
	
	
	
	
	/**
	 * @return the toData
	 */
	public static MessageProducer<JsonObject> getToData() {
		return toData;
	}


	/**
	 * @param toData the toData to set
	 */
	public static void setToData(MessageProducer<JsonObject> toData) {
		EBProducers.toData = toData;
	}


	/**
	 * @return the toEvents
	 */
	public static MessageProducer<JsonObject> getToEvents() {
		return toEvents;
	}


	/**
	 * @param toEvents the toEvents to set
	 */
	public static void setToEvents(MessageProducer<JsonObject> toEvents) {
		EBProducers.toEvents = toEvents;
	}


	public static void registerAllProducers(EventBus eb){
		setToCmds(eb.publisher("cmds"));
		setToData(eb.publisher("data"));
		setToEvents(eb.publisher("events"));
	}
}
