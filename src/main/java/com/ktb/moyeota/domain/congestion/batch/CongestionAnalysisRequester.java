package com.ktb.moyeota.domain.congestion.batch;

import com.ktb.moyeota.domain.congestion.window.CongestionWindow;
import java.util.List;

public interface CongestionAnalysisRequester {

    void request(CongestionWindow window, List<Long> spotIds);
}
