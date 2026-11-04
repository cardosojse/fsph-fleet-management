# Consultas de motorista atual e histórico de rotas por veículo

## Situação desta entrega

A implementação das consultas depende da integração do módulo de rotas, que
não está presente na branch atual, `feat/frontend-motoristas-veiculos`, no commit
`da80202`. Neste checkout não há entidade, repository, service, controller, DTO,
enum de estados ou migration de rotas/transportes. As migrations disponíveis vão
de `V1` a `V10`; nenhuma cria `routes`.
Também não foi encontrado `AGENTS.md` no projeto nem nos diretórios pais
consultados.

Este documento registra o contrato mínimo para o responsável por rotas e a
especificação das consultas a integrar. Os endpoints e DTOs abaixo são propostas;
não estão implementados. Não há fallback que trate a ausência do módulo como
ausência de motorista ou histórico vazio.

### Implementação existente em outra branch

Na inspeção das referências Git locais foi encontrada a branch
`docs/route-architecture` (também há uma referência `origin/docs/route-architecture`),
com os commits `b403228` (transportes, rotas e paradas) e `57dac36` (reservas de
agenda e retorno previsto). Sua implementação foi examinada por `git show`, sem
trocar a branch atual, executar merge ou alterar o trabalho local.

A dependência concreta é integrar esse módulo pelo fluxo do responsável, incluindo
`Route`, `RouteStatus`, `RouteRepository`, `RouteService`, `TravelAccess`,
`PageResponse`, permissões de rotas e migrations `V11` a `V15`. Seus contratos
existentes devem ser reaproveitados; não recriar essas classes nesta branch nem
copiar somente a tabela sem o restante do módulo. O contrato abaixo já usa os
nomes reais encontrados nessa implementação.

## Estrutura existente a reaproveitar

| Informação | Origem real no projeto |
| --- | --- |
| Veículo | `domain.models.Vehicle`, tabela `vehicles`, ID `UUID` |
| Motorista | `domain.models.Driver`, tabela `drivers`, ID `UUID` |
| Usuário do motorista | `Driver.user`, FK `drivers.user_id -> users.id` |
| Nome do motorista | `Driver.user.name` |
| Setor do motorista | `Driver.user.sector`, FK `users.sector_id -> sectors.id` |
| Setor do veículo | `Vehicle.sector`, FK opcional `vehicles.sector_id -> sectors.id` |

`DRIVER_PROFILES`, mencionado na modelagem, corresponde a `drivers` neste
projeto. `Driver` não tem setor próprio. O setor do veículo pode ser nulo.
O setor solicitante de `Transport.sector`, existente na branch de rotas, não deve
participar destas consultas.

`VehicleRepository.findById` já carrega `sector` com `@EntityGraph`.
`DriverRepository.findById` carrega `user` e `user.sector`.
`VehicleService.find(UUID)` já lança `ResourceNotFoundException("Veículo", id)`.
O enum `VehicleStatus.IN_ROUTE` não é persistido em `Vehicle` e não identifica
quem está dirigindo; `DriverStatus.IN_ROUTE` também não estabelece esse vínculo.

## Dependência mínima do módulo de rotas

Após integrar o módulo existente, as consultas precisam somente destes dados de
`Route` / `routes`, já definidos por ele. Não é necessária outra migration para
criar vínculos entre motorista e veículo:

