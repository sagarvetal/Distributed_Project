package com.dcms.frontend;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import com.dcms.FrontEndApp.FrontEnd;
import com.dcms.FrontEndApp.FrontEndHelper;
//import com.dcms.server.CenterServerImpl;
import com.dcms.service.ActivityLoggerService;
import com.dcms.service.HeartBeatDetector;
import com.dcms.util.ActionConstants;
import com.dcms.util.ErrorMessages;
import com.dcms.util.FieldConstants;
import com.dcms.util.FileConstants;
import com.dcms.util.LocationConstants;
import com.dcms.util.MessageTypeConstants;
import com.dcms.util.PortConstants;
import com.dcms.util.UdpServerMessages;

/*
 * @author Sagar Vetal
 * @Date 19/07/2018
 * @version 1
 * 
 */

public class FrontEndManager {
	
	 public static void main(String[] args) {
	    	
	        try{
	        	//@formatter:off
				final List<String> primaryReplicaList = new ArrayList<>(Arrays.asList(LocationConstants.MONTREAL_REPLICA_1,
															   		  				  LocationConstants.LAVAL_REPLICA_1, 
															   		  				  LocationConstants.DOLLARD_REPLICA_1));
				//@formatter:on
	        	final ActivityLoggerService activityLogger = new ActivityLoggerService(FileConstants.FRONTEND_LOG_FILE_PATH + FileConstants.FRONTEND_LOG);
	            /* Create and Initialize the ORB 
	             * Get reference to rootpoa and activate the POAManager
	             */
	            final ORB orb = ORB.init(args, null);      
	            final POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
	            rootpoa.the_POAManager().activate();
	       
	            /* Create servant and register it with the ORB */
	            final FrontEndImpl frontendImpl = new FrontEndImpl(LocationConstants.FRONTEND, activityLogger);
	       
	            /*Start UDP server for FrontEnd*/
	            new Thread(() -> {
	            	startUdpServer(activityLogger, frontendImpl);
	            }).start();

	            /* Start HeartBeat Detection of primary replica */
            	final HeartBeatDetector detector = new HeartBeatDetector(LocationConstants.FRONTEND, primaryReplicaList, true, false);
            	detector.start();
	            
	            /* Get object reference from the servant */
	            final org.omg.CORBA.Object ref = rootpoa.servant_to_reference(frontendImpl);
	            
	            /* Cast the reference to a CORBA reference */
	            final FrontEnd href = FrontEndHelper.narrow(ref);
	       
	            /* NameService invokes the transient name service  */
	            final org.omg.CORBA.Object objRef =  orb.resolve_initial_references("NameService");
	            final NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
	            
	            /* Bind the Object Reference in Naming */
	            final NameComponent path[] = ncRef.to_name(LocationConstants.FRONTEND);
	            ncRef.rebind(path, href);
	       
	            System.out.println("#========= Front End is ready and waiting =========#");
	            orb.run();
	            
	        } catch (Exception e) {
	            System.err.println("ERROR: " + e);
	            e.printStackTrace(System.out);
	        }
	    }

	private static void startUdpServer(final ActivityLoggerService activityLogger, final FrontEndImpl frontendImpl) {
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(PortConstants.FE_UDP_PORT);
			activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_SERVER_STARTED, LocationConstants.FRONTEND));
			while (true) {
				try {
					final byte[] data = new byte[1000];
					final DatagramPacket packet = new DatagramPacket(data, data.length);
					socket.receive(packet);
					
					new Thread(() -> {
						processRequest(activityLogger, frontendImpl, packet);
		        	}).start();
				} catch (IOException e) {
					activityLogger.log(MessageTypeConstants.ERROR, e.getMessage());
				}
			}
		} catch (SocketException e1) {
			activityLogger.log(MessageTypeConstants.ERROR, e1.getMessage());
		} finally {
            if (socket != null) {
            	socket.close();
            }
        }
	}

	private static void processRequest(final ActivityLoggerService activityLogger, final FrontEndImpl frontend, final DatagramPacket packet) {
        DatagramSocket socket = null;
        final String request = new String(packet.getData()).trim();
        final String[] packetData = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
        final String sourceServer = packetData[0].trim();
        final String action = packetData[1].trim();
        System.out.println("election started");
        try {
            socket = new DatagramSocket();
            activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_REQUEST_RECEIVED, action, sourceServer));
            
            switch (action) {
    		case ActionConstants.ELECTION:
    			System.out.println("election started");
    			final String crashedPrimaryReplica = packetData[2].trim();
            	//Start delay at frontendImpl 
    			frontend.setWaitFlag(true);
            	
            	//Inform other primary replica about election.
            	informPrimaryReplicas(socket, crashedPrimaryReplica);
            	
            	final BullyElection bullyElection = new BullyElection(activityLogger, crashedPrimaryReplica, true);
            	final String response = bullyElection.start();
            	if(response.equalsIgnoreCase(ErrorMessages.NO_ACTIVE_REPLICA)) {
            		activityLogger.log(MessageTypeConstants.ERROR, response);
            	}
            	try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            	frontend.setWaitFlag(false);
            	
    			break;
    		case ActionConstants.PRIMARY:
    			frontend.changePrimaryReplica(sourceServer.substring(0, 3).trim(), sourceServer, PortConstants.getUdpPort(sourceServer));
    			activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.LEADER_REPLICA, sourceServer, sourceServer.substring(0, 3).trim()));
    			break;
    		default:
    			activityLogger.log(MessageTypeConstants.ERROR, String.format(ErrorMessages.INVALID_ACTION, action));
    			break;
    		}
            
            //activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_RESPONSE_SENT, action, sourceServer));
        } catch (IOException e) {
        	activityLogger.log(MessageTypeConstants.ERROR, e.getMessage());
        } finally {
            if (socket != null) {
            	socket.close();
            }
        }
    }
	
	private static void informPrimaryReplicas(final DatagramSocket socket, final String crashedPrimaryReplica) {
		final List<String> locationList = new ArrayList<>();
    	locationList.add(LocationConstants.MONTREAL + crashedPrimaryReplica.substring(3).trim());
    	locationList.add(LocationConstants.LAVAL + crashedPrimaryReplica.substring(3).trim());
    	locationList.add(LocationConstants.DOLLARD + crashedPrimaryReplica.substring(3).trim());
    	
    	for(final String replica : locationList) {
    		if(!replica.equalsIgnoreCase(crashedPrimaryReplica)) {
    			new Thread(() -> {
    				final String electionRequest = LocationConstants.FRONTEND + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.INFORM_ELECTION;
    				sendUdpRequest(electionRequest, socket, replica);
    			}).start();
    		}
    	}
	}
	
	private static void sendUdpRequest(final String request, final DatagramSocket socket, final String replica) {
		try {
			final DatagramPacket packet = new DatagramPacket(request.getBytes(), request.getBytes().length, LocationConstants.getInetAddress(replica), PortConstants.getUdpPort(replica));
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

}
