package life.genny.channels;

import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.Message;
import rx.Observable;

public class EBConsumers {
	
	private static Observable<Message<Object>> fromEvents;
	private static Observable<Message<Object>> fromData;
	private static Observable<Message<Object>> fromServices;
	private static Observable<Message<Object>> fromCmds;	
	/**
	 * @return the events
	 */
	public static Observable<Message<Object>> getFromEvents() {
		return fromEvents;
	}
	
	/**
	 * @param events the events to set
	 */
	public static void setFromEvents(Observable<Message<Object>> events) {
		EBConsumers.fromEvents = events;
	}
	
	/**
	 * @return the data
	 */
	public static Observable<Message<Object>> getFromData() {
		return fromData;
	}
	
	/**
	 * @param data the data to set
	 */
	public static void setFromData(Observable<Message<Object>> data) {
		EBConsumers.fromData = data;
	}
	
	
	
	
	/**
	 * @return the fromRules
	 */
	public static Observable<Message<Object>> getFromServices() {
		return fromServices;
	}

	/**
	 * @param fromRules the fromRules to set
	 */
	public static void setFromServices(Observable<Message<Object>> fromServices) {
		EBConsumers.fromServices = fromServices;
	}

	
	
	/**
	 * @return the fromCmds
	 */
	public static Observable<Message<Object>> getFromCmds() {
		return fromCmds;
	}

	/**
	 * @param fromCmds the fromCmds to set
	 */
	public static void setFromCmds(Observable<Message<Object>> fromCmds) {
		EBConsumers.fromCmds = fromCmds;
	}

	public static void registerAllConsumer(EventBus eb){
		setFromData(eb.consumer("data").toObservable());
		setFromEvents(eb.consumer("events").toObservable());
		setFromServices(eb.consumer("services").toObservable());
		setFromCmds(eb.consumer("cmds").toObservable());
	}
	
}
