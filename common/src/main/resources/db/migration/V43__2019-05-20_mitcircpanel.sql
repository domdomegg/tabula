create table mitigatingcircumstancespanel(
  id varchar(255) not null,
  name varchar not null,
  department_id varchar(255) not null,
  academicyear smallint not null,
  date timestamp(6),
  endDate timestamp(6),
  location varchar(255),
  lastmodified timestamp(6) not null,
  constraint pk_mitigatingcircumstancespanel primary key (id),
  constraint fk_mitigatingcircumstancespanel_department foreign key (department_id) references department(id)
);

create index idx_mitigatingcircumstancespanel_department on mitigatingcircumstancespanel (department_id);
create index idx_mitigatingcircumstancespanel_department_year on mitigatingcircumstancespanel (department_id, academicyear);

alter table mitigatingcircumstancessubmission add column panel_id varchar;
alter table mitigatingcircumstancessubmission add constraint fk_mitigatingcircumstancessubmission_panel foreign key (panel_id) references mitigatingcircumstancespanel(id);
create index idx_mitigatingcircumstancessubmission_panel on mitigatingcircumstancessubmission (panel_id);
