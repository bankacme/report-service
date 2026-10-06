# Secuencia — Tarjetas de un cliente (`GET /reports/customers/{customerId}/cards/last-movements`)

Implementado en `GetCustomerCardsReportUseCaseImpl` (R3) y `ProductRestAdapter` /
`MovementRestAdapter` (R7). En P2 solo hay tarjetas de crédito.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant Ctrl as ReportController
    participant UC as GetCustomerCardsReportUseCaseImpl
    participant PP as ProductRestAdapter
    participant Cr as credit-service
    participant MP as MovementRestAdapter
    participant Tx as transaction-service

    C->>Ctrl: GET /reports/customers/{customerId}/cards/last-movements
    Ctrl->>UC: execute(customerId)
    UC->>PP: findCardsByCustomer(customerId)
    PP->>Cr: GET /credit-cards?customerId={id} (sin filtro de estado)
    Cr-->>PP: [tarjeta 1, tarjeta 2, ...] (o [])
    PP-->>UC: ReportProduct por tarjeta
    loop cada tarjeta, una tras otra (concatMap)
        UC->>MP: findLast(cardId, 10)
        MP->>Tx: GET /products/{cardId}/transactions?status=COMPLETED&size=10
        Tx-->>MP: últimos 10
        MP-->>UC: movimientos
    end
    UC->>UC: separar CREDIT_CARD / DEBIT_CARD
    UC->>PP: debitCardsAvailable()
    PP-->>UC: false (P2)
    UC-->>Ctrl: CustomerCardsReport
    Ctrl-->>C: 200 (creditCards, debitCards = [], debitCardsIncluded = false)

    Note over UC,Tx: Si credit-service o transaction-service no responde → 503:<br/>no se devuelve un reporte con algunas tarjetas sí y otras no
```

## Notas

- **Sin 404 por cliente.** report-service no conoce clientes: uno sin tarjetas (o inexistente)
  recibe listas vacías.
- **Cualquier estado.** Se listan también las tarjetas `OVERDUE` y `CLOSED`, cada una con sus últimos
  movimientos.
- **`concatMap`, no `flatMap`:** conserva el orden en que credit-service devuelve las tarjetas y evita
  lanzar N llamadas simultáneas a transaction-service.
- **P3:** las tarjetas de débito vienen del read model (`debitCardsIncluded = true`) y sus
  movimientos son los pagos de `debit-service`.
