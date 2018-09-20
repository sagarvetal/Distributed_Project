package com.dcms.frontend;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import com.dcms.service.ActivityLoggerService;
import com.dcms.util.ActionConstants;
import com.dcms.util.ErrorMessages;
import com.dcms.util.FieldConstants;
import com.dcms.util.LocationConstants;
import com.dcms.util.MessageTypeConstants;
import com.dcms.util.PortConstants;
import com.dcms.util.SuccessMessages;
import com.dcms.util.UdpServerMessages;

/*
 * @author Sagar Vetal
 * @Date 25/07/2018
 * @version 1
 * 
 */

public class BullyElection {

	private ActivityLoggerService activityLogger;
    private String replicaName;
    private boolean isFrontEnd;
 
    public BullyElection(final ActivityLoggerService activityLogger, final String replicaName, final boolean isFrontEnd) {
    	this.activityLogger = activityLogger;
        this.replicaName = replicaName;
        this.isFrontEnd = isFrontEnd;
    }

    public String start() {
    	String response = "";
        try {
        	final ArrayList<Integer> processList = PortConstants.getPortList(replicaName);
        	
        	final int sourceIndex = processList.indexOf(PortConstants.getUdpPort(replicaName));
        	int destinationIndex = sourceIndex + 1;
        	
        	if(isFrontEnd) {
        		if(destinationIndex >= processList.size()) {
        			destinationIndex = 0;
        		}
        		final String request = LocationConstants.FRONTEND + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.PERFORM_ELECTION;
        		response = sendPerformElectionRequest(destinationIndex, processList, request);
        	} else {
        		final String request = replicaName + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.ELECTION;
        		final ArrayList<String> responseList = sendElectionMulticastRequest(destinationIndex, processList, request);
        		
        		if(responseList.contains(SuccessMessages.OK)) {
        			final String performElectionRequest = replicaName + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.PERFORM_ELECTION;
            		response = sendPerformElectionRequest(destinationIndex, processList, performElectionRequest);
        		} else {
        			response = ErrorMessages.NO_ACTIVE_REPLICA;
        		}
        	}
        	
        } catch (Exception e) {
        	response = ErrorMessages.NO_ACTIVE_REPLICA;
        } 
        return response;
    }
    
    private String sendPerformElectionRequest(int destinationIndex, final ArrayList<Integer> processList, final String request) {
    	String response = ErrorMessages.NO_ACTIVE_REPLICA;
    	
    	while(destinationIndex < processList.size()) {
    		
    		response = sendUdpRequest(request, LocationConstants.getReplicaName(processList.get(destinationIndex)), ActionConstants.PERFORM_ELECTION);
    			
    		if(response.contains(SuccessMessages.OK)) {
    			break;
    		} else {
    			++destinationIndex;
    			response = ErrorMessages.NO_ACTIVE_REPLICA;
    		}
    	}
    	return response;
	}


	private ArrayList<String> sendElectionMulticastRequest(final int destinationIndex, final ArrayList<Integer> processList, final String request){
    	final ArrayList<String> responseList = new ArrayList<>();
    	final CountDownLatch latch = new CountDownLatch(processList.size() - destinationIndex);
    	for(int i = destinationIndex; i < processList.size(); i++) {
    		final int index = i;
    		new Thread(() -> {
    			final String response = sendUdpRequest(request, LocationConstants.getReplicaName(processList.get(index)), ActionConstants.ELECTION);
        		responseList.add(response.trim());
    			latch.countDown();
        	}).start();
    	}
    	try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	return responseList;
    }

    private String sendUdpRequest(final String request, final String toServer, final String action) {
    	String message = "";
    	DatagramSocket socket = null;
    	try {
			final InetAddress inetAddress = LocationConstants.getInetAddress(toServer);
			final int udpPort = PortConstants.getUdpPort(toServer);
			activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_REQUEST_SENT, action, toServer));
			
			socket = new DatagramSocket();
			final DatagramPacket packet = new DatagramPacket(request.getBytes(), request.getBytes().length, inetAddress, udpPort);
			socket.send(packet);
			
			try {
				socket.setSoTimeout(3000);
				byte[] data = new byte[1000];
				socket.receive(new DatagramPacket(data, data.length));
				message = new String(data);
				activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_RESPONSE_RECEiVED, action, toServer));
			} catch (SocketTimeoutException | PortUnreachableException e) {
				message = "No";
				activityLogger.log(MessageTypeConstants.ERROR, String.format(UdpServerMessages.NO_UDP_RESPONSE, toServer));
			} 
		} catch (Exception e) {
			message = "No";
			activityLogger.log(MessageTypeConstants.ERROR, e.getMessage());
		} finally {
			if(socket != null) {
				socket.close();
			}
		}
    	return message;
    }

}
