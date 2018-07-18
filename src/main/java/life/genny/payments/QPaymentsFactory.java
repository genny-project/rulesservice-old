package life.genny.payments;

import java.util.Map;

import io.vertx.rxjava.core.eventbus.EventBus;
import life.genny.payments.QPaymentsManagers.QAssemblyPaymentsManager;
import life.genny.qwanda.payments.QPaymentsServiceProvider;
import life.genny.rules.QRules;

import org.drools.core.spi.KnowledgeHelper;

public class QPaymentsFactory {
	

	private QRules rules;


	public QPaymentsFactory( QRules rules) {

		this.rules = rules;
	}
	
	public QPaymentsProvider getMessageProvider(QPaymentsServiceProvider paymentsServiceProvider)
	{
		QPaymentsProvider paymentsProvider;
		System.out.println("Payments provider type::" + paymentsServiceProvider.toString());
		
		switch (paymentsServiceProvider) {
		case ASSEMBLY:
			paymentsProvider = new QAssemblyPaymentsManager(this.rules);
		default:
			paymentsProvider = new QAssemblyPaymentsManager(this.rules);
		}
		return paymentsProvider;
	}

}
