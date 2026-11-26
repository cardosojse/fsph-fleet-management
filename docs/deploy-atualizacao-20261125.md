# Publicação integrada de 25/11/2026

API de homologação atualizada em <https://fsph-fleet-api-staging.onrender.com>.

- Código: `76781d87c4d0ae589f11ed6517e9a85c80ecda9e`.
- Deploy Render: `dep-dc3pa6lg1s2s73cajal0`, estado `live`, concluído em 25/11/2026 às 23:15:10, America/Fortaleza.
- Branch fonte existente: `perf/staging-deploy-login-20261111`, atualizada por fast-forward. Cópia de release: `integracao-staging-backend-20261125`.
- Integra a atualização oficial de relatórios `6fa7f34f2822417989631877aad38384bcbba3a0` e preserva as coletas SVO locais de 23/11, cartões, triagem, equipes, perfis e lotação.
- O conflito de filtros de histórico foi resolvido com `cast(... as Instant)` do código oficial atualizado; os testes PostgreSQL de histórico e relatórios passaram.
- Migration nova: `V30__svo_collection_tracking.sql`. Flyway registrou sua aplicação no ambiente; as migrations anteriores não foram modificadas.
- **312 testes aprovados**, zero falhas/erros/ignorados, JDK 21 e PostgreSQL 18 isolado. Fluxo de coleta e relatórios/exportações também passaram no navegador com API real.
- Health publicado `UP`; 123 caminhos OpenAPI. Leituras autenticadas dos cadastros, cartões, triagem, coletas e relatório geral retornaram 200. A conta administrativa existente conseguiu consultar as funcionalidades novas.
- Janela pós-deploy consultada: zero logs de erro entre 02:15:10Z e 02:17:12Z de 26/11/2026.

O frontend correspondente é `e7cf9bfe43be99f8b93e05732ac90d36bb27015a`, deploy `dpl_7cdhq1NVZKJ1zc3ZNEWEq5JESpvy`, em <https://fsph-fleet-web-staging.vercel.app>. O teste publicado validou consultas, PDF, Excel, impressão, sessão e responsividade, sem mocks e sem criar registros de negócio.

O serviço manteve fonte, plano, região, auto-deploy desligado, CORS e configuração de banco. Nenhuma `main` foi alterada. Os checkouts com alterações locais foram preservados; o código de publicação foi integrado em worktree separado.

Deploy anterior para referência: `dep-dbvqbkt9fdbs73bml7ag`, commit `913ae68452f67379926ce2bffcd6966a4cc4f85a`. O banco agora possui permissões `SVO_ORDERS_*`: qualquer rollback da API precisa reconhecer esses valores e ser compatível com V30. Não promover o código anterior cegamente, nem executar `repair`, limpeza ou exclusão de permissões para forçar retorno.
