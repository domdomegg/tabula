-- let's make a fake SITS

-- Module registrations, confirmed and unconfirmed
DROP TABLE CAM_SMS IF EXISTS;
DROP TABLE CAM_SMO IF EXISTS;
DROP TABLE CAM_SSN IF EXISTS;

CREATE TABLE IF NOT EXISTS CAM_SMS
(
  MOD_CODE VARCHAR(10) NOT NULL
, SMS_AGRP VARCHAR(2)
, SMS_OCCL VARCHAR(6) NOT NULL
, AYR_CODE VARCHAR(6) NOT NULL
, SPR_CODE VARCHAR(12) NOT NULL
, SMS_MCRD INTEGER
, SES_CODE VARCHAR(12)
);

CREATE TABLE IF NOT EXISTS CAM_SMO
(
  MOD_CODE VARCHAR(10) NOT NULL
, SMO_AGRP VARCHAR(2)
, MAV_OCCUR VARCHAR(6) NOT NULL
, AYR_CODE VARCHAR(6) NOT NULL
, SPR_CODE VARCHAR(12) NOT NULL
, SMO_MCRD INTEGER
, SES_CODE VARCHAR(12)
, SMO_RTSC VARCHAR(6)
);

CREATE TABLE IF NOT EXISTS CAM_SSN
(
  SSN_SPRC VARCHAR(12) NOT NULL
, SSN_AYRC VARCHAR(6) NOT NULL
, SSN_MRGS VARCHAR(6) NOT NULL
);

-- Module availability and assessment details
DROP TABLE CAM_MAB IF EXISTS;
DROP TABLE CAM_MAV IF EXISTS;

CREATE TABLE IF NOT EXISTS CAM_MAB
(
  MAP_CODE VARCHAR(10) NOT NULL
, MAB_SEQ VARCHAR(6) NOT NULL
, MAB_NAME VARCHAR(200) NOT NULL
, MAB_AGRP VARCHAR(2) NOT NULL
, AST_CODE VARCHAR(6) NOT NULL
, MAB_UDF1 CHAR(1)
, MKS_CODE VARCHAR(6)
, MAB_APAC VARCHAR(8) NOT NULL
);

CREATE TABLE IF NOT EXISTS CAM_MAV
(
  MOD_CODE VARCHAR(10) NOT NULL
, PSL_CODE CHAR(1)
, AYR_CODE VARCHAR(6) NOT NULL
, MAV_OCCUR VARCHAR(6) NOT NULL
);

DROP TABLE CAM_WSS IF EXISTS;
DROP TABLE CAM_WSM IF EXISTS;

CREATE TABLE IF NOT EXISTS CAM_WSS
(
  WSS_WSPC VARCHAR(12) NOT NULL
, WSS_SPRC VARCHAR(12) NOT NULL
, WSS_AYRC VARCHAR(6) NOT NULL
, WSS_MODC VARCHAR(8) NOT NULL
, WSS_PUBL VARCHAR(1) NOT NULL
, WSS_SEAT VARCHAR(12) NOT NULL
, WSS_MABS VARCHAR(12) NOT NULL
);

CREATE TABLE IF NOT EXISTS CAM_WSM
(
  WSM_WSPC VARCHAR(12) NOT NULL
, WSM_AYRC VARCHAR(6) NOT NULL
, WSM_MODC VARCHAR(8) NOT NULL
, WSM_APAC VARCHAR(8) NOT NULL
);

-- Module details
DROP TABLE INS_MOD IF EXISTS;

CREATE TABLE IF NOT EXISTS INS_MOD
(
  MOD_CODE VARCHAR(10) NOT NULL
, MOD_IUSE CHAR(1)
, MOT_CODE VARCHAR(5)
);

-- Student course join and programme route
DROP TABLE SRS_SCJ IF EXISTS;
DROP TABLE INS_SPR IF EXISTS;

CREATE TABLE IF NOT EXISTS SRS_SCJ
(
  SCJ_CODE VARCHAR(12) NOT NULL
, SCJ_SPRC VARCHAR(12) NOT NULL
, SCJ_CRSC VARCHAR(10)
, SCJ_UDFA CHAR(1)
);

