package com.tmw.backend.exception;

import com.tmw.backend.invoice.Invoice;
import com.tmw.backend.match.Match;
import com.tmw.backend.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ExceptionFactoryService {

    private final MatchExceptionRepository matchExceptionRepository;

    public ExceptionFactoryService(MatchExceptionRepository matchExceptionRepository) {
        this.matchExceptionRepository = matchExceptionRepository;
    }


    @Transactional
    public MatchException createMatchException(Invoice invoice,
                                               Match match,
                                               AppUser resolvedBy,
                                               ExceptionType type,
                                               Map<String, Object> details) {
        MatchException exception = new MatchException();
        exception.setInvoice(invoice);
        exception.setMatch(match);
        exception.setResolvedBy(resolvedBy);
        exception.setType(type);
        exception.setStatus(ExceptionStatus.OPEN); // Mặc định trạng thái là OPEN

        if (details != null && !details.isEmpty()) {
            exception.setDetails(details);
        }

        return matchExceptionRepository.save(exception);
    }
}