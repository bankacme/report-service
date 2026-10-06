# Secuencia — Últimos movimientos de una tarjeta (`GET /reports/products/credit-cards/{id}/last-movements`)

Implementado en `GetLastMovementsUseCaseImpl` (R3), `ProductRestAdapter` / `MovementRestAdapter` (R7)
y `LastMovementsSelector` (R2). Sirve igual para cuentas y créditos.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant Ctrl as ReportController
    participant UC as GetLastMovementsUseCaseImpl
    participant PP as ProductRestAdapter
    participant Cr as credit-service
    participant MP as MovementRestAdapter
    participant Tx as transaction-service
    participant Sel as LastMovementsSelector
    participant EH as GlobalExceptionHandler

    C->>Ctrl: GET .../credit-cards/{id}/last-movements?limit=10
    Note over Ctrl: limit fuera de 1-50 → 400 VALIDATION_ERROR (contrato)
    Ctrl->>UC: execute(LastMovementsQuery(CREDIT_CARD, id, 10))
    UC->>PP: findById(CREDIT_CARD, id)
    PP->>Cr: GET /credit-cards/{id}
    alt no existe (o el id es de otro tipo de producto)
        Cr-->>PP: 404
        PP-->>UC: vacío
        UC-->>EH: ProductNotFoundException
        EH-->>C: 404 PRODUCT_NOT_FOUND
    end
    Cr-->>PP: CreditCard
    PP-->>UC: ReportProduct (CREDIT_CARD, **** 1234, línea)
    UC->>MP: findLast(id, 10)
    MP->>Tx: GET /products/{id}/transactions?status=COMPLETED&page=0&size=10
    Tx-->>MP: hasta 10, más recientes primero
    MP-->>UC: CARD_CHARGE / CARD_PAYMENT
    UC->>Sel: select(movimientos, 10)
    Sel-->>UC: solo COMPLETED, ordenados, como mucho 10
    UC-->>Ctrl: LastMovementsReport
    Ctrl-->>C: 200 (product, limit, movements, generatedAt)
```

## Notas

- **Una sola consulta de movimientos:** `size=N` en la primera página ya trae los N más recientes
  (transaction-service ordena más reciente primero). `LastMovementsSelector` vuelve a ordenar y
  cortar, para que el resultado no dependa de ese detalle de la fuente.
- **Movimientos de tarjeta de crédito** = consumos (`CARD_CHARGE`) y pagos (`CARD_PAYMENT`), que
  credit-service registra en el historial de transaction-service (regla 8).
- **El tipo de la ruta decide a quién se pregunta:** un id de cuenta pedido como `credit-cards` se
  busca en credit-service y da 404.
- **Tarjeta de débito:** `debit-cards` → 501 `NOT_AVAILABLE` hasta P3 (lo decide el adaptador REST,
  no el caso de uso).
