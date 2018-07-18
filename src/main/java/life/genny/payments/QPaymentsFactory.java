package life.genny.payments;

import org.drools.core.spi.KnowledgeHelper;

import life.genny.payments.QPaymentsManagers.QAssemblyPaymentsManager;
import life.genny.qwanda.payments.QPaymentsServiceProvider;
import life.genny.rules.QRules;

public class QPaymentsFactory {
	

	KnowledgeHelper drools;
	private QRules rules;

	public QPaymentsFactory(QRules rules) { 
		
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
