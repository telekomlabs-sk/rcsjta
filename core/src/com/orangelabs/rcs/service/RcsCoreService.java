/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.orangelabs.rcs.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;

import com.gsma.services.rcs.Intents;
import com.gsma.services.rcs.JoynService;
import com.gsma.services.rcs.capability.ICapabilityService;
import com.gsma.services.rcs.chat.IChatService;
import com.gsma.services.rcs.contacts.IContactsService;
import com.gsma.services.rcs.ft.IFileTransferService;
import com.gsma.services.rcs.gsh.IGeolocSharingService;
import com.gsma.services.rcs.ipcall.IIPCallService;
import com.gsma.services.rcs.ish.IImageSharingService;
import com.gsma.services.rcs.session.IMultimediaSessionService;
import com.gsma.services.rcs.vsh.IVideoSharingService;
import com.orangelabs.rcs.R;
import com.orangelabs.rcs.addressbook.AccountChangedReceiver;
import com.orangelabs.rcs.core.Core;
import com.orangelabs.rcs.core.CoreListener;
import com.orangelabs.rcs.core.TerminalInfo;
import com.orangelabs.rcs.core.ims.ImsError;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.service.capability.Capabilities;
import com.orangelabs.rcs.core.ims.service.im.chat.OneOneChatSession;
import com.orangelabs.rcs.core.ims.service.im.chat.TerminatingAdhocGroupChatSession;
import com.orangelabs.rcs.core.ims.service.im.chat.TerminatingOne2OneChatSession;
import com.orangelabs.rcs.core.ims.service.im.chat.standfw.TerminatingStoreAndForwardMsgSession;
import com.orangelabs.rcs.core.ims.service.im.filetransfer.FileSharingSession;
import com.orangelabs.rcs.core.ims.service.ipcall.IPCallSession;
import com.orangelabs.rcs.core.ims.service.presence.pidf.PidfDocument;
import com.orangelabs.rcs.core.ims.service.richcall.geoloc.GeolocTransferSession;
import com.orangelabs.rcs.core.ims.service.richcall.image.ImageTransferSession;
import com.orangelabs.rcs.core.ims.service.richcall.video.VideoStreamingSession;
import com.orangelabs.rcs.core.ims.service.sip.GenericSipSession;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.platform.file.FileFactory;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.fthttp.FtHttpResumeDaoImpl;
import com.orangelabs.rcs.provider.ipcall.IPCallHistory;
import com.orangelabs.rcs.provider.messaging.RichMessagingHistory;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.sharing.RichCallHistory;
import com.orangelabs.rcs.service.api.CapabilityServiceImpl;
import com.orangelabs.rcs.service.api.ChatServiceImpl;
import com.orangelabs.rcs.service.api.ContactsServiceImpl;
import com.orangelabs.rcs.service.api.FileTransferServiceImpl;
import com.orangelabs.rcs.service.api.GeolocSharingServiceImpl;
import com.orangelabs.rcs.service.api.IPCallServiceImpl;
import com.orangelabs.rcs.service.api.ImageSharingServiceImpl;
import com.orangelabs.rcs.service.api.MultimediaSessionServiceImpl;
import com.orangelabs.rcs.service.api.VideoSharingServiceImpl;
import com.orangelabs.rcs.utils.AppUtils;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * RCS core service. This service offers a flat API to any other process (activities)
 * to access to RCS features. This service is started automatically at device boot.
 * 
 * @author Jean-Marc AUFFRET
 */
public class RcsCoreService extends Service implements CoreListener {
	/**
	 * Notification ID
	 */
	private final static int SERVICE_NOTIFICATION = 1000;
	
	/**
	 * CPU manager
	 */
	private CpuManager cpuManager = new CpuManager();

    /**
     * Account changed broadcast receiver
     */
    private AccountChangedReceiver accountChangedReceiver = null;

	// --------------------- RCSJTA API -------------------------
	
	/**
	 * Contacts API
	 */
    private ContactsServiceImpl contactsApi = null; 

    /**
	 * Capability API
	 */
    private CapabilityServiceImpl capabilityApi = null; 

	/**
	 * Chat API
	 */
    private ChatServiceImpl chatApi = null; 

	/**
	 * File transfer API
	 */
    private FileTransferServiceImpl ftApi = null; 

