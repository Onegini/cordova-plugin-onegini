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

package com.onegini.mobile.sdk.cordova.util;

import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.IDENTITY_PROVIDER_ID;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_ANONYMOUS;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_AUTHENTICATOR_ID;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_AUTHENTICATOR_TYPE;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_BODY;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_HEADERS;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_METHOD;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_PIN;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_SCOPES;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_URL;
import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.TAG;

import java.security.InvalidParameterException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.Nullable;
import android.util.Log;
import com.onegini.mobile.sdk.android.model.OneginiAuthenticator;
import com.onegini.mobile.sdk.cordova.mobileAuthentication.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.internal.http.HttpMethod;

public class ActionArgumentsUtil {
  private static final MediaType CONTENT_TYPE_PLAIN_TEXT = MediaType.parse("text/plain;charset=utf-8");
  private static final String HEADER_KEY_CONTENT_TYPE = "Content-Type";

  public static String[] getScopesFromArguments(final JSONArray args) throws JSONException {
    final String[] scopesArray;
    final JSONArray scopesJSON;

    try {
      scopesJSON = args.getJSONObject(0).getJSONArray(PARAM_SCOPES);
    } catch (JSONException e) {
      return new String[0];
    }

    scopesArray = new String[scopesJSON.length()];
    for (int i = 0; i < scopesJSON.length(); i++) {
      scopesArray[i] = scopesJSON.getString(i);
    }

    return scopesArray;
  }

  public static String getPinFromArguments(final JSONArray args) throws JSONException {
    return args.getJSONObject(0).getString(PARAM_PIN);
  }

  public static String getIdentityProviderIdFromArguments(final JSONArray args) {
    try {
      return args.getJSONObject(0).getString(IDENTITY_PROVIDER_ID);
    } catch (JSONException e) {
      return null;
    }
  }

  public static Callback.Method getCallbackMethodFromArguments(final JSONArray args) throws JSONException {
    final String methodName = args.getJSONObject(0).getString(PARAM_METHOD);
    return Callback.Method.valueOf(methodName.toUpperCase(Locale.ENGLISH));
  }

  public static boolean isFetchAnonymous(final JSONObject options) throws JSONException {
    return options.getBoolean(PARAM_ANONYMOUS);
  }

  @Nullable
  public static OneginiAuthenticator getAuthenticatorFromArguments(final JSONArray args,
                                                                   final Set<OneginiAuthenticator> availableAuthenticators) throws JSONException {
    final JSONObject options = args.getJSONObject(0);
    return getAuthenticatorFromObject(options, availableAuthenticators);
  }

  @Nullable
  public static OneginiAuthenticator getAuthenticatorFromObject(final JSONObject authenticatorObj,
                                                                final Set<OneginiAuthenticator> availableAuthenticators) throws JSONException {
    final String authenticatorType = authenticatorObj.getString(PARAM_AUTHENTICATOR_TYPE);
    final boolean hasAuthenticatorId = authenticatorObj.has(PARAM_AUTHENTICATOR_ID);

    if (hasAuthenticatorId) {
      final String authenticatorId = authenticatorObj.getString(PARAM_AUTHENTICATOR_ID);
      return getAuthenticatorByTypeAndId(availableAuthenticators, authenticatorType, authenticatorId);
    } else {
      return getAuthenticatorByType(availableAuthenticators, authenticatorType);
    }
  }

  @Nullable
  private static OneginiAuthenticator getAuthenticatorByType(final Set<OneginiAuthenticator> availableAuthenticators, final String targetType) {
    for (OneginiAuthenticator authenticator : availableAuthenticators) {
      final String type = AuthenticatorUtil.authenticatorTypeToString(authenticator.getType());

      if (type == null) {
        continue;
      }

      if (type.equals(targetType)) {
        return authenticator;
      }
    }

    return null;
  }

  @Nullable
  private static OneginiAuthenticator getAuthenticatorByTypeAndId(final Set<OneginiAuthenticator> availableAuthenticators, final String targetType,
                                                                  final String targetId) {
    for (OneginiAuthenticator authenticator : availableAuthenticators) {
      final String type = AuthenticatorUtil.authenticatorTypeToString(authenticator.getType());
      final String id = authenticator.getId();

      if (type == null || id == null) {
        continue;
      }

      if (type.equals(targetType) && id.equals(targetId)) {
        return authenticator;
      }
    }

    return null;
  }

  public static Request getRequestFromArguments(final JSONObject options, final String resourceBaseUrl) throws JSONException {
    final String url = buildUrl(options, resourceBaseUrl);
    final String method = getMethodFromOptions(options);
    final Headers headers = getHeadersFromOptions(options);
    RequestBody body = getBodyFromOptions(options, headers);

    if (methodDoesNotPermitRequestBody(method)) {
      body = null;
    }

    return new Request.Builder()
        .url(url)
        .method(method, body)
        .headers(headers)
        .build();
  }

  private static String buildUrl(final JSONObject options, final String resourceBaseUrl) throws JSONException {
    final String resourceUrl = getURLFromOptions(options);
    if(isRelative(resourceUrl)) {
      return resourceBaseUrl + resourceUrl;
    }
    return resourceUrl;
  }

  private static boolean isRelative(final String url) {
    return url.startsWith("/");
  }


  private static boolean methodDoesNotPermitRequestBody(final String method) {
    return !HttpMethod.permitsRequestBody(method);
  }

  private static String getURLFromOptions(final JSONObject options) throws JSONException {
    return options.getString(PARAM_URL);
  }

  private static String getMethodFromOptions(final JSONObject options) throws JSONException {
    return options.getString(PARAM_METHOD);
  }

  private static Headers getHeadersFromOptions(final JSONObject options) throws JSONException {
    final JSONObject headersObj = options.getJSONObject(PARAM_HEADERS);
    final Iterator<String> keys = headersObj.keys();

    final Headers.Builder headersBuilder = new Headers.Builder();
    while (keys.hasNext()) {
      String name = keys.next();
      String value = headersObj.getString(name);
      headersBuilder.add(name, value);
    }

    return headersBuilder.build();
  }

  private static RequestBody getBodyFromOptions(final JSONObject options, final Headers headers) {
    RequestBody body;
    MediaType contentType = getContentTypeForRequest(headers);

    boolean isRequestWithoutBody = !options.has(PARAM_BODY);
    if (isRequestWithoutBody) {
      return RequestBody.create(contentType, "");
    }

    try {
      body = RequestBody.create(contentType, options.getString(PARAM_BODY));
    } catch (JSONException e) {
      String message = "Error while trying to get the request body from the javascript method arguments";
      Log.e(TAG, message, e);
      throw new InvalidParameterException(message);
    }

    return body;
  }

  private static MediaType getContentTypeForRequest(final Headers headers) {
    String contentTypeHeaderValue = headers.get(HEADER_KEY_CONTENT_TYPE);

    if (contentTypeHeaderValue == null) {
      return CONTENT_TYPE_PLAIN_TEXT;
    }

    MediaType contentType = MediaType.parse(contentTypeHeaderValue);
    return contentType == null ? CONTENT_TYPE_PLAIN_TEXT : contentType;
  }
}
