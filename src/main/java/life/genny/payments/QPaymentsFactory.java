package life.genny.payments;

import java.util.Map;

import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.payments.QPaymentsManagers.QAssemblyPaymentsManager;
import life.genny.qwanda.payments.QPaymentsServiceProvider;
import life.genny.rules.QRules;

import org.drools.core.spi.KnowledgeHelper;

public class QPaymentsFactory {
	
	private Map<String, Object> decodedMapToken;
	private String token;
	private String realm;
	private String qwandaServiceUrl;
	private EventBus eventBus;
	KnowledgeHelper drools;


	public QPaymentsFactory(String qwandaServiceUrl, String token, Map<String, Object> decodedMapToken, String realm, EventBus eventBus, KnowledgeHelper drools) {

		this.decodedMapToken = decodedMapToken;
		this.qwandaServiceUrl = qwandaServiceUrl;
		this.token = token;
		this.realm = realm;
		this.eventBus = eventBus;
		this.drools = drools;
	}
	
	public QPaymentsProvider getMessageProvider(QPaymentsServiceProvider paymentsServiceProvider)
	{
		QPaymentsProvider paymentsProvider;
		System.out.println("Payments provider type::" + paymentsServiceProvider.toString());
		
		switch (paymentsServiceProvider) {
		case ASSEMBLY:
			paymentsProvider = new QAssemblyPaymentsManager(this.qwandaServiceUrl, this.token, this.decodedMapToken, this.realm, this.eventBus, this.drools);
		default:
			paymentsProvider = new QAssemblyPaymentsManager(this.qwandaServiceUrl, this.token, this.decodedMapToken, this.realm, this.eventBus, this.drools);
		}
		return paymentsProvider;
	}

}
