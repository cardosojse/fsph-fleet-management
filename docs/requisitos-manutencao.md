# Requisitos e Critérios de Aceitação — Manutenção de Veículos

## 1. Contexto e Escopo

Este documento formaliza o refinamento de requisitos e os critérios de aceitação do módulo de **Manutenção de Veículos** do sistema de gestão de frotas da **FSPH (Fundação de Saúde Parreiras Horta)**, cobrindo as seguintes histórias de usuário:

1. **Registro de manutenções preventivas e corretivas:** registrar veículo, data, quilometragem (km), descrição, oficina e custo para manter o histórico completo.
2. **Indisponibilidade operacional em manutenção:** garantir que o veículo em manutenção fique indisponível para viagens e agendamentos, evitando alocações indevidas.
3. **Programação e alertas de preventivas:** programar manutenções preventivas por quilometragem e/ou prazo e receber alertas antes da falha.
4. **Histórico e custo acumulado por veículo:** consultar o histórico completo e o custo acumulado de manutenção por veículo para subsidiar decisões de substituição ou renovação da frota.

---

## 2. Perfis e Permissões

| Permissão | Perfis Padrão | Capacidades |
|---|---|---|
| `MAINTENANCES_READ` | `ADMIN`, `GESTOR_FROTA` | Consultar histórico de manutenções, custo acumulado por veículo, programações preventivas e alertas ativos |
| `MAINTENANCES_WRITE` | `ADMIN`, `GESTOR_FROTA` | Registrar manutenções (em andamento ou concluídas), concluir/cancelar manutenções em andamento e gerenciar programações preventivas |
| `VEHICLES_READ` | `ADMIN`, `GESTOR_FROTA`, `MOTORISTA` | Consultar veículos e verificar flag de disponibilidade (`inMaintenance` / `available`) e listagem de veículos disponíveis (`/api/v1/vehicles/available`) |

---

## 3. Regras de Negócio

### RN-MAN-01 — Classificação e Ciclo de Vida da Manutenção
- Toda manutenção deve ser classificada em um tipo (`MaintenanceType`):
  - `PREVENTIVE` (Preventiva);
  - `CORRECTIVE` (Corretiva).
- Os status possíveis (`MaintenanceStatus`) são:
  - `IN_PROGRESS`: veículo encontra-se atualmente na oficina em manutenção;
  - `COMPLETED`: serviço finalizado e veículo liberado (quando ativo);
  - `CANCELED`: registro cancelado por lançamento indevido ou desistência antes da execução, não compondo o custo acumulado.

### RN-MAN-02 — Dados Obrigatórios e Validações de Registro
- Campos obrigatórios ao registrar uma manutenção:
  - `vehicleId`: deve referenciar um veículo existente e ativo (`active == true`);
  - `type`: `PREVENTIVE` ou `CORRECTIVE`;
  - `performedOn`: data de entrada/realização (`LocalDate`);
  - `odometerKm`: quilometragem do veículo no momento da manutenção (`>= 0`);
  - `description`: descrição detalhada dos serviços executados ou solicitados (não vazia);
  - `workshop`: nome da oficina responsável (não vazio, até 150 caracteres);
  - `cost`: valor em reais (`BigDecimal >= 0.00`, escala de 2 casas decimais).

### RN-MAN-03 — Bloqueio de Disponibilidade do Veículo em Manutenção
- Ao registrar uma manutenção com status `IN_PROGRESS`:
  - O veículo é bloqueado pessimistamente (`PESSIMISTIC_WRITE`) para evitar condições de corrida;
  - O veículo não pode estar inativo nem possuir outra manutenção `IN_PROGRESS` simultânea (garantido também por índice único parcial `ux_vehicle_maintenances_in_progress` no PostgreSQL);
  - O atributo `inMaintenance` do veículo passa para `true`, tornando `available = false`;
  - Enquanto `inMaintenance == true`, o veículo:
    - não aparece na listagem de veículos disponíveis para alocação (`GET /api/v1/vehicles/available`);
    - é bloqueado pelo contrato `MaintenanceAvailabilityGuard.assertVehicleAvailableForAllocation(vehicleId)` utilizado por fluxos de viagens e agendamentos;
    - não pode ser desativado até o encerramento ou cancelamento da manutenção aberta.
- Ao concluir (`COMPLETED`) ou cancelar (`CANCELED`) a manutenção `IN_PROGRESS`, `inMaintenance` retorna automaticamente para `false`.

### RN-MAN-04 — Consistência de Hodômetro
- Para abertura de manutenção `IN_PROGRESS`: `odometerKm` não pode ser inferior ao `currentOdometerKm` atual do veículo; caso seja superior, atualiza `currentOdometerKm`.
- Na conclusão de uma manutenção `IN_PROGRESS`:
  - `completedOn` deve ser igual ou posterior a `performedOn`;
  - `completionOdometerKm` (quando informado) deve ser `>= odometerKm` de entrada e `>= currentOdometerKm` atual do veículo, atualizando `currentOdometerKm`;
  - o custo final (`cost`) e a descrição podem ser ajustados com os valores definitivos da nota/ordem de serviço.