    /**
	 * Video sharing API
	 */
    private VideoSharingServiceImpl vshApi = null; 

    /**
	 * Image sharing API
	 */
    private ImageSharingServiceImpl ishApi = null; 

    /**
	 * Geoloc sharing API
	 */
    private GeolocSharingServiceImpl gshApi = null; 

    /**
	 * IP call API
	 */
    private IPCallServiceImpl ipcallApi = null; 

    /**
	 * Multimedia session API
	 */
	private MultimediaSessionServiceImpl sessionApi = null; 
		
	/**
	 * The logger
	 */
	private Logger logger = Logger.getLogger(this.getClass().getName());

	@Override
    public void onCreate() {
		// Set application context
		AndroidFactory.setApplicationContext(getApplicationContext());

		// Set the terminal version
		TerminalInfo.setProductVersion(AppUtils.getApplicationVersion(this));

    	// Start the core
    	startCore();
    }

    @Override
    public void onDestroy() {
        // Unregister account changed broadcast receiver
	    if (accountChangedReceiver != null) {
	        try {
	        	unregisterReceiver(accountChangedReceiver);
	        } catch (IllegalArgumentException e) {
	        	// Nothing to do
	        }
	    }

        // Stop the core
        Thread t = new Thread() {
            /**
             * Processing
             */
            public void run() {
                stopCore();
            }
        };
        t.start();
    }

    /**
     * Start core
     */
    public synchronized void startCore() {
		if (Core.getInstance() != null) {
			// Already started
			return;
		}

        try {
    		if (logger.isActivated()) {
    			logger.debug("Start RCS core service");
    		}
    		
			// Instantiate the settings manager
            RcsSettings.createInstance(getApplicationContext());
            
        	// Instanciate API
            contactsApi = new ContactsServiceImpl(); 
            capabilityApi = new CapabilityServiceImpl(); 
            chatApi = new ChatServiceImpl(); 
            ftApi = new FileTransferServiceImpl(); 
            vshApi = new VideoSharingServiceImpl(); 
            ishApi = new ImageSharingServiceImpl(); 
            gshApi = new GeolocSharingServiceImpl(); 
            ipcallApi = new IPCallServiceImpl(); 
        	sessionApi = new MultimediaSessionServiceImpl();             
            
            // Set the logger properties
    		Logger.activationFlag = RcsSettings.getInstance().isTraceActivated();
    		Logger.traceLevel = RcsSettings.getInstance().getTraceLevel();

    		// Terminal version
            if (logger.isActivated()) {
                logger.info("RCS stack release is " + TerminalInfo.getProductVersion());
            }
    		
    		// Instantiate the contacts manager
            ContactsManager.createInstance(getApplicationContext());

            // Instantiate the rich messaging history 
            RichMessagingHistory.createInstance(getApplicationContext());
            
            // Instantiate the rich call history 
            RichCallHistory.createInstance(getApplicationContext());

            // Instantiate the IP call history 
            IPCallHistory.createInstance(getApplicationContext());

            // Instantiate the FT HTTP DAO interface
            FtHttpResumeDaoImpl.createInstance(getApplicationContext());
            
            // Create the core
			Core.createCore(this);

			// Start the core
			Core.getInstance().startCore();		

			// Create multimedia directory on sdcard
			FileFactory.createDirectory(RcsSettings.getInstance().getPhotoRootDirectory());
			FileFactory.createDirectory(RcsSettings.getInstance().getVideoRootDirectory());
			FileFactory.createDirectory(RcsSettings.getInstance().getFileRootDirectory());
			
			// Init CPU manager
			cpuManager.init();

            // Register account changed event receiver
            if (accountChangedReceiver == null) {
                accountChangedReceiver = new AccountChangedReceiver();

                // Register account changed broadcast receiver after a timeout of 2s (This is not done immediately, as we do not want to catch
                // the removal of the account (creating and removing accounts is done asynchronously). We can reasonably assume that no
                // RCS account deletion will be done by user during this amount of time, as he just started his service.
                Handler handler = new Handler();
                handler.postDelayed(
                        new Runnable() {
                            public void run() {
                                registerReceiver(accountChangedReceiver, new IntentFilter(
                                        "android.accounts.LOGIN_ACCOUNTS_CHANGED"));
                            }},
                        2000);
            }

	        // Show a first notification
	    	addRcsServiceNotification(false, getString(R.string.rcs_core_loaded));

			if (logger.isActivated()) {
				logger.info("RCS core service started with success");
			}
		} catch(Exception e) {
			// Unexpected error
			if (logger.isActivated()) {
				logger.error("Can't instanciate the RCS core service", e);
			}
			
			// Show error in notification bar
	    	addRcsServiceNotification(false, getString(R.string.rcs_core_failed));
	    	
			// Exit service
	    	stopSelf();
		}
    }
    
