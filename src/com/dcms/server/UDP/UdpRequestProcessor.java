package com.dcms.server.UDP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import com.dcms.frontend.BullyElection;
import com.dcms.server.CenterServerImpl;
import com.dcms.service.ActivityLoggerService;
import com.dcms.service.HeartBeatDetector;
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
 * @Date 19/07/2018
 * @version 1
 * 
 */

/**
 * This class receives request packet, process it and reply to sender
 */

public class UdpRequestProcessor extends Thread {
	
    private CenterServerImpl server;
    private DatagramPacket packet;
    private ActivityLoggerService activityLogger;
    private List<String> replicaList;
    private static Queue<String> requestQueue = new ConcurrentLinkedQueue<String>();;
    
    //@formatter:off
    public UdpRequestProcessor(final ActivityLoggerService activityLogger, 
    						 final CenterServerImpl server, 
    						 final DatagramPacket packet, 
    						 final List<String> replicaList) throws IOException {
    //@formatter:off
        this.server = server;
        this.packet = packet;
        this.activityLogger = activityLogger;
        this.replicaList = replicaList;
    }

    @Override
    public void run() {
    	String response = "";
		DatagramSocket socket = null;
		final String request = new String(packet.getData()).trim();
		final String[] packetData = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
		final String sourceServer = packetData[0].trim();
		final String action = packetData[1].trim();
		try {
			socket = new DatagramSocket();
			if(!ActionConstants.HEART_BEAT_CHECK.equalsIgnoreCase(action)) {
				activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_REQUEST_RECEIVED, action, sourceServer));
			}
			
			if(!server.isPrimary() || ActionConstants.HEART_BEAT_CHECK.equalsIgnoreCase(action)	|| 
									  ActionConstants.GET_COUNT.equalsIgnoreCase(action) || 
									  ActionConstants.TRANSFER_RECORD.equalsIgnoreCase(action) ||
									  ActionConstants.REMOVE_REPLICA.equalsIgnoreCase(action) || 
									  ActionConstants.INFORM_ELECTION.equalsIgnoreCase(action) ||
									  ActionConstants.PERFORM_ELECTION.equalsIgnoreCase(action) ||
									  ActionConstants.ELECTION.equalsIgnoreCase(action) ||
									  ActionConstants.PRIMARY.equalsIgnoreCase(action)) {
				response = performOperation(request, action, socket);
			} else {
				requestQueue.offer(request);
				response = performOperation(request, action, socket);
				synchronized (requestQueue) {
					String headRequest = requestQueue.peek();
					final StringBuilder tempRequest = new StringBuilder(headRequest);
					final int index = tempRequest.indexOf(FieldConstants.FIELD_SEPARATOR_ARROW);
					headRequest = tempRequest.replace(0, index, server.getServerName()).toString();
					final String[] packet = headRequest.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
					sendRequestToSlaveReplica(headRequest, packet[1].trim());
					requestQueue.poll();
				}
			}

