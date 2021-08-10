package com.exadel.aexlogs;

import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author eabramovich
 */
public class TestMain {

    @Test
    public void testMain() {

        /* Test openRequest()
         */
        LogLine ll = new LogLine();
        ll.text = "2020-10-27 11:27:40,234 " +
            "INFO [com.exadel.appery.mobilesrv.api.security.filter.ApiKeyRequestFilter] " +
            "(default task-1) c5f91a2b-a302-483d-8c35-7e4cca67b47e " +
            "Starting request for project ab0ce6d1-8d24-4c39-b6e7-f9be307dd4bb";

        HashMap<String, RequestLine> reqLines = new HashMap<>();
        LineProcessor lp = new LineProcessor(reqLines);
        lp.openRequest(ll);
        assertEquals(reqLines.size(), 1);
        RequestLine req = reqLines.get("c5f91a2b-a302-483d-8c35-7e4cca67b47e");
        assertEquals(req.getId(), "c5f91a2b-a302-483d-8c35-7e4cca67b47e");
        assertEquals(req.getProjectId(), "ab0ce6d1-8d24-4c39-b6e7-f9be307dd4bb");

        /* Test Method extraction
         */
        ll.text = "2020-10-27 11:27:40,244 "
                + "INFO [com.exadel.appery.mobilesrv.api.runtime.ServiceRuntimeRestService] "
                + "(default task-1) c5f91a2b-a302-483d-8c35-7e4cca67b47e "
                + "Method: POST";
        lp.updateRequest(ll);
        assertEquals(reqLines.size(), 1);
        req = reqLines.get("c5f91a2b-a302-483d-8c35-7e4cca67b47e");
        assertEquals(req.getId(), "c5f91a2b-a302-483d-8c35-7e4cca67b47e");
        assertEquals(req.getMethod(), "POST");

        /* Test URL extraction
         */
        ll.text = "2020-10-27 11:27:40,244 "
                + "INFO [com.exadel.appery.mobilesrv.api.runtime.ServiceRuntimeRestService] "
                + "(default task-1) c5f91a2b-a302-483d-8c35-7e4cca67b47e "
                + "Url: https://northwell.dev.appery.io/apiexpress-api/security/login";
        lp.updateRequest(ll);
        assertEquals(reqLines.size(), 1);
        req = reqLines.get("c5f91a2b-a302-483d-8c35-7e4cca67b47e");
        assertEquals(req.getId(), "c5f91a2b-a302-483d-8c35-7e4cca67b47e");
        assertEquals(req.getUrl(), "https://northwell.dev.appery.io/apiexpress-api/security/login");

        /* Test Parameter extraction
         */
        ll.text = "2020-10-27 11:27:40,246 "
                + "INFO [com.exadel.appery.mobilesrv.api.runtime.ServiceRuntimeRestService] "
                + "(default task-1) c5f91a2b-a302-483d-8c35-7e4cca67b47e "
                + "Parameter: apiKey=ab0ce6d1-8d24-4c39-b6e7-f9be307dd4bb";
        lp.updateRequest(ll);
        assertEquals(reqLines.size(), 1);
        req = reqLines.get("c5f91a2b-a302-483d-8c35-7e4cca67b47e");
        assertEquals(req.getId(), "c5f91a2b-a302-483d-8c35-7e4cca67b47e");
        assertEquals(req.getParams().size(), 1);
        Param param = req.getParams().get(0);
        assertEquals(param.getName(), "apiKey");
        assertEquals(param.getValue(), "ab0ce6d1-8d24-4c39-b6e7-f9be307dd4bb");

        /* Test Body extraction
         */
        ll.text = "2020-10-27 11:27:40,248 "
                + "INFO [com.exadel.appery.mobilesrv.api.runtime.ServiceRuntimeRestService] "
                + "(default task-1) c5f91a2b-a302-483d-8c35-7e4cca67b47e ---- Start Body Request:";
        lp.updateRequest(ll);
        assertTrue(lp.bodyMode);

        ll.text = "{\"username\":\"s\",\"options\":{}}";
        lp.appendBody(ll);
        assertTrue(lp.bodyMode);

        ll.text = "---- End Body Request";
        lp.appendBody(ll);
        assertFalse(lp.bodyMode);
        assertEquals(req.getBody().toString(), "{\"username\":\"s\",\"options\":{}}");

        ll.text = "2020-10-27 11:27:40,256 "
                + "INFO [com.exadel.appery.mobilesrv.model.utils.LogHelper] "
                + "(default task-1) c5f91a2b-a302-483d-8c35-7e4cca67b47e "
                + "Request. Finished execution of endpoint logic. Time spent 20 millis";
        lp.closeRequest(ll);
        assertEquals(req.getMillis(), 20);
    }
}
