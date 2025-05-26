package org.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class Main extends AbstractVerticle {
    private HttpClient client;

    @Override
    public void start() {
        // create Router
        Router router = Router.router(vertx);
        router.route("/").handler(StaticHandler.create("webroot").setCachingEnabled(false));

        // Config response SSE
        HttpClient client = vertx.createHttpClient(new HttpClientOptions()
                .setSsl(true)
                .setTrustAll(true)
                .setKeepAlive(true));

        // create server
        vertx.createHttpServer().requestHandler(router).listen(8888, http -> {
            if (http.succeeded()) {
                System.out.println("Server is started!!!!");
            } else {
                System.err.println(" Failed to start server: " + http.cause());
            }
        });

        // Endpoint SSE forward from sse.dev
        router.get("/sse.dev").handler(ctx -> {
            HttpServerResponse clientResponse = ctx.response();
            clientResponse.putHeader("Content-Type", "text/event-stream");
            clientResponse.putHeader("Cache-Control", "no-cache");
            clientResponse.setChunked(true); //stream data
                                            
            // send request to sse.dev
            client.request(HttpMethod.GET, 443, "sse.dev", "/test?interval=1", req -> {
                if (req.succeeded()) {
                    HttpClientRequest httpClientRequest = req.result();
                    httpClientRequest.send(ar -> {
                        if (ar.succeeded()) {
                            HttpClientResponse httpClientResponse = ar.result();

                            // Forward data SSE to frontend
                            httpClientResponse.handler(clientResponse::write);
                            //end stream
                            httpClientResponse.endHandler(v -> {
                                clientResponse.end();
                            });
                            // Handle errors from upstream
                            httpClientResponse.exceptionHandler(err -> {
                                err.printStackTrace();
                                clientResponse.write("data: Error from upstream SSE\n\n");
                                clientResponse.end();
                            });
                        } else {
                            ar.cause().printStackTrace();
                            clientResponse.setStatusCode(500).end("data: Failed to connect to upstream\n\n");
                        }
                    });
                } else {
                    req.cause().printStackTrace();
                    clientResponse.setStatusCode(500).end("data: Unable to initiate SSE request\n\n");
                }
            });
            //cancel request when client close connection
            ctx.request().connection().closeHandler(v -> {
                System.out.println("Connection closed");
            });
        });
    }

    // Close client when server is stopped
//    @Override
//    public void stop() throws Exception {
//        if (this.client != null) {
//            this.client.close();
//        }
//    }

    public static void main(String[] args) {
        System.out.println("Starting SSE server...");
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Main());
    }
}