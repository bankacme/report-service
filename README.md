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
- [ ] R2. Dominio: `DateRange`, `PageRequest`, `Money`, modelo de lectura, `ProductReportCalculator`, `LastMovementsSelector`.
- [ ] R3. Casos de uso y puertos (`ProductQueryPort`, `MovementQueryPort`).
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
