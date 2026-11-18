# Auditoria das respostas OpenAPI

Auditoria de 18/11/2026 na branch `fix/swagger-error-documentation`, a partir de
`bba306f`. Fonte dos contratos: `src/main/resources/static/docs/openapi.yaml`.

## Escopo e causa raiz

Foram examinadas **18 operações**, incluindo download de fotografia. O contrato
original tinha **82 respostas** (18 de sucesso e 64 de erro). O schema compartilhado
`ApiError` fixava exemplos de status 409, código `INVALID_DISPOSAL_STATE` e mensagem
sobre pesagem, contaminando a geração automática dos exemplos de outras respostas.
Essa mensagem global também não era a mensagem atual de cancelamento nem de IoT.

A conferência abrangeu controllers/DTOs, serviços, adaptadores dos dois perfis,
`ApiExceptionHandler`, `ApiError`, `ApiException`, configuração de segurança,
validação JWT, filtro de dispositivos e testes existentes. Os exemplos de erros
foram extraídos dessas implementações, não de mensagens de Bean Validation:
o handler HTTP deliberadamente converte essas violações em `BAD_REQUEST` /
`Bad Request`, sem expor campos ou valores recebidos.

## Resultado por operação

O contrato revisado contém **84 respostas**: as mesmas 18 respostas de sucesso e
66 de erro. Foram removidos três 401 indevidos no catálogo público e adicionados
cinco 415 nos endpoints JSON que exigem `application/json`.

| Operação | Status documentados após revisão |
| --- | --- |
| POST `/api/v1/auth/register` | 201, 400, 409, 415, 500 |
| POST `/api/v1/auth/login` | 200, 400, 401, 415, 500 |
| GET `/api/v1/users/me` | 200, 401, 500 |
| PUT `/api/v1/users/me` | 200, 400, 401, 409, 415, 500 |
| GET `/api/v1/collection-points` | 200, 500 |
| GET `/api/v1/collection-points/{id}` | 200, 400, 404, 500 |
| GET `/api/v1/electronic-categories` | 200, 500 |
| POST `/api/v1/uploads` | 201, 400, 401, 413, 415, 500 |
| GET `/api/v1/uploads/{photoId}` | 200, 400, 401, 404, 500 |
| POST `/api/v1/disposals` | 201, 400, 401, 404, 409, 415, 422, 500 |
| GET `/api/v1/disposals/{id}` | 200, 400, 401, 404, 500 |
| DELETE `/api/v1/disposals/{id}` | 204, 400, 401, 404, 409, 500 |
| GET `/api/v1/users/me/disposals` | 200, 401, 500 |
| POST `/api/v1/iot/events` | 200, 400, 401, 403, 404, 409, 415, 422, 500 |
| GET `/api/v1/users/me/wallet` | 200, 401, 500 |
| GET `/api/v1/rewards` | 200, 401, 500 |
| POST `/api/v1/rewards/{id}/redeem` | 201, 400, 401, 404, 409, 500 |
| GET `/api/v1/users/me/redemptions` | 200, 401, 500 |

A tabela cobre as respostas documentadas e os erros de entrada, segurança e negócio
identificados nas rotas. Não é um inventário de todo status que um proxy, servidor
HTTP ou cliente com negociação de conteúdo incompatível possa provocar. Métodos
não implementados e rotas inexistentes também ficam fora das 18 operações.

## Correções documentais

- `ApiError` mantém `status`, `code`, `message`, `timestamp`, tipos e campos
  obrigatórios; removidos os três exemplos específicos do schema genérico.
- Criados **37 exemplos de erro** em `components.examples`, vinculados às respostas
  por `content.application/json.examples`. Respostas reutilizáveis agrupam somente
  cenários aplicáveis à operação. Cada corpo tem o status HTTP correspondente.
- Cadastro e atualização distinguem validação, e-mail normalizado excessivo e
  e-mail já cadastrado. Login documenta `INVALID_CREDENTIALS` e sua mensagem real.
- JWT distingue ausência (`AUTHENTICATION_REQUIRED`), token rejeitado
  (`INVALID_TOKEN`) e, apenas nas operações pertinentes, identidade indisponível
  (`INVALID_TOKEN` com outra mensagem). O desafio `WWW-Authenticate` vem do filtro:
  `Bearer` ou `Bearer error="invalid_token"`; a falha de identidade no serviço
  não emite esse header. Login não emite desafio Bearer.
- Catálogo público usa `security: []`, sem 401 indevido, nos perfis bootstrap e
  postgres. JWT malformado é ignorado nessas consultas, conforme implementação.
- IoT usa exclusivamente `X-Device-Key`, sem herdar desafio Bearer. Chave ausente,
  incorreta ou duplicada retorna 401; deviceId não autorizado retorna 403.
  Validação, descarte indisponível, estado incompatível, eventId divergente,
  precisão de peso e peso fora da categoria têm exemplos distintos e reais.
