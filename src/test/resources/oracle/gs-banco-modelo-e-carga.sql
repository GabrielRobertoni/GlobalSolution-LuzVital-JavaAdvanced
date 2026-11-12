-- Cópia LITERAL das seções de tabelas (DDL) e de carga de dados do script da disciplina de banco
-- GSBancoOracle.sql (linhas 51-167 e 442-525). Os pacotes e gatilhos PL/SQL ficam de fora porque
-- o teste roda no H2 em modo Oracle. Usado por OracleModeloBancoTest para garantir que a API funciona
-- sobre o modelo e os dados originais do banco, sem nenhuma alteração.

-- Usuários do sistema (Cuidadores e Gestores de emergência).
CREATE TABLE T_GS_USUARIO (
    id_usuario       NUMBER GENERATED ALWAYS AS IDENTITY,
    nm_usuario       VARCHAR2(120) NOT NULL,
    ds_email         VARCHAR2(160) NOT NULL,
    ds_senha         VARCHAR2(255) NOT NULL,
    tp_usuario       VARCHAR2(20) NOT NULL,
    st_usuario       CHAR(1) DEFAULT 'S' NOT NULL,
    dt_criacao       TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_T_GS_USUARIO PRIMARY KEY (id_usuario),
    CONSTRAINT UK_T_GS_USUARIO_EMAIL UNIQUE (ds_email),
    CONSTRAINT CK_T_GS_USUARIO_TIPO CHECK (tp_usuario IN ('CUIDADOR','GESTOR')),
    CONSTRAINT CK_T_GS_USUARIO_STATUS CHECK (st_usuario IN ('S','N'))
);

-- Cadastro de pacientes eletrodependentes.
CREATE TABLE T_GS_PACIENTE (
    id_paciente      NUMBER GENERATED ALWAYS AS IDENTITY,
    id_usuario       NUMBER NOT NULL,
    nm_paciente      VARCHAR2(120) NOT NULL,
    nr_cpf           VARCHAR2(14) NOT NULL,
    ds_endereco      VARCHAR2(200) NOT NULL,
    nm_bairro        VARCHAR2(80) NOT NULL,
    nm_cidade        VARCHAR2(80) NOT NULL,
    sg_estado        CHAR(2) DEFAULT 'SP' NOT NULL,
    nr_cep           VARCHAR2(9) NOT NULL,
    nr_telefone      VARCHAR2(25) NOT NULL,
    dt_criacao       TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_T_GS_PACIENTE PRIMARY KEY (id_paciente),
    CONSTRAINT UK_T_GS_PACIENTE_CPF UNIQUE (nr_cpf),
    CONSTRAINT FK_T_GS_PACIENTE_USUARIO FOREIGN KEY (id_usuario)
        REFERENCES T_GS_USUARIO (id_usuario)
);

-- Equipamentos médicos vitais cadastrados por foto/manual.
CREATE TABLE T_GS_EQUIPAMENTO (
    id_equipamento   NUMBER GENERATED ALWAYS AS IDENTITY,
    id_paciente      NUMBER NOT NULL,
    tp_aparelho      VARCHAR2(50) NOT NULL,
    nm_fabricante    VARCHAR2(80) NOT NULL,
    nm_modelo        VARCHAR2(80) NOT NULL,
    vl_potencia_watts NUMBER(6,2) NOT NULL,
    qt_autonomia_min NUMBER(5) NOT NULL,
    fl_suporte_vida  CHAR(1) DEFAULT 'S' NOT NULL,
    tp_fonte_reserva VARCHAR2(50) DEFAULT 'NENHUMA' NOT NULL,
    dt_criacao       TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_T_GS_EQUIPAMENTO PRIMARY KEY (id_equipamento),
    CONSTRAINT FK_T_GS_EQP_PACIENTE FOREIGN KEY (id_paciente)
        REFERENCES T_GS_PACIENTE (id_paciente),
    CONSTRAINT CK_T_GS_EQP_SUPORTE CHECK (fl_suporte_vida IN ('S','N')),
    CONSTRAINT CK_T_GS_EQP_POTENCIA CHECK (vl_potencia_watts > 0),
    CONSTRAINT CK_T_GS_EQP_AUTONOMIA CHECK (qt_autonomia_min >= 0)
);