CREATE TABLE IF NOT EXISTS INS_SPR
(
  SPR_CODE VARCHAR(12) NOT NULL
, STS_CODE VARCHAR(6)
, ROU_CODE VARCHAR(12)
);

-- Valid course/route options
DROP TABLE SRS_VCO IF EXISTS;

CREATE TABLE IF NOT EXISTS SRS_VCO
(
  VCO_CRSC VARCHAR(10) NOT NULL
, VCO_ROUC VARCHAR(12) NOT NULL
);

-- Valid course/route options
DROP TABLE CAM_SAS IF EXISTS;

CREATE TABLE IF NOT EXISTS CAM_SAS
(
  SPR_CODE VARCHAR(12) NOT NULL
, AYR_CODE VARCHAR(6) NOT NULL
, MOD_CODE VARCHAR(10) NOT NULL
, MAV_OCCUR VARCHAR(6) NOT NULL
, MAB_SEQ VARCHAR(6) NOT NULL
, SAS_ACTM INTEGER
, SAS_ACTG VARCHAR(2)
, SAS_AGRM INTEGER
, SAS_AGRG VARCHAR(2)
);

-- Thoughts - only the assignment importer test really needs all this data,
-- so perhaps move it into a separate file. Alternatively, just don't invoke
-- sits.sql at all in the regular PersistenceTestBase since we only require
-- an empty but functional datasource there.

INSERT INTO INS_MOD VALUES ('CH115-30', 'Y', 'S'); -- live module, students
INSERT INTO INS_MOD VALUES ('CH120-15', 'Y', 'S'); -- live module, students
INSERT INTO INS_MOD VALUES ('CH130-15', 'Y', 'S'); -- live module, no students
INSERT INTO INS_MOD VALUES ('CH130-20', 'Y', 'S'); -- live module, no students
INSERT INTO INS_MOD VALUES ('XX101-30', 'N', 'S-'); -- inactive module

-- no students registered on CH130, so should show up in list of empty groups
INSERT INTO CAM_MAV VALUES ('CH130-15', 'Y', '11/12', 'A');
INSERT INTO CAM_MAV VALUES ('CH130-20', 'Y', '11/12', 'A');
INSERT INTO CAM_MAB VALUES ('CH130-15', 'A01', 'Chem 130 A01', 'A', 'A', 'Y', null, 'CH1300');
INSERT INTO CAM_WSM VALUES ('EXJUN-12', '11/12', 'CH130-15', 'CH1300');
INSERT INTO CAM_MAB VALUES ('CH130-20', 'A01', 'Chem 130 A01 (20 CATS)', 'A', 'A', 'Y', null, 'CH1300');
INSERT INTO CAM_WSM VALUES ('EXJUN-12', '11/12', 'CH130-20', 'CH1300');

-- some more items that don't have corresponding students,
-- but don't have the right data in other tables to form a complete entry
INSERT INTO CAM_MAB VALUES ('XX100-30', 'A01', 'Mystery Meat', 'A', 'A', 'Y', null, 'XX1000');
INSERT INTO CAM_MAV VALUES ('XX100-30', 'Y', '11/12', 'A');
INSERT INTO CAM_WSM VALUES ('EXJUN-12', '11/12', 'XX100-30', 'XX1000');
INSERT INTO CAM_MAB VALUES ('XX101-30', 'A01', 'Danger Zone', 'A', 'A', 'Y', null, 'XX1010');
INSERT INTO CAM_MAV VALUES ('XX101-30', 'Y', '11/12', 'A');
INSERT INTO CAM_WSM VALUES ('EXJUN-12', '11/12', 'XX101-30', 'XX1010');

INSERT INTO CAM_MAB VALUES ('CH115-30', 'A01', 'Chemicals Essay', 'A', 'A', 'Y', null, 'CH1150');
INSERT INTO CAM_MAV VALUES ('CH115-30', 'Y', '11/12', 'A');
INSERT INTO CAM_WSM VALUES ('EXJUN-12', '11/12', 'CH115-30', 'CH1150');

