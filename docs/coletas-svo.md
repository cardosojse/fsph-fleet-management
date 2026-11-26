# Acompanhamento de coletas SVO

Checkout: `cardosojse/fsph-fleet-management`, branch `release/staging-backend-20261119`.

A triagem existente é `SvoDeathRequest`. A aprovação muda a solicitação para `TRIAGED`, mas não gerava `Transport` ou `Route`. O incremento completa `markOrderGenerated()` com `SvoCollectionOrder`, ligada por `request_id UNIQUE`. `POST /api/v1/svo/requests/{id}/order` trava a origem e retorna a ordem já existente em uma repetição, sem novo evento. Geração, mudança para `ORDER_GENERATED` e histórico pertencem à mesma transação.

O setor responsável é capturado na triagem em `triage_sector_id` e copiado para a ordem. Registros antigos recebem o setor atual do responsável pela triagem na migration, pois não havia snapshot anterior. O setor do solicitante externo não define a equipe que atenderá. Não há geração paralela de viagem nem alteração de vínculo fixo de motorista/veículo.

## Operação e acesso

- `WAITING_ASSIGNMENT → ASSIGNED → IN_PROGRESS → COLLECTED → DELIVERED`.
- Antes do início, gestor pode reatribuir recursos ou cancelar. Após início não há retorno de etapa ou cancelamento.
- Atendente: `SVO_ORDERS_READ` e `SVO_ORDERS_NOTES`, consulta o próprio setor e registra providências. A geração exige também `SVO_REQUESTS_MANAGE`.
- Equipe/motorista: `SVO_ORDERS_READ` e `SVO_ORDERS_EXECUTE`, mesmo setor, motorista designado ou membro ativo da equipe atribuída. Não basta possuir a permissão.
- Gestor SVO: gerencia e consulta indicadores do próprio setor. Gestor de frota e ADMIN têm escopo global; o gestor de frota atribui recursos, mas a execução exige operador responsável. ADMIN pode executar com a autoridade correspondente.
- Permissões novas são concedidas somente às matrizes padrão intactas. Matrizes personalizadas são preservadas e podem receber as autoridades pela tela existente de permissões. Consulta de indicadores exige READ e REPORT.

Atribuição exige motorista ativo com usuário MOTORISTA ativo, veículo ativo sem manutenção e ambos no setor da ordem. Equipe é opcional; quando usada deve estar ativa e ter membros ativos do setor. Agendamento/retorno reutilizam `RouteSchedule`, incluindo duração em minutos inteiros. O banco serializa reservas por recurso e verifica coletas, routes, trips abertas, trip_requests aprovadas/agendadas e plantões. Um plantão da mesma equipe pode cobrir a coleta. Recursos em execução permanecem ocupados até a entrega; uma reserva futura incompatível precisa ser resolvida antes de iniciar. As proteções também atingem as escritas dos módulos anteriores, impedindo reserva em sentido inverso.

## Endpoints

| Método | Caminho sob `/api/v1/svo` | Operação |
|---|---|---|
| POST | `/requests/{id}/order` | Geração idempotente após aprovação |
| GET | `/orders` | Lista paginada com escopo, status, busca, origem, período, equipe e veículo |
| GET | `/orders/{id}` | Estado e histórico persistidos |
| GET | `/orders/{id}/resources` | Recursos do setor para atribuição |
| PUT | `/orders/{id}/assignment` | Atribuição por atendimento |
| PATCH | `/orders/{id}/status` | Transição permitida e observação |
| POST | `/orders/{id}/notes` | Providência imutável |
| GET | `/orders/report` | Médias independentes e ordens que compõem os resultados |

Escritas na ordem exigem `expectedVersion`. A API trava a ordem e responde 409 à versão antiga, transição inválida, repetição de conclusão ou conflito de recursos. Identidade vem da autenticação; autor, nome do autor, horário, tipo, estados anterior/novo e versão são registrados pelo servidor. `@Version` avança inclusive para providências. O histórico tem proteção SQL contra UPDATE/DELETE.

## Tempos e prazos

`requestedAt` é a criação da solicitação original; `collectedAt` e `deliveredAt` são horários reais do servidor. O período do relatório é uma coorte por `requestedAt`, com início inclusivo e fim exclusivo. Cada média tem seu denominador: coletas com horário entram em solicitação→coleta; entregas concluídas entram em coleta→entrega e solicitação→entrega. Sem amostras, a média é `null`, não zero. As linhas devolvem os mesmos tempos para auditoria.

Não havia SLA de coleta definido no projeto. `collectionDeadlineAt` é um prazo opcional de coleta, informado pelo gestor; a classificação compara o horário da coleta (ou agora, enquanto pendente) com esse prazo. Sem prazo, `UNDEFINED`. Cancelamento encerra a classificação. Não é inferido atraso a partir de um SLA inventado.

## Validação

`SvoCollectionOrderIntegrationTests` usa HTTP/MockMvc com transações reais, PostgreSQL e `open-in-view=false`: triagem, geração sem duplicatas, atribuição, consulta e coleta por membro da equipe, providência do atendente, entrega, reabertura, autores e horários, médias antes/depois da entrega, operador alheio, outro setor, transição inválida, entrega repetida, versão antiga, duas gravações simultâneas e conflito de reserva nos dois sentidos. A migration é testada em banco vazio e atualização desde V25, preservando dados e permissões personalizadas.

Execução padrão: `./mvnw test` e `./mvnw package` usam Testcontainers. Alternativamente, `FSPH_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:PORTA/fsph_svo_test` usa exclusivamente um banco local descartável com usuário `fsph_svo_test`; o helper recusa outros hosts/nomes. O runner do front `scripts/test-svo-local.ps1 -BackendPath CAMINHO` cria esse cluster, roda todos os testes/build da API e usa outro banco para o cenário de navegador com JWT real.

Resultado local de 23/11/2026: 257 testes da API aprovados, zero falhas/erros/skips, PostgreSQL 18.4 e Java 21; package aprovado. O cenário Playwright integrado passou pelos três perfis com autenticação real, sem interceptar APIs; os detalhes e o roteiro ficam em `docs/coletas-svo.md` do front.
