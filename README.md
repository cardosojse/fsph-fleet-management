# fsph-fleet-management

Sistema de gestão de frotas desenvolvido com:

- Java 21;
- Spring Boot;
- Spring Web MVC;
- Spring Data JPA;
- Spring Security;
- Flyway;
- PostgreSQL;
- Docker e Docker Compose.

## Arquitetura e evolução da frota

O código atual possui cadastros de veículos e motoristas, vínculo entre motorista
e usuário, histórico de CNHs e documentos dos veículos, controle de quilometragem
e permissões de acesso à frota.

A API de transportes e viagens segue a separação entre solicitação (`Transport`),
viagem planejada (`Route`) e parada (`RouteStop`) do diagrama de referência.
Entidades, estados, endpoints, regras de acesso e exemplos de uso estão em
[Transportes, viagens e paradas](docs/README-route.md).

O documento também apresenta as adaptações ao código existente, as migrations
criadas e os limites deste incremento.

O planejamento inclui duração e retorno previsto, com bloqueio de reservas
sobrepostas de veículo ou motorista e alteração de agenda de viagens planejadas.

## Pré-requisitos

Antes de executar o projeto, verifique se estão instalados:

- Git;
- Java 21;
- Docker;
- Docker Compose.

Use os comandos abaixo para verificar:

```bash
git --version
java --version
docker --version
docker compose version
```

Não é necessário instalar o Maven, pois o repositório possui o Maven Wrapper (`mvnw`).

## 1. Clonar o repositório

```bash
git clone <URL_DO_REPOSITORIO>
cd fsph-fleet-management
```

Substitua `<URL_DO_REPOSITORIO>` pela URL real do projeto.

## 2. Configurar as variáveis de ambiente

Na raiz do projeto, crie o arquivo `.env` a partir do exemplo:

```bash
cp .env.example .env
```

O arquivo deve possuir estas variáveis:

```env
POSTGRES_DB=gestao_frotas
POSTGRES_USER=gestao_frotas
POSTGRES_PASSWORD=sua_senha
POSTGRES_PORT=5432

ADMIN_NAME=Administrador
ADMIN_EMAIL=admin@exemplo.com
ADMIN_PASSWORD=admin_senha
ADMIN_SECTOR_NAME=Administrativo

JWT_SECRET=
JWT_EXPIRATION=PT1H
JWT_ISSUER=fsph-fleet-management
```

Altere as senhas de exemplo e gere um JWT secret aleatório para o ambiente local.

Na primeira inicialização, o backend cria o administrador configurado acima e o
setor `Administrativo`, caso ainda não existam. Opcionalmente, personalize o nome
do setor com `ADMIN_SECTOR_NAME`. Inicializações posteriores não duplicam o usuário.

> O arquivo `.env` contém informações locais e não deve ser enviado ao Git.

## 3. Iniciar o PostgreSQL

Ainda na raiz do projeto, execute:

```bash
docker compose up -d postgres
```

Na primeira execução, o Docker baixará a imagem do PostgreSQL e criará:

- o contêiner do PostgreSQL;
- o banco definido em `POSTGRES_DB`;
- o usuário definido em `POSTGRES_USER`;
- um volume para preservar os dados.

Verifique o status:

```bash
docker compose ps
```

O banco está pronto quando o serviço `postgres` aparecer como `healthy`.

Para acompanhar os logs:

```bash
docker compose logs -f postgres
```

Pressione `Ctrl+C` para sair da visualização dos logs. Isso não encerra o banco.

## 4. Carregar as variáveis para o backend

O Docker Compose lê o `.env` automaticamente, mas o Spring Boot não. Por isso, carregue as variáveis no terminal:

```bash
set -a
source .env
set +a
```

Esse comando deve ser executado na raiz do projeto e repetido sempre que um novo terminal for aberto para iniciar o backend.

## 5. Iniciar o backend

Depois de carregar as variáveis, execute a aplicação na raiz do repositório:

```bash
./mvnw spring-boot:run
```

Na primeira execução, o Maven Wrapper baixará as dependências do projeto. Isso pode levar alguns minutos.

A aplicação estará pronta quando o terminal apresentar uma mensagem semelhante a:

```text
Started GestaoFrotaApplication
```

Por padrão, o backend será iniciado em:

```text
http://localhost:8081
```

## Ordem completa de inicialização

Sempre siga esta ordem:

1. Entrar na raiz do projeto;
2. iniciar o PostgreSQL;
3. confirmar que o banco está saudável;
4. carregar as variáveis do `.env`;
5. iniciar o Spring Boot.

Comandos resumidos:

```bash
cd fsph-fleet-management

docker compose up -d postgres
docker compose ps

set -a
source .env
set +a

./mvnw spring-boot:run
```

## Migrations do banco

As migrations ficam em:

```text
src/main/resources/db/migration/
```

Quando o backend é iniciado, o Flyway procura migrations nessa pasta e aplica automaticamente aquelas que ainda não foram executadas.

Os arquivos devem seguir o padrão:

```text
V1__descricao_da_migration.sql
V2__outra_migration.sql
```

As migrations não precisam ser executadas manualmente.

A migration `V3` cria índices únicos para e-mails e nomes de setores sem distinguir
maiúsculas e espaços nas extremidades. Se a base já contiver valores equivalentes,
é necessário resolver essas duplicatas antes de iniciar a aplicação com a nova
migration.

A migration `V5` cria a matriz de permissões por perfil e suas permissões padrão.

As migrations da base de frota são:

| Versão | Alteração |
| --- | --- |
| `V6` | Cria veículos, validações de cadastro e índice único de placa normalizada. |
| `V7` | Cria documentos dos veículos e garante um registro `ACTIVE` por tipo e veículo. |
| `V8` | Cria motoristas com vínculo único a um usuário. |
| `V9` | Cria o histórico de CNHs e garante uma CNH `ACTIVE` por motorista. |
| `V10` | Acrescenta permissões de veículos e motoristas à matriz de acesso. |

As migrations de transportes e viagens são:

| Versão | Alteração |
| --- | --- |
| `V11` | Cria solicitações de transporte com setor e solicitante. |
| `V12` | Cria viagens com veículo, motorista, criador e controle de execução. |
| `V13` | Cria paradas ordenadas que vinculam viagens e transportes. |
| `V14` | Acrescenta permissões de transportes e viagens, preservando matrizes personalizadas. |
| `V15` | Acrescenta duração e retorno previsto às viagens, com validações e índices de reserva. |

Os testes de integração usam PostgreSQL isolado via Testcontainers. Com Docker
disponível e JDK 21 configurado, execute `./mvnw -B verify` ou
`.\mvnw.cmd -B verify` no Windows.

## Parar o projeto

Para encerrar o backend, pressione `Ctrl+C` no terminal em que ele está sendo executado.

Para parar o PostgreSQL, na raiz do projeto, execute:

```bash
docker compose stop postgres
```

Os dados continuarão salvos no volume do Docker.

Para remover o contêiner, preservando os dados:

```bash
docker compose down
```

> Evite utilizar `docker compose down -v`, pois a opção `-v` remove o volume e apaga os dados do banco.

## Rotina diária

Ao começar a trabalhar:

```bash
cd fsph-fleet-management
docker compose up -d postgres

set -a
source .env
set +a

./mvnw spring-boot:run
```

Ao terminar:

1. Pressione `Ctrl+C` para encerrar o backend;
2. pare o banco:

```bash
docker compose stop postgres
```
