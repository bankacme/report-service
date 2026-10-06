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
- [x] R4. Persistencia: no aplica en P2 (read model en P3).
- [x] R5. `ReportController` (implementa `ReportsApi` generado) y `GlobalExceptionHandler`:
  - `productType` de la ruta (`accounts`, `credits`, `credit-cards`, `debit-cards`) lo traduce `ReportRestMapper`; otro valor → 400 `VALIDATION_ERROR` sin llamar al caso de uso.
  - 400 `INVALID_RANGE`, 404 `PRODUCT_NOT_FOUND`, 422 `RANGE_TOO_LARGE`, 501 `NOT_AVAILABLE`, 503 `SERVICE_UNAVAILABLE`; fechas mal escritas, `from`/`to` ausentes, `limit`/`size` fuera de rango → 400 `VALIDATION_ERROR`.
  - `GET /reports/product-categories/{category}` → 501 (desde P3).
- [x] R6. `UseCaseConfig` (casos de uso, calculadoras y `ReportSettings` desde `report.*`) y `ClockConfig` (`bank.zone`). Se hizo junto con R5 y R7: el contexto completo necesita los casos de uso y sus puertos.
- [x] R7. Adaptadores REST (`adapter/out/rest`), cada fuente con circuit breaker y timeout de 2 s (`RestSource`: 404 → vacío; cualquier otra falla → 503):
  - `ProductRestAdapter`: `GET /accounts/{id}`, `/credits/{id}`, `/credit-cards/{id}` y `/credit-cards?customerId=`; categoría, estado y cabecera según data-model 3.1; `debit-cards` → 501.
  - `MovementRestAdapter`: siempre `GET /products/{id}/transactions?status=COMPLETED`. Conteo con `size=1` (`totalElements`), intervalo completo recorriendo páginas de 100, saldo inicial con `to = from − 1` sin `from` y `size=1`, últimos N con `size=N`.
  - Pruebas con WireMock: mapeo de campos, varias páginas, 404, 500 y timeout.
- [x] R8. Calidad: Checkstyle limpio; Jacoco ~98 % de instrucciones y líneas (las ramas sin cubrir son validaciones de `null` de los records). Estrategia de la ficha §10:

  | Capa | Pruebas |
  |---|---|
  | Calculadoras | `ProductReportCalculatorTest` (ejemplo de data-model §8, sin movimientos, revertidos, sin saldo inicial), `LastMovementsSelectorTest` (menos de N, revertidos) |
  | VO | `DateRangeTest` (inválido, 366 vs 367 días, zona Lima), `PageRequestTest`, `MovementPageTest`, `ModelTest` |
  | Casos de uso | Reporte (paginación, resumen de todo el intervalo, 404, tipo equivocado, `RANGE_TOO_LARGE` sin traer movimientos), últimos N (límite), tarjetas de un cliente (sin 404, débito en P2) |
  | Adaptadores REST | WireMock: mapeo, varias páginas, 404, 500, timeout y **circuito abierto** (la 5.ª llamada no llega a la fuente) |
  | Controller | Códigos 400/404/422/501/503, parámetros obligatorios y fuera de rango |
  | Fuera de P2 | Roles y `CUSTOMER` sobre productos ajenos (P3, con seguridad), proyección y consultas Mongo (P3) |
- [x] R9. Postman: carpeta `report-service` en `bankacme.postman_collection.json` (entre la de VIP/PYME y la del Gateway), variable `reportBaseUrl` = `http://localhost:8080/api/v1`. 15 requests: reporte completo del ahorro de A (cabecera, resumen, orden, saldo final = movimiento más reciente), página 2 con el mismo resumen, reporte del crédito de A, últimos 10 y últimos 2 de la tarjeta de A, tarjetas de A y de un cliente sin tarjetas, y los errores 400/404/501. Intervalo fijo de 2026. Verificada contra los datos reales (40 comprobaciones).
- [x] R10. Cierre: README, diagramas de la ficha (§11) en `docs/` (Mermaid validado con `mmdc`) y etiqueta (`report-v1`, la pone el usuario). Durante las pruebas de R7 se detectó y corrigió en transaction-service que las patas de transferencia no registraban su comisión `FEE`.

## Diagramas
- `docs/uml/report-domain.md`: modelo de lectura (`ReportProduct`, `ReportMovement`), VO, resultados, calculadoras y reglas.
- `docs/sequence/product-report-p2.md`: reporte completo de un producto en P2 (validación, cabecera, conteo y tope, todas las páginas + saldo inicial en paralelo, resumen y página).
- `docs/sequence/card-last-movements.md`: últimos 10 movimientos de una tarjeta.
- `docs/sequence/customer-cards.md`: tarjetas de un cliente con sus últimos movimientos.
- Pendientes para P3 (dependen del read model): reporte de producto con el read model y proyección de un evento.

## Comandos
- Compilar, estilo, tests y cobertura: `.\mvnw verify` (reporte en `target/site/jacoco/index.html`)
- Arrancar (necesita `config-server` y `eureka-server` arriba): `.\mvnw spring-boot:run`

## Resincronizar el contrato
```powershell
.\copy-contracts.ps1 -ServiceName report-service
```
