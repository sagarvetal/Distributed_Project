package com.dcms.frontend;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import com.dcms.FrontEndApp.FrontEndPOA;
import com.dcms.model.Student;
import com.dcms.model.Teacher;
import com.dcms.service.ActivityLoggerService;
import com.dcms.service.CounterService;
import com.dcms.service.ValidationService;
import com.dcms.util.ActionConstants;
import com.dcms.util.ErrorMessages;
import com.dcms.util.FieldConstants;
import com.dcms.util.FileConstants;
import com.dcms.util.LocationConstants;
import com.dcms.util.MessageTypeConstants;
import com.dcms.util.PortConstants;
import com.dcms.util.RecordTypeConstants;
import com.dcms.util.UdpServerMessages;

/*
 * @author Sagar Vetal
 * @Date 19/07/2018
 * @version 1
 * 
 */

public class FrontEndImpl extends FrontEndPOA {
	
	private String serverName;
    private ActivityLoggerService activityLogger;
    private ConcurrentHashMap<String, String> primaryReplicas;
    private ConcurrentHashMap<String, Integer> primaryReplicaPortNumbers;
    private boolean waitFlag;

    public FrontEndImpl(final String serverName, final ActivityLoggerService activityLogger) throws IOException {
    	this.serverName = serverName;
        this.activityLogger = activityLogger;
        this.primaryReplicas = getPrimaryReplicas();
        this.primaryReplicaPortNumbers = getPrimaryReplicaPortNumbers();
        this.waitFlag = false;
    }
    
    private ConcurrentHashMap<String, Integer> getPrimaryReplicaPortNumbers(){
    	final ConcurrentHashMap<String, Integer> primaryReplicaPortNumbers = new ConcurrentHashMap<>();
    	primaryReplicaPortNumbers.put(LocationConstants.MONTREAL, PortConstants.MTL_REPLICA_1_UDP_PORT);
    	primaryReplicaPortNumbers.put(LocationConstants.LAVAL, PortConstants.LVL_REPLICA_1_UDP_PORT);
    	primaryReplicaPortNumbers.put(LocationConstants.DOLLARD, PortConstants.DDO_REPLICA_1_UDP_PORT);
    	return primaryReplicaPortNumbers;
    }

    private ConcurrentHashMap<String, String> getPrimaryReplicas(){
    	final ConcurrentHashMap<String, String> primaryReplicas = new ConcurrentHashMap<>();
    	primaryReplicas.put(LocationConstants.MONTREAL, LocationConstants.MONTREAL_REPLICA_1);
    	primaryReplicas.put(LocationConstants.LAVAL, LocationConstants.LAVAL_REPLICA_1);
    	primaryReplicas.put(LocationConstants.DOLLARD, LocationConstants.DOLLARD_REPLICA_1);
    	return primaryReplicas;
    }
    
    public synchronized void changePrimaryReplica(final String location, final String replicaName, final int portNo) {
    	if(primaryReplicas.containsKey(location)) {
    		primaryReplicas.put(location, replicaName);
    	}
    	if(primaryReplicaPortNumbers.containsKey(location)) {
    		primaryReplicaPortNumbers.put(location, portNo);
    	}
    }

    public synchronized String getPrimaryReplica(final String location) {
    	return primaryReplicas.get(location);
    }

    public synchronized Integer getPrimaryReplicaPortNo(final String location) {
    	return primaryReplicaPortNumbers.get(location);
    }
    
    public void setWaitFlag(final boolean flag) {
    	waitFlag = flag;
    }
    
