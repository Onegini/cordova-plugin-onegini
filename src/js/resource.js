/*
 * Copyright (c) 2017 Onegini B.V.
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

module.exports = (function (XMLHttpRequest, TextDecoder, CustomEvent) {
  const utils = require('./utils');
  const HEADER_LENGTH = 4;
  const nativeXhrProperties = [
    'onabort',
    'onerror',
    'onload',
    'onloadend',
    'onloadstart',
    'onprogress',
    'onreadystatechange',
    'ontimeout',
    'readyState',
    'response',
    'responseText',
    'responseType',
    'responseURL',
    'responseXML',
    'status',
    'statusText',
    'timeout',
    'upload',
    'XMLHttpRequestUpload',
    'withCredentials'
  ];

  const nativeXhrMethods = [
    'abort',
    'getAllResponseHeaders',
    'getResponseHeader',
    'overrideMimeType',
    'send',
    'setRequestHeader'
  ];

  const auth = {
    USER: Symbol('user'),
    ANONYMOUS: Symbol('anonymous'),
    IMPLICIT: Symbol('implicit'),
  };

  let resourceBaseUrl;

  if (!TextDecoder) {
    TextDecoder = require('text-encoding').TextDecoder;
  }

  try {
    const customEvent = new CustomEvent('test');
    customEvent.preventDefault();
    if (customEvent.defaultPrevented !== true) {
      throw new Error('Could not prevent default')
    }
  }
  catch (e) {
    CustomEvent = require('./custom-event-polyfill');
  }

  function fetch(options, successCb, failureCb) {
    let _successCb = successCb;
    let _failureCb = failureCb;

    options = parseOptions(options);

    function success(buffer) {
      _successCb(httpResponseFromArrayBuffer(buffer))
    }

    function failure(buffer) {
      if (buffer instanceof ArrayBuffer) {
        _failureCb({
          code: 8013,
          description: 'Onegini: HTTP Request failed. Check httpResponse for more info.',
          httpResponse: httpResponseFromArrayBuffer(buffer)
        });
      } else {
        _failureCb(buffer)
      }
    }

    utils.callbackExec('OneginiResourceClient', 'fetch', options, success, failure);

    if (successCb) {
      return;
    }

    return new Promise(function (resolve, reject) {
      _successCb = resolve;
      _failureCb = reject;
    });
  }

  function parseOptions(options) {
    if (options && options.anonymous) {
      console.warn("Warning: resource.fetch option 'anonymous' has been deprecated.\nInstead, set the 'auth' option to one of resource.fetch.auth.USER, ANONYMOUS or IMPLICIT");
      options.auth = options.anonymous ? auth.ANONYMOUS : auth.USER;
    }

    options = utils.getOptionsWithDefaults(options, {
      method: 'GET',
      headers: {},
      auth: auth.USER,
      anonymous: null,
    }, 'url');

    if (!options || !options.url) {
      throw new TypeError("Onegini: missing 'url' argument for fetch");
    }

    if (options.body && typeof options.body !== 'string') {
      try {
        options.body = JSON.stringify(options.body);
      } catch (e) {
        console.error(e);
        throw new TypeError('Onegini: resource.fetch: options.body could not be stringified. JSON.stringify is used to transform the body to a String.');
      }
    }

    if (!Object.values(auth).includes(options.auth)) {
      throw new TypeError('Onegini: resource.fetch: options.auth should be of one of resource.fetch.auth.USER, ANONYMOUS or IMPLICIT');
    }

    options.auth = options.auth.toString();

    return options;
  }

  function sliceBuffer(buffer) {
    buffer = ArrayBuffer.prototype.slice.call(buffer, [0, buffer.length]);
  }

  function httpResponseFromArrayBuffer(buffer) {
    sliceBuffer(buffer);
    const metaLength = new Int32Array(buffer.slice(0, HEADER_LENGTH))[0];
    const metadataBuffer = buffer.slice(HEADER_LENGTH, HEADER_LENGTH + metaLength);
    const metadata = new Uint8Array(metadataBuffer);
    const result = JSON.parse(String.fromCharCode.apply(null, metadata));

    Object.defineProperties(result, {
      'rawBody': {
        value: buffer.slice(HEADER_LENGTH + metaLength, buffer.byteLength)
      },
      'body': {
        get: function () {
          return new TextDecoder('utf-8').decode(new DataView(this.rawBody));
        }
      },
      'json': {
        get: function () {
          return JSON.parse(this.body);
        }
      }
    });

    return result;
  }

  function init(url) {
    window.XMLHttpRequest = OneginiXMLHttpRequest;
    resourceBaseUrl = url;
  }

  function disable() {
    window.XMLHttpRequest = XMLHttpRequest;
  }

  function OneginiXMLHttpRequest() {
    this._eventListeners = {};
    this.xhr = new XMLHttpRequest();
  }

  OneginiXMLHttpRequest.prototype.open = function (method, url) {
    if (url.substr(0, resourceBaseUrl.length) === resourceBaseUrl) {
      setupXhrProxy(this, method, url);
    }

    this.xhr.open.apply(this.xhr, arguments);
  };

  OneginiXMLHttpRequest.prototype.addEventListener = function (type, listener) {
    this._eventListeners[type] = this._eventListeners[type] || [];
    this._eventListeners[type].push(listener);

    this.xhr.addEventListener.apply(this.xhr, arguments);
  };

  OneginiXMLHttpRequest.prototype.removeEventListener = function (type, listener) {
    const listeners = this._eventListeners[type];

    for (let i = 0, l = listeners.length; i < l; i++) {
      if (listeners[i] === listener) {
        listeners.splice(i, 1);
        return this.removeEventListener(type, listener);
      }
    }

    this.xhr.removeEventListener.apply(this.xhr, arguments);
  };

  OneginiXMLHttpRequest.prototype.dispatchEvent = function (event) {
    const listeners = this._eventListeners[event.type];

    if (this['on' + event.type]) {
      this['on' + event.type].call(this);
    }

    if (listeners && listeners.length !== 0) {
      Object.defineProperty(event, 'target', this);
      for (let i = 0, l = listeners.length; i < l; i++) {
        listeners[i].call(this, event);
      }
    }

    if (this.onreadystatechange) {
      this.onreadystatechange();
    }
  };

  nativeXhrProperties.forEach(function (property) {
    linkXhrPropertyWithOneginiXhr(property);
  });

  nativeXhrMethods.forEach(function (method) {
    linkXhrMethodWithOneginiXhr(method);
  });

  function linkXhrPropertyWithOneginiXhr(key) {
    Object.defineProperty(OneginiXMLHttpRequest.prototype, key, {
      get: function () {
        return this.xhr[key];
      },
      set: function (value) {
        return this.xhr[key] = value;
      }
    });
  }

  function linkXhrMethodWithOneginiXhr(name) {
    Object.defineProperty(OneginiXMLHttpRequest.prototype, name, {
      value: function () {
        return this.xhr[name].apply(this.xhr, arguments);
      }
    });
  }

  function setupXhrProxy(xhr, method, url) {
    xhr._requestHeaders = {};
    xhr._responseHeaders = {};
    xhr._method = method;
    xhr._url = url;

    defineProperty(xhr, 'send', function (body) {
      fetch({
        method: xhr._method,
        url: xhr._url,
        headers: xhr._requestHeaders,
        body: body
      }, function (successResponse) {
        populateXhrWithFetchResponse(xhr, successResponse);
        xhr.dispatchEvent(new CustomEvent('load'));
      }, function (err) {
        populateXhrWithFetchResponse(xhr, err.httpResponse);
        xhr.dispatchEvent(new CustomEvent('error'));
      });
    });

    defineProperty(xhr, 'getAllResponseHeaders', function () {
      let headersString = '';

      for (let header in xhr._responseHeaders) {
        if (xhr._responseHeaders.hasOwnProperty(header)) {
          headersString += header + ': ' + xhr._responseHeaders[header] + '\n';
        }
      }

      return headersString;
    });

    defineProperty(xhr, 'getResponseHeader', function (header) {
      return xhr._responseHeaders[header];
    });

    defineProperty(xhr, 'setRequestHeader', function (header, value) {
      xhr._requestHeaders[header] = value;
    });
  }

  function populateXhrWithFetchResponse(xhr, result) {
    if (xhr.responseType === 'arrayBuffer') {
      defineProperty(xhr, 'response', result.rawBody);
    }
    else {
      defineProperty(xhr, 'response', result.body);
    }

    defineProperty(xhr, 'readyState', 4);
    defineProperty(xhr, 'responseText', result.body);
    defineProperty(xhr, 'status', result.status);
    defineProperty(xhr, 'statusText', result.statusText);
    xhr._responseHeaders = result.headers;
  }

  function defineProperty(object, property, value) {
    Object.defineProperty(object, property, {
      configurable: true,
      value: value
    });
  }

  return {
    fetch,
    init,
    disable,
    auth,
  };

})(window.XMLHttpRequest, window.TextDecoder, window.CustomEvent);
