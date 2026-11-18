# Estabilização após a auditoria PostgreSQL

Trabalho na branch `feat/postgres-integration`, sobre `fd617ba`, preservando
os commits anteriores, o SQL/DER/checksum do Hiago e os endpoints da main.
Não foram importadas as alterações de endpoints da branch adiada.

## Carteira: um snapshot para saldo e extrato

O caminho é `WalletController.currentWallet` → `WalletService.currentWallet`
→ `JpaWalletAdapter.findByUserId`. Controller e service não abrem uma transação.
O adaptador passa a abrir a leitura com `Isolation.REPEATABLE_READ`, sem alterar
as transações de crédito/resgate. O record de domínio e sua lista são montados
no mesmo snapshot e permanecem imutáveis depois que a transação termina.

O teste HTTP pausa imediatamente antes da consulta do extrato, depois que a
carteira já foi consultada. Uma segunda conexão confirma um resgate de 80
pontos e o banco passa de 83 para 3. A leitura pausada continua vendo 83 e
somente o crédito original; a próxima requisição vê 3 e os dois lançamentos.
O teste também consulta o isolamento real da conexão. Usa latches com prazos,
sem pausas arbitrárias, e delega a consulta ao repositório JPA real.

A regressão foi comprovada executando esse teste com o comportamento anterior:
**1 teste, 1 falha, 0 erros**, porque o extrato tinha dois lançamentos enquanto
os totais ainda eram antigos. O fonte corrigido foi restaurado em `finally`,
sem reset, rebase ou alteração de commits, e a suíte completa passou.

## Fotografias: resultado desconhecido não é rollback

`JpaPhotoStorage` agora trata explicitamente:

| Resultado | Arquivo | Ação |
| --- | --- | --- |
| `STATUS_COMMITTED` | Preservado | Nenhuma compensação. |
| `STATUS_ROLLED_BACK` | Removido | Compensação do arquivo novo após rollback confirmado. |
| `STATUS_UNKNOWN` | Preservado | Aviso com UUID e necessidade de reconciliação. |

Três testes do callback usam arquivos reais e store simulado; verificam os
bytes, a retenção/remoção e o aviso. Os testes PostgreSQL existentes continuam
verificando rollback real de metadados e recuperação após commit/reinício.
Não se simula uma queda de rede real nem se presume ausência de metadados
quando o commit é incerto.

Não foi criado um reconciliador automático. Após resultado desconhecido,
é preciso recuperar a disponibilidade do banco, confirmar o resultado e
conferir o vínculo antes de remover qualquer arquivo. Quedas abruptas ainda
podem deixar arquivos sem metadados; banco e diretório de fotos devem ter
backup coordenado.

## Inicialização: healthcheck com prazo antes do SQL

`scripts/init-postgres.sh` usa `docker compose up -d --wait --wait-timeout`,
com limite padrão de 60 segundos. A variável exportada
`POSTGRES_INIT_TIMEOUT_SECONDS` deve ser um inteiro positivo. Valor inválido
encerra com código 2; falha de disponibilidade encerra com código 1 e mensagem
clara, sem chamar o psql. O Compose verifica TCP em 127.0.0.1 para não aceitar
somente o servidor temporário de socket Unix do entrypoint como pronto.

O advisory lock, a verificação de schema vazio e o erro de reaplicação foram
preservados. O README e o guia distinguem banco novo de banco já preenchido.
O guia exporta o `.env` antes de chamar o inicializador, de modo que o timeout
configurado também seja recebido pelo shell.

Nove testes executam o script real com um Docker CLI simulado, sem alcançar
qualquer banco do desenvolvedor. Um teste PostgreSQL executa o corpo SQL do
script em outro banco criado dentro do Testcontainers: primeira aplicação
bem-sucedida, segunda recusada com código 3 e contagens preservadas. Não foi
executado o inicializador no banco local preenchido, nem foram apagados
volumes do desenvolvedor.

## Segurança e comportamento preservado

Novas regressões verificam:

- JWT ausente, malformado, expirado, assinatura incorreta, emissor incorreto
  e token sem assinatura, nas leituras e escritas privadas. Um token de
  controle produzido pelo mesmo helper é aceito antes das alterações.
- Isolamento de perfil, carteira e históricos; descarte/foto alheios retornam
  404 e a foto de outra conta não pode ser associada a um descarte.
- Chave IoT ausente/incorreta/duplicada, JWT de cidadão no lugar da credencial,
  dispositivo não autorizado e descarte incompatível.
- eventId repetido com descarte, tipo ou peso diferentes; os valores aceitos
  permanecem intactos, e o replay correto continua funcionando.
- Foto ausente, vazia, falsa, truncada, GIF e maior que 10 MiB. Nada é gravado
  nesses casos; PNG/JPEG reais são aceitos mesmo com extensão/MIME declarados
  diferentes, e os bytes são recuperados somente pelo proprietário.
- Upload acima do limite também é rejeitado com 413 por Tomcat via HTTP real.
- A jornada PostgreSQL mantém 83 → 3; a nova jornada bootstrap usa o catálogo
  em memória e verifica 33 → 13 com crédito/cupom idempotentes.

Esses casos confirmaram proteções existentes. Não foi encontrada outra falha
que exigisse mudar controllers, DTOs, configuração JWT, OpenAPI ou fórmula de
pontos nesta tarefa.

## Resultados e limites

`./mvnw clean verify`: **BUILD SUCCESS — 65 testes, zero falhas, erros ou
ignorados**, com PostgreSQL real via Testcontainers. Os 27 testes anteriores
foram mantidos e 38 execuções adicionadas. Não há testes desabilitados.
Relatórios: `target/surefire-reports`. A execução normal e a repetição limpa
com ordem aleatória passaram os 65 testes, sem falhas, erros ou ignorados.
A ordem de classes e métodos foi variada com sementes 20261118.

Permanecem as limitações documentadas: hashes fictícios incompatíveis, bytes
das fotos históricas ausentes, emissor JWT por instância, ausência de testes
de carga prolongada/cluster e necessidade de credenciais com privilégios
reduzidos no ambiente definitivo. A rotina de reconciliação de arquivos
continua manual. Esses pontos não foram convertidos em funcionalidades novas.

Com as regressões aprovadas, recomenda-se abrir PR e integrar após a revisão
usual. Não foi realizado push ou merge automático.
