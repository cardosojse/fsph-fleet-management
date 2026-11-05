# Homologação no Render com Neon

## Repositório e isolamento

Backend oficial: <https://github.com/cardosojse/fsph-fleet-management>.
Branch de deploy: `chore/deploy-render`, criada de `origin/main` em `9ee0dee`.
O trabalho foi feito em `C:\Users\barbo\fsph-fleet-management-render`, separado
do checkout original com alterações locais. Nenhuma alteração desse checkout
foi incorporada. A tentativa anterior já havia sido revertida com backup;
nenhuma branch preexistente foi excluída nesta entrega.

O frontend oficial é outro repositório:
<https://github.com/cardosojse/fsph-fleet-management-web>. Não são usados arquivos
de frontend de branches do backend. Figma, Jira e Mermaid indicados na tarefa
não ficaram acessíveis pelas ferramentas disponíveis; a análise se baseou no
código e nas migrations reais.

## Configuração implementada

- Java 21, Spring Boot 4.1.1, Maven e PostgreSQL JDBC existentes, sem trocar dependências.
- Dockerfile em duas etapas, Maven/Temurin 21 e runtime Temurin 21, usuário `10001:10001`.
- Contexto Docker limitado a `pom.xml` e `src`, sem `.git`, `.env`, logs ou credenciais locais.
- Perfil `prod`, porta `${PORT:8080}` e suporte aos cabeçalhos do proxy HTTPS.
- Conexão pooled para JPA e conexão direta para Flyway, com TLS `verify-full`,
  verificação de hostname, trust store Java e channel binding obrigatório.
- Hikari com até cinco conexões por padrão, Hibernate `validate` e Flyway com
  limpeza desabilitada. Todas as 18 migrations existentes foram preservadas.
- Health check `/actuator/health`, com resposta sem componentes ou detalhes internos.
- Logs de SQL e parâmetros não habilitados em produção.
- Reutilização integral de CORS, permissões, autenticação e filtros JWT existentes.
  Nenhuma classe Java de domínio, controller, service, DTO ou repository foi alterada.

O perfil padrão de desenvolvimento e o Docker Compose local continuam usando a
configuração existente. `prod` não depende do PostgreSQL do Compose.

## Variáveis para um NOVO serviço Render de homologação

| Variável | Valor/uso |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | Fornecida pelo Render; Blueprint usa `10000` |
| `NEON_HOST` | Hostname pooled da branch isolada, com `-pooler` |
| `NEON_HOST_UNPOOLED` | Hostname direto da mesma branch |
| `NEON_DATABASE` | Nome do banco de homologação |
| `NEON_USER` | Usuário próprio da homologação |
| `NEON_PASSWORD` | Senha desse usuário, cadastrada como segredo |
| `DB_POOL_SIZE` | Opcional, padrão `5` |
| `CORS_ALLOWED_ORIGINS` | Origem HTTPS confirmada do frontend Vercel, sem caminho nem barra final |
| `ADMIN_NAME` | Nome do administrador inicial da homologação |
| `ADMIN_EMAIL` | E-mail válido do administrador inicial |
| `ADMIN_PASSWORD` | Senha inicial forte, cadastrada como segredo |
| `JWT_SECRET` | Segredo próprio; o Blueprint pode gerar um valor aleatório |
| `MAIL_NOTIFICATIONS_ENABLED` | `false` nesta homologação, sem envio a destinatários reais |

Os hosts não devem conter protocolo, usuário, senha, porta, caminho ou query.
O perfil monta as URLs JDBC e passa as credenciais separadamente. Nunca usar
hosts/credenciais de produção nesta configuração de homologação.

O CORS existente aceita origens exatas em uma lista separada por vírgulas. Não
usar `*` nem padrões que incluam todos os previews Vercel. Como a publicação do
frontend não foi realizada, sua origem pública permanece pendente; ela não foi
inventada nem inserida em serviço compartilhado.

## Publicação preparada

`render.yaml` descreve um novo serviço `fsph-fleet-api-staging`, Docker, região
Ohio, branch `chore/deploy-render`, health check e deploys automáticos desligados.
Os valores `sync: false` devem ser preenchidos no painel do novo serviço.

Antes de aplicar, confirmar que o nome não pertence a um serviço existente da
equipe. Não vincular o Blueprint a serviços ou grupos de variáveis compartilhados.
Depois de configurar o novo serviço, disparar um deploy manual, aguardar saúde,
confirmar sua URL HTTPS e fornecê-la como `VITE_API_URL` ao frontend. Após obter
a URL Vercel, cadastrar essa origem exata no novo backend e validar o CORS.

