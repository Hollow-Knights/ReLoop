# ReLoop (Ecoponto)

Backend do projeto acadêmico da FIAP para a Global Solution 2026/2. O MVP
incentiva o descarte de eletrônicos em pontos de coleta. O cidadão registra o
item, acompanha a pesagem e o depósito simulados e usa os pontos recebidos
para resgatar benefícios fictícios.

O backend disponibiliza o health check, três consultas do catálogo, o fluxo
de fotos e descartes e a integração IoT simulada no perfil `bootstrap`. A
[especificação OpenAPI](src/main/resources/static/docs/openapi.yaml) indica
quais operações estão implementadas e quais ainda estão planejadas.

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
Os resumos indicam quais operações estão implementadas no `bootstrap`.
O Swagger UI fica em modo de consulta. Use os exemplos abaixo para chamar
o catálogo e o fluxo de descartes. As rotas planejadas permanecem bloqueadas.

## Consultar o catálogo

No perfil `bootstrap`, as três consultas aceitam chamadas sem JWT e retornam
dados fictícios. Fora desse perfil, o filtro exige autenticação para o
catálogo. A disponibilização do catálogo nesses ambientes depende do
adaptador PostgreSQL e da integração JWT.

```sh
curl -fsS http://localhost:8080/api/v1/collection-points
curl -fsS http://localhost:8080/api/v1/collection-points/1
curl -fsS http://localhost:8080/api/v1/electronic-categories
curl -i http://localhost:8080/api/v1/collection-points/999
```

A lista de pontos responde `200` com um array, inclusive `[]` quando estiver
vazia. A resposta de demonstração inclui, por exemplo:

```json
[
  {"id": 1, "name": "Ecoponto Centro", "address": "Rua Exemplo, 100", "latitude": -20.45, "longitude": -54.62, "isActive": true},
  {"id": 2, "name": "Ecoponto Bairro Norte", "address": "Rua Demonstração, 250", "latitude": -20.42, "longitude": -54.61, "isActive": false},
  {"id": 3, "name": "Ecoponto Bairro Sul", "address": "Rua Demonstração, 350", "latitude": -20.49, "longitude": -54.63, "isActive": true}
]
```

O detalhe de `/collection-points/1` responde `200` com as categorias
aceitas e os coletores do local.

```json
{
  "id": 1,
  "name": "Ecoponto Centro",
  "address": "Rua Exemplo, 100",
  "latitude": -20.45,
  "longitude": -54.62,
  "isActive": true,
  "acceptedCategoryIds": [1, 2, 3, 4],
  "devices": [{"deviceId": "COL-001", "isActive": true}]
}
```

A lista de categorias responde `200`. Os valores são os parâmetros
armazenados no catálogo.

```json
[
  {"id": 1, "name": "Celulares", "basePoints": 30, "pointsPerKg": 5, "maxPoints": 40, "minWeightKg": 0.05, "maxWeightKg": 2.0},
  {"id": 2, "name": "Notebooks", "basePoints": 25, "pointsPerKg": 5, "maxPoints": 45, "minWeightKg": 0.5, "maxWeightKg": 6.0},
  {"id": 3, "name": "Pequenos eletrodomésticos", "basePoints": 10, "pointsPerKg": 4, "maxPoints": 35, "minWeightKg": 0.25, "maxWeightKg": 15.0},
  {"id": 4, "name": "Cabos e carregadores", "basePoints": 0, "pointsPerKg": 4, "maxPoints": 25, "minWeightKg": 0.01, "maxWeightKg": 8.0}
]
```

Um ID positivo inexistente retorna `404`. IDs inválidos, como `0` ou
`abc`, retornam `400`.

```json
{
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "Recurso não encontrado.",
  "timestamp": "2026-11-12T22:30:00Z"
}
```

## Enviar fotos e gerenciar descartes

Execute a aplicação com o perfil `bootstrap`, conforme a seção anterior.
O perfil usa um único usuário demonstrativo para todas as requisições.

| Operação | Resultado |
| --- | --- |
| `POST /api/v1/uploads` | `201` com `photoId` e `photoUrl`. |
| `POST /api/v1/disposals` | `201` com o descarte em `WAITING_WEIGHT`. |
| `GET /api/v1/disposals/{id}` | `200` com o estado atual do descarte. |
| `DELETE /api/v1/disposals/{id}` | `204` ao cancelar um descarte que aguarda pesagem. |
| `GET /api/v1/users/me/disposals` | `200` com o histórico, do mais recente ao mais antigo, incluindo cancelados. |

Envie um JPEG ou PNG de até 10 MiB como a parte multipart `file`. O backend
valida o formato e tenta decodificar a imagem. A rota auxiliar
`GET /api/v1/uploads/{photoId}` entrega a foto quando o usuário atual é o
proprietário.

```sh
curl -fsS -F 'file=@/caminho/para/foto.png' http://localhost:8080/api/v1/uploads
# Copie os valores photoId e photoUrl da resposta.
photo_id='UUID retornado no upload'
photo_url='URI absoluta retornada no upload'
curl -fsS "$photo_url" -o /tmp/reloop-foto.png

curl -fsS -X POST http://localhost:8080/api/v1/disposals \
  -H 'Content-Type: application/json' \
  -d "{\"deviceId\":\"COL-001\",\"categoryId\":3,\"itemName\":\"Air fryer\",\"photoId\":\"$photo_id\"}"

# Substitua 1 pelo id retornado pelo cadastro.
curl -fsS http://localhost:8080/api/v1/disposals/1
curl -i -X DELETE http://localhost:8080/api/v1/disposals/1
curl -fsS http://localhost:8080/api/v1/users/me/disposals
```

