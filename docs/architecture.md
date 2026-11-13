# Arquitetura do backend

O ReLoop é uma aplicação Spring Boot única, com API REST MVC sob `/api/v1` e
módulos organizados por domínio em `br.com.fiap.reloop`. A classe
`ReLoopApplication` chama `SpringApplication.run`. O Boot carrega as
configurações do perfil ativo, monta o contexto de componentes, aplica as
autoconfigurações compatíveis com as dependências e inicia o Tomcat embutido.

Injeção de dependências significa que o Spring fornece os objetos de que um
componente precisa. Um Controller pode receber um Service no construtor, e
um teste pode fornecer um substituto para o mesmo Service. A configuração
de segurança atual recebe por parâmetro o `ObjectMapper` já configurado pelo
Boot para escrever erros JSON.

## Módulos

| Pacote | Responsabilidade |
| --- | --- |
| `auth` | Cadastro, validação de credenciais e emissão de JWT. |
| `user` | Perfil e identidade do cidadão. |
| `collection` | Locais de coleta, coletores, categorias e parâmetros de pontuação. |
| `disposal` | Ciclo de vida do descarte e cálculo da pontuação. |
| `iot` | Credencial do coletor, deduplicação e encaminhamento dos eventos ao módulo de descarte. |
| `wallet` | Saldo, créditos, débitos e extrato. |
| `reward` | Catálogo, estoque, resgates e cupons. |
| `upload` | Recepção de fotos e verificação de titularidade. |
| `security` | Emissão e validação de JWT, identidade do usuário e controle de acesso HTTP. |
| `shared.web` | Modelo de erro HTTP e tratamento de exceções MVC. |

## Camadas

Crie as camadas dentro de cada módulo conforme os casos de uso forem
implementados.

- **Controller** traduz o contrato HTTP, valida o DTO de entrada, chama o
  Service e monta a resposta com o status previsto.
- **Service** coordena casos de uso, regras de negócio, chamadas entre
  módulos e limites de transação.
- **Repository** carrega e grava dados, normalmente por meio de interfaces
  do Spring Data JPA.
- **Entity** representa o estado persistido e seu mapeamento para o banco.
- **DTO** define os campos trocados com os clientes. Ele permite evoluir a
  persistência mantendo estável o formato HTTP.

O caminho típico é Controller → Service → Repository. Serviços também podem
chamar serviços de outros módulos. O contrato HTTP versionado fica no
[OpenAPI](../src/main/resources/static/docs/openapi.yaml).

## Fluxo de descarte

1. O cidadão cadastra a conta e começa sem créditos no mecanismo em
   memória. A futura criação da carteira persistente deve ocorrer na
   mesma transação do cadastro. Após o login, o cliente envia o JWT nas
   rotas protegidas.
2. O aplicativo consulta os pontos e as categorias. Ao escanear o QR Code,
   obtém o identificador do coletor, por exemplo, `COL-001`.
3. O aplicativo envia a foto em `POST /api/v1/uploads`. `upload` registra
   seu proprietário e devolve `photoId` e `photoUrl`.
4. O aplicativo informa coletor, categoria, nome do item e foto em
   `POST /api/v1/disposals`. `disposal` consulta `collection` para validar
   local, coletor e categoria e verifica a titularidade da foto com `upload`.
   O descarte começa em `WAITING_WEIGHT`, com peso e estimativa nulos e
   zero pontos creditados.
5. O simulador envia eventos a `POST /api/v1/iot/events`, com
   `X-Device-Key`, `eventId`, `deviceId` e `disposalId`. O peso é enviado
   em `WEIGHT_RECORDED`. Cada evento exige o estado anterior indicado na tabela.

   | Estado anterior | Ação | Estado resultante |
   | --- | --- | --- |
   | `WAITING_WEIGHT` | `WEIGHT_RECORDED` | `WAITING_DEPOSIT` |
   | `WAITING_DEPOSIT` | `DOOR_OPENED` | `DEPOSIT_IN_PROGRESS` |
   | `DEPOSIT_IN_PROGRESS` | `DOOR_CLOSED` | `COMPLETED` |
   | `WAITING_WEIGHT` | Cancelamento pelo proprietário | `CANCELLED` |

6. O cidadão consulta o descarte ou o histórico para acompanhar o estado
   e a carteira para ver o saldo. Somente o proprietário pode consultar ou
   cancelar o descarte.

## Pontos e transações

Ao pesar, `disposal` valida os limites inclusivos de peso da categoria e
calcula `min(maxPoints, roundHalfUp(basePoints + weightKg * pointsPerKg))`.
O cálculo usa `BigDecimal`, escala zero e `RoundingMode.HALF_UP`. O backend
obtém os parâmetros da categoria e guarda a estimativa no descarte. No
fechamento, usa essa estimativa para o crédito.

| Categoria | Base | Pontos/kg | Máximo |
| --- | ---: | ---: | ---: |
| Celulares | 30 | 5 | 40 |
| Notebooks | 25 | 5 | 45 |
| Pequenos eletrodomésticos | 10 | 4 | 35 |
| Cabos e carregadores | 0 | 4 | 25 |

