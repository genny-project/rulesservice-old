package life.genny.channels;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.MessageProducer;

public class EBProducers {
	
	private static MessageProducer<JsonObject> toCmds;

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
	
	public static void registerAllProducers(EventBus eb){
		setToCmds(eb.publisher("cmds"));
	}
}
