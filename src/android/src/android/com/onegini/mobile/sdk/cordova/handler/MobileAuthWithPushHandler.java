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

import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_CODE_INCORRECT_PIN;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_INCORRECT_PIN;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.EVENT_FINGERPRINT_CAPTURED;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.EVENT_FINGERPRINT_FAILED;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.EVENT_PIN_REQUEST;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import androidx.annotation.NonNull;
import com.onegini.mobile.sdk.android.handlers.OneginiMobileAuthenticationHandler;
import com.onegini.mobile.sdk.android.handlers.error.OneginiError;
import com.onegini.mobile.sdk.android.handlers.error.OneginiMobileAuthenticationError;
import com.onegini.mobile.sdk.android.handlers.request.OneginiMobileAuthWithPushFingerprintRequestHandler;
import com.onegini.mobile.sdk.android.handlers.request.OneginiMobileAuthWithPushPinRequestHandler;
import com.onegini.mobile.sdk.android.handlers.request.OneginiMobileAuthWithPushRequestHandler;
import com.onegini.mobile.sdk.android.handlers.request.callback.OneginiAcceptDenyCallback;
import com.onegini.mobile.sdk.android.handlers.request.callback.OneginiFingerprintCallback;
import com.onegini.mobile.sdk.android.handlers.request.callback.OneginiPinCallback;
import com.onegini.mobile.sdk.android.model.entity.AuthenticationAttemptCounter;
import com.onegini.mobile.sdk.android.model.entity.CustomInfo;
import com.onegini.mobile.sdk.android.model.entity.OneginiMobileAuthenticationRequest;
import com.onegini.mobile.sdk.cordova.mobileAuthentication.Callback;
import com.onegini.mobile.sdk.cordova.mobileAuthentication.ConfirmationCallback;
import com.onegini.mobile.sdk.cordova.mobileAuthentication.FingerprintCallback;
import com.onegini.mobile.sdk.cordova.mobileAuthentication.PinCallback;
import com.onegini.mobile.sdk.cordova.util.PluginResultBuilder;

