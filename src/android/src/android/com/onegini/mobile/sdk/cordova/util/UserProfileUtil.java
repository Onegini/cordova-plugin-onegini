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

import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.PARAM_PROFILE_ID;

import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.Nullable;
import com.onegini.mobile.sdk.android.model.entity.UserProfile;

public class UserProfileUtil {

  @Nullable
  public static UserProfile findUserProfileById(final String id, final Set<UserProfile> userProfiles) {
    for (UserProfile userProfile : userProfiles) {
      if (id.equals(userProfile.getProfileId())) {
        return userProfile;
      }
    }

    return null;
  }

  public static JSONArray profileSetToJSONArray(final Set<UserProfile> userProfileSet) throws JSONException {
    JSONArray userProfileJSONArray = new JSONArray();
    for (final UserProfile userProfile : userProfileSet) {
      final JSONObject userProfileJSON = new JSONObject();
      userProfileJSON.put(PARAM_PROFILE_ID, userProfile.getProfileId());
      userProfileJSONArray.put(userProfileJSON);
    }

    return userProfileJSONArray;
  }
}