INSERT INTO CAM_MAB VALUES ('CH120-15', 'A01', 'Chemistry Dissertation', 'A', 'A', 'Y', null, 'CH1200');
INSERT INTO CAM_MAV VALUES ('CH120-15', 'Y', '11/12', 'A');
INSERT INTO CAM_WSM VALUES ('EXJUN-12', '11/12', 'CH120-15', 'CH1200');

-- four students, one permanently withdrawn
INSERT INTO SRS_SCJ VALUES ('0123456/1', '0123456/1', 'UDFA-G500', 'Y');
INSERT INTO INS_SPR VALUES ('0123456/1', 'C', 'G500');

INSERT INTO SRS_SCJ VALUES ('0123457/1', '0123457/1', 'UDFA-G500', 'Y');
INSERT INTO INS_SPR VALUES ('0123457/1', 'C', 'G500');

INSERT INTO SRS_SCJ VALUES ('0123458/1', '0123458/1', 'UDFA-G500', 'Y');
INSERT INTO INS_SPR VALUES ('0123458/1', 'C', 'G500');

INSERT INTO SRS_SCJ VALUES ('0123459/1', '0123459/1', 'UDFA-G500', 'Y');
INSERT INTO INS_SPR VALUES ('0123459/1', 'P', 'G500');

INSERT INTO SRS_SCJ VALUES ('0123460/1', '0123460/1', 'UDFA-G500', 'Y');
INSERT INTO INS_SPR VALUES ('0123460/1', 'C', 'G500');

-- valid course combination
INSERT INTO SRS_VCO VALUES ('UDFA-G500', 'G500');

-- unconfirmed registrations
INSERT INTO CAM_SMS VALUES ('CH115-30', 'A', 'A', '11/12', '0123456/1', 30, 'C');
INSERT INTO CAM_SSN VALUES ('0123456/1', '11/12', 'GEN');
INSERT INTO CAM_WSS VALUES ('EXJUN-12', '0123456/1', '11/12', 'CH115-30', 'Y', '1', 'A01');

INSERT INTO CAM_SMS VALUES ('CH115-30', 'A', 'A', '11/12', '0123457/1', 30, 'C');
INSERT INTO CAM_SSN VALUES ('0123457/1', '11/12', 'GEN');
INSERT INTO CAM_WSS VALUES ('EXJUN-12', '0123457/1', '11/12', 'CH115-30', 'Y', '2', 'A01');

-- confirmed registrations
INSERT INTO CAM_SMO VALUES ('CH115-30', 'A', 'A', '11/12', '0123458/1', 30, 'C', null);
INSERT INTO CAM_WSS VALUES ('EXJUN-12', '0123458/1', '11/12', 'CH115-30', 'Y', '3', 'A01');
INSERT INTO CAM_SMO VALUES ('CH120-15', 'A', 'A', '11/12', '0123458/1', 30, 'C', null);
INSERT INTO CAM_WSS VALUES ('EXJUN-12', '0123458/1', '11/12', 'CH120-15', 'Y', '1', 'A01');
INSERT INTO CAM_SSN VALUES ('0123458/1', '11/12', 'CON');

-- auto-uploaded confirmed registrations
INSERT INTO CAM_SMO VALUES ('CH115-30', null, 'A', '11/12', '0123460/1', 30, 'C', null);
INSERT INTO CAM_WSS VALUES ('EXJUN-12', '0123460/1', '11/12', 'CH115-30', 'Y', '4', 'A01');

-- Some data from other years that the import should ignore
INSERT INTO CAM_SMO VALUES ('CH130-20', 'A', 'A', '10/11', '0123458/1', 30, 'C', null);
INSERT INTO CAM_WSS VALUES ('EXJUN-11', '0123458/1', '10/11', 'CH130-20', 'Y', '1', 'A01');
INSERT INTO CAM_SSN VALUES ('0123458/1', '10/11', 'CON');

-- assessment component marks
INSERT INTO CAM_SAS VALUES ('0123456/1', '11/12', 'CH115-30', 'A', 'A01', 67, '21', 72, '1');
INSERT INTO CAM_SAS VALUES ('0123458/1', '11/12', 'CH120-15', 'A', 'A01', null, null, null, null);