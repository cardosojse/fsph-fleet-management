# Lotação administrativa

Base compatível: `97d9d92`, branch `feat/perfis-atribuicao-20261118`.
Backend Spring Boot; frontend em `fsph-fleet-management-web-integration`.

POST `/api/v1/users` e PUT `/api/v1/users/{id}` continuam usando `sectorId` e
`role` distintos, exigindo `USERS_WRITE`. Setor inexistente é recusado antes de
mutar a conta. O endpoint da própria conta altera somente a senha. A matriz V5
e suas extensões não foram alteradas: ADMIN segue global, solicitante segue
identificado individualmente e setor SVO não concede triagem a motorista.

V25 adiciona somente unidades ausentes, reconhecendo aliases explícitos de
Administrativo/Administração, Lacen, Hemose, SVO/Serviço de Verificação de Óbitos
e Coordenação de Infraestrutura. Mantém nomes, IDs, vínculos e contas sem lotação.
Não cria contas pessoais. `AdminInitializer` reutiliza aliases administrativos
no primeiro cadastro e continua sem modificar uma conta já existente.

`UserResponse` admite lotação nula defensivamente para consulta de pendências
legadas; o esquema atual e os DTOs de escrita continuam exigindo setor.
Não foi introduzida regra obrigando perfis SVO ao setor SVO: tal restrição não está
formalizada nos requisitos operacionais disponíveis. O frontend apenas sugere SVO.

Testes adicionais cobrem transferência preservando perfil/senha, alteração de
perfil preservando setor, setor inexistente sem mutação, autorização, ADMIN com
setor equivalente e resposta de pendência. O teste SQL reproduz equivalências,
idempotência e vínculos preexistentes, inclusive um vínculo nulo simulado.
Execute-o em banco local isolado com `psql -X -v ON_ERROR_STOP=1 -f
src/test/resources/db/sector-catalog-regression.sql`.

O runner frontend `scripts/test-profiles-local.ps1` compila esta fonte com
JDK 21, executa `-Dtest=*Tests,!*IntegrationTests`, cria PostgreSQL isolado e testa
a interface e a API reais. Classes dependentes de Testcontainers não são incluídas;
a persistência desta entrega é exercitada pelo runner com PostgreSQL real.

Pendências da Fundação: supervisão setorial de gestores, obrigatoriedade de
lotação SVO, regras especializadas de atendimento/coleta e identificação do canal
externo. Essa API não tem endpoints específicos de triagem/coleta nem histórico
persistido de alterações de usuário. `/vehicles/me` retorna 503 porque não existe
implementação configurada de `VehicleAssignmentQuery`; transferir setor não cria
esse vínculo. Não converter leitura global de viagens em
atendimento SVO. Nenhum serviço ou banco remoto foi alterado.
