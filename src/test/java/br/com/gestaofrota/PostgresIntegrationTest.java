package br.com.gestaofrota;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Banco isolado compartilhado pela suíte de integração, sem depender do .env local. */
public abstract class PostgresIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    protected static String jdbcUrl() { return POSTGRES.getJdbcUrl(); }
    protected static String databaseUsername() { return POSTGRES.getUsername(); }
    protected static String databasePassword() { return POSTGRES.getPassword(); }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.admin.name", () -> "Administrador de Teste");
        registry.add("app.admin.email", () -> "admin.test@exemplo.com");
        registry.add("app.admin.password", () -> "senha-de-teste");
        registry.add("app.admin.sector-name", () -> "Administrativo de Teste");
        registry.add("app.jwt.secret", () -> "segredo-de-teste-com-mais-de-32-caracteres");
        registry.add("app.jwt.expiration", () -> "PT1H");
        registry.add("app.jwt.issuer", () -> "gestao-frotas-test");
    }
}
