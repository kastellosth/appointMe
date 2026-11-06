package com.kastellosth.appointme.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "appointMe API",
                version = "1.0.0",
                description = """
                        Multi-tenant appointment scheduling and invoicing.

                        **To try it:** register, log in, create a business, then call `POST /auth/select-tenant/{tenantId}`. \
                        Click **Authorize** and paste the tenant token from that response.

                        Another business's data is always 404, never 403, so ids can't be probed.""",
                license = @License(name = "MIT")),
        security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