	@Override
	public String createTRecord(final String firstName, final String lastName, final String address, final String phone, final String specialization, final String location, final String managerId) {
		String response = "";
    	try {
            response = ValidationService.validateTeacherFields(firstName, lastName, address, phone, specialization, location);
            
            if(response.isEmpty()) {
            	final Optional<String> recordId = getIdCounter(RecordTypeConstants.TEACHER_RECORD); 
            	if(recordId.isPresent()) {
            		final String requestId = "REQ" + String.format("%05d", CounterService.getCounter(FileConstants.REQUEST_COUNTER_FILE_PATH));
            		final Teacher teacher = new Teacher(recordId.get(), firstName, lastName, address, phone, specialization, location);
            		final String request = serverName + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.ADD_TR + 
        												FieldConstants.FIELD_SEPARATOR_ARROW + requestId + 
        												FieldConstants.FIELD_SEPARATOR_ARROW + managerId + 
        												FieldConstants.FIELD_SEPARATOR_ARROW + teacher.getValues();
            		response = sendRequest(request, LocationConstants.getLocation(managerId), ActionConstants.ADD_TR);
            	} else {
            		activityLogger.log(managerId, ActionConstants.ADD_TR, ErrorMessages.RECORD_ID_GENERATION_FAILED);
            		response = false + FieldConstants.FIELD_SEPARATOR_ARROW + ErrorMessages.RECORD_ID_GENERATION_FAILED;
            	}
            } else {
            	activityLogger.log(managerId, ActionConstants.ADD_TR, response);
            	response = false + FieldConstants.FIELD_SEPARATOR_ARROW + response;
            }
        } catch (Exception e) {
            activityLogger.log(managerId, ActionConstants.ADD_TR, ErrorMessages.RECORD_CREATION_FAILED, e.getMessage());
            response = false + FieldConstants.FIELD_SEPARATOR_ARROW + String.format(ErrorMessages.RECORD_CREATION_FAILED, e.getMessage());
        }
        return response;
	}

	@Override
	public String createSRecord(final String firstName, final String lastName, final String coursesRegistered, final String status, final String statusDate, final String managerId) {
		String response = "";
    	try {
            response = ValidationService.validateStudentFields(firstName, lastName, coursesRegistered, status, statusDate);
            
            if(response.isEmpty()) {
        		final Optional<String> recordId = getIdCounter(RecordTypeConstants.STUDENT_RECORD); 
        		if(recordId.isPresent()) {
        			final String requestId = "REQ" + String.format("%05d", CounterService.getCounter(FileConstants.REQUEST_COUNTER_FILE_PATH));
        			final Student student = new Student(recordId.get(), firstName, lastName, getCourses(coursesRegistered), status, statusDate);
        			final String request = serverName + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.ADD_SR + 
														FieldConstants.FIELD_SEPARATOR_ARROW + requestId + 
														FieldConstants.FIELD_SEPARATOR_ARROW + managerId + 
														FieldConstants.FIELD_SEPARATOR_ARROW + student.getValues();
        			response = sendRequest(request, LocationConstants.getLocation(managerId), ActionConstants.ADD_SR);
        		} else {
        			activityLogger.log(managerId, ActionConstants.ADD_SR, ErrorMessages.RECORD_ID_GENERATION_FAILED);
        			response = false + FieldConstants.FIELD_SEPARATOR_ARROW + ErrorMessages.RECORD_ID_GENERATION_FAILED;
        		}
            } else {
            	activityLogger.log(managerId, ActionConstants.ADD_SR, response);
            	response = false + FieldConstants.FIELD_SEPARATOR_ARROW + response;
            }
        } catch (Exception e) {
            activityLogger.log(managerId, ActionConstants.ADD_SR, ErrorMessages.RECORD_CREATION_FAILED, e.getMessage());
            response = false + FieldConstants.FIELD_SEPARATOR_ARROW + String.format(ErrorMessages.RECORD_CREATION_FAILED, e.getMessage());
        }
        return response;
	}
	
