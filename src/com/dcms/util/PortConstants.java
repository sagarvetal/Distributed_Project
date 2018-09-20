package com.dcms.util;

import java.util.ArrayList;
import java.util.Collections;

public class PortConstants {
   
	//Front-End Port
	public static final int FE_UDP_PORT = 9000;
	public static final int HEARTBEAT_UDP_PORT = 8000;
	
	//MTL Replica Ports
	public static final int MTL_REPLICA_1_UDP_PORT = 9071;
	public static final int MTL_REPLICA_2_UDP_PORT = 9072;
	public static final int MTL_REPLICA_3_UDP_PORT = 9073;
   
	//LVL Replica Ports
	public static final int LVL_REPLICA_1_UDP_PORT = 9081;
	public static final int LVL_REPLICA_2_UDP_PORT = 9082;
	public static final int LVL_REPLICA_3_UDP_PORT = 9083;
   
	//DDO Replica Ports
	public static final int DDO_REPLICA_1_UDP_PORT = 9091;
	public static final int DDO_REPLICA_2_UDP_PORT = 9092;
	public static final int DDO_REPLICA_3_UDP_PORT = 9093;
   
	public static int getUdpPort(final String serverLocation) {
	   if(LocationConstants.MONTREAL_REPLICA_1.equalsIgnoreCase(serverLocation)) {
		   return MTL_REPLICA_1_UDP_PORT;
	   } else if(LocationConstants.MONTREAL_REPLICA_2.equalsIgnoreCase(serverLocation)) {
		   return MTL_REPLICA_2_UDP_PORT;
	   } else if(LocationConstants.MONTREAL_REPLICA_3.equalsIgnoreCase(serverLocation)) {
		   return MTL_REPLICA_3_UDP_PORT;
	   } else if(LocationConstants.LAVAL_REPLICA_1.equalsIgnoreCase(serverLocation)) {
		   return LVL_REPLICA_1_UDP_PORT;
	   } else if(LocationConstants.LAVAL_REPLICA_2.equalsIgnoreCase(serverLocation)) {
		   return LVL_REPLICA_2_UDP_PORT;
	   } else if(LocationConstants.LAVAL_REPLICA_3.equalsIgnoreCase(serverLocation)) {
		   return LVL_REPLICA_3_UDP_PORT;
	   } else if(LocationConstants.DOLLARD_REPLICA_1.equalsIgnoreCase(serverLocation)) {
		   return DDO_REPLICA_1_UDP_PORT;
	   } else if(LocationConstants.DOLLARD_REPLICA_2.equalsIgnoreCase(serverLocation)) {
		   return DDO_REPLICA_2_UDP_PORT;
	   } else if(LocationConstants.DOLLARD_REPLICA_3.equalsIgnoreCase(serverLocation)) {
		   return DDO_REPLICA_3_UDP_PORT;
	   } else if(LocationConstants.FRONTEND.equalsIgnoreCase(serverLocation)) {
		   return FE_UDP_PORT;
	   } 
	   return 0;
	}
	
	public static ArrayList<Integer> getPortList(final String serverLocation){
		final ArrayList<Integer> portList = new ArrayList<>();
		if(serverLocation.startsWith(LocationConstants.MONTREAL)) {
			portList.add(MTL_REPLICA_1_UDP_PORT);
			portList.add(MTL_REPLICA_2_UDP_PORT);
			portList.add(MTL_REPLICA_3_UDP_PORT);
		} else if(serverLocation.startsWith(LocationConstants.LAVAL)) {
			portList.add(LVL_REPLICA_1_UDP_PORT);
			portList.add(LVL_REPLICA_2_UDP_PORT);
			portList.add(LVL_REPLICA_3_UDP_PORT);
		} else if(serverLocation.startsWith(LocationConstants.DOLLARD)) {
			portList.add(DDO_REPLICA_1_UDP_PORT);
			portList.add(DDO_REPLICA_2_UDP_PORT);
			portList.add(DDO_REPLICA_3_UDP_PORT);
		}
		Collections.sort(portList);
		return portList;
	}
   
}