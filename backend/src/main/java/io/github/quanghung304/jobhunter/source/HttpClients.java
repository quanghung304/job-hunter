package io.github.quanghung304.jobhunter.source;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds one {@link RestClient} per source with the shared timeouts and the polite
 * User-Agent {@code job-hunter/0.1 (+personal use; contact: <OWNER_CONTACT_EMAIL>)}.
 */
@Component
public class HttpClients {

    private final RestClient.Builder builder;
    private final HttpProperties properties;
    private final String userAgent;

    public HttpClients(RestClient.Builder builder, HttpProperties properties,
            @Value("${OWNER_CONTACT_EMAIL:}") String ownerContactEmail) {
        this.builder = builder;
        this.properties = properties;
        this.userAgent = userAgent(ownerContactEmail);
    }

    public RestClient forBaseUrl(String baseUrl) {
        var http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    public String userAgent() {
        return userAgent;
    }

    static String userAgent(String ownerContactEmail) {
        return ownerContactEmail == null || ownerContactEmail.isBlank()
                ? "job-hunter/0.1 (+personal use)"
                : "job-hunter/0.1 (+personal use; contact: " + ownerContactEmail.strip() + ")";
    }
}
