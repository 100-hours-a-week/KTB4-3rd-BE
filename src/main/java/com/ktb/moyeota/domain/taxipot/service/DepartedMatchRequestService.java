package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.taxipot.repository.TaxiPotMatchRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartedMatchRequestService {

    private final TaxiPotMatchRequestRepository taxiPotMatchRequestRepository;
    private final Clock clock;

    @Transactional
    public int failDeparted() {
        LocalDateTime now = LocalDateTime.now(clock);
        return taxiPotMatchRequestRepository.failDeparted(now.truncatedTo(ChronoUnit.MINUTES), now);
    }
}
