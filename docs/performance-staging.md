# Deploy e acesso de homologação — 11/11/2026

## Escopo e isolamento

Branch `perf/staging-deploy-login-20261111`, criada de
`chore/preview-integracao-telas` (`2281d1f`) em um worktree novo. O PR tem essa
branch de staging como base para não misturar a integração anterior com a revisão
de performance. Não foram feitos merges, force pushes ou commits nas branches da
equipe. Nenhuma migration foi criada ou modificada; produção não foi acessada.

## Build do Render

O Dockerfile anterior copiava `src` antes da única execução Maven. Qualquer
alteração de fonte invalidava a camada que continha todos os downloads. Agora
`dependency:go-offline` roda após copiar apenas `pom.xml`; a camada de dependências
é reaproveitada em alterações de fonte. O empacotamento continua compilando os
testes com a configuração existente. A execução completa dos testes PostgreSQL
continua obrigatória na CI (`./mvnw -B verify`), com cache Maven já habilitado.

Java 21, verificação TLS, usuário sem privilégios no contêiner, validação Hibernate,
Flyway e autenticação JWT foram preservados. `.dockerignore` já limita o contexto
ao POM e a `src`; credenciais, Git e artefatos não entram na imagem.

O primeiro build após alterar o POM perde o cache de dependências e pode demorar
mais: a resolução antecipada inclui plugins. Não limpar o cache em deploys comuns.
Uma mudança de fonte deve mostrar `CACHED` nessa camada; uma mudança de POM deve
resolvê-la novamente. Medições locais e limites estão registrados na validação.

## Conta de demonstração

Somente o serviço isolado `fsph-fleet-api-staging` deve receber:

| Variável | Valor/configuração |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `staging` |
| `APP_ENVIRONMENT` | `staging` |
| `STAGING_DEMO_ENABLED` | `true` |
| `STAGING_DEMO_PASSWORD` | Segredo definido no ambiente; ao menos 12 caracteres e no máximo 72 bytes UTF-8 |

O perfil `staging` importa as mesmas configurações de conexão segura do perfil
`prod`, sem ativar esse perfil. Manter os hosts pooled/direct separados e o banco
exclusivo de homologação. As variáveis de administrador existentes continuam
necessárias; a conta administrativa não é substituída.

O usuário reservado é `demo@frota.test`, nome e setor `Demonstracao de Homologacao`,
perfil ADMIN. A senha nunca é retornada ou registrada. O bootstrap é transacional:
cria o usuário ou atualiza a senha BCrypt mantendo sua identidade e seus vínculos.
Não regrava um hash que já corresponde à senha. Com o opt-in ativo, reativa somente
essa conta gerenciada. Se e-mail, nome, setor e perfil não identificarem a conta
esperada, falha sem assumir outra conta nem elevar permissões.

Os perfis `prod`, `production`, padrão e as combinações `staging,prod` ou
`staging,production` não registram o bootstrap. A autenticação também rejeita o
e-mail reservado fora do staging autorizado, inclusive ao recarregar um JWT, mesmo
se uma conta demo estiver presente no banco. Desligar `STAGING_DEMO_ENABLED` e
reiniciar bloqueia o login e os tokens dessa conta. Outros usuários mantêm seus
perfis, permissões e fluxo de autenticação existentes.

Rotacionar a senha somente pelo ambiente e por um novo deploy/restart que aplique
a configuração. Não usar variáveis `VITE_*` para segredos. Em serviços existentes,
`sync: false` do Blueprint não cadastra uma nova variável: definir o segredo
diretamente no serviço, preservando as demais chaves.

## Inicialização, banco e cold starts

O endpoint `/actuator/health` mantém a verificação de banco e não expõe detalhes.
Não foram removidas verificações, adiadas migrations ou habilitada inicialização
lazy do Spring. Não há evidência suficiente para alterar o pool, os timeouts ou a
estratégia de compilação da JVM. O benchmark local usa PostgreSQL descartável,
1 CPU, 512 MiB, a mesma versão Java e schema previamente migrado.

O plano Free do Render suspende serviços sem tráfego por 15 minutos e o despertar
pode levar cerca de um minuto. Isso é separado do build e do startup Java. Uma
instância paga elimina essa suspensão, mas implica custo e não foi contratada.
Não foi criado ping periódico para contornar a suspensão. O Neon também pode
precisar despertar; medições locais não comprovam a latência da conexão remota.

Em 11/11/2026, a exclusão programada da branch isolada de homologação foi
removida. A API Neon confirmou `expires_at: null`; a expiração anterior de
12/11/2026 às 16h, America/Fortaleza, não está mais ativa. Nenhum dado, migration,
branch de produção ou plano foi alterado nessa operação.

