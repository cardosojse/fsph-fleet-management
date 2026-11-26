package br.com.gestaofrota;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Banco isolado compartilhado pela suíte de integração, sem depender do .env local. */
public abstract class PostgresIntegrationTest {
    private static final String EXTERNAL_URL = System.getenv("FSPH_TEST_JDBC_URL");
    private static final PostgreSQLContainer POSTGRES;
    static {
        if (EXTERNAL_URL == null || EXTERNAL_URL.isBlank()) {
            POSTGRES = new PostgreSQLContainer("postgres:17-alpine"); POSTGRES.start();
        } else {
            if (!EXTERNAL_URL.startsWith("jdbc:postgresql://127.0.0.1:") || !EXTERNAL_URL.endsWith("/fsph_svo_test"))
                throw new IllegalStateException("Use um PostgreSQL local isolado chamado fsph_svo_test");
            POSTGRES = null;
        }
    }
    protected static String jdbcUrl() { return POSTGRES == null ? EXTERNAL_URL : POSTGRES.getJdbcUrl(); }
    protected static String databaseUsername() { return POSTGRES == null ? "fsph_svo_test" : POSTGRES.getUsername(); }
    protected static String databasePassword() { return POSTGRES == null ? "" : POSTGRES.getPassword(); }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", PostgresIntegrationTest::databaseUsername);
        registry.add("spring.datasource.password", PostgresIntegrationTest::databasePassword);
        registry.add("app.admin.name", () -> "Administrador de Teste");
        registry.add("app.admin.email", () -> "admin.test@exemplo.com");
        registry.add("app.admin.password", () -> "senha-de-teste");
        registry.add("app.admin.sector-name", () -> "Administrativo de Teste");
        registry.add("app.jwt.secret", () -> "segredo-de-teste-com-mais-de-32-caracteres");
        registry.add("app.jwt.expiration", () -> "PT1H");
        registry.add("app.jwt.issuer", () -> "gestao-frotas-test");
    }
}
