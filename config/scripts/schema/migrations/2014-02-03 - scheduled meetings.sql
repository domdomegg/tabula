--- TAB-1903

alter table meetingrecord add discriminator varchar(10) default 'standard' not null ;
alter table meetingrecord add missed number(1,0) default 0;