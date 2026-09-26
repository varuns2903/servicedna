package io.github.varuns2903.servicedna.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** A service whose /quotes/{symbol} calls its own /prices/{symbol}: one server span per hop. */
@SpringBootApplication
@RestController
public class ExampleApplication {

  private final RestClient restClient;
  private final ApplicationContext context;

  public ExampleApplication(RestClient.Builder restClientBuilder, ApplicationContext context) {
    this.restClient = restClientBuilder.build(); // instrumented by the starter
    this.context = context;
  }

  public static void main(String[] args) {
    SpringApplication.run(ExampleApplication.class, args);
  }

  @GetMapping("/prices/{symbol}")
  public double price(@PathVariable String symbol) {
    return 100 + Math.abs(symbol.hashCode() % 50);
  }

  @GetMapping("/quotes/{symbol}")
  public String quote(@PathVariable String symbol) {
    int port = ((WebServerApplicationContext) context).getWebServer().getPort();
    Double price = restClient.get().uri("http://127.0.0.1:" + port + "/prices/" + symbol).retrieve().body(Double.class);
    return symbol + " @ " + price;
  }
}
