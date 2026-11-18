# ReLoop (Ecoponto)

Backend do projeto acadêmico da FIAP para a Global Solution 2026/2. O MVP
incentiva o descarte de eletrônicos em pontos de coleta. O cidadão registra o
item, acompanha a pesagem e o depósito simulados e usa os pontos recebidos
para resgatar benefícios fictícios.

O backend disponibiliza o health check, três consultas do catálogo, o fluxo
de fotos e descartes, a integração IoT simulada, a autenticação JWT e o
fluxo de carteira e recompensas nos perfis `bootstrap` e `postgres`. A
[especificação OpenAPI](src/main/resources/static/docs/openapi.yaml) define
os contratos HTTP implementados nos dois perfis.

## Pré-requisitos

- JDK 21 e Git.
- Docker Desktop em execução para os testes PostgreSQL e o banco de desenvolvimento.
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
| Spring Data JPA, Hibernate e driver PostgreSQL | Persistência e transações no perfil postgres. |
| Spring Security e OAuth2 Resource Server | Hash de senha, emissão e validação de JWT e controle de acesso. |
| Jakarta Bean Validation | Validação declarativa dos DTOs e parâmetros das requisições. |
| springdoc OpenAPI 2.10.0 | Swagger UI apontado para o contrato YAML versionado. |
| Spring Boot Actuator | Health check operacional. |
| Spring Boot Test, Spring Security Test e Testcontainers | Testes HTTP, segurança e integração com PostgreSQL real. |

## Executar sem banco

Execute na raiz do repositório. O `bootstrap` exige `JWT_SECRET` com pelo
menos 32 bytes aleatórios codificados em Base64. Gere um segredo local com
OpenSSL e mantenha o valor fora do Git.

```sh
export JWT_SECRET="$(openssl rand -base64 32)"
export IOT_DEVICE_ID=COL-001
export IOT_DEVICE_KEY='chave-local-de-demonstracao'
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
o catálogo, o fluxo de descartes e o resgate de recompensas.

## Cadastrar, entrar e atualizar o perfil

Nos perfis `bootstrap` e `postgres`, cadastro, login e as três consultas do catálogo são
públicos. Perfil, fotos, descartes, carteira e recompensas exigem
`Authorization: Bearer <token>`.
O simulador IoT usa `X-Device-Key`.
Novos cadastros exigem `document` com CPF válido, com ou sem máscara. Ele é
normalizado para 11 dígitos, único por conta e não é retornado no perfil/JWT.
O CPF dos exemplos é gerado para demonstração, sem origem em dados de pessoas.
O aplicativo usa `GET /api/v1/user` e `PUT /api/v1/user` para consultar e atualizar
exclusivamente o titular do JWT, sem enviar ID de usuário. `GET/PUT /api/v1/users/me`
permanecem disponíveis como aliases de compatibilidade, com os mesmos corpos e respostas.

```sh
base_url=http://localhost:8080
curl -fsS "$base_url/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"name":"João Pedro","email":"joao@example.com","password":"Senha123!","document":"70000000159"}'
curl -fsS "$base_url/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"joao@example.com","password":"Senha123!"}'

# Copie accessToken da resposta de login.
token='token de João'
curl -fsS "$base_url/api/v1/user" -H "Authorization: Bearer $token"
curl -fsS -X PUT "$base_url/api/v1/user" \
  -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  -d '{"name":"João Pedro Souza","email":"joaopedro@example.com"}'
