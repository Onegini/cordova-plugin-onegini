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

package com.onegini.mobile.sdk.cordova.client;

import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_CODE_FINGERPRINT_NO_AUTHENTICATION_IN_PROGRESS;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_CODE_ILLEGAL_ARGUMENT;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_CODE_NO_SUCH_AUTHENTICATOR;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_CODE_NO_USER_AUTHENTICATED;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_CODE_OPERATION_CANCELED;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_CODE_PROFILE_NOT_REGISTERED;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_CODE_PROVIDE_PIN_NO_AUTHENTICATION_IN_PROGRESS;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_FINGERPRINT_NO_AUTHENTICATION_IN_PROGRESS;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_ILLEGAL_ARGUMENT_PROFILE;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_NO_SUCH_AUTHENTICATOR;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_NO_USER_AUTHENTICATED;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_OPERATION_CANCELED;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_PROFILE_NOT_REGISTERED;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_PROVIDE_PIN_NO_AUTHENTICATION_IN_PROGRESS;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_ACCEPT;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_PROFILE_ID;

import java.util.Set;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.onegini.mobile.sdk.android.client.OneginiClient;
import com.onegini.mobile.sdk.android.handlers.request.callback.OneginiFingerprintCallback;
import com.onegini.mobile.sdk.android.handlers.request.callback.OneginiPinCallback;
import com.onegini.mobile.sdk.android.model.OneginiAuthenticator;
import com.onegini.mobile.sdk.android.model.entity.UserProfile;
import com.onegini.mobile.sdk.cordova.OneginiSDK;
import com.onegini.mobile.sdk.cordova.handler.AuthenticationHandler;
import com.onegini.mobile.sdk.cordova.handler.FingerprintAuthenticationRequestHandler;
import com.onegini.mobile.sdk.cordova.handler.ImplicitAuthenticationHandler;
import com.onegini.mobile.sdk.cordova.handler.LogoutHandler;
import com.onegini.mobile.sdk.cordova.handler.PinAuthenticationRequestHandler;
import com.onegini.mobile.sdk.cordova.util.ActionArgumentsUtil;
import com.onegini.mobile.sdk.cordova.util.PluginResultBuilder;
import com.onegini.mobile.sdk.cordova.util.UserProfileUtil;

@SuppressWarnings("unused")
public class UserAuthenticationClient extends CordovaPlugin {

  private static final String ACTION_AUTHENTICATE = "authenticate";
  private static final String ACTION_PROVIDE_PIN = "providePin";
  private static final String ACTION_RESPOND_TO_FINGERPRINT_REQUEST = "respondToFingerprintRequest";
  private static final String ACTION_FALLBACK_TO_PIN = "fallbackToPin";
  private static final String ACTION_LOGOUT = "logout";
  private static final String ACTION_GET_AUTHENTICATED_USER_PROFILE = "getAuthenticatedUserProfile";
  private static final String ACTION_AUTHENTICATE_IMPLICITLY = "authenticateImplicitly";
  private static final String ACTION_GET_IMPLICITLY_AUTHENTICATED_USER_PROFILE = "getImplicitlyAuthenticatedUserProfile";
  private static final String ACTION_CANCEL_FLOW = "cancelFlow";
  private static final int ARG_INDEX_AUTHENTICATOR = 1;

  private AuthenticationHandler authenticationHandler;

  @Override
  public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (ACTION_AUTHENTICATE.equals(action)) {
      authenticate(args, callbackContext);
      return true;
    } else if (ACTION_PROVIDE_PIN.equals(action)) {
      providePin(args, callbackContext);
      return true;
    } else if (ACTION_GET_AUTHENTICATED_USER_PROFILE.equals(action)) {
      getAuthenticatedUserProfile(callbackContext);
      return true;
    } else if (ACTION_LOGOUT.equals(action)) {
      logout(callbackContext);
      return true;
    } else if (ACTION_RESPOND_TO_FINGERPRINT_REQUEST.equals(action)) {
      respondToFingerprintRequest(callbackContext, args);
      return true;
    } else if (ACTION_FALLBACK_TO_PIN.equals(action)) {
      fallbackToPin(callbackContext);
      return true;
    } else if (ACTION_AUTHENTICATE_IMPLICITLY.equals(action)) {
      authenticateImplicitly(callbackContext, args);
      return true;
    } else if (ACTION_GET_IMPLICITLY_AUTHENTICATED_USER_PROFILE.equals(action)) {
      getImplicitlyAuthenticatedUserProfile(callbackContext);
      return true;
    } else if (ACTION_CANCEL_FLOW.equals(action)) {
      cancelFlow(callbackContext);
      return true;
    }

