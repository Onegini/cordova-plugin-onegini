/*
 * Copyright (c) 2017-2019 Onegini B.V.
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

const fs = require('fs');
const path = require('path');
const process = require('process');
const crypto = require('crypto');
const url = require('url');
const https = require('https');
const execSync = require('child_process').execSync;
const debug = require('debug')('resolve_dependencies');

const pluginId = 'cordova-plugin-onegini';
const extractedConfigPlugin = 'cordova-plugin-onegini-extracted-config';

const envVariables = {
  artifactoryUser: 'ARTIFACTORY_USER',
  artifactoryPassword: 'ARTIFACTORY_PASSWORD',
  stripSimulatorArchitectures: 'ONEGINI_STRIP_SIMULATOR_ARCHITECTURES',
  sdkDownloadPath: 'ONEGINI_SDK_DOWNLOAD_PATH'
};

const sdkVersion = '9.6.0';

const baseArtifactoryUrl = `https://repo.onegini.com/artifactory/onegini-sdk/com/onegini/mobile/sdk/ios/libOneginiSDKiOS/${sdkVersion}`;
const libOneginiSdkIos = `${baseArtifactoryUrl}/OneginiSDKiOS-${sdkVersion}.tar.gz`;

const libName = libOneginiSdkIos.substring(libOneginiSdkIos.lastIndexOf('/') + 1);

const iosSdkPathCordova = 'src/ios/OneginiSDKiOS';
const iosSdkLibPathCordova = path.join(iosSdkPathCordova, 'OneginiSDKiOS.framework/OneginiSDKiOS');
const iosSdkHeadersPathCordova = path.join(iosSdkPathCordova, 'Headers');
const cryptoLibPathCordova = path.join(iosSdkPathCordova, 'OneginiCrypto.framework');

let sdkDownloadPath;

module.exports = function (context) {
  const platform = context.opts.plugin.platform;

  // We only want to invoke the plugin for the iOS platform since it doesn't make any sense to resolve the iOS SDK dependencies when
  // you only have the Android platform installed.
  if (platform !== 'ios') {
    return;
  }

  fetchSdkDownloadPath(context);
  prepareSdkDirectories(context);
  const artifactoryCredentials = getArtifactoryCredentials(context);

  writeToStdOut(`${pluginId}: Resolving Onegini iOS SDK dependencies...`);

  // Downloading & verifying the SDK lib
  return checkSdkLibExistsOnFs()
    .then(result => downloadFile(artifactoryCredentials, result, libOneginiSdkIos))
    .then(() => checkDownloadedFileIntegrity(artifactoryCredentials, libOneginiSdkIos))
    .then(() => unzipSDK(context))
    .then(() => stripSimulatorArchitectures(context))
    .then(() => writeToStdOut('Success!\n'));
};

function fetchSdkDownloadPath(context) {
  const pluginDir = context.opts.plugin.pluginInfo.dir;
  const sdkDownloadPathVar = envVariables.sdkDownloadPath;
  const sdkDownloadPathFromEnv = process.env[sdkDownloadPathVar];

  if (sdkDownloadPathFromEnv) {
    sdkDownloadPath = path.join(sdkDownloadPathFromEnv, 'ios-sdk');
    log(`Downloading the Onegini iOS SDK to: '${sdkDownloadPath}' set in '${sdkDownloadPathVar}'`);
  }
  else {
    sdkDownloadPath = path.join(pluginDir, 'ios-sdk');
    log(`Downloading the Onegini iOS SDK to: '${sdkDownloadPath}'`);
  }
}

function checkSdkLibExistsOnFs() {
  return new Promise(resolve => {
    writeToStdOut('.');
    const libFilePath = path.join(sdkDownloadPath, libName);
    if (fs.existsSync(libFilePath)) {
      debug('SDK lib is already downloaded');
      resolve(true);
    }
    else {
      debug('SDK lib is not downloaded yet');
      resolve(false);
    }
  });
}

function calculateSha256(filepath) {
  return new Promise(resolve => {
    debug(`Generating sha256 from: ${filepath}`);
    const shasum = crypto.createHash('sha256');

    let readStream = fs.createReadStream(filepath);
    readStream.on('data', (chunk) => {
      shasum.update(chunk);
    });
    readStream.on('end', () => {
      let hash = shasum.digest('hex');
      debug(`hashing done: ${hash}`);
      resolve(hash);
    })
  });
}

function downloadFile(artifactoryCredentials, fileExists, fileUrl) {
  return new Promise((resolve, reject) => {
    writeToStdOut('.');
    if (!artifactoryCredentials.artifactoryUser || !artifactoryCredentials.artifactoryPassword) {
      reject('You must set the "ARTIFACTORY_USER" and "ARTIFACTORY_PASSWORD" environment variables in order to resolve the iOS SDK dependency.');
      return;
    }
    const filename = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);

    // Check if the file was already downloaded previously.
    if (fileExists) {
      resolve();
      return;
    }

    debug(`Downloading: ${filename}`);
    const auth = Buffer.from(`${artifactoryCredentials.artifactoryUser}:${artifactoryCredentials.artifactoryPassword}`).toString();
    const filePath = path.join(sdkDownloadPath, filename);
    const file = fs.createWriteStream(filePath);
    const options = {
      protocol: url.parse(fileUrl).protocol,
      host: url.parse(fileUrl).host,
      path: url.parse(fileUrl).path,
      auth: auth
    };
    https.get(options, (response) => {
      if (response.statusCode === 200) {
        response.pipe(file);
        file.on('finish', function () {
          file.close();
          debug(`File downloaded: ${filename}`);
          resolve();
        });
      }
      else if (response.statusCode === 404) {
        fs.unlinkSync(filePath);
        reject(`${filename} not found in Onegini Artifactory repository`)
      }
      else if (response.statusCode === 401) {
        fs.unlinkSync(filePath);
        reject(`${username} is unauthorized`)
      }
      else {
        fs.unlinkSync(filePath);
        reject(`Downloading ${filename} went wrong: ${response.statusCode}`);
      }

    }).on('error', function (err) {
      fs.unlinkSync(filePath);
      reject(`Downloading ${filename} went wrong: ${err}`);
    });
  });
}

function checkDownloadedFileIntegrity(artifactoryCredentials, fileUrl) {
  writeToStdOut('.');
  const filename = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
  const downloadedFilePath = path.join(sdkDownloadPath, filename);
  const downloadedSha256FilePath = path.join(sdkDownloadPath, `${filename}.sha256`);
  const sha256FileUrl = `${fileUrl}.sha256`;
  return new Promise((resolve, reject) => {
    downloadFile(artifactoryCredentials, false, sha256FileUrl)
      .then(() => calculateSha256(downloadedFilePath))
      .then(calculatedHash => {
        const downloadedHash = fs.readFileSync(downloadedSha256FilePath).toString();
        debug(`Comparing hashes:`);
        debug(`downloaded hash: ${downloadedHash}`);
        debug(`calculated hash: ${calculatedHash}`);

        if (downloadedHash === calculatedHash) {
          debug('Hashes match!');
          resolve();
        }
        else {
          reject(`The file (${downloadedFilePath}) is damaged. Please remove and add the Onegini Cordova plugin and clean the download directory (if you have specified the ${envVariables.sdkDownloadPath}).`);
        }
      })
      .catch((err) => {
        reject(err)
      });
  });
}

function prepareSdkDirectories(context) {
  const pluginDir = context.opts.plugin.pluginInfo.dir;
  const sdkDir = path.join(pluginDir, iosSdkPathCordova);
  const legacyHeadersPathCordova = path.join(iosSdkPathCordova, 'Headers');
  const headersDir = path.join(pluginDir, iosSdkHeadersPathCordova);

  if (!fs.existsSync(sdkDir)) {
    debug(`Create SDK directory: ${sdkDir}`);
    fs.mkdirSync(sdkDir);
  }

  if (!fs.existsSync(sdkDownloadPath)) {
    debug(`Create SDK download : ${sdkDownloadPath}`);
    fs.mkdirSync(sdkDownloadPath);
  }
  var directoriesToClean = [sdkDir];
  if (fs.existsSync(legacyHeadersPathCordova)) {
  	directoriesToClean.push(legacyHeadersPathCordova)
  }
  deleteFilesFromDirs(directoriesToClean);
}

function deleteFilesFromDirs(directories) {
  directories.forEach(dir => {
    fs.readdir(dir, (err, files) => {
      if (err) {
        console.error(err);
        console.error(`An error occurred while reading files from directory: ${dir}`)
      }

      for (const file of files) {
        if (file.endsWith('.a') || file.endsWith('.h') || file.endsWith('.framework')) {
          fs.unlink(path.join(dir, file), err => {
            if (err) {
              console.error(err);
              console.error(`An error occurred while deleting files from directory: ${dir}`)
            }
          });
        }
      }
    });
  });
}

function unzipSDK(context) {
  return new Promise((resolve) => {
    writeToStdOut('.');
    const pluginDir = context.opts.plugin.pluginInfo.dir;
    const newDir = path.join(pluginDir, iosSdkPathCordova);

    debug('Unzipping SDK to ' + newDir);
    execSync('tar -xf' + sdkDownloadPath + '/' + libName + ' -C ' + newDir);
    resolve();
  });
}

function stripSimulatorArchitectures(context) {
  return new Promise((resolve) => {
    writeToStdOut('.');

    const shouldStrip = getStripSimulatorArchitectures(context);
    if (shouldStrip !== true) {
      debug('Skipping stripping simulator architectures.');
      resolve();
      return;
    }

    const pluginDir = context.opts.plugin.pluginInfo.dir;
    const sdkLibPath = path.join(pluginDir, iosSdkLibPathCordova);

    debug("Stripping simulator architectures from: " + sdkLibPath);
    execSync('lipo -remove x86_64 ' + sdkLibPath + ' -o ' + sdkLibPath);
    execSync('lipo -remove i386 ' + sdkLibPath + ' -o ' + sdkLibPath);
    debug("Successfully stripped simulator architectures.");
    resolve();
  });
}

function getStripSimulatorArchitectures(context) {
  const shouldStrip = process.env[envVariables.stripSimulatorArchitectures] === 'true';
  if (shouldStrip) {
    return shouldStrip
  } else {
    return getStripSimulatorArchitecturesFromProperties(context)
  }
}

function getStripSimulatorArchitecturesFromProperties(context) {
  const filePath = `${context.opts.projectRoot}/plugins/${extractedConfigPlugin}/plugin.properties`;
  const hasExtractedConfig = hasExtractedConfigFiles(context);
  if (hasExtractedConfig && fs.existsSync(filePath)) {
    debug('Reading plugin.properties file');
    var content = fs.readFileSync(filePath, 'utf8');
    return parseStripSimulatorArchitectures(content);
  }
  return false;
}

function parseStripSimulatorArchitectures(fileContent) {
  var shouldStrip = false;
  ('' + fileContent).split(/[\r\n]+/)
    .map((x) => x.trim())
    .filter((x) => Boolean(x))
    .forEach(function(item, index) {
      const result = item.match(/stripSimulatorArchitectures=(.*)/i)
      if (result && result.length == 2) {
        shouldStrip = (result[1] === 'true');
        return;
      }
    })
  return shouldStrip;
}

function getArtifactoryCredentials(context) {
  const credentials = getArtifactoryCredentialsFromEnv();
  if (credentials.artifactoryUser && credentials.artifactoryPassword) {
    return credentials;
  } else {
    return getArtifactoryCredentialsFromGradleProperties(context);
  }
}

function log(line) {
  console.log(`${pluginId}: ${line}`)
}

function writeToStdOut(output) {
  process.stdout.write(output);
}

function hasExtractedConfigFiles(context) {
  return context.opts.cordova.plugins.indexOf(extractedConfigPlugin) > -1;
}

function getArtifactoryCredentialsFromEnv() {
  var username = process.env[envVariables.artifactoryUser];
  var password = process.env[envVariables.artifactoryPassword];

  if (username && password) {
    debug('Artifactory credentials found in env!');
    return {artifactoryUser: username, artifactoryPassword: password};
  }
  return new Object();
}

function getArtifactoryCredentialsFromGradleProperties(context) {
  const filePath = `${context.opts.projectRoot}/plugins/${extractedConfigPlugin}/artifactory.properties`;
  const hasExtractedConfig = hasExtractedConfigFiles(context);
  if (hasExtractedConfig && fs.existsSync(filePath)) {
    debug('Reading artifactory.properties file');
    var content = fs.readFileSync(filePath, 'utf8');
    return parseArtifactoryCredentials(content);
  }
  return new Object();
}

function parseArtifactoryCredentials(fileContent) {
  const artifactoryCredentials = new Object();
  ('' + fileContent).split(/[\r\n]+/)
    .map((x) => x.trim())
    .filter((x) => Boolean(x))
    .forEach(function(item, index) {
      const result = item.match(/artifactory(User|Password)=(.*)/i)
      if (result.length == 3) {
        const key = result[1];
        const value = result[2];

        if (key === 'User') {
          artifactoryCredentials.artifactoryUser = value;
        } else if (key === 'Password') {
          artifactoryCredentials.artifactoryPassword = value;
        }
      }
    })
  return artifactoryCredentials;
}