    /**
     * Stop core
     */
    public synchronized void stopCore() {
		if (Core.getInstance() == null) {
			// Already stopped
			return;
		}
		
		if (logger.isActivated()) {
			logger.debug("Stop RCS core service");
		}

    	// Close APIs
	    contactsApi.close();
		capabilityApi.close();
		ftApi.close();
		chatApi.close();
		ishApi.close();
		gshApi.close();
		ipcallApi.close();
    	vshApi.close();

    	// Terminate the core in background
		Core.terminateCore();

		// Close CPU manager
		cpuManager.close();

		if (logger.isActivated()) {
			logger.info("RCS core service stopped with success");
		}
    }

    @Override
    public IBinder onBind(Intent intent) {    	
        if (IContactsService.class.getName().equals(intent.getAction())) {
    		if (logger.isActivated()) {
    			logger.debug("Contacts service API binding");
    		}
            return contactsApi;
        } else
        if (ICapabilityService.class.getName().equals(intent.getAction())) {
    		if (logger.isActivated()) {
    			logger.debug("Capability service API binding");
    		}
            return capabilityApi;
        } else
        if (IFileTransferService.class.getName().equals(intent.getAction())) {
    		if (logger.isActivated()) {
    			logger.debug("File transfer service API binding");
    		}
            return ftApi;
        } else
        if (IChatService.class.getName().equals(intent.getAction())) {
    		if (logger.isActivated()) {
    			logger.debug("Chat service API binding");
    		}
            return chatApi;
        } else
        if (IVideoSharingService.class.getName().equals(intent.getAction())) {
    		if (logger.isActivated()) {
    			logger.debug("Video sharing service API binding");
    		}
            return vshApi;
        } else
        if (IImageSharingService.class.getName().equals(intent.getAction())) {
    		if (logger.isActivated()) {
    			logger.debug("Image sharing service API binding");
    		}
            return ishApi;
        } else
        if (IGeolocSharingService.class.getName().equals(intent.getAction())) {
    		if (logger.isActivated()) {
    			logger.debug("Geoloc sharing service API binding");
    		}
            return gshApi;
        } else
        if (IIPCallService.class.getName().equals(intent.getAction())) {
    		if (logger.isActivated()) {
    			logger.debug("IP call service API binding");
    		}
            return ipcallApi;
        } else
        if (IMultimediaSessionService.class.getName().equals(intent.getAction())) {
    		if (logger.isActivated()) {
    			logger.debug("SIP API binding");
    		}
            return sessionApi;
        } else {
        	return null;
        }
    }
    
    /**
     * Add RCS service notification
     * 
     * @param state Service state (ON|OFF)
     * @param label Label
     */
    public static void addRcsServiceNotification(boolean state, String label) {
    	// Create notification
    	Intent intent = new Intent(Intents.Client.ACTION_VIEW_SETTINGS);
    	intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent contentIntent = PendingIntent.getActivity(AndroidFactory.getApplicationContext(), 0, intent, 0);
		int iconId; 
		if (state) {
			iconId  = R.drawable.rcs_core_notif_on_icon;
		} else {
			iconId  = R.drawable.rcs_core_notif_off_icon; 
		}
        Notification notif = new Notification(iconId, "", System.currentTimeMillis());
        notif.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_FOREGROUND_SERVICE;
        notif.setLatestEventInfo(AndroidFactory.getApplicationContext(),
        		AndroidFactory.getApplicationContext().getString(R.string.rcs_core_rcs_notification_title),
        		label, contentIntent);
        
        // Send notification
		NotificationManager notificationManager = (NotificationManager)AndroidFactory.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(SERVICE_NOTIFICATION, notif);
    }
    
