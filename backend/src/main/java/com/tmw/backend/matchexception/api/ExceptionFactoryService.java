package com.tmw.backend.matchexception.api;

import java.util.List;
import java.util.UUID;

public interface ExceptionFactoryService {


    MatchExceptionDto createOrGetOpen(CreateMatchExceptionCommand command);

   
    List<MatchExceptionDto> createBatchIdempotent(List<CreateMatchExceptionCommand> commands);

  
    int autoCloseOpenExceptions(UUID invoiceId, UUID actorId, String reason);
}