package life.genny.routes;

import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.CorsHandler;

public class Routers {

  private static int serverPort = 8080;


  public static CorsHandler cors() {
    return CorsHandler.create("*").allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.POST)
        .allowedMethod(HttpMethod.OPTIONS).allowedHeader("X-PINGARUNER")
        .allowedHeader("Content-Type").allowedHeader("X-Requested-With");
  }
  
  public static void routers(final Vertx vertx) {
    final Router router = Router.router(vertx);
    router.route().handler(cors());
    router.route(HttpMethod.GET, "/version").handler(VersionHandler::apiGetVersionHandler);

    vertx.createHttpServer().requestHandler(router::accept).listen(serverPort);
  }
}
