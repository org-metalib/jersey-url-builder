package org.metalib.net.url;

import org.junit.jupiter.api.Test;
import org.metalib.net.url.jersey.JerseyUriBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UrlBuilderTest {

    @Test
    void test() {
        final var url = new JerseyUriBuilder()
                .uri("https://postman-echo.com/get/{p1}/{p2}")
                .host("localhost")
                .build("path-value1<>?,./", "path-value2;':\"");
        assertEquals("https://localhost/get/path-value1%3C%3E%3F,.%2F/path-value2%3B':%22", url.toString());
    }
}