-- Pontos de energia comunitários (comércios, condomínios com gerador).
CREATE TABLE T_GS_PONTO_ENERGIA (
    id_ponto         NUMBER GENERATED ALWAYS AS IDENTITY,
    nm_local         VARCHAR2(120) NOT NULL,
    tp_estabelecimento VARCHAR2(50) NOT NULL,
    nm_responsavel   VARCHAR2(100) NOT NULL,
    nr_telefone      VARCHAR2(25) NOT NULL,
    nm_bairro        VARCHAR2(80) NOT NULL,
    fl_possui_gerador CHAR(1) DEFAULT 'N' NOT NULL,
    st_disponivel    CHAR(1) DEFAULT 'S' NOT NULL,
    dt_criacao       TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_T_GS_PONTO_ENERGIA PRIMARY KEY (id_ponto),
    CONSTRAINT CK_T_GS_PONTO_GERADOR CHECK (fl_possui_gerador IN ('S','N')),
    CONSTRAINT CK_T_GS_PONTO_STATUS CHECK (st_disponivel IN ('S','N'))
);

-- Ocorrências abertas no apagão ("Estou sem luz").
CREATE TABLE T_GS_OCORRENCIA (
    id_ocorrencia    NUMBER GENERATED ALWAYS AS IDENTITY,
    id_paciente      NUMBER NOT NULL,
    dh_inicio        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    dh_fim           TIMESTAMP,
    st_ocorrencia    VARCHAR2(20) DEFAULT 'EM_ABERTO' NOT NULL,
    qt_bateria_min   NUMBER(5) NOT NULL,
    vl_score_prioridade NUMBER(5) DEFAULT 0 NOT NULL,
    ds_observacao    VARCHAR2(500),
    CONSTRAINT PK_T_GS_OCORRENCIA PRIMARY KEY (id_ocorrencia),
    CONSTRAINT FK_T_GS_OCO_PACIENTE FOREIGN KEY (id_paciente)
        REFERENCES T_GS_PACIENTE (id_paciente),
    CONSTRAINT CK_T_GS_OCO_STATUS CHECK (st_ocorrencia IN ('EM_ABERTO','EM_ATENDIMENTO','ATENDIDO','CANCELADO'))
);

-- Registro de atendimentos prestados pela Defesa Civil/Distribuidora.
CREATE TABLE T_GS_ATENDIMENTO (
    id_atendimento   NUMBER GENERATED ALWAYS AS IDENTITY,
    id_ocorrencia    NUMBER NOT NULL,
    id_ponto         NUMBER,
    tp_socorro       VARCHAR2(50) NOT NULL,
    ds_atendimento   VARCHAR2(1000) NOT NULL,
    dh_atendimento   TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_T_GS_ATENDIMENTO PRIMARY KEY (id_atendimento),
    CONSTRAINT FK_T_GS_ATEND_OCORRENCIA FOREIGN KEY (id_ocorrencia)
        REFERENCES T_GS_OCORRENCIA (id_ocorrencia),
    CONSTRAINT FK_T_GS_ATEND_PONTO FOREIGN KEY (id_ponto)
        REFERENCES T_GS_PONTO_ENERGIA (id_ponto),
    CONSTRAINT CK_T_GS_ATEND_TIPO CHECK (tp_socorro IN ('GERADOR_MOVEL','RECARGA_BATERIA','REMOÇAO_UTI','RELIGAÇÃO_PRIORITARIA'))
);

/* ============================ TABELA DE AUDITORIA ========================= */

-- Armazena o histórico DML (INSERT/UPDATE/DELETE) de T_GS_OCORRENCIA.
CREATE TABLE T_GS_AUDITORIA_DML (
    id_auditoria     NUMBER GENERATED ALWAYS AS IDENTITY,
    nm_usuario       VARCHAR2(128) NOT NULL,
    tp_operacao      VARCHAR2(10) NOT NULL,
    dh_operacao      TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    nm_tabela        VARCHAR2(128) NOT NULL,
    id_registro      VARCHAR2(100),
    ds_valor_anterior CLOB,
    ds_valor_novo    CLOB,
    CONSTRAINT PK_T_GS_AUDITORIA PRIMARY KEY (id_auditoria),
    CONSTRAINT CK_T_GS_AUDITORIA_OPERACAO CHECK (tp_operacao IN ('INSERT','UPDATE','DELETE'))
);