	private Optional<String> getIdCounter(final String recordType) throws ClassNotFoundException {
    	Optional<String> recordId = Optional.empty();
    	switch(recordType) {
	    	case RecordTypeConstants.STUDENT_RECORD:
	    		recordId = Optional.of(RecordTypeConstants.STUDENT_RECORD + String.format("%05d", CounterService.getCounter(FileConstants.STUDENT_COUNTER_FILE_PATH)));
	    		break;
	    	case RecordTypeConstants.TEACHER_RECORD:
	    		recordId = Optional.of(RecordTypeConstants.TEACHER_RECORD + String.format("%05d", CounterService.getCounter(FileConstants.TEACHER_COUNTER_FILE_PATH)));
	    		break;
	    	default:
                break;	
    	}
    	return recordId;
    }
	
	private HashSet<String> getCourses(final String coursesRegistered){
    	final HashSet<String> courses = new HashSet<>();
    	final String[] courseList = coursesRegistered.split(",");
    	for(final String course : courseList) {
    		if(!course.isEmpty()) {
    			courses.add(course.trim());
    		}
    	}
    	return courses;
    }

	@Override
	public String editRecord(final String recordId, final String fieldName, final String newValue, final String managerId) {
		String response = "";
    	try {
    		response = ValidationService.validateEditFields(recordId, fieldName, newValue);
    		
    		if(response.isEmpty()) {
    			final String requestId = "REQ" + String.format("%05d", CounterService.getCounter(FileConstants.REQUEST_COUNTER_FILE_PATH));
    			final String request = serverName + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.EDIT + 
    												FieldConstants.FIELD_SEPARATOR_ARROW + requestId + 
													FieldConstants.FIELD_SEPARATOR_ARROW + managerId + 
													FieldConstants.FIELD_SEPARATOR_ARROW + recordId +
													FieldConstants.FIELD_SEPARATOR_ARROW + fieldName +
													FieldConstants.FIELD_SEPARATOR_ARROW + newValue;
    			response = sendRequest(request, LocationConstants.getLocation(managerId), ActionConstants.EDIT);
    		} else {
    			activityLogger.log(managerId, ActionConstants.EDIT, response);
    			response = false + FieldConstants.FIELD_SEPARATOR_ARROW + response;
    		}
        } catch (Exception e) {
            activityLogger.log(managerId, ActionConstants.EDIT, ErrorMessages.RECORD_EDIT_FAILED, e.getMessage());
            response = false + FieldConstants.FIELD_SEPARATOR_ARROW + String.format(ErrorMessages.RECORD_EDIT_FAILED, e.getMessage());
        }
    	return response;
	}

	@Override
	public String getRecordCounts(final String managerId) {
		String response = "";
		try {
			final String requestId = "REQ" + String.format("%05d", CounterService.getCounter(FileConstants.REQUEST_COUNTER_FILE_PATH));
			final String request = serverName + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.GETCOUNT + 
												FieldConstants.FIELD_SEPARATOR_ARROW + requestId + 
												FieldConstants.FIELD_SEPARATOR_ARROW + managerId;
			response = sendRequest(request, LocationConstants.getLocation(managerId), ActionConstants.GETCOUNT);
		} catch (Exception e) {
			activityLogger.log(managerId, ActionConstants.GETCOUNT, ErrorMessages.RECORD_COUNT_FAILED, e.getMessage());
            response = false + FieldConstants.FIELD_SEPARATOR_ARROW + String.format(ErrorMessages.RECORD_COUNT_FAILED, e.getMessage());
		}
		return response;
	}

	@Override
	public String displayRecord(final String recordId, final String managerId) {
		String response = "";
		try {
			final String requestId = "REQ" + String.format("%05d", CounterService.getCounter(FileConstants.REQUEST_COUNTER_FILE_PATH));
			final String request = serverName + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.GET_RECORD + 
												FieldConstants.FIELD_SEPARATOR_ARROW + requestId + 
												FieldConstants.FIELD_SEPARATOR_ARROW + managerId +
												FieldConstants.FIELD_SEPARATOR_ARROW + recordId;
			response = sendRequest(request, LocationConstants.getLocation(managerId), ActionConstants.GET_RECORD);
		} catch (Exception e) {
			activityLogger.log(managerId, ActionConstants.GET_RECORD, ErrorMessages.RECORD_GET_FAILD, e.getMessage());
            response = false + FieldConstants.FIELD_SEPARATOR_ARROW + String.format(ErrorMessages.RECORD_GET_FAILD, e.getMessage());
		}
		return response;
		
	}