O descarte começa com `weightKg` e `estimatedPoints` nulos e
`awardedPoints` igual a zero. O histórico usa `categoryId`. O cancelamento
libera o coletor e mantém a foto vinculada ao descarte cancelado.

`PHOTO_BASE_URL` define a base absoluta de `photoUrl`. O padrão usa
`http://localhost` e a porta da aplicação. Para abrir a foto em um
dispositivo físico na mesma rede, informe a URL HTTP(S) acessível por ele,
por exemplo `PHOTO_BASE_URL=http://192.168.1.20:8080`. A base aceita um
prefixo de caminho quando o ambiente é servido por um proxy.

Os descartes e os metadados das fotos são mantidos em memória por processo.
Os arquivos são gravados em um diretório temporário privado com prefixo
`reloop-photos-`, sob `java.io.tmpdir`. O encerramento normal remove esse
diretório. Após uma interrupção forçada, localize a pasta da execução
encerrada no diretório temporário do sistema e remova-a manualmente. Cada
reinício começa com histórico vazio e novas associações de fotos.

## Simular a pesagem e os eventos da porta

O perfil `bootstrap` habilita `POST /api/v1/iot/events`. Configure uma
credencial local e o identificador do coletor antes de iniciar a aplicação.
O coletor padrão é `COL-001`. Sem `IOT_DEVICE_KEY`, as chamadas IoT retornam
`401`. Use a mesma chave para executar os exemplos em outro terminal.

```sh
export IOT_DEVICE_ID=COL-001
export IOT_DEVICE_KEY='chave-local-de-demonstracao'
./mvnw spring-boot:run -Dspring-boot.run.profiles=bootstrap
```

Crie um descarte em `COL-001` com a categoria `3`, conforme a seção
anterior. Substitua `1` pelo ID retornado e envie os três eventos na ordem.
Gere um identificador novo para cada evento de uma nova sessão.

```sh
disposal_id=1
event_prefix=$(uuidgen)
base_url=http://localhost:8080

curl -fsS "$base_url/api/v1/iot/events" \
  -H "X-Device-Key: $IOT_DEVICE_KEY" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${event_prefix}-peso\",\"deviceId\":\"COL-001\",\"disposalId\":$disposal_id,\"eventType\":\"WEIGHT_RECORDED\",\"weightKg\":3.8}"

curl -fsS "$base_url/api/v1/iot/events" \
  -H "X-Device-Key: $IOT_DEVICE_KEY" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${event_prefix}-abertura\",\"deviceId\":\"COL-001\",\"disposalId\":$disposal_id,\"eventType\":\"DOOR_OPENED\"}"

curl -fsS "$base_url/api/v1/iot/events" \
  -H "X-Device-Key: $IOT_DEVICE_KEY" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${event_prefix}-fechamento\",\"deviceId\":\"COL-001\",\"disposalId\":$disposal_id,\"eventType\":\"DOOR_CLOSED\"}"

curl -fsS "$base_url/api/v1/disposals/$disposal_id"
```

As respostas `200` mostram `WAITING_DEPOSIT`, `DEPOSIT_IN_PROGRESS` e
`COMPLETED`. Para um pequeno eletrodoméstico de 3,8 kg, a pesagem estima
25 pontos e o fechamento os credita em memória. O descarte concluído
libera o coletor. A confirmação pode ser consultada em `awardedPoints`
no detalhe ou no histórico do descarte.

Reenvie o mesmo evento para obter a resposta original. O mesmo `eventId`
com conteúdo diferente retorna `409`, assim como eventos fora da ordem.
Peso fora da faixa inclusiva da categoria retorna `422`. Envie
`weightKg` somente em eventos de pesagem. O reinício apaga os eventos,
os descartes e os créditos temporários.

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

Os serviços do catálogo consultam as interfaces `CollectionPointRepository`
e `ElectronicCategoryRepository`. Hoje, `InMemoryCollectionCatalog`
implementa ambas com listas imutáveis carregadas no perfil `bootstrap`.
Após a aprovação do esquema, implementações com Spring Data JPA para o
perfil `postgres` poderão preencher os mesmos modelos de leitura. Nessa
entrega futura, os controllers e services serão habilitados também em
`postgres`. O [mapeamento de campos do catálogo](docs/database/schema-contract.md#campos-consumidos-pelo-catálogo)
identifica os dados necessários para essa substituição.

Para descartes, o adaptador PostgreSQL implementará `DisposalRepository`,
mantendo a reserva do coletor e da foto atômica com o cadastro. Os metadados
da foto poderão ser persistidos via `PhotoStorage` e o arquivo poderá usar
armazenamento durável. A integração JWT fornecerá uma implementação de
`CurrentUserProvider` que extrai a identidade autenticada do contexto do
Spring Security. Os controllers e services do fluxo poderão ser ativados
nos perfis que tiverem essas implementações.

Para IoT, a implementação PostgreSQL de `IotEventRepository` precisa
guardar o evento e seu resultado, atualizar o descarte e gravar o crédito
na mesma transação. O contrato de persistência descreve os dados para o
alinhamento com Hiago. A chave demonstrativa é exclusiva do `bootstrap`.

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
    │   ├── auth/ user/ disposal/
    │   ├── iot/ wallet/ reward/ upload/
    │   ├── collection/
    │   │   ├── controller/ dto/ model/
    │   │   └── repository/ service/
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
