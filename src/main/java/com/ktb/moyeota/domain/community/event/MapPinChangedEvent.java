package com.ktb.moyeota.domain.community.event;

import java.math.BigDecimal;

public record MapPinChangedEvent(BigDecimal lat, BigDecimal lng) {
}
