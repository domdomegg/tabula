-- let's make a fake SITS

DROP TABLE CAM_FMC IF EXISTS;

CREATE TABLE CAM_FMC
(
    FMC_CODE VARCHAR(32) PRIMARY KEY NOT NULL,
    FMC_SNAM VARCHAR(15),
    FMC_NAME VARCHAR(120),
    FMC_IUSE VARCHAR(1)
);

DROP TABLE CAM_FME IF EXISTS;

CREATE TABLE CAM_FME
(
    FMC_CODE VARCHAR(32) NOT NULL,
    FME_SEQ VARCHAR(3) NOT NULL,
    FME_MODP VARCHAR(12),
    SCH_CODE VARCHAR(6),
    LEV_CODE VARCHAR(6),
    MTC_CODE VARCHAR(6)
);

INSERT INTO CAM_FMC VALUES ('A100-1-14-CAA', null, null, 'Y');
INSERT INTO CAM_FMC VALUES ('B100-1-14-CAA', null, null, 'Y');
INSERT INTO CAM_FMC VALUES ('A100-A-14-CAA', null, null, 'Y');
INSERT INTO CAM_FMC VALUES ('A100-1-AA-CAA', null, null, 'Y');

INSERT INTO CAM_FME VALUES ('A100-1-14-CAA', '01', '*', null, null, null);
INSERT INTO CAM_FME VALUES ('A100-1-14-CAA', '02', 'CH?00*', null, null, null);
INSERT INTO CAM_FME VALUES ('A100-1-14-CAA', '03', 'CH100-7.5', null, null, null);

DROP TABLE CAM_PMR IF EXISTS;

CREATE TABLE CAM_PMR
(
    PWY_CODE VARCHAR(12) NOT NULL,
    PMR_CODE VARCHAR(6) NOT NULL,
    LEV_CODE VARCHAR(6),
    PMR_DESC VARCHAR(50),
    CONSTRAINT CAM_PMRP1 PRIMARY KEY (PWY_CODE, PMR_CODE)
);

DROP TABLE CAM_PMB IF EXISTS;

CREATE TABLE CAM_PMB
(
    PWY_CODE VARCHAR(12) NOT NULL,
    PMR_CODE VARCHAR(6) NOT NULL,
    PMB_SEQ VARCHAR(3) NOT NULL,
    PMB_MIN NUMERIC(5,2),
    PMB_MAX NUMERIC(5,2),
    FMC_CODE VARCHAR(32),
    PMB_MINM NUMERIC(3),
    PMB_MAXM NUMERIC(3),
    CONSTRAINT CAM_PMBP1 PRIMARY KEY (PWY_CODE, PMR_CODE, PMB_SEQ)
);

INSERT INTO CAM_PMR VALUES ('A100', 'A1001', 1, '14/15 rule');
INSERT INTO CAM_PMR VALUES ('A100', 'A1002', 1, '14/15 rule');
INSERT INTO CAM_PMR VALUES ('A101', 'A1011', 1, '14/15 rule');
INSERT INTO CAM_PMR VALUES ('B100', 'B1001', null, '14/15 rule');
INSERT INTO CAM_PMR VALUES ('C100', 'C1001', 1, 'rule');
INSERT INTO CAM_PMR VALUES ('D100', 'D1001', 1, 'rule 14/15');

INSERT INTO CAM_PMB VALUES ('A100', 'A1001', 1, 7.5, 120, 'A100-1-14-CAA', null, null);
INSERT INTO CAM_PMB VALUES ('A100', 'A1002', 1, null, null, 'A100-1-14-CAA', 1, 10);
INSERT INTO CAM_PMB VALUES ('A101', 'A1011', 1, null, null, 'A100-1-14-CAA', null, null);
INSERT INTO CAM_PMB VALUES ('B100', 'B1001', 1, null, null, 'A100-1-14-CAA', null, null);
INSERT INTO CAM_PMB VALUES ('C100', 'C1001', 1, null, null, 'A100-1-14-CAA', null, null);
INSERT INTO CAM_PMB VALUES ('D100', 'D1001', 1, null, null, 'A100-1-14-CAA', null, null);
INSERT INTO CAM_PMB VALUES ('D100', 'D1001', 2, null, null, 'hello-there', null, null);