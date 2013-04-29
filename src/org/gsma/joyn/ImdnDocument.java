/*
 * Copyright 2013, France Telecom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gsma.joyn;

import java.lang.String;

/**
 * Class ImdnDocument.
 *
 * @author Jean-Marc AUFFRET (Orange)
 * @version 1.0
 * @since 1.0
 */
public class ImdnDocument {
    /**
     * Constant MIME_TYPE.
     */
    public static final String MIME_TYPE = "message/imdn+xml";

    /**
     * Constant DELIVERY_STATUS_DELIVERED.
     */
    public static final String DELIVERY_STATUS_DELIVERED = "delivered";

    /**
     * Constant DELIVERY_STATUS_DISPLAYED.
     */
    public static final String DELIVERY_STATUS_DISPLAYED = "displayed";

    /**
     * Constant DELIVERY_STATUS_FAILED.
     */
    public static final String DELIVERY_STATUS_FAILED = "failed";

    /**
     * Constant DELIVERY_STATUS_ERROR.
     */
    public static final String DELIVERY_STATUS_ERROR = "error";

    /**
     * Constant DELIVERY_STATUS_FORBIDDEN.
     */
    public static final String DELIVERY_STATUS_FORBIDDEN = "forbidden";

    /**
     * Constant IMDN_NAMESPACE.
     */
    public static final String IMDN_NAMESPACE = "imdn <urn:ietf:params:imdn>";

    /**
     * Constant POSITIVE_DELIVERY.
     */
    public static final String POSITIVE_DELIVERY = "positive-delivery";

    /**
     * Constant DISPLAY.
     */
    public static final String DISPLAY = "display";

    /**
     * Constant NOTIFICATION.
     */
    public static final String NOTIFICATION = "notification";

    /**
     * Creates a new instance of ImdnDocument.
     */
    public ImdnDocument() {

    }

    /**
     * Returns the status.
     *
     * @return  The status.
     */
    public String getStatus() {
        return (String) null;
    }

    /**
     * Sets the status.
     *
     * @param 
     */
    public void setStatus(String status) {

    }

    /**
     * Returns the msg id.
     *
     * @return  The msg id.
     */
    public String getMsgId() {
        return (String) null;
    }

    /**
     * Sets the msg id.
     *
     * @param 
     */
    public void setMsgId(String messageId) {

    }

} // end ImdnDocument