Não foi encontrado acesso autenticado ao Render CLI/API. O acesso automatizado
ao navegador também estava indisponível. Nenhum serviço Render foi criado ou
alterado e nenhuma URL pública de backend foi confirmada.

## Neon de homologação criado

Foi criada a branch **schema-only** `preview/fsph-deploy-render-20261105`, ID
`br-muddy-unit-be9fgdze`, no projeto existente. Nenhum dado de produção foi
copiado. Nela foram criados o banco vazio `fleet_staging` e o usuário
`fleet_staging_owner`, próprios desta validação. O banco inicial foi verificado
com zero tabelas em `public`, antes de iniciar o Flyway.

A branch expira em **12/11/2026 às 16h, America/Fortaleza**. Para manter uma
homologação depois dessa data, ajustar a retenção dessa branch ou provisionar
outro ambiente isolado; não apontar o serviço para produção como substituição.

As credenciais geradas estão somente em arquivos locais ignorados, incluindo
`.env.staging.local`. Não foram incluídas em commits, exemplos ou logs publicados.
Arquivos `.env` preexistentes não foram alterados. Nenhuma tabela, configuração,
senha ou migration da branch de produção foi modificada.

O cliente SQL Windows precisou de um bundle de CAs confiáveis instalado com o
Git. A verificação foi mantida em `verify-full`; `psql` confirmou TLS 1.3 e
validação de certificado/hostname. O perfil Java também conectou com sua trust
store, sem desabilitar validação SSL.

## Validação e operação local reproduzível

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
.\mvnw.cmd -B verify
docker build -t fsph-fleet-api:deploy-render .
docker run --name fsph-staging-validation --env-file .env.staging.local `
  -p 127.0.0.1:18082:10000 fsph-fleet-api:deploy-render
```

O arquivo de ambiente deve pertencer exclusivamente à homologação. Para testar
HTTP, configurar no processo `APP_URL`, `FRONTEND_ORIGIN`, `ADMIN_EMAIL`,
`ADMIN_PASSWORD` e `SMOKE_ALLOW_WRITES=isolated-staging`, então executar:

```bash
node scripts/smoke-deploy.mjs
```

O smoke exige Node.js 22 ou superior, verifica saúde, CORS permitido/bloqueado,
401, login JWT, identidade, criação/leitura/remoção de um setor próprio,
validação HTTP 400, logout e rejeição do token revogado. Só deve ser executado
em homologação isolada. Não imprime senhas ou tokens.

Resultados confirmados até a preparação do relatório:

- Base anterior: **133 testes passaram**, `verify` e JAR com sucesso.
- Após a configuração: **133 testes passaram**, sem erros, falhas ou testes ignorados;
  JAR executável gerado com sucesso em Java 21.
- Docker: primeira tentativa falhou ao resolver Maven Central; a repetição após
  a recuperação do DNS construiu a imagem completa com sucesso, sem mudar dependências.
- Contêiner validado com runtime Java **21.0.13**, usuário `10001:10001` e porta dinâmica.
- Neon remoto: **18 migrations aplicadas com sucesso**, última versão `19`, sem
  modificar os arquivos versionados; nenhum setor temporário do smoke ficou no banco.
- Contêiner com `prod` e Neon remoto: smoke HTTP passou em saúde, CORS permitido e
  bloqueado, JWT, endpoints protegidos, persistência CRUD, validação e revogação no logout.
- O frontend executou **3 testes Playwright com sucesso**, incluindo autenticação
  cross-origin real contra essa API de homologação, com CORS aplicado pelo navegador.

Logs locais ignorados: `target/deploy-baseline.log`, `target/deploy-final-verify.log`,
`target/docker-build.log` (tentativa com falha), `target/docker-build-retry.log`
(sucesso), `target/http-smoke.log` e `target/neon-verification.log`.

## Arquivos e revisão

Criados: `Dockerfile`, `.dockerignore`, `render.yaml`,
`src/main/resources/application-prod.properties`, `scripts/smoke-deploy.mjs` e este documento.
Modificados: `.env.example`, `.gitignore` e o link de documentação no `README.md`.

Não houve merge em `main` nem publicação automática. A descrição do PR deve
distinguir a preparação Render, a validação local com Neon remoto e a publicação
pública pendente.

Referências verificadas: [Render Blueprint](https://render.com/docs/blueprint-spec),
[pgJDBC TLS](https://jdbc.postgresql.org/documentation/ssl/),
[Neon connection pooling](https://neon.com/docs/connect/connection-pooling).