-- 1. Cinco Usuários
INSERT INTO T_GS_USUARIO (nm_usuario, ds_email, ds_senha, tp_usuario) VALUES ('Carlos Alberto Silva', 'carlos.silva@gmail.com', 'hash_carlos123', 'CUIDADOR');
INSERT INTO T_GS_USUARIO (nm_usuario, ds_email, ds_senha, tp_usuario) VALUES ('Mariana Santos Rocha', 'mariana.rocha@hotmail.com', 'hash_mariana456', 'CUIDADOR');
INSERT INTO T_GS_USUARIO (nm_usuario, ds_email, ds_senha, tp_usuario) VALUES ('Roberto Ferreira', 'roberto.ferreira@outlook.com', 'hash_roberto789', 'CUIDADOR');
INSERT INTO T_GS_USUARIO (nm_usuario, ds_email, ds_senha, tp_usuario) VALUES ('Luciana Mendes', 'luciana.mendes@defesacivil.sp.gov.br', 'hash_luciana_adm', 'GESTOR');
INSERT INTO T_GS_USUARIO (nm_usuario, ds_email, ds_senha, tp_usuario) VALUES ('Dr. Fernando Souza', 'fernando.souza@enel.com.br', 'hash_enel_gestor', 'GESTOR');

-- 2. Cinco Pacientes (Vinculados aos cuidadores)
INSERT INTO T_GS_PACIENTE (id_usuario, nm_paciente, nr_cpf, ds_endereco, nm_bairro, nm_cidade, sg_estado, nr_cep, nr_telefone)
SELECT id_usuario, 'João Carlos Silva', '123.456.789-01', 'Rua das Flores, 105', 'Jardim Paulista', 'São Paulo', 'SP', '01415-000', '(11) 98888-1111' FROM T_GS_USUARIO WHERE ds_email = 'carlos.silva@gmail.com';

INSERT INTO T_GS_PACIENTE (id_usuario, nm_paciente, nr_cpf, ds_endereco, nm_bairro, nm_cidade, sg_estado, nr_cep, nr_telefone)
SELECT id_usuario, 'Maria Aparecida Rocha', '987.654.321-02', 'Av. Brigadeiro Luís Antônio, 2100', 'Bela Vista', 'São Paulo', 'SP', '01318-000', '(11) 97777-2222' FROM T_GS_USUARIO WHERE ds_email = 'mariana.rocha@hotmail.com';

INSERT INTO T_GS_PACIENTE (id_usuario, nm_paciente, nr_cpf, ds_endereco, nm_bairro, nm_cidade, sg_estado, nr_cep, nr_telefone)
SELECT id_usuario, 'Pedro Henrique Ferreira', '456.789.123-03', 'Rua Augusta, 500', 'Consolação', 'São Paulo', 'SP', '01305-000', '(11) 96666-3333' FROM T_GS_USUARIO WHERE ds_email = 'roberto.ferreira@outlook.com';

INSERT INTO T_GS_PACIENTE (id_usuario, nm_paciente, nr_cpf, ds_endereco, nm_bairro, nm_cidade, sg_estado, nr_cep, nr_telefone)
SELECT id_usuario, 'Ana Beatriz Mendes', '234.567.890-04', 'Rua Domingos de Morais, 1200', 'Vila Mariana', 'São Paulo', 'SP', '04010-100', '(11) 95555-4444' FROM T_GS_USUARIO WHERE ds_email = 'luciana.mendes@defesacivil.sp.gov.br';

INSERT INTO T_GS_PACIENTE (id_usuario, nm_paciente, nr_cpf, ds_endereco, nm_bairro, nm_cidade, sg_estado, nr_cep, nr_telefone)
SELECT id_usuario, 'José Eduardo Souza', '345.678.901-05', 'Av. Rebouças, 3000', 'Pinheiros', 'São Paulo', 'SP', '05402-000', '(11) 94444-5555' FROM T_GS_USUARIO WHERE ds_email = 'fernando.souza@enel.com.br';

-- 3. Cinco Equipamentos
INSERT INTO T_GS_EQUIPAMENTO (id_paciente, tp_aparelho, nm_fabricante, nm_modelo, vl_potencia_watts, qt_autonomia_min, fl_suporte_vida, tp_fonte_reserva)
SELECT id_paciente, 'VENTILADOR_MECANICO', 'Philips', 'Trilogy Evo', 120.00, 900, 'S', 'BATERIA_EXTERNA' FROM T_GS_PACIENTE WHERE nr_cpf = '123.456.789-01';

