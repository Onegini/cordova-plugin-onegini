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

console.log("Running hook to install npm requirements for cordova-plugin-onegini");
module.exports = function (context) {
  return new Promise((resolve, reject) => {
    var child_process = require('child_process');
    child_process.exec('npm install', {cwd: __dirname}, function (error) {
      if (error !== null) {
        console.log('exec error: ' + error);
        reject('npm installation failed');
      }
      else {
        resolve();
      }
    });
  });
};