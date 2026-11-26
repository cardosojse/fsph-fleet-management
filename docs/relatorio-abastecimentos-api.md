# Relatório de abastecimentos: consulta persistida

Esta integração complementa a estrutura descrita em `relatorios-gerenciais.md` e
resolve, para esta primeira versão, as pendências de consulta, endpoints e acesso.

## Contrato

- `GET /api/v1/reports/fuelings`: JSON com filtros, itens e totais.
- `GET /api/v1/reports/fuelings/pdf`: download PDF.
- `GET /api/v1/reports/fuelings/excel`: download XLSX.

Todos recebem `startDate` e `endDate` obrigatórios no formato `yyyy-MM-dd`, com
limites inclusivos, e `vehicleId` UUID opcional. Período invertido ou parâmetros
inválidos produzem 400. Um veículo autorizado sem registros no período produz
relatório vazio com totais zerados, inclusive nos downloads.

## Acesso

Autenticação e `FUELINGS_READ` são obrigatórias. `ADMIN` consulta todos os veículos,
inclusive sem setor. `GESTOR_FROTA` consulta apenas veículos cujo setor atual é
igual ao `sectorId` do usuário autenticado. Gestor sem setor e todos os demais
papéis são recusados com 403, mesmo com a permissão de leitura.

O modelo tem um único setor obrigatório por usuário; não há suporte a uma lista
de setores autorizados. O setor nunca é recebido como autorização do cliente.
Para um `vehicleId` explícito, a existência é consultada dentro do escopo:
veículo inexistente e veículo fora do escopo recebem o mesmo 404, sem detalhes
sobre placa, setor ou existência global.

**Limitação histórica:** `Fueling` não armazena o setor da época do abastecimento.
O escopo usa o setor atual de `Vehicle`; transferências alteram a visibilidade de
todos os seus registros anteriores. O setor de quem registrou não define acesso.

Filtros e escopo são aplicados no banco antes do mapeamento e dos totais. JSON,
PDF e Excel usam o mesmo serviço de consulta. Os cadastros operacionais e suas
regras de acesso permanecem sob seus contratos existentes.

## Limites e formatos

A consulta busca no máximo `app.reports.limits.max-rows` registros: um registro
além da quantidade de itens permitida, pois uma linha é reservada para totais.
O montador rejeita excesso com 400, sem truncamento silencioso. O padrão de 1000
linhas permite 999 abastecimentos. Os limites adicionais dos exportadores continuam
ativos; o Excel conserva o formato textual existente.

Downloads informam `Content-Type`, `Content-Disposition: attachment` e
`Cache-Control: no-store`. Não há snapshot compartilhado entre requisições;
alterações no banco podem mudar o resultado entre visualização e download.
