package com.ktb.moyeota.domain.taxipot.matching;

import java.util.List;

public interface CandidateSelector {

    List<MatchResult> select(List<MatchCandidate> ranked);
}
