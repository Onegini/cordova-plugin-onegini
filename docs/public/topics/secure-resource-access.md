# Secure resource access

There are three types of authentication tokens your app can use to securely access resources like account information, transactions, etc. from a back-end
or resource server. A user or device needs to be authenticated before a resource can be fetched with the corresponding authentication token.
Different resources might require using different methods for authentication. The following authentication token types can be used:
- **User authentication (default)**: Requires the user to be [fully authenticated](../reference/user/authenticate.md), meaning to be authenticated with an
authenticator (PIN or Fingerprint for example).
- **Implicit authentication**: Requires the user to be [implicitly authenticated](../reference/user/authenticateImplicitly.md), meaning the user has registered with
the device before, this does not require interaction with an authenticator like PIN or Fingerprint.
- **Anonymous authentication**: Requires the device to be registered and [authenticated](../reference/device/authenticate.md) with the Token Server, but no user
has to be authenticated in any way.

The Onegini Cordova plugin exposes the [`resource.fetch`](../reference/resource/fetch.md) function to perform these types of resource calls. The plugin ensures
the confidentiality and authenticity of the payload. The application itself is responsible for the structure and/or processing of the payload.

<!-- toc -->

## Using fetch with user authentication

In order to successfully request a resource for a specific user, the client credentials must be valid and the user must have a valid access token. In other words,
the user must be logged in before a resource call can be made on their behalf. This type of resource request should be used to fetch sensitive data that
requires user authentication, like account details and transaction history. After [authentication](./user-authentication.md), a resource can be fetched as follows:

**Example code to fetch a resource with user authentication:**

```js
onegini.resource.fetch({
      url: "https://my.server.com/resources/user/transaction-history",
      auth: onegini.resource.auth.USER
    })
    .then((response) => {
      alert("Fetch success!\n\nResponse: " + response.status);
    })
    .catch((err) => {
      alert("Fetch error!\n\n" + err.description);
    });
```

The `auth` option specifies which authentication token should be send to the Token Server. This property defaults to `onegini.resource.auth.USER`, but is
explicitly set here for the sake of completion.   

## Using fetch with implicit authentication

Before fetching an implicit resource, the user must be [authenticated implicitly](../reference/user/authenticateImplicitly.md).

To specify that we will use the implicit authentication token, the `auth` property must be set to `onegini.resource.auth.IMPLICIT`.

** Example code to fetch a resource with implicit authentication: **

```js
onegini.resource.fetch({
        url: 'https://my.server.com/resources/account-balance',
        auth: onegini.resource.auth.IMPLICIT
      }).then((response) => {
        alert("Implicit fetch success!\n\nResponse: " + response.status);
      }).catch((err) => {
        alert("Implicit fetch error!\n\n" + err.description);
      });
```

## Using fetch with anonymous authentication

A device can use its OAuth credentials to authenticate itself with the Token Server, and obtain an access token. An anonymous resource call can be used in cases
where a user does not need to be logged in or even registered in order to use certain functionality, or access some resource.

To specify that we will be using the anonymous authentication token, the `auth` property must be set to `onegini.resource.auth.ANONYMOUS`.

**Example code to fetch an anonymous resource:**


```js
onegini.resource.fetch({
      url: "https://my.server.com/resources/device/app-details",
      auth: onegini.resource.auth.ANONYMOUS
    })
    .then((response) => {
      alert("Anonymous fetch success!\n\nResponse: " + response.status);
    })
    .catch((err) => {
      alert("Anonymous fetch error!\n\n" + err.description);
    });
```

In case of success, the `response` object also contains the body and any headers that were included. For more details, see the documentation for [fetch function](../reference/resource/fetch.md).

For details of the response and error objects, see the documentation for the [fetch function](../reference/resource/fetch.md).

## Intercepting XHR requests

While it is best to use `onegini.resource.fetch` to access secured resources, it is also possible to initiate `XMLHTTPRequest` calls yourself, and allow the
Onegini Cordova plugin to intercept the outgoing requests. The requests will be authenticated and encrypted as necessary. Similarly, responses will be decrypted
if necessary, allowing you to use the response as normal.

This option must be set upon initialization of the Onegini Cordova plugin. It is implemented as an argument to [`onegini.start`](../reference/start.md).

**Example code to initialize the plugin with XHR interception:**

```js
onegini.start({
      secureXhr: true
    })
    .then(() => {
      console.log("Onegini is ready!");
    })
    .catch((err) => {
      console.log("Onegini failed to initialize: ", err);
    });
```

### Caveats

* Currently, only user authenticated requests (`onegini.resource.auth.USER`) are supported using XHR intercepts.
* `xhr.responseType = 'arrayBuffer'` is only supported on Android 4.4 and up. The underlying `XMLHttpRequest` implementation on older Android devices does not support this method.
* Properties `responseURL`, `responseXML`, `timeout`, `upload`, `XMLHttpRequestUpload`, `withCredentials` are not supported.
* Events `abort`, `loadend`, `loadstart`, `progress`, `timeout` are not supported.
* Methods `abort`, `overrideMimeType` are not supported.
