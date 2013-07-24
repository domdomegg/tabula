
CREATE TABLE SMALLGROUPEVENTOCCURRENCE (
  ID NVARCHAR2(255) NOT NULL,
  WEEK INTEGER NOT NULL,
  MEMBERSGROUP_ID NVARCHAR2(255) NOT NULL,
  EVENT_ID NVARCHAR2(255) NOT NULL,
  CONSTRAINT SMALLGROUPEVENTOCCURRENCE_PK PRIMARY KEY (ID)
);

CREATE UNIQUE INDEX "IDX_SGEOCCURRENCE" ON SMALLGROUPEVENTOCCURRENCE(EVENT_ID, WEEK);

ALTER TABLE SMALLGROUPEVENTOCCURRENCE ADD CONSTRAINT SGEO_EVENT_FK FOREIGN KEY (EVENT_ID) REFERENCES SMALLGROUPEVENT;
ALTER TABLE SMALLGROUPEVENTOCCURRENCE ADD CONSTRAINT SGEO_GROUP_FK FOREIGN KEY (MEMBERSGROUP_ID) REFERENCES USERGROUP;