	@Override
	public String transferRecord(final String managerId, final String recordId, final String remoteCenterServerName) {
		String response = "";
		try {
			final String requestId = "REQ" + String.format("%05d", CounterService.getCounter(FileConstants.REQUEST_COUNTER_FILE_PATH));
			final String request = serverName + FieldConstants.FIELD_SEPARATOR_ARROW + ActionConstants.TRANSFERRECORD + 
												FieldConstants.FIELD_SEPARATOR_ARROW + requestId + 
												FieldConstants.FIELD_SEPARATOR_ARROW + managerId +
												FieldConstants.FIELD_SEPARATOR_ARROW + recordId +
												FieldConstants.FIELD_SEPARATOR_ARROW + remoteCenterServerName;
			response = sendRequest(request, LocationConstants.getLocation(managerId), ActionConstants.TRANSFERRECORD);
		} catch (Exception e) {
			activityLogger.log(managerId, ActionConstants.TRANSFERRECORD, ErrorMessages.RECORD_TRANSFER_FAILED, e.getMessage());
            response = false + FieldConstants.FIELD_SEPARATOR_ARROW + String.format(ErrorMessages.RECORD_TRANSFER_FAILED, e.getMessage());
		}
		return response;
	}
	
	private String sendRequest(final String request, final String location, final String action) {
		
		while(waitFlag) {
			//wait until leader got elected.
		}
		
		final ArrayList<String> response = new ArrayList<>();
    	final CountDownLatch latch = new CountDownLatch(1);
        
        new Thread(() -> {
        	response.add(sendUdpRequest(request, location, action));
        	latch.countDown();
        }).start();
        
        try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        return response.get(0);
    }
    
    private String sendUdpRequest(final String request, final String location, final String action) {
    	String response = null;
    	DatagramSocket socket = null;
    	try {
    		socket = new DatagramSocket();
    		activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_REQUEST_SENT, action, getPrimaryReplica(location)));

    		final long startTime = System.currentTimeMillis();
    		while(true) {
    			final DatagramPacket packet = new DatagramPacket(request.getBytes(), request.getBytes().length, LocationConstants.getInetAddress(location), getPrimaryReplicaPortNo(location));
    			socket.send(packet);
    			
    			try {
    				socket.setSoTimeout(5000);
    				byte[] data = new byte[1000];
    				socket.receive(new DatagramPacket(data, data.length));
    				response = new String(data);
    				activityLogger.log(MessageTypeConstants.INFO, String.format(UdpServerMessages.UDP_RESPONSE_RECEiVED, action, getPrimaryReplica(location)));
    				break;
    			} catch (SocketTimeoutException | PortUnreachableException e) {
    				final long currentTime = System.currentTimeMillis();
    				/* Added timeout after 10 seconds so that client will not wait forever for response in case of primary replica failure. */
    				final double timeout = (currentTime - startTime) / 1000 ;
    				if(timeout > 10) {
    					activityLogger.log(MessageTypeConstants.ERROR, String.format(UdpServerMessages.NO_UDP_RESPONSE, getPrimaryReplica(location)));
    					response = false + FieldConstants.FIELD_SEPARATOR_ARROW + String.format(UdpServerMessages.NO_UDP_RESPONSE, getPrimaryReplica(location));
    					break;
    				}
    			} 
    		}
		} catch (Exception e) {
			activityLogger.log(MessageTypeConstants.ERROR, e.getMessage());
			response = false + FieldConstants.FIELD_SEPARATOR_ARROW + e.getMessage();
		} finally {
			if(socket != null) {
				socket.close();
			}
		}
    	return response;
    }

}
