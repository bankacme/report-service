package com.bank.report.infrastructure.mapper;

import com.bank.report.domain.model.CardMovements;
import com.bank.report.domain.model.CustomerCardsReport;
import com.bank.report.domain.model.DateRange;
import com.bank.report.domain.model.LastMovementsReport;
import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.ProductReport;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportProduct;
import com.bank.report.domain.model.ReportSummary;
import com.bank.report.infrastructure.adapter.in.rest.dto.MovementPage;
import com.bank.report.infrastructure.adapter.in.rest.dto.Period;
import com.bank.report.infrastructure.adapter.in.rest.dto.ProductHeader;
import com.bank.report.infrastructure.adapter.in.rest.dto.ReportMovement;
import com.bank.report.infrastructure.adapter.in.rest.dto.TypeTotal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Dominio → DTOs generados. A mano y no con MapStruct: los enums del dominio y del contrato se
 * llaman igual, y los totales y saldos opcionales se leen mejor explícitos. Los nombres de los DTO
 * que chocan con el dominio (ProductType, ReportMovement, ...) se escriben calificados.
 */
@Component
public class ReportRestMapper {

    /** Segmento de la ruta → tipo (data-model 1.4). */
    private static final Map<String, ProductType> PATH_TYPES = Map.of(
            "accounts", ProductType.ACCOUNT,
            "credits", ProductType.CREDIT,
            "credit-cards", ProductType.CREDIT_CARD,
            "debit-cards", ProductType.DEBIT_CARD);

    /** Un valor fuera de los cuatro del contrato es 400 VALIDATION_ERROR. */
    public ProductType toProductType(String pathSegment) {
        ProductType type = PATH_TYPES.get(pathSegment);
        if (type == null) {
            throw new IllegalArgumentException("productType must be one of " + PATH_TYPES.keySet().stream()
                    .sorted().toList() + ", got: " + pathSegment);
        }
        return type;
    }

    public com.bank.report.infrastructure.adapter.in.rest.dto.ProductReport toDto(ProductReport report) {
        var dto = new com.bank.report.infrastructure.adapter.in.rest.dto.ProductReport();
        dto.setProduct(header(report.product()));
        dto.setPeriod(period(report.period()));
        dto.setSummary(summary(report.summary()));
        MovementPage page = new MovementPage();
        page.setPage(report.movements().page());
        page.setSize(report.movements().size());
        page.setTotalElements(report.movements().totalElements());
        page.setTotalPages(report.movements().totalPages());
        page.setContent(movements(report.movements().content()));
        dto.setMovements(page);
        dto.setGeneratedAt(timestamp(report.generatedAt()));
        return dto;
    }

    public com.bank.report.infrastructure.adapter.in.rest.dto.LastMovementsReport toDto(LastMovementsReport report) {
        var dto = new com.bank.report.infrastructure.adapter.in.rest.dto.LastMovementsReport();
        dto.setProduct(header(report.product()));
        dto.setLimit(report.limit());
        dto.setMovements(movements(report.movements()));
        dto.setGeneratedAt(timestamp(report.generatedAt()));
        return dto;
    }

    public com.bank.report.infrastructure.adapter.in.rest.dto.CustomerCardsReport toDto(CustomerCardsReport report) {
        var dto = new com.bank.report.infrastructure.adapter.in.rest.dto.CustomerCardsReport();
        dto.setCustomerId(report.customerId());
        dto.setDebitCardsIncluded(report.debitCardsIncluded());
        dto.setCreditCards(cards(report.creditCards()));
        dto.setDebitCards(cards(report.debitCards()));
        dto.setGeneratedAt(timestamp(report.generatedAt()));
        return dto;
    }

    private List<com.bank.report.infrastructure.adapter.in.rest.dto.CardMovements> cards(List<CardMovements> cards) {
        return cards.stream().map(card -> {
            var dto = new com.bank.report.infrastructure.adapter.in.rest.dto.CardMovements();
            dto.setCard(header(card.card()));
            dto.setMovements(movements(card.movements()));
            return dto;
        }).toList();
    }

    private ProductHeader header(ReportProduct product) {
        ProductHeader dto = new ProductHeader();
        dto.setProductId(product.productId());
        dto.setProductType(com.bank.report.infrastructure.adapter.in.rest.dto.ProductType
                .valueOf(product.productType().name()));
        dto.setCategory(com.bank.report.infrastructure.adapter.in.rest.dto.ProductCategory
                .valueOf(product.category().name()));
        dto.setCustomerId(product.customerId());
        dto.setStatus(com.bank.report.infrastructure.adapter.in.rest.dto.ProductStatus
                .valueOf(product.status().name()));
        dto.setMaskedNumber(product.attributes().maskedNumber());
        dto.setCreditLimit(amount(product.attributes().creditLimit()));
        dto.setPrincipalAmount(amount(product.attributes().principalAmount()));
        dto.setDueDate(product.attributes().dueDate());
        dto.setLinkedAccountIds(product.attributes().linkedAccountIds());
        dto.setMainAccountId(product.attributes().mainAccountId());
        dto.setExpiryDate(product.attributes().expiryDate());
        return dto;
    }

    private static Period period(DateRange range) {
        Period dto = new Period();
        dto.setFrom(range.from());
        dto.setTo(range.to());
        return dto;
    }

    private com.bank.report.infrastructure.adapter.in.rest.dto.ReportSummary summary(ReportSummary summary) {
        var dto = new com.bank.report.infrastructure.adapter.in.rest.dto.ReportSummary();
        dto.setMovementsCount((int) summary.movementsCount());
        dto.setTotalsByType(summary.totalsByType().stream().map(total -> {
            TypeTotal item = new TypeTotal();
            item.setType(com.bank.report.infrastructure.adapter.in.rest.dto.MovementType.valueOf(total.type().name()));
            item.setCount((int) total.count());
            item.setTotalAmount(total.totalAmount().amount());
            return item;
        }).toList());
        dto.setTotalFees(amount(summary.totalFees()));
        dto.setOpeningBalance(amount(summary.openingBalance()));
        dto.setClosingBalance(amount(summary.closingBalance()));
        return dto;
    }

    private List<ReportMovement> movements(List<com.bank.report.domain.model.ReportMovement> movements) {
        return movements.stream().map(movement -> {
            ReportMovement dto = new ReportMovement();
            dto.setMovementId(movement.movementId());
            dto.setType(com.bank.report.infrastructure.adapter.in.rest.dto.MovementType
                    .valueOf(movement.type().name()));
            dto.setAmount(movement.amount().amount());
            dto.setResultingBalance(amount(movement.resultingBalance()));
            dto.setDescription(movement.description());
            dto.setTransferId(movement.transferId());
            dto.setParentMovementId(movement.parentMovementId());
            dto.setFee(amount(movement.fee()));
            dto.setAccountId(movement.accountId());
            dto.setOccurredAt(timestamp(movement.occurredAt()));
            return dto;
        }).toList();
    }

    private static BigDecimal amount(Money money) {
        return money == null ? null : money.amount();
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
