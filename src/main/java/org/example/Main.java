package org.example;

//import io.vertx.core.AbstractVerticle;
//import io.vertx.core.Promise;
//import io.vertx.core.Vertx;
//import io.vertx.core.http.HttpClientResponse;
//import io.vertx.core.http.HttpServerResponse;
//import io.vertx.ext.web.Router;
//import io.vertx.ext.web.client.WebClient;
//import io.vertx.ext.web.handler.StaticHandler;
//
//public class Main extends AbstractVerticle {
//    @Override
//    public void start(Promise<Void> startPromise) {
//        // Create a router to handle incoming requests
//        Router router = Router.router(vertx);
//        router.route("/").handler(StaticHandler.create("webroot").setCachingEnabled(false));
//        router.get("/proxy-sse").handler(ctx -> {
//            HttpServerResponse response = ctx.response();
//            response.putHeader("Content-Type", "text/event-stream");
//            response.putHeader("Cache-Control", "no-cache");
//            response.setChunked(true);
//            //Send data every second, phan nay` sai
/// /            long timeId = vertx.setPeriodic(1000, id -> {
/// /                String msg = "data: {\"testing\": true, \"sse_dev\": \"It's great\", \"msg\": \"It's work!\", \"now\": " + System.currentTimeMillis() + "}\n\n";
/// /                if (ctx.failed()) {
/// /                    System.out.println("Failed to send message!!");
/// /                } else {
/// /                    response.write(msg);
/// /                }
/// /            });
/// /            //close the connection
/// /            ctx.request().connection().closeHandler(closeEvent -> {
/// /                System.out.println("Connection closed");
/// /                vertx.cancelTimer(timeId);
/// /            });
/// /        });
/// /        //connect to the server
/// /        vertx.createHttpServer().requestHandler(router).listen(8888, result -> {
/// /            if (result.succeeded()) {
/// /                System.out.println("Server is started!!");
/// /            } else {
/// /                System.out.println("Failed to start server: " + result.cause());
///// /            }
//            WebClient webClient = WebClient.create(vertx);
//            webClient.getAbs("https://sse.dev/test?interval=1")
//                    .send(ar -> {
//                        if (ar.succeeded()) {
//                            HttpClientResponse sseResponse = (HttpClientResponse) ar.result();
//                            sseResponse.handler(buffer -> {
//                                String line = buffer.toString();
//                                response.write(line);
//                            });
//                            sseResponse.endHandler(v -> {
//                                System.out.println("End of stream");
//                                response.end();
//                            });
//                        } else {
//                            response.setStatusCode(500).end("Failed to connect to SSE server");
//                        }
//                    });
//            ctx.request().connection().closeHandler(v -> {
//                System.out.println("Connection closed");
//                webClient.close();
//            });
//        });
//    }
//    public static void main(String[] args) {
//        System.out.println("Starting SSE server...");
//        Vertx vertx = Vertx.vertx();
//        vertx.deployVerticle(new Main());
//    }
//}

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class Main extends AbstractVerticle {

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

        // Endpoint SSE forward from sse.dev
        router.get("/sse.dev").handler(ctx -> {
            HttpServerResponse clientResponse = ctx.response();
            clientResponse.putHeader("Content-Type", "text/event-stream");
            clientResponse.putHeader("Cache-Control", "no-cache");
            clientResponse.setChunked(true); //stream data

            // send request to sse.dev
            client.request(HttpMethod.GET, 443, "sse.dev", "/test?interval=1", req -> {
                if (req.succeeded()) {
                    HttpClientRequest outboundRequest = req.result();
                    outboundRequest.send(ar -> {
                        if (ar.succeeded()) {
                            HttpClientResponse upstreamResponse = ar.result();

                            // Forward data SSE to frontend
                            upstreamResponse.handler(clientResponse::write);
                            //end stream
                            upstreamResponse.endHandler(v -> {
                                clientResponse.end();
                            });
                            // Handle errors from upstream
                            upstreamResponse.exceptionHandler(err -> {
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
                    clientResponse.setStatusCode(500).end("data: Failed to initiate SSE request\n\n");
                }
            });
            //cancel request when client close connection
            ctx.request().connection().closeHandler(v -> {
                System.out.println("Connection closed");
                client.close();
            });
        });

        // create server
        vertx.createHttpServer().requestHandler(router).listen(8888, http -> {
            if (http.succeeded()) {
                System.out.println("Server is started!!!!");
            } else {
                System.err.println(" Failed to start server: " + http.cause());
            }
        });
    }

    public static void main(String[] args) {
        System.out.println("Starting SSE server...");
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Main());
    }
}