package org.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class Main extends AbstractVerticle {
    @Override
    public void start(){
        Router router = Router.router(vertx);
        router.route("/").handler(StaticHandler.create("webroot").setCachingEnabled(false));
        router.get("/sse").handler(ctx ->{
            HttpServerResponse response = ctx.response();
            response.putHeader("Content-Type", "text/event-stream");
            response.putHeader("Cache-Control", "no-cache");
            response.setChunked(true);

            //Send data every second
            long timeId = vertx.setPeriodic(1000, id -> {
                String msg = "data: {\"testing\": true, \"sse_dev\": \"It's great\", \"now\": " + System.currentTimeMillis() + "}\n\n";
                if(ctx.failed()){
                    System.out.println("Failed to send message!!");
                } else {
                    response.write(msg);
                }

             });
            //close the connection
            ctx.request().connection().closeHandler(closeEvent -> {
                System.out.println("Connection closed");
                vertx.cancelTimer(timeId);
            });
        });
        //connect to the server
        vertx.createHttpServer().requestHandler(router).listen(8888, result -> {
            if (result.succeeded()) {
                System.out.println("Server is started!!");
            } else {
                System.out.println("Failed to start server: " + result.cause());
            }
        });
    }

    public static void main(String[] args) {
        System.out.println("Starting SSE server...");
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Main());
    }
}
