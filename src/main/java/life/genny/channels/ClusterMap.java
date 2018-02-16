package life.genny.channels;

import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpServerRequest;
import io.vertx.rxjava.core.shareddata.AsyncMap;
import io.vertx.rxjava.core.shareddata.SharedData;
import io.vertx.rxjava.ext.web.RoutingContext;

public class ClusterMap {

  private static Vertx vertxContext;

  /**
   * @return the vertxContext
   */
  public static Vertx getVertxContext() {
    return vertxContext;
  }

  /**
   * @param vertxContext the vertxContext to set
   */
  public static void setVertxContext(Vertx vertxContext) {
    ClusterMap.vertxContext = vertxContext;
  }

  public static void mapDTT(final RoutingContext context) {

    final HttpServerRequest req = context.request().bodyHandler(boddy -> {
      JsonObject wifiPayload = boddy.toJsonObject();
      if (wifiPayload == null) {
        context.request().response().headers().set("Content-Type", "application/json");
        JsonObject err = new JsonObject().put("status", "error");
        context.request().response().headers().set("Content-Type", "application/json");
        context.request().response().end(err.encode());
      } else {
        // a JsonObject wraps a map and it exposes type-aware getters
        String param1 = wifiPayload.getString("key");
        System.out.println("CACHE KEY:" + param1);
        String param2 = wifiPayload.getString("json");
        SharedData sd = getVertxContext().sharedData();
        if (System.getenv("GENNY_DEV") == null) {

          sd.getClusterWideMap("shared_data", (AsyncResult<AsyncMap<String, String>> res) -> {
            if (res.failed() || param1 == null || param2 == null) {
              JsonObject err = new JsonObject().put("status", "error");
              context.request().response().headers().set("Content-Type", "application/json");
              context.request().response().end(err.encode());
            } else {
              AsyncMap<String, String> amap = res.result();

              amap.put(param1, param2, (AsyncResult<Void> comp) -> {
                if (comp.failed()) {
                  JsonObject err =
                      new JsonObject().put("status", "error").put("description", "write failed");
                  context.request().response().headers().set("Content-Type", "application/json");
                  context.request().response().end(err.encode());
                } else {
                  JsonObject err = new JsonObject().put("status", "ok");
                  context.request().response().headers().set("Content-Type", "application/json");
                  context.request().response().end(err.encode());
                }
              });
            }
          });
        } else {
          sd.getLocalMap("shared_data").put(param1, param2);
          JsonObject err = new JsonObject().put("status", "ok");
          context.request().response().headers().set("Content-Type", "application/json");
          context.request().response().end(err.encode());

        }
      }

    });

  }
}
