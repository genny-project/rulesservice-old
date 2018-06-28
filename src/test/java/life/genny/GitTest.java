package life.genny;

import org.junit.Test;
import java.io.IOException;
import life.genny.qwandautils.QwandaUtils;
import life.genny.rules.RulesUtils;;

public class GitTest {

//  @Test
  public void gitTest() {

    try {
      final String layout = QwandaUtils
          .apiGet(RulesUtils.getLayoutCacheURL("README.md"), null);
      System.out.println(layout);
    } 
    catch (final IOException e) {
    		System.out.println(e.getMessage());
    }
  }

}
