package com.dcms.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class LocationConstants {
	public static final String FRONTEND = "FrontEnd";
	
    public static final String MONTREAL = "MTL";
    public static final String MONTREAL_REPLICA_1 = "MTL_Replica_1";
    public static final String MONTREAL_REPLICA_2 = "MTL_Replica_2";
    public static final String MONTREAL_REPLICA_3 = "MTL_Replica_3";
    
    public static final String LAVAL = "LVL";
    public static final String LAVAL_REPLICA_1 = "LVL_Replica_1";
    public static final String LAVAL_REPLICA_2 = "LVL_Replica_2";
    public static final String LAVAL_REPLICA_3 = "LVL_Replica_3";
    
    public static final String DOLLARD = "DDO";
    public static final String DOLLARD_REPLICA_1 = "DDO_Replica_1";
    public static final String DOLLARD_REPLICA_2 = "DDO_Replica_2";
    public static final String DOLLARD_REPLICA_3 = "DDO_Replica_3";
    
    public static final String MONTREAL_DESC = "Montreal";
    public static final String LAVAL_DESC = "Laval";
    public static final String DOLLARD_DESC = "Dollard-des-Ormeaux";
    public static final String SERVER = "Server";
    
    public static String getLocation(final String managerId) {
    	if(managerId.startsWith(MONTREAL)) {
    		return MONTREAL;
    	} else if(managerId.startsWith(LAVAL)) {
    		return LAVAL;
    	} else if(managerId.startsWith(DOLLARD)) {
    		return DOLLARD;
    	}
    	return null;
    }
    
    public static String getReplicaName(final int portNo) {
		if (PortConstants.MTL_REPLICA_1_UDP_PORT == portNo) {
			return MONTREAL_REPLICA_1;
		} else if (PortConstants.MTL_REPLICA_2_UDP_PORT == portNo) {
			return MONTREAL_REPLICA_2;
		} else if (PortConstants.MTL_REPLICA_3_UDP_PORT == portNo) {
			return MONTREAL_REPLICA_3;
		} else if (PortConstants.LVL_REPLICA_1_UDP_PORT == portNo) {
			return LAVAL_REPLICA_1;
		} else if (PortConstants.LVL_REPLICA_2_UDP_PORT == portNo) {
			return LAVAL_REPLICA_2;
		} else if (PortConstants.LVL_REPLICA_3_UDP_PORT == portNo) {
			return LAVAL_REPLICA_3;
		} else if (PortConstants.DDO_REPLICA_1_UDP_PORT == portNo) {
			return DOLLARD_REPLICA_1;
		} else if (PortConstants.DDO_REPLICA_2_UDP_PORT == portNo) {
			return DOLLARD_REPLICA_2;
		} else if (PortConstants.DDO_REPLICA_3_UDP_PORT == portNo) {
			return DOLLARD_REPLICA_3;
		} else if (PortConstants.FE_UDP_PORT == portNo) {
			return FRONTEND;
		}
    	return null;
    }
    
    public static InetAddress getInetAddress(final String serverLocation) throws UnknownHostException {
		return InetAddress.getByName("localhost");
    }
}