INSERT INTO T_GS_EQUIPAMENTO (id_paciente, tp_aparelho, nm_fabricante, nm_modelo, vl_potencia_watts, qt_autonomia_min, fl_suporte_vida, tp_fonte_reserva)
SELECT id_paciente, 'CONCENTRADOR_OXIGENIO', 'Philips Respironics', 'EverFlo', 350.00, 0, 'S', 'CILINDRO_OXIGENIO' FROM T_GS_PACIENTE WHERE nr_cpf = '987.654.321-02';

INSERT INTO T_GS_EQUIPAMENTO (id_paciente, tp_aparelho, nm_fabricante, nm_modelo, vl_potencia_watts, qt_autonomia_min, fl_suporte_vida, tp_fonte_reserva)
SELECT id_paciente, 'BOMBA_INFUSAO', 'Fresenius', 'Agilia', 30.00, 240, 'N', 'NENHUMA' FROM T_GS_PACIENTE WHERE nr_cpf = '456.789.123-03';

INSERT INTO T_GS_EQUIPAMENTO (id_paciente, tp_aparelho, nm_fabricante, nm_modelo, vl_potencia_watts, qt_autonomia_min, fl_suporte_vida, tp_fonte_reserva)
SELECT id_paciente, 'ASPIRADOR_SECREÇÃO', 'Nevoni', '1004-SUCTION', 80.00, 60, 'S', 'NENHUMA' FROM T_GS_PACIENTE WHERE nr_cpf = '234.567.890-04';

INSERT INTO T_GS_EQUIPAMENTO (id_paciente, tp_aparelho, nm_fabricante, nm_modelo, vl_potencia_watts, qt_autonomia_min, fl_suporte_vida, tp_fonte_reserva)
SELECT id_paciente, 'VENTILADOR_MECANICO', 'ResMed', 'Astral 150', 100.00, 480, 'S', 'BATERIA_EXTERNA' FROM T_GS_PACIENTE WHERE nr_cpf = '345.678.901-05';

-- 4. Cinco Pontos de Energia
INSERT INTO T_GS_PONTO_ENERGIA (nm_local, tp_estabelecimento, nm_responsavel, nr_telefone, nm_bairro, fl_possui_gerador, st_disponivel) VALUES ('Condomínio Edifício Paulista', 'CONDOMINIO', 'Síndico Roberto', '(11) 3251-0000', 'Bela Vista', 'S', 'S');
INSERT INTO T_GS_PONTO_ENERGIA (nm_local, tp_estabelecimento, nm_responsavel, nr_telefone, nm_bairro, fl_possui_gerador, st_disponivel) VALUES ('Padaria Central Novo Milênio', 'COMERCIO', 'Gerente Carlos', '(11) 3105-1111', 'Jardim Paulista', 'S', 'S');
INSERT INTO T_GS_PONTO_ENERGIA (nm_local, tp_estabelecimento, nm_responsavel, nr_telefone, nm_bairro, fl_possui_gerador, st_disponivel) VALUES ('Drogaria São Paulo 24h', 'COMERCIO', 'Farmacêutico Marcos', '(11) 3887-2222', 'Consolação', 'S', 'S');
INSERT INTO T_GS_PONTO_ENERGIA (nm_local, tp_estabelecimento, nm_responsavel, nr_telefone, nm_bairro, fl_possui_gerador, st_disponivel) VALUES ('Posto de Saúde Vila Mariana', 'PUBLICO', 'Dra. Patricia', '(11) 5571-3333', 'Vila Mariana', 'S', 'S');
INSERT INTO T_GS_PONTO_ENERGIA (nm_local, tp_estabelecimento, nm_responsavel, nr_telefone, nm_bairro, fl_possui_gerador, st_disponivel) VALUES ('Condomínio Plaza Pinheiros', 'CONDOMINIO', 'Zelador Antonio', '(11) 3032-4444', 'Pinheiros', 'N', 'S');

-- 5. Cinco Ocorrências
INSERT INTO T_GS_OCORRENCIA (id_paciente, dh_inicio, st_ocorrencia, qt_bateria_min, vl_score_prioridade, ds_observacao)
SELECT id_paciente, SYSTIMESTAMP - INTERVAL '2' HOUR, 'EM_ABERTO', 0, 1200, 'Apagão por vendaval em Bela Vista. Concentrador sem bateria.' FROM T_GS_PACIENTE WHERE nr_cpf = '987.654.321-02';

