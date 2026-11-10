package com.qizixuan.heritage.controller;

import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.entity.HeritageAuction;
import com.qizixuan.heritage.service.AuctionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Minimal Alipay sandbox redirect for classroom/demo use only.
 * It deliberately does not verify callbacks or persist payment state.
 */
@RestController
public class AlipaySandboxController {
    private static final DateTimeFormatter ALIPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do?charset=utf-8";

    private final AuctionService auctions;
    private final ObjectMapper objectMapper;

    @Value("${alipay.sandbox.enabled:false}")
    private boolean enabled;
    @Value("${alipay.sandbox.gateway:https://openapi-sandbox.dl.alipaydev.com/gateway.do?charset=utf-8}")
    private String gateway;
    @Value("${alipay.sandbox.app-id:}")
    private String appId;
    @Value("${alipay.sandbox.private-key:}")
    private String privateKey;
    @Value("${alipay.sandbox.private-key-file:}")
    private String privateKeyFile;
    @Value("${alipay.sandbox.return-url:}")
    private String configuredReturnUrl;

    public AlipaySandboxController(AuctionService auctions, ObjectMapper objectMapper) {
        this.auctions = auctions;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/api/alipay/sandbox/page", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<String> createPage(@RequestParam Long auctionId, HttpServletRequest request) {
        if (!enabled) throw new BusinessException("支付宝沙箱未启用，请配置沙箱应用信息，或选择本地模拟支付");
        String signingKey = signingKey();
        if (isBlank(appId) || isBlank(signingKey))
            throw new BusinessException("支付宝沙箱参数未配置，请填写应用 ID 和私钥文件");
        HeritageAuction auction = auctions.payableByCurrentUser(auctionId);
        BigDecimal amount = auction.getCurrentPrice() == null ? auction.getStartPrice() : auction.getCurrentPrice();
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("订单金额无效");

        String outTradeNo = "DEMO-ALIPAY-" + auction.getId() + "-" + System.currentTimeMillis();
        String returnUrl = isBlank(configuredReturnUrl)
                ? localReturnUrl(request)
                : configuredReturnUrl;
        return ApiResponse.ok(buildAutoSubmitForm(outTradeNo, auction.getTitle(), amount, returnUrl,
                auction.getId(), signingKey));
    }

    @GetMapping("/alipay/sandbox/return/{auctionId}")
    public void returnToApp(@PathVariable Long auctionId, HttpServletResponse response) throws IOException {
        response.sendRedirect("/auctions?alipay_demo=success&auction_id=" + auctionId);
    }

    private String buildAutoSubmitForm(String outTradeNo, String subject, BigDecimal amount,
                                       String returnUrl, Long auctionId, String signingKey) {
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", appId.trim());
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(ALIPAY_TIME));
        params.put("version", "1.0");
        params.put("return_url", returnUrl + "/" + auctionId);
        try {
            params.put("biz_content", objectMapper.writeValueAsString(Map.of(
                    "out_trade_no", outTradeNo,
                    "product_code", "FAST_INSTANT_TRADE_PAY",
                    "total_amount", amount.setScale(2).toPlainString(),
                    "subject", subject == null ? "非遗作品" : subject)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法生成支付宝沙箱订单", ex);
        }
        params.put("sign", sign(params, signingKey));

        String fields = params.entrySet().stream()
                .map(entry -> "<input type=\"hidden\" name=\"" + html(entry.getKey())
                        + "\" value=\"" + html(entry.getValue()) + "\">")
                .collect(Collectors.joining());
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>正在跳转支付宝沙箱</title></head>"
                + "<body><p>正在跳转支付宝沙箱，请稍候...</p><form id=\"alipay\" method=\"post\" action=\""
                + html(isBlank(gateway) ? DEFAULT_GATEWAY : gateway.trim()) + "\">" + fields
                + "</form><script>document.getElementById('alipay').submit()</script></body></html>";
    }

    static String sign(Map<String, String> params, String signingKey) {
        String content = params.entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()) && !isBlank(entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey(signingKey));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("支付宝沙箱签名失败，请检查 RSA2 私钥格式", ex);
        }
    }

    private static PrivateKey parsePrivateKey(String value) throws Exception {
        String normalized = value.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    private String localReturnUrl(HttpServletRequest request) {
        return "http://127.0.0.1:" + request.getServerPort() + "/alipay/sandbox/return";
    }

    private String signingKey() {
        if (!isBlank(privateKeyFile)) {
            try {
                return Files.readString(Path.of(privateKeyFile.trim()), StandardCharsets.US_ASCII);
            } catch (IOException ex) {
                throw new IllegalStateException("无法读取支付宝沙箱私钥文件", ex);
            }
        }
        return privateKey;
    }

    private String html(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
