/*
 *
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.identity.mgt.endpoint.client;

import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.carbon.identity.mgt.endpoint.IdentityManagementEndpointConstants;
import org.wso2.carbon.identity.mgt.endpoint.IdentityManagementServiceUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Client which invokes consent mgt remote operations.
 */
public class ConsentMgtClient {

    private static final String CLIENT = "Client ";
    private final Log log = LogFactory.getLog(ConsentMgtClient.class);
    private static String BASE_PATH = IdentityManagementServiceUtil.getInstance().getServiceContextURL()
            .replace(IdentityManagementEndpointConstants.UserInfoRecovery.SERVICE_CONTEXT_URL_DOMAIN,
                    "api/identity/consent-mgt/v1.0");
    private static final String PURPOSE_ID = "purposeId";
    private static final String PURPOSES_ENDPOINT = BASE_PATH + "/consents/purposes";
    private static final String PURPOSE_ENDPOINT = BASE_PATH + "/consents/purposes";
    private static final String PURPOSES = "purposes";
    private static final String PURPOSE = "purpose";
    private static final String PII_CATEGORIES = "piiCategories";
    private static final String DEFAULT = "DEFAULT";
    private static final String NAME = "name";

    /**
     * Returns a JSON which contains a set of purposes with piiCategories
     *
     * @param tenantDomain Tenant Domain.
     * @return A JSON string which contains purposes.
     * @throws ConsentMgtClientException ConsentMgtClientException
     */
    public String getPurposes(String tenantDomain) throws ConsentMgtClientException {

        String purposesJsonString = "";
        try {
            String purposesResponse = executeGet(PURPOSES_ENDPOINT);
            JSONArray purposes = new JSONArray(purposesResponse);
            JSONArray purposesResponseArray = new JSONArray();

            for (int purposeIndex = 0; purposeIndex < purposes.length(); purposeIndex++) {
                JSONObject purpose = (JSONObject) purposes.get(purposeIndex);
                if (!isDefaultPurpose(purpose)) {
                    purpose = retrievePurpose(purpose.getInt(PURPOSE_ID));
                    if (hasPIICategories(purpose)) {
                        purposesResponseArray.put(purpose);
                    }
                }
            }
            if (purposesResponse.length() != 0) {
                JSONObject purposesJson = new JSONObject();
                purposesJson.put(PURPOSES, purposesResponseArray);
                purposesJsonString = purposesJson.toString();
            }
            return purposesJsonString;
        } catch (IOException e) {
            throw new ConsentMgtClientException("Error while retrieving purposes", e);
        }
    }

    private String executeGet(String url) throws ConsentMgtClientException, IOException {

        boolean isDebugEnabled = log.isDebugEnabled();
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().useSystemProperties().build()) {

            HttpGet httpGet = new HttpGet(url);
            setAuthorizationHeader(httpGet);

            try (CloseableHttpResponse response = httpclient.execute(httpGet)) {

                if (isDebugEnabled) {
                    log.debug("HTTP status " + response.getStatusLine().getStatusCode());
                }
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                    String inputLine;
                    StringBuilder responseString = new StringBuilder();

                    while ((inputLine = reader.readLine()) != null) {
                        responseString.append(inputLine);
                    }
                    return responseString.toString();
                } else {
                    throw new ConsentMgtClientException("Error while retrieving data from " + url + ". Found http " +
                            "status " + response.getStatusLine());
                }
            } finally {
                httpGet.releaseConnection();
            }
        }
    }

    /**
     * adding OAuth authorization headers to a httpMethod
     *
     * @param httpMethod method which wants to add Authorization header
     */
    private void setAuthorizationHeader(HttpRequestBase httpMethod) {

        String toEncode = IdentityManagementServiceUtil.getInstance().getAppName() + ":"
                + String.valueOf(IdentityManagementServiceUtil.getInstance().getAppPassword());
        byte[] encoding = Base64.encodeBase64(toEncode.getBytes());
        String authHeader = new String(encoding, Charset.defaultCharset());
        httpMethod.addHeader(HTTPConstants.HEADER_AUTHORIZATION,
                CLIENT + authHeader);

    }

    private JSONObject retrievePurpose(int purposeId) throws ConsentMgtClientException, IOException {

        String purposeResponse = executeGet(PURPOSE_ENDPOINT + "/" + purposeId);
        JSONObject purpose = new JSONObject(purposeResponse);
        return purpose;
    }

    private boolean isDefaultPurpose(JSONObject purpose) {

        if (DEFAULT.equalsIgnoreCase(purpose.getString(PURPOSE))) {
            return true;
        }
        return false;
    }

    private boolean hasPIICategories(JSONObject purpose) {

        JSONArray piiCategories = (JSONArray) purpose.get(PII_CATEGORIES);
        return piiCategories.length() > 0;
    }
}