Para um pequeno eletrodoméstico de 3,8 kg dentro dos limites da categoria,
`10 + 3,8 × 4 = 25,2`. O arredondamento resulta em 25 pontos.

- **Eventos IoT.** `iot` autentica a credencial e confirma que ela autoriza o
  coletor. O filtro exige `X-Device-Key` antes da leitura do JSON, e o
  controller confere se a chave autoriza o `deviceId` do corpo.
  `IotEventService` verifica a transição do descarte e usa
  `PointCalculationService` na pesagem. Na repetição de um
  `eventId` com dados equivalentes, retorna a resposta original. Uma
  divergência gera `409`. A comparação do peso usa o valor decimal, de modo
  que `3.8` e `3.80` são equivalentes.
- **Persistência no bootstrap.** O adaptador em memória implementa
  `DisposalRepository` e `IotEventRepository`. A mesma seção sincronizada
  protege o descarte, o resultado associado ao identificador do evento e o
  crédito temporário indexado pelo ID do descarte. Ela também coordena a
  pesagem com o cancelamento e a abertura de novas sessões. Uma falha de
  validação ocorre antes da gravação. Cada crédito registra o usuário,
  o valor e o instante.
- **Fechamento do descarte.** `disposal` valida a transição e chama o serviço
  interno de `wallet` na implementação futura da carteira, com a identidade
  do usuário, o ID do descarte e os pontos estimados. A transação incluirá o
  crédito, a atualização do saldo e a transição a `COMPLETED`. Se o valor
  for zero, o descarte é concluído com zero pontos e sem lançamento.
- **Resgate.** `reward` usa a `Idempotency-Key` enviada pelo cliente e
  vinculada ao usuário. O serviço atualiza estoque e carteira, registra o
  débito e emite o cupom na mesma transação. Em uma repetição idêntica, devolve
  o cupom e o saldo registrados no resgate original. Operações concorrentes
  devem preservar o saldo e o estoque não negativos.

## Consultas do catálogo

`CollectionPointController` e `ElectronicCategoryController` recebem as
consultas e delegam aos serviços correspondentes. Os serviços usam
`CollectionPointRepository` e `ElectronicCategoryRepository` para ler os
dados, então convertem os modelos em DTOs do OpenAPI. O controller traduz
a ausência de um ponto para HTTP 404, formatado pelo tratamento de erros
compartilhado.

No perfil `bootstrap`, `InMemoryCollectionCatalog` fornece as duas
interfaces a partir de listas imutáveis. O futuro adaptador PostgreSQL
implementará as mesmas interfaces com Spring Data JPA. Ele deverá fornecer
os modelos de leitura do catálogo, incluindo as categorias aceitas e os
coletores no detalhe de um ponto. O fluxo de descarte consulta os
parâmetros da categoria no momento da pesagem.

## Segurança

A configuração HTTP é sem sessão. No perfil `bootstrap`, cadastro,
login, catálogo e documentação são públicos. O health check também é
público. O Spring Security exige Bearer JWT nas rotas de perfil, fotos e
descartes. O filtro de IoT exige `X-Device-Key` em sua própria rota.
O tratamento de exceções MVC e os erros do filtro usam o modelo `ApiError`.

No cadastro, `AuthService` gera o hash PBKDF2 com salt e `UserService`
normaliza o e-mail. O repositório garante a unicidade durante cadastro e
atualização. No login, `AuthService` verifica a senha e `JwtService` emite
um token HS256 de uma hora. O emissor é gerado por instância para invalidar
tokens antigos mesmo se o processo reiniciar com o mesmo segredo e
reutilizar os IDs numéricos.

Em uma rota privada, o Spring Security autentica a requisição validando
assinatura, emissor e validade do token. `JwtCurrentUserProvider` lê
o ID do claim `sub` no contexto autenticado. A autorização de cada recurso
ocorre nos serviços de fotos e descartes, que verificam a titularidade
com esse ID. Recursos de outro usuário são tratados como indisponíveis
e retornam `404`.

## Cadastro e cancelamento no bootstrap

`UploadController` recebe a foto multipart e `PhotoService` valida o
conteúdo antes de gravá-lo por `PhotoStorage`. O adaptador temporário gera
um UUID, guarda o arquivo fora do repositório e mantém os metadados em
memória. A URL de leitura é montada a partir da base configurada.

`DisposalController` converte o JSON em `CreateDisposalRequest` e valida o
DTO. `DisposalService` consulta os serviços do catálogo para validar
coletor, ponto e categoria e pede ao serviço de fotos que confirme a
titularidade. O repositório cria o descarte e garante, em uma única seção
sincronizada, a exclusividade da foto e da sessão ativa do coletor.
O cancelamento atualiza o estado somente de `WAITING_WEIGHT` para
`CANCELLED`. A foto continua vinculada e o coletor volta a ficar disponível.
