package com.bankSimulate.application;

import com.bankSimulate.domain.acquirer.Acquirer;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.enums.Status;
import com.bankSimulate.domain.enums.TerminalPurpose;
import com.bankSimulate.domain.merchant.Merchant;
import com.bankSimulate.domain.terminal.Terminal;
import com.bankSimulate.domain.terminal.TerminalPaymentMethod;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import com.bankSimulate.infrastructure.persistence.AcquirerRepository;
import com.bankSimulate.infrastructure.persistence.TerminalPaymentMethodRepository;
import com.bankSimulate.infrastructure.persistence.TerminalRepository;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import com.bankSimulate.infrastructure.web.dto.ChannelDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(ChannelService.class);

    private final TerminalRepository terminals;
    private final TerminalPaymentMethodRepository methods;
    private final AcquirerRepository acquirers;
    private final TerminalService terminalService;
    private final MerchantService merchantService;
    private final TerminalResolver resolver;
    private final String defaultChannel;
    private final String defaultCurrency;

    public ChannelService(TerminalRepository terminals, TerminalPaymentMethodRepository methods, AcquirerRepository acquirers,
                          TerminalService terminalService, MerchantService merchantService, TerminalResolver resolver,
                          @Value("${gateway.defaults.terminal.channel:WEB}") String defaultChannel,
                          @Value("${gateway.defaults.terminal.currency:VND}") String defaultCurrency) {
        this.terminals = terminals;
        this.methods = methods;
        this.acquirers = acquirers;
        this.terminalService = terminalService;
        this.merchantService = merchantService;
        this.resolver = resolver;
        this.defaultChannel = defaultChannel;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional(readOnly = true)
    public ChannelDtos.ChannelsResponse view(Merchant merchant) {
        return response(merchant);
    }

    @Transactional(readOnly = true)
    public List<String> customerMethods(Merchant merchant) {
        return List.copyOf(resolver.customerMethods(merchant));
    }

    @Transactional
    public ChannelDtos.ChannelsResponse open(Merchant merchant, ChannelDtos.OpenChannelRequest request) {
        Account account = requireAccount(request.accountName(), request.accountNumber());
        EnumSet<PaymentMethod> wanted = parseMethods(request.paymentMethods());
        Acquirer bank = selectableBank(request.bankCode());
        requireSupported(bank, wanted);
        List<Terminal> active = activeChannels(merchant);
        if (active.stream().anyMatch(t -> bank.getId().equals(t.getAcquirerId())))
            throw new ApiException(409, "BANK_ALREADY_A_CHANNEL",
                    bank.getName() + " đã là một kênh nhận tiền của bạn. Sửa kênh đó thay vì thêm mới.");
        requireMethodsFree(wanted, active, null);

        AdminDtos.SettlementAccountRequest settlement = settlementOf(bank, account);
        List<String> names = names(wanted);
        Terminal terminal = reusableTerminal(merchant, bank, active);
        boolean reused = terminal != null;
        if (reused) {
            terminalService.configure(terminal.getTerminalId(), new AdminDtos.TerminalConfigurationRequest(
                    names, threeDsFor(terminal, wanted), null, bank.getCode(), settlement));
        } else {
            AdminDtos.TerminalResponse created = terminalService.create(merchant.getMerNo(), new AdminDtos.CreateTerminalRequest(
                    "Kênh " + bank.getName(), defaultChannel, defaultCurrency, names,
                    wanted.contains(PaymentMethod.CARD) ? "OPTIONAL" : null, null, bank.getCode(), settlement));
            terminal = terminalService.requireTerminal(created.terminalId());
        }
        terminal.openAsChannel(Instant.now());
        terminals.saveAndFlush(terminal);
        syncMerchantSettlement(merchant);
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merchant.getMerNo(), terminal.getTerminalId(), null)) {
            log.info("Mở kênh {} methods={} tài khoản {}{}", bank.getCode(), names, mask(account.number()),
                    reused ? " (dùng lại terminal có sẵn)" : "");
        }
        return response(merchant);
    }

    @Transactional
    public ChannelDtos.ChannelsResponse update(Merchant merchant, String channelId, ChannelDtos.UpdateChannelRequest request) {
        List<Terminal> active = activeChannels(merchant);
        Terminal channel = find(active, channelId);
        Acquirer bank = acquirers.findById(channel.getAcquirerId())
                .filter(a -> a.getStatus() == Status.ACTIVE && a.getBankBin() != null)
                .orElseThrow(() -> new ApiException(409, "BANK_NO_LONGER_AVAILABLE",
                        "Ngân hàng của kênh này không còn trên cổng thanh toán. Thêm kênh ở ngân hàng khác rồi xóa kênh này."));
        EnumSet<PaymentMethod> wanted = parseMethods(request.paymentMethods());
        requireSupported(bank, wanted);
        requireMethodsFree(wanted, active, channel);
        boolean typed = notBlank(request.accountName()) || notBlank(request.accountNumber());
        Account account = typed ? requireAccount(request.accountName(), request.accountNumber()) : null;

        terminalService.configure(channel.getTerminalId(), new AdminDtos.TerminalConfigurationRequest(
                names(wanted), threeDsFor(channel, wanted), null, bank.getCode(),
                account == null ? null : settlementOf(bank, account)));
        syncMerchantSettlement(merchant);
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merchant.getMerNo(), channel.getTerminalId(), null)) {
            log.info("Sửa kênh {} methods={} {}", bank.getCode(), names(wanted),
                    account == null ? "giữ tài khoản" : "tài khoản mới " + mask(account.number()));
        }
        return response(merchant);
    }

    @Transactional
    public ChannelDtos.ChannelsResponse remove(Merchant merchant, String channelId) {
        List<Terminal> active = activeChannels(merchant);
        Terminal channel = find(active, channelId);
        if (active.size() == 1)
            throw new ApiException(409, "LAST_PAYMENT_CHANNEL",
                    "Phải còn ít nhất một kênh nhận tiền. Thêm kênh khác trước rồi mới xóa kênh này.");
        channel.retire();
        terminals.saveAndFlush(channel);
        syncMerchantSettlement(merchant);
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merchant.getMerNo(), channel.getTerminalId(), null)) {
            log.info("Xóa kênh (terminal giữ nguyên ACTIVE cho đơn cũ)");
        }
        return response(merchant);
    }


    private Terminal reusableTerminal(Merchant merchant, Acquirer bank, List<Terminal> active) {
        List<Terminal> all = terminals.findAllByMerchantId(merchant.getId());
        Terminal retired = all.stream()
                .filter(t -> t.isChannel() && t.isRetired() && bank.getId().equals(t.getAcquirerId()))
                .max(Comparator.comparing(Terminal::getChannelOpenedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (retired != null) return retired;
        if (!active.isEmpty()) return null;
        return all.stream()
                .filter(t -> t.getPurpose() == TerminalPurpose.DEFAULT && t.getStatus() == Status.ACTIVE)
                .findFirst().orElse(null);
    }


    private Acquirer selectableBank(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        return acquirers.findByCode(code)
                .filter(a -> a.getStatus() == Status.ACTIVE && a.getBankBin() != null)
                .orElseThrow(() -> new ApiException(400, "UNKNOWN_BANK",
                        "Ngân hàng này không có trong danh sách của cổng thanh toán"));
    }

    private static void requireSupported(Acquirer bank, EnumSet<PaymentMethod> wanted) {
        List<String> unsupported = wanted.stream().filter(m -> !bank.getPaymentMethods().contains(m)).map(Enum::name).toList();
        if (!unsupported.isEmpty())
            throw new ApiException(400, "PAYMENT_METHOD_NOT_SUPPORTED_BY_BANK",
                    bank.getName() + " không hỗ trợ " + String.join(", ", unsupported));
    }

    private void requireMethodsFree(EnumSet<PaymentMethod> wanted, List<Terminal> active, Terminal except) {
        for (Terminal other : active) {
            if (except != null && other.getId().equals(except.getId())) continue;
            List<String> taken = enabled(other).stream().filter(wanted::contains).map(Enum::name).toList();
            if (!taken.isEmpty())
                throw new ApiException(409, "PAYMENT_METHOD_IN_OTHER_CHANNEL",
                        String.join(", ", taken) + " đang nhận qua kênh " + bankName(other)
                                + ". Bỏ phương thức đó ở kênh kia trước.");
        }
    }

    private static EnumSet<PaymentMethod> parseMethods(List<String> raw) {
        List<String> clean = raw == null ? List.of() : raw.stream().filter(Objects::nonNull)
                .map(m -> m.trim().toUpperCase(Locale.ROOT)).filter(m -> !m.isEmpty()).distinct().toList();
        if (clean.isEmpty())
            throw new ApiException(400, "PAYMENT_METHODS_REQUIRED", "Chọn ít nhất một phương thức thanh toán");
        return ConfigParsers.methods(clean);
    }

    private record Account(String name, String number) {
    }

    private static Account requireAccount(String rawName, String rawNumber) {
        if (!notBlank(rawName) || !notBlank(rawNumber))
            throw new ApiException(400, "ACCOUNT_REQUIRED", "Nhập tên chủ tài khoản và số tài khoản");
        String number = rawNumber.replaceAll("\\s+", "");
        if (!number.matches("[0-9]{6,30}"))
            throw new ApiException(400, "INVALID_ACCOUNT_NUMBER", "Số tài khoản phải có 6–30 chữ số");
        return new Account(rawName.trim(), number);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String threeDsFor(Terminal t, EnumSet<PaymentMethod> wanted) {
        if (!wanted.contains(PaymentMethod.CARD)) return null;
        return t.getThreeDsPolicy() == null ? "OPTIONAL" : t.getThreeDsPolicy().name();
    }

    private static AdminDtos.SettlementAccountRequest settlementOf(Acquirer bank, Account account) {
        return new AdminDtos.SettlementAccountRequest(bank.getBankBin(), account.number(), account.name());
    }


    private List<Terminal> activeChannels(Merchant merchant) {
        return terminals.findAllByMerchantId(merchant.getId()).stream()
                .filter(t -> t.isChannel() && !t.isRetired())
                .sorted(Comparator.comparing(Terminal::getChannelOpenedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Terminal::getCreatedAt))
                .toList();
    }

    private static Terminal find(List<Terminal> active, String channelId) {
        return active.stream().filter(t -> t.getId().toString().equals(channelId)).findFirst()
                .orElseThrow(() -> new ApiException(404, "PAYMENT_CHANNEL_NOT_FOUND", "Không tìm thấy kênh nhận tiền này"));
    }

    private EnumSet<PaymentMethod> enabled(Terminal t) {
        EnumSet<PaymentMethod> set = EnumSet.noneOf(PaymentMethod.class);
        methods.findAllByTerminalId(t.getId()).stream().map(TerminalPaymentMethod::getPaymentMethod).forEach(set::add);
        return set;
    }

    private String bankName(Terminal t) {
        return t.getAcquirerId() == null ? "?" : acquirers.findById(t.getAcquirerId()).map(Acquirer::getName).orElse("?");
    }

    private void syncMerchantSettlement(Merchant merchant) {
        activeChannels(merchant).stream().filter(Terminal::hasSettlementAccount).findFirst().ifPresent(primary ->
                merchantService.updateSettlement(merchant.getMerNo(), new AdminDtos.SettlementAccountRequest(
                        primary.getSettlementBankBin(), primary.getSettlementAccountNumber(), primary.getSettlementAccountName())));
    }

    private ChannelDtos.ChannelsResponse response(Merchant merchant) {
        List<Terminal> active = activeChannels(merchant);
        List<ChannelDtos.ChannelResponse> channels = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            Terminal t = active.get(i);
            Acquirer bank = t.getAcquirerId() == null ? null : acquirers.findById(t.getAcquirerId()).orElse(null);
            channels.add(new ChannelDtos.ChannelResponse(t.getId().toString(),
                    bank == null ? null : bank.getCode(), bank == null ? "?" : bank.getName(),
                    bank == null ? null : bank.getBankBin(),
                    names(enabled(t)), names(terminalService.routableMethods(t, merchant)),
                    t.getSettlementAccountName(), t.hasSettlementAccount() ? mask(t.getSettlementAccountNumber()) : null,
                    i == 0, t.getChannelOpenedAt(), t.getStatus().name()));
        }
        List<ChannelDtos.BankOption> banks = acquirers.findAll().stream()
                .filter(a -> a.getStatus() == Status.ACTIVE && a.getBankBin() != null)
                .sorted(Comparator.comparing(Acquirer::getCode))
                .map(a -> new ChannelDtos.BankOption(a.getCode(), a.getName(), a.getBankBin(),
                        names(a.getPaymentMethods()), a.isThreeDsSupported()))
                .toList();
        return new ChannelDtos.ChannelsResponse(channels, banks, List.copyOf(resolver.customerMethods(merchant)));
    }

    private static List<String> names(java.util.Collection<PaymentMethod> values) {
        return java.util.Arrays.stream(PaymentMethod.values()).filter(values::contains).map(Enum::name).toList();
    }

    private static String mask(String number) {
        if (number == null) return null;
        return "*".repeat(Math.max(0, number.length() - 4)) + number.substring(Math.max(0, number.length() - 4));
    }
}