    /*---------------------------- CORE EVENTS ---------------------------*/

    /**
	 * Notify registration status to API
	 * 
	 * @param status Status
	 */
	private void notifyRegistrationStatusToApi(boolean status) {
		if (capabilityApi != null) {
			capabilityApi.notifyRegistrationEvent(status);
		}
		if (chatApi != null) {
			chatApi.notifyRegistrationEvent(status);
		}
		if (ftApi != null) {
			ftApi.notifyRegistrationEvent(status);
		}
		if (vshApi != null) {
			vshApi.notifyRegistrationEvent(status);
		}
		if (ishApi != null) {
			ishApi.notifyRegistrationEvent(status);
		}
		if (gshApi != null) {
			gshApi.notifyRegistrationEvent(status);
		}
		if (ipcallApi != null) {
			ipcallApi.notifyRegistrationEvent(status);
		}
		if (sessionApi != null) {
			sessionApi.notifyRegistrationEvent(status);
		}
	}    
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleCoreLayerStarted()
     */
    public void handleCoreLayerStarted() {
		if (logger.isActivated()) {
			logger.debug("Handle event core started");
		}

		// Display a notification
		addRcsServiceNotification(false, getString(R.string.rcs_core_started));
		
		// Send service up intent
		Intent intent = new Intent(JoynService.ACTION_RCS_SERVICE_UP);
		getApplicationContext().sendBroadcast(intent);
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleCoreLayerStopped()
     */
    public void handleCoreLayerStopped() {
        // Display a notification
        if (logger.isActivated()) {
            logger.debug("Handle event core terminated");
        }
        addRcsServiceNotification(false, getString(R.string.rcs_core_stopped));
    }
    
	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleRegistrationSuccessful()
	 */
	public void handleRegistrationSuccessful() {
		if (logger.isActivated()) {
			logger.debug("Handle event registration ok");
		}
		
		// Display a notification
		addRcsServiceNotification(true, getString(R.string.rcs_core_ims_connected));
		
		// Notify APIs
		notifyRegistrationStatusToApi(true);
	}

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleRegistrationFailed(com.orangelabs.rcs.core.ims.ImsError)
	 */
	public void handleRegistrationFailed(ImsError error) {
		if (logger.isActivated()) {
			logger.debug("Handle event registration failed");
		}

		// Display a notification
		addRcsServiceNotification(false, getString(R.string.rcs_core_ims_connection_failed));

		// Notify APIs
		notifyRegistrationStatusToApi(false);
	}

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleRegistrationTerminated()
	 */
	public void handleRegistrationTerminated() {
        if (logger.isActivated()) {
            logger.debug("Handle event registration terminated");
        }

        if (Core.getInstance().getImsModule().getImsConnectionManager().isDisconnectedByBattery()) {
            // Display a notification
            addRcsServiceNotification(false, getString(R.string.rcs_core_ims_battery_disconnected));
        } else {
            // Display a notification
        	addRcsServiceNotification(false, getString(R.string.rcs_core_ims_disconnected));
        }
        
		// Notify APIs
		notifyRegistrationStatusToApi(false);
	}

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handlePresenceSharingNotification(java.lang.String, java.lang.String, java.lang.String)
     */
    public void handlePresenceSharingNotification(String contact, String status, String reason) {
		if (logger.isActivated()) {
			logger.debug("Handle event presence sharing notification for " + contact + " (" + status + ":" + reason + ")");
		}
		// Not used
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handlePresenceInfoNotification(java.lang.String, com.orangelabs.rcs.core.ims.service.presence.pidf.PidfDocument)
     */
    public void handlePresenceInfoNotification(String contact, PidfDocument presence) {
    	if (logger.isActivated()) {
			logger.debug("Handle event presence info notification for " + contact);
		}
		// Not used
	}
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleCapabilitiesNotification(java.lang.String, com.orangelabs.rcs.core.ims.service.capability.Capabilities)
     */
    public void handleCapabilitiesNotification(String contact, Capabilities capabilities) {
    	if (logger.isActivated()) {
			logger.debug("Handle capabilities update notification for " + contact + " (" + capabilities.toString() + ")");
		}

		// Extract number from contact 
		String number = PhoneUtils.extractNumberFromUri(contact);

		// Notify API
		capabilityApi.receiveCapabilities(number, capabilities);
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handlePresenceSharingInvitation(java.lang.String)
     */
    public void handlePresenceSharingInvitation(String contact) {
		if (logger.isActivated()) {
			logger.debug("Handle event presence sharing invitation");
		}
		// Not used
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleContentSharingTransferInvitation(com.orangelabs.rcs.core.ims.service.richcall.image.ImageTransferSession)
     */
    public void handleContentSharingTransferInvitation(ImageTransferSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event content sharing transfer invitation");
		}

		// Broadcast the invitation
		ishApi.receiveImageSharingInvitation(session);
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleContentSharingTransferInvitation(com.orangelabs.rcs.core.ims.service.richcall.geoloc.GeolocTransferSession)
     */
    public void handleContentSharingTransferInvitation(GeolocTransferSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event content sharing transfer invitation");
		}

		// Broadcast the invitation
		gshApi.receiveGeolocSharingInvitation(session);
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleContentSharingStreamingInvitation(com.orangelabs.rcs.core.ims.service.richcall.video.VideoStreamingSession)
     */
    public void handleContentSharingStreamingInvitation(VideoStreamingSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event content sharing streaming invitation");
		}

		// Broadcast the invitation
		vshApi.receiveVideoSharingInvitation(session);
    }
	
	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleFileTransferInvitation(com.orangelabs.rcs.core.ims.service.im.filetransfer.FileSharingSession, boolean)
	 */
	public void handleFileTransferInvitation(FileSharingSession fileSharingSession, boolean isGroup) {
		if (logger.isActivated()) {
			logger.debug("Handle event file transfer invitation");
		}

    	// Broadcast the invitation
		ftApi.receiveFileTransferInvitation(fileSharingSession, isGroup);
	}
    
	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handle1to1FileTransferInvitation(com.orangelabs.rcs.core.ims.service.im.filetransfer.FileSharingSession, com.orangelabs.rcs.core.ims.service.im.chat.OneOneChatSession)
	 */
	public void handle1to1FileTransferInvitation(FileSharingSession fileSharingSession, OneOneChatSession one2oneChatSession) {
		if (logger.isActivated()) {
			logger.debug("Handle event file transfer invitation");
		}
		
    	// Broadcast the invitation
    	ftApi.receiveFileTransferInvitation(fileSharingSession, one2oneChatSession);
	}
	
	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleGroupFileTransferInvitation(com.orangelabs.rcs.core.ims.service.im.filetransfer.FileSharingSession, com.orangelabs.rcs.core.ims.service.im.chat.TerminatingAdhocGroupChatSession)
	 */
	public void handleGroupFileTransferInvitation(FileSharingSession session, TerminatingAdhocGroupChatSession groupChatSession) {
		if (logger.isActivated()) {
			logger.debug("Handle event group file transfer invitation");
		}
		
    	// Broadcast the invitation
    	ftApi.receiveFileTransferInvitation(session, groupChatSession);
	}

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleIncomingFileTransferResuming(com.orangelabs.rcs.core.ims.service.im.filetransfer.FileSharingSession, boolean, java.lang.String, java.lang.String)
     */
    public void handleIncomingFileTransferResuming(FileSharingSession session, boolean isGroup, String chatSessionId, String chatId) {
        if (logger.isActivated()) {
            logger.debug("Handle event incoming file transfer resuming");
        }

        // Broadcast the invitation
        ftApi.resumeIncomingFileTransfer(session, isGroup, chatSessionId, chatId);
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleOutgoingFileTransferResuming(com.orangelabs.rcs.core.ims.service.im.filetransfer.FileSharingSession, boolean)
     */
    public void handleOutgoingFileTransferResuming(FileSharingSession session, boolean isGroup) {
        if (logger.isActivated()) {
            logger.debug("Handle event outgoing file transfer resuming");
        }

        // Broadcast the invitation
        ftApi.resumeOutgoingFileTransfer(session, isGroup);
    }

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleOneOneChatSessionInvitation(com.orangelabs.rcs.core.ims.service.im.chat.TerminatingOne2OneChatSession)
	 */
	public void handleOneOneChatSessionInvitation(TerminatingOne2OneChatSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event receive 1-1 chat session invitation");
		}
		
    	// Broadcast the invitation
		chatApi.receiveOneOneChatInvitation(session);
    }

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleAdhocGroupChatSessionInvitation(com.orangelabs.rcs.core.ims.service.im.chat.TerminatingAdhocGroupChatSession)
	 */
	public void handleAdhocGroupChatSessionInvitation(TerminatingAdhocGroupChatSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event receive ad-hoc group chat session invitation");
		}

    	// Broadcast the invitation
		chatApi.receiveGroupChatInvitation(session);
	}
	
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleStoreAndForwardMsgSessionInvitation(com.orangelabs.rcs.core.ims.service.im.chat.standfw.TerminatingStoreAndForwardMsgSession)
     */
    public void handleStoreAndForwardMsgSessionInvitation(TerminatingStoreAndForwardMsgSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event S&F messages session invitation");
		}
		
    	// Broadcast the invitation
		chatApi.receiveOneOneChatInvitation(session);
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleMessageDeliveryStatus(java.lang.String, java.lang.String, java.lang.String)
     */
    public void handleMessageDeliveryStatus(String contact, String msgId, String status) {
		if (logger.isActivated()) {
			logger.debug("Handle message delivery status");
		}
    	
		// Notify listeners
		chatApi.receiveMessageDeliveryStatus(contact, msgId, status);
    }
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleFileDeliveryStatus(java.lang.String, java.lang.String, java.lang.String)
     */
    public void handleFileDeliveryStatus(String ftSessionId, String status, String contact) {
    	 if (logger.isActivated()) {
             logger.debug("Handle file delivery status: session=" + ftSessionId + " status=" + status + " contact="+contact);
         }

        // Notify listeners
        ftApi.handleFileDeliveryStatus(ftSessionId, status, contact);
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleSipSessionInvitation(android.content.Intent, com.orangelabs.rcs.core.ims.service.sip.GenericSipSession)
     */
    public void handleSipSessionInvitation(Intent intent, GenericSipSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event receive SIP session invitation");
		}
		
		// Broadcast the invitation
		sessionApi.receiveSipSessionInvitation(intent, session);
    }    
    
    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleSipInstantMessageReceived(android.content.Intent, com.orangelabs.rcs.core.ims.protocol.sip.SipRequest)
     */
    public void handleSipInstantMessageReceived(Intent intent, SipRequest message) {  
    	if (logger.isActivated()) {
			logger.debug("Handle event receive SIP instant message");
		}
		
		// Broadcast the message
		sessionApi.receiveSipInstantMessage(intent, message);
    }    

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleUserConfirmationRequest(java.lang.String, java.lang.String, java.lang.String, boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int)
     */
    public void handleUserConfirmationRequest(String remote, String id,
    		String type, boolean pin, String subject, String text,
    		String acceptButtonLabel, String rejectButtonLabel, int timeout) {
        if (logger.isActivated()) {
			logger.debug("Handle event user terms confirmation request");
		}

		// Nothing to do here
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleUserConfirmationAck(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public void handleUserConfirmationAck(String remote, String id, String status, String subject, String text) {
		if (logger.isActivated()) {
			logger.debug("Handle event user terms confirmation ack");
		}

		// Nothing to do here
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleUserNotification(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public void handleUserNotification(String remote, String id, String subject, String text, String okButtonLabel) {
        if (logger.isActivated()) {
            logger.debug("Handle event user terms notification");
        }

		// Nothing to do here
    }

    /* (non-Javadoc)
     * @see com.orangelabs.rcs.core.CoreListener#handleSimHasChanged()
     */
    public void handleSimHasChanged() {
        if (logger.isActivated()) {
            logger.debug("Handle SIM has changed");
        }

		// Restart the RCS service
        LauncherUtils.stopRcsService(getApplicationContext());
        LauncherUtils.launchRcsService(getApplicationContext(), true, false);
    }

	/* (non-Javadoc)
	 * @see com.orangelabs.rcs.core.CoreListener#handleIPCallInvitation(com.orangelabs.rcs.core.ims.service.ipcall.IPCallSession)
	 */
	public void handleIPCallInvitation(IPCallSession session) {
		if (logger.isActivated()) {
			logger.debug("Handle event IP call invitation");
		}
		
		// Broadcast the invitation
		ipcallApi.receiveIPCallInvitation(session);
	}
}
