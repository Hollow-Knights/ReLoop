# ReLoop (Ecoponto)

Backend do projeto acadêmico da FIAP para a Global Solution 2026/2. O MVP
incentiva o descarte de eletrônicos em pontos de coleta. O cidadão registra o
item, acompanha a pesagem e o depósito simulados e usa os pontos recebidos
para resgatar benefícios fictícios.

O Bloco 1 entrega a aplicação de base, o health check e os contratos dos 17
endpoints planejados. Consulte a [especificação OpenAPI](src/main/resources/static/docs/openapi.yaml)
para integrar o aplicativo mobile e o simulador conforme cada operação for
implementada.

## Pré-requisitos

- JDK 21 e Git.
- Acesso ao Maven Central na primeira compilação, para baixar o Maven e as dependências.
- PostgreSQL e o esquema fornecido pela equipe, quando for usar o perfil `postgres`.

No macOS, selecione um JDK 21 instalado no terminal atual. Em Apple Silicon,
use a distribuição arm64.

```sh
/usr/libexec/java_home -V
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
"$JAVA_HOME/bin/java" -version
./mvnw --version
```

O `pom.xml` é o descritor do Maven. Ele define o projeto, suas dependências,
o Java alvo e os plugins de compilação e empacotamento. O parent do Spring
Boot coordena as versões das dependências gerenciadas. O Maven Wrapper
(`./mvnw` no macOS e Linux, `mvnw.cmd` no Windows) baixa e usa o Maven 3.9.16
fixado no repositório, com verificação SHA-256.

| Tecnologia | Finalidade |
| --- | --- |
| Java 21 e Spring Boot 3.5.16 | Inicialização, autoconfiguração e servidor Tomcat embutido. |
| Spring Web MVC e Jackson | Tratamento de requisições HTTP e serialização JSON. |
| Spring Data JPA, Hibernate e driver PostgreSQL | Acesso futuro ao banco relacional. |
| Spring Security e OAuth2 Resource Server | Controle de acesso na base atual e suporte à futura validação de JWT. |
| Jakarta Bean Validation | Validação declarativa nos DTOs quando as rotas forem implementadas. |
| springdoc OpenAPI 2.10.0 | Swagger UI apontado para o contrato YAML versionado. |
| Spring Boot Actuator | Health check operacional. |
| Spring Boot Test e Spring Security Test | Base de JUnit 5, Mockito e testes de segurança para as próximas entregas. |

## Executar sem banco

Execute a compilação e a inicialização na raiz do repositório.

```sh
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=bootstrap
```

Em outro terminal, consulte o health check.

```sh
curl -fsS http://localhost:8080/actuator/health
```

A resposta esperada é `{"status":"UP"}`. Abra o [Swagger UI local](http://localhost:8080/swagger-ui.html)
para consultar os contratos, ou acesse o [YAML servido pela aplicação](http://localhost:8080/docs/openapi.yaml).
O título marca os 17 contratos como planejados e o envio de requisições pelo
Swagger fica desabilitado nesta etapa. O filtro de segurança libera o health
check e os recursos necessários para essa documentação. As rotas de negócio
estão bloqueadas.

## Integrar com PostgreSQL

Um profile é um conjunto de configurações ativado na inicialização. Escolha
um perfil por execução. O `bootstrap` desativa as autoconfigurações de
`DataSource` e JPA e habilita o Swagger UI. O `postgres` usa o driver JDBC
e o pool de conexões configurado pelo Spring Boot para acessar o banco.
O Hibernate usará `ddl-auto=validate` para conferir as entidades mapeadas
contra o esquema fornecido pela equipe. A aplicação mantém a inicialização
automática por scripts SQL desativada.

Quando a equipe disponibilizar o esquema, aplique-o no PostgreSQL e configure
a URL, o usuário e a senha fornecidos para o ambiente. É possível usar a
cópia local de [.env.example](.env.example). O Spring Boot lê as variáveis
exportadas pelo shell. Use os comandos abaixo no macOS ou Linux.

```sh
cp .env.example .env
# Preencha DB_URL, DB_USERNAME e DB_PASSWORD em .env com valores válidos para o shell.
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

O exemplo define `SPRING_PROFILES_ACTIVE=postgres`. Guarde as credenciais
em arquivos locais ignorados pelo Git ou no gerenciador de segredos do
ambiente. A proposta de [esquema PostgreSQL](docs/database/schema-contract.md)
serve de referência para o alinhamento com a equipe de banco.

## Estrutura

```text
.
├── .mvn/wrapper/                         # Versão e checksum do Maven
├── .env.example                          # Variáveis do perfil postgres
├── mvnw / mvnw.cmd
├── pom.xml
├── docs/
│   ├── architecture.md
│   └── database/schema-contract.md
└── src/main/
    ├── java/br/com/fiap/reloop/
    │   ├── ReLoopApplication.java
    │   ├── auth/ user/ collection/ disposal/
    │   ├── iot/ wallet/ reward/ upload/
    │   ├── security/
    │   └── shared/web/
    └── resources/
        ├── application.yml
        ├── application-bootstrap.yml
        ├── application-postgres.yml
        └── static/docs/openapi.yaml
```

## Convenções

- API REST em `/api/v1`, recursos em inglês e no plural, JSON em camelCase.
- IDs de entidades em `Long`, coletores em string, fotos em UUID, pesos em
  `BigDecimal`, pontos em inteiros e datas ISO 8601 em UTC.
- Requisições GET sem corpo. Erros com `status`, `code`, `message` e `timestamp`.
- JWT no cabeçalho `Authorization: Bearer <token>` e credencial do simulador
  no cabeçalho `X-Device-Key`.
- DTOs separados das entidades JPA. Atualize o YAML na mesma entrega de
  qualquer mudança de contrato. Use Conventional Commits.

O [guia de arquitetura](docs/architecture.md) descreve as camadas, o fluxo de
descarte e os limites das transações.
