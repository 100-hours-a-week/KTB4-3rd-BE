package com.ktb.moyeota.domain.taxipot.stream;

import java.util.Optional;

public interface CellEntries {

    Optional<CellRequest> request(long requestId);

    Optional<CellPot> pot(String potId);

    void put(CellRequest request);

    void removeRequest(long requestId);

    void put(CellPot pot);

    void removePot(String potId);
}
