/*
 * Copyright (c) 2017-2018 Onegini B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.onegini.mobile.sdk.cordova.handler;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import com.onegini.mobile.sdk.android.handlers.OneginiAuthenticatorDeregistrationHandler;
import com.onegini.mobile.sdk.android.handlers.error.OneginiAuthenticatorDeregistrationError;
import com.onegini.mobile.sdk.cordova.util.PluginResultBuilder;

public class AuthenticatorDeregistrationHandler implements OneginiAuthenticatorDeregistrationHandler {
  private CallbackContext callbackContext;

  public AuthenticatorDeregistrationHandler(final CallbackContext callbackContext) {
    this.callbackContext = callbackContext;
  }

  @Override
  public void onSuccess() {
    final PluginResult pluginResult = new PluginResultBuilder()
        .withSuccess()
        .build();

    sendPluginResult(pluginResult);
  }

  @Override
  public void onError(final OneginiAuthenticatorDeregistrationError oneginiAuthenticatorDeregistrationError) {
    final PluginResult pluginResult = new PluginResultBuilder()
        .withOneginiError(oneginiAuthenticatorDeregistrationError)
        .build();

    sendPluginResult(pluginResult);
  }

  public void setCallbackContext(final CallbackContext callbackContext) {
    this.callbackContext = callbackContext;
  }

  private void sendPluginResult(final PluginResult pluginResult) {
    if (!callbackContext.isFinished()) {
      callbackContext.sendPluginResult(pluginResult);
    }
  }
}
