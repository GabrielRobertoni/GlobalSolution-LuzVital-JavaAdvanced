# Luz Vital - API Java (Spring Boot)

Global Solution 2026/2 - CitizenTech - Java Advanced

O Luz Vital é voltado para quem depende de aparelhos elétricos para viver, como ventilador mecânico, concentrador de oxigênio, aspirador de secreção e bomba de infusão. Quando a luz cai, a ideia é que o socorro seja organizado pelos minutos de bateria que ainda restam em cada casa, e não pela ordem em que os chamados chegam.

Este repositório tem a API Java do projeto: cadastros, login com papéis, a parte de IA generativa e o evento assíncrono que monta a fila do gestor. O app mobile (React Native) consome esta mesma API.

| | |
|---|---|
| Repositório | https://github.com/GabrielRobertoni/GlobalSolution-LuzVital-JavaAdvanced |
| Vídeo pitch | `<link do vídeo>` |
| Deploy (Swagger) | https://luz-vital-api.onrender.com/swagger-ui.html |
| Requisito 5 (arquitetura distribuída) | Opção A: RabbitMQ, com evento publicado e consumido de forma assíncrona |

## Como testar

O jeito mais simples é usar o deploy, que não exige instalar nada. A API publicada está ligada ao Oracle da FIAP (tabelas do banco da disciplina), a um RabbitMQ no CloudAMQP e ao Gemini, então dá para testar tudo, inclusive a IA.

1. Abra o Swagger: https://luz-vital-api.onrender.com/swagger-ui.html (o endereço https://luz-vital-api.onrender.com também leva para ele).
   > Usamos o plano gratuito do Render, que desliga a API depois de 15 minutos parada. Por isso a primeira abertura pode levar de 1 a 3 minutos. Depois disso responde normalmente.
2. Em "1. Autenticação", abra `POST /auth/login`, clique em Try it out, cole um dos logins abaixo e clique em Execute:
   - gestora: `{"email": "luciana.mendes@defesacivil.sp.gov.br", "senha": "hash_luciana_adm"}`
   - cuidador: `{"email": "carlos.silva@gmail.com", "senha": "hash_carlos123"}`
