package com.qizixuan.heritage.controller;

import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.entity.AuctionBid;
import com.qizixuan.heritage.entity.HeritageAuction;
import com.qizixuan.heritage.service.AuctionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {
    private final AuctionService service;

    public AuctionController(AuctionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<HeritageAuction>> list(@RequestParam(defaultValue = "") String keyword,
                                                   @RequestParam(defaultValue = "") String status) {
        return ApiResponse.ok(service.list(keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<HeritageAuction> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/bids")
    public ApiResponse<List<AuctionBid>> bids(@PathVariable Long id) {
        return ApiResponse.ok(service.bidHistory(id));
    }

    @PostMapping
    public ApiResponse<HeritageAuction> create(@RequestBody AuctionService.AuctionRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<HeritageAuction> update(@PathVariable Long id,
                                               @RequestBody AuctionService.AuctionRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<HeritageAuction> publish(@PathVariable Long id) {
        return ApiResponse.ok(service.publish(id));
    }

    @PostMapping("/{id}/bid")
    public ApiResponse<HeritageAuction> bid(@PathVariable Long id, @RequestBody BidRequest request) {
        return ApiResponse.ok(service.placeBid(id, request == null ? null : request.amount()));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<HeritageAuction> close(@PathVariable Long id) {
        return ApiResponse.ok(service.close(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<HeritageAuction> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }

    public record BidRequest(BigDecimal amount) {}
}