- Fotografias distinguem parte ausente, arquivo vazio, imagem truncada, formato
  inválido, excesso de tamanho e foto inacessível. O 415 diferencia o Content-Type
  da requisição do formato real dos bytes. A foto de outra conta continua 404.
- Criação de descarte diferencia coletor/categoria/foto inexistente, foto vinculada,
  coletor ocupado/inativo e categoria não aceita. Consulta/cancelamento usam a
  mensagem específica de descarte, não a mensagem genérica do catálogo.
- Resgate distingue cabeçalho inválido/ausente, corpo indevido, benefício
  inexistente, saldo insuficiente, inatividade/estoque zero e conflito da chave.
  Repetição bem-sucedida continua 201 com o cupom original.
- Erros 500 distinguem falha de persistência, erro HTTP 5xx tratado pelo Spring MVC,
  falha de armazenamento de foto e categoria indisponível no processamento IoT.
  Mensagens SQL, credenciais e stack traces não fazem parte dos exemplos.
- Todas as respostas JSON de sucesso receberam exemplos explícitos. O exemplo de
  descarte inicial tem `weightKg: null`, `estimatedPoints: null` e zero crédito.
  Os três estágios IoT ilustram Notebook de 2,300 kg no postgres: estimativa/crédito
  83; resgate de 80 e saldo 3. O extrato distingue crédito e débito positivos.
  Catálogos e IDs são ilustrativos, com perfil explicitado. O JWT ilustrativo não
  é uma credencial. Download continua binário e 204 continua sem corpo.

## Alterações funcionais

**Nenhuma.** As diferenças encontradas foram resolvidas na documentação e nos testes.
Não foram alterados Java de produção, rotas, DTOs, autenticação, fórmula, SQL, DER,
configurações ou dados do PostgreSQL local. A assinatura JWT e os mecanismos de
idempotência permanecem os existentes. O 401 de identidade sem desafio Bearer foi
registrado como comportamento atual, sem mudar o backend para adaptar um exemplo.

## Regressões automatizadas

- `OpenApiContractTest`: duas verificações globais e 18 casos por operação.
  Proíbe exemplos específicos no schema genérico, verifica referências locais,
  estrutura/status dos exemplos, respostas JSON com exemplos explícitos,
  segurança pública/JWT/IoT, download binário e cancelamento sem corpo.
- `OpenApiErrorContractConfiguration` adiciona um verificador aos MockMvc dos testes
  PostgreSQL e bootstrap. Cada erro precisa corresponder a um exemplo de sua
  operação/status: código e mensagem exatos, JSON de quatro campos, status inteiro,
  timestamp ISO 8601 e Content-Type JSON. Os cenários anteriores de JWT expirado,
  assinatura incorreta, uploads inválidos, concorrência e rollback continuam ativos.
- Seis testes HTTP adicionais em `PostgresIntegrationTest` verificam validação e
  duplicidade no cadastro/perfil, mensagens do login/carteira, headers de autenticação,
  catálogo público, 415, os erros IoT, propriedade das fotos/descartes, conflitos de
  criação/cancelamento, erros de resgate e identidade indisponível. Os erros novos
  também têm expectativas literais independentes do YAML e checagem de ausência
  dos segredos do teste, senha, stack trace e detalhes PostgreSQL.
- Os 65 testes anteriores foram preservados, sem testes desabilitados. Testcontainers
  cria os bancos descartáveis; o teste do inicializador usa seu próprio banco ou
  Docker simulado. O inicializador não foi executado no banco local preenchido.

## Validação

`./mvnw clean verify`: **91 testes, zero falhas, zero erros, zero ignorados**,
com Docker/Testcontainers disponível. Inclui persistência/reinício HTTP e bootstrap.

Validação formal adicional, em um virtualenv temporário fora do projeto:
`openapi-spec-validator` 0.7.2 e `openapi-schema-validator` 0.6.3.
O documento passou como OpenAPI 3.0.3; **134 usos de exemplos de resposta** foram
validados contra seus schemas com `OAS30Validator`, resolvendo referências locais.
Isso complementa as verificações de regressão Java; o POM não foi alterado.

Para validar a estrutura novamente, instale `openapi-spec-validator` em um ambiente
isolado e execute `openapi-spec-validator src/main/resources/static/docs/openapi.yaml`.
Para rodar as regressões de contrato e HTTP, use o Maven Wrapper acima.

O Swagger UI foi conferido no navegador com o YAML atualizado, em
`http://localhost:8080/swagger-ui.html`: cadastro 400/409, carteira 401,
IoT 400/401/403/404/409/422/500 e resgate com suas variantes de conflito.
A seleção de exemplos alternativos exibiu `INVALID_TOKEN`, `EVENT_ID_CONFLICT`
e `IDEMPOTENCY_KEY_CONFLICT` com seus respectivos status e mensagens, sem herdar
os valores do descarte. A UI permanece em modo de consulta; nenhuma operação de
escrita foi executada no PostgreSQL local. Apenas o processo do backend previamente
iniciado foi reiniciado para servir a documentação nova. Nenhum push ou merge faz
parte desta auditoria.