- Para lançamento direto de manutenção já `COMPLETED` (registro histórico): caso `odometerKm > currentOdometerKm`, o hodômetro atual do veículo avança para `odometerKm`; lançamentos retroativos com quilometragem anterior são aceitos no histórico sem regredir o hodômetro atual do veículo.

### RN-MAN-05 — Programação de Manutenções Preventivas e Alertas
- Uma programação preventiva (`PreventiveMaintenanceSchedule`) exige:
  - `vehicleId` de um veículo ativo;
  - `serviceName` (ex.: `"Troca de óleo e filtros"`, `"Revisão de 50.000 km"`);
  - ao menos uma meta definida: `targetOdometerKm` (`> 0`) e/ou `targetDate`;
  - antecedência de alerta configurável: `alertLeadKm` (padrão `1000` km, `>= 0`) e `alertLeadDays` (padrão `15` dias, `>= 0`).
- Nível de alerta (`MaintenanceAlertLevel`) calculado dinamicamente para programações `PENDING`:
  - `OVERDUE` (Vencida): `currentOdometerKm >= targetOdometerKm` **ou** `targetDate < dataAtual`;
  - `DUE_SOON` (Próxima): não vencida, mas `currentOdometerKm >= (targetOdometerKm - alertLeadKm)` **ou** `targetDate <= dataAtual + alertLeadDays`;
  - `ON_TIME` (Em dia): fora da janela de alerta.
- Quando uma manutenção preventiva é registrada ou concluída vinculada a uma programação pendente (`scheduleId`), a programação passa automaticamente para `COMPLETED` com referência à manutenção executada.

### RN-MAN-06 — Histórico e Custo Acumulado por Veículo
- O histórico por veículo retorna todos os registros ordenados por `performedOn DESC, createdAt DESC`.
- O resumo consolidado de manutenção por veículo calcula:
  - `totalCost`: soma de `cost` de todas as manutenções não canceladas (`IN_PROGRESS` + `COMPLETED`);
  - `preventiveCost`: soma de `cost` das manutenções preventivas não canceladas;
  - `correctiveCost`: soma de `cost` das manutenções corretivas não canceladas;
  - `totalMaintenances`, `preventiveCount`, `correctiveCount`, `inProgressCount`;
  - `lastMaintenanceDate` e `lastMaintenanceOdometerKm` para apoiar a análise de renovação/substituição da frota.

---

## 4. Critérios de Aceitação

- **CA-MAN-01 (Registro completo):** Dado um gestor autenticado com `MAINTENANCES_WRITE` e um veículo ativo, quando registrar uma manutenção preventiva ou corretiva informando data, km, descrição, oficina e custo válido, então o registro deve ser persistido e retornado no histórico do veículo.
- **CA-MAN-02 (Bloqueio em manutenção):** Dado um veículo com manutenção registrada como `IN_PROGRESS`, quando consultar a disponibilidade do veículo ou tentar alocá-lo para viagem/agendamento ou abrir segunda manutenção simultânea, então o sistema deve indicar `inMaintenance = true` (`available = false`) e rejeitar a nova alocação com `409 Conflict`.
- **CA-MAN-03 (Liberação após conclusão):** Dado um veículo com manutenção `IN_PROGRESS`, quando o gestor concluir a manutenção informando data de conclusão e custo/km finais, então o status da manutenção deve mudar para `COMPLETED`, o hodômetro do veículo deve ser atualizado e o veículo deve voltar a ficar disponível (`inMaintenance = false`, `available = true`).
- **CA-MAN-04 (Programação preventiva inválida sem gatilho):** Dado um gestor tentando criar uma programação preventiva sem `targetOdometerKm` e sem `targetDate`, quando submeter a requisição, então a API deve rejeitar com `400 Bad Request`.
- **CA-MAN-05 (Alertas por KM e por prazo):** Dado um veículo ativo com programação pendente para `10.000 km` (alerta com `1.000 km` de antecedência) ou data em até `15 dias`, quando o veículo atingir `9.200 km` ou faltar `10 dias`, então o painel/endpoint de alertas deve listar a programação como `DUE_SOON`; ao atingir `10.000 km` ou ultrapassar a data-alvo, deve listá-la como `OVERDUE`.
- **CA-MAN-06 (Custo acumulado por veículo):** Dado um veículo com manutenções preventivas, corretivas e um registro cancelado, quando o gestor consultar o resumo de custos do veículo, então o custo acumulado deve somar exatamente as manutenções válidas (preventivas + corretivas), desconsiderando registros `CANCELED`.
- **CA-MAN-07 (Controle de acesso):** Dado um usuário sem `MAINTENANCES_WRITE` (ex.: `MOTORISTA`), quando tentar criar ou concluir uma manutenção, então a API deve responder `403 Forbidden`.