			socket.send(new DatagramPacket(response.getBytes(), response.getBytes().length, packet.getAddress(), packet.getPort()));
			if(!ActionConstants.HEART_BEAT_CHECK.equalsIgnoreCase(action)) {
				activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_RESPONSE_SENT, action, sourceServer));
			}
			
			if(ActionConstants.PERFORM_ELECTION.equalsIgnoreCase(action)) {
				/* Perform Bully Election */
				final BullyElection bullyElection = new BullyElection(activityLogger, server.getServerName(), false);
            	final String bullyResponse = bullyElection.start();
            	if(bullyResponse.equalsIgnoreCase(ErrorMessages.NO_ACTIVE_REPLICA)) {
            		server.setPrimary(true);
            		informFrontEnd(socket);
            		informOtherServers(socket);
            		
            		/* Start HeartBeat Detection of slave replica */
            		final HeartBeatDetector detector = new HeartBeatDetector(server.getServerName(), replicaList, false, true);
    				detector.start();
            	}
			}
			
		} catch (IOException e) {
			activityLogger.log(MessageTypeConstants.ERROR, e.getMessage());
		} finally {
			if (socket != null) {
				socket.close();
			}
		}
    }

    private String performOperation(final String request, final String action, final DatagramSocket socket) {
    	String response = "";
    	switch (action) {
		case ActionConstants.HEART_BEAT_CHECK:
			response = "I am alive";
			break;
		case ActionConstants.GET_COUNT:
			response = server.getRecordCount().toString();
			break;
		case ActionConstants.TRANSFER_RECORD:
			final String managerId = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim())[2].trim();
			final String record = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim())[3].trim();
			response = server.transferRecord(record, managerId);
			break;
		case ActionConstants.ADD_SR:
			response = createSRecord(request);
			break;
		case ActionConstants.ADD_TR:
			response = createTRecord(request);
			break;
		case ActionConstants.EDIT:
			response = editRecord(request);
			break;
		case ActionConstants.GET_RECORD:
			response = getRecord(request);
			break;
		case ActionConstants.GETCOUNT:
			response = getRecordCount(request);
			break;
		case ActionConstants.TRANSFERRECORD:
			response = transferRecord(request);
			break;
		case ActionConstants.REMOVE_REPLICA:
			removeCrachedReplica(request);
			break;
		case ActionConstants.ELECTION:
		case ActionConstants.PERFORM_ELECTION:
			response = SuccessMessages.OK;
			break;
		case ActionConstants.INFORM_ELECTION:
			server.setPrimary(false);
			response = SuccessMessages.OK;
			break;
		case ActionConstants.PRIMARY:
			server.setPrimary(true);
			informFrontEnd(socket);
			response = SuccessMessages.OK;
			break;
		default:
			activityLogger.log(MessageTypeConstants.ERROR, String.format(ErrorMessages.INVALID_ACTION, action));
			response = false + FieldConstants.FIELD_SEPARATOR_ARROW + String.format(ErrorMessages.INVALID_ACTION, action);
			break;
		}
    	
    	return response;
    }

    private void removeCrachedReplica(final String request) {
    	final String[] packetData = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
    	final String replica = packetData[2].trim();
    	replicaList.remove(replica);
	}

	private String createSRecord(final String request) {
		String response = "";
    	final String[] packetData = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
    	final String requestId = packetData[2].trim();
    	if(server.isRequestPerformed(requestId)) {
    		response = server.getResponse(requestId);
    	} else {
    		final String managerId = packetData[3].trim();
    		final String[] recordDetails = packetData[4].trim().split(FieldConstants.FIELD_SEPARATOR_PIPE);
    		response = server.createSRecord(recordDetails[0], recordDetails[1], recordDetails[2], recordDetails[3], recordDetails[4], recordDetails[5], managerId);
    		server.addResponse(requestId, response);
    	}
		return response;
	}

    private String createTRecord(final String request) {
    	String response = "";
    	final String[] packetData = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
    	final String requestId = packetData[2].trim();
    	if(server.isRequestPerformed(requestId)) {
    		response = server.getResponse(requestId);
    	} else {
    		final String managerId = packetData[3].trim();
    		final String[] recordDetails = packetData[4].trim().split(FieldConstants.FIELD_SEPARATOR_PIPE);
    		response = server.createTRecord(recordDetails[0], recordDetails[1], recordDetails[2], recordDetails[3], recordDetails[4], recordDetails[5], recordDetails[6], managerId);
    		server.addResponse(requestId, response);
    	}
		return response;
    }

    private String editRecord(final String request) {
    	String response = "";
    	final String[] packetData = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
    	final String requestId = packetData[2].trim();
    	if(server.isRequestPerformed(requestId)) {
    		response = server.getResponse(requestId);
    	} else {
    		final String managerId = packetData[3].trim();
    		response =  server.editRecord(packetData[4].trim(), packetData[5].trim(), packetData[6].trim(), managerId);
    		server.addResponse(requestId, response);
    	}
		return response;
    }

    private String getRecord(final String request) {
    	String response = "";
    	final String[] packetData = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
    	final String requestId = packetData[2].trim();
    	if(server.isRequestPerformed(requestId)) {
    		response = server.getResponse(requestId);
    	} else {
    		final String managerId = packetData[3].trim();
    		response =  server.displayRecord(packetData[4].trim(), managerId);
    		server.addResponse(requestId, response);
    	}
		return response;
    }

    private String getRecordCount(final String request) {
    	String response = "";
    	final String[] packetData = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
    	final String requestId = packetData[2].trim();
    	if(server.isRequestPerformed(requestId)) {
    		response = server.getResponse(requestId);
    	} else {
    		final String managerId = packetData[3].trim();
    		response =  server.getRecordCounts(managerId);
    		server.addResponse(requestId, response);
    	}
		return response;
    }

    private String transferRecord(final String request) {
    	String response = "";
    	final String[] packetData = request.split(FieldConstants.FIELD_SEPARATOR_ARROW.trim());
    	final String requestId = packetData[2].trim();
    	if(server.isRequestPerformed(requestId)) {
    		response = server.getResponse(requestId);
    	} else {
    		final String managerId = packetData[3].trim();
    		response =  server.transferRecord(managerId, packetData[4].trim(), packetData[5].trim());
    		server.addResponse(requestId, response);
    	}
		return response;
    }

    private void sendRequestToSlaveReplica(final String request, final String action) {
    	final CountDownLatch latch = new CountDownLatch(replicaList.size());
        for (final String replica : replicaList) {
            if (!replica.equalsIgnoreCase(server.getServerName())) {
                new Thread(() -> {
                	sendUdpRequest(request, replica, action);
                	latch.countDown();
                }).start();
            } 
        }
        try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
    
    private void sendUdpRequest(final String request, final String toServer, final String action) {
    	DatagramSocket socket = null;
    	try {
			final InetAddress inetAddress = LocationConstants.getInetAddress(toServer);
			final int udpPort = PortConstants.getUdpPort(toServer);
			activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_REQUEST_SENT, action, toServer));
			
			socket = new DatagramSocket();
			final DatagramPacket packet = new DatagramPacket(request.getBytes(), request.getBytes().length, inetAddress, udpPort);
			socket.send(packet);
			
			try {
				socket.setSoTimeout(2000);
				byte[] data = new byte[1000];
				socket.receive(new DatagramPacket(data, data.length));
				activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_RESPONSE_RECEiVED, action, toServer));
			} catch (SocketTimeoutException | PortUnreachableException e) {
				activityLogger.log(MessageTypeConstants.ERROR, String.format(UdpServerMessages.NO_UDP_RESPONSE, toServer));
			} 
		} catch (Exception e) {
			activityLogger.log(MessageTypeConstants.ERROR, e.getMessage());
		} finally {
			if(socket != null) {
				socket.close();
			}
		}
    }
    
    private void informFrontEnd(final DatagramSocket socket) {
    	final CountDownLatch latch = new CountDownLatch(1);
    	new Thread(() -> {
			final String request = server.getServerName() + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.PRIMARY;
			sendUdpRequest(request, socket, LocationConstants.FRONTEND);
			latch.countDown();
        }).start();
    	try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
    
    private void informOtherServers(final DatagramSocket socket) {
    	final List<String> serverList = new ArrayList<>();
    	serverList.add(LocationConstants.MONTREAL + server.getServerName().substring(3).trim());
    	serverList.add(LocationConstants.LAVAL + server.getServerName().substring(3).trim());
    	serverList.add(LocationConstants.DOLLARD + server.getServerName().substring(3).trim());
    	
    	final CountDownLatch latch = new CountDownLatch(serverList.size() - 1);
    	for(final String replica : serverList) {
    		if(!replica.equalsIgnoreCase(server.getServerName())) {
    			new Thread(() -> {
    				final String request = server.getServerName() + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.PRIMARY;
    				sendUdpRequest(request, socket, replica);
    				latch.countDown();
    			}).start();
    		}
    	}
    	try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
    
    private void sendUdpRequest(final String request, final DatagramSocket socket, final String toServer) {
		try {
			final DatagramPacket packet = new DatagramPacket(request.getBytes(), request.getBytes().length, LocationConstants.getInetAddress(toServer), PortConstants.getUdpPort(toServer));
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		} 
    }
    
}