```

O cadastro retorna `201`, e-mail em minúsculas e ID gerado no servidor.
E-mail em uso retorna `409 EMAIL_ALREADY_IN_USE`. CPF ausente ou inválido retorna
`400 BAD_REQUEST`; CPF já cadastrado retorna `409 DOCUMENT_ALREADY_IN_USE`,
com a mensagem `CPF já cadastrado.`, sem expor o documento. A senha tem de 8 a
256 caracteres no cadastro e é armazenada como hash PBKDF2 com salt.
O login retorna `401 INVALID_CREDENTIALS` se o e-mail ou a senha não
conferir. Em caso de sucesso, devolve `accessToken`, `tokenType: Bearer` e
`expiresIn: 3600`. O token HS256 tem os claims `sub` (ID do usuário),
`iat` (emissão), `exp` (expiração) e `iss` (emissor desta execução).
O mesmo token continua válido depois de atualizar o e-mail. Use o novo
e-mail no próximo login. Tokens ausentes, inválidos ou expirados em rotas
privadas retornam `401` com o código correspondente no corpo.

Para testar duas contas, repita o cadastro e o login com outro e-mail e outro CPF
sintético válido (por exemplo, `70000000230`),
guarde o segundo token e crie um descarte com o token de João seguindo a
próxima seção. Com o segundo token, o detalhe do descarte, o cancelamento
e a leitura da foto retornam `404`. Uma tentativa de cadastrar um descarte
com o `photoId` de João também retorna `404`, desde que o coletor e a
categoria informados sejam válidos. Compare os históricos com cada token.
O reinício apaga as contas em memória e invalida os tokens da execução
anterior.

## Consultar o catálogo

No perfil `bootstrap`, as três consultas aceitam chamadas sem JWT e retornam
dados fictícios. No perfil `postgres`, as mesmas consultas públicas leem
as tabelas do SQL do Hiago. As demais rotas mantêm a proteção JWT ou a
credencial IoT descrita neste documento.

```sh
curl -fsS http://localhost:8080/api/v1/collection-points
curl -fsS http://localhost:8080/api/v1/collection-points/1
curl -fsS http://localhost:8080/api/v1/electronic-categories
curl -i http://localhost:8080/api/v1/collection-points/999
```

A lista de pontos responde `200` com um array, inclusive `[]` quando estiver
vazia. Os exemplos JSON abaixo usam o catálogo do perfil `bootstrap`.
`distanceFromUserLocation` é um número em quilômetros, simulado e estável pelo
ID do ecoponto, igual na lista e no detalhe em ambos os perfis. Não utiliza GPS
nem representa distância real ou trajeto por ruas.
A resposta de demonstração inclui, por exemplo:

```json
[
  {"id": 1, "name": "Ecoponto Centro", "address": "Rua Exemplo, 100", "latitude": -20.45, "longitude": -54.62, "isActive": true, "distanceFromUserLocation": 1.2},
  {"id": 2, "name": "Ecoponto Bairro Norte", "address": "Rua Demonstração, 250", "latitude": -20.42, "longitude": -54.61, "isActive": false, "distanceFromUserLocation": 3.5},
  {"id": 3, "name": "Ecoponto Bairro Sul", "address": "Rua Demonstração, 350", "latitude": -20.49, "longitude": -54.63, "isActive": true, "distanceFromUserLocation": 5.0}
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
  "distanceFromUserLocation": 1.2,
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
Use o token obtido no login para enviar fotos e gerenciar seus descartes.

O fluxo principal do aplicativo envia o formulário e a imagem em uma única requisição:

```sh
curl -fsS http://localhost:8080/api/v1/disposals \
  -H "Authorization: Bearer $token" \
  -F 'deviceId=COL-001' \
  -F 'categoryId=1' \
  -F 'itemName=Celular' \
  -F 'file=@/caminho/item.png;type=image/png'
```

O curl define `multipart/form-data` e seu boundary. Todos os quatro campos são
obrigatórios. São aceitos JPEG/PNG reais de até 10 MiB, validados pelo conteúdo.
A resposta é `201` com o mesmo `DisposalResponse`, incluindo `photoId` e `photoUrl`.
A foto pertence ao titular do JWT; parâmetros extras não escolhem o proprietário
nem substituem o `photoId` gerado pelo servidor. O fluxo JSON com `photoId` e o
endpoint separado de upload continuam disponíveis, conforme os exemplos abaixo.
Os exemplos multipart e JSON são alternativas: use apenas um deles para iniciar
a sessão que seguirá para a pesagem. Cada coletor aceita um descarte ativo por vez.

No postgres, upload e descarte participam de uma única transação. O callback
existente apaga o arquivo somente em rollback confirmado; em `STATUS_UNKNOWN`,
preserva o arquivo e registra a necessidade de reconciliação. No bootstrap,
a operação mantém o bloqueio do repositório em memória até terminar, desfaz
inserções novas se falhar e compensa apenas as fotos criadas naquele escopo.
Inclusive falhas ao fechar o stream depois de salvá-lo acionam essa compensação.
Formulários são serializados nesse perfil de demonstração.

Filesystem e PostgreSQL não têm commit distribuído: uma queda abrupta pode deixar
arquivo pendente de reconciliação, e uma falha física ao remover arquivos exige
intervenção. Não existe limpeza automática para resultados transacionais
incertos, que poderia excluir uma fotografia confirmada.


| Operação | Resultado |
| --- | --- |
| `POST /api/v1/uploads` | `201` com `photoId` e `photoUrl`. |
| `POST /api/v1/disposals` | Aceita multipart com `file` ou JSON com `photoId`; retorna `201` com o descarte em `WAITING_WEIGHT`. |
| `GET /api/v1/disposals/{id}` | `200` com o estado atual do descarte. |
| `DELETE /api/v1/disposals/{id}` | `204` ao cancelar um descarte que aguarda pesagem. |
| `GET /api/v1/users/me/disposals` | `200` com o histórico, do mais recente ao mais antigo, incluindo cancelados. |

Para o fluxo de compatibilidade, envie primeiro um JPEG ou PNG de até 10 MiB
como a parte multipart `file` em `POST /api/v1/uploads`. O backend
valida o formato e tenta decodificar a imagem. A rota auxiliar
`GET /api/v1/uploads/{photoId}` entrega a foto quando o usuário atual é o
proprietário.

```sh
curl -fsS -H "Authorization: Bearer $token" \
  -F 'file=@/caminho/para/foto.png' http://localhost:8080/api/v1/uploads
# Copie os valores photoId e photoUrl da resposta.
photo_id='UUID retornado no upload'
photo_url='URI absoluta retornada no upload'
curl -fsS -H "Authorization: Bearer $token" "$photo_url" -o /tmp/reloop-foto.png

curl -fsS -X POST http://localhost:8080/api/v1/disposals \
  -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' \
  -d "{\"deviceId\":\"COL-001\",\"categoryId\":1,\"itemName\":\"Celular\",\"photoId\":\"$photo_id\"}"

# Após qualquer um dos dois formatos, copie o id retornado pelo POST.
disposal_id='ID retornado pelo POST'
curl -fsS -H "Authorization: Bearer $token" "$base_url/api/v1/disposals/$disposal_id"
curl -fsS -H "Authorization: Bearer $token" "$base_url/api/v1/users/me/disposals"

# Opcional: cancele apenas se não for seguir para a simulação IoT abaixo.
# curl -i -X DELETE -H "Authorization: Bearer $token" "$base_url/api/v1/disposals/$disposal_id"
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

O perfil `bootstrap` habilita `POST /api/v1/iot/events`. O roteiro de inicialização
já configura a credencial de demonstração e o coletor `COL-001`. No terminal das
requisições, exporte os mesmos valores. Se tiver escolhido outra chave, use o
valor configurado no backend. Sem `IOT_DEVICE_KEY`, as chamadas IoT retornam `401`.
Mantenha o backend em execução desde o cadastro até o resgate: reiniciar o
bootstrap apaga o descarte em memória e invalida o token.

```sh
export IOT_DEVICE_ID=COL-001
export IOT_DEVICE_KEY='chave-local-de-demonstracao'
```

Use o descarte de celular em `COL-001`, categoria `1`, criado por um dos formatos
da seção anterior, ainda em `WAITING_WEIGHT`. Mantenha `disposal_id` com o ID
retornado pelo POST e envie os três eventos na ordem. O peso de 0,500 kg é válido
para essa categoria nos dois perfis. Gere um identificador novo por evento.

```sh
# disposal_id deve conter o ID real retornado pelo POST escolhido.
event_prefix=$(uuidgen)
base_url=http://localhost:8080

curl -fsS "$base_url/api/v1/iot/events" \
  -H "X-Device-Key: $IOT_DEVICE_KEY" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${event_prefix}-peso\",\"deviceId\":\"COL-001\",\"disposalId\":$disposal_id,\"eventType\":\"WEIGHT_RECORDED\",\"weightKg\":0.500}"

curl -fsS "$base_url/api/v1/iot/events" \
  -H "X-Device-Key: $IOT_DEVICE_KEY" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${event_prefix}-abertura\",\"deviceId\":\"COL-001\",\"disposalId\":$disposal_id,\"eventType\":\"DOOR_OPENED\"}"

curl -fsS "$base_url/api/v1/iot/events" \
  -H "X-Device-Key: $IOT_DEVICE_KEY" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${event_prefix}-fechamento\",\"deviceId\":\"COL-001\",\"disposalId\":$disposal_id,\"eventType\":\"DOOR_CLOSED\"}"

curl -fsS -H "Authorization: Bearer $token" "$base_url/api/v1/disposals/$disposal_id"
```

As respostas `200` mostram `WAITING_DEPOSIT`, `DEPOSIT_IN_PROGRESS` e
`COMPLETED`. Para um celular de 0,500 kg, a pesagem estima
33 pontos e o fechamento registra um crédito na carteira do titular.
O descarte concluído libera o coletor. Consulte `awardedPoints` no
descarte e a movimentação `CREDIT` no extrato.

Reenvie o mesmo evento para obter a resposta original. O mesmo `eventId`
com conteúdo diferente retorna `409`, assim como eventos fora da ordem.
Peso fora da faixa inclusiva da categoria retorna `422`. Envie
`weightKg` somente em eventos de pesagem. O reinício apaga os eventos,
os descartes, as carteiras, os resgates e os créditos temporários.

## Consultar carteira e resgatar benefícios

Use o token do login e consulte a carteira, o catálogo e os cupons da conta.
Cada cadastro abre uma carteira vazia com saldo zero.

```sh
curl -fsS "$base_url/api/v1/users/me/wallet" -H "Authorization: Bearer $token"
curl -fsS "$base_url/api/v1/rewards" -H "Authorization: Bearer $token"
curl -fsS "$base_url/api/v1/users/me/redemptions" -H "Authorization: Bearer $token"
```

No perfil `bootstrap`, após completar o exemplo de descarte do celular, a carteira
retorna `200` com 33 pontos disponíveis. Os IDs e custos dos benefícios abaixo são
desse perfil; consulte o catálogo correspondente quando usar `postgres`.

```json
{
  "availablePoints": 33,
  "totalEarned": 33,
  "totalRedeemed": 0,
  "transactions": [
    {"id": 1, "type": "CREDIT", "points": 33, "description": "Descarte de Celular", "createdAt": "2026-11-12T20:15:00Z"}
  ]
}
```

Resgate o benefício de frete grátis, de ID `10` e custo de 20 pontos.
Gere uma chave para a tentativa. O POST usa apenas o ID na URL e os cabeçalhos.

```sh
redemption_key=$(uuidgen)
curl -fsS -X POST "$base_url/api/v1/rewards/10/redeem" \
  -H "Authorization: Bearer $token" -H "Idempotency-Key: $redemption_key"
```

O resgate retorna `201`, gera um cupom fictício e deixa 13 pontos disponíveis
neste exemplo.

```json
{
  "redemptionId": 1,
  "rewardId": 10,
  "rewardName": "Cupom de frete grátis",
  "pointsSpent": 20,
  "remainingPoints": 13,
  "couponCode": "RELOOP-1-43e2db87-d32f-4520-a8ac-12e455b139f6",
  "redeemedAt": "2026-11-12T20:30:00Z"
}
```

O histórico mostra o cupom e o saldo registrado no momento do resgate.
O extrato passa a mostrar um `DEBIT` de 20 pontos seguido pelo `CREDIT`
de 33. As duas listas ordenam os registros do mais recente para o mais antigo.

Ao repetir um resgate confirmado, envie a mesma `Idempotency-Key` com o
mesmo benefício para obter o `201` original, mesmo se o saldo ou o estoque
mudar. Cada resgate novo usa uma chave diferente. Reusar a chave para outro
benefício, tentar resgatar com saldo insuficiente ou escolher um benefício
inativo ou esgotado retorna `409`. O catálogo contém quatro exemplos
fictícios, incluindo um benefício inativo e outro com estoque inicial de uma
unidade. A carteira, o catálogo e os resgates ficam em memória no
`bootstrap`; o reinício restaura o catálogo inicial.

## Executar com PostgreSQL

O perfil `postgres` usa o [SQL original do Hiago](database/schema/database.sql),
Spring Data JPA, transações reais e armazenamento durável de fotografias.
Os contratos mobile são os mesmos do `bootstrap`: perfil em `/api/v1/user`,
distâncias simuladas no catálogo, CPF obrigatório no cadastro e descarte multipart.
O perfil também aceita `/api/v1/users/me`, upload separado e descarte JSON com
`photoId` para compatibilidade. Carteira, históricos e resgates mantêm suas rotas.

Leia o [guia de integração PostgreSQL](docs/database/postgres-integration.md)
para criar o banco uma única vez com Docker Compose, configurar o `.env`
e executar no Mac. O Hibernate usa somente `ddl-auto=validate`; a aplicação
não executa DDL ou seed na inicialização. As fotos exigem `PHOTO_STORAGE_DIR`
persistente. Não ative `bootstrap` e `postgres` juntos.

O inicializador aguarda o healthcheck TCP do banco, com limite padrão de
60 segundos (`POSTGRES_INIT_TIMEOUT_SECONDS`, exportada no shell). Falha ou
timeout interrompe a operação antes de executar SQL. Use-o somente em banco
vazio; para bancos já preenchidos, inicie apenas o serviço com
`docker compose up -d --wait --wait-timeout 60 postgres`.
Se ainda não foi aplicada nesse banco, execute explicitamente a migração
[V001 de CPF](database/migrations/README.md) uma vez, tanto após o esquema original
em instalações novas quanto em bancos existentes. O backend não migra o banco
automaticamente; usuários legados mantêm CPF nulo. Se V001 já foi aplicada,
não a reaplique. Depois inicie a aplicação.

Os catálogos diferem entre os perfis. No SQL, o exemplo testado é Notebook,
categoria 2, peso 2,300 kg, crédito 83, resgate de 80 e saldo 3. Os hashes e
arquivos de fotos dos usuários demonstrativos têm as limitações documentadas
no guia; use cadastro/upload novos para a jornada.

Pesos aceitam no máximo três casas decimais em ambos os perfis. Precisão
excedente retorna 400 sem arredondamento; a fórmula de pontos permanece igual.

## Testes automatizados

Com Java 21 e Docker Desktop ativo:

```sh
./mvnw clean verify
```

Os testes usam PostgreSQL real em contêineres descartáveis e um diretório de
fotos temporário. Não precisam de `.env`, banco criado manualmente ou JWT
externo. Incluem concorrência, rollback e reinício com HTTP real, além da
regressão bootstrap. Após o formulário integrado, a suíte tem 196 testes, preservando os 138 anteriores, incluindo leitura
consistente de carteira, estados de conclusão de upload, inicialização limitada
e casos negativos de JWT, IoT e fotografias. Os relatórios ficam em
`target/surefire-reports`. Veja as [correções da auditoria](docs/database/postgres-audit-fixes.md).


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

- API REST em `/api/v1`, recursos em inglês e JSON em camelCase. O perfil mobile usa `/api/v1/user`.
- IDs de entidades em `Long`, coletores em string, fotos em UUID, pesos em
  `BigDecimal`, pontos em inteiros e datas ISO 8601 em UTC.
- Requisições GET sem corpo. Erros com `status`, `code`, `message` e `timestamp`.
- JWT no cabeçalho `Authorization: Bearer <token>` e credencial do simulador
  no cabeçalho `X-Device-Key`.
- DTOs separados das entidades JPA. Atualize o YAML na mesma entrega de
  qualquer mudança de contrato. Use Conventional Commits.

O [guia de arquitetura](docs/architecture.md) descreve as camadas, o fluxo de
descarte e os limites das transações.
