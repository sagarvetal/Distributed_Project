package com.dcms.server.replica;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.dcms.server.CenterServerImpl;
import com.dcms.server.UDP.UdpRequestProcessor;
import com.dcms.service.ActivityLoggerService;
import com.dcms.service.HeartBeatDetector;
import com.dcms.util.FileConstants;
import com.dcms.util.LocationConstants;
import com.dcms.util.MessageTypeConstants;
import com.dcms.util.UdpServerMessages;

public class DDOServerReplica {

	public static void main(String[] args) {

		try {
			//@formatter:off
			final List<String> replicaList = new ArrayList<>(Arrays.asList(LocationConstants.DOLLARD_REPLICA_1,
														   				   LocationConstants.DOLLARD_REPLICA_2, 
														   				   LocationConstants.DOLLARD_REPLICA_3));
			//@formatter:on
			
			final ActivityLoggerService activityLogger = new ActivityLoggerService(FileConstants.SERVER_LOG_FILE_PATH + LocationConstants.DOLLARD + "/" + args[0].trim()+FileConstants.FILE_TYPE);
			final CenterServerImpl centerServerImpl = new CenterServerImpl(args[0].trim(), Integer.parseInt(args[1].trim()), Boolean.valueOf(args[2].trim()), activityLogger);
			replicaList.remove(centerServerImpl.getServerName());
			
			new Thread(() -> {
				startUdpServer(activityLogger, centerServerImpl, replicaList);
			}).start();
			
			if(centerServerImpl.isPrimary()) {
				/* Start HeartBeat Detection of slave replica */
				final HeartBeatDetector detector = new HeartBeatDetector(centerServerImpl.getServerName(), replicaList, false, true);
				detector.start();
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void startUdpServer(final ActivityLoggerService activityLogger, final CenterServerImpl server, final List<String> replicaList) {
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(server.getPortNo());
			activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_SERVER_STARTED, server.getServerName()));
			while (true) {
				try {
					final byte[] data = new byte[1000];
					final DatagramPacket packet = new DatagramPacket(data, data.length);
					socket.receive(packet);

					new UdpRequestProcessor(activityLogger, server, packet, replicaList).start();
					
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

}