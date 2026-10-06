package com.bank.report.domain.exception;

/**
 * 503 SERVICE_UNAVAILABLE: una fuente (account-, credit- o transaction-service) no respondió a
 * tiempo o su circuito está abierto. No se entregan reportes incompletos (regla 12).
 */
public class DownstreamServiceUnavailableException extends RuntimeException {

    public DownstreamServiceUnavailableException(String serviceName, Throwable cause) {
        super(serviceName + " did not respond in time", cause);
    }
}
