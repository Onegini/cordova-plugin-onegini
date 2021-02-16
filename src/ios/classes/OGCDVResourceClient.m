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

#import "OGCDVResourceClient.h"
#import "OGCDVConstants.h"

NSString *const OGCDVPluginKeyBody = @"body";
NSString *const OGCDVPluginKeyUrl = @"url";
NSString *const OGCDVPluginKeyStatus = @"status";
NSString *const OGCDVPluginKeyStatusText = @"statusText";
NSString *const OGCDVPluginKeyHeaders = @"headers";
NSString *const OGCDVPluginKeyAuthMethod = @"auth";
NSString *const OGCDVPluginAuthMethodUser = @"Symbol(user)";
NSString *const OGCDVPluginAuthMethodAnonymous = @"Symbol(anonymous)";
NSString *const OGCDVPluginAuthMethodImplicit = @"Symbol(implicit)";
int const OGCDVFetchResultHeaderLength = 4;

@implementation OGCDVResourceClient {
}

- (void)fetch:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate runInBackground:^{
        NSDictionary *options = command.arguments[0];

        NSString *url = options[OGCDVPluginKeyUrl];
        NSString *method = options[OGCDVPluginKeyMethod];
        NSString *body = options[OGCDVPluginKeyBody];
        NSMutableDictionary *headers = options[OGCDVPluginKeyHeaders];
        NSString *authMethod = options[OGCDVPluginKeyAuthMethod];

        ONGRequestBuilder *requestBuilder = [ONGRequestBuilder builder];

        if (body && ![[NSNull null] isEqual:body]) {
            [requestBuilder setBody:[body dataUsingEncoding:NSUTF8StringEncoding]];
            if ([self isContentTypeNotSet:headers]) {
                headers[@"Content-Type"] = @"text/plain;charset=utf-8";
            }
        }

        NSDictionary *convertedHeaders = [self convertNumbersToStringsInDictionary:headers];
        [requestBuilder setHeaders:convertedHeaders];
        [requestBuilder setMethod:method];
        [requestBuilder setPath:url];

        ONGResourceRequest *request = [requestBuilder build];

        if (!authMethod) {
            [self sendErrorResultForCallbackId:command.callbackId
                                 withErrorCode:OGCDVPluginErrCodeIllegalArgument
                                    andMessage:OGCDVPluginErrDescriptionInvalidFetchAuthMethod];
            return;
        }

        void (^fetchCompletion)(ONGResourceResponse*, NSError*) = ^(ONGResourceResponse *response, NSError *error) {
            CDVPluginResult *pluginResult = [self getPluginResultFromResourceResponse:response withError:error];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        };

        if ([authMethod isEqualToString:OGCDVPluginAuthMethodUser]) {
            [[ONGUserClient sharedInstance] fetchResource:request completion:fetchCompletion];
        } else if ([authMethod isEqualToString:OGCDVPluginAuthMethodAnonymous]) {
            [[ONGDeviceClient sharedInstance] fetchResource:request completion:fetchCompletion];
        } else if ([authMethod isEqualToString:OGCDVPluginAuthMethodImplicit]) {
            [[ONGUserClient sharedInstance] fetchImplicitResource:request completion:fetchCompletion];
        }
    }];
}

- (BOOL)isContentTypeNotSet:(NSMutableDictionary *)headers {
    NSArray *keys = [headers allKeys];
    for (NSString *key in keys) {
        if ([@"content-type" compare:key options:NSCaseInsensitiveSearch] == NSOrderedSame) {
            return NO;
        }
    }
    return YES;
}

- (CDVPluginResult *)getPluginResultFromResourceResponse:(ONGResourceResponse *)response withError:(NSError *)error
{
    BOOL didReceiveHttpReplySuccess = response.statusCode >= 200 && response.statusCode <= 299;
    CDVCommandStatus status;
    if (didReceiveHttpReplySuccess) {
        status = CDVCommandStatus_OK;
    } else {
        status = CDVCommandStatus_ERROR;
    }

    NSDictionary *httpMetadataJSON = @{
        OGCDVPluginKeyStatus: @(response.statusCode),
        OGCDVPluginKeyStatusText: [self getStatusText:response.statusCode],
        OGCDVPluginKeyHeaders: response.allHeaderFields == nil ? @{} : response.allHeaderFields
    };

    NSError *jsonError;
    NSData *httpMetadata = [NSJSONSerialization dataWithJSONObject:httpMetadataJSON options:NSJSONWritingPrettyPrinted error:&jsonError];
    if (jsonError) {
        return [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:@{
            OGCDVPluginKeyErrorCode: @(OGCDVPluginErrCodeIoException),
            OGCDVPluginKeyErrorDescription: @"Could not parse HTTP response to JSON"
        }];
    }

    int32_t metadataLength = (int32_t)httpMetadata.length;
    NSMutableData *payloadData = [NSMutableData data];
    [payloadData appendBytes:&metadataLength length:OGCDVFetchResultHeaderLength];
    [payloadData appendData:httpMetadata];
    [payloadData appendData:response.data];

    return [CDVPluginResult resultWithStatus:status messageAsArrayBuffer:payloadData];
}

- (NSString *)getStatusText:(NSInteger)code
{
    return [NSHTTPURLResponse localizedStringForStatusCode:code];
}

- (NSDictionary *)convertNumbersToStringsInDictionary:(NSDictionary *)dictionary
{
    if (!dictionary) {
        return nil;
    }

    NSMutableDictionary *convertedDictionary = [NSMutableDictionary new];

    for (NSString *key in dictionary.allKeys) {
        id value = dictionary[key];
        [convertedDictionary setValue:([value isKindOfClass:[NSNumber class]] ? [value stringValue] : value) forKey:key];
    }

    return convertedDictionary;
}

@end
