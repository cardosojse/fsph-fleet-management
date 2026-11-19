# Entrega integrada para a homologação existente — 19/11/2026

Destino confirmado pelo usuário: serviço Render `fsph-fleet-api-staging`
(`srv-dbn0cg0u01pc73fpooqg`), workspace `tea-dbkhptm7bikc73a946p0`.
Fonte mantida: `cardosojse/fsph-fleet-management`, raiz, branch
`perf/staging-deploy-login-20261111`, Dockerfile existente e deploy manual.
URL: https://fsph-fleet-api-staging.onrender.com.

## Integração

Branch de trabalho `release/staging-backend-20261119`, criada da versão publicada
`71df10edc611d4127cfc92b9253fc1b37effb07f`. Merge da `main` oficial
`b7b430c4d6c5d6c0a09b43cf06331b7c23538179`, incluindo:

- [PR #14](https://github.com/cardosojse/fsph-fleet-management/pull/14): equipes,
  plantões SVO e auditoria.
- [PR #15](https://github.com/cardosojse/fsph-fleet-management/pull/15): cartões
  de abastecimento e solicitações externas SVO.

Transportes, rotas, paradas, reservas, perfil/lotação, usuário atribuído,
revogação JWT, proteção do ADMIN e isolamento da demo foram preservados.
As correções de serialização SVO e filtros temporais reproduzem as mudanças
pontuais `19d6c31` e `f1e8400` do fork consolidado; sua branch inteira não entrou.
Testes HTTP adicionais reproduziram e corrigiram relações LAZY em cartões e
solicitações SVO e parâmetros de busca nulos interpretados como `bytea` no PostgreSQL.
As consultas agora carregam as relações exigidas pelos DTOs e tipam os filtros;
`open-in-view=false`, DTOs e autorização continuam preservados.

## Banco e compatibilidade

O banco de destino foi conferido por conexão direta com TLS `verify-full` e
`channelBinding=require`, em transação somente leitura: 24 migrations aplicadas,
até V25, e todos os checksums correspondem aos arquivos publicados.
Não houve modificação de configuração ou credenciais.

V1–V25 permanecem idênticas. Os números V20–V24 da `main` já estavam ocupados
por outro histórico na homologação. Somente as migrations novas foram adaptadas:

| Versão | Conteúdo |
| --- | --- |
| V26 | Equipes, membros, plantões SVO e auditoria |
| V27 | Cartões de abastecimento |
| V28 | Solicitações externas SVO |
| V29 | União das permissões antigas e novas |

A V29 não retira concessões existentes. Amplia somente matrizes padrão intactas;
matrizes personalizadas permanecem sob decisão administrativa. ADMIN já possui
todas as permissões por definição no serviço existente. Não há exclusão de dados,
`repair`, `clean`, baseline artificial ou mudança de banco.

O teste de upgrade V25→V29 conserva checksums e linhas preexistentes de usuários,
setores, veículos, motoristas, transportes, rotas com duração e paradas, além das
permissões antigas e de uma matriz personalizada. Há teste separado de banco vazio.

## Verificação e operação

Java 21 e Maven Wrapper: `mvnw.cmd -B verify`, incluindo PostgreSQL 17 real via
Testcontainers, migrations, autorização, contratos HTTP com dados vinculados,
serialização após fechamento da transação e histórico paginado. O pacote Spring
Boot é reempacotado pelo plugin existente.

Resultado final: **254 testes, zero falhas, erros ou ignorados; BUILD SUCCESS**.
O frontend preservado passou em lint, 214 testes unitários e build com Node 24
e pnpm 11.25.0. A chamada pública anterior confirmou login, lotação e logout;
a validação da nova publicação deve ser repetida após a promoção Vercel.

Não houve alteração em `.env`, `render.yaml`, Dockerfile, CORS, conexão Neon,
JWT, plano/região ou configuração de deploy. O push é normal e restrito às
branches desta entrega. Como auto-deploy está desligado, a publicação exige um
único disparo manual após os testes. Confirmar status `live`, SHA e saúde antes
de publicar o frontend.

Retornar ao artefato anterior não desfaz V26–V29. O binário anterior não conhece
os novos valores do enum de permissões; uma reversão exige um build compatível
com o catálogo V29. Não apontar automaticamente o binário antigo para este banco
nem apagar concessões para contornar essa diferença. Este histórico pertence à linha
de homologação e não substitui o histórico de outro banco que use a `main` oficial.

As telas candidatas e a atualização de dependências do frontend ficaram fora,
por escolha do usuário. A existência destes endpoints não transforma os módulos
demonstrativos do frontend em formulários integrados novos.

## Resultado observado

Commit implantado `913ae68452f67379926ce2bffcd6966a4cc4f85a`, deployment
`dep-dbvik6ivcj2c73d2a6e0`, status `live` e saúde `UP`. Após o deploy, a leitura
direta do Neon confirmou 28 migrations até V29 e todos os checksums. A comparação
por hash preservou as cinco contas e os sete setores; nenhuma permissão antiga
foi removida. Cartões, solicitações SVO, equipes, plantões, auditoria e histórico
responderam HTTP 200 com autenticação real.

O frontend `029dd686f3061708e6a5380ace3475b63b926030` foi promovido no deployment
`dpl_3NqRoHzyte5XKfHxJELAvakyJPC9`, Production/READY. O navegador público validou
login, nove chamadas cross-origin, lotação, logout e token revogado. Não houve
gravação de cadastros. Logs Render sem erros na janela `16:59:59Z`–`17:04:20Z`.
Os commits seguintes nas branches de trabalho documentam a entrega e não mudam
o SHA implantado nem disparam deploys adicionais.
