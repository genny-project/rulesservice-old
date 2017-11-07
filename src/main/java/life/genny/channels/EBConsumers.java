package life.genny.channels;

import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.Message;
import rx.Observable;

public class EBConsumers {
	
	private static Observable<Message<Object>> fromEvents;
	private static Observable<Message<Object>> fromData;
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
	
	public static void registerAllConsumer(EventBus eb){
		setFromData(eb.consumer("data").toObservable());
		setFromEvents(eb.consumer("events").toObservable());
	}
	
}
