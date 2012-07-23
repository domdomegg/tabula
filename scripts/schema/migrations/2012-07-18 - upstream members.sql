
-- HFC-243 HFC-246 import members from SITS/ADS
CREATE TABLE UPSTREAMMEMBER (
	UNIVERSITYID NVARCHAR2(100) NOT NULL,
	USERID NVARCHAR2(100) NOT NULL,
	FIRSTNAME NVARCHAR2(255),
	LASTNAME NVARCHAR2(255),
	EMAIL NVARCHAR2(4000),
	CONSTRAINT "UPSTREAMMEMBER_PK" PRIMARY KEY ("UNIVERSITYID")
);