INSERT INTO T_GS_OCORRENCIA (id_paciente, dh_inicio, st_ocorrencia, qt_bateria_min, vl_score_prioridade, ds_observacao)
SELECT id_paciente, SYSTIMESTAMP - INTERVAL '1' HOUR, 'EM_ATENDIMENTO', 60, 1000, 'Paciente em uso de aspirador de secreção.' FROM T_GS_PACIENTE WHERE nr_cpf = '234.567.890-04';

INSERT INTO T_GS_OCORRENCIA (id_paciente, dh_inicio, st_ocorrencia, qt_bateria_min, vl_score_prioridade, ds_observacao)
SELECT id_paciente, SYSTIMESTAMP - INTERVAL '30' MINUTE, 'EM_ABERTO', 480, 700, 'Ventilador em bateria interna com 8h restantes.' FROM T_GS_PACIENTE WHERE nr_cpf = '345.678.901-05';

INSERT INTO T_GS_OCORRENCIA (id_paciente, dh_inicio, st_ocorrencia, qt_bateria_min, vl_score_prioridade, ds_observacao)
SELECT id_paciente, SYSTIMESTAMP - INTERVAL '4' HOUR, 'ATENDIDO', 900, 300, 'Alimentação elétrica restabelecida pela Enel.' FROM T_GS_PACIENTE WHERE nr_cpf = '123.456.789-01';

INSERT INTO T_GS_OCORRENCIA (id_paciente, dh_inicio, st_ocorrencia, qt_bateria_min, vl_score_prioridade, ds_observacao)
SELECT id_paciente, SYSTIMESTAMP - INTERVAL '10' MINUTE, 'CANCELADO', 240, 100, 'Queda de disjuntor interno resolvida pelo cuidador.' FROM T_GS_PACIENTE WHERE nr_cpf = '456.789.123-03';

-- 6. Cinco Atendimentos
INSERT INTO T_GS_ATENDIMENTO (id_ocorrencia, id_ponto, tp_socorro, ds_atendimento)
SELECT o.id_ocorrencia, p.id_ponto, 'GERADOR_MOVEL', 'Despachado gerador portátil de 3kW da Defesa Civil.'
FROM T_GS_OCORRENCIA o CROSS JOIN T_GS_PONTO_ENERGIA p WHERE o.ds_observacao LIKE '%Bela Vista%' AND p.nm_local LIKE '%Paulista%';

INSERT INTO T_GS_ATENDIMENTO (id_ocorrencia, id_ponto, tp_socorro, ds_atendimento)
SELECT o.id_ocorrencia, p.id_ponto, 'RECARGA_BATERIA', 'Bateria externa levada para recarga no Posto de Saúde.'
FROM T_GS_OCORRENCIA o CROSS JOIN T_GS_PONTO_ENERGIA p WHERE o.ds_observacao LIKE '%aspirador%' AND p.nm_local LIKE '%Vila Mariana%';

INSERT INTO T_GS_ATENDIMENTO (id_ocorrencia, id_ponto, tp_socorro, ds_atendimento)
SELECT o.id_ocorrencia, NULL, 'RELIGAÇÃO_PRIORITARIA', 'Equipe da distribuidora direcionada para religação do alimentador.'
FROM T_GS_OCORRENCIA o WHERE o.ds_observacao LIKE '%Alimentação elétrica restabelecida%';

INSERT INTO T_GS_ATENDIMENTO (id_ocorrencia, id_ponto, tp_socorro, ds_atendimento)
SELECT o.id_ocorrencia, p.id_ponto, 'REMOÇAO_UTI', 'Paciente removido preventivamente para abrigo com gerador.'
FROM T_GS_OCORRENCIA o CROSS JOIN T_GS_PONTO_ENERGIA p WHERE o.ds_observacao LIKE '%Ventilador em bateria%' AND p.nm_local LIKE '%Pinheiros%';

INSERT INTO T_GS_ATENDIMENTO (id_ocorrencia, id_ponto, tp_socorro, ds_atendimento)
SELECT o.id_ocorrencia, p.id_ponto, 'RECARGA_BATERIA', 'Orientação remota fornecida para troca por cilindro reserva.'
FROM T_GS_OCORRENCIA o CROSS JOIN T_GS_PONTO_ENERGIA p WHERE o.ds_observacao LIKE '%disjuntor%' AND p.nm_local LIKE '%Novo Milênio%';

COMMIT;
