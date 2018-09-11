package life.genny.rules;

import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;

public class KieScannerTest {

	//@Test
	public void scanTest() 
	{
	KieServices kieServices = KieServices.Factory.get();
	ReleaseId releaseId = kieServices.newReleaseId( "life.genny", "rulesTest", "1.0-SNAPSHOT" );
	KieContainer kContainer = kieServices.newKieContainer( releaseId );
	KieScanner kScanner = kieServices.newKieScanner( kContainer );

	// Start the KieScanner polling the Maven repository every 10 seconds
	kScanner.start( 10000L );
	}
}
