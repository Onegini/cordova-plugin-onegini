package com.onegini.mobile.sdk.cordova.customregistration;


import java.util.HashSet;
import java.util.Set;

import androidx.annotation.Nullable;
import com.onegini.mobile.sdk.cordova.util.ApplicationConfigurationParser;

public class CustomIdentityProviderModel {

  private static final Set<CustomIdentityProvider> customIdentityProviders = new HashSet<CustomIdentityProvider>();

  private ApplicationConfigurationParser applicationConfigurationParser;

  public CustomIdentityProviderModel(final ApplicationConfigurationParser applicationConfigurationParser) {
    this.applicationConfigurationParser = applicationConfigurationParser;
  }

  public Set getAll() {
    return customIdentityProviders;
  }

  @Nullable
  public CustomIdentityProvider get(final String id) {
    for (final CustomIdentityProvider identityProvider : customIdentityProviders) {
      if (identityProvider.getId().equals(id)) {
        return identityProvider;
      }
    }
    return null;
  }

  public void collectFromConfigXml() {
    customIdentityProviders.clear();
    customIdentityProviders.addAll(applicationConfigurationParser.parserCustomIdentityProviders());
  }
}
