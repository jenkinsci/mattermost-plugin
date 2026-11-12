package jenkins.plugins.mattermost.workflow;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

public class TestListener implements AutoCloseable {
  public final int port;
  public final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
  private final HttpServer server;
  private final AtomicInteger requests = new AtomicInteger();
  private volatile IntUnaryOperator responseStatus = request -> 200;

  private TestListener(String path) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    port = server.getAddress().getPort();
    server.createContext(path, exchange -> {
      try (exchange) {
        messages.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = "This is the response".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus.applyAsInt(requests.incrementAndGet()), response.length);
        exchange.getResponseBody().write(response);
      }
    });
    server.start();
  }

  public static TestListener create(String path) throws IOException {
    return new TestListener(path);
  }

  public void setResponseStatus(IntUnaryOperator responseStatus) {
    this.responseStatus = responseStatus;
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
