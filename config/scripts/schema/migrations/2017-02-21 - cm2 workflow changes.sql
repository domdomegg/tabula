CREATE TABLE MARKINGWORKFLOW (
  ID NVARCHAR2(255) NOT NULL,
  WORKFLOWTYPE NVARCHAR2(255) NOT NULL,
  NAME NVARCHAR2(255) NOT NULL,
  DEPARTMENT_ID NVARCHAR2(255) NOT NULL,
  IS_REUSABLE NUMBER(1,0) DEFAULT 0 NOT NULL,
  ACADEMICYEAR NUMBER(4,0) NOT NULL,
  CONSTRAINT MARKINGWORKFLOW_PK PRIMARY KEY (ID)
);
CREATE INDEX IDX_MARKINGWORKFLOW_DEPT on MARKINGWORKFLOW(DEPARTMENT_ID);
ALTER TABLE MARKINGWORKFLOW ADD CONSTRAINT WF_DEPT_FK FOREIGN KEY (DEPARTMENT_ID) REFERENCES DEPARTMENT;

CREATE TABLE STAGEMARKERS (
  ID NVARCHAR2(255) NOT NULL,
  STAGE NVARCHAR2(255) NOT NULL,
  MARKERS NVARCHAR2(255) NOT NULL,
  WORKFLOW_ID NVARCHAR2(255) NOT NULL,
  CONSTRAINT STAGEMARKERS_PK PRIMARY KEY (ID)
);
CREATE INDEX IDX_SM_WORKFLOW on STAGEMARKERS(WORKFLOW_ID);
ALTER TABLE STAGEMARKERS ADD CONSTRAINT SM_WORKFLOW_FK FOREIGN KEY (WORKFLOW_ID) REFERENCES MARKINGWORKFLOW;

CREATE TABLE OUTSTANDINGSTAGES (
  FEEDBACK_ID NVARCHAR2(255) NOT NULL,
  STAGE NVARCHAR2(255) NOT NULL
);
CREATE UNIQUE INDEX "OUTSTANDINGSTAGES_PK" ON OUTSTANDINGSTAGES(FEEDBACK_ID, STAGE);
CREATE INDEX IDX_OUTSTANDINGSTAGES_F on OUTSTANDINGSTAGES(FEEDBACK_ID);
ALTER TABLE OUTSTANDINGSTAGES ADD CONSTRAINT OUTSTANDINGSTAGES_F_FK FOREIGN KEY (FEEDBACK_ID) REFERENCES FEEDBACK;

ALTER TABLE MARKERFEEDBACK ADD (
  MARKER NVARCHAR2(255),
  STAGE NVARCHAR2(255)
);
ALTER TABLE MARKERFEEDBACK modify (STATE null);

ALTER TABLE ASSIGNMENT ADD (CM2_WORKFLOW_ID NVARCHAR2(255));
CREATE INDEX IDX_A_CM2WORKFLOW on ASSIGNMENT(CM2_WORKFLOW_ID);
ALTER TABLE ASSIGNMENT ADD CONSTRAINT A_CM2WORKFLOW_FK FOREIGN KEY (CM2_WORKFLOW_ID) REFERENCES MARKINGWORKFLOW;