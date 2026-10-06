# Secuencia — Reporte completo de un producto en P2 (`GET /reports/products/{type}/{id}`)

Implementado en `ReportController` / `ReportRestMapper` (R5), `GenerateProductReportUseCaseImpl` (R3),
`ProductRestAdapter` y `MovementRestAdapter` sobre `RestSource` (R7) y `ProductReportCalculator` (R2).
Ejemplo: el ahorro de A en octubre, `?from=2026-10-01&to=2026-10-31&size=5`.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant GW as api-gateway
    participant Ctrl as ReportController
    participant UC as GenerateProductReportUseCaseImpl
    participant PP as ProductRestAdapter
    participant Acc as account-service
    participant MP as MovementRestAdapter
    participant Tx as transaction-service
    participant Calc as ProductReportCalculator
    participant EH as GlobalExceptionHandler

    C->>GW: GET /api/v1/reports/products/accounts/{id}?from&to&size=5
    GW->>Ctrl: lb://report-service (breaker de 2 s)
    Ctrl->>Ctrl: "accounts" → ProductType.ACCOUNT (otro valor → 400)
    Ctrl->>UC: execute(ProductReportQuery)
    UC->>UC: DateRange.of(from, to, 366) y PageRequest.of(page, size, 100)
    alt intervalo inválido
        UC-->>EH: InvalidRangeException
        EH-->>C: 400 INVALID_RANGE (sin haber llamado a nadie)
    end

    UC->>PP: findById(ACCOUNT, id)
    PP->>Acc: GET /accounts/{id} (breaker + 2 s)
    alt 404
        Acc-->>PP: 404
        PP-->>UC: vacío
        UC-->>EH: ProductNotFoundException
        EH-->>C: 404 PRODUCT_NOT_FOUND
    end
    Acc-->>PP: Account
    PP-->>UC: ReportProduct (SAVINGS, **** 4871)

    UC->>MP: countInRange(id, octubre)
    MP->>Tx: GET /products/{id}/transactions?status=COMPLETED&from&to&size=1
    Tx-->>MP: totalElements
    alt totalElements > 5000
        UC-->>EH: RangeTooLargeException
        EH-->>C: 422 RANGE_TOO_LARGE (sin traer los movimientos)
    end

    par Todos los del intervalo
        UC->>MP: findInRange(id, octubre)
        loop página 0..totalPages-1, de 100 en 100
            MP->>Tx: GET ...?status=COMPLETED&from&to&page=n&size=100
            Tx-->>MP: página
        end
    and Saldo inicial
        UC->>MP: findLastUpTo(id, 30 de setiembre)
        MP->>Tx: GET ...?status=COMPLETED&to=2026-09-30&size=1
        Tx-->>MP: último movimiento anterior (o ninguno)
    end

    UC->>UC: ordenar MOST_RECENT_FIRST
    UC->>Calc: summarize(todos, anterior)
    Calc-->>UC: ReportSummary (conteo, totales por tipo, comisiones, saldos)
    UC->>UC: MovementPage.slice(ordenados, página 0 de 5)
    UC-->>Ctrl: ProductReport
    Ctrl-->>C: 200 ProductReport (generatedAt)

    Note over PP,Tx: Cualquier falla que no sea 404 (timeout de 2 s, 5xx, circuito abierto)<br/>→ DownstreamServiceUnavailableException → 503 SERVICE_UNAVAILABLE
```

## Notas

- **Sin base de datos en P2.** Todo se consulta a los servicios dueños y se calcula en memoria; el
  costo de recorrer páginas por REST es aceptable con datos de demo (ficha §12). En P3 los dos
  adaptadores se reemplazan por consultas al read model y el caso de uso no cambia.
- **El tope se comprueba antes de traer.** Una página de tamaño 1 basta para saber `totalElements`;
  así un intervalo enorme se rechaza con dos llamadas, no con cincuenta.
- **El resumen y la página salen de la misma lista.** El resumen necesita todos los movimientos del
  intervalo (regla 4), así que la página se corta en memoria (`MovementPage.slice`) en vez de pedirla
  aparte a transaction-service.
- **Solo `COMPLETED`.** transaction-service filtra con `status=COMPLETED` (quedan fuera los
  `FAILED`, `PENDING`, `DISCARDED` y `REVERSED`, también la comisión de una pata compensada) y el
  calculador vuelve a filtrar por si acaso.
- **Saldo inicial.** Es el `resultingBalance` del último movimiento hasta el día anterior a `from`;
  la apertura de una cuenta no es un movimiento, así que una cuenta sin movimientos previos no tiene
  saldo inicial.
- **Créditos y tarjetas.** El mismo flujo con `credits` / `credit-cards`: la cabecera viene de
  credit-service y el saldo resultante es el saldo pendiente o el monto usado. `debit-cards` → 501
  hasta P3.
