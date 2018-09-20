package com.dcms.service;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.swing.Timer;

import com.dcms.util.ActionConstants;
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

public class HeartBeatDetector implements ActionListener{

	private Timer timer;
    private String fromServer;
    private List<String> toServerList;
    private List<String> replicasToBeRemoved;
    private boolean isFrontEnd;
    private boolean isPrimary;
    private ActivityLoggerService activityLogger;
    private static int maxAttempt = 3;
    private ConcurrentHashMap<String, Integer> maxAttempts;

    //@formatter:off
    public HeartBeatDetector(final String fromServer, 
    						 final List<String> toServerList, 
    						 final boolean isFrontEnd, 
    						 final boolean isPrimary) throws IOException{
    //@formatter:on
        this.timer= new Timer(3000, this);
        this.fromServer = fromServer;
        this.toServerList = toServerList;
        this.isFrontEnd = isFrontEnd;
        this.isPrimary = isPrimary;
        this.maxAttempts = setMaxAttempts();
        this.replicasToBeRemoved = new ArrayList<>();
        this.activityLogger = getLogger(fromServer, isFrontEnd);
    }

    public void start(){
        this.timer.start();
    }
    
    private ActivityLoggerService getLogger(String fromServer, final boolean isFrontEnd) throws IOException {
    	final String filePath = isFrontEnd ? FileConstants.FRONTEND_LOG_FILE_PATH : (FileConstants.SERVER_LOG_FILE_PATH + fromServer.substring(0, 3) + "/");
    	return new ActivityLoggerService(filePath + FileConstants.HEARTBEAT_LOG);
    }
    
    private ConcurrentHashMap<String, Integer> setMaxAttempts() {
    	final ConcurrentHashMap<String, Integer> maxAttempts = new ConcurrentHashMap<>();
    	for(final String toServer : toServerList) {
    		maxAttempts.put(toServer, maxAttempt);
    	}
    	return maxAttempts;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
    	final CountDownLatch latch = new CountDownLatch(toServerList.size());
    	for(final String toServer : toServerList) {
    		new Thread(() -> {
    			checkHeartBeat(toServer);
				latch.countDown();
            }).start();
    	}
    	try {
			latch.await();
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
    	
    	if(replicasToBeRemoved.size() > 0) {
    		toServerList.removeAll(replicasToBeRemoved);
    		replicasToBeRemoved.clear();
    	}
    }

    public void checkHeartBeat(final String toServer) {
    	DatagramSocket socket = null;
    	try {
    		socket = new DatagramSocket();
    		if(maxAttempts.get(toServer) != 0) {
    			//activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_REQUEST_SENT, ActionConstants.HEART_BEAT_CHECK, toServer));
    		}
    		while(maxAttempts.get(toServer) != 0) {
				final String request = fromServer + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.HEART_BEAT_CHECK;
				final DatagramPacket packet = new DatagramPacket(request.getBytes(), request.getBytes().length, LocationConstants.getInetAddress(toServer), PortConstants.getUdpPort(toServer));
				socket.send(packet);
				
				try {
					socket.setSoTimeout(1000);
					byte[] data = new byte[1000];
					socket.receive(new DatagramPacket(data, data.length));
					
					activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_RESPONSE_RECEiVED, ActionConstants.HEART_BEAT_CHECK, toServer), false);
					maxAttempts.put(toServer, maxAttempt);
					break;
				} catch (SocketTimeoutException | PortUnreachableException e) {
					maxAttempts.put(toServer, maxAttempts.get(toServer) - 1);
				} 
			}
    		
    		//Sent message to primary replicas to remove crashed slave replica from replica list
    		if(isPrimary && maxAttempts.get(toServer) == 0) {
    			informPrimaryReplica(socket, toServer);
				replicasToBeRemoved.add(toServer);
    		}

    		//Sent message to frontend to start bully algorithm when primary replica is crashed.
    		if(isFrontEnd && maxAttempts.get(toServer) == 0) {
    			//System.out.println("Election entry point");
    			final String request = "HeartBeatDetector" + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.ELECTION + FieldConstants.FIELD_SEPARATOR_ARROW + toServer;
				sendUdpRequest(request, socket, fromServer);
				replicasToBeRemoved.add(toServer);
    		}
		} catch (Exception e) {
			activityLogger.log(MessageTypeConstants.ERROR, e.getMessage());
		} finally {
			if(socket != null) {
				socket.close();
			}
		}
    }
    
    private void sendUdpRequest(final String request, final DatagramSocket socket, final String replica) {
		try {
			//System.out.println("election to FE");
			final DatagramPacket packet = new DatagramPacket(request.getBytes(), request.getBytes().length, LocationConstants.getInetAddress(replica), PortConstants.getUdpPort(replica));
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    private void informPrimaryReplica(final DatagramSocket socket, final String toServer) {
    	//System.out.println("Remove to replica");
    	final List<String> primaryReplicas = new ArrayList<>();
    	primaryReplicas.add(LocationConstants.MONTREAL + (fromServer.length() > 3 ? fromServer.substring(3) : ""));
    	primaryReplicas.add(LocationConstants.LAVAL + (fromServer.length() > 3 ? fromServer.substring(3) : ""));
    	primaryReplicas.add(LocationConstants.DOLLARD + (fromServer.length() > 3 ? fromServer.substring(3) : ""));
    	
    	final CountDownLatch latch = new CountDownLatch(primaryReplicas.size());
    	for(final String replica : primaryReplicas) {
    		new Thread(() -> {
    			final String replicaToBeRemoved = replica.substring(0, 3) + toServer.substring(3);
    			final String request = "HeartBeatDetector" + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.REMOVE_REPLICA + FieldConstants.FIELD_SEPARATOR_ARROW + replicaToBeRemoved;
				sendUdpRequest(request, socket, replica);
				latch.countDown();
            }).start();
    	}
    	try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
}
