-- TAB-571

alter table SUBMISSIONVALUE modify (SUBMISSION_ID null);
alter table SUBMISSIONVALUE add FEEDBACK_ID nvarchar2(255);
alter table FORMFIELD add CONTEXT nvarchar2(50);