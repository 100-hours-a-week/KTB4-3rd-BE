package com.ktb.moyeota.domain.congestion.batch;

import com.ktb.moyeota.domain.congestion.window.CongestionWindow;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingCongestionAnalysisRequester implements CongestionAnalysisRequester {

    @Override
    public void request(CongestionWindow window, List<Long> spotIds) {
        log.info("[CONGESTION_WINDOW_CLOSED] window_start={} window_end={} spots={}",
                window.start(), window.end(), spotIds.size());
    }
}
