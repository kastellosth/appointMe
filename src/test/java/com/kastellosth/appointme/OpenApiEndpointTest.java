package com.kastellosth.appointme;

import com.kastellosth.appointme.support.ApiClient;
import com.kastellosth.appointme.support.DatabaseTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OpenApiEndpointTest extends DatabaseTestBase {

    @Value("${local.server.port}")
    private int port;

    @Test
    void swaggerUiIsPublic() {
        ApiClient api = new ApiClient(port);

        assertEquals(200, api.send("GET", "/swagger-ui/index.html", null, null).status());
    }

    @Test
    void theSpecDescribesTheApiAndItsLogin() {
        JsonNode spec = new ApiClient(port).expect(200, "GET", "/v3/api-docs", null, null).json();

        assertEquals("bearer", spec.at("/components/securitySchemes/bearer/scheme").asString());
        assertEquals("bearer", spec.at("/security/0").propertyNames().iterator().next());
        for (String path : List.of("/auth/login", "/appointments", "/invoices/{invoiceId}/payments", "/availability")) {
            assertTrue(spec.at("/paths").has(path), path + " is missing from the spec");
        }
        assertTrue(spec.at("/paths/~1auth~1login/post/security").isEmpty(), "login must not ask for a token");
        assertTrue(spec.at("/paths/~1customers/get/parameters").toString().contains("\"size\""), "paging must be plain page, size and sort");
        assertEquals("Idempotency-Key", spec.at("/paths/~1invoices~1{invoiceId}~1payments/post/parameters/1/name").asString());
    }

    @Test
    void aMissingOrBadTokenStillCannotReachTheRestOfTheActuator() {
        assertEquals(401, new ApiClient(port).send("GET", "/actuator/env", null, null).status());
    }
}
