package org.overlord.dtgov.jbpm.util;


import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.overlord.sramp.governance.Governance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientWorkItemHandler implements WorkItemHandler {

    Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Constructor.
     */
    public HttpClientWorkItemHandler() {
    }

    /**
     * Calls an HTTP endpoint. The address of the endpoint should be set in the
     * parameter map passed into the workItem by the BPMN workflow. Both
     * this parameters 'Url' as well as the method 'Method' are required
     * parameters.
     */
    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {

        try {
            // extract required parameters
            String urlStr = (String) workItem.getParameter("Url");
            String method = (String) workItem.getParameter("Method");
            if (urlStr==null || method==null) {
                throw new Exception("Url and Method are required parameters");
            }
            urlStr = urlStr.toLowerCase();
            Map<String,Object> params = workItem.getParameters();

            // optional timeout config parameters, defaulted to 60 seconds
            Integer connectTimeout = (Integer) params.get("ConnectTimeout");
            if (connectTimeout==null) connectTimeout = 60000;
            Integer readTimeout = (Integer) params.get("ReadTimeout");
            if (readTimeout==null) readTimeout = 60000;

            // replace tokens in the urlStr, the replacement value of the token
            // should be set in the parameters Map
            for (String key : params.keySet()) {
                // break out if there are no (more) tokens in the urlStr
                if (! urlStr.contains("{")) break;
                // replace the token if it is referenced in the urlStr
                String variable = "{" + key.toLowerCase() + "}";
                if (urlStr.contains(variable)) {
                    String escapedVariable = "\\{" + key.toLowerCase() + "\\}";
                    String urlEncodedParam = URLEncoder.encode((String) params.get(key), "UTF-8").replaceAll("%2F","*2F");
                    urlStr = urlStr.replaceAll(escapedVariable, urlEncodedParam);
                }
            }
            if (urlStr.contains("{")) throw new Exception("Url contains more tokens, " +
            		"please check the workflow and pass in the correct parameters. Url=" + urlStr);

            // call http endpoint
            log.info("Calling " + method + " TO: " + urlStr );
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            addAuthorization(connection);
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                InputStream is = (InputStream) connection.getContent();
                String reply = IOUtils.toString(is);
                log.info("reply=" + reply);
            } else {
                workItem.getParameters().put("Status", "ERROR " + responseCode);
                workItem.getParameters().put("StatusMsg", "endpoint " + urlStr + " could not be reached");
                log.error("endpoint could not be reached");
            }

        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }

        // notify manager that work item has been completed
        manager.completeWorkItem(workItem.getId(), null);
    }


    /**
     * Adds Authorization config to the connection prior to the request
     * being sent to the server.
     * @param connection
     */
    private void addAuthorization(HttpURLConnection connection) {
    	Governance governance = new Governance();
    	String username = governance.getOverlordUser();
    	String password = governance.getOverlordPassword();
    	
        if (username != null && password != null) {
            String b64Auth = Base64.encodeBase64String((username + ":" + password).getBytes()).trim();
            connection.setRequestProperty("Authorization", "Basic " + b64Auth);
        } else {
            log.warn("No username (guvnor.usr) and/or password (guvnor.pwd) found in the jbpm console properties file.");
        }
    }


    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {

        // Do nothing, notifications cannot be aborted

    }


}
