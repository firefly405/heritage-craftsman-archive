package com.qizixuan.heritage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizixuan.heritage.entity.HeritageAuction;
import com.qizixuan.heritage.service.AuctionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlipaySandboxControllerTests {
    @Test
    void signsSortedRawParametersWithRsa2() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        var params = Map.of("version", "1.0", "biz_content", "{\"subject\":\"剪纸作品\"}", "app_id", "123456");
        String signature = AlipaySandboxController.sign(params, privateKey);

        var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update("app_id=123456&biz_content={\"subject\":\"剪纸作品\"}&version=1.0"
                .getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(signature)));
    }

    @Test
    void preparesSandboxPageWithLocalReturn() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();
        var auction = new HeritageAuction();
        auction.setId(7L);
        auction.setTitle("剪纸长卷");
        auction.setCurrentPrice(new BigDecimal("6900.00"));
        var service = mock(AuctionService.class);
        when(service.payableByCurrentUser(7L)).thenReturn(auction);
        var controller = new AlipaySandboxController(service, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "enabled", true);
        ReflectionTestUtils.setField(controller, "appId", "2021000123456789");
        ReflectionTestUtils.setField(controller, "privateKey",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        ReflectionTestUtils.setField(controller, "privateKeyFile", "");
        ReflectionTestUtils.setField(controller, "configuredReturnUrl", "");
        ReflectionTestUtils.setField(controller, "gateway", "https://openapi-sandbox.dl.alipaydev.com/gateway.do?charset=utf-8");
        var request = new MockHttpServletRequest();
        request.setServerPort(8081);

        String html = controller.createPage(7L, request).data();
        assertTrue(html.contains("alipay.trade.page.pay"));
        assertTrue(html.contains("6900.00"));
        assertTrue(html.contains("2021000123456789"));
        assertTrue(html.contains("action=\"https://openapi-sandbox.dl.alipaydev.com/gateway.do?charset=utf-8\""));
        assertTrue(html.contains("http://127.0.0.1:8081/alipay/sandbox/return/7"));
        assertTrue(html.contains("name=\"sign\""));

        var response = new MockHttpServletResponse();
        controller.returnToApp(7L, response);
        assertTrue(response.getRedirectedUrl().equals("/auctions?alipay_demo=success&auction_id=7"));
    }
}
