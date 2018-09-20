%% README.txt
%% For DCMS (Distributed Class Management System) Final Project

Name:-      Highly Available CORBA Distributed Class Management System (DCMS)

Authors:-   Team Number :- 15
	    Sagar Vetal(40071979)
	    Khyatibahen Chaudhary (40071098)
	    Zankhanaben Ashish Patel(40067635)
            Himanshu Kohli (40070839)
            
		               
Date:- July 29, 2018

(1) INTRODUCTION
-----------------
The Highly available Distributed Class Management System, Which tolerates process crashes only (No software bugs) using unreliable failure detection.


(2) REQUIRED SYSTEMS
--------------------
-Windows 10 or 8 
-Eclipse IDE


(3) HOW TO RUN APPLICATION?
-----------------------------
1. Run all three servers named as MTLServerReplica.java, LVLServerReplica.java, DDOServerReplica.java in following given order,
Each replica file has to be run 3 times in order to start 1 primary replica and 2 slave replica using 3 command line arguments like below,

replicaName<space>portNo<space>isPrimaryFlag

Note: First run slave replica and then run primary replica.

2.To start 2 slave replicas and primary replica for Montreal we have to run MTLServerReplica.java 3 times using below 3 arguments respectively,
MTL_Replica_2 9072 false (for slave 1)
MTL_Replica_3 9073 false (for slave 2)
MTL_Replica_1 9071 true (for primary)

3.To start 2 slave replicas and primary replica for Laval we have to run LVLServerReplica.java 3 times using below 3 arguments respectively,
LVL_Replica_2 9082 false (for slave 1)
LVL_Replica_3 9083 false (for slave 2)
LVL_Replica_1 9081 true (for primary)

4.To start 2 slave replicas and primary replica for Dollard we have to run DDOServerReplica.java 3 times using below 3 arguments respectively,
DDO_Replica_2 9092 false (for slave 1)
DDO_Replica_3 9093 false (for slave 2)
DDO_Replica_1 9091 true (for primary)

5. Start ORBD using command "start orbd -ORBInitialPort 1050" (where 1050 is orbd port number)

6. To start FrontEnd run FrontEndManager.java with arguments "-ORBInitialPort 1050 -ORBInitialHost localhost".

7. To test with Single client run SingleClientTest.java with arguments "-ORBInitialPort 1050 -ORBInitialHost localhost".

8. To test with multiple clients run MultiClientTest.java with arguments "-ORBInitialPort 1050 -ORBInitialHost localhost"

-----------------------------------------

Thank You!