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

import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.TAG;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import android.util.Log;
import okhttp3.Response;

public class OkHttpResponseUtil {

  public static JSONObject getJsonHeadersFromResponse(Response response) {
    final JSONObject result = new JSONObject();

    for (String headerName : response.headers().names()) {
      try {
        for (String headerValue : response.headers().values(headerName)) {
          result.put(headerName, headerValue);
        }
      } catch (JSONException e) {
        Log.e(TAG, "Could not parse http response header '" + headerName + "' to JSON object");
      }
    }

    return result;
  }

  @NonNull
  public static byte[] getBodyBytesFromResponse(Response response) {
    if (response.body() == null) {
      return new byte[0];
    }

    try {
      return response.body().bytes();
    } catch (IOException e) {
      String message = "Could not read response body from HTTP response";
      Log.e(TAG, message, e);
      throw new IllegalStateException(message);
    }
  }
}
