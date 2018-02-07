package life.genny.channels;

import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.ext.web.Router;


public class Routers {

  private static int serverPort =  System.getenv("API_PORT") != null ? (Integer.parseInt(System.getenv("API_PORT"))) : 8088;

 
  public static void routers(final Vertx vertx) {
    final Router router = Router.router(vertx);

    router.route().handler(RouterHandlers.cors());
    router.route(HttpMethod.POST, "/write/:param1/:param2").handler(RouterHandlers::apiMapPutHandler);
    router.route(HttpMethod.GET, "/read/:param1").handler(RouterHandlers::apiMapGetHandler);
    router.route(HttpMethod.GET, "/version").handler(VersionHandler::apiGetVersionHandler);

    
    vertx.createHttpServer().requestHandler(router::accept).listen(serverPort);
  }
  
}