    return false;
  }

  private void authenticate(final JSONArray args, final CallbackContext callbackContext) {
    final UserProfile userProfile;

    try {
      userProfile = getUserProfileForAuthentication(args);
    } catch (IllegalArgumentException e) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withError()
          .withPluginError(e.getMessage(), ERROR_CODE_ILLEGAL_ARGUMENT)
          .build());

      return;
    } catch (Exception e) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withError()
          .withPluginError(e.getMessage(), ERROR_CODE_PROFILE_NOT_REGISTERED)
          .build());

      return;
    }

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        PinAuthenticationRequestHandler.getInstance().setStartAuthenticationCallbackContext(callbackContext);
        FingerprintAuthenticationRequestHandler.getInstance().setStartAuthenticationCallbackContext(callbackContext);
        authenticationHandler = new AuthenticationHandler(callbackContext);

        if (shouldUseSpecifiedAuthenticator(args)) {
          final OneginiAuthenticator authenticator = getSpecifiedAuthenticator(args, userProfile);
          if (authenticator == null) {
            callbackContext.sendPluginResult(new PluginResultBuilder()
                .withPluginError(ERROR_DESCRIPTION_NO_SUCH_AUTHENTICATOR, ERROR_CODE_NO_SUCH_AUTHENTICATOR)
                .build());
            return;
          }
          getOneginiClient().getUserClient().authenticateUser(userProfile, authenticator, authenticationHandler);
        } else {
          getOneginiClient().getUserClient().authenticateUser(userProfile, authenticationHandler);
        }
      }
    });
  }

  private boolean shouldUseSpecifiedAuthenticator(final JSONArray args) {
    return args.length() > ARG_INDEX_AUTHENTICATOR;
  }

  private OneginiAuthenticator getSpecifiedAuthenticator(final JSONArray args, final UserProfile userProfile) {
    final Set<OneginiAuthenticator> authenticators = getOneginiClient().getUserClient().getAllAuthenticators(userProfile);

    try {
      final JSONObject options = args.getJSONObject(ARG_INDEX_AUTHENTICATOR);
      return ActionArgumentsUtil.getAuthenticatorFromObject(options, authenticators);
    } catch (JSONException e) {
      return null;
    }
  }

  private UserProfile getUserProfileForAuthentication(final JSONArray args) throws Exception {
    final UserProfile userProfile;

    try {
      userProfile = getUserProfileFromPluginArgs(args);
    } catch (JSONException e) {
      throw new IllegalArgumentException(ERROR_DESCRIPTION_ILLEGAL_ARGUMENT_PROFILE);
    }

    if (userProfile == null) {
      throw new Exception(ERROR_DESCRIPTION_PROFILE_NOT_REGISTERED);
    }

    return userProfile;
  }

  private UserProfile getUserProfileFromPluginArgs(final JSONArray args) throws JSONException {
    final String profileId = args.getJSONObject(0).getString(PARAM_PROFILE_ID);
    final Set<UserProfile> registeredUserProfiles = getOneginiClient().getUserClient().getUserProfiles();

    return UserProfileUtil.findUserProfileById(profileId, registeredUserProfiles);
  }

  private void providePin(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    final String pin = ActionArgumentsUtil.getPinFromArguments(args);
    final OneginiPinCallback pinCallback = PinAuthenticationRequestHandler.getInstance().getPinCallback();
    authenticationHandler.setCallbackContext(callbackContext);

    if (pinCallback == null) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withPluginError(ERROR_DESCRIPTION_PROVIDE_PIN_NO_AUTHENTICATION_IN_PROGRESS, ERROR_CODE_PROVIDE_PIN_NO_AUTHENTICATION_IN_PROGRESS)
          .build());

      return;
    }

    pinCallback.acceptAuthenticationRequest(pin.toCharArray());

  }

  private void respondToFingerprintRequest(final CallbackContext callbackContext, final JSONArray args) {
    final OneginiFingerprintCallback fingerprintCallback = FingerprintAuthenticationRequestHandler.getInstance().getFingerprintCallback();
    final boolean shouldAccept;

    if (fingerprintCallback == null) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withPluginError(ERROR_DESCRIPTION_FINGERPRINT_NO_AUTHENTICATION_IN_PROGRESS, ERROR_CODE_FINGERPRINT_NO_AUTHENTICATION_IN_PROGRESS)
          .build());

      return;
    }

    try {
      shouldAccept = args.getJSONObject(0).getBoolean(PARAM_ACCEPT);
    } catch (JSONException e) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withPluginError(e.getMessage(), ERROR_CODE_ILLEGAL_ARGUMENT)
          .build());

      return;
    }

    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        if (shouldAccept) {
          fingerprintCallback.acceptAuthenticationRequest();
        } else {
          fingerprintCallback.denyAuthenticationRequest();
        }
      }
    });
  }

  private void fallbackToPin(final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        final OneginiFingerprintCallback fingerprintCallback = FingerprintAuthenticationRequestHandler.getInstance().getFingerprintCallback();
        if (fingerprintCallback != null) {
          fingerprintCallback.fallbackToPin();
        }
      }
    });
  }

  private void logout(final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        getOneginiClient().getUserClient()
            .logout(new LogoutHandler(callbackContext));
      }
    });
  }

  private void getAuthenticatedUserProfile(final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        final UserProfile authenticatedUserProfile = getOneginiClient().getUserClient().getAuthenticatedUserProfile();
        final PluginResultBuilder pluginResultBuilder = new PluginResultBuilder();

        if (authenticatedUserProfile == null) {
          pluginResultBuilder
              .withPluginError(ERROR_DESCRIPTION_NO_USER_AUTHENTICATED, ERROR_CODE_NO_USER_AUTHENTICATED);
        } else {
          pluginResultBuilder
              .withSuccess()
              .withProfileId(authenticatedUserProfile);
        }

        callbackContext.sendPluginResult(pluginResultBuilder.build());
      }
    });
  }

  private void authenticateImplicitly(final CallbackContext callbackContext, final JSONArray args) throws JSONException {
    final UserProfile userProfile = getUserProfileFromPluginArgs(args);
    final String[] scopes = ActionArgumentsUtil.getScopesFromArguments(args);

    if (userProfile == null) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withPluginError(ERROR_DESCRIPTION_PROFILE_NOT_REGISTERED, ERROR_CODE_ILLEGAL_ARGUMENT)
          .build());

      return;
    }

    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        final ImplicitAuthenticationHandler implicitAuthenticationHandler = new ImplicitAuthenticationHandler(callbackContext);

        getOneginiClient().getUserClient().authenticateUserImplicitly(userProfile, scopes, implicitAuthenticationHandler);
      }
    });
  }

  private void getImplicitlyAuthenticatedUserProfile(final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        final UserProfile implicitlyAuthenticatedUserProfile = getOneginiClient().getUserClient().getImplicitlyAuthenticatedUserProfile();
        final PluginResultBuilder pluginResultBuilder = new PluginResultBuilder();

        if (implicitlyAuthenticatedUserProfile == null) {
          pluginResultBuilder.withPluginError(ERROR_DESCRIPTION_NO_USER_AUTHENTICATED, ERROR_CODE_NO_USER_AUTHENTICATED);
        } else {
          pluginResultBuilder
              .withSuccess()
              .withProfileId(implicitlyAuthenticatedUserProfile);
        }

        callbackContext.sendPluginResult(pluginResultBuilder.build());
      }
    });
  }

  private void cancelFlow(final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        if (FingerprintAuthenticationRequestHandler.getInstance().getFingerprintCallback() != null) {
          FingerprintAuthenticationRequestHandler.getInstance().getFingerprintCallback().denyAuthenticationRequest();
        } else if (PinAuthenticationRequestHandler.getInstance().getPinCallback() != null) {
          PinAuthenticationRequestHandler.getInstance().getPinCallback().denyAuthenticationRequest();
        }
      }
    });
  }

  private OneginiClient getOneginiClient() {
    return OneginiSDK.getInstance().getOneginiClient(cordova.getActivity().getApplicationContext());
  }
}