public class MobileAuthWithPushHandler
    implements OneginiMobileAuthenticationHandler, OneginiMobileAuthWithPushRequestHandler,
    OneginiMobileAuthWithPushPinRequestHandler, OneginiMobileAuthWithPushFingerprintRequestHandler {

  private static MobileAuthWithPushHandler instance = null;
  private final HashMap<Callback.Method, CallbackContext> challengeReceivers = new HashMap<Callback.Method, CallbackContext>();
  private final List<Callback> callbackQueue = new LinkedList<Callback>();
  private boolean isRunning = false;

  private MobileAuthWithPushHandler() {
  }

  public static MobileAuthWithPushHandler getInstance() {
    if (instance == null) {
      instance = new MobileAuthWithPushHandler();
    }

    return instance;
  }

  public Callback getCurrentCallback() {
    if (!isRunning) {
      return null;
    }

    return getFirstElementFromCallbackQueue();
  }

  public void registerAuthenticationChallengeReceiver(final Callback.Method method, final CallbackContext callbackContext) {
    challengeReceivers.put(method, callbackContext);
    handleNextAuthenticationRequest();
  }

  // *START* Handling mobile authentication without additional authentication

  @Override
  public void startAuthentication(final OneginiMobileAuthenticationRequest mobileAuthenticationRequest,
                                  final OneginiAcceptDenyCallback acceptDenyCallback) {
    final ConfirmationCallback confirmationCallback = new ConfirmationCallback(mobileAuthenticationRequest, acceptDenyCallback);
    addAuthenticationRequestToQueue(confirmationCallback);
  }

  // *END*

  // *START* Handling mobile authentication with PIN

  @Override
  public void startAuthentication(final OneginiMobileAuthenticationRequest oneginiMobileAuthenticationRequest, final OneginiPinCallback oneginiPinCallback,
                                  final AuthenticationAttemptCounter authenticationAttemptCounter,
                                  final OneginiMobileAuthenticationError oneginiMobileAuthenticationError) {
    final PinCallback pinCallback = new PinCallback(oneginiMobileAuthenticationRequest, oneginiPinCallback, authenticationAttemptCounter);
    final int fallbackFromFingerprintRequestQueueId = findFingerprintCallbackInTheQueue(pinCallback.getMobileAuthenticationRequest().getTransactionId());
    if (fallbackFromFingerprintRequestQueueId != -1) {
        replaceFingerprintAuthenticationRequestWithPinFallbackToQueue(pinCallback, fallbackFromFingerprintRequestQueueId);
        isRunning = false;
        handleNextAuthenticationRequest();
    } else {
        addAuthenticationRequestToQueue(pinCallback);
    }
  }

  private void replaceFingerprintAuthenticationRequestWithPinFallbackToQueue(final PinCallback pinCallback, final int isItFingerprintFallback) {
    final OneginiMobileAuthenticationRequest fallbackMobileAuthenticationRequest = getFallbackMobileAuthenticationRequest(pinCallback);
    final PinCallback fallbackPinCallback = new PinCallback(fallbackMobileAuthenticationRequest, pinCallback.getPinCallback(), pinCallback
            .getAuthenticationAttemptCounter());
    callbackQueue.remove(isItFingerprintFallback);
    callbackQueue.add(isItFingerprintFallback, fallbackPinCallback);
  }

  @NonNull
  private OneginiMobileAuthenticationRequest getFallbackMobileAuthenticationRequest(final PinCallback pinCallback) {
    final OneginiMobileAuthenticationRequest mobileAuthenticationRequest = pinCallback.getMobileAuthenticationRequest();
    return new OneginiMobileAuthenticationRequest(mobileAuthenticationRequest.getMessage(), "push_with_pin", mobileAuthenticationRequest.getUserProfile(),
            mobileAuthenticationRequest.getTransactionId(), mobileAuthenticationRequest.getSigningData());
  }

  @Override
  public void onNextAuthenticationAttempt(final AuthenticationAttemptCounter authenticationAttemptCounter) {
    final CallbackContext callbackContext = getChallengeReceiverForCallbackMethod(Callback.Method.PIN);
    final PinCallback pinCallback = (PinCallback) getFirstElementFromCallbackQueue();

    callbackContext.sendPluginResult(new PluginResultBuilder()
        .withPluginError(ERROR_DESCRIPTION_INCORRECT_PIN, ERROR_CODE_INCORRECT_PIN)
        .withSuccess()
        .shouldKeepCallback()
        .withEvent(EVENT_PIN_REQUEST)
        .withRemainingFailureCount(authenticationAttemptCounter.getRemainingAttempts())
        .withMaxFailureCount(authenticationAttemptCounter.getMaxAttempts())
        .withOneginiMobileAuthenticationRequest(pinCallback.getMobileAuthenticationRequest())
        .build());
  }

  // *END*

  // *START* Handling mobile authentication with Fingerprint

  @Override
  public void startAuthentication(final OneginiMobileAuthenticationRequest oneginiMobileAuthenticationRequest,
                                  final OneginiFingerprintCallback oneginiFingerprintCallback) {
    final FingerprintCallback fingerprintCallback = new FingerprintCallback(oneginiMobileAuthenticationRequest, oneginiFingerprintCallback);
    addAuthenticationRequestToQueue(fingerprintCallback);
  }

  @Override
  public void onNextAuthenticationAttempt() {
    final FingerprintCallback fingerprintCallback = (FingerprintCallback) getFirstElementFromCallbackQueue();
    final CallbackContext callbackContext = getChallengeReceiverForCallbackMethod(Callback.Method.FINGERPRINT);

    callbackContext.sendPluginResult(new PluginResultBuilder()
        .withSuccess()
        .shouldKeepCallback()
        .withEvent(EVENT_FINGERPRINT_FAILED)
        .withOneginiMobileAuthenticationRequest(fingerprintCallback.getMobileAuthenticationRequest())
        .build());
  }

  @Override
  public void onFingerprintCaptured() {
    final FingerprintCallback fingerprintCallback = (FingerprintCallback) getFirstElementFromCallbackQueue();
    final CallbackContext callbackContext = getChallengeReceiverForCallbackMethod(Callback.Method.FINGERPRINT);

    callbackContext.sendPluginResult(new PluginResultBuilder()
        .withSuccess()
        .shouldKeepCallback()
        .withEvent(EVENT_FINGERPRINT_CAPTURED)
        .withOneginiMobileAuthenticationRequest(fingerprintCallback.getMobileAuthenticationRequest())
        .build());
  }

  // *END*

  // *START* Mobile authentication Handler methods

  @Override
  public void onSuccess(final CustomInfo customInfo) {
    finishAuthenticationRequest(null);
  }


  @Override
  public void onError(final OneginiMobileAuthenticationError oneginiMobileAuthenticationError) {
    finishAuthenticationRequest(oneginiMobileAuthenticationError);
  }

  // *END*

  private void handleNextAuthenticationRequest() {
    final Callback callback = getFirstElementFromCallbackQueue();

    if (isRunning || callback == null) {
      return;
    }

    final CallbackContext callbackContext = getChallengeReceiverForCallbackMethod(callback.getMethod());
    if (callbackContext == null) {
      return;
    }

    isRunning = true;
    final OneginiMobileAuthenticationRequest mobileAuthenticationRequest = callback.getMobileAuthenticationRequest();
    final PluginResultBuilder pluginResultBuilder = new PluginResultBuilder();

    pluginResultBuilder
        .withSuccess()
        .shouldKeepCallback()
        .withEvent(callback.getAuthenticationRequestEventName())
        .withOneginiMobileAuthenticationRequest(mobileAuthenticationRequest);

    if (callback instanceof PinCallback) {
      final PinCallback pinCallback = (PinCallback) callback;
      pluginResultBuilder
          .withRemainingFailureCount(pinCallback.getAuthenticationAttemptCounter().getRemainingAttempts())
          .withMaxFailureCount(pinCallback.getAuthenticationAttemptCounter().getMaxAttempts());
    }

    callbackContext.sendPluginResult(pluginResultBuilder.build());
  }

  private void addAuthenticationRequestToQueue(final Callback callback) {
    callbackQueue.add(callback);
    handleNextAuthenticationRequest();
  }

  private int findFingerprintCallbackInTheQueue(final String transactionId) {
    if (transactionId == null) {
      return -1;
    }

    for (int i = 0; i < callbackQueue.size(); i++) {
      final Callback callback = callbackQueue.get(i);
      final OneginiMobileAuthenticationRequest mobileAuthenticationRequest = callback.getMobileAuthenticationRequest();
      if (callback instanceof FingerprintCallback && mobileAuthenticationRequest != null
          && transactionId.equals(mobileAuthenticationRequest.getTransactionId())) {
        return i;
      }
    }
    return -1;
  }

  private void finishAuthenticationRequest(final OneginiError oneginiError) {
    final Callback callback = callbackQueue.remove(0);
    if (callback == null) {
      // We don't have a callback anymore so we cannot perform any callback. We'll just continue to process the next authentication request
      startProcessingNextAuthenticationRequest();
      return;
    }

    final CallbackContext callbackContext = callback.getChallengeResponseCallbackContext();
    if (callbackContext == null) {
      // We don't have a challenge / response callback so we cannot perform any callback. We'll just continue to process the next authentication request
      startProcessingNextAuthenticationRequest();
      return;
    }

    final PluginResult pluginResult;

    if (oneginiError == null) {
      pluginResult = new PluginResultBuilder()
          .withSuccess()
          .build();
    } else {
      pluginResult = new PluginResultBuilder()
          .withOneginiError(oneginiError)
          .build();
    }

    callbackContext.sendPluginResult(pluginResult);

    startProcessingNextAuthenticationRequest();
  }

  private void startProcessingNextAuthenticationRequest() {
    isRunning = false;
    handleNextAuthenticationRequest();
  }

  private CallbackContext getChallengeReceiverForCallbackMethod(final Callback.Method method) {
    return challengeReceivers.get(method);
  }

  @Override
  public void finishAuthentication() {
    // We don't want to do anything here because the onSuccess or onError methods of the handler will be called. This method is a convenience method when you
    // already want to close your UI dialog after authentication was finished.
  }

  private Callback getFirstElementFromCallbackQueue() {
    if (callbackQueue.isEmpty()) {
      return null;
    }
    return callbackQueue.get(0);
  }
}
