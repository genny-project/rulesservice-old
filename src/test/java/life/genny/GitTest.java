package life.genny;

import org.junit.Test;
import java.io.IOException;
import life.genny.qwandautils.QwandaUtils;
import life.genny.utils.RulesUtils;;

public class GitTest {

//  @Test
  public void gitTest() {

    try {
      final String layout = QwandaUtils
          .apiGet(RulesUtils.getLayoutCacheURL("genny", "README.md"), null);
      System.out.println(layout);
    } catch (final IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }


    // try {
    // final String layout = GitUtils.gitGet("master", "genny-project", "layouts", "README.md");
    // System.out.println(layout);
    // } catch (IOException | GitAPIException e) {
    // // TODO Auto-generated catch block
    // e.printStackTrace();
    // }

  }

}
