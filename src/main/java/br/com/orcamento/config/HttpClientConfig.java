package br.com.orcamento.config;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
@Configuration
public class HttpClientConfig {
    @Bean RestClientCustomizer timeoutCustomizer() {
        return builder -> {
            var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
            factory.setReadTimeout(Duration.ofSeconds(60));builder.requestFactory(factory);
        };
    }
}