A organização permanece no plano Free, confirmado pela API. Esse plano não tem
prazo máximo de uso, mas mantém suas cotas de armazenamento, transferência e
compute. A ausência de exclusão programada não garante disponibilidade ilimitada
nem elimina suspensão por inatividade ou por esgotamento de cota. Não foram
contratados recursos pagos. Consultar [limites atuais](https://neon.com/pricing).

## Validação e rollout

Medições locais de build Docker: **63,08 s** na receita anterior, **118,74 s** no
primeiro build com a nova camada de dependências e **25,43 s** no rebuild após
alterações de fonte, com essa camada `CACHED`. O ganho observado no rebuild foi
de aproximadamente **60%**, com o custo inicial explicitado. São amostras locais,
não tempos medidos no Render; rede, CPU e cache podem alterar os resultados.

Em três partidas por configuração, com schema já migrado, as medianas do log
Spring Boot foram **38,53 s antes** e **38,80 s depois**; até o health HTTP, **43,03 s**
e **44,17 s**. Essa variação não demonstra melhoria de startup. A validação das
23 migrations levou cerca de 0,13–0,26 s e a inicialização Hikari, 0,05–0,17 s no
banco local. Esses dados não justificam remover validações nem aumentar o pool.

O JAR final passou em **185 testes**, sem falhas, erros ou ignorados, incluindo
integrações com PostgreSQL descartável. O login HTTP real da imagem final
retornou 200; a identidade protegida respondeu com mediana de aproximadamente
59 ms em 20 chamadas, durante outras validações locais. Não é um benchmark de
carga nem evidência de melhoria na API remota.

O navegador passou no fluxo real frontend → API Java 21 → PostgreSQL descartável:
login `demo@frota.test`, retorno à conta protegida, identidade ADMIN, logout 204 e
reuso do JWT revogado rejeitado com 401. A senha foi fornecida somente ao processo
de teste. Essa primeira validação utilizou somente o banco descartável.

Na API pública anterior, três GETs sequenciais de health apresentaram:
timeout sem resposta em **150,01 s**, depois HTTP 200 em **72,47 s**, e em seguida
HTTP 200 em **0,42 s**. Os logs consultados posteriormente registraram um cold
start anterior de **195,104 s** no Spring Boot. O serviço usa o plano Free e o
tempo de CPU disponível é um fator relevante; não se atribui essa demora ao
Flyway com base no benchmark local.

Executar `./mvnw -B -ntp verify` com Java 21 e Docker disponível. Os testes novos
cobrem criação, rotação, idempotência, colisão de conta, reativação e bloqueios de
perfil/ambiente. O teste de navegador do frontend usa uma senha fornecida ao
processo, verifica login pela interface, identidade protegida, logout e rejeição
do JWT revogado; nenhuma resposta da API é simulada nesse cenário.

### Publicação remota concluída

Em 11/11/2026, o deploy `dep-dbqalccipgvc73a0fm90`, commit `11c33eba`, ficou
**live** no serviço isolado `fsph-fleet-api-staging`. A branch do serviço foi
ajustada para esta tarefa; auto-deploy permanece desligado. Apenas as quatro
variáveis de staging foram atualizadas, preservando as demais configurações.
O outro serviço Render e os bancos de produção não foram modificados.

| Medida remota | Anterior | Publicado |
| --- | --- | --- |
| Deploy, início até live, sem fila | 246,08 s | 241,59 s |
| Spring Boot, cold start observado | 195,104 s | 163,903 s |

O deploy anterior é `dep-dbn10k5fbbac73boge00`, de 06/11; seu cold start acima
foi observado em 11/11. As amostras não isolam variação de CPU, cache ou patch
Java, portanto não demonstram ganho causal de startup. O novo build resolveu
dependências em **16,913 s** e empacotou em **9,315 s**; esse primeiro build
precisou criar a camada de cache. Não foi feito outro deploy apenas para medir.

No startup publicado, o Hikari iniciou em **0,507 s** e o Flyway validou as
**23 migrations em 0,895 s**: schema atualizado, nenhuma migration executada.
Esses tempos não indicam gargalo principal de conexão ou migração. O Java
registrado foi 21.0.13; a imagem continua na família Java 21 já utilizada.

A API pública <https://fsph-fleet-api-staging.onrender.com> passou em health 200,
preflight 200 com a origem exata do frontend, acesso anônimo 401, login 200,
identidade ADMIN 200, logout 204 e rejeição do JWT revogado 401. O Chromium
também concluiu o login real pelo endereço público
<https://fsph-fleet-web-staging.vercel.app/login>, acessou `/conta`, confirmou
identidade e perfil, saiu e validou a revogação do token: **1 teste passou**.
A senha aleatória foi cadastrada somente no ambiente do Render e fornecida ao
processo de teste; a cópia local de acesso é ignorada pelo Git.

Revisões separadas, sem merge automático:
[backend](https://github.com/cardosojse/fsph-fleet-management/pull/13) e
[frontend](https://github.com/BarbosaDev01/fsph-fleet-management-web-deploy/pull/1).

Para rollback, republicar o commit anterior no serviço de staging e restaurar
seu perfil anterior. Não reverter migrations nem apagar banco. A conta criada
fica apenas no banco isolado; em produção, o novo bloqueio impede seu acesso.

## Referências verificadas

- [Cache Docker no Render](https://render.com/docs/docker#image-caching)
- [Health checks](https://render.com/docs/health-checks)
- [Limites do plano Free](https://render.com/docs/free#spinning-down-on-idle)
- [Variáveis de ambiente](https://render.com/docs/configure-environment-variables)
- [Maven dependency:go-offline](https://maven.apache.org/plugins/maven-dependency-plugin/go-offline-mojo.html)
