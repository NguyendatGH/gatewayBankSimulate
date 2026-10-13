package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.ChannelService;
import com.bankSimulate.application.GatewayRuntimeAuth;
import com.bankSimulate.infrastructure.web.dto.ChannelDtos;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gateway")
public class MerchantChannelController {

    private final GatewayRuntimeAuth auth;
    private final ChannelService channels;

    public MerchantChannelController(GatewayRuntimeAuth auth, ChannelService channels) {
        this.auth = auth;
        this.channels = channels;
    }

    @GetMapping("/channels")
    public ChannelDtos.ChannelsResponse list(@RequestHeader("X-Merchant-No") String merNo,
                                             @RequestHeader("X-Merchant-Secret") String secret) {
        return channels.view(auth.authenticateMerchant(merNo, secret).merchant());
    }

    @PostMapping("/channels")
    public ChannelDtos.ChannelsResponse open(@RequestHeader("X-Merchant-No") String merNo,
                                             @RequestHeader("X-Merchant-Secret") String secret,
                                             @RequestBody ChannelDtos.OpenChannelRequest request) {
        return channels.open(auth.authenticateMerchant(merNo, secret).merchant(), request);
    }

    @PutMapping("/channels/{channelId}")
    public ChannelDtos.ChannelsResponse update(@RequestHeader("X-Merchant-No") String merNo,
                                               @RequestHeader("X-Merchant-Secret") String secret,
                                               @PathVariable String channelId,
                                               @RequestBody ChannelDtos.UpdateChannelRequest request) {
        return channels.update(auth.authenticateMerchant(merNo, secret).merchant(), channelId, request);
    }

    @DeleteMapping("/channels/{channelId}")
    public ChannelDtos.ChannelsResponse remove(@RequestHeader("X-Merchant-No") String merNo,
                                               @RequestHeader("X-Merchant-Secret") String secret,
                                               @PathVariable String channelId) {
        return channels.remove(auth.authenticateMerchant(merNo, secret).merchant(), channelId);
    }

    @GetMapping("/payment-methods")
    public ChannelDtos.PaymentMethodsResponse paymentMethods(@RequestHeader("X-Merchant-No") String merNo,
                                                             @RequestHeader("X-Merchant-Secret") String secret) {
        return new ChannelDtos.PaymentMethodsResponse(channels.customerMethods(auth.authenticateMerchant(merNo, secret).merchant()));
    }
}
