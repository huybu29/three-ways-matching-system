package com.tmw.backend.matching.strategy;

import com.tmw.backend.matching.api.MatchSource;

import java.util.List;

public interface MatchingStrategy {

  
    String strategyName();
   
    boolean supports(MatchSource source);


    List<ProposedMatchLine> match(MatchingContext context);
}