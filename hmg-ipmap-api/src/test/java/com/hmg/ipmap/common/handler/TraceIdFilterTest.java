package com.hmg.ipmap.common.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    static class RecordingFilterChain implements FilterChain {
        boolean proceeded = false;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            proceeded = true;
        }
    }

    static class CapturingMdcFilterChain implements FilterChain {
        final AtomicReference<String> mdcTraceIdDuringChain = new AtomicReference<>();
        boolean proceeded = false;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            // Capture current MDC value during filter chain execution
            mdcTraceIdDuringChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
            proceeded = true;
        }
    }

    static class ThrowingFilterChain implements FilterChain {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws ServletException {
            throw new ServletException("Downstream failed");
        }
    }

    @Test
    @DisplayName("Sets header and MDC; keeps header after chain and clears MDC finally")
    void setsHeaderAndMdc_keepsHeader_clearsMdcFinally() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingMdcFilterChain chain = new CapturingMdcFilterChain();

        // Execute
        filter.doFilter(request, response, chain);

        // Verify chain proceeded
        assertTrue(chain.proceeded, "Filter chain should proceed");

        // Header should be present
        String headerTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(headerTraceId, "Trace ID header must be set");
        assertFalse(headerTraceId.isBlank(), "Trace ID header must not be blank");

        // MDC should have been set during chain to the same value
        String mdcDuringChain = chain.mdcTraceIdDuringChain.get();
        assertNotNull(mdcDuringChain, "MDC trace_id should be set during chain");
        assertEquals(headerTraceId, mdcDuringChain, "MDC trace_id should equal header value");

        // After filter completes, MDC should be cleared
        assertNull(
                MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY),
                "MDC trace_id must be cleared after filter completes");
    }

    @Test
    @DisplayName("Re-applies original header value in finally if downstream modifies it")
    void reappliesOriginalHeaderIfDownstreamModifiesIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Chain that changes the header to a different value
        class ModifyingFilterChain implements FilterChain {
            String originalHeaderSeenInChain;
            boolean proceeded;

            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                MockHttpServletResponse resp = (MockHttpServletResponse) res;
                // Capture original header
                originalHeaderSeenInChain = resp.getHeader(TraceIdFilter.TRACE_ID_HEADER);
                proceeded = true;
                // Downstream overrides header (simulate misbehaving component)
                resp.setHeader(TraceIdFilter.TRACE_ID_HEADER, "DOWNSTREAM-OVERRIDE");
            }
        }

        ModifyingFilterChain chain = new ModifyingFilterChain();

        // Execute
        filter.doFilter(request, response, chain);

        assertTrue(chain.proceeded, "Filter chain should proceed");

        // Original header should be re-applied in finally
        String finalHeader = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(
                chain.originalHeaderSeenInChain, "Original header should be present during chain");
        assertEquals(
                chain.originalHeaderSeenInChain,
                finalHeader,
                "Filter must re-apply the original trace ID header in finally");

        // MDC cleared
        assertNull(
                MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY),
                "MDC should be cleared after filter completes");
    }

    @Test
    @DisplayName("Keeps header and clears MDC even if downstream throws")
    void keepsHeaderAndClearsMdc_whenDownstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ThrowingFilterChain chain = new ThrowingFilterChain();

        // Execute with exception expected
        try {
            filter.doFilter(request, response, chain);
            fail("Expected ServletException to be thrown by downstream");
        } catch (ServletException | IOException _) {
            // expected
        }

        String headerTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(headerTraceId, "Trace ID header must be set even if downstream throws");
        assertFalse(headerTraceId.isBlank(), "Trace ID header must not be blank");

        assertNull(
                MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY),
                "MDC trace_id must be cleared in finally even on exception");
    }

    @Test
    @DisplayName("Generates a non-blank trace ID on every request")
    void generatesNonBlankTraceId() throws Exception {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        RecordingFilterChain chain1 = new RecordingFilterChain();

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        RecordingFilterChain chain2 = new RecordingFilterChain();

        filter.doFilter(request1, response1, chain1);
        filter.doFilter(request2, response2, chain2);

        String id1 = response1.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        String id2 = response2.getHeader(TraceIdFilter.TRACE_ID_HEADER);

        assertNotNull(id1);
        assertNotNull(id2);
        assertFalse(id1.isBlank());
        assertFalse(id2.isBlank());
    }
}
