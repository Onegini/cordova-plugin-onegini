# Mobile authentication with push

<!-- toc -->

## Introduction

Two factor authentication can be implemented using APNs & FCM push notifications. It's a very secure and user friendly way of authentication. You can use this
feature to allow users to confirm their transactions. Confirmation can be done using simple Accept button, PIN or a Fingerprint  authenticator. All
transaction information is encrypted and no sensitive information is sent through either APNs or FCM.

## Enrollment

To enroll for push first you need to obtain the device token from FCM/APNS. This has to be done by calling native
Android/iOS libraries like `com.google.firebase:firebase-messaging`. In case of Cordova you can either create your own
JavaScript wrapper around your native code or use one of the existing wrappers. You are not limited to any particular
plugin; in the example below we used the [Cordova Plugin Push v2.0.0](https://github.com/havesource/cordova-plugin-push)
with additional [Cordova Plugin AndroidX Adapter](https://github.com/dpa99c/cordova-plugin-androidx-adapter) to make it
work with AndroidX.

Once you obtain the deviceToken, the Onegini Cordova plugin requires an authenticated or logged in user to enroll for mobile authentication with push.
The user can be enrolled on only one application instance at a time. If the user has multiple mobile devices on which the application is installed, the user
can only enroll for mobile authentication with push on one of these devices. Enrollment is done by calling `onegini.mobileAuth.push.enroll()` function.

You also need to implement handling of the notification event received by the system. After receiving the notification, you should verify if it can be handled by Onegini Cordova Plugin.
You can do it by calling `onegini.mobileAuth.push.canHandlePushMessage()` function. If the function returns `true` you can pass it the plugin by calling `onegini.mobileAuth.push.handlePushMessage()` function. Both methods expect you to pass a JSON object as the argument.

**Example code to enroll an authenticated user for mobile authentication with push using PhonegapPluginPush:**

```js
const push = PushNotification.init({android: {}, ios: {alert: "true", badge: "true", sound: "true"}});
push.on('registration', (data) => {
  onegini.mobileAuth.push.enroll(data)
    .then(() => {
      alert("Enrollment success!");
    })
    .catch((err) => {
      alert("Enrollment error!\n\n" + err.description);
    });
});
push.on('notification', (data) => {
  if (onegini.mobileAuth.push.canHandlePushMessage(data.additionalData)) {
    onegini.mobileAuth.push.handlePushMessage(data.additionalData)
      .catch((err) => alert('Push message error: ' + err.description));
  } else {
    var message = (data.title ? data.title + " " : "") + (data.message ? data.message : "");
    if (message.length > 0) {
      alert(message);
    }
  }
});
pusher.on('error', (e) => {
  alert('Push message error: ' + e.message);
});
```

If you want to use mobile fingerprint authentication, you will need to register the fingerprint authenticator for the relevant user (see
[User authentication with fingerprint](user-authentication-with-system-biometric-authenticators.md)).

Successive invocations of enrollment for mobile authentication with push will re-enroll the device only if the mobile authentication with push override is
enabled in The Token Server configuration. See the [Token Server mobile authentication configuration]({{book.app_config_mobile_authentication}}) for more
information on the server side configuration of mobile authentication.

The plugin also provides a convenience method to check whether a user is enrolled for mobile authentication with push. The `onegini.mobileAuth.push.isUserEnrolled`
method provides you with the information whether the user is already enrolled for mobile authentication with push. Below follows an example implementation.

```js
onegini.mobileAuth.push.isUserEnrolled({
      profileId: "W8DUJ2"
    })
    .then((isEnrolled) => {
      if (isEnrolled) {
        alert("The user is enrolled.");
      } else {
        alert("The user is not enrolled.");
      }
    })
    .catch((err) => {
      alert("error!\n\n" + err.description);
    });
```

## Receiving requests

Once the user is enrolled he is able to receive push notifications. When device did not receive sent push notifications you can use this feature to fetch a list
containing pending mobile authentication requests. The process of receiving a push from the Token Server can be described as follows. In this flow we call the
initiator of the mobile authentication request 'portal':

```
1. Portal -> Token Server: Initialize mobile authentication.
2. Token Server -> Portal: Identifier of the initialized mobile authentication transaction.
3. APP -> Cordova Plugin -> Token Server: Fetch all pending mobile authentication requests
```

Fetching pending mobile authentication requests can be performed using the following method:

```js
onegini.mobileAuth.push.getPendingRequests()
  .then((result) => {
    this.pushRequests = result;
  })
  .catch((err) => {
    console.error('Error while fetching pending push requests:', err);
    this.status = 'Could not fetch pending push requests';
  });
```

From the fetched obejcts you can get following information about mobile authentication request:
  - `userProfile` - instance of `ONGUserProfile` for which request has been received.
  - `message` - message specified by the portal when initiating the mobile authentication request.
  - `transactionId` - A unique identifier for each Mobile Authentication request.
  - `timestamp` - the date when the mobile authentication request was sent.
  - `timeToLive` - time to live for which mobile authentication request was sent.

Handling one of the pending mobile authentication request from the list can be performed using the following method:

```js
  onegini.mobileAuth.push.handlePendingRequest(pushRequest)
```


## Request handling

You can configure different mobile authentication types in the Token Server [mobile authentication configuration](https://docs.onegini.com/token-server/topics/mobile-apps/mobile-authentication/mobile-authentication.html#configure-authentication-properties)
panel. There are currently three different methods of authentication. These are: **push**, **push with PIN** and **push with fingerprint**.
Only once the authentication type has been configured on the Token Server can it be used.

### Push

The first type of mobile authentication request is the most simple one, push (also referred to as "confirmation"). The user has the option to simply accept or
deny a request. Typically, this is done by displaying two buttons to the user, although the choice of UI is free and left entirely to the app developer.

To handle a push mobile authentication request, the handler returned by [`onegini.mobileAuth.push.on`](../reference/mobileAuthentication/push/on.md) must be
implemented for the parameter `"confirmation"`.

**Example code to handle push mobile authentication requests:**

```js
onegini.mobileAuth.push.on("confirmation")
    .onConfirmationRequest((actions, request) => {
      console.log("New mobile authentication request", request);

      // Ask user if they want to accept or deny the request. In this
      // example, the user accepts the request.
      let userAcceptedRequest = true;

      if (userAcceptedRequest) {
        actions.accept();
      } else {
        actions.deny();
      }
    })
    .onSuccess(() => {
      alert("Mobile authentication request success!");
    })
    .onError((err) => {
      alert("Mobile authentication request failed!\n\n" + err.description);
    });
```

The method `onConfirmationRequest` will be called when the Onegini Cordova plugin receives a mobile authentication request via push. The `actions.accept` and
`actions.deny` methods allow the user to choose whether to accept or deny the mobile authentication request.

### Push with PIN

It is also possible to require the user to enter their PIN in order to confirm their identity before accepting a mobile authentication request. The push with
PIN type of mobile authentication request adds another layer of security to the two factor authentication in your product.

Handling a push with PIN is similar to the simpler push, except the parameter given to
[`onegini.mobileAuth.push.on`](../reference/mobileAuthentication/push/on.md) is `"pin"`. It also requires the implementation of a different, but familiar
handler method.

**Example code to handle push with PIN mobile authentication requests:**

```js
onegini.mobileAuth.push.on("pin")
    .onPinRequest((actions, request) => {
      console.log("New mobile authentication request", request);

      // Ask the user if they want to accept or deny the request, and enter
      // their PIN in case of accept. In this example, the user accepts the
      // request and enters a PIN of "12346".
      let userAcceptedRequest = true;
      let pin = "12346";

      if (userAcceptedRequest) {
        actions.accept(pin);
      } else {
        actions.deny();
      }
    })
    .onSuccess(() => {
      alert("Mobile authentication request success!");
    })
    .onError((err) => {
      alert("Mobile authentication request failed!\n\n" + err.description);
    });
```

The `request` argument contains some helpful properties such as the number of failed and remaining PIN attempts. See
[`onegini.mobileAuth.push.on`](../reference/mobileAuthentication/push/on.md) for more details.

### Push with fingerprint

For devices that support it, it is also possible to allow mobile authentication requests for fingerprint. Like push with PIN, this mobile authentication method
adds an extra layer of security, while often being more convenient for the user than PIN. The user is required to scan their fingerprint before the mobile
authentication request is confirmed.

The use of this mobile authentication method requires the fingerprint authenticator to have been registered for the user. See
[User authentication with fingerprint](user-authentication-with-system-biometric-authenticators.md) topic guide for more information.

Handling a push with fingerprint is very similar to the other types of mobile authentication requests. See
[`onegini.mobileAuth.push.on`](../reference/mobileAuthentication/push/on.md) for more details.

**Example code to handle push with fingerprint mobile authentication requests:**

```js
onegini.mobileAuth.push.on("fingerprint")
    .onPinRequest((actions, request) => {
      console.log("New mobile authentication request", request);

      // We assume that on fallback, the user still accepts the request
      // and provides a pin of "12346".
      let pin = "12346";

      actions.accept(pin);
    })
    .onFingerprintRequest((actions, request) => {
      console.log("New mobile authentication request", request);

      // Ask the user if they want to accept or deny the request. After
      // they accept, the OS will prompt them for a fingerprint scan. In
      // this example, the user accepts the request.
      let userAcceptedRequest = true;

      if (userAcceptedRequest) {
        actions.accept();
      } else {
        actions.deny();
      }
    })
    .onFingerprintCaptured(() => {
      // Only called on Android
      console.log("Fingerprint captured, waiting for verification.");
    })
    .onFingerprintFailed(() => {
      // Only called on Android
      console.log("Fingerprint failed! Please try again.");
    })
    .onSuccess(() => {
      alert("Mobile authentication request success!");
    })
    .onError((err) => {
      alert("Mobile authentication request failed!\n\n" + err.description);
    })
```

The details of these handler methods are exactly the same as explained in the fingerprint authentication topic guide linked above. In particular,
`onFingerprintCaptured` and `onFingerprintFailed` are only available on Android devices, due to the more restrictive nature of Touch Id for iOS.
Additionally, care must be taken to implement `onPinRequest`, as the Onegini Cordova plugin will perform a fallback to pin in the case of multiple failed
fingerprint requests.
