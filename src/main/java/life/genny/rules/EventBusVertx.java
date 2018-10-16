package life.genny.rules;

import javax.naming.NamingException;

import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.eventbus.EventBusInterface;

public class EventBusVertx implements EventBusInterface{

	EventBus eventBus;
	
	public EventBusVertx(EventBus delegate) {
		this.eventBus = delegate;
	}

	@Override
	public void send(String channel, Object msg) throws NamingException {
		// TODO Auto-generated method stub
		eventBus.send(channel, msg);
	}

}