3. Copie o `token` da resposta, clique em Authorize (o cadeado no topo da página), cole e confirme.
4. A partir daí é só seguir o [roteiro de teste](#roteiro-de-teste). Os outros usuários estão em [Usuários de teste](#usuários-de-teste).

Também dá para rodar no próprio computador, com Docker ou Maven (veja [Como rodar](#como-rodar)). Nesse caso a IA só funciona com uma chave própria do Gemini em `IA_API_KEY`. Sem a chave, a API continua funcionando e o plano de contingência sai por regras fixas.

> O Luz Vital não é serviço de emergência. Não substitui o SAMU (192), os Bombeiros (193) nem o canal da distribuidora de energia, e não dá orientação clínica. As respostas de ocorrência e de plano sempre trazem esse aviso.

## Equipe

| Nome | RM | Turma |
|---|---|---|
| Hebert Lopes do Santos | 563192 | 2TDSR |
| Nicolas Monteiro Ramiro | 562380 | 2TDSR |
| Marcus Vinícius Vila Nova da Silva | 558771 | 2TDSR |
| Bruno Ferreira | 563489 | 2TDSR |
| Gabriel Robertoni Padilha | 566293 | 2TDSR |

## Sumário

- [Como testar](#como-testar)
- [Tecnologias](#tecnologias)
- [Arquitetura](#arquitetura)
- [Modelo de dados](#modelo-de-dados)
- [Como rodar](#como-rodar)
- [Usuários de teste](#usuários-de-teste)
- [Endpoints principais](#endpoints-principais)
- [Roteiro de teste](#roteiro-de-teste)
- [Como cada requisito foi atendido](#como-cada-requisito-foi-atendido)
- [Integração com o app mobile e com a API .NET](#integração-com-o-app-mobile-e-com-a-api-net)
- [Testes automatizados](#testes-automatizados)
- [Declaração de uso de IA](#declaração-de-uso-de-ia)

## Tecnologias

- Java 21 e Spring Boot 4.1
- Spring Data JPA (Hibernate 7) sobre as tabelas `T_GS_*` do banco da disciplina. Localmente usa PostgreSQL; no deploy, o Oracle da FIAP
- Spring Security com JWT (resource server, HS256) e os papéis `CUIDADOR` e `GESTOR`
- Spring AI 2.0: `ChatClient` com saída estruturada em records e entrada de imagem (foto da etiqueta). O provedor segue a API da OpenAI; usamos o Gemini, mas também funciona com OpenAI ou Groq
- RabbitMQ (Spring AMQP): exchange do tipo topic, filas duráveis e dead letter queue
- Bean Validation, `@RestControllerAdvice` e springdoc-openapi (Swagger)
- JUnit 5, MockMvc e H2 nos testes

## Arquitetura

```mermaid
flowchart LR
    APP[App React Native<br/>cuidador e gestor] -- JWT --> API[API Java<br/>Spring Boot]
    NET[API .NET<br/>canal de emergência] -. mesmo JWT .-> API
    API -- JPA --> DB[(Oracle T_GS_* /<br/>PostgreSQL)]
    API -- "publica energia.caiu<br/>e plano.solicitado" --> MQ{{RabbitMQ<br/>exchange luzvital.eventos}}
    MQ -- luzvital.energia-caiu --> C1[Consumidor:<br/>prioridade + pontos do bairro]
    MQ -- luzvital.plano-contingencia --> C2[Consumidor:<br/>plano com Spring AI]
    C2 -- ChatClient --> LLM[LLM<br/>Gemini / OpenAI]
    API -- "foto da etiqueta<br/>(multimodal)" --> LLM
    C1 --> DB
    C2 --> DB
```

### O fluxo do "Estou sem luz"

1. O app chama `POST /ocorrencias` mandando o cabeçalho `Idempotency-Key`.
2. A API trava a linha do paciente, confere a chave e se ele já tem ocorrência ativa (a mesma regra da procedure `PR_T_GS_ABRIR_OCORRENCIA`). Depois calcula a bateria inicial, que é a menor autonomia entre os aparelhos de suporte à vida, e grava em `T_GS_OCORRENCIA`.
3. O evento `energia.caiu` só é publicado no RabbitMQ depois do commit (`@TransactionalEventListener(AFTER_COMMIT)`), para não avisar sobre algo que não chegou a ser gravado.
4. O consumidor calcula o score de prioridade e avisa os pontos de energia disponíveis no bairro do paciente, começando pelos que têm gerador. Se a mesma mensagem chegar duas vezes, os avisos não se repetem.
5. O gestor vê a ocorrência em `GET /ocorrencias/fila`, ordenada pelos minutos de bateria que restam naquele momento. Em caso de empate, ventilador vem antes de concentrador, depois o maior score e por último a ordem de chegada.

### Regra de prioridade

Seguimos a mesma regra do pacote `PKG_T_GS_EQUIPAMENTO` que o Nicolas fez no Oracle:

- Minutos restantes: a menor autonomia de suporte à vida, descontando o tempo desde que a bateria foi informada (`FN_T_GS_MINUTOS_RESTANTES`).
- Score (`FN_T_GS_CALCULA_PRIORIDADE`):

  | Minutos restantes | Score |
  |---|---|
  | 0 | 1000 |
  | até 60 | 800 + (60 - min) |
  | até 180 | 500 + (180 - min) |
  | acima de 180 | 100 |

  Quem usa aparelho de suporte à vida ganha mais 200.

### Se o RabbitMQ falhar

- Um erro no consumidor gera até 3 tentativas, com intervalo crescente. Se continuar falhando, a mensagem vai para a fila `.dlq` em vez de ficar em loop.
- Se o RabbitMQ estiver fora do ar quando a ocorrência for aberta, ela é gravada mesmo assim e já aparece na fila (ordenada pelos minutos). Um job tenta publicar o evento de novo a cada 60 segundos até ele ser consumido.

## Modelo de dados

A API usa as tabelas do script da disciplina de banco (`GSBancoOracle.sql`) do jeito que estão: mesmos nomes de tabela e coluna, mesmos tipos e mesmos valores. Isso inclui os tipos de socorro com acento exigidos pela constraint `CK_T_GS_ATEND_TIPO` (`REMOÇAO_UTI`, `RELIGAÇÃO_PRIORITARIA`) e o aparelho `ASPIRADOR_SECREÇÃO` que vem na carga.

| Tabela | Entidade JPA | Relacionamentos |
|---|---|---|
| `T_GS_USUARIO` | `Usuario` | um cuidador tem vários pacientes |
| `T_GS_PACIENTE` | `Paciente` | vários equipamentos e várias ocorrências |
| `T_GS_EQUIPAMENTO` | `Equipamento` | pertence a um paciente |
| `T_GS_PONTO_ENERGIA` | `PontoEnergia` | vários atendimentos |
| `T_GS_OCORRENCIA` | `Ocorrencia` | pertence a um paciente e tem vários atendimentos |
| `T_GS_ATENDIMENTO` | `Atendimento` | pertence a uma ocorrência e pode apontar um ponto de energia |

```mermaid
erDiagram
    T_GS_USUARIO ||--o{ T_GS_PACIENTE : "cuida de"
    T_GS_PACIENTE ||--o{ T_GS_EQUIPAMENTO : "usa"
    T_GS_PACIENTE ||--o{ T_GS_OCORRENCIA : "abre"
    T_GS_OCORRENCIA ||--o{ T_GS_ATENDIMENTO : "recebe"
    T_GS_PONTO_ENERGIA |o--o{ T_GS_ATENDIMENTO : "é acionado"
```

Não criamos nenhuma tabela a mais, e a API não grava nem altera nada quando sobe. Algumas informações só fazem sentido para o backend e não têm coluna no modelo do banco, então ficam na memória da API:

| O que fica em memória | Para que serve | Se a API reiniciar |
|---|---|---|
| Chave de idempotência do app | O "Estou sem luz" reenviado devolve a mesma ocorrência | A regra de uma ocorrência ativa por paciente continua evitando duplicidade |
| Horário em que a bateria foi informada | Contagem regressiva depois de um `PATCH /bateria` | A contagem volta a partir do `dh_inicio` |
| Avisos aos pontos de energia | Resultado do consumidor de `energia.caiu` | As próximas ocorrências geram novos avisos |
| Plano de contingência | Resposta da IA (o app guarda uma cópia no celular para usar sem internet) | É gerado de novo na próxima consulta |
| Tokens revogados no logout | Impedir o uso do token depois do logout | Com `JWT_SECRET` vazio, nenhum token antigo vale mais. Com `JWT_SECRET` fixo, um token revogado volta a valer até expirar (8 h por padrão, em `JWT_EXPIRACAO`) |

Os gatilhos do banco continuam funcionando sobre o que a API grava:

- `TRG_T_GS_AUDITORIA_OCORRENCIA` registra cada abertura, atualização e encerramento.
- `TRG_T_GS_UPDATE_BATERIA` recalcula o score quando a bateria muda. A entidade usa `@DynamicUpdate`, então o gatilho só dispara quando `qt_bateria_min` muda de fato.

## Como rodar

Há duas opções: usar o deploy, que já está no ar, ou rodar localmente com Docker ou Maven. Rodando localmente com um PostgreSQL vazio, a API cria as tabelas na primeira vez e carrega os mesmos dados do script Oracle: 5 usuários, 5 pacientes, 5 equipamentos, 5 pontos de energia, 5 ocorrências e 5 atendimentos, com os mesmos logins de teste.

### Deploy no Render

A API está em https://luz-vital-api.onrender.com/swagger-ui.html e usa estes serviços:

| Parte | Onde roda |
|---|---|
| API Java | Render, plano gratuito, com o Dockerfile deste repositório |
| Banco | Oracle 19c da FIAP, só com as tabelas `T_GS_*` do script da equipe de banco |
| Mensageria | RabbitMQ no CloudAMQP, plano gratuito, conexão `amqps://` |
| IA generativa | Gemini, via Spring AI |

Fizemos o deploy para que o professor e o app mobile usem a mesma API e os mesmos dados, sem precisar instalar JDK, PostgreSQL ou RabbitMQ. A configuração está no [`render.yaml`](render.yaml). Senhas e chaves (Oracle, CloudAMQP e Gemini) ficam só no painel do Render, nunca no repositório, e a chave do JWT é gerada pelo próprio Render.

Lembrando: no plano gratuito, depois de 15 minutos sem uso a primeira chamada pode levar de 1 a 3 minutos. O passo a passo para testar está em [Como testar](#como-testar).

### Localmente com Docker

Precisa do Docker Desktop.

```bash
docker compose up --build
```

- Swagger: http://localhost:8080/swagger-ui.html
- Painel do RabbitMQ: http://localhost:15672 (usuário e senha `guest`)

Para ligar a IA, copie o `.env.example` para `.env`, preencha `IA_API_KEY` e rode o mesmo comando. A chave do Gemini é gratuita em https://aistudio.google.com/apikey.

### Localmente sem Docker (Maven Wrapper)

Precisa de JDK 21 ou mais novo, PostgreSQL 14+ e RabbitMQ 3.12+ rodando na máquina.

1. Crie o banco:
   ```sql
   CREATE ROLE luzvital LOGIN PASSWORD 'luzvital';
   CREATE DATABASE luzvital OWNER luzvital;
   ```
2. Se quiser usar a IA ou fixar a chave do JWT, copie o `.env.example` para `.env` e preencha `IA_API_KEY` e `JWT_SECRET`. A API lê o `.env` da pasta do projeto também fora do Docker (IntelliJ ou Maven). Se a mesma variável estiver definida no sistema, ela tem prioridade.
3. Suba a API:
   ```bash
   ./mvnw spring-boot:run
   ```
   No Windows o comando é `mvnw.cmd spring-boot:run`. No IntelliJ, basta rodar a classe `LuzVitalApplication` com a pasta do projeto como diretório de trabalho.

> No Windows, a API usa automaticamente os certificados do sistema nas chamadas HTTPS (como a do provedor de IA). Colocamos isso porque, em algumas redes e antivírus que inspecionam HTTPS, o Java dava o erro `unable to find valid certification path`. Para desligar, defina `LUZVITAL_CERTIFICADOS_WINDOWS=false`.

### Localmente ligado ao Oracle da FIAP

Também testamos a API conectada ao Oracle 19c da FIAP, usando apenas as tabelas `T_GS_*` do script da equipe de banco, sem tabela extra e sem alterar nenhum dado ao subir. Funcionaram o login dos 5 usuários da carga, a leitura de pacientes, aparelhos, pontos, ocorrências e atendimentos (inclusive os valores com acento), a fila do gestor, o plano de contingência via RabbitMQ e o logout. Os gatilhos do banco também reagiram ao que a API gravou.

1. O schema precisa ter o `GSBancoOracle.sql` já executado. Não precisa de mais nada.
2. No `.env`, troque as linhas do banco por estas:
   ```properties
   DB_URL=jdbc:oracle:thin:@oracle.fiap.com.br:1521:ORCL
   DB_USER=<usuário Oracle>
   DB_PASSWORD=<senha Oracle>
   JPA_DDL_AUTO=none
   ```
   Com `JPA_DDL_AUTO=none` a API não cria nem altera tabelas no Oracle: o esquema é o do script.
3. Suba a API normalmente (IntelliJ ou `mvnw spring-boot:run`). O RabbitMQ pode ser o local, o do Docker ou o do CloudAMQP.

Como o banco já tem dados, a carga inicial não roda. A API só grava o que o usuário fizer pelo app ou pelo Swagger.

### Variáveis de ambiente

Nenhuma credencial real está no repositório.

| Variável | Padrão | Para que serve |
|---|---|---|
| `JWT_SECRET` | vazio (gera uma chave aleatória a cada subida) | Chave HS256 com 32 caracteres ou mais. A API .NET precisa usar a mesma |
| `IA_API_KEY` | vazio (IA desligada) | Chave do provedor de IA. Sem ela, o plano sai pelas regras fixas e a leitura de etiqueta responde 503 |
| `IA_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta/openai` | Endpoint compatível com a OpenAI (Gemini, `https://api.openai.com/v1`, Groq...) |
| `IA_MODELO` | `gemini-3.5-flash-lite` | Modelo usado. Para a foto da etiqueta, precisa aceitar imagem |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `luzvital` / `luzvital` / `luzvital` | PostgreSQL local |
| `DB_URL` / `JPA_DDL_AUTO` | vazio / `update` | URL JDBC completa, por exemplo a do Oracle da FIAP (com `JPA_DDL_AUTO=none`) |
| `RABBITMQ_ADDRESSES` | `amqp://guest:guest@localhost:5672` | RabbitMQ (aceita `amqps://` do CloudAMQP) |
| `CORS_ORIGENS` | `*` | Origens liberadas (Expo web) |
| `PORT` | `8080` | Porta HTTP |

Sobre a IA: testamos com chave real do Gemini nos modelos `gemini-3.5-flash-lite` (o padrão, que gera o plano em uns 15 segundos e lê a etiqueta em uns 3) e `gemini-3.5-flash`. No plano gratuito o Google limita as requisições por dia para cada modelo. Se a cota acabar (erro 429), a API segue funcionando: o plano sai pelas regras fixas e a leitura de etiqueta pede o preenchimento manual. Dá para trocar o `IA_MODELO` ou esperar a cota renovar no dia seguinte.

## Usuários de teste

São os mesmos usuários do script Oracle da disciplina de banco, com a senha que está gravada no script (coluna `ds_senha`). No Oracle a senha é conferida do jeito que está no script, porque não alteramos os dados da carga. No PostgreSQL local, a carga guarda essas mesmas senhas com BCrypt. Todo usuário criado pela API (`/auth/cadastro` e `/usuarios/gestores`) é gravado com BCrypt.

| Papel | E-mail | Senha | Paciente e aparelho na carga |
|---|---|---|---|
| CUIDADOR | `carlos.silva@gmail.com` | `hash_carlos123` | João Carlos Silva, ventilador Philips Trilogy Evo com 900 min de bateria |
| CUIDADOR | `mariana.rocha@hotmail.com` | `hash_mariana456` | Maria Aparecida Rocha, concentrador EverFlo sem bateria (tem ocorrência aberta) |
| CUIDADOR | `roberto.ferreira@outlook.com` | `hash_roberto789` | Pedro Henrique Ferreira, só bomba de infusão, que não é de suporte à vida (bom para testar a recusa) |
| GESTOR | `luciana.mendes@defesacivil.sp.gov.br` | `hash_luciana_adm` | Defesa Civil: vê a fila, registra atendimentos, cuida dos pontos e dos gestores |
| GESTOR | `fernando.souza@enel.com.br` | `hash_enel_gestor` | Distribuidora, com o mesmo acesso de gestor |

## Endpoints principais

Todas as listagens são paginadas (`?page=0&size=10&sort=campo,asc`) e voltam no formato `{conteudo, pagina, tamanho, totalElementos, totalPaginas, ultima}`.

| Método e rota | Quem pode | O que faz |
|---|---|---|
| `POST /auth/cadastro` | público | Cadastra um cuidador (sempre com papel CUIDADOR) e já devolve o token |
| `POST /auth/login` | público | Login, devolve o JWT |
| `POST /auth/logout` | autenticado | Revoga o token atual |
| `GET /auth/me` | autenticado | Dados do usuário logado |
| `GET/POST /pacientes`, `GET/PUT/DELETE /pacientes/{id}` | cuidador (só os dele); gestor lê todos | CRUD de pacientes |
| `GET/POST /equipamentos`, `GET/PUT/DELETE /equipamentos/{id}` | cuidador (só os dele); gestor lê | CRUD de aparelhos. Salvar, alterar ou excluir um aparelho pede um novo plano à IA |
| `POST /equipamentos/leitura-etiqueta` | cuidador | Cadastro por foto: a IA lê fabricante, modelo, tensão, corrente e potência (multipart, campo `foto`) |
| `GET /pacientes/{id}/plano-contingencia` | dono ou gestor | Plano de contingência gerado pela IA |
| `POST /pacientes/{id}/plano-contingencia` | dono | Pede um plano novo (responde 202 e gera em segundo plano) |
| `GET/POST /pontos-energia`, `GET/PUT/DELETE /pontos-energia/{id}` | todos leem; gestor altera | Rede de pontos de energia |
| `POST /ocorrencias` | cuidador | "Estou sem luz" (com o cabeçalho `Idempotency-Key`). Publica `energia.caiu` |
| `GET /ocorrencias`, `GET /ocorrencias/{id}` | dono ou gestor | Ocorrências com contagem regressiva, atendimentos e pontos avisados |
| `GET /ocorrencias/fila` | gestor | Fila ordenada pelos minutos de bateria que restam |
| `PATCH /ocorrencias/{id}/bateria` | dono ou gestor | Atualiza a bateria e recalcula a prioridade |
| `POST /ocorrencias/{id}/atendimentos` | gestor | Registra o socorro: `GERADOR_MOVEL`, `RECARGA_BATERIA`, `REMOCAO_UTI` ou `RELIGACAO_PRIORITARIA` |
| `POST /ocorrencias/{id}/encerramento` | dono ou gestor | Encerra como ATENDIDO e anexa o desfecho à observação, como faz a `PR_T_GS_ENCERRAR_OCORRENCIA` |
| `POST /ocorrencias/{id}/cancelamento` | dono ou gestor | Cancela (por exemplo, quando a energia volta) |
| `GET /ocorrencias/indicadores` | gestor | Tempo médio de atendimento, % resolvidas antes de a bateria acabar e pontos por bairro |
| `POST /usuarios/gestores`, `GET /usuarios` | gestor | Só um gestor pode criar outro gestor |
| `GET /actuator/health` | público | Health check (usado pela API .NET) |

### Formato dos erros

Todos os erros (400, 401, 403, 404, 409, 422, 503 e 500) seguem o mesmo formato:

```json
{
  "timestamp": "2026-11-10T21:30:00",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Dados inválidos",
  "caminho": "/equipamentos",
  "campos": [
    { "campo": "potenciaWatts", "mensagem": "a potência deve ser maior que zero" },
    { "campo": "autonomiaMinutos", "mensagem": "a autonomia não pode ser negativa" }
  ]
}
```

## Roteiro de teste

Serve tanto para o deploy quanto para a execução local, porque os ids são os mesmos da carga do banco: pacientes 1 (João), 2 (Maria) e 3 (Pedro), ocorrência 1 (Maria) e ponto 2 (Padaria Central Novo Milênio). O login no Swagger está explicado em [Como testar](#como-testar).

1. Entre como gestora (`luciana.mendes@defesacivil.sp.gov.br` / `hash_luciana_adm`) e chame `GET /ocorrencias/fila`.
   - As 3 ocorrências ativas da carga aparecem ordenadas pelos minutos de bateria. No deploy, a carga do Oracle foi feita há alguns dias, então as três já estão com 0 minuto e o desempate pelo aparelho coloca o José (ventilador) na frente da Maria (concentrador). Num PostgreSQL recém-carregado, a ordem é Maria, Ana e José.
   - Todas aparecem com `priorizada: true`, usando o score que veio na carga.
2. Entre como Mariana e chame `GET /ocorrencias/1`: aparece o atendimento `GERADOR_MOVEL` da carga. Os `pontosNotificados` são preenchidos pelo consumidor do RabbitMQ nas ocorrências abertas pela API (passo 6).
3. Entre como Carlos (`carlos.silva@gmail.com` / `hash_carlos123`) e chame `GET /pacientes`: aparece o João, além dos pacientes que você cadastrar. `GET /pacientes/2`, que é da Mariana, devolve 403.
4. `GET /pacientes/1/plano-contingencia`: na primeira consulta vem `GERANDO` e o pedido vai para o RabbitMQ (`plano.solicitado`). Alguns segundos depois, a mesma consulta traz o plano `PRONTO`, com `origem` igual a `IA` (ou `REGRAS`, se não houver chave de IA).
5. `POST /equipamentos` com `potenciaWatts: 0` devolve 400 com a lista de campos inválidos.
6. `POST /ocorrencias` com `{"pacienteId": 1}` e um UUID qualquer no cabeçalho `Idempotency-Key` (por exemplo `3f1c2b9e-8a4d-4c6e-9f00-1b2c3d4e5f60`) devolve 201.
   - Se já vier 409 na primeira tentativa, é porque alguém abriu uma ocorrência do João no deploy antes. Pegue o id em `GET /ocorrencias?pacienteId=1&status=EM_ABERTO`, cancele com `POST /ocorrencias/{id}/cancelamento` e tente de novo.
   - Repetir com a mesma chave devolve 200 e a mesma ocorrência.
   - Repetir sem chave devolve 409.
   - `GET /ocorrencias/{id}` mostra em `pontosNotificados` a Padaria Central Novo Milênio, o ponto do Jardim Paulista avisado pelo consumidor de `energia.caiu`.
7. `PATCH /ocorrencias/{id}/bateria` com `{"minutosRestantes": 45}` devolve o score 1015, e o João muda de posição na fila conforme os 45 minutos.
8. Como gestora, `POST /ocorrencias/{id}/atendimentos` com `{"tipoSocorro":"REMOCAO_UTI","descricao":"...","pontoEnergiaId":2}` muda o status para `EM_ATENDIMENTO`.
9. `POST /ocorrencias/{id}/encerramento` devolve 200. Tentar encerrar de novo devolve 409.
10. Entre como Roberto e faça `POST /ocorrencias` com `{"pacienteId": 3}`: volta 422, porque o Pedro não tem aparelho de suporte à vida.
11. Como Carlos, `GET /ocorrencias/fila` devolve 403.
12. RabbitMQ: no deploy, dá para ver o processamento assíncrono pelos `pontosNotificados` do passo 6 (consumidor de `energia.caiu`) e pelo plano que passa de `GERANDO` para `PRONTO` no passo 4 (consumidor de `plano.solicitado`). Rodando localmente, o painel http://localhost:15672 (guest / guest) mostra em Queues as filas `luzvital.energia-caiu` e `luzvital.plano-contingencia` com seus consumidores.

## Como cada requisito foi atendido

| # | Requisito | Onde está |
|---|---|---|
| 01 | API REST, 3 ou mais entidades relacionadas, CRUD e paginação | 6 entidades mapeadas nas tabelas `T_GS_*` (pacote `domain`): Usuário tem Pacientes, que têm Equipamentos e Ocorrências; cada Ocorrência tem Atendimentos, que podem apontar um Ponto de energia. CRUD completo de pacientes, equipamentos e pontos, e todas as listagens são paginadas |
| 02 | Bean Validation nos DTOs e `@RestControllerAdvice` | Records do pacote `dto` validam CPF, CEP, UF, telefone, e-mail, potência maior que zero, autonomia não negativa, tipos de socorro da constraint etc. Os erros saem por `exception/GlobalExceptionHandler`, e os 401/403 por `security/ErroSegurancaHandler`, no mesmo formato |
| 03 | Spring Security com JWT, 2 papéis e rotas por papel | `config/SecurityConfig` (regras por rota) e `@PreAuthorize` nos controllers, com o token gerado em `security/JwtService`. O cuidador só acessa os próprios pacientes (`service/Acesso`), usuário inativo (`st_usuario = 'N'`) não consegue entrar e o logout revoga o token |
| 04 | Spring AI integrado ao fluxo | `ia/IaService` faz duas coisas: gera o plano de contingência quando um aparelho é salvo, com saída estruturada em `PlanoGerado` (etapas por faixa de tempo, contatos e sinais de alerta), e lê a etiqueta do aparelho por foto, estimando a potência por tensão x corrente. Cuidados: nada de orientação clínica, nome e CPF não vão para o prompt (LGPD), 192 e 193 sempre aparecem, e se a IA falhar o plano sai pelas regras fixas |
| 05 | Arquitetura distribuída: opção A (RabbitMQ) | `messaging/RabbitConfig` (exchange topic, filas e DLQ), `EventoPublisher` (publica depois do commit), `EventoConsumidor` (`energia.caiu` e `plano.solicitado`) e `ReprocessamentoJob` |
| 06 | Swagger, README e integração com o mobile | Swagger em `/swagger-ui.html` com o Bearer configurado, este README e a seção de integração logo abaixo |

## Integração com o app mobile e com a API .NET

App React Native (Axios):

- Cabeçalho: `Authorization: Bearer <token>`. O token vem do `/auth/login` ou do `/auth/cadastro`.
- URL base: `https://luz-vital-api.onrender.com` (deploy). Com a API rodando localmente, use `http://10.0.2.2:8080` no emulador Android ou o IP da máquina na rede (`http://192.168.x.x:8080`) no celular físico.
- Paciente: `{nome, cpf, endereco, bairro, cidade, uf, cep, telefone}`.
- Aparelho: `{pacienteId, tipoAparelho, fabricante, modelo, potenciaWatts, autonomiaMinutos, suporteVida, fonteReserva}`.
- "Estou sem luz": gere um UUID a cada toque, guarde junto com o pedido na fila offline e, se precisar reenviar, use a mesma `Idempotency-Key`. Assim o reenvio não cria outra ocorrência.
- Plano de contingência: `GET /pacientes/{id}/plano-contingencia`. Se vier `GERANDO`, consulte de novo em alguns segundos. Vale salvar o plano no celular para usar sem internet.
- Cadastro por foto: mande `multipart/form-data` com o campo `foto` (JPEG, PNG ou WEBP, até 5 MB), mostre os dados lidos para o cuidador confirmar e só então chame `POST /equipamentos`.

API .NET (mesmo JWT):

- HS256 com a chave `JWT_SECRET`, que precisa ser a mesma nas duas APIs. Valide `iss = luz-vital-api` e `aud = luz-vital-app`.
- Claims: `sub` (id do usuário em `T_GS_USUARIO`), `email`, `nome`, `role` (`CUIDADOR` ou `GESTOR`), `jti` e `exp`.
- Para registrar uma ocorrência na API Java, chame `POST /ocorrencias` repassando o token do usuário e a mesma `Idempotency-Key`. A API Java publica o `energia.caiu`.
- Health check: `GET /actuator/health`.

## Testes automatizados

```bash
./mvnw test
```

São 33 testes, que rodam com H2 em memória (não precisam de Docker):

- Regras: score igual ao da `FN_T_GS_CALCULA_PRIORIDADE`, ordem da fila, contagem regressiva e códigos gravados no banco (constraints com acento).
- Etiqueta: potência estimada por tensão x corrente.
- Spring AI: `ChatModel` simulado, conferindo a saída estruturada, os cuidados no prompt e a nova tentativa quando a resposta vem cortada.
- Integração com MockMvc: 401 e 403 no formato padrão, validação, ordenação inválida, publicação de `energia.caiu`, idempotência, 409 e 422.
- `OracleModeloBancoTest`: sobe a API em cima do DDL e da carga originais do `GSBancoOracle.sql` (H2 em modo Oracle, `ddl-auto=none`), sem nenhuma tabela extra. Confere que a subida não grava nada e que login com as senhas do script, fila, atendimentos, bateria, idempotência, CRUD, plano e logout funcionam no modelo do banco sem alterá-lo.

## Declaração de uso de IA

- No produto: a API usa IA generativa (Spring AI) para gerar o plano de contingência e para ler a etiqueta do aparelho por foto. A IA não dá orientação clínica, e o cuidador sempre confirma os dados lidos. Sem chave configurada, a API continua funcionando com o plano por regras fixas.
- No desenvolvimento: usamos um assistente de IA generativa (Claude, da Anthropic) como apoio na implementação, na revisão do código, nos testes e na documentação. Revisamos, executamos e testamos tudo o que foi entregue, e as decisões de projeto e a entrega são de responsabilidade da equipe.

## Riscos e cuidados

- LGPD: saúde e endereço são dados sensíveis, por isso usamos só dados fictícios. O prompt da IA não recebe nome, CPF nem endereço do paciente.
- A regra de prioridade é uma hipótese do projeto e precisaria ser validada com profissionais de saúde. Não é um protocolo clínico.
- A leitura por foto pode errar. Por isso o app sempre pede confirmação e permite digitar os dados.
