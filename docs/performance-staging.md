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

O ambiente anterior documenta expiração do banco de homologação em **12/11/2026
às 16h, America/Fortaleza**. A retenção deve ser confirmada antes de depender desse
ambiente; não foi alterada nesta tarefa.

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
de teste. Nenhum dado foi inserido no banco remoto para essa validação.

Na API pública anterior, três GETs sequenciais de health apresentaram:
timeout sem resposta em **150,01 s**, depois HTTP 200 em **72,47 s**, e em seguida
HTTP 200 em **0,42 s**. Isso confirma uma espera inicial relevante, mas, sem os
logs do serviço, não distingue suspensão Render, despertar Neon ou outro atraso.
Não se atribui essa demora ao Flyway com base no benchmark local.

Executar `./mvnw -B -ntp verify` com Java 21 e Docker disponível. Os testes novos
cobrem criação, rotação, idempotência, colisão de conta, reativação e bloqueios de
perfil/ambiente. O teste de navegador do frontend usa uma senha fornecida ao
processo, verifica login pela interface, identidade protegida, logout e rejeição
do JWT revogado; nenhuma resposta da API é simulada nesse cenário.

O rollout do Render depende da confirmação do workspace exigida pelo conector.
Até essa confirmação, não se deve afirmar que a conta foi ativada no serviço
público ou que os tempos de deploy remoto melhoraram. O frontend pode ser
compilado e inspecionado em um deploy sem promoção de domínio, conservando o
ambiente público anterior até ambos os lados estarem validados.

Para rollback, republicar o commit anterior no serviço de staging e restaurar
seu perfil anterior. Não reverter migrations nem apagar banco. A conta criada
fica apenas no banco isolado; em produção, o novo bloqueio impede seu acesso.

## Referências verificadas

- [Cache Docker no Render](https://render.com/docs/docker#image-caching)
- [Health checks](https://render.com/docs/health-checks)
- [Limites do plano Free](https://render.com/docs/free#spinning-down-on-idle)
- [Variáveis de ambiente](https://render.com/docs/configure-environment-variables)
- [Maven dependency:go-offline](https://maven.apache.org/plugins/maven-dependency-plugin/go-offline-mojo.html)
