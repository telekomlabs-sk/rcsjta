/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
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
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.services.rcs.Intents;

/**
 * Get status intent receiver
 * 
 * @author Jean-Marc AUFFRET
 */
public class GetStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().endsWith(Intents.Service.ACTION_GET_STATUS)) {
            RcsSettings.createInstance(context);
            Bundle results = getResultExtras(true);
            results.putString(Intents.Service.EXTRA_PACKAGENAME, context.getPackageName());
            results.putBoolean(Intents.Service.EXTRA_STATUS, RcsSettings.getInstance()
                    .isServiceActivated());
            setResultExtras(results);
        }
    }
}