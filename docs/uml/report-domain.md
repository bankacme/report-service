# UML — Dominio de report-service

Paquete `com.bank.report.domain` (R2). Servicio de **solo lectura**: no hay aggregates con
comportamiento de negocio. El dominio es un modelo de lectura inmutable y dos **cálculos puros**
con Streams. En P2 el modelo se arma en cada consulta a partir de account-, credit- y
transaction-service; en P3 vendrá de un read model en Mongo, sin cambiar este paquete.

## Modelo de lectura y resultados

```mermaid
classDiagram
    direction TB

    class ReportProduct {
        <<read model>>
        +String productId
        +ProductType productType
        +ProductCategory category
        +String customerId
        +ProductStatus status
        +ProductAttributes attributes
        +Instant updatedAt
    }
    class ProductAttributes {
        <<value object>>
        +String maskedNumber
        +Money creditLimit
        +Money principalAmount
        +LocalDate dueDate
        +List linkedAccountIds
        +String mainAccountId
        +String expiryDate
        +mask(number)$ String
    }
    class ReportMovement {
        <<read model>>
        +String movementId
        +String operationId
        +String productId
        +MovementType type
        +Money amount
        +Money resultingBalance
        +MovementStatus status
        +String transferId
        +String parentMovementId
        +Instant occurredAt
        +MOST_RECENT_FIRST$ Comparator
        +isCompleted() boolean
    }

    class DateRange {
        <<value object>>
        +LocalDate from
        +LocalDate to
        +of(from, to, maxDays)$ DateRange
        +startInclusive(zone) Instant
        +endExclusive(zone) Instant
        +dayBefore() LocalDate
    }
    class PageRequest {
        <<value object>>
        +int page
        +int size
        +of(page, size, maxSize)$ PageRequest
    }
    class MovementPage {
        <<value object>>
        +int page
        +int size
        +long totalElements
        +int totalPages
        +slice(sorted, request)$ MovementPage
    }
    class Money {
        <<value object>>
        +BigDecimal amount
        +String currency = PEN
        +plus(Money) Money
    }

    class ReportSummary {
        <<result>>
        +long movementsCount
        +List~TypeTotal~ totalsByType
        +Money totalFees
        +Money openingBalance
        +Money closingBalance
    }
    class TypeTotal {
        <<result>>
        +MovementType type
        +long count
        +Money totalAmount
    }
    class ProductReport {
        <<result>>
        +Instant generatedAt
    }
    class LastMovementsReport {
        <<result>>
        +int limit
        +Instant generatedAt
    }
    class CardMovements {
        <<result>>
    }
    class CustomerCardsReport {
        <<result>>
        +String customerId
        +boolean debitCardsIncluded
        +Instant generatedAt
    }

    class ProductReportCalculator {
        <<domain service>>
        +summarize(inRange, previous) ReportSummary
    }
    class LastMovementsSelector {
        <<domain service>>
        +select(movements, limit) List
    }

    ReportProduct *-- ProductAttributes
    ReportMovement *-- Money
    ReportSummary *-- "0..*" TypeTotal
    ProductReport --> ReportProduct : product
    ProductReport --> DateRange : period
    ProductReport --> ReportSummary : summary
    ProductReport --> MovementPage : movements
    MovementPage o-- "0..*" ReportMovement
    LastMovementsReport --> ReportProduct
    LastMovementsReport o-- "0..10" ReportMovement
    CustomerCardsReport o-- "0..*" CardMovements : creditCards / debitCards
    CardMovements --> ReportProduct : card
    CardMovements o-- "0..10" ReportMovement
    ProductReportCalculator ..> ReportSummary : crea
    LastMovementsSelector ..> ReportMovement : ordena y corta
    MovementPage ..> PageRequest : usa
```

## Enums

| Enum | Valores |
|---|---|
| `ProductType` | `ACCOUNT`, `CREDIT`, `CREDIT_CARD`, `DEBIT_CARD` (en la ruta: `accounts`, `credits`, `credit-cards`, `debit-cards`) |
| `ProductCategory` | `SAVINGS`, `CHECKING`, `FIXED_TERM`, `PERSONAL_CREDIT`, `BUSINESS_CREDIT`, `CREDIT_CARD`, `DEBIT_CARD` |
| `ProductStatus` | `ACTIVE`, `INACTIVE`, `OVERDUE`, `PAID`, `CLOSED` |
| `MovementType` | Los 11 de transaction-service |
| `MovementStatus` | `COMPLETED`, `REVERSED` |

## Reglas y dónde se aplican

| Regla | Dónde |
|---|---|
| Intervalo obligatorio, `from ≤ to`, máximo 366 días (contando ambos extremos) → 400 `INVALID_RANGE` | `DateRange.of` |
| Página 0..n, tamaño 1–100 (por defecto 20) | `PageRequest.of` |
| Solo movimientos `COMPLETED`; los revertidos no cuentan | `ProductReportCalculator`, `LastMovementsSelector` (y la fuente filtra `status=COMPLETED`) |
| Más reciente primero; empate por `movementId` descendente | `ReportMovement.MOST_RECENT_FIRST` |
| El resumen cubre todo el intervalo, no solo la página | `GenerateProductReportUseCaseImpl` + `MovementPage.slice` |
| Saldo inicial = último movimiento antes del intervalo; final = el más reciente del intervalo | `ProductReportCalculator` |
| Totales por tipo en orden alfabético del tipo (como el ejemplo del contrato) | `ProductReportCalculator` |
| Más de `report.max-movements` (5000) → 422 `RANGE_TOO_LARGE`, sin traerlos | `GenerateProductReportUseCaseImpl` |
| Últimos N: 10 por defecto, 1–50 | `GetLastMovementsUseCaseImpl` |
