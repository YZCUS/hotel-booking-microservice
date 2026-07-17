package com.hotel.user.util;

import com.hotel.user.config.InternalServiceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalServiceUtilTest {

    private InternalServiceUtil internalServiceUtil;

    @BeforeEach
    void setUp() {
        InternalServiceConfig config = new InternalServiceConfig();
        ReflectionTestUtils.setField(config, "serviceSecret", "test-shared-secret");
        ReflectionTestUtils.setField(config, "allowedServices", new String[]{"api-gateway"});
        ReflectionTestUtils.setField(config, "serviceHeader", "X-Internal-Service");
        ReflectionTestUtils.setField(config, "tokenHeader", "X-Internal-Token");
        internalServiceUtil = new InternalServiceUtil(config);
    }

    @Test
    void matchesSharedInternalTokenTestVector() {
        String token = internalServiceUtil.generateToken("api-gateway", 30_000_000L);

        assertEquals("1FBydQLsmqYxAv7qEc1acIez5Pdmc/37", token);
    }

    @Test
    void acceptsCurrentGatewayToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service", "api-gateway");
        request.addHeader("X-Internal-Token", internalServiceUtil.generateCurrentToken("api-gateway"));

        assertTrue(internalServiceUtil.validateInternalServiceCall(request));
    }
}
