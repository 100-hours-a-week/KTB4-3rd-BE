package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementSummaryReader {

    private final SettlementRepository settlementRepository;
    private final AccountNoCipher accountNoCipher;

    public Optional<MessageItem.Settlement> findByCompanionId(Long companionId) {
        return settlementRepository.findWithPayeeByCompanionId(companionId)
                .filter(settlement -> settlement.getTotalAmount() != null)
                .map(this::summarize);
    }

    public MessageItem.Settlement summarize(Settlement settlement) {
        return new MessageItem.Settlement(
                settlement.getId(),
                settlement.getTotalAmount(),
                settlement.amountPerPerson(),
                new MessageItem.Payee(
                        settlement.getPayee().getId(),
                        settlement.getPayee().getName(),
                        settlement.getPayeeBankName(),
                        accountNoCipher.decrypt(settlement.getPayeeAccountNo())));
    }
}