| Dado lógico | Contrato necessário |
| --- | --- |
| `id` | Identificador da rota, `UUID` |
| `vehicle` | Associação para o `Vehicle` existente; FK para `vehicles.id` |
| `driver` | Associação para o `Driver` existente; FK para `drivers.id`, não `users.id` |
| `status` | `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `plannedStartAt` | Início planejado obrigatório, `Instant` / `planned_start_at` |
| `realStartAt` | Início efetivo; obrigatório em `IN_PROGRESS` e `COMPLETED` |
| `realEndAt` | Fim efetivo; obrigatório em `COMPLETED`, nulo em `IN_PROGRESS` |

Para os registros de viagens iniciadas, motorista e veículo devem estar
preenchidos. O ciclo de criação, início, conclusão e cancelamento continua sob
responsabilidade do módulo de rotas, inclusive a coerência dos estados e datas.
As consultas não precisam de endpoints de escrita para integrar. O
`RouteRepository` existente estende `JpaSpecificationExecutor<Route>` e permite
adicionar as seguintes leituras sem alterar o ciclo de rotas:

1. Buscar as rotas `IN_PROGRESS` por `vehicle.id`, com `driver.user.sector` e
   `vehicle.sector`, preservando a possibilidade de detectar mais de um resultado.
2. Buscar as rotas `IN_PROGRESS` por `driver.id`, para detectar que um motorista
   está simultaneamente em rotas de veículos diferentes.
3. Buscar uma página de rotas por `vehicle.id`, filtrando no banco por
   `status IN (IN_PROGRESS, COMPLETED)` e `realStartAt IS NOT NULL`, com motorista,
   datas, estado e contagem total usando exatamente os mesmos filtros.

Não usar `findFirst`, `findTop`, a última rota nem um `Optional` que esconda
duplicatas. A leitura paginada deve carregar as associações necessárias sem
paginar coleções em memória. Reaproveitar a API de leitura equivalente, caso o
módulo de rotas a ofereça, em vez de criar um segundo modelo de rota.

`RouteService.list` e `GET /api/v1/routes` já filtram por `vehicleId`, `driverId`
e um `status`, mas não retornam os dois setores nem oferecem o histórico filtrado
em conjunto por estados de execução. Essas consultas ainda precisam ser
adicionadas após a integração. O grafo atual de `RouteRepository.findAll` carrega
`vehicle`, `driver`, `driver.user` e `createdByUser`; nas novas leituras, carregar
também `vehicle.sector` e `driver.user.sector`, preservando setor de veículo nulo.

## Contrato HTTP proposto para a integração

Os controllers existentes usam `/api/v1`, IDs `UUID`, DTOs `record`, mapeamento
explícito por `from(...)`, services transacionais e `ProblemDetail` para erros.
Não existe paginação no backend da branch atual, mas o módulo de rotas já tem
`PageResponse<T>` e `TravelAccess.page(page, size)`: reaproveitar esses contratos.

O módulo de rotas restringe `ROUTES_READ` às rotas do próprio motorista e libera
visão global com `ROUTES_MANAGE`. A proposta para estas duas consultas por veículo
é exigir autenticação, `VEHICLES_READ` e `ROUTES_MANAGE`, com `@PreAuthorize` e
validação do ator ativo no service via `TravelAccess`, preservando essa fronteira.
Somente `VEHICLES_READ`, `DRIVERS_READ` ou `ROUTES_READ` não libera uma visão de
rotas de outros motoristas. Não conceder permissões novas nem presumir acesso
pelo papel. A consulta das próprias rotas permanece no endpoint de rotas existente.

### `GET /api/v1/vehicles/{id}/current-driver`

Validar a existência do veículo antes de consultar suas rotas. Veículos inativos
também existem e podem ser consultados, seguindo `VehicleService.find`.
Retornar DTO próprio, por exemplo `VehicleCurrentDriverResponse`, sem entidades
JPA, e converter as associações dentro de uma transação de leitura.

Exemplo proposto, HTTP 200, com rota em andamento:

```json
{
  "vehicleId": "00000000-0000-0000-0000-000000000001",
  "vehicleSector": { "id": "00000000-0000-0000-0000-000000000002", "name": "Logística" },
  "driving": true,
  "driver": {
    "id": "00000000-0000-0000-0000-000000000003",
    "userId": "00000000-0000-0000-0000-000000000004",
    "name": "Maria Silva",
    "sector": { "id": "00000000-0000-0000-0000-000000000005", "name": "Operações" }
  },
  "route": {
    "id": "00000000-0000-0000-0000-000000000006",
    "realStartAt": "2026-11-04T11:00:00Z"
  }
}
```

Exemplo proposto, HTTP 200, veículo existente sem rota `IN_PROGRESS`:

```json
{
  "vehicleId": "00000000-0000-0000-0000-000000000001",
  "vehicleSector": { "id": "00000000-0000-0000-0000-000000000002", "name": "Logística" },
  "driving": false,
  "driver": null,
  "route": null
}
```

`driving: false` significa "veículo sem motorista em condução no momento".
Não preencher motorista ou rota a partir do histórico. Se o veículo não tiver
setor, `vehicleSector` será nulo; o setor do motorista permanece independente.
Uma rota em andamento não deve desaparecer porque motorista/usuário foi inativado.

### `GET /api/v1/vehicles/{id}/routes/history?page=0&size=20`

Proposta: `page` começa em zero e deve ser não negativo; `size` deve estar entre
1 e 100, com padrão 20. Valores inválidos devem gerar HTTP 400 pelo padrão de
validação da API, sem ajuste silencioso. Ordenar por `realStartAt DESC, id DESC`
para desempatar datas iguais. Aplicar filtro, paginação e contagem no banco.
Reaproveitar a validação de `TravelAccess.page`; ajustar somente a ordenação da
página para a data de execução deste histórico.

O histórico proposto contém viagens iniciadas: `IN_PROGRESS` é apresentado como
em andamento, e `COMPLETED` como concluído. `PLANNED` e `CANCELLED` são excluídos,
inclusive quando houver datas efetivas inconsistentes. O total representa
viagens iniciadas, não a quantidade de viagens concluídas.

Reaproveitar `PageResponse.from(page.map(...))` com itens
`VehicleRouteHistoryResponse` (novo DTO proposto), sem serializar entidades JPA
ou depender da estrutura interna de `PageImpl`. Exemplo proposto, HTTP 200:

```json
{
  "content": [
    {
      "routeId": "00000000-0000-0000-0000-000000000006",
      "driver": {
        "id": "00000000-0000-0000-0000-000000000003",
        "userId": "00000000-0000-0000-0000-000000000004",
        "name": "Maria Silva",
        "sector": { "id": "00000000-0000-0000-0000-000000000005", "name": "Operações" }
      },
      "plannedStartAt": "2026-11-04T10:30:00Z",
      "realStartAt": "2026-11-04T11:00:00Z",
      "realEndAt": null,
      "status": "IN_PROGRESS"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Veículo existente sem viagens iniciadas retorna página vazia com HTTP 200,
`content: []`, `totalElements: 0` e `totalPages: 0`. Página além da última retorna
conteúdo vazio e preserva os totais reais. Veículo inexistente retorna HTTP 404,
inclusive quando o histórico seria vazio. Não excluir viagens de cadastros
inativos. Os setores e nomes consultados são os dos cadastros atuais; não há
modelo de snapshots para afirmar qual era o setor na data da viagem.

### Erros e conflitos

Reutilizar `ApiExceptionHandler` e as exceções existentes:

| Situação | HTTP / `code` |
| --- | --- |
| Veículo inexistente | 404 / `RESOURCE_NOT_FOUND` |
| UUID ou paginação inválidos | 400 / `VALIDATION_ERROR` |
| Mais de uma rota em andamento para o veículo | 409 / `DATA_CONFLICT` |
| Motorista da rota em andamento em outra rota também em andamento | 409 / `DATA_CONFLICT` |
| Rota em andamento sem motorista ou início efetivo | 409 / `DATA_CONFLICT` |
| Sem autenticação | 401 / `AUTHENTICATION_REQUIRED` |
| Sem alguma das permissões exigidas | 403 / `ACCESS_DENIED` |

Exemplo proposto de conflito, via `ConflictException`:

```json
{
  "type": "about:blank",
  "title": "Conflito de dados",
  "status": 409,
  "detail": "Há mais de uma rota em andamento para este veículo",
  "instance": "/api/v1/vehicles/00000000-0000-0000-0000-000000000001/current-driver",
  "code": "DATA_CONFLICT"
}
```

## Concorrência: responsabilidade separada do ciclo de rotas

A branch atual não contém a tabela ou o ciclo de rotas. Na implementação
encontrada em `docs/route-architecture`, a proteção já existe:

- `V12__create_routes.sql` define `ux_routes_running_vehicle` e
  `ux_routes_running_driver`, ambos únicos e condicionais a `status = 'IN_PROGRESS'`.
- `RouteService.start` trava rota, veículo e motorista, verifica existência de
  outra rota em andamento para ambos e executa o início na mesma transação.
- `TransportRouteIntegrationTests.serializesConcurrentStartsSharingVehicleOrDriver`
  cobre tentativas simultâneas de início compartilhando veículo ou motorista.

Portanto, não foi identificada essa lacuna na implementação de rotas inspecionada;
ela precisa ser integrada com suas migrations e regras intactas. Esses testes de
outra branch foram lidos, mas não foram executados nesta entrega. Mesmo com a
proteção, as consultas devem rejeitar dados inconsistentes com HTTP 409, sem
selecionar uma rota vencedora. Qualquer reparo de dados ou mudança no ciclo deve
ser tratado pelo responsável por rotas, separado destas consultas.

## Testes de aceitação a implementar após integrar rotas

Não foram criados testes executáveis destas consultas contra uma rota fictícia.
Eles dependem da entidade e da persistência reais do módulo de rotas. A matriz
mínima para service, repository com PostgreSQL e controller com MockMvc é:

| Cenário | Resultado esperado |
| --- | --- |
| Veículo com uma rota `IN_PROGRESS` | Motorista, nome, IDs, setores separados, rota e início efetivo corretos |
| Veículo existente sem rotas | HTTP 200 com `driving: false`, motorista e rota nulos |
| Somente rota anterior `COMPLETED` | Sem motorista atual; rota aparece no histórico |
| Somente rotas `PLANNED` e `CANCELLED` | Sem motorista atual e histórico vazio |
| Veículo inexistente | HTTP 404 nas duas consultas; sem falso resultado vazio |
| Motorista e veículo em setores distintos | Cada setor vem da sua associação, mesmo com setor solicitante diferente |
| Veículo sem setor | Setor do veículo nulo e setor do motorista preservado |
| Histórico misto com os quatro estados | Apenas `IN_PROGRESS`/`COMPLETED` com início efetivo entram no conteúdo e nos totais |
| Histórico paginado com datas empatadas | Tamanho, totais, ordem determinística e ausência de sobreposição entre páginas |
| Rotas de outro veículo | Não entram no conteúdo nem nos totais |
| Página vazia ou além da última | HTTP 200 e metadados coerentes |
| Motorista/usuário/veículo inativo | Preserva a leitura do vínculo em andamento e do histórico |
| Duas rotas em andamento para o veículo | HTTP 409; nenhuma seleção arbitrária |
| Mesmo motorista em rotas simultâneas de veículos diferentes | HTTP 409 na consulta atual |
| UUID inválido, página negativa, tamanho zero ou acima de 100 | HTTP 400 |
| Anônimo; apenas `VEHICLES_READ`; apenas `ROUTES_READ`; permissões da proposta | HTTP 401; 403; 403; 200, respectivamente |

Os testes de concorrência que comprovam impedir dois inícios simultâneos por
veículo/motorista pertencem à entrega do ciclo de rotas. A consulta deve testar
seu comportamento diante de resultados inconsistentes mesmo quando o banco
futuro impedir gravá-los (por exemplo, com mocks do repository real).

## Verificação realizada nesta entrega

Executado `./mvnw.cmd -B verify`: 63 testes, sem falhas, erros ou testes ignorados,
incluindo `FleetFoundationIntegrationTests`, testes de autorização e testes de
persistência com PostgreSQL isolado por Testcontainers.

O comando terminou com falha posterior aos testes, no goal
`spring-boot:repackage`, por não conseguir renomear
`target/fsph-fleet-management-0.0.1-SNAPSHOT.jar` para `.jar.original`.
O empacotamento não foi validado. A contagem de 63 é a da execução atual; relatórios
antigos em `target/surefire-reports` de rotas e de classes removidas não foram
contabilizados como testes executados. Nenhum processo ou artefato de outro
integrante foi removido para contornar essa falha. A única adição desta entrega é este
documento; código, migrations, credenciais e alterações locais foram preservados.
