# report-service

Reportes de solo lectura del sistema bancario (P2, paso 2.6). Ficha:
`bank-docs/services/report-service.md`. Contrato: `bank-docs/contracts/report-service/`.

- Puerto **8085**. A través del Gateway: `http://localhost:8080/api/v1/reports/...`.
- **P2 (`report.source=rest`):** sin base de datos. Consulta a `account-service`, `credit-service` y
  `transaction-service` por REST (por nombre en Eureka, circuit breaker y timeout de 2 s) y calcula
  en memoria con Streams. Si una fuente no responde → 503: no se entregan reportes incompletos.
- **P3 (`report.source=readmodel`):** read model en Mongo alimentado por eventos de Kafka; se
  cambia el adaptador sin tocar dominio ni casos de uso.

## Estado (receta R1–R10)
- [x] R1. Esqueleto: desde `bank-service-template`, paquete `com.bank.report`, contrato copiado a
  `src/main/resources/openapi/`, `bank-config/report-service.yml`. Sin Mongo (P2). Resilience4j y
  WireMock como en los demás servicios con clientes REST; `LoadBalancerConfig` para llamar por nombre.
- [x] R2. Dominio (`domain/`, sin Spring):
  - Modelo de lectura: `ReportProduct`, `ReportMovement` (con el orden `MOST_RECENT_FIRST`: `occurredAt` y luego `movementId`, descendentes), `ProductAttributes` (y `mask` del número de cuenta).
  - VO: `Money`, `DateRange` (366 días contando ambos extremos → `InvalidRangeException`; `[from 00:00, to+1 00:00)` en `bank.zone`), `PageRequest` (por defecto 0/20), `MovementPage.slice` (página en memoria de la lista ordenada).
  - Resultados: `ReportSummary`, `TypeTotal`, `ProductReport`, `LastMovementsReport`, `CardMovements`, `CustomerCardsReport`.
  - `ProductReportCalculator` (Streams: solo `COMPLETED`, totales por tipo en orden alfabético como el contrato, comisiones, saldo inicial y final) y `LastMovementsSelector`.
  - Excepciones: `ProductNotFoundException` (404), `InvalidRangeException` (400), `RangeTooLargeException` (422), `NotAvailableException` (501), `DownstreamServiceUnavailableException` (503).
  - `CategoryReportCalculator` no se hace en P2: el reporte por categoría responde 501 hasta P3.
- [x] R3. Casos de uso y puertos (`application/`, sin Spring):
  - Puertos de salida: `ProductQueryPort` (`findById`, `findCardsByCustomer`, `debitCardsAvailable`) y `MovementQueryPort` (`countInRange`, `findInRange`, `findLastUpTo` para el saldo inicial, `findLast`).
  - `GenerateProductReportUseCaseImpl`: valida intervalo y página antes de llamar a nadie (400) → cabecera (404) → cuenta y rechaza si pasa `report.max-movements` (422, sin traerlos) → todos los del intervalo + el anterior → resumen de todo el intervalo y página en memoria.
  - `GetLastMovementsUseCaseImpl` (10 por defecto, 1–50) y `GetCustomerCardsReportUseCaseImpl` (tarjetas de cualquier estado, 10 movimientos cada una, sin 404, `debitCardsIncluded` según la fuente).
  - `ReportSettings`: límites del Config Server. El reporte por categoría no tiene caso de uso en P2 (501).
  - Pruebas con `InMemorySources` (implementa ambos puertos y registra las llamadas).
- [ ] R4. Persistencia: no aplica en P2 (read model en P3).
- [ ] R5. Controller y `GlobalExceptionHandler`.
- [ ] R6. Configuración y arranque.
- [ ] R7. Clientes REST a las tres fuentes.
- [ ] R8. Calidad.
- [ ] R9. Postman.
- [ ] R10. Cierre.

## Comandos
- Compilar, estilo, tests y cobertura: `.\mvnw verify` (reporte en `target/site/jacoco/index.html`)
- Arrancar (necesita `config-server` y `eureka-server` arriba): `.\mvnw spring-boot:run`

## Resincronizar el contrato
```powershell
.\copy-contracts.ps1 -ServiceName report-service
```
