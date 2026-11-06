# Backend da prévia isolada

Branch: `chore/preview-integracao-telas`, criada de `chore/deploy-render`.
Integração de `docs/route-architecture` (`57dac36`) em uma cópia separada.

- API: <https://fsph-fleet-api-staging.onrender.com>.
- Login do frontend: <https://fsph-fleet-web-staging.vercel.app/login>.
- Código backend publicado: `035bd54`.
- Render: `dep-dbn10k5fbbac73boge00`, live, plano free, deploy automático desligado.
- Banco novo: `fleet_ui_preview`, na branch Neon de homologação existente.

Os módulos existentes foram mantidos. Transportes, rotas, paradas e reservas de
agenda já implementados na branch de origem foram integrados; conflitos de
permissões, entidades, repositórios e testes foram resolvidos somente nesta cópia.
As migrations aplicadas anteriormente não foram alteradas. Os novos módulos usam
V20–V24, com a matriz de permissões compatível com os dois conjuntos de módulos.
O banco anterior `fleet_staging` e a produção não foram alterados.

Validação: os relatórios finais somam 158 testes, sem falhas, erros ou ignorados.
A suíte completa foi executada e a classe de transportes/rotas foi reexecutada
após ajustar a expectativa das versões das migrations. O JAR foi gerado e 23
migrations foram aplicadas no banco novo, até V24.

Na API real foram verificados cadastros, documentos, CNHs, permissões,
transportes, rotas e paradas. O frontend passou em build, lint e 14 testes
unitários. No navegador, o login e as 13 telas internas consultaram a API
publicada com HTTP 200. Criação/exclusão de setor pela interface e logout passaram.
Há dados fictícios de demonstração exclusivamente no banco da prévia.

Os 264 arquivos originais dos checkouts de backend e frontend foram conferidos
por SHA-256 e permaneceram intactos, assim como índices, branches ativas, estados
Git e `main`. As branches anteriores permaneceram intactas. O processo Java local
de validação foi encerrado; a API pública permanece no Render.

A branch Neon expira em **12/11/2026 às 16h, America/Fortaleza**. Ajustar a
retenção para manter a prévia após essa data. Segredos permanecem em variáveis
do serviço e arquivos locais ignorados; nenhum está versionado.
