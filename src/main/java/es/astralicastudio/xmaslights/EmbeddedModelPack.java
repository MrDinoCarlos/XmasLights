package es.astralicastudio.xmaslights;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Serves exactly one immutable ZIP from memory; never exposes a directory or arbitrary file. */
final class EmbeddedModelPack implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final String path, publicBase;
    EmbeddedModelPack(String bind, int port, String publicBase, byte[] bytes, byte[] sha1) throws IOException {
        this.publicBase = publicBase.trim().replaceAll("/+$", "");
        String digest = HexFormat.of().formatHex(sha1);
        path = "/xmaslights/" + digest + "/" + ModelPackService.FILE_NAME;
        byte[] immutable = bytes.clone();
        server = HttpServer.create(new InetSocketAddress(bind, port), 32);
        AtomicInteger counter = new AtomicInteger();
        executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), work -> {
            Thread thread = new Thread(work, "XmasLights-pack-" + counter.incrementAndGet()); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                if (!exchange.getRequestURI().getRawPath().equals(path) || exchange.getRequestURI().getRawQuery() != null) { exchange.sendResponseHeaders(404, -1); return; }
                String method = exchange.getRequestMethod();
                if (!method.equals("GET") && !method.equals("HEAD")) { exchange.getResponseHeaders().set("Allow", "GET, HEAD"); exchange.sendResponseHeaders(405, -1); return; }
                var headers = exchange.getResponseHeaders();
                headers.set("Content-Type", "application/zip"); headers.set("X-Content-Type-Options", "nosniff");
                headers.set("Cache-Control", "public, max-age=31536000, immutable"); headers.set("ETag", "\"" + digest + "\"");
                headers.set("Content-Length", Integer.toString(immutable.length));
                exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : immutable.length);
                if (method.equals("GET")) exchange.getResponseBody().write(immutable);
            }
        });
        server.start();
    }
    static void validateBase(String address) {
        ModelPackService.validateUrl(address);
        if (!address.isBlank()) {
            URI uri = URI.create(address.trim());
            if (uri.getQuery() != null) throw new IllegalArgumentException("model-pack.embedded.public-url: base URL without query");
        }
    }
    int port() { return server.getAddress().getPort(); }
    String url(String host) {
        if (!publicBase.isBlank()) return publicBase + path;
        try { return new URI("http", null, host, port(), path, null, null).toASCIIString(); }
        catch (URISyntaxException failure) { throw new IllegalArgumentException("model-pack: invalid advertised host", failure); }
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
